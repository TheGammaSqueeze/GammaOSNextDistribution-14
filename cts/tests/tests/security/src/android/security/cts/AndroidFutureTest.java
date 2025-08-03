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

import android.content.Context;
import android.content.Intent;
import android.os.BaseBundle;
import android.os.Bundle;
import android.os.Parcel;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.runner.AndroidJUnit4;
import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import static org.junit.Assert.assertFalse;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.lang.reflect.Field;

@RunWith(AndroidJUnit4.class)
public class AndroidFutureTest extends StsExtraBusinessLogicTestCase {

    @AsbSecurityTest(cveBugId =  197228210)
    @Test
    public void testAndroidFutureReadThrowable() throws Exception {
        String filePath = "/data/system/" + System.currentTimeMillis();
        String argAfterNewLine = "%h" + filePath.replaceFirst("^/+", "");
        Bundle bundle = createBundle("java.util.logging.FileHandler", "", argAfterNewLine);
        sendBundleToSystem(bundle);
        assertFalse(filePath + " should not be created", new File(filePath).exists());
    }

    private Bundle createBundle(String className, String argBeforeNewLine,
                                String argAfterNewLine) throws Exception {
        Parcel data = Parcel.obtain();
        data.writeInt(1);
        data.writeString("a");
        data.writeInt(4);
        data.writeString("com.android.internal.infra.AndroidFuture");
        data.writeBoolean(true);
        data.writeBoolean(true);
        data.writeBoolean(true);
        data.writeBoolean(false);
        data.writeString(className);
        data.writeString(argBeforeNewLine);
        data.writeString(argAfterNewLine);

        Bundle bundle = new Bundle();
        Field parcelledDataField = BaseBundle.class.getDeclaredField("mParcelledData");
        parcelledDataField.setAccessible(true);
        parcelledDataField.set(bundle, data);
        return bundle;
    }

    private void sendBundleToSystem(Bundle theBundle) throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        Context.class
                .getMethod("sendBroadcast",
                        Intent.class,
                        String.class,
                        Bundle.class)
                .invoke(context, new Intent("DUMMY_BROADCAST"), null, theBundle);
    }
}
