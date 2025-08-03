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

package com.android.car.qc.view;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.testng.Assert.assertThrows;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.qc.QCList;
import com.android.car.qc.QCRow;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class QCListViewTest {

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private QCListView mView;

    @Before
    public void setUp() {
        mView = new QCListView(mContext);
    }

    @Test
    public void onChanged_null_noViews() {
        mView.onChanged(null);
        assertThat(mView.getChildCount()).isEqualTo(0);
    }

    @Test
    public void onChanged_invalidType_throwsIllegalArgumentException() {
        QCRow row = new QCRow.Builder().build();
        assertThrows(IllegalArgumentException.class,
                () -> mView.onChanged(row));
    }

    @Test
    public void onChanged_createsRows() {
        QCList list = new QCList.Builder()
                .addRow(new QCRow.Builder().build())
                .addRow(new QCRow.Builder().build())
                .build();
        mView.onChanged(list);
        assertThat(mView.getChildCount()).isEqualTo(2);
        assertThat(mView.getChildAt(0) instanceof QCRowView).isTrue();
        assertThat(mView.getChildAt(1) instanceof QCRowView).isTrue();
    }

    @Test
    public void onChanged_decreasedRowCount_removesExtraRows() {
        QCList list = new QCList.Builder()
                .addRow(new QCRow.Builder().build())
                .addRow(new QCRow.Builder().build())
                .build();
        mView.onChanged(list);
        assertThat(mView.getChildCount()).isEqualTo(2);
        list = new QCList.Builder()
                .addRow(new QCRow.Builder().build())
                .build();
        mView.onChanged(list);
        assertThat(mView.getChildCount()).isEqualTo(1);
    }

    @Test
    public void setActionListener_setsOnChildView() {
        QCList list = new QCList.Builder()
                .addRow(new QCRow.Builder().build())
                .addRow(new QCRow.Builder().build())
                .build();
        mView.onChanged(list);
        assertThat(mView.getChildCount()).isEqualTo(2);
        QCRowView row1 = (QCRowView) mView.getChildAt(0);
        QCRowView row2 = (QCRowView) mView.getChildAt(1);
        ExtendedMockito.spyOn(row1);
        ExtendedMockito.spyOn(row2);
        QCView.QCActionListener listener = mock(QCView.QCActionListener.class);
        mView.setActionListener(listener);
        ExtendedMockito.verify(row1).setActionListener(listener);
        ExtendedMockito.verify(row2).setActionListener(listener);
    }
}
