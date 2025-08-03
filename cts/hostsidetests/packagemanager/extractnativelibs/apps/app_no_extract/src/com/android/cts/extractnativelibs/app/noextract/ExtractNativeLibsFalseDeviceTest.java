/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.cts.extractnativelibs.app.noextract;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Device test for extractNativeLibs=false */
@RunWith(AndroidJUnit4.class)
public class ExtractNativeLibsFalseDeviceTest {
    private final Context mContext =  InstrumentationRegistry.getContext();

    /** Test that the native lib dir exists but has no native lib file in it. */
    @Test
    public void testNativeLibsNotExtracted() {
        File nativeLibDir = new File(
                InstrumentationRegistry.getContext().getApplicationInfo().nativeLibraryDir);
        Assert.assertTrue(nativeLibDir.isDirectory());
        Assert.assertEquals(0, nativeLibDir.list().length);
    }

    /** Test that the native lib is loaded when the activity is launched. */
    @Test
    public void testNativeLibsLoaded() throws Exception {
        final CountDownLatch loaded = new CountDownLatch(1);
        IntentFilter filter = new IntentFilter(mContext.getPackageName() + ".NativeLibLoaded");
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                loaded.countDown();
            }
        };
        mContext.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED/*UNAUDITED*/);
        launchActivity();
        Assert.assertTrue("Native lib not loaded", loaded.await(
                30, TimeUnit.SECONDS));
    }

    private void launchActivity() {
        Intent launchIntent = mContext.getPackageManager().getLaunchIntentForPackage(
                mContext.getPackageName());
        Assert.assertNotNull(launchIntent);
        mContext.startActivity(launchIntent);
    }
}
