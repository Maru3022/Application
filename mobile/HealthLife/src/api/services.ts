import api from '../api/client';

export const mentalApi = {
  createMood: (data: any) => api.post('/api/v1/mental/mood', data),
  getMoodHistory: () => api.get('/api/v1/mental/mood/history'),
  createJournal: (data: any) => api.post('/api/v1/mental/journal', data),
  getJournals: () => api.get('/api/v1/mental/journal'),
  createStress: (data: any) => api.post('/api/v1/mental/stress', data),
  getStressStats: () => api.get('/api/v1/mental/stress/stats'),
  getMeditations: (category?: string) => api.get('/api/v1/mental/meditations', { params: { category } }),
  completeMeditation: (id: string) => api.post(`/api/v1/mental/meditations/${id}/complete`),
  createBreathingSession: (data: any) => api.post('/api/v1/mental/breathing/session', data),
};

export const nutritionApi = {
  addFoodLog: (data: any) => api.post('/api/v1/nutrition/food-log', data),
  getFoodLogToday: () => api.get('/api/v1/nutrition/food-log/today'),
  searchFoods: (q: string) => api.get('/api/v1/nutrition/foods/search', { params: { q } }),
  getFoodByBarcode: (barcode: string) => api.post('/api/v1/nutrition/foods/barcode', barcode),
  createCustomFood: (data: any) => api.post('/api/v1/nutrition/foods/custom', data),
  getCustomFoods: () => api.get('/api/v1/nutrition/foods/custom'),
};

export const aiCoachApi = {
  chat: (message: string, context?: string) =>
    api.post('/api/v1/ai/chat', { message, context }),
  getDailyInsight: () => api.get('/api/v1/ai/insights/daily'),
  getWeeklyInsight: () => api.get('/api/v1/ai/insights/weekly'),
  getRecommendations: () => api.get('/api/v1/ai/recommendations'),
};

export const socialApi = {
  getChallenges: () => api.get('/api/v1/social/challenges'),
  createChallenge: (data: any) => api.post('/api/v1/social/challenges', data),
  joinChallenge: (id: string) => api.post(`/api/v1/social/challenges/${id}/join`),
  getFeed: () => api.get('/api/v1/social/feed'),
  createPost: (data: any) => api.post('/api/v1/social/posts', data),
  likePost: (id: string) => api.post(`/api/v1/social/posts/${id}/like`),
  getFriends: () => api.get('/api/v1/social/friends'),
};

export const userApi = {
  getProfile: () => api.get('/api/v1/users/me'),
  updateProfile: (data: any) => api.put('/api/v1/users/me', data),
  deleteAccount: () => api.delete('/api/v1/users/me'),
  getGoals: () => api.get('/api/v1/users/me/goals'),
  updateGoals: (data: any) => api.put('/api/v1/users/me/goals', data),
  getSubscription: () => api.get('/api/v1/users/me/subscription'),
  exportData: () => api.get('/api/v1/users/me/data-export'),
};

export const paymentApi = {
  getSubscriptionStatus: () => api.get('/api/v1/payments/subscription'),
  createCheckout: (priceId: string) =>
    api.post('/api/v1/payments/checkout', null, { params: { priceId } }),
  createPortal: () => api.post('/api/v1/payments/portal'),
};

export const analyticsApi = {
  trackEvent: (eventName: string, properties?: Record<string, unknown>) =>
    api.post('/api/v1/analytics/events', properties ?? {}, { params: { eventName } }),
};
