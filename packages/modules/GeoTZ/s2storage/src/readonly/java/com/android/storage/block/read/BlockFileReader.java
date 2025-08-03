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

package com.android.storage.block.read;

import com.android.storage.io.read.TypedInputStream;
import com.android.storage.util.Visitor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

/**
 * The entry point for reading a generic block file. This class is not thread-safe.
 */
public final class BlockFileReader implements AutoCloseable {

    private static final ByteBuffer EMPTY_BYTE_BUFFER = ByteBuffer.allocate(0).asReadOnlyBuffer();

    private Character mRequiredMagic;

    private Integer mRequiredMinVersion;

    private final boolean mMemoryMapBlocks;

    private char mMagic;

    private int mVersion;

    private BlockInfo[] mBlockInfos;

    private FileChannel mFileChannel;

    private BlockFileReader(boolean memoryMapBlocks) {
        mMemoryMapBlocks = memoryMapBlocks;
    }

    private void setRequiredMagic(char magic) {
        mRequiredMagic = magic;
    }

    private void setRequiredMinVersion(int requiredMinVersion) {
        mRequiredMinVersion = requiredMinVersion;
    }

    /**
     * Opens a block file. To open successfully the block file must have the expected 16-bit "magic"
     * value and a version &gt;= {@code minVersion}.
     *
     * @param memoryMapBlocks whether to read blocks via memory mapping, or reading the whole block
     *         into memory
     * @param requiredMagic the expected file magic
     * @param requiredMinVersion the required minimum file version
     */
    public static BlockFileReader open(boolean memoryMapBlocks, File file,
            char requiredMagic, int requiredMinVersion) throws IOException {
        BlockFileReader reader = new BlockFileReader(memoryMapBlocks);
        reader.setRequiredMagic(requiredMagic);
        reader.setRequiredMinVersion(requiredMinVersion);
        reader.open(file);
        return reader;
    }

    /**
     * Opens a block file.
     *
     * @param memoryMapBlocks whether to read blocks via memory mapping, or reading the whole block
     *         into memory
     */
    public static BlockFileReader open(boolean memoryMapBlocks, File file) throws IOException {
        BlockFileReader reader = new BlockFileReader(memoryMapBlocks);
        reader.open(file);
        return reader;
    }

    private void open(File file) throws IOException {
        try (TypedInputStream tis = new TypedInputStream(new FileInputStream(file))) {
            mMagic = tis.readChar();
            if (mRequiredMagic != null && mMagic != mRequiredMagic) {
                throw new IOException("Bad magic: expected " + Integer.toHexString(this.mMagic)
                        + " but was " + Integer.toHexString(mMagic));
            }

            mVersion = tis.readInt();
            if (mRequiredMinVersion != null && mVersion < mRequiredMinVersion) {
                throw new IOException("Bad version: " + mVersion
                        + ", reader requires at least " + mRequiredMinVersion);
            }

            // 10-bytes left for expansion.
            tis.skipBytes(10);

            int blockCount = tis.readInt();
            mBlockInfos = new BlockInfo[blockCount];
            for (int i = 0; i < blockCount; i++) {
                int blockInfoSize = tis.readInt();
                final int minimumBlockInfoSize =
                        (2 * Integer.BYTES) + (2 * Long.BYTES) + Byte.BYTES;
                if (blockInfoSize < minimumBlockInfoSize) {
                    throw new IOException("Minimum block info size:" + minimumBlockInfoSize);
                }

                int blockType = tis.readInt();
                long blockStartByteOffset = tis.readLong();
                long blockDataSizeBytes = tis.readLong();
                byte[] extraBytes = tis.readTinyVarByteArray();
                mBlockInfos[i] = new BlockInfo(i, blockType, blockStartByteOffset,
                        blockDataSizeBytes, extraBytes);
            }
        }
        mFileChannel = FileChannel.open(file.toPath());
    }

    /**
     * Closes the block file.
     */
    public void close() throws IOException {
        mFileChannel.close();
    }

    /** Returns information from the file's header about the block with the specified ID. */
    public BlockInfo getBlockInfo(int blockId) {
        checkFileOpen();
        return mBlockInfos[blockId];
    }

