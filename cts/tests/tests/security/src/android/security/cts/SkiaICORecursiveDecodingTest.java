/*
 * Copyright (C) 2016 The Android Open Source Project
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
import androidx.test.runner.AndroidJUnit4;
import com.android.sts.common.util.StsExtraBusinessLogicTestCase;
import org.junit.runner.RunWith;
import org.junit.Test;

import java.io.InputStream;

import android.security.cts.R;
import android.platform.test.annotations.AsbSecurityTest;

@RunWith(AndroidJUnit4.class)
public class SkiaICORecursiveDecodingTest extends StsExtraBusinessLogicTestCase {

    @AsbSecurityTest(cveBugId = 73782357)
    @Test
    public void testAndroid_cve_2017_13318() {
        doSkiaIcoRecursiveDecodingTest(R.raw.cve_2017_13318);
    }

    @AsbSecurityTest(cveBugId = 17262540)
    @Test
    public void test_android_bug_17262540() {
        doSkiaIcoRecursiveDecodingTest(R.raw.bug_17262540);
    }

    @AsbSecurityTest(cveBugId = 17265466)
    @Test
    public void test_android_bug_17265466() {
        doSkiaIcoRecursiveDecodingTest(R.raw.bug_17265466);
    }

    /**
     * Verifies that the device prevents recursive decoding of malformed ICO files
     */
    public void doSkiaIcoRecursiveDecodingTest(int resId) {
        InputStream exploitImage = getInstrumentation().getContext().getResources().openRawResource(resId);
        /**
         * The decodeStream method results in SIGSEGV (Segmentation fault) on unpatched devices
         * while decoding the exploit image which will lead to process crash
         */
        Bitmap bitmap = BitmapFactory.decodeStream(exploitImage);
    }
}
