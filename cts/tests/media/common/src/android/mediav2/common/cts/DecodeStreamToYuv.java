/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.mediav2.common.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * Class to decode a video track of a clip and write the result to a file.
 */
public class DecodeStreamToYuv extends CodecDecoderTestBase {
    private static final String LOG_TAG = DecodeStreamToYuv.class.getSimpleName();

    private final MediaFormat mStreamFormat;
    private final ByteBuffer mStreamBuffer;
    private final ArrayList<MediaCodec.BufferInfo> mStreamBufferInfos;
    private final int mFrameLimit;
    private final String mOutputPrefix;

    private String mOutputFile;
    private int mWidth;
    private int mHeight;
    private int mBytesPerSample;

    public DecodeStreamToYuv(String mediaType, String inpFile, int frameLimit, String outYuvPrefix)
            throws IOException {
        super(findDecoderForStream(mediaType, inpFile), mediaType, inpFile, LOG_TAG);
        mStreamFormat = null;
        mStreamBuffer = null;
        mStreamBufferInfos = null;
        mFrameLimit = frameLimit;
        mOutputPrefix = outYuvPrefix;
    }

    public DecodeStreamToYuv(String mediaType, String inpFile, int frameLimit) throws IOException {
        this(mediaType, inpFile, frameLimit, "test" + LOG_TAG);
    }

    public DecodeStreamToYuv(String mediaType, String inpFile) throws IOException {
        this(mediaType, inpFile, Integer.MAX_VALUE);
    }

    public DecodeStreamToYuv(MediaFormat streamFormat, ByteBuffer streamBuffer,
            ArrayList<MediaCodec.BufferInfo> streamBufferInfos) {
        super(findDecoderForFormat(streamFormat), streamFormat.getString(MediaFormat.KEY_MIME),
                null, LOG_TAG);
        mStreamFormat = streamFormat;
        mStreamBuffer = streamBuffer;
        mStreamBufferInfos = streamBufferInfos;
        mFrameLimit = Integer.MAX_VALUE;
        mOutputPrefix = "test" + LOG_TAG;
    }

    public RawResource getDecodedYuv() {
        File tmp = null;
        try {
            tmp = File.createTempFile(mOutputPrefix, ".yuv");
            mOutputFile = tmp.getAbsolutePath();
            if (mStreamFormat != null) {
                decodeToMemory(mStreamBuffer, mStreamBufferInfos, mStreamFormat, mCodecName);
            } else {
                decodeToMemory(mTestFile, mCodecName, 0, MediaExtractor.SEEK_TO_CLOSEST_SYNC,
                        mFrameLimit);
            }
        } catch (Exception e) {
            if (tmp != null && tmp.exists()) assertTrue(tmp.delete());
            throw new RuntimeException(e.getMessage());
        } catch (AssertionError e) {
            if (tmp != null && tmp.exists()) assertTrue(tmp.delete());
            throw new AssertionError(e.getMessage());
        }
        return new RawResource.Builder()
                .setFileName(mOutputFile, false)
                .setDimension(mWidth, mHeight)
                .setBytesPerSample(mBytesPerSample)
                .setColorFormat(ImageFormat.UNKNOWN)
                .build();
    }

    public static MediaFormat getFormatInStream(String mediaType, String file) throws IOException {
        File tmp = new File(file);
        if (!tmp.exists()) {
            throw new FileNotFoundException("Test Setup Error, missing file: " + file);
        }
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(file);
        MediaFormat format = null;
        for (int trackID = 0; trackID < extractor.getTrackCount(); trackID++) {
            MediaFormat fmt = extractor.getTrackFormat(trackID);
            if (mediaType.equalsIgnoreCase(fmt.getString(MediaFormat.KEY_MIME))) {
                format = fmt;
                break;
            }
        }
        extractor.release();
        if (format == null) {
            throw new IllegalArgumentException(
                    "No track with mediaType: " + mediaType + " found in file: " + file);
        }
        return format;
    }

    static String findDecoderForStream(String mediaType, String file) throws IOException {
        return findDecoderForFormat(getFormatInStream(mediaType, file));
    }

