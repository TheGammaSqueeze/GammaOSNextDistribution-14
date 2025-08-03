/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.security.cts;

import static android.system.OsConstants.F_GETFL;
import static android.system.OsConstants.O_NOFOLLOW;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AsbSecurityTest;
import android.provider.MediaStore;
import android.system.ErrnoException;
import android.system.Os;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileDescriptor;
import java.io.IOException;

@AppModeFull
@RunWith(AndroidJUnit4.class)
public class CVE_2021_39663 extends StsExtraBusinessLogicTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 200682135)
    public void testPocCVE_2021_39663() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        ContentResolver contentResolver = context.getContentResolver();
        try {
            Uri uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    new ContentValues());
            ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "rw");
            assumeNotNull(pfd);
            FileDescriptor fd = pfd.getFileDescriptor();
            int flags = Os.fcntlInt(fd, F_GETFL, 0);
            pfd.close();
            contentResolver.delete(uri, null, null);
            assumeTrue("Unable to read file status flags", flags > 0);
            assertEquals("Vulnerable to b/200682135!! O_NOFOLLOW flag not used.", O_NOFOLLOW,
                    flags & O_NOFOLLOW);
        } catch (ErrnoException | IOException e) {
            assumeNoException(e);
        }
    }
}
