import {
  App as AntdApp,
  Button,
  Card,
  Descriptions,
  Flex,
  Form,
  Input,
  InputNumber,
  Layout,
  Modal,
  Select,
  Space,
  Statistic,
  Switch,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { FloatLabel, JsonPreviewer } from 'freewind-antd-components'
import type { FC, ReactNode } from 'react'
import { startTransition, useEffect, useState } from 'react'
import type {
  ActionResult,
  ActionSummaryResponse,
  ActionTarget,
  HelpResponse,
  LogItem,
  LogsQueryResponse,
  LogsSummaryResponse,
  SnapshotNode,
  SnapshotQueryResponse,
  SnapshotSummaryResponse,
  StateQueryResponse,
  StateSummaryResponse,
} from './types'

const { Header, Content } = Layout
const pollIntervalMs = 4000
const defaultSnapshotFields =
  'id,parentId,type,text,role,backgroundColor,contentColor,visible,enabled,clickable,value,extra,bounds'

interface ActionPayload {
  action: string
  targetId: string
  text?: string
  dx?: number
  dy?: number
}

interface LogsQueryForm {
  event?: string
  level?: string
  source?: string
  targetId?: string
  screen?: string
  keyword?: string
  from?: string
  to?: string
  limit?: number
}

interface StateQueryForm {
  keys?: string
  targetId?: string
  scope?: 'app' | 'target' | 'branch'
}

interface SnapshotQueryForm {
  targetId?: string
  scope?: 'all' | 'self' | 'parent' | 'ancestors' | 'branchToRoot' | 'children' | 'subtree'
  depth?: number
  types?: string
  textKeyword?: string
  fields?: string
  limit?: number
  visible?: boolean
  clickable?: boolean
  enabled?: boolean
}

interface PreviewTreeNode {
  children: PreviewTreeNode[]
  key: string
  node: SnapshotNode
}

const App: FC = () => {
  const { message } = AntdApp.useApp()
  const [help, setHelp] = useState<HelpResponse | null>(null)
  const [actionSummary, setActionSummary] = useState<ActionSummaryResponse | null>(null)
  const [logsSummary, setLogsSummary] = useState<LogsSummaryResponse | null>(null)
  const [stateSummary, setStateSummary] = useState<StateSummaryResponse | null>(null)
  const [snapshotSummary, setSnapshotSummary] = useState<SnapshotSummaryResponse | null>(null)
  const [logsQueryResult, setLogsQueryResult] = useState<LogsQueryResponse | null>(null)
  const [stateQueryResult, setStateQueryResult] = useState<StateQueryResponse | null>(null)
  const [snapshotQueryResult, setSnapshotQueryResult] = useState<SnapshotQueryResponse | null>(null)
  const [actionResult, setActionResult] = useState<ActionResult | null>(null)
  const [loading, setLoading] = useState(false)
  const [autoRefresh, setAutoRefresh] = useState(true)
  const [actionModalOpen, setActionModalOpen] = useState(false)
  const [actionModalTitle, setActionModalTitle] = useState('Run Action')
  const [logsForm] = Form.useForm<LogsQueryForm>()
  const [stateForm] = Form.useForm<StateQueryForm>()
  const [snapshotForm] = Form.useForm<SnapshotQueryForm>()
  const [actionForm] = Form.useForm<ActionPayload>()

  const refreshSummaries = async (silent = false) => {
    if (!silent) {
      setLoading(true)
    }
    try {
      const [nextHelp, nextActionSummary, nextLogsSummary, nextStateSummary, nextSnapshotSummary] =
        await Promise.all([
          requestJson<HelpResponse>('/help'),
          requestJson<ActionSummaryResponse>('/action'),
          requestJson<LogsSummaryResponse>('/logs'),
          requestJson<StateSummaryResponse>('/state'),
          requestJson<SnapshotSummaryResponse>('/snapshot'),
        ])
      startTransition(() => {
        setHelp(nextHelp)
        setActionSummary(nextActionSummary)
        setLogsSummary(nextLogsSummary)
        setStateSummary(nextStateSummary)
        setSnapshotSummary(nextSnapshotSummary)
      })
    } catch (error) {
      if (!silent) {
        message.error(toErrorMessage(error))
      }
    } finally {
      if (!silent) {
        setLoading(false)
      }
    }
  }

  useEffect(() => {
    void refreshSummaries()
  }, [])

  useEffect(() => {
    if (!autoRefresh) {
      return
    }
    const timer = window.setInterval(() => {
      void refreshSummaries(true)
    }, pollIntervalMs)
    return () => {
      window.clearInterval(timer)
    }
  }, [autoRefresh])

  const runAction = async (payload: ActionPayload, closeModal = false) => {
    try {
      const result = await requestJson<ActionResult>('/action', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(stripEmpty(payload)),
      })
      setActionResult(result)
      message.success(`${result.action} → ${result.message}`)
      if (closeModal) {
        setActionModalOpen(false)
      }
      await Promise.all([refreshSummaries(true), queryLogs(logsForm.getFieldsValue())])
    } catch (error) {
      message.error(toErrorMessage(error))
    }
  }

  const openActionModal = (payload?: Partial<ActionPayload>) => {
    actionForm.setFieldsValue({
      action: payload?.action ?? '',
      targetId: payload?.targetId ?? '',
      text: payload?.text,
      dx: payload?.dx,
      dy: payload?.dy,
    })
    setActionModalTitle(
      payload?.targetId && payload?.action
        ? `Run ${payload.action} on ${payload.targetId}`
        : 'Run Action',
    )
    setActionModalOpen(true)
  }

  const queryLogs = async (rawValues?: LogsQueryForm) => {
    const values = rawValues ?? logsForm.getFieldsValue()
    const query = buildSearch({
      event: values.event,
      level: values.level,
      source: values.source,
      targetId: values.targetId,
      screen: values.screen,
      keyword: values.keyword,
      from: values.from,
      to: values.to,
      limit: values.limit,
    })
    try {
      const result = await requestJson<LogsQueryResponse>(`/logs${query}`)
      setLogsQueryResult(result)
    } catch (error) {
      message.error(toErrorMessage(error))
    }
  }

  const clearLogs = async () => {
    try {
      const result = await requestJson<{ ok: boolean; deletedCount: number }>('/logs', {
        method: 'DELETE',
      })
      message.success(`deleted ${result.deletedCount} logs`)
      setLogsQueryResult(null)
      await refreshSummaries(true)
    } catch (error) {
      message.error(toErrorMessage(error))
    }
  }

  const queryState = async (rawValues?: StateQueryForm) => {
    const values = rawValues ?? stateForm.getFieldsValue()
    const query = buildSearch({
      keys: values.keys,
      targetId: values.targetId,
      scope: values.scope,
    })
    try {
      const result = await requestJson<StateQueryResponse>(`/state${query}`)
      setStateQueryResult(result)
    } catch (error) {
      message.error(toErrorMessage(error))
    }
  }

  const querySnapshot = async (rawValues?: SnapshotQueryForm) => {
    const values = rawValues ?? snapshotForm.getFieldsValue()
    const query = buildSearch({
      targetId: values.targetId,
      scope: values.scope,
      depth: values.depth,
      types: values.types,
      textKeyword: values.textKeyword,
      fields: values.fields,
      limit: values.limit,
      visible: values.visible,
      clickable: values.clickable,
      enabled: values.enabled,
    })
    try {
      const result = await requestJson<SnapshotQueryResponse>(`/snapshot${query}`)
      setSnapshotQueryResult(result)
    } catch (error) {
      message.error(toErrorMessage(error))
    }
  }

  useEffect(() => {
    void querySnapshot({
      fields: defaultSnapshotFields,
      limit: 120,
      scope: 'all',
    })
  }, [])

  const helpEndpointColumns: ColumnsType<HelpResponse['endpoints'][number]> = [
    { title: 'Method', dataIndex: 'method', width: 96, render: renderMethodTag },
    { title: 'Path', dataIndex: 'path', width: 140 },
    { title: 'Summary', dataIndex: 'summary' },
    {
      title: 'Query',
      dataIndex: 'queryFields',
      render: (value?: string[]) => renderTagList(value),
    },
    {
      title: 'Body',
      dataIndex: 'bodyFields',
      render: (value?: string[]) => renderTagList(value),
    },
  ]

  const actionColumns: ColumnsType<ActionTarget> = [
    { title: 'Target', dataIndex: 'targetId', width: 180 },
    { title: 'Type', dataIndex: 'targetType', width: 120, render: renderNullableText },
    { title: 'Screen', dataIndex: 'screen', width: 140, render: renderNullableText },
    {
      title: 'Actions',
      key: 'actions',
      render: (_, record) => (
        <Space wrap>
          {record.actions.map((item) => (
            <Button
              key={`${record.targetId}-${item.name}`}
              size="small"
              onClick={() =>
                void runAction(
                  {
                    action: item.name,
                    targetId: record.targetId,
                  },
                  false,
                )
              }
            >
              {item.name}
            </Button>
          ))}
          <Button size="small" onClick={() => openActionModal({ targetId: record.targetId })}>
            custom
          </Button>
        </Space>
      ),
    },
    {
      title: 'Args',
      key: 'args',
      render: (_, record) =>
        renderTagList(
          record.actions.flatMap((item) => item.args.map((arg) => `${item.name}:${arg}`)),
        ),
    },
  ]

  const logsColumns: ColumnsType<LogItem> = [
    { title: 'Seq', dataIndex: 'seq', width: 80 },
    { title: 'Time', dataIndex: 'time', width: 150 },
    { title: 'Source', dataIndex: 'source', width: 96, render: renderSourceTag },
    { title: 'Level', dataIndex: 'level', width: 96, render: renderLevelTag },
    { title: 'Event', dataIndex: 'event', width: 120 },
    { title: 'Target', dataIndex: 'targetId', width: 180, render: renderNullableText },
    { title: 'Summary', dataIndex: 'summary', width: 240, render: renderNullableText },
    {
      title: 'Data',
      dataIndex: 'data',
      render: (value: Record<string, string | null>) => <JsonBlock value={value} />,
    },
  ]

  const snapshotColumns: ColumnsType<SnapshotNode> = [
    { title: 'Id', dataIndex: 'id', width: 180, render: renderNullableText },
    { title: 'Parent', dataIndex: 'parentId', width: 160, render: renderNullableText },
    { title: 'Type', dataIndex: 'type', width: 120, render: renderNullableText },
    { title: 'Text', dataIndex: 'text', width: 180, render: renderNullableText },
    { title: 'Role', dataIndex: 'role', width: 120, render: renderNullableText },
    { title: 'Visible', dataIndex: 'visible', width: 90, render: renderBooleanTag },
    { title: 'Enabled', dataIndex: 'enabled', width: 90, render: renderBooleanTag },
    { title: 'Clickable', dataIndex: 'clickable', width: 96, render: renderBooleanTag },
    { title: 'Value', dataIndex: 'value', width: 140, render: renderNullableText },
    {
      title: 'Bounds',
      dataIndex: 'bounds',
      width: 240,
      render: (value: SnapshotNode['bounds']) =>
        value ? `${value.left}, ${value.top}, ${value.width}, ${value.height}` : '-',
    },
    {
      title: 'Extra',
      dataIndex: 'extra',
      render: (value?: Record<string, string>) => <JsonBlock value={value ?? {}} />,
    },
  ]

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Header
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 16,
        }}
      >
        <Space direction="vertical" size={0}>
          <Typography.Title level={4} style={{ color: '#fff', margin: 0 }}>
            {help?.appName ?? 'Debug Console'}
          </Typography.Title>
          <Typography.Text style={{ color: 'rgba(255,255,255,0.85)' }}>
            {help?.screenName ?? 'loading'}
          </Typography.Text>
        </Space>
        <Space>
          <Typography.Text style={{ color: '#fff' }}>auto refresh</Typography.Text>
          <Switch checked={autoRefresh} onChange={setAutoRefresh} />
          <Button loading={loading} onClick={() => void refreshSummaries()}>
            refresh
          </Button>
        </Space>
      </Header>
      <Content style={{ padding: 24 }}>
        <Space direction="vertical" size="large" style={{ width: '100%' }}>
          <WrapGrid>
            <GridItem mdBasis="calc(25% - 12px)">
              <Card>
                <Statistic
                  title="Action Targets"
                  value={help?.counts.actionTargetCount ?? 0}
                  suffix={actionSummary?.summary.actionCount ? `/${actionSummary.summary.actionCount}` : ''}
                />
              </Card>
            </GridItem>
            <GridItem mdBasis="calc(25% - 12px)">
              <Card>
                <Statistic title="Logs" value={logsSummary?.summary.total ?? 0} />
              </Card>
            </GridItem>
            <GridItem mdBasis="calc(25% - 12px)">
              <Card>
                <Statistic title="State Keys" value={help?.counts.stateKeyCount ?? 0} />
              </Card>
            </GridItem>
            <GridItem mdBasis="calc(25% - 12px)">
              <Card>
                <Statistic title="Snapshot Nodes" value={help?.counts.snapshotNodeCount ?? 0} />
              </Card>
            </GridItem>
          </WrapGrid>

          <Card>
            <Descriptions size="small" column={{ xs: 1, md: 3 }}>
              <Descriptions.Item label="Server Time">
                {help?.serverTime ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="Capabilities">
                {renderTagList(help?.capabilities)}
              </Descriptions.Item>
              <Descriptions.Item label="Logs Range">
                {logsSummary?.summary.timeRange.from ?? '-'} ~ {logsSummary?.summary.timeRange.to ?? '-'}
              </Descriptions.Item>
            </Descriptions>
          </Card>

          <Tabs
            items={[
              {
                key: 'action',
                label: 'Action',
                children: (
                  <Space direction="vertical" size="large" style={{ width: '100%' }}>
                    <Card
                      title="Dynamic Actions"
                      extra={<Button onClick={() => openActionModal()}>manual</Button>}
                    >
                      <Table
                        rowKey="targetId"
                        columns={actionColumns}
                        dataSource={actionSummary?.items ?? []}
                        pagination={false}
                        scroll={{ x: 900 }}
                      />
                    </Card>
                    <Card title="Last Result">
                      <JsonBlock value={actionResult ?? {}} />
                    </Card>
                  </Space>
                ),
              },
              {
                key: 'logs',
                label: 'Logs',
                children: (
                  <Space direction="vertical" size="large" style={{ width: '100%' }}>
                    <Card
                      title="Summary"
                      extra={
                        <Space>
                          <Button onClick={() => void queryLogs()}>query</Button>
                          <Button danger onClick={() => void clearLogs()}>
                            clear
                          </Button>
                        </Space>
                      }
                    >
                      <WrapGrid>
                        <GridItem mdBasis="calc(33.333% - 11px)">
                          <Card size="small" title="Levels">
                            {renderCountTags(logsSummary?.summary.levelCounts)}
                          </Card>
                        </GridItem>
                        <GridItem mdBasis="calc(33.333% - 11px)">
                          <Card size="small" title="Sources">
                            {renderCountTags(logsSummary?.summary.sourceCounts)}
                          </Card>
                        </GridItem>
                        <GridItem mdBasis="calc(33.333% - 11px)">
                          <Card size="small" title="Top Events">
                            {renderCountTags(logsSummary?.summary.eventCountsTop)}
                          </Card>
                        </GridItem>
                      </WrapGrid>
                    </Card>
                    <Card title="Query">
                      <Form
                        form={logsForm}
                        layout="vertical"
                        initialValues={{ limit: 20 }}
                        onFinish={(values) => void queryLogs(values)}
                      >
                        <WrapGrid>
                          <GridItem mdBasis="calc(25% - 12px)">
                            <Form.Item name="event">
                              <FloatLabel label="event">
                                <Input />
                              </FloatLabel>
                            </Form.Item>
                          </GridItem>
                          <GridItem mdBasis="calc(25% - 12px)">
                            <Form.Item label="level" name="level">
                              <Select
                                allowClear
                                options={['debug', 'info', 'warn', 'error'].map((value) => ({
                                  label: value,
                                  value,
                                }))}
                              />
                            </Form.Item>
                          </GridItem>
                          <GridItem mdBasis="calc(25% - 12px)">
                            <Form.Item label="source" name="source">
                              <Select
                                allowClear
                                options={['human', 'ai'].map((value) => ({
                                  label: value,
                                  value,
                                }))}
                              />
                            </Form.Item>
                          </GridItem>
                          <GridItem mdBasis="calc(25% - 12px)">
                            <Form.Item label="limit" name="limit">
                              <InputNumber min={1} max={200} style={{ width: '100%' }} />
                            </Form.Item>
                          </GridItem>
                          <GridItem mdBasis="calc(25% - 12px)">
                            <Form.Item name="targetId">
                              <FloatLabel label="targetId">
                                <Input />
                              </FloatLabel>
                            </Form.Item>
                          </GridItem>
                          <GridItem mdBasis="calc(25% - 12px)">
                            <Form.Item name="screen">
                              <FloatLabel label="screen">
                                <Input />
                              </FloatLabel>
                            </Form.Item>
                          </GridItem>
                          <GridItem mdBasis="calc(25% - 12px)">
                            <Form.Item name="keyword">
                              <FloatLabel label="keyword">
                                <Input />
                              </FloatLabel>
                            </Form.Item>
                          </GridItem>
                          <GridItem mdBasis="calc(12.5% - 14px)">
                            <Form.Item name="from">
                              <FloatLabel label="from">
                                <Input placeholder="20260519-223355" />
                              </FloatLabel>
                            </Form.Item>
                          </GridItem>
                          <GridItem mdBasis="calc(12.5% - 14px)">
                            <Form.Item name="to">
                              <FloatLabel label="to">
                                <Input placeholder="20260519-223355" />
                              </FloatLabel>
                            </Form.Item>
                          </GridItem>
                        </WrapGrid>
                        <Space>
                          <Button type="primary" htmlType="submit">
                            run query
                          </Button>
                          <Button onClick={() => logsForm.resetFields()}>reset</Button>
                        </Space>
                      </Form>
                    </Card>
                    <Card title="Query Result">
                      <Table
                        rowKey="seq"
                        columns={logsColumns}
                        dataSource={logsQueryResult?.items ?? []}
                        pagination={false}
                        scroll={{ x: 1400 }}
                      />
                    </Card>
                  </Space>
                ),
              },
              {
                key: 'state',
                label: 'State',
                children: (
                  <Space direction="vertical" size="large" style={{ width: '100%' }}>
                    <Card title="Summary">
                      <Descriptions size="small" column={{ xs: 1, md: 2 }}>
                        <Descriptions.Item label="App State Keys">
                          {renderTagList(
                            stateSummary?.summary.appStateKeys.map(
                              (item) => `${item.key}=${item.sample}`,
                            ),
                          )}
                        </Descriptions.Item>
                        <Descriptions.Item label="Target States">
                          {renderTagList(stateSummary?.summary.targetStateTargets)}
                        </Descriptions.Item>
                      </Descriptions>
                    </Card>
                    <Card title="Query">
                      <Form
                        form={stateForm}
                        layout="vertical"
                        initialValues={{ scope: 'app' }}
                        onFinish={(values) => void queryState(values)}
                      >
                        <WrapGrid>
                          <GridItem mdBasis="calc(33.333% - 11px)">
                            <Form.Item name="keys">
                              <FloatLabel label="keys(csv)">
                                <Input placeholder="route,count,keyword" />
                              </FloatLabel>
                            </Form.Item>
                          </GridItem>
                          <GridItem mdBasis="calc(33.333% - 11px)">
                            <Form.Item name="targetId">
                              <FloatLabel label="targetId">
                                <Input />
                              </FloatLabel>
                            </Form.Item>
                          </GridItem>
                          <GridItem mdBasis="calc(33.333% - 11px)">
                            <Form.Item label="scope" name="scope">
                              <Select
                                options={['app', 'target', 'branch'].map((value) => ({
                                  label: value,
                                  value,
                                }))}
                              />
                            </Form.Item>
                          </GridItem>
                        </WrapGrid>
                        <Space>
                          <Button type="primary" htmlType="submit">
                            run query
                          </Button>
                          <Button onClick={() => stateForm.resetFields()}>reset</Button>
                        </Space>
                      </Form>
                    </Card>
                    <Card title="Query Result">
                      <JsonBlock value={stateQueryResult ?? {}} />
                    </Card>
                  </Space>
                ),
              },
              {
                key: 'snapshot',
                label: 'Snapshot',
                children: (
                  <Space direction="vertical" size="large" style={{ width: '100%' }}>
                    <Card title="Summary">
                      <Descriptions size="small" column={{ xs: 1, md: 2 }}>
                        <Descriptions.Item label="Roots">
                          {renderTagList(snapshotSummary?.summary.rootIds)}
                        </Descriptions.Item>
                        <Descriptions.Item label="Types">
                          {renderCountTags(snapshotSummary?.summary.typeCounts)}
                        </Descriptions.Item>
                        <Descriptions.Item label="Fields">
                          {renderTagList(snapshotSummary?.fieldCatalog)}
                        </Descriptions.Item>
                        <Descriptions.Item label="Examples">
                          {renderTagList(snapshotSummary?.examples)}
                        </Descriptions.Item>
                      </Descriptions>
                    </Card>
                    <Card title="Query">
                      <Form
                        form={snapshotForm}
                        layout="vertical"
                        initialValues={{
                          scope: 'all',
                          fields: defaultSnapshotFields,
                          limit: 30,
                        }}
                        onFinish={(values) => void querySnapshot(values)}
                      >
                        <WrapGrid>
                          <GridItem mdBasis="calc(25% - 12px)">
                            <Form.Item name="targetId">
                              <FloatLabel label="targetId">
                                <Input />
                              </FloatLabel>
                            </Form.Item>
                          </GridItem>
                          <GridItem mdBasis="calc(25% - 12px)">
                            <Form.Item label="scope" name="scope">
                              <Select
                                options={[
                                  'all',
                                  'self',
                                  'parent',
                                  'ancestors',
                                  'branchToRoot',
                                  'children',
                                  'subtree',
                                ].map((value) => ({ label: value, value }))}
                              />
                            </Form.Item>
                          </GridItem>
                          <GridItem mdBasis="calc(25% - 12px)">
                            <Form.Item label="depth" name="depth">
                              <InputNumber min={1} style={{ width: '100%' }} />
                            </Form.Item>
                          </GridItem>
                          <GridItem mdBasis="calc(25% - 12px)">
                            <Form.Item label="limit" name="limit">
                              <InputNumber min={1} max={300} style={{ width: '100%' }} />
                            </Form.Item>
                          </GridItem>
                          <GridItem mdBasis="calc(33.333% - 11px)">
                            <Form.Item name="types">
                              <FloatLabel label="types(csv)">
                                <Input placeholder="Button,TextField" />
                              </FloatLabel>
                            </Form.Item>
                          </GridItem>
                          <GridItem mdBasis="calc(33.333% - 11px)">
                            <Form.Item name="textKeyword">
                              <FloatLabel label="textKeyword">
                                <Input />
                              </FloatLabel>
                            </Form.Item>
                          </GridItem>
                          <GridItem mdBasis="calc(33.333% - 11px)">
                            <Form.Item name="fields">
                              <FloatLabel label="fields(csv)">
                                <Input />
                              </FloatLabel>
                            </Form.Item>
                          </GridItem>
                          <GridItem mdBasis="calc(16.666% - 14px)">
                            <Form.Item label="visible" name="visible" valuePropName="checked">
                              <Switch />
                            </Form.Item>
                          </GridItem>
                          <GridItem mdBasis="calc(16.666% - 14px)">
                            <Form.Item
                              label="clickable"
                              name="clickable"
                              valuePropName="checked"
                            >
                              <Switch />
                            </Form.Item>
                          </GridItem>
                          <GridItem mdBasis="calc(16.666% - 14px)">
                            <Form.Item label="enabled" name="enabled" valuePropName="checked">
                              <Switch />
                            </Form.Item>
                          </GridItem>
                        </WrapGrid>
                        <Space>
                          <Button type="primary" htmlType="submit">
                            run query
                          </Button>
                          <Button onClick={() => snapshotForm.resetFields()}>reset</Button>
                        </Space>
                      </Form>
                    </Card>
                    <Card title="Query Result">
                      <SnapshotPreview
                        actionSummary={actionSummary}
                        focusNode={(targetId) => {
                          snapshotForm.setFieldsValue({
                            fields: defaultSnapshotFields,
                            scope: 'self',
                            targetId,
                          })
                          stateForm.setFieldsValue({
                            scope: 'target',
                            targetId,
                          })
                        }}
                        nodes={snapshotQueryResult?.nodes ?? []}
                        onAction={(payload) => openActionModal(payload)}
                      />
                    </Card>
                    <Card title="Query Result Table">
                      <Table
                        rowKey={(record, index) => record.id ?? String(index)}
                        columns={snapshotColumns}
                        dataSource={snapshotQueryResult?.nodes ?? []}
                        pagination={false}
                        scroll={{ x: 1600 }}
                      />
                    </Card>
                  </Space>
                ),
              },
              {
                key: 'help',
                label: 'Help',
                children: (
                  <Space direction="vertical" size="large" style={{ width: '100%' }}>
                    <Card title="Endpoints">
                      <Table
                        rowKey={(record) => `${record.method}-${record.path}`}
                        columns={helpEndpointColumns}
                        dataSource={help?.endpoints ?? []}
                        pagination={false}
                        scroll={{ x: 900 }}
                      />
                    </Card>
                    <Card title="Raw Help">
                      <JsonBlock value={help ?? {}} />
                    </Card>
                  </Space>
                ),
              },
            ]}
          />
        </Space>
      </Content>

      <Modal
        title={actionModalTitle}
        open={actionModalOpen}
        onCancel={() => setActionModalOpen(false)}
        onOk={() => void actionForm.submit()}
      >
        <Form form={actionForm} layout="vertical" onFinish={(values) => void runAction(values, true)}>
          <Form.Item name="targetId" rules={[{ required: true, message: 'targetId required' }]}>
            <FloatLabel label="targetId">
              <Input />
            </FloatLabel>
          </Form.Item>
          <Form.Item name="action" rules={[{ required: true, message: 'action required' }]}>
            <FloatLabel label="action">
              <Input />
            </FloatLabel>
          </Form.Item>
          <Form.Item name="text">
            <FloatLabel label="text">
              <Input />
            </FloatLabel>
          </Form.Item>
          <Form.Item label="dx" name="dx">
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="dy" name="dy">
            <InputNumber style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </Layout>
  )
}

