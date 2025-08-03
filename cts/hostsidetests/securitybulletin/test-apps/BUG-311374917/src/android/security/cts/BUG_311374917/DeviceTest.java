/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.security.cts.BUG_311374917;

import static android.content.pm.PackageManager.GET_META_DATA;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class DeviceTest {

    @Test
    public void testBUG_311374917() {
        try {
            Context context = getApplicationContext();
            ApplicationInfo appInfo =
                    context.getPackageManager()
                            .getApplicationInfo(context.getPackageName(), GET_META_DATA);

            // Without Fix, app will have privileged access and test fails
            assertWithMessage("The device is vulnerable to b/311374917 !!")
                    .that(appInfo.isPrivilegedApp())
                    .isFalse();
        } catch (Exception e) {
            assume().that(e).isNull();
        }
    }
}
