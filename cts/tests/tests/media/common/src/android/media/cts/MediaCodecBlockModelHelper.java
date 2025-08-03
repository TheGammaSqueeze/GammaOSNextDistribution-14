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

package android.media.cts;

import android.media.MediaCodec;
import android.media.MediaCodec.CodecException;
import android.media.MediaCrypto;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;
import android.test.AndroidTestCase;
import android.util.Log;
import android.view.Surface;
import android.media.cts.MediaCodecAsyncHelper;
import androidx.test.filters.SdkSuppress;

import com.android.compatibility.common.util.MediaUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * MediaCodecBlockModelHelper class
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
@AppModeFull(reason = "Instant apps cannot access the SD card")
public class MediaCodecBlockModelHelper extends AndroidTestCase {
    private static final String TAG = "MediaCodecBlockModelHelper";
    private static final boolean VERBOSE = false;           // lots of logging

                                                            // H.264 Advanced Video Coding

    private static final int APP_BUFFER_SIZE = 1024 * 1024;  // 1 MB

    // The test should fail if the codec never produces output frames for the truncated input.
    // Time out processing, as we have no way to query whether the decoder will produce output.
    private static final int TIMEOUT_MS = 60000;  // 1 minute

    public enum Result {
        SUCCESS,
        FAIL,
        SKIP,
    }

    public static Result runThread(Supplier<Result> supplier) throws InterruptedException {
        final AtomicReference<Result> result = new AtomicReference<>(Result.FAIL);
        Thread thread = new Thread(new Runnable() {
            public void run() {
                result.set(supplier.get());
            }
        });
        final AtomicReference<Throwable> throwable = new AtomicReference<>();
        thread.setUncaughtExceptionHandler((Thread t, Throwable e) -> {
            throwable.set(e);
        });
        thread.start();
        thread.join(TIMEOUT_MS);
        Throwable t = throwable.get();
        if (t != null) {
            throw new AssertionError("There was an error while running the thread", t);
        }
        assertTrue("timed out decoding to end-of-stream", result.get() != Result.FAIL);
        return result.get();
    }

    public static class LinearInputBlock {
        MediaCodec.LinearBlock block;
        ByteBuffer buffer;
        int offset;
    }
    private static interface InputSlotListener {
        public void onInputSlot(MediaCodec codec, int index, LinearInputBlock input) throws Exception;
    }

    public static class ExtractorInputSlotListener implements InputSlotListener {
        public static class Builder {
            public Builder setExtractor(MediaExtractor extractor) {
                mExtractor = extractor;
                return this;
            }

            public Builder setLastBufferTimestampUs(Long timestampUs) {
                mLastBufferTimestampUs = timestampUs;
                return this;
            }

            public Builder setObtainBlockForEachBuffer(boolean enabled) {
                mObtainBlockForEachBuffer = enabled;
                return this;
            }

            public Builder setTimestampQueue(List<Long> list) {
                mTimestampList = list;
                return this;
            }

            public Builder setContentEncrypted(boolean encrypted) {
                mContentEncrypted = encrypted;
                return this;
            }

            public ExtractorInputSlotListener build() {
                if (mExtractor == null) {
                    throw new IllegalStateException("Extractor must be set");
                }
                return new ExtractorInputSlotListener(
                        mExtractor, mLastBufferTimestampUs,
                        mObtainBlockForEachBuffer, mTimestampList,
                        mContentEncrypted);
            }

            private MediaExtractor mExtractor = null;
            private Long mLastBufferTimestampUs = null;
            private boolean mObtainBlockForEachBuffer = false;
            private List<Long> mTimestampList = null;
            private boolean mContentEncrypted = false;
        }

        private ExtractorInputSlotListener(
                MediaExtractor extractor,
                Long lastBufferTimestampUs,
                boolean obtainBlockForEachBuffer,
                List<Long> timestampList,
                boolean contentEncrypted) {
            mExtractor = extractor;
            mLastBufferTimestampUs = lastBufferTimestampUs;
            mObtainBlockForEachBuffer = obtainBlockForEachBuffer;
            mTimestampList = timestampList;
            mContentEncrypted = contentEncrypted;
        }

