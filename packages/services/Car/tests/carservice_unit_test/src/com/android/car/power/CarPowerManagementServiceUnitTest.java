/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.car.power;

import static android.car.hardware.power.PowerComponentUtil.powerComponentToString;
import static android.car.hardware.power.PowerComponentUtil.powerComponentsToStrings;
import static android.net.ConnectivityManager.TETHERING_WIFI;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.automotive.powerpolicy.internal.ICarPowerPolicyDelegate;
import android.car.Car;
import android.car.ICarResultReceiver;
import android.car.builtin.app.VoiceInteractionHelper;
import android.car.feature.FakeFeatureFlagsImpl;
import android.car.feature.Flags;
import android.car.hardware.power.CarPowerManager;
import android.car.hardware.power.CarPowerPolicy;
import android.car.hardware.power.CarPowerPolicyFilter;
import android.car.hardware.power.ICarPowerPolicyListener;
import android.car.hardware.power.ICarPowerStateListener;
import android.car.hardware.power.PowerComponent;
import android.car.remoteaccess.CarRemoteAccessManager;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.car.test.mocks.JavaMockitoHelper;
import android.car.test.util.TemporaryFile;
import android.car.testapi.FakeRefactoredCarPowerPolicyDaemon;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.content.res.Resources;
import android.frameworks.automotive.powerpolicy.internal.ICarPowerPolicySystemNotification;
import android.frameworks.automotive.powerpolicy.internal.PolicyState;
import android.hardware.automotive.vehicle.VehicleApPowerBootupReason;
import android.hardware.automotive.vehicle.VehicleApPowerStateReq;
import android.hardware.automotive.vehicle.VehicleApPowerStateShutdownParam;
import android.hardware.automotive.vehicle.VehicleProperty;
import android.net.TetheringManager;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.os.UserManager;
import android.util.AtomicFile;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.util.SparseBooleanArray;
import android.util.SparseIntArray;
import android.util.SparseLongArray;
import android.util.proto.ProtoOutputStream;
import android.view.Display;

import androidx.test.filters.FlakyTest;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.car.CarLocalServices;
import com.android.car.CarServiceUtils;
import com.android.car.R;
import com.android.car.VehicleStub;
import com.android.car.hal.HalPropValue;
import com.android.car.hal.HalPropValueBuilder;
import com.android.car.hal.MockedPowerHalService;
import com.android.car.hal.PowerHalService;
import com.android.car.hal.PowerHalService.PowerState;
import com.android.car.hal.VehicleHal;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.systeminterface.DisplayInterface;
import com.android.car.systeminterface.IOInterface;
import com.android.car.systeminterface.SystemInterface;
import com.android.car.systeminterface.SystemStateInterface;
import com.android.car.systeminterface.WakeLockInterface;
import com.android.car.test.utils.TemporaryDirectory;
import com.android.car.user.CarUserService;
import com.android.compatibility.common.util.PollingCheck;
import com.android.internal.annotations.GuardedBy;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Spy;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@SmallTest
public final class CarPowerManagementServiceUnitTest extends AbstractExtendedMockitoTestCase {
    private static final String TAG = CarPowerManagementServiceUnitTest.class.getSimpleName();

    private static final Object sLock = new Object();

    private static final long WAIT_TIMEOUT_MS = 2000;
    private static final long WAIT_TIMEOUT_LONG_MS = 5000;
    private static final int WAKE_UP_DELAY = 100;
    private static final String NONSILENT_STRING = "0";
    private static final String NORMAL_BOOT = "reboot,shell";

    private static final String COMMAND_DEFINE_POLICY = "define-power-policy";
    private static final String COMMAND_DEFINE_POLICY_GROUP = "define-power-policy-group";

    private static final int CURRENT_USER_ID = 42;
    public static final String SYSTEM_POWER_POLICY_ALL_ON = "system_power_policy_all_on";
    public static final String SYSTEM_POWER_POLICY_NO_USER_INTERACTION =
            "system_power_policy_no_user_interaction";
    public static final String SYSTEM_POWER_POLICY_INITIAL_ON = "system_power_policy_initial_on";
    public static final String SYSTEM_POWER_POLICY_SUSPEND_PREP =
            "system_power_policy_suspend_prep";

    private static final String POWER_POLICY_VALID_1 = "policy_id_valid_1";
    private static final String POWER_POLICY_VALID_2 = "policy_id_valid_2";
    private static final String POWER_POLICY_EMPTY = "empty_policy";
    private static final String POWER_POLICY_WAIT_FOR_VHAL = "wait_for_vhal_policy";
    private static final String POWER_POLICY_ON = "on_policy";
    private static final String POWER_POLICY_INVALID_COMPONENT = "policy_id_for_invalid_component";
    private static final String POWER_POLICY_VALID_COMMAND = "policy_id_valid_command";
    private static final String POWER_POLICY_AUDIO_INVERT = "policy_id_audio_invert";
    private static final String POWER_POLICY_OTHER_NONE = "policy_id_other_none";
    private static final String POWER_POLICY_CUSTOM = "policy_id_custom";
    private static final String POWER_POLICY_OTHER_OFF = "policy_id_other_off";
    private static final String POWER_POLICY_CUSTOM_OTHER_OFF = "policy_id_custom_other_off";

    private static final String POWER_POLICY_GROUP_CMD_VHAL_PREFIX = "WaitForVHAL:";
    private static final String POWER_POLICY_GROUP_CMD_ON_PREFIX = "On:";
    private static final String POWER_POLICY_GROUP_VALID = "policy_group_id_valid";
    private static final String POWER_POLICY_GROUP_INVALID = "policy_group_id_invalid";
    private static final String POWER_POLICY_GROUP_1 = "policy_group_1";

    public static final int CUSTOM_COMPONENT_1000 = 1000;
    public static final int CUSTOM_COMPONENT_1001 = 1001;
    public static final int CUSTOM_COMPONENT_1002 = 1002;
    public static final int CUSTOM_COMPONENT_1003 = 1003;

    public static final List<Integer> POLICY_OTHER_NONE_ENABLED_COMPONENTS = List.of(
            PowerComponent.WIFI);
    public static final List<Integer> POLICY_OTHER_NONE_DISABLED_COMPONENTS = List.of(
            PowerComponent.AUDIO, PowerComponent.VOICE_INTERACTION,
            PowerComponent.VISUAL_INTERACTION, PowerComponent.TRUSTED_DEVICE_DETECTION);
    public static final List<Integer> POLICY_OTHER_OFF_ENABLED_COMPONENTS = List.of(
            PowerComponent.AUDIO, PowerComponent.DISPLAY, PowerComponent.VISUAL_INTERACTION);
    public static final List<Integer> POLICY_OTHER_OFF_DISABLED_COMPONENTS = List.of(
            PowerComponent.WIFI, CUSTOM_COMPONENT_1002);
    public static final List<Integer> POLICY_CUSTOM_ENABLED_COMPONENTS = List.of(
            PowerComponent.WIFI, CUSTOM_COMPONENT_1000);
    public static final List<Integer> POLICY_CUSTOM_DISABLED_COMPONENTS = List.of(
            PowerComponent.AUDIO, PowerComponent.DISPLAY, PowerComponent.VISUAL_INTERACTION,
            CUSTOM_COMPONENT_1003);
    public static final List<Integer> POLICY_CUSTOM_OTHER_OFF_ENABLED_COMPONENTS = List.of(
            PowerComponent.WIFI, CUSTOM_COMPONENT_1000);
    public static final List<Integer> POLICY_CUSTOM_OTHER_OFF_DISABLED_COMPONENTS = List.of(
            PowerComponent.AUDIO, PowerComponent.DISPLAY, PowerComponent.VISUAL_INTERACTION,
            CUSTOM_COMPONENT_1002, CUSTOM_COMPONENT_1003);

    private final FakeFeatureFlagsImpl mFeatureFlags = new FakeFeatureFlagsImpl();
    private final MockDisplayInterface mDisplayInterface = new MockDisplayInterface();
    private final MockSystemStateInterface mSystemStateInterface = new MockSystemStateInterface();
    private final MockWakeLockInterface mWakeLockInterface = new MockWakeLockInterface();
    private final MockIOInterface mIOInterface = new MockIOInterface();
    private final PowerSignalListener mPowerSignalListener = new PowerSignalListener();
    @Spy
    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final TemporaryFile mComponentStateFile;
    private final HalPropValueBuilder mHalPropValueBuilder = new HalPropValueBuilder(
            /* isAidl= */ true);

    private MockedPowerHalService mPowerHal;
    private SystemInterface mSystemInterface;
    private PowerComponentHandler mPowerComponentHandler;
    private CarPowerManagementService mService;
    private CompletableFuture<Void> mFuture;
    private TemporaryFile mFileHwStateMonitoring;
    private TemporaryFile mFileKernelSilentMode;
    private FakeCarPowerPolicyDaemon mPowerPolicyDaemon;
    private FakeRefactoredCarPowerPolicyDaemon mRefactoredPowerPolicyDaemon;
    private boolean mVoiceInteractionEnabled;
    private FakeScreenOffHandler mScreenOffHandler;

    @Mock
    private UserManager mUserManager;
    @Mock
    private Resources mResources;
    @Mock
    private CarUserService mUserService;
    @Mock
    private WifiManager mWifiManager;
    @Mock
    private TetheringManager mTetheringManager;

    public CarPowerManagementServiceUnitTest() throws Exception {
        super(CarPowerManagementService.TAG);

        mComponentStateFile = new TemporaryFile("COMPONENT_STATE_FILE");
    }

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session
            .spyStatic(ActivityManager.class)
            .spyStatic(VoiceInteractionHelper.class);
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
            .withWakeLockInterface(mWakeLockInterface)
            .withIOInterface(mIOInterface).build();
        HandlerThread handlerThread = CarServiceUtils.getHandlerThread(TAG);
        mScreenOffHandler = new FakeScreenOffHandler(
                mContext, mSystemInterface, handlerThread.getLooper());

