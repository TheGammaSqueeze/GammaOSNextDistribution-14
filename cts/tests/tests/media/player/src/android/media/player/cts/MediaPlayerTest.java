/*
 * Copyright (C) 2009 The Android Open Source Project
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
package android.media.player.cts;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertSame;
import static junit.framework.TestCase.assertTrue;
import static junit.framework.TestCase.fail;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import android.content.ContentProvider;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Rect;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.CamcorderProfile;
import android.media.MediaDataSource;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnSeekCompleteListener;
import android.media.MediaPlayer.OnTimedTextListener;
import android.media.MediaRecorder;
import android.media.MediaTimestamp;
import android.media.PlaybackParams;
import android.media.SyncParams;
import android.media.TimedText;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.Visualizer;
import android.media.cts.MediaPlayerTestBase;
import android.media.cts.TestMediaDataSource;
import android.media.cts.TestUtils.Monitor;
import android.media.cts.Utils;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresDevice;
import android.util.Log;

import androidx.core.content.FileProvider;
import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.MediaUtils;
import com.android.compatibility.common.util.NonMainlineTest;
import com.android.compatibility.common.util.Preconditions;

import junit.framework.AssertionFailedError;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.UUID;
import java.util.Vector;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Tests for the MediaPlayer API and local video/audio playback.
 *
 */
@SmallTest
@RequiresDevice
@NonMainlineTest
@AppModeFull(reason = "TODO: evaluate and port to instant")
@RunWith(AndroidJUnit4.class)
public class MediaPlayerTest extends MediaPlayerTestBase {

    private String RECORDED_FILE;
    private static final String LOG_TAG = "MediaPlayerTest";

    static final String mInpPrefix = WorkDir.getMediaDirString();

    private static final int  RECORDED_VIDEO_WIDTH  = 176;
    private static final int  RECORDED_VIDEO_HEIGHT = 144;
    private static final long RECORDED_DURATION_MS  = 3000;
    private static final float FLOAT_TOLERANCE = .0001f;
    private static final int PLAYBACK_DURATION_MS  = 10000;
    private static final int ANR_DETECTION_TIME_MS  = 20000;

    private final Vector<Integer> mTimedTextTrackIndex = new Vector<>();
    private final Monitor mOnTimedTextCalled = new Monitor();
    private int mSelectedTimedTextIndex;

    private final Vector<Integer> mSubtitleTrackIndex = new Vector<>();
    private final Monitor mOnSubtitleDataCalled = new Monitor();
    private int mSelectedSubtitleIndex;

    private final Monitor mOnMediaTimeDiscontinuityCalled = new Monitor();

    private File mOutFile;

    private int mBoundsCount;

    @Override
    @Before
    public void setUp() throws Throwable {
        super.setUp();
        RECORDED_FILE = new File(Environment.getExternalStorageDirectory(),
                "mediaplayer_record.out").getAbsolutePath();
        mOutFile = new File(RECORDED_FILE);
    }

    @Override
    @After
    public void tearDown() {
        if (mOutFile != null && mOutFile.exists()) {
            mOutFile.delete();
        }
        super.tearDown();
    }

    @Presubmit
    @Test
    public void testFlacHeapOverflow() throws Exception {
        testIfMediaServerDied("heap_oob_flac.mp3");
    }

    private static AssetFileDescriptor getAssetFileDescriptorFor(final String res)
            throws FileNotFoundException {
        Preconditions.assertTestFileExists(mInpPrefix + res);
        File inpFile = new File(mInpPrefix + res);
        ParcelFileDescriptor parcelFD =
                ParcelFileDescriptor.open(inpFile, ParcelFileDescriptor.MODE_READ_ONLY);
        return new AssetFileDescriptor(parcelFD, 0, parcelFD.getStatSize());
    }

    private void loadSubtitleSource(String res) throws Exception {
        try (AssetFileDescriptor afd = getAssetFileDescriptorFor(res)) {
            mMediaPlayer.addTimedTextSource(afd.getFileDescriptor(), afd.getStartOffset(),
                    afd.getLength(), MediaPlayer.MEDIA_MIMETYPE_TEXT_SUBRIP);
        }
    }

