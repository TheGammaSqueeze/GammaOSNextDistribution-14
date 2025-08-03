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

package android.device.stressmodes;

import android.content.ContentResolver;
import android.content.Context;
import android.media.ApplicationMediaCapabilities;
import android.net.Uri;
import android.util.Log;
import android.media.MediaFormat;
import android.media.MediaTranscodingManager;
import android.media.MediaTranscodingManager.TranscodingRequest;
import android.media.MediaTranscodingManager.TranscodingSession;
import android.media.MediaTranscodingManager.VideoTranscodingRequest;
import static org.junit.Assert.assertNotNull;

import androidx.test.InstrumentationRegistry;
import androidx.test.internal.runner.listener.InstrumentationRunListener;

import org.junit.runner.Description;

import java.io.File;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class BackgroundTranscodingStressTestMode extends InstrumentationRunListener {
    private static final String TAG = BackgroundTranscodingStressTestMode.class.getSimpleName();
    private Context mContext;
    private MediaTranscodingManager mMediaTranscodingManager = null;

    // Default setting for transcoding to H.264.
    private static final String MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final int BIT_RATE = 20000000; // 20Mbps
    private static final int WIDTH = 1920;
    private static final int HEIGHT = 1080;

    private TranscodingSession mTranscodingSession = null;

    public BackgroundTranscodingStressTestMode() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.WRITE_MEDIA_STORAGE");
        mMediaTranscodingManager = mContext.getSystemService(MediaTranscodingManager.class);
        assertNotNull(mMediaTranscodingManager);
    }

    @Override
    public final void testStarted(Description description) throws Exception {
        Log.i(TAG, "BackgroundTranscodingStressTestMode stress started");
        String path = "/data/local/tmp/testHevc.mp4";
        final File file = new File(path);

        Log.i(TAG, "Transcoding file " + file);

        // Create a file Uri: file:///data/user/0/android.media.cts/cache/HevcTranscode.mp4
        Uri destinationUri =
                Uri.parse(
                        ContentResolver.SCHEME_FILE
                                + "://"
                                + mContext.getCacheDir().getAbsolutePath()
                                + "/HevcTranscode.mp4");

        ApplicationMediaCapabilities clientCaps =
                new ApplicationMediaCapabilities.Builder().build();

        TranscodingRequest.VideoFormatResolver resolver =
                new TranscodingRequest.VideoFormatResolver(
                        clientCaps,
                        MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC, 1920, 1080));
        MediaFormat videoTrackFormat = resolver.resolveVideoFormat();
        assertNotNull(videoTrackFormat);

        int pid = android.os.Process.myPid();
        int uid = android.os.Process.myUid();

        VideoTranscodingRequest.Builder builder =
                new VideoTranscodingRequest.Builder(
                                Uri.fromFile(file), destinationUri, videoTrackFormat)
                        .setClientPid(pid)
                        .setClientUid(uid);
        TranscodingRequest request = builder.build();
        Executor listenerExecutor = Executors.newSingleThreadExecutor();

        Log.d(TAG, "transcoding to format: " + videoTrackFormat);

        mTranscodingSession =
                mMediaTranscodingManager.enqueueRequest(
                        request,
                        listenerExecutor,
                        transcodingSession -> {
                            Log.d(
                                    TAG,
                                    "Transcoding completed with result: "
                                            + transcodingSession.getResult());
                        });
        assertNotNull(mTranscodingSession);
    }

    @Override
    public final void testFinished(Description description) throws Exception {
        Log.i(TAG, "BackgroundTranscodingStressTestMode stress finished");
        mTranscodingSession.cancel();
    }
}
