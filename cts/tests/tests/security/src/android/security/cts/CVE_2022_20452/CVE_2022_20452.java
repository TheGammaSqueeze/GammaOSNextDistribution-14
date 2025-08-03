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

// This PoC has been written taking reference from:
// File: frameworks/base/core/tests/coretests/src/android/os/BundleTest.java
// Function: readFromParcelWithRwHelper_whenThrowingAndDefusing_returnsNull()

package android.security.cts.CVE_2022_20452;

import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeNoException;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CVE_2022_20452 extends StsExtraBusinessLogicTestCase {

    @AsbSecurityTest(cveBugId = 240138318)
    @Test
    public void testPocCVE_2022_20452() {
        try {
            // Create a bundle with some parcelable object and a random string
            Bundle bundle = new Bundle();
            Parcelable parcelable = new CustomParcelable();
            bundle.putParcelable("keyParcelable", parcelable);
            bundle.putString("keyStr", "valStr");

            // Read bundle contents into a parcel and also set read write helper for the parcel
            Parcel parcelledBundle = Parcel.obtain();
            bundle.writeToParcel(parcelledBundle, 0);
            parcelledBundle.setDataPosition(0);
            parcelledBundle.setReadWriteHelper(new Parcel.ReadWriteHelper());

            // First set 'shouldDefuse' to true, then read contents of parcel into a bundle.
            // In presence of fix, this will cause a ClassNotFoundException because bundle will not
            // be able to find the class for 'CustomParcelable' as the class loader is not set, so
            // Parcel will not be read properly and the code will return without reading the string.
            Bundle.setShouldDefuse(true);
            Bundle testBundle = new Bundle();
            testBundle.readFromParcel(parcelledBundle);

            // If the vulnerability is active, we will be able to read string from bundle.
            assertNull("Vulnerable to b/240138318 !!", testBundle.getString("keyStr"));
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
