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

package com.googlecode.android_scripting.facade.telephony;

import android.app.Service;
import android.content.Context;
import com.android.cellbroadcastreceiver.tests.SendTestMessages;
import com.googlecode.android_scripting.facade.FacadeManager;
import com.googlecode.android_scripting.jsonrpc.RpcReceiver;
import com.googlecode.android_scripting.rpc.Rpc;
import com.googlecode.android_scripting.rpc.RpcParameter;


/**
 * Exposes CellBroadcastReceiver test app functionality.
 */
public class CellBroadcastReceiverFacade extends RpcReceiver {
    private final Context mContext;
    private final Service mService;

    public CellBroadcastReceiverFacade(FacadeManager manager) {
        super(manager);
        mService = manager.getService();
        mContext = mService;
    }

    @Rpc(description = "Trigger a cell broadcast alert on a particular channel.")
    public void cbrSendTestAlert(
            @RpcParameter(name="messageId") Integer messageId,
            @RpcParameter(name="channelId") Integer channelId) {
        SendTestMessages.testSendMessage7bit(mContext, messageId, channelId);
    }

    public void shutdown() {

    }
}
