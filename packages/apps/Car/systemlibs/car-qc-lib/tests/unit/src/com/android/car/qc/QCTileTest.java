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

import static com.android.car.qc.QCItem.QC_TYPE_TILE;

import static com.google.common.truth.Truth.assertThat;

import android.app.PendingIntent;
import android.graphics.drawable.Icon;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class QCTileTest extends QCItemTestCase<QCTile> {

    @Test
    public void onCreate_hasCorrectType() {
        QCTile tile = createTile(/* action= */ null, /* disabledAction= */ null, /* icon= */ null);
        assertThat(tile.getType()).isEqualTo(QC_TYPE_TILE);
    }

    @Test
    public void onBundle_nullAction_noCrash() {
        QCTile tile = createTile(/* action= */ null, /* disabledAction= */ null, mDefaultIcon);
        writeAndLoadFromBundle(tile);
        // Test passes if this doesn't crash
    }

    @Test
    public void onBundle_nullIcon_noCrash() {
        QCTile tile = createTile(mDefaultAction, mDefaultDisabledAction, /* icon= */ null);
        writeAndLoadFromBundle(tile);
        // Test passes if this doesn't crash
    }

    @Test
    public void createFromParcel_accurateData() {
        QCTile tile = createTile(mDefaultAction, mDefaultDisabledAction, mDefaultIcon);
        QCTile newTile = writeAndLoadFromBundle(tile);
        assertThat(newTile.getType()).isEqualTo(QC_TYPE_TILE);
        assertThat(newTile.getSubtitle()).isEqualTo(TEST_SUBTITLE);
        assertThat(newTile.isChecked()).isTrue();
        assertThat(newTile.isEnabled()).isTrue();
        assertThat(newTile.getPrimaryAction()).isNotNull();
        assertThat(newTile.getDisabledClickAction()).isNotNull();
        assertThat(newTile.getIcon()).isNotNull();
    }

    private QCTile createTile(PendingIntent action, PendingIntent disabledAction, Icon icon) {
        return new QCTile.Builder()
                .setSubtitle(TEST_SUBTITLE)
                .setChecked(true)
                .setEnabled(true)
                .setAction(action)
                .setDisabledClickAction(disabledAction)
                .setIcon(icon)
                .build();
    }
}
