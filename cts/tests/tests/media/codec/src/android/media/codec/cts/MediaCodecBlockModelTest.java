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

package android.media.codec.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.res.AssetFileDescriptor;
import android.hardware.HardwareBuffer;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodec.CodecException;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.cts.MediaCodecAsyncHelper;
import android.media.cts.MediaCodecBlockModelHelper;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresDevice;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.MediaUtils;
import com.android.compatibility.common.util.NonMainlineTest;
import com.android.compatibility.common.util.Preconditions;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * MediaCodec tests with CONFIGURE_FLAG_USE_BLOCK_MODEL.
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.R)
@NonMainlineTest
@AppModeFull(reason = "Instant apps cannot access the SD card")
@RunWith(AndroidJUnit4.class)
public class MediaCodecBlockModelTest {
    private static final String TAG = "MediaCodecBlockModelTest";
    private static final boolean VERBOSE = false;           // lots of logging

    // Input buffers from this input video are queued up to and including the video frame with
    // timestamp LAST_BUFFER_TIMESTAMP_US.
    private static final String INPUT_RESOURCE =
            "video_480x360_mp4_h264_1350kbps_30fps_aac_stereo_192kbps_44100hz.mp4";
    private static final long LAST_BUFFER_TIMESTAMP_US = 166666;
    private boolean mIsAtLeastR = ApiLevelUtil.isAtLeast(Build.VERSION_CODES.R);
    static final String mInpPrefix = WorkDir.getMediaDirString();

    protected static AssetFileDescriptor getAssetFileDescriptorFor(final String res)
            throws FileNotFoundException {
        File inpFile = new File(mInpPrefix + res);
        Preconditions.assertTestFileExists(mInpPrefix + res);
        ParcelFileDescriptor parcelFD =
                ParcelFileDescriptor.open(inpFile, ParcelFileDescriptor.MODE_READ_ONLY);
        return new AssetFileDescriptor(parcelFD, 0, parcelFD.getStatSize());
    }

    /**
     * Tests whether decoding a short group-of-pictures succeeds. The test queues a few video frames
     * then signals end-of-stream. The test fails if the decoder doesn't output the queued frames.
     */
    @Presubmit
    @SmallTest
    @RequiresDevice
    @ApiTest(apis = "MediaCodec#CONFIGURE_FLAG_USE_BLOCK_MODEL")
    @Test
    public void testDecodeShortVideo() throws InterruptedException {
        if (!MediaUtils.check(mIsAtLeastR, "test needs Android 11")) return;
        MediaCodecBlockModelHelper.runThread(() -> runDecodeShortVideo(
            INPUT_RESOURCE,
            LAST_BUFFER_TIMESTAMP_US,
            true /* obtainBlockForEachBuffer */));
        MediaCodecBlockModelHelper.runThread(() -> runDecodeShortVideo(
            INPUT_RESOURCE,
            LAST_BUFFER_TIMESTAMP_US,
            false /* obtainBlockForEachBuffer */));
    }

    /**
     * Tests whether decoding a short audio succeeds. The test queues a few audio frames
     * then signals end-of-stream. The test fails if the decoder doesn't output the queued frames.
     */
    @Presubmit
    @SmallTest
    @RequiresDevice
    @ApiTest(apis = "MediaCodec#CONFIGURE_FLAG_USE_BLOCK_MODEL")
    @Test
    public void testDecodeShortAudio() throws InterruptedException {
        if (!MediaUtils.check(mIsAtLeastR, "test needs Android 11")) return;
        MediaCodecBlockModelHelper.runThread(() -> runDecodeShortAudio(
                INPUT_RESOURCE,
                LAST_BUFFER_TIMESTAMP_US,
                true /* obtainBlockForEachBuffer */));
        MediaCodecBlockModelHelper.runThread(() -> runDecodeShortAudio(
                INPUT_RESOURCE,
                LAST_BUFFER_TIMESTAMP_US,
                false /* obtainBlockForEachBuffer */));
    }

