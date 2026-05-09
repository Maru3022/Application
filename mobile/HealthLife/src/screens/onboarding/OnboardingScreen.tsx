import React, { useState } from 'react';
import { View, StyleSheet, Alert, ScrollView, KeyboardAvoidingView, Platform } from 'react-native';
import { Text, Button, TextInput, SegmentedButtons } from 'react-native-paper';
import * as SecureStore from 'expo-secure-store';
import { STORAGE_KEYS } from '../../constants';
import { userApi } from '../../api/services';

interface Slide {
    emoji: string;
    title: string;
    description: string;
    color: string;
}

const INFO_SLIDES: Slide[] = [
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
        description: 'Search millions of foods via OpenFoodFacts, scan barcodes, and track your daily macros.',
        color: '#FF9800',
    },
    {
        emoji: '🤖',
        title: 'AI Health Coach',
        description: 'Get personalised insights powered by DeepSeek AI — knows your data, gives real advice.',
        color: '#2196F3',
    },
];

interface Props {
    onComplete: () => void;
}

export default function OnboardingScreen({ onComplete }: Props) {
    const [step, setStep] = useState(0); // 0..3 = info slides, 4 = profile form
    const [saving, setSaving] = useState(false);

    // Profile form state
    const [goal, setGoal] = useState<'lose' | 'maintain' | 'gain'>('maintain');
    const [gender, setGender] = useState<'male' | 'female' | 'other'>('other');
    const [age, setAge] = useState('');
    const [heightCm, setHeightCm] = useState('');
    const [weightKg, setWeightKg] = useState('');

    const isInfoSlide = step < INFO_SLIDES.length;
    const slide = isInfoSlide ? INFO_SLIDES[step] : null;

    const handleNext = () => {
        if (step < INFO_SLIDES.length) {
            setStep(step + 1); // advance to next info slide or profile form
        }
    };

    const handleFinish = async () => {
        // Validate required fields
        if (!heightCm || !weightKg) {
            Alert.alert('Required', 'Please enter your height and weight so your AI Coach can personalise advice.');
            return;
        }
        const h = parseFloat(heightCm);
        const w = parseFloat(weightKg);
        if (isNaN(h) || h < 50 || h > 300) {
            Alert.alert('Invalid', 'Please enter a valid height (50–300 cm)');
            return;
        }
        if (isNaN(w) || w < 20 || w > 500) {
            Alert.alert('Invalid', 'Please enter a valid weight (20–500 kg)');
            return;
        }

        setSaving(true);
        try {
            // Save profile to user-service
            await userApi.updateProfile({
                gender,
                heightCm: h,
            });

            // Set personalised goals based on user input
            const dailySteps = goal === 'lose' ? 12000 : goal === 'gain' ? 8000 : 10000;
            const waterMl = Math.round(w * 35); // 35ml per kg body weight
            await userApi.updateGoals({
                dailySteps,
                waterMl,
                sleepMinutes: 480, // 8 hours default
            });

            await SecureStore.setItemAsync(STORAGE_KEYS.ONBOARDED, 'true');
            onComplete();
        } catch (e: any) {
            // Non-fatal — save onboarded flag anyway so user isn't stuck
            console.warn('Profile save failed:', e.message);
            await SecureStore.setItemAsync(STORAGE_KEYS.ONBOARDED, 'true');
            onComplete();
        } finally {
            setSaving(false);
        }
    };

    // ── Info slides ───────────────────────────────────────────────────────────
    if (isInfoSlide && slide) {
        return (
            <View style={[styles.container, { backgroundColor: slide.color }]}>
                <View style={styles.content}>
                    <Text style={styles.emoji}>{slide.emoji}</Text>
                    <Text style={styles.title}>{slide.title}</Text>
                    <Text style={styles.description}>{slide.description}</Text>
                </View>

                <View style={styles.dots}>
                    {[...INFO_SLIDES, { emoji: '' }].map((_, i) => (
                        <View key={i} style={[styles.dot, i === step && styles.dotActive]} />
                    ))}
                </View>

                <Button
                    mode="contained"
                    onPress={handleNext}
                    style={styles.btn}
                    buttonColor="white"
                    textColor={slide.color}
                >
                    {step === INFO_SLIDES.length - 1 ? 'Set Up Profile →' : 'Next'}
                </Button>
            </View>
        );
    }

    // ── Profile form ──────────────────────────────────────────────────────────
    return (
        <KeyboardAvoidingView
            style={styles.formContainer}
            behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        >
            <ScrollView contentContainerStyle={styles.formScroll}>
                <Text style={styles.formTitle}>Tell us about yourself</Text>
                <Text style={styles.formSubtitle}>
                    Your AI Coach uses this to give personalised advice. You can update it anytime in Settings.
                </Text>

                <Text style={styles.label}>Your goal</Text>
                <SegmentedButtons
                    value={goal}
                    onValueChange={(v) => setGoal(v as any)}
                    buttons={[
                        { value: 'lose', label: 'Lose weight' },
                        { value: 'maintain', label: 'Maintain' },
                        { value: 'gain', label: 'Gain muscle' },
                    ]}
                    style={styles.segment}
                />

                <Text style={styles.label}>Gender</Text>
                <SegmentedButtons
                    value={gender}
                    onValueChange={(v) => setGender(v as any)}
                    buttons={[
                        { value: 'male', label: 'Male' },
                        { value: 'female', label: 'Female' },
                        { value: 'other', label: 'Other' },
                    ]}
                    style={styles.segment}
                />

                <TextInput
                    label="Age (optional)"
                    value={age}
                    onChangeText={setAge}
                    keyboardType="number-pad"
                    style={styles.input}
                />
                <TextInput
                    label="Height (cm) *"
                    value={heightCm}
                    onChangeText={setHeightCm}
                    keyboardType="decimal-pad"
                    style={styles.input}
                />
                <TextInput
                    label="Current weight (kg) *"
                    value={weightKg}
                    onChangeText={setWeightKg}
                    keyboardType="decimal-pad"
                    style={styles.input}
                />

                <Text style={styles.hint}>* Required for personalised AI insights</Text>

                <Button
                    mode="contained"
                    onPress={handleFinish}
                    loading={saving}
                    style={styles.finishBtn}
                    icon="check"
                >
                    Get Started
                </Button>
            </ScrollView>
        </KeyboardAvoidingView>
    );
}

