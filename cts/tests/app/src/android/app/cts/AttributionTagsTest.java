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

package android.app.cts;

import static org.junit.Assert.assertEquals;

import android.app.stubs.LocalActivity;
import android.app.stubs.LocalProvider;
import android.app.stubs.LocalReceiver;
import android.app.stubs.LocalService;
import android.content.ComponentName;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Verify that {@code android:attributionTags} manifest attributes are honored
 * when associated components are instantiated.
 */
@RunWith(AndroidJUnit4.class)
public class AttributionTagsTest {
    private static final String TAG = "AttributionTagsTest";
    private static final String TEST_AUTHORITY = "android.app.stubs.provider";

    private Context mContext;
    private Context mTargetContext;
    private PackageManager mPackageManager;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getContext();
        mTargetContext = InstrumentationRegistry.getTargetContext();
        mPackageManager = mContext.getPackageManager();
    }

    @Test
    public void testActivity() throws Exception {
        // Verify manifest parsing
        final Intent intent = new Intent(mTargetContext, LocalActivity.class);
        assertEquals("localActivity",
                mPackageManager.getActivityInfo(intent.getComponent(), 0).attributionTags[0]);

        // Verify live behavior
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
        assertAttributionTag("activity", "localActivity");
    }

    @Test
    public void testActivityAlias() throws Exception {
        // Verify manifest parsing
        final Intent intent = new Intent();
        intent.setComponent(new ComponentName(mTargetContext.getPackageName(),
                "android.app.stubs.LocalActivityAlias"));
        assertEquals("localActivityAlias",
                mPackageManager.getActivityInfo(intent.getComponent(), 0).attributionTags[0]);

        // Verify live behavior
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
        assertAttributionTag("activity", "localActivityAlias");
    }

    @Test
    public void testService() throws Exception {
        // Verify manifest parsing
        final Intent intent = new Intent(mTargetContext, LocalService.class);
        assertEquals("localService",
                mPackageManager.getServiceInfo(intent.getComponent(), 0).attributionTags[0]);

        // Verify live behavior
        mContext.startService(intent);
        assertAttributionTag("service", "localService");
    }

    @Test
    public void testProvider() throws Exception {
        // Verify manifest parsing
        final Intent intent = new Intent(mTargetContext, LocalProvider.class);
        assertEquals("localProvider",
                mPackageManager.getProviderInfo(intent.getComponent(), 0).attributionTags[0]);

        // Probing for the attribution tag will itself spin up the provider as a
        // side-effect, so we have no special pre-work needed here
        assertAttributionTag("provider", "localProvider");
    }

    @Test
    public void testReceiver() throws Exception {
        // Verify manifest parsing
        final Intent intent = new Intent(mTargetContext, LocalReceiver.class);
        assertEquals("localReceiver",
                mPackageManager.getReceiverInfo(intent.getComponent(), 0).attributionTags[0]);

        // Verify live behavior
        mContext.sendBroadcast(intent);
        assertAttributionTag("receiver", "localReceiver");
    }

    private void assertAttributionTag(String method, String expected) throws Exception {
        try (ContentProviderClient client = mContext.getContentResolver()
                .acquireContentProviderClient(TEST_AUTHORITY)) {
            for (int i = 0; i < 10; i++) {
                final Bundle res = client.call(TEST_AUTHORITY, method, null, null);
                final String actual = res.getString(Intent.EXTRA_TITLE);
                if (actual == null) {
                    Log.v(TAG, "No attribution tag yet; waiting...");
                    SystemClock.sleep(500);
                    continue;
                } else {
                    assertEquals(expected, actual);
                    break;
                }
            }
        }
    }
}
