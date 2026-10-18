# 阶段2 验收清单（基于 `COMMERCIAL_SAAS_READINESS_PLAN.md` §9）

> 本清单**严格依据** `COMMERCIAL_SAAS_READINESS_PLAN.md` 第 9 章"阶段 2：运营创作闭环"。
> 文档原始范围 4 项，本清单逐项拆解为可验收的功能点 + 接口契约 + 数据模型 + 验收用例。

## 阶段2 文档原文（§9 摘录）

> 目标：运营用户可以从素材走到可发布内容。
>
> 1. 内容资产、媒体中心、草稿版本、模板库、选题池和导出
> 2. 平台/品类参数化分析、标题正文评论生成、多版本比较
> 3. 合规规则、审核流、审批意见和发布前预览
> 4. 浏览器辅助和手动导入作为无 API 平台的保底路径
>
> 出口条件：一个运营人员不借助开发人员，可以完成创建、生成、审核、导出和版本恢复

## 阶段2 不在范围

- 真实平台指标回写、预测对比、复盘报告 → 阶段3
- 计费后台、订阅、发票 → 阶段4
- 跨平台内容适配（小红书以外的真实适配）→ 阶段3+

---

## 子能力 1：内容资产 / 媒体中心 / 草稿版本 / 模板库 / 选题池 / 导出

### 1.1 内容资产（阶段1 已落地，需扩展）

**功能**：
- 内容资产统一模型：标题、正文、标签、平台、品类、作者、素材、版本、状态、来源
- 草稿版本、自动保存、版本比较、恢复历史版本
- 标签、文件夹、搜索、筛选、批量归档和删除

**待验收**：
- 草稿自动保存：编辑时每 30s 落一次"自动版本"（不占用版本号）
- 人工"保存为版本"：用户主动点击生成版本号 +1 的快照
- 版本比较：diff 两个版本的标题/正文（line-based）
- 版本回滚：恢复到任一历史版本
- 标签：每个资产支持多标签，标签可搜索/筛选
- 批量归档 / 删除

**接口契约（新增/补充）**：
- POST /api/v1/assets/{id}/autosave body: {title, body} - 自动保存，不创建新版本
- POST /api/v1/assets/{id}/snapshot body: {editReason} - 创建快照版本
- GET /api/v1/assets/{id}/diff?v1=2&v2=5 - 版本 diff
- POST /api/v1/assets/{id}/rollback/{versionNo} - 回滚
- PATCH /api/v1/assets/{id}/tags body: {tags: ["留学", "求职"]}
- POST /api/v1/assets/batch-archive body: {assetIds: [...]}

### 1.2 媒体中心

**功能**：
- 图片、视频、音频、字幕、截图、链接、CSV、Excel 导入
- 媒体预览、压缩、转码、缩略图、病毒扫描、元数据提取
- 任何下载链接都有权限校验和过期时间

**待验收**：
- 图片上传：支持 jpg/png/webp，限 10MB
- 视频上传：支持 mp4，限 100MB
- 缩略图自动生成：图片返回 200x200 缩略图 URL
- 病毒扫描：上传时通过 ClamAV 扫描
- 下载链接：5 分钟有效期内可访问，过期 403
- 媒体分组：按 assetId 关联

**接口契约**：
- POST /api/v1/media/upload multipart: file - 返回 {mediaId, thumbnailUrl, downloadUrl}
- GET /api/v1/media/{id}/download - 302 -> 签名 URL（5 min TTL）
- GET /api/v1/media/{id}/thumbnail?size=200 - 缩略图

### 1.3 草稿版本

**待验收**：
- 版本历史表记录：versionNo / title / body / editorId / editReason / createdAt
- 版本与 assetId 关联，可分页查询

### 1.4 模板库（阶段1 已落地，需补充）

**待验收**：
- 模板生成结果可以保存为内容草稿
- 模板关闭或不可用时，使用规则模式（不假装检索）
- 模板使用统计：30 天引用次数、采用率

### 1.5 选题池（**阶段2 新建**）

**功能**：
- 选题池：选题名称、来源平台、品类、关键词、目标人群、热度、时效、负责人、状态
- 选题去重、趋势变化、相关内容样本、风险词和可做方向
- 选题 -> 资产一键创建

**待验收**：
- app.topic_pool 表：id / workspace_id / title / source / heat_score / status / created_by / created_at
- 状态机：active -> used -> archived
- 手动录入 API
- 从 AI 搜索结果批量导入 API
- 选题 -> 资产一键创建（POST /v1/assets with topicId）
- 定时任务：每 4h 调用 GetHotTopicsTool 写入 app.hot_topic

**接口契约**：
- GET /api/v1/topics?workspaceId=&status=active - 列表
- POST /api/v1/topics body: {title, source, heatScore, tags, ...}
- PATCH /api/v1/topics/{id} body: {status, scheduledDate}
- POST /api/v1/topics/{id}/to-asset - 创建资产并关联

### 1.6 导出（阶段1 已落地，需验证）

**待验收**：
- MD 导出：含 YAML frontmatter + 正文
- DOCX 导出：含样式、标题、表格
- ZIP 导出：MD + 图片包
- CSV 导出：分析任务列表
- 签名 URL 5 分钟过期
- 限流：50 次/天/workspace

---
## 子能力 2：平台/品类参数化分析 / 标题正文评论生成 / 多版本比较

### 2.1 平台/品类参数化分析

**功能**：
- 内容类型识别、结构拆解、标题分析、情绪和收藏价值分析、转化路径分析
- 平台和品类可配置的评分维度与权重
- 每次分析记录模型、Prompt 版本、知识库版本、输入摘要、耗时

