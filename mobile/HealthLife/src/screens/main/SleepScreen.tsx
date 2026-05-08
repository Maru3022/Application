import React, { useState, useEffect, useCallback } from 'react';
import { View, StyleSheet, ScrollView, RefreshControl, Alert } from 'react-native';
import { Card, Text, Button, TextInput, ProgressBar, Chip } from 'react-native-paper';
import DateTimePicker from '@react-native-community/datetimepicker';
import { healthApi } from '../../api/health';

interface SleepEntry {
    id: string;
    sleepStart: string;
    sleepEnd: string;
    durationMin: number;
    quality: number | null;
    notes: string | null;
}

interface SleepStats {
    entryCount: number;
    avgDurationMin: number;
    avgQuality: number | null;
    goalAchievementPct: number;
}

const QUALITY_LABELS: Record<number, string> = {
    1: '😴 Very Poor', 2: '😞 Poor', 3: '😐 Fair',
    4: '🙂 Good', 5: '😊 Great',
};

export default function SleepScreen() {
    const [entries, setEntries] = useState<SleepEntry[]>([]);
    const [stats, setStats] = useState<SleepStats | null>(null);
    const [refreshing, setRefreshing] = useState(false);
    const [showForm, setShowForm] = useState(false);
    const [sleepStart, setSleepStart] = useState(new Date());
    const [sleepEnd, setSleepEnd] = useState(new Date());
    const [quality, setQuality] = useState(3);
    const [notes, setNotes] = useState('');
    const [saving, setSaving] = useState(false);

    const load = useCallback(async () => {
        try {
            const [entriesRes, statsRes] = await Promise.allSettled([
                healthApi.getSleepEntries(),
                healthApi.getSleepStats(),
            ]);
            if (entriesRes.status === 'fulfilled') setEntries(entriesRes.value.data as SleepEntry[]);
            if (statsRes.status === 'fulfilled') setStats(statsRes.value.data as SleepStats);
        } catch {}
    }, []);

    useEffect(() => { load(); }, [load]);

    const onRefresh = async () => { setRefreshing(true); await load(); setRefreshing(false); };

    const handleSave = async () => {
        if (sleepEnd <= sleepStart) {
            Alert.alert('Error', 'Wake-up time must be after bedtime');
            return;
        }
        setSaving(true);
        try {
            await healthApi.createSleep({
                sleepStart: sleepStart.toISOString(),
                sleepEnd: sleepEnd.toISOString(),
                quality,
                notes: notes || undefined,
            });
            setShowForm(false);
            setNotes('');
            setQuality(3);
            await load();
        } catch (e: any) {
            Alert.alert('Error', e.message || 'Failed to save sleep entry');
        } finally {
            setSaving(false);
        }
    };

    const formatDuration = (min: number) => `${Math.floor(min / 60)}h ${min % 60}m`;

    return (
        <ScrollView
            style={styles.container}
            refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
        >
            <Text style={styles.title}>Sleep Tracker</Text>

            {/* Stats card */}
            {stats && stats.entryCount > 0 && (
                <Card style={styles.card}>
                    <Card.Title title="30-Day Stats" />
                    <Card.Content>
                        <Text>Avg duration: {formatDuration(Math.round(stats.avgDurationMin))}</Text>
                        {stats.avgQuality && <Text>Avg quality: {stats.avgQuality.toFixed(1)} / 5</Text>}
                        <Text style={styles.label}>Goal achievement (≥7h)</Text>
                        <ProgressBar
                            progress={stats.goalAchievementPct / 100}
                            color="#9C27B0"
                            style={styles.bar}
                        />
                        <Text style={styles.pct}>{stats.goalAchievementPct.toFixed(0)}%</Text>
                    </Card.Content>
                </Card>
            )}

            {/* Log sleep button */}
            <Button
                mode="contained"
                onPress={() => setShowForm(!showForm)}
                style={styles.btn}
                icon="plus"
            >
                {showForm ? 'Cancel' : 'Log Sleep'}
            </Button>

            {/* Log form */}
            {showForm && (
                <Card style={styles.card}>
                    <Card.Title title="New Sleep Entry" />
                    <Card.Content>
                        <Text style={styles.label}>Bedtime</Text>
                        <DateTimePicker
                            value={sleepStart}
                            mode="datetime"
                            onChange={(_, d) => d && setSleepStart(d)}
                        />
                        <Text style={styles.label}>Wake-up time</Text>
                        <DateTimePicker
                            value={sleepEnd}
                            mode="datetime"
                            onChange={(_, d) => d && setSleepEnd(d)}
                        />
                        <Text style={styles.label}>Quality</Text>
                        <View style={styles.chips}>
                            {[1, 2, 3, 4, 5].map((q) => (
                                <Chip
                                    key={q}
                                    selected={quality === q}
                                    onPress={() => setQuality(q)}
                                    style={styles.chip}
                                >
                                    {q}
                                </Chip>
                            ))}
                        </View>
                        <Text style={styles.qualityLabel}>{QUALITY_LABELS[quality]}</Text>
                        <TextInput
                            label="Notes (optional)"
                            value={notes}
                            onChangeText={setNotes}
                            multiline
                            style={styles.input}
                        />
                        <Button mode="contained" onPress={handleSave} loading={saving} style={styles.btn}>
                            Save
                        </Button>
                    </Card.Content>
                </Card>
            )}

            {/* History */}
            {entries.map((e) => (
                <Card key={e.id} style={styles.card}>
                    <Card.Content>
                        <Text style={styles.entryDate}>
                            {new Date(e.sleepStart).toLocaleDateString('en-US', {
                                weekday: 'short', month: 'short', day: 'numeric',
                            })}
                        </Text>
                        <Text>Duration: {formatDuration(e.durationMin)}</Text>
                        {e.quality && <Text>Quality: {QUALITY_LABELS[e.quality] ?? e.quality}</Text>}
                        {e.notes && <Text style={styles.notes}>{e.notes}</Text>}
                    </Card.Content>
                </Card>
            ))}

            {entries.length === 0 && !showForm && (
                <Text style={styles.empty}>No sleep entries yet. Tap "Log Sleep" to start.</Text>
            )}
        </ScrollView>
    );
}

const styles = StyleSheet.create({
    container: { flex: 1, backgroundColor: '#F5F5F5', padding: 16 },
    title: { fontSize: 22, fontWeight: 'bold', marginBottom: 12 },
    card: { marginBottom: 12 },
    btn: { marginBottom: 12 },
    label: { marginTop: 12, marginBottom: 4, color: '#555' },
    bar: { marginTop: 4 },
    pct: { textAlign: 'right', color: '#9C27B0', marginTop: 2 },
    chips: { flexDirection: 'row', gap: 8, marginBottom: 4 },
    chip: { flex: 1 },
    qualityLabel: { color: '#555', marginBottom: 8 },
    input: { marginBottom: 8 },
    entryDate: { fontWeight: 'bold', marginBottom: 4 },
    notes: { color: '#666', fontStyle: 'italic', marginTop: 4 },
    empty: { textAlign: 'center', color: '#999', marginTop: 32 },
});
