import axios from 'axios';
import { storage } from './storage';
import { AuthResponse } from '../types';

const baseURL = import.meta.env.VITE_API_URL || '/api';

const api = axios.create({
  baseURL,
  headers: {
    'Content-Type': 'application/json',
  },
  withCredentials: true, // 刷新/登出时让浏览器自动携带 refresh_token Cookie
});

let isRefreshing = false;
let refreshSubscribers: ((token: string) => void)[] = [];

const onTokenRefreshed = (token: string): void => {
  refreshSubscribers.forEach((callback) => callback(token));
  refreshSubscribers = [];
};

const addRefreshSubscriber = (callback: (token: string) => void): void => {
  refreshSubscribers.push(callback);
};

// 认证接口自身的 401 不触发刷新，避免循环
const isAuthEndpoint = (url?: string): boolean =>
  !!url && url.includes('/auth/');

api.interceptors.request.use(
  (config) => {
    const token = storage.getAccessToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;

    const canRefresh =
      error.response?.status === 401 &&
      originalRequest &&
      !originalRequest._retry &&
      !isAuthEndpoint(originalRequest.url);

    if (canRefresh) {
      originalRequest._retry = true;

      if (!isRefreshing) {
        isRefreshing = true;
        try {
          // RefreshToken 在 HttpOnly Cookie 中，浏览器自动携带，无需传参
          const response = await axios.post<AuthResponse>(
            `${baseURL}/v1/auth/refresh-token`,
            null,
            { withCredentials: true }
          );

          const { accessToken } = response.data;
          storage.setAccessToken(accessToken);
          onTokenRefreshed(accessToken);
        } catch {
          storage.clearTokens();
          window.location.href = '/login';
          return Promise.reject(error);
        } finally {
          isRefreshing = false;
        }
      }

      return new Promise<string>((resolve) => {
        addRefreshSubscriber((token: string) => {
          originalRequest.headers.Authorization = `Bearer ${token}`;
          resolve(token);
        });
      }).then(() => api(originalRequest));
    }

    return Promise.reject(error);
  }
);

export default api;
