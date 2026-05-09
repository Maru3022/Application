import React, { useState, useEffect } from 'react';
import { View, StyleSheet, ScrollView, Alert } from 'react-native';
import { Card, Text, Button, TextInput, SegmentedButtons, ActivityIndicator, Chip } from 'react-native-paper';
import { nutritionApi } from '../../api/services';

interface FoodItem {
    id: string;
    name: string;
    caloriesPer100g: number;
    proteinPer100g: number;
    carbsPer100g: number;
    fatPer100g: number;
    source?: string;
}

interface FoodLogEntry {
    id: string;
    foodName: string;
    weightGrams: number;
    calories: number;
    mealType: string;
}

export default function NutritionScreen() {
    const [searchQuery, setSearchQuery] = useState('');
    const [foods, setFoods] = useState<FoodItem[]>([]);
    const [todayLog, setTodayLog] = useState<FoodLogEntry[]>([]);
    const [mealType, setMealType] = useState('lunch');
    const [searching, setSearching] = useState(false);
    const [addingId, setAddingId] = useState<string | null>(null);
    const [weightGrams, setWeightGrams] = useState<Record<string, string>>({});

    useEffect(() => {
        loadTodayLog();
    }, []);

    const loadTodayLog = async () => {
        try {
            const { data } = await nutritionApi.getFoodLogToday();
            setTodayLog(data as FoodLogEntry[]);
        } catch {}
    };

    const searchFoods = async () => {
        if (!searchQuery.trim()) return;
        setSearching(true);
        try {
            const { data } = await nutritionApi.searchFoods(searchQuery);
            setFoods(data as FoodItem[]);
            if ((data as FoodItem[]).length === 0) {
                Alert.alert('No results', `No foods found for "${searchQuery}". Try a different search term.`);
            }
        } catch {
            Alert.alert('Error', 'Search failed. Please try again.');
        } finally {
            setSearching(false);
        }
    };

    const addToLog = async (food: FoodItem) => {
        const grams = parseFloat(weightGrams[food.id] || '100');
        if (isNaN(grams) || grams <= 0) {
            Alert.alert('Error', 'Please enter a valid weight in grams');
            return;
        }
        setAddingId(food.id);
        try {
            await nutritionApi.addFoodLog({
                foodId: food.id,
                weightGrams: grams,
                mealType,
                consumedAt: new Date().toISOString(),
            });
            Alert.alert('Added', `${food.name} (${grams}g) added to ${mealType}`);
            await loadTodayLog();
        } catch (e: any) {
            Alert.alert('Error', e.response?.data?.detail || 'Failed to add food');
        } finally {
            setAddingId(null);
        }
    };

    const totalCalories = todayLog.reduce((sum, e) => sum + (e.calories || 0), 0);

    return (
        <ScrollView style={styles.container}>
            <Text style={styles.title}>Nutrition</Text>

            {/* Today's summary */}
            {todayLog.length > 0 && (
                <Card style={styles.summaryCard}>
                    <Card.Content>
                        <Text style={styles.summaryTitle}>Today: {Math.round(totalCalories)} kcal</Text>
                        <Text style={styles.summaryDetail}>{todayLog.length} entries logged</Text>
                    </Card.Content>
                </Card>
            )}

            {/* Meal type selector */}
            <SegmentedButtons
                value={mealType}
                onValueChange={setMealType}
                buttons={[
                    { value: 'breakfast', label: 'Breakfast' },
                    { value: 'lunch', label: 'Lunch' },
                    { value: 'dinner', label: 'Dinner' },
                    { value: 'snack', label: 'Snack' },
                ]}
                style={styles.segment}
            />

            {/* Search */}
            <TextInput
                label="Search food (e.g. banana, chicken breast)..."
                value={searchQuery}
                onChangeText={setSearchQuery}
                onSubmitEditing={searchFoods}
                right={<TextInput.Icon icon="magnify" onPress={searchFoods} />}
                style={styles.input}
            />
            {searching && <ActivityIndicator style={styles.loader} />}

            {/* Search results with Add button */}
            {foods.map((food) => (
                <Card key={food.id} style={styles.card}>
                    <Card.Content>
                        <View style={styles.foodHeader}>
                            <View style={styles.foodInfo}>
                                <Text style={styles.foodName}>{food.name}</Text>
                                <Text style={styles.macros}>
                                    {food.caloriesPer100g?.toFixed(0)} kcal · P:{food.proteinPer100g?.toFixed(1)}g ·
                                    C:{food.carbsPer100g?.toFixed(1)}g · F:{food.fatPer100g?.toFixed(1)}g
                                    {' '}(per 100g)
                                </Text>
                                {food.source === 'openfoodfacts' && (
                                    <Chip compact style={styles.sourceChip}>OpenFoodFacts</Chip>
                                )}
                            </View>
                        </View>
                        <View style={styles.addRow}>
                            <TextInput
                                label="Grams"
                                value={weightGrams[food.id] ?? '100'}
                                onChangeText={(v) => setWeightGrams((prev) => ({ ...prev, [food.id]: v }))}
                                keyboardType="decimal-pad"
                                style={styles.gramsInput}
                                dense
                            />
                            <Button
                                mode="contained"
                                onPress={() => addToLog(food)}
                                loading={addingId === food.id}
                                disabled={addingId !== null}
                                style={styles.addBtn}
                                icon="plus"
                            >
                                Add
                            </Button>
                        </View>
                    </Card.Content>
                </Card>
            ))}

            {/* Today's log */}
            {todayLog.length > 0 && (
                <>
                    <Text style={styles.sectionTitle}>Today's Log</Text>
                    {todayLog.map((entry) => (
                        <Card key={entry.id} style={styles.logCard}>
                            <Card.Content>
                                <View style={styles.logRow}>
                                    <Text style={styles.logName}>{entry.foodName}</Text>
                                    <Text style={styles.logCal}>{Math.round(entry.calories)} kcal</Text>
                                </View>
                                <Text style={styles.logDetail}>
                                    {entry.weightGrams}g · {entry.mealType}
                                </Text>
                            </Card.Content>
                        </Card>
                    ))}
                </>
            )}
        </ScrollView>
    );
}

