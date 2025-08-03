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

package com.android.systemui.car.systembar;

import static com.android.car.caruiportrait.common.service.CarUiPortraitService.INTENT_EXTRA_COLLAPSE_RECENTS_PANEL;
import static com.android.car.caruiportrait.common.service.CarUiPortraitService.REQUEST_FROM_SYSTEM_UI;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;

/**
 * {@link RecentsButtonStateProvider} that relies on callers to manually change the Recents state.
 */
public class CarUiRecentsButtonStateProvider extends RecentsButtonStateProvider {
    private final Context mContext;

    public CarUiRecentsButtonStateProvider(Context context, CarSystemBarButton carSystemBarButton) {
        super(context, carSystemBarButton);
        mContext = context;
    }

    @Override
    protected void initialiseListener() {
        // no-op: Launcher is responsible to inform SystemUI when Panel showing Recents is
        // expanded/collapsed
    }

    @Override
    protected boolean toggleRecents() {
        if (getIsRecentsActive()) {
            Intent intent = new Intent(REQUEST_FROM_SYSTEM_UI);
            intent.putExtra(INTENT_EXTRA_COLLAPSE_RECENTS_PANEL, true);
            mContext.getApplicationContext().sendBroadcastAsUser(intent,
                    new UserHandle(ActivityManager.getCurrentUser()));
            return true;
        }
        return super.toggleRecents();
    }
}
