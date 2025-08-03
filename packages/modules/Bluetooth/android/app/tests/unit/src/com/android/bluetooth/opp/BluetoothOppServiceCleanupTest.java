/*
 * Copyright 2024 The Android Open Source Project
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

package com.android.bluetooth.opp;

import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;

import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.platform.test.flag.junit.SetFlagsRule;

import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.BluetoothMethodProxy;
import com.android.bluetooth.TestUtils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.flags.Flags;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class BluetoothOppServiceCleanupTest {
    @Rule public final SetFlagsRule mSetFlagsRule = new SetFlagsRule();

    private boolean mIsAdapterServiceSet;

    private Context mTargetContext;

    @Mock private AdapterService mAdapterService;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mTargetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        TestUtils.setAdapterService(mAdapterService);
        mIsAdapterServiceSet = true;
    }

    @After
    public void tearDown() throws Exception {
        BluetoothMethodProxy.setInstanceForTesting(null);

        if (mIsAdapterServiceSet) {
            TestUtils.clearAdapterService(mAdapterService);
        }
    }

    @Test
    @UiThreadTest
    public void testStopAndCleanup() {
        mSetFlagsRule.enableFlags(
                Flags.FLAG_OPP_SERVICE_FIX_INDEX_OUT_OF_BOUNDS_EXCEPTION_IN_UPDATE_THREAD);

        // Don't need to disable again since it will be handled in OppService.stop
        enableBtOppProvider();

        // Add thousands of placeholder rows
        for (int i = 0; i < 2000; i++) {
            ContentValues values = new ContentValues();
            mTargetContext.getContentResolver().insert(BluetoothShare.CONTENT_URI, values);
        }

        try {
            BluetoothOppService service = new BluetoothOppService(mTargetContext);
            service.start();
            service.setAvailable(true);

            // Call stop while UpdateThread is running.
            service.stop();
            service.cleanup();
        } finally {
            mTargetContext.getContentResolver().delete(BluetoothShare.CONTENT_URI, null, null);
        }
    }

    private void enableBtOppProvider() {
        mTargetContext
                .getPackageManager()
                .setApplicationEnabledSetting(
                        mTargetContext.getPackageName(),
                        COMPONENT_ENABLED_STATE_ENABLED,
                        DONT_KILL_APP);

        ComponentName activityName =
                new ComponentName(mTargetContext, BluetoothOppProvider.class.getCanonicalName());
        mTargetContext
                .getPackageManager()
                .setComponentEnabledSetting(
                        activityName, COMPONENT_ENABLED_STATE_ENABLED, DONT_KILL_APP);
    }
}
