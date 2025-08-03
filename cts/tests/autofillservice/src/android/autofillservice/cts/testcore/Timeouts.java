/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.compatibility.common.util.Timeout;

/**
 * Timeouts for common tasks.
 */
public final class Timeouts {

    private static final long ONE_TIMEOUT_TO_RULE_THEN_ALL_MS = 20_000;
    private static final long ONE_NAPTIME_TO_RULE_THEN_ALL_MS = 2_000;

    public static final long MOCK_IME_TIMEOUT_MS = 5_000;
    public static final long DRAWABLE_TIMEOUT_MS = 5_000;

    public static final long LONG_PRESS_MS = 3000;
    public static final long RESPONSE_DELAY_MS = 1000;

    /**
     * Timeout until framework binds / unbinds from service.
     */
    public static final Timeout CONNECTION_TIMEOUT = new Timeout("CONNECTION_TIMEOUT",
            ONE_TIMEOUT_TO_RULE_THEN_ALL_MS, 2F, ONE_TIMEOUT_TO_RULE_THEN_ALL_MS);

    /**
     * Timeout for {@link MyAutofillCallback#assertNotCalled()} - test will sleep for that amount of
     * time as there is no callback that be received to assert it's not shown.
     */
    public static final long CALLBACK_NOT_CALLED_TIMEOUT_MS = ONE_NAPTIME_TO_RULE_THEN_ALL_MS;

    /**
     * Timeout until framework unbinds from a service.
     */
    // TODO: must be higher than RemoteFillService.TIMEOUT_IDLE_BIND_MILLIS, so we should use a
    // @hidden @Testing constants instead...
    public static final Timeout IDLE_UNBIND_TIMEOUT = new Timeout("IDLE_UNBIND_TIMEOUT",
            ONE_TIMEOUT_TO_RULE_THEN_ALL_MS, 2F, ONE_TIMEOUT_TO_RULE_THEN_ALL_MS);

    /**
     * Timeout to get the expected number of fill events.
     */
    public static final Timeout FILL_EVENTS_TIMEOUT = new Timeout("FILL_EVENTS_TIMEOUT",
            ONE_TIMEOUT_TO_RULE_THEN_ALL_MS, 2F, ONE_TIMEOUT_TO_RULE_THEN_ALL_MS);

    /**
     * Timeout for expected autofill requests.
     */
    public static final Timeout FILL_TIMEOUT = new Timeout("FILL_TIMEOUT",
            ONE_TIMEOUT_TO_RULE_THEN_ALL_MS, 2F, ONE_TIMEOUT_TO_RULE_THEN_ALL_MS);

    /**
     * Timeout for expected field classification requests.
     *
     * (May need to tune based on how much time it takes for field classification request to
     * trigger)
     */
    public static final Timeout FIELD_CLASSIFICATION_TIMEOUT =
            new Timeout("FIELD_CLASSIFICATION_TIMEOUT",
                ONE_TIMEOUT_TO_RULE_THEN_ALL_MS, 2F, ONE_TIMEOUT_TO_RULE_THEN_ALL_MS);

    /**
     * Timeout for expected save requests.
     */
    public static final Timeout SAVE_TIMEOUT = new Timeout("SAVE_TIMEOUT",
            ONE_TIMEOUT_TO_RULE_THEN_ALL_MS, 2F, ONE_TIMEOUT_TO_RULE_THEN_ALL_MS);

    /**
     * Timeout used when save is not expected to be shown - test will sleep for that amount of time
     * as there is no callback that be received to assert it's not shown.
     */
    public static final long SAVE_NOT_SHOWN_NAPTIME_MS = ONE_NAPTIME_TO_RULE_THEN_ALL_MS;

    /**
     * Timeout for UI operations. Typically used by {@link UiBot}.
     */
    public static final Timeout UI_TIMEOUT = new Timeout("UI_TIMEOUT",
            ONE_TIMEOUT_TO_RULE_THEN_ALL_MS, 2F, ONE_TIMEOUT_TO_RULE_THEN_ALL_MS);

    /**
     * Timeout for a11y window change events.
     */
    public static final long WINDOW_CHANGE_TIMEOUT_MS = ONE_TIMEOUT_TO_RULE_THEN_ALL_MS;

    /**
     * Timeout used when an a11y window change events is not expected to be generated - test will
     * sleep for that amount of time as there is no callback that be received to assert it's not
     * shown.
     */
    public static final long WINDOW_CHANGE_NOT_GENERATED_NAPTIME_MS =
            ONE_NAPTIME_TO_RULE_THEN_ALL_MS;

    /**
     * Timeout for webview operations. Typically used by {@link UiBot}.
     */
    // TODO(b/80317628): switch back to ONE_TIMEOUT_TO_RULE_THEN_ALL_MS once fixed...
    public static final Timeout WEBVIEW_TIMEOUT = new Timeout("WEBVIEW_TIMEOUT", 3_000, 2F, 5_000);

    /**
     * Timeout for showing the autofill dataset picker UI.
     *
     * <p>The value is usually higher than {@link #UI_TIMEOUT} because the performance of the
     * dataset picker UI can be affect by external factors in some low-level devices.
     *
     * <p>Typically used by {@link UiBot}.
     */
    public static final Timeout UI_DATASET_PICKER_TIMEOUT = new Timeout("UI_DATASET_PICKER_TIMEOUT",
            ONE_TIMEOUT_TO_RULE_THEN_ALL_MS, 2F, ONE_TIMEOUT_TO_RULE_THEN_ALL_MS);

    /**
     * Timeout used when the dataset picker is not expected to be shown - test will sleep for that
     * amount of time as there is no callback that be received to assert it's not shown.
     */
    public static final long DATASET_PICKER_NOT_SHOWN_NAPTIME_MS = ONE_NAPTIME_TO_RULE_THEN_ALL_MS;

    /**
     * Timeout (in milliseconds) for an activity to be brought out to top.
     */
    public static final Timeout ACTIVITY_RESURRECTION = new Timeout("ACTIVITY_RESURRECTION",
            ONE_TIMEOUT_TO_RULE_THEN_ALL_MS, 2F, ONE_TIMEOUT_TO_RULE_THEN_ALL_MS);

    /**
     * Timeout for changing the screen orientation.
     */
    public static final Timeout UI_SCREEN_ORIENTATION_TIMEOUT = new Timeout(
            "UI_SCREEN_ORIENTATION_TIMEOUT", ONE_TIMEOUT_TO_RULE_THEN_ALL_MS, 2F,
            ONE_TIMEOUT_TO_RULE_THEN_ALL_MS);

    /**
     * Timeout for changing the mock ime status.
     */
    public static final Timeout MOCK_IME_TIMEOUT = new Timeout(
            "MOCK_IME_TIMEOUT", MOCK_IME_TIMEOUT_MS, 2F, MOCK_IME_TIMEOUT_MS);

    private Timeouts() {
        throw new UnsupportedOperationException("contain static methods only");
    }
}
