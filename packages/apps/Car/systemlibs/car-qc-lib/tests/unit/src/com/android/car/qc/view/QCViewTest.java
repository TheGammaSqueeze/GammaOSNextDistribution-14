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
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertThrows;

import android.content.Context;

import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.qc.QCList;
import com.android.car.qc.QCRow;
import com.android.car.qc.QCTile;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class QCViewTest {

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private QCView mView;

    @Before
    public void setUp() {
        mView = new QCView(mContext);
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
    public void onChanged_list_createsListView() {
        QCList list = new QCList.Builder().build();
        mView.onChanged(list);
        assertThat(mView.getChildCount()).isEqualTo(1);
        assertThat(mView.getChildAt(0) instanceof QCListView).isTrue();
    }

    @Test
    @UiThreadTest
    public void onChanged_tile_createsTileView() {
        QCTile tile = new QCTile.Builder().build();
        mView.onChanged(tile);
        assertThat(mView.getChildCount()).isEqualTo(1);
        assertThat(mView.getChildAt(0) instanceof QCTileView).isTrue();
    }

    @Test
    @UiThreadTest
    public void onChanged_alreadyHasView_callsOnChanged() {
        QCTile tile = new QCTile.Builder().build();
        mView.onChanged(tile);
        assertThat(mView.getChildCount()).isEqualTo(1);
        assertThat(mView.getChildAt(0) instanceof QCTileView).isTrue();
        QCTileView tileView = (QCTileView) mView.getChildAt(0);
        ExtendedMockito.spyOn(tileView);
        mView.onChanged(tile);
        verify(tileView).onChanged(tile);
    }

    @Test
    @UiThreadTest
    public void setActionListener_setsOnChildView() {
        QCTile tile = new QCTile.Builder().build();
        mView.onChanged(tile);
        assertThat(mView.getChildCount()).isEqualTo(1);
        assertThat(mView.getChildAt(0) instanceof QCTileView).isTrue();
        QCTileView tileView = (QCTileView) mView.getChildAt(0);
        ExtendedMockito.spyOn(tileView);
        QCView.QCActionListener listener = mock(QCView.QCActionListener.class);
        mView.setActionListener(listener);
        ExtendedMockito.verify(tileView).setActionListener(listener);
    }
}
