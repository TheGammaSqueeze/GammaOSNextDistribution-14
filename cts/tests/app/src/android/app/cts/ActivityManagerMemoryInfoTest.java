/*
 * Copyright (C) 2008 The Android Open Source Project
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
package android.app.cts;

import android.app.ActivityManager;
import android.os.Debug;
import android.os.Parcel;
import android.os.Process;
import android.test.AndroidTestCase;

public class ActivityManagerMemoryInfoTest extends AndroidTestCase {
    protected ActivityManager.MemoryInfo mMemory;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mMemory = new ActivityManager.MemoryInfo();
    }

    public void testDescribeContents() {
        assertEquals(0, mMemory.describeContents());
    }

    public void testWriteToParcel() throws Exception {
        final long advertisedMem = 200000L;
        mMemory.advertisedMem = advertisedMem;
        final long availMem = 1000L;
        mMemory.availMem = availMem;
        final long threshold = 500L;
        mMemory.threshold = threshold;
        final boolean lowMemory = true;
        mMemory.lowMemory = lowMemory;
        Parcel parcel = Parcel.obtain();
        mMemory.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ActivityManager.MemoryInfo values =
            ActivityManager.MemoryInfo.CREATOR.createFromParcel(parcel);
        assertEquals(advertisedMem, values.advertisedMem);
        assertEquals(availMem, values.availMem);
        assertEquals(threshold, values.threshold);
        assertEquals(lowMemory, values.lowMemory);

        // test null condition.
        try {
            mMemory.writeToParcel(null, 0);
            fail("writeToParcel should throw out NullPointerException when Parcel is null");
        } catch (NullPointerException e) {
            // expected
        }
    }

    public void testReadFromParcel() throws Exception {
        final long advertisedMem = 200000L;
        mMemory.advertisedMem = advertisedMem;
        final long availMem = 1000L;
        mMemory.availMem = availMem;
        final long threshold = 500L;
        mMemory.threshold = threshold;
        final boolean lowMemory = true;
        mMemory.lowMemory = lowMemory;
        Parcel parcel = Parcel.obtain();
        mMemory.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        ActivityManager.MemoryInfo result = new ActivityManager.MemoryInfo();
        result.readFromParcel(parcel);
        assertEquals(advertisedMem, result.advertisedMem);
        assertEquals(availMem, result.availMem);
        assertEquals(threshold, result.threshold);
        assertEquals(lowMemory, result.lowMemory);

        // test null condition.
        result = new ActivityManager.MemoryInfo();
        try {
            result.readFromParcel(null);
            fail("readFromParcel should throw out NullPointerException when Parcel is null");
        } catch (NullPointerException e) {
            // expected
        }
    }

    public void testGetProcessMemoryInfo() {
        // PID == 1 is the init process.
        Debug.MemoryInfo[] result = getContext().getSystemService(ActivityManager.class)
                .getProcessMemoryInfo(new int[]{1, Process.myPid(), 1});
        assertEquals(3, result.length);
        isEmpty(result[0]);
        isEmpty(result[2]);
        isNotEmpty(result[1]);
    }

    private static void isEmpty(Debug.MemoryInfo mi) {
        assertEquals(0, mi.dalvikPss);
        assertEquals(0, mi.nativePss);
    }

    private static void isNotEmpty(Debug.MemoryInfo mi) {
        assertTrue(mi.dalvikPss > 0);
        assertTrue(mi.nativePss > 0);
    }
}
