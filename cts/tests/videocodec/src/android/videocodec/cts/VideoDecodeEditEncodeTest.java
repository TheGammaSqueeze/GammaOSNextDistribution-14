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

package android.videocodec.cts;

import static android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface;
import static android.media.MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR;
import static android.media.MediaFormat.KEY_ALLOW_FRAME_DROP;
import static android.mediav2.common.cts.CodecEncoderTestBase.getMuxerFormatForMediaType;
import static android.mediav2.common.cts.CodecEncoderTestBase.getTempFilePath;
import static android.mediav2.common.cts.CodecEncoderTestBase.muxOutput;
import static android.mediav2.common.cts.CodecTestBase.ComponentClass.HARDWARE;
import static android.mediav2.common.cts.CodecTestBase.Q_DEQ_TIMEOUT_US;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.mediav2.common.cts.CodecTestBase;
import android.mediav2.common.cts.CompareStreams;
import android.mediav2.common.cts.DecodeStreamToYuv;
import android.mediav2.common.cts.EncoderConfigParams;
import android.mediav2.common.cts.InputSurface;
import android.mediav2.common.cts.OutputSurface;
import android.mediav2.common.cts.RawResource;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.Preconditions;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * This test is similar to {@link android.media.codec.cts.DecodeEditEncodeTest}, except for the
 * edit part. The DecodeEditEncodeTest does swapping of color planes during editing, this test
 * performs smoothening and sharpening. Besides this every thing is almost same.
 * This test additionally validates the output of encoder. As smoothening and sharpening filters
 * are applied on input, the test compares block level (32x32) between smoothened clip and
 * sharpened clip and checks if they are as expected.
 */
@RunWith(Parameterized.class)
public class VideoDecodeEditEncodeTest {
    private static final String LOG_TAG = VideoDecodeEditEncodeTest.class.getSimpleName();
    private static final boolean WORK_AROUND_BUGS = false;  // avoid fatal codec bugs
    private static final boolean VERBOSE = false;
    private static final CodecTestBase.ComponentClass SELECT_SWITCH = HARDWARE;
    private static final String MEDIA_DIR = WorkDir.getMediaDirString();
    private static final String RES_CLIP =
            MEDIA_DIR + "AVICON-MOBILE-BirthdayHalfway-SI17-CRUW03-L-420-8bit-SDR-1080p-30fps.mp4";
    private static final String RES_MEDIA_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC;
    private static final int TARGET_WIDTH = 1280;
    private static final int TARGET_HEIGHT = 720;
    private static final int TARGET_BITRATE = 10000000;
    private static final float AVG_ACCEPTABLE_QUALITY = 25.0f;  // dB

