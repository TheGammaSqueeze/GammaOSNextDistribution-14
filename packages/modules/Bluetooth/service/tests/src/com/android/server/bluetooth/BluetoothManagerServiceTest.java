/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.server.bluetooth;

import static android.bluetooth.BluetoothAdapter.STATE_BLE_ON;
import static android.bluetooth.BluetoothAdapter.STATE_OFF;
import static android.bluetooth.BluetoothAdapter.STATE_ON;
import static android.bluetooth.BluetoothAdapter.STATE_TURNING_ON;

import static com.android.server.bluetooth.BluetoothManagerService.MESSAGE_BLUETOOTH_SERVICE_CONNECTED;
import static com.android.server.bluetooth.BluetoothManagerService.MESSAGE_BLUETOOTH_STATE_CHANGE;
import static com.android.server.bluetooth.BluetoothManagerService.MESSAGE_DISABLE;
import static com.android.server.bluetooth.BluetoothManagerService.MESSAGE_ENABLE;
import static com.android.server.bluetooth.BluetoothManagerService.MESSAGE_TIMEOUT_BIND;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.runners.Parameterized.Parameter;
import static org.junit.runners.Parameterized.Parameters;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;

import android.bluetooth.IBluetooth;
import android.bluetooth.IBluetoothCallback;
import android.bluetooth.IBluetoothManagerCallback;
import android.bluetooth.IBluetoothStateChangeCallback;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Message;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.test.TestLooper;
import android.provider.Settings;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bluetooth.flags.FakeFeatureFlagsImpl;
import com.android.bluetooth.flags.Flags;

import com.google.common.collect.Lists;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@RunWith(Parameterized.class)
public class BluetoothManagerServiceTest {
    private static final String TAG = BluetoothManagerServiceTest.class.getSimpleName();
    private static final int STATE_BLE_TURNING_ON = 14; // can't find the symbol because hidden api
    private static final int TIMEOUT_MS = 1000; // TO use to wait for handler execution

    BluetoothManagerService mManagerService;

    @Spy
    private final Context mContext =
            new ContextWrapper(InstrumentationRegistry.getInstrumentation().getTargetContext());

    @Spy BluetoothServerProxy mBluetoothServerProxy;
    @Mock UserManager mUserManager;
    @Mock UserHandle mUserHandle;

    @Mock IBinder mBinder;
    @Mock IBluetoothManagerCallback mManagerCallback;
    @Mock IBluetoothStateChangeCallback mStateChangeCallback;

    @Mock IBluetooth mAdapterService;
    @Mock AdapterBinder mAdapterBinder;
    private FakeFeatureFlagsImpl mFakeFlagsImpl;

    @Parameter public FlagsValue mFlagsValue;

    static class FlagsValue {
        final Map<String, Boolean> mFlagsValue;

        FlagsValue(Map<String, Boolean> flagsValue) {
            mFlagsValue = flagsValue;
        }

        private static String formatFlag(String key) {
            return key.substring(key.lastIndexOf(".") + 1);
        }

        @Override
        public String toString() {
            return mFlagsValue.entrySet().stream()
                    .filter(Map.Entry::getValue)
                    .map(Map.Entry::getKey)
                    .map(FlagsValue::formatFlag)
                    .sorted()
                    .collect(Collectors.joining(", "));
        }
    }

    private static boolean filterFlags(Map<String, Boolean> map) {
        if (map.get(Flags.FLAG_USE_NEW_AIRPLANE_MODE)) {
            return !map.get(Flags.FLAG_AIRPLANE_RESSOURCES_IN_APP);
        }
        return true;
    }

    /** Generate the Map of flag for this test Suite */
    @Parameters(name = "{0}")
    public static Iterable<? extends Object> generateParameterizedFlagsValue() {
        final String[] flags = {
            Flags.FLAG_AIRPLANE_RESSOURCES_IN_APP,
            Flags.FLAG_USE_NEW_AIRPLANE_MODE,
            Flags.FLAG_USE_NEW_SATELLITE_MODE,
        };
        final Boolean[] values = {true, false};

        List<List<Map.Entry<String, Boolean>>> flagValues =
                Arrays.stream(flags)
                        .map(flag -> Arrays.stream(values).map(val -> Map.entry(flag, val)))
                        .map(Stream::toList)
                        .toList();

        return Lists.cartesianProduct(flagValues).stream()
                .map(list -> list.toArray(new Map.Entry[0]))
                .map(Map::ofEntries)
                .filter(BluetoothManagerServiceTest::filterFlags)
                .map(FlagsValue::new)
                .map(List::of)
                .map(List::toArray)
                .toList();
    }

    TestLooper mLooper;

    boolean mHasException = false;

    static {
        // Required for reading DeviceConfig.
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(android.Manifest.permission.READ_DEVICE_CONFIG);
    }

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        // Mock these functions so security errors won't throw
        doReturn("name")
                .when(mBluetoothServerProxy)
                .settingsSecureGetString(any(), eq(Settings.Secure.BLUETOOTH_NAME));
        doReturn("00:11:22:33:44:55")
                .when(mBluetoothServerProxy)
                .settingsSecureGetString(any(), eq(Settings.Secure.BLUETOOTH_ADDRESS));

