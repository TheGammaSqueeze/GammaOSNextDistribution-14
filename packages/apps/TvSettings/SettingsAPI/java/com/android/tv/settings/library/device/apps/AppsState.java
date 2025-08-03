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

package com.android.tv.settings.library.device.apps;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.android.tv.settings.library.ManagerUtil;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceControllerState;
import com.android.tv.settings.library.util.AbstractPreferenceController;

import java.util.ArrayList;
import java.util.List;

public class AppsState extends PreferenceControllerState {
    public static final String EXTRA_VOLUME_UUID = "volumeUuid";
    public static final String EXTRA_VOLUME_NAME = "volumeName";

    public AppsState(Context context,
            UIUpdateCallback callback) {
        super(context, callback);
    }

    public static void prepareArgs(Bundle b, String volumeUuid, String volumeName) {
        b.putString(EXTRA_VOLUME_UUID, volumeUuid);
        b.putString(EXTRA_VOLUME_NAME, volumeName);
    }

    @Override
    public int getStateIdentifier() {
        return ManagerUtil.STATE_APPS;
    }

    @Override
    protected List<AbstractPreferenceController> onCreatePreferenceControllers(Context context) {
        final Activity activity = (Activity) mContext;
        final Application app = activity != null ? activity.getApplication() : null;
        List<AbstractPreferenceController> controllers = new ArrayList<>();
        controllers.add(new RecentAppsPreferenceController(mContext, app, mUIUpdateCallback,
                getStateIdentifier(), mPreferenceCompatManager));
        return controllers;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // no-op
    }
}