import React, { useState } from 'react';
import { View, StyleSheet, ScrollView, Alert } from 'react-native';
import { Card, Text, Button, TextInput } from 'react-native-paper';
import Slider from '@react-native-community/slider';
import { mentalApi } from '../../api/services';

export default function MoodScreen() {
  const [moodScore, setMoodScore] = useState(5);
  const [note, setNote] = useState('');
  const [loading, setLoading] = useState(false);

  const submitMood = async () => {
    setLoading(true);
    try {
      await mentalApi.createMood({
        moodScore,
        note,
        recordedAt: new Date().toISOString(),
      });
      Alert.alert('Saved', 'Mood entry recorded');
      setNote('');
    } catch {
      Alert.alert('Error', 'Failed to save mood');
    } finally {
      setLoading(false);
    }
  };

  return (
    <ScrollView style={styles.container}>
      <Text style={styles.title}>How are you feeling?</Text>
      <Card style={styles.card}>
        <Card.Content>
          <Text style={styles.score}>{moodScore}/10</Text>
          <Slider
            value={moodScore}
            minimumValue={1}
            maximumValue={10}
            step={1}
            onValueChange={setMoodScore}
          />
          <TextInput label="Note (optional)" value={note} onChangeText={setNote} multiline style={styles.input} />
          <Button mode="contained" onPress={submitMood} loading={loading}>Save Mood</Button>
        </Card.Content>
      </Card>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F5F5F5', padding: 16 },
  title: { fontSize: 22, fontWeight: 'bold', marginBottom: 16 },
  card: { marginBottom: 12 },
  score: { fontSize: 48, textAlign: 'center', fontWeight: 'bold', color: '#4CAF50', marginBottom: 8 },
  input: { marginBottom: 12 },
});
