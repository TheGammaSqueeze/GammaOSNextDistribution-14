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

package com.android.bluetooth.btservice;

import static android.bluetooth.IBluetoothLeAudio.LE_AUDIO_GROUP_ID_INVALID;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSinkAudioPolicy;
import android.content.Context;
import android.media.AudioManager;
import android.os.test.TestLooper;
import android.util.ArrayMap;
import android.util.SparseIntArray;

import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.BluetoothMethodProxy;
import com.android.bluetooth.TestUtils;
import com.android.bluetooth.Utils;
import com.android.bluetooth.a2dp.A2dpService;
import com.android.bluetooth.btservice.storage.DatabaseManager;
import com.android.bluetooth.hearingaid.HearingAidService;
import com.android.bluetooth.hfp.HeadsetService;
import com.android.bluetooth.le_audio.LeAudioService;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class AudioRoutingManagerTest {
    private BluetoothAdapter mAdapter;
    private BluetoothDevice mA2dpDevice;
    private BluetoothDevice mHeadsetDevice;
    private BluetoothDevice mA2dpHeadsetDevice;
    private BluetoothDevice mHearingAidDevice;
    private BluetoothDevice mLeAudioDevice;
    private BluetoothDevice mLeAudioDevice2;
    private BluetoothDevice mSecondaryAudioDevice;
    private BluetoothDevice mDualModeAudioDevice;
    private ArrayList<BluetoothDevice> mDeviceConnectionStack;
    private BluetoothDevice mMostRecentDevice;
    private AudioRoutingManager mAudioRoutingManager;
    private static final long HEARING_AID_HISYNC_ID = 1010;

    private static final int TIMEOUT_MS = 1_000;
    private static final int A2DP_HFP_SYNC_CONNECTION_TIMEOUT_MS =
            AudioRoutingManager.A2DP_HFP_SYNC_CONNECTION_TIMEOUT_MS + 2_000;
    private boolean mOriginalDualModeAudioState;
    private TestDatabaseManager mDatabaseManager;
    private TestLooper mTestLooper;

    @Mock private AdapterService mAdapterService;
    @Mock private ServiceFactory mServiceFactory;
    @Mock private A2dpService mA2dpService;
    @Mock private HeadsetService mHeadsetService;
    @Mock private HearingAidService mHearingAidService;
    @Mock private LeAudioService mLeAudioService;
    @Mock private AudioManager mAudioManager;
    @Spy private BluetoothMethodProxy mMethodProxy = BluetoothMethodProxy.getInstance();

    @Before
    public void setUp() throws Exception {
        // Set up mocks and test assets
        MockitoAnnotations.initMocks(this);
        mTestLooper = new TestLooper();
        BluetoothMethodProxy.setInstanceForTesting(mMethodProxy);
        doReturn(mTestLooper.getLooper()).when(mMethodProxy).handlerThreadGetLooper(any());
        doReturn(Collections.EMPTY_LIST)
                .when(mMethodProxy)
                .mediaSessionManagerGetActiveSessions(any());
        doNothing().when(mMethodProxy).threadStart(any());
        TestUtils.setAdapterService(mAdapterService);

        mDatabaseManager = new TestDatabaseManager(mAdapterService);

        when(mAdapterService.getSystemService(Context.AUDIO_SERVICE)).thenReturn(mAudioManager);
        when(mAdapterService.getSystemServiceName(AudioManager.class))
                .thenReturn(Context.AUDIO_SERVICE);
        when(mAdapterService.getDatabase()).thenReturn(mDatabaseManager);
        when(mServiceFactory.getA2dpService()).thenReturn(mA2dpService);
        when(mServiceFactory.getHeadsetService()).thenReturn(mHeadsetService);
        when(mServiceFactory.getHearingAidService()).thenReturn(mHearingAidService);
        when(mServiceFactory.getLeAudioService()).thenReturn(mLeAudioService);

        mAudioRoutingManager = new AudioRoutingManager(mAdapterService, mServiceFactory);
        mAudioRoutingManager.start();
        mAdapter = BluetoothAdapter.getDefaultAdapter();

        // Get devices for testing
        mA2dpDevice = TestUtils.getTestDevice(mAdapter, 0);
        mHeadsetDevice = TestUtils.getTestDevice(mAdapter, 1);
        mA2dpHeadsetDevice = TestUtils.getTestDevice(mAdapter, 2);
        mHearingAidDevice = TestUtils.getTestDevice(mAdapter, 3);
        mLeAudioDevice = TestUtils.getTestDevice(mAdapter, 4);
        mSecondaryAudioDevice = TestUtils.getTestDevice(mAdapter, 5);
        mDualModeAudioDevice = TestUtils.getTestDevice(mAdapter, 6);
        mLeAudioDevice2 = TestUtils.getTestDevice(mAdapter, 7);
        mDeviceConnectionStack = new ArrayList<>();
        mMostRecentDevice = null;
        mOriginalDualModeAudioState = Utils.isDualModeAudioEnabled();

        when(mA2dpService.setActiveDevice(any())).thenReturn(true);
        when(mHeadsetService.getHfpCallAudioPolicy(any()))
                .thenReturn(new BluetoothSinkAudioPolicy.Builder().build());
        when(mHeadsetService.setActiveDevice(any())).thenReturn(true);
        when(mHearingAidService.setActiveDevice(any())).thenReturn(true);
        when(mLeAudioService.setActiveDevice(any())).thenReturn(true);
        when(mLeAudioService.removeActiveDevice(anyBoolean())).thenReturn(true);
        when(mLeAudioService.getGroupId(any())).thenReturn(LE_AUDIO_GROUP_ID_INVALID);

        List<BluetoothDevice> connectedHearingAidDevices = new ArrayList<>();
        connectedHearingAidDevices.add(mHearingAidDevice);
        when(mHearingAidService.getHiSyncId(mHearingAidDevice)).thenReturn(HEARING_AID_HISYNC_ID);
        when(mHearingAidService.getConnectedPeerDevices(HEARING_AID_HISYNC_ID))
                .thenReturn(connectedHearingAidDevices);
    }

    @After
    public void tearDown() throws Exception {
        BluetoothMethodProxy.setInstanceForTesting(null);
        mAudioRoutingManager.cleanup();
        TestUtils.clearAdapterService(mAdapterService);
        Utils.setDualModeAudioStateForTesting(mOriginalDualModeAudioState);
    }

    @Test
    public void testSetUpAndTearDown() {}

    /** One A2DP is connected. */
    @Test
    public void onlyA2dpConnected_setA2dpActiveShouldNotCalled() {
        a2dpConnected(mA2dpDevice, false);
        mTestLooper.dispatchAll();
        verify(mA2dpService, never()).setActiveDevice(mA2dpDevice);
    }

    @Test
    public void a2dpHeadsetConnected_setA2dpActiveShouldBeCalledAfterHeadsetConnected() {
        when(mAudioManager.getMode()).thenReturn(AudioManager.MODE_IN_CALL);

        a2dpConnected(mA2dpHeadsetDevice, true);
        mTestLooper.dispatchAll();
        verify(mA2dpService, never()).setActiveDevice(mA2dpHeadsetDevice);

        headsetConnected(mA2dpHeadsetDevice, true);
        mTestLooper.dispatchAll();
        verify(mA2dpService, timeout(TIMEOUT_MS)).setActiveDevice(mA2dpHeadsetDevice);
        verify(mHeadsetService, timeout(TIMEOUT_MS)).setActiveDevice(mA2dpHeadsetDevice);
    }

    @Test
    public void a2dpAndHfpConnectedAtTheSameTime_setA2dpActiveShouldNotBeCalled() {
        when(mAudioManager.getMode()).thenReturn(AudioManager.MODE_IN_CALL);

        a2dpConnected(mA2dpHeadsetDevice, true);
        headsetConnected(mA2dpHeadsetDevice, true);
        mTestLooper.dispatchAll();
        verify(mA2dpService).setActiveDevice(mA2dpHeadsetDevice);
        verify(mHeadsetService).setActiveDevice(mA2dpHeadsetDevice);
    }

    /** Two A2DP are connected. Should set the second one active. */
    @Test
    public void secondA2dpConnected_setA2dpActiveShouldNotBeCalled() {
        a2dpConnected(mA2dpDevice, false);
        mTestLooper.dispatchAll();
        verify(mA2dpService, never()).setActiveDevice(mA2dpDevice);

        a2dpConnected(mSecondaryAudioDevice, false);
        mTestLooper.dispatchAll();
        verify(mA2dpService, never()).setActiveDevice(mSecondaryAudioDevice);
    }

    /** One A2DP is connected and disconnected later. Should then set active device to null. */
    @Test
    public void lastA2dpDisconnected_clearA2dpActive() {
        a2dpConnected(mA2dpDevice, false);
        switchA2dpActiveDevice(mA2dpDevice);
        mTestLooper.dispatchAll();
        verify(mA2dpService).setActiveDevice(mA2dpDevice);

        a2dpDisconnected(mA2dpDevice);
        mTestLooper.dispatchAll();
        verify(mA2dpService).removeActiveDevice(true);
    }

    /** Two A2DP are connected and active device is explicitly set. */
    @Test
    public void a2dpOnlyDeviceConnected_setActiveShouldNotBeCalled() {
        a2dpConnected(mA2dpDevice, false);
        mTestLooper.dispatchAll();
        verify(mA2dpService, never()).setActiveDevice(mA2dpDevice);

        a2dpConnected(mSecondaryAudioDevice, false);
        mTestLooper.dispatchAll();
        verify(mA2dpService, never()).setActiveDevice(mSecondaryAudioDevice);

        switchA2dpActiveDevice(mA2dpDevice);
        mTestLooper.dispatchAll();
        verify(mA2dpService).setActiveDevice(mA2dpDevice);
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.A2DP))
                .contains(mA2dpDevice);
    }

    /**
     * A2DP Headset and A2DP only devices are connected and the current activated A2DP only is then
     * disconnected. Should then set active device to fallback device.
     */
    @Test
    public void a2dpDeviceDisconnected_fallbackA2dpHeadset() {
        a2dpConnected(mA2dpHeadsetDevice, true);
        headsetConnected(mA2dpHeadsetDevice, true);
        mTestLooper.dispatchAll();
        verify(mA2dpService).setActiveDevice(mA2dpHeadsetDevice);
        verify(mHeadsetService).setActiveDevice(mA2dpHeadsetDevice);

        a2dpConnected(mA2dpDevice, false);
        switchA2dpActiveDevice(mA2dpDevice);
        mTestLooper.dispatchAll();
        verify(mA2dpService).setActiveDevice(mA2dpDevice);

        Mockito.clearInvocations(mA2dpService, mHeadsetService);
        a2dpDisconnected(mA2dpDevice);
        mTestLooper.dispatchAll();
        verify(mA2dpService).setActiveDevice(mA2dpHeadsetDevice);
    }

    /** One Headset is connected. */
    @Test
    public void onlyHeadsetConnected_setHeadsetActiveShouldNotBeCalled() {
        headsetConnected(mHeadsetDevice, false);
        mTestLooper.dispatchAll();
        verify(mHeadsetService, never()).setActiveDevice(mHeadsetDevice);
    }

    /** Two Headset are connected. Should set the second one active. */
    @Test
    public void secondHeadsetConnected_noDeviceActivated() {
        headsetConnected(mHeadsetDevice, false);
        mTestLooper.dispatchAll();
        verify(mHeadsetService, never()).setActiveDevice(mHeadsetDevice);

        headsetConnected(mSecondaryAudioDevice, false);
        mTestLooper.dispatchAll();
        verify(mHeadsetService, never()).setActiveDevice(mSecondaryAudioDevice);
    }

    /** One Headset is connected and disconnected later. Should then set active device to null. */
    @Test
    public void lastHeadsetDisconnected_clearHeadsetActive() {
        headsetConnected(mHeadsetDevice, false);
        switchHeadsetActiveDevice(mHeadsetDevice);
        mTestLooper.dispatchAll();
        verify(mHeadsetService).setActiveDevice(mHeadsetDevice);

        headsetDisconnected(mHeadsetDevice);
        mTestLooper.dispatchAll();
        verify(mHeadsetService).setActiveDevice(isNull());
    }

    /** Two Headsets are connected and active device is explicitly set. */
    @Test
    public void headsetActiveDeviceSelected_setActive() {
        headsetConnected(mHeadsetDevice, false);
        mTestLooper.dispatchAll();
        verify(mHeadsetService, never()).setActiveDevice(mHeadsetDevice);

        headsetConnected(mSecondaryAudioDevice, false);
        mTestLooper.dispatchAll();
        verify(mHeadsetService, never()).setActiveDevice(mSecondaryAudioDevice);

        switchHeadsetActiveDevice(mHeadsetDevice);
        mTestLooper.dispatchAll();
        verify(mHeadsetService).setActiveDevice(mHeadsetDevice);
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.HEADSET))
                .contains(mHeadsetDevice);
    }

    /**
     * Two Headset only devices are connected and the current active is then disconnected. Then it
     * should be fallback to phone.
     */
    @Test
    public void headsetSecondDeviceDisconnected_fallbackToPhone() {
        when(mAudioManager.getMode()).thenReturn(AudioManager.MODE_IN_CALL);

        headsetConnected(mHeadsetDevice, false);
        switchHeadsetActiveDevice(mHeadsetDevice);
        mTestLooper.dispatchAll();
        verify(mHeadsetService).setActiveDevice(mHeadsetDevice);

        headsetConnected(mSecondaryAudioDevice, false);
        switchHeadsetActiveDevice(mSecondaryAudioDevice);
        mTestLooper.dispatchAll();
        verify(mHeadsetService).setActiveDevice(mSecondaryAudioDevice);

        Mockito.clearInvocations(mHeadsetService);
        headsetDisconnected(mSecondaryAudioDevice);
        mTestLooper.dispatchAll();
        verify(mHeadsetService, never()).setActiveDevice(mHeadsetDevice);
    }

    @Test
    public void headsetSecondDeviceDisconnected_fallbackDeviceActiveWhileRinging() {
        when(mAudioManager.getMode()).thenReturn(AudioManager.MODE_RINGTONE);

        headsetConnected(mA2dpHeadsetDevice, true);
        a2dpConnected(mA2dpHeadsetDevice, true);
        mTestLooper.dispatchAll();
        verify(mHeadsetService).setActiveDevice(mA2dpHeadsetDevice);
        verify(mA2dpService).setActiveDevice(mA2dpHeadsetDevice);

        headsetConnected(mHeadsetDevice, false);
        switchHeadsetActiveDevice(mHeadsetDevice);
        mTestLooper.dispatchAll();
        verify(mHeadsetService).setActiveDevice(mHeadsetDevice);

        Mockito.clearInvocations(mHeadsetService);
        headsetDisconnected(mHeadsetDevice);
        mTestLooper.dispatchAll();
        verify(mHeadsetService).setActiveDevice(mA2dpHeadsetDevice);
    }

    @Test
    public void a2dpConnectedButHeadsetNotConnected_setA2dpActiveShouldNotBeCalled() {
        when(mAudioManager.getMode()).thenReturn(AudioManager.MODE_IN_CALL);
        a2dpConnected(mA2dpHeadsetDevice, true);

        mTestLooper.moveTimeForward(AudioRoutingManager.A2DP_HFP_SYNC_CONNECTION_TIMEOUT_MS / 2);
        mTestLooper.moveTimeForward(A2DP_HFP_SYNC_CONNECTION_TIMEOUT_MS);
        mTestLooper.dispatchAll();
        verify(mA2dpService, never()).setActiveDevice(mA2dpHeadsetDevice);
    }

    @Test
    public void headsetConnectedButA2dpNotConnected_setHeadsetActiveShouldNotBeCalled() {
        when(mAudioManager.getMode()).thenReturn(AudioManager.MODE_NORMAL);
        headsetConnected(mA2dpHeadsetDevice, true);

        mTestLooper.moveTimeForward(AudioRoutingManager.A2DP_HFP_SYNC_CONNECTION_TIMEOUT_MS / 2);
        mTestLooper.moveTimeForward(A2DP_HFP_SYNC_CONNECTION_TIMEOUT_MS);
        mTestLooper.dispatchAll();
        verify(mHeadsetService, never()).setActiveDevice(mA2dpHeadsetDevice);
    }

    @Test
    public void hfpActivatedAfterA2dpActivated_shouldNotActivateA2dpAgain() {
        a2dpConnected(mA2dpHeadsetDevice, true);
        a2dpConnected(mSecondaryAudioDevice, true);
        headsetConnected(mA2dpHeadsetDevice, true);
        headsetConnected(mSecondaryAudioDevice, true);

        mTestLooper.dispatchAll();
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.A2DP))
                .contains(mSecondaryAudioDevice);
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.HEADSET))
                .contains(mSecondaryAudioDevice);

        Mockito.clearInvocations(mHeadsetService);
        Mockito.clearInvocations(mA2dpService);
        // When A2DP is activated, then it should activate HFP
        switchA2dpActiveDevice(mA2dpHeadsetDevice);
        mTestLooper.dispatchAll();
        verify(mHeadsetService).setActiveDevice(mA2dpHeadsetDevice);

        Mockito.clearInvocations(mHeadsetService);
        Mockito.clearInvocations(mA2dpService);
        // If HFP activated already, it should not activate A2DP again
        switchHeadsetActiveDevice(mA2dpHeadsetDevice);
        mTestLooper.dispatchAll();
        verify(mA2dpService, never()).setActiveDevice(mA2dpHeadsetDevice);
    }

    @Test
    public void hfpConnectedAfterTimeout_shouldActivateA2dpAndHeadsetWhenConnected() {
        a2dpConnected(mA2dpHeadsetDevice, true);
        mTestLooper.moveTimeForward(A2DP_HFP_SYNC_CONNECTION_TIMEOUT_MS);
        mTestLooper.dispatchAll();
        verify(mA2dpService, never()).setActiveDevice(mA2dpHeadsetDevice);
        verify(mHeadsetService, never()).setActiveDevice(mA2dpHeadsetDevice);
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.A2DP)).isEmpty();
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.HEADSET)).isEmpty();

        headsetConnected(mA2dpHeadsetDevice, true);
        mTestLooper.dispatchAll();
        verify(mA2dpService).setActiveDevice(mA2dpHeadsetDevice);
        verify(mHeadsetService).setActiveDevice(mA2dpHeadsetDevice);
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.A2DP))
                .contains(mA2dpHeadsetDevice);
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.HEADSET))
                .contains(mA2dpHeadsetDevice);
    }

    @Test
    public void a2dpHeadsetActivated_whileActivatingAnotherA2dpHeadset() {
        // Test HS1 A2DP -> HS2 A2DP -> HS1 HFP -> HS2 HFP
        a2dpConnected(mA2dpHeadsetDevice, true);
        a2dpConnected(mSecondaryAudioDevice, true);
        headsetConnected(mA2dpHeadsetDevice, true);
        headsetConnected(mSecondaryAudioDevice, true);

        mTestLooper.dispatchAll();
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.A2DP))
                .contains(mSecondaryAudioDevice);
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.HEADSET))
                .contains(mSecondaryAudioDevice);

        a2dpDisconnected(mA2dpHeadsetDevice);
        a2dpDisconnected(mSecondaryAudioDevice);
        headsetDisconnected(mA2dpHeadsetDevice);
        headsetDisconnected(mSecondaryAudioDevice);

        Mockito.clearInvocations(mHeadsetService);
        Mockito.clearInvocations(mA2dpService);

        // Test HS1 HFP -> HS2 HFP -> HS1 A2DP -> HS2 A2DP
        headsetConnected(mA2dpHeadsetDevice, true);
        headsetConnected(mSecondaryAudioDevice, true);
        a2dpConnected(mA2dpHeadsetDevice, true);
        a2dpConnected(mSecondaryAudioDevice, true);

        mTestLooper.dispatchAll();
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.A2DP))
                .contains(mSecondaryAudioDevice);
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.HEADSET))
                .contains(mSecondaryAudioDevice);
    }

    @Test
    public void hfpActivated_whileActivatingA2dpHeadset() {
        a2dpConnected(mA2dpHeadsetDevice, true);
        mTestLooper.dispatchAll();
        verify(mA2dpService, never()).setActiveDevice(mA2dpHeadsetDevice);

        headsetConnected(mHeadsetDevice, false);
        switchHeadsetActiveDevice(mHeadsetDevice);
        mTestLooper.dispatchAll();
        verify(mHeadsetService).setActiveDevice(mHeadsetDevice);
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.HEADSET))
                .contains(mHeadsetDevice);

        headsetConnected(mA2dpHeadsetDevice, true);
        mTestLooper.dispatchAll();
        verify(mA2dpService).setActiveDevice(mA2dpHeadsetDevice);
        verify(mHeadsetService).setActiveDevice(mA2dpHeadsetDevice);
    }

    @Test
    public void a2dpActivated_whileActivatingA2dpHeadset() {
        headsetConnected(mA2dpHeadsetDevice, true);
        mTestLooper.dispatchAll();
        verify(mA2dpService, never()).setActiveDevice(mA2dpHeadsetDevice);

        a2dpConnected(mA2dpDevice, false);
        switchA2dpActiveDevice(mA2dpDevice);
        mTestLooper.dispatchAll();
        verify(mA2dpService).setActiveDevice(mA2dpDevice);

        a2dpConnected(mA2dpHeadsetDevice, true);
        mTestLooper.dispatchAll();
        verify(mA2dpService).setActiveDevice(mA2dpHeadsetDevice);
        verify(mHeadsetService).setActiveDevice(mA2dpHeadsetDevice);
    }

    /** A headset device with connecting audio policy set to NOT ALLOWED. */
    @Test
    public void notAllowedConnectingPolicyHeadsetConnected_noSetActiveDevice() {
        // setting connecting policy to NOT ALLOWED
        when(mHeadsetService.getHfpCallAudioPolicy(mHeadsetDevice))
                .thenReturn(
                        new BluetoothSinkAudioPolicy.Builder()
                                .setCallEstablishPolicy(BluetoothSinkAudioPolicy.POLICY_ALLOWED)
                                .setActiveDevicePolicyAfterConnection(
                                        BluetoothSinkAudioPolicy.POLICY_NOT_ALLOWED)
                                .setInBandRingtonePolicy(BluetoothSinkAudioPolicy.POLICY_ALLOWED)
                                .build());

        headsetConnected(mHeadsetDevice, false);
        mTestLooper.dispatchAll();
        verify(mHeadsetService, never()).setActiveDevice(mHeadsetDevice);
    }

    @Test
    public void twoHearingAidDevicesConnected_WithTheSameHiSyncId() {
        Assume.assumeTrue(
                "Ignore test when HearingAidService is not enabled", HearingAidService.isEnabled());

        List<BluetoothDevice> connectedHearingAidDevices = new ArrayList<>();
        connectedHearingAidDevices.add(mHearingAidDevice);
        connectedHearingAidDevices.add(mSecondaryAudioDevice);
        when(mHearingAidService.getHiSyncId(mSecondaryAudioDevice))
                .thenReturn(HEARING_AID_HISYNC_ID);
        when(mHearingAidService.getConnectedPeerDevices(HEARING_AID_HISYNC_ID))
                .thenReturn(connectedHearingAidDevices);

        hearingAidConnected(mHearingAidDevice);
        hearingAidConnected(mSecondaryAudioDevice);
        mTestLooper.dispatchAll();
        verify(mHearingAidService).setActiveDevice(mHearingAidDevice);
        verify(mHearingAidService).setActiveDevice(mSecondaryAudioDevice);
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.HEARING_AID))
                .containsExactly(mHearingAidDevice, mSecondaryAudioDevice);
    }

    /** A combo (A2DP + Headset) device is connected. Then a Hearing Aid is connected. */
    @Test
    public void hearingAidActive_clearA2dpAndHeadsetActive() {
        a2dpConnected(mA2dpHeadsetDevice, true);
        headsetConnected(mA2dpHeadsetDevice, true);
        mTestLooper.dispatchAll();
        verify(mA2dpService).setActiveDevice(mA2dpHeadsetDevice);
        verify(mHeadsetService).setActiveDevice(mA2dpHeadsetDevice);

        hearingAidConnected(mHearingAidDevice);
        mTestLooper.dispatchAll();
        verify(mA2dpService).removeActiveDevice(false);
        verify(mHeadsetService).setActiveDevice(null);
    }

    /** A Hearing Aid is connected. Then a combo (A2DP + Headset) device is connected. */
    @Test
    public void hearingAidAndA2dpHeadsetConnected_setA2dpHeadsetActive() {
        switchHearingAidActiveDevice(mHearingAidDevice);
        a2dpConnected(mA2dpHeadsetDevice, true);
        headsetConnected(mA2dpHeadsetDevice, true);

        mTestLooper.dispatchAll();
        verify(mHearingAidService).removeActiveDevice(false);
        verify(mA2dpService).setActiveDevice(mA2dpHeadsetDevice);
        verify(mHeadsetService).setActiveDevice(mA2dpHeadsetDevice);
    }

    /** A Hearing Aid is connected. Then an A2DP activated. */
    @Test
    public void hearingAidActive_setA2dpActiveExplicitly() {
        when(mHearingAidService.removeActiveDevice(anyBoolean())).thenReturn(true);
        hearingAidConnected(mHearingAidDevice);
        a2dpConnected(mA2dpDevice, false);
        switchA2dpActiveDevice(mA2dpDevice);

        mTestLooper.dispatchAll();
        verify(mHearingAidService).removeActiveDevice(false);
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.A2DP))
                .contains(mA2dpDevice);
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.HEARING_AID)).isEmpty();
    }

    /** A Hearing Aid is connected. Then a Headset active device is explicitly set. */
    @Test
    public void hearingAidActive_setHeadsetActiveExplicitly() {
        when(mHearingAidService.removeActiveDevice(anyBoolean())).thenReturn(true);
        hearingAidConnected(mHearingAidDevice);
        headsetConnected(mHeadsetDevice, false);
        switchHeadsetActiveDevice(mHeadsetDevice);

        mTestLooper.dispatchAll();
        verify(mHearingAidService).removeActiveDevice(false);
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.HEADSET))
                .contains(mHeadsetDevice);
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.HEARING_AID)).isEmpty();
    }

    @Test
    public void hearingAidActiveWithNull_clearHearingAidActiveDevices() {
        switchHearingAidActiveDevice(null);
        mTestLooper.dispatchAll();
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.HEARING_AID)).isEmpty();
    }

    /** One LE Audio is connected. */
    @Test
    public void onlyLeAudioConnected_setHeadsetActive() {
        leAudioConnected(mLeAudioDevice);
        mTestLooper.dispatchAll();
        verify(mLeAudioService).setActiveDevice(mLeAudioDevice);
    }

    /** Two LE Audio are connected. Should set the second one active. */
    @Test
    public void secondLeAudioConnected_setSecondLeAudioActive() {
        leAudioConnected(mLeAudioDevice);
        mTestLooper.dispatchAll();
        verify(mLeAudioService).setActiveDevice(mLeAudioDevice);

        leAudioConnected(mSecondaryAudioDevice);
        mTestLooper.dispatchAll();
        verify(mLeAudioService).setActiveDevice(mSecondaryAudioDevice);
    }

    /** One LE Audio is connected and disconnected later. Should then set active device to null. */
    @Test
    public void lastLeAudioDisconnected_clearLeAudioActive() {
        leAudioConnected(mLeAudioDevice);
        mTestLooper.dispatchAll();
        verify(mLeAudioService).setActiveDevice(mLeAudioDevice);

        leAudioDisconnected(mLeAudioDevice);
        mTestLooper.dispatchAll();
        verify(mLeAudioService).removeActiveDevice(false);
    }

    /** Two LE Audio are connected and active device is explicitly set. */
    @Test
    public void leAudioActiveDeviceSelected_setActive() {
        leAudioConnected(mLeAudioDevice);
        mTestLooper.dispatchAll();
        verify(mLeAudioService).setActiveDevice(mLeAudioDevice);

        leAudioConnected(mSecondaryAudioDevice);
        mTestLooper.dispatchAll();
        verify(mLeAudioService).setActiveDevice(mSecondaryAudioDevice);

        Mockito.clearInvocations(mLeAudioService);
        switchLeAudioActiveDevice(mLeAudioDevice);
        mTestLooper.dispatchAll();
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.LE_AUDIO))
                .contains(mLeAudioDevice);
    }

    /**
     * Two LE Audio are connected and the current active is then disconnected. Should then set
     * active device to fallback device.
     */
    @Test
    public void leAudioSecondDeviceDisconnected_fallbackDeviceActive() {
        leAudioConnected(mLeAudioDevice);
        mTestLooper.dispatchAll();
        verify(mLeAudioService).setActiveDevice(mLeAudioDevice);

        leAudioConnected(mSecondaryAudioDevice);
        mTestLooper.dispatchAll();
        verify(mLeAudioService).setActiveDevice(mSecondaryAudioDevice);

        Mockito.clearInvocations(mLeAudioService);
        leAudioDisconnected(mSecondaryAudioDevice);
        mTestLooper.dispatchAll();
        verify(mLeAudioService).setActiveDevice(mLeAudioDevice);
    }

    /** A combo (A2DP + Headset) device is connected. Then an LE Audio is connected. */
    @Test
    public void leAudioActive_clearA2dpAndHeadsetActive() {
        a2dpConnected(mA2dpHeadsetDevice, true);
        headsetConnected(mA2dpHeadsetDevice, true);
        mTestLooper.dispatchAll();
        verify(mA2dpService).setActiveDevice(mA2dpHeadsetDevice);
        verify(mHeadsetService).setActiveDevice(mA2dpHeadsetDevice);
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.A2DP))
                .contains(mA2dpHeadsetDevice);
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.HEADSET))
                .contains(mA2dpHeadsetDevice);

        leAudioConnected(mLeAudioDevice);
        mTestLooper.dispatchAll();
        verify(mA2dpService).removeActiveDevice(false);
        verify(mHeadsetService).setActiveDevice(isNull());
    }

    /** An LE Audio is connected. Then a combo (A2DP + Headset) device is connected. */
    @Test
    public void leAudioActive_setA2dpAndHeadsetActive() {
        switchLeAudioActiveDevice(mLeAudioDevice);
        a2dpConnected(mA2dpHeadsetDevice, true);
        headsetConnected(mA2dpHeadsetDevice, true);

        mTestLooper.dispatchAll();
        verify(mA2dpService).setActiveDevice(mA2dpHeadsetDevice);
        verify(mHeadsetService).setActiveDevice(mA2dpHeadsetDevice);
    }

    /** An LE Audio is connected. Then an A2DP active device is explicitly set. */
    @Test
    public void leAudioActive_setA2dpActiveExplicitly() {
        leAudioConnected(mLeAudioDevice);
        a2dpConnected(mA2dpDevice, false);
        switchA2dpActiveDevice(mA2dpDevice);

        mTestLooper.dispatchAll();
        verify(mLeAudioService).removeActiveDevice(true);
        verify(mA2dpService).setActiveDevice(mA2dpDevice);
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.A2DP))
                .contains(mA2dpDevice);
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.LE_AUDIO)).isEmpty();
    }

    /** An LE Audio is connected. Then a Headset active device is explicitly set. */
    @Test
    public void leAudioActive_setHeadsetActiveExplicitly() {
        switchLeAudioActiveDevice(mLeAudioDevice);
        headsetConnected(mHeadsetDevice, false);
        switchHeadsetActiveDevice(mHeadsetDevice);

        mTestLooper.dispatchAll();
        verify(mLeAudioService).removeActiveDevice(true);
        verify(mHeadsetService).setActiveDevice(mHeadsetDevice);
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.HEADSET))
                .contains(mHeadsetDevice);
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.LE_AUDIO)).isEmpty();
    }

    /**
     * An LE Audio connected. An A2DP activated. The A2DP disconnected. Then the LE Audio should be
     * the active one.
     */
    @Test
    public void leAudioAndA2dpActivatedThenA2dpDisconnected_fallbackToLeAudio() {
        when(mAudioManager.getMode()).thenReturn(AudioManager.MODE_NORMAL);

        leAudioConnected(mLeAudioDevice);
        mTestLooper.dispatchAll();
        verify(mLeAudioService).setActiveDevice(mLeAudioDevice);

        a2dpConnected(mA2dpDevice, false);
        switchA2dpActiveDevice(mA2dpDevice);
        mTestLooper.dispatchAll();
        verify(mA2dpService).setActiveDevice(mA2dpDevice);

        Mockito.clearInvocations(mLeAudioService);
        a2dpDisconnected(mA2dpDevice);
        mTestLooper.dispatchAll();
        verify(mA2dpService).removeActiveDevice(false);
        verify(mLeAudioService).setActiveDevice(mLeAudioDevice);
    }

    /**
     * An LE Audio set connected. The not active bud disconnected. Then the active device should not
     * change and hasFallback should be set to false.
     */
    @Test
    public void leAudioSetConnectedThenNotActiveOneDisconnected_noFallback() {
        when(mAudioManager.getMode()).thenReturn(AudioManager.MODE_NORMAL);

        leAudioConnected(mLeAudioDevice);
        mTestLooper.dispatchAll();
        verify(mLeAudioService).setActiveDevice(mLeAudioDevice);

        leAudioConnected(mLeAudioDevice2);
        mTestLooper.dispatchAll();
        verify(mLeAudioService).setActiveDevice(mLeAudioDevice2);

        Mockito.clearInvocations(mLeAudioService);
        leAudioDisconnected(mLeAudioDevice);
        mTestLooper.dispatchAll();

        verify(mLeAudioService, never()).removeActiveDevice(false);
        verify(mLeAudioService, never()).setActiveDevice(mLeAudioDevice2);
    }

    /**
     * An LE Audio set connected. The active bud disconnected. Set active device returns false
     * indicating an issue (the other bud is also disconnected). Then the active device should be
     * removed and hasFallback should be set to false.
     */
    @Test
    public void leAudioSetConnectedThenActiveOneDisconnected_noFallback() {
        when(mAudioManager.getMode()).thenReturn(AudioManager.MODE_NORMAL);

        leAudioConnected(mLeAudioDevice);
        mTestLooper.dispatchAll();
        verify(mLeAudioService).setActiveDevice(mLeAudioDevice);

        leAudioConnected(mLeAudioDevice2);
        mTestLooper.dispatchAll();
        verify(mLeAudioService).setActiveDevice(mLeAudioDevice2);

        Mockito.clearInvocations(mLeAudioService);
        // Return false to indicate an issue when setting new active device
        // (e.g. the other device disconnected as well).
        when(mLeAudioService.setActiveDevice(any())).thenReturn(false);

        leAudioDisconnected(mLeAudioDevice2);
        mTestLooper.dispatchAll();
        verify(mLeAudioService).removeActiveDevice(false);
    }

    /**
     * An LE Audio set connected. The active bud disconnected. Set active device returns true
     * indicating the other bud is going to be the active device. Then the active device should
     * change and hasFallback should be set to true.
     */
    @Test
    public void leAudioSetConnectedThenActiveOneDisconnected_hasFallback() {
        when(mAudioManager.getMode()).thenReturn(AudioManager.MODE_NORMAL);

        leAudioConnected(mLeAudioDevice);
        mTestLooper.dispatchAll();
        verify(mLeAudioService).setActiveDevice(mLeAudioDevice);

        leAudioConnected(mLeAudioDevice2);
        mTestLooper.dispatchAll();
        verify(mLeAudioService).setActiveDevice(mLeAudioDevice2);

        Mockito.clearInvocations(mLeAudioService);
        leAudioDisconnected(mLeAudioDevice2);
        mTestLooper.dispatchAll();
        verify(mLeAudioService).setActiveDevice(mLeAudioDevice);
    }

    /**
     * An A2DP only device connected. An LE Audio connected. The LE Audio disconnected. Then it
     * should be fallback to phone instead of the A2DP only device.
     */
    @Test
    public void a2dpAndLeAudioConnectedThenLeAudioDisconnected_fallbackToPhone() {
        when(mAudioManager.getMode()).thenReturn(AudioManager.MODE_NORMAL);

        a2dpConnected(mA2dpDevice, false);
        switchA2dpActiveDevice(mA2dpDevice);
        mTestLooper.dispatchAll();
        verify(mA2dpService).setActiveDevice(mA2dpDevice);

        leAudioConnected(mLeAudioDevice);
        mTestLooper.dispatchAll();
        verify(mLeAudioService).setActiveDevice(mLeAudioDevice);

        Mockito.clearInvocations(mA2dpService);
        leAudioDisconnected(mLeAudioDevice);
        mTestLooper.dispatchAll();
        verify(mLeAudioService).removeActiveDevice(false);
        verify(mA2dpService, never()).setActiveDevice(mA2dpDevice);
    }

    /**
     * An A2DP headset connected. An LE Audio connected. The LE Audio disconnected. Then the A2DP
     * headset should be the active one.
     */
    @Test
    public void a2dpHeadsetAndLeAudioConnectedThenLeAudioDisconnected_fallbackToA2dpHeadset() {
        when(mAudioManager.getMode()).thenReturn(AudioManager.MODE_NORMAL);

        a2dpConnected(mHeadsetDevice, true);
        headsetConnected(mHeadsetDevice, true);
        mTestLooper.dispatchAll();
        verify(mA2dpService).setActiveDevice(mHeadsetDevice);
        verify(mHeadsetService).setActiveDevice(mHeadsetDevice);

        leAudioConnected(mLeAudioDevice);
        mTestLooper.dispatchAll();
        verify(mLeAudioService).setActiveDevice(mLeAudioDevice);

        Mockito.clearInvocations(mA2dpService, mHeadsetService);
        leAudioDisconnected(mLeAudioDevice);
        mTestLooper.dispatchAll();
        verify(mLeAudioService).removeActiveDevice(true);
        verify(mA2dpService).setActiveDevice(mHeadsetDevice);
        verify(mHeadsetService).setActiveDevice(mHeadsetDevice);
    }

    /**
     * Two Hearing Aid are connected and the current active is then disconnected. Should then set
     * active device to fallback device.
     */
    @Test
    public void hearingAidSecondDeviceDisconnected_fallbackDeviceActive() {
        hearingAidConnected(mHearingAidDevice);
        mTestLooper.dispatchAll();
        verify(mHearingAidService).setActiveDevice(mHearingAidDevice);

        List<BluetoothDevice> connectedHearingAidDevices = new ArrayList<>();
        connectedHearingAidDevices.add(mSecondaryAudioDevice);
        when(mHearingAidService.getHiSyncId(mSecondaryAudioDevice))
                .thenReturn(HEARING_AID_HISYNC_ID + 1);
        when(mHearingAidService.getConnectedPeerDevices(HEARING_AID_HISYNC_ID + 1))
                .thenReturn(connectedHearingAidDevices);

        hearingAidConnected(mSecondaryAudioDevice);
        mTestLooper.dispatchAll();
        verify(mHearingAidService).setActiveDevice(mSecondaryAudioDevice);

        Mockito.clearInvocations(mHearingAidService);
        hearingAidDisconnected(mSecondaryAudioDevice);
        mTestLooper.dispatchAll();
        verify(mHearingAidService).setActiveDevice(mHearingAidDevice);
    }

    /**
     * Test connect/disconnect of devices. Hearing Aid, A2DP connected, LE audio, then LE audio
     * disconnected.
     */
    @Test
    public void activeDeviceChange_withHearingAidLeAudioAndA2dpDevices() {
        when(mAudioManager.getMode()).thenReturn(AudioManager.MODE_NORMAL);
        when(mHearingAidService.removeActiveDevice(anyBoolean())).thenReturn(true);

        hearingAidConnected(mHearingAidDevice);
        mTestLooper.dispatchAll();
        verify(mHearingAidService).setActiveDevice(mHearingAidDevice);

        a2dpConnected(mA2dpDevice, false);
        switchA2dpActiveDevice(mA2dpDevice);
        mTestLooper.dispatchAll();
        verify(mA2dpService).setActiveDevice(mA2dpDevice);
        verify(mHearingAidService).removeActiveDevice(false);

        Mockito.clearInvocations(mHearingAidService, mA2dpService, mLeAudioService);
        leAudioConnected(mLeAudioDevice);
        mTestLooper.dispatchAll();
        verify(mLeAudioService).setActiveDevice(mLeAudioDevice);
        verify(mA2dpService).removeActiveDevice(false);

        Mockito.clearInvocations(mHearingAidService, mA2dpService, mLeAudioService);
        leAudioDisconnected(mLeAudioDevice);
        mTestLooper.dispatchAll();
        verify(mHearingAidService).setActiveDevice(mHearingAidDevice);
        verify(mLeAudioService).removeActiveDevice(true);
    }

    /**
     * Verifies that we mutually exclude classic audio profiles (A2DP & HFP) and LE Audio when the
     * dual mode feature is disabled.
     */
    @Test
    public void dualModeAudioDeviceConnected_withDualModeFeatureDisabled() {
        // Turn off the dual mode audio flag
        Utils.setDualModeAudioStateForTesting(false);

        // Ensure we remove the LEA active device when classic audio profiles are made active
        a2dpConnected(mDualModeAudioDevice, true);
        headsetConnected(mDualModeAudioDevice, true);
        mTestLooper.dispatchAll();
        verify(mA2dpService).setActiveDevice(mDualModeAudioDevice);
        verify(mHeadsetService).setActiveDevice(mDualModeAudioDevice);
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.A2DP))
                .contains(mDualModeAudioDevice);
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.HEADSET))
                .contains(mDualModeAudioDevice);

        // Ensure we make classic audio profiles inactive when LEA is made active
        leAudioConnected(mDualModeAudioDevice);
        mTestLooper.dispatchAll();
        verify(mA2dpService).removeActiveDevice(false);
        verify(mHeadsetService).setActiveDevice(isNull());
        verify(mLeAudioService).setActiveDevice(mDualModeAudioDevice);
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.LE_AUDIO))
                .contains(mDualModeAudioDevice);
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.A2DP)).isEmpty();
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.HEADSET)).isEmpty();
    }

    /**
     * Verifies that we connect and make active both classic audio profiles (A2DP & HFP) and LE
     * Audio when the dual mode feature is enabled.
     */
    @Test
    public void dualModeAudioDeviceConnected_withDualModeFeatureEnabled() {
        // Turn on the dual mode audio flag
        Utils.setDualModeAudioStateForTesting(true);
        mDatabaseManager.setProfileConnectionPolicy(
                mDualModeAudioDevice,
                BluetoothProfile.A2DP,
                BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        mDatabaseManager.setProfileConnectionPolicy(
                mDualModeAudioDevice,
                BluetoothProfile.HEADSET,
                BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        reset(mLeAudioService);
        leAudioConnected(mDualModeAudioDevice);
        mTestLooper.dispatchAll();
        // Verify setting LEA active fails when all supported classic audio profiles are not active
        verify(mLeAudioService, never()).setActiveDevice(mDualModeAudioDevice);
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.LE_AUDIO)).isEmpty();
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.A2DP)).isEmpty();
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.HEADSET)).isEmpty();

        when(mLeAudioService.setActiveDevice(any())).thenReturn(true);
        when(mLeAudioService.removeActiveDevice(anyBoolean())).thenReturn(true);
        when(mA2dpService.setActiveDevice(any())).thenReturn(true);
        when(mA2dpService.removeActiveDevice(anyBoolean())).thenReturn(true);
        when(mHeadsetService.setActiveDevice(any())).thenReturn(true);

        a2dpConnected(mDualModeAudioDevice, true);
        headsetConnected(mDualModeAudioDevice, true);
        mTestLooper.dispatchAll();
        verify(mLeAudioService).setActiveDevice(mDualModeAudioDevice);
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.A2DP))
                .contains(mDualModeAudioDevice);
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.HEADSET))
                .contains(mDualModeAudioDevice);
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.LE_AUDIO))
                .contains(mDualModeAudioDevice);

        switchA2dpActiveDevice(null);
        mTestLooper.dispatchAll();
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.A2DP)).isEmpty();
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.LE_AUDIO))
                .contains(mDualModeAudioDevice);
    }

    /**
     * Verifies that other profiles do not have their active device cleared when we fail to make a
     * newly connected device active.
     */
    @Test
    public void setActiveDeviceFailsUponConnection() {
        Utils.setDualModeAudioStateForTesting(false);

        leAudioConnected(mLeAudioDevice);
        mTestLooper.dispatchAll();
        verify(mLeAudioService).setActiveDevice(mLeAudioDevice);

        when(mHeadsetService.setActiveDevice(any())).thenReturn(false);
        when(mA2dpService.setActiveDevice(any())).thenReturn(false);
        when(mHearingAidService.setActiveDevice(any())).thenReturn(false);
        when(mLeAudioService.setActiveDevice(any())).thenReturn(false);

        a2dpConnected(mA2dpDevice, false);
        switchA2dpActiveDevice(mA2dpDevice);
        mTestLooper.dispatchAll();
        verify(mA2dpService).setActiveDevice(mA2dpDevice);
        verify(mLeAudioService, never()).removeActiveDevice(anyBoolean());
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.A2DP)).isEmpty();
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.LE_AUDIO))
                .contains(mLeAudioDevice);

        Mockito.clearInvocations(mA2dpService, mHeadsetService, mLeAudioService);
        a2dpConnected(mA2dpHeadsetDevice, true);
        headsetConnected(mA2dpHeadsetDevice, true);
        mTestLooper.dispatchAll();
        verify(mA2dpService).setActiveDevice(mA2dpHeadsetDevice);
        verify(mHeadsetService).setActiveDevice(mA2dpHeadsetDevice);
        verify(mLeAudioService, never()).removeActiveDevice(anyBoolean());
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.A2DP)).isEmpty();
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.HEADSET)).isEmpty();
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.LE_AUDIO))
                .contains(mLeAudioDevice);

        Mockito.clearInvocations(mLeAudioService);
        hearingAidConnected(mHearingAidDevice);
        mTestLooper.dispatchAll();
        verify(mHearingAidService).setActiveDevice(mHearingAidDevice);
        verify(mLeAudioService, never()).removeActiveDevice(anyBoolean());
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.HEARING_AID)).isEmpty();
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.LE_AUDIO))
                .contains(mLeAudioDevice);

        Mockito.clearInvocations(mLeAudioService);
        leAudioConnected(mSecondaryAudioDevice);
        mTestLooper.dispatchAll();
        verify(mLeAudioService).setActiveDevice(mSecondaryAudioDevice);
        assertThat(mAudioRoutingManager.getActiveDevices(BluetoothProfile.LE_AUDIO))
                .contains(mLeAudioDevice);
    }

    /** A wired audio device is connected. Then all active devices are set to null. */
    @Test
    public void wiredAudioDeviceConnected_setAllActiveDevicesNull() {
        a2dpConnected(mA2dpDevice, false);
        switchA2dpActiveDevice(mA2dpDevice);
        headsetConnected(mHeadsetDevice, false);
        switchHeadsetActiveDevice(mHeadsetDevice);
        mTestLooper.dispatchAll();
        verify(mA2dpService).setActiveDevice(mA2dpDevice);
        verify(mHeadsetService).setActiveDevice(mHeadsetDevice);

        mAudioRoutingManager.wiredAudioDeviceConnected();
        mTestLooper.dispatchAll();
        verify(mA2dpService).removeActiveDevice(false);
        verify(mHeadsetService).setActiveDevice(isNull());
        verify(mHearingAidService).removeActiveDevice(false);
    }

    /** Helper to indicate A2dp connected for a device. */
    private void a2dpConnected(BluetoothDevice device, boolean supportHfp) {
        mDatabaseManager.setProfileConnectionPolicy(
                device, BluetoothProfile.A2DP, BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        mDatabaseManager.setProfileConnectionPolicy(
                device,
                BluetoothProfile.HEADSET,
                supportHfp
                        ? BluetoothProfile.CONNECTION_POLICY_ALLOWED
                        : BluetoothProfile.CONNECTION_POLICY_UNKNOWN);

        mDeviceConnectionStack.add(device);
        mMostRecentDevice = device;

        mAudioRoutingManager.profileConnectionStateChanged(
                BluetoothProfile.A2DP,
                device,
                BluetoothProfile.STATE_DISCONNECTED,
                BluetoothProfile.STATE_CONNECTED);
    }

    /** Helper to indicate A2dp disconnected for a device. */
    private void a2dpDisconnected(BluetoothDevice device) {
        mDeviceConnectionStack.remove(device);
        mMostRecentDevice =
                (mDeviceConnectionStack.size() > 0)
                        ? mDeviceConnectionStack.get(mDeviceConnectionStack.size() - 1)
                        : null;

        mAudioRoutingManager.profileConnectionStateChanged(
                BluetoothProfile.A2DP,
                device,
                BluetoothProfile.STATE_CONNECTED,
                BluetoothProfile.STATE_DISCONNECTED);
    }

    /** Helper to indicate A2dp active device changed for a device. */
    private void switchA2dpActiveDevice(BluetoothDevice device) {
        mDeviceConnectionStack.remove(device);
        mDeviceConnectionStack.add(device);
        mMostRecentDevice = device;

        if (device == null) {
            mAudioRoutingManager.removeActiveDevice(BluetoothProfile.A2DP, false);
        } else {
            mAudioRoutingManager.activateDeviceProfile(device, BluetoothProfile.A2DP, null);
        }
    }

    /** Helper to indicate Headset connected for a device. */
    private void headsetConnected(BluetoothDevice device, boolean supportA2dp) {
        mDatabaseManager.setProfileConnectionPolicy(
                device, BluetoothProfile.HEADSET, BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        mDatabaseManager.setProfileConnectionPolicy(
                device,
                BluetoothProfile.A2DP,
                supportA2dp
                        ? BluetoothProfile.CONNECTION_POLICY_ALLOWED
                        : BluetoothProfile.CONNECTION_POLICY_UNKNOWN);

        mDeviceConnectionStack.add(device);
        mMostRecentDevice = device;

        mAudioRoutingManager.profileConnectionStateChanged(
                BluetoothProfile.HEADSET,
                device,
                BluetoothProfile.STATE_DISCONNECTED,
                BluetoothProfile.STATE_CONNECTED);
    }

    /** Helper to indicate Headset disconnected for a device. */
    private void headsetDisconnected(BluetoothDevice device) {
        mDeviceConnectionStack.remove(device);
        mMostRecentDevice =
                (mDeviceConnectionStack.size() > 0)
                        ? mDeviceConnectionStack.get(mDeviceConnectionStack.size() - 1)
                        : null;

        mAudioRoutingManager.profileConnectionStateChanged(
                BluetoothProfile.HEADSET,
                device,
                BluetoothProfile.STATE_CONNECTED,
                BluetoothProfile.STATE_DISCONNECTED);
    }

    /** Helper to indicate Headset active device changed for a device. */
    private void switchHeadsetActiveDevice(BluetoothDevice device) {
        mDeviceConnectionStack.remove(device);
        mDeviceConnectionStack.add(device);
        mMostRecentDevice = device;

        if (device == null) {
            mAudioRoutingManager.removeActiveDevice(BluetoothProfile.HEADSET, false);
        } else {
            mAudioRoutingManager.activateDeviceProfile(device, BluetoothProfile.HEADSET, null);
        }
    }

    /** Helper to indicate Hearing Aid connected for a device. */
    private void hearingAidConnected(BluetoothDevice device) {
        mDatabaseManager.setProfileConnectionPolicy(
                device, BluetoothProfile.HEARING_AID, BluetoothProfile.CONNECTION_POLICY_ALLOWED);

        mDeviceConnectionStack.add(device);
        mMostRecentDevice = device;

        mAudioRoutingManager.profileConnectionStateChanged(
                BluetoothProfile.HEARING_AID,
                device,
                BluetoothProfile.STATE_DISCONNECTED,
                BluetoothProfile.STATE_CONNECTED);
    }

    /** Helper to indicate Hearing Aid disconnected for a device. */
    private void hearingAidDisconnected(BluetoothDevice device) {
        mDeviceConnectionStack.remove(device);
        mMostRecentDevice =
                (mDeviceConnectionStack.size() > 0)
                        ? mDeviceConnectionStack.get(mDeviceConnectionStack.size() - 1)
                        : null;

        mAudioRoutingManager.profileConnectionStateChanged(
                BluetoothProfile.HEARING_AID,
                device,
                BluetoothProfile.STATE_CONNECTED,
                BluetoothProfile.STATE_DISCONNECTED);
    }

    /** Helper to indicate Hearing Aid active device changed for a device. */
    private void switchHearingAidActiveDevice(BluetoothDevice device) {
        mDeviceConnectionStack.remove(device);
        mDeviceConnectionStack.add(device);
        mMostRecentDevice = device;

        if (device == null) {
            mAudioRoutingManager.removeActiveDevice(BluetoothProfile.HEARING_AID, false);
        } else {
            mAudioRoutingManager.activateDeviceProfile(device, BluetoothProfile.HEARING_AID, null);
        }
    }

    /** Helper to indicate LE Audio connected for a device. */
    private void leAudioConnected(BluetoothDevice device) {
        mDatabaseManager.setProfileConnectionPolicy(
                device, BluetoothProfile.LE_AUDIO, BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        mMostRecentDevice = device;

        mAudioRoutingManager.profileConnectionStateChanged(
                BluetoothProfile.LE_AUDIO,
                device,
                BluetoothProfile.STATE_DISCONNECTED,
                BluetoothProfile.STATE_CONNECTED);
    }

    /** Helper to indicate LE Audio disconnected for a device. */
    private void leAudioDisconnected(BluetoothDevice device) {
        mDeviceConnectionStack.remove(device);
        mMostRecentDevice =
                (mDeviceConnectionStack.size() > 0)
                        ? mDeviceConnectionStack.get(mDeviceConnectionStack.size() - 1)
                        : null;

        mAudioRoutingManager.profileConnectionStateChanged(
                BluetoothProfile.LE_AUDIO,
                device,
                BluetoothProfile.STATE_CONNECTED,
                BluetoothProfile.STATE_DISCONNECTED);
    }

    /** Helper to indicate LE Audio active device changed for a device. */
    private void switchLeAudioActiveDevice(BluetoothDevice device) {
        mDeviceConnectionStack.remove(device);
        mDeviceConnectionStack.add(device);
        mMostRecentDevice = device;

        if (device == null) {
            mAudioRoutingManager.removeActiveDevice(BluetoothProfile.LE_AUDIO, false);
        } else {
            mAudioRoutingManager.activateDeviceProfile(device, BluetoothProfile.LE_AUDIO, null);
        }
    }

    private class TestDatabaseManager extends DatabaseManager {
        ArrayMap<BluetoothDevice, SparseIntArray> mProfileConnectionPolicy;

        TestDatabaseManager(AdapterService service) {
            super(service);
            mProfileConnectionPolicy = new ArrayMap<>();
        }

        @Override
        public BluetoothDevice getMostRecentlyConnectedDevicesInList(
                List<BluetoothDevice> devices) {
            if (devices == null || devices.size() == 0) {
                return null;
            } else if (mMostRecentDevice != null && devices.contains(mMostRecentDevice)) {
                return mMostRecentDevice;
            }
            return devices.get(0);
        }

        @Override
        public boolean setProfileConnectionPolicy(BluetoothDevice device, int profile, int policy) {
            if (device == null) {
                return false;
            }
            if (policy != BluetoothProfile.CONNECTION_POLICY_UNKNOWN
                    && policy != BluetoothProfile.CONNECTION_POLICY_FORBIDDEN
                    && policy != BluetoothProfile.CONNECTION_POLICY_ALLOWED) {
                return false;
            }
            SparseIntArray policyMap = mProfileConnectionPolicy.get(device);
            if (policyMap == null) {
                policyMap = new SparseIntArray();
                mProfileConnectionPolicy.put(device, policyMap);
            }
            policyMap.put(profile, policy);
            return true;
        }

        @Override
        public int getProfileConnectionPolicy(BluetoothDevice device, int profile) {
            SparseIntArray policy = mProfileConnectionPolicy.get(device);
            if (policy == null) {
                return BluetoothProfile.CONNECTION_POLICY_FORBIDDEN;
            }
            return policy.get(profile, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);
        }
    }
}
