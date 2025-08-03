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

package android.car.builtin.os;

import android.annotation.SystemApi;
import android.os.Trace;

/**
 * Helper for {@code Trace} related operations.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class TraceHelper {
    private TraceHelper() {
        throw new UnsupportedOperationException();
    }

    /**
     * {@code Trace tag} that should be used by car service. This is same with
     * {@link Trace#TRACE_TAG_SYSTEM_SERVER}, so {code System Server} tracing should be enabled from
     * trace tools to access these traces.
     */
    public static final long TRACE_TAG_CAR_SERVICE = Trace.TRACE_TAG_SYSTEM_SERVER;
}