const JsonBlock: FC<{ value: unknown }> = ({ value }) => {
  return (
    <JsonPreviewer
      maxHeight={360}
      style={{ marginBottom: 0 }}
      value={value}
    />
  )
}

const SnapshotPreview: FC<{
  actionSummary: ActionSummaryResponse | null
  focusNode: (targetId: string) => void
  nodes: SnapshotNode[]
  onAction: (payload: Partial<ActionPayload>) => void
}> = ({ actionSummary, focusNode, nodes, onAction }) => {
  if (nodes.length === 0) {
    return (
      <Typography.Text type="secondary">
        no snapshot data yet
      </Typography.Text>
    )
  }

  const boundedNodes = nodes.filter((node) => node.bounds)
  const useBoundsPreview = boundedNodes.length >= Math.max(2, Math.ceil(nodes.length / 2))
  const actionMap = new Map(actionSummary?.items.map((item) => [item.targetId, item]) ?? [])

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Typography.Text type="secondary">
        {useBoundsPreview
          ? 'bounds preview'
          : 'tree preview'}
      </Typography.Text>
      {useBoundsPreview ? (
        <SnapshotBoundsPreview
          actionMap={actionMap}
          focusNode={focusNode}
          nodes={boundedNodes}
          onAction={onAction}
        />
      ) : (
        <SnapshotTreePreview
          actionMap={actionMap}
          focusNode={focusNode}
          nodes={nodes}
          onAction={onAction}
        />
      )}
    </Space>
  )
}

