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

package android.car.cts.powerpolicy;

public final class PowerPolicyConstants {
    public static final int VHAL_POWER_STATE_REQ_PROPERTY_ID = 289475072;
    public static final int VHAL_POWER_STATE_REP_PROPERTY_ID = 289475073;

    private PowerPolicyConstants() { }

    public static final class CarPowerState {
        public static final int INVALID = 0;
        public static final int WAIT_FOR_VHAL = 1;
        public static final int SUSPEND_ENTER = 2;
        public static final int SUSPEND_EXIT = 3;
        public static final int SHUTDOWN_ENTER = 5;
        public static final int ON = 6;
        public static final int SHUTDOWN_PREPARE = 7;
        public static final int SHUTDOWN_CANCELLED = 8;

        private CarPowerState() { }
    }

    public static final class VhalPowerStateReq {
        public static final int ON = 0;
        public static final int SHUTDOWN_PREPARE = 1;
        public static final int CANCEL_SHUTDOWN = 2;
        public static final int FINISHED = 3;

        private VhalPowerStateReq() { }
    }

    public static final class ShutdownParam {
        public static final int NOT_USED = 0;
        public static final int SHUTDOWN_IMMEDIATELY = 1;
        public static final int CAN_SLEEP = 2;
        public static final int SHUTDOWN_ONLY = 3;
        public static final int SLEEP_IMMEDIATELY = 4;

        private ShutdownParam() { }
    }
}
