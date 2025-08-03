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
package com.android.server.uwb.util;

import android.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Constants used by UWB modules.
 */
public class Constants {

    public static final byte[] FIRA_APPLET_AID =
            new byte[] { (byte) 0xA0, (byte) 0x00, (byte) 0x00, (byte) 0x08,
                    (byte) 0x67, (byte) 0x46, (byte) 0x41, (byte) 0x50, (byte) 0x00 };

    /**
     * The UWB session type
     */
    @IntDef(prefix = {"UWB_SESSION_TYPE_"}, value = {
            UWB_SESSION_TYPE_UNICAST,
            UWB_SESSION_TYPE_MULTICAST,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface UwbSessionType {
    }

    /**
     * Unicast UWB session (1 controller, 1 controlee).
     */
    public static final int UWB_SESSION_TYPE_UNICAST = 0;
    /**
     * Multicast UWB session (1 controller, multiple controlees).
     */
    public static final int UWB_SESSION_TYPE_MULTICAST = 1;

    private Constants() {
    }
}
