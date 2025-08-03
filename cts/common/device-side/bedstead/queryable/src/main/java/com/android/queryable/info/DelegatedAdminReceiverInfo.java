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

import android.app.admin.DelegatedAdminReceiver;

/**
 * Wrapper for information about a {@link DelegatedAdminReceiver}.
 *
 * <p>This is used instead of {@link DelegatedAdminReceiver} so that it can be easily serialized.
 */
@SuppressWarnings("NewApi")
public class DelegatedAdminReceiverInfo extends BroadcastReceiverInfo {
    public DelegatedAdminReceiverInfo(DelegatedAdminReceiver delegatedAdminReceiver) {
        super(delegatedAdminReceiver);
    }

    public DelegatedAdminReceiverInfo(
            Class<? extends DelegatedAdminReceiver> delegatedAdminReceiverClass) {
        super(delegatedAdminReceiverClass);
    }

    public DelegatedAdminReceiverInfo(String delegatedAdminReceiverClassName) {
        super(delegatedAdminReceiverClassName);
    }

    @Override
    public String toString() {
        return "DelegatedAdminReceiver{"
                + "broadcastReceiver=" + super.toString()
                + "}";
    }
}