        setCurrentUser(CURRENT_USER_ID, /* isGuest= */ false);
        setService();
    }

    @After
    public void tearDown() throws Exception {
        if (mService != null) {
            mService.release();
        }
        CarLocalServices.removeServiceForTest(CarPowerManagementService.class);
        CarServiceUtils.finishAllHandlerTasks();
        mIOInterface.tearDown();
    }

    @Test
    public void testShutdown() throws Exception {
        mPowerSignalListener.addEventListener(PowerHalService.SET_ON);
        mPowerSignalListener.addEventListener(PowerHalService.SET_SHUTDOWN_START);
        // Transition to ON state
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);

        mPowerHal.setCurrentPowerState(
                new PowerState(
                        VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.SHUTDOWN_ONLY));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_SHUTDOWN_START);
        assertThat(mService.garageModeShouldExitImmediately()).isFalse();
        mDisplayInterface.waitForAllDisplaysOff(WAIT_TIMEOUT_MS);
        mPowerSignalListener.waitFor(PowerHalService.SET_SHUTDOWN_START, WAIT_TIMEOUT_MS);
        // Send the finished signal
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        mSystemStateInterface.waitForShutdown(WAIT_TIMEOUT_MS);
    }

    @Test
    public void testCanHibernate() throws Exception {
        mPowerSignalListener.addEventListener(PowerHalService.SET_ON);
        mPowerSignalListener.addEventListener(PowerHalService.SET_HIBERNATION_ENTRY);
        mPowerSignalListener.addEventListener(PowerHalService.SET_HIBERNATION_EXIT);
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_HIBERNATE));
        assertThat(mService.garageModeShouldExitImmediately()).isFalse();

        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_HIBERNATION_ENTRY);
        mPowerSignalListener.waitFor(PowerHalService.SET_HIBERNATION_ENTRY, WAIT_TIMEOUT_MS);
        verify(mUserService).onSuspend();

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        mSystemStateInterface.setWakeupCausedByTimer(true);
        mSystemStateInterface.waitForSleepEntryAndWakeup(WAIT_TIMEOUT_MS);
        assertStateReceived(PowerHalService.SET_HIBERNATION_EXIT, 0);
        mPowerSignalListener.waitFor(PowerHalService.SET_HIBERNATION_EXIT, WAIT_TIMEOUT_MS);
    }

    @Test
    public void testHibernateImmediately() throws Exception {
        mPowerSignalListener.addEventListener(PowerHalService.SET_ON);
        mPowerSignalListener.addEventListener(PowerHalService.SET_HIBERNATION_ENTRY);
        mPowerSignalListener.addEventListener(PowerHalService.SET_HIBERNATION_EXIT);
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.HIBERNATE_IMMEDIATELY));

        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_HIBERNATION_ENTRY, 0);
        assertThat(mService.garageModeShouldExitImmediately()).isTrue();
        mPowerSignalListener.waitFor(PowerHalService.SET_HIBERNATION_ENTRY, WAIT_TIMEOUT_MS);
        verify(mUserService).onSuspend();

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        mSystemStateInterface.setWakeupCausedByTimer(true);
        mSystemStateInterface.waitForSleepEntryAndWakeup(WAIT_TIMEOUT_MS);
        assertStateReceived(PowerHalService.SET_HIBERNATION_EXIT, 0);
        mPowerSignalListener.waitFor(PowerHalService.SET_HIBERNATION_EXIT, WAIT_TIMEOUT_MS);
    }

    @Test
    public void testShutdownImmediately() throws Exception {
        mPowerSignalListener.addEventListener(PowerHalService.SET_ON);
        mPowerSignalListener.addEventListener(PowerHalService.SET_SHUTDOWN_START);
        // Transition to ON state
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);

        mPowerHal.setCurrentPowerState(
                new PowerState(
                        VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.SHUTDOWN_IMMEDIATELY));
        // Since modules have to manually schedule next wakeup, we should not schedule next wakeup
        // To test module behavior, we need to actually implement mock listener module.
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_SHUTDOWN_START, 0);
        assertThat(mService.garageModeShouldExitImmediately()).isTrue();
        mDisplayInterface.waitForAllDisplaysOff(WAIT_TIMEOUT_MS);
        mPowerSignalListener.waitFor(PowerHalService.SET_SHUTDOWN_START, WAIT_TIMEOUT_MS);
        // Send the finished signal
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        mSystemStateInterface.waitForShutdown(WAIT_TIMEOUT_MS);
    }

    @Test
    public void testEmergencyShutdown() throws Exception {
        grantAdjustShutdownProcessPermission();
        mPowerSignalListener.addEventListener(PowerHalService.SET_ON);
        mPowerSignalListener.addEventListener(PowerHalService.SET_SHUTDOWN_START);
        mPowerSignalListener.addEventListener(PowerHalService.SET_SHUTDOWN_PREPARE);

        final long[] timeout = {-1, -1};
        int preShutdownPrepareTimeoutIndex = 0;
        int shutdownPrepareTimeoutIndex = 1;

        ICarPowerStateListener listener = new ICarPowerStateListener.Stub() {
            @Override
            public void onStateChanged(int state, long expirationTimeMs) {
                if (state == CarPowerManager.STATE_PRE_SHUTDOWN_PREPARE) {
                    timeout[preShutdownPrepareTimeoutIndex] = expirationTimeMs;
                    assertWithMessage("expirationTimeMs should be less that current time").that(
                            expirationTimeMs).isLessThan(System.currentTimeMillis());
                }
                if (state == CarPowerManager.STATE_SHUTDOWN_PREPARE) {
                    timeout[shutdownPrepareTimeoutIndex] = expirationTimeMs;
                    assertWithMessage("expirationTimeMs should be less that current time").that(
                            expirationTimeMs).isLessThan(System.currentTimeMillis());
                }
                if (CarPowerManagementService.isCompletionAllowed(state)) {
                    mService.finished(state, this);
                }
            }
        };
        mService.registerInternalListener(listener);
        // Transition to ON state
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);

        mPowerHal.setCurrentPowerState(
                new PowerState(
                        VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.EMERGENCY_SHUTDOWN));

        assertStateReceivedForShutdownOrSleepWithoutPostpone(PowerHalService.SET_SHUTDOWN_START, 0);

        assertWithMessage("Garage mode cannot be executed during emergency shutdown").that(
                mService.garageModeShouldExitImmediately()).isTrue();
        mDisplayInterface.waitForAllDisplaysOff(WAIT_TIMEOUT_MS);
        mPowerSignalListener.waitFor(PowerHalService.SET_SHUTDOWN_START, WAIT_TIMEOUT_MS);
        assertWithMessage("timeout cannot be 0").that(
                timeout[preShutdownPrepareTimeoutIndex]).isGreaterThan(0);
        assertWithMessage("timeout cannot be 0").that(
                timeout[shutdownPrepareTimeoutIndex]).isGreaterThan(0);
        // Send the finished signal
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        mSystemStateInterface.waitForShutdown(WAIT_TIMEOUT_MS);
    }

    @Test
    public void testEmergencyShutdownCancel() throws Exception {
        grantAdjustShutdownProcessPermission();
        mPowerSignalListener.addEventListener(PowerHalService.SET_ON);
        mPowerSignalListener.addEventListener(PowerHalService.SET_SHUTDOWN_START);
        mPowerSignalListener.addEventListener(PowerHalService.SET_SHUTDOWN_PREPARE);
        mPowerSignalListener.addEventListener(PowerHalService.SET_WAIT_FOR_VHAL);
        mPowerSignalListener.addEventListener(PowerHalService.SET_SHUTDOWN_CANCELLED);


        // Transition to ON state
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);

        mPowerHal.setCurrentPowerState(
                new PowerState(
                        VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.EMERGENCY_SHUTDOWN));

        assertStateReceivedForShutdownOrSleepWithoutPostpone(PowerHalService.SET_SHUTDOWN_START, 0);
        mDisplayInterface.waitForAllDisplaysOff(WAIT_TIMEOUT_MS);
        mPowerSignalListener.waitFor(PowerHalService.SET_SHUTDOWN_START, WAIT_TIMEOUT_MS);

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.CANCEL_SHUTDOWN, 0));
        mPowerSignalListener.waitFor(PowerHalService.SET_SHUTDOWN_CANCELLED, WAIT_TIMEOUT_LONG_MS);
        // try to suspend
        mPowerHal.setCurrentPowerState(
                new PowerState(
                        VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.CAN_SLEEP));

        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_DEEP_SLEEP_ENTRY);

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.CANCEL_SHUTDOWN, 0));
        mPowerSignalListener.waitFor(PowerHalService.SET_SHUTDOWN_CANCELLED, WAIT_TIMEOUT_LONG_MS);
        // shutdown
        mPowerHal.setCurrentPowerState(
                new PowerState(
                        VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.SHUTDOWN_ONLY));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_SHUTDOWN_START);
    }

    private void assertStateReceivedForShutdownOrSleepWithoutPostpone(int lastState,
            int expectedSecondParameter)
            throws Exception {
        while (true) {
            if (mFuture != null && !mFuture.isDone()) {
                mFuture.complete(null);
            }
            int[] state = mPowerHal.waitForSend(WAIT_TIMEOUT_LONG_MS);
            if (state[0] == PowerHalService.SET_SHUTDOWN_POSTPONE) {
                throw new IllegalStateException("Shutdown Postpone is not allowed");
            }
            if (state[0] == lastState) {
                assertThat(state[1]).isEqualTo(expectedSecondParameter);
                return;
            }
        }
    }


    private String stateToString(int state) {
        String result;
        switch (state) {
            case CarPowerManager.STATE_INVALID ->  result = "Invalid";
            case CarPowerManager.STATE_WAIT_FOR_VHAL ->  result = "WaitForVHAL";
            case CarPowerManager.STATE_SUSPEND_ENTER ->  result = "SuspendEnter";
            case CarPowerManager.STATE_SUSPEND_EXIT ->  result = "SuspendExit";
            case CarPowerManager.STATE_SHUTDOWN_ENTER ->  result = "ShutdownEnter";
            case CarPowerManager.STATE_ON ->  result = "ON";
            case CarPowerManager.STATE_SHUTDOWN_PREPARE ->  result = "ShutdownPrepare";
            case CarPowerManager.STATE_SHUTDOWN_CANCELLED ->  result = "ShutdownCancelled";
            case CarPowerManager.STATE_HIBERNATION_ENTER ->  result = "HibernationEnter";
            case CarPowerManager.STATE_HIBERNATION_EXIT ->  result = "HibernationExit";
            case CarPowerManager.STATE_PRE_SHUTDOWN_PREPARE ->  result = "PreShutdownPrepare";
            case CarPowerManager.STATE_POST_SUSPEND_ENTER ->  result = "PostSuspendEnter";
            case CarPowerManager.STATE_POST_SHUTDOWN_ENTER ->  result = "PostShutdownEnter";
            case CarPowerManager.STATE_POST_HIBERNATION_ENTER ->  result = "PostHibernationEnter";
            default -> result = "Unknown";
        }
        return result;
    }


    @Test
    public void testSuspend() throws Exception {
        mPowerSignalListener.addEventListener(PowerHalService.SET_ON);
        // Start in the ON state
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);
        // Request suspend
        mPowerHal.setCurrentPowerState(
                new PowerState(
                        VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.CAN_SLEEP));
        // Verify suspend
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_DEEP_SLEEP_ENTRY);
        assertThat(mService.garageModeShouldExitImmediately()).isFalse();
    }

    @Test
    public void testShutdownOnSuspend() throws Exception {
        mPowerSignalListener.addEventListener(PowerHalService.SET_ON);
        mPowerSignalListener.addEventListener(PowerHalService.SET_SHUTDOWN_START);
        // Start in the ON state
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);
        // Tell it to shutdown
        mService.requestShutdownOnNextSuspend();
        // Request suspend
        mPowerHal.setCurrentPowerState(
                new PowerState(
                        VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.CAN_SLEEP));
        // Verify shutdown
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_SHUTDOWN_START);
        mPowerSignalListener.waitFor(PowerHalService.SET_SHUTDOWN_START, WAIT_TIMEOUT_MS);
        // Send the finished signal
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        mSystemStateInterface.waitForShutdown(WAIT_TIMEOUT_MS);
        // Cancel the shutdown
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.CANCEL_SHUTDOWN, 0));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_SHUTDOWN_CANCELLED);

        // Request suspend again
        mPowerHal.setCurrentPowerState(
                new PowerState(
                        VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.CAN_SLEEP));
        // Verify suspend
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_DEEP_SLEEP_ENTRY);
    }

    @Test
    public void testShutdownCancel() throws Exception {
        mPowerSignalListener.addEventListener(PowerHalService.SET_ON);
        // Start in the ON state
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);
        // Start shutting down
        mPowerHal.setCurrentPowerState(
                new PowerState(
                        VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.SHUTDOWN_IMMEDIATELY));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_SHUTDOWN_START, 0);
        // Cancel the shutdown
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.CANCEL_SHUTDOWN, 0));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_SHUTDOWN_CANCELLED);
        // Go to suspend
        mPowerHal.setCurrentPowerState(
                new PowerState(
                        VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.CAN_SLEEP));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_DEEP_SLEEP_ENTRY);
    }

    @Test
    public void testSleepImmediately() throws Exception {
        mPowerSignalListener.addEventListener(PowerHalService.SET_ON);
        mPowerSignalListener.addEventListener(PowerHalService.SET_DEEP_SLEEP_ENTRY);
        mPowerSignalListener.addEventListener(PowerHalService.SET_DEEP_SLEEP_EXIT);
        // Transition to ON state
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);

        mPowerHal.setCurrentPowerState(
                new PowerState(
                        VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.SLEEP_IMMEDIATELY));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_DEEP_SLEEP_ENTRY, 0);
        assertThat(mService.garageModeShouldExitImmediately()).isTrue();
        mPowerSignalListener.waitFor(PowerHalService.SET_DEEP_SLEEP_ENTRY, WAIT_TIMEOUT_MS);

        // Send the finished signal from HAL to CPMS
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        mSystemStateInterface.waitForSleepEntryAndWakeup(WAIT_TIMEOUT_MS);
        assertStateReceived(PowerHalService.SET_DEEP_SLEEP_EXIT, 0);
        mPowerSignalListener.waitFor(PowerHalService.SET_DEEP_SLEEP_EXIT, WAIT_TIMEOUT_MS);
    }

    @Test
    public void testShutdownWithProcessing() throws Exception {
        mPowerSignalListener.addEventListener(PowerHalService.SET_SHUTDOWN_START);
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE, 0));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_SHUTDOWN_START);
        mPowerSignalListener.waitFor(PowerHalService.SET_SHUTDOWN_START, WAIT_TIMEOUT_MS);
        // Send the finished signal
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        mSystemStateInterface.waitForShutdown(WAIT_TIMEOUT_MS);
    }

    // need to test SHUTDOWN_PREPARE -> CANCEL scenario
    @Test
    public void testUserManagerOnResumeCallAfterCancel() throws Exception {
        grantPowerPolicyPermission();
        mPowerSignalListener.addEventListener(PowerHalService.SET_SHUTDOWN_START);
        mPowerSignalListener.addEventListener(PowerHalService.SET_SHUTDOWN_CANCELLED);
        mPowerSignalListener.addEventListener(PowerHalService.SET_ON);
        assertThat(mService.getCurrentPowerPolicy().getPolicyId())
                .isEqualTo(SYSTEM_POWER_POLICY_INITIAL_ON);
        mPowerHal.setCurrentPowerState(
                new PowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.SHUTDOWN_ONLY));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_SHUTDOWN_START);
        mPowerSignalListener.waitFor(PowerHalService.SET_SHUTDOWN_START, WAIT_TIMEOUT_MS);

        mPowerHal.setCurrentPowerState(
                new PowerState(VehicleApPowerStateReq.CANCEL_SHUTDOWN, /* param= */ 0));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_SHUTDOWN_CANCELLED);

        mPowerSignalListener.waitFor(PowerHalService.SET_SHUTDOWN_CANCELLED, WAIT_TIMEOUT_MS);

        verify(mUserService).onSuspend();

        assertThat(mService.getCurrentPowerPolicy().getPolicyId())
                .isEqualTo(SYSTEM_POWER_POLICY_INITIAL_ON);

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, /* param= */ 0));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_ON);

        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);

        // onResume is being called after notification to VHAL, so there is possibility that
        // test will check mock before it is actually called, to avoid failure, timeout used.
        verify(mUserService, timeout(WAIT_TIMEOUT_LONG_MS)).onResume();

    }

    @Test
    public void testUserManagerOnResumeCallAfterSuspend() throws Exception {
        mPowerSignalListener.addEventListener(PowerHalService.SET_DEEP_SLEEP_ENTRY);
        mPowerSignalListener.addEventListener(PowerHalService.SET_DEEP_SLEEP_EXIT);
        mPowerSignalListener.addEventListener(PowerHalService.SET_ON);

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.SLEEP_IMMEDIATELY));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_DEEP_SLEEP_ENTRY, 0);
        mPowerSignalListener.waitFor(PowerHalService.SET_DEEP_SLEEP_ENTRY, WAIT_TIMEOUT_MS);
        verify(mUserService).onSuspend();

        // Send the finished signal
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        mSystemStateInterface.waitForSleepEntryAndWakeup(WAIT_TIMEOUT_MS);
        assertStateReceived(PowerHalService.SET_DEEP_SLEEP_EXIT, 0);
        mPowerSignalListener.waitFor(PowerHalService.SET_DEEP_SLEEP_EXIT, WAIT_TIMEOUT_MS);

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);

        // onResume is being called after notification to VHAL, so there is possibility that
        // test will check mock before it is actually called, to avoid failure, timeout used.
        verify(mUserService, timeout(WAIT_TIMEOUT_LONG_MS)).onResume();

        // suspend and resume again
        clearInvocations(mUserService);

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.SLEEP_IMMEDIATELY));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_DEEP_SLEEP_ENTRY, 0);
        mPowerSignalListener.waitFor(PowerHalService.SET_DEEP_SLEEP_ENTRY, WAIT_TIMEOUT_MS);

        verify(mUserService).onSuspend();

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        mSystemStateInterface.waitForSleepEntryAndWakeup(WAIT_TIMEOUT_MS);
        assertStateReceived(PowerHalService.SET_DEEP_SLEEP_EXIT, 0);
        mPowerSignalListener.waitFor(PowerHalService.SET_DEEP_SLEEP_EXIT, WAIT_TIMEOUT_MS);

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);
        verify(mUserService, timeout(WAIT_TIMEOUT_LONG_MS)).onResume();
    }

    @Test
    public void testSleepEntryAndWakeup() throws Exception {
        mPowerSignalListener.addEventListener(PowerHalService.SET_DEEP_SLEEP_ENTRY);
        mPowerSignalListener.addEventListener(PowerHalService.SET_DEEP_SLEEP_EXIT);
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_DEEP_SLEEP_ENTRY);
        mPowerSignalListener.waitFor(PowerHalService.SET_DEEP_SLEEP_ENTRY, WAIT_TIMEOUT_MS);
        // Send the finished signal from HAL to CPMS
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        mSystemStateInterface.waitForSleepEntryAndWakeup(WAIT_TIMEOUT_MS);
        assertStateReceived(PowerHalService.SET_DEEP_SLEEP_EXIT, 0);
        mPowerSignalListener.waitFor(PowerHalService.SET_DEEP_SLEEP_EXIT, WAIT_TIMEOUT_MS);
    }

    @Test
    public void testShutdownPostponeAfterSuspend_powerPolicyRefactorFlagDisabled()
            throws Exception {
        grantPowerPolicyPermission();
        mPowerSignalListener.addEventListener(PowerHalService.SET_ON);
        mPowerSignalListener.addEventListener(PowerHalService.SET_DEEP_SLEEP_ENTRY);
        mPowerSignalListener.addEventListener(PowerHalService.SET_DEEP_SLEEP_EXIT);
        // Start in the ON state
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP));
        mDisplayInterface.waitForAllDisplaysOff(WAIT_TIMEOUT_MS);
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_DEEP_SLEEP_ENTRY);
        mPowerSignalListener.waitFor(PowerHalService.SET_DEEP_SLEEP_ENTRY, WAIT_TIMEOUT_MS);

        // Send the finished signal
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        mSystemStateInterface.setWakeupCausedByTimer(true);
        mSystemStateInterface.waitForSleepEntryAndWakeup(WAIT_TIMEOUT_MS);
        assertStateReceived(PowerHalService.SET_DEEP_SLEEP_EXIT, 0);
        mPowerSignalListener.waitFor(PowerHalService.SET_DEEP_SLEEP_EXIT, WAIT_TIMEOUT_MS);
        mService.scheduleNextWakeupTime(WAKE_UP_DELAY);
        // Second processing after wakeup
        assertThat(mDisplayInterface.isAnyDisplayEnabled()).isTrue();
        expectPowerPolicySet(SYSTEM_POWER_POLICY_INITIAL_ON);

        mService.setStateForWakeUp();

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mDisplayInterface.waitForAllDisplaysOn(WAIT_TIMEOUT_MS);
        // Should wait until Handler has finished ON processing
        CarServiceUtils.runOnLooperSync(mService.getHandlerThread().getLooper(), () -> { });

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP));

        // Should suspend within timeout
        mPowerSignalListener.waitFor(PowerHalService.SET_DEEP_SLEEP_ENTRY, WAIT_TIMEOUT_MS);
    }

    @Test
    public void testShutdownPostponeAfterSuspend_powerPolicyRefactorFlagEnabled() throws Exception {
        setRefactoredService();
        grantPowerPolicyPermission();
        mPowerSignalListener.addEventListener(PowerHalService.SET_ON);
        mPowerSignalListener.addEventListener(PowerHalService.SET_DEEP_SLEEP_ENTRY);
        mPowerSignalListener.addEventListener(PowerHalService.SET_DEEP_SLEEP_EXIT);
        // Start in the ON state
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP));
        mDisplayInterface.waitForAllDisplaysOff(WAIT_TIMEOUT_MS);
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_DEEP_SLEEP_ENTRY);
        mPowerSignalListener.waitFor(PowerHalService.SET_DEEP_SLEEP_ENTRY, WAIT_TIMEOUT_MS);

        // Send the finished signal
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        mSystemStateInterface.setWakeupCausedByTimer(true);
        mSystemStateInterface.waitForSleepEntryAndWakeup(WAIT_TIMEOUT_MS);
        assertStateReceived(PowerHalService.SET_DEEP_SLEEP_EXIT, 0);
        mPowerSignalListener.waitFor(PowerHalService.SET_DEEP_SLEEP_EXIT, WAIT_TIMEOUT_MS);
        mService.scheduleNextWakeupTime(WAKE_UP_DELAY);
        // Second processing after wakeup
        assertThat(mDisplayInterface.isAnyDisplayEnabled()).isTrue();
        expectPowerPolicySet(SYSTEM_POWER_POLICY_INITIAL_ON);

        mService.setStateForWakeUp();

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mDisplayInterface.waitForAllDisplaysOn(WAIT_TIMEOUT_MS);
        // Should wait until Handler has finished ON processing
        CarServiceUtils.runOnLooperSync(mService.getHandlerThread().getLooper(), () -> { });

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP));

        // Should suspend within timeout
        mPowerSignalListener.waitFor(PowerHalService.SET_DEEP_SLEEP_ENTRY, WAIT_TIMEOUT_MS);
    }

    /**
     * This test case tests the same scenario as {@link #testUserSwitchingOnResume_differentUser()},
     * but indirectly triggering {@code switchUserOnResumeIfNecessary()} through HAL events.
     */
    @Test
    public void testSleepEntryAndWakeUpForProcessing() throws Exception {
        mService.handleOn();
        // Speed up the polling for power state transitions
        mService.setShutdownTimersForTest(10, 40);

        suspendAndResume();
    }

    @Test
    public void testRegisterListenerWithCompletion() throws Exception {
        grantAdjustShutdownProcessPermission();
        mPowerSignalListener.addEventListener(PowerHalService.SET_ON);
        mPowerSignalListener.addEventListener(PowerHalService.SET_SHUTDOWN_START);
        SparseBooleanArray stateMapToCompletion = new SparseBooleanArray();
        ICarPowerStateListener listenerRegistered = new ICarPowerStateListener.Stub() {
            @Override
            public void onStateChanged(int state, long expirationTimeMs) {
                stateMapToCompletion.put(state, true);
                if (CarPowerManagementService.isCompletionAllowed(state)) {
                    mService.finished(state, this);
                }
            }
        };
        mService.registerListenerWithCompletion(listenerRegistered);

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);
        mPowerHal.setCurrentPowerState(
                new PowerState(
                        VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.SHUTDOWN_ONLY));
        mPowerSignalListener.waitFor(PowerHalService.SET_SHUTDOWN_START, WAIT_TIMEOUT_MS);
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        mSystemStateInterface.waitForShutdown(WAIT_TIMEOUT_MS);

        assertWithMessage("WAIT_FOR_VHAL notification").that(stateMapToCompletion
                .get(CarPowerManager.STATE_WAIT_FOR_VHAL)).isFalse();
        assertWithMessage("ON notification").that(stateMapToCompletion
                .get(CarPowerManager.STATE_ON)).isTrue();
        assertWithMessage("PRE_SHUTDOWN_PREPARE notification").that(stateMapToCompletion
                .get(CarPowerManager.STATE_PRE_SHUTDOWN_PREPARE)).isTrue();
        assertWithMessage("SHUTDOWN_PREPARE notification").that(stateMapToCompletion
                .get(CarPowerManager.STATE_SHUTDOWN_PREPARE)).isTrue();
        assertWithMessage("SHUTDOWN_ENTER notification").that(stateMapToCompletion
                .get(CarPowerManager.STATE_SHUTDOWN_ENTER)).isTrue();
        assertWithMessage("POST_SHUTDOWN_ENTER notification").that(stateMapToCompletion
                .get(CarPowerManager.STATE_POST_SHUTDOWN_ENTER)).isTrue();
    }

    @Test
    public void testUnregisterListenerWithCompletion() throws Exception {
        grantAdjustShutdownProcessPermission();
        mPowerSignalListener.addEventListener(PowerHalService.SET_ON);
        mPowerSignalListener.addEventListener(PowerHalService.SET_SHUTDOWN_START);
        ICarPowerStateListener listenerUnregistered = new ICarPowerStateListener.Stub() {
            @Override
            public void onStateChanged(int state, long expirationTimeMs) {
                fail("No notification should be sent to unregistered listener");
            }
        };
        mService.registerListenerWithCompletion(listenerUnregistered);
        mService.unregisterListener(listenerUnregistered);

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);
        mPowerHal.setCurrentPowerState(
                new PowerState(
                        VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.SHUTDOWN_ONLY));
        mPowerSignalListener.waitFor(PowerHalService.SET_SHUTDOWN_START, WAIT_TIMEOUT_MS);
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        mSystemStateInterface.waitForShutdown(WAIT_TIMEOUT_MS);
    }

    @Test
    public void testShutdownPrepareWithCompletion_timeout() throws Exception {
        grantAdjustShutdownProcessPermission();
        mPowerSignalListener.addEventListener(PowerHalService.SET_ON);
        mPowerSignalListener.addEventListener(PowerHalService.SET_SHUTDOWN_START);
        // Shortens the timeout for listen completion
        when(mResources.getInteger(R.integer.config_preShutdownPrepareTimeout))
                .thenReturn(10);
        mService.setShutdownTimersForTest(1000, 1000);
        ICarPowerStateListener listener = new ICarPowerStateListener.Stub() {
            @Override
            public void onStateChanged(int state, long expirationTimeMs) {
                // Does nothing to make timeout occur
            }
        };
        mService.registerListenerWithCompletion(listener);

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);
        mPowerHal.setCurrentPowerState(
                new PowerState(
                        VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.SHUTDOWN_ONLY));
        // Power state should reach SHUTDOWN_ENTER because waiting for listeners to complete is done
        // after timeout.
        mPowerSignalListener.waitFor(PowerHalService.SET_SHUTDOWN_START, WAIT_TIMEOUT_LONG_MS);
    }

    @Test
    public void testFactoryResetOnResume() throws Exception {
        ICarResultReceiver callback = mock(ICarResultReceiver.class);
        mService.setFactoryResetCallback(callback);

        // TODO: shouldn't need to expose handleOn() but rather emulate the steps as it's done on
        // suspendAndResume(), but that method is making too many expectations that won't happen
        // it's factory reset
        mService.handleOn();

        // Arguments don't matter
        verify(callback).send(anyInt(), any());
    }

    @Test
    public void testDefineValidPowerPolicy_powerPolicyRefactorFlagDisabled() {
        int status = mService.definePowerPolicy(POWER_POLICY_VALID_1,
                new String[]{"AUDIO", "BLUETOOTH"}, new String[]{"WIFI"});

        assertThat(status).isEqualTo(PolicyOperationStatus.OK);
        assertThat(mPowerPolicyDaemon.getLastDefinedPolicyId()).isEqualTo(POWER_POLICY_VALID_1);
    }

    @Test
    public void testDefineValidPowerPolicy_powerPolicyRefactorFlagEnabled() throws Exception {
        setRefactoredService();

        int status = mService.definePowerPolicy(POWER_POLICY_VALID_1,
                new String[]{"AUDIO", "BLUETOOTH"}, new String[]{"WIFI"});

        assertThat(status).isEqualTo(PolicyOperationStatus.OK);

        assertThat(mRefactoredPowerPolicyDaemon.getLastDefinedPolicyId()).isEqualTo(
                POWER_POLICY_VALID_1);
    }

    @Test
    public void testDefineDoubleRegisteredPowerPolicy_powerPolicyRefactorFlagDisabled() {
        int status = mService.definePowerPolicy(POWER_POLICY_VALID_1,
                new String[]{"AUDIO", "BLUETOOTH"}, new String[]{"WIFI"});
        assertThat(status).isEqualTo(PolicyOperationStatus.OK);

        status = mService.definePowerPolicy(POWER_POLICY_VALID_1, new String[]{}, new String[]{});

        assertThat(status).isEqualTo(PolicyOperationStatus.ERROR_DOUBLE_REGISTERED_POWER_POLICY_ID);
    }

    @Test
    public void testDefineDoubleRegisteredPowerPolicy_powerPolicyRefactorFlagEnabled()
            throws Exception {
        setRefactoredService();
        int status = mService.definePowerPolicy(POWER_POLICY_VALID_1,
                new String[]{"AUDIO", "BLUETOOTH"}, new String[]{"WIFI"});
        assertThat(status).isEqualTo(PolicyOperationStatus.OK);

        status = mService.definePowerPolicy(POWER_POLICY_VALID_1, new String[]{}, new String[]{});

        assertThat(status).isEqualTo(PolicyOperationStatus.ERROR_DOUBLE_REGISTERED_POWER_POLICY_ID);
    }

    @Test
    public void testDefinePowerPolicyWithInvalidComponent_powerPolicyRefactorFlagDisabled() {
        int status = mService.definePowerPolicy(POWER_POLICY_INVALID_COMPONENT,
                new String[]{"AUDIO", "INVALID_COMPONENT"}, new String[]{"WIFI"});

        assertThat(status).isEqualTo(PolicyOperationStatus.ERROR_INVALID_POWER_COMPONENT);
    }

    @Test
    public void testDefinePowerPolicyWithInvalidComponent_powerPolicyRefactorFlagEnabled()
            throws Exception {
        setRefactoredService();

        int status = mService.definePowerPolicy(POWER_POLICY_INVALID_COMPONENT,
                new String[]{"AUDIO", "INVALID_COMPONENT"}, new String[]{"WIFI"});

        assertThat(status).isEqualTo(PolicyOperationStatus.ERROR_INVALID_POWER_COMPONENT);
    }

    @Test
    public void testDefinePowerPolicyFromValidCommand_powerPolicyRefactorFlagDisabled() {
        String[] args = {COMMAND_DEFINE_POLICY, POWER_POLICY_VALID_COMMAND,
                "--enable", "AUDIO,BLUETOOTH", "--disable", "ETHERNET,WIFI"};

        boolean status = definePowerPolicyFromCommand(args);

        assertWithMessage("Calling definePowerPolicyFromCommand with args: "
                + Arrays.toString(args) + " must succeed").that(status).isTrue();
        assertWithMessage("Power policy daemon must have "
                + POWER_POLICY_VALID_COMMAND + " as last defined policy id").that(
                        mPowerPolicyDaemon.getLastDefinedPolicyId()).isEqualTo(
                                POWER_POLICY_VALID_COMMAND);
    }

    @Test
    public void testDefinePowerPolicyFromValidCommand_powerPolicyRefactorFlagEnabled()
            throws Exception {
        setRefactoredService();
        String[] args = {COMMAND_DEFINE_POLICY, POWER_POLICY_VALID_COMMAND,
                "--enable", "AUDIO,BLUETOOTH", "--disable", "ETHERNET,WIFI"};

        boolean status = definePowerPolicyFromCommand(args);

        assertWithMessage("Calling definePowerPolicyFromCommand with args: "
                + Arrays.toString(args) + " must succeed").that(status).isTrue();
        assertWithMessage("Refactored power policy daemon must have "
                + POWER_POLICY_VALID_COMMAND + " as last defined policy id").that(
                        mRefactoredPowerPolicyDaemon.getLastDefinedPolicyId()).isEqualTo(
                                POWER_POLICY_VALID_COMMAND);
    }

    @Test
    public void testDefinePowerPolicyFromCommand_tooFewArgs_powerPolicyRefactorFlagDisabled() {
        String[] args = {COMMAND_DEFINE_POLICY};

        assertDefinePowerPolicyFromCommandFailed(args);
    }

    @Test
    public void testDefinePowerPolicyFromCommand_tooFewArgs_powerPolicyRefactorFlagEnabled()
            throws Exception {
        setRefactoredService();

        String[] args = {COMMAND_DEFINE_POLICY};

        assertDefinePowerPolicyFromCommandFailed(args);
    }

    @Test
    public void testDefinePowerPolicyFromCommand_missingEnComp_powerPolicyRefactorFlagDisabled() {
        String[] args = {COMMAND_DEFINE_POLICY, "policy_id_no_enabled", "--enable"};

        assertDefinePowerPolicyFromCommandFailed(args);
    }

    @Test
    public void testDefinePowerPolicyFromCommand_missingEnComp_powerPolicyRefactorFlagEnabled()
            throws Exception {
        setRefactoredService();

        String[] args = {COMMAND_DEFINE_POLICY, "policy_id_no_enabled", "--enable"};

        assertDefinePowerPolicyFromCommandFailed(args);
    }

    @Test
    public void testDefinePowerPolicyFromCommand_missingDisComp_powerPolicyRefactorFlagDisabled() {
        String[] args = {COMMAND_DEFINE_POLICY, "policy_id_no_disabled", "--disable"};

        assertDefinePowerPolicyFromCommandFailed(args);
    }

    @Test
    public void testDefinePowerPolicyFromCommand_missingDisComp_powerPolicyRefactorFlagEnabled()
            throws Exception {
        setRefactoredService();

        String[] args = {COMMAND_DEFINE_POLICY, "policy_id_no_disabled", "--disable"};

        assertDefinePowerPolicyFromCommandFailed(args);
    }

    @Test
    public void testDefinePowerPolicyFromCommand_unknownArg_powerPolicyRefactorFlagDisabled() {
        String[] args = {COMMAND_DEFINE_POLICY, "policy_id_unknown_arg", "--unknown_arg"};

        assertDefinePowerPolicyFromCommandFailed(args);
    }

    @Test
    public void testDefinePowerPolicyFromCommand_unknownArg_powerPolicyRefactorFlagEnabled()
            throws Exception {
        setRefactoredService();

        String[] args = {COMMAND_DEFINE_POLICY, "policy_id_unknown_arg", "--unknown_arg"};

        assertDefinePowerPolicyFromCommandFailed(args);
    }

    @Test
    public void testApplyPowerPolicy_powerPolicyRefactorFlagDisabled() throws Exception {
        grantPowerPolicyPermission();
        MockedPowerPolicyListener listenerToWait = setUpPowerPolicyAudioInvert();

        mService.applyPowerPolicy(POWER_POLICY_AUDIO_INVERT);

        assertPowerPolicyApplied(POWER_POLICY_AUDIO_INVERT, listenerToWait);
    }

    @Test
    @FlakyTest(bugId = 323603043)
    public void testApplyPowerPolicy_powerPolicyRefactorFlagEnabled() throws Exception {
        setRefactoredService();
        grantPowerPolicyPermission();
        MockedPowerPolicyListener listenerToWait = setUpPowerPolicyAudioInvert();

        mService.applyPowerPolicy(POWER_POLICY_AUDIO_INVERT);

        assertPowerPolicyApplied(POWER_POLICY_AUDIO_INVERT, listenerToWait);
        assertPowerPolicyRequestRemoved();
    }

    @Test
    public void testApplyInvalidPowerPolicy_powerPolicyRefactorFlagDisabled() throws Exception {
        grantPowerPolicyPermission();
        // Power policy which doesn't exist.
        String policyId = "policy_id_not_available";

        assertThrows(IllegalArgumentException.class, () -> mService.applyPowerPolicy(policyId));
    }

    @Test
    public void testApplyInvalidPowerPolicy_powerPolicyRefactorFlagEnabled() throws Exception {
        setRefactoredService();
        grantPowerPolicyPermission();
        // Power policy which doesn't exist.
        String policyId = "policy_id_not_available";

        assertThrows(IllegalArgumentException.class, () -> mService.applyPowerPolicy(policyId));
        assertPowerPolicyRequestRemoved();
    }

    @Test
    public void testApplySystemPowerPolicyFromApps_powerPolicyRefactorFlagDisabled() {
        grantPowerPolicyPermission();
        String policyId = "system_power_policy_no_user_interaction";

        assertThrows(IllegalArgumentException.class, () -> mService.applyPowerPolicy(policyId));
    }

    @Test
    public void testApplySystemPowerPolicyFromApps_powerPolicyRefactorFlagEnabled()
            throws Exception {
        setRefactoredService();
        grantPowerPolicyPermission();
        String policyId = "system_power_policy_no_user_interaction";

        assertThrows(IllegalArgumentException.class, () -> mService.applyPowerPolicy(policyId));
        assertPowerPolicyRequestRemoved();
    }

    @Test
    public void testApplyPowerPolicyFromCommand_powerPolicyRefactorFlagDisabled() throws Exception {
        grantPowerPolicyPermission();
        MockedPowerPolicyListener listenerToWait = setUpPowerPolicyAudioInvert();
        String policyId = POWER_POLICY_AUDIO_INVERT;

        boolean isSuccess = applyPowerPolicyFromCommand(policyId);

        assertWithMessage("Apply power policy from command status").that(
                isSuccess).isTrue();
        assertPowerPolicyApplied(policyId, listenerToWait);
    }

    @Test
    public void testApplyPowerPolicyFromCommand_powerPolicyRefactorFlagEnabled()
            throws Exception {
        setRefactoredService();
        grantPowerPolicyPermission();
        MockedPowerPolicyListener listenerToWait = setUpPowerPolicyAudioInvert();
        String policyId = POWER_POLICY_AUDIO_INVERT;

        boolean isSuccess = applyPowerPolicyFromCommand(policyId);

        assertWithMessage("Apply power policy from command status").that(
                isSuccess).isTrue();
        assertPowerPolicyApplied(policyId, listenerToWait);
    }

    @Test
    public void testApplyPowerPolicyInvalidCommand_powerPolicyRefactorFlagDisabled() {
        grantPowerPolicyPermission();

        try (IndentingPrintWriter writer = new IndentingPrintWriter(new StringWriter(), "  ")) {
            expectWithMessage("apply policy from command no id").that(
                    mService.applyPowerPolicyFromCommand(
                            new String[]{"apply-power-policy"}, writer)).isFalse();
        }
        expectWithMessage("apply policy from command null id").that(
                applyPowerPolicyFromCommand(null)).isFalse();
        expectWithMessage("apply policy from command unregistered id").that(
                applyPowerPolicyFromCommand("unregistered_policy_id")).isFalse();
    }

    @Test
    @FlakyTest(bugId = 323603048)
    public void testApplyPowerPolicyInvalidCommand_powerPolicyRefactorFlagEnabled()
            throws Exception {
        setRefactoredService();
        grantPowerPolicyPermission();

        try (IndentingPrintWriter writer = new IndentingPrintWriter(new StringWriter(), "  ")) {
            expectWithMessage("apply policy from command no id").that(
                    mService.applyPowerPolicyFromCommand(
                            new String[]{"apply-power-policy"}, writer)).isFalse();
        }
        expectWithMessage("apply policy from command null id").that(
                applyPowerPolicyFromCommand(null)).isFalse();
        expectWithMessage("apply policy from command unregistered id").that(
                applyPowerPolicyFromCommand("unregistered_policy_id")).isFalse();
    }

    @Test
    public void testDefinePowerPolicyGroupFromCommand_powerPolicyRefactorFlagDisabled() {
        mService.definePowerPolicy(POWER_POLICY_VALID_1, new String[0], new String[0]);
        mService.definePowerPolicy(POWER_POLICY_VALID_2, new String[0], new String[0]);
        String[] args = new String[]{COMMAND_DEFINE_POLICY_GROUP, POWER_POLICY_GROUP_VALID,
                POWER_POLICY_GROUP_CMD_VHAL_PREFIX + POWER_POLICY_VALID_1,
                POWER_POLICY_GROUP_CMD_ON_PREFIX + POWER_POLICY_VALID_2};

        boolean isSuccess = definePowerPolicyGroupFromCommand(args);

        assertWithMessage("Define power policy from command status").that(isSuccess).isTrue();
    }

    @Test
    public void testDefinePowerPolicyGroupFromCommand_powerPolicyRefactorFlagEnabled()
            throws Exception {
        setRefactoredService();
        mService.definePowerPolicy(POWER_POLICY_VALID_1, new String[0], new String[0]);
        mService.definePowerPolicy(POWER_POLICY_VALID_2, new String[0], new String[0]);
        String[] args = new String[]{COMMAND_DEFINE_POLICY_GROUP, POWER_POLICY_GROUP_VALID,
                POWER_POLICY_GROUP_CMD_VHAL_PREFIX + POWER_POLICY_VALID_1,
                POWER_POLICY_GROUP_CMD_ON_PREFIX + POWER_POLICY_VALID_2};

        boolean isSuccess = definePowerPolicyGroupFromCommand(args);

        assertWithMessage("Define power policy from command status").that(isSuccess).isTrue();
    }

    @Test
    public void testDefinePowerPolicyGroupFromCommandBadPolicy_powerPolicyRefactorFlagDisabled() {
        String[] args = new String[]{COMMAND_DEFINE_POLICY_GROUP, POWER_POLICY_GROUP_INVALID,
                POWER_POLICY_GROUP_CMD_ON_PREFIX + "policy_id_not_exist"};

        boolean isSuccess = definePowerPolicyGroupFromCommand(args);

        assertThat(isSuccess).isFalse();
    }

    @Test
    public void testDefinePowerPolicyGroupFromCommandBadPolicy_powerPolicyRefactorFlagEnabled()
            throws Exception {
        setRefactoredService();
        String[] args = new String[]{COMMAND_DEFINE_POLICY_GROUP, POWER_POLICY_GROUP_INVALID,
                POWER_POLICY_GROUP_CMD_ON_PREFIX + "policy_id_not_exist"};

        boolean isSuccess = definePowerPolicyGroupFromCommand(args);

        assertThat(isSuccess).isFalse();
    }

    @Test
    public void testDefinePowerPolicyGroupFromCommandBadState_powerPolicyRefactorFlagDisabled() {
        mService.definePowerPolicy(POWER_POLICY_VALID_1, new String[0], new String[0]);
        String[] args = new String[]{COMMAND_DEFINE_POLICY_GROUP, POWER_POLICY_GROUP_INVALID,
                "InvalidStateName:" + POWER_POLICY_VALID_1};

        boolean isSuccess = definePowerPolicyGroupFromCommand(args);

        assertThat(isSuccess).isFalse();
    }

    @Test
    public void testDefinePowerPolicyGroupFromCommandBadState_powerPolicyRefactorFlagEnabled()
            throws Exception {
        setRefactoredService();
        mService.definePowerPolicy(POWER_POLICY_VALID_1, new String[0], new String[0]);
        String[] args = new String[]{COMMAND_DEFINE_POLICY_GROUP, POWER_POLICY_GROUP_INVALID,
                "InvalidStateName:" + POWER_POLICY_VALID_1};

        boolean isSuccess = definePowerPolicyGroupFromCommand(args);

        assertThat(isSuccess).isFalse();
    }

    @Test
    public void testSetPowerPolicyGroup_powerPolicyRefactorFlagDisabled() throws Exception {
        grantPowerPolicyPermission();
        mService.definePowerPolicy(POWER_POLICY_VALID_1, new String[0], new String[0]);
        mService.definePowerPolicy(POWER_POLICY_VALID_2, new String[0], new String[0]);
        definePowerPolicyGroup(
                POWER_POLICY_GROUP_VALID, POWER_POLICY_VALID_1, POWER_POLICY_VALID_2);

        mService.setPowerPolicyGroup(POWER_POLICY_GROUP_VALID);

        assertPowerPolicyGroupSet(POWER_POLICY_GROUP_VALID);
    }

    @Test
    public void testSetPowerPolicyGroup_powerPolicyRefactorFlagEnabled() throws Exception {
        setRefactoredService();
        grantPowerPolicyPermission();
        mService.definePowerPolicy(POWER_POLICY_VALID_1, new String[0], new String[0]);
        mService.definePowerPolicy(POWER_POLICY_VALID_2, new String[0], new String[0]);
        definePowerPolicyGroup(
                POWER_POLICY_GROUP_VALID, POWER_POLICY_VALID_1, POWER_POLICY_VALID_2);

        mService.setPowerPolicyGroup(POWER_POLICY_GROUP_VALID);

        assertPowerPolicyGroupSet(POWER_POLICY_GROUP_VALID);
    }

    @Test
    public void testSetNotRegisteredPowerPolicyGroup_powerPolicyRefactorFlagDisabled() {
        grantPowerPolicyPermission();
        assertThrows("Set unregistered power policy group throws exception",
                IllegalArgumentException.class,
                () -> mService.setPowerPolicyGroup(POWER_POLICY_GROUP_INVALID));
    }

    @Test
    public void testSetNotRegisteredPowerPolicyGroup_powerPolicyRefactorFlagEnabled()
            throws Exception {
        setRefactoredService();
        grantPowerPolicyPermission();
        assertThrows("Set unregistered power policy group throws exception",
                IllegalArgumentException.class,
                () -> mService.setPowerPolicyGroup(POWER_POLICY_GROUP_INVALID));
    }

    @Test
    public void testNotifyPowerPolicyChange_powerPolicyRefactorFlagDisabled() throws Exception {
        grantPowerPolicyPermission();
        MockedPowerPolicyListener listenerAudio = new MockedPowerPolicyListener();
        MockedPowerPolicyListener listenerForDisabledComponents = new MockedPowerPolicyListener();
        MockedPowerPolicyListener listenerForEnabledComponents = new MockedPowerPolicyListener();

        CarPowerPolicyFilter filterDisabledComponents = getListenerFilterForComponents(
                CUSTOM_COMPONENT_1001, CUSTOM_COMPONENT_1002);
        CarPowerPolicyFilter filterEnabledComponents = getListenerFilterForComponents(
                CUSTOM_COMPONENT_1003);
        CarPowerPolicyFilter filterAudio = getListenerFilterForComponents(PowerComponent.AUDIO);

        mService.addPowerPolicyListener(filterAudio, listenerAudio);

        mService.definePowerPolicy(POWER_POLICY_VALID_1,
                new String[]{String.valueOf(CUSTOM_COMPONENT_1003)},
                new String[]{"AUDIO", String.valueOf(CUSTOM_COMPONENT_1001),
                        String.valueOf(CUSTOM_COMPONENT_1002)});
        mService.addPowerPolicyListener(filterDisabledComponents, listenerForDisabledComponents);
        mService.addPowerPolicyListener(filterEnabledComponents, listenerForEnabledComponents);
        mService.applyPowerPolicy(POWER_POLICY_VALID_1);
        waitForPowerPolicy(POWER_POLICY_VALID_1);
        waitForPolicyIdForListeners(POWER_POLICY_VALID_1, listenerAudio,
                listenerForDisabledComponents, listenerForEnabledComponents);

        expectPowerComponentDisabled(PowerComponent.AUDIO);
        expectPowerComponentDisabled(CUSTOM_COMPONENT_1001);
        expectPowerComponentDisabled(CUSTOM_COMPONENT_1002);
        expectPowerComponentEnabled(CUSTOM_COMPONENT_1003);
    }

    @Test
    public void testNotifyPowerPolicyChange_powerPolicyRefactorFlagEnabled() throws Exception {
        setRefactoredService();
        grantPowerPolicyPermission();
        MockedPowerPolicyListener listenerAudio = new MockedPowerPolicyListener();
        MockedPowerPolicyListener listenerForDisabledComponents = new MockedPowerPolicyListener();
        MockedPowerPolicyListener listenerForEnabledComponents = new MockedPowerPolicyListener();

        CarPowerPolicyFilter filterDisabledComponents = getListenerFilterForComponents(
                CUSTOM_COMPONENT_1001, CUSTOM_COMPONENT_1002);
        CarPowerPolicyFilter filterEnabledComponents = getListenerFilterForComponents(
                CUSTOM_COMPONENT_1003);
        CarPowerPolicyFilter filterAudio = getListenerFilterForComponents(PowerComponent.AUDIO);

        mService.addPowerPolicyListener(filterAudio, listenerAudio);

        mService.definePowerPolicy(POWER_POLICY_VALID_1,
                new String[]{String.valueOf(CUSTOM_COMPONENT_1003)},
                new String[]{"AUDIO", String.valueOf(CUSTOM_COMPONENT_1001),
                        String.valueOf(CUSTOM_COMPONENT_1002)});
        mService.addPowerPolicyListener(filterDisabledComponents, listenerForDisabledComponents);
        mService.addPowerPolicyListener(filterEnabledComponents, listenerForEnabledComponents);
        mService.applyPowerPolicy(POWER_POLICY_VALID_1);
        waitForPowerPolicy(POWER_POLICY_VALID_1);
        waitForPolicyIdForListeners(POWER_POLICY_VALID_1, listenerAudio,
                listenerForDisabledComponents, listenerForEnabledComponents);

        expectPowerComponentDisabled(PowerComponent.AUDIO);
        expectPowerComponentDisabled(CUSTOM_COMPONENT_1001);
        expectPowerComponentDisabled(CUSTOM_COMPONENT_1002);
        expectPowerComponentEnabled(CUSTOM_COMPONENT_1003);
    }

    @Test
    public void testAddPowerPolicyListener_powerPolicyRefactorFlagDisabled() throws Exception {
        grantPowerPolicyPermission();
        MockedPowerPolicyListener listenerAudio = new MockedPowerPolicyListener();
        MockedPowerPolicyListener listenerWifi = new MockedPowerPolicyListener();
        MockedPowerPolicyListener listenerLocation = new MockedPowerPolicyListener();

        CarPowerPolicyFilter filterAudio = getListenerFilterForComponents(PowerComponent.AUDIO);
        CarPowerPolicyFilter filterWifi = getListenerFilterForComponents(PowerComponent.WIFI);

        mService.addPowerPolicyListener(filterAudio, listenerAudio);
        mService.addPowerPolicyListener(filterWifi, listenerWifi);

        mService.definePowerPolicy(POWER_POLICY_VALID_1, new String[0], new String[]{
                "AUDIO", "WIFI", "DISPLAY"});
        mService.applyPowerPolicy(POWER_POLICY_VALID_1);
        waitForPowerPolicy(POWER_POLICY_VALID_1);

        expectPowerComponentDisabled(PowerComponent.AUDIO);
        expectPowerComponentDisabled(PowerComponent.WIFI);
        expectPowerComponentDisabled(PowerComponent.DISPLAY);

        mService.definePowerPolicy(POWER_POLICY_VALID_2, new String[]{"AUDIO", "WIFI"},
                new String[]{});
        mService.applyPowerPolicy(POWER_POLICY_VALID_2);

        waitForPolicyIdForListeners(POWER_POLICY_VALID_2, listenerAudio, listenerWifi);
        expectPowerComponentEnabled(PowerComponent.AUDIO);

        expectListenerPowerPolicyNull(listenerLocation);
    }

    @Test
    public void testAddPowerPolicyListener_powerPolicyRefactorFlagEnabled() throws Exception {
        setRefactoredService();
        grantPowerPolicyPermission();
        MockedPowerPolicyListener listenerAudio = new MockedPowerPolicyListener();
        MockedPowerPolicyListener listenerWifi = new MockedPowerPolicyListener();
        MockedPowerPolicyListener listenerLocation = new MockedPowerPolicyListener();

        CarPowerPolicyFilter filterAudio = getListenerFilterForComponents(PowerComponent.AUDIO);
        CarPowerPolicyFilter filterWifi = getListenerFilterForComponents(PowerComponent.WIFI);

        mService.addPowerPolicyListener(filterAudio, listenerAudio);
        mService.addPowerPolicyListener(filterWifi, listenerWifi);

        mService.definePowerPolicy(POWER_POLICY_VALID_1, new String[0], new String[]{
                "AUDIO", "WIFI", "DISPLAY"});
        mService.applyPowerPolicy(POWER_POLICY_VALID_1);
        waitForPowerPolicy(POWER_POLICY_VALID_1);

        expectPowerComponentDisabled(PowerComponent.AUDIO);
        expectPowerComponentDisabled(PowerComponent.WIFI);
        expectPowerComponentDisabled(PowerComponent.DISPLAY);

        mService.definePowerPolicy(POWER_POLICY_VALID_2, new String[]{"AUDIO", "WIFI"},
                new String[]{});
        mService.applyPowerPolicy(POWER_POLICY_VALID_2);

        waitForPolicyIdForListeners(POWER_POLICY_VALID_2, listenerAudio, listenerWifi);
        expectPowerComponentEnabled(PowerComponent.AUDIO);

        expectListenerPowerPolicyNull(listenerLocation);
    }

    @Test
    public void testRemovePowerPolicyListener_powerPolicyRefactorFlagDisabled() throws Exception {
        grantPowerPolicyPermission();
        MockedPowerPolicyListener listenerAudio = setUpPowerPolicyAudioInvert();
        MockedPowerPolicyListener referenceListenerAudio = new MockedPowerPolicyListener();
        CarPowerPolicyFilter filterAudio = getListenerFilterForComponents(PowerComponent.AUDIO);

        mService.addPowerPolicyListener(filterAudio, referenceListenerAudio);
        mService.removePowerPolicyListener(listenerAudio);
        mService.applyPowerPolicy(POWER_POLICY_AUDIO_INVERT);

        waitForPolicyId(referenceListenerAudio, POWER_POLICY_AUDIO_INVERT, "Current power policy "
                + "of referenceListenerAudio is not " + POWER_POLICY_AUDIO_INVERT);
        expectListenerPowerPolicyNull(listenerAudio);
    }

    @Test
    public void testRemovePowerPolicyListener_powerPolicyRefactorFlagEnabled() throws Exception {
        setRefactoredService();
        grantPowerPolicyPermission();
        MockedPowerPolicyListener listenerAudio = setUpPowerPolicyAudioInvert();
        MockedPowerPolicyListener referenceListenerAudio = new MockedPowerPolicyListener();
        CarPowerPolicyFilter filterAudio = getListenerFilterForComponents(PowerComponent.AUDIO);

        mService.addPowerPolicyListener(filterAudio, referenceListenerAudio);
        mService.removePowerPolicyListener(listenerAudio);
        mService.applyPowerPolicy(POWER_POLICY_AUDIO_INVERT);

        waitForPolicyId(referenceListenerAudio, POWER_POLICY_AUDIO_INVERT, "Current power policy "
                + "of referenceListenerAudio is not " + POWER_POLICY_AUDIO_INVERT);
        expectListenerPowerPolicyNull(listenerAudio);
    }

    @Test
    public void testNotifyUserActivity_withoutTimestamp() throws Exception {
        int displayId = 42;

        mService.notifyUserActivity(displayId);

        assertWithMessage(
                "Display " + displayId + " user activity update time without timestamp").that(
                mScreenOffHandler.getDisplayUserActivityTime(displayId)).isAtMost(
                        SystemClock.uptimeMillis());
    }

    @Test
    public void testNotifyUserActivity_withTimestamp() throws Exception {
        int displayId = 24;
        long timestamp = 50;

        mService.notifyUserActivity(displayId, timestamp);

        assertWithMessage(
                "Display " + displayId + " user activity update time with timestamp").that(
                        mScreenOffHandler.getDisplayUserActivityTime(displayId)).isEqualTo(
                                timestamp);
    }

    /**
     * This test case increases the code coverage to cover methods
     * {@code describeContents()} and {@code newArray()}. They are public APIs
     * can not be marked out as BOILERPLATE_CODE.
     */
    @Test
    public void testParcelableCreation() throws Exception {
        grantPowerPolicyPermission();

        CarPowerPolicy policy = mService.getCurrentPowerPolicy();
        assertThat(policy.describeContents()).isEqualTo(0);

        CarPowerPolicy[] policies = CarPowerPolicy.CREATOR.newArray(1);
        assertThat(policies.length).isEqualTo(1);

        CarPowerPolicyFilter filterAudio = getListenerFilterForComponents(PowerComponent.AUDIO);
        assertThat(filterAudio.describeContents()).isEqualTo(0);

        CarPowerPolicyFilter[] filters = CarPowerPolicyFilter.CREATOR.newArray(1);
        assertThat(filters.length).isEqualTo(1);
    }

    @Test
    public void testPowerPolicyAfterShutdownCancel_powerPolicyRefactorFlagDisabled()
            throws Exception {
        grantPowerPolicyPermission();
        mPowerSignalListener.addEventListener(PowerHalService.SET_ON);
        mPowerSignalListener.addEventListener(PowerHalService.SET_SHUTDOWN_START);
        expectPowerPolicySet(SYSTEM_POWER_POLICY_INITIAL_ON);
        mPowerHal.setCurrentPowerState(
                new PowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.SHUTDOWN_ONLY));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_SHUTDOWN_START);
        mPowerSignalListener.waitFor(PowerHalService.SET_SHUTDOWN_START, WAIT_TIMEOUT_MS);

        mPowerHal.setCurrentPowerState(
                new PowerState(VehicleApPowerStateReq.CANCEL_SHUTDOWN, /* param= */ 0));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_SHUTDOWN_CANCELLED);

        waitForPowerPolicy(SYSTEM_POWER_POLICY_INITIAL_ON);

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, /* param= */ 0));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_ON);

        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);

        waitForPowerPolicy(SYSTEM_POWER_POLICY_ALL_ON);
    }

    @Test
    public void testPowerPolicyAfterShutdownCancel_powerPolicyRefactorFlagEnabled()
            throws Exception {
        setRefactoredService();
        grantPowerPolicyPermission();
        mPowerSignalListener.addEventListener(PowerHalService.SET_ON);
        mPowerSignalListener.addEventListener(PowerHalService.SET_SHUTDOWN_START);
        expectPowerPolicySet(SYSTEM_POWER_POLICY_INITIAL_ON);
        mPowerHal.setCurrentPowerState(
                new PowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.SHUTDOWN_ONLY));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_SHUTDOWN_START);
        mPowerSignalListener.waitFor(PowerHalService.SET_SHUTDOWN_START, WAIT_TIMEOUT_MS);

        mPowerHal.setCurrentPowerState(
                new PowerState(VehicleApPowerStateReq.CANCEL_SHUTDOWN, /* param= */ 0));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_SHUTDOWN_CANCELLED);

        waitForPowerPolicy(SYSTEM_POWER_POLICY_INITIAL_ON);

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, /* param= */ 0));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_ON);

        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);

        waitForPowerPolicy(SYSTEM_POWER_POLICY_ALL_ON);
    }

    @Test
    public void testApplyPowerPolicyAtStateChanged_powerPolicyRefactorFlagDisabled()
            throws Exception {
        grantPowerPolicyPermission();
        mPowerSignalListener.addEventListener(PowerHalService.SET_ON);
        openRawResource(R.raw.valid_power_policy_with_policy_groups);

        CarPowerPolicyFilter filterAudio = getListenerFilterForComponents(PowerComponent.AUDIO);
        MockedPowerPolicyListener listenerToWait = new MockedPowerPolicyListener();
        mService.addPowerPolicyListener(filterAudio, listenerToWait);

        mService.setPowerPolicyGroup(POWER_POLICY_GROUP_1);
        mService.applyPowerPolicy(POWER_POLICY_WAIT_FOR_VHAL);
        waitForPowerPolicy(POWER_POLICY_WAIT_FOR_VHAL);
        AtomicBoolean isPolicyApplicationSuccessful = new AtomicBoolean(false);
        mService.registerListener(
                new MockedPowerStateListener(POWER_POLICY_ON, isPolicyApplicationSuccessful));

        // Transition to ON state
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));

        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);
        waitForPowerPolicy(POWER_POLICY_ON);

        expectPowerPolicySet(POWER_POLICY_ON);
        expectPowerComponentHandlerPolicySet(POWER_POLICY_ON);
        expectListenerPowerPolicyNonNull(listenerToWait);

        waitForPowerPolicyNotificationToDaemon(POWER_POLICY_ON);
        PollingCheck.waitFor(WAIT_TIMEOUT_LONG_MS, isPolicyApplicationSuccessful::get);
    }

    @Test
    public void testApplyPowerPolicyAtStateChanged_powerPolicyRefactorFlagEnabled()
            throws Exception {
        setRefactoredService();
        grantPowerPolicyPermission();
        mPowerSignalListener.addEventListener(PowerHalService.SET_ON);
        definePowerPolicyOtherComponentsOff(
                POWER_POLICY_WAIT_FOR_VHAL, List.of(), List.of());
        definePowerPolicyOtherComponentsOn(POWER_POLICY_ON, List.of(), List.of());
        mService.definePowerPolicy(POWER_POLICY_EMPTY, new String[0], new String[0]);
        definePowerPolicyGroup(
                POWER_POLICY_GROUP_1, POWER_POLICY_WAIT_FOR_VHAL, POWER_POLICY_EMPTY);

        CarPowerPolicyFilter filterAudio = getListenerFilterForComponents(PowerComponent.AUDIO);
        MockedPowerPolicyListener listenerToWait = new MockedPowerPolicyListener();
        mService.addPowerPolicyListener(filterAudio, listenerToWait);

        mService.setPowerPolicyGroup(POWER_POLICY_GROUP_1);
        mService.applyPowerPolicy(POWER_POLICY_WAIT_FOR_VHAL);
        waitForPowerPolicy(POWER_POLICY_WAIT_FOR_VHAL);
        AtomicBoolean isPolicyApplicationSuccessful = new AtomicBoolean(false);
        mService.registerListener(
                new MockedPowerStateListener(POWER_POLICY_ON, isPolicyApplicationSuccessful));

        // Transition to ON state
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));

        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);
        waitForPowerPolicy(POWER_POLICY_ON);

        expectPowerPolicySet(POWER_POLICY_ON);
        expectPowerComponentHandlerPolicySet(POWER_POLICY_ON);
        expectListenerPowerPolicyNonNull(listenerToWait);

        PollingCheck.waitFor(WAIT_TIMEOUT_LONG_MS, isPolicyApplicationSuccessful::get);
    }

    @Test
    public void testSuspendFailure() throws Exception {
        suspendWithFailure(/* nextPowerState= */ null);
        mSystemStateInterface.waitForShutdown(WAIT_TIMEOUT_MS);
    }

    @Test
    public void testSuspendFailureWithForbiddenTransition() throws Exception {
        suspendWithFailure(/* nextPowerState= */ VehicleApPowerStateReq.ON);
        mSystemStateInterface.waitForShutdown(WAIT_TIMEOUT_MS);
    }

    @Test
    public void testSuspendFailureWithAllowedTransition() throws Exception {
        mPowerSignalListener.addEventListener(PowerHalService.SET_SHUTDOWN_CANCELLED);
        suspendWithFailure(/* nextPowerState= */ VehicleApPowerStateReq.CANCEL_SHUTDOWN);
        mPowerSignalListener.waitFor(PowerHalService.SET_SHUTDOWN_CANCELLED, WAIT_TIMEOUT_MS);
    }

    @Test
    public void testPowerPolicyOnSilentBoot_powerPolicyRefactorFlagDisabled() throws Exception {
        grantPowerPolicyPermission();
        mPowerSignalListener.addEventListener(PowerHalService.SET_ON);
        mService.setSilentMode(SilentModeHandler.SILENT_MODE_FORCED_SILENT);

        waitForPowerPolicy(SYSTEM_POWER_POLICY_NO_USER_INTERACTION);

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, /* param= */ 0));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_ON);

        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);
        waitForPowerPolicy(SYSTEM_POWER_POLICY_NO_USER_INTERACTION);

        mService.setSilentMode(SilentModeHandler.SILENT_MODE_FORCED_NON_SILENT);
        waitForPowerPolicy(SYSTEM_POWER_POLICY_ALL_ON);
    }

    @Test
    public void testPowerPolicyOnSilentBoot_powerPolicyRefactorFlagEnabled() throws Exception {
        setRefactoredService();
        mRefactoredPowerPolicyDaemon.silentModeFileObserverStartWatching();
        grantPowerPolicyPermission();

        mPowerSignalListener.addEventListener(PowerHalService.SET_ON);
        mService.setSilentMode(SilentModeHandler.SILENT_MODE_FORCED_SILENT);

        waitForPowerPolicy(SYSTEM_POWER_POLICY_NO_USER_INTERACTION);

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, /* param= */ 0));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_ON);

        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);
        waitForPowerPolicy(SYSTEM_POWER_POLICY_NO_USER_INTERACTION);

        mService.setSilentMode(SilentModeHandler.SILENT_MODE_FORCED_NON_SILENT);
        waitForPowerPolicy(SYSTEM_POWER_POLICY_ALL_ON);

        mRefactoredPowerPolicyDaemon.silentModeFileObserverStopWatching();
    }

    @Test
    public void testDisableWifiAndTethering() throws Exception {
        grantPowerPolicyPermission();
        when(mResources.getBoolean(R.bool.config_wifiAdjustmentForSuspend))
                .thenReturn(true);
        when(mWifiManager.isWifiEnabled()).thenReturn(true);
        when(mWifiManager.isWifiApEnabled()).thenReturn(true);
        mService = new CarPowerManagementService(mContext, mResources, mPowerHal,
                    mSystemInterface, mUserManager, mUserService, mPowerPolicyDaemon,
                    mPowerComponentHandler, mFeatureFlags, mScreenOffHandler,
                    mFileHwStateMonitoring.getFile().getPath(),
                    mFileKernelSilentMode.getFile().getPath(), NORMAL_BOOT);
        CarLocalServices.removeServiceForTest(CarPowerManagementService.class);
        CarLocalServices.addService(CarPowerManagementService.class, mService);
        mService.init();
        mService.setShutdownTimersForTest(/* pollingIntervalMs= */ 0, /* shutdownTimeoutMs= */ 0);
        mService.scheduleNextWakeupTime(WAKE_UP_DELAY);

        suspendDevice();

        verify(mWifiManager, atLeastOnce()).setWifiEnabled(false);
        verify(mTetheringManager).stopTethering(TETHERING_WIFI);
    }

    @Test
    public void testRequestShutDownAp_On() throws Exception {
        mService.requestShutdownAp(CarRemoteAccessManager.NEXT_POWER_STATE_ON,
                /* runGarageMode= */ true);

        assertWithMessage("Requested shutdown power state")
                .that(mPowerHal.getRequestedShutdownPowerState())
                .isEqualTo(PowerHalService.PowerState.SHUTDOWN_TYPE_UNDEFINED);
    }

    @Test
    public void testRequestShutDownAp_Off() throws Exception {
        mService.requestShutdownAp(CarRemoteAccessManager.NEXT_POWER_STATE_OFF,
                /* runGarageMode= */ true);

        assertWithMessage("Requested shutdown power state")
                .that(mPowerHal.getRequestedShutdownPowerState())
                .isEqualTo(PowerHalService.PowerState.SHUTDOWN_TYPE_POWER_OFF);
    }

    @Test
    public void testRequestShutDownAp_SuspendToRam() throws Exception {
        mPowerHal.setDeepSleepEnabled(true);

        mService.requestShutdownAp(CarRemoteAccessManager.NEXT_POWER_STATE_SUSPEND_TO_RAM,
                /* runGarageMode= */ true);

        assertWithMessage("Requested shutdown power state")
                .that(mPowerHal.getRequestedShutdownPowerState())
                .isEqualTo(PowerHalService.PowerState.SHUTDOWN_TYPE_DEEP_SLEEP);
    }

    @Test
    public void testRequestShutDownAp_SuspendToRam_notAllowed() throws Exception {
        mPowerHal.setDeepSleepEnabled(false);

        assertThrows(UnsupportedOperationException.class, () -> mService.requestShutdownAp(
                CarRemoteAccessManager.NEXT_POWER_STATE_SUSPEND_TO_RAM, /* runGarageMode= */ true));
    }

    @Test
    public void testRequestShutDownAp_SuspendToDisk() throws Exception {
        mPowerHal.setHibernationEnabled(true);

        mService.requestShutdownAp(CarRemoteAccessManager.NEXT_POWER_STATE_SUSPEND_TO_DISK,
                /* runGarageMode= */ true);

        assertWithMessage("Requested shutdown power state")
                .that(mPowerHal.getRequestedShutdownPowerState())
                .isEqualTo(PowerHalService.PowerState.SHUTDOWN_TYPE_HIBERNATION);
    }

    @Test
    public void testRequestShutDownAp_SuspendToDisk_notAllowed() throws Exception {
        mPowerHal.setHibernationEnabled(false);

        assertThrows(UnsupportedOperationException.class, () -> mService.requestShutdownAp(
                CarRemoteAccessManager.NEXT_POWER_STATE_SUSPEND_TO_DISK,
                /* runGarageMode= */ true));
    }

    @Test
    public void testRequestShutDownAp_InvalidPowerState() throws Exception {
        mService.requestShutdownAp(/* nextPowerState= */ 999999, /* runGarageMode= */ true);

        assertWithMessage("Requested shutdown power state")
                .that(mPowerHal.getRequestedShutdownPowerState())
                .isEqualTo(PowerHalService.PowerState.SHUTDOWN_TYPE_UNDEFINED);
    }

    @Test
    public void testIsSuspendAvailable_hibernateAvailable() {
        mPowerHal.setHibernationEnabled(true);

        assertWithMessage("Suspend availability").that(
                mService.isSuspendAvailable(/* isHibernation= */ true)).isTrue();
    }

    @Test
    public void testIsSuspendAvailable_hibernateNotAvailable() {
        mPowerHal.setHibernationEnabled(false);

        assertWithMessage("Suspend availability").that(
                mService.isSuspendAvailable(/* isHibernation= */ true)).isFalse();
    }

    @Test
    public void testIsSuspendAvailable_deepSleepAvailable() {
        mPowerHal.setDeepSleepEnabled(true);

        assertWithMessage("Suspend availability").that(
                mService.isSuspendAvailable(/* isHibernation= */ false)).isTrue();
    }

    @Test
    public void testIsSuspendAvailable_deepSleepNotAvailable() {
        mPowerHal.setDeepSleepEnabled(false);

        assertWithMessage("Suspend availability").that(
                mService.isSuspendAvailable(/* isHibernation= */ false)).isFalse();
    }

    @Test
    public void testSendDisplayBrightness_noDisplayId() throws Exception {
        int brightness = 25;
        int displayId = Display.DEFAULT_DISPLAY;

        mService.sendDisplayBrightness(brightness);

        mPowerHal.waitForBrightnessSent(displayId, brightness, WAIT_TIMEOUT_MS);
        assertWithMessage("Display " + displayId + " brightness sent with no display ID").that(
                mPowerHal.getDisplayBrightness(displayId)).isEqualTo(brightness);
    }

    @Test
    public void testSendDisplayBrightness_withDisplayId() throws Exception {
        int brightness = 50;
        int displayId = 222;

        mService.sendDisplayBrightness(displayId, brightness);

        mPowerHal.waitForBrightnessSent(displayId, brightness, WAIT_TIMEOUT_MS);
        assertWithMessage("Display " + displayId + " brightness sent with the display ID").that(
                mPowerHal.getDisplayBrightness(displayId)).isEqualTo(brightness);
    }

    @Test
    public void testGarageModeSystemPolicyOverride() throws Exception {
        grantPowerPolicyPermission();

        openRawResource(R.raw.valid_power_policy);

        CarPowerPolicyFilter filterBluetooth =
                getListenerFilterForComponents(PowerComponent.BLUETOOTH);
        CarPowerPolicyFilter filterNfc = getListenerFilterForComponents(PowerComponent.NFC);
        MockedPowerPolicyListener listenerBluetooth = new MockedPowerPolicyListener();
        MockedPowerPolicyListener listenerNfc = new MockedPowerPolicyListener();
        mService.addPowerPolicyListener(filterBluetooth, listenerBluetooth);
        mService.addPowerPolicyListener(filterNfc, listenerNfc);

        mPowerHal.setCurrentPowerState(
                new PowerState(
                        VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.SHUTDOWN_ONLY));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_SHUTDOWN_START);
        assertWithMessage("Garage mode immediate exit status").that(
                mService.garageModeShouldExitImmediately()).isFalse();

        waitForPowerPolicy(SYSTEM_POWER_POLICY_NO_USER_INTERACTION);

        assertWithMessage("Car power policy daemon policy ID").that(
                mPowerPolicyDaemon.getLastNotifiedPolicyId()).isEqualTo(
                        SYSTEM_POWER_POLICY_NO_USER_INTERACTION);
        PollingCheck.check("Current power policy of bluetooth listener is wrong",
                WAIT_TIMEOUT_LONG_MS, () -> listenerBluetooth.getCurrentPowerPolicy() != null
                        && SYSTEM_POWER_POLICY_NO_USER_INTERACTION.equals(
                        listenerBluetooth.getCurrentPowerPolicy().getPolicyId()));
        expectWithMessage("Bluetooth component is missing from current policy enabled components")
                .that(listenerBluetooth.getCurrentPowerPolicy().getEnabledComponents()).asList()
                .contains(PowerComponent.BLUETOOTH);
        PollingCheck.check("Current power policy of NFC listener is wrong",
                WAIT_TIMEOUT_LONG_MS, () -> listenerNfc.getCurrentPowerPolicy() != null
                        && SYSTEM_POWER_POLICY_NO_USER_INTERACTION.equals(
                        listenerNfc.getCurrentPowerPolicy().getPolicyId()));
        expectWithMessage("NFC component is missing from current policy enabled components").that(
                listenerBluetooth.getCurrentPowerPolicy().getEnabledComponents()).asList().contains(
                PowerComponent.NFC);
    }


    @Test
    public void testPowerPolicyNotificationCustomComponents_powerPolicyRefactorFlagDisabled()
            throws Exception {
        grantPowerPolicyPermission();
        openRawResource(R.raw.valid_power_policy_custom_components);
        CarPowerPolicyFilter filter = getListenerFilterForComponents(CUSTOM_COMPONENT_1000);
        MockedPowerPolicyListener listener = new MockedPowerPolicyListener();
        mService.addPowerPolicyListener(filter, listener);
        mService.applyPowerPolicy(POWER_POLICY_CUSTOM_OTHER_OFF);

        waitForPowerPolicy(POWER_POLICY_CUSTOM_OTHER_OFF);
        expectPowerComponentHandlerPolicySet(POWER_POLICY_CUSTOM_OTHER_OFF);
        waitForListenerPolicyNotNull(listener);
        assertThat(mPowerPolicyDaemon.getLastNotifiedPolicyId()).isEqualTo(
                POWER_POLICY_CUSTOM_OTHER_OFF);
    }

    @Test
    public void testPowerPolicyNotificationCustomComponents_powerPolicyRefactorFlagEnabled()
            throws Exception {
        setRefactoredService();
        grantPowerPolicyPermission();
        definePowerPolicyOtherComponentsOff(POWER_POLICY_CUSTOM_OTHER_OFF,
                POLICY_CUSTOM_OTHER_OFF_ENABLED_COMPONENTS,
                POLICY_CUSTOM_OTHER_OFF_DISABLED_COMPONENTS);

        CarPowerPolicyFilter filter = getListenerFilterForComponents(CUSTOM_COMPONENT_1000);
        MockedPowerPolicyListener listener = new MockedPowerPolicyListener();
        mService.addPowerPolicyListener(filter, listener);
        mService.applyPowerPolicy(POWER_POLICY_CUSTOM_OTHER_OFF);

        waitForPowerPolicy(POWER_POLICY_CUSTOM_OTHER_OFF);
        expectPowerComponentHandlerPolicySet(POWER_POLICY_CUSTOM_OTHER_OFF);
        waitForListenerPolicyNotNull(listener);
    }

    @Test
    public void testPowerPolicyNotificationAccumulatedPolicy_powerPolicyRefactorFlagDisabled()
            throws Exception {
        grantPowerPolicyPermission();
        openRawResource(R.raw.valid_power_policy_custom_components);
        CarPowerPolicyFilter filter1000 = getListenerFilterForComponents(CUSTOM_COMPONENT_1000);
        CarPowerPolicyFilter filterAudio = getListenerFilterForComponents(PowerComponent.AUDIO);
        MockedPowerPolicyListener listener = new MockedPowerPolicyListener();
        MockedPowerPolicyListener referenceListenerAudio = new MockedPowerPolicyListener();
        mService.addPowerPolicyListener(filterAudio, referenceListenerAudio);
        mService.addPowerPolicyListener(filter1000, listener);

        // Start test
        mService.applyPowerPolicy(POWER_POLICY_OTHER_NONE);
        waitForPowerPolicy(POWER_POLICY_OTHER_NONE);

        expectPowerComponentHandlerPolicySet(POWER_POLICY_OTHER_NONE);
        waitForListenerPolicyNotNull(referenceListenerAudio);
        expectPowerComponentDisabledForListener(PowerComponent.AUDIO, referenceListenerAudio);
        // Custom component state hasn't changed, as result no current policy in listener
        expectListenerPowerPolicyNull(listener);

        mService.applyPowerPolicy(POWER_POLICY_CUSTOM_OTHER_OFF);
        waitForPowerPolicy(POWER_POLICY_CUSTOM_OTHER_OFF);

        // Audio state is not expected to change, so listener shouldn't have been updated
        waitForPolicyIdForListeners(POWER_POLICY_OTHER_NONE, referenceListenerAudio);
        waitForPolicyIdForListeners(POWER_POLICY_CUSTOM_OTHER_OFF, listener);
        expectPowerComponentEnabledForListener(CUSTOM_COMPONENT_1000, listener);
        assertThat(mPowerPolicyDaemon.getLastNotifiedPolicyId()).isEqualTo(
                POWER_POLICY_CUSTOM_OTHER_OFF);

        // Change again and ensure no notification
        mService.applyPowerPolicy(POWER_POLICY_CUSTOM);
        waitForPowerPolicy(POWER_POLICY_CUSTOM);

        // This policy doesn't change state of component_1000, so its listener shouldn't be updated
        waitForPolicyIdForListeners(POWER_POLICY_CUSTOM_OTHER_OFF, listener);
        expectPowerComponentEnabledForListener(CUSTOM_COMPONENT_1000, listener);

        // Apply policy_id_other_off, to ensure that custom component is removed from enabled in
        // accumulated policy and moved to disabled
        mService.applyPowerPolicy(POWER_POLICY_OTHER_OFF);
        waitForPowerPolicy(POWER_POLICY_OTHER_OFF);

        waitForPolicyIdForListeners(POWER_POLICY_OTHER_OFF, listener);
        expectPowerComponentDisabledForListener(CUSTOM_COMPONENT_1000, listener);
    }

    @Test
    public void testPowerPolicyNotificationAccumulatedPolicy_powerPolicyRefactorFlagEnabled()
            throws Exception {
        setRefactoredService();
        grantPowerPolicyPermission();

        definePowerPolicy(POWER_POLICY_OTHER_NONE, POLICY_OTHER_NONE_ENABLED_COMPONENTS,
                POLICY_OTHER_NONE_DISABLED_COMPONENTS);
        definePowerPolicy(POWER_POLICY_CUSTOM, POLICY_CUSTOM_ENABLED_COMPONENTS,
                POLICY_CUSTOM_DISABLED_COMPONENTS);
        definePowerPolicyOtherComponentsOff(POWER_POLICY_OTHER_OFF,
                POLICY_OTHER_OFF_ENABLED_COMPONENTS, POLICY_OTHER_OFF_DISABLED_COMPONENTS);
        definePowerPolicyOtherComponentsOff(POWER_POLICY_CUSTOM_OTHER_OFF,
                POLICY_CUSTOM_OTHER_OFF_ENABLED_COMPONENTS,
                POLICY_CUSTOM_OTHER_OFF_DISABLED_COMPONENTS);
        CarPowerPolicyFilter filter1000 = getListenerFilterForComponents(CUSTOM_COMPONENT_1000);
        CarPowerPolicyFilter filterAudio = getListenerFilterForComponents(PowerComponent.AUDIO);
        MockedPowerPolicyListener listener = new MockedPowerPolicyListener();
        MockedPowerPolicyListener referenceListenerAudio = new MockedPowerPolicyListener();
        mService.addPowerPolicyListener(filterAudio, referenceListenerAudio);
        mService.addPowerPolicyListener(filter1000, listener);

        // Start test
        mService.applyPowerPolicy(POWER_POLICY_OTHER_NONE);
        waitForPowerPolicy(POWER_POLICY_OTHER_NONE);

        expectPowerComponentHandlerPolicySet(POWER_POLICY_OTHER_NONE);
        waitForListenerPolicyNotNull(referenceListenerAudio);
        expectPowerComponentDisabledForListener(PowerComponent.AUDIO, referenceListenerAudio);
        // Custom component state hasn't changed, as result no current policy in listener
        expectListenerPowerPolicyNull(listener);

        mService.applyPowerPolicy(POWER_POLICY_CUSTOM_OTHER_OFF);
        waitForPowerPolicy(POWER_POLICY_CUSTOM_OTHER_OFF);

        // Audio state is not expected to change, so listener shouldn't have been updated
        waitForPolicyIdForListeners(POWER_POLICY_OTHER_NONE, referenceListenerAudio);
        waitForPolicyIdForListeners(POWER_POLICY_CUSTOM_OTHER_OFF, listener);
        expectPowerComponentEnabledForListener(CUSTOM_COMPONENT_1000, listener);

        // Change again and ensure no notification
        mService.applyPowerPolicy(POWER_POLICY_CUSTOM);
        waitForPowerPolicy(POWER_POLICY_CUSTOM);

        // This policy doesn't change state of component_1000, so its listener shouldn't be updated
        waitForPolicyIdForListeners(POWER_POLICY_CUSTOM_OTHER_OFF, listener);
        expectPowerComponentEnabledForListener(CUSTOM_COMPONENT_1000, listener);

        // Apply policy_id_other_off, to ensure that custom component is removed from enabled in
        // accumulated policy and moved to disabled
        mService.applyPowerPolicy(POWER_POLICY_OTHER_OFF);
        waitForPowerPolicy(POWER_POLICY_OTHER_OFF);

        waitForPolicyIdForListeners(POWER_POLICY_OTHER_OFF, listener);
        expectPowerComponentDisabledForListener(CUSTOM_COMPONENT_1000, listener);
    }

    @Test
    public void testPowerStateAidlDefinition() throws Exception {
        Field[] powerManagerFields = CarPowerManager.class.getFields();
        String statePrefix = "STATE_";

        for (int i = 0; i < powerManagerFields.length; i++) {
            String powerManagerField = powerManagerFields[i].getName();
            if (powerManagerField.startsWith(statePrefix)) {
                String powerState = powerManagerField.substring(statePrefix.length());
                int powerStateInt = powerManagerFields[i].getInt(null);
                int aidlPowerStateInt = ICarPowerPolicyDelegate.PowerState.class.getField(
                        powerState).getInt(null);
                assertWithMessage("Power state int representation of '%s'", powerState).that(
                        aidlPowerStateInt).isEqualTo(powerStateInt);
            }
        }
    }

    @Test
    public void testDumpToProto() throws Exception {
        int pollingIntervalMs = 1000;
        int prepareTimeMs = 10 * 60 * 1000;
        mService.setShutdownTimersForTest(pollingIntervalMs, prepareTimeMs);
        ProtoOutputStream proto = new ProtoOutputStream();

        mService.dumpProto(proto);

        CarPowerDumpProto carPowerDumpProto = CarPowerDumpProto.parseFrom(proto.getBytes());
        assertThat(carPowerDumpProto.getShutdownPollingIntervalMs()).isEqualTo(pollingIntervalMs);
        assertThat(carPowerDumpProto.getShutdownPrepareTimeMs()).isEqualTo(prepareTimeMs);
    }

    @Test
    public void testCanTurnOnDisplay_isNotPowerSaving() throws Exception {
        boolean isPowerSaving = false;
        mScreenOffHandler.setIsAutoPowerSaving(isPowerSaving);
        int displayId = 123;

        boolean canTurnOnDisplay = mService.canTurnOnDisplay(displayId);

        assertWithMessage("Turn on display status while not power saving").that(
                canTurnOnDisplay).isTrue();
    }

    @Test
    public void testCanTurnOnDisplay_displayOff() throws Exception {
        boolean isPowerSaving = true;
        mScreenOffHandler.setIsAutoPowerSaving(isPowerSaving);
        int displayId = 321;
        mScreenOffHandler.setDisplayPowerInfo(displayId, ScreenOffHandler.DISPLAY_POWER_MODE_OFF);

        boolean canTurnOnDisplay = mService.canTurnOnDisplay(displayId);

        assertWithMessage("Turn on display status when power mode is 'off'").that(
                canTurnOnDisplay).isFalse();
    }

    @Test
    public void testCanTurnOnDisplay_displayOn() throws Exception {
        boolean isPowerSaving = true;
        mScreenOffHandler.setIsAutoPowerSaving(isPowerSaving);
        int displayId = 333;
        mScreenOffHandler.setDisplayPowerInfo(displayId, ScreenOffHandler.DISPLAY_POWER_MODE_ON);

        boolean canTurnOnDisplay = mService.canTurnOnDisplay(displayId);

        assertWithMessage("Turn on display status when power mode is 'on'").that(
                canTurnOnDisplay).isTrue();
    }

    @Test
    public void testHandleDisplayChanged_displayOff() throws Exception {
        int displayId = Display.DEFAULT_DISPLAY;
        boolean displayOn = false;

        mService.handleDisplayChanged(displayId, displayOn);

        mScreenOffHandler.waitForDisplayHandled(WAIT_TIMEOUT_MS);
        assertWithMessage("Display " + displayId + " with state 'off' handled status")
                .that(mScreenOffHandler.isDisplayStateHandled(displayId, displayOn)).isTrue();
    }

    @Test
    public void testHandleDisplayChanged_displayOn() throws Exception {
        int displayId = 101;
        boolean displayOn = true;

        mService.handleDisplayChanged(displayId, displayOn);

        mScreenOffHandler.waitForDisplayHandled(WAIT_TIMEOUT_MS);
        assertWithMessage("Display " + displayId + " with state 'on' handled status")
                .that(mScreenOffHandler.isDisplayStateHandled(displayId, displayOn)).isTrue();
    }

    @Test
    public void testOnDisplayBrightnessChange_noDisplayId() throws Exception {
        int brightness = 10;

        mService.onDisplayBrightnessChange(brightness);

        expectDisplayBrightnessChangeApplied(Display.DEFAULT_DISPLAY, brightness);
    }

    @Test
    public void testOnDisplayBrightnessChange_withDisplayId() throws Exception {
        int brightness = 100;
        int displayId = 1;

        mService.onDisplayBrightnessChange(displayId, brightness);

        expectDisplayBrightnessChangeApplied(displayId, brightness);
    }

    @Test
    public void testOnInitComplete() throws Exception {
        setCarPowerPolicyRefactoringFeatureFlag(true);
        setServerlessRemoteAccessFlag(true);

        VehicleStub mockVehicleStub = mock(VehicleStub.class);
        when(mockVehicleStub.getHalPropValueBuilder()).thenReturn(mHalPropValueBuilder);
        when(mockVehicleStub.isAidlVhal()).thenReturn(true);

        var vehicleHal = new VehicleHal(mContext, mockVehicleStub);
        var service = new CarPowerManagementService(mContext, mResources,
                new PowerHalService(mContext, vehicleHal),
                mSystemInterface, mUserManager, mUserService, mRefactoredPowerPolicyDaemon,
                mPowerComponentHandler, mFeatureFlags, mScreenOffHandler,
                mFileHwStateMonitoring.getFile().getPath(),
                mFileKernelSilentMode.getFile().getPath(), NORMAL_BOOT);

        HalPropValue vehicleInUseRequest = mHalPropValueBuilder.build(
                VehicleProperty.VEHICLE_IN_USE, /* areaId= */ 0);
        HalPropValue vehicleInUseResponse = mHalPropValueBuilder.build(
                VehicleProperty.VEHICLE_IN_USE, /* areaId= */ 0, /* value= */ 0);
        when(mockVehicleStub.get(eq(vehicleInUseRequest))).thenReturn(vehicleInUseResponse);

        HalPropValue bootupReasonRequest = mHalPropValueBuilder.build(
                VehicleProperty.AP_POWER_BOOTUP_REASON, /* areaId= */ 0);
        HalPropValue bootupReasonResponse = mHalPropValueBuilder.build(
                VehicleProperty.AP_POWER_BOOTUP_REASON,
                /* areaId= */ 0, VehicleApPowerBootupReason.SYSTEM_ENTER_GARAGE_MODE);
        when(mockVehicleStub.get(eq(bootupReasonRequest))).thenReturn(bootupReasonResponse);

        service.onInitComplete();

        verify(mockVehicleStub).set(mHalPropValueBuilder.build(VehicleProperty.SHUTDOWN_REQUEST, 0,
                VehicleApPowerStateShutdownParam.SHUTDOWN_ONLY));
    }

    @Test
    public void testOnInitComplete_flagDisabled_doNothing() {
        setCarPowerPolicyRefactoringFeatureFlag(true);
        setServerlessRemoteAccessFlag(false);

        VehicleStub mockVehicleStub = mock(VehicleStub.class);
        when(mockVehicleStub.getHalPropValueBuilder()).thenReturn(mHalPropValueBuilder);
        when(mockVehicleStub.isAidlVhal()).thenReturn(true);

        var vehicleHal = new VehicleHal(mContext, mockVehicleStub);
        var service = new CarPowerManagementService(mContext, mResources,
                new PowerHalService(mContext, vehicleHal),
                mSystemInterface, mUserManager, mUserService, mRefactoredPowerPolicyDaemon,
                mPowerComponentHandler, mFeatureFlags, mScreenOffHandler,
                mFileHwStateMonitoring.getFile().getPath(),
                mFileKernelSilentMode.getFile().getPath(), NORMAL_BOOT);

        service.onInitComplete();
    }

    @Test
    public void testOnInitComplete_bootupReasonNotSupported() throws Exception {
        setCarPowerPolicyRefactoringFeatureFlag(true);
        setServerlessRemoteAccessFlag(true);

        VehicleStub mockVehicleStub = mock(VehicleStub.class);
        when(mockVehicleStub.getHalPropValueBuilder()).thenReturn(mHalPropValueBuilder);
        when(mockVehicleStub.isAidlVhal()).thenReturn(true);

        var vehicleHal = new VehicleHal(mContext, mockVehicleStub);
        var service = new CarPowerManagementService(mContext, mResources,
                new PowerHalService(mContext, vehicleHal),
                mSystemInterface, mUserManager, mUserService, mRefactoredPowerPolicyDaemon,
                mPowerComponentHandler, mFeatureFlags, mScreenOffHandler,
                mFileHwStateMonitoring.getFile().getPath(),
                mFileKernelSilentMode.getFile().getPath(), NORMAL_BOOT);

        HalPropValue bootupReasonRequest = mHalPropValueBuilder.build(
                VehicleProperty.AP_POWER_BOOTUP_REASON, /* areaId= */ 0);
        HalPropValue bootupReasonResponse = mHalPropValueBuilder.build(
                VehicleProperty.AP_POWER_BOOTUP_REASON,
                /* areaId= */ 0, VehicleApPowerBootupReason.SYSTEM_ENTER_GARAGE_MODE);
        when(mockVehicleStub.get(eq(bootupReasonRequest))).thenThrow(
                new IllegalArgumentException());

        service.onInitComplete();

        verify(mockVehicleStub, never()).set(any());
    }

    @Test
    public void testOnInitComplete_bootUpReasonNotGarageMode() throws Exception {
        setCarPowerPolicyRefactoringFeatureFlag(true);
        setServerlessRemoteAccessFlag(true);

        VehicleStub mockVehicleStub = mock(VehicleStub.class);
        when(mockVehicleStub.getHalPropValueBuilder()).thenReturn(mHalPropValueBuilder);
        when(mockVehicleStub.isAidlVhal()).thenReturn(true);

        var vehicleHal = new VehicleHal(mContext, mockVehicleStub);
        var service = new CarPowerManagementService(mContext, mResources,
                new PowerHalService(mContext, vehicleHal),
                mSystemInterface, mUserManager, mUserService, mRefactoredPowerPolicyDaemon,
                mPowerComponentHandler, mFeatureFlags, mScreenOffHandler,
                mFileHwStateMonitoring.getFile().getPath(),
                mFileKernelSilentMode.getFile().getPath(), NORMAL_BOOT);

        HalPropValue vehicleInUseRequest = mHalPropValueBuilder.build(
                VehicleProperty.VEHICLE_IN_USE, /* areaId= */ 0);
        HalPropValue vehicleInUseResponse = mHalPropValueBuilder.build(
                VehicleProperty.VEHICLE_IN_USE, /* areaId= */ 0, /* value= */ 0);
        when(mockVehicleStub.get(eq(vehicleInUseRequest))).thenReturn(vehicleInUseResponse);

        HalPropValue bootupReasonRequest = mHalPropValueBuilder.build(
                VehicleProperty.AP_POWER_BOOTUP_REASON, /* areaId= */ 0);
        // Bootup reason is USER_POWER_ON, not SYSTEM_ENTER_GARAGE_MODE.
        HalPropValue bootupReasonResponse = mHalPropValueBuilder.build(
                VehicleProperty.AP_POWER_BOOTUP_REASON,
                /* areaId= */ 0, VehicleApPowerBootupReason.USER_POWER_ON);
        when(mockVehicleStub.get(eq(bootupReasonRequest))).thenReturn(bootupReasonResponse);

        service.onInitComplete();

        verify(mockVehicleStub, never()).set(any());
    }

    @Test
    public void testOnInitComplete_vehicleInUse() throws Exception {
        setCarPowerPolicyRefactoringFeatureFlag(true);
        setServerlessRemoteAccessFlag(true);

        VehicleStub mockVehicleStub = mock(VehicleStub.class);
        when(mockVehicleStub.getHalPropValueBuilder()).thenReturn(mHalPropValueBuilder);
        when(mockVehicleStub.isAidlVhal()).thenReturn(true);

        var vehicleHal = new VehicleHal(mContext, mockVehicleStub);
        var service = new CarPowerManagementService(mContext, mResources,
                new PowerHalService(mContext, vehicleHal),
                mSystemInterface, mUserManager, mUserService, mRefactoredPowerPolicyDaemon,
                mPowerComponentHandler, mFeatureFlags, mScreenOffHandler,
                mFileHwStateMonitoring.getFile().getPath(),
                mFileKernelSilentMode.getFile().getPath(), NORMAL_BOOT);

        HalPropValue vehicleInUseRequest = mHalPropValueBuilder.build(
                VehicleProperty.VEHICLE_IN_USE, /* areaId= */ 0);
        // VEHICLE_IN_USE is true.
        HalPropValue vehicleInUseResponse = mHalPropValueBuilder.build(
                VehicleProperty.VEHICLE_IN_USE, /* areaId= */ 0, /* value= */ 1);
        when(mockVehicleStub.get(eq(vehicleInUseRequest))).thenReturn(vehicleInUseResponse);

        HalPropValue bootupReasonRequest = mHalPropValueBuilder.build(
                VehicleProperty.AP_POWER_BOOTUP_REASON, /* areaId= */ 0);
        HalPropValue bootupReasonResponse = mHalPropValueBuilder.build(
                VehicleProperty.AP_POWER_BOOTUP_REASON,
                /* areaId= */ 0, VehicleApPowerBootupReason.SYSTEM_ENTER_GARAGE_MODE);
        when(mockVehicleStub.get(eq(bootupReasonRequest))).thenReturn(bootupReasonResponse);

        service.onInitComplete();

        verify(mockVehicleStub, never()).set(any());
    }

    private void setCarPowerPolicyRefactoringFeatureFlag(boolean flagValue) {
        mFeatureFlags.setFlag(Flags.FLAG_CAR_POWER_POLICY_REFACTORING, flagValue);
    }

    private void setServerlessRemoteAccessFlag(boolean flagValue) {
        mFeatureFlags.setFlag(Flags.FLAG_SERVERLESS_REMOTE_ACCESS, flagValue);
    }

    /**
     * Helper method to create mService and initialize a test case
     */
    private void setService() throws Exception {
        doReturn(mResources).when(mContext).getResources();
        // During the test, changing Wifi state according to a power policy takes long time, leading
        // to timeout. Also, we don't want to actually change Wifi state.
        doReturn(mWifiManager).when(mContext).getSystemService(WifiManager.class);
        doReturn(mTetheringManager).when(mContext).getSystemService(TetheringManager.class);
        when(mResources.getInteger(R.integer.maxGarageModeRunningDurationInSecs))
                .thenReturn(900);
        when(mResources.getInteger(R.integer.config_maxSuspendWaitDuration))
                .thenReturn(WAKE_UP_DELAY);
        when(mResources.getBoolean(R.bool.config_enablePassengerDisplayPowerSaving))
                .thenReturn(false);
        doReturn(true).when(() -> VoiceInteractionHelper.isAvailable());
        doAnswer(invocation -> {
            mVoiceInteractionEnabled = (boolean) invocation.getArguments()[0];
            return null;
        }).when(() -> VoiceInteractionHelper.setEnabled(anyBoolean()));

        mFileHwStateMonitoring = new TemporaryFile("HW_STATE_MONITORING");
        mFileKernelSilentMode = new TemporaryFile("KERNEL_SILENT_MODE");
        mFileHwStateMonitoring.write(NONSILENT_STRING);
        mPowerComponentHandler = new PowerComponentHandler(mContext, mSystemInterface,
                new AtomicFile(mComponentStateFile.getFile()));
        mPowerPolicyDaemon = new FakeCarPowerPolicyDaemon();
        setCarPowerPolicyRefactoringFeatureFlag(false);
        mService = new CarPowerManagementService(mContext, mResources, mPowerHal,
                mSystemInterface, mUserManager, mUserService, mPowerPolicyDaemon,
                mPowerComponentHandler, mFeatureFlags, mScreenOffHandler,
                mFileHwStateMonitoring.getFile().getPath(),
                mFileKernelSilentMode.getFile().getPath(), NORMAL_BOOT);
        CarLocalServices.removeServiceForTest(CarPowerManagementService.class);
        CarLocalServices.addService(CarPowerManagementService.class, mService);
        mService.init();
        mService.setShutdownTimersForTest(0, 0);
        mPowerHal.setSignalListener(mPowerSignalListener);
        mService.scheduleNextWakeupTime(WAKE_UP_DELAY);
        assertStateReceived(MockedPowerHalService.SET_WAIT_FOR_VHAL, 0);
    }

    /**
     * Helper method to set up service for test cases that use the refactored power policy in CPMS
     */
    private void setRefactoredService() throws Exception {
        mRefactoredPowerPolicyDaemon = new FakeRefactoredCarPowerPolicyDaemon(mFileKernelSilentMode,
                new int[]{CUSTOM_COMPONENT_1000, CUSTOM_COMPONENT_1001, CUSTOM_COMPONENT_1002,
                        CUSTOM_COMPONENT_1003});
        setCarPowerPolicyRefactoringFeatureFlag(true);
        mService = new CarPowerManagementService(mContext, mResources, mPowerHal,
                mSystemInterface, mUserManager, mUserService, mRefactoredPowerPolicyDaemon,
                mPowerComponentHandler, mFeatureFlags, mScreenOffHandler,
                mFileHwStateMonitoring.getFile().getPath(),
                mFileKernelSilentMode.getFile().getPath(), NORMAL_BOOT);
        CarLocalServices.removeServiceForTest(CarPowerManagementService.class);
        CarLocalServices.addService(CarPowerManagementService.class, mService);
        mService.init();
        mService.initializePowerPolicy();
        mService.setShutdownTimersForTest(0, 0);
        mService.scheduleNextWakeupTime(WAKE_UP_DELAY);
        assertStateReceived(MockedPowerHalService.SET_WAIT_FOR_VHAL, 0);
    }

    private void suspendDevice() throws Exception {
        mService.handleOn();
        mPowerSignalListener.addEventListener(PowerHalService.SET_DEEP_SLEEP_ENTRY);
        mPowerSignalListener.addEventListener(PowerHalService.SET_DEEP_SLEEP_EXIT);
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP));
        mDisplayInterface.waitForAllDisplaysOff(WAIT_TIMEOUT_MS);
        mPowerSignalListener.waitFor(PowerHalService.SET_DEEP_SLEEP_ENTRY, WAIT_TIMEOUT_MS);
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        mSystemStateInterface.setWakeupCausedByTimer(true);
        mSystemStateInterface.waitForSleepEntryAndWakeup(WAIT_TIMEOUT_MS);
        mPowerSignalListener.waitFor(PowerHalService.SET_DEEP_SLEEP_EXIT, WAIT_TIMEOUT_MS);
    }

    private void suspendWithFailure(Integer nextPowerState) throws Exception {
        mSystemStateInterface.setSleepEntryResult(false);
        mSystemStateInterface.setSimulateSleep(false);
        mPowerSignalListener.addEventListener(PowerHalService.SET_ON);

        // Transition to ON state
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        mPowerSignalListener.waitFor(PowerHalService.SET_ON, WAIT_TIMEOUT_MS);

        mPowerHal.setCurrentPowerState(
                new PowerState(
                        VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                        VehicleApPowerStateShutdownParam.SLEEP_IMMEDIATELY));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_DEEP_SLEEP_ENTRY, 0);
        assertThat(mService.garageModeShouldExitImmediately()).isTrue();

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));

        mSystemStateInterface.waitForDeepSleepEntry(WAIT_TIMEOUT_MS);
        if (nextPowerState != null) {
            mPowerHal.setCurrentPowerState(new PowerState(nextPowerState, 0));
        }
    }

    private void suspendAndResume() throws Exception {
        grantPowerPolicyPermission();
        mPowerSignalListener.addEventListener(PowerHalService.SET_DEEP_SLEEP_ENTRY);
        mPowerSignalListener.addEventListener(PowerHalService.SET_DEEP_SLEEP_EXIT);
        Log.d(TAG, "suspend()");
        mVoiceInteractionEnabled = true;
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP));
        mDisplayInterface.waitForAllDisplaysOff(WAIT_TIMEOUT_MS);
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_DEEP_SLEEP_ENTRY);
        assertVoiceInteractionDisabled();
        mPowerSignalListener.waitFor(PowerHalService.SET_DEEP_SLEEP_ENTRY, WAIT_TIMEOUT_MS);
        verify(mUserService).onSuspend();

        // Send the finished signal
        Log.d(TAG, "resume()");
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        mSystemStateInterface.setWakeupCausedByTimer(true);
        mSystemStateInterface.waitForSleepEntryAndWakeup(WAIT_TIMEOUT_MS);
        assertStateReceived(PowerHalService.SET_DEEP_SLEEP_EXIT, 0);
        mPowerSignalListener.waitFor(PowerHalService.SET_DEEP_SLEEP_EXIT, WAIT_TIMEOUT_MS);
        mService.scheduleNextWakeupTime(WAKE_UP_DELAY);

        // second processing after wakeup
        assertThat(mService.getCurrentPowerPolicy().getPolicyId()).isEqualTo(
                SYSTEM_POWER_POLICY_INITIAL_ON);
        assertThat(mDisplayInterface.isAnyDisplayEnabled()).isTrue();

        mFileHwStateMonitoring.write(NONSILENT_STRING); // Wake non-silently
        mService.setStateForWakeUp();
        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.ON, 0));
        assertVoiceInteractionEnabled();

        mDisplayInterface.waitForAllDisplaysOn(WAIT_TIMEOUT_MS);
        // Should wait until Handler has finished ON processing.
        CarServiceUtils.runOnLooperSync(mService.getHandlerThread().getLooper(), () -> { });

        verify(mUserService).onResume();

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP));
        assertStateReceivedForShutdownOrSleepWithPostpone(PowerHalService.SET_DEEP_SLEEP_ENTRY);
        mPowerSignalListener.waitFor(PowerHalService.SET_DEEP_SLEEP_ENTRY, WAIT_TIMEOUT_MS);

        verify(mUserService, times(2)).onSuspend();

        mPowerHal.setCurrentPowerState(new PowerState(VehicleApPowerStateReq.FINISHED, 0));
        // PM will shutdown system as it was not woken-up due timer and it is not power on.
        mSystemStateInterface.setWakeupCausedByTimer(false);
        mSystemStateInterface.waitForSleepEntryAndWakeup(WAIT_TIMEOUT_MS);
        // Since we just woke up from shutdown, wake up time will be 0
        assertStateReceived(PowerHalService.SET_DEEP_SLEEP_EXIT, 0);
        assertThat(mDisplayInterface.isAnyDisplayEnabled()).isTrue();
    }

    private void assertStateReceived(int expectedState, int expectedParam) throws Exception {
        int[] state = mPowerHal.waitForSend(WAIT_TIMEOUT_MS);
        assertThat(state[0]).isEqualTo(expectedState);
        assertThat(state[1]).isEqualTo(expectedParam);
    }

    private void assertStateReceivedForShutdownOrSleepWithPostpone(int lastState,
            int expectedSecondParameter)
            throws Exception {
        while (true) {
            if (mFuture != null && !mFuture.isDone()) {
                mFuture.complete(null);
            }
            int[] state = mPowerHal.waitForSend(WAIT_TIMEOUT_LONG_MS);
            if (state[0] == PowerHalService.SET_SHUTDOWN_POSTPONE) {
                continue;
            }
            if (state[0] == lastState) {
                assertThat(state[1]).isEqualTo(expectedSecondParameter);
                return;
            }
        }
    }

    private void assertStateReceivedForShutdownOrSleepWithPostpone(int lastState) throws Exception {
        int expectedSecondParameter =
                (lastState == MockedPowerHalService.SET_DEEP_SLEEP_ENTRY
                        || lastState == MockedPowerHalService.SET_SHUTDOWN_START
                        || lastState == MockedPowerHalService.SET_HIBERNATION_ENTRY)
                        ? WAKE_UP_DELAY : 0;
        assertStateReceivedForShutdownOrSleepWithPostpone(lastState, expectedSecondParameter);
    }

    private void assertVoiceInteractionEnabled() throws Exception {
        PollingCheck.check("Voice interaction is not enabled", WAIT_TIMEOUT_LONG_MS,
                () -> {
                    return mVoiceInteractionEnabled;
                });
    }

    private void assertVoiceInteractionDisabled() throws Exception {
        PollingCheck.check("Voice interaction is not disabled", WAIT_TIMEOUT_LONG_MS,
                () -> {
                    return !mVoiceInteractionEnabled;
                });
    }

    private void openRawResource(int resourceId) throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        mService.readPowerPolicyFromXml(
                context.getResources().openRawResource(resourceId));
    }

    private boolean definePowerPolicyFromCommand(String[] args) {
        return mService.definePowerPolicyFromCommand(
                args, new IndentingPrintWriter(new StringWriter(), "  "));
    }

    private boolean definePowerPolicyGroupFromCommand(String[] args) {
        return mService.definePowerPolicyGroupFromCommand(
                args, new IndentingPrintWriter(new StringWriter(), "  "));
    }

    private void definePowerPolicyGroup(String policyGroupId, String waitForVhalPolicyId,
            String onPolicyId) {
        String[] args = {COMMAND_DEFINE_POLICY_GROUP, policyGroupId,
                POWER_POLICY_GROUP_CMD_VHAL_PREFIX + waitForVhalPolicyId,
                POWER_POLICY_GROUP_CMD_ON_PREFIX + onPolicyId};
        boolean policyGroupDefinedSuccessfully = definePowerPolicyGroupFromCommand(args);
        assertWithMessage("Policy group definition success").that(
                policyGroupDefinedSuccessfully).isTrue();
    }

    private boolean applyPowerPolicyFromCommand(String policyId) {
        String[] args = {"apply-power-policy", policyId};
        return mService.applyPowerPolicyFromCommand(
                args, new IndentingPrintWriter(new StringWriter(), "  "));
    }

    private MockedPowerPolicyListener setUpPowerPolicyAudioInvert() {
        String[] enabledComponents = new String[]{};
        String[] disabledComponents = new String[]{};
        if (mService.getCurrentPowerPolicy().isComponentEnabled(PowerComponent.AUDIO)) {
            disabledComponents = new String[]{"AUDIO"};
        } else {
            enabledComponents = new String[]{"AUDIO"};
        }
        return setUpPowerPolicy(/* policyId= */ POWER_POLICY_AUDIO_INVERT, enabledComponents,
                disabledComponents, /* filterComponentValues...= */ PowerComponent.AUDIO);
    }

    private CarPowerPolicyFilter getListenerFilterForComponents(int... filterComponentValues) {
        return new CarPowerPolicyFilter.Builder().setComponents(filterComponentValues).build();
    }

    private Pair<List<Integer>, List<Integer>> getAllEnabledAndDisabledComponents(
            List<Integer> enabledComponents, List<Integer> disabledComponents,
            boolean otherComponentsOn) {
        Set<Integer> componentsToAdd = mService.getPolicyReader().getAllComponents();
        List<Integer> allEnabledComponents = new ArrayList<>(enabledComponents);
        List<Integer> allDisabledComponents = new ArrayList<>(disabledComponents);
        for (int i = 0; i < enabledComponents.size(); i++) {
            componentsToAdd.remove(enabledComponents.get(i));
        }
        for (int i = 0; i < disabledComponents.size(); i++) {
            componentsToAdd.remove(disabledComponents.get(i));
        }
        if (otherComponentsOn) {
            allEnabledComponents.addAll(componentsToAdd);
        } else {
            allDisabledComponents.addAll(componentsToAdd);
        }
        return new Pair<>(allEnabledComponents, allDisabledComponents);
    }

    private void definePowerPolicy(String policyId, Iterable<Integer> enabledComponents,
            Iterable<Integer> disabledComponents) {
        mService.definePowerPolicy(policyId,
                powerComponentsToStrings(enabledComponents).toArray(String[]::new),
                powerComponentsToStrings(disabledComponents).toArray(String[]::new));
    }

    private void definePowerPolicyOtherComponentsOff(String policyId,
            List<Integer> enabledComponents, List<Integer> disabledComponents) {
        Pair<List<Integer>, List<Integer>> policyAllComponents = getAllEnabledAndDisabledComponents(
                enabledComponents, disabledComponents, /* otherComponentsOn= */ false);
        definePowerPolicy(policyId, policyAllComponents.first, policyAllComponents.second);
    }

    private void definePowerPolicyOtherComponentsOn(String policyId,
            List<Integer> enabledComponents, List<Integer> disabledComponents) {
        Pair<List<Integer>, List<Integer>> policyAllComponents = getAllEnabledAndDisabledComponents(
                enabledComponents, disabledComponents, /* otherComponentsOn= */ false);
        definePowerPolicy(policyId, policyAllComponents.first, policyAllComponents.second);
    }

    private MockedPowerPolicyListener setUpPowerPolicy(String policyId, String[] enabledComponents,
            String[] disabledComponents, int... filterComponentValues) {
        mService.definePowerPolicy(policyId, enabledComponents, disabledComponents);
        MockedPowerPolicyListener listenerToWait = new MockedPowerPolicyListener();
        mService.addPowerPolicyListener(
                getListenerFilterForComponents(filterComponentValues), listenerToWait);
        return listenerToWait;
    }

    private void assertPowerPolicyApplied(String policyId, MockedPowerPolicyListener listenerToWait)
            throws Exception {
        waitForPowerPolicy(policyId);
        assertThat(mPowerComponentHandler.getAccumulatedPolicy().getPolicyId()).isEqualTo(policyId);
        PollingCheck.check("Current power policy of listener is null", WAIT_TIMEOUT_LONG_MS,
                () -> listenerToWait.getCurrentPowerPolicy() != null);
        if (mFeatureFlags.carPowerPolicyRefactoring()) {
            assertWithMessage("Power policy daemon last notified policy ID").that(
                    mRefactoredPowerPolicyDaemon.getLastAppliedPowerPolicyId()).isEqualTo(policyId);
        } else {
            assertWithMessage("Power policy daemon last notified policy ID").that(
                    mPowerPolicyDaemon.getLastNotifiedPolicyId()).isEqualTo(policyId);
        }
    }

    private void assertPowerPolicyGroupSet(String policyGroupId) {
        assertWithMessage("Current power policy group id").that(
                mService.getCurrentPowerPolicyGroupId()).isEqualTo(policyGroupId);
    }

    private void assertPowerPolicyRequestRemoved() {
        assertWithMessage("Number of current power policy requests").that(
                mService.getNumberOfCurrentPolicyRequests()).isEqualTo(0);
    }

    private void assertDefinePowerPolicyFromCommandFailed(String[] args) {
        try (IndentingPrintWriter writer = new IndentingPrintWriter(new StringWriter(), "  ")) {
            boolean status = mService.definePowerPolicyFromCommand(args, writer);

            assertWithMessage(
                    "Calling definePowerPolicyFromCommand with args: "
                            + Arrays.toString(args) + " must fail").that(status).isFalse();
        }
    }

    private void expectPowerComponentEnabled(int component) {
        expectWithMessage("Component " + powerComponentToString(component) + " enabled").that(
                mService.getCurrentPowerPolicy().isComponentEnabled(component)).isTrue();
    }

    private void expectPowerComponentEnabledForListener(
            int component, MockedPowerPolicyListener listener) throws Exception {
        expectWithMessage("Component " + powerComponentToString(component) + " enabled "
            + "for supplied listener").that(
                    listener.getCurrentPowerPolicy().isComponentEnabled(component)).isTrue();
    }

    private void expectPowerComponentDisabled(int component) {
        expectWithMessage("Component " + powerComponentToString(component) + " disabled").that(
                mService.getCurrentPowerPolicy().isComponentEnabled(component)).isFalse();
    }

    private void expectPowerComponentDisabledForListener(
            int component, MockedPowerPolicyListener listener) throws Exception {
        expectWithMessage("Component " + powerComponentToString(component) + " disabled "
                + "for supplied listener").that(
                listener.getCurrentPowerPolicy().isComponentEnabled(component)).isFalse();
    }

    private void expectPowerPolicySet(String policyId) {
        CarPowerPolicy policy = mService.getCurrentPowerPolicy();
        assertWithMessage("Power policy null status").that(policy).isNotNull();
        expectWithMessage("Policy id").that(policy.getPolicyId()).isEqualTo(policyId);
    }

    private void expectPowerComponentHandlerPolicySet(String policyId) {
        expectWithMessage("Accumulated policy id").that(
                mPowerComponentHandler.getAccumulatedPolicy().getPolicyId()).isEqualTo(policyId);
    }

    private void expectListenerPowerPolicyNonNull(MockedPowerPolicyListener listener)
            throws Exception {
        expectWithMessage("Listener current power policy null status")
                .that(listener.getCurrentPowerPolicy()).isNotNull();
    }

    private void expectListenerPowerPolicyNull(MockedPowerPolicyListener listener)
            throws Exception {
        expectWithMessage("Listener current power policy null status")
                .that(listener.getCurrentPowerPolicy()).isNull();
    }

    private void expectDisplayBrightnessChangeApplied(int displayId, int brightness)
            throws Exception {
        mDisplayInterface.waitForDisplayBrightness(displayId, brightness, WAIT_TIMEOUT_MS);
        expectWithMessage("Display " + displayId + " brightness").that(
                mDisplayInterface.getDisplayBrightness(displayId)).isEqualTo(brightness);
    }

    private void waitForPowerPolicy(String policyId) throws Exception {
        PollingCheck.check("Policy id is not " + policyId, WAIT_TIMEOUT_LONG_MS,
                () -> {
                    CarPowerPolicy policy = mService.getCurrentPowerPolicy();
                    Log.d(TAG, "CarPowerManagementService current power policy is "
                            + policy.getPolicyId());
                    return policy != null && policyId.equals(policy.getPolicyId());
                });
    }

    private static void waitForPolicyId(MockedPowerPolicyListener listener, String policyId,
            String errorMsg) throws Exception {
        PollingCheck.check(errorMsg, WAIT_TIMEOUT_LONG_MS,
                () -> {
                    CarPowerPolicy policy = listener.getCurrentPowerPolicy();
                    return policy != null && policyId.equals(policy.getPolicyId());
                });
    }

    private static void waitForListenerPolicyNotNull(MockedPowerPolicyListener listener)
            throws Exception {
        PollingCheck.check("Current power policy of listener is null", WAIT_TIMEOUT_LONG_MS,
                () -> listener.getCurrentPowerPolicy() != null);
    }

    private static void waitForSemaphore(Semaphore semaphore, long timeoutMs)
            throws InterruptedException {
        if (!semaphore.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException("semaphore timeout");
        }
    }

    private UserInfo setCurrentUser(int userId, boolean isGuest) {
        mockGetCurrentUser(userId);
        final UserInfo userInfo = new UserInfo();
        userInfo.id = userId;
        userInfo.userType = isGuest
                ? UserManager.USER_TYPE_FULL_GUEST
                : UserManager.USER_TYPE_FULL_SECONDARY;
        Log.v(TAG, "UM.getUserInfo("  + userId + ") will return " + userInfo.toFullString());
        when(mUserManager.getUserInfo(userId)).thenReturn(userInfo);
        return userInfo;
    }

    private void waitForPolicyId(String policyId, Callable<Boolean> policyCheckCallable)
            throws Exception {
        PollingCheck.check("Policy id is not " + policyId, WAIT_TIMEOUT_LONG_MS,
                policyCheckCallable);
    }

    private void waitForPolicyIdForListeners(
            String policyId, MockedPowerPolicyListener... listeners) throws Exception {
        for (int i = 0; i < listeners.length; i++) {
            waitForPolicyId(listeners[i], policyId, "Expected listener policy ID to be "
                    + policyId);
        }
    }

    private void waitForPowerPolicyNotificationToDaemon(String policyId) throws Exception {
        waitForPolicyId(policyId,
                () -> policyId.equals(mPowerPolicyDaemon.getLastNotifiedPolicyId()));
    }

    private void grantPowerPolicyPermission() {
        doReturn(PackageManager.PERMISSION_GRANTED).when(mContext)
                .checkCallingOrSelfPermission(Car.PERMISSION_CONTROL_CAR_POWER_POLICY);
        doReturn(PackageManager.PERMISSION_GRANTED).when(mContext)
                .checkCallingOrSelfPermission(Car.PERMISSION_READ_CAR_POWER_POLICY);
    }

    private void grantAdjustShutdownProcessPermission() {
        doReturn(PackageManager.PERMISSION_GRANTED).when(mContext)
                .checkCallingOrSelfPermission(Car.PERMISSION_CONTROL_SHUTDOWN_PROCESS);
    }

    private static final class MockDisplayInterface implements DisplayInterface {
        private static final int WAIT_FOR_DISPLAY_BRIGHTNESS_RETRIES = 5;
        @GuardedBy("sLock")
        private final SparseBooleanArray mDisplayOn = new SparseBooleanArray();
        private final Semaphore mDisplayStateWait = new Semaphore(0);
        @GuardedBy("sLock")
        private final SparseIntArray mDisplayBrightnessSet = new SparseIntArray();
        private final Semaphore mDisplayBrightnessWait = new Semaphore(0);

        @Override
        public void init(CarPowerManagementService carPowerManagementService,
                CarUserService carUserService) {
            synchronized (sLock) {
                mDisplayOn.put(Display.DEFAULT_DISPLAY, true);
            }
        }

        @Override
        public void setDisplayBrightness(int brightness) {
            setDisplayBrightness(Display.DEFAULT_DISPLAY, brightness);
        }

        @Override
        public void setDisplayBrightness(int displayId, int percentBright) {
            synchronized (sLock) {
                if (percentBright == mDisplayBrightnessSet.get(displayId)) {
                    return;
                }
                mDisplayBrightnessSet.put(displayId, percentBright);
            }
            mDisplayBrightnessWait.release();
        }

        private int getDisplayBrightness(int displayId) {
            synchronized (sLock) {
                return mDisplayBrightnessSet.get(displayId);
            }
        }

        private void waitForDisplayBrightness(int displayId, int expectedBrightness, long timeoutMs)
                throws Exception {
            for (int tries = 0; tries < WAIT_FOR_DISPLAY_BRIGHTNESS_RETRIES; tries++) {
                synchronized (sLock) {
                    if (mDisplayBrightnessSet.get(displayId) == expectedBrightness) {
                        return;
                    }
                }
                if (tries < WAIT_FOR_DISPLAY_BRIGHTNESS_RETRIES - 1) {
                    waitForSemaphore(mDisplayBrightnessWait, timeoutMs);
                }
            }
            throw new IllegalStateException(
                    "wait for display " + displayId + " brightness timeout");
        }

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

        private void waitForDisplayOn(int displayId, long timeoutMs) throws Exception {
            waitForDisplayState(displayId, /* expectedState= */ true, timeoutMs);
        }

        private void waitForDisplayOff(int displayId, long timeoutMs) throws Exception {
            waitForDisplayState(displayId, /* expectedState= */ false, timeoutMs);
        }

        private void waitForAllDisplaysOn(long timeoutMs) throws Exception {
            waitForAllDisplaysState(/* expectedState= */ true, timeoutMs);
        }

        private void waitForAllDisplaysOff(long timeoutMs) throws Exception {
            waitForAllDisplaysState(/* expectedState= */ false, timeoutMs);
        }

        private void waitForAllDisplaysState(boolean expectedState, long timeoutMs)
                throws Exception {
            SparseBooleanArray displayOn;
            synchronized (sLock) {
                displayOn = mDisplayOn.clone();
            }
            for (int i = 0; i < displayOn.size(); i++) {
                int displayId = displayOn.keyAt(i);
                if (expectedState)  {
                    waitForDisplayOn(displayId, timeoutMs);
                } else {
                    waitForDisplayOff(displayId, timeoutMs);
                }
            }
        }

        private void waitForDisplayState(int displayId, boolean expectedState, long timeoutMs)
                throws Exception {
            int nTries = 0;
            while (true) {
                synchronized (sLock) {
                    boolean enabled = mDisplayOn.get(displayId);
                    if (enabled == expectedState) {
                        break;
                    }
                }
                if (nTries > 5) throw new IllegalStateException("timeout");
                waitForSemaphore(mDisplayStateWait, timeoutMs);
                nTries++;
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

    private static final class FakeScreenOffHandler extends ScreenOffHandler {
        @GuardedBy("sLock")
        private final SparseArray<FakeDisplayPowerInfo> mDisplayPowerInfos = new SparseArray<>();
        @GuardedBy("sLock")
        private final SparseLongArray mDisplayUserActivityTime = new SparseLongArray();
        @GuardedBy("sLock")
        private final SparseBooleanArray mHandledDisplayStates = new SparseBooleanArray();
        private final CountDownLatch mDisplayHandledLatch = new CountDownLatch(1);
        private boolean mIsAutoPowerSaving;
        @Retention(RetentionPolicy.SOURCE)
        @IntDef(prefix = "DISPLAY_POWER_MODE_", value = {
                DISPLAY_POWER_MODE_NONE,
                DISPLAY_POWER_MODE_OFF,
                DISPLAY_POWER_MODE_ON,
                DISPLAY_POWER_MODE_ALWAYS_ON,
        })
        @Target({ElementType.TYPE_USE})
        private @interface FakeDisplayPowerMode {}

        FakeScreenOffHandler(Context context, SystemInterface systemInterface, Looper looper) {
            super(context, systemInterface, looper);
        }

        @Override
        void init() {}

        private boolean isAutoPowerSaving() {
            return mIsAutoPowerSaving;
        }

        private void setIsAutoPowerSaving(boolean isPowerSaving) {
            mIsAutoPowerSaving = isPowerSaving;
        }

        private void setDisplayPowerInfo(int displayId, @FakeDisplayPowerMode int powerMode) {
            FakeDisplayPowerInfo info = new FakeDisplayPowerInfo(powerMode);
            mDisplayPowerInfos.put(displayId, info);
        }

        boolean canTurnOnDisplay(int displayId) {
            if (!mIsAutoPowerSaving) {
                return true;
            }
            synchronized (sLock) {
                return canTurnOnDisplayLocked(displayId);
            }
        }

        @GuardedBy("sLock")
        private boolean canTurnOnDisplayLocked(int displayId) {
            FakeDisplayPowerInfo info = mDisplayPowerInfos.get(displayId);
            if (info == null) {
                return false;
            }
            return info.getMode() != DISPLAY_POWER_MODE_OFF;
        }

        @Override
        void handleDisplayStateChange(int displayId, boolean on) {
            synchronized (sLock) {
                mHandledDisplayStates.put(displayId, on);
            }
            mDisplayHandledLatch.countDown();
        }

        private boolean isDisplayStateHandled(int displayId, boolean on) {
            synchronized (sLock) {
                // confirm that a value has actually been set for displayId in mHandledDisplayStates
                if (mHandledDisplayStates.indexOfKey(displayId) < 0) {
                    return false;
                }
                return mHandledDisplayStates.get(displayId) == on;
            }
        }

        private void waitForDisplayHandled(long timeoutMs) throws Exception {
            JavaMockitoHelper.await(mDisplayHandledLatch, timeoutMs);
        }

        @Override
        void updateUserActivity(int displayId, long eventTime) {
            synchronized (sLock) {
                mDisplayUserActivityTime.put(displayId, eventTime);
            }
        }

        public long getDisplayUserActivityTime(int displayId) throws IllegalArgumentException {
            synchronized (sLock) {
                if (mDisplayUserActivityTime.indexOfKey(displayId) > -1) {
                    return mDisplayUserActivityTime.get(displayId);
                }
                throw new IllegalArgumentException(
                        "Display " + displayId + " has no user activity timestamp.");
            }
        }

        private static final class FakeDisplayPowerInfo {
            private @FakeDisplayPowerMode int mMode;

            FakeDisplayPowerInfo(@FakeDisplayPowerMode int mode) {
                mMode = mode;
            }

            private void setMode(@FakeDisplayPowerMode int mode) {
                mMode = mode;
            }

            private @FakeDisplayPowerMode int getMode() {
                return mMode;
            }
        }
    }

    private static final class MockSystemStateInterface implements SystemStateInterface {
        private final Semaphore mShutdownWait = new Semaphore(0);
        private final Semaphore mSleepWait = new Semaphore(0);
        private final Semaphore mSleepExitWait = new Semaphore(0);

        private boolean mSleepEntryResult = true;
        private boolean mSimulateSleep = true;

        @GuardedBy("sLock")
        private boolean mWakeupCausedByTimer = false;

        @Override
        public void shutdown() {
            mShutdownWait.release();
        }

        public void waitForShutdown(long timeoutMs) throws Exception {
            waitForSemaphore(mShutdownWait, timeoutMs);
        }

        @Override
        public boolean enterDeepSleep() {
            if (mSimulateSleep) {
                return simulateSleep();
            }

            mSleepWait.release();
            return mSleepEntryResult;
        }

        @Override
        public boolean enterHibernation() {
            return simulateSleep();
        }

        private boolean simulateSleep() {
            mSleepWait.release();
            try {
                mSleepExitWait.tryAcquire(WAIT_TIMEOUT_MS , TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
            }
            return mSleepEntryResult;
        }

        public void waitForSleepEntryAndWakeup(long timeoutMs) throws Exception {
            waitForSemaphore(mSleepWait, timeoutMs);
            mSleepExitWait.release();
        }

        @Override
        public void scheduleActionForBootCompleted(Runnable action, Duration delay,
                Duration delayRange) {}

        @Override
        public boolean isWakeupCausedByTimer() {
            synchronized (sLock) {
                Log.i(TAG, "isWakeupCausedByTimer:" + mWakeupCausedByTimer);
                return mWakeupCausedByTimer;
            }
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

        @Override
        public boolean isSystemSupportingHibernation() {
            return true;
        }

        public void setSleepEntryResult(boolean sleepEntryResult) {
            mSleepEntryResult = sleepEntryResult;
        }

        public void setSimulateSleep(boolean simulateSleep) {
            mSimulateSleep = simulateSleep;
        }

        public void waitForDeepSleepEntry(long waitTimeoutMs) throws InterruptedException {
            waitForSemaphore(mSleepWait, waitTimeoutMs);
        }
    }

    private static final class MockWakeLockInterface implements WakeLockInterface {

        @Override
        public void releaseAllWakeLocks(int displayId) {}

        @Override
        public void switchToPartialWakeLock(int displayId) {}

        @Override
        public void switchToFullWakeLock(int displayId) {}
    }

    private static final class MockIOInterface implements IOInterface {
        private TemporaryDirectory mFilesDir;

        @Override
        public File getSystemCarDir() {
            if (mFilesDir == null) {
                try {
                    mFilesDir = new TemporaryDirectory(TAG);
                } catch (IOException e) {
                    Log.e(TAG, "failed to create temporary directory", e);
                    fail("failed to create temporary directory. exception was: " + e);
                }
            }
            return mFilesDir.getDirectory();
        }

        public void tearDown() {
            if (mFilesDir != null) {
                try {
                    mFilesDir.close();
                } catch (Exception e) {
                    Log.w(TAG, "could not remove temporary directory", e);
                }
            }
        }
    }

    private static class PowerSignalListener implements MockedPowerHalService.SignalListener {
        private final SparseArray<Semaphore> mSemaphores;

        private PowerSignalListener() {
            mSemaphores = new SparseArray<>();
        }

        public void addEventListener(int eventId) {
            mSemaphores.put(eventId, new Semaphore(0));
        }

        public void waitFor(int signal, long timeoutMs) throws Exception {
            Semaphore semaphore = mSemaphores.get(signal);
            if (semaphore == null) {
                throw new IllegalArgumentException("no semaphore registered for event = " + signal);
            }
            waitForSemaphore(semaphore, timeoutMs);
        }

        @Override
        public void sendingSignal(int signal) {
            Semaphore semaphore = mSemaphores.get(signal);
            if (semaphore == null) {
                return;
            }
            semaphore.release();
        }
    }

    static final class FakeCarPowerPolicyDaemon extends ICarPowerPolicySystemNotification.Stub {
        private String mLastNotifiedPolicyId;
        private String mLastDefinedPolicyId;

        @Override
        public PolicyState notifyCarServiceReady() {
            // do nothing
            return null;
        }

        @Override
        public void notifyPowerPolicyChange(String policyId, boolean force) {
            mLastNotifiedPolicyId = policyId;
        }

        @Override
        public void notifyPowerPolicyDefinition(String policyId, String[] enabledComponents,
                String[] disabledComponents) {
            mLastDefinedPolicyId = policyId;
        }

        public String getLastNotifiedPolicyId() {
            return mLastNotifiedPolicyId;
        }

        public String getLastDefinedPolicyId() {
            return mLastDefinedPolicyId;
        }

        @Override
        public String getInterfaceHash() {
            return ICarPowerPolicySystemNotification.HASH;
        }

        @Override
        public int getInterfaceVersion() {
            return ICarPowerPolicySystemNotification.VERSION;
        }
    }

    private static final class MockedPowerPolicyListener extends ICarPowerPolicyListener.Stub {
        private final Object mLock = new Object();
        private CarPowerPolicy mCurrentPowerPolicy;

        @Override
        public void onPolicyChanged(CarPowerPolicy appliedPolicy,
                CarPowerPolicy accumulatedPolicy) {
            synchronized (mLock) {
                mCurrentPowerPolicy = accumulatedPolicy;
            }
        }

        @Nullable
        public CarPowerPolicy getCurrentPowerPolicy() throws Exception {
            synchronized (mLock) {
                return mCurrentPowerPolicy;
            }
        }
    }
    private final class MockedPowerStateListener extends ICarPowerStateListener.Stub {
        private final String mOnPolicyId;
        private final AtomicBoolean mIsPolicyApplicationSuccessful;

        MockedPowerStateListener(String onPolicyId,
                AtomicBoolean isPolicyApplicationSuccessful) {
            mOnPolicyId = onPolicyId;
            mIsPolicyApplicationSuccessful = isPolicyApplicationSuccessful;
        }

        @Override
        public void onStateChanged(int state, long expirationTimeMs) {
            // when ON state is received, request to change power policy
            if (state == CarPowerManager.STATE_ON) {
                HandlerThread handlerThread = new HandlerThread(TAG + ":onStateChanged");
                handlerThread.start();
                Handler handler = new Handler(handlerThread.getLooper());
                boolean policyRequestSent = handler.post(() -> {
                    try {
                        mService.applyPowerPolicy(mOnPolicyId);
                        // if flag is not set, exception was caught
                        mIsPolicyApplicationSuccessful.set(true);
                    } catch (Exception e) {
                        Log.e(TAG, "Could not apply policy " + mOnPolicyId + " for on state; "
                                + " exception: " + Log.getStackTraceString(e));
                    }
                });
                if (!policyRequestSent) {
                    Log.w(TAG, "Could not send policy request for " + mOnPolicyId
                            + " at on state");
                }
                boolean quitSafely = handlerThread.quitSafely();
                if (!quitSafely) {
                    Log.w(TAG, "HandlerThread for MockedPowerStateListener.onStateChanged did "
                            + "not quit safely");
                }
            }
        }
    }
}
