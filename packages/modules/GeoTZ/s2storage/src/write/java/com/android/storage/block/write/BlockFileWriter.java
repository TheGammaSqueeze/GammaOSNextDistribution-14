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

package com.android.storage.block.write;

import com.android.storage.block.read.BlockData;
import com.android.storage.block.read.BlockInfo;
import com.android.storage.io.write.TypedOutputStream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Writes a block file. */
public final class BlockFileWriter implements AutoCloseable {

    private final char mMagic;

    private final int mVersion;

    private final List<BlockInfo> mBlockInfos = new ArrayList<>();

    private File mOutputFile;

    private File mTempFile;

    private TypedOutputStream mTempFileOutputStream;

    private long mCurrentBlockByteCount = 0;

    private int mBlockInfoExtraBytesCount;

    private BlockFileWriter(char magic, int version) {
        mMagic = magic;
        mVersion = version;
    }

    /**
     * Creates a {@link BlockFileWriter} ready for adding block to. The block file is created with
     * the specified magic and version.
     */
    public static BlockFileWriter open(char magic, int version, File file) throws IOException {
        BlockFileWriter writer = new BlockFileWriter(magic, version);
        writer.open(file);
        return writer;
    }

    private void open(File file) throws IOException {
        mOutputFile = file;
        mTempFile = File.createTempFile("block", "temp");
        mTempFileOutputStream = new TypedOutputStream(new FileOutputStream(mTempFile));
    }

    /**
     * Add the supplied block info / block to the block file. Blocks written in order and assigned
     * their ID based on their position.
     */
    public void addBlock(int blockType, byte[] blockInfoExtraBytes, BlockData blockData)
            throws IOException {
        Objects.requireNonNull(blockInfoExtraBytes);
        Objects.requireNonNull(blockData);

        int blockId = mBlockInfos.size();
        long startOfBlock = mCurrentBlockByteCount;

        // Don't write anything for empty blocks.
        if (blockData.getSize() != 0) {
            // Write the block's header.
            mTempFileOutputStream.writeInt(blockId);
            mCurrentBlockByteCount += Integer.BYTES;
            mTempFileOutputStream.writeInt(blockType);
            mCurrentBlockByteCount += Integer.BYTES;

            // Write the block's data.
            mCurrentBlockByteCount += copyAll(blockData, mTempFileOutputStream);
        }
        long endOfBlock = mCurrentBlockByteCount;
        long blockSizeBytes = endOfBlock - startOfBlock;
        BlockInfo blockInfo = new BlockInfo(
                blockId, blockType, startOfBlock, blockSizeBytes, blockInfoExtraBytes);
        mBlockInfoExtraBytesCount += blockInfoExtraBytes.length;
        mBlockInfos.add(blockInfo);
    }

    /** Finishes writing the file. Signals no more blocks are to be added. */
    @Override
    public void close() throws IOException {
        mTempFileOutputStream.close();
        writeHeaderAndAppendTempFile();
    }

    private void writeHeaderAndAppendTempFile() throws IOException {
        try (TypedOutputStream bos = new TypedOutputStream(new FileOutputStream(mOutputFile))) {
            // File header
            bos.writeChar(mMagic);
            bos.writeInt(mVersion);
            bos.writeBytes(new byte[10]); // Reserved
            bos.writeInt(mBlockInfos.size());

            int blockInfoLengthWithoutExtraBytes =
                    Integer.BYTES /* blockInfoSize */
                            + Integer.BYTES /* blockType */
                            + Long.BYTES /* blockOffset */
                            + Long.BYTES /* blockSizeBytes */
                            + Byte.BYTES /* extraBytesLength */;
            int headerBytesAdjustment =
                    Character.BYTES /* magic */
                            + Integer.BYTES /* version */
                            + (10 * Byte.BYTES) /* reserved */
                            + Integer.BYTES /* blockInfos.size() */
                            + (mBlockInfos.size() * blockInfoLengthWithoutExtraBytes)
                            + mBlockInfoExtraBytesCount;

            // Write block info to the TypedOutputStream.
            for (BlockInfo blockInfo : mBlockInfos) {
                byte[] extraBytes = blockInfo.getExtraBytes();
                int blockInfoSize = blockInfoLengthWithoutExtraBytes + extraBytes.length;
                bos.writeInt(blockInfoSize);
                bos.writeInt(blockInfo.getType());
                bos.writeLong(blockInfo.getBlockStartByteOffset() + headerBytesAdjustment);
                bos.writeLong(blockInfo.getBlockSizeBytes());
                bos.writeTinyByteArray(extraBytes);
            }

            // Copy the block data from the temp file.
            try (FileInputStream fis = new FileInputStream(mTempFile)) {
                copyAll(fis, bos);
            }
        }
    }

    private static int copyAll(InputStream inputStream, TypedOutputStream outputStream)
            throws IOException {
        byte[] buffer = new byte[8192];
        int totalByteCount = 0;
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) >= 0) {
            outputStream.writeBytes(buffer, 0, bytesRead);
            totalByteCount += bytesRead;
        }
        inputStream.close();
        return totalByteCount;
    }

    private static int copyAll(BlockData blockData, TypedOutputStream outputStream)
            throws IOException {
        byte[] buffer = new byte[8192];
        ByteBuffer byteBuffer = blockData.getByteBuffer();
        int totalByteCount = 0;
        while (byteBuffer.remaining() > 0) {
            int byteCount = Math.min(buffer.length, byteBuffer.remaining());
            byteBuffer.get(buffer, 0, byteCount);
            outputStream.writeBytes(buffer, 0, byteCount);
            totalByteCount += byteCount;
        }
        return totalByteCount;
    }
}
