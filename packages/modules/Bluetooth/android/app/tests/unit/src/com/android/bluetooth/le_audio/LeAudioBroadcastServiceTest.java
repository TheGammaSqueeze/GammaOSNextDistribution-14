/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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

package com.android.bluetooth.le_audio;

import static android.bluetooth.IBluetoothLeAudio.LE_AUDIO_GROUP_ID_INVALID;

import static org.mockito.Mockito.*;

import android.annotation.Nullable;
import android.bluetooth.*;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.BluetoothProfileConnectionInfo;
import android.os.Looper;
import android.os.ParcelUuid;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.TestUtils;
import com.android.bluetooth.Utils;
import com.android.bluetooth.bass_client.BassClientService;
import com.android.bluetooth.btservice.ActiveDeviceManager;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.ServiceFactory;
import com.android.bluetooth.btservice.storage.DatabaseManager;
import com.android.bluetooth.flags.Flags;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class LeAudioBroadcastServiceTest {
    private static final int TIMEOUT_MS = 1000;

    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private BluetoothAdapter mAdapter;
    private BluetoothDevice mDevice;
    private BluetoothDevice mBroadcastDevice;

    private Context mTargetContext;
    private LeAudioService mService;
    private LeAudioIntentReceiver mLeAudioIntentReceiver;
    private LinkedBlockingQueue<Intent> mIntentQueue;
    @Mock private ActiveDeviceManager mActiveDeviceManager;
    @Mock
    private AdapterService mAdapterService;
    @Mock
    private DatabaseManager mDatabaseManager;
    @Mock
    private AudioManager mAudioManager;
    @Mock private LeAudioBroadcasterNativeInterface mLeAudioBroadcasterNativeInterface;
    @Mock private LeAudioNativeInterface mLeAudioNativeInterface;
    @Mock private LeAudioTmapGattServer mTmapGattServer;
    @Mock private BassClientService mBassClientService;
    @Spy private LeAudioObjectsFactory mObjectsFactory = LeAudioObjectsFactory.getInstance();
    @Spy private ServiceFactory mServiceFactory = new ServiceFactory();

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
    private static final String TEST_BROADCAST_NAME = "Name Test";

    private static final BluetoothLeAudioCodecConfig LC3_16KHZ_CONFIG =
            new BluetoothLeAudioCodecConfig.Builder()
                    .setCodecType(BluetoothLeAudioCodecConfig.SOURCE_CODEC_TYPE_LC3)
                    .setSampleRate(BluetoothLeAudioCodecConfig.SAMPLE_RATE_16000)
                    .build();
    private static final BluetoothLeAudioCodecConfig LC3_48KHZ_CONFIG =
            new BluetoothLeAudioCodecConfig.Builder()
                    .setCodecType(BluetoothLeAudioCodecConfig.SOURCE_CODEC_TYPE_LC3)
                    .setSampleRate(BluetoothLeAudioCodecConfig.SAMPLE_RATE_48000)
                    .build();

    private static final List<BluetoothLeAudioCodecConfig> INPUT_SELECTABLE_CONFIG_STANDARD =
            List.of(LC3_16KHZ_CONFIG);
    private static final List<BluetoothLeAudioCodecConfig> OUTPUT_SELECTABLE_CONFIG_STANDARD =
            List.of(LC3_16KHZ_CONFIG);

    private static final List<BluetoothLeAudioCodecConfig> INPUT_SELECTABLE_CONFIG_HIGH =
            List.of(LC3_48KHZ_CONFIG);
    private static final List<BluetoothLeAudioCodecConfig> OUTPUT_SELECTABLE_CONFIG_HIGH =
            List.of(LC3_48KHZ_CONFIG);

    private boolean mOnBroadcastStartedCalled = false;
    private boolean mOnBroadcastStartFailedCalled = false;
    private boolean mOnBroadcastStoppedCalled = false;
    private boolean mOnBroadcastStopFailedCalled = false;
    private boolean mOnPlaybackStartedCalled = false;
    private boolean mOnPlaybackStoppedCalled = false;
    private boolean mOnBroadcastUpdatedCalled = false;
    private boolean mOnBroadcastUpdateFailedCalled = false;
    private boolean mOnBroadcastMetadataChangedCalled = false;
    private int mOnBroadcastStartFailedReason = BluetoothStatusCodes.SUCCESS;

    private final IBluetoothLeBroadcastCallback mCallbacks =
            new IBluetoothLeBroadcastCallback.Stub() {
                @Override
                public void onBroadcastStarted(int reason, int broadcastId) {
                    mOnBroadcastStartedCalled = true;
                }

                @Override
                public void onBroadcastStartFailed(int reason) {
                    mOnBroadcastStartFailedCalled = true;
                    mOnBroadcastStartFailedReason = reason;
                }

                @Override
                public void onBroadcastStopped(int reason, int broadcastId) {
                    mOnBroadcastStoppedCalled = true;
                }

                @Override
                public void onBroadcastStopFailed(int reason) {
                    mOnBroadcastStopFailedCalled = true;
                }

                @Override
                public void onPlaybackStarted(int reason, int broadcastId) {
                    mOnPlaybackStartedCalled = true;
                }

                @Override
                public void onPlaybackStopped(int reason, int broadcastId) {
                    mOnPlaybackStoppedCalled = true;
                }

                @Override
                public void onBroadcastUpdated(int reason, int broadcastId) {
                    mOnBroadcastUpdatedCalled = true;
                }

                @Override
                public void onBroadcastUpdateFailed(int reason, int broadcastId) {
                    mOnBroadcastUpdateFailedCalled = true;
                }

                @Override
                public void onBroadcastMetadataChanged(
                        int broadcastId, BluetoothLeBroadcastMetadata metadata) {
                    mOnBroadcastMetadataChangedCalled = true;
                }
            };

    @Before
    public void setUp() throws Exception {
        mTargetContext = InstrumentationRegistry.getTargetContext();

        // Set up mocks and test assets
        MockitoAnnotations.initMocks(this);

        // Use spied objects factory
        doNothing().when(mTmapGattServer).start(anyInt());
        doNothing().when(mTmapGattServer).stop();
        LeAudioObjectsFactory.setInstanceForTesting(mObjectsFactory);
        doReturn(mTmapGattServer).when(mObjectsFactory).getTmapGattServer(any());

        if (Looper.myLooper() == null) {
            Looper.prepare();
        }

        TestUtils.setAdapterService(mAdapterService);
        doReturn(mDatabaseManager).when(mAdapterService).getDatabase();
        doReturn(true).when(mAdapterService).isLeAudioBroadcastSourceSupported();
        doReturn((long)(1 << BluetoothProfile.LE_AUDIO_BROADCAST) | (1 << BluetoothProfile.LE_AUDIO))
                .when(mAdapterService).getSupportedProfilesBitMask();
        doReturn(mActiveDeviceManager).when(mAdapterService).getActiveDeviceManager();

        mAdapter = BluetoothAdapter.getDefaultAdapter();

        LeAudioBroadcasterNativeInterface.setInstance(mLeAudioBroadcasterNativeInterface);
        LeAudioNativeInterface.setInstance(mLeAudioNativeInterface);
        startService();

        mService.mAudioManager = mAudioManager;
        mService.mServiceFactory = mServiceFactory;
        when(mServiceFactory.getBassClientService()).thenReturn(mBassClientService);
        // Set up the State Changed receiver
        IntentFilter filter = new IntentFilter();
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);

        mLeAudioIntentReceiver = new LeAudioIntentReceiver();
        filter.addAction(BluetoothLeAudio.ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED);
        mTargetContext.registerReceiver(mLeAudioIntentReceiver, filter);

        mDevice = TestUtils.getTestDevice(mAdapter, 0);
        mBroadcastDevice = TestUtils.getTestDevice(mAdapter, 1);
        when(mAdapterService.getDeviceFromByte(Utils.getBytesFromAddress("FF:FF:FF:FF:FF:FF")))
                .thenReturn(mBroadcastDevice);

        mIntentQueue = new LinkedBlockingQueue<Intent>();
    }

    @After
    public void tearDown() throws Exception {
        if (mService == null || mAdapter == null) {
            return;
        }
        if (mLeAudioIntentReceiver != null) {
            mTargetContext.unregisterReceiver(mLeAudioIntentReceiver);
        }

        stopService();
        LeAudioBroadcasterNativeInterface.setInstance(null);
        LeAudioNativeInterface.setInstance(null);
        TestUtils.clearAdapterService(mAdapterService);
        reset(mAudioManager);
    }

    private void startService() throws TimeoutException {
        mService = new LeAudioService(mTargetContext);
        mService.start();
        mService.setAvailable(true);
    }

    private void stopService() throws TimeoutException {
        mService.stop();
        mService = LeAudioService.getLeAudioService();
        Assert.assertNull(mService);
    }

    /**
     * Test getting LeAudio Service
     */
    @Test
    public void testGetLeAudioService() {
        Assert.assertEquals(mService, LeAudioService.getLeAudioService());
    }

    @Test
    public void testStopLeAudioService() {
        Assert.assertEquals(mService, LeAudioService.getLeAudioService());

        InstrumentationRegistry.getInstrumentation().runOnMainSync(mService::stop);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(mService::start);
    }

    void verifyBroadcastStarted(int broadcastId, BluetoothLeBroadcastSettings settings) {
        mService.createBroadcast(settings);

        List<BluetoothLeBroadcastSubgroupSettings> settingsList =
                settings.getSubgroupSettings();

        int[] expectedQualityArray =
                settingsList.stream()
                        .mapToInt(setting -> setting.getPreferredQuality()).toArray();
        byte[][] expectedDataArray =
                settingsList.stream()
                        .map(setting -> setting.getContentMetadata().getRawMetadata())
                        .toArray(byte[][]::new);

        verify(mLeAudioBroadcasterNativeInterface, times(1))
                .createBroadcast(
                        eq(true),
                        eq(TEST_BROADCAST_NAME),
                        eq(settings.getBroadcastCode()),
                        eq(settings.getPublicBroadcastMetadata().getRawMetadata()),
                        eq(expectedQualityArray),
                        eq(expectedDataArray));

        // Check if broadcast is started automatically when created
        LeAudioStackEvent create_event =
                new LeAudioStackEvent(LeAudioStackEvent.EVENT_TYPE_BROADCAST_CREATED);
        create_event.valueInt1 = broadcastId;
        create_event.valueBool1 = true;
        mService.messageFromNative(create_event);

        // Verify if broadcast is auto-started on start
        verify(mLeAudioBroadcasterNativeInterface, times(1)).startBroadcast(eq(broadcastId));

        // Notify initial paused state
        LeAudioStackEvent state_event =
                new LeAudioStackEvent(LeAudioStackEvent.EVENT_TYPE_BROADCAST_STATE);
        state_event.valueInt1 = broadcastId;
        state_event.valueInt2 = LeAudioStackEvent.BROADCAST_STATE_PAUSED;
        mService.messageFromNative(state_event);

        // Switch to active streaming
        state_event = new LeAudioStackEvent(LeAudioStackEvent.EVENT_TYPE_BROADCAST_STATE);
        state_event.valueInt1 = broadcastId;
        state_event.valueInt2 = LeAudioStackEvent.BROADCAST_STATE_STREAMING;
        mService.messageFromNative(state_event);

        // Check if metadata is requested when the broadcast starts to stream
        verify(mLeAudioBroadcasterNativeInterface, times(1)).getBroadcastMetadata(eq(broadcastId));
        Assert.assertFalse(mOnBroadcastStartFailedCalled);
        Assert.assertTrue(mOnBroadcastStartedCalled);
    }

    void verifyBroadcastStopped(int broadcastId) {
        mService.stopBroadcast(broadcastId);
        verify(mLeAudioBroadcasterNativeInterface, times(1)).stopBroadcast(eq(broadcastId));

        LeAudioStackEvent state_event =
                new LeAudioStackEvent(LeAudioStackEvent.EVENT_TYPE_BROADCAST_STATE);
        state_event.valueInt1 = broadcastId;
        state_event.valueInt2 = LeAudioStackEvent.BROADCAST_STATE_STOPPED;
        mService.messageFromNative(state_event);

        // Verify if broadcast is auto-destroyed on stop
        verify(mLeAudioBroadcasterNativeInterface, times(1)).destroyBroadcast(eq(broadcastId));

        state_event = new LeAudioStackEvent(LeAudioStackEvent.EVENT_TYPE_BROADCAST_DESTROYED);
        state_event.valueInt1 = broadcastId;
        mService.messageFromNative(state_event);

        Assert.assertTrue(mOnBroadcastStoppedCalled);
        Assert.assertFalse(mOnBroadcastStopFailedCalled);
    }

    @Test
    public void testCreateBroadcastNative() {
        int broadcastId = 243;
        byte[] code = {0x00, 0x01, 0x00, 0x02};

        mService.mBroadcastCallbacks.register(mCallbacks);

        BluetoothLeAudioContentMetadata.Builder meta_builder =
                new BluetoothLeAudioContentMetadata.Builder();
        meta_builder.setLanguage("deu");
        meta_builder.setProgramInfo("Subgroup broadcast info");
        BluetoothLeAudioContentMetadata meta = meta_builder.build();

        verifyBroadcastStarted(broadcastId, buildBroadcastSettingsFromMetadata(meta, code, 1));
    }

    @Test
    public void testCreateBroadcastNativeMultiGroups() {
        int broadcastId = 243;
        byte[] code = {0x00, 0x01, 0x00, 0x02};

        mService.mBroadcastCallbacks.register(mCallbacks);

        BluetoothLeAudioContentMetadata.Builder meta_builder =
                new BluetoothLeAudioContentMetadata.Builder();
        meta_builder.setLanguage("deu");
        meta_builder.setProgramInfo("Subgroup broadcast info");
        BluetoothLeAudioContentMetadata meta = meta_builder.build();

        verifyBroadcastStarted(broadcastId, buildBroadcastSettingsFromMetadata(meta, code, 3));
    }

    @Test
    public void testCreateBroadcastNativeFailed() {
        int broadcastId = 243;
        byte[] code = {0x00, 0x01, 0x00, 0x02};

        mService.mBroadcastCallbacks.register(mCallbacks);

        BluetoothLeAudioContentMetadata.Builder meta_builder =
                new BluetoothLeAudioContentMetadata.Builder();
        meta_builder.setLanguage("deu");
        meta_builder.setProgramInfo("Public broadcast info");
        BluetoothLeAudioContentMetadata meta = meta_builder.build();
        BluetoothLeBroadcastSettings settings = buildBroadcastSettingsFromMetadata(meta, code, 1);
        mService.createBroadcast(settings);

        // Test data with only one subgroup
        int[] expectedQualityArray =
                {settings.getSubgroupSettings().get(0).getPreferredQuality()};
        byte[][] expectedDataArray =
                {settings.getSubgroupSettings().get(0).getContentMetadata().getRawMetadata()};

        verify(mLeAudioBroadcasterNativeInterface, times(1))
                .createBroadcast(
                        eq(true),
                        eq(TEST_BROADCAST_NAME),
                        eq(code),
                        eq(settings.getPublicBroadcastMetadata().getRawMetadata()),
                        eq(expectedQualityArray),
                        eq(expectedDataArray));

        LeAudioStackEvent create_event =
                new LeAudioStackEvent(LeAudioStackEvent.EVENT_TYPE_BROADCAST_CREATED);
        create_event.valueInt1 = broadcastId;
        create_event.valueBool1 = false;
        mService.messageFromNative(create_event);

        Assert.assertFalse(mOnBroadcastStartedCalled);
        Assert.assertTrue(mOnBroadcastStartFailedCalled);
    }

    @Test
    public void testCreateBroadcast_updateQualityToStandard() {
        byte[] code = {0x00, 0x01, 0x00, 0x02};
        int groupId = 1;
        prepareConnectedUnicastDevice(groupId);

        mService.mBroadcastCallbacks.register(mCallbacks);

        BluetoothLeAudioContentMetadata.Builder meta_builder =
                new BluetoothLeAudioContentMetadata.Builder();
        BluetoothLeAudioContentMetadata meta = meta_builder.build();
        BluetoothLeBroadcastSettings settings = buildBroadcastSettingsFromMetadata(meta, code, 1);

        when(mBassClientService.getConnectedDevices()).thenReturn(List.of(mDevice));
        // update selectable configs to be STANDARD quality
        injectGroupSelectableCodecConfigChanged(
                groupId, INPUT_SELECTABLE_CONFIG_STANDARD, OUTPUT_SELECTABLE_CONFIG_STANDARD);
        injectGroupCurrentCodecConfigChanged(groupId, LC3_16KHZ_CONFIG, LC3_48KHZ_CONFIG);

        mService.createBroadcast(settings);

        // Test data with only one subgroup
        // Verify quality is updated to standard per sinks capabilities
        int[] expectedQualityArray = {BluetoothLeBroadcastSubgroupSettings.QUALITY_STANDARD};
        byte[][] expectedDataArray = {
            settings.getSubgroupSettings().get(0).getContentMetadata().getRawMetadata()
        };

        verify(mLeAudioBroadcasterNativeInterface, times(1))
                .createBroadcast(
                        eq(true),
                        eq(TEST_BROADCAST_NAME),
                        eq(code),
                        eq(settings.getPublicBroadcastMetadata().getRawMetadata()),
                        eq(expectedQualityArray),
                        eq(expectedDataArray));
    }

    @Test
    public void testStartStopBroadcastNative() {
        int broadcastId = 243;
        byte[] code = {0x00, 0x01, 0x00, 0x02};

        mService.mBroadcastCallbacks.register(mCallbacks);

        BluetoothLeAudioContentMetadata.Builder meta_builder =
                new BluetoothLeAudioContentMetadata.Builder();
        meta_builder.setLanguage("deu");
        meta_builder.setProgramInfo("Subgroup broadcast info");
        BluetoothLeAudioContentMetadata meta = meta_builder.build();

        verifyBroadcastStarted(broadcastId, buildBroadcastSettingsFromMetadata(meta, code, 1));
        verifyBroadcastStopped(broadcastId);
    }

    @Test
    public void testBroadcastInvalidBroadcastIdRequest() {
        int broadcastId = 243;

        mService.mBroadcastCallbacks.register(mCallbacks);

        // Stop non-existing broadcast
        mService.stopBroadcast(broadcastId);
        Assert.assertFalse(mOnBroadcastStoppedCalled);
        Assert.assertTrue(mOnBroadcastStopFailedCalled);

        // Update metadata for non-existing broadcast
        BluetoothLeAudioContentMetadata.Builder meta_builder =
        new BluetoothLeAudioContentMetadata.Builder();
        meta_builder.setLanguage("eng");
        meta_builder.setProgramInfo("Public broadcast info");
        mService.updateBroadcast(broadcastId,
                buildBroadcastSettingsFromMetadata(meta_builder.build(), null, 1));
        Assert.assertFalse(mOnBroadcastUpdatedCalled);
        Assert.assertTrue(mOnBroadcastUpdateFailedCalled);
    }

    private BluetoothLeBroadcastSubgroup createBroadcastSubgroup() {
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

    private BluetoothLeBroadcastMetadata createBroadcastMetadata() {
        BluetoothDevice testDevice =
        mAdapter.getRemoteLeDevice(TEST_MAC_ADDRESS, BluetoothDevice.ADDRESS_TYPE_RANDOM);

        BluetoothLeBroadcastMetadata.Builder builder = new BluetoothLeBroadcastMetadata.Builder()
                        .setEncrypted(false)
                        .setSourceDevice(testDevice, BluetoothDevice.ADDRESS_TYPE_RANDOM)
                        .setSourceAdvertisingSid(TEST_ADVERTISER_SID)
                        .setBroadcastId(TEST_BROADCAST_ID)
                        .setBroadcastCode(null)
                        .setPaSyncInterval(TEST_PA_SYNC_INTERVAL)
                        .setPresentationDelayMicros(TEST_PRESENTATION_DELAY_MS);
        // builder expect at least one subgroup
        builder.addSubgroup(createBroadcastSubgroup());
        return builder.build();
    }

    @Test
    public void testGetAllBroadcastMetadata() {
        int broadcastId = 243;
        byte[] code = {0x00, 0x01, 0x00, 0x02};

        BluetoothLeAudioContentMetadata.Builder meta_builder =
                new BluetoothLeAudioContentMetadata.Builder();
        meta_builder.setLanguage("ENG");
        meta_builder.setProgramInfo("Public broadcast info");
        BluetoothLeAudioContentMetadata meta = meta_builder.build();
        mService.createBroadcast(buildBroadcastSettingsFromMetadata(meta, code, 1));

        LeAudioStackEvent create_event =
                new LeAudioStackEvent(LeAudioStackEvent.EVENT_TYPE_BROADCAST_CREATED);
        create_event.valueInt1 = broadcastId;
        create_event.valueBool1 = true;
        mService.messageFromNative(create_event);

        // Inject metadata stack event and verify if getter API works as expected
        LeAudioStackEvent state_event =
                new LeAudioStackEvent(LeAudioStackEvent.EVENT_TYPE_BROADCAST_METADATA_CHANGED);
        state_event.valueInt1 = broadcastId;
        state_event.broadcastMetadata = createBroadcastMetadata();
        mService.messageFromNative(state_event);

        List<BluetoothLeBroadcastMetadata> meta_list = mService.getAllBroadcastMetadata();
        Assert.assertNotNull(meta_list);
        Assert.assertNotEquals(meta_list.size(), 0);
        Assert.assertEquals(meta_list.get(0), state_event.broadcastMetadata);
    }

    @Test
    public void testIsBroadcastActive() {
        int broadcastId = 243;
        byte[] code = {0x00, 0x01, 0x00, 0x02};

        BluetoothLeAudioContentMetadata.Builder meta_builder =
                new BluetoothLeAudioContentMetadata.Builder();
        meta_builder.setLanguage("ENG");
        meta_builder.setProgramInfo("Public broadcast info");
        BluetoothLeAudioContentMetadata meta = meta_builder.build();
        mService.createBroadcast(buildBroadcastSettingsFromMetadata(meta, code, 1));

        LeAudioStackEvent create_event =
                new LeAudioStackEvent(LeAudioStackEvent.EVENT_TYPE_BROADCAST_CREATED);
        create_event.valueInt1 = broadcastId;
        create_event.valueBool1 = true;
        mService.messageFromNative(create_event);

        // Inject metadata stack event and verify if getter API works as expected
        LeAudioStackEvent state_event =
                new LeAudioStackEvent(LeAudioStackEvent.EVENT_TYPE_BROADCAST_METADATA_CHANGED);
        state_event.valueInt1 = broadcastId;
        state_event.broadcastMetadata = createBroadcastMetadata();
        mService.messageFromNative(state_event);

        // Verify if broadcast is active
        Assert.assertTrue(mService.isBroadcastActive());

        mService.stopBroadcast(broadcastId);
        verify(mLeAudioBroadcasterNativeInterface, times(1)).stopBroadcast(eq(broadcastId));

        state_event = new LeAudioStackEvent(LeAudioStackEvent.EVENT_TYPE_BROADCAST_STATE);
        state_event.valueInt1 = broadcastId;
        state_event.valueInt2 = LeAudioStackEvent.BROADCAST_STATE_STOPPED;
        mService.messageFromNative(state_event);

        verify(mLeAudioBroadcasterNativeInterface, times(1)).destroyBroadcast(eq(broadcastId));

        state_event = new LeAudioStackEvent(LeAudioStackEvent.EVENT_TYPE_BROADCAST_DESTROYED);
        state_event.valueInt1 = broadcastId;
        mService.messageFromNative(state_event);

        // Verify if broadcast is not active
        Assert.assertFalse(mService.isBroadcastActive());
    }

    private void verifyConnectionStateIntent(
            int timeoutMs, BluetoothDevice device, int newState, int prevState) {
        Intent intent = TestUtils.waitForIntent(timeoutMs, mIntentQueue);
        Assert.assertNotNull(intent);
        Assert.assertEquals(
                BluetoothLeAudio.ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED, intent.getAction());
        Assert.assertEquals(
                (BluetoothDevice) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE), device);
        Assert.assertEquals(intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1), newState);
        Assert.assertEquals(
                intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, -1), prevState);

        if (newState == BluetoothProfile.STATE_CONNECTED) {
            // ActiveDeviceManager calls deviceConnected when connected.
            mService.deviceConnected(device);
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            // ActiveDeviceManager calls deviceDisconnected when connected.
            mService.deviceDisconnected(device, false);
        }
    }

    private void prepareConnectedUnicastDevice(int groupId) {
        int direction = 3;
        int snkAudioLocation = 3;
        int srcAudioLocation = 4;
        int availableContexts = 5;

        /* Initialize native */
        LeAudioStackEvent stackEvent =
        new LeAudioStackEvent(LeAudioStackEvent.EVENT_TYPE_NATIVE_INITIALIZED);
        mService.messageFromNative(stackEvent);
        Assert.assertTrue(mService.mLeAudioNativeIsInitialized);

        /* Prepare active group to cause pending broadcast */
        doReturn(BluetoothDevice.BOND_BONDED)
                .when(mAdapterService)
                .getBondState(any(BluetoothDevice.class));
        doReturn(true).when(mLeAudioNativeInterface).connectLeAudio(any(BluetoothDevice.class));
        when(mDatabaseManager.getProfileConnectionPolicy(mDevice, BluetoothProfile.LE_AUDIO))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        doReturn(new ParcelUuid[] {BluetoothUuid.LE_AUDIO})
                .when(mAdapterService)
                .getRemoteUuids(any(BluetoothDevice.class));
        Assert.assertTrue(mService.connect(mDevice));

        // Verify the connection state broadcast, and that we are in Connected state
        verifyConnectionStateIntent(
                TIMEOUT_MS,
                mDevice,
                BluetoothProfile.STATE_CONNECTING,
                BluetoothProfile.STATE_DISCONNECTED);
        Assert.assertEquals(
                BluetoothProfile.STATE_CONNECTING, mService.getConnectionState(mDevice));

        LeAudioStackEvent create_event =
                new LeAudioStackEvent(LeAudioStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        create_event.device = mDevice;
        create_event.valueInt1 = LeAudioStackEvent.CONNECTION_STATE_CONNECTED;
        mService.messageFromNative(create_event);

        verifyConnectionStateIntent(
                TIMEOUT_MS,
                mDevice,
                BluetoothProfile.STATE_CONNECTED,
                BluetoothProfile.STATE_CONNECTING);
        Assert.assertEquals(BluetoothProfile.STATE_CONNECTED, mService.getConnectionState(mDevice));

        create_event =
                new LeAudioStackEvent(LeAudioStackEvent.EVENT_TYPE_GROUP_NODE_STATUS_CHANGED);
        create_event.device = mDevice;
        create_event.valueInt1 = groupId;
        create_event.valueInt2 = LeAudioStackEvent.GROUP_NODE_ADDED;
        mService.messageFromNative(create_event);

        create_event = new LeAudioStackEvent(LeAudioStackEvent.EVENT_TYPE_AUDIO_CONF_CHANGED);
        create_event.device = mDevice;
        create_event.valueInt1 = direction;
        create_event.valueInt2 = groupId;
        create_event.valueInt3 = snkAudioLocation;
        create_event.valueInt4 = srcAudioLocation;
        create_event.valueInt5 = availableContexts;
        mService.messageFromNative(create_event);

        // Set default codec config to HIGH quality
        injectGroupSelectableCodecConfigChanged(
                groupId, INPUT_SELECTABLE_CONFIG_HIGH, OUTPUT_SELECTABLE_CONFIG_HIGH);
        injectGroupCurrentCodecConfigChanged(groupId, LC3_16KHZ_CONFIG, LC3_48KHZ_CONFIG);
    }

    @Test
    public void testCreatePendingBroadcast() {
        int groupId = 1;
        byte[] code = {0x00, 0x01, 0x00, 0x02};

        mSetFlagsRule.enableFlags(Flags.FLAG_AUDIO_ROUTING_CENTRALIZATION);

        prepareConnectedUnicastDevice(groupId);

        LeAudioStackEvent create_event =
                new LeAudioStackEvent(LeAudioStackEvent.EVENT_TYPE_GROUP_STATUS_CHANGED);
        create_event.valueInt1 = groupId;
        create_event.valueInt2 = LeAudioStackEvent.GROUP_STATUS_ACTIVE;
        mService.messageFromNative(create_event);

        /* Prepare create broadcast */
        BluetoothLeAudioContentMetadata.Builder meta_builder =
                new BluetoothLeAudioContentMetadata.Builder();
        meta_builder.setLanguage("ENG");
        meta_builder.setProgramInfo("Public broadcast info");
        BluetoothLeAudioContentMetadata meta = meta_builder.build();

        BluetoothLeBroadcastSettings settings = buildBroadcastSettingsFromMetadata(meta, code, 1);
        mService.createBroadcast(settings);

        /* Active group should become inactive */
        int activeGroup = mService.getActiveGroupId();
        Assert.assertEquals(activeGroup, LE_AUDIO_GROUP_ID_INVALID);

        /* Imitate group inactivity to cause create broadcast */
        create_event = new LeAudioStackEvent(LeAudioStackEvent.EVENT_TYPE_GROUP_STATUS_CHANGED);
        create_event.valueInt1 = groupId;
        create_event.valueInt2 = LeAudioStackEvent.GROUP_STATUS_INACTIVE;
        mService.messageFromNative(create_event);

        List<BluetoothLeBroadcastSubgroupSettings> settingsList = settings.getSubgroupSettings();

        int[] expectedQualityArray =
                settingsList.stream().mapToInt(setting -> setting.getPreferredQuality()).toArray();
        byte[][] expectedDataArray =
                settingsList.stream()
                        .map(setting -> setting.getContentMetadata().getRawMetadata())
                        .toArray(byte[][]::new);

        verify(mLeAudioBroadcasterNativeInterface, times(1))
                .createBroadcast(
                        eq(true),
                        eq(TEST_BROADCAST_NAME),
                        eq(settings.getBroadcastCode()),
                        eq(settings.getPublicBroadcastMetadata().getRawMetadata()),
                        eq(expectedQualityArray),
                        eq(expectedDataArray));

        activeGroup = mService.getActiveGroupId();
        Assert.assertEquals(-1, activeGroup);
    }

    @Test
    public void testCreateBroadcastMoreThanMaxFailed() {
        int broadcastId = 243;
        byte[] code = {0x00, 0x01, 0x00, 0x02};

        mService.mBroadcastCallbacks.register(mCallbacks);

        BluetoothLeAudioContentMetadata.Builder meta_builder =
                new BluetoothLeAudioContentMetadata.Builder();
        meta_builder.setLanguage("deu");
        meta_builder.setProgramInfo("Subgroup broadcast info");
        BluetoothLeAudioContentMetadata meta = meta_builder.build();
        BluetoothLeBroadcastSettings settings = buildBroadcastSettingsFromMetadata(meta, code, 1);

        verifyBroadcastStarted(broadcastId, settings);
        mOnBroadcastStartedCalled = false;
        mOnBroadcastStartFailedCalled = false;

        // verify creating another broadcast will fail
        mService.createBroadcast(settings);

        Assert.assertFalse(mOnBroadcastStartedCalled);
        Assert.assertTrue(mOnBroadcastStartFailedCalled);
        Assert.assertEquals(
                BluetoothStatusCodes.ERROR_LOCAL_NOT_ENOUGH_RESOURCES,
                mOnBroadcastStartFailedReason);
    }

    private void prepareHandoverStreamingBroadcast(int groupId, int broadcastId, byte[] code) {
        mSetFlagsRule.enableFlags(Flags.FLAG_AUDIO_ROUTING_CENTRALIZATION);
        mSetFlagsRule.enableFlags(Flags.FLAG_LEAUDIO_BROADCAST_AUDIO_HANDOVER_POLICIES);

        mService.mBroadcastCallbacks.register(mCallbacks);

        prepareConnectedUnicastDevice(groupId);

        LeAudioStackEvent create_event =
                new LeAudioStackEvent(LeAudioStackEvent.EVENT_TYPE_GROUP_STATUS_CHANGED);
        create_event.valueInt1 = groupId;
        create_event.valueInt2 = LeAudioStackEvent.GROUP_STATUS_ACTIVE;
        mService.messageFromNative(create_event);

        /* Verify Unicast input and output devices changed from null to mDevice */
        verify(mAudioManager, times(2))
                .handleBluetoothActiveDeviceChanged(
                        eq(mDevice), eq(null), any(BluetoothProfileConnectionInfo.class));
        Mockito.clearInvocations(mAudioManager);

        mService.notifyActiveDeviceChanged(mDevice);

        /* Prepare create broadcast */
        BluetoothLeAudioContentMetadata.Builder meta_builder =
                new BluetoothLeAudioContentMetadata.Builder();
        meta_builder.setLanguage("ENG");
        meta_builder.setProgramInfo("Public broadcast info");
        BluetoothLeAudioContentMetadata meta = meta_builder.build();

        BluetoothLeBroadcastSettings settings = buildBroadcastSettingsFromMetadata(meta, code, 1);
        mService.createBroadcast(settings);

        verify(mAudioManager, times(1))
                .handleBluetoothActiveDeviceChanged(
                        eq(mBroadcastDevice), eq(null), any(BluetoothProfileConnectionInfo.class));

        /* Active group should become inactive */
        int activeGroup = mService.getActiveGroupId();
        Assert.assertEquals(activeGroup, LE_AUDIO_GROUP_ID_INVALID);

        /* Imitate group inactivity to cause create broadcast */
        create_event = new LeAudioStackEvent(LeAudioStackEvent.EVENT_TYPE_GROUP_STATUS_CHANGED);
        create_event.valueInt1 = groupId;
        create_event.valueInt2 = LeAudioStackEvent.GROUP_STATUS_INACTIVE;
        mService.messageFromNative(create_event);

        /* Only one Unicast device should become inactive due to Sink monitor mode */
        verify(mAudioManager, times(1))
                .handleBluetoothActiveDeviceChanged(
                        eq(null), eq(mDevice), any(BluetoothProfileConnectionInfo.class));
        Mockito.clearInvocations(mAudioManager);
        List<BluetoothLeBroadcastSubgroupSettings> settingsList = settings.getSubgroupSettings();

        int[] expectedQualityArray =
                settingsList.stream().mapToInt(setting -> setting.getPreferredQuality()).toArray();
        byte[][] expectedDataArray =
                settingsList.stream()
                        .map(setting -> setting.getContentMetadata().getRawMetadata())
                        .toArray(byte[][]::new);

        verify(mLeAudioBroadcasterNativeInterface, times(1))
                .createBroadcast(
                        eq(true),
                        eq(TEST_BROADCAST_NAME),
                        eq(settings.getBroadcastCode()),
                        eq(settings.getPublicBroadcastMetadata().getRawMetadata()),
                        eq(expectedQualityArray),
                        eq(expectedDataArray));
        verify(mLeAudioNativeInterface, times(1))
                .setUnicastMonitorMode(eq(LeAudioStackEvent.DIRECTION_SINK), eq(true));

        activeGroup = mService.getActiveGroupId();
        Assert.assertEquals(LE_AUDIO_GROUP_ID_INVALID, activeGroup);

        /* Check if broadcast is started automatically when created */
        create_event = new LeAudioStackEvent(LeAudioStackEvent.EVENT_TYPE_BROADCAST_CREATED);
        create_event.valueInt1 = broadcastId;
        create_event.valueBool1 = true;
        mService.messageFromNative(create_event);

        /* Verify if broadcast is auto-started on start */
        verify(mLeAudioBroadcasterNativeInterface, times(1)).startBroadcast(eq(broadcastId));

        /* Switch to active streaming */
        create_event = new LeAudioStackEvent(LeAudioStackEvent.EVENT_TYPE_BROADCAST_STATE);
        create_event.device = mBroadcastDevice;
        create_event.valueInt1 = broadcastId;
        create_event.valueInt2 = LeAudioStackEvent.BROADCAST_STATE_STREAMING;
        mService.messageFromNative(create_event);
    }

    @Test
    public void testInCallDrivenBroadcastSwitch() {
        int groupId = 1;
        int broadcastId = 243;
        byte[] code = {0x00, 0x01, 0x00, 0x02};

        prepareHandoverStreamingBroadcast(groupId, broadcastId, code);

        /* Imitate setting device in call */
        mService.setInCall(true);

        /* Check if broadcast is paused by InCall handling */
        verify(mLeAudioBroadcasterNativeInterface, times(1)).pauseBroadcast(eq(broadcastId));

        LeAudioStackEvent state_event =
                new LeAudioStackEvent(LeAudioStackEvent.EVENT_TYPE_BROADCAST_STATE);
        state_event.valueInt1 = broadcastId;
        state_event.valueInt2 = LeAudioStackEvent.BROADCAST_STATE_PAUSED;
        mService.messageFromNative(state_event);

        verify(mLeAudioNativeInterface, times(1)).setInCall(eq(true));

        LeAudioStackEvent create_event =
                new LeAudioStackEvent(LeAudioStackEvent.EVENT_TYPE_GROUP_STATUS_CHANGED);
        create_event.valueInt1 = groupId;
        create_event.valueInt2 = LeAudioStackEvent.GROUP_STATUS_ACTIVE;
        mService.messageFromNative(create_event);

        verify(mAudioManager, times(1))
                .handleBluetoothActiveDeviceChanged(
                        eq(mDevice), eq(null), any(BluetoothProfileConnectionInfo.class));
        verify(mAudioManager, times(1))
                .handleBluetoothActiveDeviceChanged(
                        eq(null), eq(mBroadcastDevice), any(BluetoothProfileConnectionInfo.class));

        /* Active group should become the one that was active before broadcasting */
        int activeGroup = mService.getActiveGroupId();
        Assert.assertEquals(activeGroup, groupId);

        /* Imitate setting device not in call */
        mService.setInCall(false);

        verify(mLeAudioNativeInterface, times(2)).groupSetActive(eq(LE_AUDIO_GROUP_ID_INVALID));

        /* Imitate group inactivity to cause start broadcast */
        create_event = new LeAudioStackEvent(LeAudioStackEvent.EVENT_TYPE_GROUP_STATUS_CHANGED);
        create_event.valueInt1 = groupId;
        create_event.valueInt2 = LeAudioStackEvent.GROUP_STATUS_INACTIVE;
        mService.messageFromNative(create_event);

        /* Only one Unicast device should become inactive due to Sink monitor mode */
        verify(mAudioManager, times(1))
                .handleBluetoothActiveDeviceChanged(
                        eq(null), eq(mDevice), any(BluetoothProfileConnectionInfo.class));
        verify(mAudioManager, times(1))
                .handleBluetoothActiveDeviceChanged(
                        eq(mBroadcastDevice), eq(null), any(BluetoothProfileConnectionInfo.class));

        /* Verify if broadcast is auto-started on start */
        verify(mLeAudioBroadcasterNativeInterface, times(2)).startBroadcast(eq(broadcastId));
    }

    @Test
    public void testBroadcastResumeUnicastGroupChangeRequestDriven() {
        int groupId = 1;
        int broadcastId = 243;
        byte[] code = {0x00, 0x01, 0x00, 0x02};

        prepareHandoverStreamingBroadcast(groupId, broadcastId, code);

        /* Verify if broadcast is auto-started on start */
        verify(mLeAudioBroadcasterNativeInterface, times(1)).startBroadcast(eq(broadcastId));

        /* Imitate group change request by Bluetooth Sink HAL resume request */
        LeAudioStackEvent create_event =
                new LeAudioStackEvent(LeAudioStackEvent.EVENT_TYPE_UNICAST_MONITOR_MODE_STATUS);
        create_event.valueInt1 = LeAudioStackEvent.DIRECTION_SINK;
        create_event.valueInt2 = LeAudioStackEvent.STATUS_LOCAL_STREAM_REQUESTED;
        mService.messageFromNative(create_event);

        /* Check if broadcast is paused triggered by group change request */
        verify(mLeAudioBroadcasterNativeInterface, times(1)).pauseBroadcast(eq(broadcastId));

        LeAudioStackEvent state_event =
                new LeAudioStackEvent(LeAudioStackEvent.EVENT_TYPE_BROADCAST_STATE);
        state_event.valueInt1 = broadcastId;
        state_event.valueInt2 = LeAudioStackEvent.BROADCAST_STATE_PAUSED;
        mService.messageFromNative(state_event);

        create_event = new LeAudioStackEvent(LeAudioStackEvent.EVENT_TYPE_GROUP_STATUS_CHANGED);
        create_event.valueInt1 = groupId;
        create_event.valueInt2 = LeAudioStackEvent.GROUP_STATUS_ACTIVE;
        mService.messageFromNative(create_event);

        verify(mAudioManager, times(1))
                .handleBluetoothActiveDeviceChanged(
                        eq(mDevice), eq(null), any(BluetoothProfileConnectionInfo.class));
        verify(mAudioManager, times(1))
                .handleBluetoothActiveDeviceChanged(
                        eq(null), eq(mBroadcastDevice), any(BluetoothProfileConnectionInfo.class));

        /* Active group should become the one that was active before broadcasting */
        int activeGroup = mService.getActiveGroupId();
        Assert.assertEquals(activeGroup, groupId);

        /* Imitate group change request by Bluetooth Sink HAL suspend request */
        create_event = new LeAudioStackEvent(LeAudioStackEvent.EVENT_TYPE_UNICAST_MONITOR_MODE_STATUS);
        create_event.valueInt1 = LeAudioStackEvent.DIRECTION_SINK;
        create_event.valueInt2 = LeAudioStackEvent.STATUS_LOCAL_STREAM_SUSPENDED;
        mService.messageFromNative(create_event);

        verify(mLeAudioNativeInterface, times(2)).groupSetActive(eq(LE_AUDIO_GROUP_ID_INVALID));

        /* Imitate group inactivity to cause start broadcast */
        create_event = new LeAudioStackEvent(LeAudioStackEvent.EVENT_TYPE_GROUP_STATUS_CHANGED);
        create_event.valueInt1 = groupId;
        create_event.valueInt2 = LeAudioStackEvent.GROUP_STATUS_INACTIVE;
        mService.messageFromNative(create_event);

        /* Only one Unicast device should become inactive due to Sink monitor mode */
        verify(mAudioManager, times(1))
                .handleBluetoothActiveDeviceChanged(
                        eq(null), eq(mDevice), any(BluetoothProfileConnectionInfo.class));
        verify(mAudioManager, times(1))
                .handleBluetoothActiveDeviceChanged(
                        eq(mBroadcastDevice), eq(null), any(BluetoothProfileConnectionInfo.class));

        /* Verify if broadcast is auto-started on start */
        verify(mLeAudioBroadcasterNativeInterface, times(2)).startBroadcast(eq(broadcastId));
    }

    private class LeAudioIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                mIntentQueue.put(intent);
            } catch (InterruptedException e) {
                Assert.fail("Cannot add Intent to the queue: " + e.getMessage());
            }
        }
    }

    private BluetoothLeBroadcastSettings buildBroadcastSettingsFromMetadata(
            BluetoothLeAudioContentMetadata contentMetadata,
            @Nullable byte[] broadcastCode,
            int numOfGroups) {
        BluetoothLeAudioContentMetadata.Builder publicMetaBuilder =
                new BluetoothLeAudioContentMetadata.Builder();
        publicMetaBuilder.setProgramInfo("Public broadcast info");

        BluetoothLeBroadcastSubgroupSettings.Builder subgroupBuilder =
                new BluetoothLeBroadcastSubgroupSettings.Builder()
                        .setContentMetadata(contentMetadata)
                        .setPreferredQuality(BluetoothLeBroadcastSubgroupSettings.QUALITY_HIGH);

        BluetoothLeBroadcastSettings.Builder builder = new BluetoothLeBroadcastSettings.Builder()
                        .setPublicBroadcast(true)
                        .setBroadcastName(TEST_BROADCAST_NAME)
                        .setBroadcastCode(broadcastCode)
                        .setPublicBroadcastMetadata(publicMetaBuilder.build());
        // builder expect at least one subgroup setting
        for (int i = 0; i < numOfGroups; i++) {
            // add subgroup settings with the same content
            builder.addSubgroupSettings(subgroupBuilder.build());
        }
        return builder.build();
    }

    private void injectGroupCurrentCodecConfigChanged(
            int groupId,
            BluetoothLeAudioCodecConfig inputCodecConfig,
            BluetoothLeAudioCodecConfig outputCodecConfig) {
        int eventType = LeAudioStackEvent.EVENT_TYPE_AUDIO_GROUP_CURRENT_CODEC_CONFIG_CHANGED;

        LeAudioStackEvent groupCodecConfigChangedEvent = new LeAudioStackEvent(eventType);
        groupCodecConfigChangedEvent.valueInt1 = groupId;
        groupCodecConfigChangedEvent.valueCodec1 = inputCodecConfig;
        groupCodecConfigChangedEvent.valueCodec2 = outputCodecConfig;
        mService.messageFromNative(groupCodecConfigChangedEvent);
    }

    private void injectGroupSelectableCodecConfigChanged(
            int groupId,
            List<BluetoothLeAudioCodecConfig> inputSelectableCodecConfig,
            List<BluetoothLeAudioCodecConfig> outputSelectableCodecConfig) {
        int eventType = LeAudioStackEvent.EVENT_TYPE_AUDIO_GROUP_SELECTABLE_CODEC_CONFIG_CHANGED;

        LeAudioStackEvent groupCodecConfigChangedEvent = new LeAudioStackEvent(eventType);
        groupCodecConfigChangedEvent.valueInt1 = groupId;
        groupCodecConfigChangedEvent.valueCodecList1 = inputSelectableCodecConfig;
        groupCodecConfigChangedEvent.valueCodecList2 = outputSelectableCodecConfig;
        mService.messageFromNative(groupCodecConfigChangedEvent);
    }
}
