import React, { useEffect } from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { Provider as PaperProvider } from 'react-native-paper';
import { StatusBar } from 'expo-status-bar';
import { THEME } from './constants';
import { useAuthStore } from './store/authStore';

import LoginScreen from './screens/auth/LoginScreen';
import RegisterScreen from './screens/auth/RegisterScreen';
import DashboardScreen from './screens/main/DashboardScreen';
import MoodScreen from './screens/main/MoodScreen';
import NutritionScreen from './screens/main/NutritionScreen';
import AiCoachScreen from './screens/main/AiCoachScreen';
import ProfileScreen from './screens/main/ProfileScreen';

import { createStackNavigator } from '@react-navigation/stack';

const Stack = createStackNavigator();
const Tab = createBottomTabNavigator();

function MainTabs() {
  return (
    <Tab.Navigator screenOptions={{ headerShown: true }}>
      <Tab.Screen name="Dashboard" component={DashboardScreen} options={{ title: 'Home', tabBarLabel: 'Home' }} />
      <Tab.Screen name="Mood" component={MoodScreen} options={{ tabBarLabel: 'Mood' }} />
      <Tab.Screen name="Nutrition" component={NutritionScreen} options={{ tabBarLabel: 'Food' }} />
      <Tab.Screen name="AI Coach" component={AiCoachScreen} options={{ tabBarLabel: 'Coach' }} />
      <Tab.Screen name="Profile" component={ProfileScreen} options={{ tabBarLabel: 'Profile' }} />
    </Tab.Navigator>
  );
}

function AuthStack() {
  return (
    <Stack.Navigator screenOptions={{ headerShown: false }}>
      <Stack.Screen name="Login" component={LoginScreen} />
      <Stack.Screen name="Register" component={RegisterScreen} />
    </Stack.Navigator>
  );
}

export default function App() {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const restoreSession = useAuthStore((s) => s.restoreSession);

  useEffect(() => { restoreSession(); }, []);

  return (
    <PaperProvider theme={THEME}>
      <NavigationContainer>
        {isAuthenticated ? <MainTabs /> : <AuthStack />}
      </NavigationContainer>
      <StatusBar style="auto" />
    </PaperProvider>
  );
}
