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

/**
 * IBootDone - Interface for boot_done service in system_state_server
 */
interface IBootDone {
    /**
     * Port - Port name for the tipc
     */
    const String PORT = "com.android.trusty.boot_done.tidl";

    /**
     * set_boot_done() - Set value of boolean boot_done to 'true'
     *
     * This API allows the bootloader to notify Trusty TEE of the
     * boot complete state, after which handoff to the HLOS happens.
     * HLOS especially when unsigned, shall be granted a lower
     * privilege than the bootloader.
     * Upon being notified of the boot complete state, the TEE can
     * update the Normal World access policy, for example gating
     * connection to some ports or some specific APIs exposed by a port.
     *
     */
    void set_boot_done();
}
