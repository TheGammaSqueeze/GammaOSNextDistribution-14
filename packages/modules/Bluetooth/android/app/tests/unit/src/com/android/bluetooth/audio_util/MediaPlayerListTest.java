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

package com.android.bluetooth.audio_util;

import static org.mockito.Mockito.*;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.session.MediaSession;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Looper;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class MediaPlayerListTest {
    private MediaPlayerList mMediaPlayerList;

    private @Captor ArgumentCaptor<AudioManager.AudioPlaybackCallback> mAudioCb;
    private @Captor ArgumentCaptor<MediaPlayerWrapper.Callback> mPlayerWrapperCb;
    private @Captor ArgumentCaptor<MediaData> mMediaUpdateData;
    private @Mock Context mMockContext;
    private @Mock MediaPlayerList.MediaUpdateCallback mMediaUpdateCallback;
    private @Mock MediaController mMockController;
    private @Mock MediaPlayerWrapper mMockPlayerWrapper;

    private final String mFlagDexmarker = System.getProperty("dexmaker.share_classloader", "false");
    private MediaPlayerWrapper.Callback mActivePlayerCallback;
    private MediaSessionManager mMediaSessionManager;

    @Before
    public void setUp() throws Exception {
        if (!mFlagDexmarker.equals("true")) {
            System.setProperty("dexmaker.share_classloader", "true");
        }

        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        Assert.assertNotNull(Looper.myLooper());

        MockitoAnnotations.initMocks(this);

        AudioManager mockAudioManager = mock(AudioManager.class);
        when(mMockContext.getSystemService(Context.AUDIO_SERVICE)).thenReturn(mockAudioManager);
        when(mMockContext.getSystemServiceName(AudioManager.class))
            .thenReturn(Context.AUDIO_SERVICE);

        mMediaSessionManager = InstrumentationRegistry.getTargetContext()
                .getSystemService(MediaSessionManager.class);
        PackageManager mockPackageManager = mock(PackageManager.class);
        when(mMockContext.getSystemService(Context.MEDIA_SESSION_SERVICE))
            .thenReturn(mMediaSessionManager);
        when(mMockContext.getSystemServiceName(MediaSessionManager.class))
            .thenReturn(Context.MEDIA_SESSION_SERVICE);

        mMediaPlayerList =
            new MediaPlayerList(Looper.myLooper(), InstrumentationRegistry.getTargetContext());

        when(mMockContext.registerReceiver(any(), any())).thenReturn(null);
        when(mMockContext.getApplicationContext()).thenReturn(mMockContext);
        when(mMockContext.getPackageManager()).thenReturn(mockPackageManager);
        when(mockPackageManager.queryIntentServices(any(), anyInt())).thenReturn(null);

        BrowsablePlayerConnector mockConnector = mock(BrowsablePlayerConnector.class);
        BrowsablePlayerConnector.setInstanceForTesting(mockConnector);
        mMediaPlayerList.init(mMediaUpdateCallback);

        MediaControllerFactory.inject(mMockController);
        MediaPlayerWrapperFactory.inject(mMockPlayerWrapper);

        doReturn("testPlayer").when(mMockController).getPackageName();
        when(mMockPlayerWrapper.isMetadataSynced()).thenReturn(false);
        mMediaPlayerList.setActivePlayer(mMediaPlayerList.addMediaPlayer(mMockController));

        verify(mMockPlayerWrapper).registerCallback(mPlayerWrapperCb.capture());
        mActivePlayerCallback = mPlayerWrapperCb.getValue();
    }

    @After
    public void tearDown() throws Exception {
        BrowsablePlayerConnector.setInstanceForTesting(null);


        MediaControllerFactory.inject(null);
        MediaPlayerWrapperFactory.inject(null);
        mMediaPlayerList.cleanup();
        if (!mFlagDexmarker.equals("true")) {
            System.setProperty("dexmaker.share_classloader", mFlagDexmarker);
        }
    }

    private MediaData prepareMediaData(int playbackState) {
        PlaybackState.Builder builder = new PlaybackState.Builder();
        builder.setState(playbackState, 0, 1);
        ArrayList<Metadata> list = new ArrayList<Metadata>();
        list.add(Util.empty_data());
        MediaData newData = new MediaData(
                Util.empty_data(),
                builder.build(),
                list);

        return newData;
    }

    @Test
    public void testUpdateMeidaDataForAudioPlaybackWhenAcitvePlayNotPlaying() {
        // Verify update media data with playing state
        doReturn(prepareMediaData(PlaybackState.STATE_PAUSED))
            .when(mMockPlayerWrapper).getCurrentMediaData();
        mMediaPlayerList.injectAudioPlaybacActive(true);
        verify(mMediaUpdateCallback).run(mMediaUpdateData.capture());
        MediaData data = mMediaUpdateData.getValue();
        Assert.assertEquals(data.state.getState(), PlaybackState.STATE_PLAYING);

        // verify update media data with current media player media data
        MediaData currentMediaData = prepareMediaData(PlaybackState.STATE_PAUSED);
        doReturn(currentMediaData).when(mMockPlayerWrapper).getCurrentMediaData();
        mMediaPlayerList.injectAudioPlaybacActive(false);
        verify(mMediaUpdateCallback, times(2)).run(mMediaUpdateData.capture());
        data = mMediaUpdateData.getValue();
        Assert.assertEquals(data.metadata, currentMediaData.metadata);
        Assert.assertEquals(data.state.toString(), currentMediaData.state.toString());
        Assert.assertEquals(data.queue, currentMediaData.queue);
    }

    @Test
    public void testUpdateMediaDataForActivePlayerWhenAudioPlaybackIsNotActive() {
        MediaData currMediaData = prepareMediaData(PlaybackState.STATE_PLAYING);
        mActivePlayerCallback.mediaUpdatedCallback(currMediaData);
        verify(mMediaUpdateCallback).run(currMediaData);

        currMediaData = prepareMediaData(PlaybackState.STATE_PAUSED);
        mActivePlayerCallback.mediaUpdatedCallback(currMediaData);
        verify(mMediaUpdateCallback).run(currMediaData);
    }

    @Test
    public void testNotUdpateMediaDataForAudioPlaybackWhenActivePlayerIsPlaying() {
        // Verify not update media data for Audio Playback when active player is playing
        doReturn(prepareMediaData(PlaybackState.STATE_PLAYING))
            .when(mMockPlayerWrapper).getCurrentMediaData();
        mMediaPlayerList.injectAudioPlaybacActive(true);
        mMediaPlayerList.injectAudioPlaybacActive(false);
        verify(mMediaUpdateCallback, never()).run(any());
    }

    @Test
    public void testNotUdpateMediaDataForActivePlayerWhenAudioPlaybackIsActive() {
        doReturn(prepareMediaData(PlaybackState.STATE_PLAYING))
            .when(mMockPlayerWrapper).getCurrentMediaData();
        mMediaPlayerList.injectAudioPlaybacActive(true);
        verify(mMediaUpdateCallback, never()).run(any());

        // Verify not update active player media data when audio playback is active
        mActivePlayerCallback.mediaUpdatedCallback(prepareMediaData(PlaybackState.STATE_PAUSED));
        verify(mMediaUpdateCallback, never()).run(any());
    }

    @Test
    public void testSkipGlobalPrioritySession() {
        // Store current active media player.
        MediaPlayerWrapper activeMediaPlayer = mMediaPlayerList.getActivePlayer();

        // Create MediaSession with GLOBAL_PRIORITY flag.
        MediaSession session = new MediaSession(
                InstrumentationRegistry.getTargetContext(),
                MediaPlayerListTest.class.getSimpleName());
        session.setFlags(MediaSession.FLAG_EXCLUSIVE_GLOBAL_PRIORITY
                | MediaSession.FLAG_HANDLES_MEDIA_BUTTONS);

        // Use MediaPlayerList onMediaKeyEventSessionChanged callback to send the new session.
        mMediaPlayerList.mMediaKeyEventSessionChangedListener.onMediaKeyEventSessionChanged(
                session.getController().getPackageName(), session.getSessionToken());

        // Retrieve the current available controllers
        ArrayList<android.media.session.MediaController> currentControllers =
                new ArrayList<android.media.session.MediaController>(
                mMediaSessionManager.getActiveSessions(null));
        // Add the new session
        currentControllers.add(session.getController());
        // Use MediaPlayerList onActiveSessionsChanged callback to send the new session.
        mMediaPlayerList.mActiveSessionsChangedListener.onActiveSessionsChanged(
                currentControllers);

        // Retrieve the new active MediaSession.
        MediaPlayerWrapper newActiveMediaPlayer = mMediaPlayerList.getActivePlayer();

        // Should be the same as before.
        Assert.assertEquals(activeMediaPlayer, newActiveMediaPlayer);

        session.release();
    }
}
