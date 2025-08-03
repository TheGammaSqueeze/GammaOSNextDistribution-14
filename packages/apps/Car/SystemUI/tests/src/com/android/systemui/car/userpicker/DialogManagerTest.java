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
package com.android.systemui.car.userpicker;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spyOn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;

import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper;
import android.view.View;

import androidx.test.filters.SmallTest;

import com.android.systemui.R;
import com.android.systemui.car.CarSystemUiTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;

@CarSystemUiTest
@RunWith(AndroidTestingRunner.class)
@TestableLooper.RunWithLooper
@SmallTest
public class DialogManagerTest extends UserPickerTestCase {
    private DialogManager mDialogManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        doReturn(MAIN_DISPLAY_ID).when(mContext).getDisplayId();
        mDialogManager = new DialogManager();
        View rootView = mInflater.inflate(R.layout.test_empty_layout, null);
        mDialogManager.initContextFromView(rootView);
        spyOn(mDialogManager);
    }

    @After
    public void tearDown() {
        if (mDialogManager != null) {
            mDialogManager.clearAllDialogs();
        }
    }

    @Test
    public void showMaxUserLimitReachedDialog_requestShowMaxUserDialog_callShowMaxUserDialog() {
        int dialogtype = mDialogManager.DIALOG_TYPE_MAX_USER_COUNT_REACHED;
        assertThat(mDialogManager.mUserPickerDialogs.get(dialogtype)).isNull();
        mDialogManager.showDialog(dialogtype);

        verify(mDialogManager).showDialog(eq(dialogtype));
        assertThat(mDialogManager.mUserPickerDialogs.get(dialogtype)).isNotNull();
    }

    @Test
    public void showConfirmAddUserDialog_requestShowConfirmDialog_callShowConfirmAddUserDialog() {
        int dialogtype = mDialogManager.DIALOG_TYPE_CONFIRM_ADD_USER;
        assertThat(mDialogManager.mUserPickerDialogs.get(dialogtype)).isNull();
        mDialogManager.showDialog(dialogtype);

        verify(mDialogManager).showDialog(eq(dialogtype));
        assertThat(mDialogManager.mUserPickerDialogs.get(dialogtype)).isNotNull();
    }

    @Test
    public void showAddingDialog_requestShowAddingDialog_callShowAddingDialog() {
        int dialogtype = mDialogManager.DIALOG_TYPE_ADDING_USER;
        assertThat(mDialogManager.mUserPickerDialogs.get(dialogtype)).isNull();
        mDialogManager.showDialog(dialogtype);

        verify(mDialogManager).showDialog(eq(dialogtype));
        assertThat(mDialogManager.mUserPickerDialogs.get(dialogtype)).isNotNull();
    }

    @Test
    public void dismissAddingDialog_requestDismissAddingDialog_callDismissAddingDialog() {
        int dialogtype = mDialogManager.DIALOG_TYPE_ADDING_USER;
        mDialogManager.showDialog(dialogtype);
        verify(mDialogManager).showDialog(eq(dialogtype));
        assertThat(mDialogManager.mUserPickerDialogs.get(dialogtype)).isNotNull();

        mDialogManager.dismissDialog(dialogtype);
        verify(mDialogManager).dismissDialog(eq(dialogtype));
        assertThat(mDialogManager.mUserPickerDialogs.get(dialogtype)).isNull();
    }

    @Test
    public void showSwitchingDialog_requestShowSwitchingDialog_callShowSwitchingDialog() {
        int dialogtype = mDialogManager.DIALOG_TYPE_SWITCHING;
        assertThat(mDialogManager.mUserPickerDialogs.get(dialogtype)).isNull();
        mDialogManager.showDialog(dialogtype);

        verify(mDialogManager).showDialog(eq(dialogtype));
        assertThat(mDialogManager.mUserPickerDialogs.get(dialogtype)).isNotNull();
    }

    @Test
    public void dismissSwitchingDialog_requestDismissSwitchingDialog_callDismissSwitchingDialog() {
        int dialogtype = mDialogManager.DIALOG_TYPE_SWITCHING;
        mDialogManager.showDialog(dialogtype);
        verify(mDialogManager).showDialog(eq(dialogtype));
        assertThat(mDialogManager.mUserPickerDialogs.get(dialogtype)).isNotNull();

        mDialogManager.dismissDialog(dialogtype);
        verify(mDialogManager).dismissDialog(eq(dialogtype));
        assertThat(mDialogManager.mUserPickerDialogs.get(dialogtype)).isNull();
    }

    @Test
    public void showConfirmLogoutDialog_callShowConfirmLogoutDialog() {
        int dialogtype = mDialogManager.DIALOG_TYPE_CONFIRM_LOGOUT;
        assertThat(mDialogManager.mUserPickerDialogs.get(dialogtype)).isNull();
        mDialogManager.showDialog(dialogtype);

        verify(mDialogManager).showDialog(eq(dialogtype));
        assertThat(mDialogManager.mUserPickerDialogs.get(dialogtype)).isNotNull();
    }
}
