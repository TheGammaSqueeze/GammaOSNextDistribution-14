/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.security.cts;

import android.graphics.BitmapFactory;
import android.platform.test.annotations.AsbSecurityTest;
import androidx.test.runner.AndroidJUnit4;
import com.android.sts.common.util.StsExtraBusinessLogicTestCase;
import org.junit.runner.RunWith;
import org.junit.Test;
import static org.junit.Assert.*;
import android.security.cts.R;

import java.io.BufferedInputStream;
import java.io.InputStream;

@RunWith(AndroidJUnit4.class)
public class BitmapFactoryDecodeStreamTest extends StsExtraBusinessLogicTestCase {
    /*
     * This test case reproduces the bug in CVE-2015-1532.
     * It verifies that the BitmapFactory:decodeStream method is not vulnerable
     * to heap corruption by trying to open a crafted PNG image with incorrect
     * npTc chunk.
     */
    @AsbSecurityTest(cveBugId = 19151999)
    @Test
    public void testNinePatchHeapOverflow() throws Exception {
        InputStream inStream = new BufferedInputStream(getInstrumentation().getContext().getResources().openRawResource(
                R.raw.cve_2015_1532));
        BitmapFactory.decodeStream(inStream);

    }

    @AsbSecurityTest(cveBugId = 36724453)
    @Test
    public void testPocCVE_2017_0691() throws Exception {
        InputStream exploitImage = new BufferedInputStream(getInstrumentation().getContext().getResources().openRawResource(
                R.raw.cve_2017_0691));
        BitmapFactory.decodeStream(exploitImage);
    }

    @AsbSecurityTest(cveBugId = 65290323)
    @Test
    public void test_b65290323() throws Exception {
        InputStream exploitImage = new BufferedInputStream(getInstrumentation().getContext().getResources().openRawResource(
                R.raw.b65290323));
        BitmapFactory.decodeStream(exploitImage);
    }
}
