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

package com.android.bedstead.testapp;

import android.app.admin.DelegatedAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.android.eventlib.premade.EventLibDelegatedAdminReceiver;

/**
 * Implementation of {@link DelegatedAdminReceiver} which logs events in response to callbacks and
 * supports TestApp Features.
 */
public class BaseTestAppDelegatedAdminReceiver extends EventLibDelegatedAdminReceiver {
    @Override
    public String onChoosePrivateKeyAlias(Context context, Intent intent, int uid, Uri uri,
            String alias) {
        return super.onChoosePrivateKeyAlias(context, intent, uid, uri, alias);
    }

    @Override
    public void onNetworkLogsAvailable(Context context, Intent intent, long batchToken,
            int networkLogsCount) {
        super.onNetworkLogsAvailable(context, intent, batchToken, networkLogsCount);
    }

    @Override
    public void onSecurityLogsAvailable(Context context, Intent intent) {
        super.onSecurityLogsAvailable(context, intent);
    }
}
