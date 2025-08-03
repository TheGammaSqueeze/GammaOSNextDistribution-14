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

package android.credentials.cts.unittests.service;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.SigningInfo;
import android.credentials.cts.unittests.TestUtils;
import android.platform.test.annotations.AppModeFull;
import android.service.credentials.CallingAppInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@AppModeFull(reason = "unit test")
@RunWith(AndroidJUnit4.class)
public class CallingAppInfoTest {

    final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    final String mPackageName = mContext.getOpPackageName();
    final SigningInfo mSigningInfo = mContext.getPackageManager().getPackageInfo(mPackageName,
            PackageManager.PackageInfoFlags.of(
                    PackageManager.GET_SIGNING_CERTIFICATES)).signingInfo;

    public CallingAppInfoTest() throws PackageManager.NameNotFoundException {
    }

    @Test
    public void testConstructor_nullPackageName_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new CallingAppInfo(null, mSigningInfo));
    }

    @Test
    public void testConstructor_emptyPackageName_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> new CallingAppInfo("", mSigningInfo));
    }

    @Test
    public void testConstructor_nullSigningInfo_throwsNPE() {
        assertThrows(NullPointerException.class, () -> new CallingAppInfo(mPackageName, null));
    }

    @Test
    public void testConstructor_success() {
        final CallingAppInfo info = new CallingAppInfo(mPackageName, mSigningInfo);
        assertThat(info.getPackageName()).isEqualTo(mPackageName);
        assertThat(info.getSigningInfo()).isSameInstanceAs(mSigningInfo);
    }

    @Test
    public void testWriteToParcel_success() {
        final CallingAppInfo info1 = new CallingAppInfo(mPackageName, mSigningInfo);

        final CallingAppInfo info2 = TestUtils.cloneParcelable(info1);

        assertThat(info2.getPackageName()).isEqualTo(info1.getPackageName());
        TestUtils.assertEquals(info2.getSigningInfo(), info1.getSigningInfo());
    }
}
