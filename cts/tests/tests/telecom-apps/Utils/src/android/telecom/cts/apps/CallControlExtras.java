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

package android.telecom.cts.apps;

import android.os.Bundle;

public class CallControlExtras {
    public static final String EXTRA_TELECOM_VIDEO_STATE = "EXTRA_TELECOM_VIDEO_STATE";
    public static final String EXTRA_TELECOM_DISCONNECT_CAUSE = "EXTRA_TELECOM_DISCONNECT_CAUSE";

    public static Bundle addVideoStateExtra(Bundle extras, int videoState) {
        extras.putInt(EXTRA_TELECOM_VIDEO_STATE, videoState);
        return extras;
    }

    public static int getVideoStateFromExtras(Bundle extras) {
        return extras.getInt(EXTRA_TELECOM_VIDEO_STATE);
    }

    public static boolean hasVideoStateExtra(Bundle extras) {
        return extras.containsKey(EXTRA_TELECOM_VIDEO_STATE);
    }


    public static Bundle addDisconnectCauseExtra(Bundle extras, int cause) {
        extras.putInt(EXTRA_TELECOM_DISCONNECT_CAUSE, cause);
        return extras;
    }

    public static int getDisconnectCauseFromExtras(Bundle extras) {
        return extras.getInt(EXTRA_TELECOM_DISCONNECT_CAUSE);
    }

    public static boolean hasDisconnectCauseExtra(Bundle extras) {
        return extras.containsKey(EXTRA_TELECOM_DISCONNECT_CAUSE);
    }
}
