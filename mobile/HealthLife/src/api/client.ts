import axios, { AxiosInstance, InternalAxiosRequestConfig } from 'axios';
import * as SecureStore from 'expo-secure-store';
import { API_BASE_URL, STORAGE_KEYS } from '../constants';

const api: AxiosInstance = axios.create({
    baseURL: API_BASE_URL,
    timeout: 15000,
    headers: { 'Content-Type': 'application/json' },
});

// Attach Bearer token to every request
api.interceptors.request.use(async (config: InternalAxiosRequestConfig) => {
    const token = await SecureStore.getItemAsync(STORAGE_KEYS.ACCESS_TOKEN);
    if (token && config.headers) {
        config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
});

// Automatic token refresh on 401 — retry the original request once with the new token.
// If refresh also fails, clear tokens and redirect to login.
api.interceptors.response.use(
    (response) => response,
    async (error) => {
        const originalRequest = error.config;

        if (error.response?.status === 401 && !originalRequest._retry) {
            originalRequest._retry = true;
            const refreshToken = await SecureStore.getItemAsync(STORAGE_KEYS.REFRESH_TOKEN);

            if (refreshToken) {
                try {
                    const { data } = await axios.post(`${API_BASE_URL}/api/v1/auth/refresh`, {
                        refreshToken,
                    });
                    await SecureStore.setItemAsync(STORAGE_KEYS.ACCESS_TOKEN, data.accessToken);
                    await SecureStore.setItemAsync(STORAGE_KEYS.REFRESH_TOKEN, data.refreshToken);

                    if (originalRequest.headers) {
                        originalRequest.headers.Authorization = `Bearer ${data.accessToken}`;
                    }
                    return api(originalRequest);
                } catch {
                    // Refresh failed — clear tokens so the app redirects to login
                    await SecureStore.deleteItemAsync(STORAGE_KEYS.ACCESS_TOKEN);
                    await SecureStore.deleteItemAsync(STORAGE_KEYS.REFRESH_TOKEN);
                    // Notify the auth store to update isAuthenticated state
                    // (imported lazily to avoid circular dependency)
                    try {
                        const { useAuthStore } = await import('../store/authStore');
                        useAuthStore.getState().logout();
                    } catch {}
                }
            }
        }

        return Promise.reject(error);
    },
);

export default api;
