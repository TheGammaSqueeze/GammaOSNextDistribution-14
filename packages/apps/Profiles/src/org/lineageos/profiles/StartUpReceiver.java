/*
* Copyright (C) 2015 The CyanogenMod Project
*               2020 The LineageOS Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

package org.lineageos.profiles;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import com.android.internal.widget.LockPatternUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Boot receiver which checks enables the ProfilesTrustAgent once, then disables itself.
 * We only need to do this once to make sure we don't override if it was disabled at a later point.
 */
public class StartUpReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // add ProfilesTrustAgent to list of trusted agents
        LockPatternUtils lockUtils = new LockPatternUtils(context);
        ComponentName profileTrustAgent = new ComponentName(context, ProfilesTrustAgent.class);

        List<ComponentName> enabledTrustAgents =
                lockUtils.getEnabledTrustAgents(UserHandle.USER_SYSTEM);
        if (enabledTrustAgents == null) {
            enabledTrustAgents = new ArrayList<>();
        }
        if (!enabledTrustAgents.contains(profileTrustAgent)) {
            enabledTrustAgents.add(profileTrustAgent);
            lockUtils.setEnabledTrustAgents(enabledTrustAgents, UserHandle.USER_SYSTEM);
        }

        // disable the receiver once it has enabled ProfilesTrustAgent
        ComponentName name = new ComponentName(context, StartUpReceiver.class);
        PackageManager pm = context.getPackageManager();
        pm.setComponentEnabledSetting(name,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

}
