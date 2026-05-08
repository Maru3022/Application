import React, { useState, useEffect, useCallback } from 'react';
import { View, StyleSheet, ScrollView, RefreshControl, Alert } from 'react-native';
import { Card, Text, Button, TextInput, ProgressBar } from 'react-native-paper';
import { healthApi } from '../../api/health';

interface ActivityData {
    steps: number | null;
    caloriesBurned: number | null;
    activeMinutes: number | null;
    distanceM: number | null;
}

const STEPS_GOAL = 10000;
const ACTIVE_MINUTES_GOAL = 30;

export default function ActivityScreen() {
    const [today, setToday] = useState<ActivityData>({
        steps: null, caloriesBurned: null, activeMinutes: null, distanceM: null,
    });
    const [refreshing, setRefreshing] = useState(false);
    const [showForm, setShowForm] = useState(false);
    const [steps, setSteps] = useState('');
    const [calories, setCalories] = useState('');
    const [activeMin, setActiveMin] = useState('');
    const [distance, setDistance] = useState('');
    const [saving, setSaving] = useState(false);

    const load = useCallback(async () => {
        try {
            const { data } = await healthApi.getActivityToday();
            setToday(data as ActivityData);
        } catch {}
    }, []);

    useEffect(() => { load(); }, [load]);

    const onRefresh = async () => { setRefreshing(true); await load(); setRefreshing(false); };

    const handleSync = async () => {
        setSaving(true);
        try {
            await healthApi.syncActivity({
                steps: steps ? parseInt(steps) : undefined,
                caloriesBurned: calories ? parseInt(calories) : undefined,
                activeMinutes: activeMin ? parseInt(activeMin) : undefined,
                distanceM: distance ? parseInt(distance) : undefined,
                date: new Date().toISOString().split('T')[0],
                source: 'manual',
            });
            setShowForm(false);
            setSteps(''); setCalories(''); setActiveMin(''); setDistance('');
            await load();
        } catch (e: any) {
            Alert.alert('Error', e.message || 'Failed to sync activity');
        } finally {
            setSaving(false);
        }
    };

    const stepsProgress = today.steps ? Math.min(today.steps / STEPS_GOAL, 1) : 0;
    const activeProgress = today.activeMinutes
        ? Math.min(today.activeMinutes / ACTIVE_MINUTES_GOAL, 1)
        : 0;

    return (
        <ScrollView
            style={styles.container}
            refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
        >
            <Text style={styles.title}>Activity</Text>

            <Card style={styles.card}>
                <Card.Title title="Today's Activity" />
                <Card.Content>
                    <View style={styles.stat}>
                        <Text style={styles.statLabel}>Steps</Text>
                        <Text style={styles.statValue}>
                            {today.steps?.toLocaleString() ?? '—'} / {STEPS_GOAL.toLocaleString()}
                        </Text>
                        <ProgressBar progress={stepsProgress} color="#4CAF50" style={styles.bar} />
                    </View>

                    <View style={styles.stat}>
                        <Text style={styles.statLabel}>Active Minutes</Text>
                        <Text style={styles.statValue}>
                            {today.activeMinutes ?? '—'} / {ACTIVE_MINUTES_GOAL} min
                        </Text>
                        <ProgressBar progress={activeProgress} color="#FF9800" style={styles.bar} />
                    </View>

                    <View style={styles.row}>
                        <View style={styles.mini}>
                            <Text style={styles.miniLabel}>Calories</Text>
                            <Text style={styles.miniValue}>{today.caloriesBurned ?? '—'}</Text>
                        </View>
                        <View style={styles.mini}>
                            <Text style={styles.miniLabel}>Distance</Text>
                            <Text style={styles.miniValue}>
                                {today.distanceM ? `${(today.distanceM / 1000).toFixed(1)} km` : '—'}
                            </Text>
                        </View>
                    </View>
                </Card.Content>
            </Card>

            <Button
                mode="contained"
                onPress={() => setShowForm(!showForm)}
                style={styles.btn}
                icon="pencil"
            >
                {showForm ? 'Cancel' : 'Log Activity'}
            </Button>

            {showForm && (
                <Card style={styles.card}>
                    <Card.Title title="Manual Entry" />
                    <Card.Content>
                        <TextInput label="Steps" value={steps} onChangeText={setSteps}
                            keyboardType="number-pad" style={styles.input} />
                        <TextInput label="Calories burned" value={calories} onChangeText={setCalories}
                            keyboardType="number-pad" style={styles.input} />
                        <TextInput label="Active minutes" value={activeMin} onChangeText={setActiveMin}
                            keyboardType="number-pad" style={styles.input} />
                        <TextInput label="Distance (meters)" value={distance} onChangeText={setDistance}
                            keyboardType="number-pad" style={styles.input} />
                        <Button mode="contained" onPress={handleSync} loading={saving}>
                            Save
                        </Button>
                    </Card.Content>
                </Card>
            )}
        </ScrollView>
    );
}

const styles = StyleSheet.create({
    container: { flex: 1, backgroundColor: '#F5F5F5', padding: 16 },
    title: { fontSize: 22, fontWeight: 'bold', marginBottom: 12 },
    card: { marginBottom: 12 },
    btn: { marginBottom: 12 },
    input: { marginBottom: 8 },
    stat: { marginBottom: 16 },
    statLabel: { color: '#555', marginBottom: 2 },
    statValue: { fontWeight: 'bold', marginBottom: 4 },
    bar: { height: 8, borderRadius: 4 },
    row: { flexDirection: 'row', gap: 16, marginTop: 8 },
    mini: { flex: 1, alignItems: 'center', padding: 12, backgroundColor: '#fff', borderRadius: 8 },
    miniLabel: { color: '#666', fontSize: 12 },
    miniValue: { fontWeight: 'bold', fontSize: 20, marginTop: 4 },
});
