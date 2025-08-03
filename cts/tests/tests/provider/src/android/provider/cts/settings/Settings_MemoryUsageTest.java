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

package android.provider.cts.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.testng.Assert.expectThrows;

import android.content.ContentResolver;
import android.content.Context;
import android.os.Process;
import android.os.SystemClock;
import android.provider.Settings;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.common.base.Strings;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class Settings_MemoryUsageTest {
    private static final String STRING_SETTING = Settings.System.SCREEN_BRIGHTNESS;
    private static final int sUserId = Process.myUserHandle().getIdentifier();

    private ContentResolver mContentResolver;
    private String mOldSettingValue;

    @Before
    public void setUp() throws Exception {
        final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final String packageName = context.getPackageName();
        InstrumentationRegistry.getInstrumentation().getUiAutomation().executeShellCommand(
                "appops set --user " + sUserId + " " + packageName
                        + " android:write_settings allow");
        // Wait a beat to persist the change
        SystemClock.sleep(500);
        mContentResolver = context.getContentResolver();
        assertNotNull(mContentResolver);
        mOldSettingValue = Settings.System.getString(mContentResolver, STRING_SETTING);
    }

    @After
    public void cleanUp() {
        Settings.System.putString(mContentResolver, STRING_SETTING, mOldSettingValue);
        assertEquals(mOldSettingValue, Settings.System.getString(mContentResolver, STRING_SETTING));
    }

    @Test
    public void testMemoryUsageExceeded() {
        expectThrows(Exception.class,
                () -> Settings.System.putString(
                        mContentResolver, STRING_SETTING, Strings.repeat("A", 30001)));
        // Repeated calls should throw as well
        expectThrows(Exception.class,
                () -> Settings.System.putString(
                        mContentResolver, STRING_SETTING, Strings.repeat("A", 30001)));
    }
}