        @Override
        public void onInputSlot(MediaCodec codec, int index, LinearInputBlock input) throws Exception {
            // Try to feed more data into the codec.
            if (mExtractor.getSampleTrackIndex() == -1 || mSignaledEos) {
                return;
            }
            long size = mExtractor.getSampleSize();
            String[] codecNames = new String[]{ codec.getName() };
            if (mContentEncrypted) {
                codecNames[0] = codecNames[0] + ".secure";
            }
            if (mObtainBlockForEachBuffer) {
                input.block.recycle();
                input.block = MediaCodec.LinearBlock.obtain(Math.toIntExact(size), codecNames);
                assertTrue("Blocks obtained through LinearBlock.obtain must be mappable",
                        input.block.isMappable());
                input.buffer = input.block.map();
                input.offset = 0;
            }
            if (input.buffer.capacity() < size) {
                input.block.recycle();
                input.block = MediaCodec.LinearBlock.obtain(
                        Math.toIntExact(size * 2), codecNames);
                assertTrue("Blocks obtained through LinearBlock.obtain must be mappable",
                        input.block.isMappable());
                input.buffer = input.block.map();
                input.offset = 0;
            } else if (input.buffer.capacity() - input.offset < size) {
                long capacity = input.buffer.capacity();
                input.block.recycle();
                input.block = MediaCodec.LinearBlock.obtain(
                        Math.toIntExact(capacity), codecNames);
                assertTrue("Blocks obtained through LinearBlock.obtain must be mappable",
                        input.block.isMappable());
                input.buffer = input.block.map();
                input.offset = 0;
            }
            long timestampUs = mExtractor.getSampleTime();
            int written = mExtractor.readSampleData(input.buffer, input.offset);
            boolean encrypted =
                    (mExtractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_ENCRYPTED) != 0;
            if (encrypted) {
                mExtractor.getSampleCryptoInfo(mCryptoInfo);
            }
            mExtractor.advance();
            mSignaledEos = mExtractor.getSampleTrackIndex() == -1
                    || (mLastBufferTimestampUs != null && timestampUs >= mLastBufferTimestampUs);
            MediaCodec.QueueRequest request = codec.getQueueRequest(index);
            if (encrypted) {
                request.setEncryptedLinearBlock(
                        input.block, input.offset, written, mCryptoInfo);
            } else {
                request.setLinearBlock(input.block, input.offset, written);
            }
            request.setPresentationTimeUs(timestampUs);
            request.setFlags(mSignaledEos ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
            if (mSetParams) {
                request.setIntegerParameter("vendor.int", 0);
                request.setLongParameter("vendor.long", 0);
                request.setFloatParameter("vendor.float", (float)0);
                request.setStringParameter("vendor.string", "str");
                request.setByteBufferParameter("vendor.buffer", ByteBuffer.allocate(1));
                mSetParams = false;
            }
            request.queue();
            input.offset += written;
            if (mTimestampList != null) {
                mTimestampList.add(timestampUs);
            }
        }

        private final MediaExtractor mExtractor;
        private final Long mLastBufferTimestampUs;
        private final boolean mObtainBlockForEachBuffer;
        private final List<Long> mTimestampList;
        private boolean mSignaledEos = false;
        private boolean mSetParams = true;
        private final MediaCodec.CryptoInfo mCryptoInfo = new MediaCodec.CryptoInfo();
        private final boolean mContentEncrypted;
    }

    private static interface OutputSlotListener {
        // Returns true if EOS is met
        public boolean onOutputSlot(MediaCodec codec, int index) throws Exception;
    }

    public static class DummyOutputSlotListener implements OutputSlotListener {
        public DummyOutputSlotListener(boolean graphic, List<Long> timestampList) {
            mGraphic = graphic;
            mTimestampList = timestampList;
        }

