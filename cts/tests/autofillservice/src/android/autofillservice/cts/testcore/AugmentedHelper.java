/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.autofillservice.cts.testcore;

import static android.autofillservice.cts.testcore.Timeouts.CONNECTION_TIMEOUT;
import static android.view.autofill.AutofillManager.MAX_TEMP_AUGMENTED_SERVICE_DURATION_MS;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.Activity;
import android.app.assist.AssistStructure;
import android.autofillservice.cts.testcore.CtsAugmentedAutofillService.AugmentedFillRequest;
import android.content.ComponentName;
import android.content.Context;
import android.service.autofill.augmented.FillRequest;
import android.util.Log;
import android.util.Pair;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.view.inputmethod.InlineSuggestionsRequest;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.cts.mockime.MockImeSession;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Helper for common funcionalities.
 */
public final class AugmentedHelper {

    private static final String TAG = AugmentedHelper.class.getSimpleName();

    @NonNull
    public static String getActivityName(@Nullable FillRequest request) {
        if (request == null) return "N/A (null request)";

        final ComponentName componentName = request.getActivityComponent();
        if (componentName == null) return "N/A (no component name)";

        return componentName.flattenToShortString();
    }

    /**
     * Sets the augmented capture service.
     */
    public static void setAugmentedService(@NonNull String service, Context context) {
        int userId = context.getUserId();
        Log.d(TAG, "Setting service to " + service + " for user " + userId);

        runShellCommand("cmd autofill set temporary-augmented-service %d %s %d", userId,
                service, MAX_TEMP_AUGMENTED_SERVICE_DURATION_MS);
    }

    /**
     * Resets the content capture service.
     */
    public static void resetAugmentedService(Context context) {
        int userId = context.getUserId();
        Log.d(TAG, "Resetting back to default service" + " for user " + userId);
        runShellCommand("cmd autofill set temporary-augmented-service %d", userId);
    }

    /**
     * Returns whether MockIme is available.
     */
    public static boolean mockImeIsAvailable(Context context) {
        return MockImeSession.getUnavailabilityReason(context) == null;
    }

    public static void assertBasicRequestInfo(@NonNull AugmentedFillRequest request,
            @NonNull Activity activity, @NonNull AutofillId expectedFocusedId,
            @Nullable AutofillValue expectedFocusedValue) {
        final boolean hasDefaultInlineRequest = mockImeIsAvailable(activity.getBaseContext());
        assertBasicRequestInfo(request, activity, expectedFocusedId, expectedFocusedValue,
                hasDefaultInlineRequest);
    }

    public static void assertBasicRequestInfo(@NonNull AugmentedFillRequest request,
            @NonNull Activity activity, @NonNull AutofillId expectedFocusedId,
            @Nullable AutofillValue expectedFocusedValue, boolean hasInlineRequest) {
        Objects.requireNonNull(activity);
        Objects.requireNonNull(expectedFocusedId);
        assertWithMessage("no AugmentedFillRequest").that(request).isNotNull();
        assertWithMessage("no FillRequest on %s", request).that(request.request).isNotNull();
        assertWithMessage("no FillController on %s", request).that(request.controller).isNotNull();
        assertWithMessage("no FillCallback on %s", request).that(request.callback).isNotNull();
        assertWithMessage("no CancellationSignal on %s", request).that(request.cancellationSignal)
                .isNotNull();
        // NOTE: task id can change, we might need to set it in the activity's onCreate()
        assertWithMessage("wrong task id on %s", request).that(request.request.getTaskId())
                .isEqualTo(activity.getTaskId());

        final ComponentName actualComponentName = request.request.getActivityComponent();
        assertWithMessage("no activity name on %s", request).that(actualComponentName).isNotNull();
        assertWithMessage("wrong activity name on %s", request).that(actualComponentName)
                .isEqualTo(activity.getComponentName());
        final AutofillId actualFocusedId = request.request.getFocusedId();
        assertWithMessage("no focused id on %s", request).that(actualFocusedId).isNotNull();
        assertWithMessage("wrong focused id on %s", request).that(actualFocusedId)
                .isEqualTo(expectedFocusedId);
        final AutofillValue actualFocusedValue = request.request.getFocusedValue();
        if (expectedFocusedValue != null) {
            assertWithMessage("no focused value on %s", request).that(
                    actualFocusedValue).isNotNull();
            assertAutofillValue(expectedFocusedValue, actualFocusedValue);
        } else {
            assertWithMessage("expecting null focused value on %s", request).that(
                    actualFocusedValue).isNull();
        }
        if (expectedFocusedId.isNonVirtual()) {
            final AssistStructure.ViewNode focusedViewNode = request.request.getFocusedViewNode();
            assertWithMessage("no focused view node on %s", request).that(
                    focusedViewNode).isNotNull();
            assertWithMessage("wrong autofill id in focused view node %s", focusedViewNode).that(
                    focusedViewNode.getAutofillId()).isEqualTo(expectedFocusedId);
            assertWithMessage("unexpected autofill value in focused view node %s",
                    focusedViewNode).that(focusedViewNode.getAutofillValue()).isEqualTo(
                    expectedFocusedValue);
            assertWithMessage("children nodes should not be populated for focused view node %s",
                    focusedViewNode).that(
                    focusedViewNode.getChildCount()).isEqualTo(0);
        }
        final InlineSuggestionsRequest inlineRequest =
                request.request.getInlineSuggestionsRequest();
        if (hasInlineRequest) {
            assertWithMessage("no inline request on %s", request).that(inlineRequest).isNotNull();
        } else {
            assertWithMessage("exist inline request on %s", request).that(inlineRequest).isNull();
        }
    }

