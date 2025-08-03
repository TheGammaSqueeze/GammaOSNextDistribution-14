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
package android.localemanager.cts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.LocaleList;

import org.junit.Assert;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Used to dynamically register a receiver in instrumentation test.
 *
 * <p>This is used to listen to the following:
 * <ul>
 * <li> Broadcasts sent to the current app instrumenting the test. The broadcast is sent by the
 * service being tested.
 * <li> Response sent by other apps(TestApp, InstallerApp) to the tests.
 * </ul>
 */
public class BlockingBroadcastReceiver extends BroadcastReceiver {
    private static final String EXTRA_IME_ID = "input_method_id";

    private CountDownLatch mLatch = new CountDownLatch(1);
    private String mPackageName;
    private LocaleList mLocales;
    private String mInputMethodId;
    private int mCalls;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.hasExtra(Intent.EXTRA_PACKAGE_NAME)) {
            mPackageName = intent.getStringExtra(Intent.EXTRA_PACKAGE_NAME);
        }
        if (intent.hasExtra(Intent.EXTRA_LOCALE_LIST)) {
            mLocales = intent.getParcelableExtra(Intent.EXTRA_LOCALE_LIST);
        }
        if (intent.hasExtra(EXTRA_IME_ID)) {
            mInputMethodId = intent.getStringExtra(EXTRA_IME_ID);
        }
        mCalls += 1;
        mLatch.countDown();
    }

    public String getPackageName() {
        return mPackageName;
    }

    public LocaleList getLocales() {
        return mLocales;
    }

    public String getInputMethodId() {
        return mInputMethodId;
    }

    public boolean await() throws Exception {
        return mLatch.await(5, TimeUnit.SECONDS);
    }

    public void reset() {
        mLatch = new CountDownLatch(1);
        mCalls = 0;
        mPackageName = null;
        mLocales = null;
        mInputMethodId = null;
    }

    /**
     * Waits for a while and checks no broadcasts are received.
     */
    public void assertNoBroadcastReceived() throws Exception {
        await();
        Assert.assertEquals(0, mCalls);
    }
}