        @Override
        public boolean onOutputSlot(MediaCodec codec, int index) throws Exception {
            MediaCodec.OutputFrame frame = codec.getOutputFrame(index);
            boolean eos = (frame.getFlags() & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;

            if (mGraphic && frame.getHardwareBuffer() != null) {
                frame.getHardwareBuffer().close();
            }
            if (!mGraphic && frame.getLinearBlock() != null) {
                frame.getLinearBlock().recycle();
            }

            mTimestampList.remove(frame.getPresentationTimeUs());

            codec.releaseOutputBuffer(index, false);

            return eos;
        }

        private final boolean mGraphic;
        private final List<Long> mTimestampList;
    }

    public static class SurfaceOutputSlotListener implements OutputSlotListener {
        public SurfaceOutputSlotListener(
                OutputSurface surface,
                List<Long> timestampList,
                List<FormatChangeEvent> events) {
            mOutputSurface = surface;
            mTimestampList = timestampList;
            mEvents = (events != null) ? events : new ArrayList<>();
        }

        @Override
        public boolean onOutputSlot(MediaCodec codec, int index) throws Exception {
            MediaCodec.OutputFrame frame = codec.getOutputFrame(index);
            boolean eos = (frame.getFlags() & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;

            boolean render = false;
            if (frame.getHardwareBuffer() != null) {
                frame.getHardwareBuffer().close();
                render = true;
            }

            mTimestampList.remove(frame.getPresentationTimeUs());

            if (!frame.getChangedKeys().isEmpty()) {
                mEvents.add(new FormatChangeEvent(
                        frame.getPresentationTimeUs(), frame.getChangedKeys(), frame.getFormat()));
            }

            codec.releaseOutputBuffer(index, render);
            if (render) {
                mOutputSurface.awaitNewImage();
            }

            return eos;
        }

        private final OutputSurface mOutputSurface;
        private final List<Long> mTimestampList;
        private final List<FormatChangeEvent> mEvents;
    }

    private static final UUID CLEARKEY_SCHEME_UUID =
            new UUID(0x1077efecc0b24d02L, 0xace33c1e52e2fb4bL);

    private static final byte[] CLEAR_KEY_CENC = convert(new int[] {
            0x3f, 0x0a, 0x33, 0xf3, 0x40, 0x98, 0xb9, 0xe2,
            0x2b, 0xc0, 0x78, 0xe0, 0xa1, 0xb5, 0xe8, 0x54 });

    private static byte[] convert(int[] intArray) {
        byte[] byteArray = new byte[intArray.length];
        for (int i = 0; i < intArray.length; ++i) {
            byteArray[i] = (byte)intArray[i];
        }
        return byteArray;
    }

    public static class FormatChangeEvent {
        FormatChangeEvent(long ts, Set<String> keys, MediaFormat fmt) {
            timestampUs = ts;
            changedKeys = new HashSet<>(keys);
            format = new MediaFormat(fmt);
        }

        long timestampUs;
        Set<String> changedKeys;
        MediaFormat format;

        @Override
        public String toString() {
            return Long.toString(timestampUs) + "us: changed keys=" + changedKeys
                + " format=" + format;
        }
    }

