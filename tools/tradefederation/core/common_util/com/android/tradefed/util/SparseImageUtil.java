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

package com.android.tradefed.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Utility to unsparse sparse images.
 *
 * <p>This piece of code is adopted from:
 * frameworks/base/packages/DynamicSystemInstallationService/src/com/android/dynsystem/SparseInputStream.java
 */
public class SparseImageUtil {
    private static final int SPARSE_IMAGE_MAGIC = 0xED26FF3A;

    /**
     * Tests if file is a sparse image.
     *
     * @param imgFile a {@link File} that is to be tested.
     * @return true if imgFile is a sparse image.
     */
    public static boolean isSparse(File imgFile) {
        if (!imgFile.isFile()) {
            return false;
        }
        try (FileInputStream in = new FileInputStream(imgFile)) {
            // Check magic bytes
            return readBuffer(in, 4).getInt() == SPARSE_IMAGE_MAGIC;
        } catch (IOException e) {
            // Return false if failed to read file
            return false;
        }
    }

    /**
     * Unsparses a sparse image file.
     *
     * @param imgFile a {@link File} that is a sparse image.
     * @param destFile a {@link File} to write the unsparsed image to.
     * @throws IOException if imgFile is not a sparse image.
     */
    public static void unsparse(File imgFile, File destFile) throws IOException {
        try (FileInputStream in = new FileInputStream(imgFile)) {
            SparseInputStream sis =
                    new SparseInputStream(new BufferedInputStream(in), imgFile.length());
            if (!sis.isSparse()) {
                throw new IOException("Not a sparse image: " + imgFile);
            }
            FileUtil.writeToFile(sis, destFile);
        }
    }

    /** Reads exact number of bytes. */
    private static byte[] readFully(InputStream in, int size) throws IOException {
        byte[] buf = new byte[size];
        int n = 0;
        int off = 0;
        int left = size;
        while (left > 0) {
            n = in.read(buf, off, left);
            if (n < 0) {
                throw new IOException("Unexpected EOF in readFully()");
            }
            off += n;
            left -= n;
        }
        return buf;
    }

    /** Helper that wraps result of readFully() in a ByteBuffer for easy consumption. */
    private static ByteBuffer readBuffer(InputStream in, int size) throws IOException {
        return ByteBuffer.wrap(readFully(in, size)).order(ByteOrder.LITTLE_ENDIAN);
    }

    /**
     * SparseInputStream read from upstream and detects the data format. If the upstream is a valid
     * sparse data, it will unsparse it on the fly. Otherwise, it just passthrough as is.
     */
    public static class SparseInputStream extends InputStream {
        private static final int FILE_HDR_SIZE = 28;
        private static final int CHUNK_HDR_SIZE = 12;

        /**
         * This class represents a chunk in the Android sparse image.
         *
         * @see system/core/libsparse/sparse_format.h
         */
        private static class SparseChunk {
            public static final short RAW = (short) 0xCAC1;
            public static final short FILL = (short) 0xCAC2;
            public static final short DONTCARE = (short) 0xCAC3;
            public short mChunkType;
            public int mChunkSize;
            public int mTotalSize;
            public byte[] mFill;

            @Override
            public String toString() {
                return String.format(
                        "type: %x, chunk_size: %d, total_size: %d",
                        mChunkType, mChunkSize, mTotalSize);
            }

            public static SparseChunk readChunk(InputStream in) throws IOException {
                SparseChunk chunk = new SparseChunk();
                ByteBuffer buf = readBuffer(in, CHUNK_HDR_SIZE);
                chunk.mChunkType = buf.getShort();
                /* padding = */ buf.getShort();
                chunk.mChunkSize = buf.getInt();
                chunk.mTotalSize = buf.getInt();
                if (chunk.mChunkType == FILL) {
                    chunk.mFill = readFully(in, 4);
                }
                return chunk;
            }
        }

