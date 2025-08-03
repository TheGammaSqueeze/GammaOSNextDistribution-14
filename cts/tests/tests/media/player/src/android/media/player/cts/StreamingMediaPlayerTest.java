/*
 * Copyright (C) 2011 The Android Open Source Project
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.media.MediaPlayer.TrackInfo;
import android.media.TimedMetaData;
import android.media.cts.MediaPlayerTestBase;
import android.net.Uri;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;
import android.webkit.cts.CtsTestServer;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.MediaUtils;
import com.android.compatibility.common.util.NonMainlineTest;
import com.android.compatibility.common.util.Preconditions;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.HttpCookie;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests of MediaPlayer streaming capabilities.
 */
@NonMainlineTest
@AppModeFull(reason = "TODO: evaluate and port to instant")
@RunWith(AndroidJUnit4.class)
public class StreamingMediaPlayerTest extends MediaPlayerTestBase {

    private static final String TAG = "StreamingMediaPlayerTest";
    static final String mInpPrefix = WorkDir.getMediaDirString() + "assets/";

    private static final String MODULE_NAME = "CtsMediaPlayerTestCases";
    private static final int HLS_PLAYBACK_TIME_MS = 20 * 1000;
    private CtsTestServer mServer;

    @Before
    @Override
    public void setUp() throws Throwable {
        super.setUp();
        mServer = new CtsTestServer(mContext);
    }

    @After
    @Override
    public void tearDown() {
        mServer.shutdown();
        super.tearDown();
    }

/* RTSP tests are more flaky and vulnerable to network condition.
   Disable until better solution is available
    // Streaming RTSP video from YouTube
    public void testRTSP_H263_AMR_Video1() throws Exception {
        playVideoTest("rtsp://v2.cache7.c.youtube.com/video.3gp?cid=0x271de9756065677e"
                + "&fmt=13&user=android-device-test", 176, 144);
    }
    public void testRTSP_H263_AMR_Video2() throws Exception {
        playVideoTest("rtsp://v2.cache7.c.youtube.com/video.3gp?cid=0xc80658495af60617"
                + "&fmt=13&user=android-device-test", 176, 144);
    }

    public void testRTSP_MPEG4SP_AAC_Video1() throws Exception {
        playVideoTest("rtsp://v2.cache7.c.youtube.com/video.3gp?cid=0x271de9756065677e"
                + "&fmt=17&user=android-device-test", 176, 144);
    }
    public void testRTSP_MPEG4SP_AAC_Video2() throws Exception {
        playVideoTest("rtsp://v2.cache7.c.youtube.com/video.3gp?cid=0xc80658495af60617"
                + "&fmt=17&user=android-device-test", 176, 144);
    }

    public void testRTSP_H264Base_AAC_Video1() throws Exception {
        playVideoTest("rtsp://v2.cache7.c.youtube.com/video.3gp?cid=0x271de9756065677e"
                + "&fmt=18&user=android-device-test", 480, 270);
    }
    public void testRTSP_H264Base_AAC_Video2() throws Exception {
        playVideoTest("rtsp://v2.cache7.c.youtube.com/video.3gp?cid=0xc80658495af60617"
                + "&fmt=18&user=android-device-test", 480, 270);
    }
*/

    @Test
    public void testHTTP_H263_AMR_Video1() throws Exception {
        if (!MediaUtils.checkDecoder(MediaFormat.MIMETYPE_VIDEO_H263, MediaFormat.MIMETYPE_AUDIO_AMR_NB)) {
            return; // skip
        }

        localStreamingTest("streaming_media_player_test_http_h263_amr_video1.mp4", 176, 144);
    }

    @Test
    public void testHTTP_H263_AMR_Video2() throws Exception {
        if (!MediaUtils.checkDecoder(MediaFormat.MIMETYPE_VIDEO_H263, MediaFormat.MIMETYPE_AUDIO_AMR_NB)) {
            return; // skip
        }

        localStreamingTest("streaming_media_player_test_http_h263_amr_video2.mp4", 176, 144);
    }

