import React, { useState, useEffect, useCallback } from 'react';
import { View, StyleSheet, ScrollView, RefreshControl, Alert } from 'react-native';
import { Card, Text, Button, TextInput } from 'react-native-paper';
import { healthApi } from '../../api/health';

interface WeightEntry {
    id: string;
    weightKg: number;
    bodyFatPct: number | null;
    recordedAt: string;
}

export default function WeightScreen() {
    const [entries, setEntries] = useState<WeightEntry[]>([]);
    const [refreshing, setRefreshing] = useState(false);
    const [showForm, setShowForm] = useState(false);
    const [weight, setWeight] = useState('');
    const [bodyFat, setBodyFat] = useState('');
    const [saving, setSaving] = useState(false);

    const load = useCallback(async () => {
        try {
            const { data } = await healthApi.getWeightHistory();
            setEntries(data as WeightEntry[]);
        } catch {}
    }, []);

    useEffect(() => { load(); }, [load]);

    const onRefresh = async () => { setRefreshing(true); await load(); setRefreshing(false); };

    const handleSave = async () => {
        const kg = parseFloat(weight);
        if (isNaN(kg) || kg <= 0 || kg > 500) {
            Alert.alert('Error', 'Please enter a valid weight (kg)');
            return;
        }
        setSaving(true);
        try {
            await healthApi.createWeight({
                weightKg: kg,
                bodyFatPct: bodyFat ? parseFloat(bodyFat) : undefined,
                recordedAt: new Date().toISOString(),
            });
            setWeight('');
            setBodyFat('');
            setShowForm(false);
            await load();
        } catch (e: any) {
            Alert.alert('Error', e.message || 'Failed to save weight');
        } finally {
            setSaving(false);
        }
    };

    const latest = entries[0];
    const previous = entries[1];
    const diff = latest && previous ? (latest.weightKg - previous.weightKg).toFixed(1) : null;

    return (
        <ScrollView
            style={styles.container}
            refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
        >
            <Text style={styles.title}>Weight Tracker</Text>

            {latest && (
                <Card style={styles.card}>
                    <Card.Content>
                        <Text style={styles.currentWeight}>{latest.weightKg} kg</Text>
                        {diff && (
                            <Text style={[styles.diff, parseFloat(diff) < 0 ? styles.down : styles.up]}>
                                {parseFloat(diff) > 0 ? '+' : ''}{diff} kg since last entry
                            </Text>
                        )}
                        {latest.bodyFatPct && (
                            <Text style={styles.bodyFat}>Body fat: {latest.bodyFatPct}%</Text>
                        )}
                        <Text style={styles.date}>
                            {new Date(latest.recordedAt).toLocaleDateString()}
                        </Text>
                    </Card.Content>
                </Card>
            )}

            <Button
                mode="contained"
                onPress={() => setShowForm(!showForm)}
                style={styles.btn}
                icon="plus"
            >
                {showForm ? 'Cancel' : 'Log Weight'}
            </Button>

            {showForm && (
                <Card style={styles.card}>
                    <Card.Title title="New Entry" />
                    <Card.Content>
                        <TextInput
                            label="Weight (kg)"
                            value={weight}
                            onChangeText={setWeight}
                            keyboardType="decimal-pad"
                            style={styles.input}
                        />
                        <TextInput
                            label="Body fat % (optional)"
                            value={bodyFat}
                            onChangeText={setBodyFat}
                            keyboardType="decimal-pad"
                            style={styles.input}
                        />
                        <Button mode="contained" onPress={handleSave} loading={saving}>
                            Save
                        </Button>
                    </Card.Content>
                </Card>
            )}

            {entries.slice(0, 20).map((e) => (
                <Card key={e.id} style={styles.historyCard}>
                    <Card.Content>
                        <View style={styles.row}>
                            <Text style={styles.historyWeight}>{e.weightKg} kg</Text>
                            <Text style={styles.historyDate}>
                                {new Date(e.recordedAt).toLocaleDateString()}
                            </Text>
                        </View>
                        {e.bodyFatPct && <Text style={styles.bodyFat}>{e.bodyFatPct}% body fat</Text>}
                    </Card.Content>
                </Card>
            ))}

            {entries.length === 0 && !showForm && (
                <Text style={styles.empty}>No weight entries yet.</Text>
            )}
        </ScrollView>
    );
}

const styles = StyleSheet.create({
    container: { flex: 1, backgroundColor: '#F5F5F5', padding: 16 },
    title: { fontSize: 22, fontWeight: 'bold', marginBottom: 12 },
    card: { marginBottom: 12 },
    historyCard: { marginBottom: 8 },
    btn: { marginBottom: 12 },
    input: { marginBottom: 8 },
    currentWeight: { fontSize: 48, fontWeight: 'bold', textAlign: 'center', color: '#333' },
    diff: { textAlign: 'center', fontSize: 16, marginTop: 4 },
    up: { color: '#F44336' },
    down: { color: '#4CAF50' },
    bodyFat: { textAlign: 'center', color: '#666', marginTop: 4 },
    date: { textAlign: 'center', color: '#999', marginTop: 4 },
    row: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
    historyWeight: { fontWeight: 'bold', fontSize: 16 },
    historyDate: { color: '#666' },
    empty: { textAlign: 'center', color: '#999', marginTop: 32 },
});
