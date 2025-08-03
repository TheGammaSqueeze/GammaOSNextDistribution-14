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

package com.android.cts.mocka11yime;

import android.os.SystemClock;
import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;

import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

/**
 * Provides a set of utility methods to avoid boilerplate code when writing end-to-end tests.
 */
public final class MockA11yImeEventStreamUtils {
    private static final long TIME_SLICE = 50;  // msec

    /**
     * Not intended to be instantiated.
     */
    private MockA11yImeEventStreamUtils() {
    }

    /**
     * Behavior mode of {@link #expectA11yImeEvent(MockA11yImeEventStream, Predicate,
     * MockA11yImeEventStreamUtils.EventFilterMode, long)}
     */
    public enum EventFilterMode {
        /**
         * All {@link MockA11yImeEvent} events should be checked
         */
        CHECK_ALL,
        /**
         * Only events that return {@code true} from {@link MockA11yImeEvent#isEnterEvent()} should
         * be checked
         */
        CHECK_ENTER_EVENT_ONLY,
        /**
         * Only events that return {@code false} from {@link MockA11yImeEvent#isEnterEvent()} should
         * be checked
         */
        CHECK_EXIT_EVENT_ONLY,
    }

    /**
     * Wait until an event that matches the given {@code condition} is found in the stream.
     *
     * <p>When this method succeeds to find an event that matches the given {@code condition}, the
     * stream position will be set to the next to the found object then the event found is returned.
     * </p>
     *
     * <p>For convenience, this method automatically filter out exit events (events that return
     * {@code false} from {@link MockA11yImeEvent#isEnterEvent()}.</p>
     *
     * @param stream {@link MockA11yImeEventStream} to be checked.
     * @param condition the event condition to be matched
     * @param timeout timeout in millisecond
     * @return {@link MockA11yImeEvent} found
     * @throws TimeoutException when the no event is matched to the given condition within
     *                          {@code timeout}
     */
    @NonNull
    public static MockA11yImeEvent expectA11yImeEvent(@NonNull MockA11yImeEventStream stream,
            @NonNull Predicate<MockA11yImeEvent> condition, long timeout) throws TimeoutException {
        return expectA11yImeEvent(stream, condition,
                MockA11yImeEventStreamUtils.EventFilterMode.CHECK_ENTER_EVENT_ONLY, timeout);
    }

    /**
     * Wait until an event that matches the given {@code condition} is found in the stream.
     *
     * <p>When this method succeeds to find an event that matches the given {@code condition}, the
     * stream position will be set to the next to the found object then the event found is returned.
     * </p>
     *
     * @param stream {@link MockA11yImeEventStream} to be checked.
     * @param condition the event condition to be matched
     * @param filterMode controls how events are filtered out
     * @param timeout timeout in millisecond
     * @return {@link MockA11yImeEvent} found
     * @throws TimeoutException when the no event is matched to the given condition within
     *                          {@code timeout}
     */
    @NonNull
    public static MockA11yImeEvent expectA11yImeEvent(@NonNull MockA11yImeEventStream stream,
            @NonNull Predicate<MockA11yImeEvent> condition,
            MockA11yImeEventStreamUtils.EventFilterMode filterMode, long timeout)
            throws TimeoutException {
        while (true) {
            if (timeout < 0) {
                throw new TimeoutException(
                        "event not found within the timeout: " + stream.dump());
            }
            final Predicate<MockA11yImeEvent> combinedCondition;
            switch (filterMode) {
                case CHECK_ALL:
                    combinedCondition = condition;
                    break;
                case CHECK_ENTER_EVENT_ONLY:
                    combinedCondition = event -> event.isEnterEvent() && condition.test(event);
                    break;
                case CHECK_EXIT_EVENT_ONLY:
                    combinedCondition = event -> !event.isEnterEvent() && condition.test(event);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown filterMode " + filterMode);
            }
            final Optional<MockA11yImeEvent> result = stream.seekToFirst(combinedCondition);
            if (result.isPresent()) {
                stream.skip(1);
                return result.get();
            }
            SystemClock.sleep(TIME_SLICE);
            timeout -= TIME_SLICE;
        }
    }

