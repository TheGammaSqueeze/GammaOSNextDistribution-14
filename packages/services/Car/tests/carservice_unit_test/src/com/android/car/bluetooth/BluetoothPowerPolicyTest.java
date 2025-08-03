/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.bluetooth;

import static android.car.hardware.power.PowerComponentUtil.FIRST_POWER_COMPONENT;
import static android.car.hardware.power.PowerComponentUtil.LAST_POWER_COMPONENT;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.car.hardware.power.CarPowerPolicy;
import android.provider.Settings;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.RequiresDevice;

import com.android.car.CarLocalServices;
import com.android.car.power.CarPowerManagementService;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Unit tests for {@link BluetoothPowerPolicy}
 *
 * Run:
 * atest BluetoothPowerPolicyTest
 */
@RequiresDevice
public class BluetoothPowerPolicyTest extends AbstractExtendedMockitoBluetoothTestCase {
    private MockContext mMockContext;
    @Mock private BluetoothAdapter mMockBluetoothAdapter;
    @Mock private BluetoothManager mMockBluetoothManager;
    @Mock private CarPowerManagementService mMockCarPowerManagementService;

    private BluetoothPowerPolicy mPolicy;

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session.spyStatic(BluetoothAdapter.class);
        session.spyStatic(CarLocalServices.class);
    }

    //--------------------------------------------------------------------------------------------//
    // Setup/TearDown                                                                             //
    //--------------------------------------------------------------------------------------------//

    @Before
    public void setUp() {
        mMockContext = new MockContext(InstrumentationRegistry.getTargetContext());

        when(mMockUserManager.isUserUnlocked(any())).thenReturn(false);

        mMockContext.addMockedSystemService(BluetoothManager.class, mMockBluetoothManager);
        when(mMockBluetoothManager.getAdapter()).thenReturn(mMockBluetoothAdapter);

        /**
         * Mocks {@code mBluetoothAdapter.enable()}
         */
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                turnAdapterOn();
                return null;
            }
        }).when(mMockBluetoothAdapter).enable();
        /**
         * Mocks {@code mBluetoothAdapter.disable(boolean)}
         */
        doAnswer(new Answer<Void>() {
            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                // Default: do not persist OFF state to Settings
                boolean isPersistedOff = false;
                Object[] arguments = invocation.getArguments();
                if (arguments != null && arguments.length == 1 && arguments[0] != null) {
                    isPersistedOff = (boolean) arguments[0];
                }
                turnAdapterOff(isPersistedOff);
                return null;
            }
        }).when(mMockBluetoothAdapter).disable(anyBoolean());
        /**
         * Adapter needs to be in *some* state at the beginning of each test. Default ON.
         * This will also set Bluetooth persisted state in Settings to ON.
         */
        turnAdapterOn();

        mPolicy = BluetoothPowerPolicy.create(mMockContext, USER_ID);
        Assert.assertTrue(mPolicy != null);

        CarLocalServices.addService(CarPowerManagementService.class,
                mMockCarPowerManagementService);

        mPolicy.init();
    }

    @After
    public void tearDown() {
        mPolicy.release();
        CarLocalServices.removeServiceForTest(CarPowerManagementService.class);
        if (mMockContext != null) {
            mMockContext.release();
            mMockContext = null;
        }
    }

    //--------------------------------------------------------------------------------------------//
    // Utilities                                                                                  //
    //--------------------------------------------------------------------------------------------//

    /**
     * Mocks {@link BluetoothAdapter#enable()}:
     *  - {@code BluetoothAdapter#getState()} to return {@code BluetoothAdapter.STATE_ON}.
     *  - {@code BluetoothAdapter#isEnabled()} to return {@code true}.
     *  - Persist the Bluetooth ON state in Settings.
     */
    private void turnAdapterOn() {
        when(mMockBluetoothAdapter.getState()).thenReturn(BluetoothAdapter.STATE_ON);
        when(mMockBluetoothAdapter.isEnabled()).thenReturn(true);
        persistBluetoothSettingOn();
    }

    /**
     * Mocks {@link BluetoothAdapter#disable(boolean)}:
     *  - {@code BluetoothAdapter#getState()} to return {@code BluetoothAdapter.STATE_OFF}.
     *  - {@code BluetoothAdapter#isEnabled()} to return {@code false}.
     */
    private void turnAdapterOff(boolean persist) {
        when(mMockBluetoothAdapter.getState()).thenReturn(BluetoothAdapter.STATE_OFF);
        when(mMockBluetoothAdapter.isEnabled()).thenReturn(false);
        if (persist) {
            persistBluetoothSettingOff();
        }
    }

    /**
     * Persist the Bluetooth ON state in Settings.
     */
    private void persistBluetoothSettingOn() {
        persistBluetoothSetting(1);
    }

    /**
     * Persist the Bluetooth OFF state in Settings.
     */
    private void persistBluetoothSettingOff() {
        persistBluetoothSetting(0);
    }

    /**
     * Persist the Bluetooth on/off state in Settings. Does not change the actual adapter state.
     * C.f., {@link BluetoothManagerService#persistBluetoothSetting}.
     *
     * @param persistedState: {@code 1} == ON, {@code 0} == OFF.
     */
    private void persistBluetoothSetting(int persistedState) {
        Settings.Global.putInt(mMockContext.getContentResolver(), Settings.Global.BLUETOOTH_ON,
                persistedState);
    }

    /**
     * Get the persisted Bluetooth on/off state from Settings. Does not reflect the actual
     * adapter state.
     *
     * @return {@code true} if Bluetooth is persisted ON, {@code false} otherwise.
     */
    public boolean isBluetoothPersistedOn() {
        return (Settings.Global.getInt(
                mMockContext.getContentResolver(), Settings.Global.BLUETOOTH_ON, -1) != 0);
    }

    private void sendPowerPolicyBluetoothOnOff(boolean isOn) throws Exception {
        int[] allComponents = new int[LAST_POWER_COMPONENT - FIRST_POWER_COMPONENT + 1];
        for (int component = FIRST_POWER_COMPONENT; component <= LAST_POWER_COMPONENT;
                component++) {
            allComponents[component - FIRST_POWER_COMPONENT] = component;
        }
        int[] noComponents = new int[]{};
        CarPowerPolicy policy;
        if (isOn) {
            policy = new CarPowerPolicy("bt_on", allComponents, noComponents);
        } else {
            policy = new CarPowerPolicy("bt_off", noComponents, allComponents);
        }
        mPolicy.getPowerPolicyListener().onPolicyChanged(policy, policy);
    }

    //--------------------------------------------------------------------------------------------//
    // Car Power Manager state changed event tests                                                //
    //--------------------------------------------------------------------------------------------//

    /**
     * Preconditions:
     * - User is NOT unlocked
     *
     * Action:
     * - Receive an ON call
     *
     * Outcome:
     * - State change is ignored, nothing happens
     */
    @Test
    public void testReceivePowerOnUserNotUnlocked_doNothing() throws Exception {
        clearInvocations(mMockBluetoothAdapter);

        sendPowerPolicyBluetoothOnOff(false);
        verifyNoMoreInteractions(mMockBluetoothAdapter);
    }

    /**
     * Preconditions:
     * - Adapter is on
     *
     * Action:
     * - Receive a SHUTDOWN_PREPARE call
     *
     * Outcome:
     * - Adapter is turned off without persisting the off state.
     */
    @Test
    public void testReceivePowerShutdownPrepareWhenAdapterOn_disableBluetooth() throws Exception {
        setUserUnlocked(USER_ID, true);
        turnAdapterOn();
        clearInvocations(mMockBluetoothAdapter);

        sendPowerPolicyBluetoothOnOff(false);
        verify(mMockBluetoothAdapter, times(1)).disable(false);
        Assert.assertTrue(isBluetoothPersistedOn());
    }

    /**
     * Preconditions:
     * - Adapter is off
     *
     * Action:
     * - Receive a SHUTDOWN_PREPARE call
     *
     * Outcome:
     * - Adapter is off already. No calls are made to change the state.
     */
    @Test
    public void testReceivePowerShutdownPrepareWhenAdapterOff_doNothing() throws Exception {
        setUserUnlocked(USER_ID, true);
        turnAdapterOff(true);
        clearInvocations(mMockBluetoothAdapter);

        sendPowerPolicyBluetoothOnOff(false);
        verify(mMockBluetoothAdapter, times(1)).disable(false);
        Assert.assertFalse(isBluetoothPersistedOn());
    }

    /**
     * Preconditions:
     * - Adapter is off and is persisted off
     * - Policy is initialized
     *
     * Action:
     * - Power state ON is received
     *
     * Outcome:
     * - Because the Adapter is persisted off, we should do nothing. The adapter should remain off
     */
    @Test
    public void testReceivePowerOnBluetoothPersistedOff_doNothing() throws Exception {
        setUserUnlocked(USER_ID, true);
        turnAdapterOff(true);
        clearInvocations(mMockBluetoothAdapter);

        sendPowerPolicyBluetoothOnOff(true);
        verify(mMockBluetoothAdapter, times(0)).enable();
    }

     /**
     * Preconditions:
     * - Adapter is off and is not persisted off
     * - Policy is initialized
     *
     * Action:
     * - Power state ON is received
     *
     * Outcome:
     * - Because the Adapter is not persisted off, we should turn it back on. No attempt to connect
     *   devices is made because we're yielding to the adapter ON event.
     */
    @Test
    public void testReceivePowerOnBluetoothOffNotPersisted_BluetoothOn()
            throws Exception {
        setUserUnlocked(USER_ID, true);
        turnAdapterOff(false);
        // {@code turnAdapterOff(false)} should not change the persisted state in Settings;
        // the persisted state can be anything, so explicitly set the persisted state to ON.
        persistBluetoothSettingOn();
        clearInvocations(mMockBluetoothAdapter);

        sendPowerPolicyBluetoothOnOff(true);
        verify(mMockBluetoothAdapter, times(1)).enable();
    }
}
