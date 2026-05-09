import { create } from 'zustand';
import * as SecureStore from 'expo-secure-store';
import { authApi } from '../api/auth';
import { userApi } from '../api/services';
import { STORAGE_KEYS } from '../constants';

interface User {
    id: string;
    email: string;
    displayName: string;
    subscriptionPlan?: string;
}

interface AuthState {
    isAuthenticated: boolean;
    user: User | null;
    isLoading: boolean;
    mfaRequired: boolean;
    pendingEmail: string | null;

    login: (email: string, password: string) => Promise<void>;
    register: (email: string, password: string, displayName: string) => Promise<void>;
    verifyMfa: (code: string) => Promise<void>;
    logout: () => Promise<void>;
    restoreSession: () => Promise<void>;
    refreshProfile: () => Promise<void>;
}

export const useAuthStore = create<AuthState>((set, get) => ({
    isAuthenticated: false,
    user: null,
    isLoading: false,
    mfaRequired: false,
    pendingEmail: null,

    login: async (email, password) => {
        set({ isLoading: true });
        try {
            const { data } = await authApi.login({ email, password });
            if (data.tokenType === 'MFA_REQUIRED') {
                set({ mfaRequired: true, pendingEmail: email, isLoading: false });
                return;
            }
            await SecureStore.setItemAsync(STORAGE_KEYS.ACCESS_TOKEN, data.accessToken);
            await SecureStore.setItemAsync(STORAGE_KEYS.REFRESH_TOKEN, data.refreshToken);
            set({ isAuthenticated: true, mfaRequired: false, isLoading: false });
            // Load profile after login
            await get().refreshProfile();
        } catch (error: any) {
            set({ isLoading: false });
            throw new Error(error.response?.data?.message || 'Login failed');
        }
    },

    register: async (email, password, displayName) => {
        set({ isLoading: true });
        try {
            const { data } = await authApi.register({ email, password, displayName });
            await SecureStore.setItemAsync(STORAGE_KEYS.ACCESS_TOKEN, data.accessToken);
            await SecureStore.setItemAsync(STORAGE_KEYS.REFRESH_TOKEN, data.refreshToken);
            set({ isAuthenticated: true, isLoading: false });
            await get().refreshProfile();
        } catch (error: any) {
            set({ isLoading: false });
            throw new Error(error.response?.data?.message || 'Registration failed');
        }
    },

    verifyMfa: async (code) => {
        const { pendingEmail } = get();
        if (!pendingEmail) return;
        set({ isLoading: true });
        try {
            const { data } = await authApi.verifyMfa({ email: pendingEmail, code });
            await SecureStore.setItemAsync(STORAGE_KEYS.ACCESS_TOKEN, data.accessToken);
            await SecureStore.setItemAsync(STORAGE_KEYS.REFRESH_TOKEN, data.refreshToken);
            set({ isAuthenticated: true, mfaRequired: false, pendingEmail: null, isLoading: false });
            await get().refreshProfile();
        } catch (error: any) {
            set({ isLoading: false });
            throw new Error(error.response?.data?.message || 'MFA verification failed');
        }
    },

    logout: async () => {
        const refreshToken = await SecureStore.getItemAsync(STORAGE_KEYS.REFRESH_TOKEN);
        if (refreshToken) {
            try {
                await authApi.logout(refreshToken);
            } catch {}
        }
        await SecureStore.deleteItemAsync(STORAGE_KEYS.ACCESS_TOKEN);
        await SecureStore.deleteItemAsync(STORAGE_KEYS.REFRESH_TOKEN);
        set({ isAuthenticated: false, user: null, mfaRequired: false, pendingEmail: null });
    },

    restoreSession: async () => {
        const token = await SecureStore.getItemAsync(STORAGE_KEYS.ACCESS_TOKEN);
        if (token) {
            set({ isAuthenticated: true });
            // Load profile so user.displayName is available immediately after app restart
            await get().refreshProfile();
        }
    },

    refreshProfile: async () => {
        try {
            const { data } = await userApi.getProfile();
            const profile = data as any;
            set({
                user: {
                    id: profile.id,
                    email: profile.email,
                    displayName: profile.displayName,
                    subscriptionPlan: profile.subscriptionPlan,
                },
            });
        } catch {
            // Non-fatal — user stays authenticated, profile just won't be populated
        }
    },
}));
