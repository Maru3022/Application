import React, { useState, useEffect } from 'react';
import { View, StyleSheet, ScrollView, Alert, Linking } from 'react-native';
import { Card, Text, Button, Chip, ActivityIndicator, Divider } from 'react-native-paper';
import { paymentApi } from '../../api/services';

interface SubscriptionStatus {
    plan: string;
    status: string;
    currentPeriodEnd: string | null;
    canceledAt: string | null;
}

const PLANS = [
    {
        id: 'FREE',
        name: 'Free',
        price: '$0',
        period: 'forever',
        color: '#9E9E9E',
        features: [
            'Basic health tracking',
            'Sleep, water, weight logs',
            'Mood & journal',
            '7-day history',
        ],
        priceId: null,
    },
    {
        id: 'PRO',
        name: 'Pro',
        price: '$9.99',
        period: 'per month',
        color: '#4CAF50',
        features: [
            'Everything in Free',
            'Unlimited history',
            'AI Coach (DeepSeek V3)',
            'Nutrition analysis',
            'Advanced sleep stats',
            'Priority support',
        ],
        priceId: 'price_pro', // Replace with real Stripe price ID
    },
    {
        id: 'PREMIUM',
        name: 'Premium',
        price: '$19.99',
        period: 'per month',
        color: '#FF9800',
        features: [
            'Everything in Pro',
            'Unlimited AI conversations',
            'Social challenges',
            'Custom food database',
            'Data export (CSV/JSON)',
            'Dedicated support',
        ],
        priceId: 'price_premium', // Replace with real Stripe price ID
    },
];

export default function SubscriptionScreen() {
    const [status, setStatus] = useState<SubscriptionStatus | null>(null);
    const [loading, setLoading] = useState(true);
    const [upgrading, setUpgrading] = useState<string | null>(null);

    useEffect(() => {
        loadStatus();
    }, []);

    const loadStatus = async () => {
        try {
            const { data } = await paymentApi.getSubscriptionStatus();
            setStatus(data as SubscriptionStatus);
        } catch {
            // Default to free if API unavailable
            setStatus({ plan: 'FREE', status: 'active', currentPeriodEnd: null, canceledAt: null });
        } finally {
            setLoading(false);
        }
    };

    const handleUpgrade = async (priceId: string, planName: string) => {
        setUpgrading(planName);
        try {
            const { data } = await paymentApi.createCheckout(priceId);
            const checkoutData = data as { url: string };
            if (checkoutData.url) {
                // Open Stripe Checkout in browser
                await Linking.openURL(checkoutData.url);
            }
        } catch (e: any) {
            Alert.alert(
                'Payment Unavailable',
                e.response?.data?.detail || 'Payment processing is not configured yet.',
            );
        } finally {
            setUpgrading(null);
        }
    };

    const handleManage = async () => {
        try {
            const { data } = await paymentApi.createPortal();
            const portalData = data as { url: string };
            if (portalData.url) {
                await Linking.openURL(portalData.url);
            }
        } catch (e: any) {
            Alert.alert('Error', e.response?.data?.detail || 'Failed to open billing portal.');
        }
    };

    if (loading) {
        return (
            <View style={styles.centered}>
                <ActivityIndicator size="large" />
            </View>
        );
    }

    const currentPlan = status?.plan ?? 'FREE';

    return (
        <ScrollView style={styles.container}>
            <Text style={styles.title}>Subscription</Text>

            {/* Current plan badge */}
            <Card style={styles.currentCard}>
                <Card.Content>
                    <Text style={styles.currentLabel}>Current Plan</Text>
                    <Text style={styles.currentPlan}>{currentPlan}</Text>
                    {status?.status === 'past_due' && (
                        <Text style={styles.warning}>
                            ⚠️ Payment past due — please update your payment method
                        </Text>
                    )}
                    {status?.canceledAt && (
                        <Text style={styles.warning}>
                            Canceled on {new Date(status.canceledAt).toLocaleDateString()}
                        </Text>
                    )}
                    {status?.currentPeriodEnd && (
                        <Text style={styles.renewDate}>
                            Renews {new Date(status.currentPeriodEnd).toLocaleDateString()}
                        </Text>
                    )}
                    {currentPlan !== 'FREE' && (
                        <Button mode="outlined" onPress={handleManage} style={styles.manageBtn}>
                            Manage Billing
                        </Button>
                    )}
                </Card.Content>
            </Card>

            <Divider style={styles.divider} />
            <Text style={styles.sectionTitle}>Choose a Plan</Text>

            {PLANS.map((plan) => {
                const isCurrent = currentPlan === plan.id;
                return (
                    <Card
                        key={plan.id}
                        style={[styles.planCard, isCurrent && { borderColor: plan.color, borderWidth: 2 }]}
                    >
                        <Card.Content>
                            <View style={styles.planHeader}>
                                <View>
                                    <Text style={[styles.planName, { color: plan.color }]}>
                                        {plan.name}
                                    </Text>
                                    <Text style={styles.planPrice}>
                                        {plan.price}{' '}
                                        <Text style={styles.planPeriod}>{plan.period}</Text>
                                    </Text>
                                </View>
                                {isCurrent && (
                                    <Chip style={{ backgroundColor: plan.color }}>
                                        <Text style={{ color: 'white', fontSize: 12 }}>Current</Text>
                                    </Chip>
                                )}
                            </View>

                            {plan.features.map((f) => (
                                <Text key={f} style={styles.feature}>
                                    ✓ {f}
                                </Text>
                            ))}

                            {!isCurrent && plan.priceId && (
                                <Button
                                    mode="contained"
                                    onPress={() => handleUpgrade(plan.priceId!, plan.name)}
                                    loading={upgrading === plan.name}
                                    disabled={upgrading !== null}
                                    style={[styles.upgradeBtn, { backgroundColor: plan.color }]}
                                >
                                    Upgrade to {plan.name}
                                </Button>
                            )}
                        </Card.Content>
                    </Card>
                );
            })}

            <Text style={styles.disclaimer}>
                Payments are processed securely by Stripe. Cancel anytime from Manage Billing.
            </Text>
        </ScrollView>
    );
}

const styles = StyleSheet.create({
    container: { flex: 1, backgroundColor: '#F5F5F5', padding: 16 },
    centered: { flex: 1, justifyContent: 'center', alignItems: 'center' },
    title: { fontSize: 22, fontWeight: 'bold', marginBottom: 12 },
    currentCard: { marginBottom: 16, backgroundColor: '#fff' },
    currentLabel: { color: '#666', fontSize: 12, textTransform: 'uppercase' },
    currentPlan: { fontSize: 28, fontWeight: 'bold', color: '#333', marginTop: 4 },
    warning: { color: '#F44336', marginTop: 8 },
    renewDate: { color: '#666', marginTop: 4 },
    manageBtn: { marginTop: 12 },
    divider: { marginVertical: 16 },
    sectionTitle: { fontSize: 16, fontWeight: 'bold', marginBottom: 12, color: '#555' },
    planCard: { marginBottom: 12, backgroundColor: '#fff' },
    planHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 12 },
    planName: { fontSize: 20, fontWeight: 'bold' },
    planPrice: { fontSize: 18, fontWeight: 'bold', color: '#333', marginTop: 2 },
    planPeriod: { fontSize: 14, color: '#666', fontWeight: 'normal' },
    feature: { color: '#444', marginBottom: 4, fontSize: 14 },
    upgradeBtn: { marginTop: 12 },
    disclaimer: { textAlign: 'center', color: '#999', fontSize: 12, marginVertical: 24 },
});
