import React, { useState } from 'react';
import { View, StyleSheet, ScrollView, Alert } from 'react-native';
import { Card, Text, Button, TextInput } from 'react-native-paper';
import { aiCoachApi } from '../api/services';

export default function AiCoachScreen() {
  const [message, setMessage] = useState('');
  const [chatHistory, setChatHistory] = useState<{role: string; content: string}[]>([]);
  const [loading, setLoading] = useState(false);

  const sendMessage = async () => {
    if (!message.trim()) return;
    const userMsg = message;
    setChatHistory(prev => [...prev, { role: 'user', content: userMsg }]);
    setMessage('');
    setLoading(true);
    try {
      const { data } = await aiCoachApi.chat(userMsg);
      setChatHistory(prev => [...prev, { role: 'ai', content: data.message }]);
    } catch {
      Alert.alert('Error', 'Failed to get AI response');
    } finally {
      setLoading(false);
    }
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>AI Coach 🤖</Text>
      <ScrollView style={styles.chatArea}>
        {chatHistory.map((msg, i) => (
          <Card key={i} style={[styles.msgCard, msg.role === 'user' ? styles.userMsg : styles.aiMsg]}>
            <Card.Content><Text>{msg.content}</Text></Card.Content>
          </Card>
        ))}
      </ScrollView>
      <View style={styles.inputArea}>
        <TextInput value={message} onChangeText={setMessage} placeholder="Ask your AI coach..."
          style={styles.textInput} multiline />
        <Button mode="contained" onPress={sendMessage} loading={loading} style={styles.sendBtn}>Send</Button>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F5F5F5' },
  title: { fontSize: 22, fontWeight: 'bold', padding: 16 },
  chatArea: { flex: 1, padding: 16 },
  msgCard: { marginBottom: 8 },
  userMsg: { backgroundColor: '#E3F2FD', marginLeft: 40 },
  aiMsg: { backgroundColor: '#E8F5E9', marginRight: 40 },
  inputArea: { flexDirection: 'row', padding: 12, alignItems: 'center' },
  textInput: { flex: 1, marginRight: 8 },
  sendBtn: { borderRadius: 20 },
});
