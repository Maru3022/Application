import React, { useState, useEffect, useCallback } from 'react';
import { View, StyleSheet, ScrollView, RefreshControl, Alert } from 'react-native';
import { Card, Text, Button, ProgressBar, FAB } from 'react-native-paper';
import { healthApi } from '../../api/health';

const WATER_GOAL_ML = 2000;
const QUICK_ADD_OPTIONS = [150, 200, 250, 330, 500];

export default function WaterScreen() {
    const [totalToday, setTotalToday] = useState(0);
    const [refreshing, setRefreshing] = useState(false);
    const [adding, setAdding] = useState(false);

    const load = useCallback(async () => {
        try {
            const { data } = await healthApi.getWaterToday();
            setTotalToday(data as number);
        } catch {}
    }, []);

    useEffect(() => { load(); }, [load]);

    const onRefresh = async () => { setRefreshing(true); await load(); setRefreshing(false); };

    const addWater = async (ml: number) => {
        setAdding(true);
        try {
            await healthApi.addWater({ amountMl: ml, recordedAt: new Date().toISOString() });
            await load();
        } catch (e: any) {
            Alert.alert('Error', e.message || 'Failed to log water');
        } finally {
            setAdding(false);
        }
    };

    const progress = Math.min(totalToday / WATER_GOAL_ML, 1);
    const remaining = Math.max(WATER_GOAL_ML - totalToday, 0);

    return (
        <ScrollView
            style={styles.container}
            refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} />}
        >
            <Text style={styles.title}>Water Intake</Text>

            <Card style={styles.card}>
                <Card.Content>
                    <Text style={styles.amount}>{totalToday} ml</Text>
                    <Text style={styles.goal}>Goal: {WATER_GOAL_ML} ml</Text>
                    <ProgressBar progress={progress} color="#2196F3" style={styles.bar} />
                    <Text style={styles.remaining}>
                        {remaining > 0 ? `${remaining} ml remaining` : '🎉 Goal reached!'}
                    </Text>
                </Card.Content>
            </Card>

            <Card style={styles.card}>
                <Card.Title title="Quick Add" />
                <Card.Content>
                    <View style={styles.quickAdd}>
                        {QUICK_ADD_OPTIONS.map((ml) => (
                            <Button
                                key={ml}
                                mode="outlined"
                                onPress={() => addWater(ml)}
                                disabled={adding}
                                style={styles.quickBtn}
                                compact
                            >
                                +{ml}ml
                            </Button>
                        ))}
                    </View>
                </Card.Content>
            </Card>

            <View style={styles.glassRow}>
                {Array.from({ length: 8 }).map((_, i) => (
                    <View
                        key={i}
                        style={[
                            styles.glass,
                            i < Math.floor(totalToday / 250) && styles.glassFull,
                        ]}
                    />
                ))}
            </View>
            <Text style={styles.glassLabel}>
                {Math.floor(totalToday / 250)} / 8 glasses (250ml each)
            </Text>
        </ScrollView>
    );
}

const styles = StyleSheet.create({
    container: { flex: 1, backgroundColor: '#F5F5F5', padding: 16 },
    title: { fontSize: 22, fontWeight: 'bold', marginBottom: 12 },
    card: { marginBottom: 12 },
    amount: { fontSize: 48, fontWeight: 'bold', color: '#2196F3', textAlign: 'center' },
    goal: { textAlign: 'center', color: '#666', marginBottom: 8 },
    bar: { height: 12, borderRadius: 6 },
    remaining: { textAlign: 'center', marginTop: 8, color: '#555' },
    quickAdd: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
    quickBtn: { flex: 1, minWidth: 80 },
    glassRow: { flexDirection: 'row', justifyContent: 'center', gap: 8, marginTop: 8 },
    glass: {
        width: 28, height: 40, borderRadius: 4,
        borderWidth: 2, borderColor: '#2196F3', backgroundColor: 'transparent',
    },
    glassFull: { backgroundColor: '#2196F3' },
    glassLabel: { textAlign: 'center', color: '#666', marginTop: 8 },
});
