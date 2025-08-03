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
package com.android.se;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.ServiceManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class SecureElementVendorStableInterfaceFlagTest {

    private static final String TAG =
            SecureElementVendorStableInterfaceFlagTest.class.getSimpleName();
    public static final String VSTABLE_SECURE_ELEMENT_SERVICE =
            "android.se.omapi.ISecureElementService/default";
    private Context mContext;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
        PackageManager pm = mContext.getPackageManager();
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testIsVendorStableInterfaceEnabled() {
        boolean secure_element_vintf_enabled =
                mContext.getResources().getBoolean(R.bool.secure_element_vintf_enabled);

        IBinder binder = ServiceManager.getService(VSTABLE_SECURE_ELEMENT_SERVICE);
        if (secure_element_vintf_enabled) {
            Assert.assertNotNull(binder);
        } else {
            Assert.assertNull(binder);
        }

    }
}
