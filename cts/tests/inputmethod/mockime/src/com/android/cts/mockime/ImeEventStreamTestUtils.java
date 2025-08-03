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

package com.android.cts.mockime;

import android.graphics.Rect;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Pair;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputBinding;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.window.extensions.layout.DisplayFeature;
import androidx.window.extensions.layout.FoldingFeature;
import androidx.window.extensions.layout.WindowLayoutInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

/**
 * A set of utility methods to avoid boilerplate code when writing end-to-end tests.
 */
public final class ImeEventStreamTestUtils {
    private static final long TIME_SLICE = 50;  // msec

    /**
     * Cannot be instantiated
     */
    private ImeEventStreamTestUtils() {}

    /**
     * Behavior mode of {@link #expectEvent(ImeEventStream, Predicate, EventFilterMode, long)}
     */
    public enum EventFilterMode {
        /**
         * All {@link ImeEvent} events should be checked
         */
        CHECK_ALL,
        /**
         * Only events that return {@code true} from {@link ImeEvent#isEnterEvent()} should be
         * checked
         */
        CHECK_ENTER_EVENT_ONLY,
        /**
         * Only events that return {@code false} from {@link ImeEvent#isEnterEvent()} should be
         * checked
         */
        CHECK_EXIT_EVENT_ONLY;

        private Predicate<ImeEvent> combine(Predicate<ImeEvent> predicate) {
            switch (this) {
                case CHECK_ALL:
                    return predicate;
                case CHECK_ENTER_EVENT_ONLY:
                    return withDescription(predicate + " (ENTER_ONLY)",
                            event -> event.isEnterEvent() && predicate.test(event));
                case CHECK_EXIT_EVENT_ONLY:
                    return withDescription(predicate + " (EXIT_ONLY)",
                            event -> !event.isEnterEvent() && predicate.test(event));
                default:
                    throw new IllegalArgumentException("Unknown filter mode: " + this);
            }
        }
    }

    /**
     * Wait until an event that matches the given {@code condition} is found in the stream.
     *
     * <p>When this method succeeds to find an event that matches the given {@code condition}, the
     * stream position will be set to the next to the found object then the event found is returned.
     * </p>
     *
     * <p>For convenience, this method automatically filter out exit events (events that return
     * {@code false} from {@link ImeEvent#isEnterEvent()}.</p>
     *
     * <p>TODO: Consider renaming this to {@code expectEventEnter} or something like that.</p>
     *
     * @param stream {@link ImeEventStream} to be checked.
     * @param condition the event condition to be matched
     * @param timeout timeout in millisecond
     * @return {@link ImeEvent} found
     * @throws TimeoutException when the no event is matched to the given condition within
     *                          {@code timeout}
     */
    @NonNull
    public static ImeEvent expectEvent(@NonNull ImeEventStream stream,
            @NonNull Predicate<ImeEvent> condition, long timeout) throws TimeoutException {
        return expectEvent(stream, condition, EventFilterMode.CHECK_ENTER_EVENT_ONLY, timeout);
    }

