package android.telephony.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.PackageManager;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.telephony.CallAttributes;
import android.telephony.CallState;
import android.telephony.PhoneStateListener;
import android.telephony.PreciseCallState;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.telephony.TelephonyManager.CarrierPrivilegesCallback;
import android.telephony.TelephonyRegistryManager;
import android.telephony.emergency.EmergencyNumber;
import android.telephony.ims.ImsCallProfile;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.ShellIdentityUtils;
import com.android.internal.telephony.flags.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Test TelephonyRegistryManagerTest APIs.
 */
public class TelephonyRegistryManagerTest {
    private TelephonyRegistryManager mTelephonyRegistryMgr;
    private static final long TIMEOUT_MILLIS = 1000;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() throws Exception {
        assumeTrue(InstrumentationRegistry.getContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_TELEPHONY));

        mTelephonyRegistryMgr = (TelephonyRegistryManager) InstrumentationRegistry.getContext()
                .getSystemService(Context.TELEPHONY_REGISTRY_SERVICE);
    }

    /**
     * expect security exception as there is no carrier privilege permission.
     */
    @Test
    public void testNotifyCarrierNetworkChange() {
        try {
            mTelephonyRegistryMgr.notifyCarrierNetworkChange(true);
            fail("Expected SecurityException for notifyCarrierNetworkChange");
        } catch (SecurityException ex) {
            /* Expected */
        }
    }

    /**
     * expect security exception as there is no carrier privilege permission.
     */
    @Test
    public void testNotifyCarrierNetworkChangeWithSubscription() {
        try {
            mTelephonyRegistryMgr.notifyCarrierNetworkChange(
                    SubscriptionManager.getDefaultSubscriptionId(), /*active=*/ true);
            fail("Expected SecurityException for notifyCarrierNetworkChange with subscription");
        } catch (SecurityException expected) {
        }
    }

    @Test
    public void testNotifyCallStateChangedForAllSubscriptions() throws Exception {
        Context context = InstrumentationRegistry.getContext();

        LinkedBlockingQueue<Pair<Integer, String>> queue = new LinkedBlockingQueue<>(1);
        PhoneStateListener psl = new PhoneStateListener(context.getMainExecutor()) {
            @Override
            public void onCallStateChanged(int state, String number) {
                queue.offer(Pair.create(state, number));
            }
        };
        TelephonyManager tm = context.getSystemService(TelephonyManager.class);
        tm.listen(psl, PhoneStateListener.LISTEN_CALL_STATE);
        // clear the initial result from registering the listener.
        queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        String dummyNumber = "288124";
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyRegistryMgr,
                (trm) -> trm.notifyCallStateChangedForAllSubscriptions(
                        TelephonyManager.CALL_STATE_IDLE, dummyNumber));

        Pair<Integer, String> result = queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertNotNull("Timed out waiting for phone state change", result);
        assertEquals(TelephonyManager.CALL_STATE_IDLE, result.first.longValue());
        assertTrue(!TextUtils.isEmpty(result.second));
    }

    @Test
    public void testNotifyCallStateChanged() throws Exception {
        Context context = InstrumentationRegistry.getContext();

        LinkedBlockingQueue<Pair<Integer, String>> queue = new LinkedBlockingQueue<>(1);
        PhoneStateListener psl = new PhoneStateListener(context.getMainExecutor()) {
            @Override
            public void onCallStateChanged(int state, String number) {
                queue.offer(Pair.create(state, number));
            }
        };
        TelephonyManager tm = context.getSystemService(TelephonyManager.class);
        tm = tm.createForSubscriptionId(SubscriptionManager.getDefaultSubscriptionId());
        tm.listen(psl, PhoneStateListener.LISTEN_CALL_STATE);
        // clear the initial result from registering the listener.
        queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        String dummyNumber = "288124";
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyRegistryMgr,
                (trm) -> trm.notifyCallStateChanged(
                        SubscriptionManager.getSlotIndex(
                                SubscriptionManager.getDefaultSubscriptionId()),
                        SubscriptionManager.getDefaultSubscriptionId(),
                        TelephonyManager.CALL_STATE_IDLE, dummyNumber));

        Pair<Integer, String> result = queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertNotNull("Timed out waiting for phone state change", result);
        assertEquals(TelephonyManager.CALL_STATE_IDLE, result.first.longValue());
        assertTrue(!TextUtils.isEmpty(result.second));
    }

    @Test
    public void testNotifyServiceStateChanged() throws Exception {
        Context context = InstrumentationRegistry.getContext();

        LinkedBlockingQueue<ServiceState> queue = new LinkedBlockingQueue<>(1);
        PhoneStateListener psl = new PhoneStateListener(context.getMainExecutor()) {
            @Override
            public void onServiceStateChanged(ServiceState ss) {
                queue.offer(ss);
            }
        };
        TelephonyManager tm = context.getSystemService(TelephonyManager.class);
        tm.listen(psl, PhoneStateListener.LISTEN_SERVICE_STATE);
        // clear the initial result from registering the listener.
        queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        ServiceState dummyState = new ServiceState();
        dummyState.setCdmaSystemAndNetworkId(1234, 5678);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyRegistryMgr,
                (trm) -> trm.notifyServiceStateChanged(
                        SubscriptionManager.getSlotIndex(
                                SubscriptionManager.getDefaultSubscriptionId()),
                        SubscriptionManager.getDefaultSubscriptionId(),
                        dummyState));

        ServiceState result = queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertNotNull("Timed out waiting for phone state change", result);
        assertEquals(dummyState, result);
    }

    @Test
    public void testNotifySignalStrengthChanged() throws Exception {
        Context context = InstrumentationRegistry.getContext();

        LinkedBlockingQueue<SignalStrength> queue = new LinkedBlockingQueue<>(1);
        PhoneStateListener psl = new PhoneStateListener(context.getMainExecutor()) {
            @Override
            public void onSignalStrengthsChanged(SignalStrength ss) {
                queue.offer(ss);
            }
        };
        TelephonyManager tm = context.getSystemService(TelephonyManager.class);
        tm.listen(psl, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);
        // clear the initial result from registering the listener.
        queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        SignalStrength testValue = new SignalStrength();
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyRegistryMgr,
                (trm) -> trm.notifySignalStrengthChanged(
                        SubscriptionManager.getSlotIndex(
                                SubscriptionManager.getDefaultSubscriptionId()),
                        SubscriptionManager.getDefaultSubscriptionId(),
                        testValue));

        SignalStrength result = queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertNotNull("Timed out waiting for phone state change", result);
        assertEquals(testValue, result);
    }

    @Test
    public void testNotifyMessageWaitingChanged() throws Exception {
        Context context = InstrumentationRegistry.getContext();

        LinkedBlockingQueue<Boolean> queue = new LinkedBlockingQueue<>(1);
        PhoneStateListener psl = new PhoneStateListener(context.getMainExecutor()) {
            @Override
            public void onMessageWaitingIndicatorChanged(boolean msgWaitingInd) {
                queue.offer(msgWaitingInd);
            }
        };
        TelephonyManager tm = context.getSystemService(TelephonyManager.class);
        tm.listen(psl, PhoneStateListener.LISTEN_MESSAGE_WAITING_INDICATOR);
        // clear the initial result from registering the listener.
        queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        boolean testValue = true;
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyRegistryMgr,
                (trm) -> trm.notifyMessageWaitingChanged(
                        SubscriptionManager.getSlotIndex(
                                SubscriptionManager.getDefaultSubscriptionId()),
                        SubscriptionManager.getDefaultSubscriptionId(),
                        testValue));

        boolean result = queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertEquals(testValue, result);
    }

    @Test
    public void testNotifyCallForwardingChanged() throws Exception {
        Context context = InstrumentationRegistry.getContext();

        LinkedBlockingQueue<Boolean> queue = new LinkedBlockingQueue<>(1);
        PhoneStateListener psl = new PhoneStateListener(context.getMainExecutor()) {
            @Override
            public void onCallForwardingIndicatorChanged(boolean callForwarding) {
                queue.offer(callForwarding);
            }
        };
        TelephonyManager tm = context.getSystemService(TelephonyManager.class);
        tm.listen(psl, PhoneStateListener.LISTEN_CALL_FORWARDING_INDICATOR);
        // clear the initial result from registering the listener.
        queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        boolean testValue = true;
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyRegistryMgr,
                (trm) -> trm.notifyCallForwardingChanged(
                        SubscriptionManager.getDefaultSubscriptionId(),
                        testValue));

        boolean result = queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertEquals(testValue, result);
    }

    @Test
    @RequiresFlagsDisabled(Flags.FLAG_NOTIFY_DATA_ACTIVITY_CHANGED_WITH_SLOT)
    public void testNotifyDataActivityChanged() throws Exception {
        Context context = InstrumentationRegistry.getContext();

        LinkedBlockingQueue<Integer> queue = new LinkedBlockingQueue<>(1);
        PhoneStateListener psl = new PhoneStateListener(context.getMainExecutor()) {
            @Override
            public void onDataActivity(int activity) {
                queue.offer(activity);
            }
        };
        TelephonyManager tm = context.getSystemService(TelephonyManager.class);
        tm.listen(psl, PhoneStateListener.LISTEN_DATA_ACTIVITY);
        // clear the initial result from registering the listener.
        queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        int testValue = TelephonyManager.DATA_ACTIVITY_DORMANT;
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyRegistryMgr,
                (trm) -> trm.notifyDataActivityChanged(
                        SubscriptionManager.getDefaultSubscriptionId(),
                        testValue));

        int result = queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertEquals(testValue, result);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_NOTIFY_DATA_ACTIVITY_CHANGED_WITH_SLOT)
    public void testNotifyDataActivityChangedWithSlot() throws Exception {
        Context context = InstrumentationRegistry.getContext();

        LinkedBlockingQueue<Integer> queue = new LinkedBlockingQueue<>(1);
        PhoneStateListener psl = new PhoneStateListener(context.getMainExecutor()) {
            @Override
            public void onDataActivity(int activity) {
                queue.offer(activity);
            }
        };
        TelephonyManager tm = context.getSystemService(TelephonyManager.class);
        tm.listen(psl, PhoneStateListener.LISTEN_DATA_ACTIVITY);
        // clear the initial result from registering the listener.
        queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        int testValue = TelephonyManager.DATA_ACTIVITY_DORMANT;
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyRegistryMgr,
                (trm) -> trm.notifyDataActivityChanged(
                        SubscriptionManager.getSlotIndex(
                                SubscriptionManager.getDefaultSubscriptionId()),
                        SubscriptionManager.getDefaultSubscriptionId(),
                        testValue));

        int result = queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertEquals(testValue, result);
    }

    @Test
    public void testCarrierPrivilegesCallback() throws Exception {
        Context context = InstrumentationRegistry.getContext();

        LinkedBlockingQueue<Pair<Set<String>, Set<Integer>>> carrierPrivilegesQueue =
                new LinkedBlockingQueue(2);
        LinkedBlockingQueue<Pair<String, Integer>> carrierServiceQueue = new LinkedBlockingQueue(2);

        CarrierPrivilegesCallback cpc = new TestCarrierPrivilegesCallback(carrierPrivilegesQueue,
                carrierServiceQueue);
        CarrierPrivilegesCallback cpc2 = new TestCarrierPrivilegesCallback(carrierPrivilegesQueue,
                carrierServiceQueue);

        TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    telephonyManager,
                    tm -> tm.registerCarrierPrivilegesCallback(0, context.getMainExecutor(), cpc));
            // Clear the initial carrierPrivilegesResult from registering the listener. We can't
            // necessarily guarantee this is empty so don't assert on it other than the fact we
            // got _something_. We restore this at the end of the test.
            Pair<Set<String>, Set<Integer>> initialCarrierPrivilegesState =
                    carrierPrivilegesQueue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            assertNotNull(initialCarrierPrivilegesState);
            Pair<String, Integer> initialCarrierServiceState =
                    carrierServiceQueue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            assertNotNull(initialCarrierServiceState);

            // Update state
            Set<String> privilegedPackageNames =
                    Set.of("com.carrier.package1", "com.carrier.package2");
            Set<Integer> privilegedUids = Set.of(12345, 54321);
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyRegistryMgr,
                    trm -> {
                        trm.notifyCarrierPrivilegesChanged(
                                0, privilegedPackageNames, privilegedUids);
                        trm.notifyCarrierServiceChanged(0, "com.carrier.package1", 12345);
                    });
            Pair<Set<String>, Set<Integer>> carrierPrivilegesResult =
                    carrierPrivilegesQueue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            assertEquals(privilegedPackageNames, carrierPrivilegesResult.first);
            assertEquals(privilegedUids, carrierPrivilegesResult.second);

            Pair<String, Integer> carrierServiceResult =
                    carrierServiceQueue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            assertEquals("com.carrier.package1", carrierServiceResult.first);
            assertEquals(12345, (long) carrierServiceResult.second);

            // Update the state again, but only notify carrier privileges change this time
            Set<String> newPrivilegedPackageNames = Set.of("com.carrier.package1",
                    "com.carrier.package3");
            Set<Integer> newPrivilegedUids = Set.of(12345, 678910);

            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyRegistryMgr,
                    trm -> {
                        trm.notifyCarrierPrivilegesChanged(
                                0, newPrivilegedPackageNames, newPrivilegedUids);
                    });
            // The CarrierPrivileges pkgs and UIDs should be updated
            carrierPrivilegesResult =
                    carrierPrivilegesQueue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            assertEquals(newPrivilegedPackageNames, carrierPrivilegesResult.first);
            assertEquals(newPrivilegedUids, carrierPrivilegesResult.second);

            // And the CarrierService change notification should NOT be triggered
            assertNull(carrierServiceQueue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));

            // Registering cpc2 now immediately gets us the most recent state
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    telephonyManager,
                    tm -> tm.registerCarrierPrivilegesCallback(0, context.getMainExecutor(), cpc2));
            carrierPrivilegesResult = carrierPrivilegesQueue.poll(TIMEOUT_MILLIS,
                    TimeUnit.MILLISECONDS);
            assertEquals(newPrivilegedPackageNames, carrierPrivilegesResult.first);
            assertEquals(newPrivilegedUids, carrierPrivilegesResult.second);

            carrierServiceResult = carrierServiceQueue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            assertEquals("com.carrier.package1", carrierServiceResult.first);
            assertEquals(12345, (long) carrierServiceResult.second);

            // Removing cpc means it won't get the final callback when we restore the original state
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    telephonyManager, tm -> tm.unregisterCarrierPrivilegesCallback(cpc));
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    mTelephonyRegistryMgr,
                    trm -> {
                        trm.notifyCarrierPrivilegesChanged(
                                0, initialCarrierPrivilegesState.first,
                                initialCarrierPrivilegesState.second);
                        trm.notifyCarrierServiceChanged(0, initialCarrierServiceState.first,
                                initialCarrierServiceState.second);
                    });

            carrierPrivilegesResult = carrierPrivilegesQueue.poll(TIMEOUT_MILLIS,
                    TimeUnit.MILLISECONDS);
            assertEquals(initialCarrierPrivilegesState.first, carrierPrivilegesResult.first);
            assertEquals(initialCarrierPrivilegesState.second, carrierPrivilegesResult.second);

            carrierServiceResult = carrierServiceQueue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            assertEquals(initialCarrierServiceState.first, carrierServiceResult.first);
            assertEquals(initialCarrierServiceState.second, carrierServiceResult.second);

            // No further callbacks received
            assertNull(carrierPrivilegesQueue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
            assertNull(carrierServiceQueue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
        } finally {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(
                    telephonyManager,
                    tm -> {
                        tm.unregisterCarrierPrivilegesCallback(cpc); // redundant, but still allowed
                        tm.unregisterCarrierPrivilegesCallback(cpc2);
                    });
        }
    }

    private class TestCarrierPrivilegesCallback implements CarrierPrivilegesCallback {
        LinkedBlockingQueue<Pair<Set<String>, Set<Integer>>> mCarrierPrivilegesQueue;
        LinkedBlockingQueue<Pair<String, Integer>> mCarrierServiceQueue;

        TestCarrierPrivilegesCallback(
                LinkedBlockingQueue<Pair<Set<String>, Set<Integer>>> carrierPrivilegesQueue,
                LinkedBlockingQueue<Pair<String, Integer>> carrierServiceQueue) {
            mCarrierPrivilegesQueue = carrierPrivilegesQueue;
            mCarrierServiceQueue = carrierServiceQueue;
        }

        @Override
        public void onCarrierPrivilegesChanged(@NonNull Set<String> privilegedPackageNames,
                @NonNull Set<Integer> privilegedUids) {
            mCarrierPrivilegesQueue.offer(new Pair<>(privilegedPackageNames, privilegedUids));
        }

        @Override
        public void onCarrierServiceChanged(@Nullable String carrierServicePackageName,
                int carrierServiceUid) {
            mCarrierServiceQueue.offer(new Pair<>(carrierServicePackageName, carrierServiceUid));
        }
    }


    private static class SimultaneousCallingListener extends TelephonyCallback implements
            TelephonyCallback.SimultaneousCellularCallingSupportListener {

        private final LinkedBlockingQueue<Set<Integer>> mQueue;

        SimultaneousCallingListener(LinkedBlockingQueue<Set<Integer>> queue) {
            mQueue = queue;
        }

        @Override
        public void onSimultaneousCellularCallingSubscriptionsChanged(
                @NonNull Set<Integer> simultaneousCallingSubscriptionIds) {
            mQueue.offer(simultaneousCallingSubscriptionIds);
        }
    }

    @RequiresFlagsEnabled(Flags.FLAG_SIMULTANEOUS_CALLING_INDICATIONS)
    @Test
    public void testSimultaneousCellularCallingNotifications() throws Exception {
        LinkedBlockingQueue<Set<Integer>> queue = new LinkedBlockingQueue<>(2);
        SimultaneousCallingListener listener = new SimultaneousCallingListener(queue);
        Context context = InstrumentationRegistry.getContext();
        TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(telephonyManager,
                (tm) -> tm.registerTelephonyCallback(context.getMainExecutor(), listener),
                "android.permission.READ_PRIVILEGED_PHONE_STATE");
        // get the current value
        Set<Integer> initialVal = queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        Set<Integer> testVal = new HashSet<>();
        testVal.add(1000);
        testVal.add(1100);
        try {
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyRegistryMgr,
                    (trm) -> trm.notifySimultaneousCellularCallingSubscriptionsChanged(testVal));
            Set<Integer> resultVal = queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
            assertEquals(testVal, resultVal);
        } finally {
            // set back the initial value so that we do not cause an invalid value to be returned.
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyRegistryMgr,
                    (trm) -> trm.notifySimultaneousCellularCallingSubscriptionsChanged(initialVal));
            ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(telephonyManager,
                    (tm) -> tm.unregisterTelephonyCallback(listener));
        }
    }


    @Test
    public void testNotifyPreciseCallStateWithImsCall() throws Exception {
        Context context = InstrumentationRegistry.getContext();

        LinkedBlockingQueue<List<CallState>> queue = new LinkedBlockingQueue<>(1);
        TestTelephonyCallback testCb = new TestTelephonyCallback(queue);

        TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(telephonyManager,
                (tm) -> tm.registerTelephonyCallback(context.getMainExecutor(), testCb));
        // clear the initial result from registering the listener.
        queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        int[] dummyCallStates = {PreciseCallState.PRECISE_CALL_STATE_INCOMING,
                PreciseCallState.PRECISE_CALL_STATE_ACTIVE,
                PreciseCallState.PRECISE_CALL_STATE_IDLE};
        String[] dummyImsCallIds = {"1", "0", "-1"};
        int[] dummyImsServiceTypes = {ImsCallProfile.SERVICE_TYPE_NORMAL,
                ImsCallProfile.SERVICE_TYPE_NORMAL,
                ImsCallProfile.SERVICE_TYPE_NONE};
        int[] dummyImsCallTypes = {ImsCallProfile.CALL_TYPE_VT,
                ImsCallProfile.CALL_TYPE_VOICE,
                ImsCallProfile.CALL_TYPE_NONE};
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyRegistryMgr,
                (trm) -> trm.notifyPreciseCallState(
                        SubscriptionManager.getSlotIndex(
                                SubscriptionManager.getDefaultSubscriptionId()),
                        SubscriptionManager.getDefaultSubscriptionId(),
                        dummyCallStates, dummyImsCallIds, dummyImsServiceTypes, dummyImsCallTypes));

        List<CallState> testCallStatesResult = queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertNotNull("Timed out waiting for phone state change", testCallStatesResult);
        assertEquals(2, testCallStatesResult.size());
        assertEquals(PreciseCallState.PRECISE_CALL_STATE_ACTIVE,
                testCallStatesResult.get(0).getCallState());
        assertEquals(PreciseCallState.PRECISE_CALL_STATE_INCOMING,
                testCallStatesResult.get(1).getCallState());
        assertEquals("0",
                testCallStatesResult.get(0).getImsCallSessionId());
        assertEquals("1",
                testCallStatesResult.get(1).getImsCallSessionId());
        assertEquals(ImsCallProfile.CALL_TYPE_VOICE,
                testCallStatesResult.get(0).getImsCallType());
        assertEquals(ImsCallProfile.CALL_TYPE_VT,
                testCallStatesResult.get(1).getImsCallType());
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(telephonyManager,
                (tm) -> tm.unregisterTelephonyCallback(testCb));
    }

    @Test
    public void testNotifyPreciseCallStateWithCsCall() throws Exception {
        Context context = InstrumentationRegistry.getContext();

        LinkedBlockingQueue<List<CallState>> queue = new LinkedBlockingQueue<>(1);
        TestTelephonyCallback testCb = new TestTelephonyCallback(queue);

        TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(telephonyManager,
                (tm) -> tm.registerTelephonyCallback(context.getMainExecutor(), testCb));
        // clear the initial result from registering the listener.
        queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        int[] dummyCallStates = {PreciseCallState.PRECISE_CALL_STATE_INCOMING,
                PreciseCallState.PRECISE_CALL_STATE_ACTIVE,
                PreciseCallState.PRECISE_CALL_STATE_IDLE};
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyRegistryMgr,
                (trm) -> trm.notifyPreciseCallState(
                        SubscriptionManager.getSlotIndex(
                                SubscriptionManager.getDefaultSubscriptionId()),
                        SubscriptionManager.getDefaultSubscriptionId(),
                        dummyCallStates, null, null, null));

        List<CallState> testCallStatesResult = queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertNotNull("Timed out waiting for phone state change", testCallStatesResult);
        assertEquals(2, testCallStatesResult.size());
        assertEquals(PreciseCallState.PRECISE_CALL_STATE_ACTIVE,
                testCallStatesResult.get(0).getCallState());
        assertEquals(PreciseCallState.PRECISE_CALL_STATE_INCOMING,
                testCallStatesResult.get(1).getCallState());
        assertNull(testCallStatesResult.get(0).getImsCallSessionId());
        assertNull(testCallStatesResult.get(1).getImsCallSessionId());
        assertEquals(ImsCallProfile.SERVICE_TYPE_NONE,
                testCallStatesResult.get(0).getImsCallServiceType());
        assertEquals(ImsCallProfile.SERVICE_TYPE_NONE,
                testCallStatesResult.get(1).getImsCallServiceType());
        assertEquals(ImsCallProfile.CALL_TYPE_NONE,
                testCallStatesResult.get(0).getImsCallType());
        assertEquals(ImsCallProfile.CALL_TYPE_NONE,
                testCallStatesResult.get(1).getImsCallType());
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(telephonyManager,
                (tm) -> tm.unregisterTelephonyCallback(testCb));
    }

    @Test
    public void testNotifyPreciseCallStateLegacyCallback() throws Exception {
        Context context = InstrumentationRegistry.getContext();

        LinkedBlockingQueue<CallAttributes> queue = new LinkedBlockingQueue<>(1);
        TestTelephonyCallbackLegacy testCb = new TestTelephonyCallbackLegacy(queue);

        TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(telephonyManager,
                (tm) -> tm.registerTelephonyCallback(context.getMainExecutor(), testCb));
        // clear the initial result from registering the listener.
        queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        int[] dummyCallStates = {PreciseCallState.PRECISE_CALL_STATE_INCOMING,
                PreciseCallState.PRECISE_CALL_STATE_HOLDING,
                PreciseCallState.PRECISE_CALL_STATE_IDLE};
        String[] dummyImsCallIds = {"1", "0", "-1"};
        int[] dummyImsServiceTypes = {ImsCallProfile.SERVICE_TYPE_NORMAL,
                ImsCallProfile.SERVICE_TYPE_NORMAL,
                ImsCallProfile.SERVICE_TYPE_NONE};
        int[] dummyImsCallTypes = {ImsCallProfile.CALL_TYPE_VT,
                ImsCallProfile.CALL_TYPE_VOICE,
                ImsCallProfile.CALL_TYPE_NONE};
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyRegistryMgr,
                (trm) -> trm.notifyPreciseCallState(
                        SubscriptionManager.getSlotIndex(
                                SubscriptionManager.getDefaultSubscriptionId()),
                        SubscriptionManager.getDefaultSubscriptionId(),
                        dummyCallStates, dummyImsCallIds, dummyImsServiceTypes, dummyImsCallTypes));

        CallAttributes testCallAttributesResult = queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertNotNull("Timed out waiting for phone state change", testCallAttributesResult);

        assertEquals(PreciseCallState.PRECISE_CALL_STATE_HOLDING,
                testCallAttributesResult.getPreciseCallState().getForegroundCallState());
        assertEquals(PreciseCallState.PRECISE_CALL_STATE_IDLE,
                testCallAttributesResult.getPreciseCallState().getBackgroundCallState());
        assertEquals(PreciseCallState.PRECISE_CALL_STATE_INCOMING,
                testCallAttributesResult.getPreciseCallState().getRingingCallState());

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(telephonyManager,
                (tm) -> tm.unregisterTelephonyCallback(testCb));
    }

    @Test
    public void testNotifyPreciseCallStatePhoneStateListener() throws Exception {
        Context context = InstrumentationRegistry.getContext();

        LinkedBlockingQueue<CallAttributes> queue = new LinkedBlockingQueue<>(1);
        PhoneStateListener psl = new PhoneStateListener(context.getMainExecutor()) {
            @Override
            public void onCallAttributesChanged(CallAttributes ca) {
                queue.offer(ca);
            }
        };

        TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(telephonyManager,
                (tm) -> tm.listen(psl, PhoneStateListener.LISTEN_CALL_ATTRIBUTES_CHANGED));
        // clear the initial result from registering the listener.
        queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        int[] dummyCallStates = {PreciseCallState.PRECISE_CALL_STATE_IDLE,
                PreciseCallState.PRECISE_CALL_STATE_ACTIVE,
                PreciseCallState.PRECISE_CALL_STATE_HOLDING};
        String[] dummyImsCallIds = {"-1", "0", "1"};
        int[] dummyImsServiceTypes = {ImsCallProfile.SERVICE_TYPE_NONE,
                ImsCallProfile.SERVICE_TYPE_NORMAL,
                ImsCallProfile.SERVICE_TYPE_NORMAL};
        int[] dummyImsCallTypes = {ImsCallProfile.CALL_TYPE_NONE,
                ImsCallProfile.CALL_TYPE_VOICE,
                ImsCallProfile.CALL_TYPE_VT};
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyRegistryMgr,
                (trm) -> trm.notifyPreciseCallState(
                        SubscriptionManager.getSlotIndex(
                                SubscriptionManager.getDefaultSubscriptionId()),
                        SubscriptionManager.getDefaultSubscriptionId(),
                        dummyCallStates, dummyImsCallIds, dummyImsServiceTypes, dummyImsCallTypes));

        CallAttributes testCallAttributesResult = queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertNotNull("Timed out waiting for phone state change", testCallAttributesResult);

        assertEquals(PreciseCallState.PRECISE_CALL_STATE_ACTIVE,
                testCallAttributesResult.getPreciseCallState().getForegroundCallState());
        assertEquals(PreciseCallState.PRECISE_CALL_STATE_HOLDING,
                testCallAttributesResult.getPreciseCallState().getBackgroundCallState());
        assertEquals(PreciseCallState.PRECISE_CALL_STATE_IDLE,
                testCallAttributesResult.getPreciseCallState().getRingingCallState());
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(telephonyManager,
                (tm) -> tm.listen(psl, PhoneStateListener.LISTEN_NONE));
    }

    @Test
    public void testNotifyOutgoingEmergencyCall() throws Exception {
        Context context = InstrumentationRegistry.getContext();
        String testEmergencyNumber = "9998887776655443210";
        EmergencyNumber emergencyNumber = new EmergencyNumber(
                testEmergencyNumber,
                "us",
                "30",
                EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_UNSPECIFIED,
                new ArrayList<>(),
                EmergencyNumber.EMERGENCY_NUMBER_SOURCE_NETWORK_SIGNALING,
                EmergencyNumber.EMERGENCY_CALL_ROUTING_NORMAL);
        int defaultSubId = SubscriptionManager.getDefaultSubscriptionId();
        int phoneId = SubscriptionManager.getSlotIndex(defaultSubId);

        LinkedBlockingQueue<List<CallState>> queue = new LinkedBlockingQueue<>(1);
        TestTelephonyCallback testCb = new TestTelephonyCallback(queue);

        // Register telephony callback.
        TelephonyManager telephonyManager = context.getSystemService(TelephonyManager.class);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(telephonyManager,
                (tm) -> tm.registerTelephonyCallback(context.getMainExecutor(), testCb));
        // clear the initial result from registering the listener.
        queue.poll(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);

        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(mTelephonyRegistryMgr,
                (trm) -> trm.notifyOutgoingEmergencyCall(phoneId, defaultSubId, emergencyNumber));
        assertTrue(testCb.mCallbackSemaphore.tryAcquire(15, TimeUnit.SECONDS));
        assertEquals(emergencyNumber, testCb.mLastOutgoingEmergencyNumber);
        ShellIdentityUtils.invokeMethodWithShellPermissionsNoReturn(telephonyManager,
                (tm) -> tm.unregisterTelephonyCallback(testCb));
    }

    private class TestTelephonyCallback extends TelephonyCallback
            implements TelephonyCallback.CallAttributesListener,
            TelephonyCallback.OutgoingEmergencyCallListener {
        public Semaphore mCallbackSemaphore = new Semaphore(0);
        LinkedBlockingQueue<List<CallState>> mTestCallStatesQueue;
        private EmergencyNumber mLastOutgoingEmergencyNumber;

        TestTelephonyCallback(LinkedBlockingQueue<List<CallState>> queue) {
            mTestCallStatesQueue = queue;
        }
        @Override
        public void onCallStatesChanged(@NonNull List<CallState> callStateList) {
            mTestCallStatesQueue.offer(callStateList);
        }

        @Override
        public void onOutgoingEmergencyCall(EmergencyNumber placedEmergencyNumber,
                int subscriptionId) {
            Log.i("telecomTag", "onOutgoingEmergencyCall: telephony callback");
            mLastOutgoingEmergencyNumber = placedEmergencyNumber;
            mCallbackSemaphore.release();
        }
    }

    private class TestTelephonyCallbackLegacy extends TelephonyCallback
            implements TelephonyCallback.CallAttributesListener {
        LinkedBlockingQueue<CallAttributes> mTestCallAttributes;
        TestTelephonyCallbackLegacy(LinkedBlockingQueue<CallAttributes> queue) {
            mTestCallAttributes = queue;
        }
        @Override
        public void onCallAttributesChanged(@NonNull CallAttributes callAttributes) {
            mTestCallAttributes.offer(callAttributes);
        }
    }
}
