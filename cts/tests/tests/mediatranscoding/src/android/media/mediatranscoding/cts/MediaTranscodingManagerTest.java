/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.media.mediatranscoding.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.media.ApplicationMediaCapabilities;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaTranscodingManager;
import android.media.MediaTranscodingManager.TranscodingRequest;
import android.media.MediaTranscodingManager.TranscodingSession;
import android.media.MediaTranscodingManager.VideoTranscodingRequest;
import android.net.Uri;
import android.os.Bundle;
import android.os.FileUtils;
import android.os.ParcelFileDescriptor;
import android.os.SystemProperties;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresDevice;
import android.util.Log;

import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RequiresDevice
@AppModeFull(reason = "Instant apps cannot access the SD card")
@SdkSuppress(minSdkVersion = 31, codeName = "S")
@RunWith(AndroidJUnit4.class)
public class MediaTranscodingManagerTest {
    private static final String TAG = "MediaTranscodingManagerTest";
    /** The time to wait for the transcode operation to complete before failing the test. */
    private static final int TRANSCODE_TIMEOUT_SECONDS = 10;
    /** Copy the transcoded video to /storage/emulated/0/Download/ */
    private static final boolean DEBUG_TRANSCODED_VIDEO = false;
    /** Dump both source yuv and transcode YUV to /storage/emulated/0/Download/ */
    private static final boolean DEBUG_YUV = false;

    private Context mContext;
    private ContentResolver mContentResolver;
    private MediaTranscodingManager mMediaTranscodingManager = null;
    private Uri mSourceHEVCVideoUri = null;
    private Uri mSourceAVCVideoUri = null;
    private Uri mDestinationUri = null;

    // Default setting for transcoding to H.264.
    private static final String MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final int BIT_RATE = 4000000;            // 4Mbps
    private static final int WIDTH = 720;
    private static final int HEIGHT = 480;
    private static final int FRAME_RATE = 30;
    private static final int INT_NOT_SET = Integer.MIN_VALUE;

    // Threshold for the psnr to make sure the transcoded video is valid.
    private static final int PSNR_THRESHOLD = 20;

    // Copy the resource to cache.
    private Uri resourceToUri(int resId, String name) throws IOException {
        Uri cacheUri = Uri.parse(ContentResolver.SCHEME_FILE + "://"
                + mContext.getCacheDir().getAbsolutePath() + "/" + name);

        InputStream is = mContext.getResources().openRawResource(resId);
        OutputStream os = mContext.getContentResolver().openOutputStream(cacheUri);

        FileUtils.copy(is, os);
        return cacheUri;
    }

    private static Uri generateNewUri(Context context, String filename) {
        File outFile = new File(context.getExternalCacheDir(), filename);
        return Uri.fromFile(outFile);
    }

    // Generates an invalid uri which will let the service return transcoding failure.
    private static Uri generateInvalidTranscodingUri(Context context) {
        File outFile = new File(context.getExternalCacheDir(), "InvalidUri.mp4");
        return Uri.fromFile(outFile);
    }

    /**
     * Creates a MediaFormat with the default settings.
     */
    private static MediaFormat createDefaultMediaFormat() {
        return createMediaFormat(MIME_TYPE, WIDTH, HEIGHT, INT_NOT_SET /* frameRate */,
                BIT_RATE /* bitrate */);
    }

    /**
     * Creates a MediaFormat with custom settings.
     */
    private static MediaFormat createMediaFormat(String mime, int width, int height, int frameRate,
            int bitrate) {
        MediaFormat format = new MediaFormat();
        if (mime != null) {
            format.setString(MediaFormat.KEY_MIME, mime);
        }
        if (width != INT_NOT_SET) {
            format.setInteger(MediaFormat.KEY_WIDTH, width);
        }
        if (height != INT_NOT_SET) {
            format.setInteger(MediaFormat.KEY_HEIGHT, height);
        }
        if (frameRate != INT_NOT_SET) {
            format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        }
        if (bitrate != INT_NOT_SET) {
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        }
        return format;
    }

