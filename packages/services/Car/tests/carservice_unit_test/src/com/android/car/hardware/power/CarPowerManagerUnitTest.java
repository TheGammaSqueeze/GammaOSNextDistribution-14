/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.car.hardware.power;

import static android.car.hardware.power.PowerComponent.AUDIO;
import static android.car.hardware.power.PowerComponent.BLUETOOTH;
import static android.car.hardware.power.PowerComponent.CELLULAR;
import static android.car.hardware.power.PowerComponent.CPU;
import static android.car.hardware.power.PowerComponent.DISPLAY;
import static android.car.hardware.power.PowerComponent.ETHERNET;
import static android.car.hardware.power.PowerComponent.INPUT;
import static android.car.hardware.power.PowerComponent.LOCATION;
import static android.car.hardware.power.PowerComponent.MEDIA;
import static android.car.hardware.power.PowerComponent.MICROPHONE;
import static android.car.hardware.power.PowerComponent.NFC;
import static android.car.hardware.power.PowerComponent.PROJECTION;
import static android.car.hardware.power.PowerComponent.TRUSTED_DEVICE_DETECTION;
import static android.car.hardware.power.PowerComponent.VISUAL_INTERACTION;
import static android.car.hardware.power.PowerComponent.VOICE_INTERACTION;
import static android.car.hardware.power.PowerComponent.WIFI;

import static com.android.car.test.power.CarPowerPolicyUtil.assertPolicyIdentical;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import android.annotation.NonNull;
import android.automotive.powerpolicy.internal.ICarPowerPolicyDelegate;
import android.car.Car;
import android.car.feature.Flags;
import android.car.hardware.power.CarPowerManager;
import android.car.hardware.power.CarPowerPolicy;
import android.car.hardware.power.CarPowerPolicyFilter;
import android.car.hardware.power.PowerComponent;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.car.test.mocks.JavaMockitoHelper;
import android.car.test.util.TemporaryFile;
import android.car.testapi.FakeRefactoredCarPowerPolicyDaemon;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.frameworks.automotive.powerpolicy.internal.ICarPowerPolicySystemNotification;
import android.hardware.automotive.vehicle.VehicleApPowerStateReq;
import android.hardware.automotive.vehicle.VehicleApPowerStateShutdownParam;
import android.os.IInterface;
import android.os.UserManager;
import android.util.AtomicFile;
import android.util.Log;
import android.util.SparseBooleanArray;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.car.R;
import com.android.car.hal.MockedPowerHalService;
import com.android.car.hal.PowerHalService;
import com.android.car.hal.PowerHalService.PowerState;
import com.android.car.power.CarPowerManagementService;
import com.android.car.power.PowerComponentHandler;
import com.android.car.systeminterface.DisplayInterface;
import com.android.car.systeminterface.SystemInterface;
import com.android.car.systeminterface.SystemStateInterface;
import com.android.car.user.CarUserService;
import com.android.compatibility.common.util.PollingCheck;
import com.android.internal.annotations.GuardedBy;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@SmallTest
public final class CarPowerManagerUnitTest extends AbstractExtendedMockitoTestCase {
    private static final String TAG = CarPowerManagerUnitTest.class.getSimpleName();
    private static final long WAIT_TIMEOUT_MS = 5_000;
    private static final long WAIT_TIMEOUT_LONG_MS = 10_000;
    // A shorter value for use when the test is expected to time out
    private static final long WAIT_WHEN_TIMEOUT_EXPECTED_MS = 100;

    private final MockDisplayInterface mDisplayInterface = new MockDisplayInterface();
    private final MockSystemStateInterface mSystemStateInterface = new MockSystemStateInterface();
    private final ICarPowerPolicyDelegate mRefactoredPowerPolicyDaemon =
            new FakeRefactoredCarPowerPolicyDaemon(
                    /* fileKernelSilentMode= */ new TemporaryFile("KERNEL_SILENT_FILE"),
                    /* customComponents= */ null);

    @Spy
    private final Context mContext =
            InstrumentationRegistry.getInstrumentation().getTargetContext();
    private final Executor mExecutor = mContext.getMainExecutor();
    private final TemporaryFile mComponentStateFile;

