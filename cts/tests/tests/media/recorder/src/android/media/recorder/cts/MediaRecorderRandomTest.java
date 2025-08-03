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
package android.media.recorder.cts;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.media.MediaRecorder;
import android.media.cts.MediaHeavyPresubmitTest;
import android.media.cts.MediaTestBase;
import android.os.Environment;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;
import android.view.SurfaceHolder;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.NonMainlineTest;
import com.android.compatibility.common.util.WatchDog;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Random;

/**
 * Random input test for {@link MediaRecorder}.
 *
 * <p>Only fails when a crash or a blocking call happens. Does not verify output.
 */
@NonMainlineTest
@MediaHeavyPresubmitTest
@AppModeFull(reason = "TODO: evaluate and port to instant")
@RunWith(AndroidJUnit4.class)
public class MediaRecorderRandomTest extends MediaTestBase {
    private static final String TAG = "MediaRecorderRandomTest";
    private static final int MAX_PARAM = 1000000;
    private static final String OUTPUT_FILE =
            Environment.getExternalStorageDirectory().toString() + "/record.3gp";

    private static final int NUMBER_OF_RECORDER_RANDOM_ACTIONS = 100000;

    private MediaRecorder mRecorder;
    private SurfaceHolder mSurfaceHolder;

    // Modified across multiple threads
    private volatile boolean mMediaServerDied;

    @Before
    @Override
    public void setUp() throws Throwable {
        super.setUp();
        mMediaServerDied = false;
        mSurfaceHolder = getActivity().getSurfaceHolder();
        try {
            // Running this on UI thread make sure that
            // onError callback can be received.
            getActivity().runOnUiThread(() -> mRecorder = new MediaRecorder());
        } catch (Throwable e) {
            e.printStackTrace();
            fail();
        }
    }

    @After
    @Override
    public void tearDown() {
        if (mRecorder != null) {
            mRecorder.release();
            mRecorder = null;
        }
        super.tearDown();
    }

    @Test
    public void testRecorderRandomAction() throws Exception {
        WatchDog watchDog = new WatchDog(5000);
        try {
            long seed = System.currentTimeMillis();
            Log.v(TAG, "seed = " + seed);
            Random r = new Random(seed);

            mMediaServerDied = false;
            mRecorder.setOnErrorListener(new MediaRecorder.OnErrorListener() {
                @Override
                public void onError(MediaRecorder recorder, int what, int extra) {
                    if (mRecorder == recorder &&
                            what == MediaRecorder.MEDIA_ERROR_SERVER_DIED) {
                        Log.e(TAG, "mediaserver process died");
                        mMediaServerDied = true;
                    }
                }
            });

            final int[] width = {176, 352, 320, 640, 1280, 1920};
            final int[] height = {144, 288, 240, 480, 720, 1080};
            final int[] audioSource = {
                    MediaRecorder.AudioSource.DEFAULT,
                    MediaRecorder.AudioSource.MIC,
                    MediaRecorder.AudioSource.CAMCORDER,
            };

            watchDog.start();
            for (int i = 0; i < NUMBER_OF_RECORDER_RANDOM_ACTIONS; i++) {
                int action = r.nextInt(14);
                int param = r.nextInt(MAX_PARAM);

                Log.d(TAG, "Action: " + action + " Param: " + param);
                watchDog.reset();
                assertTrue(!mMediaServerDied);

                try {
                    switch (action) {
                        case 0: {
                            // We restrict the audio sources because setting some sources
                            // may cause 2+ second delays because the input device may
                            // retry - loop (e.g. VOICE_UPLINK for voice call to be initiated).
                            final int index = param % audioSource.length;
                            mRecorder.setAudioSource(audioSource[index]);
                            break;
                        }
                        case 1:
                            // Limiting the random test to test default and camera source
                            // and not include video surface as required setInputSurface isn't
                            // done in this test.
                            mRecorder.setVideoSource(param % 2);
                            break;
                        case 2:
                            mRecorder.setOutputFormat(param % 5);
                            break;
                        case 3:
                            mRecorder.setAudioEncoder(param % 3);
                            break;
                        case 4:
                            mRecorder.setVideoEncoder(param % 5);
                            break;
                        case 5:
                            mRecorder.setPreviewDisplay(mSurfaceHolder.getSurface());
                            break;
                        case 6:
                            int index = param % width.length;
                            mRecorder.setVideoSize(width[index], height[index]);
                            break;
                        case 7:
                            mRecorder.setVideoFrameRate((param % 40) - 1);
                            break;
                        case 8:
                            mRecorder.setOutputFile(OUTPUT_FILE);
                            break;
                        case 9:
                            mRecorder.prepare();
                            break;
                        case 10:
                            mRecorder.start();
                            break;
                        case 11:
                            Thread.sleep(param % 20);
                            break;
                        case 12:
                            mRecorder.stop();
                            break;
                        case 13:
                            mRecorder.reset();
                            break;
                        default:
                            break;
                    }
                } catch (Exception e) {
                }
            }
        } catch (Exception e) {
            Log.v(TAG, e.toString());
        } finally {
            watchDog.stop();
        }
    }
}
