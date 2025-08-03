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

package android.security.cts;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeNotNull;

import android.accounts.Account;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AsbSecurityTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CVE_2021_0934 extends StsExtraBusinessLogicTestCase {

    @AppModeFull
    @AsbSecurityTest(cveBugId = 169762606)
    @Test
    public void testPocCVE_2021_0934() {
        try {
            // Creating an account with arguments 'name' and 'type' whose
            // lengths are greater than 200
            String name = new String(new char[300]).replace("\0", "n");
            String type = new String(new char[300]).replace("\0", "t");
            Account acc = new Account(name, type);
            assumeNotNull(acc);

            // Shouldn't have reached here, unless fix is not present
            fail("Vulnerable to b/169762606, allowing account name/type "
                    + "with character count 300 whereas limit is 200");
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                // This is expected with fix
                return;
            }
            assumeNoException(e);
        }
    }
}