    private MockedPowerHalService mPowerHal;
    private SystemInterface mSystemInterface;
    private CarPowerManagementService mService;
    private CarPowerManager mCarPowerManager;
    private PowerComponentHandler mPowerComponentHandler;

    private static final Object sLock = new Object();

    @Mock
    private Resources mResources;
    @Mock
    private Car mCar;
    @Mock
    private UserManager mUserManager;
    @Mock
    private CarUserService mCarUserService;
    //TODO(286303350): replace this with refactored power policy daemon once refactor is complete
    @Mock
    private ICarPowerPolicySystemNotification mPowerPolicyDaemon;

    public CarPowerManagerUnitTest() throws Exception {
        super(CarPowerManager.TAG);
        mComponentStateFile = new TemporaryFile("COMPONENT_STATE_FILE");
    }

    @Before
    public void setUp() throws Exception {
        mPowerHal = new MockedPowerHalService(/*isPowerStateSupported=*/true,
                /*isDeepSleepAllowed=*/true,
                /*isHibernationAllowed=*/true,
                /*isTimedWakeupAllowed=*/true);
        mSystemInterface = SystemInterface.Builder.defaultSystemInterface(mContext)
                .withDisplayInterface(mDisplayInterface)
                .withSystemStateInterface(mSystemStateInterface)
                .build();
        setService();
        mCarPowerManager = new CarPowerManager(mCar, mService);
    }

    @After
    public void tearDown() throws Exception {
        if (mService != null) {
            mService.release();
        }
    }

