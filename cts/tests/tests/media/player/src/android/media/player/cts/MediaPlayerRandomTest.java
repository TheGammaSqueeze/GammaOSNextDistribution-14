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
package android.media.player.cts;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.media.cts.MediaHeavyPresubmitTest;
import android.media.cts.MediaTestBase;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;
import android.view.SurfaceHolder;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.NonMainlineTest;
import com.android.compatibility.common.util.Preconditions;
import com.android.compatibility.common.util.WatchDog;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Random;

/**
 * Random input test for {@link MediaPlayer}.
 *
 * <p>Only fails when a crash or a blocking call happens. Does not verify output.
 */
@NonMainlineTest
@MediaHeavyPresubmitTest
@AppModeFull(reason = "TODO: evaluate and port to instant")
@RunWith(AndroidJUnit4.class)
public class MediaPlayerRandomTest extends MediaTestBase {
    private static final int MAX_PARAM = 1000000;
    private static final String TAG = "MediaPlayerRandomTest";

    private static final String mInpPrefix = WorkDir.getMediaDirString();

    private static final int NUMBER_OF_PLAYER_RANDOM_ACTIONS = 100000;

    private MediaPlayer mPlayer;
    private SurfaceHolder mSurfaceHolder;

    // Modified across multiple threads
    private volatile boolean mMediaServerDied;

    @Before
    @Override
    public void setUp() throws Throwable {
        super.setUp();
        getInstrumentation().waitForIdleSync();
        mMediaServerDied = false;
        mSurfaceHolder = getActivity().getSurfaceHolder();
        try {
            // Running this on UI thread make sure that
            // onError callback can be received.
            getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    mPlayer = new MediaPlayer();
                }
            });
        } catch (Throwable e) {
            e.printStackTrace();
            fail();
        }
    }

    @After
    @Override
    public void tearDown() {
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }
        super.tearDown();
    }

    private void loadSource(final String res) throws Exception {
        Preconditions.assertTestFileExists(mInpPrefix + res);
        File inpFile = new File(mInpPrefix + res);
        ParcelFileDescriptor parcelFD =
                ParcelFileDescriptor.open(inpFile, ParcelFileDescriptor.MODE_READ_ONLY);
        AssetFileDescriptor afd = new AssetFileDescriptor(parcelFD, 0, parcelFD.getStatSize());
        try {
            mPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(),
                    afd.getLength());
        } finally {
            afd.close();
        }
    }

    @Test
    public void testPlayerRandomActionAV1() throws Exception {
        testPlayerRandomAction(
                "video_480x360_webm_av1_400kbps_30fps_vorbis_stereo_128kbps_48000hz.webm");
    }

    @Test
    public void testPlayerRandomActionH264() throws Exception {
        testPlayerRandomAction(
                "video_480x360_mp4_h264_500kbps_30fps_aac_stereo_128kbps_44100hz.mp4");
    }

    @Test
    public void testPlayerRandomActionHEVC() throws Exception {
        testPlayerRandomAction(
                "video_480x360_mp4_hevc_650kbps_30fps_aac_stereo_128kbps_48000hz.mp4");
    }

    @Test
    public void testPlayerRandomActionMpeg2() throws Exception {
        testPlayerRandomAction(
                "video_480x360_mp4_mpeg2_1500kbps_30fps_aac_stereo_128kbps_48000hz.mp4");
    }

    private void testPlayerRandomAction(final String res) throws Exception {
        WatchDog watchDog = new WatchDog(5000);
        try {
            mPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    if (mPlayer == mp &&
                            what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
                        Log.e(TAG, "mediaserver process died");
                        mMediaServerDied = true;
                    }
                    return true;
                }
            });
            loadSource(res);
            mPlayer.setDisplay(mSurfaceHolder);
            mPlayer.prepare();
            mPlayer.start();

            long seed = System.currentTimeMillis();
            Log.v(TAG, "seed = " + seed);
            Random r = new Random(seed);

            watchDog.start();
            for (int i = 0; i < NUMBER_OF_PLAYER_RANDOM_ACTIONS; i++) {
                int action = r.nextInt(12);
                int param1 = r.nextInt(MAX_PARAM);
                int param2 = r.nextInt(MAX_PARAM);
                Log.d(TAG, "Action: " + action + " Param1: " + param1 + " Param2: " + param2);
                watchDog.reset();
                assertTrue(!mMediaServerDied);

                try {
                    switch (action) {
                        case 0:
                            mPlayer.getCurrentPosition();
                            break;
                        case 1:
                            mPlayer.getDuration();
                            break;
                        case 2:
                            mPlayer.getVideoHeight();
                            break;
                        case 3:
                            mPlayer.getVideoWidth();
                            break;
                        case 4:
                            mPlayer.isPlaying();
                            break;
                        case 5:
                            mPlayer.pause();
                            break;
                        case 6:
                            // Don't add mPlayer.prepare() call here for two reasons:
                            // 1. calling prepare() is a bad idea since it is a blocking call, and
                            // 2. when prepare() is in progress, mediaserver died message will not
                            // be sent to apps
                            mPlayer.prepareAsync();
                            break;
                        case 7:
                            mPlayer.seekTo(param1);
                            break;
                        case 8:
                            mPlayer.setLooping(param1 % 2 == 0);
                            break;
                        case 9:
                            mPlayer.setVolume((param1 * 2.0f) / MAX_PARAM,
                                    (param2 * 2.0f) / MAX_PARAM);
                            break;
                        case 10:
                            mPlayer.start();
                            break;
                        case 11:
                            Thread.sleep(param1 % 20);
                            break;
                    }
                } catch (Exception e) {
                }
            }
            mPlayer.stop();
        } catch (Exception e) {
            Log.v(TAG, e.toString());
        } finally {
            watchDog.stop();
        }
    }
}
