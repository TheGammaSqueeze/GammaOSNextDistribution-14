/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.platform.test.annotations.AsbSecurityTest;
import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import java.io.InputStream;

import android.security.cts.R;

import static org.junit.Assert.*;

import androidx.test.runner.AndroidJUnit4;
import org.junit.runner.RunWith;
import org.junit.Test;

@RunWith(AndroidJUnit4.class)
public class DecodeTest extends StsExtraBusinessLogicTestCase {
    /**
     * Verifies that the device fails to decode a large, corrupt BMP.
     *
     * Prior to fixing bug 34778578, decoding this file would crash. Instead, it should fail to
     * decode.
     */
    @AsbSecurityTest(cveBugId = 34778578)
    @Test
    public void test_android_bug_34778578() {
        InputStream exploitImage = getInstrumentation().getContext().getResources().openRawResource(R.raw.bug_34778578);
        Bitmap bitmap = BitmapFactory.decodeStream(exploitImage);
        assertNull(bitmap);
    }

    /**
     * Verifies that the device fails to decode a truncated animated webp.
     *
     * Prior to fixing bug 67381469, decoding this file would crash. Instead, it should fail to
     * decode.
     */
    @AsbSecurityTest(cveBugId = 67381469)
    @Test
    public void test_android_bug_67381469() {
        InputStream exploitImage = getInstrumentation().getContext().getResources().openRawResource(R.raw.bug_67381469);
        Bitmap bitmap = BitmapFactory.decodeStream(exploitImage);
        assertNull(bitmap);
    }
}
