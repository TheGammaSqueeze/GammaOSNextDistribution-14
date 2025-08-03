/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.os.cts;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.content.ActivityNotFoundException;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.image.DynamicSystemClient;
import android.platform.test.annotations.AppModeFull;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@AppModeFull(reason = "Instant apps cannot access DynamicSystemClient")
@SmallTest
@RunWith(AndroidJUnit4.class)
public class DynamicSystemClientTest implements DynamicSystemClient.OnStatusChangedListener {
    private boolean mUpdated;
    private Instrumentation mInstrumentation;

    private static final String DSU_PACKAGE_NAME = "com.android.dynsystem";

    private PackageManager getPackageManager() {
        return mInstrumentation.getContext().getPackageManager();
    }

    public void onStatusChanged(int status, int cause, long progress, Throwable detail) {
        mUpdated = true;
    }

    @Before
    public void setUp() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mInstrumentation.getUiAutomation().adoptShellPermissionIdentity();
    }

    @Test
    public void testDynamicSystemClient() {
        DynamicSystemClient dSClient = new DynamicSystemClient(mInstrumentation.getTargetContext());
        dSClient.setOnStatusChangedListener(this);
        try {
            dSClient.bind();
        } catch (SecurityException e) {
            fail();
        }
        Uri uri = Uri.parse("https://www.google.com/").buildUpon().build();
        mUpdated = false;
        try {
            dSClient.start(uri, 1024L << 10);
        } catch (ActivityNotFoundException e) {
            try {
                getPackageManager().getPackageInfo(DSU_PACKAGE_NAME, 0);
            } catch (PackageManager.NameNotFoundException ignore) {
                Assume.assumeNoException(ignore);
            }
            throw e;
        } catch (SecurityException e) {
            fail();
        }
        try {
            Thread.sleep(3 * 1000);
        } catch (InterruptedException e) {
            fail();
        }
        assertTrue(mUpdated);
        try {
            dSClient.unbind();
        } catch (SecurityException e) {
            fail();
        }
    }

    @Test
    public void testDynamicSystemClient_withoutOnStatusChangedListener() {
        DynamicSystemClient dSClient = new DynamicSystemClient(mInstrumentation.getTargetContext());
        try {
            dSClient.bind();
        } catch (SecurityException e) {
            fail();
        }
        Uri uri = Uri.parse("https://www.google.com/").buildUpon().build();
        try {
            dSClient.start(uri, 1024L << 10);
        } catch (ActivityNotFoundException e) {
            try {
                getPackageManager().getPackageInfo(DSU_PACKAGE_NAME, 0);
            } catch (PackageManager.NameNotFoundException ignore) {
                Assume.assumeNoException(ignore);
            }
            throw e;
        } catch (SecurityException e) {
            fail();
        }
        try {
            Thread.sleep(3 * 1000);
        } catch (InterruptedException e) {
            fail();
        }
        try {
            dSClient.unbind();
        } catch (SecurityException e) {
            fail();
        }
    }

    @After
    public void tearDown() {
        mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
    }
}