    private static final String[] KERNELS = {
            // 5x5 Gaussian smoothing filter
            "float kernel[KERNEL_SIZE];\n"
                    + "kernel[0] = 1.0 / 273.0;\n"
                    + "kernel[1] = 4.0 / 273.0;\n"
                    + "kernel[2] = 7.0 / 273.0;\n"
                    + "kernel[3] = 4.0 / 273.0;\n"
                    + "kernel[4] = 1.0 / 273.0;\n"

                    + "kernel[5] = 4.0 / 273.0;\n"
                    + "kernel[6] = 16.0 / 273.0;\n"
                    + "kernel[7] = 26.0 / 273.0;\n"
                    + "kernel[8] = 16.0 / 273.0;\n"
                    + "kernel[9] = 4.0 / 273.0;\n"

                    + "kernel[10] = 7.0 / 273.0;\n"
                    + "kernel[11] = 26.0 / 273.0;\n"
                    + "kernel[12] = 41.0 / 273.0;\n"
                    + "kernel[13] = 26.0 / 273.0;\n"
                    + "kernel[14] = 7.0 / 273.0;\n"

                    + "kernel[15] = 4.0 / 273.0;\n"
                    + "kernel[16] = 16.0 / 273.0;\n"
                    + "kernel[17] = 26.0 / 273.0;\n"
                    + "kernel[18] = 16.0 / 273.0;\n"
                    + "kernel[19] = 4.0 / 273.0;\n"

                    + "kernel[20] = 1.0 / 273.0;\n"
                    + "kernel[21] = 4.0 / 273.0;\n"
                    + "kernel[22] = 7.0 / 273.0;\n"
                    + "kernel[23] = 4.0 / 273.0;\n"
                    + "kernel[24] = 1.0 / 273.0;\n",

            // 5x5 Sharpening filter
            // Sharpening Kernel = 2 * Identity matrix - Gaussian smoothing kernel
            "float kernel[KERNEL_SIZE];\n"
                    + "kernel[0] = -1.0 / 273.0;\n"
                    + "kernel[1] = -4.0 / 273.0;\n"
                    + "kernel[2] = -7.0 / 273.0;\n"
                    + "kernel[3] = -4.0 / 273.0;\n"
                    + "kernel[4] = -1.0 / 273.0;\n"

                    + "kernel[5] = -4.0 / 273.0;\n"
                    + "kernel[6] = -16.0 / 273.0;\n"
                    + "kernel[7] = -26.0 / 273.0;\n"
                    + "kernel[8] = -16.0 / 273.0;\n"
                    + "kernel[9] = -4.0 / 273.0;\n"

                    + "kernel[10] = -7.0 / 273.0;\n"
                    + "kernel[11] = -26.0 / 273.0;\n"
                    + "kernel[12] = 505.0 / 273.0;\n"
                    + "kernel[13] = -26.0 / 273.0;\n"
                    + "kernel[14] = -7.0 / 273.0;\n"

                    + "kernel[15] = -4.0 / 273.0;\n"
                    + "kernel[16] = -16.0 / 273.0;\n"
                    + "kernel[17] = -26.0 / 273.0;\n"
                    + "kernel[18] = -16.0 / 273.0;\n"
                    + "kernel[19] = -4.0 / 273.0;\n"

                    + "kernel[20] = -1.0 / 273.0;\n"
                    + "kernel[21] = -4.0 / 273.0;\n"
                    + "kernel[22] = -7.0 / 273.0;\n"
                    + "kernel[23] = -4.0 / 273.0;\n"
                    + "kernel[24] = -1.0 / 273.0;\n"
    };

    private final String mEncoderName;
    private final String mMediaType;

    private final ArrayList<String> mTmpFiles = new ArrayList<>();

    public VideoDecodeEditEncodeTest(String encoderName, String mediaType,
            @SuppressWarnings("unused") String allTestParams) {
        mEncoderName = encoderName;
        mMediaType = mediaType;
    }

    @Parameterized.Parameters(name = "{index}_{0}_{1}")
    public static Collection<Object[]> input() {
        final boolean isEncoder = true;
        final boolean needAudio = false;
        final boolean needVideo = true;
        // mediaType
        final List<Object[]> exhaustiveArgsList = Arrays.asList(new Object[][]{
                {MediaFormat.MIMETYPE_VIDEO_AVC},
                {MediaFormat.MIMETYPE_VIDEO_HEVC},
        });
        return CodecTestBase.prepareParamList(exhaustiveArgsList, isEncoder, needAudio, needVideo,
                false, SELECT_SWITCH);
    }

    @After
    public void tearDown() {
        for (String tmpFile : mTmpFiles) {
            File tmp = new File(tmpFile);
            if (tmp.exists()) assertTrue("unable to delete file " + tmpFile, tmp.delete());
        }
        mTmpFiles.clear();
    }

