import React, { useEffect, useState } from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { createStackNavigator } from '@react-navigation/stack';
import { Provider as PaperProvider } from 'react-native-paper';
import { StatusBar } from 'expo-status-bar';
import * as SecureStore from 'expo-secure-store';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { THEME, STORAGE_KEYS } from './constants';
import { useAuthStore } from './store/authStore';

// Auth screens
import LoginScreen from './screens/auth/LoginScreen';
import RegisterScreen from './screens/auth/RegisterScreen';

// Onboarding
import OnboardingScreen from './screens/onboarding/OnboardingScreen';

// Main screens
import DashboardScreen from './screens/main/DashboardScreen';
import MoodScreen from './screens/main/MoodScreen';
import NutritionScreen from './screens/main/NutritionScreen';
import AiCoachScreen from './screens/main/AiCoachScreen';
import ProfileScreen from './screens/main/ProfileScreen';
import SleepScreen from './screens/main/SleepScreen';
import WaterScreen from './screens/main/WaterScreen';
import WeightScreen from './screens/main/WeightScreen';
import ActivityScreen from './screens/main/ActivityScreen';
import SettingsScreen from './screens/main/SettingsScreen';
import SubscriptionScreen from './screens/main/SubscriptionScreen';

const Stack = createStackNavigator();
const Tab = createBottomTabNavigator();
const HealthStack = createStackNavigator();
const ProfileStack = createStackNavigator();

function HealthStackNavigator() {
    return (
        <HealthStack.Navigator>
            <HealthStack.Screen name="Dashboard" component={DashboardScreen} options={{ title: 'Home' }} />
            <HealthStack.Screen name="Sleep" component={SleepScreen} />
            <HealthStack.Screen name="Water" component={WaterScreen} />
            <HealthStack.Screen name="Weight" component={WeightScreen} />
            <HealthStack.Screen name="Activity" component={ActivityScreen} />
        </HealthStack.Navigator>
    );
}

function ProfileStackNavigator() {
    return (
        <ProfileStack.Navigator>
            <ProfileStack.Screen name="Profile" component={ProfileScreen} />
            <ProfileStack.Screen name="Settings" component={SettingsScreen} />
            <ProfileStack.Screen name="Subscription" component={SubscriptionScreen} />
        </ProfileStack.Navigator>
    );
}

function MainTabs() {
    return (
        <Tab.Navigator
            screenOptions={({ route }) => ({
                headerShown: false,
                tabBarIcon: ({ color, size }) => {
                    const icons: Record<string, string> = {
                        Home: 'home',
                        Mood: 'emoticon-happy',
                        Food: 'food-apple',
                        Coach: 'robot',
                        Me: 'account',
                    };
                    return (
                        <MaterialCommunityIcons
                            name={(icons[route.name] ?? 'circle') as any}
                            size={size}
                            color={color}
                        />
                    );
                },
                tabBarActiveTintColor: '#4CAF50',
                tabBarInactiveTintColor: '#999',
            })}
        >
            <Tab.Screen name="Home" component={HealthStackNavigator} options={{ tabBarLabel: 'Home' }} />
            <Tab.Screen name="Mood" component={MoodScreen} />
            <Tab.Screen name="Food" component={NutritionScreen} options={{ headerShown: true }} />
            <Tab.Screen name="Coach" component={AiCoachScreen} options={{ headerShown: true, title: 'AI Coach' }} />
            <Tab.Screen name="Me" component={ProfileStackNavigator} options={{ tabBarLabel: 'Profile' }} />
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
    const [onboarded, setOnboarded] = useState<boolean | null>(null);

    useEffect(() => {
        const init = async () => {
            await restoreSession();
            const val = await SecureStore.getItemAsync(STORAGE_KEYS.ONBOARDED);
            setOnboarded(val === 'true');
        };
        init();
    }, []);

    // Still loading
    if (onboarded === null) return null;

    return (
        <PaperProvider theme={THEME}>
            <NavigationContainer>
                {!onboarded ? (
                    <OnboardingScreen onComplete={() => setOnboarded(true)} />
                ) : isAuthenticated ? (
                    <MainTabs />
                ) : (
                    <AuthStack />
                )}
            </NavigationContainer>
            <StatusBar style="auto" />
        </PaperProvider>
    );
}
