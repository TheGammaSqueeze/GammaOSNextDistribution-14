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

package com.android.telephony.sats2range;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.android.storage.block.write.BlockWriter;
import com.android.telephony.sats2range.read.HeaderBlock;
import com.android.telephony.sats2range.read.SatS2RangeFileFormat;
import com.android.telephony.sats2range.utils.TestUtils;
import com.android.telephony.sats2range.write.HeaderBlockWriter;

import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.io.IOException;

/** Tests for {@link HeaderBlockWriter} and {@link HeaderBlock}. */
public class HeaderBlockTest {
    @Test
    public void readWrite() throws IOException {
        SatS2RangeFileFormat fileFormat = TestUtils.createS2RangeFileFormat(true);

        // Create header data using HeaderBlockWriter.
        HeaderBlockWriter headerBlockWriter = HeaderBlockWriter.create(fileFormat);
        BlockWriter.ReadBack readBack = headerBlockWriter.close();
        assertEquals(SatS2RangeFileFormat.BLOCK_TYPE_HEADER, readBack.getType());
        assertArrayEquals(new byte[0], readBack.getExtraBytes());

        // Read the data back and confirm it matches what we expected.
        HeaderBlock headerBlock = HeaderBlock.wrap(readBack.getBlockData());
        assertEquals(fileFormat, headerBlock.getFileFormat());
    }

    @Test
    public void visit() throws Exception {
        SatS2RangeFileFormat fileFormat = TestUtils.createS2RangeFileFormat(true);

        // Create header data using HeaderBlockWriter.
        HeaderBlockWriter headerBlockWriter = HeaderBlockWriter.create(fileFormat);
        BlockWriter.ReadBack readBack = headerBlockWriter.close();

        // Read the data back and confirm it matches what we expected.
        HeaderBlock headerBlock = HeaderBlock.wrap(readBack.getBlockData());

        HeaderBlock.HeaderBlockVisitor mockVisitor =
                Mockito.mock(HeaderBlock.HeaderBlockVisitor.class);

        headerBlock.visit(mockVisitor);

        InOrder inOrder = Mockito.inOrder(mockVisitor);
        inOrder.verify(mockVisitor).begin();
        inOrder.verify(mockVisitor).visitFileFormat(fileFormat);
        inOrder.verify(mockVisitor).end();
    }
}