    /**
     * Wait until an event that matches the given {@code condition} is found in the stream.
     *
     * <p>When this method succeeds to find an event that matches the given {@code condition}, the
     * stream position will be set to the next to the found object then the event found is returned.
     * </p>
     *
     * @param stream {@link ImeEventStream} to be checked.
     * @param condition the event condition to be matched
     * @param filterMode controls how events are filtered out
     * @param timeout timeout in millisecond
     * @return {@link ImeEvent} found
     * @throws TimeoutException when the no event is matched to the given condition within
     *                          {@code timeout}
     */
    @NonNull
    public static ImeEvent expectEvent(@NonNull ImeEventStream stream,
            @NonNull Predicate<ImeEvent> condition, EventFilterMode filterMode, long timeout)
            throws TimeoutException {
        final var combinedCondition = filterMode.combine(condition);
        try {
            while (true) {
                if (timeout < 0) {
                    throw new TimeoutException(
                            "event " + combinedCondition + " not found within the timeout: "
                                    + stream.dump());
                }
                Optional<ImeEvent> result = stream.seekToFirst(combinedCondition);
                if (result.isPresent()) {
                    stream.skip(1);
                    return result.get();
                }
                Thread.sleep(TIME_SLICE);
                timeout -= TIME_SLICE;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(
                    "expectEvent " + combinedCondition + " interrupted: " + stream.dump(), e);
        }
    }

    /**
     * Checks if {@code eventName} has occurred on the EditText(or TextView) of the current
     * activity.
     * @param eventName event name to check
     * @param marker Test marker set to {@link android.widget.EditText#setPrivateImeOptions(String)}
     * @return true if event occurred.
     */
    public static Predicate<ImeEvent> editorMatcher(
            @NonNull String eventName, @NonNull String marker) {
        return withDescription(eventName + "(marker=" + marker + ")", event -> {
            if (!TextUtils.equals(eventName, event.getEventName())) {
                return false;
            }
            final EditorInfo editorInfo = event.getArguments().getParcelable("editorInfo");
            return TextUtils.equals(marker, editorInfo.privateImeOptions);
        });
    }

    /**
     * Returns a matcher to check if the {@code name} is from
     * {@code MockIme.Tracer#onVerify(String, BooleanSupplier)}
     */
    public static Predicate<ImeEvent> verificationMatcher(@NonNull String name) {
        return withDescription("onVerify(name=" + name + ")",
                event -> "onVerify".equals(event.getEventName())
                        && name.equals(event.getArguments().getString("name")));
    }

    /**
     * Checks if {@code eventName} has occurred on the EditText(or TextView) of the current
     * activity mainly for onStartInput restarting check.
     * @param eventName event name to check
     * @param marker Test marker set to {@link android.widget.EditText#setPrivateImeOptions(String)}
     * @return true if event occurred and restarting is false.
     */
    public static Predicate<ImeEvent> editorMatcherRestartingFalse(
            @NonNull String eventName, @NonNull String marker) {
        return withDescription(eventName + "(marker=" + marker + ", restarting=false)", event -> {
            if (!TextUtils.equals(eventName, event.getEventName())) {
                return false;
            }
            final EditorInfo editorInfo = event.getArguments().getParcelable("editorInfo");
            final boolean restarting = event.getArguments().getBoolean("restarting");
            return (TextUtils.equals(marker, editorInfo.privateImeOptions) && !restarting);
        });
    }

    /**
    * Checks if {@code eventName} has occurred on the EditText(or TextView) of the current
    * activity.
    * @param eventName event name to check
    * @param fieldId typically same as {@link android.view.View#getId()}.
    * @return true if event occurred.
    */
    public static Predicate<ImeEvent> editorMatcher(@NonNull String eventName, int fieldId) {
        return withDescription(eventName + "(fieldId=" + fieldId + ")", event -> {
            if (!TextUtils.equals(eventName, event.getEventName())) {
                return false;
            }
            final EditorInfo editorInfo = event.getArguments().getParcelable("editorInfo");
            return fieldId == editorInfo.fieldId;
        });
    }

    public static Predicate<ImeEvent> showSoftInputMatcher(int requiredFlags) {
        return withDescription("showSoftInput(requiredFlags=" + requiredFlags + ")", event -> {
            if (!TextUtils.equals("showSoftInput", event.getEventName())) {
                return false;
            }
            final int flags = event.getArguments().getInt("flags");
            return (flags & requiredFlags) == requiredFlags;
        });
    }

    public static Predicate<ImeEvent> hideSoftInputMatcher() {
        return withDescription("hideSoftInput",
                event -> TextUtils.equals("hideSoftInput", event.getEventName()));
    }

    /**
     * Wait until an event that matches the given command is consumed by the {@link MockIme}.
     *
     * <p>For convenience, this method automatically filter out enter events (events that return
     * {@code true} from {@link ImeEvent#isEnterEvent()}.</p>
     *
     * <p>TODO: Consider renaming this to {@code expectCommandConsumed} or something like that.</p>
     *
     * @param stream {@link ImeEventStream} to be checked.
     * @param command {@link ImeCommand} to be waited for.
     * @param timeout timeout in millisecond
     * @return {@link ImeEvent} found
     * @throws TimeoutException when the no event is matched to the given condition within
     *                          {@code timeout}
     */
    @NonNull
    public static ImeEvent expectCommand(@NonNull ImeEventStream stream,
            @NonNull ImeCommand command, long timeout) throws TimeoutException {
        var predicate = withDescription(
                "onHandleCommand(id=" + command.getId() + ")", event -> {
                    if (!TextUtils.equals("onHandleCommand", event.getEventName())) {
                        return false;
                    }
                    final ImeCommand eventCommand =
                            ImeCommand.fromBundle(event.getArguments().getBundle("command"));
                    return eventCommand.getId() == command.getId();
                });
        return expectEvent(stream, predicate, EventFilterMode.CHECK_EXIT_EVENT_ONLY, timeout);
    }

    /**
     * Assert that an event that matches the given {@code condition} will no be found in the stream
     * within the given {@code timeout}.
     *
     * <p>When this method succeeds, the stream position will not change.</p>
     *
     * <p>For convenience, this method automatically filter out exit events (events that return
     * {@code false} from {@link ImeEvent#isEnterEvent()}.</p>
     *
     * <p>TODO: Consider renaming this to {@code notExpectEventEnter} or something like that.</p>
     *
     * @param stream {@link ImeEventStream} to be checked.
     * @param condition the event condition to be matched
     * @param timeout timeout in millisecond
     * @throws AssertionError if such an event is found within the given {@code timeout}
     */
    public static void notExpectEvent(@NonNull ImeEventStream stream,
            @NonNull Predicate<ImeEvent> condition, long timeout) {
        notExpectEvent(stream, condition, EventFilterMode.CHECK_ENTER_EVENT_ONLY, timeout);
    }

    /**
     * Assert that an event that matches the given {@code condition} will no be found in the stream
     * within the given {@code timeout}.
     *
     * <p>When this method succeeds, the stream position will not change.</p>
     *
     * @param stream {@link ImeEventStream} to be checked.
     * @param condition the event condition to be matched
     * @param filterMode controls how events are filtered out
     * @param timeout timeout in millisecond
     * @throws AssertionError if such an event is found within the given {@code timeout}
     */
    public static void notExpectEvent(@NonNull ImeEventStream stream,
            @NonNull Predicate<ImeEvent> condition, EventFilterMode filterMode, long timeout) {
        final var combinedCondition = filterMode.combine(condition);
        try {
            while (true) {
                if (timeout < 0) {
                    return;
                }
                if (stream.findFirst(combinedCondition).isPresent()) {
                    throw new AssertionError(
                            "notExpectEvent " + combinedCondition + " failed: " + stream.dump());
                }
                Thread.sleep(TIME_SLICE);
                timeout -= TIME_SLICE;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(
                    "notExpectEvent " + combinedCondition + " failed: " + stream.dump(), e);
        }
    }

    /**
     * A specialized version of {@link #expectEvent(ImeEventStream, Predicate, long)} to wait for
     * {@link android.view.inputmethod.InputMethod#bindInput(InputBinding)}.
     *
     * @param stream {@link ImeEventStream} to be checked.
     * @param targetProcessPid PID to be matched to {@link InputBinding#getPid()}
     * @param timeout timeout in millisecond
     * @throws TimeoutException when "bindInput" is not called within {@code timeout} msec
     */
    public static void expectBindInput(@NonNull ImeEventStream stream, int targetProcessPid,
            long timeout) throws TimeoutException {
        expectEvent(stream, event -> {
            if (!TextUtils.equals("bindInput", event.getEventName())) {
                return false;
            }
            final InputBinding binding = event.getArguments().getParcelable("binding");
            return binding.getPid() == targetProcessPid;
        }, EventFilterMode.CHECK_EXIT_EVENT_ONLY,  timeout);
    }

    /**
     * Checks if {@code eventName} has occurred and given {@param key} has value {@param value}.
     * @param eventName event name to check.
     * @param key the key that should be checked.
     * @param value the expected value for the given {@param key}.
     */
    public static void expectEventWithKeyValue(@NonNull ImeEventStream stream,
            @NonNull String eventName, @NonNull String key, int value, long timeout)
            throws TimeoutException {
        var condition = withDescription(eventName + "(" + key + "=" + value + ")",
                event -> TextUtils.equals(eventName, event.getEventName())
                        && value == event.getArguments().getInt(key));
        expectEvent(stream, condition, timeout);
    }

    /**
     * Assert that the {@link MockIme} will not be terminated abruptly with executing a command to
     * check if it's still alive and verify the number of create/destroy callback should be paired.
     *
     * @param session {@link MockImeSession} to be checked.
     * @param timeout timeout in millisecond to check if {@link MockIme} is still alive.
     * @throws Exception
     */
    public static void expectNoImeCrash(@NonNull MockImeSession session, long timeout)
            throws Exception {
        // Issue any trivial command to make sure that the MockIme is still alive.
        final ImeCommand command = session.callGetDisplayId();
        expectCommand(session.openEventStream(), command, timeout);
        // A filter that matches exit events of "onCreate", "onDestroy", and the *command* above.
        final Predicate<ImeEvent> matcher = event -> {
            if (!event.isEnterEvent()) {
                return false;
            }
            switch (event.getEventName()) {
                case "onHandleCommand": {
                    final ImeCommand eventCommand =
                            ImeCommand.fromBundle(event.getArguments().getBundle("command"));
                    return eventCommand.getId() == command.getId();
                }
                case "onCreate":
                case "onDestroy":
                    return true;
                default:
                    return false;
            }
        };
        final ImeEventStream stream = session.openEventStream();
        String lastEventName = null;
        // Allowed pairs of (lastEventName, eventName):
        //  - (null, "onCreate")
        //  - ("onCreate", "onDestroy")
        //  - ("onCreate", "onHandleCommand") -> then stop searching
        //  - ("onDestroy", "onCreate")
        while (true) {
            final String eventName =
                    stream.seekToFirst(matcher).map(ImeEvent::getEventName).orElse("");
            final Pair<String, String> pair = Pair.create(lastEventName, eventName);
            if (pair.equals(Pair.create("onCreate", "onHandleCommand"))) {
                break;  // Done!
            }
            if (pair.equals(Pair.create(null, "onCreate"))
                    || pair.equals(Pair.create("onCreate", "onDestroy"))
                    || pair.equals(Pair.create("onDestroy", "onCreate"))) {
                lastEventName = eventName;
                stream.skip(1);
                continue;
            }
            throw new AssertionError("IME might have crashed. lastEventName="
                    + lastEventName + " eventName=" + eventName + "\n" + stream.dump());
        }
    }

    /**
     * Waits until {@code MockIme} does not send {@code "onInputViewLayoutChanged"} event
     * for a certain period of time ({@code stableThresholdTime} msec).
     *
     * <p>When this returns non-null {@link ImeLayoutInfo}, the stream position will be set to
     * the next event of the returned layout event.  Otherwise this method does not change stream
     * position.</p>
     * @param stream {@link ImeEventStream} to be checked.
     * @param stableThresholdTime threshold time to consider that {@link MockIme}'s layout is
     *                            stable, in millisecond
     * @return last {@link ImeLayoutInfo} if {@link MockIme} sent one or more
     *         {@code "onInputViewLayoutChanged"} event.  Otherwise {@code null}
     */
    public static ImeLayoutInfo waitForInputViewLayoutStable(@NonNull ImeEventStream stream,
            long stableThresholdTime) {
        ImeLayoutInfo lastLayout = null;
        final Predicate<ImeEvent> layoutFilter = event ->
                !event.isEnterEvent() && event.getEventName().equals("onInputViewLayoutChanged");
        try {
            long deadline = SystemClock.elapsedRealtime() + stableThresholdTime;
            while (true) {
                if (deadline < SystemClock.elapsedRealtime()) {
                    return lastLayout;
                }
                final Optional<ImeEvent> event = stream.seekToFirst(layoutFilter);
                if (event.isPresent()) {
                    // Remember the last event and extend the deadline again.
                    lastLayout = ImeLayoutInfo.readFromBundle(event.get().getArguments());
                    deadline = SystemClock.elapsedRealtime() + stableThresholdTime;
                    stream.skip(1);
                }
                Thread.sleep(TIME_SLICE);
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("notExpectEvent failed: " + stream.dump(), e);
        }
    }

    /**
     * Clear all events with {@code eventName} in given {@code stream} and returns a forked
     * {@link ImeEventStream} without events with {@code eventName}.
     * <p>It is used to make sure previous events influence the test. </p>
     *
     * @param stream {@link ImeEventStream} to be cleared
     * @param eventName The targeted cleared event name
     * @return A forked {@link ImeEventStream} without event with {@code eventName}
     */
    public static ImeEventStream clearAllEvents(@NonNull ImeEventStream stream,
            @NonNull String eventName) {
        while (stream.seekToFirst(event -> eventName.equals(event.getEventName())).isPresent()) {
            stream.skip(1);
        }
        return stream.copy();
    }

    public static Predicate<ImeEvent> withDescription(String description, Predicate<ImeEvent> p) {
        return new Predicate<>() {
            @Override
            public boolean test(ImeEvent ev) {
                return p.test(ev);
            }

            @Override
            public String toString() {
                return description;
            }
        };
    }

    /**
     *  A copy of {@link WindowLayoutInfo} class just for the purpose of testing with MockIME
     *  test setup.
     *  This is because only in this setup we will pass {@link WindowLayoutInfo} through
     *  different processes.
     */
    public static class WindowLayoutInfoParcelable implements Parcelable {
        private List<DisplayFeature> mDisplayFeatures = new ArrayList<DisplayFeature>();

        public WindowLayoutInfoParcelable(WindowLayoutInfo windowLayoutInfo) {
            this.mDisplayFeatures = windowLayoutInfo.getDisplayFeatures();
        }
        public WindowLayoutInfoParcelable(Parcel in) {
            while (in.dataAvail() > 0) {
                Rect bounds;
                int type = -1, state = -1;
                bounds = in.readParcelable(Rect.class.getClassLoader(), Rect.class);
                type = in.readInt();
                state = in.readInt();
                mDisplayFeatures.add(new FoldingFeature(bounds, type, state));
            }
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof WindowLayoutInfoParcelable)) {
                return false;
            }

            List<androidx.window.extensions.layout.DisplayFeature> listA =
                    this.getDisplayFeatures();
            List<DisplayFeature> listB = ((WindowLayoutInfoParcelable) o).getDisplayFeatures();
            if (listA.size() != listB.size()) return false;
            for (int i = 0; i < listA.size(); ++i) {
                if (!listA.get(i).equals(listB.get(i))) {
                    return false;
                }
            }

            return true;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            // The actual implementation is FoldingFeature, DisplayFeature is an abstract class.
            mDisplayFeatures.forEach(feature -> {
                        dest.writeParcelable(feature.getBounds(), flags);
                        dest.writeInt(((FoldingFeature) feature).getType());
                        dest.writeInt(((FoldingFeature) feature).getState());
                    }
            );
        }

        public List<DisplayFeature> getDisplayFeatures() {
            return mDisplayFeatures;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public static final Parcelable.Creator<WindowLayoutInfoParcelable> CREATOR =
                new Parcelable.Creator<WindowLayoutInfoParcelable>() {

                    @Override
                    public WindowLayoutInfoParcelable createFromParcel(Parcel in) {
                        return new WindowLayoutInfoParcelable(in);
                    }

                    @Override
                    public WindowLayoutInfoParcelable[] newArray(int size) {
                        return new WindowLayoutInfoParcelable[size];
                    }
                };
    }
}