        // Test is not allowed to send broadcast as Bluetooth. doNothing Prevent SecurityException
        doNothing().when(mContext).sendBroadcastAsUser(any(), any(), any(), any());
        doReturn(mUserManager).when(mContext).getSystemService(UserManager.class);

        doReturn(mBinder).when(mManagerCallback).asBinder();

        doReturn(mAdapterBinder).when(mBluetoothServerProxy).createAdapterBinder(any());
        doReturn(mAdapterService).when(mAdapterBinder).getAdapterBinder();

        doReturn(mock(Intent.class))
                .when(mContext)
                .registerReceiverForAllUsers(any(), any(), eq(null), eq(null));

        doReturn(true)
                .when(mContext)
                .bindServiceAsUser(
                        any(Intent.class),
                        any(ServiceConnection.class),
                        anyInt(),
                        any(UserHandle.class));

        BluetoothServerProxy.setInstanceForTesting(mBluetoothServerProxy);

        mLooper = new TestLooper();

        mFakeFlagsImpl = new FakeFeatureFlagsImpl();
        mFlagsValue.mFlagsValue.forEach(mFakeFlagsImpl::setFlag);

        mManagerService =
                new BluetoothManagerService(mContext, mLooper.getLooper(), mFakeFlagsImpl);
        mManagerService.initialize(mUserHandle);

