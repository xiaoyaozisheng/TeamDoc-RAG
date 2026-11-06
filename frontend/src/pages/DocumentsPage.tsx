import { useCallback, useEffect, useState } from 'react';
import { Modal } from 'antd';
import { DocumentItem } from '../types';
import { documentService } from '../services/rag';
import { NavBar } from '../components/NavBar';

const STATUS_BADGE: Record<DocumentItem['status'], string> = {
  PENDING: 'bg-gray-100 text-gray-600',
  PROCESSING: 'bg-blue-50 text-blue-700',
  SUCCESS: 'bg-green-50 text-green-700',
  FAILED: 'bg-red-50 text-red-600',
};

const STATUS_LABEL: Record<DocumentItem['status'], string> = {
  PENDING: '待处理',
  PROCESSING: '处理中',
  SUCCESS: '已入库',
  FAILED: '失败',
};

export const DocumentsPage = (): React.ReactNode => {
  const [documents, setDocuments] = useState<DocumentItem[]>([]);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(async () => {
    try {
      setDocuments(await documentService.list());
    } catch {
      setError('文档列表加载失败');
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const onUpload = async (files: FileList | null): Promise<void> => {
    if (!files || files.length === 0) {
      return;
    }
    setUploading(true);
    setError(null);
    try {
      for (const file of Array.from(files)) {
        await documentService.upload(file);
      }
      await refresh();
    } catch (err) {
      const message = (err as { response?: { data?: { message?: string } } }).response?.data?.message;
      setError(message || '上传失败（仅支持 PDF/MD/TXT/DOCX，不超过 20MB）');
    } finally {
      setUploading(false);
    }
  };

  const onReindex = async (id: number): Promise<void> => {
    setError(null);
    try {
      await documentService.reindex(id);
      await refresh();
    } catch {
      setError('重新入库失败');
    }
  };

  const onDelete = (doc: DocumentItem): void => {
    // 删除不可恢复（切块与向量同步清理），必须二次确认
    Modal.confirm({
      title: '确认删除文档？',
      content: `「${doc.name}」将被删除，其切块与向量同步清理，删除后不可再被检索召回。`,
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        setError(null);
        try {
          await documentService.remove(doc.id);
          await refresh();
        } catch {
          setError('删除失败');
        }
      },
    });
  };

  return (
    <div className="min-h-screen bg-gray-100">
      <NavBar activePath="/documents" />
      <div className="max-w-6xl mx-auto px-4 py-8">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold text-gray-800">文档管理</h1>
        <label className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg cursor-pointer">
          {uploading ? '上传中…' : '上传文档'}
          <input
            type="file"
            className="hidden"
            accept=".pdf,.md,.txt,.docx"
            multiple
            disabled={uploading}
            onChange={(e) => void onUpload(e.target.files)}
          />
        </label>
      </div>

      {error && <p className="mb-4 text-sm text-red-500">{error}</p>}

      <div className="bg-white rounded-xl shadow-md overflow-hidden">
        <table className="w-full text-sm">
          <thead className="bg-gray-50 text-gray-500">
            <tr>
              <th className="text-left px-4 py-3">文档名</th>
              <th className="text-left px-4 py-3">类型</th>
              <th className="text-left px-4 py-3">状态</th>
              <th className="text-left px-4 py-3">大小</th>
              <th className="text-left px-4 py-3">上传人</th>
              <th className="text-left px-4 py-3">时间</th>
              <th className="text-right px-4 py-3">操作</th>
            </tr>
          </thead>
          <tbody>
            {documents.length === 0 && (
              <tr>
                <td colSpan={7} className="text-center text-gray-400 py-8">
                  暂无文档，上传 PDF / Markdown / TXT / DOCX 后自动解析入库
                </td>
              </tr>
            )}
            {documents.map((doc) => (
              <tr key={doc.id} className="border-t border-gray-100 hover:bg-gray-50">
                <td className="px-4 py-3 text-gray-800">{doc.name}</td>
                <td className="px-4 py-3 text-gray-500">{doc.type}</td>
                <td className="px-4 py-3">
                  <span className={`px-2 py-1 rounded-full text-xs ${STATUS_BADGE[doc.status]}`}>
                    {STATUS_LABEL[doc.status]}
                  </span>
                  {doc.errorMsg && doc.status === 'FAILED' && (
                    <span className="ml-2 text-xs text-red-400" title={doc.errorMsg}>
                      {doc.errorMsg.slice(0, 30)}
                    </span>
                  )}
                </td>
                <td className="px-4 py-3 text-gray-500">{(doc.sizeBytes / 1024).toFixed(1)} KB</td>
                <td className="px-4 py-3 text-gray-500">{doc.uploadedByEmail || '-'}</td>
                <td className="px-4 py-3 text-gray-500">{new Date(doc.createdAt).toLocaleString()}</td>
                <td className="px-4 py-3 text-right space-x-2">
                  <button
                    onClick={() => void documentService.download(doc.id, doc.name)}
                    className="px-2 py-1 text-xs rounded bg-gray-100 text-gray-600 hover:bg-gray-200"
                  >
                    下载
                  </button>
                  <button
                    onClick={() => void onReindex(doc.id)}
                    className="px-2 py-1 text-xs rounded bg-blue-50 text-blue-700 hover:bg-blue-100"
                  >
                    重新入库
                  </button>
                  <button
                    onClick={() => onDelete(doc)}
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
    </div>
  );
};
