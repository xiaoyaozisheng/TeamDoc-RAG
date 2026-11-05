import { useAuth } from '../hooks/useAuth';
import { NavBar } from '../components/NavBar';

export const Dashboard = (): React.ReactNode => {
  const { user } = useAuth();

  return (
    <div className="min-h-screen bg-gray-100">
      <NavBar activePath="/dashboard" />

      <div className="max-w-6xl mx-auto px-4 py-8">
        <div className="bg-white rounded-xl shadow-md p-6">
          <h2 className="text-2xl font-bold text-gray-800 mb-4">欢迎回来，{user?.username}!</h2>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-6">
            <div className="bg-blue-50 rounded-lg p-4">
              <p className="text-gray-600 text-sm">用户ID</p>
              <p className="text-2xl font-semibold text-gray-800">{user?.id}</p>
            </div>
            <div className="bg-green-50 rounded-lg p-4">
              <p className="text-gray-600 text-sm">邮箱</p>
              <p className="text-2xl font-semibold text-gray-800">{user?.email}</p>
            </div>
            <div className="bg-purple-50 rounded-lg p-4">
              <p className="text-gray-600 text-sm">角色</p>
              <p className="text-xl font-semibold text-gray-800">
                {user?.roles?.map(r => r.name).join(', ') || '用户'}
              </p>
            </div>
          </div>

          <div className="bg-gray-50 rounded-lg p-4">
            <p className="text-gray-600 text-sm mb-2">权限</p>
            <div className="flex flex-wrap gap-2">
              {user?.permissions?.map((permission, index) => (
                <span
                  key={index}
                  className="px-3 py-1 bg-indigo-100 text-indigo-700 rounded-full text-sm"
                >
                  {permission}
                </span>
              )) || <span className="text-gray-400">暂无权限</span>}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
