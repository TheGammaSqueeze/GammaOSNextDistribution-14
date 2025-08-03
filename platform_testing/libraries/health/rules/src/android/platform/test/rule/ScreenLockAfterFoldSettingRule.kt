/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.platform.test.rule

import android.provider.Settings

/** Rule to control [Settings.System.FOLD_LOCK_BEHAVIOR] */
class ScreenLockAfterFoldSettingRule :
    SystemSettingRule<String>(settingName = Settings.System.FOLD_LOCK_BEHAVIOR) {

    fun setStayAwakeOnFold() {
        setSettingValue(SETTING_VALUE_STAY_AWAKE_ON_FOLD)
    }

    fun setSelectiveStayAwake() {
        setSettingValue(SETTING_VALUE_SELECTIVE_STAY_AWAKE)
    }

    fun setSleepOnFold() {
        setSettingValue(SETTING_VALUE_SLEEP_ON_FOLD)
    }

    private companion object {
        const val SETTING_VALUE_STAY_AWAKE_ON_FOLD = "stay_awake_on_fold_key"
        const val SETTING_VALUE_SELECTIVE_STAY_AWAKE = "selective_stay_awake_key"
        const val SETTING_VALUE_SLEEP_ON_FOLD = "sleep_on_fold_key"
    }
}