    /** Returns the block with the specified ID. */
    public Block getBlock(int blockId) throws IOException {
        checkFileOpen();

        BlockInfo blockInfo = mBlockInfos[blockId];
        if (blockInfo.getBlockSizeBytes() == 0) {
            return new Block(blockId, blockInfo.getType(), EMPTY_BYTE_BUFFER);
        }

        ByteBuffer allBlockBuffer;
        if (mMemoryMapBlocks) {
            // Map the entire block, including the block header.
            allBlockBuffer = mFileChannel.map(
                    FileChannel.MapMode.READ_ONLY,
                    blockInfo.getBlockStartByteOffset(),
                    blockInfo.getBlockSizeBytes());
            if (allBlockBuffer.order() != ByteOrder.BIG_ENDIAN) {
                throw new IllegalStateException("Byte order must be BIG_ENDIAN");
            }
        } else {
            // No memory map version

            // Not thread safe because of the read call.
            long blockSizeBytesLong = blockInfo.getBlockSizeBytes();
            if (blockSizeBytesLong > Integer.MAX_VALUE) {
                throw new IOException("Block too large to read into memory. Try mapping instead.");
            }
            int blockSizeBytes = (int) blockSizeBytesLong;
            allBlockBuffer = ByteBuffer.allocate(blockSizeBytes);
            mFileChannel.position(blockInfo.getBlockStartByteOffset());
            int bytesRead = mFileChannel.read(allBlockBuffer);
            if (bytesRead != blockSizeBytes) {
                throw new IllegalStateException("Unable to read " + blockSizeBytes
                        + ", only read" + bytesRead);
            }
            allBlockBuffer.rewind();
            allBlockBuffer = allBlockBuffer.asReadOnlyBuffer();
        }

        // Read the block header.
        // This information is redundant, but serves as a check that nothing is wrong with reading
        // or writing files.
        int actualId = allBlockBuffer.getInt();
        if (actualId != blockId) {
            throw new IllegalStateException("Expected id=" + blockId + ", but was " + actualId);
        }
        int actualType = allBlockBuffer.getInt();
        if (actualType != blockInfo.getType()) {
            throw new IllegalStateException(
                    "Expected type=" + blockInfo.getType() + ", but was " + actualType);
        }

        // The part of the block that holds the data.
        ByteBuffer blockDataBytes = allBlockBuffer.slice();
        return new Block(actualId, actualType, blockDataBytes);
    }

    /** Returns the number of blocks in the file. */
    public int getBlockCount() {
        checkFileOpen();

        return mBlockInfos.length;
    }

    /** A {@link Visitor} for the {@link BlockFileReader}. See {@link #visit} */
    public interface BlockFileVisitor extends Visitor {

        /** Called after {@link #begin()}, once. */
        void visitFileHeader(char magic, int version, int blockCount) throws VisitException;

        /** Called after {@link #visitFileHeader}, once for each block info in the file. */
        void visitBlockInfo(BlockInfo blockInfo) throws VisitException;

        /**
         * Called after {@link #visitBlockInfo(BlockInfo)} has been called for all block infos,
         * once for each block.
         */
        void visitBlock(Block block) throws VisitException;
    }

    /**
     * Issues callbacks to the supplied {@link BlockFileVisitor} containing information from the
     * block file.
     */
    public void visit(BlockFileVisitor visitor) throws Visitor.VisitException {
        checkFileOpen();

        try {
            visitor.begin();
            visitor.visitFileHeader(mMagic, mVersion, mBlockInfos.length);
            for (int i = 0; i < mBlockInfos.length; i++) {
                visitor.visitBlockInfo(mBlockInfos[i]);
            }
            try {
                for (int i = 0; i < getBlockCount(); i++) {
                    visitor.visitBlock(getBlock(i));
                }
            } catch (IOException e) {
                throw new Visitor.VisitException(e);
            }
        } finally {
            visitor.end();
        }
    }

    private void checkFileOpen() {
        if (!mFileChannel.isOpen()) {
            throw new IllegalStateException("BlockFile is closed.");
        }
    }
}
