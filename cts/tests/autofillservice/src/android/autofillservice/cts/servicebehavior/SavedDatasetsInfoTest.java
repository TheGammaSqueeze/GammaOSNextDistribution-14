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

package android.autofillservice.cts.servicebehavior;

import static android.service.autofill.SavedDatasetsInfo.TYPE_OTHER;
import static android.service.autofill.SavedDatasetsInfo.TYPE_PASSWORDS;
import static android.service.autofill.SavedDatasetsInfoCallback.ERROR_NEEDS_USER_ACTION;
import static android.service.autofill.SavedDatasetsInfoCallback.ERROR_OTHER;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.autofillservice.cts.commontests.AutoFillServiceTestCase;
import android.autofillservice.cts.testcore.InstrumentedAutoFillService;
import android.platform.test.annotations.AppModeFull;
import android.service.autofill.SavedDatasetsInfo;
import android.service.autofill.SavedDatasetsInfoCallback;
import android.util.ArraySet;

import com.google.common.collect.Lists;

import org.junit.Before;
import org.junit.Test;

import java.util.Collections;

/**
 * Tests for
 * {@link android.service.autofill.AutofillService#onSavedDatasetsInfoRequest(SavedDatasetsInfoCallback)}.
 */
// TODO: Use a TestAPI to test this.
@AppModeFull(reason = "Tests the service only")
public class SavedDatasetsInfoTest extends AutoFillServiceTestCase.ManualActivityLaunch {

    @Before
    public void before() throws Exception {
        enableService();
    }

    @Test
    public void testUnimplemented() throws Exception {
        assertThat(executePasswordCountRequest()).isEqualTo("resultCode=1");
    }

    @Test
    public void testError() throws Exception {
        InstrumentedAutoFillService.setSavedDatasetsInfoReplier(
                (callback) -> callback.onError(ERROR_NEEDS_USER_ACTION));
        assertThat(executePasswordCountRequest()).isEqualTo("resultCode=1");

        InstrumentedAutoFillService.setSavedDatasetsInfoReplier(
                (callback) -> callback.onError(ERROR_OTHER));
        assertThat(executePasswordCountRequest()).isEqualTo("resultCode=1");
    }

    @Test
    public void testSuccess() throws Exception {
        InstrumentedAutoFillService.setSavedDatasetsInfoReplier(
                (callback) -> callback.onSuccess(
                        new ArraySet<>(Lists.newArrayList(
                                new SavedDatasetsInfo(TYPE_OTHER, 99),
                                new SavedDatasetsInfo(TYPE_PASSWORDS, 5)))));
        assertThat(executePasswordCountRequest()).isEqualTo("resultCode=0\nvalue=5");
    }

    @Test
    public void testSuccess_withoutValidCounts() throws Exception {
        InstrumentedAutoFillService.setSavedDatasetsInfoReplier(
                (callback) -> callback.onSuccess(Collections.emptySet()));
        assertThat(executePasswordCountRequest()).isEqualTo("resultCode=1");

        InstrumentedAutoFillService.setSavedDatasetsInfoReplier((callback) ->
                assertThrows(() ->
                        callback.onSuccess(
                                Collections.singleton(
                                        new SavedDatasetsInfo(TYPE_PASSWORDS, -10)))));
    }

    private String executePasswordCountRequest() {
        return runShellCommand(
                "cmd autofill get saved-password-count "
                        + getInstrumentation().getTargetContext().getUserId());
    }
}
