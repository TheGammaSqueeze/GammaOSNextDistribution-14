/*
 * Copyright (C) 2021 The Android Open Source Project
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

package libcore.org.apache.harmony.dalvik.ddmc;

import static org.junit.Assert.assertNotEquals;

import dalvik.system.VMDebug;
import java.io.File;
import java.io.IOException;
import org.apache.harmony.dalvik.ddmc.DdmVmInternal;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DdmVmInternalTest {

    public static Stuff stuff;

    /**
     * Simplified version of art/tools/ahat/src/test-dump/Main.java
     * @throws IOException
     */
    @Test
    public void setRecentAllocationsTrackingEnabled() throws IOException {
        stuff = new Stuff();

        // Create a bunch of unreachable objects pointing to basicString
        for (int i = 0; i < 100; i++) {
            stuff.basicStringRef = new Object[]{stuff.basicString};
        }

        String beforePath = File.createTempFile("before", "").getAbsolutePath();
        String afterPath = File.createTempFile("after", "").getAbsolutePath();

        VMDebug.dumpHprofData(beforePath);
        DdmVmInternal.setRecentAllocationsTrackingEnabled(true);
        VMDebug.dumpHprofData(afterPath);
        DdmVmInternal.setRecentAllocationsTrackingEnabled(false);

        File before = new File(beforePath);
        File after = new File(afterPath);
        before.delete();
        after.delete();
    }
}
