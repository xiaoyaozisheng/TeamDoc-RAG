import { useAuth } from '../hooks/useAuth';

const NAV_ITEMS = [
  { path: '/dashboard', label: '概览' },
  { path: '/documents', label: '文档管理' },
  { path: '/chat', label: '知识库问答' },
  { path: '/eval', label: '评估' },
];

/** 页面顶部导航 */
export const NavBar = ({ activePath }: { activePath: string }): React.ReactNode => {
  const { user, logout } = useAuth();

  return (
    <nav className="bg-white shadow-md">
      <div className="max-w-6xl mx-auto px-4">
        <div className="flex justify-between items-center h-16">
          <div className="flex items-center space-x-6">
            <span className="text-xl font-bold text-blue-600">TeamDoc RAG</span>
            <div className="flex space-x-1">
              {NAV_ITEMS.map((item) => (
                <a
                  key={item.path}
                  href={item.path}
                  className={`px-3 py-2 rounded-lg text-sm transition-colors ${
                    activePath === item.path
                      ? 'bg-blue-50 text-blue-700 font-medium'
                      : 'text-gray-600 hover:bg-gray-100'
                  }`}
                >
                  {item.label}
                </a>
              ))}
            </div>
          </div>
          <div className="flex items-center space-x-4">
            <span className="text-gray-600">{user?.email}</span>
            <button
              onClick={logout}
              className="px-4 py-2 bg-red-500 hover:bg-red-600 text-white rounded-lg transition-colors"
            >
              退出登录
            </button>
          </div>
        </div>
      </div>
    </nav>
  );
};
