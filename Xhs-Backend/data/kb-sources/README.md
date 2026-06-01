# 知识库源文档目录

将待入库的 `.md` 或 `.txt` 放在此目录，然后在 **local** 环境调用：

`POST /api/v1/admin/kb/reindex`

## 文档格式（推荐 Markdown + Front Matter）

```markdown
---
doc_id: CASE-01
doc_type: viral_case
content_type: OFFER
title: 双非逆袭字节
tags: 逆袭,职场
persona: agency
---

正文内容……
```

未写 front matter 时：`doc_id` 取文件名（不含扩展名），`doc_type` 默认 `viral_case`（可在 `application.yml` 的 `app.kb.default-doc-type` 修改）。

## 切片说明

使用 Spring AI `TokenTextSplitter`（按 token 智能切分，尽量在句号/换行处断开，支持 overlap）。参数见 `app.kb.chunking.*`。

## 预览切片（不入库）

`GET /api/v1/admin/kb/preview?docId=CASE-01`
