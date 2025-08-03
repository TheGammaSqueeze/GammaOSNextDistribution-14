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

/**
 * Adapter interface that wraps methods from
 * {@link android.provider.BlockedNumberContract.BlockedNumbers} and
 * {@link com.android.server.telecom.settings.BlockedNumbersUtil} to make things testable.
 */
public interface BlockedNumbersAdapter {
    boolean shouldShowEmergencyCallNotification (Context context);
    void updateEmergencyCallNotification(Context context, boolean showNotification);
}
