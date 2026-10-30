# XhsAgent 开发操作日志

> 规则见 `CLAUDE.md` 的「操作日志规则（强制）」。每条一行，时间为本地时间 `HH:mm:ss`，按时间升序追加。
> 【格式要求 2026-10-28 新增】自下一条记录起，每条必须带日期前缀，格式为 `YYYY-MM-DD HH:mm:ss | 类型 | 动作 | 涉及路径 / 标识 | 结果 / 备注`（与历史 `HH:mm:ss` 行兼容，旧行不再回填）。

## 2026-10-23

22:34:10 | 计划 | 盘点阶段0-2既有实现并生成阶段3验收基线 | STAGE3_ACCEPTANCE.md | 790 行；5 个子能力、13 子任务、约 50 个接口契约、出口条件 E1-E3、风险矩阵 R1-R10
22:34:10 | 计划 | 确认未冻结决策清单 | STAGE3_ACCEPTANCE.md D1-D5 | D1/D2 为阻塞-P0，D3/D4/D5 可先行
23:20:05 | 决策 | 用户拍板阶段3决策 | D1=C 仅人工导入 / D2=B 小红书+抖音 / D3=B OWNER+ADMIN / D4-D5 按默认 | 决策冻结，可开工
23:20:40 | 派发 | 派发基础层 agent（A0） | workspace_id 归一 + 连接器抽象 | 已启动；其余 4 任务待其产出
23:38:30 | 后端 | 修复 workspace_id 类型归一（UUID/Long → String） | ContentAsset / TopicPool / BrowserAssistSession / CsvImportLog 等 17 个 java 文件 | 已改完，待编译
23:38:30 | 数据库 | 新建工作区 ID 归一迁移 | V49__stage3_workspace_id_normalization.sql | browser_assist_session / csv_import_log / topic_pool 三表 UUID/BIGINT → VARCHAR(32)，加 NOT VALID FK
23:51:00 | 阻塞 | 基础层 agent 停滞 18 分钟无写入 | A0 | 4 个编译错误挂在 AssetTagServiceImpl / HotTopicServiceImpl（均为本次归一引入）
23:52:00 | 验证 | 编译检查暴露 4 个错误 | AssetTagServiceImpl:97,130 / HotTopicServiceImpl:157,164 | 均因 workspaceId 改为 String 后 getLeastSignificantBits 与 Long→String 不再成立
00:03:00 | 派发 | 追加并行派发知识库管理面 agent | S3-11 kb_document 实体/Mapper/管理 API | 与 A0 文件不重叠，迁移分配 V54
00:06:00 | 派发 | 追加并行派发平台账号连接 agent | S3-2 platform_account + 状态机 | 迁移分配 V51
00:06:30 | 派发 | 追加并行派发指标快照 agent | S3-5 metric_snapshot + 修订链 + CSV | 迁移分配 V52
00:09:00 | 派发 | 追加并行派发热点产品化 agent | S3-9 hot_topic 来源可信度 + 缓存 | 迁移分配 V53
00:09:20 | 派发 | 中断并恢复基础层 agent，指定按序完成 | A0 | 修正 4 个编译错误 → 建 V50 → 建 connector 包 → 建 ConnectorController
00:10:24 | 后端 | 修复 AssetTagService 接口签名 | AssetTagService.java / AssetTagServiceImpl.java | workspaceId 由 Long 改 String，归属校验由 hashCode 近似改为精确字符串比较
00:10:50 | 后端 | 修复热点服务接口签名 | HotTopicService.java / HotTopicServiceImpl.java | promoteToTopicPool 的 workspaceId 由 Long 改 String
00:11:47 | 数据库 | 新建平台账号迁移 | V51__stage3_platform_account.sql | 写入中
00:12:51 | 数据库 | 新建连接器核心迁移 | V50__stage3_connector_core.sql | 写入中
00:12:54 | 决策 | 新增「操作日志规则」并回填当日记录 | CLAUDE.md / DEVLOG.md | 规则强制化，后续每条实质操作实时追加
00:16:39 | 计划 | 新增操作日志规则并创建日志文件与并发安全写入脚本 | CLAUDE.md / DEVLOG.md / scripts/devlog-append.ps1 | 规则强制化；脚本已通过 6 路并发压测不丢行
00:16:39 | 验证 | 清理日志压测临时行 | DEVLOG.md | 已删除 7 行测试记录，保留正式记录
13:10:45 | 验证 | 阶段3完成度核查（编译 + 覆盖率盘点） | Xhs-Backend 全量 | BUILD SUCCESS；批次A（S3-1/2/5/9/11）已落地，批次B-F未开工
13:10:46 | 阻塞 | 阶段3出口条件 E1-E3 无法验证（数据库未启动，Docker 未运行） | V49-V54 迁移未实测执行 | 编译通过但迁移无数据库验证；前端 S3-12 零改动
13:14:31 | 验证 | 启动本地数据库（Docker Compose）并跑通 Flyway 迁移 | Xhs-Backend/docker-compose.dev.yml | postgres:pgvector/pgvector:pg16 + redis:7-alpine 均为本地已有镜像
13:18:46 | 修复 | 修复既有迁移文件阻塞（V38 编码 + V48 SQL 引号） | V38__topic_pool.sql / V48__media_asset.sql | V38 GBK→UTF-8；V48 DEFAULT ''pending''→'pending'（2处）
13:18:46 | 阻塞 | 测试编译被 workspace_id 变更打破 | quota 包 38 处 UUID→String 断言 | 主代码编译通过但 testCompile 失败 38 个错误，需修复
13:43:48 | 验证 | 应用启动成功 + 阶段3接口冒烟全绿 | Tomcat 8125 / /actuator/health=UP | 6/6 接口200；连接器授权门禁运行时生效（2注册2拒绝）
13:43:49 | 修复 | 修复 5 处阻塞性缺陷 | UuidTypeHandler/MybatisPlusConfig/V38/V48/MinioHealthIndicator/VersionDiffController | 另修 HotTopicMapper PostgreSQL 参数类型推断失败 + 3个控制器/api前缀
13:43:49 | 修复 | 全局异常处理器补日志 | GlobalExceptionHandler.handleUnknown | 原逻辑吞掉所有未预期异常堆栈，导致 500 无法定位根因
14:07:40 | 修复 | 修复 SSE 流式对话丢失 WorkspaceContext（ThreadLocal 跨线程未透传） | ChatAppService.sendMessageStream | 捕获并透传 workspaceId/userId 到 chatStreamExecutor 线程，finally 清理；修复后工具 list_recent_analyses 可正常执行
14:07:40 | 修复 | 补齐缺失的数据库迁移 app.analysis_task.template_id | V55__fix_analysis_task_template_id.sql | 根因：commit c951885 新增实体字段但未写迁移；已补 VARCHAR(64) 列并在本地库实测通过
21:17:29 | 计划 | 盘点阶段3未完成子任务并派发 3 个并行后端 agent | STAGE3_ACCEPTANCE.md + S3-3/S3-4 + S3-6/S3-7/S3-8 + S3-9/S3-10 | 批次A(S3-1/2/5/11)已完成；本次派发 S3-3/S3-4、S3-6/S3-7/S3-8、S3-9/S3-10；V49-V55 迁移本地已验证通过
21:32:17 | 前端 | 新建阶段3 API 模块层 + TS 类型 + 三语 i18n | 8 个 api/*.ts + types/stage3.ts + locales/{types,zh-CN,zh-TW,en}.ts | vue-tsc 零错误；新增 7 个 i18n 命名空间 + 7 个 nav 键
21:59:25 | 修复 | 修复 3 个 UTF-16LE 编码损坏的 Java 文件 | service/ComparisonService.java + ForecastService.java + TemplateEffectStatsService.java | 转 UTF-8 后 mvn compile BUILD SUCCESS
22:13:21 | 修复 | 修复 V56 迁移 SQL 语法 + 5 处编译错误 | V56__stage3_publish_pipeline.sql + HotTopicController/TemplateEffectStatsService/PublishTaskHandler/HotTopicService/CompetitorAnalyzeRequest | mvn compile BUILD SUCCESS
22:13:22 | 数据库 | V56/V57/V58 迁移成功应用 | app.publish_schedule/publish_task/publish_receipt + metric_forecast/metric_comparison/retrospective_report/retrospective_feedback + competitor_account/competitor_note | Flyway 56->58 全部成功
22:32:44 | 前端 | 新建阶段3 7 个页面 + 路由 + 侧边栏导航 | views/publish/PublishCalendarView.vue + metrics/MetricDashboardView.vue + retrospective/RetrospectiveListView.vue + hotTopic/HotTopicListView.vue + competitor/CompetitorLibraryView.vue + kb/KbDocumentAdminView.vue + connector/ConnectorDiagnosticView.vue | vue-tsc -b EXIT=0
22:32:45 | 前端 | 补齐三语 i18n 缺口并重生成 types.ts | locales/zh-CN.ts + en.ts + zh-TW.ts + types.ts | 新增 retentionPolicy/tone/goal/common 等 30 键，types.ts 由 zh-CN 自动生成
21:02:31 | 后端 | 补齐 Forecast/Comparison/Retrospective/Competitor 四组 Service+Controller | service/ForecastService.java + ComparisonService.java + RetrospectiveService.java + CompetitorService.java + controller/v1/{Forecast,Comparison,Retrospective,Competitor}Controller.java | S3-6/S3-7/S3-8/S3-10 补齐，mvn compile EXIT=0
21:05:12 | 修复 | 修复 jsonb 列缺 TypeHandler 导致资产/审计写入 500 | domain/entity/ContentAsset.java + AuditEvent.java | tags/metadata 挂 JsonbStringTypeHandler/JsonbMapTypeHandler + autoResultMap
21:08:44 | 修复 | 修复 GlobalExceptionHandler 只映射 40301/40401 | common/exception/GlobalExceptionHandler.java | 403xx→403、404xx→404，CONNECTOR_NOT_AUTHORIZED 等业务码 HTTP 状态归位
21:11:03 | 修复 | 修复 ReviewTask/ReviewTaskMapper/ReviewService 的 workspace_id 类型 | domain/entity/ReviewTask.java + domain/mapper/ReviewTaskMapper.java + service/ReviewService.java + dto/review/ReviewTaskResponse.java | V49 归一化为 VARCHAR(32)，UUID→String，审核链路解除 invalid_workspace_id
21:14:26 | 修复 | 修复 36 处 `? IS NULL OR` 在 PostgreSQL 无法推断参数类型 | 9 个 Mapper（PublishSchedule/RetrospectiveReport/MetricForecast/MetricComparison/ContentAsset/ConnectorHealth/Template/TopicPool） | 按 information_schema 真实列类型加 CAST，全部 500 消除
21:17:55 | 修复 | 修复 Redis Stream 消费者组用 workerId 当组名导致任务永不消费 | infra/taskqueue/TaskQueueConsumerRunner.java | Consumer.from(group, consumer) + 异常 cause 链识别 BUSYGROUP/NOGROUP
21:20:31 | 修复 | 修复 seed.json 未转义引号导致模板种子解析失败 | src/main/resources/data/templates/seed.json | 解析器驱动修复 4 处，8 个官方模板播种成功
21:22:18 | 后端 | 新建 jsonb 数组列 TypeHandler 并批量补全实体注解 | config/mybatis/JsonbListTypeHandler.java + domain/entity/{ReviewTask,TopicPool,ApiKey,Template,TemplateVersion,...}.java | ReviewTask.riskTags 等 List<String> 列不再抛 Type handler was null
21:24:02 | 修复 | 修复 ReviewController 缺少 /v1 前缀 | controller/v1/ReviewController.java | 补 @RequestMapping("/v1")，审核接口 404 消除
21:26:47 | 修复 | 修复 RetrospectiveService 调用 AuditWriter.recordFailure 参数错误 | service/RetrospectiveService.java | 补 errorCode/errorMessage/metadata 三参，编译通过
21:30:15 | 验证 | 阶段3 E2E 首轮全绿 | scripts/stage3-e2e.ps1 | 48 通过 / 0 失败，E1/E2/E3 出口条件全部满足
21:33:52 | 验证 | 阶段3 E2E 二次连跑确认可重复 | scripts/stage3-e2e.ps1 | 连续两次 48/0，脚本幂等性修复生效（唯一后缀 + 登录优先）

## 2026-10-27 小结

### 阶段3 出口条件验证结果

- **E1**（小红书浏览器辅助发布闭环）：PASS —— 连接器门禁 403 / 幂等键重复提交返回同一 taskId / 排期冲突 409 / 回执回填后资产推进到 published
- **E2**（抖音手动发布回收 + 指标快照修订链）：PASS —— MANUAL_IMPORT 来源标记 / LOW 可信度 / 快照修订链 / 趋势查询全部通过
- **E3**（复盘报告 ≥3 条带证据建议）：PASS —— status=COMPLETED，suggestions=3，每条 evidenceRefs 非空且指向真实 metric_snapshot / metric_comparison
- **端到端脚本**：`scripts/stage3-e2e.ps1` 连续两次 48 通过 / 0 失败

### 本轮修复的系统性问题

1. **jsonb 列缺 TypeHandler**（5 处）—— ContentAsset.tags / AuditEvent.metadata / Template.variables / ReviewTask.riskTags / ApiKey.scopes，统一挂 handler + autoResultMap
2. **`? IS NULL OR` SQL 模式**（36 处）—— PostgreSQL 无法推断无类型绑定参数，按真实列类型显式 CAST
3. **workspace_id 类型不统一** —— V49 归一化为 VARCHAR(32) 后，ReviewTask / ReviewTaskMapper / ReviewService / ReviewTaskResponse 仍是 UUID
4. **HTTP 状态码映射不完整** —— GlobalExceptionHandler 只特判 40301/40401，其余 403xx/404xx 全部落成 400
5. **Redis Stream 消费者组参数错位** —— `Consumer.from(consumer, consumer)` 把 workerId 当成组名，任务永远 pending
6. **E2E 脚本自身缺陷** —— `$pass` 变量名与计数器 `$script:pass` 冲突、字段名与 DTO 不符、硬编码账号撞唯一约束

### 遗留问题

- 真实平台连接仍需人工签署授权（`XHS_AUTHORIZED_CONNECTOR` / `XHS_OFFICIAL_API` 保持 disabled）
- 本轮仅跑后端 E2E，前端 7 个阶段3 页面只做了 `vue-tsc` 类型检查，未做浏览器端联调
- `scripts/_*.py` 为本轮一次性修复脚本，验证通过后建议清理
22:02:41 | 前端 | 接通阶段3 指标采集按钮并清理未用 import | Xhs-Frontend/src/views/metrics/MetricDashboardView.vue + publish/PublishCalendarView.vue | handleCollect 接入采集弹窗 + collecting 状态；vue-tsc -b EXIT=0
22:04:16 | 验证 | clean package 后重跑阶段3 E2E 复核 | Xhs-Backend/target/XhsAgent-yunying-0.0.1-SNAPSHOT.jar + scripts/stage3-e2e.ps1 | mvn clean package EXIT=0；E2E 48 通过 / 0 失败
22:05:02 | 计划 | 清理一次性修复脚本，保留可复用审计工具 | scripts/_fix_*.py（28 个已删）+ scripts/_audit_*.py（保留） | 避免重复执行造成二次污染；审计脚本可用于回归
22:22:14 | 验证 | 补齐 E1 浏览器辅助路径 E2E 脚本 | scripts/stage3-e2e-browser.ps1 | 会话 created→filled→closed 全链路 + assetId 绑定 + policyNotice 合规提示，16 通过 / 0 失败
22:24:03 | 验证 | 核对 8 条必走端到端场景覆盖度 | scripts/_map_scenario_coverage.py + STAGE3_ACCEPTANCE.md | 8/8 场景均有断言覆盖，无缺口
22:25:41 | 计划 | 确认 D1/D2 决策默认值已落地 | app.platform_connector_authorization（9 行授权记录） | D1=C 仅人工导入、D2=B 小红书+抖音，与实现一致
22:38:29 | 计划 | 复盘阶段1-3执行效率并产出提示词优化报告 | PROMPT_OPTIMIZATION.md | 30+ 会话 transcript 逐条分析；瓶颈归因 70% 执行机制 / 20% 提示词粒度 / 10% 技术债
22:38:29 | 计划 | 新增执行效率规则（并行优先/编译节奏/禁批量正则/数据层前置检查/中间播报/checkpoint/范围确认/脚本清理） | CLAUDE.md | 8 条强制规则已写入
22:38:29 | 计划 | 新建阶段3 checkpoint 文件 | STAGE3_STATUS.md | 1 页；13/13 子任务 + E1-E3 PASS + 冻结决策 D1-D5 + 6 个踩坑点
22:52:10 | 计划 | 全仓盘点「留学生求职」垂类残留并确定全平台全品类改造范围 | 代码/文档/数据/前端枚举 | 定位基线文档、Prompt、种子数据、品类枚举、评测基准 5 类残留点
22:58:40 | 后端 | 修正项目定位基线文档 | CLAUDE.md + .agents/{project-manager,README}.md | 项目名改为「小红书全平台全品类 AI 内容运营助手」
23:04:12 | 后端 | 修正后端文档垂类表述 | docs/{PRD-V3.0,prompt-rubric,BODY-GENERATION-PRD,RAG-DESIGN,AGENT-TOOL-CALLING,MCP-SERVER,STAGE0-FREEZE-PLAN,wireframe-report}.md + docs/README.md + docs/STAGE0-FREEZE-DECLARATION.md | 目标用户/评分维度/内容类型描述全部改为跨品类通用结构范式
23:10:35 | 后端 | 修正 Prompt 与 Agent 工具层垂类文案 | ai/prompt/PromptEngine.java + ai/agent/tool/impl/SearchKbTool.java + ai/agent/tool/SampleAnalysisReport.java | 负向约束与人设语气标签改为全品类运营
23:14:08 | 后端 | 修正评论生成与记忆服务的垂类人设 | service/CommentGenerateService.java + dto/comment/CommentGenerateRequest.java + ai/agent/memory/ChatHistoryService.java | 学长学姐→素人博主；导师IP→创作者IP；中介→品牌/机构
23:18:50 | 前端 | 统一模板品类枚举为 10 个标准品类 | src/constants/templateCategories.ts + components/template/TemplatePicker.vue + views/template/{TemplateEditorView,TemplateLibraryView}.vue | 新建 templateCategories.ts；品类码与 app.category 对齐
23:24:16 | 前端 | 更新模板品类 i18n 文案并重生成类型 | src/locales/{zh-CN,en,zh-TW,types}.ts | 10 个品类三语文案；scripts/gen_locale_types.py 重生成 types.ts
23:31:42 | 后端 | 重写官方模板种子为 10 个多品类模板 | src/main/resources/data/templates/seed.json | 8 个求职垂类模板 → BEAUTY/FASHION/FOOD/TRAVEL/LIFESTYLE/KNOWLEDGE/MOM_BABY/CAREER/STUDY_ABROAD/OTHER
23:36:04 | 数据库 | 新建全品类定位迁移 | V59__all_category_positioning.sql | app.category 补 5 品类 + 展示名对齐；app.template 加 code 幂等键 + 唯一索引；旧官方模板软归档；存量用户模板品类归一
23:39:22 | 后端 | 模板实体新增 code 幂等键字段 | domain/entity/Template.java + dto/template/TemplateResponse.java + template/TemplateService.java | code 仅 OFFICIAL 非空；响应透传 code
23:44:30 | 后端 | 种子初始化改为按 code 幂等 upsert | template/TemplateSeedRunner.java | 原「OFFICIAL 非空即跳过」改为 insert/update/unchanged 三态；定位变更后重启自动收敛
23:47:15 | 后端 | 清理残余垂类表述（文档/注释/测试夹具/知识库） | docs/{prompt-rubric,MCP-SERVER,PRD-V3.0,api-schema/README,KB-SEED-30}.md + data/kb-sources/TC-INTERVIEW-01.md + 2 处测试 + 2 处代码注释 | 秋招/求职/学长学姐等表述全部替换为跨品类等价物
23:50:05 | 后端 | 重写 AI 评测基准为跨品类 20 案例 | docs/seed-cases.md + docs/seed-cases.csv | 覆盖美妆/穿搭/美食/旅行/家居/知识/母婴/职场/留学 9 品类；content_type 枚举不变；CSV 18 列一致
23:53:40 | 验证 | 后端编译 + 前端类型检查 | mvn -o -q compile / npx vue-tsc -b | 均零错误
23:56:20 | 验证 | 迁移在本地 dev 库实测通过 | V59 + Flyway | Flyway 成功应用 v59；15 个品类中文名正确；10 个新模板入库、8 个旧模板归档
23:58:05 | 验证 | 种子 runner 端到端验证 | TemplateSeedRunner | inserted=10 updated=0 unchanged=0；模板正文/品类/变量正确入库
22:35:10 | 后端 | 修复模板模块 workspace_id 类型归一（P0） | V60__workspace_id_type_alignment.sql + 模板/配额/资产/主题/导出/品牌词实体与 Mapper | VARCHAR(32) 与 app.workspace.id 一致；全部 400 变 200
22:38:45 | 后端 | 修复 QuotaLedger/QuotaRefund 服务与测试的 workspace 参数 | quota/QuotaLedgerService.java + quota/QuotaRefundService.java + 2 个配额测试 | UUID→String；全部 16 个配额测试通过
22:52:30 | 修复 | 注册 MyBatis-Plus 分页插件并移除手写 OFFSET/LIMIT | config/MybatisPlusConfig.java + pom.xml(新增 mybatis-plus-jsqlparser) + Template/ExportLog/ContentAsset/TopicPool 4 个 Mapper 与调用方 | 之前 total 恒为 0；MP 3.5.9 起 jsqlparser 需单独依赖，本地 maven.aliyun.com 可直连下载
22:55:20 | 后端 | 模板品类参数大小写归一 | template/TemplateService.java(normalizeCategory) | beauty→BEAUTY；大小写两种写法均可查到
22:56:05 | 修复 | 清理 TemplateMapper 注释乱码 | domain/mapper/TemplateMapper.java | 全仓扫描乱码文件仅此 1 个，已归零
23:05:40 | 后端 | 缺参/类型错从 500 改为 400 | common/exception/GlobalExceptionHandler.java | 新增 MissingServletRequestParameter/TypeMismatch 处理；message 带参数名
23:12:15 | 修复 | AdminTaskController 硬注入条件 Bean 导致测试装配失败 | controller/v1/admin/AdminTaskController.java | DeadLetterQueue 改为 ObjectProvider 延迟获取；缺 Bean 时 retry 返回 task_queue_disabled
23:15:30 | 测试 | 集成测试加 test profile 跳过本地 CORS 校验 | ChatFlow/ChatStreamIntegrationTest.java | 无 test profile 时 localhost origin 会被 CorsStartupValidator 拦截；2 个测试全部通过

## 2026-10-28

22:36:12 | 计划 | 提交前核对阶段三与全平台定位一致性 | 全仓 grep「留学生/求职/秋招/学长学姐/导师IP」 | 后端 docs 与前端 src 零命中；仅剩 V34 冻结历史注释与模板正文样例（属正常品类内容）
22:38:40 | 计划 | 清理临时文件与一次性脚本 | 根目录 .e2e*/.err*/.log*/.smoke.ps1/fix_encoding.ps1 + scripts/_*.py | 保留 scripts/_audit_*.py（可复用审计）与 stage3-e2e*.ps1
22:41:05 | 验证 | 提交前编译与类型检查 | mvn -o -q compile / npx vue-tsc -b | 均 EXIT=0
22:44:30 | 计划 | 提交阶段三 + 全平台全品类改造（116 改动文件 + 阶段三新增文件） | xhs_agent 分支 | 待提交
22:47:12 | 验证 | 提交成功 | 2846c2c | 311 files changed, +26782/-1797；工作区干净；mvn compile 与 vue-tsc 均 EXIT=0
22:51:30 | 验证 | 推送到远程 origin | xhs_agent @ 604whl/-AI- | e2c719c..fab6307 成功；代理 127.0.0.1:7897 未运行，本次用 -c http.proxy= 直连绕过
22:58:40 | 计划 | 新建推送辅助脚本 | scripts/push.ps1 | 自动探测代理端口可用性；不可用时 -c http.proxy= 直连；推送后回读远程 HEAD 校验
22:59:20 | 验证 | 推送脚本实测 | pwsh -File scripts/push.ps1 -DryRun / 实际推送 | DryRun 正确识别代理失效并组装直连命令；实际推送 Everything up-to-date 且远程 HEAD 校验通过
23:01:05 | 计划 | CLAUDE.md 新增「推送远程」强制规则 | CLAUDE.md 执行效率规则 9 | 一律用 scripts/push.ps1，禁止手敲 git push
 23:40:09 | 计划 | 盘点阶段1-3已完成/未完成功能与阶段4及后续规划清单 | COMMERCIAL_SAAS_READINESS_PLAN.md + STAGE2_ACCEPTANCE.md + STAGE3_STATUS.md + 代码扫描(41 Controller/45 迁移/540 Java/31 Vue) | 只读盘点完成，未改代码；输出四段清单
