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

package com.android.queryable.queries;

import android.app.admin.DelegatedAdminReceiver;

import com.android.queryable.Queryable;
import com.android.queryable.info.DelegatedAdminReceiverInfo;

/** Query for a {@link DelegatedAdminReceiver}. */
public interface DelegatedAdminReceiverQuery<E extends Queryable>
        extends Query<DelegatedAdminReceiverInfo>  {

    /** Queries a {@link DelegatedAdminReceiver}. */
    static DelegatedAdminReceiverQueryHelper.DelegatedAdminReceiverQueryBase delegatedAdminReceiver() {
        return new DelegatedAdminReceiverQueryHelper.DelegatedAdminReceiverQueryBase();
    }

    BroadcastReceiverQuery<E> broadcastReceiver();
}
