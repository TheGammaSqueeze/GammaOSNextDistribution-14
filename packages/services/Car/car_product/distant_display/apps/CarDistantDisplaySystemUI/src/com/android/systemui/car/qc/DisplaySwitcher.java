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

package com.android.systemui.car.qc;

import static com.android.car.ui.utils.CarUiUtils.drawableToBitmap;

import android.content.Context;
import android.graphics.drawable.Icon;

import com.android.car.qc.QCItem;
import com.android.car.qc.QCList;
import com.android.car.qc.QCRow;
import com.android.car.qc.provider.BaseLocalQCProvider;
import com.android.systemui.car.distantdisplay.common.DistantDisplayQcItem;
import com.android.systemui.car.systembar.DistantDisplayController;

import javax.inject.Inject;

/**
 * A {@link BaseLocalQCProvider} that builds the distant display quick controls panel.
 */
public class DisplaySwitcher extends BaseLocalQCProvider implements
        DistantDisplayControlsUpdateListener {

    private final DistantDisplayController mDistantDisplayController;

    @Inject
    public DisplaySwitcher(Context context,
            DistantDisplayController distantDisplayController) {
        super(context);
        mDistantDisplayController = distantDisplayController;
    }

    @Override
    public QCItem getQCItem() {
        QCList.Builder listBuilder = new QCList.Builder();
        DistantDisplayQcItem metadata =
                mDistantDisplayController.getMetadata();
        if (metadata != null) {
            QCRow.Builder builder = new QCRow.Builder()
                    .setTitle(metadata.getTitle())
                    .setSubtitle(metadata.getSubtitle());
            if (metadata.getIcon() != null) {
                builder.setIcon(Icon.createWithBitmap(drawableToBitmap(metadata.getIcon())));
            }
            listBuilder.addRow(builder.build());
        }
        DistantDisplayQcItem controls = mDistantDisplayController.getControls();
        if (controls != null) {
            Icon icon = Icon.createWithBitmap(drawableToBitmap(controls.getIcon()));
            QCRow controlElement = new QCRow.Builder()
                    .setTitle(controls.getTitle())
                    .setIcon(icon)
                    .build();
            controlElement.setActionHandler(controls.getActionHandler());
            listBuilder.addRow(controlElement);
        }
        return listBuilder.build();
    }

    @Override
    protected void onSubscribed() {
        mDistantDisplayController.setDistantDisplayControlsUpdateListener(this);
    }

    @Override
    protected void onUnsubscribed() {
        mDistantDisplayController.setDistantDisplayControlsUpdateListener(null);
    }

    @Override
    public void onControlsChanged() {
        notifyChange();
    }
}
