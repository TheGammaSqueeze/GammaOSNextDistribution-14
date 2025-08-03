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

package android.content.res.cts;

import android.content.cts.R;
import android.content.res.Resources;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.test.AndroidTestCase;

import androidx.test.filters.SmallTest;

@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class ResourceNameTest extends AndroidTestCase {

    @SmallTest
    public void testGetResourceName() {
        final Resources res = mContext.getResources();

        final String fullName = res.getResourceName(R.string.simple);
        assertEquals("android.content.cts:string/simple", fullName);

        final String packageName = res.getResourcePackageName(R.string.simple);
        assertEquals("android.content.cts", packageName);

        final String typeName = res.getResourceTypeName(R.string.simple);
        assertEquals("string", typeName);

        final String entryName = res.getResourceEntryName(R.string.simple);
        assertEquals("simple", entryName);
    }

    @SmallTest
    public void testGetResourceIdentifier() {
        final Resources res = mContext.getResources();
        int resid = res.getIdentifier(
                "android.content.cts:string/simple",
                null, null);
        assertEquals(R.string.simple, resid);

        resid = res.getIdentifier("string/simple", null,
                "android.content.cts");
        assertEquals(R.string.simple, resid);

        resid = res.getIdentifier("simple", "string",
                "android.content.cts");
        assertEquals(R.string.simple, resid);
    }
}

