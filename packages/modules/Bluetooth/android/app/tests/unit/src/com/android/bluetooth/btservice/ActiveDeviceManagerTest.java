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

package com.android.bluetooth.btservice;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothLeBroadcastMetadata;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSinkAudioPolicy;
import android.content.Context;
import android.media.AudioManager;
import android.os.test.TestLooper;
import android.platform.test.flag.junit.SetFlagsRule;
import android.util.ArrayMap;
import android.util.SparseIntArray;

import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.BluetoothMethodProxy;
import com.android.bluetooth.TestUtils;
import com.android.bluetooth.Utils;
import com.android.bluetooth.a2dp.A2dpService;
import com.android.bluetooth.btservice.storage.DatabaseManager;
import com.android.bluetooth.flags.Flags;
import com.android.bluetooth.hearingaid.HearingAidService;
import com.android.bluetooth.hfp.HeadsetService;
import com.android.bluetooth.le_audio.LeAudioService;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class ActiveDeviceManagerTest {
    private BluetoothAdapter mAdapter;
    private BluetoothDevice mA2dpDevice;
    private BluetoothDevice mHeadsetDevice;
    private BluetoothDevice mA2dpHeadsetDevice;
    private BluetoothDevice mHearingAidDevice;
    private BluetoothDevice mLeAudioDevice;
    private BluetoothDevice mLeAudioDevice2;
    private BluetoothDevice mLeHearingAidDevice;
    private BluetoothDevice mSecondaryAudioDevice;
    private BluetoothDevice mDualModeAudioDevice;
    private ArrayList<BluetoothDevice> mDeviceConnectionStack;
    private BluetoothDevice mMostRecentDevice;
    private ActiveDeviceManager mActiveDeviceManager;
    private long mHearingAidHiSyncId = 1010;

    private static final int TIMEOUT_MS = 1_000;
    private static final int A2DP_HFP_SYNC_CONNECTION_TIMEOUT_MS =
            ActiveDeviceManager.A2DP_HFP_SYNC_CONNECTION_TIMEOUT_MS + 2_000;
    private boolean mOriginalDualModeAudioState;
    private TestDatabaseManager mDatabaseManager;
    private TestLooper mTestLooper;
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

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
        doNothing().when(mMethodProxy).threadStart(any());
        mTestLooper.startAutoDispatch();
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

        mActiveDeviceManager = new ActiveDeviceManager(mAdapterService, mServiceFactory);
        mActiveDeviceManager.start();
        mAdapter = BluetoothAdapter.getDefaultAdapter();

        // Get devices for testing
        mA2dpDevice = TestUtils.getTestDevice(mAdapter, 0);
        mHeadsetDevice = TestUtils.getTestDevice(mAdapter, 1);
        mA2dpHeadsetDevice = TestUtils.getTestDevice(mAdapter, 2);
        mHearingAidDevice = TestUtils.getTestDevice(mAdapter, 3);
        mLeAudioDevice = TestUtils.getTestDevice(mAdapter, 4);
        mLeHearingAidDevice = TestUtils.getTestDevice(mAdapter, 5);
        mSecondaryAudioDevice = TestUtils.getTestDevice(mAdapter, 6);
        mDualModeAudioDevice = TestUtils.getTestDevice(mAdapter, 7);
        mLeAudioDevice2 = TestUtils.getTestDevice(mAdapter, 8);
        mDeviceConnectionStack = new ArrayList<>();
        mMostRecentDevice = null;
        mOriginalDualModeAudioState = Utils.isDualModeAudioEnabled();

        when(mA2dpService.setActiveDevice(any())).thenReturn(true);
        when(mHeadsetService.getHfpCallAudioPolicy(any())).thenReturn(
                new BluetoothSinkAudioPolicy.Builder().build());
        when(mHeadsetService.setActiveDevice(any())).thenReturn(true);
        when(mHearingAidService.setActiveDevice(any())).thenReturn(true);
        when(mLeAudioService.setActiveDevice(any())).thenReturn(true);
        when(mLeAudioService.removeActiveDevice(anyBoolean())).thenReturn(true);

        when(mLeAudioService.getLeadDevice(mLeAudioDevice)).thenReturn(mLeAudioDevice);
        when(mLeAudioService.getLeadDevice(mLeAudioDevice2)).thenReturn(mLeAudioDevice2);
        when(mLeAudioService.getLeadDevice(mDualModeAudioDevice)).thenReturn(mDualModeAudioDevice);
        when(mLeAudioService.getLeadDevice(mLeHearingAidDevice)).thenReturn(mLeHearingAidDevice);

        List<BluetoothDevice> connectedHearingAidDevices = new ArrayList<>();
        connectedHearingAidDevices.add(mHearingAidDevice);
        when(mHearingAidService.getHiSyncId(mHearingAidDevice)).thenReturn(mHearingAidHiSyncId);
        when(mHearingAidService.getConnectedPeerDevices(mHearingAidHiSyncId))
                .thenReturn(connectedHearingAidDevices);

        when(mA2dpService.getFallbackDevice()).thenAnswer(invocation -> {
            if (!mDeviceConnectionStack.isEmpty() && Objects.equals(mA2dpDevice,
                    mDeviceConnectionStack.get(mDeviceConnectionStack.size() - 1))) {
                return mA2dpDevice;
            }
            return null;
        });
        when(mHeadsetService.getFallbackDevice()).thenAnswer(invocation -> {
            if (!mDeviceConnectionStack.isEmpty() && Objects.equals(mHeadsetDevice,
                    mDeviceConnectionStack.get(mDeviceConnectionStack.size() - 1))) {
                return mHeadsetDevice;
            }
            return null;
        });
    }

    @After
    public void tearDown() throws Exception {
        mTestLooper.stopAutoDispatchAndIgnoreExceptions();
        BluetoothMethodProxy.setInstanceForTesting(null);
        mActiveDeviceManager.cleanup();
        TestUtils.clearAdapterService(mAdapterService);
        Utils.setDualModeAudioStateForTesting(mOriginalDualModeAudioState);
    }

    @Test
    public void testSetUpAndTearDown() {}

    /**
     * One A2DP is connected.
     */
    @Test
    public void onlyA2dpConnected_setA2dpActive() {
        a2dpConnected(mA2dpDevice, false);
        verify(mA2dpService, timeout(TIMEOUT_MS)).setActiveDevice(mA2dpDevice);
    }

    @Test
    public void a2dpHeadsetConnected_setA2dpActiveShouldBeCalledAfterHeadsetConnected() {
        when(mAudioManager.getMode()).thenReturn(AudioManager.MODE_IN_CALL);

        a2dpConnected(mA2dpHeadsetDevice, true);
        verify(mA2dpService, after(TIMEOUT_MS).never()).setActiveDevice(mA2dpHeadsetDevice);
        headsetConnected(mA2dpHeadsetDevice, true);
        verify(mA2dpService, timeout(TIMEOUT_MS)).setActiveDevice(mA2dpHeadsetDevice);
        verify(mHeadsetService, timeout(TIMEOUT_MS)).setActiveDevice(mA2dpHeadsetDevice);
    }

    @Test
    public void a2dpAndHfpConnectedAtTheSameTime_setA2dpActiveShouldBeCalled() {
        mTestLooper.stopAutoDispatchAndIgnoreExceptions();
        when(mAudioManager.getMode()).thenReturn(AudioManager.MODE_IN_CALL);

        a2dpConnected(mA2dpHeadsetDevice, true);
        headsetConnected(mA2dpHeadsetDevice, true);
        mTestLooper.dispatchAll();
        verify(mA2dpService).setActiveDevice(mA2dpHeadsetDevice);
        verify(mHeadsetService).setActiveDevice(mA2dpHeadsetDevice);
    }

    /**
     * Two A2DP are connected. Should set the second one active.
     */
    @Test
    public void secondA2dpConnected_setSecondA2dpActive() {
        a2dpConnected(mA2dpDevice, false);
        verify(mA2dpService, timeout(TIMEOUT_MS)).setActiveDevice(mA2dpDevice);

        a2dpConnected(mSecondaryAudioDevice, false);
        verify(mA2dpService, timeout(TIMEOUT_MS)).setActiveDevice(mSecondaryAudioDevice);
    }

    /**
     * One A2DP is connected and disconnected later. Should then set active device to null.
     */
    @Test
    public void lastA2dpDisconnected_clearA2dpActive() {
        a2dpConnected(mA2dpDevice, false);
        verify(mA2dpService, timeout(TIMEOUT_MS)).setActiveDevice(mA2dpDevice);

        a2dpDisconnected(mA2dpDevice);
        verify(mA2dpService, timeout(TIMEOUT_MS)).removeActiveDevice(true);
    }

    /**
     * Two A2DP are connected and active device is explicitly set.
     */
    @Test
    public void a2dpActiveDeviceSelected_setActive() {
        a2dpConnected(mA2dpDevice, false);
        verify(mA2dpService, timeout(TIMEOUT_MS)).setActiveDevice(mA2dpDevice);

        a2dpConnected(mSecondaryAudioDevice, false);
        verify(mA2dpService, timeout(TIMEOUT_MS)).setActiveDevice(mSecondaryAudioDevice);

        a2dpActiveDeviceChanged(mA2dpDevice);
        // Don't call mA2dpService.setActiveDevice()
        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        verify(mA2dpService, times(1)).setActiveDevice(mA2dpDevice);
        Assert.assertEquals(mA2dpDevice, mActiveDeviceManager.getA2dpActiveDevice());
    }

    /**
     * Two A2DP devices are connected and the current active is then disconnected.
     * Should then set active device to fallback device.
     */
    @Test
    public void a2dpSecondDeviceDisconnected_fallbackDeviceActive() {
        a2dpConnected(mA2dpDevice, false);
        verify(mA2dpService, timeout(TIMEOUT_MS)).setActiveDevice(mA2dpDevice);

        a2dpConnected(mSecondaryAudioDevice, false);
        verify(mA2dpService, timeout(TIMEOUT_MS)).setActiveDevice(mSecondaryAudioDevice);

        Mockito.clearInvocations(mA2dpService);
        a2dpDisconnected(mSecondaryAudioDevice);
        verify(mA2dpService, timeout(TIMEOUT_MS)).setActiveDevice(mA2dpDevice);
    }

    /**
     * One Headset is connected.
     */
    @Test
    public void onlyHeadsetConnected_setHeadsetActive() {
        headsetConnected(mHeadsetDevice, false);
        verify(mHeadsetService, timeout(TIMEOUT_MS)).setActiveDevice(mHeadsetDevice);
    }

    /**
     * Two Headset are connected. Should set the second one active.
     */
    @Test
    public void secondHeadsetConnected_setSecondHeadsetActive() {
        headsetConnected(mHeadsetDevice, false);
        verify(mHeadsetService, timeout(TIMEOUT_MS)).setActiveDevice(mHeadsetDevice);

        headsetConnected(mSecondaryAudioDevice, false);
        verify(mHeadsetService, timeout(TIMEOUT_MS)).setActiveDevice(mSecondaryAudioDevice);
    }

    /**
     * One Headset is connected and disconnected later. Should then set active device to null.
     */
    @Test
    public void lastHeadsetDisconnected_clearHeadsetActive() {
        headsetConnected(mHeadsetDevice, false);
        verify(mHeadsetService, timeout(TIMEOUT_MS)).setActiveDevice(mHeadsetDevice);

        headsetDisconnected(mHeadsetDevice);
        verify(mHeadsetService, timeout(TIMEOUT_MS)).setActiveDevice(isNull());
    }

    /**
     * Two Headset are connected and active device is explicitly set.
     */
    @Test
    public void headsetActiveDeviceSelected_setActive() {
        headsetConnected(mHeadsetDevice, false);
        verify(mHeadsetService, timeout(TIMEOUT_MS)).setActiveDevice(mHeadsetDevice);

        headsetConnected(mSecondaryAudioDevice, false);
        verify(mHeadsetService, timeout(TIMEOUT_MS)).setActiveDevice(mSecondaryAudioDevice);

        headsetActiveDeviceChanged(mHeadsetDevice);
        // Don't call mHeadsetService.setActiveDevice()
        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        verify(mHeadsetService, times(1)).setActiveDevice(mHeadsetDevice);
        Assert.assertEquals(mHeadsetDevice, mActiveDeviceManager.getHfpActiveDevice());
    }

    /**
     * Two Headsets are connected and the current active is then disconnected.
     * Should then set active device to fallback device.
     */
    @Test
    public void headsetSecondDeviceDisconnected_fallbackDeviceActive() {
        when(mAudioManager.getMode()).thenReturn(AudioManager.MODE_IN_CALL);

        headsetConnected(mHeadsetDevice, false);
        verify(mHeadsetService, timeout(TIMEOUT_MS)).setActiveDevice(mHeadsetDevice);

        headsetConnected(mSecondaryAudioDevice, false);
        verify(mHeadsetService, timeout(TIMEOUT_MS)).setActiveDevice(mSecondaryAudioDevice);

        Mockito.clearInvocations(mHeadsetService);
        headsetDisconnected(mSecondaryAudioDevice);
        verify(mHeadsetService, timeout(TIMEOUT_MS)).setActiveDevice(mHeadsetDevice);
    }

    @Test
    public void a2dpConnectedButHeadsetNotConnected_setA2dpActive() {
        mTestLooper.stopAutoDispatchAndIgnoreExceptions();
        when(mAudioManager.getMode()).thenReturn(AudioManager.MODE_IN_CALL);
        a2dpConnected(mA2dpHeadsetDevice, true);

        mTestLooper.moveTimeForward(ActiveDeviceManager.A2DP_HFP_SYNC_CONNECTION_TIMEOUT_MS / 2);
        mTestLooper.dispatchAll();
        verify(mA2dpService, never()).setActiveDevice(mA2dpHeadsetDevice);
        mTestLooper.moveTimeForward(A2DP_HFP_SYNC_CONNECTION_TIMEOUT_MS);
        mTestLooper.dispatchAll();
        verify(mA2dpService).setActiveDevice(mA2dpHeadsetDevice);
    }

    @Test
    public void headsetConnectedButA2dpNotConnected_setHeadsetActive() {
        mTestLooper.stopAutoDispatchAndIgnoreExceptions();
        when(mAudioManager.getMode()).thenReturn(AudioManager.MODE_NORMAL);
        headsetConnected(mA2dpHeadsetDevice, true);

        mTestLooper.moveTimeForward(ActiveDeviceManager.A2DP_HFP_SYNC_CONNECTION_TIMEOUT_MS / 2);
        mTestLooper.dispatchAll();
        verify(mHeadsetService, never()).setActiveDevice(mA2dpHeadsetDevice);
        mTestLooper.moveTimeForward(A2DP_HFP_SYNC_CONNECTION_TIMEOUT_MS);
        mTestLooper.dispatchAll();
        verify(mHeadsetService).setActiveDevice(mA2dpHeadsetDevice);
    }

    @Test
    public void hfpActivatedAfterA2dpActivated_shouldNotActivateA2dpAgain() {
        a2dpConnected(mA2dpHeadsetDevice, true);
        a2dpConnected(mSecondaryAudioDevice, true);
        headsetConnected(mA2dpHeadsetDevice, true);
        headsetConnected(mSecondaryAudioDevice, true);

        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        assertThat(mActiveDeviceManager.getA2dpActiveDevice()).isEqualTo(mSecondaryAudioDevice);
        assertThat(mActiveDeviceManager.getHfpActiveDevice()).isEqualTo(mSecondaryAudioDevice);

        Mockito.clearInvocations(mHeadsetService);
        Mockito.clearInvocations(mA2dpService);

        // When A2DP is activated, then it should activate HFP
        a2dpActiveDeviceChanged(mA2dpHeadsetDevice);
        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        verify(mHeadsetService).setActiveDevice(mA2dpHeadsetDevice);

        // If HFP activated already, it should not activate A2DP again
        headsetActiveDeviceChanged(mA2dpHeadsetDevice);
        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        verify(mA2dpService, never()).setActiveDevice(mA2dpHeadsetDevice);
    }

    @Test
    public void hfpActivatedAfterTimeout_shouldActivateA2dpAgain() {
        mTestLooper.stopAutoDispatchAndIgnoreExceptions();
        a2dpConnected(mA2dpHeadsetDevice, true);
        headsetConnected(mA2dpHeadsetDevice, true);
        a2dpActiveDeviceChanged(null);
        headsetActiveDeviceChanged(null);

        mTestLooper.dispatchAll();
        Mockito.clearInvocations(mHeadsetService);
        Mockito.clearInvocations(mA2dpService);

        // When A2DP is activated, then it should activate HFP
        a2dpActiveDeviceChanged(mA2dpHeadsetDevice);
        mTestLooper.moveTimeForward(A2DP_HFP_SYNC_CONNECTION_TIMEOUT_MS);
        mTestLooper.dispatchAll();
        verify(mA2dpService, never()).setActiveDevice(any());
        verify(mHeadsetService).setActiveDevice(mA2dpHeadsetDevice);

        a2dpActiveDeviceChanged(null);
        // When HFP activated after timeout, it should activate A2DP again
        headsetActiveDeviceChanged(mA2dpHeadsetDevice);
        mTestLooper.dispatchAll();
        verify(mA2dpService).setActiveDevice(mA2dpHeadsetDevice);
    }

    @Test
    public void a2dpHeadsetActivated_whileActivatingAnotherA2dpHeadset() {
        a2dpConnected(mA2dpHeadsetDevice, true);
        a2dpConnected(mSecondaryAudioDevice, true);
        headsetConnected(mA2dpHeadsetDevice, true);
        headsetConnected(mSecondaryAudioDevice, true);

        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        assertThat(mActiveDeviceManager.getA2dpActiveDevice()).isEqualTo(mSecondaryAudioDevice);
        assertThat(mActiveDeviceManager.getHfpActiveDevice()).isEqualTo(mSecondaryAudioDevice);

        Mockito.clearInvocations(mHeadsetService);
        Mockito.clearInvocations(mA2dpService);

        // Test HS1 A2DP -> HS2 A2DP -> HS1 HFP -> HS2 HFP
        a2dpActiveDeviceChanged(mA2dpHeadsetDevice);
        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        assertThat(mActiveDeviceManager.getA2dpActiveDevice()).isEqualTo(mA2dpHeadsetDevice);
        assertThat(mActiveDeviceManager.getHfpActiveDevice()).isEqualTo(mA2dpHeadsetDevice);
        verify(mHeadsetService).setActiveDevice(mA2dpHeadsetDevice);

        a2dpActiveDeviceChanged(mSecondaryAudioDevice);
        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        assertThat(mActiveDeviceManager.getA2dpActiveDevice()).isEqualTo(mSecondaryAudioDevice);
        assertThat(mActiveDeviceManager.getHfpActiveDevice()).isEqualTo(mSecondaryAudioDevice);
        verify(mHeadsetService).setActiveDevice(mSecondaryAudioDevice);

        headsetActiveDeviceChanged(mA2dpHeadsetDevice);
        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        assertThat(mActiveDeviceManager.getA2dpActiveDevice()).isEqualTo(mSecondaryAudioDevice);
        assertThat(mActiveDeviceManager.getHfpActiveDevice()).isEqualTo(mA2dpHeadsetDevice);
        verify(mA2dpService, never()).setActiveDevice(any());

        headsetActiveDeviceChanged(mSecondaryAudioDevice);
        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        assertThat(mActiveDeviceManager.getA2dpActiveDevice()).isEqualTo(mSecondaryAudioDevice);
        assertThat(mActiveDeviceManager.getHfpActiveDevice()).isEqualTo(mSecondaryAudioDevice);
        verify(mA2dpService, never()).setActiveDevice(any());

        Mockito.clearInvocations(mHeadsetService);
        Mockito.clearInvocations(mA2dpService);

        // Test HS1 HFP -> HS2 HFP -> HS1 A2DP -> HS2 A2DP
        headsetActiveDeviceChanged(mA2dpHeadsetDevice);
        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        assertThat(mActiveDeviceManager.getA2dpActiveDevice()).isEqualTo(mA2dpHeadsetDevice);
        assertThat(mActiveDeviceManager.getHfpActiveDevice()).isEqualTo(mA2dpHeadsetDevice);
        verify(mA2dpService).setActiveDevice(mA2dpHeadsetDevice);

        headsetActiveDeviceChanged(mSecondaryAudioDevice);
        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        assertThat(mActiveDeviceManager.getA2dpActiveDevice()).isEqualTo(mSecondaryAudioDevice);
        assertThat(mActiveDeviceManager.getHfpActiveDevice()).isEqualTo(mSecondaryAudioDevice);
        verify(mA2dpService).setActiveDevice(mSecondaryAudioDevice);

        a2dpActiveDeviceChanged(mA2dpHeadsetDevice);
        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        assertThat(mActiveDeviceManager.getA2dpActiveDevice()).isEqualTo(mA2dpHeadsetDevice);
        assertThat(mActiveDeviceManager.getHfpActiveDevice()).isEqualTo(mSecondaryAudioDevice);
        verify(mHeadsetService, never()).setActiveDevice(any());

        a2dpActiveDeviceChanged(mSecondaryAudioDevice);
        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        assertThat(mActiveDeviceManager.getA2dpActiveDevice()).isEqualTo(mSecondaryAudioDevice);
        assertThat(mActiveDeviceManager.getHfpActiveDevice()).isEqualTo(mSecondaryAudioDevice);
        verify(mHeadsetService, never()).setActiveDevice(any());

        Mockito.clearInvocations(mHeadsetService);
        Mockito.clearInvocations(mA2dpService);
    }

    @Test
    public void hfpActivated_whileActivatingA2dpHeadset() {
        headsetConnected(mHeadsetDevice, false);
        a2dpConnected(mA2dpHeadsetDevice, true);
        headsetConnected(mA2dpHeadsetDevice, true);
        a2dpActiveDeviceChanged(null);
        headsetActiveDeviceChanged(null);

        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        Mockito.clearInvocations(mHeadsetService);
        Mockito.clearInvocations(mA2dpService);

        // Test HS1 HFP -> HFP only -> HS1 A2DP
        headsetActiveDeviceChanged(mA2dpHeadsetDevice);
        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        assertThat(mActiveDeviceManager.getA2dpActiveDevice()).isEqualTo(mA2dpHeadsetDevice);
        assertThat(mActiveDeviceManager.getHfpActiveDevice()).isEqualTo(mA2dpHeadsetDevice);
        verify(mA2dpService).setActiveDevice(mA2dpHeadsetDevice);

        headsetActiveDeviceChanged(mHeadsetDevice);
        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        assertThat(mActiveDeviceManager.getA2dpActiveDevice()).isEqualTo(mA2dpHeadsetDevice);
        assertThat(mActiveDeviceManager.getHfpActiveDevice()).isEqualTo(mHeadsetDevice);
        verify(mA2dpService, never()).setActiveDevice(mHeadsetDevice);

        a2dpActiveDeviceChanged(mA2dpHeadsetDevice);
        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        assertThat(mActiveDeviceManager.getA2dpActiveDevice()).isEqualTo(mA2dpHeadsetDevice);
        assertThat(mActiveDeviceManager.getHfpActiveDevice()).isEqualTo(mHeadsetDevice);
        verify(mHeadsetService, never()).setActiveDevice(any());
    }

    @Test
    public void a2dpActivated_whileActivatingA2dpHeadset() {
        a2dpConnected(mA2dpDevice, false);
        a2dpConnected(mA2dpHeadsetDevice, true);
        headsetConnected(mA2dpHeadsetDevice, true);
        a2dpActiveDeviceChanged(null);
        headsetActiveDeviceChanged(null);

        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        Mockito.clearInvocations(mHeadsetService);
        Mockito.clearInvocations(mA2dpService);

        // Test HS1 HFP -> A2DP only -> HS1 A2DP
        headsetActiveDeviceChanged(mA2dpHeadsetDevice);
        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        assertThat(mActiveDeviceManager.getA2dpActiveDevice()).isEqualTo(mA2dpHeadsetDevice);
        assertThat(mActiveDeviceManager.getHfpActiveDevice()).isEqualTo(mA2dpHeadsetDevice);
        verify(mA2dpService).setActiveDevice(mA2dpHeadsetDevice);

        a2dpActiveDeviceChanged(mA2dpDevice);
        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        assertThat(mActiveDeviceManager.getA2dpActiveDevice()).isEqualTo(mA2dpDevice);
        assertThat(mActiveDeviceManager.getHfpActiveDevice()).isEqualTo(mA2dpHeadsetDevice);
        verify(mHeadsetService, never()).setActiveDevice(mA2dpDevice);

        a2dpActiveDeviceChanged(mA2dpHeadsetDevice);
        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        assertThat(mActiveDeviceManager.getA2dpActiveDevice()).isEqualTo(mA2dpHeadsetDevice);
        assertThat(mActiveDeviceManager.getHfpActiveDevice()).isEqualTo(mA2dpHeadsetDevice);
        verify(mHeadsetService, never()).setActiveDevice(any());
    }

    /**
     * A headset device with connecting audio policy set to NOT ALLOWED.
     */
    @Test
    public void notAllowedConnectingPolicyHeadsetConnected_noSetActiveDevice() {
        // setting connecting policy to NOT ALLOWED
        when(mHeadsetService.getHfpCallAudioPolicy(mHeadsetDevice))
                .thenReturn(new BluetoothSinkAudioPolicy.Builder()
                        .setCallEstablishPolicy(BluetoothSinkAudioPolicy.POLICY_ALLOWED)
                        .setActiveDevicePolicyAfterConnection(
                                BluetoothSinkAudioPolicy.POLICY_NOT_ALLOWED)
                        .setInBandRingtonePolicy(BluetoothSinkAudioPolicy.POLICY_ALLOWED)
                        .build());

        headsetConnected(mHeadsetDevice, false);
        verify(mHeadsetService, never()).setActiveDevice(mHeadsetDevice);
    }

    @Test
    public void twoHearingAidDevicesConnected_WithTheSameHiSyncId() {
        Assume.assumeTrue("Ignore test when HearingAidService is not enabled",
                HearingAidService.isEnabled());

        List<BluetoothDevice> connectedHearingAidDevices = new ArrayList<>();
        connectedHearingAidDevices.add(mHearingAidDevice);
        connectedHearingAidDevices.add(mSecondaryAudioDevice);
        when(mHearingAidService.getHiSyncId(mSecondaryAudioDevice))
                .thenReturn(mHearingAidHiSyncId);
        when(mHearingAidService.getConnectedPeerDevices(mHearingAidHiSyncId))
                .thenReturn(connectedHearingAidDevices);

        hearingAidConnected(mHearingAidDevice);
        hearingAidConnected(mSecondaryAudioDevice);
        verify(mHearingAidService, timeout(TIMEOUT_MS)).setActiveDevice(mHearingAidDevice);
        verify(mHearingAidService, never()).setActiveDevice(mSecondaryAudioDevice);
    }

    /**
     * A combo (A2DP + Headset) device is connected. Then a Hearing Aid is connected.
     */
    @Test
    public void hearingAidActive_clearA2dpAndHeadsetActive() {
        a2dpConnected(mA2dpHeadsetDevice, true);
        headsetConnected(mA2dpHeadsetDevice, true);
        verify(mA2dpService, timeout(TIMEOUT_MS).atLeastOnce()).setActiveDevice(mA2dpHeadsetDevice);
        verify(mHeadsetService, timeout(TIMEOUT_MS).atLeastOnce())
                .setActiveDevice(mA2dpHeadsetDevice);

        hearingAidActiveDeviceChanged(mHearingAidDevice);
        verify(mA2dpService, timeout(TIMEOUT_MS)).removeActiveDevice(false);
        verify(mHeadsetService, timeout(TIMEOUT_MS)).setActiveDevice(null);
    }

    /**
     * A Hearing Aid is connected. Then a combo (A2DP + Headset) device is connected.
     */
    @Test
    public void hearingAidActive_dontSetA2dpAndHeadsetActive() {
        hearingAidActiveDeviceChanged(mHearingAidDevice);
        a2dpConnected(mA2dpHeadsetDevice, true);
        headsetConnected(mA2dpHeadsetDevice, true);

        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        verify(mA2dpService, never()).setActiveDevice(mA2dpHeadsetDevice);
        verify(mHeadsetService, never()).setActiveDevice(mA2dpHeadsetDevice);
    }

    /**
     * A Hearing Aid is connected. Then an A2DP active device is explicitly set.
     */
    @Test
    public void hearingAidActive_setA2dpActiveExplicitly() {
        when(mHearingAidService.removeActiveDevice(anyBoolean())).thenReturn(true);

        hearingAidActiveDeviceChanged(mHearingAidDevice);
        a2dpConnected(mA2dpDevice, false);
        a2dpActiveDeviceChanged(mA2dpDevice);

        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        verify(mHearingAidService).removeActiveDevice(false);
        // Don't call mA2dpService.setActiveDevice()
        verify(mA2dpService, never()).setActiveDevice(mA2dpDevice);
        Assert.assertEquals(mA2dpDevice, mActiveDeviceManager.getA2dpActiveDevice());
        Assert.assertTrue(mActiveDeviceManager.getHearingAidActiveDevices().isEmpty());
    }

    /**
     * A Hearing Aid is connected. Then a Headset active device is explicitly set.
     */
    @Test
    public void hearingAidActive_setHeadsetActiveExplicitly() {
        when(mHearingAidService.removeActiveDevice(anyBoolean())).thenReturn(true);

        hearingAidActiveDeviceChanged(mHearingAidDevice);
        headsetConnected(mHeadsetDevice, false);
        headsetActiveDeviceChanged(mHeadsetDevice);

        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        verify(mHearingAidService).removeActiveDevice(false);
        // Don't call mHeadsetService.setActiveDevice()
        verify(mHeadsetService, never()).setActiveDevice(mHeadsetDevice);
        Assert.assertEquals(mHeadsetDevice, mActiveDeviceManager.getHfpActiveDevice());
        Assert.assertTrue(mActiveDeviceManager.getHearingAidActiveDevices().isEmpty());
    }

    @Test
    public void hearingAidActiveWithNull_clearHearingAidActiveDevices() {
        hearingAidActiveDeviceChanged(null);
        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        assertThat(mActiveDeviceManager.getHearingAidActiveDevices()).isEmpty();
    }

    /**
     * One LE Audio is connected.
     */
    @Test
    public void onlyLeAudioConnected_setHeadsetActive() {
        leAudioConnected(mLeAudioDevice);
        verify(mLeAudioService, timeout(TIMEOUT_MS)).setActiveDevice(mLeAudioDevice);
    }

    /**
     * Two LE Audio are connected. Should set the second one active.
     */
    @Test
    public void secondLeAudioConnected_setSecondLeAudioActive() {
        leAudioConnected(mLeAudioDevice);
        verify(mLeAudioService, timeout(TIMEOUT_MS)).setActiveDevice(mLeAudioDevice);

        leAudioConnected(mSecondaryAudioDevice);
        verify(mLeAudioService, timeout(TIMEOUT_MS)).setActiveDevice(mSecondaryAudioDevice);
    }

    /**
     * One LE Audio  is connected and disconnected later. Should then set active device to null.
     */
    @Test
    public void lastLeAudioDisconnected_clearLeAudioActive() {
        leAudioConnected(mLeAudioDevice);
        verify(mLeAudioService, timeout(TIMEOUT_MS)).setActiveDevice(mLeAudioDevice);

        leAudioDisconnected(mLeAudioDevice);
        verify(mLeAudioService, timeout(TIMEOUT_MS)).removeActiveDevice(false);
    }

    /**
     * Two LE Audio are connected and active device is explicitly set.
     */
    @Test
    public void leAudioActiveDeviceSelected_setActive() {
        leAudioConnected(mLeAudioDevice);
        verify(mLeAudioService, timeout(TIMEOUT_MS)).setActiveDevice(mLeAudioDevice);

        leAudioConnected(mSecondaryAudioDevice);
        verify(mLeAudioService, timeout(TIMEOUT_MS)).setActiveDevice(mSecondaryAudioDevice);

        Mockito.clearInvocations(mLeAudioService);
        leAudioActiveDeviceChanged(mLeAudioDevice);
        // Don't call mLeAudioService.setActiveDevice()
        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        verify(mLeAudioService, never()).setActiveDevice(any(BluetoothDevice.class));
        Assert.assertEquals(mLeAudioDevice, mActiveDeviceManager.getLeAudioActiveDevice());
    }

    /**
     * Two LE Audio are connected and the current active is then disconnected.
     * Should then set active device to fallback device.
     */
    @Test
    public void leAudioSecondDeviceDisconnected_fallbackDeviceActive() {
        leAudioConnected(mLeAudioDevice);
        verify(mLeAudioService, timeout(TIMEOUT_MS)).setActiveDevice(mLeAudioDevice);

        leAudioConnected(mLeAudioDevice2);
        verify(mLeAudioService, timeout(TIMEOUT_MS)).setActiveDevice(mLeAudioDevice2);

        Mockito.clearInvocations(mLeAudioService);
        leAudioDisconnected(mLeAudioDevice2);
        verify(mLeAudioService, timeout(TIMEOUT_MS)).setActiveDevice(mLeAudioDevice);
    }

    /**
     * A combo (A2DP + Headset) device is connected. Then an LE Audio is connected.
     */
    @Test
    public void leAudioActive_clearA2dpAndHeadsetActive() {
        a2dpConnected(mA2dpHeadsetDevice, true);
        headsetConnected(mA2dpHeadsetDevice, true);
        verify(mA2dpService, timeout(TIMEOUT_MS).atLeastOnce()).setActiveDevice(mA2dpHeadsetDevice);
        verify(mHeadsetService, timeout(TIMEOUT_MS).atLeastOnce())
                .setActiveDevice(mA2dpHeadsetDevice);

        leAudioActiveDeviceChanged(mLeAudioDevice);
        verify(mA2dpService, timeout(TIMEOUT_MS)).removeActiveDevice(false);
        verify(mHeadsetService, timeout(TIMEOUT_MS)).setActiveDevice(isNull());
    }

    /**
     * An LE Audio is connected. Then a combo (A2DP + Headset) device is connected.
     */
    @Test
    public void leAudioActive_setA2dpAndHeadsetActive() {
        leAudioActiveDeviceChanged(mLeAudioDevice);
        a2dpConnected(mA2dpHeadsetDevice, true);
        headsetConnected(mA2dpHeadsetDevice, true);

        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        verify(mA2dpService, atLeastOnce()).setActiveDevice(mA2dpHeadsetDevice);
        verify(mHeadsetService, atLeastOnce()).setActiveDevice(mA2dpHeadsetDevice);
    }

    /**
     * An LE Audio is connected. Then an A2DP active device is explicitly set.
     */
    @Test
    public void leAudioActive_setA2dpActiveExplicitly() {
        leAudioActiveDeviceChanged(mLeAudioDevice);
        a2dpConnected(mA2dpDevice, false);
        a2dpActiveDeviceChanged(mA2dpDevice);

        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        verify(mLeAudioService).removeActiveDevice(true);
        verify(mA2dpService).setActiveDevice(mA2dpDevice);
        Assert.assertEquals(mA2dpDevice, mActiveDeviceManager.getA2dpActiveDevice());
        Assert.assertNull(mActiveDeviceManager.getLeAudioActiveDevice());
    }

    /**
     * An LE Audio is connected. Then a Headset active device is explicitly set.
     */
    @Test
    public void leAudioActive_setHeadsetActiveExplicitly() {
        leAudioActiveDeviceChanged(mLeAudioDevice);
        headsetConnected(mHeadsetDevice, false);
        headsetActiveDeviceChanged(mHeadsetDevice);

        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        verify(mLeAudioService).removeActiveDevice(true);
        verify(mHeadsetService).setActiveDevice(mHeadsetDevice);
        Assert.assertEquals(mHeadsetDevice, mActiveDeviceManager.getHfpActiveDevice());
        Assert.assertNull(mActiveDeviceManager.getLeAudioActiveDevice());
    }

    /**
     * An LE Audio connected. An A2DP connected. The A2DP disconnected.
     * Then the LE Audio should be the active one.
     */
    @Test
    public void leAudioAndA2dpConnectedThenA2dpDisconnected_fallbackToLeAudio() {
        when(mAudioManager.getMode()).thenReturn(AudioManager.MODE_NORMAL);

        leAudioConnected(mLeAudioDevice);
        verify(mLeAudioService, timeout(TIMEOUT_MS)).setActiveDevice(mLeAudioDevice);

        a2dpConnected(mA2dpDevice, false);
        verify(mA2dpService, timeout(TIMEOUT_MS)).setActiveDevice(mA2dpDevice);

        Mockito.clearInvocations(mLeAudioService);
        a2dpDisconnected(mA2dpDevice);
        verify(mA2dpService, timeout(TIMEOUT_MS).atLeast(1)).removeActiveDevice(false);
        verify(mLeAudioService, timeout(TIMEOUT_MS)).setActiveDevice(mLeAudioDevice);
    }

    /**
     * An LE Audio set connected. The not active bud disconnected.
     * Then the active device should not change and hasFallback should be set to false.
     */
    @Test
    public void leAudioSetConnectedThenNotActiveOneDisconnected_noFallback() {
        when(mAudioManager.getMode()).thenReturn(AudioManager.MODE_NORMAL);

        leAudioConnected(mLeAudioDevice);
        verify(mLeAudioService, timeout(TIMEOUT_MS)).setActiveDevice(mLeAudioDevice);

        leAudioConnected(mLeAudioDevice2);
        verify(mLeAudioService, timeout(TIMEOUT_MS)).setActiveDevice(mLeAudioDevice2);

        Mockito.clearInvocations(mLeAudioService);

        leAudioDisconnected(mLeAudioDevice);

        verify(mLeAudioService, never()).removeActiveDevice(false);
        verify(mLeAudioService, never()).setActiveDevice(mLeAudioDevice2);
        verify(mLeAudioService, timeout(TIMEOUT_MS)).deviceDisconnected(mLeAudioDevice, false);
    }

    /**
     * An LE Audio set connected. The active bud disconnected. Set active device
     * returns false indicating an issue (the other bud is also disconnected).
     * Then the active device should be removed and hasFallback should be set to false.
     */
    @Test
    public void leAudioSetConnectedThenActiveOneDisconnected_noFallback() {
        when(mAudioManager.getMode()).thenReturn(AudioManager.MODE_NORMAL);

        leAudioConnected(mLeAudioDevice);
        verify(mLeAudioService, timeout(TIMEOUT_MS)).setActiveDevice(mLeAudioDevice);

        leAudioConnected(mLeAudioDevice2);
        verify(mLeAudioService, timeout(TIMEOUT_MS)).setActiveDevice(mLeAudioDevice2);

        Mockito.clearInvocations(mLeAudioService);

        // Return false to indicate an issue when setting new active device
        // (e.g. the other device disconnected as well).
        when(mLeAudioService.setActiveDevice(any())).thenReturn(false);

        leAudioDisconnected(mLeAudioDevice2);

        verify(mLeAudioService, timeout(TIMEOUT_MS)).removeActiveDevice(false);
        verify(mLeAudioService, timeout(TIMEOUT_MS)).deviceDisconnected(mLeAudioDevice2, false);
    }

    /**
     * An LE Audio set connected. The active bud disconnected. Set active device
     * returns true indicating the other bud is going to be the active device.
     * Then the active device should change and hasFallback should be set to true.
     */
    @Test
    public void leAudioSetConnectedThenActiveOneDisconnected_hasFallback() {
        when(mAudioManager.getMode()).thenReturn(AudioManager.MODE_NORMAL);

        leAudioConnected(mLeAudioDevice);
        verify(mLeAudioService, timeout(TIMEOUT_MS)).setActiveDevice(mLeAudioDevice);

        leAudioConnected(mLeAudioDevice2);
        verify(mLeAudioService, timeout(TIMEOUT_MS)).setActiveDevice(mLeAudioDevice2);

        Mockito.clearInvocations(mLeAudioService);

        leAudioDisconnected(mLeAudioDevice2);

        verify(mLeAudioService, timeout(TIMEOUT_MS)).setActiveDevice(mLeAudioDevice);
        verify(mLeAudioService, timeout(TIMEOUT_MS)).deviceDisconnected(mLeAudioDevice2, true);
    }

    @Test
    public void leAudioSetConnectedGroupThenDisconnected_noFallback() {
        mSetFlagsRule.enableFlags(Flags.FLAG_LEAUDIO_ACTIVE_DEVICE_MANAGER_GROUP_HANDLING_FIX);
        when(mAudioManager.getMode()).thenReturn(AudioManager.MODE_NORMAL);

        leAudioConnected(mLeAudioDevice);
        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        verify(mLeAudioService, timeout(TIMEOUT_MS)).setActiveDevice(mLeAudioDevice);

        Mockito.clearInvocations(mLeAudioService);

        when(mLeAudioService.getLeadDevice(mLeAudioDevice2)).thenReturn(mLeAudioDevice);
        leAudioConnected(mLeAudioDevice2);
        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        verify(mLeAudioService, never()).setActiveDevice(any());

        Mockito.clearInvocations(mLeAudioService);

        leAudioDisconnected(mLeAudioDevice2);
        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        verify(mLeAudioService, never()).setActiveDevice(any());
        verify(mLeAudioService, never()).removeActiveDevice(anyBoolean());

        leAudioDisconnected(mLeAudioDevice);
        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        verify(mLeAudioService, timeout(TIMEOUT_MS)).removeActiveDevice(false);
        verify(mLeAudioService, timeout(TIMEOUT_MS)).deviceDisconnected(mLeAudioDevice2, false);
    }

    /**
     * An A2DP connected. An LE Audio connected. The LE Audio disconnected.
     * Then the A2DP should be the active one.
     */
    @Test
    public void a2dpAndLeAudioConnectedThenLeAudioDisconnected_fallbackToA2dp() {
        when(mAudioManager.getMode()).thenReturn(AudioManager.MODE_NORMAL);

        a2dpConnected(mA2dpDevice, false);
        verify(mA2dpService, timeout(TIMEOUT_MS)).setActiveDevice(mA2dpDevice);

        leAudioConnected(mLeAudioDevice);
        verify(mLeAudioService, timeout(TIMEOUT_MS)).setActiveDevice(mLeAudioDevice);

        Mockito.clearInvocations(mA2dpService);
        leAudioDisconnected(mLeAudioDevice);
        verify(mLeAudioService, timeout(TIMEOUT_MS).atLeast(1)).removeActiveDevice(true);
        verify(mA2dpService, timeout(TIMEOUT_MS)).setActiveDevice(mA2dpDevice);
    }

    /**
     * Two Hearing Aid are connected and the current active is then disconnected.
     * Should then set active device to fallback device.
     */
    @Test
    public void hearingAidSecondDeviceDisconnected_fallbackDeviceActive() {
        hearingAidConnected(mHearingAidDevice);
        verify(mHearingAidService, timeout(TIMEOUT_MS)).setActiveDevice(mHearingAidDevice);

        List<BluetoothDevice> connectedHearingAidDevices = new ArrayList<>();
        connectedHearingAidDevices.add(mSecondaryAudioDevice);
        when(mHearingAidService.getHiSyncId(mSecondaryAudioDevice))
                .thenReturn(mHearingAidHiSyncId + 1);
        when(mHearingAidService.getConnectedPeerDevices(mHearingAidHiSyncId + 1))
                .thenReturn(connectedHearingAidDevices);

        hearingAidConnected(mSecondaryAudioDevice);
        verify(mHearingAidService, timeout(TIMEOUT_MS)).setActiveDevice(mSecondaryAudioDevice);

        Mockito.clearInvocations(mHearingAidService);
        hearingAidDisconnected(mSecondaryAudioDevice);
        verify(mHearingAidService, timeout(TIMEOUT_MS)).setActiveDevice(mHearingAidDevice);
    }

    /**
     * Hearing aid is connected, but active device is different BT.
     * When the active device is disconnected, the hearing aid should be the active one.
     */
    @Test
    public void activeDeviceDisconnected_fallbackToHearingAid() {
        when(mAudioManager.getMode()).thenReturn(AudioManager.MODE_NORMAL);
        when(mA2dpService.setActiveDevice(any())).thenReturn(true);
        when(mLeAudioService.setActiveDevice(any())).thenReturn(true);
        when(mHearingAidService.setActiveDevice(any())).thenReturn(true);
        when(mHearingAidService.removeActiveDevice(anyBoolean())).thenReturn(true);

        hearingAidConnected(mHearingAidDevice);
        verify(mHearingAidService, timeout(TIMEOUT_MS)).setActiveDevice(mHearingAidDevice);

        leAudioConnected(mLeAudioDevice);
        a2dpConnected(mA2dpDevice, false);

        a2dpActiveDeviceChanged(mA2dpDevice);
        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());

        verify(mHearingAidService).removeActiveDevice(false);
        verify(mLeAudioService, never()).setActiveDevice(mLeAudioDevice);
        verify(mA2dpService, never()).setActiveDevice(mA2dpDevice);

        a2dpDisconnected(mA2dpDevice);
        verify(mA2dpService, timeout(TIMEOUT_MS).atLeast(1)).removeActiveDevice(false);
        verify(mHearingAidService, timeout(TIMEOUT_MS).times(2))
                .setActiveDevice(mHearingAidDevice);
    }

    /**
     * One LE Hearing Aid is connected.
     */
    @Test
    public void onlyLeHearingAidConnected_setLeAudioActive() {
        leHearingAidConnected(mLeHearingAidDevice);
        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        verify(mLeAudioService, never()).setActiveDevice(mLeHearingAidDevice);

        leAudioConnected(mLeHearingAidDevice);
        verify(mLeAudioService, timeout(TIMEOUT_MS)).setActiveDevice(mLeHearingAidDevice);
    }

    /**
     * LE audio is connected after LE Hearing Aid device.
     * Keep LE hearing Aid active.
     */
    @Test
    public void leAudioConnectedAfterLeHearingAid_setLeAudioActiveShouldNotBeCalled() {
        leHearingAidConnected(mLeHearingAidDevice);
        leAudioConnected(mLeHearingAidDevice);
        verify(mLeAudioService, timeout(TIMEOUT_MS)).setActiveDevice(mLeHearingAidDevice);

        leAudioConnected(mLeAudioDevice);
        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        verify(mLeAudioService, never()).setActiveDevice(mLeAudioDevice);
    }

    /**
     * Test connect/disconnect of devices.
     * Hearing Aid, LE Hearing Aid, A2DP connected, then LE hearing Aid and hearing aid
     * disconnected.
     */
    @Test
    public void activeDeviceChange_withHearingAidLeHearingAidAndA2dpDevices() {
        when(mAudioManager.getMode()).thenReturn(AudioManager.MODE_NORMAL);
        when(mHearingAidService.removeActiveDevice(anyBoolean())).thenReturn(true);

        hearingAidConnected(mHearingAidDevice);
        verify(mHearingAidService, timeout(TIMEOUT_MS)).setActiveDevice(mHearingAidDevice);

        leHearingAidConnected(mLeHearingAidDevice);
        leAudioConnected(mLeHearingAidDevice);
        verify(mLeAudioService, timeout(TIMEOUT_MS)).setActiveDevice(mLeHearingAidDevice);

        a2dpConnected(mA2dpDevice, false);
        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        verify(mA2dpService, never()).setActiveDevice(mA2dpDevice);

        Mockito.clearInvocations(mHearingAidService, mA2dpService);
        leHearingAidDisconnected(mLeHearingAidDevice);
        leAudioDisconnected(mLeHearingAidDevice);
        verify(mHearingAidService, timeout(TIMEOUT_MS)).setActiveDevice(mHearingAidDevice);
        verify(mA2dpService, timeout(TIMEOUT_MS)).removeActiveDevice(false);

        hearingAidDisconnected(mHearingAidDevice);
        verify(mA2dpService, timeout(TIMEOUT_MS)).setActiveDevice(mA2dpDevice);
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
        verify(mA2dpService, timeout(TIMEOUT_MS).atLeastOnce())
                .setActiveDevice(mDualModeAudioDevice);
        verify(mHeadsetService, timeout(TIMEOUT_MS).atLeastOnce())
                .setActiveDevice(mDualModeAudioDevice);
        verify(mLeAudioService, timeout(TIMEOUT_MS).atLeastOnce()).removeActiveDevice(true);
        Assert.assertEquals(mDualModeAudioDevice, mActiveDeviceManager.getA2dpActiveDevice());
        Assert.assertEquals(mDualModeAudioDevice, mActiveDeviceManager.getHfpActiveDevice());

        // Ensure we make classic audio profiles inactive when LEA is made active
        leAudioConnected(mDualModeAudioDevice);
        verify(mA2dpService, timeout(TIMEOUT_MS)).removeActiveDevice(false);
        verify(mHeadsetService, timeout(TIMEOUT_MS)).setActiveDevice(isNull());
        verify(mLeAudioService, timeout(TIMEOUT_MS)).setActiveDevice(mDualModeAudioDevice);
        Assert.assertEquals(mDualModeAudioDevice, mActiveDeviceManager.getLeAudioActiveDevice());
    }

    /**
     * Verifies that we connect and make active both classic audio profiles (A2DP & HFP) and LE
     * Audio when the dual mode feature is enabled.
     */
    @Test
    public void dualModeAudioDeviceConnected_withDualModeFeatureEnabled() {
        // Turn on the dual mode audio flag
        Utils.setDualModeAudioStateForTesting(true);
        reset(mLeAudioService);
        when(mAdapterService.isAllSupportedClassicAudioProfilesActive(mDualModeAudioDevice))
                .thenReturn(false);

        leAudioConnected(mDualModeAudioDevice);
        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        // Verify setting LEA active fails when all supported classic audio profiles are not active
        verify(mLeAudioService, timeout(TIMEOUT_MS)).setActiveDevice(mDualModeAudioDevice);
        Assert.assertNull(mActiveDeviceManager.getLeAudioActiveDevice());
        Assert.assertNull(mActiveDeviceManager.getA2dpActiveDevice());
        Assert.assertNull(mActiveDeviceManager.getHfpActiveDevice());

        when(mLeAudioService.setActiveDevice(any())).thenReturn(true);
        when(mLeAudioService.removeActiveDevice(anyBoolean())).thenReturn(true);
        when(mLeAudioService.getLeadDevice(mDualModeAudioDevice)).thenReturn(mDualModeAudioDevice);

        Mockito.clearInvocations(mLeAudioService);

        // Ensure we make LEA active after all supported classic profiles are active
        a2dpActiveDeviceChanged(mDualModeAudioDevice);
        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());

        when(mAdapterService.isAllSupportedClassicAudioProfilesActive(mDualModeAudioDevice))
                .thenReturn(true);
        headsetActiveDeviceChanged(mDualModeAudioDevice);
        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());

        // When A2DP device is getting active, first LeAudio device is removed from active devices
        // and later added
        verify(mLeAudioService, times(1)).removeActiveDevice(anyBoolean());
        verify(mLeAudioService, times(1)).setActiveDevice(mDualModeAudioDevice);

        Assert.assertEquals(mDualModeAudioDevice, mActiveDeviceManager.getA2dpActiveDevice());
        Assert.assertEquals(mDualModeAudioDevice, mActiveDeviceManager.getHfpActiveDevice());
        Assert.assertEquals(mDualModeAudioDevice, mActiveDeviceManager.getLeAudioActiveDevice());

        // Verify LEA made inactive when a supported classic audio profile is made inactive
        a2dpActiveDeviceChanged(null);
        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        Assert.assertEquals(null, mActiveDeviceManager.getA2dpActiveDevice());
        Assert.assertEquals(null, mActiveDeviceManager.getLeAudioActiveDevice());
    }

    /**
     * Verifies that other profiles do not have their active device cleared when we fail to make
     * a newly connected device active.
     */
    @Test
    public void setActiveDeviceFailsUponConnection() {
        Utils.setDualModeAudioStateForTesting(false);
        when(mHeadsetService.setActiveDevice(any())).thenReturn(false);
        when(mA2dpService.setActiveDevice(any())).thenReturn(false);
        when(mHearingAidService.setActiveDevice(any())).thenReturn(false);
        when(mLeAudioService.setActiveDevice(any())).thenReturn(false);

        leAudioConnected(mDualModeAudioDevice);
        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        verify(mLeAudioService, timeout(TIMEOUT_MS)).setActiveDevice(mDualModeAudioDevice);

        leAudioActiveDeviceChanged(mDualModeAudioDevice);
        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        verify(mA2dpService, times(1)).removeActiveDevice(anyBoolean());
        verify(mHeadsetService, times(1)).setActiveDevice(null);
        verify(mHearingAidService, times(1)).removeActiveDevice(anyBoolean());

        a2dpConnected(mA2dpDevice, false);
        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        verify(mA2dpService, timeout(TIMEOUT_MS)).setActiveDevice(mA2dpDevice);
        verify(mLeAudioService, never()).removeActiveDevice(anyBoolean());

        a2dpConnected(mA2dpHeadsetDevice, true);
        headsetConnected(mA2dpHeadsetDevice, true);
        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        verify(mA2dpService, timeout(TIMEOUT_MS).atLeastOnce()).setActiveDevice(mA2dpHeadsetDevice);
        verify(mHeadsetService, timeout(TIMEOUT_MS).atLeastOnce())
                .setActiveDevice(mA2dpHeadsetDevice);
        verify(mLeAudioService, never()).removeActiveDevice(anyBoolean());

        headsetConnected(mHeadsetDevice, false);
        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        verify(mHeadsetService, timeout(TIMEOUT_MS)).setActiveDevice(mHeadsetDevice);
        verify(mLeAudioService, never()).removeActiveDevice(anyBoolean());

        hearingAidConnected(mHearingAidDevice);
        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        verify(mHearingAidService, timeout(TIMEOUT_MS)).setActiveDevice(mHearingAidDevice);
        verify(mLeAudioService, never()).removeActiveDevice(anyBoolean());
        verify(mA2dpService, times(1)).removeActiveDevice(anyBoolean());
        verify(mHeadsetService, times(1)).setActiveDevice(null);

        leAudioConnected(mLeHearingAidDevice);
        leHearingAidConnected(mLeHearingAidDevice);
        TestUtils.waitForLooperToFinishScheduledTask(mActiveDeviceManager.getHandlerLooper());
        verify(mLeAudioService, times(2)).setActiveDevice(mLeHearingAidDevice);
        verify(mA2dpService, times(1)).removeActiveDevice(anyBoolean());
        verify(mHeadsetService, times(1)).setActiveDevice(null);
        verify(mHearingAidService, times(1)).removeActiveDevice(anyBoolean());
    }

    /**
     * A wired audio device is connected. Then all active devices are set to null.
     */
    @Test
    public void wiredAudioDeviceConnected_setAllActiveDevicesNull() {
        a2dpConnected(mA2dpDevice, false);
        headsetConnected(mHeadsetDevice, false);
        verify(mA2dpService, timeout(TIMEOUT_MS)).setActiveDevice(mA2dpDevice);
        verify(mHeadsetService, timeout(TIMEOUT_MS)).setActiveDevice(mHeadsetDevice);

        mActiveDeviceManager.wiredAudioDeviceConnected();
        verify(mA2dpService, timeout(TIMEOUT_MS)).removeActiveDevice(false);
        verify(mHeadsetService, timeout(TIMEOUT_MS)).setActiveDevice(isNull());
        verify(mHearingAidService, timeout(TIMEOUT_MS)).removeActiveDevice(false);
    }

    /**
     * Verifies if Le Audio Broadcast is streaming, connected a2dp device should not be set as
     * active.
     */
    @Test
    public void a2dpConnectedWhenBroadcasting_notSetA2dpActive() {
        mSetFlagsRule.enableFlags(Flags.FLAG_LEAUDIO_BROADCAST_AUDIO_HANDOVER_POLICIES);
        final List<BluetoothLeBroadcastMetadata> metadataList = mock(List.class);
        when(mLeAudioService.getAllBroadcastMetadata()).thenReturn(metadataList);
        a2dpConnected(mA2dpDevice, false);
        verify(mA2dpService, never()).setActiveDevice(any());
        a2dpConnected(mA2dpDevice, true);
        verify(mA2dpService, never()).setActiveDevice(any());
    }

    /**
     * Verifies if Le Audio Broadcast is streaming, connected headset device should not be set as
     * active.
     */
    @Test
    public void headsetConnectedWhenBroadcasting_notSetHeadsetActive() {
        mSetFlagsRule.enableFlags(Flags.FLAG_LEAUDIO_BROADCAST_AUDIO_HANDOVER_POLICIES);
        final List<BluetoothLeBroadcastMetadata> metadataList = mock(List.class);
        when(mLeAudioService.getAllBroadcastMetadata()).thenReturn(metadataList);
        headsetConnected(mHeadsetDevice, false);
        verify(mHeadsetService, never()).setActiveDevice(any());
        headsetConnected(mHeadsetDevice, true);
        verify(mHeadsetService, never()).setActiveDevice(any());
    }

    /**
     * Verifies if Le Audio Broadcast is streaming, connected hearing aid device should not be set
     * as active.
     */
    @Test
    public void hearingAidConnectedWhenBroadcasting_notSetHearingAidActive() {
        mSetFlagsRule.enableFlags(Flags.FLAG_LEAUDIO_BROADCAST_AUDIO_HANDOVER_POLICIES);
        final List<BluetoothLeBroadcastMetadata> metadataList = mock(List.class);
        when(mLeAudioService.getAllBroadcastMetadata()).thenReturn(metadataList);
        hearingAidConnected(mHearingAidDevice);
        verify(mHearingAidService, never()).setActiveDevice(any());
    }

    /**
     * Verifies if Le Audio Broadcast is streaming, connected LE hearing aid device should not be
     * set as active.
     */
    @Test
    public void leHearingAidConnectedWhenBroadcasting_notSetLeHearingAidActive() {
        mSetFlagsRule.enableFlags(Flags.FLAG_LEAUDIO_BROADCAST_AUDIO_HANDOVER_POLICIES);
        final List<BluetoothLeBroadcastMetadata> metadataList = mock(List.class);
        when(mLeAudioService.getAllBroadcastMetadata()).thenReturn(metadataList);
        leHearingAidConnected(mLeHearingAidDevice);
        verify(mLeAudioService, never()).setActiveDevice(any());
    }

    /**
     * Helper to indicate A2dp connected for a device.
     */
    private void a2dpConnected(BluetoothDevice device, boolean supportHfp) {
        mDatabaseManager.setProfileConnectionPolicy(
                device,
                BluetoothProfile.HEADSET,
                supportHfp
                        ? BluetoothProfile.CONNECTION_POLICY_ALLOWED
                        : BluetoothProfile.CONNECTION_POLICY_UNKNOWN);

        mDeviceConnectionStack.add(device);
        mMostRecentDevice = device;

        mActiveDeviceManager.profileConnectionStateChanged(
                BluetoothProfile.A2DP,
                device,
                BluetoothProfile.STATE_DISCONNECTED,
                BluetoothProfile.STATE_CONNECTED);
    }

    /**
     * Helper to indicate A2dp disconnected for a device.
     */
    private void a2dpDisconnected(BluetoothDevice device) {
        mDeviceConnectionStack.remove(device);
        mMostRecentDevice = (mDeviceConnectionStack.size() > 0)
                ? mDeviceConnectionStack.get(mDeviceConnectionStack.size() - 1) : null;

        mActiveDeviceManager.profileConnectionStateChanged(
                BluetoothProfile.A2DP,
                device,
                BluetoothProfile.STATE_CONNECTED,
                BluetoothProfile.STATE_DISCONNECTED);
    }

    /** Helper to indicate A2dp active device changed for a device. */
    private void a2dpActiveDeviceChanged(BluetoothDevice device) {
        mDeviceConnectionStack.remove(device);
        mDeviceConnectionStack.add(device);
        mMostRecentDevice = device;

        mActiveDeviceManager.profileActiveDeviceChanged(BluetoothProfile.A2DP, device);
    }

    /** Helper to indicate Headset connected for a device. */
    private void headsetConnected(BluetoothDevice device, boolean supportA2dp) {
        mDatabaseManager.setProfileConnectionPolicy(
                device,
                BluetoothProfile.A2DP,
                supportA2dp
                        ? BluetoothProfile.CONNECTION_POLICY_ALLOWED
                        : BluetoothProfile.CONNECTION_POLICY_UNKNOWN);

        mDeviceConnectionStack.add(device);
        mMostRecentDevice = device;

        mActiveDeviceManager.profileConnectionStateChanged(
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

        mActiveDeviceManager.profileConnectionStateChanged(
                BluetoothProfile.HEADSET,
                device,
                BluetoothProfile.STATE_CONNECTED,
                BluetoothProfile.STATE_DISCONNECTED);
    }

    /** Helper to indicate Headset active device changed for a device. */
    private void headsetActiveDeviceChanged(BluetoothDevice device) {
        mDeviceConnectionStack.remove(device);
        mDeviceConnectionStack.add(device);
        mMostRecentDevice = device;

        mActiveDeviceManager.profileActiveDeviceChanged(BluetoothProfile.HEADSET, device);
    }

    /**
     * Helper to indicate Hearing Aid connected for a device.
     */
    private void hearingAidConnected(BluetoothDevice device) {
        mDeviceConnectionStack.add(device);
        mMostRecentDevice = device;

        mActiveDeviceManager.profileConnectionStateChanged(
                BluetoothProfile.HEARING_AID,
                device,
                BluetoothProfile.STATE_DISCONNECTED,
                BluetoothProfile.STATE_CONNECTED);
    }

    /**
     * Helper to indicate Hearing Aid disconnected for a device.
     */
    private void hearingAidDisconnected(BluetoothDevice device) {
        mDeviceConnectionStack.remove(device);
        mMostRecentDevice = (mDeviceConnectionStack.size() > 0)
                ? mDeviceConnectionStack.get(mDeviceConnectionStack.size() - 1) : null;

        mActiveDeviceManager.profileConnectionStateChanged(
                BluetoothProfile.HEARING_AID,
                device,
                BluetoothProfile.STATE_CONNECTED,
                BluetoothProfile.STATE_DISCONNECTED);
    }

    /**
     * Helper to indicate Hearing Aid active device changed for a device.
     */
    private void hearingAidActiveDeviceChanged(BluetoothDevice device) {
        mDeviceConnectionStack.remove(device);
        mDeviceConnectionStack.add(device);
        mMostRecentDevice = device;

        mActiveDeviceManager.profileActiveDeviceChanged(BluetoothProfile.HEARING_AID, device);
    }

    /**
     * Helper to indicate LE Audio connected for a device.
     */
    private void leAudioConnected(BluetoothDevice device) {
        mMostRecentDevice = device;

        mActiveDeviceManager.profileConnectionStateChanged(
                BluetoothProfile.LE_AUDIO,
                device,
                BluetoothProfile.STATE_DISCONNECTED,
                BluetoothProfile.STATE_CONNECTED);
    }

    /**
     * Helper to indicate LE Audio disconnected for a device.
     */
    private void leAudioDisconnected(BluetoothDevice device) {
        mDeviceConnectionStack.remove(device);
        mMostRecentDevice = (mDeviceConnectionStack.size() > 0)
                ? mDeviceConnectionStack.get(mDeviceConnectionStack.size() - 1) : null;

        mActiveDeviceManager.profileConnectionStateChanged(
                BluetoothProfile.LE_AUDIO,
                device,
                BluetoothProfile.STATE_CONNECTED,
                BluetoothProfile.STATE_DISCONNECTED);
    }

    /**
     * Helper to indicate LE Audio active device changed for a device.
     */
    private void leAudioActiveDeviceChanged(BluetoothDevice device) {
        mDeviceConnectionStack.remove(device);
        mDeviceConnectionStack.add(device);
        mMostRecentDevice = device;

        mActiveDeviceManager.profileActiveDeviceChanged(BluetoothProfile.LE_AUDIO, device);
    }

    /**
     * Helper to indicate LE Hearing Aid connected for a device.
     */
    private void leHearingAidConnected(BluetoothDevice device) {
        mDeviceConnectionStack.add(device);
        mMostRecentDevice = device;

        mActiveDeviceManager.profileConnectionStateChanged(
                BluetoothProfile.HAP_CLIENT,
                device,
                BluetoothProfile.STATE_DISCONNECTED,
                BluetoothProfile.STATE_CONNECTED);
    }

    /** Helper to indicate LE Hearing Aid disconnected for a device. */
    private void leHearingAidDisconnected(BluetoothDevice device) {
        mDeviceConnectionStack.remove(device);
        mMostRecentDevice = (mDeviceConnectionStack.size() > 0)
                ? mDeviceConnectionStack.get(mDeviceConnectionStack.size() - 1) : null;

        mActiveDeviceManager.profileConnectionStateChanged(
                BluetoothProfile.HAP_CLIENT,
                device,
                BluetoothProfile.STATE_CONNECTED,
                BluetoothProfile.STATE_DISCONNECTED);
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
            } else if (devices.contains(mLeHearingAidDevice)) {
                return mLeHearingAidDevice;
            } else if (devices.contains(mHearingAidDevice)) {
                return mHearingAidDevice;
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