const SnapshotBoundsPreview: FC<{
  actionMap: Map<string, ActionTarget>
  focusNode: (targetId: string) => void
  nodes: SnapshotNode[]
  onAction: (payload: Partial<ActionPayload>) => void
}> = ({ actionMap, focusNode, nodes, onAction }) => {
  const lefts = nodes.map((node) => node.bounds?.left ?? 0)
  const tops = nodes.map((node) => node.bounds?.top ?? 0)
  const rights = nodes.map((node) => (node.bounds?.left ?? 0) + (node.bounds?.width ?? 0))
  const bottoms = nodes.map((node) => (node.bounds?.top ?? 0) + (node.bounds?.height ?? 0))
  const minLeft = Math.min(...lefts)
  const minTop = Math.min(...tops)
  const sourceWidth = Math.max(...rights) - minLeft
  const sourceHeight = Math.max(...bottoms) - minTop
  const maxPreviewWidth = 760
  const scale = Math.min(1, maxPreviewWidth / Math.max(sourceWidth, 1))

  return (
    <div style={{ overflowX: 'auto', paddingBottom: 8 }}>
      <div
        style={{
          background: '#f5f5f5',
          border: '1px solid #d9d9d9',
          borderRadius: 12,
          height: Math.max(sourceHeight * scale, 240),
          position: 'relative',
          width: Math.max(sourceWidth * scale, 320),
        }}
      >
        {nodes.map((node, index) => {
          const bounds = node.bounds
          if (!bounds) {
            return null
          }
          const actionTarget = node.id ? actionMap.get(node.id) : undefined
          return (
            <button
              key={node.id ?? String(index)}
              onClick={() => {
                if (node.id) {
                  focusNode(node.id)
                }
                if (node.id && actionTarget) {
                  onAction({
                    action: actionTarget.actions[0]?.name,
                    targetId: node.id,
                  })
                }
              }}
              style={{
                alignItems: 'flex-start',
                background: node.backgroundColor || previewBackgroundColor(node.type),
                border: node.clickable ? '2px solid #1677ff' : '1px solid #8c8c8c',
                borderRadius: 8,
                color: node.contentColor || '#111',
                cursor: node.id ? 'pointer' : 'default',
                display: 'flex',
                flexDirection: 'column',
                gap: 4,
                left: (bounds.left - minLeft) * scale,
                minHeight: Math.max(bounds.height * scale, 22),
                overflow: 'hidden',
                padding: 6,
                position: 'absolute',
                textAlign: 'left',
                top: (bounds.top - minTop) * scale,
                width: Math.max(bounds.width * scale, 64),
              }}
              type="button"
            >
              <span style={{ fontSize: 10, opacity: 0.7 }}>
                {node.type}
              </span>
              <span style={{ fontSize: 12, fontWeight: 600, lineHeight: 1.2 }}>
                {previewNodeTitle(node)}
              </span>
            </button>
          )
        })}
      </div>
    </div>
  )
}

