/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.cellbroadcastreceiver.unit;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.app.AlertDialog;

import com.android.cellbroadcastreceiver.CellBroadcastOptOutActivity;

import org.junit.Before;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unittest for CellBroadcastOptOutActivity.
 */
public class CellBroadcastOptOutActivityTest extends
        CellBroadcastActivityTestCase<CellBroadcastOptOutActivity> {
    @Mock
    private CellBroadcastOptOutActivity.TestableShowAlertDialog mMockShowDialog;
    private AlertDialog mAlertDialog;

    public CellBroadcastOptOutActivityTest() {
        super(CellBroadcastOptOutActivity.class);
    }

    private void setCreatedAlertDialog(AlertDialog alertDialog) {
        mAlertDialog = alertDialog;
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        CellBroadcastOptOutActivity.sShowDialog = mMockShowDialog;
    }

    public void testOnCreate_shouldCreateAlertDialog() throws Throwable {
        CellBroadcastOptOutActivity activityUT = startActivity();
        assertThat(activityUT).isNotNull();

        AlertDialog alertDialog = activityUT.mAlertDialog;
        assertThat(alertDialog).isNotNull();

        verify(mMockShowDialog).show(alertDialog);
    }

    /**
     * Currently it's impossible to test against button clicks in framework of ActivityUnitTestCase.
     * Because it has issue of attaching the dialog to a window.
     * In future we should consider adding UI test e.g. espresso to test them.
     * @throws Throwable
     */
    public void testShowOptOutDialog_shouldCreateAlertDialog() throws Throwable {
        Activity activity = startActivity();
        // Create alert dialog with activity passed in.
        runTestOnUiThread(() ->
                setCreatedAlertDialog(CellBroadcastOptOutActivity.showOptOutDialog(activity)));
        assertThat(mAlertDialog).isNotNull();
        // Verify the dialog is shown.
        verify(mMockShowDialog).show(mAlertDialog);
    }
}
