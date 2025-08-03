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

package com.android.car.settings.qc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import androidx.annotation.VisibleForTesting;

/**
 * Responds to actions performed by Quick Controls provided by CarSettings and notifies of
 * any changes.
 */
public class SettingsQCBroadcastReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent i) {
        String action = i.getAction();
        if (SettingsQCRegistry.isValidAction(action)) {
            SettingsQCItem qcItem = createQCItemInstance(context,
                    SettingsQCRegistry.getQCClassByUri(Uri.parse(action)));
            qcItem.onNotifyChange(i);
        }
    }

    @VisibleForTesting
    SettingsQCItem createQCItemInstance(Context context, Class<? extends SettingsQCItem> qcItem) {
        return SettingsQCItem.createInstance(context, qcItem);
    }
}
