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

package com.android.systemui.car.qc;

import android.content.Context;

import androidx.annotation.NonNull;

import com.android.car.qc.QCItem;
import com.android.car.qc.QCList;
import com.android.car.qc.QCRow;
import com.android.car.qc.provider.BaseLocalQCProvider;
import com.android.systemui.R;
import com.android.systemui.car.drivemode.DriveModeManager;

import javax.inject.Inject;

/**
 * Local provider for the DriveMode panel.
 */
public class DriveModeQcPanel extends BaseLocalQCProvider implements DriveModeManager.Callback {

    private final DriveModeManager mDriveModeManager;

    @Inject
    public DriveModeQcPanel(Context context, DriveModeManager driveModeManager) {
        super(context);
        mDriveModeManager = driveModeManager;
        mDriveModeManager.addCallback(this);
    }

    @Override
    public QCItem getQCItem() {
        QCList.Builder listBuilder = new QCList.Builder();

        for (String driveMode : mDriveModeManager.getAvailableDriveModes()) {
            QCRow row = new QCRow.Builder()
                    .setTitle(driveMode)
                    .setSubtitle(getSubtitle(driveMode))
                    .build();
            row.setActionHandler((item, context, intent) -> {
                mDriveModeManager.setDriveMode(driveMode);
            });
            listBuilder.addRow(row);
        }

        return listBuilder.build();
    }

    private String getSubtitle(String driveMode) {
        return mDriveModeManager.getDriveMode().equals(driveMode)
                ? mContext.getResources().getString(R.string.qc_drive_mode_active_subtitle)
                : null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mDriveModeManager != null) {
            mDriveModeManager.removeCallback(this);
        }
    }

    @Override
    public void onDriveModeChanged(String newDriveMode) {
        notifyChange();
    }
}
