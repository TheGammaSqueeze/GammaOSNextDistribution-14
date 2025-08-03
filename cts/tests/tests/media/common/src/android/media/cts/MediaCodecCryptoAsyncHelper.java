/*
 * Copyright (C) 2023 The Android Open Source Project
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
import android.media.MediaCodec.CryptoException;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaCrypto;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import android.util.Log;
import android.os.HandlerThread;
import android.os.Handler;
import android.os.Build;
import android.platform.test.annotations.AppModeFull;
import android.test.AndroidTestCase;
import androidx.test.filters.SdkSuppress;

import android.view.Surface;

import android.media.cts.MediaCodecAsyncHelper;
import android.media.cts.MediaCodecBlockModelHelper;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.android.compatibility.common.util.MediaUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Supplier;

import org.junit.Assume;
/**
 * MediaCodecCryptoAsyncHelper class
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
public class MediaCodecCryptoAsyncHelper {
    private static final String TAG = "MediaCodecCryptoAsyncHelper";
    private static final boolean VERBOSE = false;           // lots of logging

    public static class ExtractorInputSlotListener
            implements MediaCodecAsyncHelper.InputSlotListener {
        public static class Builder {
            public Builder setExtractor(MediaExtractor extractor) {
                mExtractor = extractor;
                return this;
            }

            public Builder setLastBufferTimestampUs(Long timestampUs) {
                mLastBufferTimestampUs = timestampUs;
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
                        mContentEncrypted);
            }
            private MediaExtractor mExtractor = null;
            private Long mLastBufferTimestampUs = null;
            private boolean mContentEncrypted = false;
        }

        private ExtractorInputSlotListener(
                MediaExtractor extractor,
                Long lastBufferTimestampUs,
                boolean contentEncrypted) {
            mExtractor = extractor;
            mLastBufferTimestampUs = lastBufferTimestampUs;
            mContentEncrypted = contentEncrypted;
        }

        @Override
        public void onInputSlot(MediaCodec codec, int index) throws Exception {
            if (mSignaledEos) return;
            // Try to feed more data into the codec.
            ByteBuffer inputBuffer = codec.getInputBuffer(index);
            final int sampleSize = mExtractor.readSampleData(inputBuffer, 0);
            final long sampleTime = mExtractor.getSampleTime();
            mSignaledEos = mExtractor.getSampleTrackIndex() == -1
                    || (mLastBufferTimestampUs != null && sampleTime >= mLastBufferTimestampUs);
            int flags = mSignaledEos ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0;
            boolean isSampleEncrypted = (mExtractor.getSampleFlags() &
                MediaExtractor.SAMPLE_FLAG_ENCRYPTED) != 0;
            if (sampleSize <= 0) {
                codec.queueInputBuffer(
                index,
                0 /* offset */,
                0 /* sampleSize */,
                0 /* sampleTime */,
                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            } else if (isSampleEncrypted) {
                final MediaCodec.CryptoInfo info = new MediaCodec.CryptoInfo();
                mExtractor.getSampleCryptoInfo(info);
                    codec.queueSecureInputBuffer(
                        index,
                        0 /* offset */,
                        info,
                        sampleTime,
                        flags);
                mExtractor.advance();
            } else {
                codec.queueInputBuffer(
                    index,
                    0 /* offset */,
                    sampleSize,
                    sampleTime,
                    flags);

                mExtractor.advance();
            }
        }
        private final MediaExtractor mExtractor;
        private final Long mLastBufferTimestampUs;
        private boolean mSignaledEos = false;
        private final boolean mContentEncrypted;
    }

    private static class SurfaceOutputSlotListener
            implements MediaCodecAsyncHelper.OutputSlotListener {

       protected final OutputSurface mOutputSurface;

       public SurfaceOutputSlotListener(
                OutputSurface surface) {
            mOutputSurface = surface;
        }

        @Override
        public boolean onOutputSlot(MediaCodec codec, int index,
            MediaCodec.BufferInfo info) throws Exception {

            boolean endOfStream = false;
            if (info != null) {
                endOfStream = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
            }
            codec.releaseOutputBuffer(index, false);
            return endOfStream;
        }
    }

    /*
     * Calling this function with a secure decoder should attempt to throw a
     * CryptoException as clearKey video should not be using a secure codec.
     * Since onCryptoError() is not overridden here, base implementation should
     * by default throw an IllegalStateException. This exception is caught
     * and queued.The exception can arrive before/after the IllegalStateException
     * from the codec. Hence the test fails if onCryptoError() is not called within
     * 500 ms.
     */
    public static void runShortClearKeyVideoWithNoCryptoErrorOverride(
            MediaExtractor mediaExtractor,
            Long lastBufferTimestampUs,
            MediaCrypto crypto) throws Exception {
        boolean secure = true;
        OutputSurface outputSurface = null;
        MediaCodec mediaCodec = null;
        final LinkedBlockingQueue<Throwable>
                threadExceptionQueue = new LinkedBlockingQueue<>();
        final LinkedBlockingQueue<MediaCodecAsyncHelper.SlotEvent>
                slotQueue = new LinkedBlockingQueue<>();
        boolean isSecureDecodeASuccess = false;
        try {
            outputSurface = new OutputSurface(1, 1, false, secure);
            MediaFormat mediaFormat = mediaExtractor.getTrackFormat(
                    mediaExtractor.getSampleTrackIndex());
            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
            String codecName = MediaCodecAsyncHelper.getDecoderForType(mime, secure);
            Assume.assumeTrue("No Codec Found for this format", codecName != null);
            mediaCodec = MediaCodec.createByCodecName(codecName);
            HandlerThread callbackThread = new HandlerThread("MediaCodec callback thread");
            callbackThread.start();
            callbackThread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {

                public void uncaughtException(Thread t, Throwable e) {
                    try {
                        if (e instanceof IllegalStateException) {
                            threadExceptionQueue.put(e);
                        }
                    } catch(InterruptedException ex){/*ignore*/}
                }
            });
            MediaCodec.Callback mediaCallback = new MediaCodec.Callback() {
                @Override
                public void onInputBufferAvailable(MediaCodec codec, int index) {
                    slotQueue.offer(new MediaCodecAsyncHelper.SlotEvent(true, index));
                }

                @Override
                public void onOutputBufferAvailable(
                        MediaCodec codec, int index, MediaCodec.BufferInfo info) {
                    slotQueue.offer(new MediaCodecAsyncHelper.SlotEvent(false, index, info));
                }

                @Override
                public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
                }

                @Override
                public void onError(MediaCodec codec, CodecException e) {
                }

            };
            mediaCodec.setCallback(mediaCallback, new Handler(callbackThread.getLooper()));
            runComponentWithInput(
                    mediaCodec,
                    crypto,
                    mediaFormat,
                    outputSurface.getSurface(),
                    false,  // encoder
                    new MediaCodecCryptoAsyncHelper.ExtractorInputSlotListener
                            .Builder()
                            .setExtractor(mediaExtractor)
                            .setLastBufferTimestampUs(lastBufferTimestampUs)
                            .setContentEncrypted(crypto != null)
                            .build(),
                    new SurfaceOutputSlotListener(outputSurface),
                    slotQueue);
            // secure decoder should have failed
            isSecureDecodeASuccess = true;

        } catch (IllegalStateException e) {
            /*
             * expected, but do not propagate this as we need to see
             * if an error is queued to "threadExceptionQueue"
             */
        } catch (CryptoException e) {
            // this is caught only to revise error message
            throw new IllegalStateException(
                    "CryptoException should be thrown in callback", e);
        } finally {
            if (mediaCodec != null) {
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

        if (isSecureDecodeASuccess) {
            /*secure decoders should have errored out here*/
            throw new IllegalStateException("Secure codec should have failed");
        }

        // check to see if Base impl of onCryptoError() is called from framework
        if (threadExceptionQueue.poll(500L, TimeUnit.MILLISECONDS) == null) {
            throw new IllegalStateException("onCryptoError() base exception not called");
        }
    }

    /*
     * This can be called with a secure decoder or with a normal decoder.
     * Since we have a clear key video, both decoder types are called with
     * a crypto object. Calling this function with a secure decoder
     * should throw a CryptoException as clearKey video should not be using
     * a secure codec. This exception should be called into
     * onCryptoError() callback which satisfies the callback usage.
     * onCryptoError() can arrive before/after the IllegalStateException from
     * the codec. Hence the test fails if onCryptoError() is not called within
     * 500 ms.
     *
     * When used with a non-secure codec, the decoder should get initialized
     * with CONFIGURE_FLAG_USE_CRYPTO_ASYNC flag and should be successful.
     */
    public static void runDecodeShortClearKeyVideo(
            MediaExtractor mediaExtractor, boolean secure,
            Long lastBufferTimestampUs,
            MediaCrypto crypto) throws Exception {
        OutputSurface outputSurface = null;
        MediaCodec mediaCodec = null;
        final LinkedBlockingQueue<MediaCodec.CryptoInfo>
                cryptoInfoQueue = new LinkedBlockingQueue<>();
        final LinkedBlockingQueue<MediaCodecAsyncHelper.SlotEvent>
                slotQueue = new LinkedBlockingQueue<>();
        try {
            outputSurface = new OutputSurface(1, 1, false, secure);
            MediaFormat mediaFormat = mediaExtractor.getTrackFormat(
                    mediaExtractor.getSampleTrackIndex());
            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
            String codecName = MediaCodecAsyncHelper.getDecoderForType(mime, secure);
            Assume.assumeTrue("No Codec Found for this format", codecName != null);
            mediaCodec = MediaCodec.createByCodecName(codecName);
            MediaCodec.Callback mediaCallback = new MediaCodec.Callback() {
                @Override
                public void onInputBufferAvailable(MediaCodec codec, int index) {
                    slotQueue.offer(new MediaCodecAsyncHelper.SlotEvent(true, index));
                }

                @Override
                public void onOutputBufferAvailable(
                        MediaCodec codec, int index, MediaCodec.BufferInfo info) {
                    slotQueue.offer(new MediaCodecAsyncHelper.SlotEvent(false, index, info));
                }

                @Override
                public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
                }

                @Override
                public void onError(MediaCodec codec, CodecException e) {
                }

                @Override
                public void onCryptoError(MediaCodec codec, CryptoException e) {
                    MediaCodec.CryptoInfo info = e.getCryptoInfo();
                    // set to indicate the callback has happened.
                    if (info != null) {
                        try {
                            cryptoInfoQueue.put(info);
                            Log.i(TAG, "MediaCodec fired Crypto Error: "
                                    + e.getErrorCode() + " Detail: " + e.getMessage());
                        } catch(InterruptedException ex) {/* ignore */}
                    }
                }
            };
            mediaCodec.setCallback(mediaCallback);
            runComponentWithInput(
                    mediaCodec,
                    crypto,
                    mediaFormat,
                    outputSurface.getSurface(),
                    false,  // encoder
                    new MediaCodecCryptoAsyncHelper.ExtractorInputSlotListener
                            .Builder()
                            .setExtractor(mediaExtractor)
                            .setLastBufferTimestampUs(lastBufferTimestampUs)
                            .setContentEncrypted(crypto != null)
                            .build(),
                    new SurfaceOutputSlotListener(outputSurface),
                    slotQueue);
            if (secure) {
                throw new IllegalStateException("Secure codec should have failed");
            }
        } catch (IllegalStateException e) {
            // this is expected only for secure codec.
            if (!secure) {
                throw e;
            }
            if (cryptoInfoQueue.poll(500L, TimeUnit.MILLISECONDS) == null) {
                throw new IllegalStateException("onCryptoError() not called yet");
            }
        } catch (CryptoException e) {
            // this is caught only to revise error message
            throw new IllegalStateException(
                    "CryptoException should be thrown in callback", e);
        } finally {
            if (mediaCodec != null) {
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

    private static void runComponentWithInput(
            MediaCodec mediaCodec,
            MediaCrypto crypto,
            MediaFormat mediaFormat,
            Surface surface,
            boolean encoder,
            MediaCodecAsyncHelper.InputSlotListener inputListener,
            MediaCodecAsyncHelper.OutputSlotListener outputListener,
            LinkedBlockingQueue<MediaCodecAsyncHelper.SlotEvent> slotQueue)
            throws InterruptedException, Exception {
        int flags = MediaCodec.CONFIGURE_FLAG_USE_CRYPTO_ASYNC;
        mediaCodec.configure(mediaFormat, surface, crypto, flags);
        mediaCodec.start();
        boolean endOfStream = false;
        boolean signaledEos = false;
        while (!endOfStream && !Thread.interrupted()) {
            MediaCodecAsyncHelper.SlotEvent event;
            event = slotQueue.take();

            if (event.input) {
                inputListener.onInputSlot(mediaCodec, event.index);
            } else {
                endOfStream = outputListener.onOutputSlot(mediaCodec, event.index, event.info);
            }
        }
        if (!endOfStream) {
            throw new Exception("Codec output not complete with end of stream");
        }
    }

    // BlockModel Tests
    public static void runDecodeShortVideoUsingBlockModel(
            MediaExtractor mediaExtractor,
            boolean secure,
            Long lastBufferTimestampUs,
            MediaCrypto crypto,
            boolean obtainBlockForEachBuffer) throws Exception{
        OutputSurface outputSurface = null;
        MediaCodec mediaCodec = null;
       final LinkedBlockingQueue<MediaCodec.CryptoInfo>
              cryptoInfoQueue = new LinkedBlockingQueue<>();
        try {
            outputSurface = new OutputSurface(1, 1, false, secure);
            MediaFormat mediaFormat = mediaExtractor.getTrackFormat(
                    mediaExtractor.getSampleTrackIndex());
            String mime = mediaFormat.getString(MediaFormat.KEY_MIME);
            String codecName = MediaCodecAsyncHelper.getDecoderForType(mime, secure);
            Assume.assumeTrue("No Codec Found for this format", codecName != null);
            mediaCodec = MediaCodec.createByCodecName(codecName);

            List<Long> timestampList = Collections.synchronizedList(new ArrayList<>());
            runComponentWithLinearInputUsingBlockModel(
                    mediaCodec,
                    crypto,
                    mediaFormat,
                    outputSurface.getSurface(),
                    new MediaCodecBlockModelHelper.ExtractorInputSlotListener
                            .Builder()
                            .setExtractor(mediaExtractor)
                            .setLastBufferTimestampUs(lastBufferTimestampUs)
                            .setObtainBlockForEachBuffer(obtainBlockForEachBuffer)
                            .setTimestampQueue(timestampList)
                            .setContentEncrypted(true)
                            .build(),
                    new MediaCodecBlockModelHelper.SurfaceOutputSlotListener(
                            outputSurface, timestampList, null),
                    cryptoInfoQueue);
            if (secure) {
                throw new IllegalStateException("Secure codec should have failed");
            }
        } catch (IllegalStateException e) {
            // this is expected only for secure codec.
            if (!secure) {
                throw e;
            }
            if (cryptoInfoQueue.poll(500L, TimeUnit.MILLISECONDS) == null) {
                throw new IllegalStateException("onCryptoError() not called yet");
            }
        } catch (CryptoException e) {
            // this is caught only to revise error message
            throw new IllegalStateException(
                    "CryptoException should be thrown in callback", e);
        } finally {
            if (mediaCodec != null) {
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

    public static void runComponentWithLinearInputUsingBlockModel(
            MediaCodec mediaCodec,
            MediaCrypto crypto,
            MediaFormat mediaFormat,
            Surface surface,
            MediaCodecBlockModelHelper.ExtractorInputSlotListener inputListener,
            MediaCodecBlockModelHelper.SurfaceOutputSlotListener outputListener,
            LinkedBlockingQueue<MediaCodec.CryptoInfo> cryptoInfoQueue)
            throws InterruptedException, Exception {
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

            @Override
            public void onCryptoError(MediaCodec codec, CryptoException e) {
                 MediaCodec.CryptoInfo info = e.getCryptoInfo();
                // set to indicate the callback has happened.
                if (info != null) {
                    try {
                        cryptoInfoQueue.put(info);
                        Log.i(TAG, "MediaCodec fired Crypto Error: "
                                + e.getErrorCode() + " Detail: " + e.getMessage());
                    } catch(InterruptedException ex) {/* ignore */}
                }
            }
        });
        String[] codecNames = new String[]{ mediaCodec.getName() };
        MediaCodecBlockModelHelper.LinearInputBlock input =
                new MediaCodecBlockModelHelper.LinearInputBlock();
        // this is termed in assumeTrue as failure of this is not the objective of this test.
        if (!mediaCodec.getCodecInfo().isVendor() && mediaCodec.getName().startsWith("c2.")) {
            Assume.assumeTrue("Google default c2.* codecs are copy-free compatible with" +
                    "LinearBlocks", MediaCodec.LinearBlock.isCodecCopyFreeCompatible(codecNames));
        }
        if (crypto != null) {
            codecNames[0] = codecNames[0] + ".secure";
        }
        input.block = MediaCodec.LinearBlock.obtain(
                1024 * 1024 /*APP_BUFFER_SIZE*/, codecNames);
        // this is not the scope of this test case. A failure of isMappable()
        // is not the objective of this test.
        Assume.assumeTrue("Blocks obtained through LinearBlock.obtain must be mappable",
                input.block.isMappable());
        input.buffer = input.block.map();
        input.offset = 0;

        int flags = MediaCodec.CONFIGURE_FLAG_USE_BLOCK_MODEL
                | MediaCodec.CONFIGURE_FLAG_USE_CRYPTO_ASYNC;
        mediaCodec.configure(mediaFormat, surface, crypto, flags);
        mediaCodec.start();
        boolean endOfStream = false;
        boolean signaledEos = false;
        while (!endOfStream && !Thread.interrupted()) {
            MediaCodecAsyncHelper.SlotEvent event;
            event = queue.take();
            if (event.input) {
                inputListener.onInputSlot(mediaCodec, event.index, input);
            } else {
                endOfStream = outputListener.onOutputSlot(mediaCodec, event.index);
            }
        }
        input.block.recycle();
        if(!endOfStream) {
            throw new Exception("Codec output not complete with end of stream");
        }
    }
}
