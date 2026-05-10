import React from 'react';
import { View, StyleSheet, Alert } from 'react-native';
import { List, Switch, Button, Text } from 'react-native-paper';
import { useAuthStore } from '../../store/authStore';

export default function ProfileScreen({ navigation }: any) {
  const logout = useAuthStore((s) => s.logout);
  const [notifications, setNotifications] = React.useState(true);
  const [darkMode, setDarkMode] = React.useState(false);

  const handleLogout = () => {
    Alert.alert('Logout', 'Are you sure?', [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Logout', style: 'destructive', onPress: logout },
    ]);
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Settings</Text>
      <List.Item title="Notifications" left={(props: any) => <List.Icon icon="bell" {...props} />}
        right={() => <Switch value={notifications} onValueChange={setNotifications} />} />
      <List.Item title="Dark Mode" left={(props: any) => <List.Icon icon="theme-light-dark" {...props} />}
        right={() => <Switch value={darkMode} onValueChange={setDarkMode} />} />
      <List.Item title="Goals" left={(props: any) => <List.Icon icon="target" {...props} />} onPress={() => navigation.navigate('Settings')} />
      <List.Item title="Subscription" left={(props: any) => <List.Icon icon="crown" {...props} />} onPress={() => navigation.navigate('Subscription')} />
      <List.Item title="Privacy & Data" left={(props: any) => <List.Icon icon="shield-lock" {...props} />} onPress={() => {}} />
      <List.Item title="Help & Support" left={(props: any) => <List.Icon icon="help-circle" {...props} />} onPress={() => {}} />
      <Button mode="outlined" onPress={handleLogout} style={styles.logoutBtn} textColor="#F44336">
        Sign Out
      </Button>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#F5F5F5', padding: 16 },
  title: { fontSize: 22, fontWeight: 'bold', marginBottom: 16 },
  logoutBtn: { marginTop: 24, borderColor: '#F44336' },
});
