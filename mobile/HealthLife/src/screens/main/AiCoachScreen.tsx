import React, { useState, useRef, useEffect } from 'react';
import { View, StyleSheet, ScrollView, Alert, Platform } from 'react-native';
import { Card, Text, Button, TextInput, ActivityIndicator } from 'react-native-paper';
import { API_BASE_URL, STORAGE_KEYS } from '../../constants';
import * as SecureStore from 'expo-secure-store';
import { aiCoachApi } from '../../api/services';

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
    const eventSourceRef = useRef<any>(null);

    useEffect(() => {
        return () => {
            // Clean up SSE connection on unmount
            if (eventSourceRef.current) {
                eventSourceRef.current.close?.();
            }
        };
    }, []);

    const scrollToBottom = () => {
        setTimeout(() => scrollRef.current?.scrollToEnd({ animated: true }), 100);
    };

    const sendMessage = async () => {
        if (!message.trim() || loading) return;
        const userMsg = message.trim();
        setMessage('');
        setLoading(true);

        setChatHistory((prev) => [...prev, { role: 'user', content: userMsg }]);
        scrollToBottom();

        // Try SSE streaming first; fall back to regular request if not supported
        if (Platform.OS !== 'web') {
            await sendWithStreaming(userMsg);
        } else {
            await sendWithoutStreaming(userMsg);
        }
    };

    /**
     * Streaming via SSE — shows tokens as they arrive for a real-time feel.
     * Uses fetch with ReadableStream since EventSource doesn't support POST.
     */
    const sendWithStreaming = async (userMsg: string) => {
        const token = await SecureStore.getItemAsync(STORAGE_KEYS.ACCESS_TOKEN);

        // Add placeholder AI message that will be filled token by token
        setChatHistory((prev) => [...prev, { role: 'ai', content: '', streaming: true }]);

        try {
            const response = await fetch(`${API_BASE_URL}/api/v1/ai/chat/stream`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    Authorization: `Bearer ${token}`,
                },
                body: JSON.stringify({ message: userMsg }),
            });

            if (!response.ok || !response.body) {
                throw new Error(`HTTP ${response.status}`);
            }

            const reader = response.body.getReader();
            const decoder = new TextDecoder();
            let buffer = '';

            while (true) {
                const { done, value } = await reader.read();
                if (done) break;

                buffer += decoder.decode(value, { stream: true });
                const lines = buffer.split('\n');
                buffer = lines.pop() ?? '';

                for (const line of lines) {
                    if (line.startsWith('data:')) {
                        const token = line.slice(5).trim();
                        if (token && token !== '[DONE]') {
                            setChatHistory((prev) => {
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
                    } else if (line.startsWith('event: error')) {
                        throw new Error('Stream error from server');
                    }
                }
            }

            // Mark streaming as complete
            setChatHistory((prev) => {
                const updated = [...prev];
                const last = updated[updated.length - 1];
                if (last?.role === 'ai') {
                    updated[updated.length - 1] = { ...last, streaming: false };
                }
                return updated;
            });
        } catch (e: any) {
            // Remove the empty streaming placeholder and fall back to regular request
            setChatHistory((prev) => prev.filter((m) => !(m.role === 'ai' && m.streaming)));
            await sendWithoutStreaming(userMsg);
        } finally {
            setLoading(false);
        }
    };

    /** Fallback: regular POST request (no streaming). */
    const sendWithoutStreaming = async (userMsg: string) => {
        try {
            const { data } = await aiCoachApi.chat(userMsg);
            setChatHistory((prev) => [...prev, { role: 'ai', content: data.message }]);
            scrollToBottom();
        } catch {
            Alert.alert('Error', 'Failed to get AI response. Please try again.');
        } finally {
            setLoading(false);
        }
    };

    const QUICK_PROMPTS = [
        'How can I improve my sleep?',
        'What should I eat today?',
        'Give me a workout tip',
        'How am I doing this week?',
    ];

    return (
        <View style={styles.container}>
            <Text style={styles.title}>AI Coach 🤖</Text>

            <ScrollView ref={scrollRef} style={styles.chatArea}>
                {chatHistory.length === 0 && (
                    <View style={styles.emptyState}>
                        <Text style={styles.emptyText}>
                            Ask me anything about your health! I know your recent data.
                        </Text>
                        <View style={styles.quickPrompts}>
                            {QUICK_PROMPTS.map((p) => (
                                <Button
                                    key={p}
                                    mode="outlined"
                                    compact
                                    onPress={() => {
                                        setMessage(p);
                                    }}
                                    style={styles.quickBtn}
                                >
                                    {p}
                                </Button>
                            ))}
                        </View>
                    </View>
                )}

                {chatHistory.map((msg, i) => (
                    <Card
                        key={i}
                        style={[styles.msgCard, msg.role === 'user' ? styles.userMsg : styles.aiMsg]}
                    >
                        <Card.Content>
                            <Text style={msg.role === 'user' ? styles.userText : styles.aiText}>
                                {msg.content}
                                {msg.streaming && <Text style={styles.cursor}>▌</Text>}
                            </Text>
                        </Card.Content>
                    </Card>
                ))}

                {loading && chatHistory[chatHistory.length - 1]?.role === 'user' && (
                    <View style={styles.typingIndicator}>
                        <ActivityIndicator size="small" />
                        <Text style={styles.typingText}>AI Coach is thinking...</Text>
                    </View>
                )}
            </ScrollView>

            <View style={styles.inputArea}>
                <TextInput
                    value={message}
                    onChangeText={setMessage}
                    placeholder="Ask your AI coach..."
                    style={styles.textInput}
                    multiline
                    maxLength={1000}
                    onSubmitEditing={sendMessage}
                />
                <Button
                    mode="contained"
                    onPress={sendMessage}
                    loading={loading}
                    disabled={!message.trim() || loading}
                    style={styles.sendBtn}
                    icon="send"
                >
                    Send
                </Button>
            </View>
        </View>
    );
}

const styles = StyleSheet.create({
    container: { flex: 1, backgroundColor: '#F5F5F5' },
    title: { fontSize: 22, fontWeight: 'bold', padding: 16, paddingBottom: 8 },
    chatArea: { flex: 1, paddingHorizontal: 16 },
    emptyState: { paddingTop: 32, alignItems: 'center' },
    emptyText: { color: '#666', textAlign: 'center', marginBottom: 16, lineHeight: 22 },
    quickPrompts: { width: '100%', gap: 8 },
    quickBtn: { marginBottom: 4 },
    msgCard: { marginBottom: 8 },
    userMsg: { backgroundColor: '#E3F2FD', marginLeft: 40 },
    aiMsg: { backgroundColor: '#E8F5E9', marginRight: 40 },
    userText: { color: '#1565C0' },
    aiText: { color: '#2E7D32', lineHeight: 22 },
    cursor: { color: '#4CAF50' },
    typingIndicator: { flexDirection: 'row', alignItems: 'center', gap: 8, padding: 8 },
    typingText: { color: '#888', fontStyle: 'italic' },
    inputArea: { flexDirection: 'row', padding: 12, alignItems: 'flex-end', gap: 8 },
    textInput: { flex: 1, maxHeight: 120 },
    sendBtn: { borderRadius: 20, marginBottom: 4 },
});