    public static Result runDecodeShortVideo(
            MediaExtractor mediaExtractor,
            Long lastBufferTimestampUs,
            boolean obtainBlockForEachBuffer,
            MediaFormat format,
            List<FormatChangeEvent> events,
            byte[] sessionId) {
        OutputSurface outputSurface = null;
        MediaCodec mediaCodec = null;
        MediaCrypto crypto = null;
        try {
            outputSurface = new OutputSurface(1, 1);
            MediaFormat mediaFormat = mediaExtractor.getTrackFormat(
                    mediaExtractor.getSampleTrackIndex());
            if (format != null) {
                // copy CSD
                for (int i = 0; i < 3; ++i) {
                    String key = "csd-" + i;
                    if (mediaFormat.containsKey(key)) {
                        format.setByteBuffer(key, mediaFormat.getByteBuffer(key));
                    }
                }
                mediaFormat = format;
            }
            // TODO: b/147748978
            String[] codecs = MediaUtils.getDecoderNames(true /* isGoog */, mediaFormat);
            if (codecs.length == 0) {
                Log.i(TAG, "No decoder found for format= " + mediaFormat);
                return Result.SKIP;
            }
            mediaCodec = MediaCodec.createByCodecName(codecs[0]);

            if (sessionId != null) {
                crypto = new MediaCrypto(CLEARKEY_SCHEME_UUID, new byte[0] /* initData */);
                crypto.setMediaDrmSession(sessionId);
            }
            List<Long> timestampList = Collections.synchronizedList(new ArrayList<>());
            Result result = runComponentWithLinearInput(
                    mediaCodec,
                    crypto,
                    mediaFormat,
                    outputSurface.getSurface(),
                    false,  // encoder
                    new MediaCodecBlockModelHelper.ExtractorInputSlotListener
                            .Builder()
                            .setExtractor(mediaExtractor)
                            .setLastBufferTimestampUs(lastBufferTimestampUs)
                            .setObtainBlockForEachBuffer(obtainBlockForEachBuffer)
                            .setTimestampQueue(timestampList)
                            .setContentEncrypted(sessionId != null)
                            .build(),
                    new SurfaceOutputSlotListener(outputSurface, timestampList, events));
            if (result == Result.SUCCESS) {
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
            if (outputSurface != null) {
                outputSurface.release();
            }
            if (crypto != null) {
                crypto.release();
            }
        }
    }

    public static Result runComponentWithLinearInput(
            MediaCodec mediaCodec,
            MediaCrypto crypto,
            MediaFormat mediaFormat,
            Surface surface,
            boolean encoder,
            MediaCodecBlockModelHelper.InputSlotListener inputListener,
            MediaCodecBlockModelHelper.OutputSlotListener outputListener) throws Exception {
        final LinkedBlockingQueue<MediaCodecAsyncHelper.SlotEvent> queue = new LinkedBlockingQueue<>();
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
        String[] codecNames = new String[]{ mediaCodec.getName() };
        LinearInputBlock input = new LinearInputBlock();
        if (!mediaCodec.getCodecInfo().isVendor() && mediaCodec.getName().startsWith("c2.")) {
            assertTrue("Google default c2.* codecs are copy-free compatible with LinearBlocks",
                    MediaCodec.LinearBlock.isCodecCopyFreeCompatible(codecNames));
        }
        if (crypto != null) {
            codecNames[0] = codecNames[0] + ".secure";
        }
        input.block = MediaCodec.LinearBlock.obtain(
                APP_BUFFER_SIZE, codecNames);
        assertTrue("Blocks obtained through LinearBlock.obtain must be mappable",
                input.block.isMappable());
        input.buffer = input.block.map();
        input.offset = 0;

        int flags = MediaCodec.CONFIGURE_FLAG_USE_BLOCK_MODEL;
        if (encoder) {
            flags |= MediaCodec.CONFIGURE_FLAG_ENCODE;
        }
        mediaCodec.configure(mediaFormat, surface, crypto, flags);
        mediaCodec.start();
        boolean eos = false;
        boolean signaledEos = false;
        while (!eos && !Thread.interrupted()) {
            MediaCodecAsyncHelper.SlotEvent event;
            try {
                event = queue.take();
            } catch (InterruptedException e) {
                return Result.FAIL;
            }

            if (event.input) {
                inputListener.onInputSlot(mediaCodec, event.index, input);
            } else {
                eos = outputListener.onOutputSlot(mediaCodec, event.index);
            }
        }

        input.block.recycle();
        return eos ? Result.SUCCESS : Result.FAIL;
    }
}
