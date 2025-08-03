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

package com.android.storage.s2;

import static com.android.storage.s2.S2CellOrdering.asUnsignedNumeric;
import static com.android.storage.s2.S2Support.MAX_FACE_ID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Tests for {@link S2CellOrdering}. */
public class S2CellOrderingTest {

    @Test
    public void ordering() {
        for (int level = 0; level <= S2Support.MAX_S2_LEVEL; level++) {
            long minCellIdNumeric = S2CellOrdering.getMinCellIdNumeric(level);
            long maxCellIdNumeric = S2CellOrdering.getMaxCellIdNumeric(level);
            for (int faceId = 0; faceId <= S2Support.MAX_FACE_ID; faceId++) {
                long faceMinCellIdNumeric = asUnsignedNumeric(S2Support.cellId(level, faceId, 0));
                long faceMaxCellIdNumeric = asUnsignedNumeric(
                        S2Support.cellId(level, faceId, S2Support.getMaxIndex(level)));

                if (level == 0) {
                    // level 0, index is always zero: there's just one cell ID per face.
                    assertEquals("level=" + level + ", faceId=" + faceId,
                            faceMinCellIdNumeric, faceMaxCellIdNumeric);
                } else {
                    assertTrue("level=" + level + ", faceId=" + faceId,
                            faceMinCellIdNumeric < faceMaxCellIdNumeric);
                }

                if (faceId == 0) {
                    assertEquals("level=" + level + ", faceId=" + faceId,
                            minCellIdNumeric, faceMinCellIdNumeric);
                } else {
                    assertTrue("level=" + level + ", faceId=" + faceId,
                            minCellIdNumeric < faceMinCellIdNumeric);
                }
                if (faceId == MAX_FACE_ID) {
                    assertEquals("level=" + level + ", faceId=" + faceId,
                            maxCellIdNumeric, faceMaxCellIdNumeric);
                } else {
                    assertTrue("level=" + level + ", faceId=" + faceId,
                            maxCellIdNumeric > faceMaxCellIdNumeric);
                }
            }
        }
    }
}
