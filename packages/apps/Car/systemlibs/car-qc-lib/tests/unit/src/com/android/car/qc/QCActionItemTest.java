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
import static com.android.car.qc.QCItem.QC_TYPE_ACTION_TOGGLE;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.app.PendingIntent;
import android.graphics.drawable.Icon;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class QCActionItemTest extends QCItemTestCase<QCActionItem> {
    private static final String TEST_CONTENT_DESCRIPTION = "test_content_description";

    @Test
    public void onCreate_invalidType_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> createAction("INVALID_TYPE", /* action= */ null,
                        /* disabledAction= */ null, /* icon= */ null, /* contentDescription=*/
                        null));
    }

    @Test
    public void onCreateSwitch_hasCorrectType() {
        QCActionItem action = createAction(QC_TYPE_ACTION_SWITCH, /* action= */ null,
                /* disabledAction= */ null, /* icon= */null, /* contentDescription=*/ null);
        assertThat(action.getType()).isEqualTo(QC_TYPE_ACTION_SWITCH);
    }

    @Test
    public void onCreateToggle_hasCorrectType() {
        QCActionItem action = createAction(QC_TYPE_ACTION_TOGGLE, /* action= */ null,
                /* disabledAction= */ null, /* icon= */ null, /* contentDescription=*/ null);
        assertThat(action.getType()).isEqualTo(QC_TYPE_ACTION_TOGGLE);
    }

    @Test
    public void onBundle_nullActions_noCrash() {
        QCActionItem action = createAction(QC_TYPE_ACTION_TOGGLE, /* action= */ null,
                /* disabledAction= */ null, mDefaultIcon, /* contentDescription=*/ null);
        writeAndLoadFromBundle(action);
        // Test passes if this doesn't crash
    }

    @Test
    public void onBundle_nullIcon_noCrash() {
        QCActionItem action = createAction(QC_TYPE_ACTION_TOGGLE, mDefaultAction,
                mDefaultDisabledAction, /* icon= */ null, /* contentDescription=*/ null);
        writeAndLoadFromBundle(action);
        // Test passes if this doesn't crash
    }

    @Test
    public void onBundle_switch_accurateData() {
        QCActionItem action = createAction(QC_TYPE_ACTION_SWITCH, mDefaultAction,
                mDefaultDisabledAction, /* icon= */ null, TEST_CONTENT_DESCRIPTION);
        QCActionItem newAction = writeAndLoadFromBundle(action);
        assertThat(newAction.getType()).isEqualTo(QC_TYPE_ACTION_SWITCH);
        assertThat(newAction.isChecked()).isTrue();
        assertThat(newAction.isEnabled()).isTrue();
        assertThat(newAction.isClickableWhileDisabled()).isFalse();
        assertThat(newAction.getPrimaryAction()).isNotNull();
        assertThat(newAction.getIcon()).isNull();
        assertThat(newAction.getContentDescription()).isEqualTo(TEST_CONTENT_DESCRIPTION);
    }

    @Test
    public void onBundle_toggle_accurateDate() {
        QCActionItem action = createAction(QC_TYPE_ACTION_TOGGLE, mDefaultAction,
                mDefaultDisabledAction, mDefaultIcon, TEST_CONTENT_DESCRIPTION);
        QCActionItem newAction = writeAndLoadFromBundle(action);
        assertThat(newAction.getType()).isEqualTo(QC_TYPE_ACTION_TOGGLE);
        assertThat(newAction.isChecked()).isTrue();
        assertThat(newAction.isEnabled()).isTrue();
        assertThat(newAction.isClickableWhileDisabled()).isFalse();
        assertThat(newAction.getPrimaryAction()).isNotNull();
        assertThat(newAction.getIcon()).isNotNull();
        assertThat(newAction.getContentDescription()).isEqualTo(TEST_CONTENT_DESCRIPTION);
    }

    private QCActionItem createAction(String type, PendingIntent action,
            PendingIntent disabledAction, Icon icon, String contentDescription) {
        return new QCActionItem.Builder(type)
                .setChecked(true)
                .setEnabled(true)
                .setAction(action)
                .setDisabledClickAction(disabledAction)
                .setIcon(icon)
                .setContentDescription(contentDescription)
                .build();
    }
}
