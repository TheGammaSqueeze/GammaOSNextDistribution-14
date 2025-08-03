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

package android.app.fgstesthelper;

/**
 * A foreground service without a specific type.
 */
public class LocalForegroundServiceNoType extends LocalForegroundServiceBase {
    private static final String TAG = "LocalForegroundServiceNoType";
    private static final String NOTIFICATION_CHANNEL_ID = "cts/" + TAG;

    @Override
    String getNotificationChannelId() {
        return NOTIFICATION_CHANNEL_ID;
    }

    @Override
    String getTag() {
        return TAG;
    }

    @Override
    int getNotificationIcon() {
        return R.drawable.black;
    }
}
