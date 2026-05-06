import React, { useState, useEffect, useCallback } from 'react';
import { View, StyleSheet, ScrollView, RefreshControl } from 'react-native';
import { Card, Text, ProgressBar, IconButton, ActivityIndicator } from 'react-native-paper';
import { healthApi } from '../../api/health';

interface DashboardData {
  waterToday: number;
  stepsToday: number;
  stepsGoal: number;
  lastSleepHours: number | null;
  lastSleepMinutes: number | null;
}

const WATER_GOAL_ML = 2000;
const DEFAULT_STEPS_GOAL = 10000;
const SLEEP_GOAL_HOURS = 8;

export default function DashboardScreen() {
  const [data, setData] = useState<DashboardData>({
    waterToday: 0,
    stepsToday: 0,
    stepsGoal: DEFAULT_STEPS_GOAL,
    lastSleepHours: null,
    lastSleepMinutes: null,
  });
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);

  const loadData = useCallback(async () => {
    try {
      // Fetch water, activity, and sleep in parallel for faster load
      const [waterRes, activityRes, sleepRes] = await Promise.allSettled([
        healthApi.getWaterToday(),
        healthApi.getActivityToday(),
        healthApi.getSleepEntries(),
      ]);

      const waterToday =
        waterRes.status === 'fulfilled' ? (waterRes.value.data as number) : 0;

      let stepsToday = 0;
      let stepsGoal = DEFAULT_STEPS_GOAL;
      if (activityRes.status === 'fulfilled') {
        const activity = activityRes.value.data as any;
        stepsToday = activity?.steps ?? 0;
        stepsGoal = activity?.stepsGoal ?? DEFAULT_STEPS_GOAL;
      }

      let lastSleepHours: number | null = null;
      let lastSleepMinutes: number | null = null;
      if (sleepRes.status === 'fulfilled') {
        const entries = sleepRes.value.data as any[];
        if (entries && entries.length > 0) {
          const latest = entries[0];
          if (latest.sleepStart && latest.sleepEnd) {
            const durationMs =
              new Date(latest.sleepEnd).getTime() -
              new Date(latest.sleepStart).getTime();
            const totalMinutes = Math.round(durationMs / 60000);
            lastSleepHours = Math.floor(totalMinutes / 60);
            lastSleepMinutes = totalMinutes % 60;
          }
        }
      }

      setData({ waterToday, stepsToday, stepsGoal, lastSleepHours, lastSleepMinutes });
    } catch (err) {
      // Non-fatal: dashboard degrades gracefully if a service is unavailable
      console.warn('Dashboard load error:', err);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const onRefresh = async () => {
    setRefreshing(true);
    await loadData();
    setRefreshing(false);
  };

  const sleepLabel =
    data.lastSleepHours !== null
      ? `Last night: ${data.lastSleepHours}h ${data.lastSleepMinutes}m`
      : 'No sleep data yet';

  const sleepProgress =
    data.lastSleepHours !== null
      ? Math.min((data.lastSleepHours * 60 + (data.lastSleepMinutes ?? 0)) / (SLEEP_GOAL_HOURS * 60), 1)
      : 0;

  if (loading) {
    return (
      <View style={styles.centered}>
        <ActivityIndicator size="large" />
      </View>
    );
  }

  return (
    <ScrollView
      refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
      style={styles.container}
    >
      <Text style={styles.greeting}>Good day! 👋</Text>

      <Card style={styles.card}>
        <Card.Title
          title="Water Intake"
          left={(props: any) => <IconButton icon="water" {...props} />}
        />
        <Card.Content>
          <Text>{data.waterToday} / {WATER_GOAL_ML} ml</Text>
          <ProgressBar
            progress={Math.min(data.waterToday / WATER_GOAL_ML, 1)}
            color="#2196F3"
            style={styles.progressBar}
          />
        </Card.Content>
      </Card>

      <Card style={styles.card}>
        <Card.Title
          title="Today's Steps"
          left={(props: any) => <IconButton icon="walk" {...props} />}
        />
        <Card.Content>
          <Text>{data.stepsToday.toLocaleString()} / {data.stepsGoal.toLocaleString()} steps</Text>
          <ProgressBar
            progress={Math.min(data.stepsToday / data.stepsGoal, 1)}
            color="#4CAF50"
            style={styles.progressBar}
          />
        </Card.Content>
      </Card>

      <Card style={styles.card}>
        <Card.Title
          title="Sleep"
          left={(props: any) => <IconButton icon="bed" {...props} />}
        />
        <Card.Content>
          <Text>{sleepLabel}</Text>
          <ProgressBar
            progress={sleepProgress}
            color="#9C27B0"
            style={styles.progressBar}
          />
        </Card.Content>
      </Card>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F5F5F5', padding: 16 },
  centered: { flex: 1, justifyContent: 'center', alignItems: 'center' },
  greeting: { fontSize: 24, fontWeight: 'bold', marginBottom: 16 },
  card: { marginBottom: 12 },
  progressBar: { marginTop: 8 },
});
