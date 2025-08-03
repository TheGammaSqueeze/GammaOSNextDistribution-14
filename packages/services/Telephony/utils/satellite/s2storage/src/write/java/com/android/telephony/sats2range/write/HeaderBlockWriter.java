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

package com.android.telephony.sats2range.write;

import com.android.storage.block.read.BlockData;
import com.android.storage.block.write.BlockWriter;
import com.android.storage.io.write.TypedOutputStream;
import com.android.telephony.sats2range.read.HeaderBlock;
import com.android.telephony.sats2range.read.SatS2RangeFileFormat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;

/** A {@link BlockWriter} that can generate a satellite S2 data file header block. */
public final class HeaderBlockWriter implements BlockWriter {

    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    private final File mFile;

    private final SatS2RangeFileFormat mFileFormat;

    private boolean mIsOpen = true;

    private HeaderBlockWriter(SatS2RangeFileFormat fileFormat, File file) {
        mFileFormat = fileFormat;
        mFile = file;
    }

    /** Creates a new {@link HeaderBlockWriter}. */
    public static HeaderBlockWriter create(SatS2RangeFileFormat fileFormat) throws IOException {
        return new HeaderBlockWriter(fileFormat, File.createTempFile("header", ".bin"));
    }

    @Override
    public ReadBack close() throws IOException {
        checkIsOpen();
        mIsOpen = false;

        try (TypedOutputStream tos = new TypedOutputStream(new FileOutputStream(mFile))) {
            tos.writeUnsignedByte(mFileFormat.getS2Level());
            tos.writeUnsignedByte(mFileFormat.getPrefixBitCount());
            tos.writeUnsignedByte(mFileFormat.getSuffixBitCount());
            tos.writeUnsignedByte(mFileFormat.getTableEntryBitCount());
            tos.writeUnsignedByte(mFileFormat.getSuffixTableBlockIdOffset());
            tos.writeUnsignedByte(mFileFormat.isAllowedList()
                    ? HeaderBlock.TRUE : HeaderBlock.FALSE);
        }

        FileChannel fileChannel = FileChannel.open(mFile.toPath(), StandardOpenOption.READ);
        MappedByteBuffer map = fileChannel.map(FileChannel.MapMode.READ_ONLY, 0, mFile.length());
        fileChannel.close();
        BlockData blockData = new BlockData(map);
        return new ReadBack() {
            @Override
            public byte[] getExtraBytes() {
                return EMPTY_BYTE_ARRAY;
            }

            @Override
            public int getType() {
                return SatS2RangeFileFormat.BLOCK_TYPE_HEADER;
            }

            @Override
            public BlockData getBlockData() {
                return blockData;
            }
        };
    }

    private void checkIsOpen() {
        if (!mIsOpen) {
            throw new IllegalStateException("Writer is closed.");
        }
    }
}
