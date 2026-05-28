export type AppLocale = 'zh-CN' | 'zh-TW' | 'en'

export const LOCALE_OPTIONS: Array<{ value: AppLocale; labelKey: string }> = [
  { value: 'zh-CN', labelKey: 'language.zhCN' },
  { value: 'zh-TW', labelKey: 'language.zhTW' },
  { value: 'en', labelKey: 'language.en' },
]

export interface LocaleMessages {
  app: {
    title: string
  }
  common: {
    on: string
    off: string
    requestFailed: string
  }
  language: {
    label: string
    zhCN: string
    zhTW: string
    en: string
  }
  nav: {
    logo: string
    dashboard: string
    analysisNew: string
    titles: string
    history: string
    settings: string
    headerTitle: string
    logout: string
  }
  auth: {
    brandBadge: string
    brandTitle: string
    brandDesc: string
    feature1: string
    feature2: string
    feature3: string
    welcomeBack: string
    createAccount: string
    loginSubtitle: string
    registerSubtitle: string
    loginTab: string
    registerTab: string
    email: string
    password: string
    nickname: string
    confirmPassword: string
    emailPlaceholder: string
    passwordPlaceholder: string
    nicknamePlaceholder: string
    passwordRegisterPlaceholder: string
    confirmPasswordPlaceholder: string
    loginBtn: string
    registerBtn: string
    loginSuccess: string
    registerSuccess: string
    loginFailed: string
    registerFailed: string
    validation: {
      emailRequired: string
      emailInvalid: string
      passwordRequired: string
      passwordMin: string
      passwordPattern: string
      nicknameMax: string
      confirmPasswordRequired: string
      confirmPasswordMismatch: string
    }
  }
  dashboard: {
    quickStart: string
    quickStartDesc: string
    newAnalysis: string
    titleGenerate: string
    system: string
    architecture: string
    model: string
    rag: string
    greeting: string
    welcomeDesc: string
    quotaTitle: string
    quotaHint: string
    quickAnalyze: string
    quickAnalyzeTag: string
    titlePlaceholder: string
    bodyPlaceholder: string
    analyzeNow: string
    generateTitles: string
    trySample: string
    recentRecords: string
    refresh: string
    viewAll: string
    noRecords: string
    untitled: string
    status: string
    score: string
    createdAt: string
    tipsTitle: string
    titleDrawerTitle: string
    titleGoal: string
    titleResults: string
    copy: string
    copied: string
    validationRequired: string
    titleValidation: string
    analyzeSuccess: string
    analyzeFailed: string
    draftSaved: string
    titleApiPending: string
    sampleTitle: string
    sampleBody: string
    statuses: {
      pending: string
      processing: string
      completed: string
      failed: string
    }
    titleGoals: {
      high_ctr: string
      high_collect: string
      high_conversion: string
      anxiety: string
      offer: string
      info_gap: string
    }
    tips: [string, string, string]
  }
  analysis: {
    newTitle: string
    newDesc: string
    aiInsight: string
    mockAnalyzeDone: string
    reportTitle: string
    reportHint: string
    scenario: string
    persona: string
    title: string
    body: string
    submit: string
    scenarioDraft: string
    scenarioPublished: string
    scenarioCompetitor: string
  }
  titles: {
    title: string
    desc: string
    aiEngine: string
    inputSection: string
    goal: string
    count: string
    empty: string
    resultsTitle: string
    resultsPlaceholder: string
    aiGenerating: string
    stepEmotion: string
    stepCtr: string
    stepStructure: string
    stepOptimize: string
    ctrPredict: string
    viralIndex: string
    emotionValue: string
    highCtr: string
    applyToAnalysis: string
    applyToAnalysisSuccess: string
    apply: string
    applied: string
    clearLink: string
    linkedAnalysis: string
    loadAnalysisFailed: string
    generateFailed: string
    copyFailed: string
    openFullPage: string
    guideTitle: string
    guideNote: string
    guideTips: [string, string, string]
    typeTags: {
      anxiety: string
      info_gap: string
      comeback: string
      conflict: string
    }
    ctr: {
      low: string
      medium: string
      high: string
    }
  }
  history: {
    title: string
    subtitle: string
    keywordPlaceholder: string
    filterStatus: string
    filterScenario: string
    filterAll: string
    refresh: string
    total: string
    noRecords: string
    newAnalysis: string
    delete: string
    deleteConfirm: string
    deleteSuccess: string
    deleteFailed: string
    actions: string
    view: string
    persona: string
    status: string
    score: string
    createdAt: string
    untitled: string
    statuses: {
      pending: string
      processing: string
      completed: string
      failed: string
    }
  }
  settings: {
    title: string
    defaultPersona: string
  }
  persona: {
    agency: string
    mentor: string
    senior: string
  }
  workbench: {
    aiAnalyzing: string
    stepStructure: string
    stepEmotion: string
    stepCtr: string
    viralScoreTitle: string
    viralIndex: string
    ctrScore: string
    emotionScore: string
    collectScore: string
    conversionScore: string
    viralScoreEmpty: string
    recommendedTitles: string
    regenerate: string
    titlesEmpty: string
    hotPointsTitle: string
    userPsychology: string
    emotionType: string
    hotPointsEmpty: string
    hotTopicsTitle: string
    hotTopicsEmpty: string
    sensitiveTitle: string
    sensitiveFound: string
    sensitiveSafe: string
    highlightPreview: string
    topicApplied: string
    riskLevel: {
      low: string
      medium: string
      high: string
    }
    optimizationTitle: string
    currentIssues: string
    optimizationSuggestions: string
    optimizationEmpty: string
  }
}
