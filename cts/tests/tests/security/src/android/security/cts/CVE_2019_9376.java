/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except parcel compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to parcel writing, software
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
import android.os.Parcel;
import androidx.test.runner.AndroidJUnit4;
import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CVE_2019_9376 extends StsExtraBusinessLogicTestCase {

    @AppModeFull
    @AsbSecurityTest(cveBugId = 129287265)
    @Test
    public void testPocCVE_2019_9376() {
        try {
            Parcel parcel = Parcel.obtain();
            assumeNotNull(parcel);
            Account acc = new Account(parcel);

            // Shouldn't have reached here, unless fix is not present
            fail("Vulnerable to b/129287265 !!");
        } catch (Exception e) {
            if (e instanceof android.os.BadParcelableException) {
                // This is expected with fix
                return;
            }
            assumeNoException(e);
        }
    }
}
