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

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeNoException;

import android.content.Context;
import android.net.Uri;
import android.os.Parcel;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CVE_2022_20338 extends StsExtraBusinessLogicTestCase {

    // b/171966843
    // Vulnerable package : framework.jar
    // Vulnerable app     : Not applicable
    @AsbSecurityTest(cveBugId = 171966843)
    @Test
    public void testPocCVE_2022_20338() {
        try {
            Context context = getInstrumentation().getContext();

            final int representation = 1 /* REPRESENTATION_ENCODED */;
            Parcel parcel = Parcel.obtain();
            parcel.writeInt(3 /* HierarchicalUri.TYPE_ID */);
            parcel.writeByteArray((context.getString(R.string.cve_2022_20338_scheme)).getBytes());
            parcel.writeInt(representation);
            parcel.writeByteArray(
                    (context.getString(R.string.cve_2022_20338_authority)).getBytes());
            parcel.writeInt(representation);
            parcel.writeByteArray((context.getString(R.string.cve_2022_20338_path)).getBytes());
            parcel.writeInt(representation);
            parcel.writeByteArray(null /* query */);
            parcel.writeInt(representation);
            parcel.writeByteArray(null /* fragment */);
            parcel.setDataPosition(0);
            Uri uri = Uri.CREATOR.createFromParcel(parcel);

            // on vulnerable device, the uri format will be incorrect due to improper input
            // validation. The test fails if the uri string matches the invalidURL.
            assertFalse(
                    context.getString(R.string.cve_2022_20338_failMsg),
                    uri.toString().equals((context.getString(R.string.cve_2022_20338_invalidURL))));
        } catch (Exception e) {
            assumeNoException(e);
        }
    }
}
