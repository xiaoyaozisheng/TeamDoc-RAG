// AccessToken 存 sessionStorage（标签页关闭即失效，XSS 影响窗口小）；
// RefreshToken 由后端通过 HttpOnly Cookie 管理，JS 完全不接触，因此这里没有任何 RefreshToken 读写。
const ACCESS_TOKEN_KEY = 'access_token';

export const storage = {
  getAccessToken: (): string | null => {
    return sessionStorage.getItem(ACCESS_TOKEN_KEY);
  },

  setAccessToken: (token: string): void => {
    sessionStorage.setItem(ACCESS_TOKEN_KEY, token);
  },

  removeAccessToken: (): void => {
    sessionStorage.removeItem(ACCESS_TOKEN_KEY);
  },

  clearTokens: (): void => {
    sessionStorage.removeItem(ACCESS_TOKEN_KEY);
  },

  isAuthenticated: (): boolean => {
    return !!sessionStorage.getItem(ACCESS_TOKEN_KEY);
  },
};
