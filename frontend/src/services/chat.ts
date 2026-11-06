import api from '../utils/axios';
import { Citation } from '../types';
import { storage } from '../utils/storage';

const baseURL = import.meta.env.VITE_API_URL || '/api';

export interface StreamHandlers {
  onAnswerChunk: (text: string) => void;
  onCitations: (citations: Citation[]) => void;
  onDone: (info: { conversationId: number; rejected: boolean }) => void;
  onError: (message: string) => void;
}

/** 用 HttpOnly Cookie 静默刷新 AccessToken（fetch 场景无法走 axios 拦截器） */
const refreshAccessToken = async (): Promise<boolean> => {
  try {
    const response = await fetch(`${baseURL}/v1/auth/refresh-token`, {
      method: 'POST',
      credentials: 'include',
    });
    if (!response.ok) {
      return false;
    }
    const data = await response.json();
    storage.setAccessToken(data.accessToken);
    return true;
  } catch {
    return false;
  }
};

/**
 * SSE 流式问答（CHAT-01）：POST + ReadableStream 解析 text/event-stream。
 * 401 时静默刷新令牌并重试一次；异常时由调用方降级处理。
 */
export const streamAsk = async (question: string, handlers: StreamHandlers): Promise<void> => {
  const attempt = async (): Promise<Response> => {
    const token = storage.getAccessToken();
    return fetch(`${baseURL}/v1/chat/ask/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({ question }),
    });
  };

  let response: Response;
  try {
    response = await attempt();
    if (response.status === 401 && (await refreshAccessToken())) {
      response = await attempt();
    }
  } catch {
    handlers.onError('网络异常，请稍后重试');
    return;
  }

  if (!response.ok || !response.body) {
    handlers.onError(`问答失败（HTTP ${response.status}）`);
    return;
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';

  const dispatch = (raw: string): void => {
    // SSE 事件以空行分隔；data: 前缀为负载（服务端发送的是纯文本块，无需 JSON 解析）
    const dataLines = raw
      .split('\n')
      .filter((line) => line.startsWith('data:'))
      .map((line) => line.slice(5).replace(/^ /, ''));
    if (dataLines.length === 0) {
      return;
    }
    const payload = dataLines.join('\n');
    const eventName = raw.split('\n').find((line) => line.startsWith('event:'))?.slice(6).trim() ?? 'answer';
    if (eventName === 'answer') {
      handlers.onAnswerChunk(payload);
    } else if (eventName === 'citations') {
      try {
        handlers.onCitations(JSON.parse(payload));
      } catch {
        /* 引用解析失败不阻塞回答 */
      }
    } else if (eventName === 'done') {
      try {
        handlers.onDone(JSON.parse(payload));
      } catch {
        /* ignore */
      }
    }
  };

  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done) {
        break;
      }
      buffer += decoder.decode(value, { stream: true });
      let separator = buffer.indexOf('\n\n');
      while (separator >= 0) {
        dispatch(buffer.slice(0, separator));
        buffer = buffer.slice(separator + 2);
        separator = buffer.indexOf('\n\n');
      }
    }
    if (buffer.trim()) {
      dispatch(buffer);
    }
  } catch {
    handlers.onError('流式响应中断');
  }
};

export interface ChatHistoryItem {
  id: number;
  question: string;
  answer: string;
  citations: Citation[];
  rating: 'NONE' | 'USEFUL' | 'USELESS' | 'WRONG_CITATION';
  createdAt: string | null;
}

export const chatService = {
  feedback: async (conversationId: number, rating: string, comment?: string): Promise<void> => {
    await api.post('/v1/chat/feedback', { conversationId, rating, comment });
  },

  /** 问答历史（CHAT-06）：刷新页面后恢复历史会话 */
  history: async (page = 0, size = 20): Promise<ChatHistoryItem[]> => {
    const response = await api.get<ChatHistoryItem[]>('/v1/chat/history', {
      params: { page, size },
    });
    return response.data;
  },
};
