/**
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

package android.security.cts;

import android.platform.test.annotations.AsbSecurityTest;
import androidx.test.runner.AndroidJUnit4;
import com.android.sts.common.util.StsExtraBusinessLogicTestCase;
import dalvik.system.VMRuntime;
import org.junit.runner.RunWith;
import org.junit.Test;
import static org.junit.Assert.assertFalse;

@RunWith(AndroidJUnit4.class)
public class CVE_2021_0394 extends StsExtraBusinessLogicTestCase {
    static {
        System.loadLibrary("ctssecurity_jni");
    }

    /**
     * b/172655291
     */
    @Test
    @AsbSecurityTest(cveBugId = 172655291)
    public void testPocCVE_2021_0394() throws Exception {
        VMRuntime vmRuntime = VMRuntime.getRuntime();
        if (!vmRuntime.isCheckJniEnabled()) {
            assertFalse(poc());
        }
    }

    public static native boolean poc();
}
