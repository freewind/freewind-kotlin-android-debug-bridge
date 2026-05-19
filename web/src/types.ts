export type JsonObject = Record<string, unknown>

export interface HelpEndpoint {
  method: string
  path: string
  summary: string
  queryFields?: string[]
  bodyFields?: string[]
}

export interface HelpResponse {
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
  endpoints: HelpEndpoint[]
  examples: string[]
}

export interface ActionExample {
  action: string
  targetId: string
}

export interface ActionSpec {
  name: string
  args: string[]
  summary?: string | null
  example: ActionExample
}

export interface ActionTarget {
  targetId: string
  targetType?: string | null
  screen?: string | null
  actions: ActionSpec[]
}

export interface ActionSummaryResponse {
  summary: {
    targetCount: number
    actionCount: number
  }
  items: ActionTarget[]
}

export interface ActionResult {
  accepted: boolean
  message: string
  action: string
  targetId?: string | null
}

export interface LogItem {
  seq: number
  time: string
  source: string
  level: string
  event: string
  targetId?: string | null
  summary?: string | null
  data: Record<string, string | null>
}

export interface LogsSummaryResponse {
  summary: {
    total: number
    timeRange: {
      from?: string | null
      to?: string | null
    }
    levelCounts: Record<string, number>
    sourceCounts: Record<string, number>
    eventCountsTop: Record<string, number>
  }
}

export interface LogsQueryResponse {
  items: LogItem[]
  nextAfterSeq: number
}

export interface StateSummaryResponse {
  summary: {
    appStateKeys: Array<{
      key: string
      sample: string
    }>
    targetStateTargets: string[]
  }
}

export interface StateQueryResponse {
  appState?: Record<string, string>
  targetState?: Record<string, string>
}

export interface SnapshotNode {
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

export interface SnapshotSummaryResponse {
  summary: {
    screen: string
    nodeCount: number
    rootIds: string[]
    typeCounts: Record<string, number>
    clickableCount: number
  }
  fieldCatalog: string[]
  examples: string[]
}

export interface SnapshotQueryResponse {
  screen: string
  nodes: SnapshotNode[]
}
