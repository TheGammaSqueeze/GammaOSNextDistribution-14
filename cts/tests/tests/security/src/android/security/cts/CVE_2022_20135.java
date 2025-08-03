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

import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeNotNull;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Field;

@AppModeFull
@RunWith(AndroidJUnit4.class)
public class CVE_2022_20135 extends StsExtraBusinessLogicTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 220303465)
    public void testPocCVE_2022_20135() {
        Bundle bundle = new Bundle();
        try {
            Class clazz = Class.forName("android.service.gatekeeper.GateKeeperResponse");
            assumeNotNull(clazz);
            Object obj = clazz.getMethod("createGenericResponse", int.class).invoke(null, 0);
            assumeNotNull(obj);
            Field field = clazz.getDeclaredField("mPayload");
            assumeNotNull(field);
            field.setAccessible(true);
            field.set(obj, new byte[0]);
            bundle.putParcelable("1", (Parcelable) obj);
            bundle.putByteArray("2", new byte[1000]);
        } catch (Exception ex) {
            assumeNoException(ex);
        }
        Parcel parcel = Parcel.obtain();
        assumeNotNull(parcel);
        parcel.writeBundle(bundle);
        parcel.setDataPosition(0);
        Bundle newBundle = new Bundle();
        newBundle.readFromParcel(parcel);
        newBundle.keySet();
    }
}
