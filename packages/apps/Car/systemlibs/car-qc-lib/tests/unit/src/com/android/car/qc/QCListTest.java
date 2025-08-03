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

import static com.android.car.qc.QCItem.QC_TYPE_LIST;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class QCListTest extends QCItemTestCase<QCList> {

    @Test
    public void onCreate_hasCorrectType() {
        QCList list = createList(Collections.emptyList());
        assertThat(list.getType()).isEqualTo(QC_TYPE_LIST);
    }

    @Test
    public void createFromParcel_accurateData() {
        QCRow row = new QCRow.Builder()
                .setTitle(TEST_TITLE)
                .setSubtitle(TEST_SUBTITLE)
                .setIcon(mDefaultIcon)
                .setPrimaryAction(mDefaultAction)
                .build();

        QCList list = createList(Collections.singletonList(row));
        QCList newList = writeAndLoadFromBundle(list);
        assertThat(newList.getType()).isEqualTo(QC_TYPE_LIST);
        assertThat(newList.getRows().size()).isEqualTo(1);
    }

    private QCList createList(List<QCRow> rows) {
        QCList.Builder builder = new QCList.Builder();
        for (QCRow row : rows) {
            builder.addRow(row);
        }
        return builder.build();
    }
}
