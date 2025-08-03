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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.media.AudioFormat;
import android.media.Image;
import android.media.MediaCodec;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.IntStream;
import java.util.zip.CRC32;

/**
 * Class to store the output received from mediacodec components. The dequeueOutput() call sends
 * compressed/decoded bytes of data and their corresponding timestamp information. This is stored
 * in memory and outPtsList fields of this class. For video decoders, the decoded information can
 * be overwhelming as it is uncompressed YUV. For them we compute the CRC32 checksum of the
 * output image and buffer and store it instead.
 * <p>
 * ByteBuffer output of encoder/decoder components can be written to disk by setting ENABLE_DUMP
 * to true. Exercise CAUTION while running tests with ENABLE_DUMP set to true as this will crowd
 * the storage with files. These files are configured to be deleted on exit. So, in order to see
 * the captured output, File.deleteOnExit() needs to be be commented. Also it might be necessary
 * to set option name="cleanup-apks" to "false" in AndroidTest.xml.
 */
public class OutputManager {
    private static final String LOG_TAG = OutputManager.class.getSimpleName();
    private static final boolean ENABLE_DUMP = false;

    private byte[] mMemory;
    private int mMemIndex;
    private final CRC32 mCrc32UsingImage;
    private final CRC32 mCrc32UsingBuffer;
    private final ArrayList<Long> mInpPtsList;
    private final ArrayList<Long> mOutPtsList;
    private final StringBuilder mErrorLogs;
    private final StringBuilder mSharedErrorLogs;
    private File mOutFileYuv;
    private boolean mAppendToYuvFile;
    private File mOutFileY;
    private boolean mAppendToYFile;
    private File mOutFileDefault;

    public OutputManager() {
        this(new StringBuilder());
    }

    public OutputManager(StringBuilder sharedErrorLogs) {
        mMemory = new byte[1024];
        mMemIndex = 0;
        mCrc32UsingImage = new CRC32();
        mCrc32UsingBuffer = new CRC32();
        mInpPtsList = new ArrayList<>();
        mOutPtsList = new ArrayList<>();
        mErrorLogs = new StringBuilder(
                "##################       Error Details         ####################\n");
        mSharedErrorLogs = sharedErrorLogs;
    }

    public void saveInPTS(long pts) {
        // Add only unique timeStamp, discarding any duplicate frame / non-display frame
        if (!mInpPtsList.contains(pts)) {
            mInpPtsList.add(pts);
        }
    }

    public void saveOutPTS(long pts) {
        mOutPtsList.add(pts);
    }

    public boolean isPtsStrictlyIncreasing(long lastPts) {
        boolean res = true;
        for (int i = 0; i < mOutPtsList.size(); i++) {
            if (lastPts < mOutPtsList.get(i)) {
                lastPts = mOutPtsList.get(i);
            } else {
                mErrorLogs.append("Timestamp values are not strictly increasing. \n");
                mErrorLogs.append("Frame indices around which timestamp values decreased :- \n");
                for (int j = Math.max(0, i - 3); j < Math.min(mOutPtsList.size(), i + 3); j++) {
                    if (j == 0) {
                        mErrorLogs.append(String.format(Locale.getDefault(),
                                "pts of frame idx -1 is %d \n", lastPts));
                    }
                    mErrorLogs.append(String.format(Locale.getDefault(),
                            "pts of frame idx %d is %d \n", j, mOutPtsList.get(j)));
                }
                res = false;
                break;
            }
        }
        return res;
    }

