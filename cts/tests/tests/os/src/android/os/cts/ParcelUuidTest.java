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

package android.os.cts;

import static org.junit.Assert.assertEquals;

import android.os.Parcel;
import android.os.ParcelUuid;

import org.junit.Test;

import java.util.UUID;

public class ParcelUuidTest {
    private static final String TEST_UUID = "41217664-9172-527a-b3d5-edabb50a7d69";

    @Test
    public void testTypical() {
        UUID uuid = UUID.fromString(TEST_UUID);
        assertEquals(uuid, new ParcelUuid(uuid).getUuid());
        assertEquals(uuid, ParcelUuid.fromString(TEST_UUID).getUuid());
    }

    @Test
    public void testSymmetry() {
        UUID uuid = UUID.fromString(TEST_UUID);
        ParcelUuid before = new ParcelUuid(uuid);

        Parcel p = Parcel.obtain();
        p.writeParcelable(before, 0);
        p.setDataPosition(0);

        ParcelUuid after = p.readParcelable(null, ParcelUuid.class);
        assertEquals(before, after);
        p.recycle();
    }
}
