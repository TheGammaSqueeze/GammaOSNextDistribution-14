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

package android.security.cts;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeNotNull;

import android.hardware.location.GeofenceHardwareRequestParcelable;
import android.os.BadParcelableException;
import android.os.Parcel;
import android.os.Parcelable;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

@AppModeFull
@RunWith(AndroidJUnit4.class)
public class CVE_2022_20142 extends StsExtraBusinessLogicTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 216631962)
    public void testPocCVE_2022_20142() {
        Parcelable.Creator<GeofenceHardwareRequestParcelable> obj =
                GeofenceHardwareRequestParcelable.CREATOR;
        assumeNotNull(obj);
        Parcel parcel = Parcel.obtain();
        assumeNotNull(parcel);

        // any integer which is not equal to
        // GeofenceHardwareRequest.GEOFENCE_TYPE_CIRCLE
        parcel.writeInt(1024);

        // reset the position so that reads start from the beginning
        parcel.setDataPosition(0);

        try {
            obj.createFromParcel(parcel);
        } catch (Exception ex) {
            if (ex instanceof BadParcelableException) {
                // expected with fix
                return;
            }
            assumeNoException(ex);
        }
        parcel.recycle();
        fail("Vulnerable to b/216631962 !!");
    }
}
