import { ref, shallowRef } from 'vue'
import { fetchScoredTitles } from '@/api/titles'
import type { ScoredTitle, TitleGenerateParams, TitleLoadingStep } from '@/types/title'

export function useTitleGeneration() {
  const loading = ref(false)
  const loadingStep = ref<TitleLoadingStep>('idle')
  const hasResult = ref(false)
  const titles = shallowRef<ScoredTitle[]>([])
  const promptVersion = ref('')

  async function runLoadingSteps(task: () => Promise<void>) {
    loading.value = true
    loadingStep.value = 'emotion'
    await delay(550)
    loadingStep.value = 'ctr'
    await delay(550)
    loadingStep.value = 'structure'
    await delay(550)
    loadingStep.value = 'optimize'
    await task()
    loadingStep.value = 'done'
    await delay(280)
    loadingStep.value = 'idle'
    loading.value = false
    hasResult.value = true
  }

  async function generate(params: TitleGenerateParams, analysisId?: string) {
    titles.value = []
    promptVersion.value = ''
    hasResult.value = false

    await runLoadingSteps(async () => {
      const result = await fetchScoredTitles(params, analysisId)
      titles.value = result.titles
      promptVersion.value = result.promptVersion
    })
  }

  function reset() {
    loading.value = false
    loadingStep.value = 'idle'
    hasResult.value = false
    titles.value = []
    promptVersion.value = ''
  }

  return {
    loading,
    loadingStep,
    hasResult,
    titles,
    promptVersion,
    generate,
    reset,
  }
}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}