const SnapshotTreePreview: FC<{
  actionMap: Map<string, ActionTarget>
  focusNode: (targetId: string) => void
  nodes: SnapshotNode[]
  onAction: (payload: Partial<ActionPayload>) => void
}> = ({ actionMap, focusNode, nodes, onAction }) => {
  const roots = buildPreviewTree(nodes)
  return (
    <Space direction="vertical" size="small" style={{ width: '100%' }}>
      {roots.map((item) => (
        <SnapshotTreeNodeView
          actionMap={actionMap}
          focusNode={focusNode}
          key={item.key}
          node={item}
          onAction={onAction}
        />
      ))}
    </Space>
  )
}

const SnapshotTreeNodeView: FC<{
  actionMap: Map<string, ActionTarget>
  focusNode: (targetId: string) => void
  node: PreviewTreeNode
  onAction: (payload: Partial<ActionPayload>) => void
}> = ({ actionMap, focusNode, node, onAction }) => {
  const { children, key, node: current } = node
  const actionTarget = current.id ? actionMap.get(current.id) : undefined
  const currentId = current.id

  return (
    <div
      key={key}
      style={{
        border: '1px solid #d9d9d9',
        borderLeft: current.clickable ? '3px solid #1677ff' : '1px solid #d9d9d9',
        borderRadius: 10,
        padding: 12,
      }}
    >
      <Flex align="center" gap={8} justify="space-between" wrap>
        <Space size={6} wrap>
          <Tag>{current.type ?? 'Unknown'}</Tag>
          {current.role ? <Tag color="cyan">{current.role}</Tag> : null}
          {current.id ? <Tag color="geekblue">{current.id}</Tag> : null}
          <Typography.Text strong>{previewNodeTitle(current)}</Typography.Text>
        </Space>
        <Space size={6} wrap>
          {currentId ? (
            <Button size="small" onClick={() => focusNode(currentId)}>
              focus
            </Button>
          ) : null}
          {currentId && actionTarget ? (
            <Button
              size="small"
              type="primary"
              onClick={() =>
                onAction({
                  action: actionTarget.actions[0]?.name,
                  targetId: currentId,
                })
              }
            >
              {actionTarget.actions[0]?.name ?? 'action'}
            </Button>
          ) : null}
        </Space>
      </Flex>
      {children.length > 0 ? (
        <div style={{ marginLeft: 16, marginTop: 12 }}>
          <Space direction="vertical" size="small" style={{ width: '100%' }}>
            {children.map((child) => (
              <SnapshotTreeNodeView
                actionMap={actionMap}
                focusNode={focusNode}
                key={child.key}
                node={child}
                onAction={onAction}
              />
            ))}
          </Space>
        </div>
      ) : null}
    </div>
  )
}