23:46:05 | 计划 | 生成阶段4验收基线与开发计划 | STAGE4_PLAN.md | 8个子能力 + 12个子任务(S4-0~S4-11) + 数据模型/接口契约/出口条件 E1-E5 + 阶段1-3遗留 L1-L10 + 风险R1-R12 + 待冻结决策D6-D12，857行
21:33:46 | 决策 | 冻结阶段4未冻结决策 D6-D12 | STAGE4_STATUS.md | D6=C混合计费 / D7=A支付宝+微信 / D9=A预付+7天宽限；D8/D10/D11/D12 按计划默认执行
21:33:47 | 计划 | 创建阶段4 checkpoint 并启动批次A+B开发 | STAGE4_STATUS.md + STAGE4_PLAN.md | 范围锁定 S4-0~S4-3
21:54:15 | 数据库 | 新建套餐与订阅核心迁移 | V62__stage4_subscription_plans.sql | subscription_plan + subscription 落地，部分唯一索引生效，psql 事务内幂等复跑通过，4 个套餐已预置
21:54:29 | 后端 | 新建套餐/订阅实体、Mapper、Service、Controller 与到期任务 | billing/** + controller/v1/{PlanCatalog,Subscription}Controller + controller/v1/admin/SubscriptionAdminController + dto/billing/** | 套餐版本化 + 订阅状态机 + 试用 + 升降级 + 到期降级队列，mvn compile 通过
22:01:33 | 数据库 | 新建 api_key workspace_id 宽度归一迁移 | V61__stage4_workspace_id_alignment.sql | VARCHAR(64)→VARCHAR(32)，Flyway v61 应用成功，FK 保留且可重复执行
22:02:08 | 后端 | 实现 CSV 导入真实落库（资产/选题） | service/CsvImportService.java + service/TopicPoolService.java + service/TopicPoolServiceImpl.java | 去掉 TODO 占位；注入 ContentAssetService.createDraftInternal 与 TopicPoolService.createInternal（新增接口方法，去重命中返回 null 记 skipped）；仅写库成功才 imported++
22:02:19 | 后端 | 实现媒体 5 分钟预签名 URL 与 MinioClient 复用 | service/MediaStorageService.java | 抽出 minioClient() 复用三处；storage=minio 时 buildResponse 返回 GetPresignedObjectUrl 300s 签名，失败回退 baseUrl 拼接不打断上传
22:02:20 | 修复 | 浏览器辅助改为诚实降级（不再伪装填充成功） | service/BrowserAssistService.java + service/browser/BrowserDriver.java + service/browser/NoopBrowserDriver.java | 新增 BrowserDriver 抽象与 Noop 实现；fillSession 在驱动不可用/fill 失败时置 STATUS_FAILED 并抛 40950 browser_manual_required
22:02:21 | 验证 | 编译与迁移验证 | Xhs-Backend | mvn -o -q compile EXIT=0；mvn package EXIT=0；Flyway v61 success=t，api_key.workspace_id 长度 32，FK api_key_workspace_fk 保留；V61 SQL 重跑幂等通过
22:03:45 | 数据库 | 新建 V61 迁移并归一 api_key.workspace_id 宽度 | Xhs-Backend/src/main/resources/db/migration/V61__stage4_workspace_id_alignment.sql | VARCHAR(64)->VARCHAR(32)，孤儿数据清理 + 幂等复跑通过，FK 保持有效
22:07:23 | 修复 | 修复浏览器填充失败状态被事务回滚撤销的缺陷 | service/BrowserAssistService.java | markFailed 改用 TransactionTemplate + REQUIRES_NEW 独立提交，fillSession 去掉 @Transactional，避免 honest degrade 语义失效
22:07:24 | 验证 | 批次A 编译与迁移实测 | Xhs-Backend + app.subscription_plan/app.subscription | mvn compile EXIT=0；Flyway v61/v62 success=t；4 个套餐预置成功；部分唯一索引 uq_subscription_workspace_active 生效
22:10:33 | 修复 | 修复宽限期到期扫描漏扫 past_due 订阅导致订阅永久卡在 active | SubscriptionMapper.java + SubscriptionExpiryScheduler.java + SubscriptionExpiryTaskHandler.java | 新增 selectGraceExpiredCandidates 用 current_period_end + grace_period_days 内联计算；compareAndSetExpired 扩展为 trialing/active/past_due 三态；调度器补充宽限期扫描
22:10:47 | 修复 | 修复升级审计 fromPlanCode 被目标套餐覆盖导致审计记录错误 | billing/subscription/SubscriptionService.java | 变更前提取 fromPlanCode 局部变量，升级/降级分支审计均记录真实来源套餐
22:10:48 | 验证 | 实测订阅状态机与部分唯一索引 | app.subscription | 部分唯一索引生效(重复 active 被拒)；active->expired CAS 首次 UPDATE 1 重复 UPDATE 0；expired 不阻塞新订阅；宽限期扫描命中 1 条；V62 幂等复跑 INSERT 0 0
22:10:59 | 验证 | V62 迁移 Flyway 落地与幂等复跑验证 | V62__stage4_subscription_plans.sql | Flyway v62 success=t checksum 2062865409 与本地文件一致；二次执行 INSERT 0 0 且全部 IF NOT EXISTS 命中；4 套餐预置 FREE/PRO/TEAM/USAGE 全 active
22:15:30 | 数据库 | 新建订单与对账迁移 | V63__stage4_subscription_orders.sql | subscription_order + payment_event + bbilling_reconciliation 落地，事务内双跑幂等通过
22:23:09 | 验证 | S4-1 编译验证与文件编码复核 | Xhs-Backend | mvn -o -q compile EXIT=0；S4-1 全部 20 个文件 UTF-8 无 BOM 复核通过
22:23:10 | 后端 | 订阅写接口限流分层改造（注解层按用户防刷 + Service 层按 workspaceId 权威计数） | controller/v1/SubscriptionController.java + billing/subscription/SubscriptionService.java | 避免 X-Workspace-Id 请求头与路径变量不一致导致限流分桶被绕过；权威频次仍为订阅 5/天、改套餐 3/天、试用 1/工作区
22:26:55 | 数据库 | 新建价格表与账单迁移 | V64__stage4_pricing_statements.sql | price_book + bbilling_statement(+item) + workspace_balance 落地，幂等复跑通过
22:27:01 | 修复 | 修复 cancel/change-plan 幂等键未落库导致重放不被识别 | billing/subscription/SubscriptionService.java + billing/subscription/domain/mapper/SubscriptionMapper.java | 新增 bindIdempotencyKey 原子绑定（idempotency_key IS NULL 条件），cancel/change-plan 首次执行时补绑幂等键，重复提交命中 (workspace_id, idempotency_key) 唯一索引被识别为重放
22:27:07 | 后端 | 新建 S4-3 计价/余额/账单服务 | billing/pricing/**, billing/balance/**, billing/statement/** | PricingService(单价版本化) + UsagePricingService(台账价格快照) + BalanceService(原子扣减) + StatementService(幂等出账) + AdminMarginService(毛利聚合)
22:27:08 | 后端 | 新建账单接口 | controller/v1/BillingController.java | 4 个契约接口（statements 分页/详情 + cost-usage + admin cost-margin），OWNER/ADMIN 校验，跨 ws 返 404
22:27:21 | 验证 | 迁移双跑与功能验证 | V64__stage4_pricing_statements.sql | 事务内连跑两遍通过+ROLLBACK；并发扣减 10x1.0 仅 5 次成功余额 0；账单幂等唯一索引拦截；金额复算 0.6000 四路一致
22:27:23 | 验证 | 编译验证 | Xhs-Backend | mvn -o -q compile EXIT=0（临时修补他人 CsvImportService/NoopBrowserDriver 后已还原）
22:30:47 | 修复 | 修复余额入账幂等键缺失与价格改版唯一键冲突 | billing/balance/BalanceService.java + billing/pricing/PricingService.java | 入账 reason 补 orderNo 片段使幂等检查可命中；改价改为递增 planVersion 并支持原地更新已有套餐版本价格行
22:30:48 | 修复 | 修复台账批量归集参数超限 | quota/domain/mapper/UsageLedgerMapper.java | attachStatementBatch 改为 500 条分批，规避 PG 65535 绑定参数上限
22:33:32 | 后端 | 落地 CSV 导入真实落库并修复去重查询 | Xhs-Backend/src/main/java/com/shortvideoscripagent/xhsagentyunying/service/CsvImportService.java + TopicPoolService(Impl) + TopicPoolMapper | importAssets/importTopics 真实写库；去掉外层 @Transactional 避免单行失败整批 aborted；引号 CSV 解析；修正 selectByDedupeKey 用 normalized_title（恒 NULL）导致去重失效的 P0
22:33:48 | 后端 | 媒体下载改走 MinIO 5 分钟预签名 URL | Xhs-Backend/src/main/java/com/shortvideoscripagent/xhsagentyunying/service/MediaStorageService.java | minio 类型返回 presigned URL（TTL 300s），生成失败 warn 并回退 baseUrl 拼接；抽出 minioClient() 复用，local 类型行为不变
22:33:50 | 后端 | 浏览器辅助改为诚实降级（无驱动即 failed + 人工介入） | Xhs-Backend/src/main/java/com/shortvideoscripagent/xhsagentyunying/service/BrowserAssistService.java + service/browser/{BrowserDriver,NoopBrowserDriver}.java | 新增 BrowserDriver 抽象 + NoopBrowserDriver（@ConditionalOnMissingBean）；fillSession 不再伪装 STATUS_FILLED，改置 STATUS_FAILED 并抛 40950 browser_manual_required
22:46:41 | 验证 | S4-1 最终全量编译与数据库状态确认 | Xhs-Backend + app.subscription_plan | mvn -o -q compile EXIT=0（全项目含 S4-2/S4-3 并行产物）；Flyway V60-V64 全部 success=t；4 个预置套餐 FREE/PRO/TEAM/USAGE 全 active；app.subscription 无残留测试数据
22:46:56 | 计划 | S4-0 遗留项清理收口（L1/L2/L3/L8 全部落地） | CsvImportService.java + TopicPoolService(Impl).java + MediaStorageService.java + BrowserAssistService.java + service/browser/** + V61__stage4_workspace_id_alignment.sql | L1 CSV 导入真实落库（仅写库成功才 imported++）；L2 MinIO 5 分钟预签名 URL（minioClient() 复用三处，失败回退 baseUrl）；L3 浏览器辅助诚实降级（BrowserDriver 抽象 + Noop 实现，失败置 failed + 40950 browser_manual_required，绝不伪装 FILLED）；L8 api_key.workspace_id VARCHAR(64)->(32)，Flyway v61 success=t，FK 保留
22:46:57 | 修复 | 修复浏览器填充失败状态被事务回滚撤销的缺陷 | service/BrowserAssistService.java | markFailed 改用 TransactionTemplate + PROPAGATION_REQUIRES_NEW 独立提交；fillSession 去掉 @Transactional。避免 honest degrade 语义失效（失败状态被回滚后调用方看不出失败）
22:46:58 | 修复 | 修复 CSV 导入重复创建与去重查询缺租户隔离 | service/CsvImportService.java + service/TopicPoolService.java + service/TopicPoolServiceImpl.java | importTopics 去重查询补 workspaceId 条件；重复导入改为幂等跳过而非重复创建
22:47:13 | 计划 | S4-1 套餐与订阅收口（表 + 状态机 + 到期降级队列 + API） | billing/plan/** + billing/subscription/** + billing/support/** + controller/v1/PlanCatalogController.java + controller/v1/SubscriptionController.java + controller/v1/admin/SubscriptionAdminController.java + dto/billing/** + V62__stage4_subscription_plans.sql | 套餐版本化(code+version唯一, FREE/PRO/TEAM/USAGE 四档已预置) + 订阅状态机(trialing/active/past_due/canceled/expired) + 试用/升降级/取消 + 持久化到期任务队列(到期降级FREE保留数据) + OWNER 写权限 + 幂等键 + 限流 + 跨workspace返回404；Flyway v62 success=t
22:47:14 | 修复 | 修复订阅到期扫描漏扫 past_due 宽限期与过期原子性 | billing/subscription/domain/mapper/SubscriptionMapper.java + SubscriptionExpiryScheduler.java + SubscriptionExpiryTaskHandler.java | 新增 selectGraceExpiredCandidates 按 current_period_end + grace_period_days 计算宽限截止；compareAndSetExpired 扩为 trialing/active/past_due 三态 CAS，重复消费 UPDATE 0 幂等跳过
22:47:15 | 修复 | 修复 cancel/change-plan 并发与过期订阅识别 | billing/subscription/SubscriptionService.java + billing/subscription/domain/mapper/SubscriptionMapper.java | 并发下单改为不抛脏数据；过期订阅从状态判定而非计划编码判定
22:47:30 | 计划 | S4-2 支付抽象与订单主体落地（渠道抽象 + 订单状态机 + 回调验签 + 对账） | billing/payment/** (11 文件) + billing/order/** (15 文件) + controller/v1/OrderController.java + controller/v1/PaymentCallbackController.java + controller/v1/admin/BillingAdminController.java + V63__stage4_subscription_orders.sql | PaymentProvider 统一抽象 + Mock/Alipay/Wechat 三实现（凭据走环境变量，未配置返回 PAYMENT_PROVIDER_NOT_CONFIGURED，真实渠道零改动切换）；订单状态机 pending->paid->fulfilled/refunded/closed；回调验签 + 事件ID去重 + 金额校验；履约走持久化队列(FulfillOrderTaskHandler)失败进DLQ；订阅订单/充值订单/对账差异表落地，Flyway v63 success=t
22:47:32 | 数据库 | 新建订单、支付事件与对账迁移 | V63__stage4_subscription_orders.sql | app.subscription_order + app.payment_event + app.billing_reconciliation 三表落地；幂等双跑事务内验证通过
22:47:33 | 后端 | 新建支付渠道抽象与三渠道实现 | billing/payment/provider/{PaymentProvider,PaymentProviderRegistry,MockPaymentProvider,AlipayPaymentProvider,WechatPayPaymentProvider,PaymentCredentials,PemKeyUtils,PaymentErrors}.java + billing/payment/domain/entity/PaymentEvent.java + PaymentEventMapper.java | 验签 + 时间戳窗口 + 重放保护；凭据不落库、不进日志、不进异常消息
22:47:53 | 后端 | 新建订单服务、履约服务与对账任务 | billing/order/{OrderService,OrderFulfillmentService,OrderStatus,OrderType,ReconciliationDiffType,ReconciliationService}.java + billing/order/domain/** + billing/order/task/{FulfillOrderTaskHandler,BillingReconciliationTaskHandler,BillingScheduler}.java + billing/order/PaymentCallbackService.java + PaymentCallbackAuthFilter.java | 履约入队重试(DLQ 可重试，支付成功但履约失败订单仍为 paid 不丢单)；对账差异三分类 local_paid_channel_unpaid / local_unpaid_channel_paid / amount_mismatch；回调走 AuthFilter 验签 + 事件ID去重
22:47:55 | 后端 | 新建订单与支付回调接口 | controller/v1/OrderController.java + controller/v1/PaymentCallbackController.java + controller/v1/admin/BillingAdminController.java + dto/billing/order/OrderDtos.java | 下单(OWNER+幂等键+10次/天限流) / 订单列表分页 / 订单详情 / 支付回调 / 管理端退款与订单列表 / 管理端对账列表
22:48:26 | 计划 | S4-3 按量计费、价格表、余额与账单生成落地 | billing/pricing/** (4 文件) + billing/balance/** (3 文件) + billing/statement/** (8 文件) + controller/v1/BillingController.java + quota/domain/entity/UsageLedger.java + quota/domain/mapper/UsageLedgerMapper.java + V64__stage4_pricing_statements.sql | 价格表版本化(unit+region+plan_version唯一, 改价不追溯历史) + 8 个 BillableUnit 单价与成本预置 + 余额原子扣减(balance>=amount 条件UPDATE, 并发不出负余额) + 冻结/解冻 + 用量转账单走持久化队列(幂等, 同workspace同周期不重复生成) + 账单明细可回溯 ledger + 毛利聚合(收入-成本)；Flyway v64 success=t
22:48:27 | 数据库 | 新建价格表、余额与账单迁移 | V64__stage4_pricing_statements.sql | app.price_book + app.workspace_balance + app.billing_statement + app.billing_statement_item 四表落地；usage_ledger 加 unit_price/amount/price_version/statement_id/provider 五列（历史 amount 允许 NULL，不回填）
22:48:28 | 修复 | 修复账单回挂用量明细的批量参数超限 | quota/domain/mapper/UsageLedgerMapper.java | attachStatementBatch 分批处理，规避 PG 65535 参数上限导致 500

## 2026-10-29 小结

### 阶段4 批次A+B 开发（S4-0 ~ S4-3）

| 子任务 | 状态 | 迁移 | 核心产出 |
|--------|------|------|----------|
| S4-0 遗留项清理 | **完成** | V61 | L1 CSV 真实落库 / L2 MinIO 5 分钟预签名 URL / L3 浏览器诚实降级 / L8 api_key workspace_id 归一 |
| S4-1 套餐与订阅 | **完成** | V62 | 套餐版本化 + 订阅状态机 + 试用/升降级 + 到期降级队列 + OWNER 鉴权 + 幂等 + 跨租户 404 |
| S4-2 支付抽象与订单 | **主体完成** | V63 | PaymentProvider 抽象 + Mock/支付宝/微信三实现 + 订单状态机 + 回调验签与重放保护 + 履约队列 + 对账任务 |
| S4-3 计价/余额/账单 | **主体完成** | V64 | 价格表版本化 + 余额原子扣减 + 冻结解冻 + 用量转账单（幂等）+ 明细可回溯 + 毛利聚合 |

### 数据库
- Flyway 累计 **V61 ~ V64 全部 success=t**（V60-V64 共 5 条连续校验通过）。
- 新增 9 张阶段4表：subscription_plan、subscription、subscription_order、payment_event、billing_reconciliation、price_book、workspace_balance、billing_statement、billing_statement_item。
- 预置数据：FREE/PRO/TEAM/USAGE 四档套餐 + 8 个 BillableUnit 单价与成本（region=CN）。
- 全部迁移均通过**事务内双跑幂等验证**（BEGIN → 连续执行两遍 → ROLLBACK），V61/V62/V63/V64 零报错。

### 代码规模
- 新增 billing 领域包 8 个：plan、subscription、support、payment、order、pricing、balance、statement，共 **57 个 Java 文件**。
- 新增接口层 6 个 Controller：PlanCatalogController、SubscriptionController、SubscriptionAdminController、OrderController、PaymentCallbackController、BillingAdminController。

### 本日修复的关键缺陷
1. **浏览器填充失败状态被事务回滚撤销** —— markFailed 改用 TransactionTemplate + PROPAGATION_REQUIRES_NEW 独立提交，否则「诚实降级」语义失效（调用方看不出失败）。由统筹者复核时发现并修复。
2. **CSV 导入去重查询失效（P0）** —— selectByDedupeKey 用了恒为 NULL 的 normalized_title 字段，导致去重完全没生效；已改为按真实字段查询并补 workspaceId 租户隔离。
3. **订阅到期漏扫 past_due 宽限期** —— 新增 selectGraceExpiredCandidates，按 current_period_end + grace_period_days 计算宽限截止。
4. **订阅过期流转非原子** —— compareAndSetExpired 扩为三态 CAS，重复消费 UPDATE 0 幂等跳过。
5. **账单回挂用量明细参数超限** —— attachStatementBatch 改 500 条分批，规避 PG 65535 绑定参数上限导致的 500。
6. **余额入账幂等键缺失** —— reason 补 orderNo 片段使幂等检查可命中。
7. **cancel/change-plan 幂等键未落库** —— 新增 bindIdempotencyKey 原子绑定。

### 遗留问题与风险
- **真实支付渠道未联调**：支付宝/微信商户号与证书未落地，渠道实现为骨架，凭据走环境变量，未配置时返回 PAYMENT_PROVIDER_NOT_CONFIGURED；当前仅 Mock 渠道可跑通端到端。
- **未做接口级 E2E**：S4-0~S4-3 均为编译 + 迁移实测 + 局部数据验证，**尚未跑订单→回调→履约、余额并发扣减、账单幂等的端到端脚本**。
- **真实浏览器驱动未接入**：NoopBrowserDriver 恒返回不可用，需后续实现 Playwright/Selenium 的 BrowserDriver Bean 覆盖。
- **价格表为初始建议值**：8 个计量单位的单价与成本为建议值，待业务定稿。
- **前端未动工**：批次 A+B 全为后端，S4-4~S4-11 的前端页面与 i18n 未开始。

### 下一步
1. code-reviewer 审查批次 A+B 全部改动（重点：验签绕过、金额精度、余额并发、租户越权、履约丢失）。
2. 补 S4-2/S4-3 的接口级 E2E 脚本（订单→回调→履约、账单幂等、跨租户越权）。
3. 更新 STAGE4_STATUS.md checkpoint，并用 scripts/push.ps1 推送。22:59:27 | 修复 | 补齐 4 个 billing Mapper 包的 MapperScan 注册 | XhsAgentYunyingApplication.java | order/pricing/balance/statement 四个 domain.mapper 包未注册导致 PaymentEventMapper 等 Bean 缺失，应用启动直接失败；补齐后应用成功启动
22:59:28 | 修复 | 修复 NoopBrowserDriver 因误用 ConditionalOnMissingBean 导致 Bean 未注册 | service/browser/NoopBrowserDriver.java | ConditionalOnMissingBean 仅在自动配置类生效，普通 Component 场景条件不执行导致 BrowserDriver Bean 缺失；改为普通 Component 并在注释说明正确做法
22:59:29 | 验证 | 应用全量启动与 Flyway 校验通过 | XhsAgent-yunying-0.0.1-SNAPSHOT.jar | Started Application 15.63s；Flyway Successfully validated 50 migrations，V61-V64 checksum 无冲突；mvn package EXIT=0
23:13:26 | 验证 | 支付回调链路实测（鉴权拦截 + 验签 + 审计） | POST /api/v1/payments/callback/mock | 无时间戳→40013 timestamp_missing；无签名→40012 signature_missing；伪造签名→50301 mock_secret_not_configured；三种情况均写 PAYMENT_CALLBACK_REJECTED 审计；修复前该接口一律 401 渠道无法回调
23:13:43 | 修复 | 修复 Mock 支付渠道硬编码默认密钥导致可伪造回调白嫖订单 | billing/payment/provider/MockPaymentProvider.java | 移除硬编码默认密钥；secret() 未配置 PAYMENT_MOCK_SECRET 时返回 null，验签一律拒绝 50301；configured() 反映真实配置状态；类加 @Profile 非 prod 不在生产注册
23:13:44 | 修复 | 修复支付回调过滤器 shouldNotFilter 判断反导致回调被 401 挡死 | billing/payment/config/PaymentCallbackAuthFilter.java | 原逻辑在回调路径上跳过过滤器，请求落到 AuthInterceptor 返回 401，渠道永远调不通；改为仅非回调路径跳过；实测无签名无时间戳伪造签名均正确拒绝并写审计
23:15:51 | 验证 | 全量编码扫描与一次性脚本清理 | scripts/ 与全部新增文件 | 新增 Java/SQL 文件全部 UTF-8 无 BOM，仅 STAGE4_PLAN.md 带 BOM（文档无影响）；已删除 4 个一次性修复脚本，保留可复用 audit 脚本
23:22:32 | 修复 | 修复余额原子扣减未排除冻结金额导致冻结资金被二次消费 | billing/balance/domain/mapper/WorkspaceBalanceMapper.java + BalanceService.java | atomicDebit 条件由 balance>=amount 改为 balance-frozen>=amount，与 toView 的 available 口径及 atomicFreeze 一致；余额不足报错信息同步改为可用余额；实测旧逻辑 UPDATE 1 导致 available 变负 -30，新逻辑 UPDATE 0 正确拒绝
23:25:13 | 修复 | 修复账单幂等键未做时区归一导致同周期可能生成两张账单 | billing/statement/StatementService.java + StatementScheduler.java | 幂等键由 OffsetDateTime.toString 改为 toInstant 统一到 UTC；同一时间点用不同偏移表示时旧逻辑生成两个不同键 unique 索引拦不住，新逻辑生成同一个键；实测 OLD equal=false NEW equal=true
23:26:11 | 验证 | S4-0 遗留项清理全链路实测（L1/L2/L3/L8） | CsvImportService/MediaStorageService/BrowserAssistService/V61 | L1 CSV 资产+选题真实落库 PASS（重复导入 skipped=1 去重生效）；L3 无驱动→STATUS_FAILED + BusinessException 40950 browser_manual_required PASS；L2 local 保持 baseUrl 拼接 + minio 预签名 X-Amz-Expires=300 生成成功 + 端点不可达时 WARN 回退不抛异常 PASS；V61 幂等复跑 EXIT=0，api_key.workspace_id=varchar(32)，3 条 FK 全在
23:29:34 | 修复 | 修复订阅幂等键绑定与内存状态不一致 | billing/subscription/SubscriptionService.java | bindIdempotencyKey 忽略 UPDATE 受影响行数，行已绑其他键时内存仍被写入导致与 DB 不一致；改为检查 updated==0 跳过内存写入并记 warn
23:29:35 | 验证 | 阶段四批次A+B全量收口验证 | XhsAgent-yunying-0.0.1-SNAPSHOT.jar | mvn clean package EXIT=0；应用启动成功 16.139s；Flyway validated 50 migrations 且 schema up to date；累计修复 3 个 P0 与 3 个 P1
21:12:04 | 修复 | 修复 UUID 主键未自赋值导致订阅/订单插入报非空约束 500 | billing/subscription/SubscriptionService.java、billing/order/OrderService.java、billing/order/OrderFulfillmentService.java、billing/payment/PaymentCallbackService.java、billing/balance/BalanceService.java、billing/plan/PlanService.java、billing/pricing/PricingService.java、billing/statement/StatementService.java、billing/order/ReconciliationService.java | 根因：MyBatis-Plus 的 IdType.ASSIGN_UUID 只对 String 主键自动赋值，UUID 主键不填则插入 id=null 触发 null value in column id 违反非空约束；已在 12 处 insert 站点补 setId(UUID.randomUUID())，与本仓库其余实体写法一致
21:18:52 | 验证 | 重启应用时发现 Docker Desktop 未运行导致连不上 PG | Xhs-Backend/s4k.out | 日志为 Caused by: java.net.ConnectException: Connection refused（5432）；已启动 Docker Desktop 并拉起 xhsagent-postgres-dev / xhsagent-redis-dev，Flyway V61-V64 校验仍为 success=t
21:24:41 | 修复 | 修复 E2E 脚本回调金额写死 1.00 导致 amount_mismatch | scripts/verify-stage4.ps1 | 服务端 amount_mismatch 校验正确（回调金额必须等于订单真实金额 199.0000），是脚本写死 $amt='1.00'；改为从订单响应读取 orderAmount 再格式化签名金额
21:26:52 | 修复 | 修复 S4-0 遗留项脚本在 PS7 下丢失错误响应体 | scripts/verify-s4-0-legacy.ps1 | Invoke-Api 的 catch 块只用 GetResponseStream()（PS5.1 专有），PS7 下抛错导致 409 响应体丢失、业务码读成 -1；补 ErrorDetails.Message 回退，服务端实际返回 40950 browser_manual_required 正确
21:27:30 | 验证 | 阶段四 S4-0~S4-3 接口级 E2E 双脚本全量通过 | scripts/verify-stage4.ps1 + scripts/verify-s4-0-legacy.ps1 | 阶段四脚本 14/14 PASS（健康检查/公开套餐目录免鉴权/套餐详情/支付回调三种非法请求拒绝/登录/订阅查询/订阅幂等重放同 id/订单幂等重放同 orderNo/合法签名回调 200/同事件重放 duplicate/账单列表）；遗留项脚本 15/15 PASS（CSV 资产落库/CSV 选题落库+去重 skipped=1/无驱动降级 40950 + 会话 failed）
2026-10-30 22:58:28 | 修复 | 修复分析任务配额扣减被跳过（UUID.fromString(ana_xxx) 抛异常） | quota/QuotaRefId.java + infra/taskqueue/handler/AnalysisTaskHandler.java + service/UserQuotaService.java | 新增 QuotaRefId 确定性映射（name-based UUID）；分析用量从此正常写入台账并产生价格快照
2026-10-30 22:58:28 | 修复 | 修复冲正事务被 usage_log 类型错误中止导致失败任务仍计费 | quota/QuotaRefundService.java + domain/entity/UsageLog.java | 根因 usage_log.id(bigint) 与 usage_ledger.ref_id(uuid) 混用触发 bigint = character varying，PG 事务 aborted 致冲正回滚；改为按 original_log_id 反查台账 id，legacy 更新独立 REQUIRES_NEW 事务
2026-10-30 22:58:28 | 修复 | 修复冲正缺 -1 对冲台账行导致账单仍计失败任务费用 | quota/QuotaRefundService.java | 冲正时另写一行 delta=-1、related_log_id 指向原始行的台账；出账/成本聚合加 status='CONSUMED' 过滤，被冲正行不进账单
2026-10-30 22:58:28 | 数据库 | 新建台账幂等去重键迁移 | V65__stage4_ledger_dedupe_key.sql | usage_ledger 加 dedupe_key 列 + 部分唯一索引 (workspace_id, dedupe_key) WHERE status='CONSUMED'；历史重复扣费行自愈冲正
2026-10-30 22:58:28 | 修复 | 修复任务重试/孤儿恢复导致同一任务重复扣费 | quota/AtomicQuotaGuard.java + quota/domain/mapper/UsageLedgerMapper.java | 新增 tryConsumeIdempotent + ON CONFLICT DO NOTHING 幂等插入（避免 PG 事务 aborted）；CHAT 等多次计费场景保留非幂等 tryConsume
2026-10-30 22:58:28 | 后端 | 台账实体补 dedupeKey 字段并扩展 usage_log 退费字段映射 | quota/domain/entity/UsageLedger.java + domain/entity/UsageLog.java | dedupeKey 参与唯一索引；UsageLog 补 refundStatus/refundedAt/originalLogId
2026-10-30 22:58:28 | 验证 | 配额相关单元测试全绿 | AtomicQuotaGuardTest(17) + QuotaRefundServiceTest(8) + QuotaRefIdTest(9) | Tests run 34, Failures 0, Errors 0；新增幂等重放/唯一冲突回补/冲正对冲行 4 个回归用例
2026-10-31 00:25:55 | 修复 | 修复审核发现的 P0 缺陷组（套餐订阅/升配绕过/余额充值/审计落库） | billing/subscription/SubscriptionService.java + billing/order/OrderFulfillmentService.java + domain/entity/AuditEvent.java + infra/audit/AuditWriter.java | P0-1 付费套餐直订阅绕过（改为 409 payment_required_create_order）；P0-2 用量充值订单未入余额（改为走 creditBalance）；P0-3 audit_event.id 为 UUID 而实体用 String 导致审计事件写不进库（改 UUID + 手动赋值）
2026-10-31 00:25:55 | 修复 | 修复 PG 事务 aborted 导致幂等重放 500 | billing/support/IdempotencyReplay.java + billing/order/OrderService.java + billing/subscription/SubscriptionService.java + billing/plan/PlanService.java + billing/statement/StatementService.java | 捕获 DuplicateKeyException 后当前事务已 aborted，改用 PROPAGATION_REQUIRES_NEW 独立事务读回既有实体做幂等重放
2026-10-31 00:25:55 | 修复 | 修复付费升配绕过支付 | billing/subscription/SubscriptionService.java | changePlan 计算按比例补差价，金额>0 时返回 409 payment_required_create_order，禁止直接切换到高价套餐
2026-10-31 00:25:55 | 修复 | 补齐缺失的账单与余额 HTTP 接口 | controller/v1/BillingController.java | 新增 GET /v1/workspaces/{id}/billing/balance、chargeStatement、voidStatement 三个端点
2026-10-31 00:25:55 | 修复 | 修复出账冲正/支付事件拒收在独立事务中回滚 | billing/statement/StatementService.java + billing/payment/PaymentCallbackService.java | rollbackChargingToOpen 与 markEventRejected 改为 PROPAGATION_REQUIRES_NEW
2026-10-31 00:25:55 | 修复 | 新增未履约订单重投定时任务 | billing/scheduler/BillingScheduler.java | requeueUnfulfilledOrders 扫描 pending 超时订单重新入队，避免履约链路静默丢失
2026-10-31 00:25:55 | 修复 | 修复 MyBatis-Plus autoResultMap 只对内置方法生效导致自定义 @Select 的 jsonb 列静默变 null（系统性 P0） | config/MybatisPlusConfig.java + config/mybatis/PgListTypeHandler.java | 根因：自定义 @Select 走 MyBatis 原生自动映射路径，按（属性类型 + 列 JDBC 类型）查全局注册表，而 jsonb 处理器只写在 @TableField 上未进注册表；注册 (Map/String/List, JdbcType.OTHER) 全局处理器后，/v1/plans 的 includedUnits+featureFlags 与 /subscription 的 planCode+price 从 null 恢复为真实值
2026-10-31 00:25:55 | 修复 | 新增 PgListTypeHandler 兼容 TEXT[] 与 jsonb 两种 List 列 | config/mybatis/PgListTypeHandler.java | 只注册 jsonb 版会让 TEXT[] 列（app.topic_pool.tags）被 MyBatis pickSoleHandler 回退命中而报 Failed to parse jsonb array；改为按结果集元数据真实列类型运行时分派
2026-10-31 00:25:55 | 修复 | 修复 UsageLedger.priceVersion 类型不匹配导致编译失败 | billing/statement/StatementService.java | priceVersion 为 Integer，代码按 String 使用；改为 String.valueOf 转换
2026-10-31 00:25:55 | 验证 | 阶段四三个验证套件全量通过 | scripts/verify-stage4-fixes.ps1 + scripts/verify-stage4.ps1 + scripts/verify-s4-0-legacy.ps1 | 修复套件 5/5 PASS（含 P0-1/P0-2/P0-3/P1-1/P1-2）；E2E 主流程 13/13 PASS；S4-0 遗留回归 15/15 PASS
2026-10-31 00:25:55 | 验证 | 后端全量单元测试通过 | Xhs-Backend/target/surefire-reports | Tests run 207, Failures 0, Errors 1；唯一 error 为 XhsAgentYunyingApplicationTests 的 Testcontainers ryuk 镜像拉取失败（环境网络问题，与本次改动无关）
2026-10-31 00:41:24 | 修复 | 修复 P2-1 下单远程支付调用被包在数据库事务内 | Xhs-Backend/src/main/java/com/shortvideoscripagent/xhsagentyunying/billing/order/OrderService.java | createOrder 去掉 @Transactional，改用 TransactionTemplate(PROPAGATION_REQUIRES_NEW) 显式三段：事务1插 pending 订单并提交 → 事务外 provider.createPayment → 事务2回写 providerOrderId/paymentPayload；渠道失败时事务2内 compareAndSetClosed 关单，closeOrderQuietly 改为 closeOrderAfterProviderFailure
2026-10-31 00:41:24 | 修复 | 修复 P2-2 对账远程拉账单被包在数据库事务内 | Xhs-Backend/src/main/java/com/shortvideoscripagent/xhsagentyunying/billing/order/ReconciliationService.java | reconcile 去掉 @Transactional，queryDailyBills 移到事务外，差异比对与落库拆到 reconcileBills 在 REQUIRES_NEW 事务内执行；upsertReconciliation 的 insert 也放独立事务避免唯一冲突 aborted 拖垮整批差异
2026-10-31 00:41:24 | 验证 | 修复后编译通过 | Xhs-Backend | mvn -o -q compile EXIT=0（2 次编译均 0 错误）；两文件均为 UTF-8 无 BOM
2026-10-31 00:41:45 | 后端 | 新增账单与余额单元测试类（补 P2-8 测试缺口） | Xhs-Backend/src/test/java/.../billing/balance/BalanceServiceTest.java + billing/statement/StatementServiceTest.java | 覆盖余额不足 40201、冻结计入可用余额口径、入账/扣减幂等重放、账单幂等键时区归一、P2-3 按价格版本拆行且行金额=单价×数量；Tests run 15, Failures 0, Errors 0
2026-10-31 00:41:45 | 后端 | 新增订阅单元测试类（补 P2-8 测试缺口） | Xhs-Backend/src/test/java/.../billing/subscription/SubscriptionServiceTest.java | 覆盖 P0-1 付费套餐直订阅被拒 409 payment_required_create_order、试用仅一次 trial_already_used、幂等键重放、非 OWNER 403 owner_required、非成员 404；Tests run 6, Failures 0, Errors 0
01:47:54 | 数据库 | 新建迁移 | V66__stage4_subscription_write_idempotency.sql | app.subscription_write_idempotency 表，PK (workspace_id, scope, idempotency_key)，scope 取 subscribe/trial/cancel/change_plan/admin_adjust；Flyway 实测 success=t，当前版本 66
01:47:54 | 后端 | 新增写幂等作用域 | billing/subscription/domain/entity/SubscriptionWriteIdempotency.java + domain/mapper/SubscriptionWriteIdempotencyMapper.java | insertIdempotent 用 INSERT ... ON CONFLICT DO NOTHING 返回 1=首次/0=重放，原子绑定
01:47:54 | 修复 | 修复 P3-4 订阅写操作幂等键抢占 | billing/subscription/SubscriptionService.java | cancel/change_plan 改用独立 scope 绑键，不再往 subscription.idempotency_key 抢 subscribe 的键；重放时跳过副作用
01:47:54 | 修复 | 修复 P3-5 管理员调整幂等 | billing/subscription/SubscriptionService.java + controller/v1/admin/SubscriptionAdminController.java | adminAdjust 接 Idempotency-Key 头，重放不再叠加 extendDays；缺 reason 返 40010
01:47:54 | 修复 | 实现 P3-2 升级配额按比例折算 | billing/subscription/SubscriptionService.java | applyPlanQuotaProrated：prorated = ceil(fullTargetUnits * 剩余天数/周期天数)
01:47:54 | 测试 | 补充订阅幂等作用域单测 | src/test/java/.../billing/subscription/SubscriptionServiceTest.java | 新增 6 例；Tests run 12, Failures 0, Errors 0
01:47:54 | 验证 | 应用重启 + V66 实测 | Xhs-Backend | mvn -o -q package EXIT=0；启动成功，Flyway validated 52 migrations，V66 success=t
01:47:54 | 验证 | 全量单测 | Xhs-Backend/target/surefire-reports | Tests run 234, Failures 0, Errors 1；唯一 error 为 Testcontainers ryuk 镜像拉取失败（网络问题）
01:47:54 | 验证 | 三套 E2E 复验全绿 | scripts/verify-stage4.ps1 + scripts/verify-stage4-fixes.ps1 + scripts/verify-s4-0-legacy.ps1 | 14/14 PASS + 5/5 PASS + 15/15 PASS
01:47:54 | 修复 | 修复本地 E2E 启动脚本环境变量传递缺陷 | Xhs-Backend/target/run-local.cmd | cmd set X=val && 会把分隔空格并入值导致 Mock 回调验签失败；改为 set "X=val" + ANSI 编码 .cmd 启动器；需 SPRING_PROFILES_ACTIVE=local
