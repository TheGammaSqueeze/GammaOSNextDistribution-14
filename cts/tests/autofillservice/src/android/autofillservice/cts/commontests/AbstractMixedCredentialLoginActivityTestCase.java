/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.autofillservice.cts.commontests;

import android.autofillservice.cts.activities.LoginCredentialMixedActivity;
import android.autofillservice.cts.testcore.AutofillActivityTestRule;
import android.autofillservice.cts.testcore.IdMode;
import android.autofillservice.cts.testcore.UiBot;
import android.util.Log;

import org.junit.After;

/**
 * Base class for test cases using {@link LoginCredentialMixedActivity}.
 */
public abstract class AbstractMixedCredentialLoginActivityTestCase
        extends AutoFillServiceTestCase.AutoActivityLaunch<LoginCredentialMixedActivity> {

    protected LoginCredentialMixedActivity mActivity;

    protected AbstractMixedCredentialLoginActivityTestCase(UiBot inlineUiBot) {
        super(inlineUiBot);
    }

    @Override
    protected AutofillActivityTestRule<LoginCredentialMixedActivity> getActivityRule() {
        return new AutofillActivityTestRule<LoginCredentialMixedActivity>(
                LoginCredentialMixedActivity.class) {
            @Override
            protected void afterActivityLaunched() {
                mActivity = getActivity();
            }
        };
    }

    @After
    public void resetReplierIdMode() {
        Log.d("AbstractLoginActivityTestCase", "resetReplierIdMode()");
        sReplier.setIdMode(IdMode.RESOURCE_ID);
    }
}