    private static String getShader(String width, String height, String kernel) {
        return "#extension GL_OES_EGL_image_external : require\n"
                + "#define KERNEL_SIZE 25\n"
                + "precision mediump float;\n"
                + "varying vec2 vTextureCoord;\n"
                + "uniform samplerExternalOES sTexture;\n"
                + width
                + height
                + "const float step_w = 1.0/width;\n"
                + "const float step_h = 1.0/height;\n"
                + "void main() {\n"
                + kernel
                + "  vec2 offset[KERNEL_SIZE];"
                + "  offset[0] = vec2(-2.0 * step_w, -2.0 * step_h);\n"  // [-2, -2] // row -2
                + "  offset[1] = vec2(-step_w, -2.0 * step_h);\n"  // [-1, -2]
                + "  offset[2] = vec2(0.0, -2.0 * step_h);\n"  // [0, -2]
                + "  offset[3] = vec2(step_w, -2.0 * step_h);\n"  // [1, -2]
                + "  offset[4] = vec2(2.0 * step_w, -2.0 * step_h);\n"  // [2, -2]

                + "  offset[5] = vec2(-2.0 * step_w, -step_h);\n"  // [-2, -1]  // row -1
                + "  offset[6] = vec2(-step_w, -step_h);\n"  // [-1, -1]
                + "  offset[7] = vec2(0.0, -step_h);\n"  // [0, -1]
                + "  offset[8] = vec2(step_w, -step_h);\n"  // [1, -1]
                + "  offset[9] = vec2(2.0 * step_w, -step_h);\n"  // [2, -1]

                + "  offset[10] = vec2(-2.0 * step_w, 0.0);\n"  // [-2, 0]  // curr row
                + "  offset[11] = vec2(-step_w, 0.0);\n"  // [-1, 0]
                + "  offset[12] = vec2(0.0, 0.0);\n"  // [0, 0]
                + "  offset[13] = vec2(step_w, 0.0);\n"  // [1, 0]
                + "  offset[14] = vec2(2.0 * step_w, 0.0);\n"  // [2, 0]

                + "  offset[15] = vec2(-2.0 * step_w, step_h);\n"  // [-2, 1]  // row +1
                + "  offset[16] = vec2(-step_w, step_h);\n"  // [-1, 1]
                + "  offset[17] = vec2(0.0, step_h);\n"  // [0, 1]
                + "  offset[18] = vec2(step_w, step_h);\n"  // [1, 1]
                + "  offset[19] = vec2(2.0 * step_w, step_h);\n"  // [2, 1]

                + "  offset[20] = vec2(-2.0 * step_w, 2.0 * step_h);\n"  // [-2, 2]  // row +2
                + "  offset[21] = vec2(-step_w, 2.0 * step_h);\n"  // [-1, 2]
                + "  offset[22] = vec2(0.0, 2.0 * step_h);\n"  // [-0, 2]
                + "  offset[23] = vec2(step_w, 2.0 * step_h);\n"  // [1, 2]
                + "  offset[24] = vec2(2.0 * step_w, 2.0 * step_h);\n"  // [2, 2]

                + "  vec4 sum = vec4(0.0);\n"
                + "  vec4 sample;\n"
                + "  for (int i=0; i<KERNEL_SIZE; i++) {\n"
                + "    sample = texture2D(sTexture, vTextureCoord + offset[i]).rgba;\n"
                + "    sum = sum + sample * kernel[i];\n"
                + "  }\n"
                + "  gl_FragColor = sum;\n"
                + "}\n";
    }

    /**
     * The elementary stream coming out of the encoder needs to be fed back into
     * the decoder one chunk at a time.  If we just wrote the data to a file, we would lose
     * the information about chunk boundaries.  This class stores the encoded data in memory,
     * retaining the chunk organization.
     */
    private static class VideoChunks {
        private MediaFormat mMediaFormat;
        private byte[] mMemory = new byte[1024];
        private int mMemIndex = 0;
        private final ArrayList<MediaCodec.BufferInfo> mBufferInfo = new ArrayList<>();

