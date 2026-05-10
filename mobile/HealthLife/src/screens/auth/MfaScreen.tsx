import React, { useState } from 'react';
import { Alert, KeyboardAvoidingView, Platform, StyleSheet } from 'react-native';
import { Button, Text, TextInput } from 'react-native-paper';
import { useAuthStore } from '../../store/authStore';

export default function MfaScreen() {
  const [code, setCode] = useState('');
  const [loading, setLoading] = useState(false);
  const verifyMfa = useAuthStore((s) => s.verifyMfa);

  const handleVerify = async () => {
    if (!code || code.length < 6) {
      Alert.alert('Error', 'Enter the 6-digit code from your authenticator app');
      return;
    }
    setLoading(true);
    try {
      await verifyMfa(code);
    } catch (err: any) {
      Alert.alert('Verification failed', err.message || 'Invalid MFA code');
    } finally {
      setLoading(false);
    }
  };

  return (
    <KeyboardAvoidingView style={styles.container} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <Text style={styles.title}>Two-Factor Authentication</Text>
      <Text style={styles.subtitle}>Enter the 6-digit code to continue</Text>
      <TextInput
        label="MFA Code"
        value={code}
        onChangeText={setCode}
        keyboardType="number-pad"
        maxLength={6}
        style={styles.input}
      />
      <Button mode="contained" onPress={handleVerify} loading={loading}>
        Verify
      </Button>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, padding: 24, justifyContent: 'center', backgroundColor: '#F5F5F5' },
  title: { fontSize: 26, fontWeight: 'bold', textAlign: 'center', marginBottom: 8, color: '#333' },
  subtitle: { fontSize: 14, textAlign: 'center', color: '#666', marginBottom: 20 },
  input: { marginBottom: 16 },
});
