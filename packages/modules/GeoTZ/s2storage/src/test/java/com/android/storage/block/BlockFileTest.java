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

package com.android.storage.block;

import static com.android.storage.testing.MoreAsserts.assertThrows;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;

import com.android.storage.block.read.Block;
import com.android.storage.block.read.BlockData;
import com.android.storage.block.read.BlockFileReader;
import com.android.storage.block.read.BlockInfo;
import com.android.storage.block.read.BlockMatcher;
import com.android.storage.block.write.BlockFileWriter;

import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Tests {@link BlockFileReader}, {@link Block}, {@link BlockInfo} and {@link BlockFileWriter}.
 */
public class BlockFileTest {

    @Test
    public void testReadWrite_memoryMapped() throws Exception {
        testReadWrite(true);
    }

    @Test
    public void testReadWrite_notMemoryMapped() throws Exception {
        testReadWrite(false);
    }

    private void testReadWrite(boolean memoryMapBlocks) throws Exception {
        char magic = 0xABCD;
        File blockFile = File.createTempFile("test", ".tmp");
        byte[] block1ExtraBytes = "Extra bytes 1".getBytes(StandardCharsets.UTF_8);
        byte[] block1DataBytes = "Block 1 bytes".getBytes(StandardCharsets.UTF_8);
        byte[] block2ExtraBytes = "Extra bytes 2".getBytes(StandardCharsets.UTF_8);
        byte[] block2DataBytes = "Block 2 bytes".getBytes(StandardCharsets.UTF_8);

        try (BlockFileWriter bfw = BlockFileWriter.open(magic, 1, blockFile)) {
            bfw.addBlock(1111, block1ExtraBytes, new BlockData(getByteBuffer(block1DataBytes)));
            bfw.addBlock(2222, block2ExtraBytes, new BlockData(getByteBuffer(block2DataBytes)));
        }

        try (BlockFileReader bfr = BlockFileReader.open(memoryMapBlocks, blockFile, magic, 1)) {
            BlockInfo block1Info = bfr.getBlockInfo(0);
            assertEquals(0, block1Info.getId());
            assertEquals(1111, block1Info.getType());
            assertArrayEquals(block1ExtraBytes, block1Info.getExtraBytes());

            Block block1 = bfr.getBlock(0);
            assertEquals(0, block1.getId());
            assertEquals(1111, block1.getType());
            assertEquals(block1DataBytes.length, block1.getData().getSize());

            BlockData block1Data = block1.getData();
            assertArrayEquals(block1DataBytes, block1Data.getBytes(0, block1DataBytes.length));

            BlockInfo block2Info = bfr.getBlockInfo(1);
            assertEquals(1, block2Info.getId());
            assertEquals(2222, block2Info.getType());
            assertArrayEquals(block2ExtraBytes, block2Info.getExtraBytes());

            Block block2 = bfr.getBlock(1);
            assertEquals(1, block2.getId());
            assertEquals(2222, block2.getType());
            assertEquals(block2DataBytes.length, block2.getData().getSize());

            BlockData block2Data = block2.getData();
            assertArrayEquals(block2DataBytes, block2Data.getBytes(0, block2DataBytes.length));
        }
    }

    @Test
    public void blockIdBounds() throws Exception {
        char magic = 0xABCD;
        File blockFile = File.createTempFile("test", ".tmp");
        byte[] block1DataBytes = "Block 1 bytes".getBytes(StandardCharsets.UTF_8);
        byte[] block2DataBytes = "Block 2 bytes".getBytes(StandardCharsets.UTF_8);

        try (BlockFileWriter bfw = BlockFileWriter.open(magic, 2, blockFile)) {
            byte[] blockInfoExtraBytes = new byte[0];
            bfw.addBlock(1111, blockInfoExtraBytes, new BlockData(getByteBuffer(block1DataBytes)));
            bfw.addBlock(2222, blockInfoExtraBytes, new BlockData(getByteBuffer(block2DataBytes)));
        }
        int blockCount = 2;

        try (BlockFileReader bfr = BlockFileReader.open(false, blockFile, magic, 2)) {

            assertThrows(IndexOutOfBoundsException.class, () -> bfr.getBlockInfo(-1));
            assertThrows(IndexOutOfBoundsException.class, () -> bfr.getBlock(-1));
            assertThrows(IndexOutOfBoundsException.class, () -> bfr.getBlockInfo(blockCount));
            assertThrows(IndexOutOfBoundsException.class, () -> bfr.getBlock(blockCount));
        }
    }

    @Test
    public void visit() throws Exception {
        char magic = 0xABCD;
        File blockFile = File.createTempFile("test", ".tmp");
        byte[] block1ExtraBytes = "Extra bytes 1".getBytes(StandardCharsets.UTF_8);
        byte[] block1DataBytes = "Block 1 bytes".getBytes(StandardCharsets.UTF_8);
        byte[] block2ExtraBytes = "Extra bytes 2".getBytes(StandardCharsets.UTF_8);
        byte[] block2DataBytes = "Block 2 bytes".getBytes(StandardCharsets.UTF_8);

        int version = 2;
        try (BlockFileWriter bfw = BlockFileWriter.open(magic, version, blockFile)) {
            bfw.addBlock(1111, block1ExtraBytes, new BlockData(getByteBuffer(block1DataBytes)));
            bfw.addBlock(2222, block2ExtraBytes, new BlockData(getByteBuffer(block2DataBytes)));
        }
        int blockCount = 2;

        boolean memoryMapBlocks = false;
        int requiredMinVersion = 1;
        try (BlockFileReader bfr =
                     BlockFileReader.open(memoryMapBlocks, blockFile, magic, requiredMinVersion)) {
            BlockInfo blockInfo1111 = bfr.getBlockInfo(0);
            BlockInfo blockInfo2222 = bfr.getBlockInfo(1);
            Block block1111 = bfr.getBlock(0);
            Block block2222 = bfr.getBlock(1);

            BlockFileReader.BlockFileVisitor mockVisitor =
                    Mockito.mock(BlockFileReader.BlockFileVisitor.class);

            bfr.visit(mockVisitor);

            InOrder inOrder = Mockito.inOrder(mockVisitor);
            inOrder.verify(mockVisitor).begin();
            inOrder.verify(mockVisitor).visitFileHeader(magic, version, blockCount);
            inOrder.verify(mockVisitor).visitBlockInfo(blockInfo1111);
            inOrder.verify(mockVisitor).visitBlockInfo(blockInfo2222);
            inOrder.verify(mockVisitor).visitBlock(argThat(new BlockMatcher(block1111)));
            inOrder.verify(mockVisitor).visitBlock(argThat(new BlockMatcher(block2222)));
            inOrder.verify(mockVisitor).end();
        }
    }

    private static ByteBuffer getByteBuffer(byte[] block1DataBytes) {
        return ByteBuffer.wrap(block1DataBytes).asReadOnlyBuffer();
    }
}
