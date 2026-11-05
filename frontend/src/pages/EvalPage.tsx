import { useCallback, useEffect, useState } from 'react';
import { EvalQuestionItem, EvalRunItem } from '../types';
import { evalService } from '../services/rag';
import { NavBar } from '../components/NavBar';

export const EvalPage = (): React.ReactNode => {
  const [questions, setQuestions] = useState<EvalQuestionItem[]>([]);
  const [runs, setRuns] = useState<EvalRunItem[]>([]);
  const [question, setQuestion] = useState('');
  const [expectedDocId, setExpectedDocId] = useState('');
  const [negative, setNegative] = useState(false);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      const [questionList, runList] = await Promise.all([evalService.listQuestions(), evalService.runs()]);
      setQuestions(questionList);
      setRuns(runList);
    } catch {
      setError('评估数据加载失败（需要 eval:manage 权限）');
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const onAdd = async (): Promise<void> => {
    if (!question.trim()) {
      return;
    }
    try {
      await evalService.addQuestion({
        question: question.trim(),
        expectedDocId: expectedDocId ? Number(expectedDocId) : null,
        negative,
      });
      setQuestion('');
      setExpectedDocId('');
      setNegative(false);
      await refresh();
    } catch {
      setError('添加评估问题失败');
    }
  };

  const onRun = async (): Promise<void> => {
    setRunning(true);
    setError(null);
    try {
      await evalService.run();
      await refresh();
    } catch {
      setError('评估执行失败');
    } finally {
      setRunning(false);
    }
  };

  return (
    <div className="min-h-screen bg-gray-100">
      <NavBar activePath="/eval" />
      <div className="max-w-6xl mx-auto px-4 py-8 space-y-8">
      <div>
        <div className="flex justify-between items-center mb-4">
          <h1 className="text-2xl font-bold text-gray-800">黄金评估集</h1>
          <button
            onClick={() => void onRun()}
            disabled={running || questions.length === 0}
            className="px-4 py-2 bg-blue-600 hover:bg-blue-700 disabled:bg-gray-300 text-white rounded-lg"
          >
            {running ? '评估中…' : '批量评估'}
          </button>
        </div>

        {error && <p className="mb-3 text-sm text-red-500">{error}</p>}

        <div className="bg-white rounded-xl shadow-md p-4 mb-4 flex flex-wrap gap-3 items-center">
          <input
            value={question}
            onChange={(e) => setQuestion(e.target.value)}
            placeholder="评估问题，如：报销审批需要几级？"
            className="flex-1 min-w-[240px] px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          <input
            value={expectedDocId}
            onChange={(e) => setExpectedDocId(e.target.value)}
            placeholder="期望命中文档 ID（正样本）"
            className="w-56 px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          <label className="flex items-center gap-1 text-sm text-gray-600">
            <input type="checkbox" checked={negative} onChange={(e) => setNegative(e.target.checked)} />
            负样本（知识库无答案）
          </label>
          <button
            onClick={() => void onAdd()}
            disabled={!question.trim()}
            className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 disabled:bg-gray-300 text-white rounded-lg text-sm"
          >
            添加
          </button>
        </div>

        <div className="bg-white rounded-xl shadow-md overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-gray-500">
              <tr>
                <th className="text-left px-4 py-3">问题</th>
                <th className="text-left px-4 py-3">类型</th>
                <th className="text-left px-4 py-3">期望文档</th>
                <th className="text-right px-4 py-3">操作</th>
              </tr>
            </thead>
            <tbody>
              {questions.length === 0 && (
                <tr>
                  <td colSpan={4} className="text-center text-gray-400 py-6">
                    暂无评估问题，建议 30–50 题并包含 3–5 道负样本
                  </td>
                </tr>
              )}
              {questions.map((item) => (
                <tr key={item.id} className="border-t border-gray-100">
                  <td className="px-4 py-3 text-gray-800">{item.question}</td>
                  <td className="px-4 py-3">
                    <span
                      className={`px-2 py-1 rounded-full text-xs ${
                        item.negative ? 'bg-amber-50 text-amber-700' : 'bg-green-50 text-green-700'
                      }`}
                    >
                      {item.negative ? '负样本' : '正样本'}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-gray-500">{item.expectedDocId >= 0 ? item.expectedDocId : '-'}</td>
                  <td className="px-4 py-3 text-right">
                    <button
                      onClick={() => void evalService.removeQuestion(item.id).then(refresh)}
                      className="px-2 py-1 text-xs rounded bg-red-50 text-red-600 hover:bg-red-100"
                    >
                      删除
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      <div>
        <h2 className="text-xl font-bold text-gray-800 mb-4">评估历史</h2>
        <div className="bg-white rounded-xl shadow-md overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-gray-500">
              <tr>
                <th className="text-left px-4 py-3">时间</th>
                <th className="text-left px-4 py-3">题数</th>
                <th className="text-left px-4 py-3">Recall@5</th>
                <th className="text-left px-4 py-3">拒答准确率</th>
              </tr>
            </thead>
            <tbody>
              {runs.length === 0 && (
                <tr>
                  <td colSpan={4} className="text-center text-gray-400 py-6">
                    暂无评估记录
                  </td>
                </tr>
              )}
              {runs.map((run) => (
                <tr key={run.id} className="border-t border-gray-100">
                  <td className="px-4 py-3 text-gray-500">{new Date(run.createdAt).toLocaleString()}</td>
                  <td className="px-4 py-3 text-gray-800">{run.questionCount}</td>
                  <td className="px-4 py-3">
                    <span className={run.metrics.recallAt5 >= 0.8 ? 'text-green-600 font-medium' : 'text-amber-600 font-medium'}>
                      {(run.metrics.recallAt5 * 100).toFixed(1)}%
                    </span>
                  </td>
                  <td className="px-4 py-3 text-gray-800">
                    {(run.metrics.rejectionAccuracy * 100).toFixed(1)}%
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
      </div>
    </div>
  );
};
