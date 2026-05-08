import React, { useState, useEffect } from 'react';
import { View, StyleSheet, ScrollView, Alert } from 'react-native';
import { List, Switch, Button, Text, Divider, TextInput } from 'react-native-paper';
import { userApi } from '../../api/services';
import { useAuthStore } from '../../store/authStore';

interface Goals {
    dailySteps: number | null;
    sleepMinutes: number | null;
    waterMl: number | null;
    targetWeightKg: number | null;
}

export default function SettingsScreen() {
    const logout = useAuthStore((s) => s.logout);
    const [notifications, setNotifications] = useState(true);
    const [goals, setGoals] = useState<Goals>({
        dailySteps: 10000, sleepMinutes: 480, waterMl: 2000, targetWeightKg: null,
    });
    const [editingGoals, setEditingGoals] = useState(false);
    const [saving, setSaving] = useState(false);

    useEffect(() => {
        userApi.getGoals()
            .then(({ data }) => setGoals(data as Goals))
            .catch(() => {});
    }, []);

    const handleSaveGoals = async () => {
        setSaving(true);
        try {
            await userApi.updateGoals(goals);
            setEditingGoals(false);
            Alert.alert('Saved', 'Your goals have been updated.');
        } catch (e: any) {
            Alert.alert('Error', e.message || 'Failed to save goals');
        } finally {
            setSaving(false);
        }
    };

    const handleDeleteAccount = () => {
        Alert.alert(
            'Delete Account',
            'This will permanently delete your account and all data. This cannot be undone.',
            [
                { text: 'Cancel', style: 'cancel' },
                {
                    text: 'Delete',
                    style: 'destructive',
                    onPress: async () => {
                        try {
                            await userApi.deleteAccount();
                            await logout();
                        } catch (e: any) {
                            Alert.alert('Error', e.message || 'Failed to delete account');
                        }
                    },
                },
            ],
        );
    };

    const handleLogout = () => {
        Alert.alert('Sign Out', 'Are you sure you want to sign out?', [
            { text: 'Cancel', style: 'cancel' },
            { text: 'Sign Out', style: 'destructive', onPress: logout },
        ]);
    };

    return (
        <ScrollView style={styles.container}>
            <Text style={styles.section}>Preferences</Text>
            <List.Item
                title="Push Notifications"
                description="Reminders and health tips"
                left={(p: any) => <List.Icon icon="bell" {...p} />}
                right={() => <Switch value={notifications} onValueChange={setNotifications} />}
            />

            <Divider />
            <Text style={styles.section}>Health Goals</Text>

            {editingGoals ? (
                <View style={styles.goalsForm}>
                    <TextInput
                        label="Daily Steps"
                        value={goals.dailySteps?.toString() ?? ''}
                        onChangeText={(v) => setGoals({ ...goals, dailySteps: parseInt(v) || null })}
                        keyboardType="number-pad"
                        style={styles.input}
                    />
                    <TextInput
                        label="Sleep Goal (minutes)"
                        value={goals.sleepMinutes?.toString() ?? ''}
                        onChangeText={(v) => setGoals({ ...goals, sleepMinutes: parseInt(v) || null })}
                        keyboardType="number-pad"
                        style={styles.input}
                    />
                    <TextInput
                        label="Water Goal (ml)"
                        value={goals.waterMl?.toString() ?? ''}
                        onChangeText={(v) => setGoals({ ...goals, waterMl: parseInt(v) || null })}
                        keyboardType="number-pad"
                        style={styles.input}
                    />
                    <TextInput
                        label="Target Weight (kg, optional)"
                        value={goals.targetWeightKg?.toString() ?? ''}
                        onChangeText={(v) => setGoals({ ...goals, targetWeightKg: parseFloat(v) || null })}
                        keyboardType="decimal-pad"
                        style={styles.input}
                    />
                    <View style={styles.row}>
                        <Button mode="outlined" onPress={() => setEditingGoals(false)} style={styles.halfBtn}>
                            Cancel
                        </Button>
                        <Button mode="contained" onPress={handleSaveGoals} loading={saving} style={styles.halfBtn}>
                            Save
                        </Button>
                    </View>
                </View>
            ) : (
                <>
                    <List.Item
                        title="Daily Steps"
                        description={`${goals.dailySteps?.toLocaleString() ?? '—'} steps`}
                        left={(p: any) => <List.Icon icon="walk" {...p} />}
                    />
                    <List.Item
                        title="Sleep Goal"
                        description={`${goals.sleepMinutes ? Math.floor(goals.sleepMinutes / 60) + 'h ' + (goals.sleepMinutes % 60) + 'm' : '—'}`}
                        left={(p: any) => <List.Icon icon="bed" {...p} />}
                    />
                    <List.Item
                        title="Water Goal"
                        description={`${goals.waterMl ?? '—'} ml`}
                        left={(p: any) => <List.Icon icon="water" {...p} />}
                    />
                    <Button mode="outlined" onPress={() => setEditingGoals(true)} style={styles.btn}>
                        Edit Goals
                    </Button>
                </>
            )}

            <Divider />
            <Text style={styles.section}>Account</Text>
            <List.Item
                title="Privacy & Data Export"
                description="Download all your data (GDPR)"
                left={(p: any) => <List.Icon icon="shield-lock" {...p} />}
                onPress={() => Alert.alert('Data Export', 'Your data export will be emailed to you within 24 hours.')}
            />
            <List.Item
                title="Help & Support"
                left={(p: any) => <List.Icon icon="help-circle" {...p} />}
                onPress={() => {}}
            />

            <Button mode="outlined" onPress={handleLogout} style={[styles.btn, styles.logoutBtn]} textColor="#F44336">
                Sign Out
            </Button>
            <Button mode="text" onPress={handleDeleteAccount} textColor="#F44336" style={styles.deleteBtn}>
                Delete Account
            </Button>
        </ScrollView>
    );
}

const styles = StyleSheet.create({
    container: { flex: 1, backgroundColor: '#F5F5F5' },
    section: { fontSize: 12, fontWeight: 'bold', color: '#888', paddingHorizontal: 16, paddingTop: 16, paddingBottom: 4, textTransform: 'uppercase' },
    goalsForm: { padding: 16 },
    input: { marginBottom: 8 },
    row: { flexDirection: 'row', gap: 8 },
    halfBtn: { flex: 1 },
    btn: { margin: 16 },
    logoutBtn: { borderColor: '#F44336' },
    deleteBtn: { marginBottom: 32 },
});