    static String findDecoderForFormat(MediaFormat format) {
        MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        String codecName = mcl.findDecoderForFormat(format);
        if (codecName == null) {
            throw new IllegalArgumentException("No decoder for format: " + format);
        }
        return codecName;
    }

    protected void dequeueOutput(int bufferIndex, MediaCodec.BufferInfo info) {
        if (info.size > 0) {
            Image img = mCodec.getOutputImage(bufferIndex);
            assertNotNull(img);
            writeImage(img);
            if (mOutputCount == 0) {
                MediaFormat format = mCodec.getOutputFormat();
                mWidth = getWidth(format);
                mHeight = getHeight(format);
                int imgFormat = img.getFormat();
                mBytesPerSample = (ImageFormat.getBitsPerPixel(imgFormat) * 2) / (8 * 3);
            }
            mOutputCount++;
        }
        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mSawOutputEOS = true;
        }
        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
            mOutputBuff.saveOutPTS(info.presentationTimeUs);
            mOutputCount++;
        }
        mCodec.releaseOutputBuffer(bufferIndex, false);
    }

    static YUVImage getImage(Image image) {
        YUVImage yuvImage = new YUVImage();
        int format = image.getFormat();
        assertTrue("unexpected image format",
                format == ImageFormat.YUV_420_888 || format == ImageFormat.YCBCR_P010);
        int bytesPerSample = (ImageFormat.getBitsPerPixel(format) * 2) / (8 * 3);  // YUV420

        Rect cropRect = image.getCropRect();
        int imageWidth = cropRect.width();
        int imageHeight = cropRect.height();
        assertTrue("unexpected image dimensions", imageWidth > 0 && imageHeight > 0);

        int imageLeft = cropRect.left;
        int imageTop = cropRect.top;
        Image.Plane[] planes = image.getPlanes();
        for (int i = 0; i < planes.length; ++i) {
            ByteBuffer buf = planes[i].getBuffer();
            int width, height, rowStride, pixelStride, x, y, left, top;
            rowStride = planes[i].getRowStride();
            pixelStride = planes[i].getPixelStride();
            if (i == 0) {
                assertEquals(bytesPerSample, pixelStride);
                width = imageWidth;
                height = imageHeight;
                left = imageLeft;
                top = imageTop;
            } else {
                width = imageWidth / 2;
                height = imageHeight / 2;
                left = imageLeft / 2;
                top = imageTop / 2;
            }
            int cropOffset = (left * pixelStride) + top * rowStride;
            // local contiguous pixel buffer
            byte[] bb = new byte[width * height * bytesPerSample];

            int base = buf.position();
            int pos = base + cropOffset;
            if (pixelStride == bytesPerSample) {
                for (y = 0; y < height; ++y) {
                    buf.position(pos + y * rowStride);
                    buf.get(bb, y * width * bytesPerSample, width * bytesPerSample);
                }
            } else {
                // local line buffer
                byte[] lb = new byte[rowStride];
                // do it pixel-by-pixel
                for (y = 0; y < height; ++y) {
                    buf.position(pos + y * rowStride);
                    // we're only guaranteed to have pixelStride * (width - 1) +
                    // bytesPerSample bytes
                    buf.get(lb, 0, pixelStride * (width - 1) + bytesPerSample);
                    for (x = 0; x < width; ++x) {
                        for (int bytePos = 0; bytePos < bytesPerSample; ++bytePos) {
                            bb[y * width * bytesPerSample + x * bytesPerSample + bytePos] =
                                    lb[x * pixelStride + bytePos];
                        }
                    }
                }
            }
            buf.position(base);
            yuvImage.mData.add(bb);
        }
        return yuvImage;
    }

    void writeImage(Image image) {
        YUVImage yuvImage = getImage(image);
        try (FileOutputStream outputStream = new FileOutputStream(mOutputFile, mOutputCount != 0)) {
            for (int i = 0; i < yuvImage.mData.size(); i++) {
                outputStream.write(yuvImage.mData.get(i));
            }
        } catch (IOException e) {
            fail("unable to write file : " + mOutputFile + " received exception : " + e);
        }
    }
}
