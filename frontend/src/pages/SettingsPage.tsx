import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Alert, Button, Form, Input, message, Select, Switch, Tabs } from 'antd';
import { adminService, ProviderConfigView } from '../services/rag';
import { useAuth } from '../hooks/useAuth';
import { NavBar } from '../components/NavBar';

interface EmbeddingForm {
  provider: string;
  baseUrl: string;
  model: string;
  apiKey: string;
  dimensions: number;
}

interface LlmForm {
  provider: string;
  baseUrl: string;
  model: string;
  apiKey: string;
  timeoutSeconds: number;
}

interface RetrievalForm {
  rerankerEnabled: boolean;
}

/** 系统管理页（SYS-03）：Embedding/LLM Provider 运行时配置 + Reranker 开关（仅系统管理员） */
export const SettingsPage = (): React.ReactNode => {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [config, setConfig] = useState<ProviderConfigView | null>(null);
  const [loading, setLoading] = useState(true);
  const [savingEmbedding, setSavingEmbedding] = useState(false);
  const [savingLlm, setSavingLlm] = useState(false);
  const [savingRetrieval, setSavingRetrieval] = useState(false);
  const [reindexWarning, setReindexWarning] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isAdmin = user?.roles?.some((role) => role.name === 'ROLE_ADMIN');

  useEffect(() => {
    if (user && !isAdmin) {
      navigate('/dashboard');
    }
  }, [user, isAdmin, navigate]);

  useEffect(() => {
    void (async () => {
      try {
        setConfig(await adminService.getProviders());
      } catch {
        setError('Provider 配置加载失败');
      } finally {
        setLoading(false);
      }
    })();
  }, []);

  const applyUpdate = async (
    payload: Parameters<typeof adminService.updateProviders>[0],
    setSaving: (v: boolean) => void,
    successText: string,
  ): Promise<void> => {
    setSaving(true);
    setError(null);
    try {
      const result = await adminService.updateProviders(payload);
      setConfig(result.config);
      setReindexWarning(result.reindexRequired);
      message.success(successText);
    } catch (err) {
      const apiMessage = (err as { response?: { data?: { message?: string } } }).response?.data?.message;
      message.error(apiMessage || '保存失败');
    } finally {
      setSaving(false);
    }
  };

  if (loading || !config) {
    return (
      <div className="min-h-screen bg-gray-100">
        <NavBar activePath="/settings" />
        <p className="p-8 text-gray-500">加载中…</p>
      </div>
    );
  }

  const embeddingTab = (
    <Form<EmbeddingForm>
      layout="vertical"
      key={config.embedding.provider + config.embedding.model}
      initialValues={{
        provider: config.embedding.provider,
        baseUrl: config.embedding.baseUrl,
        model: config.embedding.model,
        apiKey: '',
        dimensions: config.embedding.dimensions,
      }}
      onFinish={(values) => {
        const payload: Parameters<typeof adminService.updateProviders>[0] = {
          embedding: {
            provider: values.provider,
            baseUrl: values.baseUrl || undefined,
            model: values.model || undefined,
            apiKey: values.apiKey || undefined,
            dimensions: values.dimensions,
          },
        };
        void applyUpdate(payload, setSavingEmbedding, 'Embedding 配置已保存');
      }}
    >
      <Form.Item label="当前密钥（脱敏）">
        <Input value={config.embedding.apiKeyMasked || '（未配置）'} disabled />
      </Form.Item>
      <Form.Item name="provider" label="Provider" rules={[{ required: true }]}>
        <Select
          options={[
            { value: 'hashing', label: 'hashing（本地确定性，零依赖）' },
            { value: 'openai', label: 'openai（OpenAI 兼容端点）' },
          ]}
        />
      </Form.Item>
      <Form.Item name="baseUrl" label="Base URL">
        <Input placeholder="https://api.openai.com" />
      </Form.Item>
      <Form.Item name="model" label="模型">
        <Input placeholder="text-embedding-3-small" />
      </Form.Item>
      <Form.Item
        name="apiKey"
        label="API Key（留空保持不变）"
        tooltip="仅在需要更换密钥时填写；保存后只显示脱敏值"
      >
        <Input.Password placeholder="sk-..." autoComplete="new-password" />
      </Form.Item>
      <Form.Item
        name="dimensions"
        label="向量维度"
        extra="必须与 pgvector 的 vector(dim) 一致；修改后需对所有文档执行 reindex"
      >
        <Input type="number" min={64} />
      </Form.Item>
      <Button type="primary" htmlType="submit" loading={savingEmbedding}>
        保存 Embedding 配置
      </Button>
    </Form>
  );

  const llmTab = (
    <Form<LlmForm>
      layout="vertical"
      key={config.llm.provider + config.llm.model}
      initialValues={{
        provider: config.llm.provider,
        baseUrl: config.llm.baseUrl,
        model: config.llm.model,
        apiKey: '',
        timeoutSeconds: 60,
      }}
      onFinish={(values) => {
        const payload: Parameters<typeof adminService.updateProviders>[0] = {
          llm: {
            provider: values.provider,
            baseUrl: values.baseUrl || undefined,
            model: values.model || undefined,
            apiKey: values.apiKey || undefined,
            timeoutSeconds: values.timeoutSeconds,
          },
        };
        void applyUpdate(payload, setSavingLlm, 'LLM 配置已保存');
      }}
    >
      <Form.Item label="当前密钥（脱敏）">
        <Input value={config.llm.apiKeyMasked || '（未配置）'} disabled />
      </Form.Item>
      <Form.Item name="provider" label="Provider" rules={[{ required: true }]}>
        <Select
          options={[
            { value: 'extractive', label: 'extractive（离线兜底：片段内最佳句 + 拒答）' },
            { value: 'openai', label: 'openai（OpenAI 兼容端点）' },
          ]}
        />
      </Form.Item>
      <Form.Item name="baseUrl" label="Base URL">
        <Input placeholder="https://api.openai.com" />
      </Form.Item>
      <Form.Item name="model" label="模型">
        <Input placeholder="gpt-4o-mini" />
      </Form.Item>
      <Form.Item
        name="apiKey"
        label="API Key（留空保持不变）"
        tooltip="切换到 openai 且当前无密钥时必填"
      >
        <Input.Password placeholder="sk-..." autoComplete="new-password" />
      </Form.Item>
      <Form.Item name="timeoutSeconds" label="超时（秒）" rules={[{ required: true }]}>
        <Input type="number" min={5} max={300} />
      </Form.Item>
      <Button type="primary" htmlType="submit" loading={savingLlm}>
        保存 LLM 配置
      </Button>
    </Form>
  );

  const retrievalTab = (
    <Form<RetrievalForm>
      layout="vertical"
      initialValues={{ rerankerEnabled: config.rerankerEnabled }}
      key={String(config.rerankerEnabled)}
      onFinish={(values) => {
        // Reranker 开关经 providers 接口的运行时属性通道（热生效 + 持久化）
        void applyUpdate({ rerankerEnabled: values.rerankerEnabled }, setSavingRetrieval, '检索配置已保存');
      }}
    >
      <Form.Item
        name="rerankerEnabled"
        label="启用 Reranker 重排（RET-07）"
        valuePropName="checked"
        extra="开启后对 RRF 融合候选（默认 Top20）做词面重排再取 Top5；关闭时直接取 RRF Top5"
      >
        <Switch />
      </Form.Item>
      <Button type="primary" htmlType="submit" loading={savingRetrieval}>
        保存检索配置
      </Button>
    </Form>
  );

  return (
    <div className="min-h-screen bg-gray-100">
      <NavBar activePath="/settings" />
      <div className="max-w-4xl mx-auto px-4 py-8">
        <h1 className="text-2xl font-bold text-gray-800 mb-6">系统管理</h1>

        {!isAdmin && (
          <Alert
            className="mb-4"
            type="error"
            message="仅系统管理员可访问本页"
            description="你的账户没有 ROLE_ADMIN 角色。"
          />
        )}

        {error && <Alert className="mb-4" type="error" message={error} />}

        {reindexWarning && (
          <Alert
            className="mb-4"
            type="warning"
            showIcon
            message="Embedding 配置已变更"
            description="已入库文档的向量由旧模型生成，请到「文档管理」对各文档执行重新入库（reindex），否则检索质量会下降。"
          />
        )}

        <div className="bg-white rounded-xl shadow-md p-6">
          <Tabs
            items={[
              { key: 'embedding', label: 'Embedding', children: embeddingTab },
              { key: 'llm', label: 'LLM', children: llmTab },
              { key: 'retrieval', label: '检索', children: retrievalTab },
            ]}
          />
        </div>

        <p className="mt-4 text-xs text-gray-500">
          配置持久化在数据库 <code>app_settings</code> 表中，重启后自动恢复；密钥仅以脱敏形式展示，
          保存的新密钥不会回传。参考文档见{' '}
          <Link to="/documents" className="text-blue-600">文档管理</Link>。
        </p>
      </div>
    </div>
  );
};