    @Test
    public void testHTTP_MPEG4SP_AAC_Video1() throws Exception {
        if (!MediaUtils.checkDecoder(MediaFormat.MIMETYPE_VIDEO_MPEG4)) {
            return; // skip
        }

        localStreamingTest("streaming_media_player_test_http_mpeg4_sp_aac_video1.mp4", 176, 144);
    }

    @Test
    public void testHTTP_MPEG4SP_AAC_Video2() throws Exception {
        if (!MediaUtils.checkDecoder(MediaFormat.MIMETYPE_VIDEO_MPEG4)) {
            return; // skip
        }

        localStreamingTest("streaming_media_player_test_http_mpeg4_sp_aac_video2.mp4", 176, 144);
    }

    @Test
    public void testHTTP_H264Base_AAC_Video1() throws Exception {
        if (!MediaUtils.checkDecoder(MediaFormat.MIMETYPE_VIDEO_AVC)) {
            return; // skip
        }

        localStreamingTest("streaming_media_player_test_http_h264_base_aac_video1.mp4", 640, 360);
    }

    @Test
    public void testHTTP_H264Base_AAC_Video2() throws Exception {
        if (!MediaUtils.checkDecoder(MediaFormat.MIMETYPE_VIDEO_AVC)) {
            return; // skip
        }

        localStreamingTest("streaming_media_player_test_http_h264_base_aac_video2.mp4", 640, 360);
    }

    // Streaming HLS video downloaded from YouTube
    @Test
    public void testHLS() throws Exception {
        if (!MediaUtils.checkDecoder(MediaFormat.MIMETYPE_VIDEO_AVC)) {
            return; // skip
        }

        localHlsTest("hls_variant/index.m3u8", false /*isAudioOnly*/);
    }

    @Test
    public void testHlsWithHeadersCookies() throws Exception {
        if (!MediaUtils.checkDecoder(MediaFormat.MIMETYPE_VIDEO_AVC)) {
            return; // skip
        }

        // TODO: fake values for headers/cookies till we find a server that actually needs them
        HashMap<String, String> headers = new HashMap<>();
        headers.put("header0", "value0");
        headers.put("header1", "value1");

        String cookieName = "auth_1234567";
        String cookieValue = "0123456789ABCDEF0123456789ABCDEF";
        HttpCookie cookie = new HttpCookie(cookieName, cookieValue);
        cookie.setHttpOnly(true);
        cookie.setDomain("www.youtube.com");
        cookie.setPath("/");        // all paths
        cookie.setSecure(false);
        cookie.setDiscard(false);
        cookie.setMaxAge(24 * 3600);  // 24hrs

        java.util.Vector<HttpCookie> cookies = new java.util.Vector<HttpCookie>();
        cookies.add(cookie);

        localHlsTest("hls_variant/index.m3u8", false /*isAudioOnly*/);
    }

    @Test
    public void testHlsSampleAes_bbb_audio_only_overridable() throws Exception {
        if (!MediaUtils.checkDecoder(MediaFormat.MIMETYPE_VIDEO_AVC)) {
            return; // skip
        }
        localHlsTest("audio_only/index.m3u8", true /*isAudioOnly*/);
    }

    @Test
    public void testHlsSampleAes_bbb_unmuxed_1500k() throws Exception {
        if (!MediaUtils.checkDecoder(MediaFormat.MIMETYPE_VIDEO_AVC)) {
            return; // skip
        }
        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1920, 1080);
        String[] decoderNames = MediaUtils.getDecoderNames(false, format);

