/*
 * Copyright (C) 2015 The Android Open Source Project
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
package android.car.apitest;

import static com.google.common.truth.Truth.assertThat;

import android.car.content.pm.AppBlockingPackageInfo;
import android.car.content.pm.CarAppBlockingPolicy;
import android.os.Parcel;
import android.util.Log;

import androidx.test.filters.SmallTest;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;

@SmallTest
public final class CarAppBlockingPolicyTest extends CarLessApiTestBase {
    private static final String TAG = AppBlockingPackageInfoTest.class.getSimpleName();

    @Test
    @ApiTest(apis = {"android.car.content.pm.AppBlockingPackageInfo#CREATOR",
            "android.car.content.pm.CarAppBlockingPolicy#CREATOR"})
    public void testParcelling() throws Exception {
        AppBlockingPackageInfo carServiceInfo =
                AppBlockingPackageInfoTest.createInfoCarService(mContext);
        AppBlockingPackageInfo selfInfo =
                AppBlockingPackageInfoTest.createInfoSelf(mContext);
        // this is only for testing parcelling. contents has nothing to do with actual app blocking.
        AppBlockingPackageInfo[] allowlists = new AppBlockingPackageInfo[] { carServiceInfo,
                selfInfo };
        AppBlockingPackageInfo[] denylists = new AppBlockingPackageInfo[] { selfInfo };
        CarAppBlockingPolicy policyExpected = new CarAppBlockingPolicy(allowlists, denylists);
        Parcel dest = Parcel.obtain();
        policyExpected.writeToParcel(dest, 0);
        dest.setDataPosition(0);
        CarAppBlockingPolicy policyRead = new CarAppBlockingPolicy(dest);
        Log.i(TAG, "expected:" + policyExpected + ",read:" + policyRead);
        assertThat(policyRead).isEqualTo(policyExpected);
    }
}
