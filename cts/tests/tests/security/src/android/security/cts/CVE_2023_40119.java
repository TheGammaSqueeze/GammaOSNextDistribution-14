/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeNoException;

import android.net.Uri;
import android.os.Parcel;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CVE_2023_40119 extends StsExtraBusinessLogicTestCase {

    @AsbSecurityTest(cveBugId = 231476072)
    @Test
    public void testPocCVE_2023_40119() {
        try {
            Parcel parcel = Parcel.obtain();

            // Create an evil URI passing a scheme (that hides an unsafe authority and the ssp both)
            // , a fake ssp and a fragment to Uri.fromParts() and write the evil URI to a parcel.
            Uri.fromParts(
                            "scheme://notAllowedAuthorityAndSsp" /* scheme */,
                            "allowedAuthorityAndSsp" /* scheme-specific-part */,
                            "fragment" /* fragment */)
                    .writeToParcel(parcel, 0 /* No additional flags or options */);
            parcel.setDataPosition(0);

            Uri uriFromParcel = Uri.CREATOR.createFromParcel(parcel);

            // Without fix, the scheme from parsing the string representation of uriFromParcel will
            // return "scheme" as Uri parser checks for the delimiter ':' but the scheme from
            // uriFromParcel.getScheme() will return "scheme://notAllowedAuthorityAndSsp" which
            // hides the "not allowed" authority ("notAllowedAuthorityAndSsp") in the scheme
            // enabling bypass of authority checks.
            assertEquals(
                    "Vulnerable to b/231476072 !!, URIs are not canonicalized across AIDL"
                        + " boundaries",
                    Uri.parse(uriFromParcel.toString()).getScheme(),
                    uriFromParcel.getScheme());
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