    static boolean arePtsListsIdentical(ArrayList<Long> refList, ArrayList<Long> testList,
            StringBuilder msg) {
        boolean res = true;
        if (refList.size() != testList.size()) {
            msg.append("Reference and test timestamps list sizes are not identical \n");
            msg.append(String.format(Locale.getDefault(), "reference pts list size is %d \n",
                    refList.size()));
            msg.append(String.format(Locale.getDefault(), "test pts list size is %d \n",
                    testList.size()));
            res = false;
        }
        for (int i = 0; i < Math.min(refList.size(), testList.size()); i++) {
            if (!Objects.equals(refList.get(i), testList.get(i))) {
                msg.append(String.format(Locale.getDefault(),
                        "Frame idx %d, ref pts %dus, test pts %dus \n", i, refList.get(i),
                        testList.get(i)));
                res = false;
            }
        }
        if (refList.size() < testList.size()) {
            for (int i = refList.size(); i < testList.size(); i++) {
                msg.append(String.format(Locale.getDefault(),
                        "Frame idx %d, ref pts EMPTY, test pts %dus \n", i, testList.get(i)));
            }
        } else if (refList.size() > testList.size()) {
            for (int i = testList.size(); i < refList.size(); i++) {
                msg.append(String.format(Locale.getDefault(),
                        "Frame idx %d, ref pts %dus, test pts EMPTY \n", i, refList.get(i)));
            }
        }
        if (!res) {
            msg.append("Are frames for which timestamps differ between reference and test. \n");
        }
        return res;
    }

    public boolean isOutPtsListIdenticalToInpPtsList(boolean requireSorting) {
        ArrayList<Long> inpPtsListCopy = new ArrayList<>(mInpPtsList);
        Collections.sort(inpPtsListCopy);
        if (requireSorting) {
            ArrayList<Long> outPtsListCopy = new ArrayList<>(mOutPtsList);
            Collections.sort(outPtsListCopy);
            return arePtsListsIdentical(inpPtsListCopy, outPtsListCopy, mErrorLogs);
        }
        return arePtsListsIdentical(inpPtsListCopy, mOutPtsList, mErrorLogs);
    }

    public int getOutStreamSize() {
        return mMemIndex;
    }

    public void checksum(ByteBuffer buf, int size) {
        checksum(buf, size, 0, 0, 0, 0);
    }

    public void checksum(ByteBuffer buf, MediaCodec.BufferInfo info) {
        int pos = buf.position();
        buf.position(info.offset);
        checksum(buf, info.size, 0, 0, 0, 0);
        buf.position(pos);
    }

    public void checksum(ByteBuffer buf, int size, int width, int height, int stride,
            int bytesPerSample) {
        int cap = buf.capacity();
        assertTrue("checksum() params are invalid: size = " + size + " cap = " + cap,
                size > 0 && size <= cap);
        if (buf.hasArray()) {
            if (width > 0 && height > 0 && stride > 0 && bytesPerSample > 0) {
                int offset = buf.position() + buf.arrayOffset();
                byte[] bb = new byte[width * height * bytesPerSample];
                for (int i = 0; i < height; ++i) {
                    System.arraycopy(buf.array(), offset, bb, i * width * bytesPerSample,
                            width * bytesPerSample);
                    offset += stride;
                }
                mCrc32UsingBuffer.update(bb, 0, width * height * bytesPerSample);
                if (ENABLE_DUMP) {
                    dumpY(bb, 0, width * height * bytesPerSample);
                }
            } else {
                mCrc32UsingBuffer.update(buf.array(), buf.position() + buf.arrayOffset(), size);
            }
        } else if (width > 0 && height > 0 && stride > 0 && bytesPerSample > 0) {
            // Checksum only the Y plane
            int pos = buf.position();
            byte[] bb = new byte[width * height * bytesPerSample];
            // we parallelize these copies from non-array buffers because it yields 60% speedup on
            // 4 core systems. On 4k images, this means 4k frame checksums go from 200 to 80
            // milliseconds, and this allows some of our 4k video tests to run in 4 minutes,
            // bringing it under the 10 minutes limit imposed by the test infrastructure.
            IntStream.range(0, height).parallel().forEach(i -> {
                int offset = pos + stride * i;
                // Creating a duplicate as Bytebuffer.position() is not threadsafe and the
                // duplication does not copy the content.
                ByteBuffer dup = buf.asReadOnlyBuffer();
                dup.position(offset);
                dup.get(bb, i * width * bytesPerSample, width * bytesPerSample);
            });
            mCrc32UsingBuffer.update(bb, 0, width * height * bytesPerSample);
            if (ENABLE_DUMP) {
                dumpY(bb, 0, width * height * bytesPerSample);
            }
            buf.position(pos);
        } else {
            int pos = buf.position();
            final int rdsize = Math.min(4096, size);
            byte[] bb = new byte[rdsize];
            int chk;
            for (int i = 0; i < size; i += chk) {
                chk = Math.min(rdsize, size - i);
                buf.get(bb, 0, chk);
                mCrc32UsingBuffer.update(bb, 0, chk);
            }
            buf.position(pos);
        }
    }