        private void splitMediaToMuxerParameters(@NonNull String srcPath, @NonNull String mediaType,
                int frameLimit) throws IOException {
            // Set up MediaExtractor to read from the source.
            MediaExtractor extractor = new MediaExtractor();
            Preconditions.assertTestFileExists(srcPath);
            extractor.setDataSource(srcPath);

            // Set up MediaFormat
            for (int trackID = 0; trackID < extractor.getTrackCount(); trackID++) {
                extractor.selectTrack(trackID);
                MediaFormat format = extractor.getTrackFormat(trackID);
                if (mediaType.equals(format.getString(MediaFormat.KEY_MIME))) {
                    mMediaFormat = format;
                    break;
                } else {
                    extractor.unselectTrack(trackID);
                }
            }

            if (null == mMediaFormat) {
                extractor.release();
                throw new IllegalArgumentException(
                        "could not find usable track in file " + srcPath);
            }

            // Set up location for elementary stream
            File file = new File(srcPath);
            int bufferSize = (int) file.length();
            bufferSize = ((bufferSize + 127) >> 7) << 7;
            // Ideally, Sum of return values of extractor.readSampleData(...) should not exceed
            // source file size. But in case of Vorbis, aosp extractor appends an additional 4
            // bytes to the data at every readSampleData() call. bufferSize <<= 1 empirically
            // large enough to hold the excess 4 bytes per read call
            bufferSize <<= 1;
            mMemory = new byte[bufferSize];
            ByteBuffer byteBuffer = ByteBuffer.allocate(bufferSize);
            mMemIndex = 0;
            mBufferInfo.clear();

            // Let MediaExtractor do its thing
            boolean sawEOS = false;
            int offset = 0;
            int frameCount = 0;
            while (!sawEOS && frameCount < frameLimit) {
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                bufferInfo.offset = offset;
                bufferInfo.size = extractor.readSampleData(byteBuffer, offset);
                if (bufferInfo.size < 0) {
                    sawEOS = true;
                } else {
                    bufferInfo.presentationTimeUs = extractor.getSampleTime();
                    int flags = extractor.getSampleFlags();
                    bufferInfo.flags = 0;
                    if ((flags & MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                        bufferInfo.flags |= MediaCodec.BUFFER_FLAG_KEY_FRAME;
                    }
                    mBufferInfo.add(bufferInfo);
                    extractor.advance();
                }
                offset += bufferInfo.size;
                frameCount++;
            }
            byteBuffer.rewind();
            byteBuffer.get(mMemory, 0, byteBuffer.limit());
            mMemIndex = byteBuffer.limit();
            extractor.release();
        }

        public void addChunkData(ByteBuffer buf, MediaCodec.BufferInfo info) {
            MediaCodec.BufferInfo copy = new MediaCodec.BufferInfo();
            copy.set(mMemIndex, info.size, info.presentationTimeUs, info.flags);
            mBufferInfo.add(copy);

            if (mMemIndex + info.size >= mMemory.length) {
                mMemory = Arrays.copyOf(mMemory, mMemIndex + info.size);
            }
            buf.position(info.offset);
            buf.get(mMemory, mMemIndex, info.size);
            mMemIndex += info.size;
        }

        /**
         * Sets the MediaFormat, for the benefit of a future decoder.
         */
        public void setMediaFormat(MediaFormat format) {
            mMediaFormat = format;
        }

        /**
         * Gets the MediaFormat that was used by the encoder.
         */
        public MediaFormat getMediaFormat() {
            return new MediaFormat(mMediaFormat);
        }

        /**
         * Returns the number of chunks currently held.
         */
        public int getNumChunks() {
            return mBufferInfo.size();
        }

        /**
         * Copies the data from chunk N into "dest".  Advances dest.position.
         */
        public void getChunkData(int chunk, ByteBuffer dest) {
            int offset = mBufferInfo.get(chunk).offset;
            int size = mBufferInfo.get(chunk).size;
            dest.put(mMemory, offset, size);
        }

        /**
         * Returns the flags associated with chunk N.
         */
        public int getChunkFlags(int chunk) {
            return mBufferInfo.get(chunk).flags;
        }

        /**
         * Returns the timestamp associated with chunk N.
         */
        public long getChunkTime(int chunk) {
            return mBufferInfo.get(chunk).presentationTimeUs;
        }

        public ArrayList<MediaCodec.BufferInfo> getChunkInfos() {
            return mBufferInfo;
        }

        public ByteBuffer getBuffer() {
            return ByteBuffer.wrap(mMemory);
        }

        public void dumpBuffer() throws IOException {
            File dump = File.createTempFile(LOG_TAG + "OUT", ".bin");
            Log.d(LOG_TAG, "dump file name is " + dump.getAbsolutePath());
            try (FileOutputStream outputStream = new FileOutputStream(dump)) {
                outputStream.write(mMemory, 0, mMemIndex);
            }
        }
    }

