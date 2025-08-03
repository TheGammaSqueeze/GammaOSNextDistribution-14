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

package android.autofillservice.cts.dialog;

import static com.google.common.truth.Truth.assertThat;

import android.autofillservice.cts.activities.LoginActivity;
import android.autofillservice.cts.activities.VirtualContainerActivity;
import android.autofillservice.cts.commontests.AutoFillServiceTestCase;
import android.platform.test.annotations.AppModeFull;
import android.view.autofill.AutofillManager;

import androidx.annotation.NonNull;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;

import org.junit.Test;
import org.junit.rules.TestRule;

/**
 * Basic initial tests for the Dialog UI. These tests should probably be moved to a full-fledged
 * test suite like DialogLoginActivityTest later.
 */
public class DialogTest extends AutoFillServiceTestCase.BaseTestCase {

    private static final String TAG = "DialogTest";

    @Test
    public void showAutofillDialog_noResponse_returnsFalse() throws Exception {
        // Set service.
        enableService();

        // The feature is currently disabled so there is no request at all.

        ActivityScenario<LoginActivity> scenario = ActivityScenario.launch(LoginActivity.class);
        scenario.moveToState(Lifecycle.State.RESUMED);

        scenario.onActivity((activity -> {
            AutofillManager manager = activity.getAutofillManager();
            assertThat(manager.showAutofillDialog(activity.getUsername())).isFalse();
        }));
    }

    @Test
    @AppModeFull(reason = "other tests should provide enough coverage")
    public void showAutofillDialogForVirtualView_noDialogPresentation_returnsFalse()
            throws Exception {
        // Set service.
        enableService();

        // The feature is currently disabled so there is no request at all.

        ActivityScenario<VirtualContainerActivity> scenario = ActivityScenario.launch(
                VirtualContainerActivity.class);
        scenario.moveToState(Lifecycle.State.RESUMED);

        scenario.onActivity((activity -> {
            AutofillManager manager = activity.getAutofillManager();
            assertThat(
                    manager.showAutofillDialog(
                            activity.getVirtualViewHolder(), activity.getUsernameVirtualId()))
                    .isFalse();
        }));
    }

    @NonNull
    @Override
    protected TestRule getMainTestRule() {
        // No-op test rule because we're managing activities ourselves.
        return (base, description) -> base;
    }
}
