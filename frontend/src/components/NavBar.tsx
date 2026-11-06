import { useState } from 'react';
import { NavLink } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { ChangePasswordModal } from './ChangePasswordModal';

const NAV_ITEMS = [
  { path: '/dashboard', label: '概览' },
  { path: '/documents', label: '文档管理' },
  { path: '/chat', label: '知识库问答' },
  { path: '/eval', label: '评估' },
];

/** 页面顶部导航：SPA 内部跳转（NavLink，避免整页刷新）+ 自助改密 + 管理员设置入口 */
export const NavBar = ({ activePath }: { activePath: string }): React.ReactNode => {
  const { user, logout } = useAuth();
  const [passwordModalOpen, setPasswordModalOpen] = useState(false);

  const isAdmin = user?.roles?.some((role) => role.name === 'ROLE_ADMIN');

  return (
    <nav className="bg-white shadow-md">
      <div className="max-w-6xl mx-auto px-4">
        <div className="flex justify-between items-center h-16">
          <div className="flex items-center space-x-6">
            <span className="text-xl font-bold text-blue-600">TeamDoc RAG</span>
            <div className="flex space-x-1">
              {NAV_ITEMS.map((item) => (
                <NavLink
                  key={item.path}
                  to={item.path}
                  className={({ isActive }) => `px-3 py-2 rounded-lg text-sm transition-colors ${
                    isActive || activePath === item.path
                      ? 'bg-blue-50 text-blue-700 font-medium'
                      : 'text-gray-600 hover:bg-gray-100'
                  }`}
                >
                  {item.label}
                </NavLink>
              ))}
              {isAdmin && (
                <NavLink
                  to="/settings"
                  className={({ isActive }) => `px-3 py-2 rounded-lg text-sm transition-colors ${
                    isActive || activePath === '/settings'
                      ? 'bg-blue-50 text-blue-700 font-medium'
                      : 'text-gray-600 hover:bg-gray-100'
                  }`}
                >
                  系统管理
                </NavLink>
              )}
            </div>
          </div>
          <div className="flex items-center space-x-4">
            <span className="text-gray-600">{user?.email}</span>
            <button
              onClick={() => setPasswordModalOpen(true)}
              className="px-4 py-2 bg-gray-100 hover:bg-gray-200 text-gray-700 rounded-lg transition-colors"
            >
              修改密码
            </button>
            <button
              onClick={logout}
              className="px-4 py-2 bg-red-500 hover:bg-red-600 text-white rounded-lg transition-colors"
            >
              退出登录
            </button>
          </div>
        </div>
      </div>
      <ChangePasswordModal open={passwordModalOpen} onClose={() => setPasswordModalOpen(false)} />
    </nav>
  );
};
