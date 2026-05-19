import {
  App as AntdApp,
  Button,
  Card,
  Col,
  Descriptions,
  Form,
  Input,
  InputNumber,
  Layout,
  Modal,
  Row,
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
import type { FC } from 'react'
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
const defaultSnapshotFields = 'id,parentId,type,text,role,visible,enabled,clickable,value,bounds'

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
          <Row gutter={[16, 16]}>
            <Col xs={24} md={6}>
              <Card>
                <Statistic
                  title="Action Targets"
                  value={help?.counts.actionTargetCount ?? 0}
                  suffix={actionSummary?.summary.actionCount ? `/${actionSummary.summary.actionCount}` : ''}
                />
              </Card>
            </Col>
            <Col xs={24} md={6}>
              <Card>
                <Statistic title="Logs" value={logsSummary?.summary.total ?? 0} />
              </Card>
            </Col>
            <Col xs={24} md={6}>
              <Card>
                <Statistic title="State Keys" value={help?.counts.stateKeyCount ?? 0} />
              </Card>
            </Col>
            <Col xs={24} md={6}>
              <Card>
                <Statistic title="Snapshot Nodes" value={help?.counts.snapshotNodeCount ?? 0} />
              </Card>
            </Col>
          </Row>

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
                      <Row gutter={[16, 16]}>
                        <Col xs={24} md={8}>
                          <Card size="small" title="Levels">
                            {renderCountTags(logsSummary?.summary.levelCounts)}
                          </Card>
                        </Col>
                        <Col xs={24} md={8}>
                          <Card size="small" title="Sources">
                            {renderCountTags(logsSummary?.summary.sourceCounts)}
                          </Card>
                        </Col>
                        <Col xs={24} md={8}>
                          <Card size="small" title="Top Events">
                            {renderCountTags(logsSummary?.summary.eventCountsTop)}
                          </Card>
                        </Col>
                      </Row>
                    </Card>
                    <Card title="Query">
                      <Form
                        form={logsForm}
                        layout="vertical"
                        initialValues={{ limit: 20 }}
                        onFinish={(values) => void queryLogs(values)}
                      >
                        <Row gutter={[16, 0]}>
                          <Col xs={24} md={6}>
                            <Form.Item label="event" name="event">
                              <Input />
                            </Form.Item>
                          </Col>
                          <Col xs={24} md={6}>
                            <Form.Item label="level" name="level">
                              <Select
                                allowClear
                                options={['debug', 'info', 'warn', 'error'].map((value) => ({
                                  label: value,
                                  value,
                                }))}
                              />
                            </Form.Item>
                          </Col>
                          <Col xs={24} md={6}>
                            <Form.Item label="source" name="source">
                              <Select
                                allowClear
                                options={['human', 'ai'].map((value) => ({
                                  label: value,
                                  value,
                                }))}
                              />
                            </Form.Item>
                          </Col>
                          <Col xs={24} md={6}>
                            <Form.Item label="limit" name="limit">
                              <InputNumber min={1} max={200} style={{ width: '100%' }} />
                            </Form.Item>
                          </Col>
                          <Col xs={24} md={6}>
                            <Form.Item label="targetId" name="targetId">
                              <Input />
                            </Form.Item>
                          </Col>
                          <Col xs={24} md={6}>
                            <Form.Item label="screen" name="screen">
                              <Input />
                            </Form.Item>
                          </Col>
                          <Col xs={24} md={6}>
                            <Form.Item label="keyword" name="keyword">
                              <Input />
                            </Form.Item>
                          </Col>
                          <Col xs={24} md={3}>
                            <Form.Item label="from" name="from">
                              <Input placeholder="20260519-223355" />
                            </Form.Item>
                          </Col>
                          <Col xs={24} md={3}>
                            <Form.Item label="to" name="to">
                              <Input placeholder="20260519-223355" />
                            </Form.Item>
                          </Col>
                        </Row>
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
                        <Row gutter={[16, 0]}>
                          <Col xs={24} md={8}>
                            <Form.Item label="keys(csv)" name="keys">
                              <Input placeholder="route,count,keyword" />
                            </Form.Item>
                          </Col>
                          <Col xs={24} md={8}>
                            <Form.Item label="targetId" name="targetId">
                              <Input />
                            </Form.Item>
                          </Col>
                          <Col xs={24} md={8}>
                            <Form.Item label="scope" name="scope">
                              <Select
                                options={['app', 'target', 'branch'].map((value) => ({
                                  label: value,
                                  value,
                                }))}
                              />
                            </Form.Item>
                          </Col>
                        </Row>
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
                        <Row gutter={[16, 0]}>
                          <Col xs={24} md={6}>
                            <Form.Item label="targetId" name="targetId">
                              <Input />
                            </Form.Item>
                          </Col>
                          <Col xs={24} md={6}>
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
                          </Col>
                          <Col xs={24} md={6}>
                            <Form.Item label="depth" name="depth">
                              <InputNumber min={1} style={{ width: '100%' }} />
                            </Form.Item>
                          </Col>
                          <Col xs={24} md={6}>
                            <Form.Item label="limit" name="limit">
                              <InputNumber min={1} max={300} style={{ width: '100%' }} />
                            </Form.Item>
                          </Col>
                          <Col xs={24} md={8}>
                            <Form.Item label="types(csv)" name="types">
                              <Input placeholder="Button,TextField" />
                            </Form.Item>
                          </Col>
                          <Col xs={24} md={8}>
                            <Form.Item label="textKeyword" name="textKeyword">
                              <Input />
                            </Form.Item>
                          </Col>
                          <Col xs={24} md={8}>
                            <Form.Item label="fields(csv)" name="fields">
                              <Input />
                            </Form.Item>
                          </Col>
                          <Col xs={24} md={4}>
                            <Form.Item label="visible" name="visible" valuePropName="checked">
                              <Switch />
                            </Form.Item>
                          </Col>
                          <Col xs={24} md={4}>
                            <Form.Item
                              label="clickable"
                              name="clickable"
                              valuePropName="checked"
                            >
                              <Switch />
                            </Form.Item>
                          </Col>
                          <Col xs={24} md={4}>
                            <Form.Item label="enabled" name="enabled" valuePropName="checked">
                              <Switch />
                            </Form.Item>
                          </Col>
                        </Row>
                        <Space>
                          <Button type="primary" htmlType="submit">
                            run query
                          </Button>
                          <Button onClick={() => snapshotForm.resetFields()}>reset</Button>
                        </Space>
                      </Form>
                    </Card>
                    <Card title="Query Result">
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
          <Form.Item
            label="targetId"
            name="targetId"
            rules={[{ required: true, message: 'targetId required' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            label="action"
            name="action"
            rules={[{ required: true, message: 'action required' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item label="text" name="text">
            <Input />
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
    <Typography.Paragraph
      style={{
        marginBottom: 0,
        whiteSpace: 'pre-wrap',
        wordBreak: 'break-word',
        fontFamily: 'monospace',
      }}
    >
      {JSON.stringify(value, null, 2)}
    </Typography.Paragraph>
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