const renderTagList = (values?: Array<string | null | undefined>) => {
  if (!values || values.length === 0) {
    return '-'
  }
  return (
    <Space wrap>
      {values.filter(Boolean).map((value) => (
        <Tag key={value}>{value}</Tag>
      ))}
    </Space>
  )
}

const renderCountTags = (value?: Record<string, number>) => {
  if (!value || Object.keys(value).length === 0) {
    return '-'
  }
  return (
    <Space wrap>
      {Object.entries(value).map(([key, count]) => (
        <Tag key={key}>{`${key}:${count}`}</Tag>
      ))}
    </Space>
  )
}

const renderNullableText = (value?: string | null) => value || '-'

const renderMethodTag = (value: string) => {
  const color = value === 'POST' ? 'purple' : value === 'DELETE' ? 'red' : 'blue'
  return <Tag color={color}>{value}</Tag>
}

const renderLevelTag = (value: string) => {
  const colorByLevel: Record<string, string> = {
    debug: 'default',
    info: 'blue',
    warn: 'orange',
    error: 'red',
  }
  return <Tag color={colorByLevel[value] ?? 'default'}>{value}</Tag>
}

const renderSourceTag = (value: string) => {
  const colorBySource: Record<string, string> = {
    human: 'green',
    ai: 'geekblue',
  }
  return <Tag color={colorBySource[value] ?? 'default'}>{value}</Tag>
}

