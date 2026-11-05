import { Link } from 'react-router-dom';

export const NotFound = (): React.ReactNode => {
  return (
    <div className="min-h-screen bg-gray-100 flex items-center justify-center">
      <div className="text-center">
        <h1 className="text-6xl font-bold text-gray-400 mb-4">404</h1>
        <h2 className="text-2xl font-semibold text-gray-600 mb-4">页面未找到</h2>
        <p className="text-gray-500 mb-6">抱歉，您访问的页面不存在</p>
        <Link
          to="/login"
          className="px-6 py-3 bg-blue-600 hover:bg-blue-700 text-white rounded-lg transition-colors"
        >
          返回首页
        </Link>
      </div>
    </div>
  );
};
