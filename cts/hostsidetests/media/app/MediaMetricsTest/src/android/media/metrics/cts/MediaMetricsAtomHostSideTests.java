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

package android.media.metrics.cts;

import static android.media.cts.MediaMetricsTestConstants.LOG_SESSION_ID_KEY;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.device.collectors.util.SendToInstrumentation;
import android.hardware.DataSpace;
import android.media.metrics.BundleSession;
import android.media.metrics.EditingEndedEvent;
import android.media.metrics.EditingSession;
import android.media.metrics.LogSessionId;
import android.media.metrics.MediaItemInfo;
import android.media.metrics.MediaMetricsManager;
import android.media.metrics.NetworkEvent;
import android.media.metrics.PlaybackErrorEvent;
import android.media.metrics.PlaybackMetrics;
import android.media.metrics.PlaybackSession;
import android.media.metrics.PlaybackStateEvent;
import android.media.metrics.RecordingSession;
import android.media.metrics.TrackChangeEvent;
import android.media.metrics.TranscodingSession;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.provider.DeviceConfig;
import android.util.Log;
import android.util.Size;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.DeviceConfigStateHelper;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MediaMetricsAtomHostSideTests {
    private static final String TAG = "MediaMetricsAtomHostSideTests";
    private static final String MEDIA_METRICS_MODE = "media_metrics_mode";
    private static final String PLAYER_METRICS_APP_ALLOWLIST = "player_metrics_app_allowlist";
    private static final String PLAYER_METRICS_APP_BLOCKLIST = "player_metrics_app_blocklist";
    private static final String PLAYER_METRICS_PER_APP_ATTRIBUTION_ALLOWLIST =
            "player_metrics_per_app_attribution_allowlist";
    private static final String PLAYER_METRICS_PER_APP_ATTRIBUTION_BLOCKLIST =
            "player_metrics_per_app_attribution_blocklist";
    private static final String MEDIA_METRICS_MODE_ON = "1";
    private static final String MEDIA_METRICS_MODE_BLOCKLIST = "2";
    private static final String MEDIA_METRICS_MODE_ALLOWLIST = "3";
    private static final String TEST_PKG = "android.media.metrics.cts";
    private static final int DEVICE_PROPERTY_PROPAGATION_DELAY_MICROSECONDS = 1000;

    static {
        System.loadLibrary("CtsMediaMetricsHostTestAppJni");
    }

    private final DeviceConfigStateHelper mDeviceConfigStateHelper =
            new DeviceConfigStateHelper(DeviceConfig.NAMESPACE_MEDIA);

    private void resetProperties() throws InterruptedException {
        // ensure the event/metric is sent.
        Thread.sleep(1000);
        // see DeviceConfig#resetToDefaults(int, String).
        mDeviceConfigStateHelper.restoreOriginalValues();
        // ensure the property is set.
        Thread.sleep(DEVICE_PROPERTY_PROPAGATION_DELAY_MICROSECONDS);
    }

    private void turnOnForTesting() throws InterruptedException {
        mDeviceConfigStateHelper.set(MEDIA_METRICS_MODE, MEDIA_METRICS_MODE_ON);
        // ensure the property is set.
        Thread.sleep(DEVICE_PROPERTY_PROPAGATION_DELAY_MICROSECONDS);
    }

    @Test
    public void testPlaybackStateEvent_default() throws Exception {
        turnOnForTesting();
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        MediaMetricsManager manager = context.getSystemService(MediaMetricsManager.class);
        PlaybackSession s = manager.createPlaybackSession();
        PlaybackStateEvent e =
                new PlaybackStateEvent.Builder().build();
        s.reportPlaybackStateEvent(e);
        resetProperties();
    }


    @Test
    public void testPlaybackStateEvent() throws Exception {
        turnOnForTesting();
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        MediaMetricsManager manager = context.getSystemService(MediaMetricsManager.class);
        PlaybackSession s = manager.createPlaybackSession();
        PlaybackStateEvent e =
                new PlaybackStateEvent.Builder()
                        .setTimeSinceCreatedMillis(1763L)
                        .setState(PlaybackStateEvent.STATE_JOINING_FOREGROUND)
                        .setMetricsBundle(new Bundle())
                        .build();
        s.reportPlaybackStateEvent(e);
        resetProperties();
    }

    @Test
    public void testPlaybackErrorEvent_default() throws Exception {
        turnOnForTesting();
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        MediaMetricsManager manager = context.getSystemService(MediaMetricsManager.class);
        PlaybackSession s = manager.createPlaybackSession();
        PlaybackErrorEvent e =
                new PlaybackErrorEvent.Builder()
                        .setException(new Exception(""))
                        .build();
        s.reportPlaybackErrorEvent(e);
        resetProperties();
    }

    @Test
    public void testPlaybackErrorEvent() throws Exception {
        turnOnForTesting();
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        MediaMetricsManager manager = context.getSystemService(MediaMetricsManager.class);
        PlaybackSession s = manager.createPlaybackSession();
        PlaybackErrorEvent e =
                new PlaybackErrorEvent.Builder()
                        .setTimeSinceCreatedMillis(17630000L)
                        .setErrorCode(PlaybackErrorEvent.ERROR_RUNTIME)
                        .setSubErrorCode(378)
                        .setException(new Exception("test exception"))
                        .setMetricsBundle(new Bundle())
                        .build();
        s.reportPlaybackErrorEvent(e);
        resetProperties();
    }

    @Test
    public void testTrackChangeEvent_default() throws Exception {
        turnOnForTesting();
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        MediaMetricsManager manager = context.getSystemService(MediaMetricsManager.class);
        PlaybackSession s = manager.createPlaybackSession();
        TrackChangeEvent e =
                new TrackChangeEvent.Builder(TrackChangeEvent.TRACK_TYPE_AUDIO).build();
        s.reportTrackChangeEvent(e);
        resetProperties();
    }

    @Test
    public void testTrackChangeEvent_text() throws Exception {
        turnOnForTesting();
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        MediaMetricsManager manager = context.getSystemService(MediaMetricsManager.class);
        PlaybackSession s = manager.createPlaybackSession();
        TrackChangeEvent e =
                new TrackChangeEvent.Builder(TrackChangeEvent.TRACK_TYPE_TEXT)
                        .setTimeSinceCreatedMillis(37278L)
                        .setTrackState(TrackChangeEvent.TRACK_STATE_ON)
                        .setTrackChangeReason(TrackChangeEvent.TRACK_CHANGE_REASON_MANUAL)
                        .setContainerMimeType("text/foo")
                        .setSampleMimeType("text/plain")
                        .setCodecName("codec_1")
                        .setBitrate(1024)
                        .setLanguage("EN")
                        .setLanguageRegion("US")
                        .build();
        s.reportTrackChangeEvent(e);
        resetProperties();
    }

    @Test
    public void testTrackChangeEvent_audio() throws Exception {
        turnOnForTesting();
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        MediaMetricsManager manager = context.getSystemService(MediaMetricsManager.class);
        PlaybackSession s = manager.createPlaybackSession();
        TrackChangeEvent e =
                new TrackChangeEvent.Builder(TrackChangeEvent.TRACK_TYPE_AUDIO)
                        .setTimeSinceCreatedMillis(37278L)
                        .setTrackState(TrackChangeEvent.TRACK_STATE_OFF)
                        .setTrackChangeReason(TrackChangeEvent.TRACK_CHANGE_REASON_INITIAL)
                        .setContainerMimeType("audio/foo")
                        .setSampleMimeType("audio/avc")
                        .setCodecName("codec_2")
                        .setBitrate(1025)
                        .setLanguage("EN")
                        .setLanguageRegion("US")
                        .setAudioSampleRate(89)
                        .setChannelCount(3)
                        .build();
        s.reportTrackChangeEvent(e);
        resetProperties();
    }

    @Test
    public void testTrackChangeEvent_video() throws Exception {
        turnOnForTesting();
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        MediaMetricsManager manager = context.getSystemService(MediaMetricsManager.class);
        PlaybackSession s = manager.createPlaybackSession();
        TrackChangeEvent e =
                new TrackChangeEvent.Builder(TrackChangeEvent.TRACK_TYPE_VIDEO)
                        .setTimeSinceCreatedMillis(37278L)
                        .setTrackState(TrackChangeEvent.TRACK_STATE_OFF)
                        .setTrackChangeReason(TrackChangeEvent.TRACK_CHANGE_REASON_INITIAL)
                        .setContainerMimeType("video/foo")
                        .setSampleMimeType("video/mpeg")
                        .setCodecName("codec_3")
                        .setBitrate(1025)
                        .setLanguage("EN")
                        .setLanguageRegion("US")
                        .setHeight(1080)
                        .setWidth(1440)
                        .setVideoFrameRate(60)
                        .setMetricsBundle(new Bundle())
                        .build();
        s.reportTrackChangeEvent(e);
        resetProperties();
    }

    @Test
    public void testNetworkEvent_default() throws Exception {
        turnOnForTesting();
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        MediaMetricsManager manager = context.getSystemService(MediaMetricsManager.class);
        PlaybackSession s = manager.createPlaybackSession();
        NetworkEvent e =
                new NetworkEvent.Builder().build();
        s.reportNetworkEvent(e);
        resetProperties();
    }

    @Test
    public void testNetworkEvent() throws Exception {
        turnOnForTesting();
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        MediaMetricsManager manager = context.getSystemService(MediaMetricsManager.class);
        PlaybackSession s = manager.createPlaybackSession();
        NetworkEvent e =
                new NetworkEvent.Builder()
                        .setTimeSinceCreatedMillis(3032L)
                        .setNetworkType(NetworkEvent.NETWORK_TYPE_WIFI)
                        .setMetricsBundle(new Bundle())
                        .build();
        s.reportNetworkEvent(e);
        resetProperties();
    }

    @Test
    public void testPlaybackMetrics_default() throws Exception {
        turnOnForTesting();
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        MediaMetricsManager manager = context.getSystemService(MediaMetricsManager.class);
        PlaybackSession s = manager.createPlaybackSession();
        PlaybackMetrics e =
                new PlaybackMetrics.Builder().build();
        s.reportPlaybackMetrics(e);
        resetProperties();
    }

    @Test
    public void testPlaybackMetrics() throws Exception {
        turnOnForTesting();
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        MediaMetricsManager manager = context.getSystemService(MediaMetricsManager.class);
        PlaybackSession s = manager.createPlaybackSession();
        PlaybackMetrics e =
                new PlaybackMetrics.Builder()
                        .setMediaDurationMillis(233L)
                        .setStreamSource(PlaybackMetrics.STREAM_SOURCE_NETWORK)
                        .setStreamType(PlaybackMetrics.STREAM_TYPE_OTHER)
                        .setPlaybackType(PlaybackMetrics.PLAYBACK_TYPE_LIVE)
                        .setDrmType(PlaybackMetrics.DRM_TYPE_WIDEVINE_L1)
                        .setContentType(PlaybackMetrics.CONTENT_TYPE_MAIN)
                        .setPlayerName("ExoPlayer")
                        .setPlayerVersion("1.01x")
                        .setVideoFramesPlayed(1024)
                        .setVideoFramesDropped(32)
                        .setAudioUnderrunCount(22)
                        .setNetworkBytesRead(102400)
                        .setLocalBytesRead(2000)
                        .setNetworkTransferDurationMillis(6000)
                        .setDrmSessionId(new byte[]{2, 3, 3, 10})
                        .setMetricsBundle(new Bundle())
                        .addExperimentId(123)
                        .build();
        s.reportPlaybackMetrics(e);
        resetProperties();
    }

    @Test
    public void testSessionId() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        MediaMetricsManager manager = context.getSystemService(MediaMetricsManager.class);

        try (PlaybackSession s = manager.createPlaybackSession()) {
            LogSessionId idObj = s.getSessionId();
            assertThat(idObj).isNotEqualTo(null);
            assertThat(idObj.getStringId().length()).isGreaterThan(0);
        }
    }

    @Test
    public void testRecordingSession() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        MediaMetricsManager manager = context.getSystemService(MediaMetricsManager.class);

        try (RecordingSession s = manager.createRecordingSession()) {
            assertThat(s).isNotEqualTo(null);
            LogSessionId idObj = s.getSessionId();
            assertThat(idObj).isNotEqualTo(null);
            assertThat(idObj.getStringId().length()).isGreaterThan(0);
        }
    }

    @Test
    public void testEditingSession() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        MediaMetricsManager manager = context.getSystemService(MediaMetricsManager.class);

        try (EditingSession s = manager.createEditingSession()) {
            assertThat(s).isNotEqualTo(null);
            LogSessionId idObj = s.getSessionId();
            assertThat(idObj).isNotEqualTo(null);
            assertThat(idObj.getStringId().length()).isGreaterThan(0);
        }
    }

    @Test
    public void testEditingEndedEvent_default() throws Exception {
        turnOnForTesting();
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        MediaMetricsManager manager = context.getSystemService(MediaMetricsManager.class);
        try (EditingSession s = manager.createEditingSession()) {
            EditingEndedEvent e =
                    new EditingEndedEvent.Builder(EditingEndedEvent.FINAL_STATE_SUCCEEDED).build();
            s.reportEditingEndedEvent(e);
        }
        resetProperties();
    }

    @Test
    public void testEditingEndedEvent_error() throws Exception {
        turnOnForTesting();
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        MediaMetricsManager manager = context.getSystemService(MediaMetricsManager.class);
        try (EditingSession s = manager.createEditingSession()) {
            EditingEndedEvent e =
                    new EditingEndedEvent.Builder(EditingEndedEvent.FINAL_STATE_ERROR)
                            .setTimeSinceCreatedMillis(17630000L)
                            .setFinalProgressPercent(10f)
                            .setExporterName("some.unknown.library:1.0-alpha01")
                            .setMuxerName("androidx.media3:media3-muxer:1.2.1")
                            .setErrorCode(PlaybackErrorEvent.ERROR_RUNTIME)
                            .setMetricsBundle(new Bundle())
                            .build();
            s.reportEditingEndedEvent(e);
            resetProperties();
        }
    }

    @Test
    public void testEditingEndedEvent_transcodeAndClip() throws Exception {
        turnOnForTesting();
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        MediaMetricsManager manager = context.getSystemService(MediaMetricsManager.class);
        try (EditingSession s = manager.createEditingSession()) {
            // Video: H.265 HDR (HLG, BT.2020) -> H.264 (SDR) transcoding
            // Audio: transmuxing
            // Audio and video are both clipped from 4 s to 3 s.
            // Video is downscaled from portrait 1080p to portrait 720p.
            MediaItemInfo inputMediaItemInfo =
                    new MediaItemInfo.Builder()
                            .setSourceType(MediaItemInfo.SOURCE_TYPE_CAMERA)
                            .addDataType(MediaItemInfo.DATA_TYPE_VIDEO)
                            .addDataType(MediaItemInfo.DATA_TYPE_AUDIO)
                            .addDataType(MediaItemInfo.DATA_TYPE_HIGH_DYNAMIC_RANGE_VIDEO)
                            .setDurationMillis(4_000)
                            .setClipDurationMillis(3_000)
                            .setContainerMimeType("video/mp4")
                            .addSampleMimeType("video/hevc")
                            .addSampleMimeType("audio/mp4a-latm")
                            .addCodecName("c2.android.hevc.decoder")
                            .addCodecName("c2.android.aac.decoder")
                            .setAudioSampleRateHz(44100)
                            .setAudioChannelCount(2)
                            .setAudioSampleCount(10_000)
                            .setVideoSize(new Size(1080, 1920))
                            .setVideoDataSpace(
                                    DataSpace.pack(
                                            DataSpace.STANDARD_BT2020,
                                            DataSpace.TRANSFER_HLG,
                                            DataSpace.RANGE_FULL))
                            .setVideoFrameRate(25f)
                            .setVideoSampleCount(100)
                            .build();
            MediaItemInfo outputMediaItemInfo =
                    new MediaItemInfo.Builder()
                            .addDataType(MediaItemInfo.DATA_TYPE_VIDEO)
                            .addDataType(MediaItemInfo.DATA_TYPE_AUDIO)
                            .setDurationMillis(3_000)
                            .setContainerMimeType("video/mp4")
                            .addSampleMimeType("video/avc")
                            .addSampleMimeType("audio/mp4a-latm")
                            .addCodecName("c2.android.avc.encoder")
                            .addCodecName("c2.android.aac.encoder")
                            .setAudioSampleRateHz(44100)
                            .setAudioChannelCount(2)
                            .setAudioSampleCount(10_000)
                            .setVideoSize(new Size(720, 1280))
                            .setVideoDataSpace(
                                    DataSpace.pack(
                                            DataSpace.STANDARD_BT709,
                                            DataSpace.TRANSFER_LINEAR,
                                            DataSpace.RANGE_FULL))
                            .setVideoFrameRate(25f)
                            .setVideoSampleCount(100)
                            .build();
            EditingEndedEvent e =
                    new EditingEndedEvent.Builder(EditingEndedEvent.FINAL_STATE_SUCCEEDED)
                            .setFinalProgressPercent(100f)
                            .setTimeSinceCreatedMillis(2_000)
                            .setExporterName("androidx.media3:media3-transformer:1.3.0-beta01")
                            .setMuxerName("androidx.media3:media3-muxer:1.3.0-beta01")
                            .addOperationType(EditingEndedEvent.OPERATION_TYPE_VIDEO_TRANSCODE)
                            .addOperationType(EditingEndedEvent.OPERATION_TYPE_AUDIO_TRANSMUX)
                            .addOperationType(EditingEndedEvent.OPERATION_TYPE_AUDIO_EDIT)
                            .addOperationType(EditingEndedEvent.OPERATION_TYPE_VIDEO_EDIT)
                            .addInputMediaItemInfo(inputMediaItemInfo)
                            .setOutputMediaItemInfo(outputMediaItemInfo)
                            .setMetricsBundle(new Bundle())
                            .build();
            s.reportEditingEndedEvent(e);
            resetProperties();
        }
    }

    @Test
    public void testTranscodingSession() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        MediaMetricsManager manager = context.getSystemService(MediaMetricsManager.class);

        try (TranscodingSession s = manager.createTranscodingSession()) {
            assertThat(s).isNotEqualTo(null);
            LogSessionId idObj = s.getSessionId();
            assertThat(idObj).isNotEqualTo(null);
            assertThat(idObj.getStringId().length()).isGreaterThan(0);
        }
    }

    @Test
    public void testBundleSession() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        MediaMetricsManager manager = context.getSystemService(MediaMetricsManager.class);

        try (BundleSession s = manager.createBundleSession()) {
            assertThat(s).isNotEqualTo(null);
            LogSessionId idObj = s.getSessionId();
            assertThat(idObj).isNotEqualTo(null);
            assertThat(idObj.getStringId().length()).isGreaterThan(0);
        }
    }

    @Test
    public void testBundleSessionPlaybackStateEvent() throws Exception {
        turnOnForTesting();
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        MediaMetricsManager manager = context.getSystemService(MediaMetricsManager.class);
        BundleSession s = manager.createBundleSession();
        PersistableBundle b = new PersistableBundle();
        b.putInt(BundleSession.KEY_STATSD_ATOM, 322);
        // eventually want these keys from within the service side.
        b.putString("playbackstateevent-sessionid", s.getSessionId().getStringId());
        b.putInt("playbackstateevent-state", PlaybackStateEvent.STATE_JOINING_FOREGROUND);
        b.putLong("playbackstateevent-lifetime", 1763L);
        s.reportBundleMetrics(b);
        resetProperties();
    }

    @Test
    public void testAppBlocklist() throws Exception {
        mDeviceConfigStateHelper.set(
                new DeviceConfig.Properties.Builder(DeviceConfig.NAMESPACE_MEDIA)
                        .setString(MEDIA_METRICS_MODE, MEDIA_METRICS_MODE_BLOCKLIST)
                        .setString(PLAYER_METRICS_PER_APP_ATTRIBUTION_BLOCKLIST, "")
                        .setString(PLAYER_METRICS_APP_BLOCKLIST, TEST_PKG)
                        .build());
        Thread.sleep(DEVICE_PROPERTY_PROPAGATION_DELAY_MICROSECONDS);
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        MediaMetricsManager manager = context.getSystemService(MediaMetricsManager.class);
        PlaybackSession s = manager.createPlaybackSession();
        PlaybackStateEvent e =
                new PlaybackStateEvent.Builder()
                        .setTimeSinceCreatedMillis(1763L)
                        .setState(PlaybackStateEvent.STATE_JOINING_FOREGROUND)
                        .setMetricsBundle(new Bundle())
                        .build();
        s.reportPlaybackStateEvent(e);
        writeSessionIdToFile(s.getSessionId().getStringId());
        resetProperties();
    }

    @Test
    public void testAttributionBlocklist() throws Exception {
        mDeviceConfigStateHelper.set(
                new DeviceConfig.Properties.Builder(DeviceConfig.NAMESPACE_MEDIA)
                        .setString(MEDIA_METRICS_MODE, MEDIA_METRICS_MODE_BLOCKLIST)
                        .setString(PLAYER_METRICS_PER_APP_ATTRIBUTION_BLOCKLIST, TEST_PKG)
                        .setString(PLAYER_METRICS_APP_BLOCKLIST, "")
                        .build());
        Thread.sleep(DEVICE_PROPERTY_PROPAGATION_DELAY_MICROSECONDS);
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        MediaMetricsManager manager = context.getSystemService(MediaMetricsManager.class);
        PlaybackSession s = manager.createPlaybackSession();
        PlaybackMetrics e =
                new PlaybackMetrics.Builder()
                        .setMediaDurationMillis(233L)
                        .setStreamSource(PlaybackMetrics.STREAM_SOURCE_NETWORK)
                        .setStreamType(PlaybackMetrics.STREAM_TYPE_OTHER)
                        .setPlaybackType(PlaybackMetrics.PLAYBACK_TYPE_LIVE)
                        .setDrmType(PlaybackMetrics.DRM_TYPE_WIDEVINE_L1)
                        .setContentType(PlaybackMetrics.CONTENT_TYPE_MAIN)
                        .setPlayerName("ExoPlayer")
                        .setPlayerVersion("1.01x")
                        .setVideoFramesPlayed(1024)
                        .setVideoFramesDropped(32)
                        .setAudioUnderrunCount(22)
                        .setNetworkBytesRead(102400)
                        .setLocalBytesRead(2000)
                        .setNetworkTransferDurationMillis(6000)
                        .setDrmSessionId(new byte[]{2, 3, 3, 10})
                        .setMetricsBundle(new Bundle())
                        .addExperimentId(123)
                        .build();
        s.reportPlaybackMetrics(e);
        writeSessionIdToFile(s.getSessionId().getStringId());
        resetProperties();
    }

    @Test
    public void testAppAllowlist() throws Exception {
        mDeviceConfigStateHelper.set(
                new DeviceConfig.Properties.Builder(DeviceConfig.NAMESPACE_MEDIA)
                        .setString(MEDIA_METRICS_MODE, MEDIA_METRICS_MODE_ALLOWLIST)
                        .setString(PLAYER_METRICS_PER_APP_ATTRIBUTION_ALLOWLIST, "")
                        .setString(PLAYER_METRICS_APP_ALLOWLIST, TEST_PKG)
                        .build());
        Thread.sleep(DEVICE_PROPERTY_PROPAGATION_DELAY_MICROSECONDS);
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        MediaMetricsManager manager = context.getSystemService(MediaMetricsManager.class);
        PlaybackSession s = manager.createPlaybackSession();
        PlaybackStateEvent e =
                new PlaybackStateEvent.Builder()
                        .setTimeSinceCreatedMillis(1763L)
                        .setState(PlaybackStateEvent.STATE_JOINING_FOREGROUND)
                        .setMetricsBundle(new Bundle())
                        .build();
        s.reportPlaybackStateEvent(e);
        writeSessionIdToFile(s.getSessionId().getStringId());
        resetProperties();
    }

    @Test
    public void testAttributionAllowlist() throws Exception {
        mDeviceConfigStateHelper.set(
                new DeviceConfig.Properties.Builder(DeviceConfig.NAMESPACE_MEDIA)
                        .setString(MEDIA_METRICS_MODE, MEDIA_METRICS_MODE_ALLOWLIST)
                        .setString(PLAYER_METRICS_PER_APP_ATTRIBUTION_ALLOWLIST, TEST_PKG)
                        .setString(PLAYER_METRICS_APP_ALLOWLIST, "")
                        .build());
        Thread.sleep(DEVICE_PROPERTY_PROPAGATION_DELAY_MICROSECONDS);
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        MediaMetricsManager manager = context.getSystemService(MediaMetricsManager.class);
        PlaybackSession s = manager.createPlaybackSession();
        PlaybackMetrics e =
                new PlaybackMetrics.Builder()
                        .setMediaDurationMillis(233L)
                        .setStreamSource(PlaybackMetrics.STREAM_SOURCE_NETWORK)
                        .setStreamType(PlaybackMetrics.STREAM_TYPE_OTHER)
                        .setPlaybackType(PlaybackMetrics.PLAYBACK_TYPE_LIVE)
                        .setDrmType(PlaybackMetrics.DRM_TYPE_WIDEVINE_L1)
                        .setContentType(PlaybackMetrics.CONTENT_TYPE_MAIN)
                        .setPlayerName("ExoPlayer")
                        .setPlayerVersion("1.01x")
                        .setVideoFramesPlayed(1024)
                        .setVideoFramesDropped(32)
                        .setAudioUnderrunCount(22)
                        .setNetworkBytesRead(102400)
                        .setLocalBytesRead(2000)
                        .setNetworkTransferDurationMillis(6000)
                        .setDrmSessionId(new byte[]{2, 3, 3, 10})
                        .setMetricsBundle(new Bundle())
                        .addExperimentId(123)
                        .build();
        s.reportPlaybackMetrics(e);
        writeSessionIdToFile(s.getSessionId().getStringId());
        resetProperties();
    }

    /**
     * Open aaudio low latency output stream and then close
     */
    @Test
    public native void testAAudioLowLatencyOutputStream();

    /**
     * Open aaudio low latency input stream and then close
     */
    @Test
    public native void testAAudioLowLatencyInputStream();

    /**
     * Open aaudio legacy output stream and then close
     */
    @Test
    public native void testAAudioLegacyOutputStream();

    /**
     * Open aaudio legacy input stream and then close
     */
    @Test
    public native void testAAudioLegacyInputStream();

    @Test
    public void testMidiMetrics() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        MidiTestHelper midiTestHelper = new MidiTestHelper(context);
        midiTestHelper.testEchoVariableMessage(9 /* 9 bytes */);
    }

    private void writeSessionIdToFile(String stringId) {
        Log.i(TAG, LOG_SESSION_ID_KEY + "=" + stringId);
        Bundle b = new Bundle();
        b.putString(LOG_SESSION_ID_KEY, stringId);
        SendToInstrumentation.sendBundle(InstrumentationRegistry.getInstrumentation(), b);
    }
}
