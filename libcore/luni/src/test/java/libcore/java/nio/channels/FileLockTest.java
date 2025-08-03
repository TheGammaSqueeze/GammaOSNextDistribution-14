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

package libcore.java.nio.channels;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class FileLockTest {

    @Test
    public void testToString() throws Exception {
        File tmp = File.createTempFile("FileLockTest", "tmp");

        try(FileOutputStream fos = new FileOutputStream(tmp)) {
            try(FileChannel fc = fos.getChannel()) {
                FileLock lock = fc.lock(0, 42, false);
                String strLock = lock.toString();
                assertTrue(strLock.contains("0"));
                assertTrue(strLock.contains("42"));
                assertTrue(strLock.contains("exclusive"));
                assertTrue(strLock.contains("valid"));
                assertFalse(strLock.contains("invalid"));

                lock.release();
                strLock = lock.toString();
                assertTrue(strLock.contains("0"));
                assertTrue(strLock.contains("42"));
                assertTrue(strLock.contains("exclusive"));
                assertTrue(strLock.contains("invalid"));
            }
        }

        try(FileInputStream fis = new FileInputStream(tmp)) {
            try(FileChannel fc = fis.getChannel()) {
                FileLock lock = fc.lock(0, 42, true);
                String strLock = lock.toString();
                assertTrue(strLock.contains("0"));
                assertTrue(strLock.contains("42"));
                assertTrue(strLock.contains("shared"));
                assertTrue(strLock.contains("valid"));
                assertFalse(strLock.contains("invalid"));

                lock.release();
                strLock = lock.toString();
                assertTrue(strLock.contains("0"));
                assertTrue(strLock.contains("42"));
                assertTrue(strLock.contains("shared"));
                assertTrue(strLock.contains("invalid"));
            }
        }
    }
}
