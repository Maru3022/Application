import api from '../api/client';

export interface SleepRequest {
  sleepStart: string;
  sleepEnd: string;
  quality?: number;
  notes?: string;
  source?: string;
}

export interface WaterRequest {
  amountMl: number;
  recordedAt: string;
}

export interface WeightRequest {
  weightKg: number;
  bodyFatPct?: number;
  recordedAt: string;
}

export const healthApi = {
  createSleep: (data: SleepRequest) =>
    api.post('/api/v1/health/sleep', data),

  getSleepEntries: (from?: string, to?: string) =>
    api.get('/api/v1/health/sleep', { params: { from, to } }),

  addWater: (data: WaterRequest) =>
    api.post('/api/v1/health/water', data),

  getWaterToday: () =>
    api.get<number>('/api/v1/health/water/today'),

  createWeight: (data: WeightRequest) =>
    api.post('/api/v1/health/weight', data),

  getWeightHistory: () =>
    api.get('/api/v1/health/weight/history'),

  syncActivity: (data: any) =>
    api.post('/api/v1/health/activity/sync', data),

  getActivityToday: () =>
    api.get('/api/v1/health/activity/today'),

  createSymptom: (data: any) =>
    api.post('/api/v1/health/symptoms', data),

  createCycle: (data: any) =>
    api.post('/api/v1/health/cycle', data),

  getCyclePrediction: () =>
    api.get('/api/v1/health/cycle/prediction'),

  getDashboard: () =>
    api.get('/api/v1/health/dashboard'),
};