    // returns true on success
    private boolean loadResource(final String res) throws Exception {
        Preconditions.assertTestFileExists(mInpPrefix + res);
        if (!MediaUtils.hasCodecsForResource(mInpPrefix + res)) {
            return false;
        }

        try (AssetFileDescriptor afd = getAssetFileDescriptorFor(res)) {
            mMediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(),
                    afd.getLength());

            // Although it is only meant for video playback, it should not
            // cause issues for audio-only playback.
            int videoScalingMode = sUseScaleToFitMode ?
                    MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT
                    : MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING;

            mMediaPlayer.setVideoScalingMode(videoScalingMode);
        }
        sUseScaleToFitMode = !sUseScaleToFitMode;  // Alternate the scaling mode
        return true;
    }

    private boolean checkLoadResource(String res) throws Exception {
        return MediaUtils.check(loadResource(res), "no decoder found");
    }

    private void playLoadedVideoTest(final String res, int width, int height) throws Exception {
        if (!checkLoadResource(res)) {
            return; // skip
        }

        playLoadedVideo(width, height, 0);
    }

    private void testIfMediaServerDied(final String res) throws Exception {
        mMediaPlayer.setOnErrorListener((mp, what, extra) -> {
            assertSame(mp, mMediaPlayer);
            assertTrue("mediaserver process died", what != MediaPlayer.MEDIA_ERROR_SERVER_DIED);
            Log.w(LOG_TAG, "onError " + what);
            return false;
        });

        mMediaPlayer.setOnCompletionListener(mp -> {
            assertSame(mp, mMediaPlayer);
            mOnCompletionCalled.signal();
        });

        AssetFileDescriptor afd = getAssetFileDescriptorFor(res);
        mMediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
        afd.close();
        try {
            mMediaPlayer.prepare();
            mMediaPlayer.start();
            if (!mOnCompletionCalled.waitForSignal(5000)) {
                Log.w(LOG_TAG, "testIfMediaServerDied: Timed out waiting for Error/Completion");
            }
        } catch (Exception e) {
            Log.w(LOG_TAG, "playback failed", e);
        } finally {
            mMediaPlayer.release();
        }
    }

    // Bug 13652927
    @Test
    public void testVorbisCrash() throws Exception {
        MediaPlayer mp = mMediaPlayer;
        MediaPlayer mp2 = mMediaPlayer2;
        AssetFileDescriptor afd2 = getAssetFileDescriptorFor("testmp3_2.mp3");
        mp2.setDataSource(afd2.getFileDescriptor(), afd2.getStartOffset(), afd2.getLength());
        afd2.close();
        mp2.prepare();
        mp2.setLooping(true);
        mp2.start();

        for (int i = 0; i < 20; i++) {
            try {
                AssetFileDescriptor afd = getAssetFileDescriptorFor("bug13652927.ogg");
                mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
                mp.prepare();
                fail("shouldn't be here");
            } catch (Exception e) {
                // expected to fail
                Log.i("@@@", "failed: " + e);
            }
            Thread.sleep(500);
            assertTrue("media server died", mp2.isPlaying());
            mp.reset();
        }
    }

    @Presubmit
    @Test
    public void testPlayNullSourcePath() throws Exception {
        try {
            mMediaPlayer.setDataSource((String) null);
            fail("Null path was accepted");
        } catch (RuntimeException e) {
            // expected
        }
    }

    @Test
    public void testPlayContentUri() throws Exception {
        String testFile = "testmp3_2.mp3";
        File localFile;
        try (AssetFileDescriptor mediaFd = getAssetFileDescriptorFor(testFile)) {
            Environment.getExternalStorageDirectory();
            File externalFilesDir = mContext.getExternalFilesDir(/* type= */ null);
            localFile = new File(externalFilesDir, "test_files/" + testFile);
            localFile.mkdirs();
            Files.copy(
                    mediaFd.createInputStream(),
                    localFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }

        MediaPlayer mediaPlayer = new MediaPlayer();
        try {
            Uri contentUri = FileProvider.getUriForFile(
                    mContext, /* authority= */ "com.android.media.player.cts.provider", localFile);
            mediaPlayer.setDataSource(mContext, contentUri);
            mediaPlayer.prepare();

            assertFalse(mediaPlayer.isPlaying());
            mediaPlayer.start();
            assertTrue(mediaPlayer.isPlaying());

            // waiting to complete
            while (mediaPlayer.isPlaying()) {
                Thread.sleep(SLEEP_TIME);
            }
        } finally {
            mediaPlayer.release();
            localFile.delete();
        }
    }

    @Test
    public void testPlayAudioFromDataURI() throws Exception {
        final int mp3Duration = 34909;
        final int tolerance = 70;
        final int seekDuration = 100;

        // This is "testmp3_2.raw", base64-encoded.
        final String res = "testmp3_3.raw";

        Preconditions.assertTestFileExists(mInpPrefix + res);
        InputStream is = new FileInputStream(mInpPrefix + res);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));

        Uri uri = Uri.parse("data:;base64," + reader.readLine());

        MediaPlayer mp = MediaPlayer.create(mContext, uri);

        try {
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mp.setWakeMode(mContext, PowerManager.PARTIAL_WAKE_LOCK);

            assertFalse(mp.isPlaying());
            mp.start();
            assertTrue(mp.isPlaying());

            assertFalse(mp.isLooping());
            mp.setLooping(true);
            assertTrue(mp.isLooping());

            assertEquals(mp3Duration, mp.getDuration(), tolerance);
            int pos = mp.getCurrentPosition();
            assertTrue(pos >= 0);
            assertTrue(pos < mp3Duration - seekDuration);

            mp.seekTo(pos + seekDuration);
            assertEquals(pos + seekDuration, mp.getCurrentPosition(), tolerance);

            // test pause and restart
            mp.pause();
            Thread.sleep(SLEEP_TIME);
            assertFalse(mp.isPlaying());
            mp.start();
            assertTrue(mp.isPlaying());

            // test stop and restart
            mp.stop();
            mp.reset();
            mp.setDataSource(mContext, uri);
            mp.prepare();
            assertFalse(mp.isPlaying());
            mp.start();
            assertTrue(mp.isPlaying());

            // waiting to complete
            while(mp.isPlaying()) {
                Thread.sleep(SLEEP_TIME);
            }
        } finally {
            mp.release();
        }
    }

    @Test
    public void testPlayAudioMp3() throws Exception {
        internalTestPlayAudio("testmp3_2.mp3",
                34909 /* duration */, 70 /* tolerance */, 100 /* seekDuration */);
    }

    @Test
    public void testPlayAudioOpus() throws Exception {
        internalTestPlayAudio("testopus.opus",
                34909 /* duration */, 70 /* tolerance */, 100 /* seekDuration */);
    }

    @Test
    public void testPlayAudioAmr() throws Exception {
        internalTestPlayAudio("testamr.amr",
                34909 /* duration */, 70 /* tolerance */, 100 /* seekDuration */);
    }

    private void internalTestPlayAudio(final String res,
            int mp3Duration, int tolerance, int seekDuration) throws Exception {
        String filePath = mInpPrefix + res;
        Preconditions.assertTestFileExists(filePath);
        assumeTrue("codecs not found for " +  filePath, MediaUtils.hasCodecsForResource(filePath));
        MediaPlayer mp = MediaPlayer.create(mContext, Uri.fromFile(new File(filePath)));
        try {
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mp.setWakeMode(mContext, PowerManager.PARTIAL_WAKE_LOCK);

            assertFalse(mp.isPlaying());
            mp.start();
            assertTrue(mp.isPlaying());

            assertFalse(mp.isLooping());
            mp.setLooping(true);
            assertTrue(mp.isLooping());

            assertEquals(mp3Duration, mp.getDuration(), tolerance);
            int pos = mp.getCurrentPosition();
            assertTrue(pos >= 0);
            assertTrue(pos < mp3Duration - seekDuration);

            mp.seekTo(pos + seekDuration);
            assertEquals(pos + seekDuration, mp.getCurrentPosition(), tolerance);

            // test pause and restart
            mp.pause();
            Thread.sleep(SLEEP_TIME);
            assertFalse(mp.isPlaying());
            mp.start();
            assertTrue(mp.isPlaying());

            // test stop and restart
            mp.stop();
            mp.reset();
            AssetFileDescriptor afd = getAssetFileDescriptorFor(res);
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mp.prepare();
            assertFalse(mp.isPlaying());
            mp.start();
            assertTrue(mp.isPlaying());

            // waiting to complete
            while(mp.isPlaying()) {
                Thread.sleep(SLEEP_TIME);
            }
        } finally {
            mp.release();
        }
    }

    @Test
    public void testConcurrentPlayAudio() throws Exception {
        final String res = "test1m1s.mp3"; // MP3 longer than 1m are usualy offloaded
        final int recommendedTolerance = 70;
        final List<Integer> offsets = new ArrayList<>();

        Preconditions.assertTestFileExists(mInpPrefix + res);
        List<MediaPlayer> mps = Stream.generate(
                () -> MediaPlayer.create(mContext, Uri.fromFile(new File(mInpPrefix + res))))
                                      .limit(5).collect(Collectors.toList());

        try {
            for (MediaPlayer mp : mps) {
                mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mp.setWakeMode(mContext, PowerManager.PARTIAL_WAKE_LOCK);

                assertFalse(mp.isPlaying());
                mp.start();
                assertTrue(mp.isPlaying());

                assertFalse(mp.isLooping());
                mp.setLooping(true);
                assertTrue(mp.isLooping());

                int pos = mp.getCurrentPosition();
                assertTrue(pos >= 0);

                Thread.sleep(SLEEP_TIME); // Delay each track to be able to hear them
            }

            // Check that all mp3 are playing concurrently here
            // Record the offsets between streams, but don't enforce them
            for (MediaPlayer mp : mps) {
                int pos = mp.getCurrentPosition();
                Thread.sleep(SLEEP_TIME);
                offsets.add(Math.abs(pos + SLEEP_TIME - mp.getCurrentPosition()));
            }

            if (offsets.stream().anyMatch(offset -> offset > recommendedTolerance)) {
                Log.w(LOG_TAG, "testConcurrentPlayAudio: some concurrent playing offsets "
                        + offsets + " are above the recommended tolerance of "
                        + recommendedTolerance + "ms.");
            } else {
                Log.i(LOG_TAG, "testConcurrentPlayAudio: all concurrent playing offsets "
                        + offsets + " are under the recommended tolerance of "
                        + recommendedTolerance + "ms.");
            }
        } finally {
            mps.forEach(MediaPlayer::release);
        }
    }

    @Test
    public void testPlayAudioLooping() throws Exception {
        final String res = "testmp3.mp3";

        Preconditions.assertTestFileExists(mInpPrefix + res);
        MediaPlayer mp = MediaPlayer.create(mContext, Uri.fromFile(new File(mInpPrefix + res)));
        try {
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mp.setWakeMode(mContext, PowerManager.PARTIAL_WAKE_LOCK);
            mp.setLooping(true);
            mOnCompletionCalled.reset();
            mp.setOnCompletionListener(mp1 -> {
                Log.i("@@@", "got oncompletion");
                mOnCompletionCalled.signal();
            });

            assertFalse(mp.isPlaying());
            mp.start();
            assertTrue(mp.isPlaying());

            long duration = mp.getDuration();
            Thread.sleep(duration * 4); // allow for several loops
            assertTrue(mp.isPlaying());
            assertEquals("wrong number of completion signals", 0, mOnCompletionCalled.getNumSignal());
            mp.setLooping(false);

            // wait for playback to finish
            while(mp.isPlaying()) {
                Thread.sleep(SLEEP_TIME);
            }
            assertEquals("wrong number of completion signals", 1, mOnCompletionCalled.getNumSignal());
        } finally {
            mp.release();
        }
    }

    @Test
    public void testPlayMidi() throws Exception {
        runMidiTest("midi8sec.mid", 8000 /* duration */);
        runMidiTest("testrtttl.rtttl", 30000 /* duration */);
        runMidiTest("testimy.imy", 5625 /* duration */);
        runMidiTest("testota.ota", 5906 /* duration */);
        runMidiTest("testmxmf.mxmf", 29095 /* duration */);
    }

    private void runMidiTest(final String res, int midiDuration) throws Exception {
        final int tolerance = 70;
        final int seekDuration = 1000;

        Preconditions.assertTestFileExists(mInpPrefix + res);
        MediaPlayer mp = MediaPlayer.create(mContext, Uri.fromFile(new File(mInpPrefix + res)));
        try {
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mp.setWakeMode(mContext, PowerManager.PARTIAL_WAKE_LOCK);

            mp.start();

            assertFalse(mp.isLooping());
            mp.setLooping(true);
            assertTrue(mp.isLooping());

            assertEquals(midiDuration, mp.getDuration(), tolerance);
            int pos = mp.getCurrentPosition();
            assertTrue(pos >= 0);
            assertTrue(pos < midiDuration - seekDuration);

            mp.seekTo(pos + seekDuration);
            assertEquals(pos + seekDuration, mp.getCurrentPosition(), tolerance);

            // test stop and restart
            mp.stop();
            mp.reset();
            AssetFileDescriptor afd = getAssetFileDescriptorFor(res);
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mp.prepare();
            mp.start();

            Thread.sleep(SLEEP_TIME);
        } finally {
            mp.release();
        }
    }

    private final class VerifyAndSignalTimedText implements MediaPlayer.OnTimedTextListener {

        final boolean mCheckStartTimeIncrease;
        final int mTargetSignalCount;
        int mPrevStartMs = -1;

        VerifyAndSignalTimedText() {
            this(Integer.MAX_VALUE, false);
        }

        VerifyAndSignalTimedText(int targetSignalCount, boolean checkStartTimeIncrease) {
            mTargetSignalCount = targetSignalCount;
            mCheckStartTimeIncrease = checkStartTimeIncrease;
        }

        void reset() {
            mPrevStartMs = -1;
        }

        @Override
        public void onTimedText(MediaPlayer mp, TimedText text) {
            final int toleranceMs = 500;
            final int durationMs = 500;
            int posMs = mMediaPlayer.getCurrentPosition();
            if (text != null) {
                text.getText();
                String plainText = text.getText();
                if (plainText != null) {
                    StringTokenizer tokens = new StringTokenizer(plainText.trim(), ":");
                    int subtitleTrackIndex = Integer.parseInt(tokens.nextToken());
                    int startMs = Integer.parseInt(tokens.nextToken());
                    Log.d(LOG_TAG, "text: " + plainText.trim() +
                          ", trackId: " + subtitleTrackIndex + ", posMs: " + posMs);
                    assertTrue("The diff between subtitle's start time " + startMs +
                               " and current time " + posMs +
                               " is over tolerance " + toleranceMs,
                               (posMs >= startMs - toleranceMs) &&
                               (posMs < startMs + durationMs + toleranceMs) );
                    assertEquals("Expected track: " + mSelectedTimedTextIndex +
                                 ", actual track: " + subtitleTrackIndex,
                                 mSelectedTimedTextIndex, subtitleTrackIndex);
                    assertTrue("timed text start time did not increase; current: " + startMs +
                               ", previous: " + mPrevStartMs,
                               !mCheckStartTimeIncrease || startMs > mPrevStartMs);
                    mPrevStartMs = startMs;
                    mOnTimedTextCalled.signal();
                    if (mTargetSignalCount >= mOnTimedTextCalled.getNumSignal()) {
                        reset();
                    }
                }
                Rect bounds = text.getBounds();
                if (bounds != null) {
                    Log.d(LOG_TAG, "bounds: " + bounds);
                    mBoundsCount++;
                    Rect expected = new Rect(0, 0, 352, 288);
                    assertEquals("wrong bounds", expected, bounds);
                }
            }
        }

    }

    static class OutputListener {
        AudioEffect mVc;
        Visualizer mVis;
        boolean mSoundDetected;
        OutputListener(int session) {
            // creating a volume controller on output mix ensures that ro.audio.silent mutes
            // audio after the effects and not before
            mVc = new AudioEffect(
                    AudioEffect.EFFECT_TYPE_NULL,
                    UUID.fromString("119341a0-8469-11df-81f9-0002a5d5c51b"),
                    0,
                    session);
            mVc.setEnabled(true);
            mVis = new Visualizer(session);
            int size = 256;
            int[] range = Visualizer.getCaptureSizeRange();
            if (size < range[0]) {
                size = range[0];
            }
            if (size > range[1]) {
                size = range[1];
            }
            assertEquals(Visualizer.SUCCESS, mVis.setCaptureSize(size));

            Visualizer.OnDataCaptureListener onDataCaptureListener =
                    new Visualizer.OnDataCaptureListener() {
                        @Override
                        public void onWaveFormDataCapture(Visualizer visualizer,
                                byte[] waveform, int samplingRate) {
                            if (!mSoundDetected) {
                                for (byte b : waveform) {
                                    // 8 bit unsigned PCM, zero level is at 128, which is -128 when
                                    // seen as a signed byte
                                    if (b != -128) {
                                        mSoundDetected = true;
                                        break;
                                    }
                                }
                            }
                        }

                        @Override
                        public void onFftDataCapture(
                                Visualizer visualizer, byte[] fft, int samplingRate) {}
                    };

            mVis.setDataCaptureListener(
                    onDataCaptureListener,
                    /* rate= */ 10000, // In milliHertz.
                    /* waveform= */ true, // Is PCM.
                    /* fft= */ false); // Do not request a frequency capture.
            assertEquals(Visualizer.SUCCESS, mVis.setEnabled(true));
        }

        void reset() {
            mSoundDetected = false;
        }

        boolean heardSound() {
            return mSoundDetected;
        }

        void release() {
            mVis.release();
            mVc.release();
        }
    }

    @Test
    public void testPlayAudioTwice() throws Exception {

        final String res = "camera_click.ogg";

        Preconditions.assertTestFileExists(mInpPrefix + res);
        MediaPlayer mp = MediaPlayer.create(mContext, Uri.fromFile(new File(mInpPrefix + res)));
        try {
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mp.setWakeMode(mContext, PowerManager.PARTIAL_WAKE_LOCK);

            OutputListener listener = new OutputListener(mp.getAudioSessionId());

            Thread.sleep(SLEEP_TIME);
            assertFalse("noise heard before test started", listener.heardSound());

            mp.start();
            Thread.sleep(SLEEP_TIME);
            assertFalse("player was still playing after " + SLEEP_TIME + " ms", mp.isPlaying());
            assertTrue("nothing heard while test ran", listener.heardSound());
            listener.reset();
            mp.seekTo(0);
            mp.start();
            Thread.sleep(SLEEP_TIME);
            assertTrue("nothing heard when sound was replayed", listener.heardSound());
            listener.release();
        } finally {
            mp.release();
        }
    }

    @Test
    public void testPlayVideo() throws Exception {
        playLoadedVideoTest("testvideo.3gp", 352, 288);
    }

    private void initMediaPlayer(MediaPlayer player) throws Exception {
        try (AssetFileDescriptor afd = getAssetFileDescriptorFor("test1m1s.mp3")) {
            player.reset();
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            player.prepare();
            // Test needs the mediaplayer to playback at least about 5 seconds of content.
            // Clip used here has a duration of 61 seconds, given PLAYBACK_DURATION_MS for play.
            // This leaves enough remaining time, with gapless enabled or disabled,
            player.seekTo(player.getDuration() - PLAYBACK_DURATION_MS);
        }
    }

    @Presubmit
    @Test
    public void testSetNextMediaPlayerWithReset() throws Exception {

        initMediaPlayer(mMediaPlayer);

        try {
            initMediaPlayer(mMediaPlayer2);
            mMediaPlayer2.reset();
            mMediaPlayer.setNextMediaPlayer(mMediaPlayer2);
            fail("setNextMediaPlayer() succeeded with unprepared player");
        } catch (RuntimeException e) {
            // expected
        } finally {
            mMediaPlayer.reset();
        }
    }

    @Presubmit
    @Test
    public void testSetNextMediaPlayerWithRelease() throws Exception {

        initMediaPlayer(mMediaPlayer);

        try {
            initMediaPlayer(mMediaPlayer2);
            mMediaPlayer2.release();
            mMediaPlayer.setNextMediaPlayer(mMediaPlayer2);
            fail("setNextMediaPlayer() succeeded with unprepared player");
        } catch (RuntimeException e) {
            // expected
        } finally {
            mMediaPlayer.reset();
        }
    }

    @Test
    public void testSetNextMediaPlayer() throws Exception {
        final int ITERATIONS = 3;
        // the +1 is for the trailing test of setNextMediaPlayer(null)
        final int TOTAL_TIMEOUT_MS = PLAYBACK_DURATION_MS * (ITERATIONS + 1)
                        + ANR_DETECTION_TIME_MS + 5000 /* listener latency(ms) */;
        initMediaPlayer(mMediaPlayer);

        final Monitor mTestCompleted = new Monitor();

        Thread timer = new Thread(() -> {
            long startTime = SystemClock.elapsedRealtime();
            while(true) {
                SystemClock.sleep(SLEEP_TIME);
                if (mTestCompleted.isSignalled()) {
                    // done
                    return;
                }
                long now = SystemClock.elapsedRealtime();
                if ((now - startTime) > TOTAL_TIMEOUT_MS) {
                    // We've been running beyond TOTAL_TIMEOUT and still aren't done,
                    // so we're stuck somewhere. Signal ourselves to dump the thread stacks.
                    android.os.Process.sendSignal(android.os.Process.myPid(), 3);
                    SystemClock.sleep(2000);
                    fail("Test is stuck, see ANR stack trace for more info. You may need to" +
                            " create /data/anr first");
                    return;
                }
            }
        });

        timer.start();

        try {
            for (int i = 0; i < ITERATIONS; i++) {

                initMediaPlayer(mMediaPlayer2);
                mOnCompletionCalled.reset();
                mOnInfoCalled.reset();
                mMediaPlayer.setOnCompletionListener(mp -> {
                    assertEquals(mMediaPlayer, mp);
                    mOnCompletionCalled.signal();
                });
                mMediaPlayer2.setOnInfoListener((mp, what, extra) -> {
                    assertEquals(mMediaPlayer2, mp);
                    if (what == MediaPlayer.MEDIA_INFO_STARTED_AS_NEXT) {
                        mOnInfoCalled.signal();
                    }
                    return false;
                });

                mMediaPlayer.setNextMediaPlayer(mMediaPlayer2);
                mMediaPlayer.start();
                assertTrue(mMediaPlayer.isPlaying());
                assertFalse(mOnCompletionCalled.isSignalled());
                assertFalse(mMediaPlayer2.isPlaying());
                assertFalse(mOnInfoCalled.isSignalled());
                while(mMediaPlayer.isPlaying()) {
                    Thread.sleep(SLEEP_TIME);
                }
                // wait a little longer in case the callbacks haven't quite made it through yet
                Thread.sleep(100);
                assertTrue(mMediaPlayer2.isPlaying());
                assertTrue(mOnCompletionCalled.isSignalled());
                assertTrue(mOnInfoCalled.isSignalled());

                // At this point the 1st player is done, and the 2nd one is playing.
                // Now swap them, and go through the loop again.
                MediaPlayer tmp = mMediaPlayer;
                mMediaPlayer = mMediaPlayer2;
                mMediaPlayer2 = tmp;
            }

            // Now test that setNextMediaPlayer(null) works. 1 is still playing, 2 is done
            // this is the final "+1" in our time calculations above
            mOnCompletionCalled.reset();
            mOnInfoCalled.reset();
            initMediaPlayer(mMediaPlayer2);
            mMediaPlayer.setNextMediaPlayer(mMediaPlayer2);

            mMediaPlayer.setOnCompletionListener(mp -> {
                assertEquals(mMediaPlayer, mp);
                mOnCompletionCalled.signal();
            });
            mMediaPlayer2.setOnInfoListener((mp, what, extra) -> {
                assertEquals(mMediaPlayer2, mp);
                if (what == MediaPlayer.MEDIA_INFO_STARTED_AS_NEXT) {
                    mOnInfoCalled.signal();
                }
                return false;
            });
            assertTrue(mMediaPlayer.isPlaying());
            assertFalse(mOnCompletionCalled.isSignalled());
            assertFalse(mMediaPlayer2.isPlaying());
            assertFalse(mOnInfoCalled.isSignalled());
            Thread.sleep(SLEEP_TIME);
            mMediaPlayer.setNextMediaPlayer(null);
            while(mMediaPlayer.isPlaying()) {
                Thread.sleep(SLEEP_TIME);
            }
            // wait a little longer in case the callbacks haven't quite made it through yet
            Thread.sleep(100);
            assertFalse(mMediaPlayer.isPlaying());
            assertFalse(mMediaPlayer2.isPlaying());
            assertTrue(mOnCompletionCalled.isSignalled());
            assertFalse(mOnInfoCalled.isSignalled());

        } finally {
            mMediaPlayer.reset();
            mMediaPlayer2.reset();
        }
        mTestCompleted.signal();

    }

    // The following tests are all a bit flaky, which is why they're retried a
    // few times in a loop.

    // This test uses one mp3 that is silent but has a strong positive DC offset,
    // and a second mp3 that is also silent but has a strong negative DC offset.
    // If the two are played back overlapped, they will cancel each other out,
    // and result in zeroes being detected. If there is a gap in playback, that
    // will also result in zeroes being detected.
    // Note that this test does NOT guarantee that the correct data is played
    @Test
    public void testGapless1() throws Exception {
        flakyTestWrapper("monodcpos.mp3", "monodcneg.mp3");
    }

    // This test is similar, but uses two identical m4a files that have some noise
    // with a strong positive DC offset. This is used to detect if there is
    // a gap in playback
    // Note that this test does NOT guarantee that the correct data is played
    @Test
    public void testGapless2() throws Exception {
        flakyTestWrapper("stereonoisedcpos.m4a", "stereonoisedcpos.m4a");
    }

    // same as above, but with a mono file
    @Test
    public void testGapless3() throws Exception {
        flakyTestWrapper("mononoisedcpos.m4a", "mononoisedcpos.m4a");
    }

    private void flakyTestWrapper(final String res1, final String res2) throws Exception {
        boolean success = false;
        // test usually succeeds within a few tries, but occasionally may fail
        // many times in a row, so be aggressive and try up to 20 times
        for (int i = 0; i < 20 && !success; i++) {
            try {
                testGapless(res1, res2);
                success = true;
            } catch (Throwable t) {
                SystemClock.sleep(1000);
            }
        }
        // Try one more time. If this succeeds, we'll consider the test a success,
        // otherwise the exception gets thrown
        if (!success) {
            testGapless(res1, res2);
        }
    }

    private void testGapless(final String res1, final String res2) throws Exception {
        MediaPlayer mp1 = null;
        MediaPlayer mp2 = null;
        AudioEffect vc = null;
        Visualizer vis = null;
        AudioManager am = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        int oldRingerMode = Integer.MIN_VALUE;
        int oldVolume = Integer.MIN_VALUE;
        try {
            if (am.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) {
                Utils.toggleNotificationPolicyAccess(
                        mContext.getPackageName(), getInstrumentation(), true /* on */);
            }

            mp1 = new MediaPlayer(mContext);
            mp1.setAudioStreamType(AudioManager.STREAM_MUSIC);

            AssetFileDescriptor afd = getAssetFileDescriptorFor(res1);
            mp1.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mp1.prepare();

            int session = mp1.getAudioSessionId();

            mp2 = new MediaPlayer(mContext);
            mp2.setAudioSessionId(session);
            mp2.setAudioStreamType(AudioManager.STREAM_MUSIC);

            afd = getAssetFileDescriptorFor(res2);
            mp2.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            mp2.prepare();

            // creating a volume controller on output mix ensures that ro.audio.silent mutes
            // audio after the effects and not before
            vc = new AudioEffect(
                            AudioEffect.EFFECT_TYPE_NULL,
                            UUID.fromString("119341a0-8469-11df-81f9-0002a5d5c51b"),
                            0,
                            session);
            vc.setEnabled(true);
            int captureintervalms = mp1.getDuration() + mp2.getDuration() - 2000;
            int size = 256;
            int[] range = Visualizer.getCaptureSizeRange();
            if (size < range[0]) {
                size = range[0];
            }
            if (size > range[1]) {
                size = range[1];
            }
            byte[] vizdata = new byte[size];

            vis = new Visualizer(session);

            oldRingerMode = am.getRingerMode();
            // make sure we aren't in silent mode
            if (am.getRingerMode() != AudioManager.RINGER_MODE_NORMAL) {
                am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            }
            oldVolume = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            am.setStreamVolume(AudioManager.STREAM_MUSIC, 1, 0);

            assertEquals("setCaptureSize failed",
                    Visualizer.SUCCESS, vis.setCaptureSize(vizdata.length));
            assertEquals("setEnabled failed", Visualizer.SUCCESS, vis.setEnabled(true));

            mp1.setNextMediaPlayer(mp2);
            mp1.start();
            assertTrue(mp1.isPlaying());
            assertFalse(mp2.isPlaying());
            // allow playback to get started
            Thread.sleep(SLEEP_TIME);
            long start = SystemClock.elapsedRealtime();
            // there should be no consecutive zeroes (-128) in the capture buffer
            // when going to the next file. If silence is detected right away, then
            // the volume is probably turned all the way down (visualizer data
            // is captured after volume adjustment).
            boolean first = true;
            while((SystemClock.elapsedRealtime() - start) < captureintervalms) {
                assertEquals(Visualizer.SUCCESS, vis.getWaveForm(vizdata));
                for (int i = 0; i < vizdata.length - 1; i++) {
                    if (vizdata[i] == -128 && vizdata[i + 1] == -128) {
                        if (first) {
                            fail("silence detected, please increase volume and rerun test");
                        } else {
                            fail("gap or overlap detected at t=" +
                                    (SLEEP_TIME + SystemClock.elapsedRealtime() - start) +
                                    ", offset " + i);
                        }
                        break;
                    }
                }
                first = false;
            }
        } finally {
            if (mp1 != null) {
                mp1.release();
            }
            if (mp2 != null) {
                mp2.release();
            }
            if (vis != null) {
                vis.release();
            }
            if (vc != null) {
                vc.release();
            }
            if (oldRingerMode != Integer.MIN_VALUE) {
                am.setRingerMode(oldRingerMode);
            }
            if (oldVolume != Integer.MIN_VALUE) {
                am.setStreamVolume(AudioManager.STREAM_MUSIC, oldVolume, 0);
            }
            Utils.toggleNotificationPolicyAccess(
                    mContext.getPackageName(), getInstrumentation(), false  /* on == false */);
        }
    }

    /**
     * Test for reseting a surface during video playback
     * After reseting, the video should continue playing
     * from the time setDisplay() was called
     */
    @Test
    public void testVideoSurfaceResetting() throws Exception {
        final int tolerance = 150;
        final int audioLatencyTolerance = 1000;  /* covers audio path latency variability */
        final int seekPos = 4760;  // This is the I-frame position

        final CountDownLatch seekDone = new CountDownLatch(1);

        mMediaPlayer.setOnSeekCompleteListener(mp -> seekDone.countDown());

        if (!checkLoadResource("testvideo.3gp")) {
            return; // skip;
        }
        playLoadedVideo(352, 288, -1);

        Thread.sleep(SLEEP_TIME);

        int posBefore = mMediaPlayer.getCurrentPosition();
        mMediaPlayer.setDisplay(getActivity().getSurfaceHolder2());
        int posAfter = mMediaPlayer.getCurrentPosition();

        /* temporarily disable timestamp checking because MediaPlayer now seeks to I-frame
         * position, instead of requested position. setDisplay invovles a seek operation
         * internally.
         */
        // TODO: uncomment out line below when MediaPlayer can seek to requested position.
        // assertEquals(posAfter, posBefore, tolerance);
        assertTrue(mMediaPlayer.isPlaying());

        Thread.sleep(SLEEP_TIME);

        mMediaPlayer.seekTo(seekPos);
        seekDone.await();
        posAfter = mMediaPlayer.getCurrentPosition();
        assertEquals(seekPos, posAfter, tolerance + audioLatencyTolerance);

        Thread.sleep(SLEEP_TIME / 2);
        posBefore = mMediaPlayer.getCurrentPosition();
        mMediaPlayer.setDisplay(null);
        posAfter = mMediaPlayer.getCurrentPosition();
        // TODO: uncomment out line below when MediaPlayer can seek to requested position.
        // assertEquals(posAfter, posBefore, tolerance);
        assertTrue(mMediaPlayer.isPlaying());

        Thread.sleep(SLEEP_TIME);

        posBefore = mMediaPlayer.getCurrentPosition();
        mMediaPlayer.setDisplay(getActivity().getSurfaceHolder());
        posAfter = mMediaPlayer.getCurrentPosition();

        // TODO: uncomment out line below when MediaPlayer can seek to requested position.
        // assertEquals(posAfter, posBefore, tolerance);
        assertTrue(mMediaPlayer.isPlaying());

        Thread.sleep(SLEEP_TIME);
    }

    @Test
    public void testRecordedVideoPlayback0() throws Exception {
        testRecordedVideoPlaybackWithAngle(0);
    }

    @Test
    public void testRecordedVideoPlayback90() throws Exception {
        testRecordedVideoPlaybackWithAngle(90);
    }

    @Test
    public void testRecordedVideoPlayback180() throws Exception {
        testRecordedVideoPlaybackWithAngle(180);
    }

    @Test
    public void testRecordedVideoPlayback270() throws Exception {
        testRecordedVideoPlaybackWithAngle(270);
    }

    private boolean hasCamera() {
        return getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
    }

    private void testRecordedVideoPlaybackWithAngle(int angle) throws Exception {
        int width = RECORDED_VIDEO_WIDTH;
        int height = RECORDED_VIDEO_HEIGHT;
        final String file = RECORDED_FILE;
        final long durationMs = RECORDED_DURATION_MS;

        if (!hasCamera()) {
            return;
        }

        boolean isSupported = false;
        Camera camera = Camera.open(0);
        Camera.Parameters parameters = camera.getParameters();
        List<Camera.Size> videoSizes = parameters.getSupportedVideoSizes();
        // getSupportedVideoSizes returns null when separate video/preview size
        // is not supported.
        if (videoSizes == null) {
            // If we have CamcorderProfile use it instead of Preview size.
            if (CamcorderProfile.hasProfile(0, CamcorderProfile.QUALITY_LOW)) {
                CamcorderProfile profile = CamcorderProfile.get(0, CamcorderProfile.QUALITY_LOW);
                videoSizes = new ArrayList<>();
                videoSizes.add(camera.new Size(profile.videoFrameWidth, profile.videoFrameHeight));
            } else {
                videoSizes = parameters.getSupportedPreviewSizes();
            }
        }
        for (Camera.Size size : videoSizes)
        {
            if (size.width == width && size.height == height) {
                isSupported = true;
                break;
            }
        }
        camera.release();
        if (!isSupported) {
            width = videoSizes.get(0).width;
            height = videoSizes.get(0).height;
        }
        checkOrientation(angle);
        recordVideo(width, height, angle, file, durationMs);
        checkDisplayedVideoSize(width, height, angle, file);
        checkVideoRotationAngle(angle, file);
    }

    private void checkOrientation(int angle) throws Exception {
        assertTrue(angle >= 0);
        assertTrue(angle < 360);
        assertEquals(0, (angle % 90));
    }

    private void recordVideo(
            int w, int h, int angle, String file, long durationMs) throws Exception {

        MediaRecorder recorder = new MediaRecorder();
        recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.DEFAULT);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        recorder.setOutputFile(file);
        recorder.setOrientationHint(angle);
        recorder.setVideoSize(w, h);
        recorder.setPreviewDisplay(getActivity().getSurfaceHolder2().getSurface());
        recorder.prepare();
        recorder.start();
        Thread.sleep(durationMs);
        recorder.stop();
        recorder.release();
    }

    private void checkDisplayedVideoSize(
            int w, int h, int angle, String file) throws Exception {

        int displayWidth  = w;
        int displayHeight = h;
        if ((angle % 180) != 0) {
            displayWidth  = h;
            displayHeight = w;
        }
        playVideoTest(file, displayWidth, displayHeight);
    }

    private void checkVideoRotationAngle(int angle, String file) throws IOException {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(file);
        String rotation = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
        retriever.release();
        assertNotNull(rotation);
        assertEquals(Integer.parseInt(rotation), angle);
    }

    // setPlaybackParams() with non-zero speed should start playback.
    @Test
    public void testSetPlaybackParamsPositiveSpeed() throws Exception {
        if (!checkLoadResource(
                "video_480x360_mp4_h264_1000kbps_30fps_aac_stereo_128kbps_44100hz.mp4")) {
            return; // skip
        }

        mMediaPlayer.setOnSeekCompleteListener(mp -> mOnSeekCompleteCalled.signal());
        mOnCompletionCalled.reset();
        mMediaPlayer.setOnCompletionListener(mp -> mOnCompletionCalled.signal());
        mMediaPlayer.setDisplay(mActivity.getSurfaceHolder());

        mMediaPlayer.prepare();

        mOnSeekCompleteCalled.reset();
        mMediaPlayer.seekTo(0);
        mOnSeekCompleteCalled.waitForSignal();

        final float playbackRate = 1.0f;

        int playTime = 2000;  // The testing clip is about 10 second long.
        mMediaPlayer.setPlaybackParams(new PlaybackParams().setSpeed(playbackRate));
        assertTrue("MediaPlayer should be playing", mMediaPlayer.isPlaying());
        Thread.sleep(playTime);
        assertTrue("MediaPlayer should still be playing",
                mMediaPlayer.getCurrentPosition() > 0);

        int duration = mMediaPlayer.getDuration();
        mOnSeekCompleteCalled.reset();
        mMediaPlayer.seekTo(duration - 1000);
        mOnSeekCompleteCalled.waitForSignal();

        mOnCompletionCalled.waitForSignal();
        assertFalse("MediaPlayer should not be playing", mMediaPlayer.isPlaying());
        int eosPosition = mMediaPlayer.getCurrentPosition();

        mMediaPlayer.setPlaybackParams(new PlaybackParams().setSpeed(playbackRate));
        assertTrue("MediaPlayer should be playing after EOS", mMediaPlayer.isPlaying());
        Thread.sleep(playTime);
        int position = mMediaPlayer.getCurrentPosition();
        assertTrue("MediaPlayer should still be playing after EOS",
                position > 0 && position < eosPosition);

        mMediaPlayer.stop();
    }

    // setPlaybackParams() with zero speed should pause playback.
    @Test
    public void testSetPlaybackParamsZeroSpeed() throws Exception {
        if (!checkLoadResource(
                "video_480x360_mp4_h264_1000kbps_30fps_aac_stereo_128kbps_44100hz.mp4")) {
            return; // skip
        }

        mMediaPlayer.setOnSeekCompleteListener(mp -> mOnSeekCompleteCalled.signal());
        mMediaPlayer.setDisplay(mActivity.getSurfaceHolder());

        mMediaPlayer.prepare();

        mMediaPlayer.setPlaybackParams(new PlaybackParams().setSpeed(0.0f));
        assertFalse("MediaPlayer should not be playing", mMediaPlayer.isPlaying());

        int playTime = 2000;  // The testing clip is about 10 second long.
        mOnSeekCompleteCalled.reset();
        mMediaPlayer.seekTo(0);
        mOnSeekCompleteCalled.waitForSignal();
        Thread.sleep(playTime);
        assertFalse("MediaPlayer should not be playing", mMediaPlayer.isPlaying());
        int positionAtStart = mMediaPlayer.getCurrentPosition();
        // Allow both 0 and 23 (the timestamp of the second audio sample) to avoid flaky failures
        // on builds that don't include http://r.android.com/2700283.
        if (positionAtStart != 0 && positionAtStart != 23) {
            fail("MediaPlayer position should be 0 or 23");
        }

        mMediaPlayer.start();
        Thread.sleep(playTime);
        assertTrue("MediaPlayer should be playing", mMediaPlayer.isPlaying());
        assertTrue("MediaPlayer position should be > 0", mMediaPlayer.getCurrentPosition() > 0);

        mMediaPlayer.setPlaybackParams(new PlaybackParams().setSpeed(0.0f));
        assertFalse("MediaPlayer should not be playing", mMediaPlayer.isPlaying());
        Thread.sleep(1000);
        int position = mMediaPlayer.getCurrentPosition();
        Thread.sleep(playTime);
        assertEquals("MediaPlayer should be paused", mMediaPlayer.getCurrentPosition(), position);

        mMediaPlayer.stop();
    }

    @Test
    public void testPlaybackRate() throws Exception {
        final int toleranceMs = 1000;
        if (!checkLoadResource(
                "video_480x360_mp4_h264_1000kbps_30fps_aac_stereo_128kbps_44100hz.mp4")) {
            return; // skip
        }

        mMediaPlayer.setDisplay(mActivity.getSurfaceHolder());
        mMediaPlayer.prepare();
        SyncParams sync = new SyncParams().allowDefaults();
        mMediaPlayer.setSyncParams(sync);
        sync = mMediaPlayer.getSyncParams();

        float[] rates = { 0.25f, 0.5f, 1.0f, 2.0f };
        for (float playbackRate : rates) {
            mMediaPlayer.seekTo(0);
            Thread.sleep(1000);
            int playTime = 4000;  // The testing clip is about 10 second long.
            mMediaPlayer.setPlaybackParams(new PlaybackParams().setSpeed(playbackRate));
            mMediaPlayer.start();
            Thread.sleep(playTime);
            PlaybackParams pbp = mMediaPlayer.getPlaybackParams();
            assertEquals(
                    playbackRate, pbp.getSpeed(),
                    FLOAT_TOLERANCE + playbackRate * sync.getTolerance());
            assertTrue("MediaPlayer should still be playing", mMediaPlayer.isPlaying());

            int playedMediaDurationMs = mMediaPlayer.getCurrentPosition();
            int diff = Math.abs((int)(playedMediaDurationMs / playbackRate) - playTime);
            if (diff > toleranceMs) {
                fail("Media player had error in playback rate " + playbackRate
                     + ", play time is " + playTime + " vs expected " + playedMediaDurationMs);
            }
            mMediaPlayer.pause();
            pbp = mMediaPlayer.getPlaybackParams();
            assertEquals(0.f, pbp.getSpeed(), FLOAT_TOLERANCE);
        }
        mMediaPlayer.stop();
    }

    @Presubmit
    @Test
    public void testSeekModes() throws Exception {
        // This clip has 2 I frames at 66687us and 4299687us.
        if (!checkLoadResource(
                "bbb_s1_320x240_mp4_h264_mp2_800kbps_30fps_aac_lc_5ch_240kbps_44100hz.mp4")) {
            return; // skip
        }

        mMediaPlayer.setOnSeekCompleteListener(mp -> mOnSeekCompleteCalled.signal());
        mMediaPlayer.setDisplay(mActivity.getSurfaceHolder());
        mMediaPlayer.prepare();
        mOnSeekCompleteCalled.reset();
        mMediaPlayer.start();

        final int seekPosMs = 3000;
        final int timeToleranceMs = 100;
        final int syncTime1Ms = 67;
        final int syncTime2Ms = 4300;

        // TODO: tighten checking range. For now, ensure mediaplayer doesn't
        // seek to previous sync or next sync.
        int cp = runSeekMode(MediaPlayer.SEEK_CLOSEST, seekPosMs);
        assertTrue("MediaPlayer did not seek to closest position",
                cp > seekPosMs && cp < syncTime2Ms);

        // TODO: tighten checking range. For now, ensure mediaplayer doesn't
        // seek to closest position or next sync.
        cp = runSeekMode(MediaPlayer.SEEK_PREVIOUS_SYNC, seekPosMs);
        assertTrue("MediaPlayer did not seek to preivous sync position",
                cp < seekPosMs - timeToleranceMs);

        // TODO: tighten checking range. For now, ensure mediaplayer doesn't
        // seek to closest position or previous sync.
        cp = runSeekMode(MediaPlayer.SEEK_NEXT_SYNC, seekPosMs);
        assertTrue("MediaPlayer did not seek to next sync position",
                cp > syncTime2Ms - timeToleranceMs);

        // TODO: tighten checking range. For now, ensure mediaplayer doesn't
        // seek to closest position or previous sync.
        cp = runSeekMode(MediaPlayer.SEEK_CLOSEST_SYNC, seekPosMs);
        assertTrue("MediaPlayer did not seek to closest sync position",
                cp > syncTime2Ms - timeToleranceMs);

        mMediaPlayer.stop();
    }

    private int runSeekMode(int seekMode, int seekPosMs) throws Exception {
        final int sleepIntervalMs = 100;
        int timeRemainedMs = 10000;  // total time for testing
        final int timeToleranceMs = 100;

        mMediaPlayer.seekTo(seekPosMs, seekMode);
        mOnSeekCompleteCalled.waitForSignal();
        mOnSeekCompleteCalled.reset();
        int cp = -seekPosMs;
        while (timeRemainedMs > 0) {
            cp = mMediaPlayer.getCurrentPosition();
            // Wait till MediaPlayer starts rendering since MediaPlayer caches
            // seek position as current position.
            if (cp < seekPosMs - timeToleranceMs || cp > seekPosMs + timeToleranceMs) {
                break;
            }
            timeRemainedMs -= sleepIntervalMs;
            Thread.sleep(sleepIntervalMs);
        }
        assertTrue("MediaPlayer did not finish seeking in time for mode " + seekMode,
                timeRemainedMs > 0);
        return cp;
    }

    @Test
    public void testGetTimestamp() throws Exception {
        final int toleranceUs = 100000;
        final float playbackRate = 1.0f;
        if (!checkLoadResource(
                "video_480x360_mp4_h264_1000kbps_30fps_aac_stereo_128kbps_44100hz.mp4")) {
            return; // skip
        }

        mMediaPlayer.setDisplay(mActivity.getSurfaceHolder());
        mMediaPlayer.prepare();
        mMediaPlayer.start();
        mMediaPlayer.setPlaybackParams(new PlaybackParams().setSpeed(playbackRate));
        Thread.sleep(SLEEP_TIME);  // let player get into stable state.
        long nt1 = System.nanoTime();
        MediaTimestamp ts1 = mMediaPlayer.getTimestamp();
        long nt2 = System.nanoTime();
        assertNotNull("Media player should return a valid time stamp", ts1);
        assertEquals("MediaPlayer had error in clockRate " + ts1.getMediaClockRate(),
                playbackRate, ts1.getMediaClockRate(), 0.001f);
        assertTrue("The nanoTime of Media timestamp should be taken when getTimestamp is called.",
                nt1 <= ts1.getAnchorSystemNanoTime() && ts1.getAnchorSystemNanoTime() <= nt2);

        mMediaPlayer.pause();
        ts1 = mMediaPlayer.getTimestamp();
        assertNotNull("Media player should return a valid time stamp", ts1);
        assertEquals("Media player should have play rate of 0.0f when paused", 0.0f,
                ts1.getMediaClockRate());

        mMediaPlayer.seekTo(0);
        mMediaPlayer.start();
        Thread.sleep(SLEEP_TIME);  // let player get into stable state.
        int playTime = 4000;  // The testing clip is about 10 second long.
        ts1 = mMediaPlayer.getTimestamp();
        assertNotNull("Media player should return a valid time stamp", ts1);
        Thread.sleep(playTime);
        MediaTimestamp ts2 = mMediaPlayer.getTimestamp();
        assertNotNull("Media player should return a valid time stamp", ts2);
        assertEquals("The clockRate should not be changed.", ts1.getMediaClockRate(),
                ts2.getMediaClockRate());
        assertEquals("MediaPlayer had error in timestamp.",
                ts1.getAnchorMediaTimeUs() + (long)(playTime * ts1.getMediaClockRate() * 1000),
                ts2.getAnchorMediaTimeUs(), toleranceUs);

        mMediaPlayer.stop();
    }

    @Test
    public void testMediaTimeDiscontinuity() throws Exception {
        if (!checkLoadResource(
                "bbb_s1_320x240_mp4_h264_mp2_800kbps_30fps_aac_lc_5ch_240kbps_44100hz.mp4")) {
            return; // skip
        }

        mMediaPlayer.setOnSeekCompleteListener(mp -> mOnSeekCompleteCalled.signal());
        final BlockingDeque<MediaTimestamp> timestamps = new LinkedBlockingDeque<>();
        mMediaPlayer.setOnMediaTimeDiscontinuityListener(
                (mp, timestamp) -> {
                    mOnMediaTimeDiscontinuityCalled.signal();
                    timestamps.add(timestamp);
                });
        mMediaPlayer.setDisplay(mActivity.getSurfaceHolder());
        mMediaPlayer.prepare();

        // Timestamp needs to be reported when playback starts.
        mOnMediaTimeDiscontinuityCalled.reset();
        mMediaPlayer.start();
        do {
            assertTrue(mOnMediaTimeDiscontinuityCalled.waitForSignal(1000));
        } while (timestamps.getLast().getMediaClockRate() != 1.0f);

        // Timestamp needs to be reported when seeking is done.
        mOnSeekCompleteCalled.reset();
        mOnMediaTimeDiscontinuityCalled.reset();
        mMediaPlayer.seekTo(3000);
        mOnSeekCompleteCalled.waitForSignal();
        do {
            assertTrue(mOnMediaTimeDiscontinuityCalled.waitForSignal(1000));
        } while (timestamps.getLast().getMediaClockRate() != 1.0f);

        // Timestamp needs to be updated when playback rate changes.
        mOnMediaTimeDiscontinuityCalled.reset();
        mMediaPlayer.setPlaybackParams(new PlaybackParams().setSpeed(0.5f));
        do {
            assertTrue(mOnMediaTimeDiscontinuityCalled.waitForSignal(1000));
        } while (timestamps.getLast().getMediaClockRate() != 0.5f);

        // Timestamp needs to be updated when player is paused.
        mOnMediaTimeDiscontinuityCalled.reset();
        mMediaPlayer.pause();
        do {
            assertTrue(mOnMediaTimeDiscontinuityCalled.waitForSignal(1000));
        } while (timestamps.getLast().getMediaClockRate() != 0.0f);

        // Check if there is no more notification after clearing listener.
        mMediaPlayer.clearOnMediaTimeDiscontinuityListener();
        mMediaPlayer.start();
        mOnMediaTimeDiscontinuityCalled.reset();
        Thread.sleep(1000);
        assertEquals(0, mOnMediaTimeDiscontinuityCalled.getNumSignal());

        mMediaPlayer.reset();
    }

    @Test
    public void testLocalVideo_MKV_H265_1280x720_500kbps_25fps_AAC_Stereo_128kbps_44100Hz()
            throws Exception {
        playLoadedVideoTest("video_1280x720_mkv_h265_500kbps_25fps_aac_stereo_128kbps_44100hz.mkv",
                1280, 720);
    }
    @Test
    public void testLocalVideo_MP4_H264_480x360_500kbps_25fps_AAC_Stereo_128kbps_44110Hz()
            throws Exception {
        playLoadedVideoTest("video_480x360_mp4_h264_500kbps_25fps_aac_stereo_128kbps_44100hz.mp4",
                480, 360);
    }

    @Test
    public void testLocalVideo_MP4_H264_480x360_500kbps_30fps_AAC_Stereo_128kbps_44110Hz()
            throws Exception {
        playLoadedVideoTest("video_480x360_mp4_h264_500kbps_30fps_aac_stereo_128kbps_44100hz.mp4",
                480, 360);
    }

    @Test
    public void testLocalVideo_MP4_H264_480x360_1000kbps_25fps_AAC_Stereo_128kbps_44110Hz()
            throws Exception {
        playLoadedVideoTest("video_480x360_mp4_h264_1000kbps_25fps_aac_stereo_128kbps_44100hz.mp4",
                480, 360);
    }

    @Test
    public void testLocalVideo_MP4_H264_480x360_1000kbps_30fps_AAC_Stereo_128kbps_44110Hz()
            throws Exception {
        playLoadedVideoTest("video_480x360_mp4_h264_1000kbps_30fps_aac_stereo_128kbps_44100hz.mp4",
                480, 360);
    }

    @Test
    public void testLocalVideo_MP4_H264_480x360_1350kbps_25fps_AAC_Stereo_128kbps_44110Hz()
            throws Exception {
        playLoadedVideoTest("video_480x360_mp4_h264_1350kbps_25fps_aac_stereo_128kbps_44100hz.mp4",
                480, 360);
    }

    @Test
    public void testLocalVideo_MP4_H264_480x360_1350kbps_30fps_AAC_Stereo_128kbps_44110Hz()
            throws Exception {
        playLoadedVideoTest("video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_128kbps_44100hz.mp4",
                480, 360);
    }

    @Test
    public void testLocalVideo_MP4_H264_480x360_1350kbps_30fps_AAC_Stereo_128kbps_44110Hz_frag()
            throws Exception {
        playLoadedVideoTest(
                "video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_128kbps_44100hz_fragmented.mp4",
                480, 360);
    }


    @Test
    public void testLocalVideo_MP4_H264_480x360_1350kbps_30fps_AAC_Stereo_192kbps_44110Hz()
            throws Exception {
        playLoadedVideoTest("video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_192kbps_44100hz.mp4",
                480, 360);
    }

    @Test
    public void testLocalVideo_3gp_H263_176x144_56kbps_12fps_AAC_Mono_24kbps_11025Hz()
            throws Exception {
        playLoadedVideoTest("video_176x144_3gp_h263_56kbps_12fps_aac_mono_24kbps_11025hz.3gp", 176,
                144);
    }

    @Test
    public void testLocalVideo_3gp_H263_176x144_56kbps_12fps_AAC_Mono_24kbps_22050Hz()
            throws Exception {
        playLoadedVideoTest("video_176x144_3gp_h263_56kbps_12fps_aac_mono_24kbps_22050hz.3gp", 176,
                144);
    }

    @Test
    public void testLocalVideo_3gp_H263_176x144_56kbps_12fps_AAC_Stereo_24kbps_11025Hz()
            throws Exception {
        playLoadedVideoTest("video_176x144_3gp_h263_56kbps_12fps_aac_stereo_24kbps_11025hz.3gp",
                176, 144);
    }

    @Test
    public void testLocalVideo_3gp_H263_176x144_56kbps_12fps_AAC_Stereo_24kbps_22050Hz()
            throws Exception {
        playLoadedVideoTest("video_176x144_3gp_h263_56kbps_12fps_aac_stereo_24kbps_22050hz.3gp",
                176, 144);
    }

    @Test
    public void testLocalVideo_3gp_H263_176x144_56kbps_12fps_AAC_Stereo_128kbps_11025Hz()
            throws Exception {
        playLoadedVideoTest("video_176x144_3gp_h263_56kbps_12fps_aac_stereo_128kbps_11025hz.3gp",
                176, 144);
    }

    @Test
    public void testLocalVideo_3gp_H263_176x144_56kbps_12fps_AAC_Stereo_128kbps_22050Hz()
            throws Exception {
        playLoadedVideoTest("video_176x144_3gp_h263_56kbps_12fps_aac_stereo_128kbps_22050hz.3gp",
                176, 144);
    }

    @Test
    public void testLocalVideo_3gp_H263_176x144_56kbps_25fps_AAC_Mono_24kbps_11025Hz()
            throws Exception {
        playLoadedVideoTest("video_176x144_3gp_h263_56kbps_25fps_aac_mono_24kbps_11025hz.3gp", 176,
                144);
    }

    @Test
    public void testLocalVideo_3gp_H263_176x144_56kbps_25fps_AAC_Mono_24kbps_22050Hz()
            throws Exception {
        playLoadedVideoTest("video_176x144_3gp_h263_56kbps_25fps_aac_mono_24kbps_22050hz.3gp", 176,
                144);
    }

    @Test
    public void testLocalVideo_3gp_H263_176x144_56kbps_25fps_AAC_Stereo_24kbps_11025Hz()
            throws Exception {
        playLoadedVideoTest("video_176x144_3gp_h263_56kbps_25fps_aac_stereo_24kbps_11025hz.3gp",
                176, 144);
    }

    @Test
    public void testLocalVideo_3gp_H263_176x144_56kbps_25fps_AAC_Stereo_24kbps_22050Hz()
            throws Exception {
        playLoadedVideoTest("video_176x144_3gp_h263_56kbps_25fps_aac_stereo_24kbps_22050hz.3gp",
                176, 144);
    }

    @Test
    public void testLocalVideo_3gp_H263_176x144_56kbps_25fps_AAC_Stereo_128kbps_11025Hz()
            throws Exception {
        playLoadedVideoTest("video_176x144_3gp_h263_56kbps_25fps_aac_stereo_128kbps_11025hz.3gp",
                176, 144);
    }

    @Test
    public void testLocalVideo_3gp_H263_176x144_56kbps_25fps_AAC_Stereo_128kbps_22050Hz()
            throws Exception {
        playLoadedVideoTest("video_176x144_3gp_h263_56kbps_25fps_aac_stereo_128kbps_22050hz.3gp",
                176, 144);
    }

    @Test
    public void testLocalVideo_3gp_H263_176x144_300kbps_12fps_AAC_Mono_24kbps_11025Hz()
            throws Exception {
        playLoadedVideoTest("video_176x144_3gp_h263_300kbps_12fps_aac_mono_24kbps_11025hz.3gp", 176,
                144);
    }

    @Test
    public void testLocalVideo_3gp_H263_176x144_300kbps_12fps_AAC_Mono_24kbps_22050Hz()
            throws Exception {
        playLoadedVideoTest("video_176x144_3gp_h263_300kbps_12fps_aac_mono_24kbps_22050hz.3gp", 176,
                144);
    }

    @Test
    public void testLocalVideo_3gp_H263_176x144_300kbps_12fps_AAC_Stereo_24kbps_11025Hz()
            throws Exception {
        playLoadedVideoTest("video_176x144_3gp_h263_300kbps_12fps_aac_stereo_24kbps_11025hz.3gp",
                176, 144);
    }

    @Test
    public void testLocalVideo_3gp_H263_176x144_300kbps_12fps_AAC_Stereo_24kbps_22050Hz()
            throws Exception {
        playLoadedVideoTest("video_176x144_3gp_h263_300kbps_12fps_aac_stereo_24kbps_22050hz.3gp",
                176, 144);
    }

    @Test
    public void testLocalVideo_3gp_H263_176x144_300kbps_12fps_AAC_Stereo_128kbps_11025Hz()
            throws Exception {
        playLoadedVideoTest("video_176x144_3gp_h263_300kbps_12fps_aac_stereo_128kbps_11025hz.3gp",
                176, 144);
    }

    @Test
    public void testLocalVideo_3gp_H263_176x144_300kbps_12fps_AAC_Stereo_128kbps_22050Hz()
            throws Exception {
        playLoadedVideoTest("video_176x144_3gp_h263_300kbps_12fps_aac_stereo_128kbps_22050hz.3gp",
                176, 144);
    }

    @Test
    public void testLocalVideo_3gp_H263_176x144_300kbps_25fps_AAC_Mono_24kbps_11025Hz()
            throws Exception {
        playLoadedVideoTest("video_176x144_3gp_h263_300kbps_25fps_aac_mono_24kbps_11025hz.3gp", 176,
                144);
    }

    @Test
    public void testLocalVideo_3gp_H263_176x144_300kbps_25fps_AAC_Mono_24kbps_22050Hz()
            throws Exception {
        playLoadedVideoTest("video_176x144_3gp_h263_300kbps_25fps_aac_mono_24kbps_22050hz.3gp", 176,
                144);
    }

    @Test
    public void testLocalVideo_3gp_H263_176x144_300kbps_25fps_AAC_Stereo_24kbps_11025Hz()
            throws Exception {
        playLoadedVideoTest("video_176x144_3gp_h263_300kbps_25fps_aac_stereo_24kbps_11025hz.3gp",
                176, 144);
    }

    @Test
    public void testLocalVideo_3gp_H263_176x144_300kbps_25fps_AAC_Stereo_24kbps_22050Hz()
            throws Exception {
        playLoadedVideoTest("video_176x144_3gp_h263_300kbps_25fps_aac_stereo_24kbps_22050hz.3gp",
                176, 144);
    }

    @Test
    public void testLocalVideo_3gp_H263_176x144_300kbps_25fps_AAC_Stereo_128kbps_11025Hz()
            throws Exception {
        playLoadedVideoTest("video_176x144_3gp_h263_300kbps_25fps_aac_stereo_128kbps_11025hz.3gp",
                176, 144);
    }

    @Test
    public void testLocalVideo_3gp_H263_176x144_300kbps_25fps_AAC_Stereo_128kbps_22050Hz()
            throws Exception {
        playLoadedVideoTest("video_176x144_3gp_h263_300kbps_25fps_aac_stereo_128kbps_22050hz.3gp",
                176, 144);
    }

    @Test
    public void testLocalVideo_cp1251_3_a_ms_acm_mp3() throws Exception {
        playLoadedVideoTest("cp1251_3_a_ms_acm_mp3.mkv", -1, -1);
    }

    @Test
    public void testLocalVideo_mkv_audio_pcm_be() throws Exception {
        playLoadedVideoTest("mkv_audio_pcms16be.mkv", -1, -1);
    }

    @Test
    public void testLocalVideo_mkv_audio_pcm_le() throws Exception {
        playLoadedVideoTest("mkv_audio_pcms16le.mkv", -1, -1);
    }

    @Test
    public void testLocalVideo_segment000001_m2ts()
            throws Exception {
        if (checkLoadResource("segment000001.ts")) {
            mMediaPlayer.stop();
            assertTrue(checkLoadResource("segment000001_m2ts.mp4"));
            playLoadedVideo(320, 240, 0);
        } else {
            MediaUtils.skipTest("no mp2 support, skipping m2ts");
        }
    }

    private void readSubtitleTracks() throws Exception {
        mSubtitleTrackIndex.clear();
        MediaPlayer.TrackInfo[] trackInfos = mMediaPlayer.getTrackInfo();
        if (trackInfos == null || trackInfos.length == 0) {
            return;
        }

        Vector<Integer> subtitleTrackIndex = new Vector<>();
        for (int i = 0; i < trackInfos.length; ++i) {
            assertNotNull(trackInfos[i]);
            if (trackInfos[i].getTrackType() == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_SUBTITLE) {
                subtitleTrackIndex.add(i);
            }
        }

        mSubtitleTrackIndex.addAll(subtitleTrackIndex);
    }

    private void selectSubtitleTrack(int index) throws Exception {
        int trackIndex = mSubtitleTrackIndex.get(index);
        mMediaPlayer.selectTrack(trackIndex);
        mSelectedSubtitleIndex = index;
    }

    private void deselectSubtitleTrack(int index) throws Exception {
        int trackIndex = mSubtitleTrackIndex.get(index);
        mMediaPlayer.deselectTrack(trackIndex);
        if (mSelectedSubtitleIndex == index) {
            mSelectedSubtitleIndex = -1;
        }
    }

    @Test
    public void testDeselectTrackForSubtitleTracks() throws Throwable {
        if (!checkLoadResource("testvideo_with_2_subtitle_tracks.mp4")) {
            return; // skip;
        }

        getInstrumentation().waitForIdleSync();

        mMediaPlayer.setOnSubtitleDataListener((mp, data) -> {
            if (data != null && data.getData() != null) {
                mOnSubtitleDataCalled.signal();
            }
        });
        mMediaPlayer.setOnInfoListener((mp, what, extra) -> {
            if (what == MediaPlayer.MEDIA_INFO_METADATA_UPDATE) {
                mOnInfoCalled.signal();
            }
            return false;
        });

        mMediaPlayer.setDisplay(getActivity().getSurfaceHolder());
        mMediaPlayer.setScreenOnWhilePlaying(true);
        mMediaPlayer.setWakeMode(mContext, PowerManager.PARTIAL_WAKE_LOCK);

        mMediaPlayer.prepare();
        mMediaPlayer.start();
        assertTrue(mMediaPlayer.isPlaying());

        // Closed caption tracks are in-band.
        // So, those tracks will be found after processing a number of frames.
        mOnInfoCalled.waitForSignal(1500);

        mOnInfoCalled.reset();
        mOnInfoCalled.waitForSignal(1500);

        readSubtitleTracks();

        // Run twice to check if repeated selection-deselection on the same track works well.
        for (int i = 0; i < 2; i++) {
            // Waits until at least one subtitle is fired. Timeout is 2.5 seconds.
            selectSubtitleTrack(i);
            mOnSubtitleDataCalled.reset();
            assertTrue(mOnSubtitleDataCalled.waitForSignal(2500));

            // Try deselecting track.
            deselectSubtitleTrack(i);
            mOnSubtitleDataCalled.reset();
            assertFalse(mOnSubtitleDataCalled.waitForSignal(1500));
        }

        try {
            deselectSubtitleTrack(0);
            fail("Deselecting unselected track: expected RuntimeException, " +
                 "but no exception has been triggered.");
        } catch (RuntimeException e) {
            // expected
        }

        mMediaPlayer.stop();
    }

    @Test
    public void testChangeSubtitleTrack() throws Throwable {
        if (!checkLoadResource("testvideo_with_2_subtitle_tracks.mp4")) {
            return; // skip;
        }

        mMediaPlayer.setOnSubtitleDataListener((mp, data) -> {
            if (data != null && data.getData() != null) {
                mOnSubtitleDataCalled.signal();
            }
        });
        mMediaPlayer.setOnInfoListener((mp, what, extra) -> {
            if (what == MediaPlayer.MEDIA_INFO_METADATA_UPDATE) {
                mOnInfoCalled.signal();
            }
            return false;
        });

        mMediaPlayer.setDisplay(getActivity().getSurfaceHolder());
        mMediaPlayer.setScreenOnWhilePlaying(true);
        mMediaPlayer.setWakeMode(mContext, PowerManager.PARTIAL_WAKE_LOCK);

        mMediaPlayer.prepare();
        mMediaPlayer.start();
        assertTrue(mMediaPlayer.isPlaying());

        // Closed caption tracks are in-band.
        // So, those tracks will be found after processing a number of frames.
        mOnInfoCalled.waitForSignal(1500);

        mOnInfoCalled.reset();
        mOnInfoCalled.waitForSignal(1500);

        readSubtitleTracks();

        // Waits until at least two captions are fired. Timeout is 2.5 sec.
        selectSubtitleTrack(0);
        assertTrue(mOnSubtitleDataCalled.waitForCountedSignals(2, 2500) >= 2);

        mOnSubtitleDataCalled.reset();
        selectSubtitleTrack(1);
        assertTrue(mOnSubtitleDataCalled.waitForCountedSignals(2, 2500) >= 2);

        mMediaPlayer.stop();
    }

    @Test
    public void testOnSubtitleDataListener() throws Throwable {
        if (!checkLoadResource("testvideo_with_2_subtitle_tracks.mp4")) {
            return; // skip;
        }

        mMediaPlayer.setOnSubtitleDataListener((mp, data) -> {
            if (data != null && data.getData() != null
                    && data.getTrackIndex() == mSubtitleTrackIndex.get(0)) {
                mOnSubtitleDataCalled.signal();
            }
        });
        mMediaPlayer.setOnInfoListener((mp, what, extra) -> {
            if (what == MediaPlayer.MEDIA_INFO_METADATA_UPDATE) {
                mOnInfoCalled.signal();
            }
            return false;
        });

        mMediaPlayer.setDisplay(getActivity().getSurfaceHolder());
        mMediaPlayer.setScreenOnWhilePlaying(true);
        mMediaPlayer.setWakeMode(mContext, PowerManager.PARTIAL_WAKE_LOCK);

        mMediaPlayer.prepare();
        mMediaPlayer.start();
        assertTrue(mMediaPlayer.isPlaying());

        // Closed caption tracks are in-band.
        // So, those tracks will be found after processing a number of frames.
        mOnInfoCalled.waitForSignal(1500);

        mOnInfoCalled.reset();
        mOnInfoCalled.waitForSignal(1500);

        readSubtitleTracks();

        // Waits until at least two captions are fired. Timeout is 2.5 sec.
        selectSubtitleTrack(0);
        assertTrue(mOnSubtitleDataCalled.waitForCountedSignals(2, 2500) >= 2);

        // Check if there is no more notification after clearing listener.
        mMediaPlayer.clearOnSubtitleDataListener();
        mMediaPlayer.seekTo(0);
        mMediaPlayer.start();
        mOnSubtitleDataCalled.reset();
        Thread.sleep(2500);
        assertEquals(0, mOnSubtitleDataCalled.getNumSignal());

        mMediaPlayer.stop();
    }

    @Presubmit
    @Test
    public void testGetTrackInfoForVideoWithSubtitleTracks() throws Throwable {
        if (!checkLoadResource("testvideo_with_2_subtitle_tracks.mp4")) {
            return; // skip;
        }

        getInstrumentation().waitForIdleSync();

        mMediaPlayer.setOnInfoListener((mp, what, extra) -> {
            if (what == MediaPlayer.MEDIA_INFO_METADATA_UPDATE) {
                mOnInfoCalled.signal();
            }
            return false;
        });

        mMediaPlayer.setDisplay(getActivity().getSurfaceHolder());
        mMediaPlayer.setScreenOnWhilePlaying(true);
        mMediaPlayer.setWakeMode(mContext, PowerManager.PARTIAL_WAKE_LOCK);

        mMediaPlayer.prepare();
        mMediaPlayer.start();
        assertTrue(mMediaPlayer.isPlaying());

        // The media metadata will be changed while playing since closed caption tracks are in-band
        // and those tracks will be found after processing a number of frames. These tracks will be
        // found within one second.
        mOnInfoCalled.waitForSignal(1500);

        mOnInfoCalled.reset();
        mOnInfoCalled.waitForSignal(1500);

        readSubtitleTracks();
        assertEquals(2, mSubtitleTrackIndex.size());

        mMediaPlayer.stop();
    }

    private void readTimedTextTracks() throws Exception {
        mTimedTextTrackIndex.clear();
        MediaPlayer.TrackInfo[] trackInfos = mMediaPlayer.getTrackInfo();
        if (trackInfos == null || trackInfos.length == 0) {
            return;
        }

        Vector<Integer> externalTrackIndex = new Vector<>();
        for (int i = 0; i < trackInfos.length; ++i) {
            assertNotNull(trackInfos[i]);
            if (trackInfos[i].getTrackType() == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT) {
                MediaFormat format = trackInfos[i].getFormat();
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (MediaPlayer.MEDIA_MIMETYPE_TEXT_SUBRIP.equals(mime)) {
                    externalTrackIndex.add(i);
                } else {
                    mTimedTextTrackIndex.add(i);
                }
            }
        }

        mTimedTextTrackIndex.addAll(externalTrackIndex);
    }

    private int getTimedTextTrackCount() {
        return mTimedTextTrackIndex.size();
    }

    private void selectTimedTextTrack(int index) throws Exception {
        int trackIndex = mTimedTextTrackIndex.get(index);
        mMediaPlayer.selectTrack(trackIndex);
        mSelectedTimedTextIndex = index;
    }

    private void deselectTimedTextTrack(int index) throws Exception {
        int trackIndex = mTimedTextTrackIndex.get(index);
        mMediaPlayer.deselectTrack(trackIndex);
        if (mSelectedTimedTextIndex == index) {
            mSelectedTimedTextIndex = -1;
        }
    }

    @Test
    public void testDeselectTrackForTimedTextTrack() throws Throwable {
        if (!checkLoadResource("testvideo_with_2_timedtext_tracks.3gp")) {
            return; // skip;
        }
        runOnUiThread(() -> {
            try {
                loadSubtitleSource("test_subtitle1_srt.3gp");
            } catch (Exception e) {
                throw new AssertionFailedError(e.getMessage());
            }
        });
        getInstrumentation().waitForIdleSync();

        mMediaPlayer.setDisplay(getActivity().getSurfaceHolder());
        mMediaPlayer.setScreenOnWhilePlaying(true);
        mMediaPlayer.setWakeMode(mContext, PowerManager.PARTIAL_WAKE_LOCK);
        mMediaPlayer.setOnTimedTextListener((mp, text) -> {
            if (text != null) {
                String plainText = text.getText();
                if (plainText != null) {
                    mOnTimedTextCalled.signal();
                    Log.d(LOG_TAG, "text: " + plainText.trim());
                }
            }
        });
        mMediaPlayer.prepare();
        readTimedTextTracks();
        assertEquals(getTimedTextTrackCount(), 3);

        mMediaPlayer.start();
        assertTrue(mMediaPlayer.isPlaying());

        // Run twice to check if repeated selection-deselection on the same track works well.
        for (int i = 0; i < 2; i++) {
            // Waits until at least one subtitle is fired. Timeout is 1.5 sec.
            selectTimedTextTrack(0);
            mOnTimedTextCalled.reset();
            assertTrue(mOnTimedTextCalled.waitForSignal(1500));

            // Try deselecting track.
            deselectTimedTextTrack(0);
            mOnTimedTextCalled.reset();
            assertFalse(mOnTimedTextCalled.waitForSignal(1500));
        }

        // Run the same test for external subtitle track.
        for (int i = 0; i < 2; i++) {
            selectTimedTextTrack(2);
            mOnTimedTextCalled.reset();
            assertTrue(mOnTimedTextCalled.waitForSignal(1500));

            // Try deselecting track.
            deselectTimedTextTrack(2);
            mOnTimedTextCalled.reset();
            assertFalse(mOnTimedTextCalled.waitForSignal(1500));
        }

        try {
            deselectTimedTextTrack(0);
            fail("Deselecting unselected track: expected RuntimeException, " +
                 "but no exception has been triggered.");
        } catch (RuntimeException e) {
            // expected
        }

        mMediaPlayer.stop();
    }

    @Test
    public void testChangeTimedTextTrack() throws Throwable {
        testChangeTimedTextTrackWithSpeed(1.0f);
    }

    @Test
    public void testChangeTimedTextTrackFast() throws Throwable {
        testChangeTimedTextTrackWithSpeed(2.0f);
    }

    private void testChangeTimedTextTrackWithSpeed(float speed) throws Throwable {
        testTimedText("testvideo_with_2_timedtext_tracks.3gp", 2,
                new String[] {"test_subtitle1_srt.3gp", "test_subtitle2_srt.3gp"},
                new VerifyAndSignalTimedText(),
                () -> {
                    selectTimedTextTrack(0);
                    mOnTimedTextCalled.reset();

                    mMediaPlayer.start();
                    if (speed != 1.0f) {
                        mMediaPlayer.setPlaybackParams(new PlaybackParams().setSpeed(speed));
                    }

                    assertTrue(mMediaPlayer.isPlaying());

                    // Waits until at least two subtitles are fired. Timeout is 2.5 sec.
                    // Please refer the test srt files:
                    // test_subtitle1_srt.3gp and test_subtitle2_srt.3gp
                    assertTrue(mOnTimedTextCalled.waitForCountedSignals(2, 2500) >= 2);

                    selectTimedTextTrack(1);
                    mOnTimedTextCalled.reset();
                    assertTrue(mOnTimedTextCalled.waitForCountedSignals(2, 2500) >= 2);

                    selectTimedTextTrack(2);
                    mOnTimedTextCalled.reset();
                    assertTrue(mOnTimedTextCalled.waitForCountedSignals(2, 2500) >= 2);

                    selectTimedTextTrack(3);
                    mOnTimedTextCalled.reset();
                    assertTrue(mOnTimedTextCalled.waitForCountedSignals(2, 2500) >= 2);
                    mMediaPlayer.stop();

                    assertEquals("Wrong bounds count", 2, mBoundsCount);
                    return null;
                });
    }

    @Test
    public void testSeekWithTimedText() throws Throwable {
        AtomicInteger iteration = new AtomicInteger(5);
        AtomicInteger num = new AtomicInteger(10);
        try {
            Bundle args = InstrumentationRegistry.getArguments();
            num.set(Integer.parseInt(args.getString("num", "10")));
            iteration.set(Integer.parseInt(args.getString("iteration", "5")));
        } catch (Exception e) {
            Log.w(LOG_TAG, "bad num/iteration arguments, using default", e);
        }
        testTimedText("testvideo_with_2_timedtext_tracks.3gp", 2, new String [] {},
                new VerifyAndSignalTimedText(num.get(), true),
                () -> {
                    selectTimedTextTrack(0);
                    mOnSeekCompleteCalled.reset();
                    mOnTimedTextCalled.reset();
                    OnSeekCompleteListener seekListener = mp -> mOnSeekCompleteCalled.signal();
                    mMediaPlayer.setOnSeekCompleteListener(seekListener);
                    mMediaPlayer.start();
                    assertTrue(mMediaPlayer.isPlaying());
                    int n = num.get();
                    for (int i = 0; i < iteration.get(); ++i) {
                        assertEquals(n, mOnTimedTextCalled.waitForCountedSignals(n, 15000));
                        mOnTimedTextCalled.reset();
                        mMediaPlayer.seekTo(0);
                        mOnSeekCompleteCalled.waitForSignal();
                        mOnSeekCompleteCalled.reset();
                    }
                    mMediaPlayer.stop();
                    return null;
                });
    }

    private void testTimedText(
            String resource, int numInternalTracks, String[] subtitleResources,
            OnTimedTextListener onTimedTextListener, Callable<?> testBody) throws Throwable {
        if (!checkLoadResource(resource)) {
            return; // skip;
        }

        mMediaPlayer.setDisplay(getActivity().getSurfaceHolder());
        mMediaPlayer.setScreenOnWhilePlaying(true);
        mMediaPlayer.setWakeMode(mContext, PowerManager.PARTIAL_WAKE_LOCK);
        mMediaPlayer.setOnTimedTextListener(onTimedTextListener);
        mBoundsCount = 0;

        mMediaPlayer.prepare();
        assertFalse(mMediaPlayer.isPlaying());
        runOnUiThread(() -> {
            try {
                readTimedTextTracks();
            } catch (Exception e) {
                throw new AssertionFailedError(e.getMessage());
            }
        });
        getInstrumentation().waitForIdleSync();
        assertEquals(getTimedTextTrackCount(), numInternalTracks);

        runOnUiThread(() -> {
            try {
                // Adds two more external subtitle files.
                for (String subRes : subtitleResources) {
                    loadSubtitleSource(subRes);
                }
                readTimedTextTracks();
            } catch (Exception e) {
                throw new AssertionFailedError(e.getMessage());
            }
        });
        getInstrumentation().waitForIdleSync();
        assertEquals(getTimedTextTrackCount(), numInternalTracks + subtitleResources.length);

        testBody.call();
    }

    @Presubmit
    @Test
    public void testGetTrackInfoForVideoWithTimedText() throws Throwable {
        if (!checkLoadResource("testvideo_with_2_timedtext_tracks.3gp")) {
            return; // skip;
        }
        runOnUiThread(() -> {
            try {
                loadSubtitleSource("test_subtitle1_srt.3gp");
                loadSubtitleSource("test_subtitle2_srt.3gp");
            } catch (Exception e) {
                throw new AssertionFailedError(e.getMessage());
            }
        });
        getInstrumentation().waitForIdleSync();
        mMediaPlayer.prepare();
        mMediaPlayer.start();

        readTimedTextTracks();
        selectTimedTextTrack(2);

        int count = 0;
        MediaPlayer.TrackInfo[] trackInfos = mMediaPlayer.getTrackInfo();
        assertTrue(trackInfos != null && trackInfos.length != 0);
        for (MediaPlayer.TrackInfo trackInfo : trackInfos) {
            assertNotNull(trackInfo);
            if (trackInfo.getTrackType() == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_TIMEDTEXT) {
                String trackLanguage = trackInfo.getLanguage();
                assertNotNull(trackLanguage);
                trackLanguage = trackLanguage.trim();
                Log.d(LOG_TAG, "track info lang: " + trackLanguage);
                assertTrue("Should not see empty track language with our test data.",
                        trackLanguage.length() > 0);
                count++;
            }
        }
        // There are 4 subtitle tracks in total in our test data.
        assertEquals(4, count);
    }

    /*
     *  This test assumes the resources being tested are between 8 and 14 seconds long
     *  The ones being used here are 10 seconds long.
     */
    @Test
    public void testResumeAtEnd() throws Throwable {
        int testsRun =
            testResumeAtEnd("loudsoftmp3.mp3") +
            testResumeAtEnd("loudsoftwav.wav") +
            testResumeAtEnd("loudsoftogg.ogg") +
            testResumeAtEnd("loudsoftitunes.m4a") +
            testResumeAtEnd("loudsoftfaac.m4a") +
            testResumeAtEnd("loudsoftaac.aac");
        if (testsRun == 0) {
            MediaUtils.skipTest("no decoder found");
        }
    }

    // returns 1 if test was run, 0 otherwise
    private int testResumeAtEnd(final String res) throws Throwable {
        if (!loadResource(res)) {
            Log.i(LOG_TAG, "testResumeAtEnd: No decoder found for " + res + " --- skipping.");
            return 0; // skip
        }
        mMediaPlayer.prepare();
        mOnCompletionCalled.reset();
        mMediaPlayer.setOnCompletionListener(mp -> {
            mOnCompletionCalled.signal();
            mMediaPlayer.start();
        });
        // skip the first part of the file so we reach EOF sooner
        mMediaPlayer.seekTo(5000);
        mMediaPlayer.start();
        // sleep long enough that we restart playback at least once, but no more
        Thread.sleep(10000);
        assertTrue("MediaPlayer should still be playing", mMediaPlayer.isPlaying());
        mMediaPlayer.reset();
        assertEquals("wrong number of repetitions", 1, mOnCompletionCalled.getNumSignal());
        return 1;
    }

    @Test
    public void testPositionAtEnd() throws Throwable {
        int testsRun =
            testPositionAtEnd("test1m1shighstereo.mp3") +
            testPositionAtEnd("loudsoftmp3.mp3") +
            testPositionAtEnd("loudsoftwav.wav") +
            testPositionAtEnd("loudsoftogg.ogg") +
            testPositionAtEnd("loudsoftitunes.m4a") +
            testPositionAtEnd("loudsoftfaac.m4a") +
            testPositionAtEnd("loudsoftaac.aac");
        if (testsRun == 0) {
            MediaUtils.skipTest(LOG_TAG, "no decoder found");
        }
    }

    private int testPositionAtEnd(final String res) throws Throwable {
        if (!loadResource(res)) {
            Log.i(LOG_TAG, "testPositionAtEnd: No decoder found for " + res + " --- skipping.");
            return 0; // skip
        }
        mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mMediaPlayer.prepare();
        int duration = mMediaPlayer.getDuration();
        assertTrue("resource too short", duration > 6000);
        mOnCompletionCalled.reset();
        mMediaPlayer.setOnCompletionListener(mp -> mOnCompletionCalled.signal());
        mMediaPlayer.seekTo(duration - 5000);
        mMediaPlayer.start();
        while (mMediaPlayer.isPlaying()) {
            Log.i("@@@@", "position: " + mMediaPlayer.getCurrentPosition());
            Thread.sleep(500);
        }
        Log.i("@@@@", "final position: " + mMediaPlayer.getCurrentPosition());
        assertTrue(mMediaPlayer.getCurrentPosition() > duration - 1000);
        mMediaPlayer.reset();
        return 1;
    }

    @Test
    public void testCallback() throws Throwable {
        final int mp4Duration = 8484;

        if (!checkLoadResource("testvideo.3gp")) {
            return; // skip;
        }

        mMediaPlayer.setDisplay(getActivity().getSurfaceHolder());
        mMediaPlayer.setScreenOnWhilePlaying(true);

        mMediaPlayer.setOnVideoSizeChangedListener(
                (mp, width, height) -> mOnVideoSizeChangedCalled.signal());

        mMediaPlayer.setOnPreparedListener(mp -> mOnPrepareCalled.signal());

        mMediaPlayer.setOnSeekCompleteListener(mp -> mOnSeekCompleteCalled.signal());

        mOnCompletionCalled.reset();
        mMediaPlayer.setOnCompletionListener(mp -> mOnCompletionCalled.signal());

        mMediaPlayer.setOnErrorListener((mp, what, extra) -> {
            mOnErrorCalled.signal();
            return false;
        });

        mMediaPlayer.setOnInfoListener((mp, what, extra) -> {
            mOnInfoCalled.signal();
            return false;
        });

        assertFalse(mOnPrepareCalled.isSignalled());
        assertFalse(mOnVideoSizeChangedCalled.isSignalled());
        mMediaPlayer.prepare();
        mOnPrepareCalled.waitForSignal();
        mOnVideoSizeChangedCalled.waitForSignal();
        mOnSeekCompleteCalled.reset();
        mMediaPlayer.seekTo(mp4Duration >> 1);
        mOnSeekCompleteCalled.waitForSignal();
        assertFalse(mOnCompletionCalled.isSignalled());
        mMediaPlayer.start();
        while(mMediaPlayer.isPlaying()) {
            Thread.sleep(SLEEP_TIME);
        }
        assertFalse(mMediaPlayer.isPlaying());
        mOnCompletionCalled.waitForSignal();
        assertFalse(mOnErrorCalled.isSignalled());
        mMediaPlayer.stop();
        mMediaPlayer.start();
        mOnErrorCalled.waitForSignal();
    }

    @Test
    public void testRecordAndPlay() throws Exception {
        if (!hasMicrophone()) {
            MediaUtils.skipTest(LOG_TAG, "no microphone");
            return;
        }
        if (!MediaUtils.checkDecoder(MediaFormat.MIMETYPE_AUDIO_AMR_NB)
                || !MediaUtils.checkEncoder(MediaFormat.MIMETYPE_AUDIO_AMR_NB)) {
            return; // skip
        }
        File outputFile = new File(Environment.getExternalStorageDirectory(),
                "record_and_play.3gp");
        String outputFileLocation = outputFile.getAbsolutePath();
        try {
            recordMedia(outputFileLocation);
            MediaPlayer mp = new MediaPlayer();
            try {
                mp.setDataSource(outputFileLocation);
                mp.prepareAsync();
                Thread.sleep(SLEEP_TIME);
                playAndStop(mp);
            } finally {
                mp.release();
            }

            Uri uri = Uri.parse(outputFileLocation);
            mp = new MediaPlayer();
            try {
                mp.setDataSource(mContext, uri);
                mp.prepareAsync();
                Thread.sleep(SLEEP_TIME);
                playAndStop(mp);
            } finally {
                mp.release();
            }

            try {
                mp = MediaPlayer.create(mContext, uri);
                playAndStop(mp);
            } finally {
                if (mp != null) {
                    mp.release();
                }
            }

            try {
                mp = MediaPlayer.create(mContext, uri, getActivity().getSurfaceHolder());
                playAndStop(mp);
            } finally {
                if (mp != null) {
                    mp.release();
                }
            }
        } finally {
            outputFile.delete();
        }
    }

    private void playAndStop(MediaPlayer mp) throws Exception {
        mp.start();
        Thread.sleep(SLEEP_TIME);
        mp.stop();
    }

    private void recordMedia(String outputFile) throws Exception {
        MediaRecorder mr = new MediaRecorder();
        try {
            mr.setAudioSource(MediaRecorder.AudioSource.MIC);
            mr.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mr.setOutputFile(outputFile);

            mr.prepare();
            mr.start();
            Thread.sleep(SLEEP_TIME);
            mr.stop();
        } finally {
            mr.release();
        }
    }

    private boolean hasMicrophone() {
        return getActivity().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_MICROPHONE);
    }

    // Smoke test playback from a MediaDataSource.
    @Test
    public void testPlaybackFromAMediaDataSource() throws Exception {
        final String res = "video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_192kbps_44100hz.mp4";
        final int duration = 10000;

        Preconditions.assertTestFileExists(mInpPrefix + res);
        if (!MediaUtils.hasCodecsForResource(mInpPrefix + res)) {
            return;
        }

        TestMediaDataSource dataSource =
                TestMediaDataSource.fromAssetFd(getAssetFileDescriptorFor(res));
        // Test returning -1 from getSize() to indicate unknown size.
        dataSource.returnFromGetSize(-1);
        mMediaPlayer.setDataSource(dataSource);
        playLoadedVideo(null, null, -1);
        assertTrue(mMediaPlayer.isPlaying());

        // Test pause and restart.
        mMediaPlayer.pause();
        Thread.sleep(SLEEP_TIME);
        assertFalse(mMediaPlayer.isPlaying());
        mMediaPlayer.start();
        assertTrue(mMediaPlayer.isPlaying());

        // Test reset.
        mMediaPlayer.stop();
        mMediaPlayer.reset();
        mMediaPlayer.setDataSource(dataSource);
        mMediaPlayer.prepare();
        mMediaPlayer.start();
        assertTrue(mMediaPlayer.isPlaying());

        // Test seek. Note: the seek position is cached and returned as the
        // current position so there's no point in comparing them.
        mMediaPlayer.seekTo(duration - SLEEP_TIME);
        while (mMediaPlayer.isPlaying()) {
            Thread.sleep(SLEEP_TIME);
        }
    }

    @Presubmit
    @Test
    public void testNullMediaDataSourceIsRejected() throws Exception {
        try {
            mMediaPlayer.setDataSource((MediaDataSource) null);
            fail("Null MediaDataSource was accepted");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Presubmit
    @Test
    public void testMediaDataSourceIsClosedOnReset() throws Exception {
        TestMediaDataSource dataSource = new TestMediaDataSource(new byte[0]);
        mMediaPlayer.setDataSource(dataSource);
        mMediaPlayer.reset();
        assertTrue(dataSource.isClosed());
    }

    @Presubmit
    @Test
    public void testPlaybackFailsIfMediaDataSourceThrows() throws Exception {
        final String res = "video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_192kbps_44100hz.mp4";
        Preconditions.assertTestFileExists(mInpPrefix + res);
        if (!MediaUtils.hasCodecsForResource(mInpPrefix + res)) {
            return;
        }

        setOnErrorListener();
        TestMediaDataSource dataSource =
                TestMediaDataSource.fromAssetFd(getAssetFileDescriptorFor(res));
        mMediaPlayer.setDataSource(dataSource);
        mMediaPlayer.prepare();

        dataSource.throwFromReadAt();
        mMediaPlayer.start();
        assertTrue(mOnErrorCalled.waitForSignal());
    }

    @Presubmit
    @Test
    public void testPlaybackFailsIfMediaDataSourceReturnsAnError() throws Exception {
        final String res = "video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_192kbps_44100hz.mp4";
        Preconditions.assertTestFileExists(mInpPrefix + res);
        if (!MediaUtils.hasCodecsForResource(mInpPrefix + res)) {
            return;
        }

        setOnErrorListener();
        TestMediaDataSource dataSource =
                TestMediaDataSource.fromAssetFd(getAssetFileDescriptorFor(res));
        mMediaPlayer.setDataSource(dataSource);
        mMediaPlayer.prepare();

        dataSource.returnFromReadAt(-2);
        mMediaPlayer.start();
        assertTrue(mOnErrorCalled.waitForSignal());
    }

    @Presubmit
    @Test
    public void testSetOnRtpRxNoticeListenerWithoutPermission() {
        try {
            mMediaPlayer.setOnRtpRxNoticeListener(
                    mContext, Runnable::run, (mp, noticeType, params) -> {});
            fail();
        } catch (IllegalArgumentException e) {
            // Expected. We don't have the required permission.
        }
    }

    @Presubmit
    @Test
    public void testSetOnRtpRxNoticeListenerWithPermission() {
        try {
            getInstrumentation().getUiAutomation().adoptShellPermissionIdentity();
            mMediaPlayer.setOnRtpRxNoticeListener(
                    mContext, Runnable::run, (mp, noticeType, params) -> {});
        } finally {
            getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
        }
    }

    @Presubmit
    @Test
    public void testConstructorWithNullContextFails() {
        assertThrows(NullPointerException.class, () -> new MediaPlayer(/*context=*/null));
    }

    /** {@link ContentProvider} implementation which serves local files using content:// URIs. */
    public static final class TestFileProvider extends FileProvider {
        public TestFileProvider() {
            super(R.xml.media_player_test_content_path);
        }
    }

}
