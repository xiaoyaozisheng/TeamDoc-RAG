import { useState, useEffect, useCallback } from 'react';
import { authService } from '../services/auth';
import { storage } from '../utils/storage';
import { UserResponse } from '../types';

interface UseAuthReturn {
  user: UserResponse | null;
  isLoading: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (username: string, email: string, password: string) => Promise<void>;
  logout: () => void;
}

const extractStatus = (err: unknown): number | undefined =>
  (err as { response?: { status?: number } })?.response?.status;

export const useAuth = (): UseAuthReturn => {
  const [user, setUser] = useState<UserResponse | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  const fetchUser = useCallback(async () => {
    if (!storage.isAuthenticated()) {
      setIsLoading(false);
      return;
    }

    try {
      const userData = await authService.getUserInfo();
      setUser(userData);
    } catch (err) {
      const status = extractStatus(err);
      if (status === 401 || status === 403) {
        // 会话已失效：清除本地状态并回到登录页。
        // 不清会导致 ProtectedRoute 的非响应式检查通过而页面停留在空数据状态。
        storage.clearTokens();
        setUser(null);
        window.location.href = '/login';
        return;
      }
      // 网络抖动等其他错误：保留令牌，不清空登录态
      setUser(null);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchUser();
  }, [fetchUser]);

  const login = async (email: string, password: string): Promise<void> => {
    await authService.login({ email, password });
    await fetchUser();
  };

  const register = async (username: string, email: string, password: string): Promise<void> => {
    await authService.register({ username, email, password });
    await fetchUser();
  };

  const logout = (): void => {
    authService.logout();
    setUser(null);
  };

  return { user, isLoading, login, register, logout };
};
