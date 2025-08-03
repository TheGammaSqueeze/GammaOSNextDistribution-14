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

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.car.VehicleAreaSeat;
import android.car.VehiclePropertyIds;
import android.car.VehicleSeatOccupancyState;
import android.car.drivingstate.CarDrivingStateEvent;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyEvent;
import android.car.hardware.property.ICarPropertyEventListener;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.android.car.CarDrivingStateService;
import com.android.car.CarLocalServices;
import com.android.car.CarPropertyService;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.Arrays;

/**
 * Unit tests for {@link BluetoothDeviceConnectionPolicy}
 *
 * Run:
 * atest BluetoothDeviceConnectionPolicyTest
 */
public class BluetoothDeviceConnectionPolicyTest extends AbstractExtendedMockitoBluetoothTestCase {
    private static final String TAG = BluetoothDeviceConnectionPolicyTest.class.getSimpleName();
    private static final boolean VERBOSE = false;

    private static final long WAIT_TIMEOUT_MS = 5000;

    private MockContext mMockContext;
    @Mock private BluetoothAdapter mMockBluetoothAdapter;
    @Mock private BluetoothManager mMockBluetoothManager;
    @Mock private CarBluetoothService mMockBluetoothService;
    @Mock private CarPropertyService mMockCarPropertyService;
    @Mock private CarDrivingStateService mMockCarDrivingStateService;

    private Context mTargetContext;

    private BluetoothDeviceConnectionPolicy mPolicy;
    @Captor private ArgumentCaptor<ICarPropertyEventListener> mSeatListenerCaptor;

    // Can't set these programmatically in individual tests since SeatOnOccupiedListener.mDriverSeat
    // is final, and BluetoothDeviceConnectionPolicy.mSeatOnOccupiedListener is also final.
    // BluetoothDeviceConnectionPolicy is created once in setUp(), so individual tests cannot set
    // the driver's seat location programmatically.
    //
    // Please ensure the two seats are different values.
    private static final int DRIVER_SEAT = VehicleAreaSeat.SEAT_ROW_1_RIGHT;
    private static final int PASSENGER_SEAT = VehicleAreaSeat.SEAT_ROW_1_LEFT;

    @Override
    protected void onSessionBuilder(CustomMockitoSessionBuilder session) {
        session.spyStatic(CarLocalServices.class);
    }

    //--------------------------------------------------------------------------------------------//
    // Setup/TearDown                                                                             //
    //--------------------------------------------------------------------------------------------//

    @Before
    public void setUp() {
        mTargetContext = InstrumentationRegistry.getTargetContext();
        mMockContext = new MockContext(mTargetContext);

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

        mockGetCarLocalService(CarPropertyService.class, mMockCarPropertyService);
        mockGetCarLocalService(CarDrivingStateService.class, mMockCarDrivingStateService);

        // setting the driver's seat location
        when(mMockCarPropertyService
                .getPropertySafe(eq(VehiclePropertyIds.INFO_DRIVER_SEAT), anyInt()))
                .thenReturn(new CarPropertyValue<Integer>(VehiclePropertyIds.INFO_DRIVER_SEAT,
                0 /*areaId*/, new Integer(DRIVER_SEAT)));

        mPolicy = BluetoothDeviceConnectionPolicy.create(mMockContext, USER_ID,
                mMockBluetoothService);
        Assert.assertTrue(mPolicy != null);

        // Get the seat occupancy listener
        when(mMockCarPropertyService.registerListenerSafe(anyInt(), anyFloat(),
                mSeatListenerCaptor.capture())).thenReturn(true);
    }

