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

package android.car.apitest;

import static com.google.common.truth.Truth.assertWithMessage;

import android.annotation.UserIdInt;
import android.car.settings.CarSettings;
import android.content.ContentResolver;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@SmallTest
public final class CarSettingsTest extends CarApiTestBase {

    private static final String TAG = CarSettingsTest.class.getSimpleName();
    private static final String SCOPE_GLOBAL = "Global";
    private static final String SCOPE_SECURE = "Secure";

    private final HashMap<String, String> mSettings = new HashMap<>();
    private final @UserIdInt int mUserId = UserHandle.USER_CURRENT;

    private final ContentResolver mContentResolver;

    public CarSettingsTest() throws Exception {
        mContentResolver = getContext().getContentResolver();
    }

    @Test
    public void testCarSettingsNames() throws Exception {
        loadSettingNames();

        boolean isAllSettingsReadable = checkAllSettingsReadable();

        assertWithMessage("car settings readable").that(isAllSettingsReadable).isTrue();
    }

    private void loadSettingNames() {
        mSettings.put(CarSettings.Global.DEFAULT_USER_RESTRICTIONS_SET, SCOPE_GLOBAL);
        mSettings.put(CarSettings.Global.SYSTEM_BAR_VISIBILITY_OVERRIDE, SCOPE_GLOBAL);
        mSettings.put(CarSettings.Global.DISABLE_INSTRUMENTATION_SERVICE, SCOPE_GLOBAL);
        mSettings.put(CarSettings.Global.ENABLE_USER_SWITCH_DEVELOPER_MESSAGE, SCOPE_GLOBAL);
        mSettings.put(CarSettings.Global.LAST_ACTIVE_USER_ID, SCOPE_GLOBAL);
        mSettings.put(CarSettings.Global.LAST_ACTIVE_PERSISTENT_USER_ID, SCOPE_GLOBAL);
        mSettings.put(CarSettings.Secure.KEY_AUDIO_FOCUS_NAVIGATION_REJECTED_DURING_CALL,
                SCOPE_SECURE);
        mSettings.put(CarSettings.Secure.KEY_AUDIO_PERSIST_VOLUME_GROUP_MUTE_STATES, SCOPE_SECURE);
        mSettings.put(CarSettings.Secure.KEY_ENABLE_INITIAL_NOTICE_SCREEN_TO_USER, SCOPE_SECURE);
        mSettings.put(CarSettings.Secure.KEY_SETUP_WIZARD_IN_PROGRESS, SCOPE_SECURE);

        mSettings.put(CarSettings.Secure.KEY_BLUETOOTH_DEVICES, SCOPE_SECURE);
    }

    private boolean checkAllSettingsReadable() throws Exception {
        Iterator<Map.Entry<String, String>> settingEntrys = mSettings.entrySet().iterator();
        while (settingEntrys.hasNext()) {
            Map.Entry<String, String> entry = settingEntrys.next();
            String name = entry.getKey();
            String scope = entry.getValue();
            switch (scope) {
                case SCOPE_GLOBAL:
                    Settings.Global.getString(mContentResolver, name);
                    break;
                case SCOPE_SECURE:
                    Settings.Secure.getStringForUser(mContentResolver, name, mUserId);
                    break;
                default:
                    Log.e(TAG, "unsupported scope: " + scope);
                    return false;
            }
        }
        return true;
    }
}
