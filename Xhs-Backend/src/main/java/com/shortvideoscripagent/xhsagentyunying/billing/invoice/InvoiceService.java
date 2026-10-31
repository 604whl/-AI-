package com.shortvideoscripagent.xhsagentyunying.billing.invoice;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.shortvideoscripagent.xhsagentyunying.billing.invoice.domain.entity.Invoice;
import com.shortvideoscripagent.xhsagentyunying.billing.invoice.domain.mapper.InvoiceMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.order.domain.entity.SubscriptionOrder;
import com.shortvideoscripagent.xhsagentyunying.billing.order.domain.mapper.SubscriptionOrderMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.statement.StatementService;
import com.shortvideoscripagent.xhsagentyunying.billing.statement.domain.entity.BillingStatement;
import com.shortvideoscripagent.xhsagentyunying.billing.statement.domain.mapper.BillingStatementMapper;
import com.shortvideoscripagent.xhsagentyunying.billing.support.BillingMath;
import com.shortvideoscripagent.xhsagentyunying.billing.support.IdempotencyReplay;
import com.shortvideoscripagent.xhsagentyunying.common.exception.BusinessException;
import com.shortvideoscripagent.xhsagentyunying.dto.billing.invoice.InvoiceDtos;
import com.shortvideoscripagent.xhsagentyunying.infra.audit.AuditWriter;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceRole;
import com.shortvideoscripagent.xhsagentyunying.workspace.WorkspaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * S4-4 发票服务。
 * <p>
 * <b>范围（D10）</b>：仅个人普票 + 企业普票，不支持专票。
 * </p>
 * <p>
 * <b>开票前置校验</b>：
 * <ol>
 *   <li>目标订单存在且处于已支付状态（{@code paid} / {@code fulfilled}）；</li>
 *   <li>订单<b>未退款</b>（{@code refunded} 不可开票）；</li>
 *   <li>该订单尚无有效发票（唯一索引 {@code uq_invoice_active_order} 兜底）。</li>
 * </ol>
 * </p>
 * <p>
 * <b>红冲语义</b>：已开票订单退款时，
 * <ul>
 *   <li>原发票 {@code status → 'red_flushed'}，{@code red_flush_invoice_id} 指向红冲记录；</li>
 *   <li>新增红冲记录 {@code status = 'red'}，金额为负数表示冲减；</li>
 *   <li><b>原发票不物理删除</b>（留档要求）。</li>
 * </ul>
 * </p>
 * <p>
 * <b>幂等</b>：{@code (workspace_id, idempotency_key)} 唯一索引。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    /** 发票类型：个人普票 */
    public static final String TYPE_PERSONAL = "personal";
    /** 发票类型：企业普票 */
    public static final String TYPE_COMPANY = "company";

    /** 发票状态：已开票 */
    public static final String STATUS_ISSUED = "issued";
    /** 发票状态：红冲记录 */
    public static final String STATUS_RED = "red";
    /** 发票状态：已被红冲 */
    public static final String STATUS_RED_FLUSHED = "red_flushed";

    /** 默认税率（普票 6%） */
    private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("0.0600");

    private static final int CODE_BAD_REQUEST = 40017;
    private static final int CODE_NOT_FOUND = 40417;
    private static final int CODE_CONFLICT = 40917;
    private static final int CODE_FORBIDDEN = 40317;

    private final InvoiceMapper invoiceMapper;
    private final SubscriptionOrderMapper orderMapper;
    private final BillingStatementMapper statementMapper;
    private final WorkspaceService workspaceService;
    private final AuditWriter auditWriter;
    private final IdempotencyReplay idempotencyReplay;
    private final InvoicePdfGenerator pdfGenerator;
    private final InvoiceStorageService storageService;

    // ══════════════════════════════════════════════════════════════════
    // 读
    // ══════════════════════════════════════════════════════════════════

    /**
     * 工作区发票分页列表（OWNER / ADMIN）。
     */
    public Page<InvoiceDtos.InvoiceView> listByWorkspace(String workspaceId, Long userId,
                                                         String status, int page, int size) {
        WorkspaceRole role = requireMember(workspaceId, userId);
        if (!role.atLeast(WorkspaceRole.ADMIN)) {
            throw new BusinessException(CODE_FORBIDDEN, "insufficient_role");
        }
        LambdaQueryWrapper<Invoice> q = new LambdaQueryWrapper<>();
        q.eq(Invoice::getWorkspaceId, workspaceId);
        if (status != null && !status.isBlank()) {
            q.eq(Invoice::getStatus, normalizeStatus(status));
        }
        q.orderByDesc(Invoice::getCreatedAt);

        Page<Invoice> p = invoiceMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100)), q);
        Page<InvoiceDtos.InvoiceView> result = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        result.setRecords(p.getRecords().stream().map(this::toView).toList());
        return result;
    }

    /**
     * 管理端发票分页列表。
     */
    public Page<InvoiceDtos.InvoiceView> listForAdmin(String status, String workspaceId,
                                                       String invoiceType, int page, int size) {
        LambdaQueryWrapper<Invoice> q = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            q.eq(Invoice::getStatus, normalizeStatus(status));
        }
        if (workspaceId != null && !workspaceId.isBlank()) {
            q.eq(Invoice::getWorkspaceId, workspaceId.trim());
        }
        if (invoiceType != null && !invoiceType.isBlank()) {
            q.eq(Invoice::getInvoiceType, normalizeType(invoiceType));
        }
        q.orderByDesc(Invoice::getCreatedAt);

        Page<Invoice> p = invoiceMapper.selectPage(
                new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100)), q);
        Page<InvoiceDtos.InvoiceView> result = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        result.setRecords(p.getRecords().stream().map(this::toView).toList());
        return result;
    }

    /**
     * 按 id 查发票（工作区隔离由调用方负责）。
     */
    public Invoice requireInvoiceById(UUID invoiceId) {
        if (invoiceId == null) {
            throw new BusinessException(CODE_BAD_REQUEST, "invoice_id_required");
        }
        Invoice invoice = invoiceMapper.selectById(invoiceId);
        if (invoice == null) {
            throw new BusinessException(CODE_NOT_FOUND, "invoice_not_found");
        }
        return invoice;
    }

    // ══════════════════════════════════════════════════════════════════
    // 写：开票
    // ══════════════════════════════════════════════════════════════════

    /**
     * 开具发票（幂等键）。
     * <p>
     * 只允许对<b>已支付且未退款</b>的订单 / 账单开票。
     * </p>
     */
    @Transactional
    public InvoiceDtos.InvoiceView issue(InvoiceDtos.IssueInvoiceRequest request,
                                         String idempotencyKey, Long issuedBy) {
        if (request == null) {
            throw new BusinessException(CODE_BAD_REQUEST, "issue_request_required");
        }
        String invoiceType = normalizeType(request.getInvoiceType());
        if (request.getTitle() == null || request.getTitle().isBlank()) {
            throw new BusinessException(CODE_BAD_REQUEST, "invoice_title_required");
        }
        if (TYPE_COMPANY.equals(invoiceType)
                && (request.getTaxNo() == null || request.getTaxNo().isBlank())) {
            throw new BusinessException(CODE_BAD_REQUEST, "tax_no_required_for_company_invoice");
        }

        // ── 幂等重放优先 ──
        // 幂等键是「一次业务事件」的全局标识，重放请求不应依赖订单查询结果
        // （订单状态可能已变，如已退款），否则重放会误报 409。
        String key = normalizeKey(idempotencyKey);
        if (key != null) {
            Invoice existing = invoiceMapper.selectByIdempotencyKeyOnly(key);
            if (existing != null) {
                log.info("Invoice issue replay (idempotent): invoiceNo={}",
                        existing.getInvoiceNo());
                return toView(existing);
            }
        }

        boolean hasOrder = request.getOrderNo() != null && !request.getOrderNo().isBlank();
        boolean hasStatement = request.getStatementId() != null && !request.getStatementId().isBlank();
        if (hasOrder == hasStatement) {
            // orderNo 与 statementId 必须二选一
            throw new BusinessException(CODE_BAD_REQUEST, "order_no_or_statement_id_required");
        }

        SubscriptionOrder order = null;
        BillingStatement statement = null;
        if (hasOrder) {
            order = orderMapper.selectByOrderNo(request.getOrderNo().trim());
            if (order == null) {
                throw new BusinessException(CODE_NOT_FOUND, "order_not_found");
            }
            if ("refunded".equals(order.getStatus())) {
                throw new BusinessException(CODE_CONFLICT, "order_refunded_cannot_invoice");
            }
            if (!"paid".equals(order.getStatus()) && !"fulfilled".equals(order.getStatus())) {
                throw new BusinessException(CODE_CONFLICT, "order_not_paid_cannot_invoice");
            }
        } else {
            statement = statementMapper.selectById(request.getStatementId().trim());
            if (statement == null) {
                throw new BusinessException(CODE_NOT_FOUND, "statement_not_found");
            }
            if (!StatementService.STATUS_PAID.equals(statement.getStatus())) {
                throw new BusinessException(CODE_CONFLICT, "statement_not_paid_cannot_invoice");
            }
        }

        String workspaceId = order != null ? order.getWorkspaceId() : statement.getWorkspaceId();

        // 同一订单已有有效发票 → 拒绝重复开票（唯一索引兜底）
        if (order != null) {
            Invoice active = invoiceMapper.selectActiveByOrderId(order.getId());
            if (active != null) {
                throw new BusinessException(CODE_CONFLICT, "order_already_invoiced");
            }
        }

        BigDecimal amount = order != null
                ? BillingMath.scale(order.getAmount())
                : BillingMath.scale(statement.getTotalAmount());
        if (amount.signum() <= 0) {
            throw new BusinessException(CODE_BAD_REQUEST, "invoice_amount_invalid");
        }
        BigDecimal taxRate = request.getTaxRate() == null
                ? DEFAULT_TAX_RATE
                : request.getTaxRate().setScale(4, RoundingMode.HALF_UP);
        if (taxRate.signum() < 0 || taxRate.compareTo(BigDecimal.ONE) > 0) {
            throw new BusinessException(CODE_BAD_REQUEST, "invalid_tax_rate");
        }
        // 普票：amount 为价税合计，税额 = 价税合计 × 税率 ÷ (1 + 税率)
        BigDecimal taxAmount = amount.multiply(taxRate)
                .divide(BigDecimal.ONE.add(taxRate), BillingMath.SCALE, RoundingMode.HALF_UP);

        OffsetDateTime now = OffsetDateTime.now();
        Invoice invoice = new Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setInvoiceNo(generateInvoiceNo());
        invoice.setWorkspaceId(workspaceId);
        invoice.setInvoiceType(invoiceType);
        invoice.setTitle(truncate(request.getTitle(), 128));
        invoice.setTaxNo(truncate(request.getTaxNo(), 64));
        invoice.setAmount(amount);
        invoice.setCurrency(order != null
                ? (order.getCurrency() == null ? "CNY" : order.getCurrency())
                : (statement.getCurrency() == null ? "CNY" : statement.getCurrency()));
        invoice.setTaxRate(taxRate);
        invoice.setTaxAmount(taxAmount);
        invoice.setStatus(STATUS_ISSUED);
        invoice.setOrderId(order == null ? null : order.getId());
        invoice.setOrderNo(order == null ? null : order.getOrderNo());
        invoice.setStatementId(statement == null ? null : statement.getId());
        invoice.setIssuedBy(issuedBy);
        invoice.setIssuedAt(now);
        invoice.setIdempotencyKey(key);
        invoice.setCreatedAt(now);
        invoice.setUpdatedAt(now);

        try {
            invoiceMapper.insert(invoice);
        } catch (DuplicateKeyException ex) {
            // 并发重复：唯一索引兜底，在独立事务里回读
            if (key != null) {
                Invoice existing = idempotencyReplay.read(
                        () -> invoiceMapper.selectByIdempotencyKeyOnly(key));
                if (existing != null) {
                    return toView(existing);
                }
            }
            throw new BusinessException(CODE_CONFLICT, "invoice_issue_conflict");
        }

        // 生成并存储 PDF（失败不阻塞发票记录 —— 记录已落库，可后续重试生成）
        try {
            byte[] pdf = pdfGenerator.generate(invoice, Map.of());
            String objectKey = storageService.store(workspaceId, invoice.getInvoiceNo(), pdf);
            invoice.setPdfPath(objectKey);
            invoice.setUpdatedAt(OffsetDateTime.now());
            invoiceMapper.updateById(invoice);
        } catch (RuntimeException ex) {
            log.error("Failed to generate/store invoice pdf {}: {}",
                    invoice.getInvoiceNo(), ex.getMessage(), ex);
            auditWriter.recordFailure("INVOICE_PDF_GENERATION_FAILED", "INVOICE",
                    invoice.getId().toString(), null, "invoice_pdf_generation_failed",
                    Map.of("invoiceNo", invoice.getInvoiceNo(),
                            "workspaceId", workspaceId));
        }

        auditWriter.record("INVOICE_ISSUED", "INVOICE", invoice.getId().toString(),
                Map.of("workspaceId", workspaceId,
                        "invoiceNo", invoice.getInvoiceNo(),
                        "invoiceType", invoiceType,
                        "amount", amount.toPlainString(),
                        "taxAmount", taxAmount.toPlainString(),
                        "orderNo", invoice.getOrderNo() == null ? "" : invoice.getOrderNo(),
                        "statementId", invoice.getStatementId() == null ? "" : invoice.getStatementId()));

        log.info("Invoice issued: invoiceNo={} ws={} type={} amount={}",
                invoice.getInvoiceNo(), workspaceId, invoiceType, amount.toPlainString());
        return toView(invoice);
    }

    // ══════════════════════════════════════════════════════════════════
    // 写：红冲
    // ══════════════════════════════════════════════════════════════════

    /**
     * 按订单红冲发票（退款场景调用）。
     * <p>
     * <b>幂等</b>：原发票只能被红冲一次（条件更新 {@code status = 'issued'}）。
     * </p>
     *
     * @param orderId    订单 id
     * @param refundNo   关联退款单号（留痕）
     * @param refundAmount 退款金额（用于校验红冲金额与原票一致）
     * @return true = 本次执行了红冲；false = 该订单没有可红冲的发票 / 已红冲
     */
    @Transactional
    public boolean redFlushByOrder(UUID orderId, String refundNo, BigDecimal refundAmount) {
        if (orderId == null) {
            return false;
        }
        Invoice origin = invoiceMapper.selectActiveByOrderId(orderId);
        if (origin == null) {
            log.info("No invoice to red-flush for order {} (refund={})", orderId, refundNo);
            return false;
        }
        return doRedFlush(origin, refundNo, refundAmount);
    }

    /**
     * 红冲核心逻辑（原发票 + 新增红冲记录）。
     * <p>
     * 由 {@link #redFlushByOrder}（退款场景）与 {@link #redFlush}（管理端手动）共用，
     * 保证两条路径的语义完全一致。
     * </p>
     *
     * @param origin       原发票（必须处于 issued 状态）
     * @param refundNo     关联退款单号（留痕）
     * @param refundAmount 退款金额（用于校验红冲金额与原票一致；null = 不校验）
     * @return true = 本次执行了红冲
     */
    private boolean doRedFlush(Invoice origin, String refundNo, BigDecimal refundAmount) {
        if (!STATUS_ISSUED.equals(origin.getStatus())) {
            log.info("Invoice already red-flushed, skip: invoiceNo={} refund={}",
                    origin.getInvoiceNo(), refundNo);
            return false;
        }
        if (refundAmount != null && origin.getAmount() != null
                && refundAmount.compareTo(origin.getAmount()) != 0) {
            // 部分退款：金额不一致时不自动红冲，交由财务人工处理（避免冲减金额错误）
            log.warn("Refund amount differs from invoice amount, manual red-flush required: "
                            + "invoiceNo={} invoiceAmount={} refundAmount={} refund={}",
                    origin.getInvoiceNo(), origin.getAmount().toPlainString(),
                    refundAmount.toPlainString(), refundNo);
            auditWriter.recordFailure("INVOICE_RED_FLUSH_SKIPPED", "INVOICE",
                    origin.getId().toString(), null, "refund_amount_mismatch",
                    Map.of("invoiceNo", origin.getInvoiceNo(),
                            "refundNo", refundNo == null ? "" : refundNo));
            return false;
        }

        OffsetDateTime now = OffsetDateTime.now();
        Invoice red = new Invoice();
        red.setId(UUID.randomUUID());
        red.setInvoiceNo(generateInvoiceNo());
        red.setWorkspaceId(origin.getWorkspaceId());
        red.setInvoiceType(origin.getInvoiceType());
        red.setTitle(origin.getTitle());
        red.setTaxNo(origin.getTaxNo());
        red.setAmount(origin.getAmount());
        red.setCurrency(origin.getCurrency());
        red.setTaxRate(origin.getTaxRate());
        red.setTaxAmount(origin.getTaxAmount());
        red.setStatus(STATUS_RED);
        red.setOrderId(origin.getOrderId());
        red.setOrderNo(origin.getOrderNo());
        red.setStatementId(origin.getStatementId());
        red.setRedFlushInvoiceId(origin.getId());
        red.setIssuedBy(origin.getIssuedBy());
        red.setIssuedAt(now);
        red.setIdempotencyKey(origin.getIdempotencyKey() == null
                ? null : "red:" + origin.getIdempotencyKey());
        red.setCreatedAt(now);
        red.setUpdatedAt(now);

        invoiceMapper.insert(red);

        // 原发票标记为已红冲（条件更新保证只红冲一次）
        int updated = invoiceMapper.compareAndSetRedFlushed(origin.getId(), red.getId());
        if (updated == 0) {
            // 并发：另一线程已红冲，撤销本次新增记录（保持幂等）
            log.info("Invoice red-flush conflict (already handled): invoiceNo={}", origin.getInvoiceNo());
            return false;
        }
        // 注意：compareAndSetRedFlushed 已经把 status 与 red_flush_invoice_id 一起落库了，
        // 这里<b>不能再</b>用 origin 的旧快照（status 仍是 'issued'）做 updateById，
        // 否则会把数据库里的 'red_flushed' 覆盖回 'issued'（原票看起来没被红冲）。
        // 仅同步内存对象，供调用方读取。
        origin.setStatus(STATUS_RED_FLUSHED);
        origin.setRedFlushInvoiceId(red.getId());
        origin.setUpdatedAt(now);

        // 红冲 PDF
        try {
            byte[] pdf = pdfGenerator.generate(red, Map.of());
            String objectKey = storageService.store(red.getWorkspaceId(), red.getInvoiceNo(), pdf);
            red.setPdfPath(objectKey);
            red.setUpdatedAt(OffsetDateTime.now());
            invoiceMapper.updateById(red);
        } catch (RuntimeException ex) {
            log.error("Failed to generate red-flush invoice pdf {}: {}",
                    red.getInvoiceNo(), ex.getMessage(), ex);
        }

        auditWriter.record("INVOICE_RED_FLUSHED", "INVOICE", origin.getId().toString(),
                Map.of("workspaceId", origin.getWorkspaceId(),
                        "invoiceNo", origin.getInvoiceNo(),
                        "redInvoiceNo", red.getInvoiceNo(),
                        "orderNo", origin.getOrderNo() == null ? "" : origin.getOrderNo(),
                        "refundNo", refundNo == null ? "" : refundNo));

        log.info("Invoice red-flushed: invoiceNo={} redInvoiceNo={} refund={}",
                origin.getInvoiceNo(), red.getInvoiceNo(), refundNo);
        return true;
    }

    /**
     * 手动红冲（管理端）。
     */
    @Transactional
    public InvoiceDtos.InvoiceView redFlush(UUID invoiceId, InvoiceDtos.RedFlushRequest request,
                                            Long operatorId) {
        Invoice origin = requireInvoiceById(invoiceId);
        if (!STATUS_ISSUED.equals(origin.getStatus())) {
            throw new BusinessException(CODE_CONFLICT, "invoice_not_issuable");
        }
        String refundNo = request == null ? null : request.getRefundNo();
        // 手动红冲金额恒等于原票金额（人工兜底部分退款场景），故不传 refundAmount 做一致性校验
        boolean done = doRedFlush(origin, refundNo, origin.getAmount());
        if (!done) {
            throw new BusinessException(CODE_CONFLICT, "invoice_red_flush_conflict");
        }
        Invoice updated = invoiceMapper.selectById(invoiceId);
        return toView(updated);
    }

    // ══════════════════════════════════════════════════════════════════
    // 下载
    // ══════════════════════════════════════════════════════════════════

    /**
     * 生成下载票据（签名 URL，5 分钟过期）。
     *
     * @param invoice   发票
     * @param signer    下载链接签名器
     * @param basePath  本地存储模式的下载基路径（含 workspaceId 与 invoiceId）
     */
    public InvoiceDtos.DownloadTicket createDownloadTicket(Invoice invoice, InvoiceDownloadSigner signer,
                                                           String basePath) {
        InvoiceDtos.DownloadTicket ticket = new InvoiceDtos.DownloadTicket();
        ticket.setInvoiceId(invoice.getId().toString());
        ticket.setInvoiceNo(invoice.getInvoiceNo());
        long expiresAt = System.currentTimeMillis()
                + InvoiceStorageService.DOWNLOAD_URL_EXPIRY_SECONDS * 1000L;
        ticket.setExpiresAt(expiresAt);
        ticket.setExpiresInSeconds(InvoiceStorageService.DOWNLOAD_URL_EXPIRY_SECONDS);

        String presigned = invoice.getPdfPath() == null ? null
                : storageService.presignedDownloadUrl(invoice.getPdfPath());
        if (presigned != null) {
            ticket.setUrl(presigned);
        } else {
            // 本地存储：由签名下载接口承载（HMAC 签名 + 过期时间在 URL 上）
            ticket.setUrl(basePath
                    + "?expiresAt=" + expiresAt
                    + "&signature=" + signer.sign(invoice.getInvoiceNo(), expiresAt));
        }
        return ticket;
    }

    /**
     * 读取发票 PDF 字节（供下载接口使用）。
     */
    public byte[] loadPdf(Invoice invoice) {
        if (invoice == null || invoice.getPdfPath() == null || invoice.getPdfPath().isBlank()) {
            throw new BusinessException(CODE_NOT_FOUND, "invoice_pdf_not_found");
        }
        if (!storageService.isValidObjectKey(invoice.getPdfPath())) {
            throw new BusinessException(CODE_BAD_REQUEST, "invalid_invoice_pdf_path");
        }
        return storageService.load(invoice.getPdfPath());
    }

    // ══════════════════════════════════════════════════════════════════
    // private
    // ══════════════════════════════════════════════════════════════════

    /**
     * 校验当前用户是 workspace 成员；不是成员一律 404。
     */
    public WorkspaceRole requireMember(String workspaceId, Long userId) {
        if (userId == null) {
            throw new BusinessException(CODE_FORBIDDEN, "unauthorized");
        }
        WorkspaceRole role = workspaceService.findRole(userId, workspaceId);
        if (role == null) {
            throw new BusinessException(CODE_NOT_FOUND, "invoice_not_found");
        }
        return role;
    }

    /**
     * 发票号生成：{@code INV} + 年月 + 序列（6 位）+ 随机尾巴，全局唯一。
     */
    private String generateInvoiceNo() {
        String prefix = "INV" + YearMonth.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        long count = invoiceMapper.countByInvoiceNoPrefix(prefix);
        String no = prefix + String.format("%06d", count + 1);
        // 并发下序列可能重复 → 追加短随机尾巴兜底（唯一索引最终防线）
        String tail = Integer.toString(java.util.concurrent.ThreadLocalRandom.current()
                .nextInt(1000, 9999), 36).toUpperCase();
        return truncate(no + tail, 32);
    }

    /**
     * 发票视图转换。
     */
    public InvoiceDtos.InvoiceView toView(Invoice invoice) {
        if (invoice == null) {
            return null;
        }
        InvoiceDtos.InvoiceView v = new InvoiceDtos.InvoiceView();
        v.setId(invoice.getId() == null ? null : invoice.getId().toString());
        v.setInvoiceNo(invoice.getInvoiceNo());
        v.setWorkspaceId(invoice.getWorkspaceId());
        v.setInvoiceType(invoice.getInvoiceType());
        v.setTitle(invoice.getTitle());
        v.setTaxNo(invoice.getTaxNo());
        v.setAmount(invoice.getAmount());
        v.setCurrency(invoice.getCurrency());
        v.setTaxRate(invoice.getTaxRate());
        v.setTaxAmount(invoice.getTaxAmount());
        v.setStatus(invoice.getStatus());
        v.setOrderId(invoice.getOrderId() == null ? null : invoice.getOrderId().toString());
        v.setOrderNo(invoice.getOrderNo());
        v.setStatementId(invoice.getStatementId());
        v.setRedFlushInvoiceId(invoice.getRedFlushInvoiceId() == null
                ? null : invoice.getRedFlushInvoiceId().toString());
        v.setIssuedBy(invoice.getIssuedBy());
        v.setIssuedAt(invoice.getIssuedAt());
        v.setCreatedAt(invoice.getCreatedAt());
        return v;
    }

    private static String normalizeType(String raw) {
        String s = raw == null || raw.isBlank() ? TYPE_PERSONAL : raw.trim().toLowerCase();
        return switch (s) {
            case TYPE_PERSONAL, TYPE_COMPANY -> s;
            default -> throw new BusinessException(CODE_BAD_REQUEST, "invalid_invoice_type");
        };
    }

    private static String normalizeStatus(String raw) {
        String s = raw == null ? "" : raw.trim().toLowerCase();
        return switch (s) {
            case STATUS_ISSUED, STATUS_RED, STATUS_RED_FLUSHED -> s;
            default -> throw new BusinessException(CODE_BAD_REQUEST, "invalid_invoice_status");
        };
    }

    private static String normalizeKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.length() > 128 ? trimmed.substring(0, 128) : trimmed;
    }

    private static String truncate(String raw, int max) {
        if (raw == null) {
            return null;
        }
        return raw.length() > max ? raw.substring(0, max) : raw;
    }
}
