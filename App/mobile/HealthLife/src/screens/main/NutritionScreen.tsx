import React, { useState } from 'react';
import { View, StyleSheet, ScrollView, Alert } from 'react-native';
import { Card, Text, Button, TextInput, SegmentedButtons } from 'react-native-paper';
import { nutritionApi } from '../api/services';

export default function NutritionScreen() {
  const [searchQuery, setSearchQuery] = useState('');
  const [foods, setFoods] = useState<any[]>([]);
  const [mealType, setMealType] = useState('lunch');

  const searchFoods = async () => {
    try {
      const { data } = await nutritionApi.searchFoods(searchQuery);
      setFoods(data);
    } catch {
      Alert.alert('Error', 'Search failed');
    }
  };

  return (
    <ScrollView style={styles.container}>
      <Text style={styles.title}>Nutrition</Text>
      <TextInput label="Search food..." value={searchQuery} onChangeText={setSearchQuery}
        right={<TextInput.Icon icon="magnify" onPress={searchFoods} />} style={styles.input} />
      <SegmentedButtons value={mealType} onValueChange={setMealType} buttons={[
        { value: 'breakfast', label: 'Breakfast' },
        { value: 'lunch', label: 'Lunch' },
        { value: 'dinner', label: 'Dinner' },
        { value: 'snack', label: 'Snack' },
      ]} style={styles.segment} />
      {foods.map((food: any) => (
        <Card key={food.id} style={styles.card}>
          <Card.Content>
            <Text style={styles.foodName}>{food.name}</Text>
            <Text>{food.caloriesPer100g} kcal/100g</Text>
          </Card.Content>
        </Card>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F5F5F5', padding: 16 },
  title: { fontSize: 22, fontWeight: 'bold', marginBottom: 16 },
  input: { marginBottom: 12 },
  segment: { marginBottom: 12 },
  card: { marginBottom: 8 },
  foodName: { fontWeight: 'bold', fontSize: 16 },
});
