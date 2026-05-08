import api from '../api/client';

export interface RegisterRequest {
    email: string;
    password: string;
    displayName?: string;
}

export interface LoginRequest {
    email: string;
    password: string;
}

export interface MfaVerifyRequest {
    email: string;
    code: string;
}

export interface AuthResponse {
    accessToken: string;
    refreshToken: string;
    tokenType: string;
    expiresIn: number;
    mfaRequired?: boolean;
}

export const authApi = {
    register: (data: RegisterRequest) => api.post<AuthResponse>('/api/v1/auth/register', data),

    login: (data: LoginRequest) => api.post<AuthResponse>('/api/v1/auth/login', data),

    refresh: (refreshToken: string) =>
        api.post<AuthResponse>('/api/v1/auth/refresh', { refreshToken }),

    logout: (refreshToken: string) =>
        api.post('/api/v1/auth/logout', null, {
            headers: { 'X-Refresh-Token': refreshToken },
        }),

    setupMfa: () => api.post('/api/v1/auth/mfa/setup'),

    /**
     * FIX: email is now sent in the request body (not X-User-Email header)
     * to prevent identity spoofing during MFA verification.
     */
    verifyMfa: (data: MfaVerifyRequest) =>
        api.post<AuthResponse>('/api/v1/auth/mfa/verify', data),

    requestPasswordReset: (email: string) =>
        api.post('/api/v1/auth/password/reset', { email }),

    confirmPasswordReset: (token: string, newPassword: string) =>
        api.post('/api/v1/auth/password/reset/confirm', { token, newPassword }),

    /** Google Sign-In — pass the ID token from Google Sign-In SDK */
    loginWithGoogle: (idToken: string) =>
        api.post<AuthResponse>('/api/v1/auth/oauth/google', null, {
            params: { idToken },
        }),

    /** Apple Sign-In — pass the identity token from Sign in with Apple */
    loginWithApple: (identityToken: string, email?: string, fullName?: string) =>
        api.post<AuthResponse>('/api/v1/auth/oauth/apple', null, {
            params: { identityToken, email, fullName },
        }),
};
