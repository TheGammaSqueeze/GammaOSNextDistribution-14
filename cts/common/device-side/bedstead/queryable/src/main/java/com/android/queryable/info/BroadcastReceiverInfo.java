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

package com.android.queryable.info;

import android.content.BroadcastReceiver;

/**
 * Wrapper for information about an {@link BroadcastReceiver}.
 *
 * <p>This is used instead of {@link BroadcastReceiver} so that it can be easily serialized.
 */
public class BroadcastReceiverInfo extends ReceiverInfo {

    public BroadcastReceiverInfo(BroadcastReceiver broadcastReceiver) {
        this(broadcastReceiver.getClass());
    }

    public BroadcastReceiverInfo(Class<? extends BroadcastReceiver> broadcastReceiverClass) {
        this(broadcastReceiverClass.getName());
    }

    public BroadcastReceiverInfo(String broadcastReceiverClassName) {
        super(broadcastReceiverClassName);
        // TODO(scottjonathan): Add more information about the broadcast receiver
    }

    @Override
    public String toString() {
        return "BroadcastReceiver{"
                + "class=" + super.toString()
                + "}";
    }
}
