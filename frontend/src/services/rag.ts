import api from '../utils/axios';
import { DocumentItem, EvalQuestionItem, EvalRunItem, RetrievalHit } from '../types';

export const documentService = {
  list: async (): Promise<DocumentItem[]> => {
    const response = await api.get<DocumentItem[]>('/v1/documents');
    return response.data;
  },

  upload: async (file: File): Promise<DocumentItem> => {
    const form = new FormData();
    form.append('file', file);
    const response = await api.post<DocumentItem>('/v1/documents', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    return response.data;
  },

  remove: async (id: number): Promise<void> => {
    await api.delete(`/v1/documents/${id}`);
  },

  reindex: async (id: number): Promise<DocumentItem> => {
    const response = await api.post<DocumentItem>(`/v1/documents/${id}/reindex`);
    return response.data;
  },

  download: async (id: number, name: string): Promise<void> => {
    const response = await api.get<Blob>(`/v1/documents/${id}/download`, { responseType: 'blob' });
    const url = URL.createObjectURL(response.data);
    const link = document.createElement('a');
    link.href = url;
    link.download = name;
    link.click();
    URL.revokeObjectURL(url);
  },
};

export const retrievalService = {
  search: async (query: string): Promise<RetrievalHit[]> => {
    const response = await api.post<{ hits: RetrievalHit[] }>('/v1/retrieval/search', { query });
    return response.data.hits;
  },
};

// ===== 系统管理（SYS-03，仅 ADMIN） =====

export interface ProviderConfigView {
  embedding: {
    provider: string;
    dimensions: number;
    model: string;
    baseUrl: string;
    apiKeyMasked: string;
  };
  llm: {
    provider: string;
    model: string;
    baseUrl: string;
    apiKeyMasked: string;
  };
  rerankerEnabled: boolean;
}

export interface ProviderConfigUpdate {
  embedding?: Partial<{
    provider: string;
    baseUrl: string;
    model: string;
    apiKey: string;
    dimensions: number;
  }>;
  llm?: Partial<{
    provider: string;
    baseUrl: string;
    model: string;
    apiKey: string;
    timeoutSeconds: number;
  }>;
  /** RET-07 检索重排开关 */
  rerankerEnabled?: boolean;
}

export const adminService = {
  getProviders: async (): Promise<ProviderConfigView> => {
    const response = await api.get<ProviderConfigView>('/v1/admin/providers');
    return response.data;
  },

  /** 返回 reindexRequired 标记与更新后的配置视图 */
  updateProviders: async (
    payload: ProviderConfigUpdate,
  ): Promise<{ reindexRequired: boolean; config: ProviderConfigView }> => {
    const response = await api.put<{ reindexRequired: boolean; config: ProviderConfigView }>(
      '/v1/admin/providers',
      payload,
    );
    return response.data;
  },
};

export const evalService = {
  listQuestions: async (): Promise<EvalQuestionItem[]> => {
    const response = await api.get<EvalQuestionItem[]>('/v1/eval/questions');
    return response.data;
  },

  addQuestion: async (payload: {
    question: string;
    expectedDocId?: number | null;
    negative?: boolean;
  }): Promise<EvalQuestionItem> => {
    const response = await api.post<EvalQuestionItem>('/v1/eval/questions', payload);
    return response.data;
  },

  removeQuestion: async (id: number): Promise<void> => {
    await api.delete(`/v1/eval/questions/${id}`);
  },

  run: async (): Promise<EvalRunItem['metrics']> => {
    const response = await api.post<EvalRunItem['metrics']>('/v1/eval/runs');
    return response.data;
  },

  runs: async (): Promise<EvalRunItem[]> => {
    const response = await api.get<EvalRunItem[]>('/v1/eval/runs');
    return response.data;
  },
};