    @After
    public void tearDown() {
        if (mPolicy != null) {
            mPolicy.release();
        }
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

    private void sendAdapterStateChanged(int newState) {
        Assert.assertTrue(mMockContext != null);
        Intent intent = new Intent(BluetoothAdapter.ACTION_STATE_CHANGED);
        intent.putExtra(BluetoothAdapter.EXTRA_STATE, newState);
        mMockContext.sendBroadcast(intent);
    }

    private void sendSeatOnOccupied(int seat) {
        CarPropertyValue<Integer> value = new CarPropertyValue<Integer>(
                VehiclePropertyIds.SEAT_OCCUPANCY, seat,
                new Integer(VehicleSeatOccupancyState.OCCUPIED));
        CarPropertyEvent event = new CarPropertyEvent(
                CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE, value);
        try {
            mSeatListenerCaptor.getValue().onEvent(Arrays.asList(event));
        } catch (Throwable e) {
            Log.e(TAG, "sendSeatOnOccupied: " + e);
        }

    }

    private void setDrivingState(int value) {
        when(mMockCarDrivingStateService.getCurrentDrivingState())
                .thenReturn(new CarDrivingStateEvent(value, 0 /*timeStamp*/));
    }

    //--------------------------------------------------------------------------------------------//
    // Policy Init tests                                                                          //
    //--------------------------------------------------------------------------------------------//

    /**
     * Preconditions:
     * - Adapter is on
     *
     * Action:
     * - Initialize the policy
     *
     * Outcome:
     * - Because the Adapter is ON at init time, we should attempt to connect devices
     */
    @Test
    public void testInitWithAdapterOn_connectDevices() {
        turnAdapterOn();
        mPolicy.init();
        verify(mMockBluetoothService, timeout(WAIT_TIMEOUT_MS).atLeastOnce()).connectDevices();
    }

    /**
     * Preconditions:
     * - Adapter is off
     *
     * Action:
     * - Initialize the policy
     *
     * Outcome:
     * - Because the Adapter is OFF at init time, we should not attempt to connect devices
     */
    @Test
    public void testInitWithAdapterOff_doNothing() {
        turnAdapterOff(false);
        mPolicy.init();
        verify(mMockBluetoothService, times(0)).connectDevices();
    }

    //--------------------------------------------------------------------------------------------//
    // Bluetooth stack adapter status changed event tests                                         //
    //--------------------------------------------------------------------------------------------//

    /**
     * Preconditions:
     * - Policy is initialized
     *
     * Action:
     * - Adapter state TURNING_OFF is received
     *
     * Outcome:
     * - Do nothing
     */
    @Test
    public void testReceiveAdapterTurningOff_doNothing() {
        mPolicy.init();
        reset(mMockBluetoothService);

        sendAdapterStateChanged(BluetoothAdapter.STATE_TURNING_OFF);
        verify(mMockBluetoothService, times(0)).connectDevices();
    }

    /**
     * Preconditions:
     * - Policy is initialized
     *
     * Action:
     * - Adapter state OFF is received
     *
     * Outcome:
     * - Do nothing
     */
    @Test
    public void testReceiveAdapterOff_doNothing() {
        mPolicy.init();
        reset(mMockBluetoothService);

        sendAdapterStateChanged(BluetoothAdapter.STATE_OFF);
        verify(mMockBluetoothService, times(0)).connectDevices();
    }

    /**
     * Preconditions:
     * - Policy is initialized
     *
     * Action:
     * - Adapter state TURNING_ON is received
     *
     * Outcome:
     * - Do nothing
     */
    @Test
    public void testReceiveAdapterTurningOn_doNothing() {
        mPolicy.init();
        reset(mMockBluetoothService);

        sendAdapterStateChanged(BluetoothAdapter.STATE_TURNING_ON);
        verify(mMockBluetoothService, times(0)).connectDevices();
    }

    /**
     * Preconditions:
     * - Policy is initialized
     *
     * Action:
     * - Adapter state ON is received
     *
     * Outcome:
     * - Attempt to connect devices
     */
    @Test
    public void testReceiveAdapterOn_connectDevices() {
        mPolicy.init();
        reset(mMockBluetoothService);

        sendAdapterStateChanged(BluetoothAdapter.STATE_ON);
        verify(mMockBluetoothService, timeout(WAIT_TIMEOUT_MS).atLeastOnce()).connectDevices();
    }

    //--------------------------------------------------------------------------------------------//
    // Seat occupancy event tests                                                                 //
    //--------------------------------------------------------------------------------------------//

    /**
     * Preconditions:
     * - Policy is initialized
     * - Adapter is ON
     * - Car is in parked state
     *
     * Action:
     * - Driver's seat sensor OCCUPIED is received
     *
     * Outcome:
     * - Attempt to connect devices
     */
    @Test
    public void testSeatOnOccupied_driverSeat_parked_connectDevices() {
        turnAdapterOn();
        mPolicy.init();
        setDrivingState(CarDrivingStateEvent.DRIVING_STATE_PARKED);
        reset(mMockBluetoothService);

        sendSeatOnOccupied(DRIVER_SEAT);
        verify(mMockBluetoothService, times(1)).connectDevices();
    }

    /**
     * Preconditions:
     * - Policy is initialized
     * - Adapter is ON
     * - Car is in parked state
     *
     * Action:
     * - Passenger's seat sensor OCCUPIED is received
     *
     * Outcome:
     * - Do nothing
     */
    @Test
    public void testSeatOnOccupied_passengerSeat_parked_doNothing() {
        turnAdapterOn();
        mPolicy.init();
        setDrivingState(CarDrivingStateEvent.DRIVING_STATE_PARKED);
        reset(mMockBluetoothService);

        sendSeatOnOccupied(PASSENGER_SEAT);
        verify(mMockBluetoothService, times(0)).connectDevices();
    }

    /**
     * Preconditions:
     * - Policy is initialized
     * - Adapter is ON
     * - Car is in driving state
     *
     * Action:
     * - Driver's seat sensor OCCUPIED is received
     *
     * Outcome:
     * - Do nothing
     */
    @Test
    public void testSeatOnOccupied_driverSeat_driving_doNothing() {
        turnAdapterOn();
        mPolicy.init();
        setDrivingState(CarDrivingStateEvent.DRIVING_STATE_MOVING);
        reset(mMockBluetoothService);

        sendSeatOnOccupied(DRIVER_SEAT);
        verify(mMockBluetoothService, times(0)).connectDevices();
    }

    /**
     * Tests the case where if {@link VehiclePropertyIds.INFO_DRIVER_SEAT} is not registered or is
     * not available, then car policy should still be able to proceed to be created, and not crash
     * car Bluetooth.
     *
     * Preconditions:
     * - {@code mCarPropertyService.getProperty(VehiclePropertyIds.INFO_DRIVER_SEAT,
     *   VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL)} returns {@code null}.
     *
     * Action:
     * - Create a new {@link BluetoothDeviceConnectionPolicy}.
     *
     * Outcome:
     * - A new instance of {@link BluetoothDeviceConnectionPolicy} is successfully created, e.g.,
     *   no NPE or anything else that prevents creation of policy in this case
     */
    @Test
    public void testGetDriverSeatLocationNull_policyCreated() {
        when(mMockCarPropertyService
                .getPropertySafe(eq(VehiclePropertyIds.INFO_DRIVER_SEAT), anyInt()))
                .thenReturn(null);

        BluetoothDeviceConnectionPolicy policyUnderTest = BluetoothDeviceConnectionPolicy.create(
                mMockContext, USER_ID, mMockBluetoothService);
        Assert.assertTrue(policyUnderTest != null);
    }

    /**
     * Tests the case where if {@link CarDrivingStateService#getCurrentDrivingState()} returns
     * null, {@link CarServicesHelper#isParked()} should not throw a NPE.
     *
     * Preconditions:
     * - {@link CarDrivingStateService#getCurrentDrivingState()} returns {@code null}.
     *
     * Action:
     * - Call {@link CarServicesHelper#isParked()}.
     *
     * Outcome:
     * - {@link CarServicesHelper#isParked()} returns {@code false}.
     */
    @Test
    public void testGetDrivingStateNull_noNpe() {
        when(mMockCarDrivingStateService.getCurrentDrivingState()).thenReturn(null);

        BluetoothDeviceConnectionPolicy.CarServicesHelper helperUnderTest =
                mPolicy.new CarServicesHelper();

        Assert.assertFalse(helperUnderTest.isParked());
    }
}