const styles = StyleSheet.create({
    container: { flex: 1, backgroundColor: '#F5F5F5', padding: 16 },
    title: { fontSize: 22, fontWeight: 'bold', marginBottom: 12 },
    summaryCard: { marginBottom: 12, backgroundColor: '#E8F5E9' },
    summaryTitle: { fontSize: 18, fontWeight: 'bold', color: '#2E7D32' },
    summaryDetail: { color: '#555', marginTop: 2 },
    segment: { marginBottom: 12 },
    input: { marginBottom: 8 },
    loader: { marginVertical: 8 },
    card: { marginBottom: 8 },
    foodHeader: { marginBottom: 8 },
    foodInfo: { flex: 1 },
    foodName: { fontWeight: 'bold', fontSize: 16, marginBottom: 2 },
    macros: { color: '#666', fontSize: 12 },
    sourceChip: { marginTop: 4, alignSelf: 'flex-start', backgroundColor: '#E3F2FD' },
    addRow: { flexDirection: 'row', alignItems: 'center', gap: 8, marginTop: 8 },
    gramsInput: { width: 90 },
    addBtn: { flex: 1 },
    sectionTitle: { fontSize: 16, fontWeight: 'bold', marginTop: 16, marginBottom: 8, color: '#555' },
    logCard: { marginBottom: 6, backgroundColor: '#fff' },
    logRow: { flexDirection: 'row', justifyContent: 'space-between' },
    logName: { fontWeight: 'bold', flex: 1 },
    logCal: { color: '#FF9800', fontWeight: 'bold' },
    logDetail: { color: '#888', fontSize: 12, marginTop: 2 },
});
