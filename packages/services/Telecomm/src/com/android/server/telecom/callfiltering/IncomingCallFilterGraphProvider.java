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

package com.android.server.telecom.callfiltering;

import android.content.Context;

import com.android.server.telecom.Call;
import com.android.server.telecom.TelecomSystem;
import com.android.server.telecom.Timeouts;

/**
 * Interface to provide a {@link IncomingCallFilterGraph}. This class serve for unit test purpose
 * to mock an incoming call filter graph in test code.
 */
public interface IncomingCallFilterGraphProvider {


    /**
     * Provide a {@link  IncomingCallFilterGraph}
     * @param call The call for the filters.
     * @param listener Callback object to trigger when filtering is done.
     * @param context An android context.
     * @param timeoutsAdapter Adapter to provide timeout value for call filtering.
     * @param lock Telecom lock.
     * @return
     */
    IncomingCallFilterGraph createGraph(Call call, CallFilterResultCallback listener,
            Context context,
            Timeouts.Adapter timeoutsAdapter, TelecomSystem.SyncRoot lock);
}
