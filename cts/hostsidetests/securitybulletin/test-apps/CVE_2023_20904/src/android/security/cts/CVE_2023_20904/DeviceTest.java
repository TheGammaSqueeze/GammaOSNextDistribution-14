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

package android.security.cts.CVE_2023_20904;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeNoException;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;

@RunWith(AndroidJUnit4.class)
public class DeviceTest {
    Context mContext;

    private String getSettingsPkgName() {
        Intent settingsIntent = new Intent(Settings.ACTION_SETTINGS);
        ComponentName settingsComponent =
                settingsIntent.resolveActivity(mContext.getPackageManager());
        String pkgName = settingsComponent != null ? settingsComponent.getPackageName()
                : mContext.getString(R.string.defaultSettingsPkg);
        return pkgName;
    }

    @Test
    public void testgetTrampolineIntent() {
        try {
            mContext = getApplicationContext();
            String settingsPkg = getSettingsPkgName();
            Context settingsContext = mContext.createPackageContext(settingsPkg,
                    Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);

            // Invoking getTrampolineIntent method using reflection
            ClassLoader settingsClassLoader = settingsContext.getClassLoader();
            Class<?> SettingsActivityClass = settingsClassLoader
                    .loadClass(settingsPkg + mContext.getString(R.string.settingsActivity));
            Method getTrampolineIntentMethod = SettingsActivityClass.getDeclaredMethod(
                    mContext.getString(R.string.getTrampolineIntent), Intent.class, String.class);
            getTrampolineIntentMethod.setAccessible(true);
            Intent intent = new Intent();
            intent.setSelector(new Intent(mContext.getString(R.string.selectorIntent)));
            Intent trampolineIntent = (Intent) getTrampolineIntentMethod.invoke(null, intent, "");
            Bundle bundle = trampolineIntent.getExtras();
            assertFalse(mContext.getString(R.string.failMessage),
                    bundle.get(Settings.EXTRA_SETTINGS_EMBEDDED_DEEP_LINK_INTENT_URI).toString()
                            .contains(mContext.getString(R.string.selectorIntent)));
        } catch (Exception e) {
            assumeNoException(e);
        }
    }

}
