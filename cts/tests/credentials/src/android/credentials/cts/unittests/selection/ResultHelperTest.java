/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.credentials.cts.unittests.selection;

import static android.credentials.cts.unittests.TestUtils.assertEquals;
import static android.credentials.flags.Flags.FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.content.Intent;
import android.credentials.selection.FailureDialogResult;
import android.credentials.selection.FailureResult;
import android.credentials.selection.ProviderPendingIntentResponse;
import android.credentials.selection.UserSelectionDialogResult;
import android.credentials.selection.UserSelectionResult;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

@Presubmit
@AppModeFull(reason = "unit test")
@RunWith(AndroidJUnit4.class)
public class ResultHelperTest {

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @RequiresFlagsEnabled(FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED)
    public void sendUiFailure() {
        final FailureResult failure = new FailureResult(FailureResult.ERROR_CODE_UI_FAILURE, "msg");
        ResultReceiver resultReceiver = mock(ResultReceiver.class);
        ArgumentCaptor<Bundle> resultDataCaptor = ArgumentCaptor.forClass(Bundle.class);

        FailureResult.sendFailureResult(resultReceiver, failure);

        verify(resultReceiver).send(eq(/*RESULT_CODE_DATA_PARSING_FAILURE*/3),
                resultDataCaptor.capture());
        assertThat(FailureDialogResult.fromResultData(
                resultDataCaptor.getValue()).getErrorMessage()).isEqualTo("msg");
    }

    @Test
    @RequiresFlagsEnabled(FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED)
    public void sendFailure_nullMessage() {
        final FailureResult failure = new FailureResult(FailureResult.ERROR_CODE_UI_FAILURE, null);
        ResultReceiver resultReceiver = mock(ResultReceiver.class);
        ArgumentCaptor<Bundle> resultDataCaptor = ArgumentCaptor.forClass(Bundle.class);

        FailureResult.sendFailureResult(resultReceiver, failure);

        verify(resultReceiver).send(eq(/*RESULT_CODE_DATA_PARSING_FAILURE*/3),
                resultDataCaptor.capture());
        assertThat(FailureDialogResult.fromResultData(
                resultDataCaptor.getValue()).getErrorMessage()).isNull();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED)
    public void sendUserCancelFailure() {
        final FailureResult failure = new FailureResult(
                FailureResult.ERROR_CODE_DIALOG_CANCELED_BY_USER, "error");
        ResultReceiver resultReceiver = mock(ResultReceiver.class);
        ArgumentCaptor<Bundle> resultDataCaptor = ArgumentCaptor.forClass(Bundle.class);

        FailureResult.sendFailureResult(resultReceiver, failure);

        verify(resultReceiver).send(eq(/*RESULT_CODE_DIALOG_USER_CANCELED*/0),
                resultDataCaptor.capture());
        assertThat(FailureDialogResult.fromResultData(
                resultDataCaptor.getValue()).getErrorMessage()).isEqualTo("error");
    }

    @Test
    @RequiresFlagsEnabled(FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED)
    public void sendCancelAndLaunchSettingsFailure() {
        final FailureResult failure = new FailureResult(
                FailureResult.ERROR_CODE_CANCELED_AND_LAUNCHED_SETTINGS, "error msg");
        ResultReceiver resultReceiver = mock(ResultReceiver.class);
        ArgumentCaptor<Bundle> resultDataCaptor = ArgumentCaptor.forClass(Bundle.class);

        FailureResult.sendFailureResult(resultReceiver, failure);

        verify(resultReceiver).send(eq(/*RESULT_CODE_CANCELED_AND_LAUNCHED_SETTINGS*/1),
                resultDataCaptor.capture());
        assertThat(FailureDialogResult.fromResultData(
                resultDataCaptor.getValue()).getErrorMessage()).isEqualTo("error msg");
    }

    @Test
    @RequiresFlagsEnabled(FLAG_CONFIGURABLE_SELECTOR_UI_ENABLED)
    public void sendUserSelectionResult() {
        final Intent providerData = new Intent().putExtra("key1", 2L).putExtra("key2", "val");
        final UserSelectionResult result = new UserSelectionResult(
                "providerId", "entryKey", "entrySubkey",
                new ProviderPendingIntentResponse(-1, providerData));
        ResultReceiver resultReceiver = mock(ResultReceiver.class);
        ArgumentCaptor<Bundle> resultDataCaptor = ArgumentCaptor.forClass(Bundle.class);

        UserSelectionResult.sendUserSelectionResult(resultReceiver, result);

        verify(resultReceiver).send(eq(/*RESULT_CODE_DIALOG_COMPLETE_WITH_SELECTION*/2),
                resultDataCaptor.capture());
        Bundle data = resultDataCaptor.getValue();
        UserSelectionDialogResult actualResult = UserSelectionDialogResult.fromResultData(data);
        assertThat(actualResult.getProviderId()).isEqualTo("providerId");
        assertThat(actualResult.getEntryKey()).isEqualTo("entryKey");
        assertThat(actualResult.getEntrySubkey()).isEqualTo("entrySubkey");
        assertThat(actualResult.getPendingIntentProviderResponse().getResultCode()).isEqualTo(-1);
        assertEquals(actualResult.getPendingIntentProviderResponse().getResultData().getExtras(),
                providerData.getExtras());
    }
}
