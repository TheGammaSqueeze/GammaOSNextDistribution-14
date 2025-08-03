/*
 * Copyright 2020 The Android Open Source Project
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

package android.media.drmframework.cts;

import android.media.DrmInitData;
import android.os.Build;
import android.test.AndroidTestCase;

import androidx.test.filters.SdkSuppress;

import com.android.compatibility.common.util.NonMainlineTest;

import java.util.UUID;

@NonMainlineTest
// methods introduced as hidden in R; first exposed in S
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
public class DrmInitDataTest extends AndroidTestCase {

    public void testSchemeInitDataConstructor() {
        UUID uuid = new UUID(1, 1);
        String mimeType = "mime/type";
        byte[] data = new byte[0];
        DrmInitData.SchemeInitData schemeInitData =
                new DrmInitData.SchemeInitData(uuid, mimeType, data);
        assertSame(uuid, schemeInitData.uuid);
        assertSame(mimeType, schemeInitData.mimeType);
        assertSame(data, schemeInitData.data);
    }
}
