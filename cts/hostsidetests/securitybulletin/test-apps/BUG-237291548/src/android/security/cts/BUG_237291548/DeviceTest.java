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

package android.security.cts.BUG_237291548;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static org.junit.Assert.assertEquals;

import android.content.pm.PackageManager;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class DeviceTest {

    private static final String MIME_GROUP = "myMimeGroup";

    PackageManager mPm = getApplicationContext().getPackageManager();

    @Test(expected = IllegalStateException.class)
    public void testExceedGroupLimit() {
        Set<String> mimeTypes = mPm.getMimeGroup(MIME_GROUP);
        assertEquals(mimeTypes.size(), 0);
        for (int i = 0; i < 500; i++) {
            mimeTypes.add("MIME" + i);
            mPm.setMimeGroup(MIME_GROUP, mimeTypes);
        }
        mimeTypes = mPm.getMimeGroup(MIME_GROUP);
        assertEquals(500, mimeTypes.size());
        mimeTypes.add("ONETOMANYMIME");
        mPm.setMimeGroup(MIME_GROUP, mimeTypes);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExceedMimeLengthLimit() {
        Set<String> mimeTypes = new HashSet<>();
        mimeTypes.add(new String(new char[64]).replace("\0", "MIME"));
        mPm.setMimeGroup(MIME_GROUP, mimeTypes);
    }
}
