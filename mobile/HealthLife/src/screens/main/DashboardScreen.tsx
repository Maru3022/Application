import React, { useState, useEffect } from 'react';
import { View, StyleSheet, ScrollView, RefreshControl } from 'react-native';
import { Card, Text, ProgressBar, IconButton } from 'react-native-paper';
import { healthApi } from '../api/health';

export default function DashboardScreen() {
  const [waterToday, setWaterToday] = useState(0);
  const [refreshing, setRefreshing] = useState(false);

  const loadData = async () => {
    try {
      const { data } = await healthApi.getWaterToday();
      setWaterToday(data);
    } catch {}
  };

  useEffect(() => { loadData(); }, []);

  const onRefresh = async () => {
    setRefreshing(true);
    await loadData();
    setRefreshing(false);
  };

  return (
    <ScrollView refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />} style={styles.container}>
      <Text style={styles.greeting}>Good day! 👋</Text>

      <Card style={styles.card}>
        <Card.Title title="Water Intake" left={(props: any) => <IconButton icon="water" {...props} />} />
        <Card.Content>
          <Text>{waterToday} / 2000 ml</Text>
          <ProgressBar progress={Math.min(waterToday / 2000, 1)} color="#2196F3" />
        </Card.Content>
      </Card>

      <Card style={styles.card}>
        <Card.Title title="Today's Steps" left={(props: any) => <IconButton icon="walk" {...props} />} />
        <Card.Content>
          <Text>Track your daily activity</Text>
          <ProgressBar progress={0.5} color="#4CAF50" />
        </Card.Content>
      </Card>

      <Card style={styles.card}>
        <Card.Title title="Sleep" left={(props: any) => <IconButton icon="bed" {...props} />} />
        <Card.Content>
          <Text>Last night: 7h 30m</Text>
          <ProgressBar progress={0.94} color="#9C27B0" />
        </Card.Content>
      </Card>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F5F5F5', padding: 16 },
  greeting: { fontSize: 24, fontWeight: 'bold', marginBottom: 16 },
  card: { marginBottom: 12 },
});