    @Before
    public void setUp() throws Exception {
        Log.d(TAG, "setUp");

        Assume.assumeTrue("Media transcoding disabled",
                SystemProperties.getBoolean("sys.fuse.transcode_enabled", false));

        PackageManager pm =
                InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageManager();
        Assume.assumeFalse("Unsupported device type (TV, Watch, Car)",
                pm.hasSystemFeature(pm.FEATURE_LEANBACK)
                || pm.hasSystemFeature(pm.FEATURE_WATCH)
                || pm.hasSystemFeature(pm.FEATURE_AUTOMOTIVE));

        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mContentResolver = mContext.getContentResolver();

        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity("android.permission.WRITE_MEDIA_STORAGE");
        mMediaTranscodingManager = mContext.getSystemService(MediaTranscodingManager.class);
        assertNotNull(mMediaTranscodingManager);
        androidx.test.InstrumentationRegistry.registerInstance(
                InstrumentationRegistry.getInstrumentation(), new Bundle());

        // Setup default source HEVC 480p file uri.
        mSourceHEVCVideoUri = resourceToUri(R.raw.Video_HEVC_480p_30Frames,
                "Video_HEVC_480p_30Frames.mp4");

        // Setup source AVC file uri.
        mSourceAVCVideoUri = resourceToUri(R.raw.Video_AVC_30Frames,
                "Video_AVC_30Frames.mp4");

        // Setup destination file.
        mDestinationUri = generateNewUri(mContext, "transcoded.mp4");
    }

    @After
    public void tearDown() throws Exception {
        InstrumentationRegistry
                .getInstrumentation().getUiAutomation().dropShellPermissionIdentity();
    }

