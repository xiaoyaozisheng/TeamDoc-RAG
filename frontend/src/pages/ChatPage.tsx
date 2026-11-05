import { useCallback, useEffect, useRef, useState } from 'react';
import { Citation } from '../types';
import { streamAsk, chatService } from '../services/chat';
import { NavBar } from '../components/NavBar';

interface ChatEntry {
  question: string;
  answer: string;
  citations: Citation[];
  rejected: boolean;
  conversationId: number | null;
}

export const ChatPage = (): React.ReactNode => {
  const [entries, setEntries] = useState<ChatEntry[]>([]);
  const [question, setQuestion] = useState('');
  const [asking, setAsking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [entries]);

  const ask = useCallback(async () => {
    const trimmed = question.trim();
    if (!trimmed || asking) {
      return;
    }
    setAsking(true);
    setError(null);
    setQuestion('');

    setEntries((prev) => [
      ...prev,
      { question: trimmed, answer: '', citations: [], rejected: false, conversationId: null },
    ]);

    let answerText = '';
    const updateLast = (patch: Partial<ChatEntry>): void => {
      setEntries((prev) => {
        const next = [...prev];
        next[next.length - 1] = { ...next[next.length - 1], ...patch };
        return next;
      });
    };

    try {
      await streamAsk(trimmed, {
        onAnswerChunk: (text) => {
          answerText += text;
          updateLast({ answer: answerText });
        },
        onCitations: (citations) => updateLast({ citations }),
        onDone: (info) => updateLast({ conversationId: info.conversationId, rejected: info.rejected }),
        onError: (message) => setError(message),
      });
    } finally {
      setAsking(false);
    }
  }, [question, asking]);

  const feedback = async (conversationId: number | null, rating: string): Promise<void> => {
    if (conversationId == null) {
      return;
    }
    try {
      await chatService.feedback(conversationId, rating);
      setError(null);
    } catch {
      setError('反馈提交失败');
    }
  };

  return (
    <div className="min-h-screen bg-gray-100">
      <NavBar activePath="/chat" />
      <div className="max-w-4xl mx-auto px-4 py-8 flex flex-col" style={{ minHeight: 'calc(100vh - 4rem)' }}>
      <h1 className="text-2xl font-bold text-gray-800 mb-4">知识库问答</h1>

      <div className="flex-1 space-y-4 overflow-y-auto mb-4">
        {entries.length === 0 && (
          <div className="bg-white rounded-xl shadow p-6 text-gray-500 text-center">
            输入问题，系统将基于本团队文档回答并标注引用来源；资料不足时会明确拒答。
          </div>
        )}
        {entries.map((entry, idx) => (
          <div key={idx} className="space-y-2">
            <div className="flex justify-end">
              <div className="bg-blue-600 text-white rounded-xl px-4 py-2 max-w-[70%] whitespace-pre-wrap">
                {entry.question}
              </div>
            </div>
            <div className="flex justify-start">
              <div
                className={`rounded-xl px-4 py-3 max-w-[85%] whitespace-pre-wrap ${
                  entry.rejected ? 'bg-amber-50 text-amber-800' : 'bg-white shadow text-gray-800'
                }`}
              >
                {entry.answer || (asking ? '思考中…' : '')}
                {entry.citations.length > 0 && (
                  <div className="mt-3 pt-3 border-t border-gray-100">
                    <p className="text-xs text-gray-400 mb-1">引用来源：</p>
                    <div className="flex flex-wrap gap-2">
                      {entry.citations.map((citation) => (
                        <a
                          key={citation.index}
                          href={`/documents?doc=${citation.documentId}`}
                          className="text-xs px-2 py-1 bg-indigo-50 text-indigo-700 rounded-full hover:bg-indigo-100"
                        >
                          [{citation.index}] {citation.documentName} · 第{citation.pageNumber}页
                        </a>
                      ))}
                    </div>
                  </div>
                )}
                {!entry.rejected && entry.conversationId != null && (
                  <div className="mt-3 flex gap-2">
                    <button
                      onClick={() => feedback(entry.conversationId, 'USEFUL')}
                      className="text-xs px-2 py-1 rounded bg-green-50 text-green-700 hover:bg-green-100"
                    >
                      有用
                    </button>
                    <button
                      onClick={() => feedback(entry.conversationId, 'USELESS')}
                      className="text-xs px-2 py-1 rounded bg-gray-50 text-gray-600 hover:bg-gray-100"
                    >
                      无用
                    </button>
                    <button
                      onClick={() => feedback(entry.conversationId, 'WRONG_CITATION')}
                      className="text-xs px-2 py-1 rounded bg-red-50 text-red-600 hover:bg-red-100"
                    >
                      引用错误
                    </button>
                  </div>
                )}
              </div>
            </div>
          </div>
        ))}
        <div ref={bottomRef} />
      </div>

      {error && <p className="text-sm text-red-500 mb-2">{error}</p>}

      <form
        onSubmit={(e) => {
          e.preventDefault();
          void ask();
        }}
        className="flex gap-2"
      >
        <input
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          placeholder="例如：报销审批需要几级？"
          className="flex-1 px-4 py-3 border border-gray-300 rounded-xl focus:outline-none focus:ring-2 focus:ring-blue-500"
          disabled={asking}
        />
        <button
          type="submit"
          disabled={asking || !question.trim()}
          className="px-6 py-3 bg-blue-600 hover:bg-blue-700 disabled:bg-gray-300 text-white rounded-xl font-medium"
        >
          {asking ? '回答中…' : '提问'}
        </button>
      </form>
      </div>
    </div>
  );
};