    /**
     * Tests whether encoding a short audio succeeds. The test queues a few audio frames
     * then signals end-of-stream. The test fails if the encoder doesn't output the queued frames.
     */
    @Presubmit
    @SmallTest
    @RequiresDevice
    @ApiTest(apis = "MediaCodec#CONFIGURE_FLAG_USE_BLOCK_MODEL")
    @Test
    public void testEncodeShortAudio() throws InterruptedException {
        if (!MediaUtils.check(mIsAtLeastR, "test needs Android 11")) return;
        MediaCodecBlockModelHelper.runThread(() -> runEncodeShortAudio());
    }

    /**
     * Tests whether encoding a short video succeeds. The test queues a few video frames
     * then signals end-of-stream. The test fails if the encoder doesn't output the queued frames.
     */
    @Presubmit
    @SmallTest
    @RequiresDevice
    @ApiTest(apis = "MediaCodec#CONFIGURE_FLAG_USE_BLOCK_MODEL")
    @Test
    public void testEncodeShortVideo() throws InterruptedException {
        if (!MediaUtils.check(mIsAtLeastR, "test needs Android 11")) return;
        MediaCodecBlockModelHelper.runThread(() -> runEncodeShortVideo());
    }

    private MediaCodecBlockModelHelper.Result runDecodeShortAudio(
            String inputResource,
            long lastBufferTimestampUs,
            boolean obtainBlockForEachBuffer) {
        MediaExtractor mediaExtractor = null;
        MediaCodec mediaCodec = null;
        try {
            mediaExtractor = getMediaExtractorForMimeType(inputResource, "audio/");
            MediaFormat mediaFormat =
                    mediaExtractor.getTrackFormat(mediaExtractor.getSampleTrackIndex());
            // TODO: b/147748978
            String[] codecs = MediaUtils.getDecoderNames(true /* isGoog */, mediaFormat);
            if (codecs.length == 0) {
                Log.i(TAG, "No decoder found for format= " + mediaFormat);
                return MediaCodecBlockModelHelper.Result.SKIP;
            }
            mediaCodec = MediaCodec.createByCodecName(codecs[0]);

            List<Long> timestampList = Collections.synchronizedList(new ArrayList<>());
            MediaCodecBlockModelHelper.Result result =
                MediaCodecBlockModelHelper.runComponentWithLinearInput(
                    mediaCodec,
                    null,  // crypto
                    mediaFormat,
                    null,  // surface
                    false,  // encoder
                    new MediaCodecBlockModelHelper.ExtractorInputSlotListener
                            .Builder()
                            .setExtractor(mediaExtractor)
                            .setLastBufferTimestampUs(lastBufferTimestampUs)
                            .setObtainBlockForEachBuffer(obtainBlockForEachBuffer)
                            .setTimestampQueue(timestampList)
                            .build(),
                    new MediaCodecBlockModelHelper.DummyOutputSlotListener(
                            false /* graphic */, timestampList));
            if (result == MediaCodecBlockModelHelper.Result.SUCCESS) {
                assertTrue("Timestamp should match between input / output: " + timestampList,
                        timestampList.isEmpty());
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException("error reading input resource", e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (mediaCodec != null) {
                mediaCodec.stop();
                mediaCodec.release();
            }
            if (mediaExtractor != null) {
                mediaExtractor.release();
            }
        }
    }

    private MediaCodecBlockModelHelper.Result runEncodeShortAudio() {
        MediaExtractor mediaExtractor = null;
        MediaCodec mediaCodec = null;
        try {
            mediaExtractor = getMediaExtractorForMimeType(
                    "okgoogle123_good.wav", MediaFormat.MIMETYPE_AUDIO_RAW);
            MediaFormat mediaFormat = new MediaFormat(
                    mediaExtractor.getTrackFormat(mediaExtractor.getSampleTrackIndex()));
            mediaFormat.setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_AUDIO_AAC);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
            // TODO: b/147748978
            String[] codecs = MediaUtils.getEncoderNames(true /* isGoog */, mediaFormat);
            if (codecs.length == 0) {
                Log.i(TAG, "No encoder found for format= " + mediaFormat);
                return MediaCodecBlockModelHelper.Result.SKIP;
            }
            mediaCodec = MediaCodec.createByCodecName(codecs[0]);

            List<Long> timestampList = Collections.synchronizedList(new ArrayList<>());
            MediaCodecBlockModelHelper.Result result =
                MediaCodecBlockModelHelper.runComponentWithLinearInput(
                    mediaCodec,
                    null,  // crypto
                    mediaFormat,
                    null,  // surface
                    true,  // encoder
                    new MediaCodecBlockModelHelper.ExtractorInputSlotListener
                            .Builder()
                            .setExtractor(mediaExtractor)
                            .setLastBufferTimestampUs(LAST_BUFFER_TIMESTAMP_US)
                            .setTimestampQueue(timestampList)
                            .build(),
                    new MediaCodecBlockModelHelper.DummyOutputSlotListener(
                            false /* graphic */, timestampList));
            if (result == MediaCodecBlockModelHelper.Result.SUCCESS) {
                assertTrue("Timestamp should match between input / output: " + timestampList,
                        timestampList.isEmpty());
            }
            return result;
        } catch (IOException e) {
            throw new RuntimeException("error reading input resource", e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (mediaCodec != null) {
                mediaCodec.stop();
                mediaCodec.release();
            }
            if (mediaExtractor != null) {
                mediaExtractor.release();
            }
        }
    }

    private MediaCodecBlockModelHelper.Result runEncodeShortVideo() {
        final int kWidth = 176;
        final int kHeight = 144;
        final int kFrameRate = 15;
        MediaCodec mediaCodec = null;
        ArrayList<HardwareBuffer> hardwareBuffers = new ArrayList<>();
        try {
            MediaFormat mediaFormat = MediaFormat.createVideoFormat(
                    MediaFormat.MIMETYPE_VIDEO_AVC, kWidth, kHeight);
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, kFrameRate);
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, 1000000);
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
            mediaFormat.setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            // TODO: b/147748978
            String[] codecs = MediaUtils.getEncoderNames(true /* isGoog */, mediaFormat);
            if (codecs.length == 0) {
                Log.i(TAG, "No encoder found for format= " + mediaFormat);
                return MediaCodecBlockModelHelper.Result.SKIP;
            }
            mediaCodec = MediaCodec.createByCodecName(codecs[0]);

            long usage = HardwareBuffer.USAGE_CPU_READ_OFTEN;
            usage |= HardwareBuffer.USAGE_CPU_WRITE_OFTEN;
            if (mediaCodec.getCodecInfo().isHardwareAccelerated()) {
                usage |= HardwareBuffer.USAGE_VIDEO_ENCODE;
            }
            if (!HardwareBuffer.isSupported(
                        kWidth, kHeight, HardwareBuffer.YCBCR_420_888, 1 /* layer */, usage)) {
                Log.i(TAG, "HardwareBuffer doesn't support " + kWidth + "x" + kHeight
                        + "; YCBCR_420_888; usage(" + Long.toHexString(usage) + ")");
                return MediaCodecBlockModelHelper.Result.SKIP;
            }

            List<Long> timestampList = Collections.synchronizedList(new ArrayList<>());

            final LinkedBlockingQueue<MediaCodecAsyncHelper.SlotEvent> queue =
                new LinkedBlockingQueue<>();
            mediaCodec.setCallback(new MediaCodec.Callback() {
                @Override
                public void onInputBufferAvailable(MediaCodec codec, int index) {
                    queue.offer(new MediaCodecAsyncHelper.SlotEvent(true, index));
                }

                @Override
                public void onOutputBufferAvailable(
                        MediaCodec codec, int index, MediaCodec.BufferInfo info) {
                    queue.offer(new MediaCodecAsyncHelper.SlotEvent(false, index));
                }

                @Override
                public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
                }

                @Override
                public void onError(MediaCodec codec, CodecException e) {
                }
            });

            int flags = MediaCodec.CONFIGURE_FLAG_USE_BLOCK_MODEL;
            flags |= MediaCodec.CONFIGURE_FLAG_ENCODE;

            mediaCodec.configure(mediaFormat, null, null, flags);
            mediaCodec.start();
            boolean eos = false;
            boolean signaledEos = false;
            int frameIndex = 0;
            while (!eos && !Thread.interrupted()) {
                MediaCodecAsyncHelper.SlotEvent event;
                try {
                    event = queue.take();
                } catch (InterruptedException e) {
                    return MediaCodecBlockModelHelper.Result.FAIL;
                }

                if (event.input) {
                    if (signaledEos) {
                        continue;
                    }
                    while (hardwareBuffers.size() <= event.index) {
                        hardwareBuffers.add(null);
                    }
                    HardwareBuffer buffer = hardwareBuffers.get(event.index);
                    if (buffer == null) {
                        buffer = HardwareBuffer.create(
                                kWidth, kHeight, HardwareBuffer.YCBCR_420_888, 1, usage);
                        hardwareBuffers.set(event.index, buffer);
                    }
                    try (Image image = MediaCodec.mapHardwareBuffer(buffer)) {
                        assertNotNull("CPU readable/writable image must be mappable", image);
                        assertEquals(kWidth, image.getWidth());
                        assertEquals(kHeight, image.getHeight());
                        // For Y plane
                        int rowSampling = 1;
                        int colSampling = 1;
                        for (Image.Plane plane : image.getPlanes()) {
                            ByteBuffer planeBuffer = plane.getBuffer();
                            for (int row = 0; row < kHeight / rowSampling; ++row) {
                                int rowOffset = row * plane.getRowStride();
                                for (int col = 0; col < kWidth / rowSampling; ++col) {
                                    planeBuffer.put(
                                            rowOffset + col * plane.getPixelStride(),
                                            (byte)(frameIndex * 4));
                                }
                            }
                            // For Cb and Cr planes
                            rowSampling = 2;
                            colSampling = 2;
                        }
                    }

                    long timestampUs = 1000000l * frameIndex / kFrameRate;
                    ++frameIndex;
                    if (frameIndex >= 32) {
                        signaledEos = true;
                    }
                    timestampList.add(timestampUs);
                    mediaCodec.getQueueRequest(event.index)
                            .setHardwareBuffer(buffer)
                            .setPresentationTimeUs(timestampUs)
                            .setFlags(signaledEos ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0)
                            .queue();
                } else {
                    MediaCodec.OutputFrame frame = mediaCodec.getOutputFrame(event.index);
                    eos = (frame.getFlags() & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;

                    if (!eos) {
                        assertNotNull(frame.getLinearBlock());
                        frame.getLinearBlock().recycle();
                    }

                    timestampList.remove(frame.getPresentationTimeUs());

                    mediaCodec.releaseOutputBuffer(event.index, false);
                }
            }

            if (!timestampList.isEmpty()) {
                assertTrue("Timestamp should match between input / output: " + timestampList,
                        timestampList.isEmpty());
            }
            return eos ? MediaCodecBlockModelHelper.Result.SUCCESS
                : MediaCodecBlockModelHelper.Result.FAIL;
        } catch (IOException e) {
            throw new RuntimeException("error reading input resource", e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (mediaCodec != null) {
                mediaCodec.stop();
                mediaCodec.release();
            }
            for (HardwareBuffer buffer : hardwareBuffers) {
                if (buffer != null) {
                    buffer.close();
                }
            }
        }
    }

    private MediaCodecBlockModelHelper.Result runDecodeShortVideo(
            String inputResource,
            long lastBufferTimestampUs,
            boolean obtainBlockForEachBuffer) {
        return MediaCodecBlockModelHelper.runDecodeShortVideo(
                getMediaExtractorForMimeType(inputResource, "video/"),
                lastBufferTimestampUs, obtainBlockForEachBuffer, null, null, null);
    }

    private static MediaExtractor getMediaExtractorForMimeType(final String resource,
            String mimeTypePrefix) {
        MediaExtractor mediaExtractor = new MediaExtractor();
        try (AssetFileDescriptor afd = getAssetFileDescriptorFor(resource)) {
            mediaExtractor.setDataSource(
                    afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        int trackIndex;
        for (trackIndex = 0; trackIndex < mediaExtractor.getTrackCount(); trackIndex++) {
            MediaFormat trackMediaFormat = mediaExtractor.getTrackFormat(trackIndex);
            if (trackMediaFormat.getString(MediaFormat.KEY_MIME).startsWith(mimeTypePrefix)) {
                mediaExtractor.selectTrack(trackIndex);
                break;
            }
        }
        if (trackIndex == mediaExtractor.getTrackCount()) {
            throw new IllegalStateException("couldn't get a video track");
        }

        return mediaExtractor;
    }
}
