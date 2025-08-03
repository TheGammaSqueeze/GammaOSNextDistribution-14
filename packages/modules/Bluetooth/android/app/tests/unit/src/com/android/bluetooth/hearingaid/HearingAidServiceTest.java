/*
 * Copyright 2018 The Android Open Source Project
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

package com.android.bluetooth.hearingaid;

import static androidx.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHearingAid;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothUuid;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.BluetoothProfileConnectionInfo;
import android.os.Looper;
import android.os.ParcelUuid;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.TestUtils;
import com.android.bluetooth.btservice.ActiveDeviceManager;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.AudioRoutingManager;
import com.android.bluetooth.btservice.storage.DatabaseManager;
import com.android.bluetooth.flags.Flags;
import com.android.bluetooth.x.com.android.modules.utils.SynchronousResultReceiver;

import org.hamcrest.Matcher;
import org.hamcrest.core.AllOf;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.hamcrest.MockitoHamcrest;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class HearingAidServiceTest {
    private static final Duration TIMEOUT = Duration.ofSeconds(1);

    private static final BluetoothAdapter mAdapter = BluetoothAdapter.getDefaultAdapter();
    private static final BluetoothDevice mLeftDevice = TestUtils.getTestDevice(mAdapter, 0);
    private static final BluetoothDevice mRightDevice = TestUtils.getTestDevice(mAdapter, 1);
    private static final BluetoothDevice mSingleDevice = TestUtils.getTestDevice(mAdapter, 2);

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private HearingAidService mService;
    private HearingAidService.BluetoothHearingAidBinder mServiceBinder;
    private HashMap<BluetoothDevice, LinkedBlockingQueue<Intent>> mDeviceQueueMap;

    @Mock private AdapterService mAdapterService;
    @Mock private ActiveDeviceManager mActiveDeviceManager;
    @Mock private AudioRoutingManager mAudioRoutingManager;
    @Mock private Context mContext;
    @Mock private DatabaseManager mDatabaseManager;
    @Mock private HearingAidNativeInterface mNativeInterface;
    @Mock private AudioManager mAudioManager;

    private InOrder mInOrder = null;

    @Before
    public void setUp() throws Exception {
        // Set up mocks and test assets
        MockitoAnnotations.initMocks(this);
        mInOrder = inOrder(mContext);

        TestUtils.mockGetSystemService(
                mContext, Context.AUDIO_SERVICE, AudioManager.class, mAudioManager);

        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        TestUtils.setAdapterService(mAdapterService);
        doAnswer(
                        invocation -> {
                            if (Flags.audioRoutingCentralization()) {
                                return mAudioRoutingManager;
                            } else {
                                return mActiveDeviceManager;
                            }
                        })
                .when(mAdapterService)
                .getActiveDeviceManager();
        doReturn(mDatabaseManager).when(mAdapterService).getDatabase();

        HearingAidNativeInterface.setInstance(mNativeInterface);
        startService();
        mServiceBinder = (HearingAidService.BluetoothHearingAidBinder) mService.initBinder();
        mServiceBinder.mIsTesting = true;

        // Override the timeout value to speed up the test
        HearingAidStateMachine.sConnectTimeoutMs = (int) TIMEOUT.toMillis(); // 1s

        doReturn(BluetoothDevice.BOND_BONDED)
                .when(mAdapterService)
                .getBondState(any(BluetoothDevice.class));
        doReturn(new ParcelUuid[] {BluetoothUuid.HEARING_AID})
                .when(mAdapterService)
                .getRemoteUuids(any(BluetoothDevice.class));
    }

    @After
    public void tearDown() throws Exception {
        stopService();
        HearingAidNativeInterface.setInstance(null);
        TestUtils.clearAdapterService(mAdapterService);
    }

    private void startService() throws TimeoutException {
        mService = new HearingAidService(mContext);
        mService.start();
        mService.setAvailable(true);
    }

    private void stopService() throws TimeoutException {
        mService.stop();
        mService = HearingAidService.getHearingAidService();
        assertThat(mService).isNull();
    }

    @SafeVarargs
    private void verifyIntentSent(Matcher<Intent>... matchers) {
        mInOrder.verify(mContext, timeout(TIMEOUT.toMillis() * 2))
                .sendBroadcast(MockitoHamcrest.argThat(AllOf.allOf(matchers)), any(), any());
    }

    private void verifyConnectionStateIntent(BluetoothDevice device, int newState, int prevState) {
        verifyConnectionStateIntent(device, newState, prevState, true);
    }

    private void verifyConnectionStateIntent(
            BluetoothDevice device, int newState, int prevState, boolean stopAudio) {
        verifyIntentSent(
                hasAction(BluetoothHearingAid.ACTION_CONNECTION_STATE_CHANGED),
                hasExtra(BluetoothDevice.EXTRA_DEVICE, device),
                hasExtra(BluetoothProfile.EXTRA_STATE, newState),
                hasExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, prevState));

        if (newState == BluetoothProfile.STATE_CONNECTED) {
            // ActiveDeviceManager calls setActiveDevice when connected.
            mService.setActiveDevice(device);
        } else if (prevState == BluetoothProfile.STATE_CONNECTED) {
            if (mService.getConnectedDevices().isEmpty()) {
                mService.removeActiveDevice(stopAudio);
            }
        }
    }

    /** Test getting HearingAid Service: getHearingAidService() */
    @Test
    public void testGetHearingAidService() {
        assertThat(mService).isEqualTo(HearingAidService.getHearingAidService());
    }

    /** Test stop HearingAid Service */
    @Test
    public void testStopHearingAidService() {
        // Prepare: connect
        connectDevice(mLeftDevice);
        // HearingAid Service is already running: test stop(). Note: must be done on the main thread
        InstrumentationRegistry.getInstrumentation().runOnMainSync(mService::stop);
        // Try to restart the service. Note: must be done on the main thread
        InstrumentationRegistry.getInstrumentation().runOnMainSync(mService::start);
    }

    /** Test get/set priority for BluetoothDevice */
    @Test
    public void testGetSetPriority() throws Exception {
        when(mDatabaseManager.getProfileConnectionPolicy(mLeftDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
        // indirect call of mService.getConnectionPolicy to test BluetoothHearingAidBinder
        final SynchronousResultReceiver<Integer> recv = SynchronousResultReceiver.get();
        final int defaultRecvValue = -1000;
        mServiceBinder.getConnectionPolicy(mLeftDevice, null, recv);
        int connectionPolicy = recv.awaitResultNoInterrupt(TIMEOUT).getValue(defaultRecvValue);
        assertThat(connectionPolicy).isEqualTo(BluetoothProfile.CONNECTION_POLICY_UNKNOWN);

        when(mDatabaseManager.getProfileConnectionPolicy(mLeftDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);
        assertThat(mService.getConnectionPolicy(mLeftDevice))
                .isEqualTo(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);

        when(mDatabaseManager.getProfileConnectionPolicy(mLeftDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        assertThat(mService.getConnectionPolicy(mLeftDevice))
                .isEqualTo(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
    }

    /** Test okToConnect method using various test cases */
    @Test
    public void testOkToConnect() {
        int badPriorityValue = 1024;
        int badBondState = 42;
        testOkToConnectCase(
                mSingleDevice,
                BluetoothDevice.BOND_NONE,
                BluetoothProfile.CONNECTION_POLICY_UNKNOWN,
                false);
        testOkToConnectCase(
                mSingleDevice,
                BluetoothDevice.BOND_NONE,
                BluetoothProfile.CONNECTION_POLICY_FORBIDDEN,
                false);
        testOkToConnectCase(
                mSingleDevice,
                BluetoothDevice.BOND_NONE,
                BluetoothProfile.CONNECTION_POLICY_ALLOWED,
                false);
        testOkToConnectCase(mSingleDevice, BluetoothDevice.BOND_NONE, badPriorityValue, false);
        testOkToConnectCase(
                mSingleDevice,
                BluetoothDevice.BOND_BONDING,
                BluetoothProfile.CONNECTION_POLICY_UNKNOWN,
                false);
        testOkToConnectCase(
                mSingleDevice,
                BluetoothDevice.BOND_BONDING,
                BluetoothProfile.CONNECTION_POLICY_FORBIDDEN,
                false);
        testOkToConnectCase(
                mSingleDevice,
                BluetoothDevice.BOND_BONDING,
                BluetoothProfile.CONNECTION_POLICY_ALLOWED,
                false);
        testOkToConnectCase(mSingleDevice, BluetoothDevice.BOND_BONDING, badPriorityValue, false);
        testOkToConnectCase(
                mSingleDevice,
                BluetoothDevice.BOND_BONDED,
                BluetoothProfile.CONNECTION_POLICY_UNKNOWN,
                true);
        testOkToConnectCase(
                mSingleDevice,
                BluetoothDevice.BOND_BONDED,
                BluetoothProfile.CONNECTION_POLICY_FORBIDDEN,
                false);
        testOkToConnectCase(
                mSingleDevice,
                BluetoothDevice.BOND_BONDED,
                BluetoothProfile.CONNECTION_POLICY_ALLOWED,
                true);
        testOkToConnectCase(mSingleDevice, BluetoothDevice.BOND_BONDED, badPriorityValue, false);
        testOkToConnectCase(
                mSingleDevice, badBondState, BluetoothProfile.CONNECTION_POLICY_UNKNOWN, false);
        testOkToConnectCase(
                mSingleDevice, badBondState, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN, false);
        testOkToConnectCase(
                mSingleDevice, badBondState, BluetoothProfile.CONNECTION_POLICY_ALLOWED, false);
        testOkToConnectCase(mSingleDevice, badBondState, badPriorityValue, false);
    }

    /**
     * Test that an outgoing connection to device that does not have Hearing Aid UUID is rejected
     */
    @Test
    public void testOutgoingConnectMissingHearingAidUuid() {
        // Update the device priority so okToConnect() returns true
        when(mDatabaseManager.getProfileConnectionPolicy(mLeftDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        when(mDatabaseManager.getProfileConnectionPolicy(
                        mRightDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);
        when(mDatabaseManager.getProfileConnectionPolicy(
                        mSingleDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);
        doReturn(true).when(mNativeInterface).connectHearingAid(any(BluetoothDevice.class));
        doReturn(true).when(mNativeInterface).disconnectHearingAid(any(BluetoothDevice.class));

        // Return No UUID
        doReturn(new ParcelUuid[] {})
                .when(mAdapterService)
                .getRemoteUuids(any(BluetoothDevice.class));

        // Send a connect request
        assertThat(mService.connect(mLeftDevice)).isFalse();
    }

    /** Test that an outgoing connection to device with PRIORITY_OFF is rejected */
    @Test
    public void testOutgoingConnectPriorityOff() throws Exception {
        doReturn(true).when(mNativeInterface).connectHearingAid(any(BluetoothDevice.class));
        doReturn(true).when(mNativeInterface).disconnectHearingAid(any(BluetoothDevice.class));

        // Set the device priority to PRIORITY_OFF so connect() should fail
        when(mDatabaseManager.getProfileConnectionPolicy(mLeftDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);

        // Send a connect request via BluetoothHearingAidBinder
        final SynchronousResultReceiver<Boolean> recv = SynchronousResultReceiver.get();
        boolean defaultRecvValue = true;
        mServiceBinder.connect(mLeftDevice, null, recv);
        assertThat(recv.awaitResultNoInterrupt(TIMEOUT).getValue(defaultRecvValue)).isFalse();
    }

    /** Test that an outgoing connection times out */
    @Test
    public void testOutgoingConnectTimeout() throws Exception {
        // Update the device priority so okToConnect() returns true
        when(mDatabaseManager.getProfileConnectionPolicy(mLeftDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        when(mDatabaseManager.getProfileConnectionPolicy(
                        mRightDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);
        when(mDatabaseManager.getProfileConnectionPolicy(
                        mSingleDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);
        doReturn(true).when(mNativeInterface).connectHearingAid(any(BluetoothDevice.class));
        doReturn(true).when(mNativeInterface).disconnectHearingAid(any(BluetoothDevice.class));

        // Send a connect request
        assertThat(mService.connect(mLeftDevice)).isTrue();

        // Verify the connection state broadcast, and that we are in Connecting state
        verifyConnectionStateIntent(
                mLeftDevice,
                BluetoothProfile.STATE_CONNECTING,
                BluetoothProfile.STATE_DISCONNECTED);
        // indirect call of mService.getConnectionState to test BluetoothHearingAidBinder
        final SynchronousResultReceiver<Integer> recv = SynchronousResultReceiver.get();
        int defaultRecvValue = -1000;
        mServiceBinder.getConnectionState(mLeftDevice, null, recv);
        int connectionState = recv.awaitResultNoInterrupt(TIMEOUT).getValue(defaultRecvValue);
        assertThat(connectionState).isEqualTo(BluetoothProfile.STATE_CONNECTING);

        // Verify the connection state broadcast, and that we are in Disconnected state
        verifyConnectionStateIntent(
                mLeftDevice,
                BluetoothProfile.STATE_DISCONNECTED,
                BluetoothProfile.STATE_CONNECTING);
        assertThat(mService.getConnectionState(mLeftDevice))
                .isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
    }

    /** Test that the Hearing Aid Service connects to left and right device at the same time. */
    @Test
    public void testConnectAPair_connectBothDevices() {
        // Update hiSyncId map
        getHiSyncIdFromNative();
        // Update the device priority so okToConnect() returns true
        when(mDatabaseManager.getProfileConnectionPolicy(mLeftDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        when(mDatabaseManager.getProfileConnectionPolicy(
                        mRightDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        when(mDatabaseManager.getProfileConnectionPolicy(
                        mSingleDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);
        doReturn(true).when(mNativeInterface).connectHearingAid(any(BluetoothDevice.class));
        doReturn(true).when(mNativeInterface).disconnectHearingAid(any(BluetoothDevice.class));

        // Send a connect request
        assertThat(mService.connect(mLeftDevice)).isTrue();

        // Verify the connection state broadcast, and that we are in Connecting state
        verifyConnectionStateIntent(
                mLeftDevice,
                BluetoothProfile.STATE_CONNECTING,
                BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mService.getConnectionState(mLeftDevice))
                .isEqualTo(BluetoothProfile.STATE_CONNECTING);
        verifyConnectionStateIntent(
                mRightDevice,
                BluetoothProfile.STATE_CONNECTING,
                BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mService.getConnectionState(mRightDevice))
                .isEqualTo(BluetoothProfile.STATE_CONNECTING);
    }

    /** Test that the service disconnects the current pair before connecting to another pair. */
    @Test
    public void testConnectAnotherPair_disconnectCurrentPair() throws Exception {
        // Update hiSyncId map
        getHiSyncIdFromNative();
        // Update the device priority so okToConnect() returns true
        when(mDatabaseManager.getProfileConnectionPolicy(mLeftDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        when(mDatabaseManager.getProfileConnectionPolicy(
                        mRightDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        when(mDatabaseManager.getProfileConnectionPolicy(
                        mSingleDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        doReturn(true).when(mNativeInterface).connectHearingAid(any(BluetoothDevice.class));
        doReturn(true).when(mNativeInterface).disconnectHearingAid(any(BluetoothDevice.class));

        // Send a connect request
        assertThat(mService.connect(mLeftDevice)).isTrue();

        // Verify the connection state broadcast, and that we are in Connecting state
        verifyConnectionStateIntent(
                mLeftDevice,
                BluetoothProfile.STATE_CONNECTING,
                BluetoothProfile.STATE_DISCONNECTED);
        verifyConnectionStateIntent(
                mRightDevice,
                BluetoothProfile.STATE_CONNECTING,
                BluetoothProfile.STATE_DISCONNECTED);

        HearingAidStackEvent connCompletedEvent;
        // Send a message to trigger connection completed
        connCompletedEvent =
                new HearingAidStackEvent(HearingAidStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        connCompletedEvent.device = mLeftDevice;
        connCompletedEvent.valueInt1 = HearingAidStackEvent.CONNECTION_STATE_CONNECTED;
        mService.messageFromNative(connCompletedEvent);
        connCompletedEvent =
                new HearingAidStackEvent(HearingAidStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        connCompletedEvent.device = mRightDevice;
        connCompletedEvent.valueInt1 = HearingAidStackEvent.CONNECTION_STATE_CONNECTED;
        mService.messageFromNative(connCompletedEvent);

        // Verify the connection state broadcast, and that we are in Connected state for right side
        verifyConnectionStateIntent(
                mLeftDevice, BluetoothProfile.STATE_CONNECTED, BluetoothProfile.STATE_CONNECTING);
        verifyConnectionStateIntent(
                mRightDevice, BluetoothProfile.STATE_CONNECTED, BluetoothProfile.STATE_CONNECTING);

        // Send a connect request for another pair
        assertThat(mService.connect(mSingleDevice)).isTrue();

        // Verify the connection state broadcast, and that the first pair is in Disconnecting state
        verifyConnectionStateIntent(
                mRightDevice,
                BluetoothProfile.STATE_DISCONNECTING,
                BluetoothProfile.STATE_CONNECTED);
        verifyConnectionStateIntent(
                mLeftDevice,
                BluetoothProfile.STATE_DISCONNECTING,
                BluetoothProfile.STATE_CONNECTED);
        // indirect call of mService.getConnectedDevices to test BluetoothHearingAidBinder
        final SynchronousResultReceiver<List<BluetoothDevice>> recv =
                SynchronousResultReceiver.get();
        List<BluetoothDevice> defaultRecvValue = null;
        mServiceBinder.getConnectedDevices(null, recv);
        assertThat(recv.awaitResultNoInterrupt(TIMEOUT).getValue(defaultRecvValue))
                .doesNotContain(mLeftDevice);
        assertThat(mService.getConnectedDevices()).doesNotContain(mRightDevice);

        // Verify the connection state broadcast, and that the second device is in Connecting state
        verifyConnectionStateIntent(
                mSingleDevice,
                BluetoothProfile.STATE_CONNECTING,
                BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mService.getConnectionState(mSingleDevice))
                .isEqualTo(BluetoothProfile.STATE_CONNECTING);
    }

    /** Test that the outgoing connect/disconnect and audio switch is successful. */
    @Test
    public void testAudioManagerConnectDisconnect() throws Exception {
        // Update hiSyncId map
        getHiSyncIdFromNative();
        // Update the device priority so okToConnect() returns true
        when(mDatabaseManager.getProfileConnectionPolicy(mLeftDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        when(mDatabaseManager.getProfileConnectionPolicy(
                        mRightDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        when(mDatabaseManager.getProfileConnectionPolicy(
                        mSingleDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);
        doReturn(true).when(mNativeInterface).connectHearingAid(any(BluetoothDevice.class));
        doReturn(true).when(mNativeInterface).disconnectHearingAid(any(BluetoothDevice.class));

        // Send a connect request
        assertThat(mService.connect(mLeftDevice)).isTrue();
        assertThat(mService.connect(mRightDevice)).isTrue();

        // Verify the connection state broadcast, and that we are in Connecting state
        verifyConnectionStateIntent(
                mLeftDevice,
                BluetoothProfile.STATE_CONNECTING,
                BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mService.getConnectionState(mLeftDevice))
                .isEqualTo(BluetoothProfile.STATE_CONNECTING);
        verifyConnectionStateIntent(
                mRightDevice,
                BluetoothProfile.STATE_CONNECTING,
                BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mService.getConnectionState(mRightDevice))
                .isEqualTo(BluetoothProfile.STATE_CONNECTING);

        HearingAidStackEvent connCompletedEvent;
        // Send a message to trigger connection completed
        connCompletedEvent =
                new HearingAidStackEvent(HearingAidStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        connCompletedEvent.device = mLeftDevice;
        connCompletedEvent.valueInt1 = HearingAidStackEvent.CONNECTION_STATE_CONNECTED;
        mService.messageFromNative(connCompletedEvent);

        // Verify the connection state broadcast, and that we are in Connected state
        verifyConnectionStateIntent(
                mLeftDevice, BluetoothProfile.STATE_CONNECTED, BluetoothProfile.STATE_CONNECTING);
        assertThat(mService.getConnectionState(mLeftDevice))
                .isEqualTo(BluetoothProfile.STATE_CONNECTED);

        // Send a message to trigger connection completed for right side
        connCompletedEvent =
                new HearingAidStackEvent(HearingAidStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        connCompletedEvent.device = mRightDevice;
        connCompletedEvent.valueInt1 = HearingAidStackEvent.CONNECTION_STATE_CONNECTED;
        mService.messageFromNative(connCompletedEvent);

        // Verify the connection state broadcast, and that we are in Connected state for right side
        verifyConnectionStateIntent(
                mRightDevice, BluetoothProfile.STATE_CONNECTED, BluetoothProfile.STATE_CONNECTING);
        assertThat(mService.getConnectionState(mRightDevice))
                .isEqualTo(BluetoothProfile.STATE_CONNECTED);

        // Verify the list of connected devices
        assertThat(mService.getConnectedDevices()).containsAtLeast(mLeftDevice, mRightDevice);

        // Verify the audio is routed to Hearing Aid Profile
        verify(mAudioManager)
                .handleBluetoothActiveDeviceChanged(
                        eq(mLeftDevice), eq(null), any(BluetoothProfileConnectionInfo.class));

        // Send a disconnect request
        assertThat(mService.disconnect(mLeftDevice)).isTrue();
        // Send a disconnect request via BluetoothHearingAidBinder
        final SynchronousResultReceiver<Boolean> recv = SynchronousResultReceiver.get();
        boolean revalueRecvValue = false;
        mServiceBinder.disconnect(mRightDevice, null, recv);
        assertThat(recv.awaitResultNoInterrupt(TIMEOUT).getValue(revalueRecvValue)).isTrue();

        // Verify the connection state broadcast, and that we are in Disconnecting state
        verifyConnectionStateIntent(
                mRightDevice,
                BluetoothProfile.STATE_DISCONNECTING,
                BluetoothProfile.STATE_CONNECTED);
        assertThat(mService.getConnectionState(mRightDevice))
                .isEqualTo(BluetoothProfile.STATE_DISCONNECTING);
        verifyConnectionStateIntent(
                mLeftDevice,
                BluetoothProfile.STATE_DISCONNECTING,
                BluetoothProfile.STATE_CONNECTED);
        assertThat(mService.getConnectionState(mLeftDevice))
                .isEqualTo(BluetoothProfile.STATE_DISCONNECTING);

        // Send a message to trigger disconnection completed
        connCompletedEvent =
                new HearingAidStackEvent(HearingAidStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        connCompletedEvent.device = mLeftDevice;
        connCompletedEvent.valueInt1 = HearingAidStackEvent.CONNECTION_STATE_DISCONNECTED;
        mService.messageFromNative(connCompletedEvent);

        // Verify the connection state broadcast, and that we are in Disconnected state
        verifyConnectionStateIntent(
                mLeftDevice,
                BluetoothProfile.STATE_DISCONNECTED,
                BluetoothProfile.STATE_DISCONNECTING);
        assertThat(mService.getConnectionState(mLeftDevice))
                .isEqualTo(BluetoothProfile.STATE_DISCONNECTED);

        // Send a message to trigger disconnection completed to the right device
        connCompletedEvent =
                new HearingAidStackEvent(HearingAidStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        connCompletedEvent.device = mRightDevice;
        connCompletedEvent.valueInt1 = HearingAidStackEvent.CONNECTION_STATE_DISCONNECTED;
        mService.messageFromNative(connCompletedEvent);

        // Verify the connection state broadcast, and that we are in Disconnected state
        verifyConnectionStateIntent(
                mRightDevice,
                BluetoothProfile.STATE_DISCONNECTED,
                BluetoothProfile.STATE_DISCONNECTING);
        assertThat(mService.getConnectionState(mRightDevice))
                .isEqualTo(BluetoothProfile.STATE_DISCONNECTED);

        // Verify the list of connected devices
        assertThat(mService.getConnectedDevices()).containsNoneOf(mLeftDevice, mRightDevice);

        // Verify the audio is not routed to Hearing Aid Profile.
        // Music should be paused (i.e. should not suppress noisy intent)
        ArgumentCaptor<BluetoothProfileConnectionInfo> connectionInfoArgumentCaptor =
                ArgumentCaptor.forClass(BluetoothProfileConnectionInfo.class);
        verify(mAudioManager)
                .handleBluetoothActiveDeviceChanged(
                        eq(null), eq(mLeftDevice), connectionInfoArgumentCaptor.capture());
        BluetoothProfileConnectionInfo connectionInfo = connectionInfoArgumentCaptor.getValue();
        assertThat(connectionInfo.isSuppressNoisyIntent()).isFalse();
    }

    /**
     * Test that the noisy intent is suppressed when we call HearingAidService#removeActiveDevice()
     * with (stopAudio == false).
     */
    @Test
    public void testAudioManagerConnectDisconnect_suppressNoisyIntentCase() throws Exception {
        // Update hiSyncId map
        getHiSyncIdFromNative();
        // Update the device priority so okToConnect() returns true
        when(mDatabaseManager.getProfileConnectionPolicy(mLeftDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        doReturn(true).when(mNativeInterface).connectHearingAid(any(BluetoothDevice.class));
        doReturn(true).when(mNativeInterface).disconnectHearingAid(any(BluetoothDevice.class));

        // Send a connect request
        assertThat(mService.connect(mLeftDevice)).isTrue();

        // Verify the connection state broadcast, and that we are in Connecting state
        verifyConnectionStateIntent(
                mLeftDevice,
                BluetoothProfile.STATE_CONNECTING,
                BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mService.getConnectionState(mLeftDevice))
                .isEqualTo(BluetoothProfile.STATE_CONNECTING);

        HearingAidStackEvent connCompletedEvent;
        // Send a message to trigger connection completed
        connCompletedEvent =
                new HearingAidStackEvent(HearingAidStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        connCompletedEvent.device = mLeftDevice;
        connCompletedEvent.valueInt1 = HearingAidStackEvent.CONNECTION_STATE_CONNECTED;
        mService.messageFromNative(connCompletedEvent);

        // Verify the connection state broadcast, and that we are in Connected state
        verifyConnectionStateIntent(
                mLeftDevice, BluetoothProfile.STATE_CONNECTED, BluetoothProfile.STATE_CONNECTING);
        assertThat(mService.getConnectionState(mLeftDevice))
                .isEqualTo(BluetoothProfile.STATE_CONNECTED);

        // Verify the list of connected devices
        assertThat(mService.getConnectedDevices()).contains(mLeftDevice);

        // Verify the audio is routed to Hearing Aid Profile
        verify(mAudioManager)
                .handleBluetoothActiveDeviceChanged(
                        eq(mLeftDevice), eq(null), any(BluetoothProfileConnectionInfo.class));

        // Send a disconnect request
        assertThat(mService.disconnect(mLeftDevice)).isTrue();

        // Verify the connection state broadcast, and that we are in Disconnecting state
        // Note that we call verifyConnectionStateIntent() with (stopAudio == false).
        verifyConnectionStateIntent(
                mLeftDevice,
                BluetoothProfile.STATE_DISCONNECTING,
                BluetoothProfile.STATE_CONNECTED,
                false);
        assertThat(mService.getConnectionState(mLeftDevice))
                .isEqualTo(BluetoothProfile.STATE_DISCONNECTING);

        // Verify the audio is not routed to Hearing Aid Profile.
        // Note that music should be not paused (i.e. should suppress noisy intent)
        ArgumentCaptor<BluetoothProfileConnectionInfo> connectionInfoArgumentCaptor =
                ArgumentCaptor.forClass(BluetoothProfileConnectionInfo.class);
        verify(mAudioManager)
                .handleBluetoothActiveDeviceChanged(
                        eq(null), eq(mLeftDevice), connectionInfoArgumentCaptor.capture());
        BluetoothProfileConnectionInfo connectionInfo = connectionInfoArgumentCaptor.getValue();
        assertThat(connectionInfo.isSuppressNoisyIntent()).isTrue();
    }

    /**
     * Test that only CONNECTION_STATE_CONNECTED or CONNECTION_STATE_CONNECTING Hearing Aid stack
     * events will create a state machine.
     */
    @Test
    public void testCreateStateMachineStackEvents() {
        // Update the device priority so okToConnect() returns true
        when(mDatabaseManager.getProfileConnectionPolicy(mLeftDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        when(mDatabaseManager.getProfileConnectionPolicy(
                        mRightDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);
        when(mDatabaseManager.getProfileConnectionPolicy(
                        mSingleDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);
        doReturn(true).when(mNativeInterface).connectHearingAid(any(BluetoothDevice.class));
        doReturn(true).when(mNativeInterface).disconnectHearingAid(any(BluetoothDevice.class));

        // Hearing Aid stack event: CONNECTION_STATE_CONNECTING - state machine should be created
        generateConnectionMessageFromNative(
                mLeftDevice,
                BluetoothProfile.STATE_CONNECTING,
                BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mService.getConnectionState(mLeftDevice))
                .isEqualTo(BluetoothProfile.STATE_CONNECTING);
        assertThat(mService.getDevices()).contains(mLeftDevice);

        // HearingAid stack event: CONNECTION_STATE_DISCONNECTED - state machine should be removed
        generateConnectionMessageFromNative(
                mLeftDevice,
                BluetoothProfile.STATE_DISCONNECTED,
                BluetoothProfile.STATE_CONNECTING);
        assertThat(mService.getConnectionState(mLeftDevice))
                .isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mService.getDevices()).contains(mLeftDevice);
        mService.bondStateChanged(mLeftDevice, BluetoothDevice.BOND_NONE);
        assertThat(mService.getDevices()).doesNotContain(mLeftDevice);

        // stack event: CONNECTION_STATE_CONNECTED - state machine should be created
        generateConnectionMessageFromNative(
                mLeftDevice, BluetoothProfile.STATE_CONNECTED, BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mService.getConnectionState(mLeftDevice))
                .isEqualTo(BluetoothProfile.STATE_CONNECTED);
        assertThat(mService.getDevices()).contains(mLeftDevice);

        // stack event: CONNECTION_STATE_DISCONNECTED - state machine should be removed
        generateConnectionMessageFromNative(
                mLeftDevice, BluetoothProfile.STATE_DISCONNECTED, BluetoothProfile.STATE_CONNECTED);
        assertThat(mService.getConnectionState(mLeftDevice))
                .isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mService.getDevices()).contains(mLeftDevice);
        mService.bondStateChanged(mLeftDevice, BluetoothDevice.BOND_NONE);
        assertThat(mService.getDevices()).doesNotContain(mLeftDevice);

        // stack event: CONNECTION_STATE_DISCONNECTING - state machine should not be created
        generateUnexpectedConnectionMessageFromNative(
                mLeftDevice, BluetoothProfile.STATE_DISCONNECTING);
        assertThat(mService.getConnectionState(mLeftDevice))
                .isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mService.getDevices()).doesNotContain(mLeftDevice);

        // stack event: CONNECTION_STATE_DISCONNECTED - state machine should not be created
        generateUnexpectedConnectionMessageFromNative(
                mLeftDevice, BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mService.getConnectionState(mLeftDevice))
                .isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mService.getDevices()).doesNotContain(mLeftDevice);
    }

    /**
     * Test that a state machine in DISCONNECTED state is removed only after the device is unbond.
     */
    @Test
    public void testDeleteStateMachineUnbondEvents() {
        // Update the device priority so okToConnect() returns true
        when(mDatabaseManager.getProfileConnectionPolicy(mLeftDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        when(mDatabaseManager.getProfileConnectionPolicy(
                        mRightDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);
        when(mDatabaseManager.getProfileConnectionPolicy(
                        mSingleDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);
        doReturn(true).when(mNativeInterface).connectHearingAid(any(BluetoothDevice.class));
        doReturn(true).when(mNativeInterface).disconnectHearingAid(any(BluetoothDevice.class));

        // HearingAid stack event: CONNECTION_STATE_CONNECTING - state machine should be created
        generateConnectionMessageFromNative(
                mLeftDevice,
                BluetoothProfile.STATE_CONNECTING,
                BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mService.getConnectionState(mLeftDevice))
                .isEqualTo(BluetoothProfile.STATE_CONNECTING);
        assertThat(mService.getDevices()).contains(mLeftDevice);
        // Device unbond - state machine is not removed
        mService.bondStateChanged(mLeftDevice, BluetoothDevice.BOND_NONE);
        assertThat(mService.getDevices()).contains(mLeftDevice);

        // HearingAid stack event: CONNECTION_STATE_CONNECTED - state machine is not removed
        mService.bondStateChanged(mLeftDevice, BluetoothDevice.BOND_BONDED);
        generateConnectionMessageFromNative(
                mLeftDevice, BluetoothProfile.STATE_CONNECTED, BluetoothProfile.STATE_CONNECTING);
        assertThat(mService.getConnectionState(mLeftDevice))
                .isEqualTo(BluetoothProfile.STATE_CONNECTED);
        assertThat(mService.getDevices()).contains(mLeftDevice);
        // Device unbond - state machine is not removed
        mService.bondStateChanged(mLeftDevice, BluetoothDevice.BOND_NONE);
        assertThat(mService.getDevices()).contains(mLeftDevice);

        // HearingAid stack event: CONNECTION_STATE_DISCONNECTING - state machine is not removed
        mService.bondStateChanged(mLeftDevice, BluetoothDevice.BOND_BONDED);
        generateConnectionMessageFromNative(
                mLeftDevice,
                BluetoothProfile.STATE_DISCONNECTING,
                BluetoothProfile.STATE_CONNECTED);
        assertThat(mService.getConnectionState(mLeftDevice))
                .isEqualTo(BluetoothProfile.STATE_DISCONNECTING);
        assertThat(mService.getDevices()).contains(mLeftDevice);
        // Device unbond - state machine is not removed
        mService.bondStateChanged(mLeftDevice, BluetoothDevice.BOND_NONE);
        assertThat(mService.getDevices()).contains(mLeftDevice);

        // HearingAid stack event: CONNECTION_STATE_DISCONNECTED - state machine is not removed
        mService.bondStateChanged(mLeftDevice, BluetoothDevice.BOND_BONDED);
        generateConnectionMessageFromNative(
                mLeftDevice,
                BluetoothProfile.STATE_DISCONNECTED,
                BluetoothProfile.STATE_DISCONNECTING);
        assertThat(mService.getConnectionState(mLeftDevice))
                .isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mService.getDevices()).contains(mLeftDevice);
        // Device unbond - state machine is removed
        mService.bondStateChanged(mLeftDevice, BluetoothDevice.BOND_NONE);
        assertThat(mService.getDevices()).doesNotContain(mLeftDevice);
    }

    /**
     * Test that a CONNECTION_STATE_DISCONNECTED Hearing Aid stack event will remove the state
     * machine only if the device is unbond.
     */
    @Test
    public void testDeleteStateMachineDisconnectEvents() {
        // Update the device priority so okToConnect() returns true
        when(mDatabaseManager.getProfileConnectionPolicy(mLeftDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        when(mDatabaseManager.getProfileConnectionPolicy(
                        mRightDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);
        when(mDatabaseManager.getProfileConnectionPolicy(
                        mSingleDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);
        doReturn(true).when(mNativeInterface).connectHearingAid(any(BluetoothDevice.class));
        doReturn(true).when(mNativeInterface).disconnectHearingAid(any(BluetoothDevice.class));

        // HearingAid stack event: CONNECTION_STATE_CONNECTING - state machine should be created
        generateConnectionMessageFromNative(
                mLeftDevice,
                BluetoothProfile.STATE_CONNECTING,
                BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mService.getConnectionState(mLeftDevice))
                .isEqualTo(BluetoothProfile.STATE_CONNECTING);
        assertThat(mService.getDevices()).contains(mLeftDevice);

        // HearingAid stack event: CONNECTION_STATE_DISCONNECTED - state machine is not removed
        generateConnectionMessageFromNative(
                mLeftDevice,
                BluetoothProfile.STATE_DISCONNECTED,
                BluetoothProfile.STATE_CONNECTING);
        assertThat(mService.getConnectionState(mLeftDevice))
                .isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mService.getDevices()).contains(mLeftDevice);

        // HearingAid stack event: CONNECTION_STATE_CONNECTING - state machine remains
        generateConnectionMessageFromNative(
                mLeftDevice,
                BluetoothProfile.STATE_CONNECTING,
                BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mService.getConnectionState(mLeftDevice))
                .isEqualTo(BluetoothProfile.STATE_CONNECTING);
        assertThat(mService.getDevices()).contains(mLeftDevice);

        // Device bond state marked as unbond - state machine is not removed
        doReturn(BluetoothDevice.BOND_NONE)
                .when(mAdapterService)
                .getBondState(any(BluetoothDevice.class));
        assertThat(mService.getDevices()).contains(mLeftDevice);

        // HearingAid stack event: CONNECTION_STATE_DISCONNECTED - state machine is removed
        generateConnectionMessageFromNative(
                mLeftDevice,
                BluetoothProfile.STATE_DISCONNECTED,
                BluetoothProfile.STATE_CONNECTING);
        assertThat(mService.getConnectionState(mLeftDevice))
                .isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mService.getDevices()).doesNotContain(mLeftDevice);
    }

    @Test
    public void testConnectionStateChangedActiveDevice() throws Exception {
        // Update hiSyncId map
        getHiSyncIdFromNative();
        // Update the device priority so okToConnect() returns true
        when(mDatabaseManager.getProfileConnectionPolicy(mLeftDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        when(mDatabaseManager.getProfileConnectionPolicy(
                        mRightDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        when(mDatabaseManager.getProfileConnectionPolicy(
                        mSingleDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);

        generateConnectionMessageFromNative(
                mRightDevice,
                BluetoothProfile.STATE_CONNECTED,
                BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mService.getActiveDevices()).contains(mRightDevice);

        // indirect call of mService.getActiveDevices to test BluetoothHearingAidBinder
        final SynchronousResultReceiver<List<BluetoothDevice>> recv =
                SynchronousResultReceiver.get();
        List<BluetoothDevice> defaultRecvValue = null;
        mServiceBinder.getActiveDevices(null, recv);
        assertThat(recv.awaitResultNoInterrupt(TIMEOUT).getValue(defaultRecvValue))
                .doesNotContain(mLeftDevice);

        generateConnectionMessageFromNative(
                mLeftDevice, BluetoothProfile.STATE_CONNECTED, BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mService.getActiveDevices()).containsExactly(mRightDevice, mLeftDevice);

        generateConnectionMessageFromNative(
                mRightDevice,
                BluetoothProfile.STATE_DISCONNECTED,
                BluetoothProfile.STATE_CONNECTED);
        assertThat(mService.getActiveDevices()).doesNotContain(mRightDevice);
        assertThat(mService.getActiveDevices()).contains(mLeftDevice);

        generateConnectionMessageFromNative(
                mLeftDevice, BluetoothProfile.STATE_DISCONNECTED, BluetoothProfile.STATE_CONNECTED);
        assertThat(mService.getActiveDevices()).containsNoneOf(mRightDevice, mLeftDevice);
    }

    @Test
    public void testConnectionStateChangedAnotherActiveDevice() throws Exception {
        // Update hiSyncId map
        getHiSyncIdFromNative();
        // Update the device priority so okToConnect() returns true
        when(mDatabaseManager.getProfileConnectionPolicy(mLeftDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        when(mDatabaseManager.getProfileConnectionPolicy(
                        mRightDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        when(mDatabaseManager.getProfileConnectionPolicy(
                        mSingleDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);

        generateConnectionMessageFromNative(
                mRightDevice,
                BluetoothProfile.STATE_CONNECTED,
                BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mService.getActiveDevices()).contains(mRightDevice);
        assertThat(mService.getActiveDevices()).doesNotContain(mLeftDevice);

        generateConnectionMessageFromNative(
                mLeftDevice, BluetoothProfile.STATE_CONNECTED, BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mService.getActiveDevices()).containsExactly(mRightDevice, mLeftDevice);

        generateConnectionMessageFromNative(
                mSingleDevice,
                BluetoothProfile.STATE_CONNECTED,
                BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mService.getActiveDevices()).containsNoneOf(mRightDevice, mLeftDevice);
        assertThat(mService.getActiveDevices()).contains(mSingleDevice);

        SynchronousResultReceiver<Boolean> recv = SynchronousResultReceiver.get();
        boolean defaultRecvValue = false;
        mServiceBinder.setActiveDevice(null, null, recv);
        assertThat(recv.awaitResultNoInterrupt(TIMEOUT).getValue(defaultRecvValue)).isTrue();
        assertThat(mService.getActiveDevices()).doesNotContain(mSingleDevice);

        mSetFlagsRule.enableFlags(Flags.FLAG_AUDIO_ROUTING_CENTRALIZATION);
        recv = SynchronousResultReceiver.get();
        mServiceBinder.setActiveDevice(null, null, recv);
        verify(mAudioRoutingManager)
                .activateDeviceProfile(null, BluetoothProfile.HEARING_AID, recv);
    }

    /**
     * Verify the correctness during first time connection. Connect to left device -> Get left
     * device hiSyncId -> Connect to right device -> Get right device hiSyncId -> Both devices
     * should be always connected
     */
    @Test
    public void firstTimeConnection_shouldConnectToBothDevices() {
        // Update the device priority so okToConnect() returns true
        when(mDatabaseManager.getProfileConnectionPolicy(mLeftDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        when(mDatabaseManager.getProfileConnectionPolicy(
                        mRightDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        doReturn(true).when(mNativeInterface).connectHearingAid(any(BluetoothDevice.class));
        doReturn(true).when(mNativeInterface).disconnectHearingAid(any(BluetoothDevice.class));
        // Send a connect request for left device
        assertThat(mService.connect(mLeftDevice)).isTrue();
        // Verify the connection state broadcast, and that we are in Connecting state
        verifyConnectionStateIntent(
                mLeftDevice,
                BluetoothProfile.STATE_CONNECTING,
                BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mService.getConnectionState(mLeftDevice))
                .isEqualTo(BluetoothProfile.STATE_CONNECTING);

        HearingAidStackEvent connCompletedEvent;
        // Send a message to trigger connection completed
        connCompletedEvent =
                new HearingAidStackEvent(HearingAidStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        connCompletedEvent.device = mLeftDevice;
        connCompletedEvent.valueInt1 = HearingAidStackEvent.CONNECTION_STATE_CONNECTED;
        mService.messageFromNative(connCompletedEvent);

        // Verify the connection state broadcast, and that we are in Connected state
        verifyConnectionStateIntent(
                mLeftDevice, BluetoothProfile.STATE_CONNECTED, BluetoothProfile.STATE_CONNECTING);
        assertThat(mService.getConnectionState(mLeftDevice))
                .isEqualTo(BluetoothProfile.STATE_CONNECTED);

        // Get hiSyncId for left device
        HearingAidStackEvent hiSyncIdEvent =
                new HearingAidStackEvent(HearingAidStackEvent.EVENT_TYPE_DEVICE_AVAILABLE);
        hiSyncIdEvent.device = mLeftDevice;
        hiSyncIdEvent.valueInt1 = 0x02;
        hiSyncIdEvent.valueLong2 = 0x0101;
        mService.messageFromNative(hiSyncIdEvent);

        // Send a connect request for right device
        assertThat(mService.connect(mRightDevice)).isTrue();
        // Verify the connection state broadcast, and that we are in Connecting state
        verifyConnectionStateIntent(
                mRightDevice,
                BluetoothProfile.STATE_CONNECTING,
                BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mService.getConnectionState(mRightDevice))
                .isEqualTo(BluetoothProfile.STATE_CONNECTING);
        // Verify the left device is still connected
        assertThat(mService.getConnectionState(mLeftDevice))
                .isEqualTo(BluetoothProfile.STATE_CONNECTED);

        // Send a message to trigger connection completed
        connCompletedEvent =
                new HearingAidStackEvent(HearingAidStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        connCompletedEvent.device = mRightDevice;
        connCompletedEvent.valueInt1 = HearingAidStackEvent.CONNECTION_STATE_CONNECTED;
        mService.messageFromNative(connCompletedEvent);

        // Verify the connection state broadcast, and that we are in Connected state
        verifyConnectionStateIntent(
                mRightDevice, BluetoothProfile.STATE_CONNECTED, BluetoothProfile.STATE_CONNECTING);
        assertThat(mService.getConnectionState(mRightDevice))
                .isEqualTo(BluetoothProfile.STATE_CONNECTED);
        assertThat(mService.getConnectionState(mLeftDevice))
                .isEqualTo(BluetoothProfile.STATE_CONNECTED);

        // Get hiSyncId for right device
        hiSyncIdEvent = new HearingAidStackEvent(HearingAidStackEvent.EVENT_TYPE_DEVICE_AVAILABLE);
        hiSyncIdEvent.device = mRightDevice;
        hiSyncIdEvent.valueInt1 = 0x02;
        hiSyncIdEvent.valueLong2 = 0x0101;
        mService.messageFromNative(hiSyncIdEvent);

        assertThat(mService.getConnectionState(mRightDevice))
                .isEqualTo(BluetoothProfile.STATE_CONNECTED);
        assertThat(mService.getConnectionState(mLeftDevice))
                .isEqualTo(BluetoothProfile.STATE_CONNECTED);
    }

    /** Get the HiSyncId from native stack after connecting to left device, then connect right */
    @Test
    public void getHiSyncId_afterFirstDeviceConnected() {
        // Update the device priority so okToConnect() returns true
        when(mDatabaseManager.getProfileConnectionPolicy(mLeftDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        when(mDatabaseManager.getProfileConnectionPolicy(
                        mRightDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        when(mDatabaseManager.getProfileConnectionPolicy(
                        mSingleDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        doReturn(true).when(mNativeInterface).connectHearingAid(any(BluetoothDevice.class));
        doReturn(true).when(mNativeInterface).disconnectHearingAid(any(BluetoothDevice.class));
        // Send a connect request
        assertThat(mService.connect(mLeftDevice)).isTrue();
        // Verify the connection state broadcast, and that we are in Connecting state
        verifyConnectionStateIntent(
                mLeftDevice,
                BluetoothProfile.STATE_CONNECTING,
                BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mService.getConnectionState(mLeftDevice))
                .isEqualTo(BluetoothProfile.STATE_CONNECTING);
        assertThat(mService.getConnectionState(mRightDevice))
                .isEqualTo(BluetoothProfile.STATE_DISCONNECTED);

        HearingAidStackEvent connCompletedEvent;
        // Send a message to trigger connection completed
        connCompletedEvent =
                new HearingAidStackEvent(HearingAidStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        connCompletedEvent.device = mLeftDevice;
        connCompletedEvent.valueInt1 = HearingAidStackEvent.CONNECTION_STATE_CONNECTED;
        mService.messageFromNative(connCompletedEvent);
        // Verify the connection state broadcast, and that we are in Connected state
        verifyConnectionStateIntent(
                mLeftDevice, BluetoothProfile.STATE_CONNECTED, BluetoothProfile.STATE_CONNECTING);
        assertThat(mService.getConnectionState(mLeftDevice))
                .isEqualTo(BluetoothProfile.STATE_CONNECTED);

        // Get hiSyncId update from native stack
        getHiSyncIdFromNative();
        // Send a connect request for right
        assertThat(mService.connect(mRightDevice)).isTrue();
        // Verify the connection state broadcast, and that we are in Connecting state
        verifyConnectionStateIntent(
                mRightDevice,
                BluetoothProfile.STATE_CONNECTING,
                BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mService.getConnectionState(mRightDevice))
                .isEqualTo(BluetoothProfile.STATE_CONNECTING);
        // Verify the left device is still connected
        assertThat(mService.getConnectionState(mLeftDevice))
                .isEqualTo(BluetoothProfile.STATE_CONNECTED);

        // Send a message to trigger connection completed
        connCompletedEvent =
                new HearingAidStackEvent(HearingAidStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        connCompletedEvent.device = mRightDevice;
        connCompletedEvent.valueInt1 = HearingAidStackEvent.CONNECTION_STATE_CONNECTED;
        mService.messageFromNative(connCompletedEvent);

        // Verify the connection state broadcast, and that we are in Connected state
        verifyConnectionStateIntent(
                mRightDevice, BluetoothProfile.STATE_CONNECTED, BluetoothProfile.STATE_CONNECTING);
        assertThat(mService.getConnectionState(mRightDevice))
                .isEqualTo(BluetoothProfile.STATE_CONNECTED);
        assertThat(mService.getConnectionState(mLeftDevice))
                .isEqualTo(BluetoothProfile.STATE_CONNECTED);
    }

    /** Test that the service can update HiSyncId from native message */
    @Test
    public void getHiSyncIdFromNative_addToMap() throws Exception {
        getHiSyncIdFromNative();
        assertThat(mService.getHiSyncIdMap()).containsKey(mLeftDevice);
        assertThat(mService.getHiSyncIdMap()).containsKey(mRightDevice);
        assertThat(mService.getHiSyncIdMap()).containsKey(mSingleDevice);

        SynchronousResultReceiver<Long> recv = SynchronousResultReceiver.get();
        long defaultRecvValue = -1000;
        mServiceBinder.getHiSyncId(mLeftDevice, null, recv);
        long id = recv.awaitResultNoInterrupt(TIMEOUT).getValue(defaultRecvValue);
        assertThat(id).isNotEqualTo(BluetoothHearingAid.HI_SYNC_ID_INVALID);

        recv = SynchronousResultReceiver.get();
        mServiceBinder.getHiSyncId(mRightDevice, null, recv);
        id = recv.awaitResultNoInterrupt(TIMEOUT).getValue(defaultRecvValue);
        assertThat(id).isNotEqualTo(BluetoothHearingAid.HI_SYNC_ID_INVALID);

        recv = SynchronousResultReceiver.get();
        mServiceBinder.getHiSyncId(mSingleDevice, null, recv);
        id = recv.awaitResultNoInterrupt(TIMEOUT).getValue(defaultRecvValue);
        assertThat(id).isNotEqualTo(BluetoothHearingAid.HI_SYNC_ID_INVALID);
    }

    /** Test that the service removes the device from HiSyncIdMap when it's unbonded */
    @Test
    public void deviceUnbonded_removeHiSyncId() {
        getHiSyncIdFromNative();
        mService.bondStateChanged(mLeftDevice, BluetoothDevice.BOND_NONE);
        assertThat(mService.getHiSyncIdMap()).doesNotContainKey(mLeftDevice);
    }

    @Test
    public void serviceBinder_callGetDeviceMode() throws Exception {
        final SynchronousResultReceiver<Integer> recv = SynchronousResultReceiver.get();
        mServiceBinder.getDeviceMode(mSingleDevice, null, recv);
        int mode = recv.awaitResultNoInterrupt(TIMEOUT).getValue(BluetoothHearingAid.MODE_UNKNOWN);
        // return unknown value if no device connected
        assertThat(mode).isEqualTo(BluetoothHearingAid.MODE_UNKNOWN);
    }

    @Test
    public void serviceBinder_callGetDeviceSide() throws Exception {
        final SynchronousResultReceiver<Integer> recv = SynchronousResultReceiver.get();
        mServiceBinder.getDeviceSide(mSingleDevice, null, recv);
        int side = recv.awaitResultNoInterrupt(TIMEOUT).getValue(BluetoothHearingAid.SIDE_UNKNOWN);

        // return unknown value if no device connected
        assertThat(side).isEqualTo(BluetoothHearingAid.SIDE_UNKNOWN);
    }

    @Test
    public void serviceBinder_setConnectionPolicy() throws Exception {
        when(mDatabaseManager.setProfileConnectionPolicy(
                        mSingleDevice,
                        BluetoothProfile.HEARING_AID,
                        BluetoothProfile.CONNECTION_POLICY_UNKNOWN))
                .thenReturn(true);

        final SynchronousResultReceiver<Boolean> recv = SynchronousResultReceiver.get();
        boolean defaultRecvValue = false;
        mServiceBinder.setConnectionPolicy(
                mSingleDevice, BluetoothProfile.CONNECTION_POLICY_UNKNOWN, null, recv);
        assertThat(recv.awaitResultNoInterrupt(TIMEOUT).getValue(defaultRecvValue)).isTrue();
        verify(mDatabaseManager)
                .setProfileConnectionPolicy(
                        mSingleDevice,
                        BluetoothProfile.HEARING_AID,
                        BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
    }

    @Test
    public void serviceBinder_setVolume() throws Exception {
        final SynchronousResultReceiver<Void> recv = SynchronousResultReceiver.get();
        mServiceBinder.setVolume(0, null, recv);
        recv.awaitResultNoInterrupt(TIMEOUT);
        verify(mNativeInterface).setVolume(0);
    }

    @Test
    public void dump_doesNotCrash() {
        // Update the device priority so okToConnect() returns true
        when(mDatabaseManager.getProfileConnectionPolicy(
                        mSingleDevice, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        doReturn(true).when(mNativeInterface).connectHearingAid(any(BluetoothDevice.class));

        // Send a connect request
        mService.connect(mSingleDevice);

        mService.dump(new StringBuilder());
    }

    private void connectDevice(BluetoothDevice device) {
        HearingAidStackEvent connCompletedEvent;

        List<BluetoothDevice> prevConnectedDevices = mService.getConnectedDevices();

        // Update the device priority so okToConnect() returns true
        when(mDatabaseManager.getProfileConnectionPolicy(device, BluetoothProfile.HEARING_AID))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        doReturn(true).when(mNativeInterface).connectHearingAid(device);
        doReturn(true).when(mNativeInterface).disconnectHearingAid(device);

        // Send a connect request
        assertThat(mService.connect(device)).isTrue();

        // Verify the connection state broadcast, and that we are in Connecting state
        verifyConnectionStateIntent(
                device, BluetoothProfile.STATE_CONNECTING, BluetoothProfile.STATE_DISCONNECTED);
        assertThat(mService.getConnectionState(device))
                .isEqualTo(BluetoothProfile.STATE_CONNECTING);

        // Send a message to trigger connection completed
        connCompletedEvent =
                new HearingAidStackEvent(HearingAidStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        connCompletedEvent.device = device;
        connCompletedEvent.valueInt1 = HearingAidStackEvent.CONNECTION_STATE_CONNECTED;
        mService.messageFromNative(connCompletedEvent);

        // Verify the connection state broadcast, and that we are in Connected state
        verifyConnectionStateIntent(
                device, BluetoothProfile.STATE_CONNECTED, BluetoothProfile.STATE_CONNECTING);
        assertThat(mService.getConnectionState(device)).isEqualTo(BluetoothProfile.STATE_CONNECTED);

        // Verify that the device is in the list of connected devices
        assertThat(mService.getConnectedDevices()).contains(device);
        // Verify the list of previously connected devices
        for (BluetoothDevice prevDevice : prevConnectedDevices) {
            assertThat(mService.getConnectedDevices()).contains(prevDevice);
        }
    }

    private void generateConnectionMessageFromNative(
            BluetoothDevice device, int newConnectionState, int oldConnectionState) {
        HearingAidStackEvent stackEvent =
                new HearingAidStackEvent(HearingAidStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        stackEvent.device = device;
        stackEvent.valueInt1 = newConnectionState;
        mService.messageFromNative(stackEvent);
        // Verify the connection state broadcast
        verifyConnectionStateIntent(device, newConnectionState, oldConnectionState);
    }

    private void generateUnexpectedConnectionMessageFromNative(
            BluetoothDevice device, int newConnectionState) {
        HearingAidStackEvent stackEvent =
                new HearingAidStackEvent(HearingAidStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        stackEvent.device = device;
        stackEvent.valueInt1 = newConnectionState;
        mService.messageFromNative(stackEvent);
        // Verify the connection state broadcast
        mInOrder.verify(mContext, times(0))
                .sendBroadcast(
                        MockitoHamcrest.argThat(
                                hasAction(BluetoothHearingAid.ACTION_CONNECTION_STATE_CHANGED)),
                        any(),
                        any());
    }

    /**
     * Helper function to test okToConnect() method
     *
     * @param device test device
     * @param bondState bond state value, could be invalid
     * @param priority value, could be invalid, could be invalid
     * @param expected expected result from okToConnect()
     */
    private void testOkToConnectCase(
            BluetoothDevice device, int bondState, int priority, boolean expected) {
        doReturn(bondState).when(mAdapterService).getBondState(device);
        when(mDatabaseManager.getProfileConnectionPolicy(device, BluetoothProfile.HEARING_AID))
                .thenReturn(priority);
        assertThat(mService.okToConnect(device)).isEqualTo(expected);
    }

    private void getHiSyncIdFromNative() {
        HearingAidStackEvent event =
                new HearingAidStackEvent(HearingAidStackEvent.EVENT_TYPE_DEVICE_AVAILABLE);
        event.device = mLeftDevice;
        event.valueInt1 = 0x02;
        event.valueLong2 = 0x0101;
        mService.messageFromNative(event);
        event.device = mRightDevice;
        event.valueInt1 = 0x03;
        mService.messageFromNative(event);
        event.device = mSingleDevice;
        event.valueInt1 = 0x00;
        event.valueLong2 = 0x0102;
        mService.messageFromNative(event);
    }
}
