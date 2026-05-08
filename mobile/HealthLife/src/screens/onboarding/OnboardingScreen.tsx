import React, { useState } from 'react';
import { View, StyleSheet, Dimensions, ScrollView } from 'react-native';
import { Text, Button } from 'react-native-paper';
import * as SecureStore from 'expo-secure-store';
import { STORAGE_KEYS } from '../../constants';

const { width } = Dimensions.get('window');

interface Slide {
    emoji: string;
    title: string;
    description: string;
    color: string;
}

const SLIDES: Slide[] = [
    {
        emoji: '💚',
        title: 'Welcome to HealthLife',
        description: 'Your all-in-one health and wellness companion. Track, improve, and celebrate your health journey.',
        color: '#4CAF50',
    },
    {
        emoji: '😴',
        title: 'Track Your Sleep',
        description: 'Log your sleep patterns and get insights on how to improve your rest quality.',
        color: '#9C27B0',
    },
    {
        emoji: '🥗',
        title: 'Monitor Nutrition',
        description: 'Search thousands of foods, scan barcodes, and track your daily macros effortlessly.',
        color: '#FF9800',
    },
    {
        emoji: '🤖',
        title: 'AI Health Coach',
        description: 'Get personalised insights and recommendations powered by advanced AI — available 24/7.',
        color: '#2196F3',
    },
    {
        emoji: '🎯',
        title: 'Set Your Goals',
        description: 'Define your health goals and track your progress. Small steps lead to big changes.',
        color: '#F44336',
    },
];

interface Props {
    onComplete: () => void;
}

export default function OnboardingScreen({ onComplete }: Props) {
    const [currentIndex, setCurrentIndex] = useState(0);

    const handleNext = async () => {
        if (currentIndex < SLIDES.length - 1) {
            setCurrentIndex(currentIndex + 1);
        } else {
            await SecureStore.setItemAsync(STORAGE_KEYS.ONBOARDED, 'true');
            onComplete();
        }
    };

    const handleSkip = async () => {
        await SecureStore.setItemAsync(STORAGE_KEYS.ONBOARDED, 'true');
        onComplete();
    };

    const slide = SLIDES[currentIndex];

    return (
        <View style={[styles.container, { backgroundColor: slide.color }]}>
            <Button
                mode="text"
                onPress={handleSkip}
                textColor="rgba(255,255,255,0.8)"
                style={styles.skip}
            >
                Skip
            </Button>

            <View style={styles.content}>
                <Text style={styles.emoji}>{slide.emoji}</Text>
                <Text style={styles.title}>{slide.title}</Text>
                <Text style={styles.description}>{slide.description}</Text>
            </View>

            {/* Dots */}
            <View style={styles.dots}>
                {SLIDES.map((_, i) => (
                    <View
                        key={i}
                        style={[styles.dot, i === currentIndex && styles.dotActive]}
                    />
                ))}
            </View>

            <Button
                mode="contained"
                onPress={handleNext}
                style={styles.btn}
                buttonColor="white"
                textColor={slide.color}
            >
                {currentIndex === SLIDES.length - 1 ? 'Get Started' : 'Next'}
            </Button>
        </View>
    );
}

const styles = StyleSheet.create({
    container: { flex: 1, padding: 24 },
    skip: { alignSelf: 'flex-end', marginTop: 8 },
    content: { flex: 1, justifyContent: 'center', alignItems: 'center' },
    emoji: { fontSize: 80, marginBottom: 32 },
    title: { fontSize: 28, fontWeight: 'bold', color: 'white', textAlign: 'center', marginBottom: 16 },
    description: { fontSize: 16, color: 'rgba(255,255,255,0.9)', textAlign: 'center', lineHeight: 24 },
    dots: { flexDirection: 'row', justifyContent: 'center', gap: 8, marginBottom: 24 },
    dot: { width: 8, height: 8, borderRadius: 4, backgroundColor: 'rgba(255,255,255,0.4)' },
    dotActive: { backgroundColor: 'white', width: 24 },
    btn: { marginBottom: 16 },
});
