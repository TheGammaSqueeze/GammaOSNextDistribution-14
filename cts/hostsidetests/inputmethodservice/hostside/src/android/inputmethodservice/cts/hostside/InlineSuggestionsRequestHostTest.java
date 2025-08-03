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

package android.inputmethodservice.cts.hostside;

import android.compat.cts.CompatChangeGatingTestCase;
import android.inputmethodservice.cts.common.test.DeviceTestConstants;

import com.google.common.collect.ImmutableSet;

/**
 * Host side tests for the {@link android.app.compat.CompatChanges} API changes in
 * {@link android.view.inputmethod.InlineSuggestionsRequest}.
 *
 * <p>See also {@link android.inputmethodservice.cts.devicetest.InlineSuggestionsRequestDeviceTest}.
 */
public class InlineSuggestionsRequestHostTest extends CompatChangeGatingTestCase {

    @Override
    protected void setUp() throws Exception {
        installPackage(DeviceTestConstants.APK, true);
    }

    public void testImeAutofillDefaultSupportedLocalesIsEmpty_changeEnabled() throws Exception {
        runDeviceCompatTest(DeviceTestConstants.PACKAGE, ".InlineSuggestionsRequestDeviceTest",
                "imeAutofillDefaultSupportedLocalesIsEmpty_changeEnabled",
                /*enabledChanges*/ImmutableSet.of(169273070L),
                /*disabledChanges*/ ImmutableSet.of());
    }
}
