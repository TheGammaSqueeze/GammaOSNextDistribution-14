/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static org.junit.Assert.*;

import android.platform.test.annotations.AsbSecurityTest;
import android.security.cts.R;

import android.content.Context;
import android.content.Intent;
import android.os.BaseBundle;
import android.os.Bundle;
import android.os.Parcel;
import android.util.Log;
import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import java.io.File;
import java.lang.reflect.Field;

import org.junit.runner.RunWith;
import org.junit.Test;

@RunWith(AndroidJUnit4.class)
public class ParcelableExceptionTest extends StsExtraBusinessLogicTestCase {

    @AsbSecurityTest(cveBugId = 65281159)
    @Test
    public void test_CVE_2017_0871() throws Exception {
        String filePath = "/data/system/" + System.currentTimeMillis();
        File file = new File(filePath);
        Bundle bundle = createBundle("java.io.FileOutputStream", filePath);
        sendBundleToSystem(bundle);
        assertFalse(file.exists());
    }

    private Bundle createBundle(String className, String constructorArgument) throws Exception {
        Parcel data = Parcel.obtain();
        data.writeInt(1);
        data.writeString("a");
        data.writeInt(4);
        data.writeString("android.os.ParcelableException");
        data.writeString(className);
        data.writeString(constructorArgument);

        Bundle bundle = new Bundle();
        Field parcelledDataField = BaseBundle.class.getDeclaredField("mParcelledData");
        parcelledDataField.setAccessible(true);
        parcelledDataField.set(bundle, data);
        return bundle;
    }

    private void sendBundleToSystem(Bundle theBundle) throws Exception {
        Context.class
                .getMethod("sendBroadcast",
                        Intent.class,
                        String.class,
                        Bundle.class)
                .invoke(getContext(), new Intent("DUMMY_BROADCAST"), null, theBundle);
    }
}
