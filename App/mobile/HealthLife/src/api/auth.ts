import api from '../api/client';

export interface RegisterRequest {
  email: string;
  password: string;
  displayName?: string;
}

export interface LoginRequest {
  email: string;
  password: string;
  code?: string;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  mfaRequired?: boolean;
}

export const authApi = {
  register: (data: RegisterRequest) =>
    api.post<AuthResponse>('/api/v1/auth/register', data),

  login: (data: LoginRequest) =>
    api.post<AuthResponse>('/api/v1/auth/login', data),

  refresh: (refreshToken: string) =>
    api.post<AuthResponse>('/api/v1/auth/refresh', { refreshToken }),

  logout: (refreshToken: string) =>
    api.post('/api/v1/auth/logout', null, {
      headers: { 'X-Refresh-Token': refreshToken },
    }),

  setupMfa: () =>
    api.post('/api/v1/auth/mfa/setup'),

  verifyMfa: (code: string, email: string) =>
    api.post<AuthResponse>('/api/v1/auth/mfa/verify', { code }, {
      headers: { 'X-User-Email': email },
    }),

  requestPasswordReset: (email: string) =>
    api.post('/api/v1/auth/password/reset', { email }),

  confirmPasswordReset: (token: string, newPassword: string) =>
    api.post('/api/v1/auth/password/reset/confirm', { token, newPassword }),
};
