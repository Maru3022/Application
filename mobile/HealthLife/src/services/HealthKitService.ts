/**
 * HealthKit (iOS) and Health Connect (Android) integration service.
 *
 * Provides a unified API for reading health data from the platform's
 * native health store and syncing it to the HealthLife backend.
 *
 * iOS:  Uses react-native-health (Apple HealthKit)
 * Android: Uses react-native-health-connect (Google Health Connect)
 *
 * Usage:
 *   const service = HealthKitService.getInstance();
 *   await service.requestPermissions();
 *   const steps = await service.getStepsToday();
 */

import { Platform } from 'react-native';
import { healthApi } from '../api/health';

// Conditional imports — only available after native build (not in Expo Go)
let AppleHealthKit: any = null;
let HealthConnect: any = null;

try {
    if (Platform.OS === 'ios') {
        AppleHealthKit = require('react-native-health').default;
    } else if (Platform.OS === 'android') {
        HealthConnect = require('react-native-health-connect');
    }
} catch {
    // Not available in Expo Go — native build required
}

export interface HealthPermissions {
    steps: boolean;
    sleep: boolean;
    weight: boolean;
    heartRate: boolean;
    activeEnergy: boolean;
}

class HealthKitService {
    private static instance: HealthKitService;
    private initialized = false;

    static getInstance(): HealthKitService {
        if (!HealthKitService.instance) {
            HealthKitService.instance = new HealthKitService();
        }
        return HealthKitService.instance;
    }

    get isAvailable(): boolean {
        return Platform.OS === 'ios' ? !!AppleHealthKit : !!HealthConnect;
    }

    // ── Permissions ───────────────────────────────────────────────────────────

    async requestPermissions(): Promise<boolean> {
        if (!this.isAvailable) return false;

        try {
            if (Platform.OS === 'ios') {
                return await this.requestiOSPermissions();
            } else {
                return await this.requestAndroidPermissions();
            }
        } catch (e) {
            console.warn('HealthKit permission request failed:', e);
            return false;
        }
    }

    private async requestiOSPermissions(): Promise<boolean> {
        return new Promise((resolve) => {
            const permissions = {
                permissions: {
                    read: [
                        AppleHealthKit.Constants.Permissions.Steps,
                        AppleHealthKit.Constants.Permissions.SleepAnalysis,
                        AppleHealthKit.Constants.Permissions.Weight,
                        AppleHealthKit.Constants.Permissions.HeartRate,
                        AppleHealthKit.Constants.Permissions.ActiveEnergyBurned,
                        AppleHealthKit.Constants.Permissions.DistanceWalkingRunning,
                    ],
                    write: [
                        AppleHealthKit.Constants.Permissions.Steps,
                        AppleHealthKit.Constants.Permissions.Weight,
                    ],
                },
            };
            AppleHealthKit.initHealthKit(permissions, (err: any) => {
                if (err) {
                    console.warn('HealthKit init error:', err);
                    resolve(false);
                } else {
                    this.initialized = true;
                    resolve(true);
                }
            });
        });
    }

    private async requestAndroidPermissions(): Promise<boolean> {
        try {
            await HealthConnect.initialize();
            const granted = await HealthConnect.requestPermission([
                { accessType: 'read', recordType: 'Steps' },
                { accessType: 'read', recordType: 'SleepSession' },
                { accessType: 'read', recordType: 'Weight' },
                { accessType: 'read', recordType: 'HeartRate' },
                { accessType: 'read', recordType: 'ActiveCaloriesBurned' },
                { accessType: 'read', recordType: 'Distance' },
            ]);
            this.initialized = granted.length > 0;
            return this.initialized;
        } catch (e) {
            console.warn('Health Connect permission error:', e);
            return false;
        }
    }

    // ── Steps ─────────────────────────────────────────────────────────────────

    async getStepsToday(): Promise<number> {
        if (!this.initialized) return 0;
        try {
            if (Platform.OS === 'ios') {
                return await this.getiOSStepsToday();
            } else {
                return await this.getAndroidStepsToday();
            }
        } catch {
            return 0;
        }
    }

    private getiOSStepsToday(): Promise<number> {
        return new Promise((resolve) => {
            const options = {
                date: new Date().toISOString(),
                includeManuallyAdded: true,
            };
            AppleHealthKit.getStepCount(options, (err: any, results: any) => {
                resolve(err ? 0 : results?.value ?? 0);
            });
        });
    }

    private async getAndroidStepsToday(): Promise<number> {
        const now = new Date();
        const startOfDay = new Date(now.getFullYear(), now.getMonth(), now.getDate());
        const records = await HealthConnect.readRecords('Steps', {
            timeRangeFilter: {
                operator: 'between',
                startTime: startOfDay.toISOString(),
                endTime: now.toISOString(),
            },
        });
        return records.records.reduce((sum: number, r: any) => sum + (r.count ?? 0), 0);
    }

    // ── Sleep ─────────────────────────────────────────────────────────────────

    async getLastSleepSession(): Promise<{ startTime: string; endTime: string } | null> {
        if (!this.initialized) return null;
        try {
            if (Platform.OS === 'ios') {
                return await this.getiOSLastSleep();
            } else {
                return await this.getAndroidLastSleep();
            }
        } catch {
            return null;
        }
    }

    private getiOSLastSleep(): Promise<{ startTime: string; endTime: string } | null> {
        return new Promise((resolve) => {
            const options = {
                startDate: new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString(),
                endDate: new Date().toISOString(),
            };
            AppleHealthKit.getSleepSamples(options, (err: any, results: any[]) => {
                if (err || !results?.length) return resolve(null);
                const last = results[results.length - 1];
                resolve({ startTime: last.startDate, endTime: last.endDate });
            });
        });
    }

    private async getAndroidLastSleep(): Promise<{ startTime: string; endTime: string } | null> {
        const records = await HealthConnect.readRecords('SleepSession', {
            timeRangeFilter: {
                operator: 'between',
                startTime: new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString(),
                endTime: new Date().toISOString(),
            },
        });
        if (!records.records.length) return null;
        const last = records.records[records.records.length - 1];
        return { startTime: last.startTime, endTime: last.endTime };
    }

    // ── Sync to backend ───────────────────────────────────────────────────────

    /**
     * Reads today's health data from the native store and syncs it to the backend.
     * Call this on app foreground or after permissions are granted.
     */
    async syncToBackend(): Promise<void> {
        if (!this.initialized) return;

        try {
            // Sync steps
            const steps = await this.getStepsToday();
            if (steps > 0) {
                await healthApi.syncActivity({
                    steps,
                    date: new Date().toISOString().split('T')[0],
                    source: Platform.OS === 'ios' ? 'healthkit' : 'health_connect',
                });
            }

            // Sync last sleep
            const sleep = await this.getLastSleepSession();
            if (sleep) {
                await healthApi.createSleep({
                    sleepStart: sleep.startTime,
                    sleepEnd: sleep.endTime,
                    source: Platform.OS === 'ios' ? 'healthkit' : 'health_connect',
                });
            }
        } catch (e) {
            console.warn('Health sync failed:', e);
        }
    }
}

export default HealthKitService;
