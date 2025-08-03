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

package com.android.sts.common;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;

import java.util.Optional;
import java.util.concurrent.TimeoutException;

public class NativePocMallocDebugAsserter implements NativePocAsserter {
    private final String mallocDebugOptions;
    private final Optional<String> mallocDebugOnService;

    /**
     * Returns a NativePocAsserter that attaches libc malloc debug to a service before running the
     * PoC and checks for any malloc debug error on that service while the poc runs.
     */
    public static NativePocAsserter assertNoMallocDebugErrorOnService(
            String options, String service) {
        return new NativePocMallocDebugAsserter(options, service);
    }

    /**
     * Returns a NativePocAsserter that attaches libc malloc debug to the PoC and checks for any
     * malloc debug error while the poc runs.
     */
    public static NativePocAsserter assertNoMallocDebugErrorOnPoc(String options) {
        return new NativePocMallocDebugAsserter(options, null);
    }

    private NativePocMallocDebugAsserter(String options, String service) {
        this.mallocDebugOptions = options;
        this.mallocDebugOnService = Optional.ofNullable(service);
    }

    @Override
    public AutoCloseable withAutoCloseable(NativePoc nativePoc, ITestDevice device)
            throws DeviceNotAvailableException, TimeoutException, ProcessUtil.KillException {
        if (mallocDebugOnService.isPresent()) {
            return MallocDebug.withLibcMallocDebugOnService(
                    device, mallocDebugOptions, mallocDebugOnService.get());
        } else {
            return MallocDebug.withLibcMallocDebugOnNewProcess(
                    device, mallocDebugOptions, nativePoc.pocName());
        }
    }
}
