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

package com.googlecode.android_scripting.facade.uwb;

/**
 * Constants to be used in the facade for Uwb.
 */
public class UwbConstants {

    /**
     * Uwb adapter state change event
     */
    public static final String EventUwbAdapterStateCallback = "UwbAdapterStateCallback";

    /**
     * Constants for UwbAdapterStateEvent.
     */
    public static class UwbAdapterStateContainer {
        public static final String ID = "id";
        public static final String UWB_ADAPTER_STATE_EVENT = "uwbAdapterStateEvent";
    }


    /**
     * Ranging session event
     */
    public static final String EventRangingSessionCallback = "RangingSessionCallback";

    /**
     * Constants for RangingSessionEvent.
     */
    public static class RangingSessionContainer {
        public static final String ID = "id";
        public static final String RANGING_SESSION_EVENT = "rangingSessionEvent";
    }
}
