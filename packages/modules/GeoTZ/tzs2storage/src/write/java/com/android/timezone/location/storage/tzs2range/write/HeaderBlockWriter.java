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

package com.android.timezone.location.storage.tzs2range.write;

import com.android.storage.block.read.BlockData;
import com.android.storage.block.write.BlockWriter;
import com.android.storage.io.write.TypedOutputStream;
import com.android.timezone.location.storage.tzs2range.BankedTzIdSets;
import com.android.timezone.location.storage.tzs2range.TzS2RangeFileFormat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardOpenOption;
import java.util.List;

/** A {@link BlockWriter} that can generate a TZ S2 data file header block. */
public final class HeaderBlockWriter implements BlockWriter {

    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];

    private final BankedTzIdSetsPacker mBankedTzIdSetsPacker;

    private final File mFile;

    private final TzS2RangeFileFormat mFileFormat;

    private boolean mIsOpen = true;

    private HeaderBlockWriter(TzS2RangeFileFormat fileFormat, File file) {
        mFileFormat = fileFormat;
        mBankedTzIdSetsPacker = new BankedTzIdSetsPacker(fileFormat.getMaxTzIdSetIdValue() + 1);
        mFile = file;
    }

    /** Creates a new {@link HeaderBlockWriter}. */
    public static HeaderBlockWriter create(TzS2RangeFileFormat fileFormat) throws IOException {
        return new HeaderBlockWriter(fileFormat, File.createTempFile("header", ".bin"));
    }

    /**
     * Returns the {@link BankedTzIdSetsPacker} to use when adding time zone ID sets to the TZ S2
     * data file.
     */
    public BankedTzIdSetsPacker getBankedTzIdSetsPacker() {
        return mBankedTzIdSetsPacker;
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
            tos.writeUnsignedByte(mFileFormat.getTableEntryRangeLengthBitCount());
            tos.writeUnsignedByte(mFileFormat.getSuffixTableBlockIdOffset());

            // Write tzid strings
            BankedTzIdSets bankedTzIdSets = mBankedTzIdSetsPacker.create();
            List<String> strings = bankedTzIdSets.getStringsByIndex();
            tos.writeChar(strings.size());
            for (String string : strings) {
                byte[] bytes = string.getBytes(StandardCharsets.UTF_8);
                tos.writeTinyByteArray(bytes);
            }

            // Write banks
            int bankCount = bankedTzIdSets.getBankCount();
            tos.writeUnsignedByte(bankCount);
            for (int bankIndex = 0; bankIndex < bankCount; bankIndex++) {
                BankedTzIdSets.Bank bank = bankedTzIdSets.getBank(bankIndex);
                // Write sets
                int setCount = bank.getTzIdSetCount();
                tos.writeUnsignedByte(setCount);
                for (int setIndex = 0; setIndex < setCount; setIndex++) {
                    BankedTzIdSets.TzIdSet tzIdSet = bank.getTzIdSet(setIndex);
                    List<Integer> stringIds = tzIdSet.getStringIds();
                    char[] stringIdsAsCharArray = new char[stringIds.size()];
                    for (int stringIdIndex = 0; stringIdIndex < stringIds.size(); stringIdIndex++) {
                        int stringId = stringIds.get(stringIdIndex);
                        if (stringId < 0 || stringId > Character.MAX_VALUE) {
                            throw new IllegalStateException(
                                    "stringIdentifier must be representable in 16 bits,"
                                            + " stringIdentifier=" + stringId);
                        }
                        stringIdsAsCharArray[stringIdIndex] = (char) stringId;
                    }
                    tos.writeTinyCharArray(stringIdsAsCharArray);
                }
            }
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
                return TzS2RangeFileFormat.BLOCK_TYPE_HEADER;
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