        mManagerService.registerAdapter(mManagerCallback);
    }

    @After
    public void tearDown() {
        if (mManagerService != null) {
            mManagerService.unregisterAdapter(mManagerCallback);
            mManagerService = null;
        }
        mLooper.moveTimeForward(120_000); // 120 seconds
        // Do not try to assert if `syncHandler()` already raised an exception for it
        if (!mHasException) {
            assertThat(mLooper.nextMessage()).isNull();
        }
        validateMockitoUsage();
    }

    /**
     * Dispatch all the message on the Loopper and check that the what is expected
     *
     * @param what list of message that are expected to be run by the handler
     */
    private void syncHandler(int... what) {
        IntStream.of(what)
                .forEach(
                        w -> {
                            Message msg = mLooper.nextMessage();
                            assertThat(msg).isNotNull();
                            assertThat(msg.what).isEqualTo(w);
                            msg.getTarget().dispatchMessage(msg);
                        });
    }

    @Test
    public void onUserRestrictionsChanged_disallowBluetooth_onlySendDisableMessageOnSystemUser()
            throws InterruptedException {
        // Mimic the case when restriction settings changed
        doReturn(true)
                .when(mUserManager)
                .hasUserRestrictionForUser(eq(UserManager.DISALLOW_BLUETOOTH), any());
        doReturn(false)
                .when(mUserManager)
                .hasUserRestrictionForUser(eq(UserManager.DISALLOW_BLUETOOTH_SHARING), any());

        // Check if disable message sent once for system user only

        // test run on user -1, should not turning Bluetooth off
        mManagerService.onUserRestrictionsChanged(UserHandle.CURRENT);
        assertThat(mLooper.nextMessage()).isNull();

        // called from SYSTEM user, should try to toggle Bluetooth off
        mManagerService.onUserRestrictionsChanged(UserHandle.SYSTEM);
        syncHandler(MESSAGE_DISABLE);
    }

    @Test
    public void testApmEnhancementEnabled() {
        // Change the apm enhancement enabled value to 0
        Settings.Global.putInt(mContext.getContentResolver(), "apm_enhancement_enabled", 0);
        assertThat(
                        Settings.Global.getInt(
                                mContext.getContentResolver(), "apm_enhancement_enabled", 0))
                .isEqualTo(0);

        // Confirm that apm enhancement enabled value has been updated to 1
        mManagerService.setApmEnhancementState();
        assertThat(
                        Settings.Global.getInt(
                                mContext.getContentResolver(), "apm_enhancement_enabled", 0))
                .isEqualTo(1);
    }

    @Test
    public void enable_bindFailure_removesTimeout() throws Exception {
        doReturn(false)
                .when(mContext)
                .bindServiceAsUser(
                        any(Intent.class),
                        any(ServiceConnection.class),
                        anyInt(),
                        any(UserHandle.class));
        mManagerService.enableBle("enable_bindFailure_removesTimeout", mBinder);
        syncHandler(MESSAGE_ENABLE);

        // TODO(b/280518177): Failed to start should be noted / reported in metrics
        // Maybe show a popup or a crash notification
        // Should we attempt to re-bind ?
    }

    @Test
    public void enable_bindTimeout() throws Exception {
        mManagerService.enableBle("enable_bindTimeout", mBinder);
        syncHandler(MESSAGE_ENABLE);

        mLooper.moveTimeForward(120_000); // 120 seconds
        syncHandler(MESSAGE_TIMEOUT_BIND);
        // Force handling the message now without waiting for the timeout to fire

        // TODO(b/280518177): A lot of stuff is wrong here since when a timeout occur:
        //   * No error is printed to the user
        //   * Code stop trying to start the bluetooth.
        //   * if user ask to enable again, it will start a second bind but the first still run
    }

    private void acceptBluetoothBinding(IBinder binder, String name, int n) {
        ComponentName compName = new ComponentName("", "com.android.bluetooth." + name);

        ArgumentCaptor<BluetoothManagerService.BluetoothServiceConnection> captor =
                ArgumentCaptor.forClass(BluetoothManagerService.BluetoothServiceConnection.class);
        verify(mContext, times(n))
                .bindServiceAsUser(
                        any(Intent.class), captor.capture(), anyInt(), any(UserHandle.class));
        assertThat(captor.getAllValues().size()).isEqualTo(n);

        captor.getAllValues().get(n - 1).onServiceConnected(compName, binder);
        syncHandler(MESSAGE_BLUETOOTH_SERVICE_CONNECTED);
    }

    private static IBluetoothCallback captureBluetoothCallback(AdapterBinder adapterBinder)
            throws Exception {
        ArgumentCaptor<IBluetoothCallback> captor =
                ArgumentCaptor.forClass(IBluetoothCallback.class);
        verify(adapterBinder).registerCallback(captor.capture(), any());
        assertThat(captor.getAllValues().size()).isEqualTo(1);
        return captor.getValue();
    }

    IBluetoothCallback transition_offToBleOn() throws Exception {
        // Binding of IBluetooth
        acceptBluetoothBinding(mBinder, "btservice.AdapterService", 1);

        // TODO(b/280518177): This callback is too early, bt is not ON nor BLE_ON
        verify(mManagerCallback).onBluetoothServiceUp(any());

        IBluetoothCallback btCallback = captureBluetoothCallback(mAdapterBinder);
        verify(mAdapterBinder).enable(anyBoolean(), any());

        // AdapterService is sending AdapterState.BLE_TURN_ON that will trigger this callback
        // and in parallel it call its `bringUpBle()`
        btCallback.onBluetoothStateChange(STATE_OFF, STATE_BLE_TURNING_ON);
        syncHandler(MESSAGE_BLUETOOTH_STATE_CHANGE);
        assertThat(mManagerService.getState()).isEqualTo(STATE_BLE_TURNING_ON);

        // assertThat(mManagerService.waitForManagerState(STATE_BLE_TURNING_ON)).isTrue();

        // GattService has been started by AdapterService and it will enable native side then
        // trigger the stateChangeCallback from native
        btCallback.onBluetoothStateChange(STATE_BLE_TURNING_ON, STATE_BLE_ON);
        syncHandler(MESSAGE_BLUETOOTH_STATE_CHANGE);
        assertThat(mManagerService.getState()).isEqualTo(STATE_BLE_ON);

        // Check that we sent 2 intent, one for BLE_TURNING_ON, one for BLE_ON
        // TODO(b/280518177): assert the intent are the correct one
        verify(mContext, times(2)).sendBroadcastAsUser(any(), any(), any(), any());
        return btCallback;
    }

    private IBluetoothCallback transition_offToOn() throws Exception {
        IBluetoothCallback btCallback = transition_offToBleOn();
        verify(mAdapterBinder, times(1)).startBrEdr(any());

        // AdapterService go to turning_on and start all profile on its own
        btCallback.onBluetoothStateChange(STATE_BLE_ON, STATE_TURNING_ON);
        syncHandler(MESSAGE_BLUETOOTH_STATE_CHANGE);
        // When all the profile are started, adapterService consider it is ON
        btCallback.onBluetoothStateChange(STATE_TURNING_ON, STATE_ON);
        syncHandler(MESSAGE_BLUETOOTH_STATE_CHANGE);

        // Check that we sent 6 intent, 4 for BLE: BLE_TURNING_ON + BLE_ON + TURNING_ON + ON
        // and 2 for classic: TURNING_ON + ON
        // TODO(b/280518177): assert the intent are the correct one
        verify(mContext, times(6)).sendBroadcastAsUser(any(), any(), any(), any());

        return btCallback;
    }

    @Test
    public void offToBleOn() throws Exception {
        // In order to go to BLE only, the persisted state should be BLUETOOTH_OFF
        doReturn(BluetoothManagerService.BLUETOOTH_OFF)
                .when(mBluetoothServerProxy)
                .getBluetoothPersistedState(any(), anyInt());

        mManagerService.enableBle("test_offToBleOn", mBinder);
        syncHandler(MESSAGE_ENABLE);

        transition_offToBleOn();

        // Check that there was no transition to STATE_ON
        verify(mAdapterBinder, times(0)).startBrEdr(any());
        assertThat(mManagerService.getState()).isEqualTo(STATE_BLE_ON);
    }

    @Test
    public void offToOn() throws Exception {
        // In order to not go to BLE only, the persisted state should not be BLUETOOTH_OFF
        doReturn(BluetoothManagerService.BLUETOOTH_ON_BLUETOOTH)
                .when(mBluetoothServerProxy)
                .getBluetoothPersistedState(any(), anyInt());

        mManagerService.enable("test_offToOn");
        syncHandler(MESSAGE_ENABLE);

        transition_offToOn();

        assertThat(mManagerService.getState()).isEqualTo(STATE_ON);
    }
}
