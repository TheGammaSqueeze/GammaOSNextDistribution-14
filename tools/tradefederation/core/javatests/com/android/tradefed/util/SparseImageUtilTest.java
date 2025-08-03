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

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Unit tests for {@link SparseImageUtil} */
@RunWith(JUnit4.class)
public class SparseImageUtilTest {
    private File mSparseImageFile;

    @Before
    public void setUp() throws IOException {
        mSparseImageFile = FileUtil.createTempFile("sparse", ".img");
        try (FileOutputStream out = new FileOutputStream(mSparseImageFile)) {
            out.write(getSparseImageData());
        }
    }

    @After
    public void tearDown() {
        FileUtil.deleteFile(mSparseImageFile);
    }

    /** Verify {@link com.android.tradefed.util.SparseImageUtil#isSparse}. */
    @Test
    public void testIsSparse() {
        Assert.assertTrue(SparseImageUtil.isSparse(mSparseImageFile));
    }

    /** Verify {@link com.android.tradefed.util.SparseImageUtil#unsparse}. */
    @Test
    public void testUnsparse() throws IOException {
        File unsparsedFile = FileUtil.createTempFile("unsparse", ".img");
        byte[] unsparsedData = null;
        try {
            SparseImageUtil.unsparse(mSparseImageFile, unsparsedFile);
            try (FileInputStream in = new FileInputStream(unsparsedFile)) {
                unsparsedData = StreamUtil.getByteArrayListFromStream(in).getContents();
            }
            Assert.assertArrayEquals(getUnsparsedImageData(), unsparsedData);
        } finally {
            FileUtil.deleteFile(unsparsedFile);
        }
    }

    /**
     * Returns some sparse data.
     *
     * @see https://android.googlesource.com/platform/system/core/+/master/libsparse/sparse_format.h
     */
    private byte[] getSparseImageData() {
        final int SPARSE_IMAGE_MAGIC = 0xED26FF3A;
        ByteBuffer buffer = ByteBuffer.allocate(4096);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        // Header
        buffer.putInt(SPARSE_IMAGE_MAGIC);
        buffer.putShort((short) 1);
        buffer.putShort((short) 0);
        buffer.putShort((short) 28);
        buffer.putShort((short) 12);
        buffer.putInt(4); /* block size */
        buffer.putInt(512 + 256); /* total blocks */
        buffer.putInt(2); /* total chunks */
        buffer.putInt(0); /* ignore check sum */
        // RAW chunk, 2048 bytes of lorem ipsum
        byte[] loremIpsum = getLoremIpsum();
        buffer.putShort((short) 0xCAC1);
        buffer.putShort((short) 0); /* padding */
        buffer.putInt(loremIpsum.length / 4); /* data size in terms of number of blocks */
        buffer.putInt(12 + loremIpsum.length); /* header size + data size */
        buffer.put(loremIpsum);
        // DONTCARE chunk, 1024 bytes of zeroes
        byte[] zeroes = new byte[1024];
        buffer.putShort((short) 0xCAC3);
        buffer.putShort((short) 0); /* padding */
        buffer.putInt(zeroes.length / 4); /* data size in terms of number of blocks */
        buffer.putInt(12 + zeroes.length); /* header size + data size */
        buffer.put(zeroes);
        return Arrays.copyOf(buffer.array(), buffer.position());
    }

    private byte[] getUnsparsedImageData() {
        byte[] loremIpsum = getLoremIpsum();
        // Pad lorem ipsum with 1024 bytes of zeroes
        return Arrays.copyOf(loremIpsum, loremIpsum.length + 1024);
    }

    /** Returns a chunk of text data. */
    private byte[] getLoremIpsum() {
        final int dataLen = 2048; /* Must be a multiple of 4 */
        final String loremIpsumString =
                "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor"
                        + " incididunt ut labore et dolore magna aliqua. Enim neque volutpat ac"
                        + " tincidunt vitae semper quis lectus. Est pellentesque elit ullamcorper"
                        + " dignissim cras tincidunt lobortis feugiat vivamus. Vitae ultricies leo"
                        + " integer malesuada nunc vel. Ultrices tincidunt arcu non sodales neque"
                        + " sodales ut etiam sit. Arcu cursus vitae congue mauris rhoncus aenean."
                        + " Consectetur a erat nam at lectus urna duis convallis convallis. Suscipit"
                        + " tellus mauris a diam maecenas sed. At elementum eu facilisis sed odio."
                        + " Neque sodales ut etiam sit.";
        final byte[] loremIpsumBytes = loremIpsumString.getBytes();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        while (buffer.size() < dataLen) {
            buffer.write(loremIpsumBytes, 0, loremIpsumBytes.length);
        }
        return Arrays.copyOf(buffer.toByteArray(), dataLen);
    }
}
