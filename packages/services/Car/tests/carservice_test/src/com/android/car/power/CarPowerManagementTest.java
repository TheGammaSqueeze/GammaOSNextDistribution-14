/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.car.Car;
import android.car.feature.Flags;
import android.car.hardware.power.CarPowerManager;
import android.car.hardware.power.CarPowerPolicy;
import android.car.hardware.power.CarPowerPolicyFilter;
import android.car.hardware.power.ICarPowerPolicyListener;
import android.car.hardware.power.ICarPowerStateListener;
import android.car.hardware.power.PowerComponent;
import android.car.hardware.property.VehicleHalStatusCode;
import android.car.test.mocks.JavaMockitoHelper;
import android.frameworks.automotive.powerpolicy.internal.ICarPowerPolicySystemNotification;
import android.hardware.automotive.vehicle.VehicleApPowerStateConfigFlag;
import android.hardware.automotive.vehicle.VehicleApPowerStateReport;
import android.hardware.automotive.vehicle.VehicleApPowerStateReq;
import android.hardware.automotive.vehicle.VehicleApPowerStateReqIndex;
import android.hardware.automotive.vehicle.VehicleApPowerStateShutdownParam;
import android.hardware.automotive.vehicle.VehiclePropValue;
import android.hardware.automotive.vehicle.VehicleProperty;
import android.hardware.automotive.vehicle.VehiclePropertyAccess;
import android.hardware.automotive.vehicle.VehiclePropertyChangeMode;
import android.os.Handler;
import android.os.Looper;
import android.os.ServiceSpecificException;
import android.os.SystemClock;
import android.util.SparseBooleanArray;
import android.view.Display;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;

import com.android.car.MockedCarTestBase;
import com.android.car.hal.test.AidlMockedVehicleHal.VehicleHalPropertyHandler;
import com.android.car.hal.test.AidlVehiclePropValueBuilder;
import com.android.car.systeminterface.DisplayInterface;
import com.android.car.systeminterface.SystemInterface;
import com.android.car.systeminterface.SystemStateInterface;
import com.android.car.user.CarUserService;
import com.android.internal.annotations.GuardedBy;

