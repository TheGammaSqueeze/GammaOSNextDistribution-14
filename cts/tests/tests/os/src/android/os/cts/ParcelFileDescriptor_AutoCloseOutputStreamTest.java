/*
 * Copyright (C) 2009 The Android Open Source Project
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

import static org.junit.Assert.fail;

import android.os.ParcelFileDescriptor;
import android.os.ParcelFileDescriptor.AutoCloseOutputStream;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.Reference;
import java.nio.channels.FileLock;

@RunWith(AndroidJUnit4.class)
public class ParcelFileDescriptor_AutoCloseOutputStreamTest {
    @Test
    public void testAutoCloseOutputStream() throws Exception {
        ParcelFileDescriptor pf = ParcelFileDescriptorTest.makeParcelFileDescriptor();

        AutoCloseOutputStream out = new AutoCloseOutputStream(pf);

        out.write(2);

        out.close();

        try {
            out.write(2);
            fail("Failed to throw exception.");
        } catch (IOException e) {
            // expected
        }
    }

    @Test
    public void testCloseOrdering() throws Exception {
        // b/118316956: Make sure that we close the OutputStream before we close the PFD.
        ParcelFileDescriptor pfd = ParcelFileDescriptorTest.makeParcelFileDescriptor();
        FileLock l = null;
        try (FileOutputStream out = new AutoCloseOutputStream(pfd)) {
            l = out.getChannel().lock();
        } finally {
            Reference.reachabilityFence(l);
        }
    }
}