    /**
     * Verify that setting null destination uri will throw exception.
     */
    @Test
    public void testCreateTranscodingRequestWithNullDestinationUri() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> {
            VideoTranscodingRequest request =
                    new VideoTranscodingRequest.Builder(mSourceHEVCVideoUri, null,
                            createDefaultMediaFormat())
                            .build();
        });
    }

    /**
     * Verify that setting invalid pid will throw exception.
     */
    @Test
    public void testCreateTranscodingWithInvalidClientPid() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> {
            VideoTranscodingRequest request =
                    new VideoTranscodingRequest.Builder(mSourceHEVCVideoUri, mDestinationUri,
                            createDefaultMediaFormat())
                            .setClientPid(-1)
                            .build();
        });
    }

    /**
     * Verify that setting invalid uid will throw exception.
     */
    @Test
    public void testCreateTranscodingWithInvalidClientUid() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> {
            VideoTranscodingRequest request =
                    new VideoTranscodingRequest.Builder(mSourceHEVCVideoUri, mDestinationUri,
                            createDefaultMediaFormat())
                            .setClientUid(-1)
                            .build();
        });
    }

    /**
     * Verify that setting null source uri will throw exception.
     */
    @Test
    public void testCreateTranscodingRequestWithNullSourceUri() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> {
            VideoTranscodingRequest request =
                    new VideoTranscodingRequest.Builder(null, mDestinationUri,
                            createDefaultMediaFormat())
                            .build();
        });
    }

    /**
     * Verify that not setting source uri will throw exception.
     */
    @Test
    public void testCreateTranscodingRequestWithoutSourceUri() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> {
            VideoTranscodingRequest request =
                    new VideoTranscodingRequest.Builder(null, mDestinationUri,
                            createDefaultMediaFormat())
                            .build();
        });
    }

    /**
     * Verify that not setting destination uri will throw exception.
     */
    @Test
    public void testCreateTranscodingRequestWithoutDestinationUri() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> {
            VideoTranscodingRequest request =
                    new VideoTranscodingRequest.Builder(mSourceHEVCVideoUri, null,
                            createDefaultMediaFormat())
                            .build();
        });
    }


    /**
     * Verify that setting video transcoding without setting video format will throw exception.
     */
    @Test
    public void testCreateTranscodingRequestWithoutVideoFormat() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> {
            VideoTranscodingRequest request =
                    new VideoTranscodingRequest.Builder(mSourceHEVCVideoUri, mDestinationUri, null)
                            .build();
        });
    }

    private void testTranscodingWithExpectResult(Uri srcUri, Uri dstUri, int expectedResult)
            throws Exception {
        Semaphore transcodeCompleteSemaphore = new Semaphore(0);

        VideoTranscodingRequest request =
                new VideoTranscodingRequest.Builder(srcUri, dstUri, createDefaultMediaFormat())
                        .build();
        Executor listenerExecutor = Executors.newSingleThreadExecutor();

        TranscodingSession session = mMediaTranscodingManager.enqueueRequest(
                request,
                listenerExecutor,
                transcodingSession -> {
                    Log.d(TAG,
                            "Transcoding completed with result: " + transcodingSession.getResult());
                    transcodeCompleteSemaphore.release();
                    assertEquals(expectedResult, transcodingSession.getResult());
                });
        assertNotNull(session);

        if (session != null) {
            Log.d(TAG, "testMediaTranscodingManager - Waiting for transcode to complete.");
            boolean finishedOnTime = transcodeCompleteSemaphore.tryAcquire(
                    TRANSCODE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertTrue("Transcode failed to complete in time.", finishedOnTime);
        }

        File dstFile = new File(dstUri.getPath());;
        if (expectedResult == TranscodingSession.RESULT_SUCCESS) {
            // Checks the destination file get generated.
            assertTrue("Failed to create destination file", dstFile.exists());
        }

        if (dstFile.exists()) {
            dstFile.delete();
        }
    }

    // Tests transcoding from invalid file uri and expects failure.
    @Test
    public void testTranscodingInvalidSrcUri() throws Exception {
        Uri invalidSrcUri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://"
                + mContext.getPackageName() + "/source.mp4");
        // Create a file Uri: android.resource://android.media.cts/temp.mp4
        Uri destinationUri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://"
                + mContext.getPackageName() + "/temp.mp4");
        Log.d(TAG, "Transcoding " + invalidSrcUri + "to destination: " + destinationUri);

        testTranscodingWithExpectResult(invalidSrcUri, destinationUri,
                TranscodingSession.RESULT_ERROR);
    }

    // Tests transcoding to a uri in res folder and expects failure as test could not write to res
    // folder.
    @Test
    public void testTranscodingToResFolder() throws Exception {
        // Create a file Uri:  android.resource://android.media.cts/temp.mp4
        Uri destinationUri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://"
                + mContext.getPackageName() + "/temp.mp4");
        Log.d(TAG, "Transcoding to destination: " + destinationUri);

        testTranscodingWithExpectResult(mSourceHEVCVideoUri, destinationUri,
                TranscodingSession.RESULT_ERROR);
    }

    // Tests transcoding to a uri in internal cache folder and expects success.
    @Test
    public void testTranscodingToCacheDir() throws Exception {
        // Create a file Uri: file:///data/user/0/android.media.cts/cache/temp.mp4
        Uri destinationUri = Uri.parse(ContentResolver.SCHEME_FILE + "://"
                + mContext.getCacheDir().getAbsolutePath() + "/temp.mp4");
        Log.d(TAG, "Transcoding to cache: " + destinationUri);

        testTranscodingWithExpectResult(mSourceHEVCVideoUri, destinationUri,
                TranscodingSession.RESULT_SUCCESS);
    }

    // Tests transcoding to a uri in internal files directory and expects success.
    @Test
    public void testTranscodingToInternalFilesDir() throws Exception {
        // Create a file Uri: file:///data/user/0/android.media.cts/files/temp.mp4
        Uri destinationUri = Uri.fromFile(new File(mContext.getFilesDir(), "temp.mp4"));
        Log.i(TAG, "Transcoding to files dir: " + destinationUri);

        testTranscodingWithExpectResult(mSourceHEVCVideoUri, destinationUri,
                TranscodingSession.RESULT_SUCCESS);
    }

    @Test
    public void testHevcTranscoding720PVideo30FramesWithoutAudio() throws Exception {
        transcodeFile(resourceToUri(R.raw.Video_HEVC_720p_30Frames,
                "Video_HEVC_720p_30Frames.mp4"), false /* testFileDescriptor */);
    }

    @Test
    public void testAvcTranscoding1080PVideo30FramesWithoutAudio() throws Exception {
        transcodeFile(resourceToUri(R.raw.Video_AVC_30Frames, "Video_AVC_30Frames.mp4"),
                false /* testFileDescriptor */);
    }

    @Test
    public void testHevcTranscoding1080PVideo30FramesWithoutAudio() throws Exception {
        transcodeFile(
                resourceToUri(R.raw.Video_HEVC_30Frames, "Video_HEVC_30Frames.mp4"),
                false /* testFileDescriptor */);
    }

    // Enable this after fixing b/175641397
    @Test
    public void testHevcTranscoding1080PVideo1FrameWithAudio() throws Exception {
        transcodeFile(resourceToUri(R.raw.Video_HEVC_1Frame_Audio,
                "Video_HEVC_1Frame_Audio.mp4"), false /* testFileDescriptor */);
    }

    @Test
    public void testHevcTranscoding1080PVideo37FramesWithAudio() throws Exception {
        transcodeFile(resourceToUri(R.raw.Video_HEVC_37Frames_Audio,
                "Video_HEVC_37Frames_Audio.mp4"), false /* testFileDescriptor */);
    }

    @Test
    public void testHevcTranscoding1080PVideo72FramesWithAudio() throws Exception {
        transcodeFile(resourceToUri(R.raw.Video_HEVC_72Frames_Audio,
                "Video_HEVC_72Frames_Audio.mp4"), false /* testFileDescriptor */);
    }

    // This test will only run when the device support decoding and encoding 4K video.
    @Test
    public void testHevcTranscoding4KVideo64FramesWithAudio() throws Exception {
        transcodeFile(resourceToUri(R.raw.Video_4K_HEVC_64Frames_Audio,
                "Video_4K_HEVC_64Frames_Audio.mp4"), false /* testFileDescriptor */);
    }

    @Test
    public void testHevcTranscodingWithFileDescriptor() throws Exception {
        transcodeFile(resourceToUri(R.raw.Video_HEVC_37Frames_Audio,
                "Video_HEVC_37Frames_Audio.mp4"), true /* testFileDescriptor */);
    }

    private void transcodeFile(Uri fileUri, boolean testFileDescriptor) throws Exception {
        Semaphore transcodeCompleteSemaphore = new Semaphore(0);

        // Create a file Uri: file:///data/user/0/android.media.cts/cache/HevcTranscode.mp4
        Uri destinationUri = Uri.parse(ContentResolver.SCHEME_FILE + "://"
                + mContext.getCacheDir().getAbsolutePath() + "/HevcTranscode.mp4");

        ApplicationMediaCapabilities clientCaps =
                new ApplicationMediaCapabilities.Builder().build();

        MediaFormat srcVideoFormat = getVideoTrackFormat(fileUri);
        assertNotNull(srcVideoFormat);

        int width = srcVideoFormat.getInteger(MediaFormat.KEY_WIDTH);
        int height = srcVideoFormat.getInteger(MediaFormat.KEY_HEIGHT);

        TranscodingRequest.VideoFormatResolver
                resolver = new TranscodingRequest.VideoFormatResolver(clientCaps,
                MediaFormat.createVideoFormat(
                        MediaFormat.MIMETYPE_VIDEO_HEVC, width, height));
        assertTrue(resolver.shouldTranscode());
        MediaFormat videoTrackFormat = resolver.resolveVideoFormat();
        assertNotNull(videoTrackFormat);

        // Return if the source or target video format is not supported
        if (!isFormatSupported(srcVideoFormat, false)
                || !isFormatSupported(videoTrackFormat, true)) {
            return;
        }

        int pid = android.os.Process.myPid();
        int uid = android.os.Process.myUid();

        VideoTranscodingRequest.Builder builder =
                new VideoTranscodingRequest.Builder(fileUri, destinationUri, videoTrackFormat)
                        .setClientPid(pid)
                        .setClientUid(uid);

        AssetFileDescriptor srcFd = null;
        AssetFileDescriptor dstFd = null;
        if (testFileDescriptor) {
            // Open source Uri.
            srcFd = mContentResolver.openAssetFileDescriptor(fileUri,
                    "r");
            builder.setSourceFileDescriptor(srcFd.getParcelFileDescriptor());
            // Open destination Uri
            dstFd = mContentResolver.openAssetFileDescriptor(destinationUri, "rw");
            builder.setDestinationFileDescriptor(dstFd.getParcelFileDescriptor());
        }
        VideoTranscodingRequest request = builder.build();
        Executor listenerExecutor = Executors.newSingleThreadExecutor();
        assertEquals(pid, request.getClientPid());
        assertEquals(uid, request.getClientUid());

        Log.d(TAG, "transcoding to format: " + videoTrackFormat);

        TranscodingSession session = mMediaTranscodingManager.enqueueRequest(
                request,
                listenerExecutor,
                transcodingSession -> {
                    Log.d(TAG,
                            "Transcoding completed with result: " + transcodingSession.getResult());
                    assertEquals(TranscodingSession.RESULT_SUCCESS, transcodingSession.getResult());
                    transcodeCompleteSemaphore.release();
                });
        assertNotNull(session);
        assertTrue(compareFormat(videoTrackFormat, request.getVideoTrackFormat()));
        assertEquals(fileUri, request.getSourceUri());
        assertEquals(destinationUri, request.getDestinationUri());
        if (testFileDescriptor) {
            assertEquals(srcFd.getParcelFileDescriptor(), request.getSourceFileDescriptor());
            assertEquals(dstFd.getParcelFileDescriptor(), request.getDestinationFileDescriptor());
        }

        if (session != null) {
            Log.d(TAG, "testMediaTranscodingManager - Waiting for transcode to cancel.");
            boolean finishedOnTime = transcodeCompleteSemaphore.tryAcquire(
                    TRANSCODE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            assertTrue("Transcode failed to complete in time.", finishedOnTime);
        }

        if (DEBUG_TRANSCODED_VIDEO) {
            try {
                // Add the system time to avoid duplicate that leads to write failure.
                String filename =
                        "transcoded_" + System.nanoTime() + "_" + fileUri.getLastPathSegment();
                String path = "/storage/emulated/0/Download/" + filename;
                final File file = new File(path);
                ParcelFileDescriptor pfd = mContext.getContentResolver().openFileDescriptor(
                        destinationUri, "r");
                FileInputStream fis = new FileInputStream(pfd.getFileDescriptor());
                FileOutputStream fos = new FileOutputStream(file);
                FileUtils.copy(fis, fos);
            } catch (IOException e) {
                Log.e(TAG, "Failed to copy file", e);
            }
        }

        assertEquals(TranscodingSession.STATUS_FINISHED, session.getStatus());
        assertEquals(TranscodingSession.RESULT_SUCCESS, session.getResult());
        assertEquals(TranscodingSession.ERROR_NONE, session.getErrorCode());

        // TODO(hkuang): Validate the transcoded video's width and height, framerate.

        // Validates the transcoded video's psnr.
        // Enable this after fixing b/175644377
        MediaTranscodingTestUtil.VideoTranscodingStatistics stats =
                MediaTranscodingTestUtil.computeStats(mContext, fileUri, destinationUri, DEBUG_YUV);
        assertTrue("PSNR: " + stats.mAveragePSNR + " is too low",
                stats.mAveragePSNR >= PSNR_THRESHOLD);

        if (srcFd != null) {
            srcFd.close();
        }
        if (dstFd != null) {
            dstFd.close();
        }
    }

    private void testVideoFormatResolverShouldTranscode(String mime, int width, int height,
            int frameRate) {
        ApplicationMediaCapabilities clientCaps =
                new ApplicationMediaCapabilities.Builder().build();

        MediaFormat mediaFormat = createMediaFormat(mime, width, height, frameRate, BIT_RATE);

        TranscodingRequest.VideoFormatResolver
                resolver = new TranscodingRequest.VideoFormatResolver(clientCaps,
                mediaFormat);
        assertTrue(resolver.shouldTranscode());
        MediaFormat videoTrackFormat = resolver.resolveVideoFormat();
        assertNotNull(videoTrackFormat);
    }

    @Test
    public void testVideoFormatResolverValidArgs() {
        testVideoFormatResolverShouldTranscode(MediaFormat.MIMETYPE_VIDEO_HEVC, WIDTH, HEIGHT,
                FRAME_RATE);
    }

    @Test
    public void testVideoFormatResolverAv1Mime() {
        ApplicationMediaCapabilities clientCaps =
                new ApplicationMediaCapabilities.Builder().build();

        MediaFormat mediaFormat = createMediaFormat(MediaFormat.MIMETYPE_VIDEO_AV1, WIDTH, HEIGHT,
                FRAME_RATE, BIT_RATE);

        TranscodingRequest.VideoFormatResolver
                resolver = new TranscodingRequest.VideoFormatResolver(clientCaps,
                mediaFormat);
        assertFalse(resolver.shouldTranscode());
        MediaFormat videoTrackFormat = resolver.resolveVideoFormat();
        assertNull(videoTrackFormat);
    }

    private void testVideoFormatResolverInvalidArgs(String mime, int width, int height,
            int frameRate) {
        ApplicationMediaCapabilities clientCaps =
                new ApplicationMediaCapabilities.Builder().build();

        MediaFormat mediaFormat = createMediaFormat(mime, width, height, frameRate, BIT_RATE);

        TranscodingRequest.VideoFormatResolver
                resolver = new TranscodingRequest.VideoFormatResolver(clientCaps,
                mediaFormat);

        assertThrows(IllegalArgumentException.class, () -> {
            MediaFormat videoTrackFormat = resolver.resolveVideoFormat();
        });
    }

    @Test
    public void testVideoFormatResolverZeroWidth() {
        testVideoFormatResolverInvalidArgs(MediaFormat.MIMETYPE_VIDEO_HEVC, 0 /* width */,
                HEIGHT, FRAME_RATE);
    }

    @Test
    public void testVideoFormatResolverZeroHeight() {
        testVideoFormatResolverInvalidArgs(MediaFormat.MIMETYPE_VIDEO_HEVC, WIDTH,
                0 /* height */, FRAME_RATE);
    }

    @Test
    public void testVideoFormatResolverZeroFrameRate() {
        testVideoFormatResolverInvalidArgs(MediaFormat.MIMETYPE_VIDEO_HEVC, WIDTH,
                HEIGHT, 0 /* frameRate */);
    }

    @Test
    public void testVideoFormatResolverNegativeWidth() {
        testVideoFormatResolverInvalidArgs(MediaFormat.MIMETYPE_VIDEO_HEVC, -WIDTH,
                HEIGHT, FRAME_RATE);
    }

    @Test
    public void testVideoFormatResolverNegativeHeight() {
        testVideoFormatResolverInvalidArgs(MediaFormat.MIMETYPE_VIDEO_HEVC, WIDTH,
                -HEIGHT, FRAME_RATE);
    }

    @Test
    public void testVideoFormatResolverNegativeFrameRate() {
        testVideoFormatResolverInvalidArgs(MediaFormat.MIMETYPE_VIDEO_HEVC, WIDTH,
                HEIGHT, -FRAME_RATE);
    }

    @Test
    public void testVideoFormatResolverMissingWidth() {
        testVideoFormatResolverInvalidArgs(MediaFormat.MIMETYPE_VIDEO_HEVC, INT_NOT_SET /* width*/,
                HEIGHT /* height */, FRAME_RATE);
    }

    @Test
    public void testVideoFormatResolverMissingHeight() {
        testVideoFormatResolverInvalidArgs(MediaFormat.MIMETYPE_VIDEO_HEVC, WIDTH,
                INT_NOT_SET /* height */, FRAME_RATE);
    }

    @Test
    public void testVideoFormatResolverMissingFrameRate() {
        testVideoFormatResolverShouldTranscode(MediaFormat.MIMETYPE_VIDEO_HEVC, WIDTH, HEIGHT,
                INT_NOT_SET /* frameRate */);
    }

    private boolean compareFormat(MediaFormat fmt1, MediaFormat fmt2) {
        if (fmt1 == fmt2) return true;
        if (fmt1 == null || fmt2 == null) return false;

        return (fmt1.getString(MediaFormat.KEY_MIME) == fmt2.getString(MediaFormat.KEY_MIME) &&
                fmt1.getInteger(MediaFormat.KEY_WIDTH) == fmt2.getInteger(MediaFormat.KEY_WIDTH) &&
                fmt1.getInteger(MediaFormat.KEY_HEIGHT) == fmt2.getInteger(MediaFormat.KEY_HEIGHT)
                && fmt1.getInteger(MediaFormat.KEY_BIT_RATE) == fmt2.getInteger(
                MediaFormat.KEY_BIT_RATE));
    }

    @Test
    public void testCancelTranscoding() throws Exception {
        Log.d(TAG, "Starting: testCancelTranscoding");
        Semaphore transcodeCompleteSemaphore = new Semaphore(0);
        final CountDownLatch statusLatch = new CountDownLatch(1);

        Uri destinationUri = Uri.parse(ContentResolver.SCHEME_FILE + "://"
                + mContext.getCacheDir().getAbsolutePath() + "/HevcTranscode.mp4");

        VideoTranscodingRequest request =
                new VideoTranscodingRequest.Builder(mSourceHEVCVideoUri, destinationUri,
                        createDefaultMediaFormat())
                        .build();
        Executor listenerExecutor = Executors.newSingleThreadExecutor();

        TranscodingSession session = mMediaTranscodingManager.enqueueRequest(
                request,
                listenerExecutor,
                transcodingSession -> {
                    Log.d(TAG,
                            "Transcoding completed with result: " + transcodingSession.getResult());
                    assertEquals(TranscodingSession.RESULT_CANCELED,
                            transcodingSession.getResult());
                    transcodeCompleteSemaphore.release();
                });
        assertNotNull(session);

        assertTrue(session.getSessionId() != -1);

        // Wait for progress update before cancel the transcoding.
        session.setOnProgressUpdateListener(listenerExecutor,
                new TranscodingSession.OnProgressUpdateListener() {
                    @Override
                    public void onProgressUpdate(TranscodingSession session, int newProgress) {
                        if (newProgress > 0) {
                            statusLatch.countDown();
                        }
                        assertEquals(newProgress, session.getProgress());
                    }
                });

        statusLatch.await(2, TimeUnit.MILLISECONDS);
        session.cancel();

        Log.d(TAG, "testMediaTranscodingManager - Waiting for transcode to cancel.");
        boolean finishedOnTime = transcodeCompleteSemaphore.tryAcquire(
                30, TimeUnit.MILLISECONDS);

        assertEquals(TranscodingSession.STATUS_FINISHED, session.getStatus());
        assertEquals(TranscodingSession.RESULT_CANCELED, session.getResult());
        assertEquals(TranscodingSession.ERROR_NONE, session.getErrorCode());
        assertTrue("Fails to cancel transcoding", finishedOnTime);
    }

    @Test
    public void testTranscodingProgressUpdate() throws Exception {
        Log.d(TAG, "Starting: testTranscodingProgressUpdate");

        Semaphore transcodeCompleteSemaphore = new Semaphore(0);

        // Create a file Uri: file:///data/user/0/android.media.mediatranscoding.cts/cache/HevcTranscode.mp4
        Uri destinationUri = Uri.parse(ContentResolver.SCHEME_FILE + "://"
                + mContext.getCacheDir().getAbsolutePath() + "/HevcTranscode.mp4");

        VideoTranscodingRequest request =
                new VideoTranscodingRequest.Builder(mSourceHEVCVideoUri, destinationUri,
                        createDefaultMediaFormat())
                        .build();
        Executor listenerExecutor = Executors.newSingleThreadExecutor();

        TranscodingSession session = mMediaTranscodingManager.enqueueRequest(request,
                listenerExecutor,
                TranscodingSession -> {
                    Log.d(TAG,
                            "Transcoding completed with result: " + TranscodingSession.getResult());
                    assertEquals(TranscodingSession.RESULT_SUCCESS, TranscodingSession.getResult());
                    transcodeCompleteSemaphore.release();
                });
        assertNotNull(session);

        AtomicInteger progressUpdateCount = new AtomicInteger(0);

        // Set progress update executor and use the same executor as result listener.
        session.setOnProgressUpdateListener(listenerExecutor,
                new TranscodingSession.OnProgressUpdateListener() {
                    int mPreviousProgress = 0;

                    @Override
                    public void onProgressUpdate(TranscodingSession session, int newProgress) {
                        assertTrue("Invalid proress update", newProgress > mPreviousProgress);
                        assertTrue("Invalid proress update", newProgress <= 100);
                        mPreviousProgress = newProgress;
                        progressUpdateCount.getAndIncrement();
                        Log.i(TAG, "Get progress update " + newProgress);
                    }
                });

        boolean finishedOnTime = transcodeCompleteSemaphore.tryAcquire(
                TRANSCODE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertTrue("Transcode failed to complete in time.", finishedOnTime);
        assertTrue("Failed to receive at least 10 progress updates",
                progressUpdateCount.get() > 10);
    }

    @Test
    public void testClearOnProgressUpdateListener() throws Exception {
        Log.d(TAG, "Starting: testClearOnProgressUpdateListener");

        Semaphore transcodeCompleteSemaphore = new Semaphore(0);

        // Create a file Uri: file:///data/user/0/android.media.mediatranscoding.cts/cache/HevcTranscode.mp4
        Uri destinationUri = Uri.parse(ContentResolver.SCHEME_FILE + "://"
                + mContext.getCacheDir().getAbsolutePath() + "/HevcTranscode.mp4");

        VideoTranscodingRequest request =
                new VideoTranscodingRequest.Builder(mSourceHEVCVideoUri, destinationUri,
                        createDefaultMediaFormat())
                        .build();
        Executor listenerExecutor = Executors.newSingleThreadExecutor();

        TranscodingSession session = mMediaTranscodingManager.enqueueRequest(request,
                listenerExecutor,
                TranscodingSession -> {
                    Log.d(TAG,
                            "Transcoding completed with result: " + TranscodingSession.getResult());
                    assertEquals(TranscodingSession.RESULT_SUCCESS, TranscodingSession.getResult());
                    transcodeCompleteSemaphore.release();
                });
        assertNotNull(session);

        AtomicInteger progressUpdateCount = new AtomicInteger(0);

        // Set progress update executor and use the same executor as result listener.
        session.setOnProgressUpdateListener(listenerExecutor,
                new TranscodingSession.OnProgressUpdateListener() {
                    int mPreviousProgress = 0;

                    @Override
                    public void onProgressUpdate(TranscodingSession session, int newProgress) {
                        if (mPreviousProgress == 0) {
                            // Clear listener the first time this is called.
                            session.clearOnProgressUpdateListener();
                            // Reset the progress update count in case calls are pending now.
                            listenerExecutor.execute(() -> progressUpdateCount.set(1));
                        }
                        mPreviousProgress = newProgress;
                        progressUpdateCount.getAndIncrement();
                        Log.i(TAG, "Get progress update " + newProgress);
                    }
                });

        boolean finishedOnTime = transcodeCompleteSemaphore.tryAcquire(
                TRANSCODE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertTrue("Transcode failed to complete in time.", finishedOnTime);
        assertTrue("Expected exactly one progress update", progressUpdateCount.get() == 1);
    }

    @Test
    public void testAddingClientUids() throws Exception {
        Log.d(TAG, "Starting: testTranscodingProgressUpdate");

        Semaphore transcodeCompleteSemaphore = new Semaphore(0);

        // Create a file Uri: file:///data/user/0/android.media.mediatranscoding.cts/cache/HevcTranscode.mp4
        Uri destinationUri = Uri.parse(ContentResolver.SCHEME_FILE + "://"
                + mContext.getCacheDir().getAbsolutePath() + "/HevcTranscode.mp4");

        VideoTranscodingRequest request =
                new VideoTranscodingRequest.Builder(mSourceHEVCVideoUri, destinationUri,
                        createDefaultMediaFormat())
                        .build();
        Executor listenerExecutor = Executors.newSingleThreadExecutor();

        TranscodingSession session = mMediaTranscodingManager.enqueueRequest(request,
                listenerExecutor,
                TranscodingSession -> {
                    Log.d(TAG,
                            "Transcoding completed with result: " + TranscodingSession.getResult());
                    assertEquals(TranscodingSession.RESULT_SUCCESS, TranscodingSession.getResult());
                    transcodeCompleteSemaphore.release();
                });
        assertNotNull(session);

        session.addClientUid(1898 /* test_uid */);
        session.addClientUid(1899 /* test_uid */);
        session.addClientUid(1900 /* test_uid */);

        List<Integer> uids = session.getClientUids();
        assertTrue(uids.size() == 4);  // At least 4 uid included the original request uid.
        assertTrue(uids.contains(1898));
        assertTrue(uids.contains(1899));
        assertTrue(uids.contains(1900));

        AtomicInteger progressUpdateCount = new AtomicInteger(0);

        // Set progress update executor and use the same executor as result listener.
        session.setOnProgressUpdateListener(listenerExecutor,
                new TranscodingSession.OnProgressUpdateListener() {
                    int mPreviousProgress = 0;

                    @Override
                    public void onProgressUpdate(TranscodingSession session, int newProgress) {
                        assertTrue("Invalid proress update", newProgress > mPreviousProgress);
                        assertTrue("Invalid proress update", newProgress <= 100);
                        mPreviousProgress = newProgress;
                        progressUpdateCount.getAndIncrement();
                        Log.i(TAG, "Get progress update " + newProgress);
                    }
                });

        boolean finishedOnTime = transcodeCompleteSemaphore.tryAcquire(
                TRANSCODE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertTrue("Transcode failed to complete in time.", finishedOnTime);
        assertTrue("Failed to receive at least 10 progress updates",
                progressUpdateCount.get() > 10);
    }

    private MediaFormat getVideoTrackFormat(Uri fileUri) throws IOException {
        MediaFormat videoFormat = null;
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(fileUri.toString());
        // Find video track format
        for (int trackID = 0; trackID < extractor.getTrackCount(); trackID++) {
            MediaFormat format = extractor.getTrackFormat(trackID);
            if (format.getString(MediaFormat.KEY_MIME).startsWith("video/")) {
                videoFormat = format;
                break;
            }
        }
        extractor.release();
        return videoFormat;
    }

    private boolean isFormatSupported(MediaFormat format, boolean isEncoder) {
        String mime = format.getString(MediaFormat.KEY_MIME);
        MediaCodec codec = null;
        try {
            // The underlying transcoder library uses AMediaCodec_createEncoderByType
            // to create encoder. So we cannot perform an exhaustive search of
            // all codecs that support the format. This is because the codec that
            // advertises support for the format during search may not be the one
            // instantiated by the transcoder library. So, we have to check whether
            // the codec returned by createEncoderByType supports the format.
            // The same point holds for decoder too.
            if (isEncoder) {
                codec = MediaCodec.createEncoderByType(mime);
            } else {
                codec = MediaCodec.createDecoderByType(mime);
            }
            MediaCodecInfo info = codec.getCodecInfo();
            MediaCodecInfo.CodecCapabilities caps = info.getCapabilitiesForType(mime);
            if (caps != null && caps.isFormatSupported(format) && info.isHardwareAccelerated()) {
                return true;
            }
        } catch (IOException e) {
            Log.d(TAG, "Exception: " + e);
        } finally {
            if (codec != null) {
                codec.release();
            }
        }
        return false;
    }
}