    @Test
    public void testRequestShutdownOnNextSuspend_positive() throws Exception {
        setPowerOn();
        // Tell it to shutdown
        mCarPowerManager.requestShutdownOnNextSuspend();
        // Request suspend
        setPowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP);
        // Verify shutdown
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_SHUTDOWN_START, 0);
    }

    @Test
    public void testRequestShutdownOnNextSuspend_negative() throws Exception {
        setPowerOn();

        // Do not tell it to shutdown

        // Request suspend
        setPowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP);
        // Verify suspend
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_DEEP_SLEEP_ENTRY, 0);
    }

    @Test
    public void testScheduleNextWakeupTime() throws Exception {
        setPowerOn();

        int wakeTime = 1234;
        mCarPowerManager.scheduleNextWakeupTime(wakeTime);

        setPowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP);

        // Verify that we suspended with the requested wake-up time
        assertStateReceivedForShutdownOrSleepWithPostpone(
                PowerHalService.SET_DEEP_SLEEP_ENTRY, wakeTime);
    }

    @Test
    public void testSetListener() throws Exception {
        setPowerOn();

        WaitablePowerStateListener listener = new WaitablePowerStateListener(3);

        setPowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP);

        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_DEEP_SLEEP_ENTRY, 0);

        List<Integer> states = listener.await();
        checkThatStatesReceivedInOrder("Check that events were received in order", states,
                List.of(CarPowerManager.STATE_PRE_SHUTDOWN_PREPARE,
                        CarPowerManager.STATE_SHUTDOWN_PREPARE,
                        CarPowerManager.STATE_SUSPEND_ENTER));
    }

    @Test
    public void testSetListenerWithCompletion() throws Exception {
        grantAdjustShutdownProcessPermission();
        List<Integer> expectedStates = List.of(CarPowerManager.STATE_ON,
                CarPowerManager.STATE_PRE_SHUTDOWN_PREPARE,
                CarPowerManager.STATE_SHUTDOWN_PREPARE,
                CarPowerManager.STATE_SUSPEND_ENTER);
        WaitablePowerStateListenerWithCompletion listener =
                new WaitablePowerStateListenerWithCompletion(/* initialCount= */ 4);

        setPowerOn();
        setPowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP);
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_DEEP_SLEEP_ENTRY, 0);

        List<Integer> states = listener.await();
        checkThatStatesReceivedInOrder("Check that events were received in order", states,
                expectedStates);
    }

    @Test
    public void testClearListener() throws Exception {
        setPowerOn();

        // Set a listener with a short timeout, because we expect the timeout to happen
        WaitablePowerStateListener listener =
                new WaitablePowerStateListener(1, WAIT_WHEN_TIMEOUT_EXPECTED_MS);

        mCarPowerManager.clearListener();

        setPowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP);

        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_DEEP_SLEEP_ENTRY, 0);
        // Verify that the listener didn't run
        assertThrows(IllegalStateException.class, () -> listener.await());
    }

    @Test
    public void testGetPowerState() throws Exception {
        setPowerOn();
        assertThat(mCarPowerManager.getPowerState()).isEqualTo(PowerHalService.SET_ON);

        // Request suspend
        setPowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP);
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_DEEP_SLEEP_ENTRY, 0);
        assertThat(mCarPowerManager.getPowerState())
                .isEqualTo(PowerHalService.SET_DEEP_SLEEP_ENTRY);
    }

    @Test
    public void testGetCurrentPowerPolicy() throws Exception {
        grantPowerPolicyPermission();
        CarPowerPolicy expected = new CarPowerPolicy("test_policy4",
                new int[]{AUDIO, MEDIA, DISPLAY, INPUT, CPU},
                new int[]{BLUETOOTH, CELLULAR, ETHERNET, LOCATION, MICROPHONE, NFC, PROJECTION,
                        TRUSTED_DEVICE_DETECTION, VISUAL_INTERACTION, VOICE_INTERACTION, WIFI});
        PolicyDefinition[] policyDefinitions = new PolicyDefinition[]{
                new PolicyDefinition("test_policy1", new String[]{"WIFI"}, new String[]{"AUDIO"}),
                new PolicyDefinition("test_policy2", new String[]{"WIFI", "DISPLAY"},
                        new String[]{"NFC"}),
                new PolicyDefinition("test_policy3", new String[]{"CPU", "INPUT"},
                        new String[]{"WIFI"}),
                new PolicyDefinition("test_policy4", new String[]{"MEDIA", "AUDIO"},
                        new String[]{})};
        for (PolicyDefinition definition : policyDefinitions) {
            mService.definePowerPolicy(definition.policyId, definition.enabledComponents,
                    definition.disabledComponents);
        }

        for (PolicyDefinition definition : policyDefinitions) {
            mCarPowerManager.applyPowerPolicy(definition.policyId);
        }

        assertPolicyIdentical(expected, mCarPowerManager.getCurrentPowerPolicy());
    }

    @Test
    public void testApplyPowerPolicy() throws Exception {
        grantPowerPolicyPermission();
        String policyId = "no_change_policy";
        mService.definePowerPolicy(policyId, new String[0], new String[0]);

        mCarPowerManager.applyPowerPolicy(policyId);

        assertThat(mCarPowerManager.getCurrentPowerPolicy().getPolicyId()).isEqualTo(policyId);
    }

    @Test
    public void testApplyPowerPolicy_invalidId() throws Exception {
        grantPowerPolicyPermission();
        String policyId = "invalid_power_policy";

        assertThrows(IllegalArgumentException.class,
                () -> mCarPowerManager.applyPowerPolicy(policyId));
    }

    @Test
    public void testApplyPowerPolicy_nullPolicyId() throws Exception {
        grantPowerPolicyPermission();
        assertThrows(IllegalArgumentException.class, () -> mCarPowerManager.applyPowerPolicy(null));
    }

    @Test
    public void testAddPowerPolicyListener() throws Exception {
        grantPowerPolicyPermission();

        // Prepare for test
        applyInitialPolicyForTest(/* policyName= */ "audio_off", /* enabledComponents= */
                new String[]{}, /* disabledComponents= */ new String[]{"AUDIO"});

        String policyId = "audio_on_wifi_off";
        mService.definePowerPolicy(policyId, new String[]{"AUDIO"}, new String[]{"WIFI"});
        MockedPowerPolicyListener listenerAudio = new MockedPowerPolicyListener();
        MockedPowerPolicyListener listenerWifi = new MockedPowerPolicyListener();
        MockedPowerPolicyListener listenerLocation = new MockedPowerPolicyListener();
        CarPowerPolicyFilter filterAudio = new CarPowerPolicyFilter.Builder()
                .setComponents(PowerComponent.AUDIO).build();
        CarPowerPolicyFilter filterWifi = new CarPowerPolicyFilter.Builder()
                .setComponents(PowerComponent.WIFI).build();
        CarPowerPolicyFilter filterLocation = new CarPowerPolicyFilter.Builder()
                .setComponents(PowerComponent.LOCATION).build();

        mCarPowerManager.addPowerPolicyListener(mExecutor, filterAudio, listenerAudio);
        mCarPowerManager.addPowerPolicyListener(mExecutor, filterWifi, listenerWifi);
        mCarPowerManager.addPowerPolicyListener(mExecutor, filterLocation, listenerLocation);
        mCarPowerManager.applyPowerPolicy(policyId);

        assertPowerPolicyId(listenerAudio, policyId, "Current policy ID of listenerAudio is not "
                + policyId);
        assertPowerPolicyId(listenerWifi, policyId, "Current policy ID of listenerWifi is not "
                + policyId);
        assertThat(listenerLocation.getCurrentPolicyId()).isNull();
    }

    @Test
    public void testAddPowerPolicyListener_Twice_WithDifferentFilters() throws Exception {
        grantPowerPolicyPermission();
        String policyId = "audio_on_wifi_off";
        mService.definePowerPolicy(policyId, new String[]{"AUDIO"}, new String[]{"WIFI"});
        MockedPowerPolicyListener listener = new MockedPowerPolicyListener();
        CarPowerPolicyFilter filterAudio = new CarPowerPolicyFilter.Builder()
                .setComponents(PowerComponent.AUDIO).build();
        CarPowerPolicyFilter filterLocation = new CarPowerPolicyFilter.Builder()
                .setComponents(PowerComponent.LOCATION).build();

        mCarPowerManager.addPowerPolicyListener(mExecutor, filterAudio, listener);
        mCarPowerManager.addPowerPolicyListener(mExecutor, filterLocation, listener);
        mCarPowerManager.applyPowerPolicy(policyId);

        assertThat(listener.getCurrentPolicyId()).isNull();
    }

    @Test
    public void testAddPowerPolicyListener_nullListener() throws Exception {
        MockedPowerPolicyListener listener = new MockedPowerPolicyListener();
        CarPowerPolicyFilter filter = new CarPowerPolicyFilter.Builder()
                .setComponents(PowerComponent.AUDIO).build();

        assertThrows(NullPointerException.class,
                () -> mCarPowerManager.addPowerPolicyListener(null, filter, listener));
        assertThrows(NullPointerException.class,
                () -> mCarPowerManager.addPowerPolicyListener(mExecutor, filter, null));
        assertThrows(NullPointerException.class,
                () -> mCarPowerManager.addPowerPolicyListener(mExecutor, null, listener));
    }

    @Test
    public void testRemovePowerPolicyListener() throws Exception {
        grantPowerPolicyPermission();

        String initialPolicyId = "audio_off";
        applyInitialPolicyForTest(initialPolicyId, /* enabledComponents= */
                new String[]{}, /* disabledComponents= */ new String[]{"AUDIO"});

        String policyId = "audio_on_wifi_off";
        mService.definePowerPolicy(policyId, new String[]{"AUDIO"}, new String[]{"WIFI"});
        MockedPowerPolicyListener listenerOne = new MockedPowerPolicyListener();
        MockedPowerPolicyListener listenerTwo = new MockedPowerPolicyListener();
        CarPowerPolicyFilter filterAudio = new CarPowerPolicyFilter.Builder()
                .setComponents(PowerComponent.AUDIO).build();

        mCarPowerManager.addPowerPolicyListener(mExecutor, filterAudio, listenerOne);
        mCarPowerManager.addPowerPolicyListener(mExecutor, filterAudio, listenerTwo);
        mCarPowerManager.removePowerPolicyListener(listenerOne);
        mCarPowerManager.applyPowerPolicy(policyId);

        String receivedPolicyId = listenerOne.getCurrentPolicyId();
        assertWithMessage("Policy ID received after removing listeners")
                .that(receivedPolicyId == null || receivedPolicyId.equals(initialPolicyId))
                .isTrue();
        assertPowerPolicyId(listenerTwo, policyId, "Current policy ID of listenerTwo is not "
                + policyId);
    }

    private void applyInitialPolicyForTest(String policyName, String[] enabledComponents,
            String[] disabledComponents) {
        mService.definePowerPolicy(policyName, enabledComponents, disabledComponents);
        mCarPowerManager.applyPowerPolicy(policyName);
    }

    @Test
    public void testRemovePowerPolicyListener_Twice() throws Exception {
        grantPowerPolicyPermission();
        MockedPowerPolicyListener listener = new MockedPowerPolicyListener();
        CarPowerPolicyFilter filter = new CarPowerPolicyFilter.Builder()
                .setComponents(PowerComponent.AUDIO).build();

        // Remove unregistered listener should not throw an exception.
        mCarPowerManager.removePowerPolicyListener(listener);

        mCarPowerManager.addPowerPolicyListener(mExecutor, filter, listener);
        mCarPowerManager.removePowerPolicyListener(listener);
        // Remove the same listener twice should nont throw an exception.
        mCarPowerManager.removePowerPolicyListener(listener);
    }

    @Test
    public void testRemovePowerPolicyListener_nullListener() throws Exception {
        assertThrows(NullPointerException.class,
                () -> mCarPowerManager.removePowerPolicyListener(null));
    }

    /**
     * Helper method to create mService and initialize a test case
     */
    private void setService() throws Exception {
        Log.i(TAG, "setService(): overridden overlay properties: "
                + ", maxGarageModeRunningDurationInSecs="
                + mResources.getInteger(R.integer.maxGarageModeRunningDurationInSecs));
        doReturn(mResources).when(mContext).getResources();
        doReturn(false).when(mResources).getBoolean(
                R.bool.config_enablePassengerDisplayPowerSaving);
        mPowerComponentHandler = new PowerComponentHandler(mContext, mSystemInterface,
                new AtomicFile(mComponentStateFile.getFile()));
        IInterface powerPolicyDaemon;
        if (Flags.carPowerPolicyRefactoring()) {
            powerPolicyDaemon = mRefactoredPowerPolicyDaemon;
        } else {
            powerPolicyDaemon = mPowerPolicyDaemon;
        }
        mService = new CarPowerManagementService(mContext, mResources, mPowerHal, mSystemInterface,
                mUserManager, mCarUserService, powerPolicyDaemon, mPowerComponentHandler,
                /* featureFlags= */ null, /* screenOffHandler= */ null,
                /* silentModeHwStatePath= */ null, /* silentModeKernelStatePath= */ null,
                /* bootReason= */ null);
        mService.init();
        if (Flags.carPowerPolicyRefactoring()) {
            mService.initializePowerPolicy();
        }
        mService.setShutdownTimersForTest(0, 0);
        assertStateReceived(PowerHalService.SET_WAIT_FOR_VHAL, 0);
    }

    private void assertStateReceived(int expectedState, int expectedParam) throws Exception {
        int[] state = mPowerHal.waitForSend(WAIT_TIMEOUT_MS);
        assertThat(state).asList().containsExactly(expectedState, expectedParam).inOrder();
    }

    /**
     * Helper method to get the system into ON
     */
    private void setPowerOn() throws Exception {
        setPowerState(VehicleApPowerStateReq.ON, 0);
        int[] state = mPowerHal.waitForSend(WAIT_TIMEOUT_MS);
        assertThat(state[0]).isEqualTo(PowerHalService.SET_ON);
    }

    /**
     * Helper to set the PowerHal state
     *
     * @param stateEnum  Requested state enum
     * @param stateParam Addition state parameter
     */
    private void setPowerState(int stateEnum, int stateParam) {
        mPowerHal.setCurrentPowerState(new PowerState(stateEnum, stateParam));
    }

    private void assertStateReceivedForShutdownOrSleepWithPostpone(
            int lastState, int stateParameter) throws Exception {
        long startTime = System.currentTimeMillis();
        while (true) {
            int[] state = mPowerHal.waitForSend(WAIT_TIMEOUT_LONG_MS);
            if (state[0] == lastState) {
                assertThat(state[1]).isEqualTo(stateParameter);
                return;
            }
            assertThat(state[0]).isEqualTo(PowerHalService.SET_SHUTDOWN_POSTPONE);
            assertThat(System.currentTimeMillis() - startTime).isLessThan(WAIT_TIMEOUT_LONG_MS);
        }
    }

    private void grantPowerPolicyPermission() {
        when(mCar.getContext()).thenReturn(mContext);
        doReturn(PackageManager.PERMISSION_GRANTED).when(mContext)
                .checkCallingOrSelfPermission(Car.PERMISSION_CONTROL_CAR_POWER_POLICY);
        doReturn(PackageManager.PERMISSION_GRANTED).when(mContext)
                .checkCallingOrSelfPermission(Car.PERMISSION_READ_CAR_POWER_POLICY);
    }

    private void grantAdjustShutdownProcessPermission() {
        when(mCar.getContext()).thenReturn(mContext);
        doReturn(PackageManager.PERMISSION_GRANTED).when(mContext)
                .checkCallingOrSelfPermission(Car.PERMISSION_CONTROL_SHUTDOWN_PROCESS);
    }

    private static void checkThatStatesReceivedInOrder(String message, List<Integer> states,
            List<Integer> referenceStates) {
        assertWithMessage(message).that(states).containsExactlyElementsIn(
                referenceStates).inOrder();
    }

    private static void assertPowerPolicyId(MockedPowerPolicyListener listener, String policyId,
            String errorMsg) throws Exception {
        PollingCheck.check(errorMsg, WAIT_TIMEOUT_MS,
                () -> policyId.equals(listener.getCurrentPolicyId()));
    }

    private static boolean isCompletionAllowed(@CarPowerManager.CarPowerState int state) {
        switch (state) {
            case CarPowerManager.STATE_PRE_SHUTDOWN_PREPARE:
            case CarPowerManager.STATE_SHUTDOWN_PREPARE:
            case CarPowerManager.STATE_SHUTDOWN_ENTER:
            case CarPowerManager.STATE_SUSPEND_ENTER:
            case CarPowerManager.STATE_HIBERNATION_ENTER:
            case CarPowerManager.STATE_POST_SHUTDOWN_ENTER:
            case CarPowerManager.STATE_POST_SUSPEND_ENTER:
            case CarPowerManager.STATE_POST_HIBERNATION_ENTER:
                return true;
            default:
                return false;
        }
    }

    private static final class MockDisplayInterface implements DisplayInterface {
        @GuardedBy("sLock")
        private final SparseBooleanArray mDisplayOn = new SparseBooleanArray();
        private final Semaphore mDisplayStateWait = new Semaphore(0);

        @Override
        public void init(CarPowerManagementService carPowerManagementService,
                CarUserService carUserService) {}

        @Override
        public void setDisplayBrightness(int brightness) {}

        @Override
        public void setDisplayBrightness(int displayId, int brightness) {}

        @Override
        public void setDisplayState(int displayId, boolean on) {
            synchronized (sLock) {
                mDisplayOn.put(displayId, on);
            }
            mDisplayStateWait.release();
        }

        @Override
        public void setAllDisplayState(boolean on) {
            synchronized (sLock) {
                for (int i = 0; i < mDisplayOn.size(); i++) {
                    int displayId = mDisplayOn.keyAt(i);
                    setDisplayState(displayId, on);
                }
            }
        }

        @Override
        public void startDisplayStateMonitoring() {}

        @Override
        public void stopDisplayStateMonitoring() {}

        @Override
        public void refreshDisplayBrightness() {}

        @Override
        public void refreshDisplayBrightness(int displayId) {}

        @Override
        public boolean isAnyDisplayEnabled() {
            synchronized (sLock) {
                for (int i = 0; i < mDisplayOn.size(); i++) {
                    int displayId = mDisplayOn.keyAt(i);
                    if (isDisplayEnabled(displayId)) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        public boolean isDisplayEnabled(int displayId) {
            synchronized (sLock) {
                return mDisplayOn.get(displayId);
            }
        }
    }

    /**
     * Helper class to set a power-state listener,
     * verify that the listener gets called the
     * right number of times, and return the final
     * power state.
     */
    private final class WaitablePowerStateListener {
        private final CountDownLatch mLatch;
        private List<Integer> mReceivedStates = new ArrayList<Integer>();
        private long mTimeoutValue = WAIT_TIMEOUT_MS;

        WaitablePowerStateListener(int initialCount, long customTimeout) {
            this(initialCount);
            mTimeoutValue = customTimeout;
        }

        WaitablePowerStateListener(int initialCount) {
            mLatch = new CountDownLatch(initialCount);
            mCarPowerManager.setListener(mContext.getMainExecutor(),
                    (state) -> {
                        mReceivedStates.add(state);
                        mLatch.countDown();
                    });
        }

        List<Integer> await() throws Exception {
            JavaMockitoHelper.await(mLatch, mTimeoutValue);
            return List.copyOf(mReceivedStates);
        }
    }

    /**
     * Helper class to set a power-state listener with completion,
     * verify that the listener gets called the right number of times,
     * verify that the CompletablePowerStateChangeFuture is provided, complete the
     * CompletablePowerStateChangeFuture, and return the all listened states in order.
     */
    private final class WaitablePowerStateListenerWithCompletion {
        private final CountDownLatch mLatch;
        private final List<Integer> mReceivedStates = new ArrayList<>();
        private int mRemainingCount;

        WaitablePowerStateListenerWithCompletion(int initialCount) {
            mRemainingCount = initialCount;
            mLatch = new CountDownLatch(initialCount);
            mCarPowerManager.setListenerWithCompletion(mContext.getMainExecutor(),
                    (state, future) -> {
                        mReceivedStates.add(state);
                        mRemainingCount--;
                        if (isCompletionAllowed(state)) {
                            assertThat(future).isNotNull();
                            future.complete();
                        } else {
                            assertThat(future).isNull();
                        }
                        mLatch.countDown();
                    });
        }

        List<Integer> await() throws Exception {
            JavaMockitoHelper.await(mLatch, WAIT_TIMEOUT_MS);
            assertThat(mRemainingCount).isEqualTo(0);
            return mReceivedStates;
        }
    }

    private static final class MockSystemStateInterface implements SystemStateInterface {
        private final Semaphore mShutdownWait = new Semaphore(0);
        private final Semaphore mSleepWait = new Semaphore(0);
        private final Semaphore mSleepExitWait = new Semaphore(0);

        @GuardedBy("sLock")
        private boolean mWakeupCausedByTimer = false;

        @Override
        public void shutdown() {
            mShutdownWait.release();
        }

        public void waitForShutdown(long timeoutMs) throws Exception {
            JavaMockitoHelper.await(mShutdownWait, timeoutMs);
        }

        @Override
        public boolean enterDeepSleep() {
            return simulateSleep();
        }

        @Override
        public boolean enterHibernation() {
            return simulateSleep();
        }

        private boolean simulateSleep() {
            mSleepWait.release();
            try {
                mSleepExitWait.acquire();
            } catch (InterruptedException e) {
            }
            return true;
        }

        public void waitForSleepEntryAndWakeup(long timeoutMs) throws Exception {
            JavaMockitoHelper.await(mSleepWait, timeoutMs);
            mSleepExitWait.release();
        }

        @Override
        public void scheduleActionForBootCompleted(Runnable action, Duration delay,
                Duration delayRange) {}

        @Override
        public boolean isWakeupCausedByTimer() {
            Log.i(TAG, "isWakeupCausedByTimer:" + mWakeupCausedByTimer);
            return mWakeupCausedByTimer;
        }

        public void setWakeupCausedByTimer(boolean set) {
            synchronized (sLock) {
                mWakeupCausedByTimer = set;
            }
        }

        @Override
        public boolean isSystemSupportingDeepSleep() {
            return true;
        }
    }

    private final class MockedPowerPolicyListener implements
            CarPowerManager.CarPowerPolicyListener {
        private static final int MAX_LISTENER_WAIT_TIME_SEC = 1;

        private final CountDownLatch mLatch = new CountDownLatch(1);
        private String mCurrentPolicyId;

        @Override
        public void onPolicyChanged(@NonNull CarPowerPolicy policy) {
            mCurrentPolicyId = policy.getPolicyId();
            mLatch.countDown();
        }

        public String getCurrentPolicyId() throws Exception {
            if (mLatch.await(MAX_LISTENER_WAIT_TIME_SEC, TimeUnit.SECONDS)) {
                return mCurrentPolicyId;
            }
            return null;
        }
    }

    private static final class PolicyDefinition {
        public final String policyId;
        public final String[] enabledComponents;
        public final String[] disabledComponents;

        private PolicyDefinition(String policyId, String[] enabledComponents,
                String[] disabledComponents) {
            this.policyId = policyId;
            this.enabledComponents = enabledComponents;
            this.disabledComponents = disabledComponents;
        }
    }
}
