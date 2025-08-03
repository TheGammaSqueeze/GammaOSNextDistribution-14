/**
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

package com.android.cts.rolesecuritytest;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assume.*;

import android.app.Instrumentation;
import android.app.role.RoleManager;
import android.content.Context;
import android.os.Build;
import android.os.UserHandle;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.mainline.MainlineModule;
import com.android.compatibility.common.util.mainline.ModuleDetector;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.Test;

@RunWith(AndroidJUnit4.class)
public class DeviceTest {
    private Instrumentation mInstrumentation;

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
    }

    @Test
    public void cannotGetSmsRoleHolderForAnotherUser() throws Exception {
        assumeFalse(ModuleDetector
                .moduleIsPlayManaged(getInstrumentation().getContext().getPackageManager(),
                    MainlineModule.PERMISSION_CONTROLLER));
        assertNotEquals("This test should be run in a secondary user", UserHandle.USER_SYSTEM,
                UserHandle.myUserId());
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return;
        }
        boolean exploitSuccessful = false;
        try {
            final Context context = ApplicationProvider.getApplicationContext();
            final RoleManager roleManager = context.getSystemService(RoleManager.class);
            final Field serviceField = roleManager.getClass().getDeclaredField("mService");
            serviceField.setAccessible(true);
            final Object roleService = serviceField.get(roleManager);
            final String getSmsRoleHolderMethodName = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    ? "getSmsRoleHolder" : "getDefaultSmsPackage";
            final Method getSmsRoleHolderMethod = roleService.getClass().getMethod(
                    getSmsRoleHolderMethodName, int.class);
            getSmsRoleHolderMethod.invoke(roleService, UserHandle.USER_SYSTEM);
            exploitSuccessful = true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        assertFalse("Exploit succeeded", exploitSuccessful);
    }

    private Instrumentation getInstrumentation() {
        return mInstrumentation;
    }
}