    public void checksum(Image image) {
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
            int width, height, rowStride, pixelStride, left, top;
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

            if (buf.hasArray()) {
                byte[] b = buf.array();
                int offs = buf.arrayOffset() + cropOffset;
                if (pixelStride == bytesPerSample) {
                    for (int y = 0; y < height; ++y) {
                        System.arraycopy(b, offs + y * rowStride, bb, y * width * bytesPerSample,
                                width * bytesPerSample);
                    }
                } else {
                    // do it pixel-by-pixel
                    for (int y = 0; y < height; ++y) {
                        int lineOffset = offs + y * rowStride;
                        for (int x = 0; x < width; ++x) {
                            for (int bytePos = 0; bytePos < bytesPerSample; ++bytePos) {
                                bb[y * width * bytesPerSample + x * bytesPerSample + bytePos] =
                                        b[lineOffset + x * pixelStride + bytePos];
                            }
                        }
                    }
                }
            } else { // almost always ends up here due to direct buffers
                int base = buf.position();
                int pos = base + cropOffset;
                // we parallelize these copies from non-array buffers because it yields 60% speedup on
                // 4 core systems. On 4k images, this means 4k frame checksums go from 200 to 80
                // milliseconds, and this allows some of our 4k video tests to run in 4 minutes,
                // bringing it under the 10 minutes limit imposed by the test infrastructure.
                if (pixelStride == bytesPerSample) {
                    IntStream.range(0, height).parallel().forEach(y -> {
                        // Creating a duplicate as Bytebuffer.position() is not threadsafe and the
                        // duplication does not copy the content.
                        ByteBuffer dup = buf.asReadOnlyBuffer();
                        dup.position(pos + y * rowStride);
                        dup.get(bb, y * width * bytesPerSample, width * bytesPerSample);
                    });
                } else {
                    IntStream.range(0, height).parallel().forEach(y -> {
                        byte[] lb = new byte[rowStride];
                        // Creating a duplicate as Bytebuffer.position() is not threadsafe and the
                        // duplication does not copy the content.
                        ByteBuffer dup = buf.asReadOnlyBuffer();
                        dup.position(pos + y * rowStride);
                        // we're only guaranteed to have pixelStride * (width - 1) +
                        // bytesPerSample bytes
                        dup.get(lb, 0, pixelStride * (width - 1) + bytesPerSample);
                        for (int x = 0; x < width; ++x) {
                            for (int bytePos = 0; bytePos < bytesPerSample; ++bytePos) {
                                bb[y * width * bytesPerSample + x * bytesPerSample + bytePos] =
                                        lb[x * pixelStride + bytePos];
                            }
                        }
                    });
                }
                buf.position(base);
            }
            mCrc32UsingImage.update(bb, 0, width * height * bytesPerSample);
            if (ENABLE_DUMP) {
                dumpYuv(bb, 0, width * height * bytesPerSample);
            }
        }
    }

    public void saveToMemory(ByteBuffer buf, MediaCodec.BufferInfo info) {
        if (mMemIndex + info.size >= mMemory.length) {
            mMemory = Arrays.copyOf(mMemory, mMemIndex + info.size);
        }
        int base = buf.position();
        buf.position(info.offset);
        buf.get(mMemory, mMemIndex, info.size);
        mMemIndex += info.size;
        buf.position(base);
    }

    void position(int index) {
        if (index < 0 || index >= mMemory.length) index = 0;
        mMemIndex = index;
    }

    public ByteBuffer getBuffer() {
        return ByteBuffer.wrap(mMemory);
    }

    public StringBuilder getSharedErrorLogs() {
        return mSharedErrorLogs;
    }

    public void reset() {
        position(0);
        mCrc32UsingImage.reset();
        mCrc32UsingBuffer.reset();
        mInpPtsList.clear();
        mOutPtsList.clear();
        mSharedErrorLogs.setLength(0);
        mErrorLogs.setLength(0);
        mErrorLogs.append("##################       Error Details         ####################\n");
        cleanUp();
    }

    public void cleanUp() {
        if (mOutFileYuv != null && mOutFileYuv.exists()) mOutFileYuv.delete();
        mOutFileYuv = null;
        mAppendToYuvFile = false;
        if (mOutFileY != null && mOutFileY.exists()) mOutFileY.delete();
        mOutFileY = null;
        mAppendToYFile = false;
        if (mOutFileDefault != null && mOutFileDefault.exists()) mOutFileDefault.delete();
        mOutFileDefault = null;
    }

    public float getRmsError(Object refObject, int audioFormat) {
        double totalErrorSquared = 0;
        double avgErrorSquared;
        int bytesPerSample = AudioFormat.getBytesPerSample(audioFormat);
        if (refObject instanceof float[]) {
            if (audioFormat != AudioFormat.ENCODING_PCM_FLOAT) return Float.MAX_VALUE;
            float[] refData = (float[]) refObject;
            if (refData.length != mMemIndex / bytesPerSample) return Float.MAX_VALUE;
            float[] floatData = new float[refData.length];
            ByteBuffer.wrap(mMemory, 0, mMemIndex).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                    .get(floatData);
            for (int i = 0; i < refData.length; i++) {
                float d = floatData[i] - refData[i];
                totalErrorSquared += d * d;
            }
            avgErrorSquared = (totalErrorSquared / refData.length);
        } else if (refObject instanceof int[]) {
            int[] refData = (int[]) refObject;
            int[] intData;
            if (audioFormat == AudioFormat.ENCODING_PCM_24BIT_PACKED) {
                if (refData.length != (mMemIndex / bytesPerSample)) return Float.MAX_VALUE;
                intData = new int[refData.length];
                for (int i = 0, j = 0; i < mMemIndex; i += 3, j++) {
                    intData[j] = mMemory[j] | (mMemory[j + 1] << 8) | (mMemory[j + 2] << 16);
                }
            } else if (audioFormat == AudioFormat.ENCODING_PCM_32BIT) {
                if (refData.length != mMemIndex / bytesPerSample) return Float.MAX_VALUE;
                intData = new int[refData.length];
                ByteBuffer.wrap(mMemory, 0, mMemIndex).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer()
                        .get(intData);
            } else {
                return Float.MAX_VALUE;
            }
            for (int i = 0; i < intData.length; i++) {
                float d = intData[i] - refData[i];
                totalErrorSquared += d * d;
            }
            avgErrorSquared = (totalErrorSquared / refData.length);
        } else if (refObject instanceof short[]) {
            short[] refData = (short[]) refObject;
            if (refData.length != mMemIndex / bytesPerSample) return Float.MAX_VALUE;
            if (audioFormat != AudioFormat.ENCODING_PCM_16BIT) return Float.MAX_VALUE;
            short[] shortData = new short[refData.length];
            ByteBuffer.wrap(mMemory, 0, mMemIndex).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    .get(shortData);
            for (int i = 0; i < shortData.length; i++) {
                float d = shortData[i] - refData[i];
                totalErrorSquared += d * d;
            }
            avgErrorSquared = (totalErrorSquared / refData.length);
        } else if (refObject instanceof byte[]) {
            byte[] refData = (byte[]) refObject;
            if (refData.length != mMemIndex / bytesPerSample) return Float.MAX_VALUE;
            if (audioFormat != AudioFormat.ENCODING_PCM_8BIT) return Float.MAX_VALUE;
            byte[] byteData = new byte[refData.length];
            ByteBuffer.wrap(mMemory, 0, mMemIndex).get(byteData);
            for (int i = 0; i < byteData.length; i++) {
                float d = byteData[i] - refData[i];
                totalErrorSquared += d * d;
            }
            avgErrorSquared = (totalErrorSquared / refData.length);
        } else {
            return Float.MAX_VALUE;
        }
        return (float) Math.sqrt(avgErrorSquared);
    }

    public long getCheckSumImage() {
        return mCrc32UsingImage.getValue();
    }

    public long getCheckSumBuffer() {
        return mCrc32UsingBuffer.getValue();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!this.equalsPtsList(o)) return false;
        if (!this.equalsDequeuedOutput(o)) return false;
        return true;
    }

    public boolean equalsPtsList(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OutputManager that = (OutputManager) o;
        return arePtsListsIdentical(mOutPtsList, that.mOutPtsList, mSharedErrorLogs);
    }

    public boolean equalsByteOutput(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OutputManager that = (OutputManager) o;
        boolean isEqual = true;
        if (mMemIndex == that.mMemIndex) {
            if (!Arrays.equals(mMemory, that.mMemory)) {
                isEqual = false;
                int count = 0;
                StringBuilder msg = new StringBuilder();
                for (int i = 0; i < mMemIndex; i++) {
                    if (mMemory[i] != that.mMemory[i]) {
                        count++;
                        msg.append(String.format(Locale.getDefault(),
                                "At offset %d, ref buffer val is %x and test buffer val is %x \n",
                                i, mMemory[i], that.mMemory[i]));
                        if (count == 20) {
                            msg.append("stopping after 20 mismatches, ...\n");
                            break;
                        }
                    }
                }
                if (count != 0) {
                    mSharedErrorLogs.append("Ref and Test outputs are not identical \n");
                    mSharedErrorLogs.append(msg);
                }
            }
        } else {
            isEqual = false;
            mSharedErrorLogs.append("ref and test output sizes are not identical \n");
            mSharedErrorLogs.append(
                    String.format(Locale.getDefault(), "Ref output buffer size %d \n", mMemIndex));
            mSharedErrorLogs.append(
                    String.format(Locale.getDefault(), "Test output buffer size %d \n",
                            that.mMemIndex));
        }
        return isEqual;
    }

    // TODO: Timestamps for deinterlaced content are under review. (E.g. can decoders
    // produce multiple progressive frames?) For now, do not verify timestamps.
    public boolean equalsDequeuedOutput(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OutputManager that = (OutputManager) o;
        boolean isEqual = true;
        if (mCrc32UsingImage.getValue() != that.mCrc32UsingImage.getValue()) {
            isEqual = false;
            mSharedErrorLogs.append("CRC32 checksums computed for image buffers received from "
                    + "getOutputImage() do not match between ref and test runs. \n");
            mSharedErrorLogs.append(String.format(Locale.getDefault(),
                    "Ref CRC32 checksum value is %d \n", mCrc32UsingImage.getValue()));
            mSharedErrorLogs.append(String.format(Locale.getDefault(),
                    "Test CRC32 checksum value is %d \n", that.mCrc32UsingImage.getValue()));
            if (ENABLE_DUMP) {
                mSharedErrorLogs.append(String.format(Locale.getDefault(),
                        "Decoded Ref YUV file is at : %s \n", mOutFileYuv.getAbsolutePath()));
                mSharedErrorLogs.append(String.format(Locale.getDefault(),
                        "Decoded Test YUV file is at : %s \n", that.mOutFileYuv.getAbsolutePath()));
            } else {
                mSharedErrorLogs.append("As the reference YUV and test YUV are different, try "
                        + "re-running the test by changing ENABLE_DUMP of OutputManager class to "
                        + "'true' to dump the decoded YUVs for further analysis. \n");
            }
        }
        if (mCrc32UsingBuffer.getValue() != that.mCrc32UsingBuffer.getValue()) {
            isEqual = false;
            mSharedErrorLogs.append("CRC32 checksums computed for byte buffers received from "
                    + "getOutputBuffer() do not match between ref and test runs. \n");
            mSharedErrorLogs.append(String.format(Locale.getDefault(),
                    "Ref CRC32 checksum value is %d \n", mCrc32UsingBuffer.getValue()));
            mSharedErrorLogs.append(String.format(Locale.getDefault(),
                    "Test CRC32 checksum value is %d \n", that.mCrc32UsingBuffer.getValue()));
            if (ENABLE_DUMP) {
                if (mOutFileY != null) {
                    mSharedErrorLogs.append(String.format(Locale.getDefault(),
                            "Decoded Ref Y file is at : %s \n", mOutFileY.getAbsolutePath()));
                }
                if (that.mOutFileY != null) {
                    mSharedErrorLogs.append(String.format(Locale.getDefault(),
                            "Decoded Test Y file is at : %s \n", that.mOutFileY.getAbsolutePath()));
                }
                if (mMemIndex > 0) {
                    mSharedErrorLogs.append(String.format(Locale.getDefault(),
                            "Output Ref ByteBuffer is dumped at : %s \n", dumpBuffer()));
                }
                if (that.mMemIndex > 0) {
                    mSharedErrorLogs.append(String.format(Locale.getDefault(),
                            "Output Test ByteBuffer is dumped at : %s \n", that.dumpBuffer()));
                }
            } else {
                mSharedErrorLogs.append("As the output of the component is not consistent, try "
                        + "re-running the test by changing ENABLE_DUMP of OutputManager class to "
                        + "'true' to dump the outputs for further analysis. \n");
            }
        }
        if (!equalsByteOutput(that)) {
            isEqual = false;
        }
        return isEqual;
    }

    public String getErrMsg() {
        return (mErrorLogs.toString() + mSharedErrorLogs.toString());
    }

    public void dumpYuv(byte[] mem, int offset, int size) {
        try {
            if (mOutFileYuv == null) {
                mOutFileYuv = File.createTempFile(LOG_TAG + "YUV", ".bin");
                mOutFileYuv.deleteOnExit();
            }
            try (FileOutputStream outputStream = new FileOutputStream(mOutFileYuv,
                    mAppendToYuvFile)) {
                outputStream.write(mem, offset, size);
                mAppendToYuvFile = true;
            }
        } catch (Exception e) {
            fail("Encountered IOException during output image write. Exception is" + e);
        }
    }

    public void dumpY(byte[] mem, int offset, int size) {
        try {
            if (mOutFileY == null) {
                mOutFileY = File.createTempFile(LOG_TAG + "Y", ".bin");
                mOutFileY.deleteOnExit();
            }
            try (FileOutputStream outputStream = new FileOutputStream(mOutFileY, mAppendToYFile)) {
                outputStream.write(mem, offset, size);
                mAppendToYFile = true;
            }
        } catch (Exception e) {
            fail("Encountered IOException during output image write. Exception is" + e);
        }
    }

    public String dumpBuffer() {
        if (ENABLE_DUMP) {
            try {
                if (mOutFileDefault == null) {
                    mOutFileDefault = File.createTempFile(LOG_TAG + "OUT", ".bin");
                    mOutFileDefault.deleteOnExit();
                }
                try (FileOutputStream outputStream = new FileOutputStream(mOutFileDefault)) {
                    outputStream.write(mMemory, 0, mMemIndex);
                }
            } catch (Exception e) {
                fail("Encountered IOException during output buffer write. Exception is" + e);
            }
            return mOutFileDefault.getAbsolutePath();
        }
        return "file not dumped yet, re-run the test by changing ENABLE_DUMP of OutputManager "
                + "class to 'true' to dump the buffer";
    }

    public String getOutYuvFileName() {
        return (mOutFileYuv != null) ? mOutFileYuv.getAbsolutePath() : null;
    }
}