const renderBooleanTag = (value?: boolean) => {
  if (value == null) {
    return '-'
  }
  return <Tag color={value ? 'green' : 'default'}>{String(value)}</Tag>
}

const buildSearch = (value: Record<string, string | number | boolean | undefined>) => {
  const params = new URLSearchParams()
  Object.entries(value).forEach(([key, raw]) => {
    if (raw == null) {
      return
    }
    const normalized = String(raw).trim()
    if (!normalized) {
      return
    }
    params.set(key, normalized)
  })
  const query = params.toString()
  return query ? `?${query}` : ''
}

const stripEmpty = <T extends object>(value: T): T => {
  return Object.fromEntries(
    Object.entries(value).filter(([, item]) => item != null && String(item).trim() !== ''),
  ) as T
}

const requestJson = async <T,>(path: string, init?: RequestInit): Promise<T> => {
  const response = await fetch(path, init)
  const text = await response.text()
  if (!response.ok) {
    throw new Error(`HTTP ${response.status} ${text}`)
  }
  return JSON.parse(text) as T
}

const toErrorMessage = (error: unknown) => {
  if (error instanceof Error) {
    return error.message
  }
  return String(error)
}

export default App

const buildPreviewTree = (nodes: SnapshotNode[]): PreviewTreeNode[] => {
  const items = nodes.map((node, index) => ({
    children: [] as PreviewTreeNode[],
    key: node.id ?? `node-${index}`,
    node,
  }))
  const byId = new Map(items.map((item) => [item.node.id, item] as const))
  const roots: PreviewTreeNode[] = []

  items.forEach((item) => {
    const parentId = item.node.parentId
    if (!parentId) {
      roots.push(item)
      return
    }
    const parent = byId.get(parentId)
    if (!parent) {
      roots.push(item)
      return
    }
    parent.children.push(item)
  })

  return roots
}

const previewNodeTitle = (node: SnapshotNode) => {
  return (
    node.text ||
    node.value ||
    node.extra?.label ||
    node.id ||
    node.type ||
    'node'
  )
}

const previewBackgroundColor = (type?: string) => {
  switch (type) {
    case 'Button':
      return '#e6f4ff'
    case 'TextField':
      return '#ffffff'
    case 'Switch':
      return '#f6ffed'
    case 'Card':
      return '#fafafa'
    default:
      return '#f5f5f5'
  }
}

const WrapGrid: FC<{ children: ReactNode }> = ({ children }) => {
  return (
    <Flex
      gap={16}
      style={{ width: '100%' }}
      wrap
    >
      {children}
    </Flex>
  )
}

const GridItem: FC<{
  children: ReactNode
  mdBasis: string
}> = ({ children, mdBasis }) => {
  return (
    <div
      style={{
        flex: `1 1 ${mdBasis}`,
        minWidth: 220,
      }}
    >
      {children}
    </div>
  )
}