**待验收**：
- 分析任务支持 platform=XIAOHONGSHU / DOUYIN / ...
- 分析任务支持 category=STUDY_ABROAD / CAREER / ...
- 评分维度按 platform+category 切换
- 报告字段稳定 Schema（版本化）

### 2.2 标题 / 正文 / 评论生成

**功能**：
- 标题批量生成、分类、评分、筛选和 A/B 版本
- 正文生成、段落结构、CTA、标签、评论、私信回复和平台适配改写
- 生成参数：平台、品类、人设、目标、风格、长度、关键词、禁用词

**待验收**：
- 标题生成：批量 5-10 个，含评分
- 正文生成：分平台/分品类差异化
- **评论生成**（**阶段2 新建**）：
  - 4 种场景：首问 / 追问 / 私信话术 / 转化话术
  - 入参：{commentType, platform, persona, draftContext, maxLength}
  - 出参：{comment, requiresReview, complianceRisk, riskLevel}
  - 合规扫描：HIGH/BLOCKED 拒绝，MEDIUM 标记走审核
- 平台适配改写：同一内容 -> 抖音/小红书不同版本

### 2.3 多版本比较

**功能**：
- 生成结果可编辑、保存为草稿、复制、下载、提交审核和继续优化
- 生成结果和输入报告建立关联

**待验收**：
- 同一资产可保存 N 个候选版本
- 版本间 diff 可视化
- "采用"动作：把候选版本定稿为 currentVersion

---

## 子能力 3：合规规则 / 审核流 / 审批意见 / 发布前预览

### 3.1 合规规则（阶段1 已落地）

**功能**：
- 敏感词、绝对化表述、虚假承诺、医疗金融风险、广告法风险、平台规则风险
- 品类级禁用词和品牌级禁用词
- 风险分级：阻断、需要审核、提示
- 规则库可热更新

**待验收**：
- 规则集可热更新（DB-driven，无需重启）
- 规则版本化：每次变更产生新版本号
- 品牌级禁用词：每个 workspace 可独立配置

### 3.2 审核流（阶段1 已落地，需扩展）

**功能**：
- 审核流：草稿、待审核、退回、已通过、已排期、已发布、已归档
- 审核意见、审批人、时间、版本和审计记录

**待验收**：
- 高风险内容自动 require review
- Reviewer 队列 + Editor 提交记录
- 审批意见附在版本上
- 审计：每次审核写 audit_event

### 3.3 发布前预览

**功能**：
- 发布前预览：平台长度、标签、封面比例、视频规格、链接和 CTA

**待验收**：
- 预览按平台规格显示标题/正文/标签长度警告
- 封面比例：1:1 / 3:4 / 16:9 不同平台不同推荐
- 视频规格：分辨率、时长、码率

**接口契约**：
- GET /api/v1/assets/{id}/preview?platform=XIAOHONGSHU - 平台预览（含校验）

---

## 子能力 4：浏览器辅助 / 手动导入

### 4.1 浏览器辅助

**功能**：
- 无 API 平台（如小红书）的自动化辅助
- 用户在平台页面确认，系统负责填充和回收
- 浏览器辅助和手动导入作为无 API 平台的保底路径

**待验收**：
- app.browser_assist_session 表：会话生命周期
- 浏览器辅助打开小红书创作页 -> 自动填标题/正文/标签 -> **不点发布**，截图存档等用户确认
- 浏览器辅助操作写入 audit_event
- Cookie 加密存储（AES-GCM），24h 过期
- 政策提示：UI 显著位置提示"用户自负风险"

**接口契约**：
- POST /api/v1/browser-assist/sessions body: {platform, accountLabel}
- GET /api/v1/browser-assist/sessions/{id} - 状态
- DELETE /api/v1/browser-assist/sessions/{id} - 销毁
- POST /api/v1/browser-assist/fill body: {sessionId, assetId} - 填充创作页

### 4.2 手动导入

**功能**：
- 没有可靠连接器的平台：CSV、Excel、截图、链接、文本、媒体上传

**待验收**：
- CSV 导入：每行一条资产 / 选题 / 竞品笔记
- 截图导入：OCR 识别后入库（**可降级为人工录入**）
- 链接导入：粘贴 URL -> 抓取标题/正文/缩略图（**可降级为人工录入**）

---

## 阶段2 整体验收（出口条件）

> 一个运营人员不借助开发人员，可以完成创建、生成、审核、导出和版本恢复

### 必走的端到端场景

1. **注册登录 -> 创建工作区 -> 完成首次设置**
2. **选题**：从选题池选/录入选题 -> 创建内容资产草稿
3. **生成**：调用标题/正文生成 -> 多版本保存 -> 选一个采用
4. **审核**：提交审核 -> Reviewer 批准/驳回（高风险自动 require review）
5. **预览**：发布前预览 -> 平台规格检查
6. **发布**：小红书走浏览器辅助（确认后由运营点发布）；其他平台走导出 -> 手动复制
7. **版本管理**：任意时刻回滚/对比历史版本
8. **导出**：MD/DOCX/ZIP/CSV -> 任意格式下载

### 性能 / 可靠性

- 分析任务 P95 < 45 秒
- 标题/正文生成 P95 < 30 秒
- 移动端可查看/编辑/审核/复制/导出
- 异步任务刷新/断网重连后可恢复

---

## 阶段2 不写测试（用户决策）

- 不写 JUnit / Mockito / SpringBootTest
- 验收 = mvn compile 通过 + 端到端场景跑通
- 集成 / 回归由运营同事在使用中验证

## 阶段2 派发策略（用户决策）

- 4 个子能力 -> 4 个 subagent 并行
- 每个 subagent 完成一个子能力后回报
- 由统筹者按"summary_first"格式 review 每个 commit
- 不裁剪范围，严格按文档 §9 阶段2 4 项 + 出口条件
