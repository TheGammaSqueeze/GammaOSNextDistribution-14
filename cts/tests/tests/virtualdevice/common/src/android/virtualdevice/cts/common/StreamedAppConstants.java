/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.virtualdevice.cts.common;

import android.content.ComponentName;

public class StreamedAppConstants {

    public static final String STREAMED_APP_PACKAGE = "android.virtualdevice.streamedtestapp";

    public static final ComponentName CLIPBOARD_TEST_ACTIVITY = new ComponentName(
            STREAMED_APP_PACKAGE, STREAMED_APP_PACKAGE + ".ClipboardTestActivity");

    public static final ComponentName PERMISSION_TEST_ACTIVITY = new ComponentName(
            STREAMED_APP_PACKAGE, STREAMED_APP_PACKAGE + ".PermissionTestActivity");

    public static final ComponentName DEFAULT_HOME_ACTIVITY = new ComponentName(
            STREAMED_APP_PACKAGE, STREAMED_APP_PACKAGE + ".HomeActivity");

    public static final ComponentName CUSTOM_HOME_ACTIVITY = new ComponentName(
            STREAMED_APP_PACKAGE, STREAMED_APP_PACKAGE + ".CustomHomeActivity");

    // The test activity should attempt to write a ClipData to the clipboard.
    public static final String ACTION_WRITE = "writeClipboard";

    // The test activity should attempt to read the clipboard contents and send them back.
    public static final String ACTION_READ = "readClipboard";

    // A boolean used to return the result of hasPrimaryClip()
    public static final String EXTRA_HAS_CLIP_DATA = "hasClipData";

    // A parcelable containing ClipData that was written or read from the clipboard.
    public static final String EXTRA_CLIP_DATA = "clipData";

    // The id of the device whose clipboard to access.
    public static final String EXTRA_DEVICE_ID = "deviceId";

    // The name of the permission to request.
    public static final String EXTRA_PERMISSION_NAME = "permissionName";
}
