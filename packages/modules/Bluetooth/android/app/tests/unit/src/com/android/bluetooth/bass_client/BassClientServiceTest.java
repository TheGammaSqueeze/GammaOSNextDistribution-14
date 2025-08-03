/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.bluetooth.bass_client;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.notNull;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothLeAudioCodecConfigMetadata;
import android.bluetooth.BluetoothLeAudioContentMetadata;
import android.bluetooth.BluetoothLeBroadcastAssistant;
import android.bluetooth.BluetoothLeBroadcastChannel;
import android.bluetooth.BluetoothLeBroadcastMetadata;
import android.bluetooth.BluetoothLeBroadcastReceiveState;
import android.bluetooth.BluetoothLeBroadcastSubgroup;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.BluetoothUuid;
import android.bluetooth.IBluetoothLeBroadcastAssistantCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Binder;
import android.os.Message;
import android.os.ParcelUuid;
import android.os.RemoteException;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.TestUtils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.ServiceFactory;
import com.android.bluetooth.btservice.storage.DatabaseManager;
import com.android.bluetooth.csip.CsipSetCoordinatorService;
import com.android.bluetooth.flags.FakeFeatureFlagsImpl;
import com.android.bluetooth.flags.Flags;
import com.android.bluetooth.le_audio.LeAudioService;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;

