# Xhs-Frontend

小红书留学生求职 AI 运营助手 — Web 工作台。

## 技术栈

Vue 3 + TypeScript + Vite + Pinia + Element Plus + Axios + vue-i18n

## 开发

```bash
npm install
npm run dev
```

访问 http://localhost:5173 ，API 通过 Vite 代理到 `http://localhost:8125/api`。

## 目录

见 [后端 TECH-ARCHITECTURE.md](../Xhs-Backend/docs/TECH-ARCHITECTURE.md) §10。

## 环境变量

可选 `.env.development`：

```
VITE_API_BASE=/api/v1
```

## 多语言

支持 **简体中文**（`zh-CN`）、**繁體中文**（`zh-TW`）、**English**（`en`）。

- 文案目录：`src/locales/`（`zh-CN.ts` / `zh-TW.ts` / `en.ts`）
- 语言切换：登录页与顶部导航栏右上角
- 选择会写入 `localStorage`（`xhs_locale`），下次访问自动恢复
- Element Plus 组件语言随应用语言联动

cd f:\projectAI\XhsAgent\Xhs-Frontend
npm install
npm run dev
