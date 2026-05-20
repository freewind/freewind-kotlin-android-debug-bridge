// 这里只描述 HTTP 协议 JSON，不描述内部 domain model。
// 后端字段若变，优先同步这里，再看 App.tsx 与 README。
export type HelpResponse = {
  appName: string
  screenName: string
  serverTime: string
  capabilities: string[]
  counts: {
    actionTargetCount: number
    logCount: number
    stateKeyCount: number
    snapshotNodeCount: number
  }
  endpoints: Array<{
    method: string
    path: string
    summary: string
    queryFields?: string[]
    bodyFields?: string[]
  }>
  examples: string[]
}

export type ActionRequest = {
  action: string
  targetId: string
  text?: string
  dx?: number
  dy?: number
  args?: Record<string, string>
  source?: string
}

export type ActionResponse = {
  accepted: boolean
  message: string
  action?: string
  targetId?: string
}

export type ActionCatalogResponse = {
  summary: {
    targetCount: number
    actionCount: number
  }
  items: Array<{
    targetId: string
    targetType?: string | null
    screen?: string | null
    actions: Array<{
      name: string
      args: string[]
      summary?: string | null
      example: ActionRequest
    }>
  }>
}

export type LogEntry = {
  seq: number
  time: string
  source: string
  level: string
  event: string
  targetId?: string
  summary?: string
  data: Record<string, string>
}

export type LogsResponse = {
  summary?: {
    total: number
    timeRange?: {
      from?: string | null
      to?: string | null
    }
    levelCounts: Record<string, number>
    sourceCounts: Record<string, number>
    eventCountsTop: Record<string, number>
  }
  items?: LogEntry[]
  nextAfterSeq?: number
}

export type LogsClearResponse = {
  // 新字段给 web / AI 用；旧字段保留兼容历史调用方。
  accepted: boolean
  message: string
  clearedCount: number
  ok?: boolean
  deletedCount?: number
}

export type StateResponse = {
  summary?: {
    appStateKeys: Array<{ key: string; sample: string }>
    targetStateTargets: string[]
  }
  appState?: Record<string, string>
  targetState?: Record<string, string>
}

export type SnapshotNode = {
  id?: string
  parentId?: string | null
  type?: string
  text?: string | null
  role?: string | null
  backgroundColor?: string | null
  contentColor?: string | null
  visible?: boolean
  enabled?: boolean
  clickable?: boolean
  value?: string | null
  extra?: Record<string, string>
  bounds?: {
    left: number
    top: number
    width: number
    height: number
  } | null
}

export type SnapshotPreviewNode = SnapshotNode & {
  id: string
  bounds: NonNullable<SnapshotNode['bounds']>
}

export type SnapshotResponse = {
  summary?: {
    screen: string
    nodeCount: number
    rootIds: string[]
    typeCounts: Record<string, number>
    clickableCount: number
  }
  fieldCatalog?: string[]
  examples?: string[]
  screen?: string
  nodes?: SnapshotNode[]
}
