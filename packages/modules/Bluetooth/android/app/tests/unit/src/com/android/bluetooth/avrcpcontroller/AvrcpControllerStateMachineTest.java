/*
 * Copyright 2019 The Android Open Source Project
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
package com.android.bluetooth.avrcpcontroller;

import static android.Manifest.permission.BLUETOOTH_CONNECT;

import static org.mockito.Mockito.*;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAvrcpController;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Looper;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.SparseArray;

import androidx.test.filters.FlakyTest;
import androidx.test.filters.MediumTest;
import androidx.test.rule.ServiceTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.R;
import com.android.bluetooth.TestUtils;
import com.android.bluetooth.a2dpsink.A2dpSinkNativeInterface;
import com.android.bluetooth.a2dpsink.A2dpSinkService;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.storage.DatabaseManager;

import org.hamcrest.core.IsInstanceOf;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class AvrcpControllerStateMachineTest {
    private static final int ASYNC_CALL_TIMEOUT_MILLIS = 100;
    private static final int CONNECT_TIMEOUT_TEST_MILLIS = 1000;
    private static final int KEY_DOWN = 0;
    private static final int KEY_UP = 1;

    private BluetoothAdapter mAdapter;

    @Rule public final ServiceTestRule mAvrcpServiceRule = new ServiceTestRule();
    @Rule public final ServiceTestRule mA2dpServiceRule = new ServiceTestRule();
    @Mock private AdapterService mA2dpAdapterService;
    @Mock private AdapterService mAvrcpAdapterService;
    @Mock private A2dpSinkService mA2dpSinkService;
    @Mock private DatabaseManager mDatabaseManager;
    @Mock private AudioManager mAudioManager;
    @Mock private Resources mMockResources;
    private ArgumentCaptor<Intent> mIntentArgument = ArgumentCaptor.forClass(Intent.class);
    @Mock private AvrcpControllerService mAvrcpControllerService;
    @Mock private AvrcpControllerNativeInterface mNativeInterface;
    @Mock private A2dpSinkNativeInterface mA2dpSinkNativeInterface;
    @Mock private AvrcpCoverArtManager mCoverArtManager;

    private byte[] mTestAddress = new byte[]{01, 01, 01, 01, 01, 01};
    private BluetoothDevice mTestDevice = null;
    private AvrcpControllerStateMachine mAvrcpStateMachine = null;

    @Before
    public void setUp() throws Exception {
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        Assert.assertNotNull(Looper.myLooper());

        MockitoAnnotations.initMocks(this);

        // Start a real A2dpSinkService so we can replace the static instance with our mock
        doReturn(mDatabaseManager).when(mA2dpAdapterService).getDatabase();
        TestUtils.setAdapterService(mA2dpAdapterService);
        A2dpSinkNativeInterface.setInstance(mA2dpSinkNativeInterface);
        A2dpSinkService.setA2dpSinkService(mA2dpSinkService);
        TestUtils.clearAdapterService(mA2dpAdapterService);

        // Start an AvrcpControllerService to get a real BluetoothMediaBrowserService up
        TestUtils.setAdapterService(mAvrcpAdapterService);
        AvrcpControllerNativeInterface.setInstance(mNativeInterface);

        // Mock an AvrcpControllerService to give to all state machines
        doReturn(BluetoothProfile.STATE_DISCONNECTED).when(mCoverArtManager).getState(any());
        doReturn(15).when(mAudioManager).getStreamMaxVolume(anyInt());
        doReturn(8).when(mAudioManager).getStreamVolume(anyInt());
        doReturn(true).when(mAudioManager).isVolumeFixed();
        when(mMockResources.getBoolean(R.bool.a2dp_sink_automatically_request_audio_focus))
                .thenReturn(true);
        doReturn(mMockResources).when(mAvrcpControllerService).getResources();
        doReturn(mAudioManager).when(mAvrcpControllerService)
                .getSystemService(Context.AUDIO_SERVICE);
        doReturn(Context.AUDIO_SERVICE).when(mAvrcpControllerService)
                .getSystemServiceName(AudioManager.class);
        doReturn(mCoverArtManager).when(mAvrcpControllerService).getCoverArtManager();
        mAvrcpControllerService.sBrowseTree = new BrowseTree(null);

        // Ensure our MediaBrowserService starts with a blank state
        BluetoothMediaBrowserService.reset();

        // This line must be called to make sure relevant objects are initialized properly
        mAdapter = BluetoothAdapter.getDefaultAdapter();

        // Set up device and state machine under test
        mTestDevice = mAdapter.getRemoteDevice(mTestAddress);
        mAvrcpStateMachine = makeStateMachine(mTestDevice);
        setActiveDevice(mTestDevice);
    }

    @After
    public void tearDown() throws Exception {
        destroyStateMachine(mAvrcpStateMachine);
        TestUtils.clearAdapterService(mAvrcpAdapterService);
        A2dpSinkNativeInterface.setInstance(null);
        AvrcpControllerNativeInterface.setInstance(null);
    }

    /**
     * Create a state machine to test
     */
    private AvrcpControllerStateMachine makeStateMachine(BluetoothDevice device) {
        AvrcpControllerStateMachine sm =
                new AvrcpControllerStateMachine(device, mAvrcpControllerService, mNativeInterface);
        sm.start();
        return sm;
    }

    /**
     * Destroy a state machine you created to test
     */
    private void destroyStateMachine(AvrcpControllerStateMachine sm) {
        if (sm == null || sm.getState() == BluetoothProfile.STATE_DISCONNECTED) return;

        sm.disconnect();
        TestUtils.waitForLooperToBeIdle(sm.getHandler().getLooper());

        // is disconnected
        Assert.assertEquals(sm.getState(), BluetoothProfile.STATE_DISCONNECTED);

        // told mAvrcpControllerService to remove it
        verify(mAvrcpControllerService).removeStateMachine(eq(sm));
    }

    /**
     * Set up which device the AvrcpControllerService will report as active
     */
    private void setActiveDevice(BluetoothDevice device) {
        doReturn(device).when(mAvrcpControllerService).getActiveDevice();
        if (mTestDevice.equals(device)) {
            mAvrcpStateMachine.setDeviceState(AvrcpControllerService.DEVICE_STATE_ACTIVE);
        } else {
            mAvrcpStateMachine.setDeviceState(AvrcpControllerService.DEVICE_STATE_INACTIVE);
            BluetoothMediaBrowserService.reset();
        }
    }

    /**
     * Send an audio focus changed event to the state machine under test
     */
    private void sendAudioFocusUpdate(int state) {
        when(mA2dpSinkService.getFocusState()).thenReturn(state);
        mAvrcpStateMachine.sendMessage(
                AvrcpControllerStateMachine.AUDIO_FOCUS_STATE_CHANGE, state);
    }

    /**
     * Setup Connected State for a given state machine
     *
     * @return number of times mAvrcpControllerService.sendBroadcastAsUser() has been invoked
     */
    private int setUpConnectedState(boolean control, boolean browsing) {

        Assert.assertThat(mAvrcpStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(AvrcpControllerStateMachine.Disconnected.class));

        mAvrcpStateMachine.connect(StackEvent.connectionStateChanged(control, browsing));

        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());
        verify(mAvrcpControllerService, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(2)).sendBroadcast(
                mIntentArgument.capture(), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        Assert.assertThat(mAvrcpStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(AvrcpControllerStateMachine.Connected.class));
        Assert.assertEquals(mAvrcpStateMachine.getState(), BluetoothProfile.STATE_CONNECTED);

        return BluetoothProfile.STATE_CONNECTED;
    }

    private AvrcpItem makeTrack(String title, String artist, String album, long trackNum,
            long totalTracks, String genre, long duration, String imageHandle) {
        AvrcpItem.Builder builder = new AvrcpItem.Builder();
        builder.setItemType(AvrcpItem.TYPE_MEDIA);
        builder.setType(AvrcpItem.MEDIA_AUDIO);
        builder.setDevice(mTestDevice);
        builder.setPlayable(true);
        builder.setUid(0);
        builder.setUuid("AVRCP-ITEM-TEST-UUID");

        builder.setTitle(title);
        builder.setArtistName(artist);
        builder.setAlbumName(album);
        builder.setTrackNumber(trackNum);
        builder.setTotalNumberOfTracks(totalTracks);
        builder.setGenre(genre);
        builder.setPlayingTime(duration);
        if (imageHandle != null) {
            builder.setCoverArtHandle(imageHandle);
        }

        return builder.build();
    }

    private AvrcpPlayer makePlayer(BluetoothDevice device, int playerId, String playerName,
            int playerType, byte[] playerFeatures, int playStatus) {
        AvrcpPlayer.Builder apb = new AvrcpPlayer.Builder();
        apb.setDevice(device);
        apb.setPlayerId(playerId);
        apb.setName(playerName);
        apb.setPlayerType(playerType);
        apb.setSupportedFeatures(playerFeatures);
        apb.setPlayStatus(playStatus);
        return apb.build();
    }

    /**
     * Send a message to the state machine that the track has changed. Must be connected to
     * do this.
     */
    private void setCurrentTrack(AvrcpItem track) {
        mAvrcpStateMachine.sendMessage(AvrcpControllerStateMachine.MESSAGE_PROCESS_TRACK_CHANGED,
                track);
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());
        Assert.assertEquals(mAvrcpStateMachine.getCurrentTrack(), track);
    }

    /**
     * Set the current play status (Play, Pause, etc.) of the device
     */
    private void setPlaybackState(int state) {
        mAvrcpStateMachine.sendMessage(
                AvrcpControllerStateMachine.MESSAGE_PROCESS_PLAY_STATUS_CHANGED, state);
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());
    }

    /**
     * Set the current playback position of the device
     */
    private void setPlaybackPosition(int position, int duration) {
        mAvrcpStateMachine.sendMessage(
                AvrcpControllerStateMachine.MESSAGE_PROCESS_PLAY_POS_CHANGED, duration, position);
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());
    }

    /**
     * Make an AvrcpItem suitable for being included in the Now Playing list for the test device
     */
    private AvrcpItem makeNowPlayingItem(long uid, String name) {
        AvrcpItem.Builder aib = new AvrcpItem.Builder();
        aib.setDevice(mTestDevice);
        aib.setItemType(AvrcpItem.TYPE_MEDIA);
        aib.setType(AvrcpItem.MEDIA_AUDIO);
        aib.setTitle(name);
        aib.setUid(uid);
        aib.setUuid(UUID.randomUUID().toString());
        aib.setPlayable(true);
        return aib.build();
    }

    /**
     * Get the current Now Playing list for the test device
     */
    private List<AvrcpItem> getNowPlayingList() {
        BrowseTree.BrowseNode nowPlaying = mAvrcpStateMachine.findNode("NOW_PLAYING");
        List<AvrcpItem> nowPlayingList = new ArrayList<AvrcpItem>();
        for (BrowseTree.BrowseNode child : nowPlaying.getChildren()) {
            nowPlayingList.add(child.mItem);
        }
        return nowPlayingList;
    }

    /**
     * Set the current Now Playing list for the test device
     */
    private void setNowPlayingList(List<AvrcpItem> nowPlayingList) {
        BrowseTree.BrowseNode nowPlaying = mAvrcpStateMachine.findNode("NOW_PLAYING");
        mAvrcpStateMachine.requestContents(nowPlaying);
        mAvrcpStateMachine.sendMessage(
                AvrcpControllerStateMachine.MESSAGE_PROCESS_GET_FOLDER_ITEMS, nowPlayingList);
        mAvrcpStateMachine.sendMessage(
                AvrcpControllerStateMachine.MESSAGE_PROCESS_GET_FOLDER_ITEMS_OUT_OF_RANGE);

        // Wait for the now playing list to be propagated
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());

        // Make sure its set by re grabbing the node and checking its contents are cached
        nowPlaying = mAvrcpStateMachine.findNode("NOW_PLAYING");
        Assert.assertTrue(nowPlaying.isCached());
        assertNowPlayingList(nowPlayingList);
    }

    private String avrcpItemListToString(List<AvrcpItem> items) {
        StringBuilder s = new StringBuilder();
        s.append("[");
        if (items != null) {
            for (int i = 0; i < items.size(); i++) {
                AvrcpItem item = items.get(i);
                s.append((item != null ? Long.toString(item.getUid()) : "null"));
                if (i != items.size() - 1) s.append(", ");
            }
        }
        s.append("]");
        return s.toString();
    }

    /**
     * Assert that the Now Playing list is a particular value
     */
    private void assertNowPlayingList(List<AvrcpItem> expected) {
        List<AvrcpItem> current = getNowPlayingList();
        String err = "Now playing list incorrect, expected="
                + avrcpItemListToString(expected) + ", actual=" + avrcpItemListToString(current);
        Assert.assertEquals(err, expected.size(), current.size());
        for (int i = 0; i < expected.size(); i++) {
            Assert.assertEquals(err, expected.get(i), current.get(i));
        }
    }

    /**
     * Test to confirm that the state machine is capable of cycling through the 4
     * connection states, and that upon completion, it cleans up afterwards.
     */
    @Test
    public void testDisconnect() {
        int numBroadcastsSent = setUpConnectedState(true, true);
        testDisconnectInternal(numBroadcastsSent);
    }

    /**
     * Test to confirm that the state machine is capable of cycling through the 4
     * connection states with no crashes, even if the {@link AvrcpControllerService} is stopped and
     * the {@code sBrowseTree} is null. This could happen if BT is disabled as the profile is being
     * disconnected.
     */
    @Test
    public void testDisconnectWithNullBrowseTree() {
        int numBroadcastsSent = setUpConnectedState(true, true);
        mAvrcpControllerService.stop();

        testDisconnectInternal(numBroadcastsSent);
    }

    private void testDisconnectInternal(int numBroadcastsSent) {
        mAvrcpStateMachine.disconnect();
        numBroadcastsSent += 2;
        verify(mAvrcpControllerService,
                timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(numBroadcastsSent)).sendBroadcast(
                mIntentArgument.capture(), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        Assert.assertEquals(mTestDevice, mIntentArgument.getValue().getParcelableExtra(
                BluetoothDevice.EXTRA_DEVICE));
        Assert.assertEquals(BluetoothAvrcpController.ACTION_CONNECTION_STATE_CHANGED,
                mIntentArgument.getValue().getAction());
        Assert.assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mIntentArgument.getValue().getIntExtra(BluetoothProfile.EXTRA_STATE, -1));
        Assert.assertThat(mAvrcpStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(AvrcpControllerStateMachine.Disconnected.class));
        Assert.assertEquals(mAvrcpStateMachine.getState(), BluetoothProfile.STATE_DISCONNECTED);
        verify(mAvrcpControllerService).removeStateMachine(eq(mAvrcpStateMachine));
    }

    /**
     * Test to confirm that a control only device can be established (no browsing)
     */
    @Test
    public void testControlOnly() {
        int numBroadcastsSent = setUpConnectedState(true, false);
        MediaControllerCompat.TransportControls transportControls =
                BluetoothMediaBrowserService.getTransportControls();
        Assert.assertNotNull(transportControls);
        Assert.assertEquals(PlaybackStateCompat.STATE_NONE,
                BluetoothMediaBrowserService.getPlaybackState().getState());
        mAvrcpStateMachine.disconnect();
        numBroadcastsSent += 2;
        verify(mAvrcpControllerService,
                timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(numBroadcastsSent)).sendBroadcast(
                mIntentArgument.capture(), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        Assert.assertEquals(mTestDevice, mIntentArgument.getValue().getParcelableExtra(
                BluetoothDevice.EXTRA_DEVICE));
        Assert.assertEquals(BluetoothAvrcpController.ACTION_CONNECTION_STATE_CHANGED,
                mIntentArgument.getValue().getAction());
        Assert.assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mIntentArgument.getValue().getIntExtra(BluetoothProfile.EXTRA_STATE, -1));
        Assert.assertThat(mAvrcpStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(AvrcpControllerStateMachine.Disconnected.class));
        Assert.assertEquals(mAvrcpStateMachine.getState(), BluetoothProfile.STATE_DISCONNECTED);
        verify(mAvrcpControllerService).removeStateMachine(eq(mAvrcpStateMachine));
    }

    /**
     * Test to confirm that a browsing only device can be established (no control)
     */
    @Test
    @FlakyTest
    public void testBrowsingOnly() {
        Assert.assertEquals(0, mAvrcpControllerService.sBrowseTree.mRootNode.getChildrenCount());
        int numBroadcastsSent = setUpConnectedState(false, true);
        Assert.assertEquals(1, mAvrcpControllerService.sBrowseTree.mRootNode.getChildrenCount());
        Assert.assertEquals(PlaybackStateCompat.STATE_NONE,
                BluetoothMediaBrowserService.getPlaybackState().getState());
        mAvrcpStateMachine.disconnect();
        numBroadcastsSent += 2;
        verify(mAvrcpControllerService,
                timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(numBroadcastsSent)).sendBroadcast(
                mIntentArgument.capture(), eq(BLUETOOTH_CONNECT),
                any(Bundle.class));
        Assert.assertEquals(mTestDevice, mIntentArgument.getValue().getParcelableExtra(
                BluetoothDevice.EXTRA_DEVICE));
        Assert.assertEquals(BluetoothAvrcpController.ACTION_CONNECTION_STATE_CHANGED,
                mIntentArgument.getValue().getAction());
        Assert.assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mIntentArgument.getValue().getIntExtra(BluetoothProfile.EXTRA_STATE, -1));
        Assert.assertThat(mAvrcpStateMachine.getCurrentState(),
                IsInstanceOf.instanceOf(AvrcpControllerStateMachine.Disconnected.class));
        Assert.assertEquals(mAvrcpStateMachine.getState(), BluetoothProfile.STATE_DISCONNECTED);
        verify(mAvrcpControllerService).removeStateMachine(eq(mAvrcpStateMachine));
    }

    /**
     * Test to make sure the state machine is tracking the correct device
     */
    @Test
    public void testGetDevice() {
        Assert.assertEquals(mAvrcpStateMachine.getDevice(), mTestDevice);
    }

    /**
     * Test that dumpsys will generate information about connected devices
     */
    @Test
    public void testDump() {
        StringBuilder sb = new StringBuilder();
        mAvrcpStateMachine.dump(sb);
        Assert.assertNotNull(sb.toString());
    }

    /**
     * Test media browser play command
     */
    @Test
    public void testPlay() throws Exception {
        setUpConnectedState(true, true);
        MediaControllerCompat.TransportControls transportControls =
                BluetoothMediaBrowserService.getTransportControls();

        //Play
        transportControls.play();
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_PLAY),
                        eq(KEY_DOWN));
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_PLAY),
                        eq(KEY_UP));
    }

    /**
     * Test media browser pause command
     */
    @Test
    public void testPause() throws Exception {
        setUpConnectedState(true, true);
        MediaControllerCompat.TransportControls transportControls =
                BluetoothMediaBrowserService.getTransportControls();

        //Pause
        transportControls.pause();
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_PAUSE),
                        eq(KEY_DOWN));
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_PAUSE),
                        eq(KEY_UP));
    }

    /**
     * Test media browser stop command
     */
    @Test
    public void testStop() throws Exception {
        setUpConnectedState(true, true);
        MediaControllerCompat.TransportControls transportControls =
                BluetoothMediaBrowserService.getTransportControls();

        //Stop
        transportControls.stop();
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_STOP),
                        eq(KEY_DOWN));
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_STOP),
                        eq(KEY_UP));
    }

    /**
     * Test media browser next command
     */
    @Test
    public void testNext() throws Exception {
        setUpConnectedState(true, true);
        MediaControllerCompat.TransportControls transportControls =
                BluetoothMediaBrowserService.getTransportControls();

        //Next
        transportControls.skipToNext();
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_FORWARD),
                        eq(KEY_DOWN));
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_FORWARD),
                        eq(KEY_UP));
    }

    /**
     * Test media browser previous command
     */
    @Test
    public void testPrevious() throws Exception {
        setUpConnectedState(true, true);
        MediaControllerCompat.TransportControls transportControls =
                BluetoothMediaBrowserService.getTransportControls();

        //Previous
        transportControls.skipToPrevious();
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_BACKWARD),
                        eq(KEY_DOWN));
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_BACKWARD),
                        eq(KEY_UP));
    }

    /**
     * Test media browser fast forward command
     */
    @Test
    @FlakyTest
    public void testFastForward() throws Exception {
        setUpConnectedState(true, true);
        MediaControllerCompat.TransportControls transportControls =
                BluetoothMediaBrowserService.getTransportControls();

        //FastForward
        transportControls.fastForward();
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_FF),
                        eq(KEY_DOWN));
        //Finish FastForwarding
        transportControls.play();
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_FF),
                        eq(KEY_UP));
    }

    /**
     * Test media browser rewind command
     */
    @Test
    public void testRewind() throws Exception {
        setUpConnectedState(true, true);
        MediaControllerCompat.TransportControls transportControls =
                BluetoothMediaBrowserService.getTransportControls();

        //Rewind
        transportControls.rewind();
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_REWIND),
                        eq(KEY_DOWN));
        //Finish Rewinding
        transportControls.play();
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_REWIND),
                        eq(KEY_UP));
    }

    /**
     * Test media browser skip to queue item
     */
    @Test
    public void testSkipToQueueInvalid() throws Exception {
        byte scope = 1;
        int minSize = 0;
        int maxSize = 255;
        setUpConnectedState(true, true);
        MediaControllerCompat.TransportControls transportControls =
                BluetoothMediaBrowserService.getTransportControls();

        //Play an invalid item below start
        transportControls.skipToQueueItem(minSize - 1);
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(0))
                .playItem(eq(mTestAddress), eq(scope), anyLong(), anyInt());

        //Play an invalid item beyond end
        transportControls.skipToQueueItem(maxSize + 1);
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(0))
                .playItem(eq(mTestAddress), eq(scope), anyLong(), anyInt());
    }

    /**
     * Test media browser shuffle command
     */
    @Test
    public void testShuffle() throws Exception {
        byte[] shuffleSetting = new byte[]{3};
        byte[] shuffleMode = new byte[]{2};

        setUpConnectedState(true, true);
        MediaControllerCompat.TransportControls transportControls =
                BluetoothMediaBrowserService.getTransportControls();

        //Shuffle
        transportControls.setShuffleMode(1);
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .setPlayerApplicationSettingValues(
                        eq(mTestAddress), eq((byte) 1), eq(shuffleSetting), eq(shuffleMode));
    }

    /**
     * Test media browser repeat command
     */
    @Test
    public void testRepeat() throws Exception {
        byte[] repeatSetting = new byte[]{2};
        byte[] repeatMode = new byte[]{3};

        setUpConnectedState(true, true);
        MediaControllerCompat.TransportControls transportControls =
                BluetoothMediaBrowserService.getTransportControls();

        //Shuffle
        transportControls.setRepeatMode(2);
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .setPlayerApplicationSettingValues(
                        eq(mTestAddress), eq((byte) 1), eq(repeatSetting), eq(repeatMode));
    }

    /**
     * Test media browsing
     * Verify that a browse tree is created with the proper root
     * Verify that a player can be fetched and added to the browse tree
     * Verify that the contents of a player are fetched upon request
     */
    @Test
    @FlakyTest
    public void testBrowsingCommands() {
        setUpConnectedState(true, true);
        final String rootName = "__ROOT__" + mTestDevice.getAddress().toString();
        final String playerName = "Player 1";

        //Get the root of the device
        BrowseTree.BrowseNode results = mAvrcpStateMachine.findNode(rootName);
        Assert.assertEquals(rootName, results.getID());

        //Request fetch the list of players
        BrowseTree.BrowseNode playerNodes = mAvrcpStateMachine.findNode(results.getID());
        mAvrcpStateMachine.requestContents(results);
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .getPlayerList(eq(mTestAddress), eq(0), eq(19));

        //Provide back a player object
        byte[] playerFeatures =
                new byte[]{0, 0, 0, 0, 0, (byte) 0xb7, 0x01, 0x0c, 0x0a, 0, 0, 0, 0, 0, 0, 0};
        AvrcpPlayer playerOne = makePlayer(mTestDevice, 1, playerName, 1, playerFeatures, 1);
        List<AvrcpPlayer> testPlayers = new ArrayList<>();
        testPlayers.add(playerOne);
        mAvrcpStateMachine.sendMessage(AvrcpControllerStateMachine.MESSAGE_PROCESS_GET_PLAYER_ITEMS,
                testPlayers);
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());

        //Verify that the player object is available.
        playerNodes = mAvrcpStateMachine.findNode(results.getID());
        Assert.assertEquals(true, results.isCached());
        Assert.assertEquals("MediaItem{mFlags=1, mDescription=" + playerName + ", null, null}",
                results.getChildren().get(0).getMediaItem().toString());

        //Fetch contents of that player object
        BrowseTree.BrowseNode playerOneNode = mAvrcpStateMachine.findNode(
                results.getChildren().get(0).getID());
        mAvrcpStateMachine.requestContents(playerOneNode);
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .setBrowsedPlayer(eq(mTestAddress), eq(1));
        mAvrcpStateMachine.sendMessage(AvrcpControllerStateMachine.MESSAGE_PROCESS_FOLDER_PATH, 5);
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .getFolderList(eq(mTestAddress), eq(0), eq(4));
    }

    /**
     * Test our reaction to an available players changed event
     *
     * Verify that we issue a command to fetch the new available players
     */
    @Test
    public void testAvailablePlayersChanged() {
        setUpConnectedState(true, true);
        final String rootName = "__ROOT__" + mTestDevice.getAddress().toString();

        // Send an available players have changed event
        mAvrcpStateMachine.sendMessage(
                AvrcpControllerStateMachine.MESSAGE_PROCESS_AVAILABLE_PLAYER_CHANGED);

        // Verify we've uncached our browse root and made the call to fetch new players
        Assert.assertFalse(mAvrcpStateMachine.findNode(rootName).isCached());
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .getPlayerList(eq(mTestAddress), eq(0), eq(19));
    }

    /**
     * Test how we handle receiving an available players list that contains the player we know to
     * be the addressed player
     */
    @Test
    public void testAvailablePlayersReceived_AddressedPlayerExists() {
        setUpConnectedState(true, true);
        final String rootName = "__ROOT__" + mTestDevice.getAddress().toString();

        // Set an addressed player that will be in the available players set. A new player triggers
        // a now playing list download, so send back nothing.
        mAvrcpStateMachine.sendMessage(
                AvrcpControllerStateMachine.MESSAGE_PROCESS_ADDRESSED_PLAYER_CHANGED, 1);
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());
        mAvrcpStateMachine.sendMessage(
                AvrcpControllerStateMachine.MESSAGE_PROCESS_GET_FOLDER_ITEMS_OUT_OF_RANGE);
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());
        clearInvocations(mAvrcpControllerService);
        clearInvocations(mNativeInterface);

        // Send an available players have changed event
        mAvrcpStateMachine.sendMessage(
                AvrcpControllerStateMachine.MESSAGE_PROCESS_AVAILABLE_PLAYER_CHANGED);

        // Verify we've uncached our browse root and made the call to fetch new players
        Assert.assertFalse(mAvrcpStateMachine.findNode(rootName).isCached());
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .getPlayerList(eq(mTestAddress), eq(0), eq(19));

        // Send available players set that contains our addressed player
        byte[] playerFeatures =
                new byte[]{0, 0, 0, 0, 0, (byte) 0xb7, 0x01, 0x0c, 0x0a, 0, 0, 0, 0, 0, 0, 0};
        AvrcpPlayer playerOne = makePlayer(mTestDevice, 1, "Player 1", 1, playerFeatures, 1);
        AvrcpPlayer playerTwo = makePlayer(mTestDevice, 2, "Player 2", 1, playerFeatures, 1);
        List<AvrcpPlayer> testPlayers = new ArrayList<>();
        testPlayers.add(playerOne);
        testPlayers.add(playerTwo);
        mAvrcpStateMachine.sendMessage(AvrcpControllerStateMachine.MESSAGE_PROCESS_GET_PLAYER_ITEMS,
                testPlayers);

        // Wait for them to be processed
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());

        // Verify we processed the first players properly. Note the addressed player should always
        // be in the available player set.
        Assert.assertTrue(mAvrcpStateMachine.findNode(rootName).isCached());
        SparseArray<AvrcpPlayer> players = mAvrcpStateMachine.getAvailablePlayers();
        Assert.assertTrue(players.contains(mAvrcpStateMachine.getAddressedPlayerId()));
        Assert.assertEquals(testPlayers.size(), players.size());
        for (AvrcpPlayer player : testPlayers) {
            Assert.assertTrue(players.contains(player.getId()));
        }

        // Verify we request metadata, playback state and now playing list
        assertNowPlayingList(new ArrayList<AvrcpItem>());
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .getNowPlayingList(eq(mTestAddress), eq(0), eq(19));
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .getCurrentMetadata(eq(mTestAddress));
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .getPlaybackState(eq(mTestAddress));
    }

    /**
     * Test how we handle receiving an available players list that does not contain the player we
     * know to be the addressed player
     */
    @Test
    public void testAvailablePlayersReceived_AddressedPlayerDoesNotExist() {
        setUpConnectedState(true, true);
        final String rootName = "__ROOT__" + mTestDevice.getAddress().toString();

        // Send an available players have changed event
        mAvrcpStateMachine.sendMessage(
                AvrcpControllerStateMachine.MESSAGE_PROCESS_AVAILABLE_PLAYER_CHANGED);

        // Verify we've uncached our browse root and made the call to fetch new players
        Assert.assertFalse(mAvrcpStateMachine.findNode(rootName).isCached());
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .getPlayerList(eq(mTestAddress), eq(0), eq(19));

        // Send available players set that does not contain the addressed player
        byte[] playerFeatures =
                new byte[]{0, 0, 0, 0, 0, (byte) 0xb7, 0x01, 0x0c, 0x0a, 0, 0, 0, 0, 0, 0, 0};
        AvrcpPlayer playerOne = makePlayer(mTestDevice, 1, "Player 1", 1, playerFeatures, 1);
        AvrcpPlayer playerTwo = makePlayer(mTestDevice, 2, "Player 2", 1, playerFeatures, 1);
        List<AvrcpPlayer> testPlayers = new ArrayList<>();
        testPlayers.add(playerOne);
        testPlayers.add(playerTwo);
        mAvrcpStateMachine.sendMessage(AvrcpControllerStateMachine.MESSAGE_PROCESS_GET_PLAYER_ITEMS,
                testPlayers);

        // Wait for them to be processed
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());

        // Verify we processed the players properly. Note the addressed player is currently the
        // default player and is not in the available player set sent. This means we'll have an
        // extra player at ID -1.
        Assert.assertTrue(mAvrcpStateMachine.findNode(rootName).isCached());
        SparseArray<AvrcpPlayer> players = mAvrcpStateMachine.getAvailablePlayers();
        Assert.assertTrue(players.contains(mAvrcpStateMachine.getAddressedPlayerId()));
        Assert.assertEquals(testPlayers.size() + 1, players.size());
        for (AvrcpPlayer player : testPlayers) {
            Assert.assertTrue(players.contains(player.getId()));
        }

        // Verify we do not request metadata, playback state and now playing list because we're
        // sure the addressed player and metadata we have isn't impacted by the new players
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(0))
                .getNowPlayingList(any(), anyInt(), anyInt());
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(0))
                .getCurrentMetadata(any());
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(0))
                .getPlaybackState(any());
    }

    /**
     * Test addressed media player changing to a player we know about
     * Verify when the addressed media player changes browsing data updates
     */
    @Test
    public void testAddressedPlayerChangedToNewKnownPlayer() {
        setUpConnectedState(true, true);
        final String rootName = "__ROOT__" + mTestDevice.getAddress().toString();

        //Get the root of the device
        BrowseTree.BrowseNode results = mAvrcpStateMachine.findNode(rootName);
        Assert.assertEquals(rootName, results.getID());

        //Request fetch the list of players
        BrowseTree.BrowseNode playerNodes = mAvrcpStateMachine.findNode(results.getID());
        mAvrcpStateMachine.requestContents(results);
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .getPlayerList(eq(mTestAddress), eq(0), eq(19));

        //Provide back two player objects, IDs 1 and 2
        byte[] playerFeatures =
                new byte[]{0, 0, 0, 0, 0, (byte) 0xb7, 0x01, 0x0c, 0x0a, 0, 0, 0, 0, 0, 0, 0};
        AvrcpPlayer playerOne = makePlayer(mTestDevice, 1, "Player 1", 1, playerFeatures, 1);
        AvrcpPlayer playerTwo = makePlayer(mTestDevice, 2, "Player 2", 1, playerFeatures, 1);
        List<AvrcpPlayer> testPlayers = new ArrayList<>();
        testPlayers.add(playerOne);
        testPlayers.add(playerTwo);
        mAvrcpStateMachine.sendMessage(AvrcpControllerStateMachine.MESSAGE_PROCESS_GET_PLAYER_ITEMS,
                testPlayers);

        //Set something arbitrary for the current Now Playing list
        List<AvrcpItem> nowPlayingList = new ArrayList<AvrcpItem>();
        nowPlayingList.add(makeNowPlayingItem(1, "Song 1"));
        nowPlayingList.add(makeNowPlayingItem(2, "Song 2"));
        setNowPlayingList(nowPlayingList);
        clearInvocations(mAvrcpControllerService);
        clearInvocations(mNativeInterface);

        //Change players and verify that BT attempts to update the results
        mAvrcpStateMachine.sendMessage(
                AvrcpControllerStateMachine.MESSAGE_PROCESS_ADDRESSED_PLAYER_CHANGED, 2);
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());

        // The addressed player should always be in the available player set
        Assert.assertEquals(2, mAvrcpStateMachine.getAddressedPlayerId());
        SparseArray<AvrcpPlayer> players = mAvrcpStateMachine.getAvailablePlayers();
        Assert.assertTrue(players.contains(mAvrcpStateMachine.getAddressedPlayerId()));

        //Make sure the Now Playing list is now cleared
        assertNowPlayingList(new ArrayList<AvrcpItem>());

        // Verify that a player change to a player with Now Playing support causes a refresh.
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .getNowPlayingList(eq(mTestAddress), eq(0), eq(19));

        // Verify we request metadata and playback state
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .getCurrentMetadata(eq(mTestAddress));
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .getPlaybackState(eq(mTestAddress));
    }

    /**
     * Test addressed media player change to a player we don't know about
     * Verify when the addressed media player changes browsing data updates
     * Verify that the contents of a player are fetched upon request
     */
    @Test
    public void testAddressedPlayerChangedToUnknownPlayer() {
        setUpConnectedState(true, true);
        final String rootName = "__ROOT__" + mTestDevice.getAddress().toString();

        //Get the root of the device
        BrowseTree.BrowseNode rootNode = mAvrcpStateMachine.findNode(rootName);
        Assert.assertEquals(rootName, rootNode.getID());

        //Request fetch the list of players
        BrowseTree.BrowseNode playerNodes = mAvrcpStateMachine.findNode(rootNode.getID());
        mAvrcpStateMachine.requestContents(rootNode);

        //Provide back a player object
        byte[] playerFeatures =
                new byte[]{0, 0, 0, 0, 0, (byte) 0xb7, 0x01, 0x0c, 0x0a, 0, 0, 0, 0, 0, 0, 0};
        AvrcpPlayer playerOne = makePlayer(mTestDevice, 1, "Player 1", 1, playerFeatures, 1);
        List<AvrcpPlayer> testPlayers = new ArrayList<>();
        testPlayers.add(playerOne);
        mAvrcpStateMachine.sendMessage(
                AvrcpControllerStateMachine.MESSAGE_PROCESS_GET_PLAYER_ITEMS, testPlayers);

        //Set something arbitrary for the current Now Playing list
        List<AvrcpItem> nowPlayingList = new ArrayList<AvrcpItem>();
        nowPlayingList.add(makeNowPlayingItem(1, "Song 1"));
        nowPlayingList.add(makeNowPlayingItem(2, "Song 2"));
        setNowPlayingList(nowPlayingList);

        //Change players
        mAvrcpStateMachine.sendMessage(
                AvrcpControllerStateMachine.MESSAGE_PROCESS_ADDRESSED_PLAYER_CHANGED, 4);
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());

        //Make sure the Now Playing list is now cleared and we requested metadata
        assertNowPlayingList(new ArrayList<AvrcpItem>());
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .getCurrentMetadata(eq(mTestAddress));
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .getPlaybackState(eq(mTestAddress));
    }

    /**
     * Test what we do when we receive an addressed player change to a player with the same ID as
     * the current addressed play.
     *
     * Verify we assume nothing and re-fetch the current metadata and playback status.
     */
    @Test
    public void testAddressedPlayerChangedToSamePlayerId() {
        setUpConnectedState(true, true);
        final String rootName = "__ROOT__" + mTestDevice.getAddress().toString();

        // Set the addressed player so we can change to the same one
        mAvrcpStateMachine.sendMessage(
                AvrcpControllerStateMachine.MESSAGE_PROCESS_ADDRESSED_PLAYER_CHANGED, 1);

        // Wait until idle so Now Playing List is queried for, resolve it
        TestUtils.waitForLooperToBeIdle(mAvrcpStateMachine.getHandler().getLooper());
        mAvrcpStateMachine.sendMessage(
                AvrcpControllerStateMachine.MESSAGE_PROCESS_GET_FOLDER_ITEMS_OUT_OF_RANGE);
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());

        //Get the root of the device
        BrowseTree.BrowseNode rootNode = mAvrcpStateMachine.findNode(rootName);
        Assert.assertEquals(rootName, rootNode.getID());

        //Request fetch the list of players
        BrowseTree.BrowseNode playerNodes = mAvrcpStateMachine.findNode(rootNode.getID());
        mAvrcpStateMachine.requestContents(rootNode);

        // Send available players set that contains our addressed player
        byte[] playerFeatures =
                new byte[]{0, 0, 0, 0, 0, (byte) 0xb7, 0x01, 0x0c, 0x0a, 0, 0, 0, 0, 0, 0, 0};
        AvrcpPlayer playerOne = makePlayer(mTestDevice, 1, "Player 1", 1, playerFeatures, 1);
        AvrcpPlayer playerTwo = makePlayer(mTestDevice, 2, "Player 2", 1, playerFeatures, 1);
        List<AvrcpPlayer> testPlayers = new ArrayList<>();
        testPlayers.add(playerOne);
        testPlayers.add(playerTwo);
        mAvrcpStateMachine.sendMessage(AvrcpControllerStateMachine.MESSAGE_PROCESS_GET_PLAYER_ITEMS,
                testPlayers);

        // Wait until idle so Now Playing List is queried for again, resolve it again
        TestUtils.waitForLooperToBeIdle(mAvrcpStateMachine.getHandler().getLooper());
        mAvrcpStateMachine.sendMessage(
                AvrcpControllerStateMachine.MESSAGE_PROCESS_GET_FOLDER_ITEMS_OUT_OF_RANGE);
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());
        clearInvocations(mAvrcpControllerService);
        clearInvocations(mNativeInterface);

        // Send an addressed player changed to the same player ID
        mAvrcpStateMachine.sendMessage(
                AvrcpControllerStateMachine.MESSAGE_PROCESS_ADDRESSED_PLAYER_CHANGED, 1);
        TestUtils.waitForLooperToBeIdle(mAvrcpStateMachine.getHandler().getLooper());

        // Verify we make no assumptions about the player ID and still fetch metadata, play status
        // and now playing list (since player 1 supports it)
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .getNowPlayingList(eq(mTestAddress), eq(0), eq(19));
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .getCurrentMetadata(eq(mTestAddress));
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .getPlaybackState(eq(mTestAddress));
    }

    /**
     * Test that the Now Playing playlist is updated when it changes.
     */
    @Test
    public void testNowPlaying() {
        setUpConnectedState(true, true);
        mAvrcpStateMachine.nowPlayingContentChanged();
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .getNowPlayingList(eq(mTestAddress), eq(0), eq(19));
    }

    /**
     * Test that AVRCP events such as playback commands can execute while performing browsing.
     */
    @Test
    public void testPlayWhileBrowsing() {
        setUpConnectedState(true, true);
        final String rootName = "__ROOT__" + mTestDevice.getAddress().toString();
        final String playerName = "Player 1";

        //Get the root of the device
        BrowseTree.BrowseNode results = mAvrcpStateMachine.findNode(rootName);
        Assert.assertEquals(rootName, results.getID());

        //Request fetch the list of players
        BrowseTree.BrowseNode playerNodes = mAvrcpStateMachine.findNode(results.getID());
        mAvrcpStateMachine.requestContents(results);

        MediaControllerCompat.TransportControls transportControls =
                BluetoothMediaBrowserService.getTransportControls();
        transportControls.play();
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_PLAY),
                        eq(KEY_DOWN));
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_PLAY),
                        eq(KEY_UP));
    }

    /**
     * Test that Absolute Volume Registration is working
     */
    @Test
    public void testRegisterAbsVolumeNotification() {
        byte label = 42;
        setUpConnectedState(true, true);
        mAvrcpStateMachine.sendMessage(
                AvrcpControllerStateMachine.MESSAGE_PROCESS_REGISTER_ABS_VOL_NOTIFICATION, label);
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS))
                .sendRegisterAbsVolRsp(any(), anyByte(), eq(127), eq((int) label));
    }

    /**
     * Test playback does not request focus when another app is playing music.
     */
    @Test
    public void testPlaybackWhileMusicPlaying() {
        when(mMockResources.getBoolean(R.bool.a2dp_sink_automatically_request_audio_focus))
                .thenReturn(false);
        when(mA2dpSinkService.getFocusState()).thenReturn(AudioManager.AUDIOFOCUS_NONE);
        doReturn(true).when(mAudioManager).isMusicActive();
        setUpConnectedState(true, true);
        mAvrcpStateMachine.sendMessage(
                AvrcpControllerStateMachine.MESSAGE_PROCESS_PLAY_STATUS_CHANGED,
                PlaybackStateCompat.STATE_PLAYING);
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_PAUSE),
                        eq(KEY_DOWN));
        verify(mA2dpSinkService, never()).requestAudioFocus(mTestDevice, true);
    }

    /**
     * Test playback requests focus while nothing is playing music.
     */
    @Test
    public void testPlaybackWhileIdle() {
        when(mA2dpSinkService.getFocusState()).thenReturn(AudioManager.AUDIOFOCUS_NONE);
        doReturn(false).when(mAudioManager).isMusicActive();
        setUpConnectedState(true, true);
        mAvrcpStateMachine.sendMessage(
                AvrcpControllerStateMachine.MESSAGE_PROCESS_PLAY_STATUS_CHANGED,
                PlaybackStateCompat.STATE_PLAYING);
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());
        verify(mA2dpSinkService).requestAudioFocus(mTestDevice, true);
    }

    /**
     * Test receiving a playback status of playing while we're in an error state
     * as it relates to getting audio focus.
     *
     * Verify we send a pause command and never attempt to request audio focus
     */
    @Test
    public void testPlaybackWhileErrorState() {
        when(mA2dpSinkService.getFocusState()).thenReturn(AudioManager.ERROR);
        setUpConnectedState(true, true);
        mAvrcpStateMachine.sendMessage(
                AvrcpControllerStateMachine.MESSAGE_PROCESS_PLAY_STATUS_CHANGED,
                PlaybackStateCompat.STATE_PLAYING);
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_PAUSE),
                        eq(KEY_DOWN));
        verify(mA2dpSinkService, never()).requestAudioFocus(mTestDevice, true);
    }

    /**
     * Test receiving a playback status of playing while we have focus
     *
     * Verify we do not send a pause command and never attempt to request audio focus
     */
    @Test
    public void testPlaybackWhilePlayingState() {
        when(mA2dpSinkService.getFocusState()).thenReturn(AudioManager.AUDIOFOCUS_GAIN);
        setUpConnectedState(true, true);
        Assert.assertTrue(mAvrcpStateMachine.isActive());
        mAvrcpStateMachine.sendMessage(
                AvrcpControllerStateMachine.MESSAGE_PROCESS_PLAY_STATUS_CHANGED,
                PlaybackStateCompat.STATE_PLAYING);
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());
        verify(mNativeInterface, never())
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_PAUSE),
                        eq(KEY_DOWN));
        verify(mA2dpSinkService, never()).requestAudioFocus(mTestDevice, true);
    }

    /**
     * Test that isActive() reports the proper value when we're active
     */
    @Test
    public void testIsActive_deviceActive() {
        Assert.assertTrue(mAvrcpStateMachine.isActive());
    }

    /**
     * Test that isActive() reports the proper value when we're inactive
     */
    @Test
    public void testIsActive_deviceInactive() {
        setActiveDevice(null);
        Assert.assertFalse(mAvrcpStateMachine.isActive());
    }

    /**
     * Test becoming active from the inactive state
     */
    @Test
    public void testBecomeActive() {
        // Note device starts as active in setUp() and state cascades come the CONNECTED state
        setUpConnectedState(true, true);
        Assert.assertTrue(mAvrcpStateMachine.isActive());

        // Make the device inactive
        setActiveDevice(null);
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());
        Assert.assertFalse(mAvrcpStateMachine.isActive());

        // Change device state while inactive
        AvrcpItem track = makeTrack("title", "artist", "album", 1, 10, "none", 10, null);
        List<AvrcpItem> nowPlayingList = new ArrayList<AvrcpItem>();
        AvrcpItem queueItem1 = makeNowPlayingItem(0, "title");
        AvrcpItem queueItem2 = makeNowPlayingItem(1, "title 2");
        nowPlayingList.add(queueItem1);
        nowPlayingList.add(queueItem2);
        setCurrentTrack(track);
        setPlaybackState(PlaybackStateCompat.STATE_PAUSED);
        setPlaybackPosition(7, 10);
        setNowPlayingList(nowPlayingList);

        // Make device active
        setActiveDevice(mTestDevice);
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());
        Assert.assertTrue(mAvrcpStateMachine.isActive());

        // See that state from BluetoothMediaBrowserService is updated
        MediaSessionCompat session = BluetoothMediaBrowserService.getSession();
        Assert.assertNotNull(session);
        MediaControllerCompat controller = session.getController();
        Assert.assertNotNull(controller);

        MediaMetadataCompat metadata = controller.getMetadata();
        Assert.assertNotNull(metadata);
        Assert.assertEquals("title", metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE));
        Assert.assertEquals("artist", metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST));
        Assert.assertEquals("album", metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM));
        Assert.assertEquals(1, metadata.getLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER));
        Assert.assertEquals(10, metadata.getLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS));
        Assert.assertEquals("none", metadata.getString(MediaMetadataCompat.METADATA_KEY_GENRE));
        Assert.assertEquals(10, metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION));

        PlaybackStateCompat playbackState = controller.getPlaybackState();
        Assert.assertNotNull(playbackState);
        Assert.assertEquals(PlaybackStateCompat.STATE_PAUSED, playbackState.getState());
        Assert.assertEquals(7, playbackState.getPosition());

        List<MediaSessionCompat.QueueItem> queue = controller.getQueue();
        Assert.assertNotNull(queue);
        Assert.assertEquals(2, queue.size());
        Assert.assertEquals("title", queue.get(0).getDescription().getTitle().toString());
        Assert.assertEquals("title 2", queue.get(1).getDescription().getTitle().toString());
    }

    /**
     * Test becoming inactive from the active state
     */
    @Test
    public void testBecomeInactive() {
        // Note device starts as active in setUp()
        setUpConnectedState(true, true);
        Assert.assertTrue(mAvrcpStateMachine.isActive());

        // Set the active device to something else, verify we're inactive and send a pause upon
        // becoming inactive
        setActiveDevice(null);
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_PAUSE),
                        eq(KEY_DOWN));
        Assert.assertFalse(mAvrcpStateMachine.isActive());
    }

    @Test
    public void testTrackChangedWhileActive_currentTrackAndQueueNumberUpdated() {
        setUpConnectedState(true, true);

        // Set track
        AvrcpItem track = makeTrack("Song 1", "artist", "album", 1, 2, "none", 10, null);
        setCurrentTrack(track);

        // Set current Now Playing list
        List<AvrcpItem> nowPlayingList = new ArrayList<AvrcpItem>();
        nowPlayingList.add(makeNowPlayingItem(1, "Song 1"));
        nowPlayingList.add(makeNowPlayingItem(2, "Song 2"));
        setNowPlayingList(nowPlayingList);

        // Set playing
        setPlaybackState(PlaybackStateCompat.STATE_PLAYING);

        // Wait
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());

        // Verify track and playback state
        MediaSessionCompat session = BluetoothMediaBrowserService.getSession();
        Assert.assertNotNull(session);
        MediaControllerCompat controller = session.getController();
        Assert.assertNotNull(controller);

        MediaMetadataCompat metadata = controller.getMetadata();
        Assert.assertNotNull(metadata);
        Assert.assertEquals("Song 1", metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE));
        Assert.assertEquals("artist", metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST));
        Assert.assertEquals("album", metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM));
        Assert.assertEquals(1, metadata.getLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER));
        Assert.assertEquals(2, metadata.getLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS));
        Assert.assertEquals("none", metadata.getString(MediaMetadataCompat.METADATA_KEY_GENRE));
        Assert.assertEquals(10, metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION));

        PlaybackStateCompat playbackState = controller.getPlaybackState();
        Assert.assertNotNull(playbackState);
        Assert.assertEquals(PlaybackStateCompat.STATE_PLAYING, playbackState.getState());
        Assert.assertEquals(0, playbackState.getActiveQueueItemId());

        // Track changes, with new metadata and new track number
        track = makeTrack("Song 2", "artist", "album", 2, 2, "none", 10, null);
        setCurrentTrack(track);
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());

        // Assert new track metadata and active queue item
        metadata = controller.getMetadata();
        Assert.assertNotNull(metadata);
        Assert.assertEquals("Song 2", metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE));
        Assert.assertEquals("artist", metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST));
        Assert.assertEquals("album", metadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM));
        Assert.assertEquals(2, metadata.getLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER));
        Assert.assertEquals(2, metadata.getLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS));
        Assert.assertEquals("none", metadata.getString(MediaMetadataCompat.METADATA_KEY_GENRE));
        Assert.assertEquals(10, metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION));

        playbackState = controller.getPlaybackState();
        Assert.assertNotNull(playbackState);
        Assert.assertEquals(PlaybackStateCompat.STATE_PLAYING, playbackState.getState());
        Assert.assertEquals(1, playbackState.getActiveQueueItemId());
    }

    /**
     * Test receiving a track change update when we're not the active device
     */
    @Test
    public void testTrackChangeWhileNotActiveDevice() {
        setUpConnectedState(true, true);

        // Set the active device to something else, verify we're inactive and send a pause upon
        // becoming inactive
        setActiveDevice(null);
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());
        Assert.assertFalse(mAvrcpStateMachine.isActive());

        // Change track while inactive
        AvrcpItem track = makeTrack("title", "artist", "album", 1, 10, "none", 10, null);
        setCurrentTrack(track);

        // Since we're not active, verify BluetoothMediaBrowserService does not have these values
        MediaSessionCompat session = BluetoothMediaBrowserService.getSession();
        Assert.assertNotNull(session);
        MediaControllerCompat controller = session.getController();
        Assert.assertNotNull(controller);

        MediaMetadataCompat metadata = controller.getMetadata();
        Assert.assertNull(metadata); // track starts as null and shouldn't change
    }

    /**
     * Test receiving a playback status of playing when we're not the active device
     */
    @Test
    public void testPlaybackWhileNotActiveDevice() {
        setUpConnectedState(true, true);

        // Set the active device to something else, verify we're inactive
        setActiveDevice(null);
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());
        Assert.assertFalse(mAvrcpStateMachine.isActive());
        clearInvocations(mAvrcpControllerService);
        clearInvocations(mNativeInterface);

        // Now that we're inactive, receive a playback status of playing
        setPlaybackState(PlaybackStateCompat.STATE_PLAYING);

        // Verify we send a pause, never request audio focus, and the playback state on
        // BluetoothMediaBrowserService never updates.
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_PAUSE),
                        eq(KEY_DOWN));
        verify(mA2dpSinkService, never()).requestAudioFocus(mTestDevice, true);
        Assert.assertEquals(PlaybackStateCompat.STATE_ERROR,
                BluetoothMediaBrowserService.getPlaybackState().getState());
    }

    /**
     * Test receiving a play position update when we're not the active device
     */
    @Test
    public void testPlayPositionChangeWhileNotActiveDevice() {
        setUpConnectedState(true, true);

        // Set the active device to something else, verify we're inactive
        setActiveDevice(null);
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());
        Assert.assertFalse(mAvrcpStateMachine.isActive());
        clearInvocations(mAvrcpControllerService);
        clearInvocations(mNativeInterface);

        // Now that we're inactive, receive a play position change
        setPlaybackPosition(1, 10);

        // Since we're not active, verify BluetoothMediaBrowserService does not have these values
        MediaSessionCompat session = BluetoothMediaBrowserService.getSession();
        Assert.assertNotNull(session);
        MediaControllerCompat controller = session.getController();
        Assert.assertNotNull(controller);

        PlaybackStateCompat playbackState = controller.getPlaybackState();
        Assert.assertNotNull(playbackState);
        Assert.assertEquals(0, playbackState.getPosition());
    }

    /**
     * Test receiving a now playing list update when we're not the active device
     */
    @Test
    public void testNowPlayingListChangeWhileNotActiveDevice() {
        setUpConnectedState(true, true);

        // Set the active device to something else, verify we're inactive and send a pause upon
        // becoming inactive
        setActiveDevice(null);
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());
        Assert.assertFalse(mAvrcpStateMachine.isActive());

        // Change queue while inactive
        List<AvrcpItem> nowPlayingList = new ArrayList<AvrcpItem>();
        AvrcpItem queueItem1 = makeNowPlayingItem(0, "title");
        AvrcpItem queueItem2 = makeNowPlayingItem(1, "title 2");
        AvrcpItem queueItem3 = makeNowPlayingItem(1, "title 3");
        nowPlayingList.add(queueItem1);
        nowPlayingList.add(queueItem2);
        nowPlayingList.add(queueItem3);
        setNowPlayingList(nowPlayingList);

        // Since we're not active, verify BluetoothMediaBrowserService does not have these values
        MediaSessionCompat session = BluetoothMediaBrowserService.getSession();
        Assert.assertNotNull(session);
        MediaControllerCompat controller = session.getController();
        Assert.assertNotNull(controller);

        List<MediaSessionCompat.QueueItem> queue = controller.getQueue();
        Assert.assertNull(queue);
    }

    /**
     * Test receiving an audio focus changed event when we are not recovering from a transient loss.
     * This should result in no play command being sent.
     */
    @Test
    public void testOnAudioFocusGain_playNotSent() {
        setUpConnectedState(true, true);
        sendAudioFocusUpdate(AudioManager.AUDIOFOCUS_GAIN);
        TestUtils.waitForLooperToBeIdle(mAvrcpStateMachine.getHandler().getLooper());
        verify(mNativeInterface, never())
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_PAUSE),
                        eq(KEY_DOWN));
        verify(mNativeInterface, never())
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_PAUSE),
                        eq(KEY_UP));
    }

    /**
     * Test receiving a transient loss audio focus changed event while playing. A pause should be
     * sent
     */
    @Test
    public void testOnAudioFocusTransientLossWhilePlaying_pauseSent() {
        when(mMockResources.getBoolean(R.bool.a2dp_sink_automatically_request_audio_focus))
                .thenReturn(false);
        setUpConnectedState(true, true);
        sendAudioFocusUpdate(AudioManager.AUDIOFOCUS_GAIN);
        setPlaybackState(PlaybackStateCompat.STATE_PLAYING);
        sendAudioFocusUpdate(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);

        TestUtils.waitForLooperToBeIdle(mAvrcpStateMachine.getHandler().getLooper());
        verify(mNativeInterface, times(1))
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_PAUSE),
                        eq(KEY_DOWN));
        verify(mNativeInterface, times(1))
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_PAUSE),
                        eq(KEY_UP));
    }

    /**
     * Test receiving a transient loss audio focus changed event while paused. No pause should be
     * sent
     */
    @Test
    public void testOnAudioFocusTransientLossWhilePaused_pauseNotSent() {
        setUpConnectedState(true, true);
        sendAudioFocusUpdate(AudioManager.AUDIOFOCUS_GAIN);
        setPlaybackState(PlaybackStateCompat.STATE_PAUSED);
        sendAudioFocusUpdate(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);

        TestUtils.waitForLooperToBeIdle(mAvrcpStateMachine.getHandler().getLooper());
        verify(mNativeInterface, never())
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_PAUSE),
                        eq(KEY_DOWN));
        verify(mNativeInterface, never())
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_PAUSE),
                        eq(KEY_UP));
    }

    /**
     * Test receiving an audio focus loss event. A pause should be sent if we were playing
     */
    @Test
    public void testOnAudioFocusLossWhilePlaying_pauseSent() {
        setUpConnectedState(true, true);
        sendAudioFocusUpdate(AudioManager.AUDIOFOCUS_GAIN);
        setPlaybackState(PlaybackStateCompat.STATE_PLAYING);
        sendAudioFocusUpdate(AudioManager.AUDIOFOCUS_LOSS);

        TestUtils.waitForLooperToBeIdle(mAvrcpStateMachine.getHandler().getLooper());
        verify(mNativeInterface, times(1))
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_PAUSE),
                        eq(KEY_DOWN));
        verify(mNativeInterface, times(1))
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_PAUSE),
                        eq(KEY_UP));
    }

    /**
     * Test receiving an audio focus loss event. A pause should not be sent if we were paused
     */
    @Test
    public void testOnAudioFocusLossWhilePause_pauseNotSent() {
        setUpConnectedState(true, true);
        sendAudioFocusUpdate(AudioManager.AUDIOFOCUS_GAIN);
        setPlaybackState(PlaybackStateCompat.STATE_PAUSED);
        sendAudioFocusUpdate(AudioManager.AUDIOFOCUS_LOSS);

        TestUtils.waitForLooperToBeIdle(mAvrcpStateMachine.getHandler().getLooper());
        verify(mNativeInterface, times(0))
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_PAUSE),
                        eq(KEY_DOWN));
        verify(mNativeInterface, times(0))
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_PAUSE),
                        eq(KEY_UP));
    }

    /**
     * Test receiving an audio focus gained event following a transient loss where we sent a pause
     * and no event happened in between that should cause us to remain paused.
     */
    @Test
    public void testOnAudioFocusGainFromTransientLossWhilePlaying_playSent() {
        testOnAudioFocusTransientLossWhilePlaying_pauseSent();
        clearInvocations(mAvrcpControllerService);
        clearInvocations(mNativeInterface);
        setPlaybackState(PlaybackStateCompat.STATE_PAUSED);
        sendAudioFocusUpdate(AudioManager.AUDIOFOCUS_GAIN);

        TestUtils.waitForLooperToBeIdle(mAvrcpStateMachine.getHandler().getLooper());
        verify(mNativeInterface, times(1))
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_PLAY),
                        eq(KEY_DOWN));
        verify(mNativeInterface, times(1))
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_PLAY),
                        eq(KEY_UP));
    }

    /**
     * Test receiving an audio focus changed event following a transient loss where
     */
    @Test
    public void testOnAudioFocusGainFromTransientLossWhilePlayingWithPause_playNotSent() {
        testOnAudioFocusTransientLossWhilePlaying_pauseSent();
        clearInvocations(mAvrcpControllerService);
        clearInvocations(mNativeInterface);
        setPlaybackState(PlaybackStateCompat.STATE_PAUSED);
        TestUtils.waitForLooperToBeIdle(mAvrcpStateMachine.getHandler().getLooper());

        MediaControllerCompat.TransportControls transportControls =
                BluetoothMediaBrowserService.getTransportControls();
        transportControls.pause();
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_PAUSE),
                        eq(KEY_DOWN));
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_PAUSE),
                        eq(KEY_UP));

        sendAudioFocusUpdate(AudioManager.AUDIOFOCUS_GAIN);
        TestUtils.waitForLooperToBeIdle(mAvrcpStateMachine.getHandler().getLooper());

        verify(mNativeInterface, never())
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_PLAY),
                        eq(KEY_DOWN));
        verify(mNativeInterface, never())
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_PLAY),
                        eq(KEY_UP));
    }

    /**
     * Test receiving an audio focus gain event coming out of a transient loss where a stop command
     * has been sent
     */
    @Test
    public void testOnAudioFocusGainFromTransientLossWithStop_playNotSent() {
        testOnAudioFocusTransientLossWhilePlaying_pauseSent();
        clearInvocations(mAvrcpControllerService);
        clearInvocations(mNativeInterface);
        setPlaybackState(PlaybackStateCompat.STATE_PAUSED);
        TestUtils.waitForLooperToBeIdle(mAvrcpStateMachine.getHandler().getLooper());

        MediaControllerCompat.TransportControls transportControls =
                BluetoothMediaBrowserService.getTransportControls();
        transportControls.stop();
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_STOP),
                        eq(KEY_DOWN));
        verify(mNativeInterface, timeout(ASYNC_CALL_TIMEOUT_MILLIS).times(1))
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_STOP),
                        eq(KEY_UP));

        sendAudioFocusUpdate(AudioManager.AUDIOFOCUS_GAIN);
        TestUtils.waitForLooperToBeIdle(mAvrcpStateMachine.getHandler().getLooper());

        verify(mNativeInterface, never())
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_PLAY),
                        eq(KEY_DOWN));
        verify(mNativeInterface, never())
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_PLAY),
                        eq(KEY_UP));
    }

    /**
     * Test receiving an audio focus gain coming out of a transient loss where we were paused going
     * into the transient loss. No play should be sent because not play state needs to be recovered
     */
    @Test
    public void testOnAudioFocusGainFromTransientLossWhilePaused_playNotSent() {
        testOnAudioFocusTransientLossWhilePaused_pauseNotSent();
        clearInvocations(mAvrcpControllerService);
        clearInvocations(mNativeInterface);
        sendAudioFocusUpdate(AudioManager.AUDIOFOCUS_GAIN);

        TestUtils.waitForLooperToBeIdle(mAvrcpStateMachine.getHandler().getLooper());
        verify(mNativeInterface, never())
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_PLAY),
                        eq(KEY_DOWN));
        verify(mNativeInterface, never())
                .sendPassThroughCommand(
                        eq(mTestAddress),
                        eq(AvrcpControllerService.PASS_THRU_CMD_ID_PLAY),
                        eq(KEY_UP));
    }

    /**
     * Test receiving a now playing content changed event while downloading now playing content and
     * make sure our final now playing content downloaded matches what's expected
     */
    @Test
    public void testNowPlayingListChangedWhileFetchingNowPlayingList_fetchAbortedAndRestarted() {
        setUpConnectedState(true, true);
        sendAudioFocusUpdate(AudioManager.AUDIOFOCUS_GAIN);

        // Fill the list with songs 1 -> 25, more than download step size
        List<AvrcpItem> nowPlayingList = new ArrayList<AvrcpItem>();
        for (int i = 1; i <= 25; i++) {
            String title = "Song " + Integer.toString(i);
            nowPlayingList.add(makeNowPlayingItem(i, title));
        }

        // Fill the list with songs 26 -> 50
        List<AvrcpItem> updatedNowPlayingList = new ArrayList<AvrcpItem>();
        for (int i = 26; i <= 50; i++) {
            String title = "Song " + Integer.toString(i);
            updatedNowPlayingList.add(makeNowPlayingItem(i, title));
        }

        // Hand hold the download events
        BrowseTree.BrowseNode nowPlaying = mAvrcpStateMachine.findNode("NOW_PLAYING");
        mAvrcpStateMachine.requestContents(nowPlaying);
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());

        // Verify download attempt and send some elements over, verify next set is requested
        verify(mNativeInterface, times(1)).getNowPlayingList(eq(mTestAddress), eq(0), eq(19));
        mAvrcpStateMachine.sendMessage(AvrcpControllerStateMachine.MESSAGE_PROCESS_GET_FOLDER_ITEMS,
                new ArrayList<AvrcpItem>(nowPlayingList.subList(0, 20)));
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());
        verify(mNativeInterface, times(1)).getNowPlayingList(eq(mTestAddress), eq(20), eq(39));

        // Force a now playing content invalidation and verify attempted download
        mAvrcpStateMachine.nowPlayingContentChanged();
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());

        // Send requested items, they're likely from the new list at this point, but it shouldn't
        // matter what they are because we should toss them out and restart our download next.
        mAvrcpStateMachine.sendMessage(AvrcpControllerStateMachine.MESSAGE_PROCESS_GET_FOLDER_ITEMS,
                new ArrayList<AvrcpItem>(nowPlayingList.subList(20, 25)));
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());

        verify(mNativeInterface, times(2)).getNowPlayingList(eq(mTestAddress), eq(0), eq(19));

        mAvrcpStateMachine.sendMessage(AvrcpControllerStateMachine.MESSAGE_PROCESS_GET_FOLDER_ITEMS,
                new ArrayList<AvrcpItem>(updatedNowPlayingList.subList(0, 20)));
        mAvrcpStateMachine.sendMessage(AvrcpControllerStateMachine.MESSAGE_PROCESS_GET_FOLDER_ITEMS,
                new ArrayList<AvrcpItem>(updatedNowPlayingList.subList(20, 25)));
        mAvrcpStateMachine.sendMessage(
                AvrcpControllerStateMachine.MESSAGE_PROCESS_GET_FOLDER_ITEMS_OUT_OF_RANGE);

        // Wait for the now playing list to be propagated
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());

        // Make sure its set by re grabbing the node and checking its contents are cached
        nowPlaying = mAvrcpStateMachine.findNode("NOW_PLAYING");
        Assert.assertTrue(nowPlaying.isCached());
        assertNowPlayingList(updatedNowPlayingList);
    }

    /**
     * Test receiving a now playing content changed event right after we queued a fetch of some now
     * playing items. Make sure our final now playing content downloaded matches what's expected
     */
    @Test
    public void testNowPlayingListChangedQueuedFetchingNowPlayingList_fetchAbortedAndRestarted() {
        setUpConnectedState(true, true);
        sendAudioFocusUpdate(AudioManager.AUDIOFOCUS_GAIN);

        // Fill the list with songs 1 -> 25, more than download step size
        List<AvrcpItem> nowPlayingList = new ArrayList<AvrcpItem>();
        for (int i = 1; i <= 25; i++) {
            String title = "Song " + Integer.toString(i);
            nowPlayingList.add(makeNowPlayingItem(i, title));
        }

        // Fill the list with songs 26 -> 50
        List<AvrcpItem> updatedNowPlayingList = new ArrayList<AvrcpItem>();
        for (int i = 26; i <= 50; i++) {
            String title = "Song " + Integer.toString(i);
            updatedNowPlayingList.add(makeNowPlayingItem(i, title));
        }

        // Hand hold the download events
        BrowseTree.BrowseNode nowPlaying = mAvrcpStateMachine.findNode("NOW_PLAYING");
        mAvrcpStateMachine.requestContents(nowPlaying);
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());

        // Verify download attempt and send some elements over, verify next set is requested
        verify(mNativeInterface, times(1)).getNowPlayingList(eq(mTestAddress), eq(0), eq(19));
        mAvrcpStateMachine.nowPlayingContentChanged();

        mAvrcpStateMachine.sendMessage(AvrcpControllerStateMachine.MESSAGE_PROCESS_GET_FOLDER_ITEMS,
                new ArrayList<AvrcpItem>(nowPlayingList.subList(0, 20)));
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());

        // Receiving the previous members should cause our fetch process to realize we're aborted
        // and a new (second) request should be triggered for the list from the beginning
        verify(mNativeInterface, times(2)).getNowPlayingList(eq(mTestAddress), eq(0), eq(19));

        // Send whole list
        mAvrcpStateMachine.sendMessage(AvrcpControllerStateMachine.MESSAGE_PROCESS_GET_FOLDER_ITEMS,
                new ArrayList<AvrcpItem>(updatedNowPlayingList.subList(0, 20)));
        mAvrcpStateMachine.sendMessage(AvrcpControllerStateMachine.MESSAGE_PROCESS_GET_FOLDER_ITEMS,
                new ArrayList<AvrcpItem>(updatedNowPlayingList.subList(20, 25)));
        mAvrcpStateMachine.sendMessage(
                AvrcpControllerStateMachine.MESSAGE_PROCESS_GET_FOLDER_ITEMS_OUT_OF_RANGE);

        // Wait for the now playing list to be propagated
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());

        // Make sure its set by re grabbing the node and checking its contents are cached
        nowPlaying = mAvrcpStateMachine.findNode("NOW_PLAYING");
        Assert.assertTrue(nowPlaying.isCached());
        assertNowPlayingList(updatedNowPlayingList);
    }

    /**
     * Test receiving an addressed player changed event while downloading now playing content and
     * make sure our final now playing content downloaded matches what's expected.
     */
    @Test
    public void testAddressedPlayerChangedWhileFetchingNowPlayingList_fetchAbortedAndRestarted() {
        setUpConnectedState(true, true);
        sendAudioFocusUpdate(AudioManager.AUDIOFOCUS_GAIN);

        // Fill the list with songs 1 -> 25, more than download step size
        List<AvrcpItem> nowPlayingList = new ArrayList<AvrcpItem>();
        for (int i = 1; i <= 25; i++) {
            String title = "Song " + Integer.toString(i);
            nowPlayingList.add(makeNowPlayingItem(i, title));
        }

        // Fill the list with songs 26 -> 50
        List<AvrcpItem> updatedNowPlayingList = new ArrayList<AvrcpItem>();
        for (int i = 26; i <= 50; i++) {
            String title = "Song " + Integer.toString(i);
            updatedNowPlayingList.add(makeNowPlayingItem(i, title));
        }

        // Hand hold the download events
        BrowseTree.BrowseNode nowPlaying = mAvrcpStateMachine.findNode("NOW_PLAYING");
        mAvrcpStateMachine.requestContents(nowPlaying);
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());

        // Verify download attempt and send some elements over, verify next set is requested
        verify(mNativeInterface, times(1)).getNowPlayingList(eq(mTestAddress), eq(0), eq(19));
        mAvrcpStateMachine.sendMessage(AvrcpControllerStateMachine.MESSAGE_PROCESS_GET_FOLDER_ITEMS,
                new ArrayList<AvrcpItem>(nowPlayingList.subList(0, 20)));
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());
        verify(mNativeInterface, times(1)).getNowPlayingList(eq(mTestAddress), eq(20), eq(39));

        // Force a now playing content invalidation due to addressed player change
        mAvrcpStateMachine.sendMessage(
                AvrcpControllerStateMachine.MESSAGE_PROCESS_ADDRESSED_PLAYER_CHANGED, 1);
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());

        // Send requested items, they're likely from the new list at this point, but it shouldn't
        // matter what they are because we should toss them out and restart our download next.
        mAvrcpStateMachine.sendMessage(AvrcpControllerStateMachine.MESSAGE_PROCESS_GET_FOLDER_ITEMS,
                new ArrayList<AvrcpItem>(nowPlayingList.subList(20, 25)));
        TestUtils.waitForLooperToBeIdle(mAvrcpStateMachine.getHandler().getLooper());

        verify(mNativeInterface, times(2)).getNowPlayingList(eq(mTestAddress), eq(0), eq(19));

        mAvrcpStateMachine.sendMessage(AvrcpControllerStateMachine.MESSAGE_PROCESS_GET_FOLDER_ITEMS,
                new ArrayList<AvrcpItem>(updatedNowPlayingList.subList(0, 20)));
        mAvrcpStateMachine.sendMessage(AvrcpControllerStateMachine.MESSAGE_PROCESS_GET_FOLDER_ITEMS,
                new ArrayList<AvrcpItem>(updatedNowPlayingList.subList(20, 25)));
        mAvrcpStateMachine.sendMessage(
                AvrcpControllerStateMachine.MESSAGE_PROCESS_GET_FOLDER_ITEMS_OUT_OF_RANGE);

        // Wait for the now playing list to be propagated
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());

        // Make sure its set by re grabbing the node and checking its contents are cached
        nowPlaying = mAvrcpStateMachine.findNode("NOW_PLAYING");
        Assert.assertTrue(nowPlaying.isCached());
        assertNowPlayingList(updatedNowPlayingList);
    }

    /**
     * Test receiving an addressed player changed event while downloading now playing content and
     * make sure our final now playing content downloaded matches what's expected.
     */
    @Test
    public void testAddressedPlayerChangedQueuedFetchingNowPlayingList_fetchAbortedAndRestarted() {
        setUpConnectedState(true, true);
        sendAudioFocusUpdate(AudioManager.AUDIOFOCUS_GAIN);

        // Fill the list with songs 1 -> 25, more than download step size
        List<AvrcpItem> nowPlayingList = new ArrayList<AvrcpItem>();
        for (int i = 1; i <= 25; i++) {
            String title = "Song " + Integer.toString(i);
            nowPlayingList.add(makeNowPlayingItem(i, title));
        }

        // Fill the list with songs 26 -> 50
        List<AvrcpItem> updatedNowPlayingList = new ArrayList<AvrcpItem>();
        for (int i = 26; i <= 50; i++) {
            String title = "Song " + Integer.toString(i);
            updatedNowPlayingList.add(makeNowPlayingItem(i, title));
        }

        // Hand hold the download events
        BrowseTree.BrowseNode nowPlaying = mAvrcpStateMachine.findNode("NOW_PLAYING");
        mAvrcpStateMachine.requestContents(nowPlaying);
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());

        // Verify download attempt and send some elements over, verify next set is requested
        verify(mNativeInterface, times(1)).getNowPlayingList(eq(mTestAddress), eq(0), eq(19));

        // Force a now playing content invalidation due to addressed player change, happening
        // before we've received any items from the remote device.
        mAvrcpStateMachine.sendMessage(
                AvrcpControllerStateMachine.MESSAGE_PROCESS_ADDRESSED_PLAYER_CHANGED, 1);
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());

        // Now, send the items in and let it process
        mAvrcpStateMachine.sendMessage(AvrcpControllerStateMachine.MESSAGE_PROCESS_GET_FOLDER_ITEMS,
                new ArrayList<AvrcpItem>(nowPlayingList.subList(0, 20)));
        TestUtils.waitForLooperToBeIdle(mAvrcpStateMachine.getHandler().getLooper());

        verify(mNativeInterface, times(2)).getNowPlayingList(eq(mTestAddress), eq(0), eq(19));

        // Send requested items, they're likely from the new list at this point, but it shouldn't
        // matter what they are because we should toss them out and restart our download next.
        mAvrcpStateMachine.sendMessage(AvrcpControllerStateMachine.MESSAGE_PROCESS_GET_FOLDER_ITEMS,
                new ArrayList<AvrcpItem>(updatedNowPlayingList.subList(0, 20)));
        mAvrcpStateMachine.sendMessage(AvrcpControllerStateMachine.MESSAGE_PROCESS_GET_FOLDER_ITEMS,
                new ArrayList<AvrcpItem>(updatedNowPlayingList.subList(20, 25)));
        mAvrcpStateMachine.sendMessage(
                AvrcpControllerStateMachine.MESSAGE_PROCESS_GET_FOLDER_ITEMS_OUT_OF_RANGE);

        // Wait for the now playing list to be propagated
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());

        // Make sure its set by re grabbing the node and checking its contents are cached
        nowPlaying = mAvrcpStateMachine.findNode("NOW_PLAYING");
        Assert.assertTrue(nowPlaying.isCached());
        assertNowPlayingList(updatedNowPlayingList);
    }

    /**
     * Test making a browse request where results don't come back within the timeout window. The
     * node should still be notified on.
     */
    @Test
    public void testMakeBrowseRequestWithTimeout_contentsCachedAndNotified() {
        setUpConnectedState(true, true);
        sendAudioFocusUpdate(AudioManager.AUDIOFOCUS_GAIN);

        //Set something arbitrary for the current Now Playing list
        List<AvrcpItem> nowPlayingList = new ArrayList<AvrcpItem>();
        nowPlayingList.add(makeNowPlayingItem(1, "Song 1"));
        setNowPlayingList(nowPlayingList);
        clearInvocations(mAvrcpControllerService);
        clearInvocations(mNativeInterface);

        // Invalidate the contents by doing a new fetch
        BrowseTree.BrowseNode nowPlaying = mAvrcpStateMachine.findNode("NOW_PLAYING");
        mAvrcpStateMachine.requestContents(nowPlaying);
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());

        // Request for new contents should be sent
        verify(mNativeInterface, times(1)).getNowPlayingList(eq(mTestAddress), eq(0), eq(19));
        Assert.assertFalse(nowPlaying.isCached());

        // Send timeout on our own instead of waiting 10 seconds
        mAvrcpStateMachine.sendMessage(AvrcpControllerStateMachine.MESSAGE_INTERNAL_CMD_TIMEOUT);
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());

        // Node should be set to cached and notified on
        assertNowPlayingList(new ArrayList<AvrcpItem>());
        Assert.assertTrue(nowPlaying.isCached());

        // See that state from BluetoothMediaBrowserService is updated to null (i.e. empty)
        MediaSessionCompat session = BluetoothMediaBrowserService.getSession();
        Assert.assertNotNull(session);
        MediaControllerCompat controller = session.getController();
        Assert.assertNotNull(controller);
        List<MediaSessionCompat.QueueItem> queue = controller.getQueue();
        Assert.assertNull(queue);
    }

    /**
     * Test making a browse request with a null node. The request should not generate any native
     * layer browse requests.
     */
    @Test
    public void testNullBrowseRequest_requestDropped() {
        setUpConnectedState(true, true);
        sendAudioFocusUpdate(AudioManager.AUDIOFOCUS_GAIN);
        clearInvocations(mAvrcpControllerService);
        clearInvocations(mNativeInterface);
        mAvrcpStateMachine.requestContents(null);
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());
        verifyNoMoreInteractions(mAvrcpControllerService);
        verifyNoMoreInteractions(mNativeInterface);
    }

    /**
     * Test making a browse request with browsing disconnected. The request should not generate any
     * native layer browse requests.
     */
    @Test
    public void testBrowseRequestWhileDisconnected_requestDropped() {
        final String rootName = "__ROOT__" + mTestDevice.getAddress().toString();
        setUpConnectedState(true, false);
        sendAudioFocusUpdate(AudioManager.AUDIOFOCUS_GAIN);
        clearInvocations(mAvrcpControllerService);
        clearInvocations(mNativeInterface);
        BrowseTree.BrowseNode deviceRoot = mAvrcpStateMachine.findNode(rootName);
        mAvrcpStateMachine.requestContents(deviceRoot);
        TestUtils.waitForLooperToFinishScheduledTask(mAvrcpStateMachine.getHandler().getLooper());
        verifyNoMoreInteractions(mAvrcpControllerService);
        verifyNoMoreInteractions(mNativeInterface);
    }

    /**
     * Queue a control channel connection event, a request while browse is disconnected, a browse
     * connection event, and then another browse request and be sure the final request still is sent
     */
    @Test
    public void testBrowseRequestWhileDisconnectedThenRequestWhileConnected_secondRequestSent() {
        final String rootName = "__ROOT__" + mTestDevice.getAddress().toString();
        setUpConnectedState(true, false);
        sendAudioFocusUpdate(AudioManager.AUDIOFOCUS_GAIN);
        clearInvocations(mAvrcpControllerService);
        clearInvocations(mNativeInterface);
        BrowseTree.BrowseNode deviceRoot = mAvrcpStateMachine.findNode(rootName);
        mAvrcpStateMachine.requestContents(deviceRoot);
        // issues a player list fetch
        mAvrcpStateMachine.connect(StackEvent.connectionStateChanged(true, true));
        TestUtils.waitForLooperToBeIdle(mAvrcpStateMachine.getHandler().getLooper());
        verify(mNativeInterface, times(1)).getPlayerList(eq(mTestAddress), eq(0), eq(19));
    }
}
