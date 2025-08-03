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

package com.android.eventlib.premade;

import android.app.admin.DelegatedAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.android.eventlib.events.delegatedadminreceivers.DelegatedAdminChoosePrivateKeyAliasEvent;
import com.android.eventlib.events.delegatedadminreceivers.DelegatedAdminChoosePrivateKeyAliasEvent.DelegatedAdminChoosePrivateKeyAliasEventLogger;
import com.android.eventlib.events.delegatedadminreceivers.DelegatedAdminSecurityLogsAvailableEvent;
import com.android.eventlib.events.delegatedadminreceivers.DelegatedAdminSecurityLogsAvailableEvent.DelegatedAdminSecurityLogsAvailableEventLogger;
import com.android.eventlib.events.deviceadminreceivers.DelegatedAdminNetworkLogsAvailableEvent;
import com.android.eventlib.events.deviceadminreceivers.DelegatedAdminNetworkLogsAvailableEvent.DelegatedAdminNetworkLogsAvailableEventLogger;

/**
 * {@link DelegatedAdminReceiver} which logs all callbacks using EventLib.
 */
@SuppressWarnings("NewApi")
public class EventLibDelegatedAdminReceiver extends DelegatedAdminReceiver {

    private String mOverrideDelegatedAdminReceiverClassName;

    public void setOverrideDelegatedAdminReceiverClassName(
            String overrideDelegatedAdminReceiverClassName) {
        mOverrideDelegatedAdminReceiverClassName = overrideDelegatedAdminReceiverClassName;
    }

    /**
     * Get the class name for this {@link DelegatedAdminReceiver}.
     *
     * <p>This will account for the name being overridden.
     */
    public String className() {
        if (mOverrideDelegatedAdminReceiverClassName != null) {
            return mOverrideDelegatedAdminReceiverClassName;
        } else {
            return EventLibDelegatedAdminReceiver.class.getName();
        }
    }

    @Override
    public String onChoosePrivateKeyAlias(Context context, Intent intent, int uid, Uri uri,
            String alias) {
        DelegatedAdminChoosePrivateKeyAliasEventLogger logger =
                DelegatedAdminChoosePrivateKeyAliasEvent
                        .logger(this, context, intent, uid, uri, alias);

        if (mOverrideDelegatedAdminReceiverClassName != null) {
            logger.setDelegatedAdminReceiver(mOverrideDelegatedAdminReceiverClassName);
        }

        logger.log();

        // TODO(b/198280332) Allow TestApp to return values for methods.
        if (uri == null) {
            return null;
        }
        return uri.getQueryParameter("alias");
    }

    @Override
    public void onNetworkLogsAvailable(Context context, Intent intent, long batchToken,
            int networkLogsCount) {
        DelegatedAdminNetworkLogsAvailableEventLogger logger =
                DelegatedAdminNetworkLogsAvailableEvent
                        .logger(this, context, intent, batchToken, networkLogsCount);

        if (mOverrideDelegatedAdminReceiverClassName != null) {
            logger.setDelegatedAdminReceiver(mOverrideDelegatedAdminReceiverClassName);
        }

        logger.log();
    }

    @Override
    public void onSecurityLogsAvailable(Context context, Intent intent) {
        DelegatedAdminSecurityLogsAvailableEventLogger logger =
                DelegatedAdminSecurityLogsAvailableEvent.logger(this, context, intent);

        if (mOverrideDelegatedAdminReceiverClassName != null) {
            logger.setDelegatedAdminReceiver(mOverrideDelegatedAdminReceiverClassName);
        }

        logger.log();
    }
}