        private BufferedInputStream mIn;
        private boolean mIsSparse;
        private long mOriginalSize;
        private long mBlockSize;
        private long mTotalBlocks;
        private long mTotalChunks;
        private SparseChunk mCur;
        private long mLeft;
        private int mCurChunks;

        public SparseInputStream(BufferedInputStream in, long originalSize) throws IOException {
            mIn = in;
            mOriginalSize = originalSize;
            in.mark(FILE_HDR_SIZE * 2);
            ByteBuffer buf = null;
            try {
                buf = readBuffer(mIn, FILE_HDR_SIZE);
                mIsSparse = (buf.getInt() == SPARSE_IMAGE_MAGIC);
            } catch (IOException e) {
                mIsSparse = false;
            }
            if (!mIsSparse) {
                mIn.reset();
                return;
            }
            int major = buf.getShort();
            int minor = buf.getShort();
            if (major > 0x1 || minor > 0x0) {
                throw new IOException("Unsupported sparse version: " + major + "." + minor);
            }
            if (buf.getShort() != FILE_HDR_SIZE) {
                throw new IOException("Illegal file header size");
            }
            if (buf.getShort() != CHUNK_HDR_SIZE) {
                throw new IOException("Illegal chunk header size");
            }
            mBlockSize = buf.getInt();
            if ((mBlockSize & 0x3) != 0) {
                throw new IOException("Illegal block size, must be a multiple of 4: " + mBlockSize);
            }
            mTotalBlocks = buf.getInt();
            mTotalChunks = buf.getInt();
            mLeft = 0;
            mCurChunks = 0;
        }

        /**
         * Check if it needs to open a new chunk.
         *
         * @return true if it's EOF
         */
        private boolean prepareChunk() throws IOException {
            if (mCur == null || mLeft <= 0) {
                if (++mCurChunks > mTotalChunks) {
                    return true;
                }
                mCur = SparseChunk.readChunk(mIn);
                mLeft = mCur.mChunkSize * mBlockSize;
            }
            return mLeft == 0;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            if (!mIsSparse) {
                return mIn.read(buf, off, len);
            }
            if (prepareChunk()) {
                return -1;
            }
            int n = -1;
            switch (mCur.mChunkType) {
                case SparseChunk.RAW:
                    n = mIn.read(buf, off, (int) Math.min(mLeft, len));
                    mLeft -= n;
                    break;
                case SparseChunk.DONTCARE:
                    n = (int) Math.min(mLeft, len);
                    Arrays.fill(buf, off, off + n, (byte) 0);
                    mLeft -= n;
                    break;
                case SparseChunk.FILL:
                    // The FILL type is rarely used, so use a simple implementation.
                    n = super.read(buf, off, len);
                    break;
                default:
                    throw new IOException("Unsupported Chunk:" + mCur);
            }
            return n;
        }

        @Override
        public int read() throws IOException {
            if (!mIsSparse) {
                return mIn.read();
            }
            if (prepareChunk()) {
                return -1;
            }
            int ret = -1;
            switch (mCur.mChunkType) {
                case SparseChunk.RAW:
                    ret = mIn.read();
                    break;
                case SparseChunk.DONTCARE:
                    ret = 0;
                    break;
                case SparseChunk.FILL:
                    ret = Byte.toUnsignedInt(mCur.mFill[(4 - ((int) mLeft & 0x3)) & 0x3]);
                    break;
                default:
                    throw new IOException("Unsupported Chunk:" + mCur);
            }
            mLeft--;
            return ret;
        }

        @Override
        public void close() throws IOException {
            StreamUtil.close(mIn);
        }

        /**
         * Return the total number of bytes in the unsparsed image.
         *
         * @return mOriginalSize if underlying stream is already non-sparse.
         */
        public long size() {
            if (!mIsSparse) {
                return mOriginalSize;
            }
            return mBlockSize * mTotalBlocks;
        }

        public boolean isSparse() {
            return mIsSparse;
        }
    }
}