    public static void assertAutofillValue(@NonNull AutofillValue expectedValue,
            @NonNull AutofillValue actualValue) {
        // It only supports text values for now...
        assertWithMessage("expected value is not text: %s", expectedValue)
                .that(expectedValue.isText()).isTrue();
        assertAutofillValue(expectedValue.getTextValue().toString(), actualValue);
    }

    public static void assertAutofillValue(@NonNull String expectedValue,
            @NonNull AutofillValue actualValue) {
        assertWithMessage("actual value is not text: %s", actualValue)
                .that(actualValue.isText()).isTrue();

        assertWithMessage("wrong autofill value").that(actualValue.getTextValue().toString())
                .isEqualTo(expectedValue);
    }

    @NonNull
    public static String toString(@Nullable List<Pair<AutofillId, AutofillValue>> values) {
        if (values == null) return "null";
        final StringBuilder string = new StringBuilder("[");
        final int size = values.size();
        for (int i = 0; i < size; i++) {
            final Pair<AutofillId, AutofillValue> value = values.get(i);
            string.append(i).append(':').append(value.first).append('=')
                   .append(Helper.toString(value.second));
            if (i < size - 1) {
                string.append(", ");
            }

        }
        return string.append(']').toString();
    }

    @NonNull
    public static String toString(@Nullable FillRequest request) {
        if (request == null) return "(null request)";

        final StringBuilder string =
                new StringBuilder("FillRequest[act=").append(getActivityName(request))
                .append(", taskId=").append(request.getTaskId());

        final AutofillId focusedId = request.getFocusedId();
        if (focusedId != null) {
            string.append(", focusedId=").append(focusedId);
        }
        final AutofillValue focusedValue = request.getFocusedValue();
        if (focusedValue != null) {
            string.append(", focusedValue=").append(focusedValue);
        }

        return string.append(']').toString();
    }

    // Used internally by UiBot to assert the UI
    public static String getContentDescriptionForUi(@NonNull AutofillId focusedId) {
        return "ui_for_" + focusedId;
    }

    private AugmentedHelper() {
        throw new UnsupportedOperationException("contain static methods only");
    }

    /**
     * Awaits for a latch to be counted down.
     */
    public static void await(@NonNull CountDownLatch latch, @NonNull String fmt,
            @Nullable Object... args)
            throws InterruptedException {
        final boolean called = latch.await(CONNECTION_TIMEOUT.ms(), TimeUnit.MILLISECONDS);
        if (!called) {
            throw new IllegalStateException(String.format(fmt, args)
                    + " in " + CONNECTION_TIMEOUT.ms() + "ms");
        }
    }
}
