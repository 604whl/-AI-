const EMOTION_WORDS = [
  '破防',
  '崩溃',
  '焦虑',
  '卷疯',
  '至暗',
  '认清',
  '悟了',
  '终于',
  '彻底',
  '太难',
  '绝望',
]

const CONFLICT_WORDS = ['卷', '疯', 'VS', '却', '但', '然而', '没想到', '竟然']

const RESULT_WORDS = [
  '上岸',
  'Offer',
  '录取',
  '逆袭',
  '成功',
  '认清',
  '真相',
  '结果',
  '回复',
]

/** 从标题文本中提取可高亮片段 */
export function extractHighlightSegments(title: string, extra: string[] = []): string[] {
  const found = new Set<string>()

  const numberPatterns = title.match(/\d+[\u4e00-\u9fa5]{0,4}/g) ?? []
  numberPatterns.forEach((s) => found.add(s))

  for (const word of [...EMOTION_WORDS, ...CONFLICT_WORDS, ...RESULT_WORDS]) {
    if (title.includes(word)) found.add(word)
  }

  extra.forEach((s) => {
    if (s && title.includes(s)) found.add(s)
  })

  return [...found].sort((a, b) => b.length - a.length)
}

function escapeRegExp(text: string): string {
  return text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

/** 将标题转为带高亮 mark 的 HTML */
export function highlightTitleHtml(title: string, segments: string[]): string {
  if (!segments.length) return escapeHtml(title)

  const sorted = [...segments].sort((a, b) => b.length - a.length)
  let html = escapeHtml(title)

  for (const seg of sorted) {
    if (!seg) continue
    html = html.replace(
      new RegExp(escapeRegExp(seg), 'g'),
      `<mark class="title-highlight">${escapeHtml(seg)}</mark>`,
    )
  }

  return html
}

/** CTR 是否属于高亮档位 */
export function isHighCtr(ctr: number, threshold = 88): boolean {
  return ctr >= threshold
}
