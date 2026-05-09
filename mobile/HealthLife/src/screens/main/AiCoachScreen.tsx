import React, { useState, useRef, useCallback } from 'react';
import {
  View,
  StyleSheet,
  ScrollView,
  Alert,
  KeyboardAvoidingView,
  Platform,
} from 'react-native';
import { Card, Text, Button, TextInput, ActivityIndicator } from 'react-native-paper';
import * as SecureStore from 'expo-secure-store';
import { API_BASE_URL, STORAGE_KEYS } from '../../constants';

interface Message {
  role: 'user' | 'ai';
  content: string;
  streaming?: boolean;
}

export default function AiCoachScreen() {
  const [message, setMessage] = useState('');
  const [chatHistory, setChatHistory] = useState<Message[]>([]);
  const [loading, setLoading] = useState(false);
  const scrollRef = useRef<ScrollView>(null);
  const abortRef = useRef<AbortController | null>(null);

  const scrollToBottom = useCallback(() => {
    setTimeout(() => scrollRef.current?.scrollToEnd({ animated: true }), 100);
  }, []);

  const sendMessage = async () => {
    const userMsg = message.trim();
    if (!userMsg || loading) return;

    setMessage('');
    setLoading(true);

    setChatHistory(prev => [...prev, { role: 'user', content: userMsg }]);
    scrollToBottom();

    // Append an empty AI message that we'll stream into
    setChatHistory(prev => [...prev, { role: 'ai', content: '', streaming: true }]);

    try {
      const token = await SecureStore.getItemAsync(STORAGE_KEYS.ACCESS_TOKEN);

      abortRef.current = new AbortController();

      const response = await fetch(`${API_BASE_URL}/api/v1/ai/chat/stream`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify({ message: userMsg }),
        signal: abortRef.current.signal,
      });

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      const reader = response.body?.getReader();
      if (!reader) throw new Error('No response body');

      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });

        // SSE lines: "data: <token>\n\n"
        const lines = buffer.split('\n');
        buffer = lines.pop() ?? '';

        for (const line of lines) {
          if (line.startsWith('data:')) {
            const token = line.slice(5).trim();
            if (token === '[DONE]') continue;
            // Append token to the last AI message
            setChatHistory(prev => {
              const updated = [...prev];
              const last = updated[updated.length - 1];
              if (last?.role === 'ai') {
                updated[updated.length - 1] = {
                  ...last,
                  content: last.content + token,
                };
              }
              return updated;
            });
            scrollToBottom();
          }
        }
      }

      // Mark streaming as done
      setChatHistory(prev => {
        const updated = [...prev];
        const last = updated[updated.length - 1];
        if (last?.role === 'ai') {
          updated[updated.length - 1] = { ...last, streaming: false };
        }
        return updated;
      });
    } catch (err: unknown) {
      if (err instanceof Error && err.name === 'AbortError') {
        // User cancelled — leave partial message in place
        setChatHistory(prev => {
          const updated = [...prev];
          const last = updated[updated.length - 1];
          if (last?.role === 'ai') {
            updated[updated.length - 1] = { ...last, streaming: false };
          }
          return updated;
        });
      } else {
        // Remove empty AI placeholder and show error
        setChatHistory(prev => {
          const updated = [...prev];
          if (updated[updated.length - 1]?.role === 'ai' && !updated[updated.length - 1].content) {
            updated.pop();
          }
          return updated;
        });
        Alert.alert('Error', 'Failed to get AI response. Please try again.');
      }
    } finally {
      setLoading(false);
      abortRef.current = null;
    }
  };

  const cancelStream = () => {
    abortRef.current?.abort();
  };

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      keyboardVerticalOffset={88}
    >
      <Text style={styles.title}>AI Coach 🤖</Text>

      <ScrollView
        ref={scrollRef}
        style={styles.chatArea}
        contentContainerStyle={styles.chatContent}
        keyboardShouldPersistTaps="handled"
      >
        {chatHistory.length === 0 && (
          <Text style={styles.placeholder}>
            Ask me anything about your health, fitness, or nutrition goals!
          </Text>
        )}
        {chatHistory.map((msg, i) => (
          <Card
            key={i}
            style={[styles.msgCard, msg.role === 'user' ? styles.userMsg : styles.aiMsg]}
          >
            <Card.Content>
              <Text style={msg.role === 'user' ? styles.userText : styles.aiText}>
                {msg.content}
                {msg.streaming && <Text style={styles.cursor}>▋</Text>}
              </Text>
            </Card.Content>
          </Card>
        ))}
      </ScrollView>

      <View style={styles.inputArea}>
        <TextInput
          value={message}
          onChangeText={setMessage}
          placeholder="Ask your AI coach…"
          style={styles.textInput}
          multiline
          maxLength={1000}
          disabled={loading}
        />
        {loading ? (
          <View style={styles.rightActions}>
            <ActivityIndicator size="small" style={styles.spinner} />
            <Button mode="outlined" onPress={cancelStream} style={styles.cancelBtn} compact>
              Stop
            </Button>
          </View>
        ) : (
          <Button
            mode="contained"
            onPress={sendMessage}
            style={styles.sendBtn}
            disabled={!message.trim()}
          >
            Send
          </Button>
        )}
      </View>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F5F5F5' },
  title: { fontSize: 22, fontWeight: 'bold', padding: 16, paddingBottom: 8 },
  chatArea: { flex: 1 },
  chatContent: { padding: 16, paddingBottom: 8 },
  placeholder: { color: '#9E9E9E', textAlign: 'center', marginTop: 40, fontSize: 15 },
  msgCard: { marginBottom: 10, elevation: 1 },
  userMsg: { backgroundColor: '#E3F2FD', marginLeft: 40 },
  aiMsg: { backgroundColor: '#E8F5E9', marginRight: 40 },
  userText: { color: '#1565C0' },
  aiText: { color: '#2E7D32' },
  cursor: { opacity: 0.7 },
  inputArea: { flexDirection: 'row', padding: 12, alignItems: 'flex-end', backgroundColor: '#fff', borderTopWidth: 1, borderTopColor: '#E0E0E0' },
  textInput: { flex: 1, marginRight: 8, maxHeight: 120, backgroundColor: '#fff' },
  rightActions: { flexDirection: 'row', alignItems: 'center' },
  spinner: { marginRight: 4 },
  cancelBtn: { borderRadius: 20 },
  sendBtn: { borderRadius: 20 },
});
