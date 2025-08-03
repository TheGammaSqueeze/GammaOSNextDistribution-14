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

import static org.mockito.Mockito.mock;

import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

/** Tests {@link BlockInfo}. */
public class BlockInfoTest {

    @Test
    public void visit() throws Exception {
        int id = 1234;
        int type = 2345;
        long startOffset = 34567;
        long size = 456789;
        byte[] extraBytes = "Extra bytes".getBytes();
        BlockInfo blockInfo = new BlockInfo(id, type, startOffset, size, extraBytes);
        BlockInfo.BlockInfoVisitor mockVisitor = mock(BlockInfo.BlockInfoVisitor.class);
        blockInfo.visit(mockVisitor);

        InOrder inOrder = Mockito.inOrder(mockVisitor);
        inOrder.verify(mockVisitor).begin();
        inOrder.verify(mockVisitor).visit(blockInfo);
        inOrder.verify(mockVisitor).end();
    }
}
