# verify-2-comment-gen.ps1
# 手工验证脚本 — 评论生成 IDU stage2-2
# 说明：4 个场景各 1 个 curl 案例，运行前请确保后端服务已启动

$BASE = 'http://localhost:8080'

Write-Host '=== IDU stage2-2 评论生成 — 手工验证脚本 ===' -ForegroundColor Cyan
Write-Host ''

# 场景 1：首问回复 FIRST_QUESTION
Write-Host '[场景1] FIRST_QUESTION - 首问回复' -ForegroundColor Yellow
Write-Host 'curl -X POST "/api/v1/comments/generate" \'
Write-Host '  -H 'Content-Type: application/json' \'
Write-Host '  -d ''{"commentType":"FIRST_QUESTION","platform":"XIAOHONGSHU","persona":"mentor","draftContext":"学姐分享的澳洲留学经验贴","maxLength":200}'''
Write-Host ''
curl -X POST "/api/v1/comments/generate" 
  -H 'Content-Type: application/json' 
  -d '{\"commentType\":\"FIRST_QUESTION\",\"platform\":\"XIAOHONGSHU\",\"persona\":\"mentor\",\"draftContext\":\"学姐分享的澳洲留学经验贴\",\"maxLength\":200}'
Write-Host ''
Write-Host ''

# 场景 2：追问 ASK_FOLLOW_UP
Write-Host '[场景2] ASK_FOLLOW_UP - 追问' -ForegroundColor Yellow
Write-Host 'curl -X POST "/api/v1/comments/generate" \'
Write-Host '  -H 'Content-Type: application/json' \'
Write-Host '  -d ''{"commentType":"ASK_FOLLOW_UP","platform":"XIAOHONGSHU","persona":"agency","draftContext":"上条回复提到商科推荐","maxLength":150}'''
Write-Host ''
curl -X POST "/api/v1/comments/generate" 
  -H 'Content-Type: application/json' 
  -d '{\"commentType\":\"ASK_FOLLOW_UP\",\"platform\":\"XIAOHONGSHU\",\"persona\":\"agency\",\"draftContext\":\"上条回复提到商科推荐\",\"maxLength\":150}'
Write-Host ''
Write-Host ''

# 场景 3：私信话术 PRIVATE_MESSAGE
Write-Host '[场景3] PRIVATE_MESSAGE - 私信话术' -ForegroundColor Yellow
Write-Host 'curl -X POST "/api/v1/comments/generate" \'
Write-Host '  -H 'Content-Type: application/json' \'
Write-Host '  -d ''{"commentType":"PRIVATE_MESSAGE","platform":"DOUYIN","persona":"senior","draftContext":"留学中介推广视频","maxLength":100}'''
Write-Host ''
curl -X POST "/api/v1/comments/generate" 
  -H 'Content-Type: application/json' 
  -d '{\"commentType\":\"PRIVATE_MESSAGE\",\"platform\":\"DOUYIN\",\"persona\":\"senior\",\"draftContext\":\"留学中介推广视频\",\"maxLength\":100}'
Write-Host ''
Write-Host ''

# 场景 4：转化话术 CONVERSION
Write-Host '[场景4] CONVERSION - 转化话术' -ForegroundColor Yellow
Write-Host 'curl -X POST "/api/v1/comments/generate" \'
Write-Host '  -H 'Content-Type: application/json' \'
Write-Host '  -d ''{"commentType":"CONVERSION","platform":"XIAOHONGSHU","persona":"mentor","draftContext":"学姐整理的留学费用对比表","maxLength":250}'''
Write-Host ''
curl -X POST "/api/v1/comments/generate" 
  -H 'Content-Type: application/json' 
  -d '{\"commentType\":\"CONVERSION\",\"platform\":\"XIAOHONGSHU\",\"persona\":\"mentor\",\"draftContext\":\"学姐整理的留学费用对比表\",\"maxLength\":250}'
Write-Host ''
Write-Host ''

Write-Host '=== 验证完成 ===' -ForegroundColor Cyan
Write-Host '期望结果：code=0, data.comment 有内容, riskLevel=LOW (mock 模式）'