/**
 * Tests for {@link BassClientService}
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class BassClientServiceTest {
    private final String mFlagDexmarker = System.getProperty("dexmaker.share_classloader", "false");

    private static final int TIMEOUT_MS = 1000;

    private static final int MAX_HEADSET_CONNECTIONS = 5;
    private static final ParcelUuid[] FAKE_SERVICE_UUIDS = {BluetoothUuid.BASS};
    private static final int ASYNC_CALL_TIMEOUT_MILLIS = 250;

    private static final String TEST_MAC_ADDRESS = "00:11:22:33:44:55";
    private static final int TEST_BROADCAST_ID = 42;
    private static final int TEST_ADVERTISER_SID = 1234;
    private static final int TEST_PA_SYNC_INTERVAL = 100;
    private static final int TEST_PRESENTATION_DELAY_MS = 345;

    private static final int TEST_CODEC_ID = 42;
    private static final int TEST_CHANNEL_INDEX = 56;

    // For BluetoothLeAudioCodecConfigMetadata
    private static final long TEST_AUDIO_LOCATION_FRONT_LEFT = 0x01;
    private static final long TEST_AUDIO_LOCATION_FRONT_RIGHT = 0x02;

    // For BluetoothLeAudioContentMetadata
    private static final String TEST_PROGRAM_INFO = "Test";
    // German language code in ISO 639-3
    private static final String TEST_LANGUAGE = "deu";
    private static final int TEST_SOURCE_ID = 10;
    private static final int TEST_NUM_SOURCES = 2;


    private static final int TEST_MAX_NUM_DEVICES = 3;

    private final HashMap<BluetoothDevice, BassClientStateMachine> mStateMachines = new HashMap<>();
    private final List<BassClientStateMachine> mStateMachinePool = new ArrayList<>();
    private HashMap<BluetoothDevice, LinkedBlockingQueue<Intent>> mIntentQueue;

    private Context mTargetContext;
    private BassClientService mBassClientService;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothDevice mCurrentDevice;
    private BluetoothDevice mCurrentDevice1;
    private BassIntentReceiver mBassIntentReceiver;
    private FakeFeatureFlagsImpl mFakeFlagsImpl;

    @Spy private BassObjectsFactory mObjectsFactory = BassObjectsFactory.getInstance();
    @Mock private AdapterService mAdapterService;
    @Mock private DatabaseManager mDatabaseManager;
    @Mock private BluetoothLeScannerWrapper mBluetoothLeScannerWrapper;
    @Mock private ServiceFactory mServiceFactory;
    @Mock private CsipSetCoordinatorService mCsipService;
    @Mock private LeAudioService mLeAudioService;
    @Mock private IBluetoothLeBroadcastAssistantCallback mCallback;
    @Mock private Binder mBinder;

    BluetoothLeBroadcastSubgroup createBroadcastSubgroup() {
        BluetoothLeAudioCodecConfigMetadata codecMetadata =
                new BluetoothLeAudioCodecConfigMetadata.Builder()
                        .setAudioLocation(TEST_AUDIO_LOCATION_FRONT_LEFT).build();
        BluetoothLeAudioContentMetadata contentMetadata =
                new BluetoothLeAudioContentMetadata.Builder()
                        .setProgramInfo(TEST_PROGRAM_INFO).setLanguage(TEST_LANGUAGE).build();
        BluetoothLeBroadcastSubgroup.Builder builder = new BluetoothLeBroadcastSubgroup.Builder()
                .setCodecId(TEST_CODEC_ID)
                .setCodecSpecificConfig(codecMetadata)
                .setContentMetadata(contentMetadata);

        BluetoothLeAudioCodecConfigMetadata channelCodecMetadata =
                new BluetoothLeAudioCodecConfigMetadata.Builder()
                        .setAudioLocation(TEST_AUDIO_LOCATION_FRONT_RIGHT).build();

        // builder expect at least one channel
        BluetoothLeBroadcastChannel channel =
                new BluetoothLeBroadcastChannel.Builder()
                        .setSelected(true)
                        .setChannelIndex(TEST_CHANNEL_INDEX)
                        .setCodecMetadata(channelCodecMetadata)
                        .build();
        builder.addChannel(channel);
        return builder.build();
    }

    BluetoothLeBroadcastMetadata createBroadcastMetadata(int broadcastId) {
        BluetoothDevice testDevice = mBluetoothAdapter.getRemoteLeDevice(TEST_MAC_ADDRESS,
                        BluetoothDevice.ADDRESS_TYPE_RANDOM);

        BluetoothLeBroadcastMetadata.Builder builder = new BluetoothLeBroadcastMetadata.Builder()
                        .setEncrypted(false)
                        .setSourceDevice(testDevice, BluetoothDevice.ADDRESS_TYPE_RANDOM)
                        .setSourceAdvertisingSid(TEST_ADVERTISER_SID)
                        .setBroadcastId(broadcastId)
                        .setBroadcastCode(null)
                        .setPaSyncInterval(TEST_PA_SYNC_INTERVAL)
                        .setPresentationDelayMicros(TEST_PRESENTATION_DELAY_MS);
        // builder expect at least one subgroup
        builder.addSubgroup(createBroadcastSubgroup());
        return builder.build();
    }

    @Before
    public void setUp() throws Exception {
        if (!mFlagDexmarker.equals("true")) {
            System.setProperty("dexmaker.share_classloader", "true");
        }

        mTargetContext = InstrumentationRegistry.getTargetContext();
        MockitoAnnotations.initMocks(this);
        TestUtils.setAdapterService(mAdapterService);
        BassObjectsFactory.setInstanceForTesting(mObjectsFactory);

        doReturn(new ParcelUuid[]{BluetoothUuid.BASS}).when(mAdapterService)
                .getRemoteUuids(any(BluetoothDevice.class));
        // This line must be called to make sure relevant objects are initialized properly
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Mock methods in AdapterService
        doReturn(FAKE_SERVICE_UUIDS).when(mAdapterService)
                .getRemoteUuids(any(BluetoothDevice.class));
        doReturn(BluetoothDevice.BOND_BONDED).when(mAdapterService)
                .getBondState(any(BluetoothDevice.class));
        doReturn(mDatabaseManager).when(mAdapterService).getDatabase();
        doAnswer(invocation -> {
            Set<BluetoothDevice> keys = mStateMachines.keySet();
            return keys.toArray(new BluetoothDevice[keys.size()]);
        }).when(mAdapterService).getBondedDevices();

        // Mock methods in BassObjectsFactory
        doAnswer(
                        invocation -> {
                            assertThat(mCurrentDevice).isNotNull();
                            final BassClientStateMachine stateMachine =
                                    mock(BassClientStateMachine.class);
                            doReturn(new ArrayList<>()).when(stateMachine).getAllSources();
                            doReturn(TEST_NUM_SOURCES)
                                    .when(stateMachine)
                                    .getMaximumSourceCapacity();
                            doReturn((BluetoothDevice) invocation.getArgument(0))
                                    .when(stateMachine)
                                    .getDevice();
                            mStateMachines.put(
                                    (BluetoothDevice) invocation.getArgument(0), stateMachine);
                            return stateMachine;
                        })
                .when(mObjectsFactory)
                .makeStateMachine(any(), any(), any(), any(), any());
        doReturn(mBluetoothLeScannerWrapper).when(mObjectsFactory)
                .getBluetoothLeScannerWrapper(any());

        mFakeFlagsImpl = new FakeFeatureFlagsImpl();
        mFakeFlagsImpl.setFlag(Flags.FLAG_LEAUDIO_BROADCAST_AUDIO_HANDOVER_POLICIES, false);

        mBassClientService = new BassClientService(mTargetContext, mFakeFlagsImpl);
        mBassClientService.start();
        mBassClientService.setAvailable(true);

        mBassClientService.mServiceFactory = mServiceFactory;
        doReturn(mCsipService).when(mServiceFactory).getCsipSetCoordinatorService();
        doReturn(mLeAudioService).when(mServiceFactory).getLeAudioService();

        when(mCallback.asBinder()).thenReturn(mBinder);
        mBassClientService.registerCallback(mCallback);

        mIntentQueue = new HashMap<>();
        mIntentQueue.put(mCurrentDevice, new LinkedBlockingQueue<>());
        mIntentQueue.put(mCurrentDevice1, new LinkedBlockingQueue<>());

        // Set up the Connection State Changed receiver
        IntentFilter filter = new IntentFilter();
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        filter.addAction(BluetoothLeBroadcastAssistant.ACTION_CONNECTION_STATE_CHANGED);

        mBassIntentReceiver = new BassIntentReceiver();
        mTargetContext.registerReceiver(mBassIntentReceiver, filter, Context.RECEIVER_EXPORTED);
    }

    @After
    public void tearDown() throws Exception {
        if (mBassClientService == null) {
            return;
        }
        mBassClientService.unregisterCallback(mCallback);

        mBassClientService.stop();
        mBassClientService = BassClientService.getBassClientService();
        assertThat(mBassClientService).isNull();
        mStateMachines.clear();
        mCurrentDevice = null;
        mCurrentDevice1 = null;
        mTargetContext.unregisterReceiver(mBassIntentReceiver);
        mIntentQueue.clear();
        BassObjectsFactory.setInstanceForTesting(null);
        TestUtils.clearAdapterService(mAdapterService);

        if (!mFlagDexmarker.equals("true")) {
            System.setProperty("dexmaker.share_classloader", mFlagDexmarker);
        }
    }

    private class BassIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                BluetoothDevice device = intent.getParcelableExtra(
                        BluetoothDevice.EXTRA_DEVICE);
                assertThat(device).isNotNull();
                LinkedBlockingQueue<Intent> queue = mIntentQueue.get(device);
                assertThat(queue).isNotNull();
                queue.put(intent);
            } catch (InterruptedException e) {
                throw new AssertionError("Cannot add Intent to the queue: " + e.getMessage());
            }
        }
    }

    /**
     * Test to verify that BassClientService can be successfully started
     */
    @Test
    public void testGetBassClientService() {
        assertThat(mBassClientService).isEqualTo(BassClientService.getBassClientService());
        // Verify default connection and audio states
        mCurrentDevice = TestUtils.getTestDevice(mBluetoothAdapter, 0);
        assertThat(mBassClientService.getConnectionState(mCurrentDevice))
                .isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
    }

    /**
     * Test if getProfileConnectionPolicy works after the service is stopped.
     */
    @Test
    public void testGetPolicyAfterStopped() {
        mBassClientService.stop();
        when(mDatabaseManager
                .getProfileConnectionPolicy(mCurrentDevice,
                        BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
        Assert.assertEquals("Initial device policy",
                BluetoothProfile.CONNECTION_POLICY_UNKNOWN,
                mBassClientService.getConnectionPolicy(mCurrentDevice));
    }

    /**
     * Test connecting to a test device.
     *  - service.connect() should return false
     *  - bassClientStateMachine.sendMessage(CONNECT) should be called.
     */
    @Test
    public void testConnect() {
        when(mDatabaseManager.getProfileConnectionPolicy(any(BluetoothDevice.class),
                eq(BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT)))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        mCurrentDevice = TestUtils.getTestDevice(mBluetoothAdapter, 0);

        assertThat(mBassClientService.connect(mCurrentDevice)).isTrue();
        verify(mObjectsFactory)
                .makeStateMachine(
                        eq(mCurrentDevice),
                        eq(mBassClientService),
                        eq(mAdapterService),
                        any(),
                        any());
        BassClientStateMachine stateMachine = mStateMachines.get(mCurrentDevice);
        assertThat(stateMachine).isNotNull();
        verify(stateMachine).sendMessage(BassClientStateMachine.CONNECT);
    }

    /**
     * Test connecting to a null device.
     *  - service.connect() should return false.
     */
    @Test
    public void testConnect_nullDevice() {
        when(mDatabaseManager.getProfileConnectionPolicy(any(BluetoothDevice.class),
                eq(BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT)))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        BluetoothDevice nullDevice = null;

        assertThat(mBassClientService.connect(nullDevice)).isFalse();
    }

    /**
     * Test connecting to a device when the connection policy is forbidden.
     *  - service.connect() should return false.
     */
    @Test
    public void testConnect_whenConnectionPolicyIsForbidden() {
        when(mDatabaseManager.getProfileConnectionPolicy(any(BluetoothDevice.class),
                eq(BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT)))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);
        mCurrentDevice = TestUtils.getTestDevice(mBluetoothAdapter, 0);
        assertThat(mCurrentDevice).isNotNull();

        assertThat(mBassClientService.connect(mCurrentDevice)).isFalse();
    }

    /**
     * Test whether service.startSearchingForSources() calls BluetoothLeScannerWrapper.startScan().
     */
    @Test
    public void testStartSearchingForSources() {
        prepareConnectedDeviceGroup();
        List<ScanFilter> scanFilters = new ArrayList<>();

        assertThat(mStateMachines.size()).isEqualTo(2);
        for (BassClientStateMachine sm : mStateMachines.values()) {
            Mockito.clearInvocations(sm);
        }

        mBassClientService.startSearchingForSources(scanFilters);

        verify(mBluetoothLeScannerWrapper).startScan(notNull(), notNull(), notNull());
        for (BassClientStateMachine sm : mStateMachines.values()) {
            verify(sm).sendMessage(BassClientStateMachine.START_SCAN_OFFLOAD);
        }
    }

    /**
     * Test whether service.startSearchingForSources() does not call
     * BluetoothLeScannerWrapper.startScan() when the scanner instance cannot be achieved.
     */
    @Test
    public void testStartSearchingForSources_whenScannerIsNull() {
        doReturn(null).when(mObjectsFactory).getBluetoothLeScannerWrapper(any());
        List<ScanFilter> scanFilters = new ArrayList<>();

        mBassClientService.startSearchingForSources(scanFilters);

        verify(mBluetoothLeScannerWrapper, never()).startScan(any(), any(), any());
    }

    private void prepareConnectedDeviceGroup() {
        when(mDatabaseManager.getProfileConnectionPolicy(any(BluetoothDevice.class),
                        eq(BluetoothProfile.LE_AUDIO_BROADCAST_ASSISTANT)))
                        .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        mCurrentDevice = TestUtils.getTestDevice(mBluetoothAdapter, 0);
        mCurrentDevice1 = TestUtils.getTestDevice(mBluetoothAdapter, 1);

        // Prepare intent queues
        mIntentQueue.put(mCurrentDevice, new LinkedBlockingQueue<>());
        mIntentQueue.put(mCurrentDevice1, new LinkedBlockingQueue<>());

        // Mock the CSIP group
        List<BluetoothDevice> groupDevices = new ArrayList<>();
        groupDevices.add(mCurrentDevice);
        groupDevices.add(mCurrentDevice1);
        doReturn(groupDevices).when(mCsipService)
                .getGroupDevicesOrdered(mCurrentDevice, BluetoothUuid.CAP);
        doReturn(groupDevices).when(mCsipService)
                .getGroupDevicesOrdered(mCurrentDevice1, BluetoothUuid.CAP);

        // Prepare connected devices
        assertThat(mBassClientService.connect(mCurrentDevice)).isTrue();
        assertThat(mBassClientService.connect(mCurrentDevice1)).isTrue();

        assertThat(mStateMachines.size()).isEqualTo(2);
        for (BassClientStateMachine sm: mStateMachines.values()) {
            // Verify the call
            verify(sm).sendMessage(eq(BassClientStateMachine.CONNECT));

            // Notify the service about the connection event
            BluetoothDevice dev = sm.getDevice();
            doCallRealMethod().when(sm)
                .broadcastConnectionState(eq(dev), any(Integer.class), any(Integer.class));
            sm.mService = mBassClientService;
            sm.mDevice = dev;
            sm.broadcastConnectionState(dev, BluetoothProfile.STATE_CONNECTING,
                    BluetoothProfile.STATE_CONNECTED);

            doReturn(BluetoothProfile.STATE_CONNECTED).when(sm).getConnectionState();
            doReturn(true).when(sm).isConnected();

            // Inject initial broadcast source state
            BluetoothLeBroadcastMetadata meta = createBroadcastMetadata(TEST_BROADCAST_ID);
            injectRemoteSourceStateSourceAdded(sm, meta, TEST_SOURCE_ID,
                BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE,
                meta.isEncrypted() ?
                        BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_DECRYPTING :
                        BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_NOT_ENCRYPTED,
                null);
            injectRemoteSourceStateRemoval(sm, TEST_SOURCE_ID);

            injectRemoteSourceStateSourceAdded(sm, meta, TEST_SOURCE_ID + 1,
                BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE,
                meta.isEncrypted() ?
                        BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_DECRYPTING :
                        BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_NOT_ENCRYPTED,
                null);
            injectRemoteSourceStateRemoval(sm, TEST_SOURCE_ID + 1);
        }
    }

    private void verifyConnectionStateIntent(int timeoutMs, BluetoothDevice device, int newState,
            int prevState) {
        Intent intent = TestUtils.waitForIntent(timeoutMs, mIntentQueue.get(device));
        assertThat(intent).isNotNull();
        assertThat(BluetoothLeBroadcastAssistant.ACTION_CONNECTION_STATE_CHANGED)
                .isEqualTo(intent.getAction());
        assertThat(device).isEqualTo(intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE));
        assertThat(newState).isEqualTo(intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1));
        assertThat(prevState).isEqualTo(intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE,
                -1));
    }

    private void verifyAddSourceForGroup(BluetoothLeBroadcastMetadata meta) {
        // Add broadcast source
        mBassClientService.addSource(mCurrentDevice, meta, true);

        // Verify all group members getting ADD_BCAST_SOURCE message
        assertThat(mStateMachines.size()).isEqualTo(2);
        for (BassClientStateMachine sm: mStateMachines.values()) {
            ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
            verify(sm, atLeast(1)).sendMessage(messageCaptor.capture());

            Message msg = messageCaptor.getAllValues().stream()
                    .filter(m -> (m.what == BassClientStateMachine.ADD_BCAST_SOURCE)
                                        && (m.obj == meta))
                    .findFirst()
                    .orElse(null);
            assertThat(msg).isNotNull();
        }
    }

    private BluetoothLeBroadcastReceiveState injectRemoteSourceState(BassClientStateMachine sm,
            BluetoothLeBroadcastMetadata meta, int sourceId, int paSynState, int encryptionState,
            byte[] badCode, long bisSyncState) {
        BluetoothLeBroadcastReceiveState recvState = new BluetoothLeBroadcastReceiveState(
                sourceId,
                meta.getSourceAddressType(),
                meta.getSourceDevice(),
                meta.getSourceAdvertisingSid(),
                meta.getBroadcastId(),
                paSynState,
                encryptionState,
                badCode,
                meta.getSubgroups().size(),
                // Bis sync states
                meta.getSubgroups().stream()
                        .map(e -> bisSyncState)
                        .collect(Collectors.toList()),
                meta.getSubgroups().stream()
                                .map(e -> e.getContentMetadata())
                                .collect(Collectors.toList())
                );
        doReturn(meta).when(sm).getCurrentBroadcastMetadata(eq(sourceId));

        List<BluetoothLeBroadcastReceiveState> stateList = sm.getAllSources();
        if (stateList == null) {
            stateList = new ArrayList<BluetoothLeBroadcastReceiveState>();
        } else {
            stateList.removeIf(e -> e.getSourceId() == sourceId);
        }
        stateList.add(recvState);
        doReturn(stateList).when(sm).getAllSources();

        return recvState;
    }

    private BluetoothLeBroadcastReceiveState injectRemoteSourceStateSourceAdded(
            BassClientStateMachine sm, BluetoothLeBroadcastMetadata meta, int sourceId,
            int paSynState, int encryptionState, byte[] badCode) {
        BluetoothLeBroadcastReceiveState recvState =
                injectRemoteSourceState(sm, meta, sourceId, paSynState, encryptionState, badCode,
                        (long) 0x00000002);

        mBassClientService.getCallbacks().notifySourceAdded(sm.getDevice(), recvState,
                        BluetoothStatusCodes.REASON_LOCAL_APP_REQUEST);
        TestUtils.waitForLooperToFinishScheduledTask(mBassClientService.getCallbacks().getLooper());

        return recvState;
    }

    private BluetoothLeBroadcastReceiveState injectRemoteSourceStateChanged(
            BassClientStateMachine sm, BluetoothLeBroadcastMetadata meta, int sourceId,
            int paSynState, int encryptionState, byte[] badCode, long bisSyncState) {
        BluetoothLeBroadcastReceiveState recvState =
                injectRemoteSourceState(sm, meta, sourceId, paSynState, encryptionState, badCode,
                        bisSyncState);

        mBassClientService.getCallbacks().notifyReceiveStateChanged(sm.getDevice(),
                        recvState.getSourceId(), recvState);
        TestUtils.waitForLooperToFinishScheduledTask(mBassClientService.getCallbacks().getLooper());

        return recvState;
    }

    private void injectRemoteSourceStateRemoval(BassClientStateMachine sm, int sourceId) {
        List<BluetoothLeBroadcastReceiveState> stateList = sm.getAllSources();
        if (stateList == null) {
                stateList = new ArrayList<BluetoothLeBroadcastReceiveState>();
        }
        stateList.replaceAll(e -> {
            if (e.getSourceId() != sourceId) return e;
            return new BluetoothLeBroadcastReceiveState(
                sourceId,
                BluetoothDevice.ADDRESS_TYPE_PUBLIC,
                mBluetoothAdapter.getRemoteLeDevice("00:00:00:00:00:00",
                        BluetoothDevice.ADDRESS_TYPE_PUBLIC),
                0,
                0,
                BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE,
                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_NOT_ENCRYPTED,
                null,
                0,
                Arrays.asList(new Long[0]),
                Arrays.asList(new BluetoothLeAudioContentMetadata[0])
            );
        });
        doReturn(stateList).when(sm).getAllSources();

        mBassClientService.getCallbacks().notifySourceRemoved(sm.getDevice(), sourceId,
                        BluetoothStatusCodes.REASON_LOCAL_APP_REQUEST);
        TestUtils.waitForLooperToFinishScheduledTask(mBassClientService.getCallbacks().getLooper());
    }

    /**
     * Test whether service.addSource() does send proper messages to all the
     * state machines within the Csip coordinated group
     */
    @Test
    public void testAddSourceForGroup() {
        prepareConnectedDeviceGroup();
        BluetoothLeBroadcastMetadata meta = createBroadcastMetadata(TEST_BROADCAST_ID);
        verifyAddSourceForGroup(meta);
    }


    /**
     * Test whether service.addSource() source id can be propagated through callback correctly
     */
    @Test
    public void testAddSourceCallbackForGroup() {
        prepareConnectedDeviceGroup();
        BluetoothLeBroadcastMetadata meta = createBroadcastMetadata(TEST_BROADCAST_ID);
        verifyAddSourceForGroup(meta);
        for (BassClientStateMachine sm: mStateMachines.values()) {
            if (sm.getDevice().equals(mCurrentDevice)) {
                injectRemoteSourceStateSourceAdded(sm, meta, TEST_SOURCE_ID,
                        BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE,
                        meta.isEncrypted() ?
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_DECRYPTING :
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_NOT_ENCRYPTED,
                        null);
                // verify source id
                try {
                    verify(mCallback, timeout(TIMEOUT_MS).atLeastOnce()).
                            onSourceAdded(eq(mCurrentDevice), eq(TEST_SOURCE_ID),
                            eq(BluetoothStatusCodes.REASON_LOCAL_APP_REQUEST));
                } catch (RemoteException e) {
                    throw e.rethrowFromSystemServer();
                }
            } else if (sm.getDevice().equals(mCurrentDevice1)) {
                injectRemoteSourceStateSourceAdded(sm, meta, TEST_SOURCE_ID + 1,
                        BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE,
                        meta.isEncrypted() ?
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_DECRYPTING :
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_NOT_ENCRYPTED,
                        null);
                // verify source id
                try {
                    verify(mCallback, timeout(TIMEOUT_MS).atLeastOnce()).
                            onSourceAdded(eq(mCurrentDevice1), eq(TEST_SOURCE_ID + 1),
                            eq(BluetoothStatusCodes.REASON_LOCAL_APP_REQUEST));
                } catch (RemoteException e) {
                    throw e.rethrowFromSystemServer();
                }
            }
        }
    }
   /**
     * Test whether service.modifySource() does send proper messages to all the
     * state machines within the Csip coordinated group
     */
    @Test
    public void testModifySourceForGroup() {
        prepareConnectedDeviceGroup();
        BluetoothLeBroadcastMetadata meta = createBroadcastMetadata(TEST_BROADCAST_ID);
        verifyAddSourceForGroup(meta);
        for (BassClientStateMachine sm: mStateMachines.values()) {
            if (sm.getDevice().equals(mCurrentDevice)) {
                injectRemoteSourceStateSourceAdded(sm, meta, TEST_SOURCE_ID,
                        BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE,
                        meta.isEncrypted() ?
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_DECRYPTING :
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_NOT_ENCRYPTED,
                        null);
            } else if (sm.getDevice().equals(mCurrentDevice1)) {
                injectRemoteSourceStateSourceAdded(sm, meta, TEST_SOURCE_ID + 1,
                        BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE,
                        meta.isEncrypted() ?
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_DECRYPTING :
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_NOT_ENCRYPTED,
                        null);
            }
        }

        // Update broadcast source using other member of the same group
        BluetoothLeBroadcastMetadata metaUpdate =
                new BluetoothLeBroadcastMetadata.Builder(meta)
                        .setBroadcastId(TEST_BROADCAST_ID + 1).build();
        mBassClientService.modifySource(mCurrentDevice1, TEST_SOURCE_ID + 1, metaUpdate);

        // Verify all group members getting UPDATE_BCAST_SOURCE message on proper sources
        assertThat(mStateMachines.size()).isEqualTo(2);
        for (BassClientStateMachine sm: mStateMachines.values()) {
            ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
            verify(sm, atLeast(1)).sendMessage(messageCaptor.capture());

            Optional<Message> msg = messageCaptor.getAllValues().stream()
                    .filter(m -> m.what == BassClientStateMachine.UPDATE_BCAST_SOURCE)
                    .findFirst();
            assertThat(msg.isPresent()).isEqualTo(true);
            assertThat(msg.get().obj).isEqualTo(metaUpdate);

            // Verify using the right sourceId on each device
            if (sm.getDevice().equals(mCurrentDevice)) {
                assertThat(msg.get().arg1).isEqualTo(TEST_SOURCE_ID);
            } else if (sm.getDevice().equals(mCurrentDevice1)) {
                assertThat(msg.get().arg1).isEqualTo(TEST_SOURCE_ID + 1);
            }
        }
    }

    /**
     * Test whether service.removeSource() does send proper messages to all the
     * state machines within the Csip coordinated group
     */
    @Test
    public void testRemoveSourceForGroup() {
        prepareConnectedDeviceGroup();
        BluetoothLeBroadcastMetadata meta = createBroadcastMetadata(TEST_BROADCAST_ID);
        verifyAddSourceForGroup(meta);
        for (BassClientStateMachine sm: mStateMachines.values()) {
            if (sm.getDevice().equals(mCurrentDevice)) {
                injectRemoteSourceStateSourceAdded(sm, meta, TEST_SOURCE_ID,
                        BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE,
                        meta.isEncrypted() ?
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_DECRYPTING :
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_NOT_ENCRYPTED,
                        null);
            } else if (sm.getDevice().equals(mCurrentDevice1)) {
                injectRemoteSourceStateSourceAdded(sm, meta, TEST_SOURCE_ID + 1,
                        BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE,
                        meta.isEncrypted() ?
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_DECRYPTING :
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_NOT_ENCRYPTED,
                        null);
            }
        }

        // Remove broadcast source using other member of the same group
        mBassClientService.removeSource(mCurrentDevice1, TEST_SOURCE_ID + 1);

        // Verify all group members getting REMOVE_BCAST_SOURCE message
        assertThat(mStateMachines.size()).isEqualTo(2);
        for (BassClientStateMachine sm: mStateMachines.values()) {
            ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
            verify(sm, atLeast(1)).sendMessage(messageCaptor.capture());

            Optional<Message> msg = messageCaptor.getAllValues().stream()
                    .filter(m -> m.what == BassClientStateMachine.REMOVE_BCAST_SOURCE)
                    .findFirst();
            assertThat(msg.isPresent()).isEqualTo(true);

            // Verify using the right sourceId on each device
            if (sm.getDevice().equals(mCurrentDevice)) {
                assertThat(msg.get().arg1).isEqualTo(TEST_SOURCE_ID);
            } else if (sm.getDevice().equals(mCurrentDevice1)) {
                assertThat(msg.get().arg1).isEqualTo(TEST_SOURCE_ID + 1);
            }
        }
    }

    /**
     * Test whether service.removeSource() does send modify source to all the state machines if
     * either PA or BIS is synced
     */
    @Test
    public void testRemoveSourceForGroupAndTriggerModifySource() {
        prepareConnectedDeviceGroup();
        BluetoothLeBroadcastMetadata meta = createBroadcastMetadata(TEST_BROADCAST_ID);
        verifyAddSourceForGroup(meta);
        for (BassClientStateMachine sm : mStateMachines.values()) {
            injectRemoteSourceStateSourceAdded(
                    sm,
                    meta,
                    TEST_SOURCE_ID,
                    BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_SYNCHRONIZED,
                    meta.isEncrypted()
                            ? BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_DECRYPTING
                            : BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_NOT_ENCRYPTED,
                    null);
            doReturn(meta).when(sm).getCurrentBroadcastMetadata(eq(TEST_SOURCE_ID));
            doReturn(true).when(sm).isSyncedToTheSource(eq(TEST_SOURCE_ID));
        }

        // Remove broadcast source
        mBassClientService.removeSource(mCurrentDevice, TEST_SOURCE_ID);

        // Verify all group members getting UPDATE_BCAST_SOURCE message
        // because PA state is synced
        assertThat(mStateMachines.size()).isEqualTo(2);
        for (BassClientStateMachine sm : mStateMachines.values()) {
            ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
            verify(sm, atLeast(1)).sendMessage(messageCaptor.capture());

            Optional<Message> msg =
                    messageCaptor.getAllValues().stream()
                            .filter(m -> m.what == BassClientStateMachine.UPDATE_BCAST_SOURCE)
                            .findFirst();
            assertThat(msg.isPresent()).isEqualTo(true);

            // Verify using the right sourceId on each device
            assertThat(msg.get().arg1).isEqualTo(TEST_SOURCE_ID);
        }

        for (BassClientStateMachine sm : mStateMachines.values()) {
            // Update receiver state
            injectRemoteSourceStateChanged(
                    sm,
                    meta,
                    TEST_SOURCE_ID,
                    BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE,
                    meta.isEncrypted()
                            ? BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_DECRYPTING
                            : BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_NOT_ENCRYPTED,
                    null,
                    (long) 0x00000001);
            verify(mLeAudioService).activeBroadcastAssistantNotification(eq(true));
        }

        // Remove broadcast source
        mBassClientService.removeSource(mCurrentDevice, TEST_SOURCE_ID);

        // Verify all group members getting UPDATE_BCAST_SOURCE message if
        // bis sync state is non-zero and pa sync state is not synced
        assertThat(mStateMachines.size()).isEqualTo(2);
        for (BassClientStateMachine sm : mStateMachines.values()) {
            ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
            verify(sm, atLeast(1)).sendMessage(messageCaptor.capture());

            Optional<Message> msg =
                    messageCaptor.getAllValues().stream()
                            .filter(m -> m.what == BassClientStateMachine.UPDATE_BCAST_SOURCE)
                            .findFirst();
            assertThat(msg.isPresent()).isEqualTo(true);

            // Verify using the right sourceId on each device
            assertThat(msg.get().arg1).isEqualTo(TEST_SOURCE_ID);
        }

        for (BassClientStateMachine sm : mStateMachines.values()) {
            injectRemoteSourceStateRemoval(sm, TEST_SOURCE_ID);
        }
    }

    /**
     * Test whether the group operation flag is set on addSource() and removed on removeSource
     */
    @Test
    public void testGroupStickyFlagSetUnset() {
        prepareConnectedDeviceGroup();
        BluetoothLeBroadcastMetadata meta = createBroadcastMetadata(TEST_BROADCAST_ID);

        verifyAddSourceForGroup(meta);
        // Inject source added
        for (BassClientStateMachine sm: mStateMachines.values()) {
            if (sm.getDevice().equals(mCurrentDevice)) {
                injectRemoteSourceStateSourceAdded(sm, meta, TEST_SOURCE_ID,
                        BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE,
                        meta.isEncrypted() ?
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_DECRYPTING :
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_NOT_ENCRYPTED,
                        null);
            } else if (sm.getDevice().equals(mCurrentDevice1)) {
                injectRemoteSourceStateSourceAdded(sm, meta, TEST_SOURCE_ID + 1,
                        BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE,
                        meta.isEncrypted() ?
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_DECRYPTING :
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_NOT_ENCRYPTED,
                        null);
            }
        }

        // Remove broadcast source
        mBassClientService.removeSource(mCurrentDevice, TEST_SOURCE_ID);
        // Inject source removed
        for (BassClientStateMachine sm: mStateMachines.values()) {
            ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
            verify(sm, atLeast(1)).sendMessage(messageCaptor.capture());

            Optional<Message> msg = messageCaptor.getAllValues().stream()
                    .filter(m -> m.what == BassClientStateMachine.REMOVE_BCAST_SOURCE)
                    .findFirst();
            assertThat(msg.isPresent()).isEqualTo(true);

            if (sm.getDevice().equals(mCurrentDevice)) {
                assertThat(msg.get().arg1).isEqualTo(TEST_SOURCE_ID);
                injectRemoteSourceStateRemoval(sm, TEST_SOURCE_ID);
            } else if (sm.getDevice().equals(mCurrentDevice1)) {
                assertThat(msg.get().arg1).isEqualTo(TEST_SOURCE_ID + 1);
                injectRemoteSourceStateRemoval(sm, TEST_SOURCE_ID + 1);
            }
        }

        // Update broadcast source
        BluetoothLeBroadcastMetadata metaUpdate = createBroadcastMetadata(TEST_BROADCAST_ID + 1);
        mBassClientService.modifySource(mCurrentDevice, TEST_SOURCE_ID, metaUpdate);

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        Optional<Message> msg;

        // Verrify that one device got the message...
        verify(mStateMachines.get(mCurrentDevice), atLeast(1)).sendMessage(messageCaptor.capture());
        msg = messageCaptor.getAllValues().stream()
                    .filter(m -> m.what == BassClientStateMachine.UPDATE_BCAST_SOURCE)
                    .findFirst();
        assertThat(msg.isPresent()).isTrue();
        assertThat(msg.orElse(null)).isNotNull();

        //... but not the other one, since the sticky group flag should have been removed
        messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(mStateMachines.get(mCurrentDevice1), atLeast(1))
                .sendMessage(messageCaptor.capture());
        msg = messageCaptor.getAllValues().stream()
                    .filter(m -> m.what == BassClientStateMachine.UPDATE_BCAST_SOURCE)
                    .findFirst();
        assertThat(msg.isPresent()).isFalse();
    }

    /** Test switch source will be triggered if adding new source when sink has source */
    @Test
    public void testSwitchSourceAfterSourceAdded() {
        prepareConnectedDeviceGroup();
        BluetoothLeBroadcastMetadata meta = createBroadcastMetadata(TEST_BROADCAST_ID);
        BluetoothLeBroadcastMetadata newMeta = createBroadcastMetadata(TEST_BROADCAST_ID + 1);
        verifyAddSourceForGroup(meta);
        for (BassClientStateMachine sm : mStateMachines.values()) {
            injectRemoteSourceStateSourceAdded(
                    sm,
                    meta,
                    TEST_SOURCE_ID,
                    BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE,
                    meta.isEncrypted()
                            ? BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_DECRYPTING
                            : BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_NOT_ENCRYPTED,
                    null);
            injectRemoteSourceStateSourceAdded(
                    sm,
                    meta,
                    TEST_SOURCE_ID + 1,
                    BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE,
                    meta.isEncrypted()
                            ? BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_DECRYPTING
                            : BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_NOT_ENCRYPTED,
                    null);
        }

        // Add another new broadcast source
        mBassClientService.addSource(mCurrentDevice, newMeta, true);

        // Verify all group members getting SWITCH_BCAST_SOURCE message and first source got
        // selected
        // to remove
        assertThat(mStateMachines.size()).isEqualTo(2);
        for (BassClientStateMachine sm : mStateMachines.values()) {
            ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
            verify(sm, atLeast(1)).sendMessage(messageCaptor.capture());

            Optional<Message> msg =
                    messageCaptor.getAllValues().stream()
                            .filter(
                                    m ->
                                            (m.what == BassClientStateMachine.SWITCH_BCAST_SOURCE)
                                                    && (m.obj == newMeta)
                                                    && (m.arg1 == TEST_SOURCE_ID))
                            .findFirst();
            assertThat(msg.isPresent()).isTrue();
            assertThat(msg.orElse(null)).isNotNull();
        }
    }

    /**
     * Test that after multiple calls to service.addSource() with a group operation flag set,
     * there are two call to service.removeSource() needed to clear the flag
     */
    @Test
    public void testAddRemoveMultipleSourcesForGroup() {
        prepareConnectedDeviceGroup();
        BluetoothLeBroadcastMetadata meta = createBroadcastMetadata(TEST_BROADCAST_ID);
        verifyAddSourceForGroup(meta);
        assertThat(mStateMachines.size()).isEqualTo(2);
        for (BassClientStateMachine sm: mStateMachines.values()) {
            if (sm.getDevice().equals(mCurrentDevice)) {
                injectRemoteSourceStateSourceAdded(sm, meta, TEST_SOURCE_ID,
                        BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE,
                        meta.isEncrypted() ?
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_DECRYPTING :
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_NOT_ENCRYPTED,
                        null);
            } else if (sm.getDevice().equals(mCurrentDevice1)) {
                injectRemoteSourceStateSourceAdded(sm, meta, TEST_SOURCE_ID + 1,
                        BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE,
                        meta.isEncrypted() ?
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_DECRYPTING :
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_NOT_ENCRYPTED,
                        null);
            } else {
                throw new AssertionError("Unexpected device");
            }
        }

        // Add another broadcast source
        BluetoothLeBroadcastMetadata meta1 =
                new BluetoothLeBroadcastMetadata.Builder(meta)
                        .setBroadcastId(TEST_BROADCAST_ID + 1).build();
        verifyAddSourceForGroup(meta1);
        assertThat(mStateMachines.size()).isEqualTo(2);
        for (BassClientStateMachine sm: mStateMachines.values()) {
            if (sm.getDevice().equals(mCurrentDevice)) {
                injectRemoteSourceStateSourceAdded(sm, meta1, TEST_SOURCE_ID + 2,
                        BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE,
                        meta1.isEncrypted() ?
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_DECRYPTING :
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_NOT_ENCRYPTED,
                        null);
            } else if (sm.getDevice().equals(mCurrentDevice1)) {
                injectRemoteSourceStateSourceAdded(sm, meta1, TEST_SOURCE_ID + 3,
                        BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE,
                        meta1.isEncrypted() ?
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_DECRYPTING :
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_NOT_ENCRYPTED,
                        null);
            } else {
                throw new AssertionError("Unexpected device");
            }
        }

        // Remove the first broadcast source
        mBassClientService.removeSource(mCurrentDevice, TEST_SOURCE_ID);
        assertThat(mStateMachines.size()).isEqualTo(2);
        for (BassClientStateMachine sm: mStateMachines.values()) {
            ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
            verify(sm, atLeast(1)).sendMessage(messageCaptor.capture());

            Optional<Message> msg = messageCaptor.getAllValues().stream()
                    .filter(m -> m.what == BassClientStateMachine.REMOVE_BCAST_SOURCE)
                    .findFirst();
            assertThat(msg.isPresent()).isEqualTo(true);

            // Verify using the right sourceId on each device
            if (sm.getDevice().equals(mCurrentDevice)) {
                assertThat(msg.get().arg1).isEqualTo(TEST_SOURCE_ID);
                injectRemoteSourceStateRemoval(sm, TEST_SOURCE_ID);
            } else if (sm.getDevice().equals(mCurrentDevice1)) {
                assertThat(msg.get().arg1).isEqualTo(TEST_SOURCE_ID + 1);
                injectRemoteSourceStateRemoval(sm, TEST_SOURCE_ID + 1);
            } else {
                throw new AssertionError("Unexpected device");
            }
        }

        // Modify the second one and verify all group members getting UPDATE_BCAST_SOURCE
        BluetoothLeBroadcastMetadata metaUpdate = createBroadcastMetadata(TEST_BROADCAST_ID + 3);
        mBassClientService.modifySource(mCurrentDevice1, TEST_SOURCE_ID + 3, metaUpdate);
        assertThat(mStateMachines.size()).isEqualTo(2);
        for (BassClientStateMachine sm: mStateMachines.values()) {
            ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
            verify(sm, atLeast(1)).sendMessage(messageCaptor.capture());

            Optional<Message> msg = messageCaptor.getAllValues().stream()
                    .filter(m -> m.what == BassClientStateMachine.UPDATE_BCAST_SOURCE)
                    .findFirst();
            assertThat(msg.isPresent()).isEqualTo(true);
            assertThat(msg.get().obj).isEqualTo(metaUpdate);

            // Verify using the right sourceId on each device
            if (sm.getDevice().equals(mCurrentDevice)) {
                    assertThat(msg.get().arg1).isEqualTo(TEST_SOURCE_ID + 2);
            } else if (sm.getDevice().equals(mCurrentDevice1)) {
                    assertThat(msg.get().arg1).isEqualTo(TEST_SOURCE_ID + 3);
            } else {
                throw new AssertionError("Unexpected device");
            }
        }

        // Remove the second broadcast source and verify all group members getting
        // REMOVE_BCAST_SOURCE message for the second source
        mBassClientService.removeSource(mCurrentDevice, TEST_SOURCE_ID + 2);
        assertThat(mStateMachines.size()).isEqualTo(2);
        for (BassClientStateMachine sm: mStateMachines.values()) {
            ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
            verify(sm, atLeast(1)).sendMessage(messageCaptor.capture());

            if (sm.getDevice().equals(mCurrentDevice)) {
                Optional<Message> msg = messageCaptor.getAllValues().stream()
                        .filter(m -> (m.what == BassClientStateMachine.REMOVE_BCAST_SOURCE)
                                && (m.arg1 == TEST_SOURCE_ID + 2))
                        .findFirst();
                assertThat(msg.isPresent()).isEqualTo(true);
                injectRemoteSourceStateRemoval(sm, TEST_SOURCE_ID + 2);
            } else if (sm.getDevice().equals(mCurrentDevice1)) {
                Optional<Message> msg = messageCaptor.getAllValues().stream()
                        .filter(m -> (m.what == BassClientStateMachine.REMOVE_BCAST_SOURCE)
                                && (m.arg1 == TEST_SOURCE_ID + 3))
                        .findFirst();
                assertThat(msg.isPresent()).isEqualTo(true);
                injectRemoteSourceStateRemoval(sm, TEST_SOURCE_ID + 3);
            } else {
                throw new AssertionError("Unexpected device");
            }
        }

        // Fake the autonomous source change - or other client setting the source
        for (BassClientStateMachine sm: mStateMachines.values()) {
            clearInvocations(sm);

            BluetoothLeBroadcastMetadata metaOther =
                    createBroadcastMetadata(TEST_BROADCAST_ID + 20);
            injectRemoteSourceStateSourceAdded(sm, metaOther, TEST_SOURCE_ID + 20,
                    BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE,
                    meta.isEncrypted() ?
                            BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_DECRYPTING :
                            BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_NOT_ENCRYPTED,
                    null);
        }

        // Modify this source and verify it is not group managed
        BluetoothLeBroadcastMetadata metaUpdate2 = createBroadcastMetadata(TEST_BROADCAST_ID + 30);
        mBassClientService.modifySource(mCurrentDevice1, TEST_SOURCE_ID + 20, metaUpdate2);
        for (BassClientStateMachine sm: mStateMachines.values()) {
            if (sm.getDevice().equals(mCurrentDevice)) {
                verify(sm, times(0)).sendMessage(any());
            } else if (sm.getDevice().equals(mCurrentDevice1)) {
                ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
                verify(sm, times(1)).sendMessage(messageCaptor.capture());
                List<Message> msgs = messageCaptor.getAllValues().stream()
                        .filter(m -> (m.what == BassClientStateMachine.UPDATE_BCAST_SOURCE)
                                && (m.arg1 == TEST_SOURCE_ID + 20))
                        .collect(Collectors.toList());
                assertThat(msgs.size()).isEqualTo(1);
            } else {
                throw new AssertionError("Unexpected device");
            }
        }
    }

    @Test
    public void testInvalidRequestForGroup() {
        // Prepare the initial state
        prepareConnectedDeviceGroup();

        // Verify errors are reported for the entire group
        mBassClientService.addSource(mCurrentDevice1, null, true);
        assertThat(mStateMachines.size()).isEqualTo(2);
        for (BassClientStateMachine sm : mStateMachines.values()) {
            verify(sm, times(0)).sendMessage(any());
        }

        // Prepare valid source for group
        BluetoothLeBroadcastMetadata meta = createBroadcastMetadata(TEST_BROADCAST_ID);
        verifyAddSourceForGroup(meta);
        for (BassClientStateMachine sm: mStateMachines.values()) {
            if (sm.getDevice().equals(mCurrentDevice)) {
                injectRemoteSourceStateSourceAdded(sm, meta, TEST_SOURCE_ID,
                        BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE,
                        meta.isEncrypted() ?
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_DECRYPTING :
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_NOT_ENCRYPTED,
                        null);
            } else if (sm.getDevice().equals(mCurrentDevice1)) {
                injectRemoteSourceStateSourceAdded(sm, meta, TEST_SOURCE_ID + 1,
                        BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE,
                        meta.isEncrypted() ?
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_DECRYPTING :
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_NOT_ENCRYPTED,
                        null);
            }
        }

        // Verify errors are reported for the entire group
        mBassClientService.modifySource(mCurrentDevice, TEST_SOURCE_ID, null);
        assertThat(mStateMachines.size()).isEqualTo(2);
        for (BassClientStateMachine sm: mStateMachines.values()) {
            BluetoothDevice dev = sm.getDevice();
            try {
                verify(mCallback, after(TIMEOUT_MS).times(1)).onSourceModifyFailed(eq(dev),
                        eq(TEST_SOURCE_ID), eq(BluetoothStatusCodes.ERROR_BAD_PARAMETERS));
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }

        assertThat(mStateMachines.size()).isEqualTo(2);
        for (BassClientStateMachine sm: mStateMachines.values()) {
            doReturn(BluetoothProfile.STATE_DISCONNECTED).when(sm).getConnectionState();
        }

        // Verify errors are reported for the entire group
        mBassClientService.removeSource(mCurrentDevice, TEST_SOURCE_ID);
        assertThat(mStateMachines.size()).isEqualTo(2);
        for (BassClientStateMachine sm: mStateMachines.values()) {
            BluetoothDevice dev = sm.getDevice();
            try {
                verify(mCallback, after(TIMEOUT_MS).times(1)).onSourceRemoveFailed(eq(dev),
                        eq(TEST_SOURCE_ID), eq(BluetoothStatusCodes.ERROR_REMOTE_LINK_ERROR));
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }
    }

    /**
     * Test that an outgoing connection to two device that have BASS UUID is successful
     * and a connection state change intent is sent
     */
    @Test
    public void testConnectedIntent() {
        prepareConnectedDeviceGroup();

        assertThat(mStateMachines.size()).isEqualTo(2);
        for (BassClientStateMachine sm: mStateMachines.values()) {
            BluetoothDevice dev = sm.getDevice();
            verifyConnectionStateIntent(TIMEOUT_MS, dev, BluetoothProfile.STATE_CONNECTED,
                    BluetoothProfile.STATE_CONNECTING);
        }

        List<BluetoothDevice> devices = mBassClientService.getConnectedDevices();
        assertThat(devices.contains(mCurrentDevice)).isTrue();
        assertThat(devices.contains(mCurrentDevice1)).isTrue();
    }

    @Test
    public void testActiveSyncedSource_AddRemoveGet() {
        final int testSyncHandle = 1;
        prepareConnectedDeviceGroup();
        assertThat(mStateMachines.size()).isEqualTo(2);

        assertThat(mBassClientService.getActiveSyncedSources(mCurrentDevice)).isEqualTo(null);
        assertThat(mBassClientService.getActiveSyncedSources(mCurrentDevice1)).isEqualTo(null);

        // Verify add active synced source
        mBassClientService.addActiveSyncedSource(mCurrentDevice, testSyncHandle);
        mBassClientService.addActiveSyncedSource(mCurrentDevice1, testSyncHandle);
        // Verify duplicated source won't be added
        mBassClientService.addActiveSyncedSource(mCurrentDevice, testSyncHandle);
        mBassClientService.addActiveSyncedSource(mCurrentDevice1, testSyncHandle);
        assertThat(mBassClientService.getActiveSyncedSources(mCurrentDevice)).isNotEqualTo(null);
        assertThat(mBassClientService.getActiveSyncedSources(mCurrentDevice1)).isNotEqualTo(null);
        assertThat(mBassClientService.getActiveSyncedSources(mCurrentDevice).size()).isEqualTo(1);
        assertThat(mBassClientService.getActiveSyncedSources(mCurrentDevice1).size()).isEqualTo(1);

        // Verify remove active synced source
        mBassClientService.removeActiveSyncedSource(mCurrentDevice, testSyncHandle);
        mBassClientService.removeActiveSyncedSource(mCurrentDevice1, testSyncHandle);
        assertThat(mBassClientService.getActiveSyncedSources(mCurrentDevice)).isEqualTo(null);
        assertThat(mBassClientService.getActiveSyncedSources(mCurrentDevice1)).isEqualTo(null);
    }

    @Test
    public void testSelectSource_invalidActiveSource() {
        final int testSyncHandle = 0;
        final int testSyncHandle1 = 1;
        final int testSyncHandle2 = 2;
        final int testSyncHandle3 = 3;
        byte[] scanRecord = new byte[]{
                0x02, 0x01, 0x1a, // advertising flags
                0x05, 0x02, 0x52, 0x18, 0x0a, 0x11, // 16 bit service uuids
                0x04, 0x09, 0x50, 0x65, 0x64, // name
                0x02, 0x0A, (byte) 0xec, // tx power level
                0x05, 0x30, 0x54, 0x65, 0x73, 0x74, // broadcast name: Test
                0x06, 0x16, 0x52, 0x18, 0x50, 0x64, 0x65, // service data
                0x08, 0x16, 0x56, 0x18, 0x07, 0x03, 0x06, 0x07, 0x08,
                // service data - public broadcast,
                // feature - 0x7, metadata len - 0x3, metadata - 0x6, 0x7, 0x8
                0x05, (byte) 0xff, (byte) 0xe0, 0x00, 0x02, 0x15, // manufacturer specific data
                0x03, 0x50, 0x01, 0x02, // an unknown data type won't cause trouble
        };
        ScanRecord record = ScanRecord.parseFromBytes(scanRecord);

        prepareConnectedDeviceGroup();
        assertThat(mStateMachines.size()).isEqualTo(2);

        // Verify add active synced source
        mBassClientService.addActiveSyncedSource(mCurrentDevice, testSyncHandle);
        mBassClientService.addActiveSyncedSource(mCurrentDevice1, testSyncHandle);
        assertThat(mBassClientService.getActiveSyncedSources(mCurrentDevice)).isNotEqualTo(null);
        assertThat(mBassClientService.getActiveSyncedSources(mCurrentDevice1)).isNotEqualTo(null);
        assertThat(mBassClientService.getActiveSyncedSources(mCurrentDevice).size()).isEqualTo(1);
        assertThat(mBassClientService.getActiveSyncedSources(mCurrentDevice1).size()).isEqualTo(1);

        // Verify selectSource with max synced device should not proceed
        mBassClientService.addActiveSyncedSource(mCurrentDevice, testSyncHandle1);
        mBassClientService.addActiveSyncedSource(mCurrentDevice1, testSyncHandle1);
        mBassClientService.addActiveSyncedSource(mCurrentDevice, testSyncHandle2);
        mBassClientService.addActiveSyncedSource(mCurrentDevice1, testSyncHandle2);
        mBassClientService.addActiveSyncedSource(mCurrentDevice, testSyncHandle3);
        mBassClientService.addActiveSyncedSource(mCurrentDevice1, testSyncHandle3);

        assertThat(mBassClientService.getActiveSyncedSources(mCurrentDevice)).isNotEqualTo(null);
        assertThat(mBassClientService.getActiveSyncedSources(mCurrentDevice1)).isNotEqualTo(null);
        assertThat(mBassClientService.getActiveSyncedSources(mCurrentDevice).size()).isEqualTo(4);
        assertThat(mBassClientService.getActiveSyncedSources(mCurrentDevice1).size()).isEqualTo(4);

        BluetoothDevice testDevice4 = mBluetoothAdapter.getRemoteLeDevice(
                "00:01:02:03:04:05", BluetoothDevice.ADDRESS_TYPE_RANDOM);
        ScanResult scanResult1 = new ScanResult(testDevice4, 0, 0, 0, 0, 0, 0, 0, record, 0);
        mBassClientService.selectSource(mCurrentDevice, scanResult1, false);
        mBassClientService.selectSource(mCurrentDevice1, scanResult1, false);
        for (BassClientStateMachine sm : mStateMachines.values()) {
            ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
            verify(sm, atLeast(1)).sendMessage(messageCaptor.capture());

            Optional<Message> msg = messageCaptor.getAllValues().stream()
                    .filter(m -> m.what == BassClientStateMachine.REACHED_MAX_SOURCE_LIMIT)
                    .findFirst();
            assertThat(msg.isPresent()).isEqualTo(true);
        }

        // Verify remove all active synced source
        mBassClientService.removeActiveSyncedSource(mCurrentDevice, null);
        mBassClientService.removeActiveSyncedSource(mCurrentDevice1, null);
        assertThat(mBassClientService.getActiveSyncedSources(mCurrentDevice)).isEqualTo(null);
        assertThat(mBassClientService.getActiveSyncedSources(mCurrentDevice1)).isEqualTo(null);
    }

    @Test
    public void testPeriodicAdvertisementResultMap_updateGetAndModifyNotifiedFlag() {
        final String testBroadcastName = "Test";
        final int testSyncHandle = 1;
        final int testBroadcastId = 42;
        final int testBroadcastIdInvalid = 43;
        final int testAdvertiserSid = 1234;
        final int testAdvInterval = 100;

        BluetoothDevice testDevice =
                mBluetoothAdapter.getRemoteLeDevice(
                        TEST_MAC_ADDRESS, BluetoothDevice.ADDRESS_TYPE_RANDOM);

        // mock the update in selectSource
        mBassClientService.updatePeriodicAdvertisementResultMap(
                testDevice,
                testDevice.getAddressType(),
                BassConstants.INVALID_SYNC_HANDLE,
                BassConstants.INVALID_ADV_SID,
                testAdvInterval,
                testBroadcastId,
                null,
                testBroadcastName);

        // mock the update in onSyncEstablished
        mBassClientService.updatePeriodicAdvertisementResultMap(
                testDevice,
                BassConstants.INVALID_ADV_ADDRESS_TYPE,
                testSyncHandle,
                testAdvertiserSid,
                BassConstants.INVALID_ADV_INTERVAL,
                BassConstants.INVALID_BROADCAST_ID,
                null,
                null);

        assertThat(mBassClientService.getPeriodicAdvertisementResult(testDevice, testBroadcastIdInvalid))
                .isEqualTo(null);
        PeriodicAdvertisementResult paResult =
                mBassClientService.getPeriodicAdvertisementResult(testDevice, testBroadcastId);
        assertThat(paResult.getAddressType()).isEqualTo(BluetoothDevice.ADDRESS_TYPE_RANDOM);
        assertThat(paResult.getSyncHandle()).isEqualTo(testSyncHandle);
        assertThat(paResult.getAdvSid()).isEqualTo(testAdvertiserSid);
        assertThat(paResult.getAdvInterval()).isEqualTo(testAdvInterval);
        assertThat(paResult.getBroadcastName()).isEqualTo(testBroadcastName);

        // validate modify notified flag
        paResult.setNotified(true);
        assertThat(paResult.isNotified()).isEqualTo(true);
        mBassClientService.clearNotifiedFlags();
        assertThat(paResult.isNotified()).isEqualTo(false);
    }

    @Test
    public void testSyncHandleToBroadcastIdMap_getSyncHandleAndGetBroadcastId() {
        final String testBroadcastName = "Test";
        final int testSyncHandle = 1;
        final int testSyncHandleInvalid = 2;
        final int testBroadcastId = 42;
        final int testBroadcastIdInvalid = 43;
        final int testAdvertiserSid = 1234;
        final int testAdvInterval = 100;

        BluetoothDevice testDevice =
                mBluetoothAdapter.getRemoteLeDevice(
                        TEST_MAC_ADDRESS, BluetoothDevice.ADDRESS_TYPE_RANDOM);

        // mock the update in selectSource
        mBassClientService.updatePeriodicAdvertisementResultMap(
                testDevice,
                testDevice.getAddressType(),
                BassConstants.INVALID_SYNC_HANDLE,
                BassConstants.INVALID_ADV_SID,
                testAdvInterval,
                testBroadcastId,
                null,
                testBroadcastName);

        // mock the update in onSyncEstablished
        mBassClientService.updatePeriodicAdvertisementResultMap(
                testDevice,
                BassConstants.INVALID_ADV_ADDRESS_TYPE,
                testSyncHandle,
                testAdvertiserSid,
                BassConstants.INVALID_ADV_INTERVAL,
                BassConstants.INVALID_BROADCAST_ID,
                null,
                null);

        assertThat(mBassClientService.getSyncHandleForBroadcastId(testBroadcastIdInvalid))
                .isEqualTo(BassConstants.INVALID_SYNC_HANDLE);
        assertThat(mBassClientService.getBroadcastIdForSyncHandle(testSyncHandleInvalid))
                .isEqualTo(BassConstants.INVALID_BROADCAST_ID);
        assertThat(mBassClientService.getSyncHandleForBroadcastId(testBroadcastId))
                .isEqualTo(testSyncHandle);
        assertThat(mBassClientService.getBroadcastIdForSyncHandle(testSyncHandle))
                .isEqualTo(testBroadcastId);
    }

    @Test
    public void testSuspendResumeSourceSynchronization() {
        prepareConnectedDeviceGroup();
        BluetoothLeBroadcastMetadata meta = createBroadcastMetadata(TEST_BROADCAST_ID);
        verifyAddSourceForGroup(meta);
        for (BassClientStateMachine sm: mStateMachines.values()) {
            if (sm.getDevice().equals(mCurrentDevice)) {
                injectRemoteSourceStateSourceAdded(sm, meta, TEST_SOURCE_ID,
                        BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE,
                        meta.isEncrypted() ?
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_DECRYPTING :
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_NOT_ENCRYPTED,
                        null);
            } else if (sm.getDevice().equals(mCurrentDevice1)) {
                injectRemoteSourceStateSourceAdded(sm, meta, TEST_SOURCE_ID + 1,
                        BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE,
                        meta.isEncrypted() ?
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_DECRYPTING :
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_NOT_ENCRYPTED,
                        null);
            }
        }

        mBassClientService.suspendAllReceiversSourceSynchronization();

        // Inject source removed
        for (BassClientStateMachine sm: mStateMachines.values()) {
            ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
            verify(sm, atLeast(1)).sendMessage(messageCaptor.capture());

            Optional<Message> msg = messageCaptor.getAllValues().stream()
                    .filter(m -> m.what == BassClientStateMachine.REMOVE_BCAST_SOURCE)
                    .findFirst();
            assertThat(msg.isPresent()).isEqualTo(true);

            if (sm.getDevice().equals(mCurrentDevice)) {
                assertThat(msg.get().arg1).isEqualTo(TEST_SOURCE_ID);
                injectRemoteSourceStateRemoval(sm, TEST_SOURCE_ID);
            } else if (sm.getDevice().equals(mCurrentDevice1)) {
                assertThat(msg.get().arg1).isEqualTo(TEST_SOURCE_ID + 1);
                injectRemoteSourceStateRemoval(sm, TEST_SOURCE_ID + 1);
            }
        }

        mBassClientService.resumeReceiversSourceSynchronization();

        // Verify all group members getting ADD_BCAST_SOURCE message
        assertThat(mStateMachines.size()).isEqualTo(2);
        for (BassClientStateMachine sm: mStateMachines.values()) {
            ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
            verify(sm, atLeast(1)).sendMessage(messageCaptor.capture());

            long count = messageCaptor.getAllValues().stream()
                    .filter(m -> (m.what == BassClientStateMachine.ADD_BCAST_SOURCE)
                                        && (m.obj == meta))
                    .count();
            assertThat(count).isEqualTo(2);
        }
    }

    @Test
    public void testHandleUnicastSourceStreamStatusChange() {
        prepareConnectedDeviceGroup();
        BluetoothLeBroadcastMetadata meta = createBroadcastMetadata(TEST_BROADCAST_ID);
        verifyAddSourceForGroup(meta);
        for (BassClientStateMachine sm: mStateMachines.values()) {
            if (sm.getDevice().equals(mCurrentDevice)) {
                injectRemoteSourceStateSourceAdded(sm, meta, TEST_SOURCE_ID,
                        BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE,
                        meta.isEncrypted() ?
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_DECRYPTING :
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_NOT_ENCRYPTED,
                        null);

                // Update receiver state
                injectRemoteSourceStateChanged(sm, meta, TEST_SOURCE_ID,
                        BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE,
                        meta.isEncrypted() ?
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_DECRYPTING :
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_NOT_ENCRYPTED,
                        null, (long) 0x00000001);
                verify(mLeAudioService).activeBroadcastAssistantNotification(eq(true));
            } else if (sm.getDevice().equals(mCurrentDevice1)) {
                injectRemoteSourceStateSourceAdded(sm, meta, TEST_SOURCE_ID + 1,
                        BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE,
                        meta.isEncrypted() ?
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_DECRYPTING :
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_NOT_ENCRYPTED,
                        null);

                // Update receiver state
                injectRemoteSourceStateChanged(sm, meta, TEST_SOURCE_ID + 1,
                        BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE,
                        meta.isEncrypted() ?
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_DECRYPTING :
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_NOT_ENCRYPTED,
                        null, (long) 0x00000002);
            }
        }

        /* Unicast would like to stream */
        mBassClientService.handleUnicastSourceStreamStatusChange(
                0 /* STATUS_LOCAL_STREAM_REQUESTED */);

        // Inject source removed
        for (BassClientStateMachine sm: mStateMachines.values()) {
            ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
            verify(sm, atLeast(1)).sendMessage(messageCaptor.capture());

            Optional<Message> msg = messageCaptor.getAllValues().stream()
                    .filter(m -> m.what == BassClientStateMachine.REMOVE_BCAST_SOURCE)
                    .findFirst();
            assertThat(msg.isPresent()).isEqualTo(true);

            if (sm.getDevice().equals(mCurrentDevice)) {
                assertThat(msg.get().arg1).isEqualTo(TEST_SOURCE_ID);
                injectRemoteSourceStateRemoval(sm, TEST_SOURCE_ID);
            } else if (sm.getDevice().equals(mCurrentDevice1)) {
                assertThat(msg.get().arg1).isEqualTo(TEST_SOURCE_ID + 1);
                injectRemoteSourceStateRemoval(sm, TEST_SOURCE_ID + 1);
            }
        }

        /* Unicast finished streaming */
        mBassClientService.handleUnicastSourceStreamStatusChange(
                2 /* STATUS_LOCAL_STREAM_SUSPENDED */);

        // Verify all group members getting ADD_BCAST_SOURCE message
        assertThat(mStateMachines.size()).isEqualTo(2);
        for (BassClientStateMachine sm: mStateMachines.values()) {
            ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
            verify(sm, atLeast(1)).sendMessage(messageCaptor.capture());

            long count = messageCaptor.getAllValues().stream()
                    .filter(m -> (m.what == BassClientStateMachine.ADD_BCAST_SOURCE)
                                        && (m.obj == meta))
                    .count();
            assertThat(count).isEqualTo(2);
        }

        // Update receiver state with lost BIS sync
        for (BassClientStateMachine sm: mStateMachines.values()) {
            if (sm.getDevice().equals(mCurrentDevice)) {
                injectRemoteSourceStateChanged(sm, meta, TEST_SOURCE_ID,
                        BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE,
                        meta.isEncrypted() ?
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_DECRYPTING :
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_NOT_ENCRYPTED,
                        null, (long) 0x00000000);
                verify(mLeAudioService).activeBroadcastAssistantNotification(eq(false));
            } else if (sm.getDevice().equals(mCurrentDevice1)) {
                injectRemoteSourceStateChanged(sm, meta, TEST_SOURCE_ID + 1,
                        BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE,
                        meta.isEncrypted() ?
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_DECRYPTING :
                                BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_NOT_ENCRYPTED,
                        null, (long) 0x00000000);
            }
        }
    }

    private void prepareTwoSynchronizedDevices() {
        BluetoothLeBroadcastMetadata meta = createBroadcastMetadata(TEST_BROADCAST_ID);

        doReturn(new ArrayList<BluetoothLeBroadcastMetadata>(Arrays.asList(meta)))
                .when(mLeAudioService)
                .getAllBroadcastMetadata();
        prepareConnectedDeviceGroup();

        verifyAddSourceForGroup(meta);
        for (BassClientStateMachine sm : mStateMachines.values()) {
            if (sm.getDevice().equals(mCurrentDevice)) {
                injectRemoteSourceStateSourceAdded(
                        sm,
                        meta,
                        TEST_SOURCE_ID,
                        BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE,
                        meta.isEncrypted()
                                ? BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_DECRYPTING
                                : BluetoothLeBroadcastReceiveState
                                        .BIG_ENCRYPTION_STATE_NOT_ENCRYPTED,
                        null);
                // verify source id
                try {
                    verify(mCallback, timeout(TIMEOUT_MS).atLeastOnce())
                            .onSourceAdded(
                                    eq(mCurrentDevice),
                                    eq(TEST_SOURCE_ID),
                                    eq(BluetoothStatusCodes.REASON_LOCAL_APP_REQUEST));
                } catch (RemoteException e) {
                    throw e.rethrowFromSystemServer();
                }
            } else if (sm.getDevice().equals(mCurrentDevice1)) {
                injectRemoteSourceStateSourceAdded(
                        sm,
                        meta,
                        TEST_SOURCE_ID + 1,
                        BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE,
                        meta.isEncrypted()
                                ? BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_DECRYPTING
                                : BluetoothLeBroadcastReceiveState
                                        .BIG_ENCRYPTION_STATE_NOT_ENCRYPTED,
                        null);
                // verify source id
                try {
                    verify(mCallback, timeout(TIMEOUT_MS).atLeastOnce())
                            .onSourceAdded(
                                    eq(mCurrentDevice1),
                                    eq(TEST_SOURCE_ID + 1),
                                    eq(BluetoothStatusCodes.REASON_LOCAL_APP_REQUEST));
                } catch (RemoteException e) {
                    throw e.rethrowFromSystemServer();
                }
            }
        }
    }

    @Test
    public void testPrivateBroadcastIntentionalDisconnection() {
        prepareTwoSynchronizedDevices();

        /* Imitate broadcast being active */
        doReturn(true).when(mLeAudioService).isPlaying(TEST_BROADCAST_ID);
        /* Imitate devices being primary */
        doReturn(true).when(mLeAudioService).isPrimaryDevice(mCurrentDevice);
        doReturn(true).when(mLeAudioService).isPrimaryDevice(mCurrentDevice1);

        /* Imitate device 1/2 disconnection from StateMachine context */
        mBassClientService.handleDeviceDisconnection(mCurrentDevice, true);

        /* After first device disconnection and de-synchronization expect not stopping broadcast */
        verify(mLeAudioService, times(0)).stopBroadcast(eq(TEST_BROADCAST_ID));

        /* Imitate first device being in disconnected state */
        doReturn(BluetoothProfile.STATE_DISCONNECTED)
                .when(mStateMachines.get(mCurrentDevice))
                .getConnectionState();

        /* Imitate device 2/2 disconnection from StateMachine context */
        mBassClientService.handleDeviceDisconnection(mCurrentDevice1, true);

        /* After second device disconnection and de-synchronization expect stopping broadcast */
        verify(mLeAudioService, times(1)).stopBroadcast(eq(TEST_BROADCAST_ID));
    }

    @Test
    public void testPrivateBroadcastUnintentionalDisconnection() {
        prepareTwoSynchronizedDevices();

        /* Imitate broadcast being active */
        doReturn(true).when(mLeAudioService).isPlaying(TEST_BROADCAST_ID);
        /* Imitate devices being primary */
        doReturn(true).when(mLeAudioService).isPrimaryDevice(mCurrentDevice);
        doReturn(true).when(mLeAudioService).isPrimaryDevice(mCurrentDevice1);

        /* Imitate device 1/2 disconnection from StateMachine context */
        mBassClientService.handleDeviceDisconnection(mCurrentDevice, false);

        /* After first device disconnection and de-synchronization expect not stopping broadcast */
        verify(mLeAudioService, times(0)).stopBroadcast(eq(TEST_BROADCAST_ID));

        /* Imitate first device being in disconnected state */
        doReturn(BluetoothProfile.STATE_DISCONNECTED)
                .when(mStateMachines.get(mCurrentDevice))
                .getConnectionState();

        /* Imitate device 2/2 disconnection from StateMachine context */
        mBassClientService.handleDeviceDisconnection(mCurrentDevice1, false);

        /* After second device disconnection and de-synchronization expect stopping broadcast */
        verify(mLeAudioService, times(1)).stopBroadcast(eq(TEST_BROADCAST_ID));
    }

    @Test
    public void testAudioSharingIntentionalDisconnection() {
        prepareTwoSynchronizedDevices();

        /* Imitate broadcast being active */
        doReturn(true).when(mLeAudioService).isPlaying(TEST_BROADCAST_ID);
        /* Imitate devices being primary */
        doReturn(true).when(mLeAudioService).isPrimaryDevice(mCurrentDevice);
        doReturn(false).when(mLeAudioService).isPrimaryDevice(mCurrentDevice1);

        /* Imitate device 1/2 disconnection from StateMachine context */
        mBassClientService.handleDeviceDisconnection(mCurrentDevice, true);

        /* After first device disconnection and de-synchronization expect stopping broadcast */
        verify(mLeAudioService, times(1)).stopBroadcast(eq(TEST_BROADCAST_ID));

        /* Imitate first device being in disconnected state */
        doReturn(BluetoothProfile.STATE_DISCONNECTED)
                .when(mStateMachines.get(mCurrentDevice))
                .getConnectionState();

        /* Imitate device 2/2 disconnection from StateMachine context */
        mBassClientService.handleDeviceDisconnection(mCurrentDevice1, true);

        /* After second device disconnection and de-synchronization expect not stopping broadcast */
        verify(mLeAudioService, times(1)).stopBroadcast(eq(TEST_BROADCAST_ID));
    }

    @Test
    public void testAudioSharingUnintentionalDisconnection() {
        prepareTwoSynchronizedDevices();

        /* Imitate broadcast being active */
        doReturn(true).when(mLeAudioService).isPlaying(TEST_BROADCAST_ID);
        /* Imitate devices being primary */
        doReturn(true).when(mLeAudioService).isPrimaryDevice(mCurrentDevice);
        doReturn(false).when(mLeAudioService).isPrimaryDevice(mCurrentDevice1);

        /* Imitate device 1/2 disconnection from StateMachine context */
        mBassClientService.handleDeviceDisconnection(mCurrentDevice, false);

        /* After first device disconnection and de-synchronization expect not stopping broadcast */
        verify(mLeAudioService, times(0)).stopBroadcast(eq(TEST_BROADCAST_ID));

        /* Imitate first device being in disconnected state */
        doReturn(BluetoothProfile.STATE_DISCONNECTED)
                .when(mStateMachines.get(mCurrentDevice))
                .getConnectionState();

        /* Imitate device 2/2 disconnection from StateMachine context */
        mBassClientService.handleDeviceDisconnection(mCurrentDevice1, false);

        /* After second device disconnection and de-synchronization timeout to be fired */
        verify(mLeAudioService, times(0)).stopBroadcast(eq(TEST_BROADCAST_ID));
    }

    @Test
    public void testNotifyBroadcastStateChangedStopped() {
        BluetoothLeBroadcastMetadata meta = createBroadcastMetadata(TEST_BROADCAST_ID);

        doReturn(new ArrayList<BluetoothLeBroadcastMetadata>(Arrays.asList(meta)))
                .when(mLeAudioService)
                .getAllBroadcastMetadata();
        prepareConnectedDeviceGroup();

        verifyAddSourceForGroup(meta);
        for (BassClientStateMachine sm : mStateMachines.values()) {
            if (sm.getDevice().equals(mCurrentDevice)) {
                injectRemoteSourceStateSourceAdded(
                        sm,
                        meta,
                        TEST_SOURCE_ID,
                        BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE,
                        meta.isEncrypted()
                                ? BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_DECRYPTING
                                : BluetoothLeBroadcastReceiveState
                                        .BIG_ENCRYPTION_STATE_NOT_ENCRYPTED,
                        null);
                // verify source id
                try {
                    verify(mCallback, timeout(TIMEOUT_MS).atLeastOnce())
                            .onSourceAdded(
                                    eq(mCurrentDevice),
                                    eq(TEST_SOURCE_ID),
                                    eq(BluetoothStatusCodes.REASON_LOCAL_APP_REQUEST));
                } catch (RemoteException e) {
                    throw e.rethrowFromSystemServer();
                }
            } else if (sm.getDevice().equals(mCurrentDevice1)) {
                injectRemoteSourceStateSourceAdded(
                        sm,
                        meta,
                        TEST_SOURCE_ID + 1,
                        BluetoothLeBroadcastReceiveState.PA_SYNC_STATE_IDLE,
                        meta.isEncrypted()
                                ? BluetoothLeBroadcastReceiveState.BIG_ENCRYPTION_STATE_DECRYPTING
                                : BluetoothLeBroadcastReceiveState
                                        .BIG_ENCRYPTION_STATE_NOT_ENCRYPTED,
                        null);
                // verify source id
                try {
                    verify(mCallback, timeout(TIMEOUT_MS).atLeastOnce())
                            .onSourceAdded(
                                    eq(mCurrentDevice1),
                                    eq(TEST_SOURCE_ID + 1),
                                    eq(BluetoothStatusCodes.REASON_LOCAL_APP_REQUEST));
                } catch (RemoteException e) {
                    throw e.rethrowFromSystemServer();
                }
            }
        }

        /* Imitate broadcast being not active */
        doReturn(false).when(mLeAudioService).isPlaying(TEST_BROADCAST_ID);

        mBassClientService.notifyBroadcastStateChanged(
                0 /* BROADCAST_STATE_STOPPED */, TEST_BROADCAST_ID);

        /* Imitiate scenario when if there would be broadcast - stop would be called */
        mBassClientService.handleDeviceDisconnection(mCurrentDevice, true);
        mBassClientService.handleDeviceDisconnection(mCurrentDevice1, true);

        /* Imitate first device being in disconnected state */
        doReturn(BluetoothProfile.STATE_DISCONNECTED)
                .when(mStateMachines.get(mCurrentDevice))
                .getConnectionState();

        /* After second device disconnection and de-synchronization expect not calling broadcast to
         * stop due to previous broadcast stream stopped */
        verify(mLeAudioService, times(0)).stopBroadcast(eq(TEST_BROADCAST_ID));
    }
}