    /**
     * Edits a video file, saving the contents to a new file.  This involves decoding and
     * re-encoding, not to mention conversions between YUV and RGB, and so may be lossy.
     * <p>
     * If we recognize the decoded format we can do this in Java code using the ByteBuffer[]
     * output, but it's not practical to support all OEM formats.  By using a SurfaceTexture
     * for output and a Surface for input, we can avoid issues with obscure formats and can
     * use a fragment shader to do transformations.
     */
    private VideoChunks editVideoFile(VideoChunks inputData, String kernel) throws IOException {
        VideoChunks outputData = new VideoChunks();
        MediaCodec decoder = null;
        MediaCodec encoder = null;
        InputSurface inputSurface = null;
        OutputSurface outputSurface = null;

        try {
            // find decoder for the test clip
            MediaFormat decoderFormat = inputData.getMediaFormat();
            decoderFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, COLOR_FormatSurface);
            ArrayList<MediaFormat> formats = new ArrayList<>();
            formats.add(decoderFormat);
            ArrayList<String> decoders = CodecTestBase.selectCodecs(RES_MEDIA_TYPE, formats, null,
                    false, SELECT_SWITCH);
            assumeTrue("Could not find decoder for format : " + decoderFormat, decoders.size() > 0);
            String decoderName = decoders.get(0);

            // build encoder format and check if it is supported by the current component
            EncoderConfigParams.Builder foreman =
                    new EncoderConfigParams.Builder(mMediaType)
                            .setWidth(TARGET_WIDTH)
                            .setHeight(TARGET_HEIGHT)
                            .setColorFormat(COLOR_FormatSurface)
                            .setInputBitDepth(8)
                            .setFrameRate(30)
                            .setBitRate(TARGET_BITRATE)
                            .setBitRateMode(BITRATE_MODE_VBR);
            MediaFormat encoderFormat = foreman.build().getFormat();
            formats.clear();
            formats.add(encoderFormat);
            assumeTrue("Encoder: " + mEncoderName + " doesn't support format: " + encoderFormat,
                    CodecTestBase.areFormatsSupported(mEncoderName, mMediaType, formats));

            // configure
            encoder = MediaCodec.createByCodecName(mEncoderName);
            encoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = new InputSurface(encoder.createInputSurface(), false);
            if (inputSurface.getWidth() != TARGET_WIDTH
                    || inputSurface.getHeight() != TARGET_HEIGHT) {
                inputSurface.updateSize(TARGET_WIDTH, TARGET_HEIGHT);
            }
            inputSurface.makeCurrent();
            encoder.start();

            // OutputSurface uses the EGL context created by InputSurface.
            decoder = MediaCodec.createByCodecName(decoderName);
            outputSurface = new OutputSurface();
            outputSurface.changeFragmentShader(getShader(
                    "const float width = " + (float) TARGET_WIDTH + ";\n",
                    "const float height = " + (float) TARGET_HEIGHT + ";\n", kernel));
            // do not allow frame drops
            decoderFormat.setInteger(KEY_ALLOW_FRAME_DROP, 0);

            decoder.configure(decoderFormat, outputSurface.getSurface(), null, 0);
            decoder.start();

            // verify that we are not dropping frames
            MediaFormat format = decoder.getInputFormat();
            assertEquals("Could not prevent frame dropping", 0,
                    format.getInteger(KEY_ALLOW_FRAME_DROP));

            editVideoData(inputData, decoder, outputSurface, inputSurface, encoder, outputData);
        } finally {
            if (VERBOSE) {
                Log.d(LOG_TAG, "shutting down encoder, decoder");
            }
            if (outputSurface != null) {
                outputSurface.release();
            }
            if (inputSurface != null) {
                inputSurface.release();
            }
            if (encoder != null) {
                encoder.stop();
                encoder.release();
            }
            if (decoder != null) {
                decoder.stop();
                decoder.release();
            }
        }
        return outputData;
    }