import com.google.android.collect.Lists;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class CarPowerManagementTest extends MockedCarTestBase {

    private static final int STATE_POLLING_INTERVAL_MS = 1; // Milliseconds
    private static final int STATE_TRANSITION_MAX_WAIT_MS = 5 * STATE_POLLING_INTERVAL_MS;
    private static final int TEST_SHUTDOWN_TIMEOUT_MS = 100 * STATE_POLLING_INTERVAL_MS;
    private static final int POLICY_APPLICATION_TIMEOUT_MS = 10_000;
    private static final String POWER_POLICY_S2R = "system_power_policy_suspend_prep";

    private final PowerStatePropertyHandler mPowerStateHandler = new PowerStatePropertyHandler();
    private final MockDisplayInterface mMockDisplayInterface = new MockDisplayInterface();
    private final SystemStateInterfaceForSuspend mMockSystemStateInterface =
            new SystemStateInterfaceForSuspend();

    @Override
    protected SystemInterface.Builder getSystemInterfaceBuilder() {
        SystemInterface.Builder builder = super.getSystemInterfaceBuilder();
        return builder.withDisplayInterface(mMockDisplayInterface)
                .withSystemStateInterface(mMockSystemStateInterface);
    }

    @Override
    protected void configureMockedHal() {
        addAidlProperty(VehicleProperty.AP_POWER_STATE_REQ, mPowerStateHandler)
                .setConfigArray(Lists.newArrayList(
                        VehicleApPowerStateConfigFlag.ENABLE_DEEP_SLEEP_FLAG
                                | VehicleApPowerStateConfigFlag.ENABLE_HIBERNATION_FLAG))
                .setChangeMode(VehiclePropertyChangeMode.ON_CHANGE).build();
        addAidlProperty(VehicleProperty.AP_POWER_STATE_REPORT, mPowerStateHandler)
                .setAccess(VehiclePropertyAccess.WRITE)
                .setChangeMode(VehiclePropertyChangeMode.ON_CHANGE).build();
    }

    @Override
    protected void configureResourceOverrides(MockResources resources) {
        super.configureResourceOverrides(resources);
        resources.overrideResource(com.android.car.R.integer.config_maxSuspendWaitDuration, 10000);
    }

    /**********************************************************************************************
     * Test immediate shutdown
     **********************************************************************************************/
    @Test
    public void testImmediateShutdownFromWaitForVhal() throws Exception {
        assertWaitForVhal();
        mPowerStateHandler.sendStateAndCheckResponse(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.SHUTDOWN_IMMEDIATELY,
                VehicleApPowerStateReport.SHUTDOWN_START);
    }

    @Test
    public void testImmediateShutdownFromWaitForVhal_ErrorCodeFromVhal() throws Exception {
        // The exceptions from VHAL should be handled in PowerHalService and not propagated.

        assertWaitForVhal();

        mPowerStateHandler.setStatus(VehicleHalStatusCode.STATUS_TRY_AGAIN);

        mPowerStateHandler.sendStateAndExpectNoResponse(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.SHUTDOWN_IMMEDIATELY);

        mPowerStateHandler.setStatus(VehicleHalStatusCode.STATUS_ACCESS_DENIED);

        mPowerStateHandler.sendStateAndExpectNoResponse(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.SHUTDOWN_IMMEDIATELY);

        mPowerStateHandler.setStatus(VehicleHalStatusCode.STATUS_NOT_AVAILABLE);

        mPowerStateHandler.sendStateAndExpectNoResponse(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.SHUTDOWN_IMMEDIATELY);

        mPowerStateHandler.setStatus(VehicleHalStatusCode.STATUS_INTERNAL_ERROR);

        mPowerStateHandler.sendStateAndExpectNoResponse(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.SHUTDOWN_IMMEDIATELY);

        // Clear status code.
        mPowerStateHandler.setStatus(VehicleHalStatusCode.STATUS_OK);
    }

    @Test
    public void testImmediateShutdownFromOn() throws Exception {
        assertWaitForVhal();
        // Transition to ON state first
        mPowerStateHandler.sendStateAndCheckResponse(
                VehicleApPowerStateReq.ON,
                /* param= */ 0,
                VehicleApPowerStateReport.ON);
        // Send immediate shutdown from ON state
        mPowerStateHandler.sendStateAndCheckResponse(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.SHUTDOWN_IMMEDIATELY,
                VehicleApPowerStateReport.SHUTDOWN_START);
    }

    @Test
    public void testImmediateShutdownFromShutdownPrepare() throws Exception {
        assertWaitForVhal();
        registerListenerToFakeGarageMode();

        // Put device into SHUTDOWN_PREPARE
        mPowerStateHandler.sendStateAndCheckResponse(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP,
                VehicleApPowerStateReport.SHUTDOWN_PREPARE);
        // Initiate shutdown immediately while in SHUTDOWN_PREPARE
        mPowerStateHandler.sendStateAndCheckResponse(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.SHUTDOWN_IMMEDIATELY,
                VehicleApPowerStateReport.SHUTDOWN_START);
    }

    @Test
    public void testDeepSleepEntryAfterGarageMode() throws Exception {
        assertWaitForVhal();

        registerListenerToFakeGarageMode();

        // Put device into SHUTDOWN_PREPARE
        mPowerStateHandler.sendStateAndCheckResponse(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP,
                VehicleApPowerStateReport.SHUTDOWN_PREPARE);

        mPowerStateHandler.waitForStateSetAndGetAll(DEFAULT_WAIT_TIMEOUT_MS,
                VehicleApPowerStateReport.DEEP_SLEEP_ENTRY);
    }

    /**********************************************************************************************
     * Test cancelling of shutdown.
     **********************************************************************************************/
    @Test
    public void testCancelShutdownFromShutdownPrepare() throws Exception {
        assertWaitForVhal();
        mPowerStateHandler.sendStateAndCheckResponse(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP,
                VehicleApPowerStateReport.SHUTDOWN_PREPARE);
        // Shutdown may only be cancelled from SHUTDOWN_PREPARE
        mPowerStateHandler.sendStateAndCheckResponse(
                VehicleApPowerStateReq.CANCEL_SHUTDOWN,
                /* param= */  0,
                VehicleApPowerStateReport.SHUTDOWN_CANCELLED);
    }

    @Test
    public void testCancelShutdownFromWaitForFinish() throws Exception {
        assertWaitForVhal();
        mPowerStateHandler.sendStateAndCheckResponse(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP,
                VehicleApPowerStateReport.DEEP_SLEEP_ENTRY);
        // After DEEP_SLEEP_ENTRY, we're in WAIT_FOR_FINISH
        mPowerStateHandler.sendStateAndCheckResponse(
                VehicleApPowerStateReq.CANCEL_SHUTDOWN,
                /* param= */ 0,
                VehicleApPowerStateReport.SHUTDOWN_CANCELLED);
    }

    /**********************************************************************************************
     * Test for invalid state transtions
     **********************************************************************************************/
    @Test
    public void testInvalidTransitionsFromWaitForVhal() throws Exception {
        assertWaitForVhal();
        mPowerStateHandler.sendStateAndExpectNoResponse(VehicleApPowerStateReq.CANCEL_SHUTDOWN, 0);
        mPowerStateHandler.sendStateAndExpectNoResponse(VehicleApPowerStateReq.FINISHED, 0);
    }

    @Test
    public void testInvalidTransitionsFromOn() throws Exception {
        assertWaitForVhal();
        // Transition to ON state first
        mPowerStateHandler.sendStateAndCheckResponse(
                VehicleApPowerStateReq.ON,
                /* param= */ 0,
                VehicleApPowerStateReport.ON);
        mPowerStateHandler.sendStateAndExpectNoResponse(VehicleApPowerStateReq.CANCEL_SHUTDOWN, 0);
        mPowerStateHandler.sendStateAndExpectNoResponse(VehicleApPowerStateReq.FINISHED, 0);
    }

    @Test
    public void testInvalidTransitionsFromPrepareShutdown() throws Exception {
        assertWaitForVhal();
        registerListenerToFakeGarageMode();

        // Transition to SHUTDOWN_PREPARE first
        mPowerStateHandler.sendStateAndCheckResponse(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP,
                VehicleApPowerStateReport.SHUTDOWN_PREPARE);
        // Cannot go back to ON state from here
        mPowerStateHandler.sendStateAndExpectNoResponse(VehicleApPowerStateReq.ON, 0);
        // SHUTDOWN_PREPARE should not generate state transitions unless it's an IMMEDIATE_SHUTDOWN
        mPowerStateHandler.sendStateAndExpectNoResponse(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP);
        mPowerStateHandler.sendStateAndExpectNoResponse(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.SHUTDOWN_ONLY);
        // Test the FINISH message last, in case SHUTDOWN_PREPARE finishes early and this test
        // should be failing.
        mPowerStateHandler.sendStateAndExpectNoResponse(VehicleApPowerStateReq.FINISHED, 0);
    }

    @Test
    public void testInvalidTransitionsFromWaitForFinish() throws Exception {
        assertWaitForVhal();
        mPowerStateHandler.sendStateAndCheckResponse(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP,
                VehicleApPowerStateReport.DEEP_SLEEP_ENTRY);
        mPowerStateHandler.sendStateAndExpectNoResponse(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP);
        mPowerStateHandler.sendStateAndExpectNoResponse(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.SHUTDOWN_ONLY);
        // TODO:  This state may be allowed in the future, if we decide it's necessary
        mPowerStateHandler.sendStateAndExpectNoResponse(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.SHUTDOWN_IMMEDIATELY);
    }

    @Test
    public void testInvalidTransitionsFromWaitForFinish2() throws Exception {
        assertWaitForVhal();
        mPowerStateHandler.sendStateAndCheckResponse(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.SHUTDOWN_ONLY,
                VehicleApPowerStateReport.SHUTDOWN_START);
        mPowerStateHandler.sendStateAndExpectNoResponse(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP);
        mPowerStateHandler.sendStateAndExpectNoResponse(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.SHUTDOWN_ONLY);
        // TODO:  This state may be allowed in the future, if we decide it's necessary
        mPowerStateHandler.sendStateAndExpectNoResponse(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.SHUTDOWN_IMMEDIATELY);
    }

    /**********************************************************************************************
     * Test sleep entry
     **********************************************************************************************/
    // This test also verifies the display state as the device goes in and out of suspend.
    @Test
    public void testSleepEntry() throws Exception {
        PowerPolicyListener powerPolicyListener = new PowerPolicyListener(POWER_POLICY_S2R);
        CarPowerPolicyFilter filter = new CarPowerPolicyFilter.Builder()
                .setComponents(PowerComponent.WIFI).build();
        CarPowerManagementService cpms = getCarPowerManagementService();
        cpms.addPowerPolicyListener(filter, powerPolicyListener);

        assertWaitForVhal();
        mMockDisplayInterface.waitForAllDisplayState(false);
        mPowerStateHandler.sendStateAndCheckResponse(
                VehicleApPowerStateReq.ON,
                /* param= */ 0,
                VehicleApPowerStateReport.ON);
        mMockDisplayInterface.waitForAllDisplayState(true);
        mPowerStateHandler.sendPowerState(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP);
        // The state machine should go to SHUTDOWN_PREPARE, but may
        // quickly transition to SHUTDOWN_POSTPONE. Report success
        // if we got to SHUTDOWN_PREPARE, even if we're not there now.
        assertResponseTransient(VehicleApPowerStateReport.SHUTDOWN_PREPARE, 0, true);

        mMockDisplayInterface.waitForAllDisplayState(false);
        assertResponse(VehicleApPowerStateReport.DEEP_SLEEP_ENTRY, 0, false);
        mMockDisplayInterface.waitForAllDisplayState(false);
        mPowerStateHandler.sendPowerState(VehicleApPowerStateReq.FINISHED, 0);
        powerPolicyListener.waitForPowerPolicy();
        assertResponse(VehicleApPowerStateReport.DEEP_SLEEP_EXIT, 0, true);
        mMockDisplayInterface.waitForAllDisplayState(false);

        cpms.removePowerPolicyListener(powerPolicyListener);
    }

    @Test
    public void testSleepImmediateEntry() throws Exception {
        assertWaitForVhal();
        mMockDisplayInterface.waitForAllDisplayState(false);
        mPowerStateHandler.sendStateAndCheckResponse(
                VehicleApPowerStateReq.ON,
                /* param= */ 0,
                VehicleApPowerStateReport.ON);
        mMockDisplayInterface.waitForAllDisplayState(true);
        mPowerStateHandler.sendPowerState(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.SLEEP_IMMEDIATELY);
        assertResponseTransient(VehicleApPowerStateReport.DEEP_SLEEP_ENTRY, 0, true);
    }

    @Test
    public void testInvalidPowerStateEvent() throws Exception {
        assertWaitForVhal();

        // No param in the event, should be ignored.
        getAidlMockedVehicleHal().injectEvent(
                    AidlVehiclePropValueBuilder.newBuilder(VehicleProperty.AP_POWER_STATE_REQ)
                            .setTimestamp(SystemClock.elapsedRealtimeNanos())
                            .addIntValues(0)
                            .build());

        assertEquals(mPowerStateHandler.getSetWaitSemaphore().availablePermits(), 0);
    }

    @Test
    public void testUnallowedPowerStateEventAtSuspend() throws Exception {
        PowerPolicyListener powerPolicyListener = new PowerPolicyListener(POWER_POLICY_S2R);
        CarPowerPolicyFilter filter = new CarPowerPolicyFilter.Builder()
                .setComponents(PowerComponent.WIFI).build();
        CarPowerManagementService cpms = getCarPowerManagementService();
        cpms.addPowerPolicyListener(filter, powerPolicyListener);

        assertWaitForVhal();

        mPowerStateHandler.sendStateAndCheckResponse(
                VehicleApPowerStateReq.ON,
                /* param= */ 0,
                VehicleApPowerStateReport.ON);
        mMockDisplayInterface.waitForAllDisplayState(true);
        // Makes the suspend unsuccessful.
        mMockSystemStateInterface.setExpectedSuspendStatus(/* expectedStatus= */ false);
        mPowerStateHandler.sendPowerState(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.SLEEP_IMMEDIATELY);

        assertResponse(VehicleApPowerStateReport.DEEP_SLEEP_ENTRY, /* expectedParam= */ 0,
                /* checkParam= */ false);

        mPowerStateHandler.sendPowerState(VehicleApPowerStateReq.FINISHED, /* param= */ 0);
        powerPolicyListener.waitForPowerPolicy();

        // Sends unallowed power state request.
        mPowerStateHandler.sendPowerState(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.SLEEP_IMMEDIATELY);

        // Makes the suspend successful.
        mMockSystemStateInterface.setExpectedSuspendStatus(/* expectedStatus= */ true);

        assertResponseTransient(VehicleApPowerStateReport.DEEP_SLEEP_EXIT, /* expectedParam= */ 0,
                /* checkParam= */ true);

        cpms.removePowerPolicyListener(powerPolicyListener);
    }

    @Test
    public void testShutdownPostponeDuringHibernationEnter() throws Exception {
        testShutdownPostponeWhileListenerPendingInState(CarPowerManager.STATE_HIBERNATION_ENTER,
                VehicleApPowerStateShutdownParam.CAN_HIBERNATE,
                VehicleApPowerStateReport.HIBERNATION_ENTRY);
    }

    @Test
    public void testShutdownPostponeDuringShutdownEnter() throws Exception {
        testShutdownPostponeWhileListenerPendingInState(CarPowerManager.STATE_SHUTDOWN_ENTER,
                VehicleApPowerStateShutdownParam.SHUTDOWN_ONLY,
                VehicleApPowerStateReport.SHUTDOWN_START);
    }

    @Test
    public void testShutdownPostponeDuringSuspendEnter() throws Exception {
        testShutdownPostponeWhileListenerPendingInState(CarPowerManager.STATE_SUSPEND_ENTER,
                VehicleApPowerStateShutdownParam.CAN_SLEEP,
                VehicleApPowerStateReport.DEEP_SLEEP_ENTRY);
    }

    @Test
    public void testShutdownPostponeDuringPreShutdownPrepare() throws Exception {
        testShutdownPostponeWhileListenerPendingInState(CarPowerManager.STATE_PRE_SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP,
                VehicleApPowerStateReport.DEEP_SLEEP_ENTRY);
    }

    @Test
    public void testShutdownPostponeDuringPreShutdownPrepareWithImmediately() throws Exception {
        testShutdownPostponeWhileListenerPendingInState(CarPowerManager.STATE_PRE_SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.SHUTDOWN_IMMEDIATELY,
                VehicleApPowerStateReport.SHUTDOWN_START);
    }

    @Test
    public void testSleepShutdownFromPreShutdownPrepare() throws Exception {
        assertWaitForVhal();

        registerListenerToFakeGarageMode();

        AtomicBoolean errorOccurred = new AtomicBoolean(false);

        // Semaphore to signal receive of PRE_SHUTDOWN_PREPARE
        Semaphore eventWaitSemaphore = new Semaphore(0);
        // Semaphore to signal completion of PRE_SHUTDOWN_PREPARE
        Semaphore completionSemaphore = new Semaphore(0);

        CarPowerManagementService cpms = getCarPowerManagementService();
        ICarPowerStateListener listener = new ICarPowerStateListener.Stub() {
            @Override
            public void onStateChanged(int state, long expirationTimeMs) {
                if (!CarPowerManagementService.isCompletionAllowed(state)) {
                    return;
                }

                Handler handler = new Handler(Looper.getMainLooper());
                ICarPowerStateListener cpmsListener = this;
                Runnable completionRunnable = () -> cpms.completeHandlingPowerStateChange(state,
                        cpmsListener);

                if (state == CarPowerManager.STATE_PRE_SHUTDOWN_PREPARE) {
                    new Thread(() -> {
                        try {
                            eventWaitSemaphore.release();
                            if (completionSemaphore.tryAcquire(DEFAULT_WAIT_TIMEOUT_MS,
                                    TimeUnit.MILLISECONDS)) {
                                handler.post(completionRunnable);
                            } else {
                                errorOccurred.set(true);
                            }
                        } catch (InterruptedException e) {
                            errorOccurred.set(true);
                        }
                        completionSemaphore.drainPermits();
                    }).start();
                } else {
                    cpms.completeHandlingPowerStateChange(state, this);
                }
            }
        };
        cpms.registerInternalListener(listener);

        mPowerStateHandler.sendPowerState(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP);

        assertThat(eventWaitSemaphore.tryAcquire(DEFAULT_WAIT_TIMEOUT_MS,
                TimeUnit.MILLISECONDS)).isTrue();

        mPowerStateHandler.sendPowerState(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.SHUTDOWN_IMMEDIATELY);

        completionSemaphore.release();

        mPowerStateHandler.waitForStateSetAndGetAll(DEFAULT_WAIT_TIMEOUT_MS,
                VehicleApPowerStateReport.SHUTDOWN_START);
        assertThat(errorOccurred.get()).isFalse();
    }

    @Test
    public void testShutdownImmediatelyFromPreShutdownPrepare() throws Exception {
        assertWaitForVhal();

        registerListenerToFakeGarageMode();

        AtomicBoolean errorOccurred = new AtomicBoolean(false);

        // Semaphore to signal receive of PRE_SHUTDOWN_PREPARE
        Semaphore eventWaitSemaphore = new Semaphore(0);
        // Semaphore to signal completion of PRE_SHUTDOWN_PREPARE
        Semaphore completionSemaphore = new Semaphore(0);

        CarPowerManagementService cpms = getCarPowerManagementService();
        ICarPowerStateListener listener = new ICarPowerStateListener.Stub() {
            @Override
            public void onStateChanged(int state, long expirationTimeMs) {
                if (!CarPowerManagementService.isCompletionAllowed(state)) {
                    return;
                }

                ICarPowerStateListener cpmsListener = this;

                if (state == CarPowerManager.STATE_PRE_SHUTDOWN_PREPARE) {
                    new Thread(() -> {
                        try {
                            eventWaitSemaphore.release();
                            if (completionSemaphore.tryAcquire(DEFAULT_WAIT_TIMEOUT_MS,
                                    TimeUnit.MILLISECONDS)) {
                                cpms.completeHandlingPowerStateChange(state, cpmsListener);
                            } else {
                                errorOccurred.set(true);
                            }
                        } catch (InterruptedException e) {
                            errorOccurred.set(true);
                        }
                        completionSemaphore.drainPermits();
                    }).start();
                } else {
                    cpms.completeHandlingPowerStateChange(state, this);
                }
            }
        };
        cpms.registerInternalListener(listener);

        mPowerStateHandler.sendPowerState(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.CAN_SLEEP);

        assertThat(eventWaitSemaphore.tryAcquire(DEFAULT_WAIT_TIMEOUT_MS,
                TimeUnit.MILLISECONDS)).isTrue();

        mPowerStateHandler.sendPowerState(
                VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                VehicleApPowerStateShutdownParam.SHUTDOWN_IMMEDIATELY);

        completionSemaphore.release();

        mPowerStateHandler.waitForStateSetAndGetAll(DEFAULT_WAIT_TIMEOUT_MS,
                VehicleApPowerStateReport.SHUTDOWN_START);
        assertThat(errorOccurred.get()).isFalse();
    }

    @Test
    public void testApplyPowerPolicy_vhalPropertyUpdated() throws Exception {
        String policyIdWifiOff = "policy_id_wifi_off";
        String policyIdWifiOn = "policy_id_wifi_on";
        // This is how the other test cases in this file interact w/CPMS, but would it make sense
        // to just define mService = (CarPowerManagementService) getCarService(Car.POWER_SERVICE)?
        CarPowerManagementService cpms = getCarPowerManagementService();
        cpms.definePowerPolicy(policyIdWifiOff, new String[]{}, new String[]{"WIFI"});
        cpms.definePowerPolicy(policyIdWifiOn, new String[]{"WIFI"}, new String[]{});
        PowerPolicyListener wifiOffListener = new PowerPolicyListener(policyIdWifiOff);
        PowerPolicyListener wifiOnListener = new PowerPolicyListener(policyIdWifiOn);
        CarPowerPolicyFilter filterWifi = new CarPowerPolicyFilter.Builder()
                .setComponents(PowerComponent.WIFI).build();
        cpms.addPowerPolicyListener(filterWifi, wifiOffListener);
        cpms.addPowerPolicyListener(filterWifi, wifiOnListener);

        cpms.applyPowerPolicy(policyIdWifiOn);
        wifiOnListener.waitForPowerPolicy();
        cpms.applyPowerPolicy(policyIdWifiOff);
        wifiOffListener.waitForPowerPolicy();

        if (!Flags.carPowerPolicyRefactoring()) {
            Mockito.verify((ICarPowerPolicySystemNotification) getMockedPowerPolicyDaemon())
                    .notifyPowerPolicyChange(policyIdWifiOff, /* force= */ false);
        }
    }

    private void testShutdownPostponeWhileListenerPendingInState(final int targetState,
            int stateRequestParam, int finalPowerState) throws Exception {
        assertWaitForVhal();

        Semaphore notificationSem = new Semaphore(0);

        AtomicBoolean errorOccurred = new AtomicBoolean(false);
        CarPowerManagementService cpms = getCarPowerManagementService();

        ICarPowerStateListener listener = new ICarPowerStateListener.Stub() {
            @Override
            public void onStateChanged(int state, long expirationTimeMs) {
                if (!CarPowerManagementService.isCompletionAllowed(state)) {
                    return;
                }

                ICarPowerStateListener cpmsListener = this;
                if (state == targetState) {
                    new Thread(() -> {
                        try {
                            if (!notificationSem.tryAcquire(DEFAULT_WAIT_TIMEOUT_MS,
                                    TimeUnit.MILLISECONDS)) {
                                errorOccurred.set(true);
                            }
                            cpms.completeHandlingPowerStateChange(state, cpmsListener);
                        } catch (InterruptedException e) {
                            errorOccurred.set(true);
                        }
                    }).start();
                } else {
                    cpms.completeHandlingPowerStateChange(state, this);
                }
            }
        };
        cpms.registerInternalListener(listener);

        //start shutown prepare
        mPowerStateHandler.sendPowerState(VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                stateRequestParam);
        // wait for SHUTDOWN_POSTPONE
        mPowerStateHandler.waitForStateSetAndGetAll(DEFAULT_WAIT_TIMEOUT_MS,
                VehicleApPowerStateReport.SHUTDOWN_POSTPONE);
        // complete listener
        notificationSem.release();

        mPowerStateHandler.waitForStateSetAndGetAll(DEFAULT_WAIT_TIMEOUT_MS, finalPowerState);
        assertThat(errorOccurred.get()).isFalse();
    }

    // Check that 'expectedState' was reached and is the current state.
    private void assertResponse(int expectedState, int expectedParam, boolean checkParam)
            throws Exception {
        LinkedList<int[]> setEvents = mPowerStateHandler.waitForStateSetAndGetAll(
                DEFAULT_WAIT_TIMEOUT_MS, expectedState);
        int[] last = setEvents.getLast();
        assertEquals(expectedState, last[0]);
        if (checkParam) {
            assertEquals(expectedParam, last[1]);
        }
    }

    // Check that 'expectedState' was reached. (But it's OK if it is not still current.)
    private void assertResponseTransient(int expectedState, int expectedParam, boolean checkParam)
            throws Exception {
        LinkedList<int[]> setEvents = mPowerStateHandler.waitForStateSetAndGetAll(
                DEFAULT_WAIT_TIMEOUT_MS, expectedState);
        for (int[] aState : setEvents) {
            if (expectedState != aState[0]) continue;
            if (checkParam) {
                assertEquals(expectedParam, aState[1]);
            }
            return; // Success
        }
        fail("Did not find expected state: " + expectedState);
    }

    private void assertWaitForVhal() throws Exception {
        mPowerStateHandler.waitForSubscription(DEFAULT_WAIT_TIMEOUT_MS);
        LinkedList<int[]> setEvents = mPowerStateHandler.waitForStateSetAndGetAll(
                DEFAULT_WAIT_TIMEOUT_MS, VehicleApPowerStateReport.WAIT_FOR_VHAL);
        int[] first = setEvents.getFirst();
        assertEquals(VehicleApPowerStateReport.WAIT_FOR_VHAL, first[0]);
        assertEquals(0, first[1]);
    }

    private void registerListenerToFakeGarageMode() {
        CarPowerManagementService cpms = getCarPowerManagementService();
        ICarPowerStateListener listener = new ICarPowerStateListener.Stub() {
            @Override
            public void onStateChanged(int state, long expirationTimeMs) {
                if (CarPowerManagementService.isCompletionAllowed(state)) {
                    // Do not call finished() to stay in shutdown prepare, when Garage Mode is
                    // supposed to be running.
                    if (state == CarPowerManager.STATE_SHUTDOWN_PREPARE
                            && !cpms.garageModeShouldExitImmediately()) {
                        return;
                    }
                    cpms.completeHandlingPowerStateChange(state, this);
                }
            }
        };
        cpms.registerInternalListener(listener);
    }

    private CarPowerManagementService getCarPowerManagementService() {
        CarPowerManagementService cpms =
                (CarPowerManagementService) getCarService(Car.POWER_SERVICE);
        if (Flags.carPowerPolicyRefactoring()) {
            cpms.initializePowerPolicy();
        }
        return cpms;
    }

    private static final class MockDisplayInterface implements DisplayInterface {
        private final Object mLock = new Object();
        @GuardedBy("mLock")
        private final SparseBooleanArray mDisplayOn = new SparseBooleanArray();
        private final Semaphore mDisplayStateWait = new Semaphore(0);
        private CarPowerManagementService mCarPowerManagementService;

        @Override
        public void init(CarPowerManagementService carPowerManagementService,
                CarUserService carUserService) {
            mCarPowerManagementService = carPowerManagementService;
            synchronized (mLock) {
                mDisplayOn.put(Display.DEFAULT_DISPLAY, true);
            }
        }

        @Override
        public void setDisplayBrightness(int brightness) {}

        @Override
        public void setDisplayBrightness(int displayId, int brightness) {}

        @Override
        public void setDisplayState(int displayId, boolean on) {
            synchronized (mLock) {
                mDisplayOn.put(displayId, on);
            }
            mDisplayStateWait.release();
        }

        @Override
        public void setAllDisplayState(boolean on) {
            synchronized (mLock) {
                for (int i = 0; i < mDisplayOn.size(); i++) {
                    int displayId = mDisplayOn.keyAt(i);
                    setDisplayState(displayId, on);
                }
            }
        }

        boolean waitForDisplayState(int displayId, boolean expectedState) throws Exception {
            boolean enabled = false;
            synchronized (mLock) {
                enabled = mDisplayOn.get(displayId);
            }
            if (expectedState == enabled) {
                return true;
            }
            mDisplayStateWait.tryAcquire(MockedCarTestBase.SHORT_WAIT_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS);
            return expectedState == enabled;
        }

        void waitForAllDisplayState(boolean expectedState) throws Exception {
            SparseBooleanArray displayOn;
            synchronized (mLock) {
                displayOn = mDisplayOn.clone();
            }
            for (int i = 0; i < displayOn.size(); i++) {
                int displayId = displayOn.keyAt(i);
                waitForDisplayState(displayId, expectedState);
            }
        }

        @Override
        public void startDisplayStateMonitoring() {
            // To reduce test duration, decrease the polling interval and the
            // time to wait for a shutdown
            mCarPowerManagementService.setShutdownTimersForTest(STATE_POLLING_INTERVAL_MS,
                    TEST_SHUTDOWN_TIMEOUT_MS);
        }

        @Override
        public void stopDisplayStateMonitoring() {}

        @Override
        public void refreshDisplayBrightness() {}

        @Override
        public void refreshDisplayBrightness(int displayId) {}

        @Override
        public boolean isAnyDisplayEnabled() {
            synchronized (mLock) {
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
            synchronized (mLock) {
                return mDisplayOn.get(displayId);
            }
        }
    }

    private class PowerStatePropertyHandler implements VehicleHalPropertyHandler {

        private int mPowerState = VehicleApPowerStateReq.ON;
        private int mPowerParam = 0;
        private int mStatus = VehicleHalStatusCode.STATUS_OK;

        private final Semaphore mSubscriptionWaitSemaphore = new Semaphore(0);
        private final Semaphore mSetWaitSemaphore = new Semaphore(0);
        private final LinkedList<int[]> mSetStates = new LinkedList<>();

        public Semaphore getSetWaitSemaphore() {
            return mSetWaitSemaphore;
        }

        public void setStatus(int status) {
            mStatus = status;
        }

        @Override
        public void onPropertySet(VehiclePropValue value) {
            if (mStatus != VehicleHalStatusCode.STATUS_OK) {
                throw new ServiceSpecificException(mStatus);
            }
            int[] v = value.value.int32Values;
            synchronized (this) {
                mSetStates.add(new int[] {
                        v[VehicleApPowerStateReqIndex.STATE],
                        v[VehicleApPowerStateReqIndex.ADDITIONAL]
                });
            }
            mSetWaitSemaphore.release();
        }

        @Override
        public synchronized VehiclePropValue onPropertyGet(VehiclePropValue value) {
            if (mStatus != VehicleHalStatusCode.STATUS_OK) {
                throw new ServiceSpecificException(mStatus);
            }
            return AidlVehiclePropValueBuilder.newBuilder(VehicleProperty.AP_POWER_STATE_REQ)
                    .setTimestamp(SystemClock.elapsedRealtimeNanos())
                    .addIntValues(mPowerState, mPowerParam)
                    .build();
        }

        @Override
        public void onPropertySubscribe(int property, float sampleRate) {
            mSubscriptionWaitSemaphore.release();
        }

        @Override
        public void onPropertyUnsubscribe(int property) {
            //ignore
        }

        private void waitForSubscription(long timeoutMs) throws Exception {
            if (!mSubscriptionWaitSemaphore.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                fail("waitForSubscription timeout");
            }
        }

        private LinkedList<int[]> waitForStateSetAndGetAll(long timeoutMs, int expectedState)
                throws Exception {
            while (true) {
                if (!mSetWaitSemaphore.tryAcquire(timeoutMs, TimeUnit.MILLISECONDS)) {
                    fail("waitForStateSetAndGetAll timeout");
                }
                LinkedList<int[]> result = new LinkedList<>();
                synchronized (this) {
                    boolean found = false;

                    while (!mSetStates.isEmpty()) {
                        int[] state = mSetStates.pop();
                        result.add(state);
                        if (state[0] == expectedState) {
                            found = true;
                            break;
                        }
                    }
                    if (found) {
                        // update semaphore to actual number of events in the list
                        mSetWaitSemaphore.drainPermits();
                        mSetWaitSemaphore.release(mSetStates.size());
                        return result;
                    }
                }
            }
        }

        private void sendStateAndCheckResponse(int state, int param, int expectedState)
                throws Exception {
            sendPowerState(state, param);
            waitForStateSetAndGetAll(DEFAULT_WAIT_TIMEOUT_MS, expectedState);
        }

        /**
         * Checks that a power state transition does NOT occur. If any state does occur during
         * the timeout period (other than a POSTPONE), then the test fails.
         */
        private void sendStateAndExpectNoResponse(int state, int param) throws Exception {
            sendPowerState(state, param);
            // Wait to see if a state transition occurs
            long startTime = SystemClock.elapsedRealtime();
            while (true) {
                long timeWaitingMs = SystemClock.elapsedRealtime() - startTime;
                if (timeWaitingMs > STATE_TRANSITION_MAX_WAIT_MS) {
                    // No meaningful state transition: this is a success!
                    return;
                }
                if (!mSetWaitSemaphore.tryAcquire(STATE_TRANSITION_MAX_WAIT_MS,
                        TimeUnit.MILLISECONDS)) {
                    // No state transition, this is a success!
                    return;
                }
                synchronized (this) {
                    while (!mSetStates.isEmpty()) {
                        int[] newState = mSetStates.pop();
                        if (newState[0] != VehicleApPowerStateReport.SHUTDOWN_POSTPONE) {
                            fail("Unexpected state change occurred, state="
                                    + Arrays.toString(newState));
                        }
                    }
                    mSetWaitSemaphore.drainPermits();
                }
            }
        }

        private void sendPowerState(int state, int param) {
            getAidlMockedVehicleHal().injectEvent(
                    AidlVehiclePropValueBuilder.newBuilder(VehicleProperty.AP_POWER_STATE_REQ)
                            .setTimestamp(SystemClock.elapsedRealtimeNanos())
                            .addIntValues(state, param)
                            .build());
        }
    }

    private static final class PowerPolicyListener extends ICarPowerPolicyListener.Stub {
        private final CountDownLatch mLatch = new CountDownLatch(1);
        private final String mWaitingPolicyId;

        private PowerPolicyListener(String policyId) {
            mWaitingPolicyId = policyId;
        }

        @Override
        public void onPolicyChanged(CarPowerPolicy appliedPolicy,
                CarPowerPolicy accumulatedPolicy) {
            if (Objects.equals(appliedPolicy.getPolicyId(), mWaitingPolicyId)) {
                mLatch.countDown();
            }
        }

        public void waitForPowerPolicy() throws Exception {
            JavaMockitoHelper.await(mLatch, POLICY_APPLICATION_TIMEOUT_MS);
        }
    }

    private static final class SystemStateInterfaceForSuspend implements SystemStateInterface {

        private final Object mLock = new Object();

        @GuardedBy("mLock")
        private boolean mExpectedSuspendStatus = true;

        @Override
        public void shutdown() {}

        @Override
        public boolean enterDeepSleep() {
            synchronized (mLock) {
                return mExpectedSuspendStatus;
            }
        }

        @Override
        public boolean enterHibernation() {
            synchronized (mLock) {
                return mExpectedSuspendStatus;
            }
        }

        @Override
        public void scheduleActionForBootCompleted(Runnable action, Duration delay,
                Duration delayRange) {}

        public void setExpectedSuspendStatus(boolean expectedStatus) {
            synchronized (mLock) {
                mExpectedSuspendStatus = expectedStatus;
            }
        }
    }
}