        if (decoderNames.length == 0) {
            MediaUtils.skipTest("No decoders for " + format);
        } else {
            localHlsTest("unmuxed_1500k/index.m3u8", false /*isAudioOnly*/);
        }
    }


    // Streaming audio from local HTTP server
    @Test
    public void testPlayMp3Stream1() throws Throwable {
        localHttpAudioStreamTest("ringer.mp3", false, false);
    }
    @Test
    public void testPlayMp3Stream2() throws Throwable {
        localHttpAudioStreamTest("ringer.mp3", false, false);
    }
    @Test
    public void testPlayMp3StreamRedirect() throws Throwable {
        localHttpAudioStreamTest("ringer.mp3", true, false);
    }
    @Test
    public void testPlayMp3StreamNoLength() throws Throwable {
        localHttpAudioStreamTest("noiseandchirps.mp3", false, true);
    }
    @Test
    public void testPlayOggStream() throws Throwable {
        localHttpAudioStreamTest("noiseandchirps.ogg", false, false);
    }
    @Test
    public void testPlayOggStreamRedirect() throws Throwable {
        localHttpAudioStreamTest("noiseandchirps.ogg", true, false);
    }
    @Test
    public void testPlayOggStreamNoLength() throws Throwable {
        localHttpAudioStreamTest("noiseandchirps.ogg", false, true);
    }
    @Test
    public void testPlayMp3Stream1Ssl() throws Throwable {
        localHttpsAudioStreamTest("ringer.mp3", false, false);
    }

    private void localHttpAudioStreamTest(final String name, boolean redirect, boolean nolength)
            throws Throwable {
        Preconditions.assertTestFileExists(mInpPrefix + name);
        String stream_url = null;
        if (redirect) {
            // Stagefright doesn't have a limit, but we can't test support of infinite redirects
            // Up to 4 redirects seems reasonable though.
            stream_url = mServer.getRedirectingAssetUrl(mInpPrefix + name, 4);
        } else {
            stream_url = mServer.getAssetUrl(mInpPrefix + name);
        }
        if (nolength) {
            stream_url = stream_url + "?" + CtsTestServer.NOLENGTH_POSTFIX;
        }

        if (!MediaUtils.checkCodecsForPath(mContext, stream_url)) {
            return; // skip
        }

        mMediaPlayer.setDataSource(stream_url);

        mMediaPlayer.setDisplay(getActivity().getSurfaceHolder());
        mMediaPlayer.setScreenOnWhilePlaying(true);

        mOnBufferingUpdateCalled.reset();
        mMediaPlayer.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {
            @Override
            public void onBufferingUpdate(MediaPlayer mp, int percent) {
                mOnBufferingUpdateCalled.signal();
            }
        });
        mMediaPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                fail("Media player had error " + what + " playing " + name);
                return true;
            }
        });

        assertFalse(mOnBufferingUpdateCalled.isSignalled());
        mMediaPlayer.prepare();

        if (nolength) {
            mMediaPlayer.start();
            Thread.sleep(LONG_SLEEP_TIME);
            assertFalse(mMediaPlayer.isPlaying());
        } else {
            mOnBufferingUpdateCalled.waitForSignal();
            mMediaPlayer.start();
            Thread.sleep(SLEEP_TIME);
        }
        mMediaPlayer.stop();
        mMediaPlayer.reset();
    }
    private void localHttpsAudioStreamTest(final String name, boolean redirect, boolean nolength)
            throws Throwable {
        Preconditions.assertTestFileExists(mInpPrefix + name);
        CtsTestServer server = new CtsTestServer(mContext, /* ssl */ true);
        try {
            String stream_url = null;
            if (redirect) {
                // Stagefright doesn't have a limit, but we can't test support of infinite redirects
                // Up to 4 redirects seems reasonable though.
                stream_url = server.getRedirectingAssetUrl(mInpPrefix + name, 4);
            } else {
                stream_url = server.getAssetUrl(mInpPrefix + name);
            }
            if (nolength) {
                stream_url = stream_url + "?" + CtsTestServer.NOLENGTH_POSTFIX;
            }

            mMediaPlayer.setDataSource(stream_url);

            mMediaPlayer.setDisplay(getActivity().getSurfaceHolder());
            mMediaPlayer.setScreenOnWhilePlaying(true);

            mOnBufferingUpdateCalled.reset();
            mMediaPlayer.setOnBufferingUpdateListener(
                    (mp, percent) -> mOnBufferingUpdateCalled.signal());
            mMediaPlayer.setOnErrorListener((mp, what, extra) -> {
                fail("Media player had error " + what + " playing " + name);
                return true;
            });

            assertFalse(mOnBufferingUpdateCalled.isSignalled());
            try {
                mMediaPlayer.prepare();
            } catch (Exception ex) {
                return;
            }
            fail("https playback should have failed");
        } finally {
            server.shutdown();
        }
    }

    @Test
    public void testPlayHlsStream() throws Throwable {
        if (!MediaUtils.checkDecoder(MediaFormat.MIMETYPE_VIDEO_AVC)) {
            return; // skip
        }
        localHlsTest("hls.m3u8", false, false, false /*isAudioOnly*/);
    }

    @Test
    public void testPlayHlsStreamWithQueryString() throws Throwable {
        if (!MediaUtils.checkDecoder(MediaFormat.MIMETYPE_VIDEO_AVC)) {
            return; // skip
        }
        localHlsTest("hls.m3u8", true, false, false /*isAudioOnly*/);
    }

    @Test
    public void testPlayHlsStreamWithRedirect() throws Throwable {
        if (!MediaUtils.checkDecoder(MediaFormat.MIMETYPE_VIDEO_AVC)) {
            return; // skip
        }
        localHlsTest("hls.m3u8", false, true, false /*isAudioOnly*/);
    }

    @Test
    public void testPlayHlsStreamWithTimedId3() throws Throwable {
        if (!MediaUtils.checkDecoder(MediaFormat.MIMETYPE_VIDEO_AVC)) {
            Log.d(TAG, "Device doesn't have video codec, skipping test");
            return;
        }
        Preconditions.assertTestFileExists(mInpPrefix + "prog_index.m3u8");

        // counter must be final if we want to access it inside onTimedMetaData;
        // use AtomicInteger so we can have a final counter object with mutable integer value.
        final AtomicInteger counter = new AtomicInteger();
        String stream_url = mServer.getAssetUrl(mInpPrefix + "prog_index.m3u8");
        mMediaPlayer.setDataSource(stream_url);
        mMediaPlayer.setDisplay(getActivity().getSurfaceHolder());
        mMediaPlayer.setScreenOnWhilePlaying(true);
        mMediaPlayer.setWakeMode(mContext, PowerManager.PARTIAL_WAKE_LOCK);
        mMediaPlayer.setOnTimedMetaDataAvailableListener(
                new MediaPlayer.OnTimedMetaDataAvailableListener() {
                    @Override
                    public void onTimedMetaDataAvailable(MediaPlayer mp, TimedMetaData md) {
                        counter.incrementAndGet();
                        int pos = mp.getCurrentPosition();
                        long timeUs = md.getTimestamp();
                        byte[] rawData = md.getMetaData();
                        // Raw data contains an id3 tag holding the decimal string representation of
                        // the associated time stamp rounded to the closest half second.

                        int offset = 0;
                        offset += 3; // "ID3"
                        offset += 2; // version
                        offset += 1; // flags
                        offset += 4; // size
                        offset += 4; // "TXXX"
                        offset += 4; // frame size
                        offset += 2; // frame flags
                        offset += 1; // "\x03" : UTF-8 encoded Unicode
                        offset += 1; // "\x00" : null-terminated empty description

                        int length = rawData.length;
                        length -= offset;
                        length -= 1; // "\x00" : terminating null

                        String data = new String(rawData, offset, length);
                        int dataTimeUs = Integer.parseInt(data);
                        assertTrue("Timed ID3 timestamp does not match content",
                                Math.abs(dataTimeUs - timeUs) < 500000);
                        assertTrue("Timed ID3 arrives after timestamp", pos * 1000 < timeUs);
                    }
                });

        final Object completion = new Object();
        mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            int mRun;
            @Override
            public void onCompletion(MediaPlayer mp) {
                if (mRun++ == 0) {
                    mMediaPlayer.seekTo(0);
                    mMediaPlayer.start();
                } else {
                    mMediaPlayer.stop();
                    synchronized (completion) {
                        completion.notify();
                    }
                }
            }
        });

        mMediaPlayer.prepare();

        // Select the ID3 track before calling start() to ensure all the timed
        // ID3s are received.
        int i = -1;
        TrackInfo[] trackInfos = mMediaPlayer.getTrackInfo();
        for (i = 0; i < trackInfos.length; i++) {
            TrackInfo trackInfo = trackInfos[i];
            if (trackInfo.getTrackType() == TrackInfo.MEDIA_TRACK_TYPE_METADATA) {
                break;
            }
        }
        assertTrue("Stream has no timed ID3 track", i >= 0);
        mMediaPlayer.selectTrack(i);

        mMediaPlayer.start();
        assertTrue("MediaPlayer not playing", mMediaPlayer.isPlaying());

        synchronized (completion) {
            completion.wait();
        }

        // There are a total of 19 metadata access units in the test stream; every one of them
        // should be received twice: once before the seek and once after.
        assertTrue("Incorrect number of timed ID3s received", counter.get() == 38);
    }

    private static class WorkerWithPlayer implements Runnable {
        private final Object mLock = new Object();
        private Looper mLooper;
        private MediaPlayer mMediaPlayer;

        /**
         * Creates a worker thread with the given name. The thread
         * then runs a {@link android.os.Looper}.
         * @param name A name for the new thread
         */
        WorkerWithPlayer(String name) {
            Thread t = new Thread(null, this, name);
            t.setPriority(Thread.MIN_PRIORITY);
            t.start();
            synchronized (mLock) {
                while (mLooper == null) {
                    try {
                        mLock.wait();
                    } catch (InterruptedException ex) {
                    }
                }
            }
        }

        public MediaPlayer getPlayer() {
            return mMediaPlayer;
        }

        @Override
        public void run() {
            synchronized (mLock) {
                Looper.prepare();
                mLooper = Looper.myLooper();
                mMediaPlayer = new MediaPlayer();
                mLock.notifyAll();
            }
            Looper.loop();
        }

        public void quit() {
            mLooper.quit();
            mMediaPlayer.release();
        }
    }

    @Test
    public void testBlockingReadRelease() throws Throwable {
        WorkerWithPlayer worker = new WorkerWithPlayer("player");
        final MediaPlayer mp = worker.getPlayer();

        Preconditions.assertTestFileExists(mInpPrefix + "noiseandchirps.ogg");
        String path = mServer.getDelayedAssetUrl(mInpPrefix + "noiseandchirps.ogg", 15000);
        mp.setDataSource(path);
        mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                fail("prepare should not succeed");
            }
        });
        mp.prepareAsync();
        Thread.sleep(1000);
        long start = SystemClock.elapsedRealtime();
        mp.release();
        long end = SystemClock.elapsedRealtime();
        long releaseDuration = (end - start);
        assertTrue("release took too long: " + releaseDuration, releaseDuration < 1000);

        // give the worker a bit of time to start processing the message before shutting it down
        Thread.sleep(5000);
        worker.quit();
    }

    private void localStreamingTest(String name, int width, int height) throws Exception {
        Preconditions.assertTestFileExists(mInpPrefix + name);
        String streamUrl = mServer.getAssetUrl(mInpPrefix + name);
        playVideoTest(streamUrl, width, height);
    }

    private void localHlsTest(final String name, boolean appendQueryString,
            boolean redirect, boolean isAudioOnly) throws Exception {
        localHlsTest(name, null, null, appendQueryString, redirect, isAudioOnly);
    }

    private void localHlsTest(final String name, boolean isAudioOnly)
            throws Exception {
        localHlsTest(name, null, null, false, false, isAudioOnly);
    }

    private void localHlsTest(String name, Map<String, String> headers, List<HttpCookie> cookies,
            boolean appendQueryString, boolean redirect, boolean isAudioOnly)
            throws Exception {
        Preconditions.assertTestFileExists(mInpPrefix + name);

        String stream_url = null;
        if (redirect) {
            stream_url = mServer.getQueryRedirectingAssetUrl(mInpPrefix + name);
        } else {
            stream_url = mServer.getAssetUrl(mInpPrefix + name);
        }
        if (appendQueryString) {
            stream_url += "?foo=bar/baz";
        }
        if (isAudioOnly) {
            playLiveAudioOnlyTest(Uri.parse(stream_url), headers, cookies, HLS_PLAYBACK_TIME_MS);
        } else {
            playLiveVideoTest(Uri.parse(stream_url), headers, cookies, HLS_PLAYBACK_TIME_MS);
        }
    }
}
