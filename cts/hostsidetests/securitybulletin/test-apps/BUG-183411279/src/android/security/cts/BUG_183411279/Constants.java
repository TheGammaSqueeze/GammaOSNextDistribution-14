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

package android.security.cts.BUG_183411279;

final class Constants {

    public static final String TAG = "BUG-183411279";
    public static final String TEST_APP_PACKAGE = Constants.class.getPackage().getName();
    public static final String CAR_SETTINGS_APP_PACKAGE = "com.android.car.settings";

    public static final String TAPJACKED_ACTIVITY_INTENT_ACTION = "android.settings.USER_SETTINGS";
    public static final String ACTION_START_TAPJACKING = TAG + ".start_tapjacking";
}