    /**
     * Checks if {@code eventName} has occurred on the EditText(or TextView) of the current
     * activity.
     * @param eventName event name to check
     * @param marker Test marker set to {@link android.widget.EditText#setPrivateImeOptions(String)}
     * @return true if event occurred.
     */
    public static Predicate<MockA11yImeEvent> editorMatcherForA11yIme(
            @NonNull String eventName, @NonNull String marker) {
        return event -> {
            if (!TextUtils.equals(eventName, event.getEventName())) {
                return false;
            }
            final EditorInfo editorInfo = event.getArguments().getParcelable("editorInfo",
                    EditorInfo.class);
            return TextUtils.equals(marker, editorInfo.privateImeOptions);
        };
    }


    /**
     * Wait until an event that matches the given command is consumed by the MockA11yIme.
     *
     * <p>For convenience, this method automatically filter out enter events (events that return
     * {@code true} from {@link MockA11yImeEvent#isEnterEvent()}.</p>
     *
     * @param stream {@link MockA11yImeEventStream} to be checked.
     * @param command {@link MockA11yImeCommand} to be waited for.
     * @param timeout timeout in millisecond
     * @return {@link MockA11yImeEvent} found
     * @throws TimeoutException when the no event is matched to the given condition within
     *                          {@code timeout}
     */
    @NonNull
    public static MockA11yImeEvent expectA11yImeCommand(@NonNull MockA11yImeEventStream stream,
            @NonNull MockA11yImeCommand command, long timeout) throws TimeoutException {
        final Predicate<MockA11yImeEvent> predicate = event -> {
            if (!TextUtils.equals("onHandleCommand", event.getEventName())) {
                return false;
            }
            final MockA11yImeCommand eventCommand =
                    MockA11yImeCommand.fromBundle(event.getArguments().getBundle("command"));
            return eventCommand.getId() == command.getId();
        };
        return expectA11yImeEvent(stream, predicate,
                MockA11yImeEventStreamUtils.EventFilterMode.CHECK_EXIT_EVENT_ONLY, timeout);
    }

    /**
     * Assert that an event that matches the given {@code condition} will no be found in the stream
     * within the given {@code timeout}.
     *
     * <p>When this method succeeds, the stream position will not change.</p>
     *
     * <p>For convenience, this method automatically filter out exit events (events that return
     * {@code false} from {@link MockA11yImeEvent#isEnterEvent()}.</p>
     *
     * @param stream {@link MockA11yImeEventStream} to be checked.
     * @param condition the event condition to be matched
     * @param timeout timeout in millisecond
     * @throws AssertionError if such an event is found within the given {@code timeout}
     */
    public static void notExpectA11yImeEvent(@NonNull MockA11yImeEventStream stream,
            @NonNull Predicate<MockA11yImeEvent> condition, long timeout) {
        notExpectA11yImeEvent(stream, condition,
                MockA11yImeEventStreamUtils.EventFilterMode.CHECK_ENTER_EVENT_ONLY, timeout);
    }

    /**
     * Assert that an event that matches the given {@code condition} will no be found in the stream
     * within the given {@code timeout}.
     *
     * <p>When this method succeeds, the stream position will not change.</p>
     *
     * @param stream {@link MockA11yImeEventStream} to be checked.
     * @param condition the event condition to be matched
     * @param filterMode controls how events are filtered out
     * @param timeout timeout in millisecond
     * @throws AssertionError if such an event is found within the given {@code timeout}
     */
    public static void notExpectA11yImeEvent(@NonNull MockA11yImeEventStream stream,
            @NonNull Predicate<MockA11yImeEvent> condition,
            MockA11yImeEventStreamUtils.EventFilterMode filterMode, long timeout) {
        final Predicate<MockA11yImeEvent> combinedCondition;
        switch (filterMode) {
            case CHECK_ALL:
                combinedCondition = condition;
                break;
            case CHECK_ENTER_EVENT_ONLY:
                combinedCondition = event -> event.isEnterEvent() && condition.test(event);
                break;
            case CHECK_EXIT_EVENT_ONLY:
                combinedCondition = event -> !event.isEnterEvent() && condition.test(event);
                break;
            default:
                throw new IllegalArgumentException("Unknown filterMode " + filterMode);
        }
        while (true) {
            if (timeout < 0) {
                return;
            }
            if (stream.findFirst(combinedCondition).isPresent()) {
                throw new AssertionError("notExpectEvent failed: " + stream.dump());
            }
            SystemClock.sleep(TIME_SLICE);
            timeout -= TIME_SLICE;
        }
    }
}
