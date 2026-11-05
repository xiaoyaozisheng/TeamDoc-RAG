import api from '../utils/axios';
import { storage } from '../utils/storage';
import { AuthRequest, AuthResponse, RegisterRequest, UserResponse } from '../types';

export const authService = {
  login: async (credentials: AuthRequest): Promise<AuthResponse> => {
    const response = await api.post<AuthResponse>('/v1/auth/login', credentials);
    // RefreshToken 由后端通过 HttpOnly Cookie 下发，前端只保存 AccessToken
    storage.setAccessToken(response.data.accessToken);
    return response.data;
  },

  register: async (userData: RegisterRequest): Promise<AuthResponse> => {
    const response = await api.post<AuthResponse>('/v1/auth/register', userData);
    storage.setAccessToken(response.data.accessToken);
    return response.data;
  },

  logout: async (): Promise<void> => {
    try {
      // 撤销服务端 RefreshToken（浏览器自动携带 Cookie），后端同时清除 Cookie
      await api.post('/v1/auth/logout');
    } catch {
      // 即使服务端撤销失败也要完成本地登出
    } finally {
      storage.clearTokens();
      window.location.href = '/login';
    }
  },

  getUserInfo: async (): Promise<UserResponse> => {
    const response = await api.get<UserResponse>('/v1/auth/me');
    return response.data;
  },
};