const styles = StyleSheet.create({
    // Info slide styles
    container: { flex: 1, padding: 24 },
    content: { flex: 1, justifyContent: 'center', alignItems: 'center' },
    emoji: { fontSize: 80, marginBottom: 32 },
    title: { fontSize: 28, fontWeight: 'bold', color: 'white', textAlign: 'center', marginBottom: 16 },
    description: { fontSize: 16, color: 'rgba(255,255,255,0.9)', textAlign: 'center', lineHeight: 24 },
    dots: { flexDirection: 'row', justifyContent: 'center', gap: 8, marginBottom: 24 },
    dot: { width: 8, height: 8, borderRadius: 4, backgroundColor: 'rgba(255,255,255,0.4)' },
    dotActive: { backgroundColor: 'white', width: 24 },
    btn: { marginBottom: 16 },
    // Profile form styles
    formContainer: { flex: 1, backgroundColor: '#F5F5F5' },
    formScroll: { padding: 24 },
    formTitle: { fontSize: 26, fontWeight: 'bold', color: '#333', marginBottom: 8 },
    formSubtitle: { color: '#666', marginBottom: 24, lineHeight: 20 },
    label: { fontWeight: 'bold', color: '#444', marginBottom: 8, marginTop: 8 },
    segment: { marginBottom: 16 },
    input: { marginBottom: 12 },
    hint: { color: '#999', fontSize: 12, marginBottom: 24 },
    finishBtn: { marginBottom: 32 },
});
