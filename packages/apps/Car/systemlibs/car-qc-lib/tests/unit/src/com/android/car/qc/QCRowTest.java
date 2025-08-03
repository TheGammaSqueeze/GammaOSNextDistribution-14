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

package com.android.car.qc;

import static com.android.car.qc.QCItem.QC_TYPE_ACTION_SWITCH;
import static com.android.car.qc.QCItem.QC_TYPE_ROW;

import static com.google.common.truth.Truth.assertThat;

import android.app.PendingIntent;
import android.graphics.drawable.Icon;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class QCRowTest extends QCItemTestCase<QCRow> {

    @Test
    public void onCreate_hasCorrectType() {
        QCRow row = createRow(/* action= */ null, /* disabledAction= */ null, /* icon= */ null);
        assertThat(row.getType()).isEqualTo(QC_TYPE_ROW);
    }

    @Test
    public void onBundle_nullActions_noCrash() {
        QCRow row = createRow(/* action= */ null, /* disabledAction= */ null, mDefaultIcon);
        writeAndLoadFromBundle(row);
        // Test passes if this doesn't crash
    }

    @Test
    public void onBundle_nullIcon_noCrash() {
        QCRow row = createRow(mDefaultAction, mDefaultDisabledAction, /* icon= */ null);
        writeAndLoadFromBundle(row);
        // Test passes if this doesn't crash
    }

    @Test
    public void createFromParcel_accurateData() {
        QCRow row = createRow(mDefaultAction, mDefaultDisabledAction, mDefaultIcon);
        QCRow newRow = writeAndLoadFromBundle(row);
        assertThat(newRow.getType()).isEqualTo(QC_TYPE_ROW);
        assertThat(newRow.getTitle()).isEqualTo(TEST_TITLE);
        assertThat(newRow.getSubtitle()).isEqualTo(TEST_SUBTITLE);
        assertThat(newRow.getPrimaryAction()).isNotNull();
        assertThat(newRow.getStartIcon()).isNotNull();
    }

    @Test
    public void createFromParcel_accurateData_startItem() {
        QCActionItem item = new QCActionItem.Builder(QC_TYPE_ACTION_SWITCH).build();

        QCRow row = createRow(/* action= */ null, /* disabledAction= */ null, /* icon= */ null,
                Collections.singletonList(item), Collections.emptyList(), Collections.emptyList());
        QCRow newRow = writeAndLoadFromBundle(row);
        assertThat(newRow.getStartItems().size()).isEqualTo(1);
    }

    @Test
    public void createFromParcel_accurateData_endItem() {
        QCActionItem item = new QCActionItem.Builder(QC_TYPE_ACTION_SWITCH).build();

        QCRow row = createRow(/* action= */ null, /* disabledAction= */ null, /* icon= */ null,
                Collections.emptyList(), Collections.singletonList(item), Collections.emptyList());
        QCRow newRow = writeAndLoadFromBundle(row);
        assertThat(newRow.getEndItems().size()).isEqualTo(1);
    }

    @Test
    public void createFromParcel_accurateData_slider() {
        QCSlider slider = new QCSlider.Builder().build();

        QCRow row = createRow(/* action= */ null, /* disabledAction= */ null, /* icon= */ null,
                Collections.emptyList(), Collections.emptyList(),
                Collections.singletonList(slider));
        QCRow newRow = writeAndLoadFromBundle(row);
        assertThat(newRow.getSlider()).isNotNull();
    }

    private QCRow createRow(PendingIntent action, PendingIntent disabledAction, Icon icon) {
        return createRow(action, disabledAction, icon, Collections.emptyList(),
                Collections.emptyList(), Collections.emptyList());
    }

    private QCRow createRow(PendingIntent action, PendingIntent disabledAction, Icon icon,
            List<QCActionItem> startItems, List<QCActionItem> endItems, List<QCSlider> sliders) {
        QCRow.Builder builder = new QCRow.Builder()
                .setTitle(TEST_TITLE)
                .setSubtitle(TEST_SUBTITLE)
                .setIcon(icon)
                .setPrimaryAction(action)
                .setDisabledClickAction(disabledAction);
        for (QCActionItem item : startItems) {
            builder.addStartItem(item);
        }
        for (QCActionItem item : endItems) {
            builder.addEndItem(item);
        }
        for (QCSlider slider : sliders) {
            builder.addSlider(slider);
        }
        return builder.build();
    }
}
