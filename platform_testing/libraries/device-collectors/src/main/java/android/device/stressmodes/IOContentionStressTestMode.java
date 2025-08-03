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
import android.content.res.AssetFileDescriptor;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaMuxer;
import android.net.Uri;
import android.util.Log;
import android.media.MediaFormat;

import androidx.test.InstrumentationRegistry;
import androidx.test.internal.runner.listener.InstrumentationRunListener;

import org.junit.runner.Description;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This stressor test read file from a mp4 file and write to a new file to simulate the IO
 * contention.
 */
public class IOContentionStressTestMode extends InstrumentationRunListener {
    private static final String TAG = IOContentionStressTestMode.class.getSimpleName();
    private Context mContext;
    private static final int MAX_SAMPLE_SIZE = 256 * 1024;
    private ContentResolver mContentResolver;
    private boolean mStop = false;
    private ExecutorService mExecutorService = null;

    public IOContentionStressTestMode() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.WRITE_MEDIA_STORAGE");
        mContentResolver = mContext.getContentResolver();
        mExecutorService = Executors.newSingleThreadExecutor();
    }

    public final void doCopyFile() throws Exception {
        Log.i(TAG, "doCopyFile");
        String path = "/data/local/tmp/testHevc.mp4";
        final File file = new File(path);

        Log.i(TAG, "Copying file " + file);

        // Create a file Uri: file:///data/user/0/android.media.cts/cache/HevcTranscode.mp4
        Uri destinationUri =
                Uri.parse(
                        ContentResolver.SCHEME_FILE
                                + "://"
                                + mContext.getCacheDir().getAbsolutePath()
                                + "/HevcTranscode.mp4");

        // Set up MediaExtractor to read from the source.
        AssetFileDescriptor srcFd =
                mContentResolver.openAssetFileDescriptor(Uri.fromFile(file), "r");
        AssetFileDescriptor dstFd = null;
        MediaExtractor extractor = null;
        MediaMuxer muxer = null;

        try {
            extractor = new MediaExtractor();
            extractor.setDataSource(
                    srcFd.getFileDescriptor(), srcFd.getStartOffset(), srcFd.getLength());

            int trackCount = extractor.getTrackCount();

            // Set up MediaMuxer for the destination.
            muxer =
                    new MediaMuxer(
                            destinationUri.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            // Set up the tracks.
            HashMap<Integer, Integer> indexMap = new HashMap<Integer, Integer>(trackCount);
            for (int i = 0; i < trackCount; i++) {
                extractor.selectTrack(i);
                MediaFormat format = extractor.getTrackFormat(i);
                int dstIndex = muxer.addTrack(format);
                indexMap.put(i, dstIndex);
            }

            // Copy the samples from MediaExtractor to MediaMuxer.
            boolean sawEOS = false;
            int bufferSize = MAX_SAMPLE_SIZE;
            int frameCount = 0;
            int offset = 100;

            ByteBuffer dstBuf = ByteBuffer.allocate(bufferSize);
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();

            muxer.start();
            while (!sawEOS && !mStop) {
                bufferInfo.offset = offset;
                bufferInfo.size = extractor.readSampleData(dstBuf, offset);

                if (bufferInfo.size < 0) {
                    sawEOS = true;
                    bufferInfo.size = 0;
                } else {
                    bufferInfo.presentationTimeUs = extractor.getSampleTime();
                    bufferInfo.flags = extractor.getSampleFlags();
                    int trackIndex = extractor.getSampleTrackIndex();

                    muxer.writeSampleData(indexMap.get(trackIndex), dstBuf, bufferInfo);
                    extractor.advance();

                    frameCount++;
                }
            }
        } finally {
            if (muxer != null) {
                muxer.stop();
                muxer.release();
            }
            if (extractor != null) {
                extractor.release();
            }
            if (srcFd != null) {
                srcFd.close();
            }
        }
    }

    @Override
    public final void testStarted(Description description) throws Exception {
        mExecutorService = Executors.newSingleThreadExecutor();
        mStop = false;
        Log.i(TAG, "IOContentionStressTestMode stress started");
        mExecutorService.submit(
                new Runnable() {
                    @Override
                    public void run() {
                        while (!mStop) {
                            try {
                                doCopyFile();
                            } catch (Exception ex) {
                                Log.e(TAG, "Copy file get exception: " + ex);
                            }
                        }
                    }
                });
    }

    @Override
    public final void testFinished(Description description) throws Exception {
        Log.i(TAG, "IOContentionStressTestMode stress finished");
        mStop = true;
        if (mExecutorService != null) {
            mExecutorService.shutdown();
        }
    }
}
