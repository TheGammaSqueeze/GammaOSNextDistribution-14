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

package com.android.tradefed.util.zip;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CentralDirectoryInfoTest {
    private static final int CENTRAL_FILE_HEADER_SIGNATURE = 0x02014b50;

    private static final int ZIP64_EXTRA_FIELD_HEADER_ID = 0x0001;
    private static final String TEST_FILE_NAME = "test.txt";
    private static final String TEST_FILE_CONTENT = "test";
    private static final String TEST_FILE_HEADER_COMMENT = "comment";

    private static final int DEFAULT_OFFSET = 10;

    private static final int TEST_FILE_CRC32 = 22222;

    private static final long DEFAULT_ZIP64_LOCAL_HEADER_OFFSET = 100L;

    private byte[] createCentralDirectoryData(boolean isZip64, byte[] extra) {
        byte[] nameBytes = TEST_FILE_NAME.getBytes(UTF_8);
        byte[] commentBytes = TEST_FILE_HEADER_COMMENT.getBytes(UTF_8);
        byte[] contentBytes = TEST_FILE_CONTENT.getBytes(UTF_8);

        ByteBuffer buffer = ByteBuffer.allocate(1000).order(ByteOrder.LITTLE_ENDIAN);
        buffer.position(DEFAULT_OFFSET);
        buffer.putInt(CENTRAL_FILE_HEADER_SIGNATURE); // Central file header signature (0x02014b50)
        buffer.putShort((short) 0); // Version made by
        buffer.putShort((short) 0); // Version needed to extract
        buffer.putShort((short) 0); // General purpose bit flag
        buffer.putShort((short) 0); // Compression method (store)
        buffer.putShort((short) 0); // Last mod file time
        buffer.putShort((short) 0); // Last mod file date
        buffer.putInt(TEST_FILE_CRC32); // CRC-32
        if (isZip64) {
            buffer.putInt(-1); // Compressed size (0xffffffff for zip64)
            buffer.putInt(-1); // Uncompressed size (0xffffffff for zip64)
        } else {
            buffer.putInt(contentBytes.length); // Compressed size
            buffer.putInt(contentBytes.length); // Uncompressed size
        }
        buffer.putShort((short) nameBytes.length); // Filename length
        buffer.putShort((short) extra.length); // Extra field length
        buffer.putShort((short) commentBytes.length); // File comment length
        buffer.putShort((short) 0); // Disk number start
        buffer.putShort((short) 0); // Internal file attributes
        buffer.putInt(0); // External file attributes
        if (isZip64) {
            buffer.putInt(-1); // Relative offset of local header (0xffffffff for zip64)
        } else {
            buffer.putInt(0); // Relative offset of local header
        }
        buffer.put(nameBytes); // Filename
        buffer.put(extra); // Extra field
        buffer.put(commentBytes); // File comment

        return buffer.array();
    }

    private void writeZip64InfoToExtraField(ByteBuffer extra) {
        extra.putShort((short) ZIP64_EXTRA_FIELD_HEADER_ID); // Header ID
        extra.putShort((short) 28); // Size
        extra.putLong(TEST_FILE_CONTENT.length()); // Uncompressed file size
        extra.putLong(TEST_FILE_CONTENT.length()); // Compressed file size
        extra.putLong(DEFAULT_ZIP64_LOCAL_HEADER_OFFSET); // Relative header offset
        extra.putInt(0); // Disk Start number
    }

    @Test
    public void centralDirectoryInfo() throws Exception {
        byte[] data = createCentralDirectoryData(false, new byte[0]);

        CentralDirectoryInfo info = new CentralDirectoryInfo(data, DEFAULT_OFFSET, false);

        assertEquals(info.getCompressionMethod(), 0);
        assertEquals(info.getCrc(), TEST_FILE_CRC32);
        assertEquals(info.getCompressedSize(), TEST_FILE_CONTENT.length());
        assertEquals(info.getUncompressedSize(), TEST_FILE_CONTENT.length());
        assertEquals(info.getFileName(), TEST_FILE_NAME);
        assertEquals(info.getFileNameLength(), TEST_FILE_NAME.length());
        assertEquals(info.getExtraFieldLength(), 0);
        assertEquals(info.getFileCommentLength(), TEST_FILE_HEADER_COMMENT.length());
        assertEquals(info.getLocalHeaderOffset(), 0);
    }

    @Test
    public void centralDirectoryInfo_useZip64() throws Exception {
        ByteBuffer extra = ByteBuffer.allocate(32).order(ByteOrder.LITTLE_ENDIAN);
        writeZip64InfoToExtraField(extra);
        byte[] data = createCentralDirectoryData(true, extra.array());

        CentralDirectoryInfo info = new CentralDirectoryInfo(data, DEFAULT_OFFSET, true);

        assertEquals(info.getUncompressedSize(), TEST_FILE_CONTENT.length());
        assertEquals(info.getCompressedSize(), TEST_FILE_CONTENT.length());
        assertEquals(info.getLocalHeaderOffset(), DEFAULT_ZIP64_LOCAL_HEADER_OFFSET);
    }

    @Test
    public void centralDirectoryInfo_useZip64_multipleEntriesInExtraField() throws Exception {
        ByteBuffer extra = ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN);
        // First entry in the extra field, 4 bytes.
        extra.putShort((short) 0x0011); // Header ID
        extra.putShort((short) 0); // Size
        // Second entry (zip64 extended information), 32 bytes.
        writeZip64InfoToExtraField(extra);
        byte[] data = createCentralDirectoryData(true, extra.array());

        CentralDirectoryInfo info = new CentralDirectoryInfo(data, DEFAULT_OFFSET, true);

        assertEquals(info.getUncompressedSize(), TEST_FILE_CONTENT.length());
        assertEquals(info.getCompressedSize(), TEST_FILE_CONTENT.length());
        assertEquals(info.getLocalHeaderOffset(), DEFAULT_ZIP64_LOCAL_HEADER_OFFSET);
    }

    @Test
    public void centralDirectoryInfo_noZip64Field_throwException() throws Exception {
        ByteBuffer extra = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        extra.putShort((short) 0x0002); // HeaderID
        extra.putShort((short) 0); // Size
        byte[] data = createCentralDirectoryData(true, extra.array());

        assertThrows(
                RuntimeException.class, () -> new CentralDirectoryInfo(data, DEFAULT_OFFSET, true));
    }
}