    /**
     * Edits a stream of video data.
     */
    private void editVideoData(VideoChunks inputData, MediaCodec decoder,
            OutputSurface outputSurface, InputSurface inputSurface, MediaCodec encoder,
            VideoChunks outputData) {
        ByteBuffer[] decoderInputBuffers = decoder.getInputBuffers();
        ByteBuffer[] encoderOutputBuffers = encoder.getOutputBuffers();
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int inputChunk = 0;
        int outputCount = 0;

        boolean outputDone = false;
        boolean inputDone = false;
        boolean decoderDone = false;
        while (!outputDone) {
            if (VERBOSE) {
                Log.d(LOG_TAG, "edit loop");
            }

            // Feed more data to the decoder.
            if (!inputDone) {
                int inputBufIndex = decoder.dequeueInputBuffer(Q_DEQ_TIMEOUT_US);
                if (inputBufIndex >= 0) {
                    if (inputChunk == inputData.getNumChunks()) {
                        // End of stream -- send empty frame with EOS flag set.
                        decoder.queueInputBuffer(inputBufIndex, 0, 0, 0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                        if (VERBOSE) {
                            Log.d(LOG_TAG, "sent input EOS (with zero-length frame)");
                        }
                    } else {
                        // Copy a chunk of input to the decoder.  The first chunk should have
                        // the BUFFER_FLAG_CODEC_CONFIG flag set.
                        ByteBuffer inputBuf = decoderInputBuffers[inputBufIndex];
                        inputBuf.clear();
                        inputData.getChunkData(inputChunk, inputBuf);
                        int flags = inputData.getChunkFlags(inputChunk);
                        long time = inputData.getChunkTime(inputChunk);
                        decoder.queueInputBuffer(inputBufIndex, 0, inputBuf.position(),
                                time, flags);
                        if (VERBOSE) {
                            Log.d(LOG_TAG, "submitted frame " + inputChunk + " to dec, size="
                                    + inputBuf.position() + " flags=" + flags);
                        }
                        inputChunk++;
                    }
                } else {
                    if (VERBOSE) {
                        Log.d(LOG_TAG, "input buffer not available");
                    }
                }
            }

            // Assume output is available.  Loop until both assumptions are false.
            boolean decoderOutputAvailable = !decoderDone;
            boolean encoderOutputAvailable = true;
            while (decoderOutputAvailable || encoderOutputAvailable) {
                // Start by draining any pending output from the encoder.  It's important to
                // do this before we try to stuff any more data in.
                int encoderStatus = encoder.dequeueOutputBuffer(info, Q_DEQ_TIMEOUT_US);
                if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // no output available yet
                    if (VERBOSE) {
                        Log.d(LOG_TAG, "no output from encoder available");
                    }
                    encoderOutputAvailable = false;
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                    encoderOutputBuffers = encoder.getOutputBuffers();
                    if (VERBOSE) {
                        Log.d(LOG_TAG, "encoder output buffers changed");
                    }
                } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat newFormat = encoder.getOutputFormat();
                    outputData.setMediaFormat(newFormat);
                    if (VERBOSE) {
                        Log.d(LOG_TAG, "encoder output format changed: " + newFormat);
                    }
                } else if (encoderStatus < 0) {
                    fail("unexpected result from encoder.dequeueOutputBuffer: " + encoderStatus);
                } else { // encoderStatus >= 0
                    ByteBuffer encodedData = encoderOutputBuffers[encoderStatus];
                    if (encodedData == null) {
                        fail("encoderOutputBuffer " + encoderStatus + " was null");
                    }

                    // Write the data to the output "file".
                    if (info.size != 0) {
                        encodedData.position(info.offset);
                        encodedData.limit(info.offset + info.size);

                        outputData.addChunkData(encodedData, info);

                        if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) outputCount++;

                        if (VERBOSE) {
                            Log.d(LOG_TAG, "encoder output " + info.size + " bytes");
                        }
                    }
                    outputDone = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    encoder.releaseOutputBuffer(encoderStatus, false);
                }
                if (encoderStatus != MediaCodec.INFO_TRY_AGAIN_LATER) {
                    // Continue attempts to drain output.
                    continue;
                }

                // Encoder is drained, check to see if we've got a new frame of output from
                // the decoder.  (The output is going to a Surface, rather than a ByteBuffer,
                // but we still get information through BufferInfo.)
                if (!decoderDone) {
                    int decoderStatus = decoder.dequeueOutputBuffer(info, Q_DEQ_TIMEOUT_US);
                    if (decoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        // no output available yet
                        if (VERBOSE) {
                            Log.d(LOG_TAG, "no output from decoder available");
                        }
                        decoderOutputAvailable = false;
                    } else if (decoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        //decoderOutputBuffers = decoder.getOutputBuffers();
                        if (VERBOSE) {
                            Log.d(LOG_TAG, "decoder output buffers changed (we don't care)");
                        }
                    } else if (decoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        // expected before first buffer of data
                        MediaFormat newFormat = decoder.getOutputFormat();
                        if (VERBOSE) {
                            Log.d(LOG_TAG, "decoder output format changed: " + newFormat);
                        }
                    } else if (decoderStatus < 0) {
                        fail("unexpected result from decoder.dequeueOutputBuffer: "
                                + decoderStatus);
                    } else { // decoderStatus >= 0
                        if (VERBOSE) {
                            Log.d(LOG_TAG, "surface decoder given buffer " + decoderStatus
                                    + " (size=" + info.size + ")");
                        }
                        // The ByteBuffers are null references, but we still get a nonzero
                        // size for the decoded data.
                        boolean doRender = (info.size != 0);

                        // As soon as we call releaseOutputBuffer, the buffer will be forwarded
                        // to SurfaceTexture to convert to a texture.  The API doesn't
                        // guarantee that the texture will be available before the call
                        // returns, so we need to wait for the onFrameAvailable callback to
                        // fire.  If we don't wait, we risk rendering from the previous frame.
                        decoder.releaseOutputBuffer(decoderStatus, doRender);
                        if (doRender) {
                            // This waits for the image and renders it after it arrives.
                            if (VERBOSE) {
                                Log.d(LOG_TAG, "awaiting frame");
                            }
                            outputSurface.awaitNewImage();
                            outputSurface.drawImage();

                            // Send it to the encoder.
                            inputSurface.setPresentationTime(info.presentationTimeUs * 1000);
                            if (VERBOSE) {
                                Log.d(LOG_TAG, "swapBuffers");
                            }
                            inputSurface.swapBuffers();
                        }
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            // forward decoder EOS to encoder
                            if (VERBOSE) {
                                Log.d(LOG_TAG, "signaling input EOS");
                            }
                            if (WORK_AROUND_BUGS) {
                                // Bail early, possibly dropping a frame.
                                return;
                            } else {
                                encoder.signalEndOfInputStream();
                            }
                        }
                    }
                }
            }
        }

        if (inputChunk != outputCount) {
            throw new RuntimeException("frame lost: " + inputChunk + " in, " + outputCount
                    + " out");
        }
    }

    private double computeVariance(RawResource yuv) throws IOException {
        Preconditions.assertTestFileExists(yuv.mFileName);
        assertEquals("has support for 8 bit clips only", 1, yuv.mBytesPerSample);
        final int bSize = 16;
        assertTrue("chosen block size is too large with respect to image dimensions",
                yuv.mWidth > bSize && yuv.mHeight > bSize);
        double variance = 0;
        int blocks = 0;
        try (RandomAccessFile refStream = new RandomAccessFile(new File(yuv.mFileName), "r")) {
            int ySize = yuv.mWidth * yuv.mHeight;
            int uvSize = ySize >> 1;
            byte[] luma = new byte[ySize];

            while (true) {
                int bytesReadRef = refStream.read(luma);
                if (bytesReadRef == -1) break;
                assertEquals("bad, reading unaligned frame size", bytesReadRef, ySize);
                refStream.skipBytes(uvSize);
                for (int i = 0; i < yuv.mHeight - bSize; i += bSize) {
                    for (int j = 0; j < yuv.mWidth - bSize; j += bSize) {
                        long sse = 0, sum = 0;
                        int offset = i * yuv.mWidth + j;
                        for (int p = 0; p < bSize; p++) {
                            for (int q = 0; q < bSize; q++) {
                                int sample = luma[offset + p * yuv.mWidth + q];
                                sum += sample;
                                sse += sample * sample;
                            }
                        }
                        double meanOfSquares = ((double) sse) / (bSize * bSize);
                        double mean = ((double) sum) / (bSize * bSize);
                        double squareOfMean = mean * mean;
                        double blockVariance = (meanOfSquares - squareOfMean);
                        assertTrue("variance can't be negative", blockVariance >= 0.0f);
                        variance += blockVariance;
                        assertTrue("caution overflow", variance >= 0.0);
                        blocks++;
                    }
                }
            }
            return variance / blocks;
        }
    }

    /**
     * Extract, Decode, Edit, Encode and Validate. Check description of class
     * {@link VideoDecodeEditEncodeTest}
     */
    @ApiTest(apis = {"android.opengl.GLES20#GL_FRAGMENT_SHADER",
            "android.media.format.MediaFormat#KEY_ALLOW_FRAME_DROP",
            "MediaCodecInfo.CodecCapabilities#COLOR_FormatSurface"})
    @Test
    public void testVideoEdit() throws IOException, InterruptedException {
        VideoChunks sourceChunks = new VideoChunks();
        sourceChunks.splitMediaToMuxerParameters(RES_CLIP, RES_MEDIA_TYPE, 90);
        VideoChunks[] outputData = new VideoChunks[2];
        for (int i = 0; i < 2; i++) {
            outputData[i] = editVideoFile(sourceChunks, KERNELS[i]);
        }
        String tmpPathA = getTempFilePath("");
        mTmpFiles.add(tmpPathA);
        String tmpPathB = getTempFilePath("");
        mTmpFiles.add(tmpPathB);

        int muxerFormat = getMuxerFormatForMediaType(mMediaType);
        muxOutput(tmpPathA, muxerFormat, outputData[0].getMediaFormat(), outputData[0].getBuffer(),
                outputData[0].getChunkInfos());
        muxOutput(tmpPathB, muxerFormat, outputData[1].getMediaFormat(), outputData[1].getBuffer(),
                outputData[1].getChunkInfos());

        CompareStreams cs = null;
        try {
            cs = new CompareStreams(mMediaType, tmpPathA, mMediaType, tmpPathB, false, false);
            double[] avgPSNR = cs.getAvgPSNR();
            final double weightedAvgPSNR = (4 * avgPSNR[0] + avgPSNR[1] + avgPSNR[2]) / 6;
            if (weightedAvgPSNR < AVG_ACCEPTABLE_QUALITY) {
                fail(String.format("Average PSNR of the sequence: %f is < threshold : %f\n",
                        weightedAvgPSNR, AVG_ACCEPTABLE_QUALITY));
            }
        } finally {
            if (cs != null) cs.cleanUp();
        }
        DecodeStreamToYuv yuvRes = new DecodeStreamToYuv(mMediaType, tmpPathA, Integer.MAX_VALUE,
                LOG_TAG);
        RawResource yuv = yuvRes.getDecodedYuv();
        mTmpFiles.add(yuv.mFileName);
        double varA = computeVariance(yuv);

        yuvRes = new DecodeStreamToYuv(mMediaType, tmpPathB, Integer.MAX_VALUE, LOG_TAG);
        yuv = yuvRes.getDecodedYuv();
        mTmpFiles.add(yuv.mFileName);
        double varB = computeVariance(yuv);

        Log.d(LOG_TAG, "variance is " + varA + " " + varB);
        assertTrue(String.format("Blurred clip variance is not less than sharpened clip. Variance"
                        + " of blurred clip is %f, variance of sharpened clip is %f", varA, varB),
                varA < varB);
    }
}
