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

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.BroadcastReceiver;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.inputmethod.TextAttribute;

import androidx.annotation.AnyThread;
import androidx.annotation.GuardedBy;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.compatibility.common.util.PollingCheck;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * Works as a controller and lifecycle object of an active session to MockA11yIme.
 *
 * <p>Public methods are not thread-safe.</p>
 */
public final class MockA11yImeSession implements AutoCloseable {
    private static final String ACTION_NAME_PREFIX = "com.android.cts.mocka11yime.EVENT_ACTION_";

    @NonNull
    private final Context mContext;

    @NonNull
    private final String mActionName;

    private final MockA11yImeEventStream mEventStream;

    @FunctionalInterface
    private interface Closer {
        void close() throws Exception;
    }

    @NonNull
    private final Closer mCloser;

    @NonNull
    private final AtomicBoolean mActive = new AtomicBoolean(true);

    private static final class EventStore {
        private static final int INITIAL_ARRAY_SIZE = 32;

        @NonNull
        public final MockA11yImeEvent[] mArray;
        public int mLength;

        EventStore() {
            mArray = new MockA11yImeEvent[INITIAL_ARRAY_SIZE];
            mLength = 0;
        }

        EventStore(EventStore src, int newLength) {
            mArray = new MockA11yImeEvent[newLength];
            mLength = src.mLength;
            System.arraycopy(src.mArray, 0, mArray, 0, src.mLength);
        }

        public EventStore add(MockA11yImeEvent event) {
            if (mLength + 1 <= mArray.length) {
                mArray[mLength] = event;
                ++mLength;
                return this;
            } else {
                return new EventStore(this, mLength * 2).add(event);
            }
        }

        public MockA11yImeEventStream.EventArray takeSnapshot() {
            return new MockA11yImeEventStream.EventArray(mArray, mLength);
        }
    }

    private MockA11yImeSession(@NonNull Context context,
            @NonNull String actionName,
            @NonNull MockA11yImeEventStream eventStream,
            @NonNull Closer closer) {
        mContext = context;
        mActionName = actionName;
        mCloser = closer;
        mEventStream = eventStream;
    }

    private static final class EventReceiver extends BroadcastReceiver {
        private final Object mLock = new Object();

        @GuardedBy("mLock")
        @NonNull
        private EventStore mCurrentEventStore = new EventStore();

        @NonNull
        private final String mActionName;

        EventReceiver(@NonNull String actionName) {
            mActionName = actionName;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (TextUtils.equals(mActionName, intent.getAction())) {
                synchronized (mLock) {
                    mCurrentEventStore = mCurrentEventStore.add(
                            MockA11yImeEvent.fromBundle(intent.getExtras()));
                }
            }
        }

        @AnyThread
        public MockA11yImeEventStream.EventArray takeEventSnapshot() {
            synchronized (mLock) {
                return mCurrentEventStore.takeSnapshot();
            }
        }
    }

    /**
     * Creates a new MockA11yIme session.
     *
     * <p>Note that in general you cannot call {@link Instrumentation#getUiAutomation()} while
     * using {@link MockA11yImeSession} because doing so creates a new {@link UiAutomation} instance
     * without {@link UiAutomation#FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES}, which kills all the
     * instances of {@link android.accessibilityservice.AccessibilityService} including MockA11yIme.
     * </p>
     *
     * @param context {@link Context} to be used to receive inter-process events from the
     *                MockA11yIme. (e.g. via {@link BroadcastReceiver}
     * @param uiAutomation {@link UiAutomation}, which is initialized at least with
     *                     {@link UiAutomation#FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES}.
     * @param settings {@link MockA11yImeSettings} to be passed to MockA11yIme.
     * @return A session object, with which you can retrieve event logs from the MockA11yIme and
     *         can clean up the session.
     */
    public static MockA11yImeSession create(@NonNull Context context,
            @NonNull UiAutomation uiAutomation, @NonNull MockA11yImeSettings settings,
            long timeout) throws Exception {

        final String originalEnabledAccessibilityServices =
                Settings.Secure.getString(context.getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);

        final Callable<Boolean> notEnabledCondition;
        final Callable<Boolean> enabledCondition;
        {
            final AccessibilityManager accessibilityManager =
                    context.getSystemService(AccessibilityManager.class);
            final Predicate<AccessibilityServiceInfo> mockA11yImeMatcher =
                    info -> MockA11yImeConstants.COMPONENT_NAME.equals(info.getComponentName());
            notEnabledCondition = () -> accessibilityManager.getEnabledAccessibilityServiceList(
                            AccessibilityServiceInfo.FEEDBACK_GENERIC)
                    .stream()
                    .noneMatch(mockA11yImeMatcher);
            enabledCondition = () -> accessibilityManager.getEnabledAccessibilityServiceList(
                            AccessibilityServiceInfo.FEEDBACK_GENERIC)
                    .stream()
                    .anyMatch(mockA11yImeMatcher);
        }

        final String actionName = ACTION_NAME_PREFIX + SystemClock.elapsedRealtimeNanos();
        final EventReceiver receiver = new EventReceiver(actionName);

        final HandlerThread handlerThread = new HandlerThread("EventReceiver");
        handlerThread.start();
        context.registerReceiver(receiver,
                new IntentFilter(actionName), null /* broadcastPermission */,
                new Handler(handlerThread.getLooper()), Context.RECEIVER_EXPORTED);

        runWithShellPermission(uiAutomation, Manifest.permission.WRITE_SECURE_SETTINGS, () ->
                Settings.Secure.putString(context.getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, null));

        final ContentProviderClient contentProviderClient = context.getContentResolver()
                .acquireContentProviderClient(MockA11yImeConstants.SETTINGS_PROVIDER_AUTHORITY);
        if (contentProviderClient == null) {
            throw new UnsupportedOperationException("Failed to find "
                    + MockA11yImeConstants.SETTINGS_PROVIDER_AUTHORITY);
        }

        PollingCheck.check("Wait until MockA11yIME becomes unavailable", timeout,
                notEnabledCondition);

        final Bundle bundle = new Bundle();
        bundle.putString(
                MockA11yImeConstants.BundleKey.EVENT_CALLBACK_INTENT_ACTION_NAME,
                actionName);
        bundle.putParcelable(
                MockA11yImeConstants.BundleKey.SETTINGS, settings.getRawBundle());

        contentProviderClient.call(
                MockA11yImeConstants.SETTINGS_PROVIDER_AUTHORITY,
                MockA11yImeConstants.ContentProviderCommand.WRITE, null, bundle);

        runWithShellPermission(uiAutomation, Manifest.permission.WRITE_SECURE_SETTINGS, () ->
                Settings.Secure.putString(context.getContentResolver(),
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                        MockA11yImeConstants.COMPONENT_NAME.flattenToShortString()));

        PollingCheck.check("MockA11yIme did not become available in " + timeout + " msec. "
                + "Make sure you set UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES when "
                + "obtaining UiAutomation object.", timeout, enabledCondition);

        return new MockA11yImeSession(context, actionName,
                new MockA11yImeEventStream(receiver::takeEventSnapshot), () -> {

            context.unregisterReceiver(receiver);
            handlerThread.quitSafely();

            contentProviderClient.call(
                    MockA11yImeConstants.SETTINGS_PROVIDER_AUTHORITY,
                    MockA11yImeConstants.ContentProviderCommand.DELETE, null, null);
            contentProviderClient.close();

            runWithShellPermission(uiAutomation, Manifest.permission.WRITE_SECURE_SETTINGS, () ->
                    Settings.Secure.putString(context.getContentResolver(),
                            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                            originalEnabledAccessibilityServices));
        });
    }

    private static void runWithShellPermission(@NonNull UiAutomation uiAutomation,
            @NonNull String permission, @NonNull Runnable runnable) {
        uiAutomation.adoptShellPermissionIdentity(permission);
        try {
            runnable.run();
        } finally {
            uiAutomation.dropShellPermissionIdentity();
        }
    }

    /**
     * @return {@link MockA11yImeEventStream} object that stores events sent from MockA11yIme.
     */
    public MockA11yImeEventStream openEventStream() {
        return mEventStream.copy();
    }

    /**
     * Closes the active session and disable MockA11yIme.
     */
    public void close() throws Exception {
        mActive.set(false);
        mCloser.close();
    }

    /**
     * @return {@code true} until {@link #close()} gets called.
     */
    @AnyThread
    public boolean isActive() {
        return mActive.get();
    }

    /**
     * Common logic to send a special command to MockA11yIme.
     *
     * @param commandName command to be passed to MockA11yIme
     * @param params {@link Bundle} to be passed to MockA11yIme as a parameter set of
     *               {@code commandName}
     * @return {@link MockA11yImeCommand} that is sent to MockA11yIme.
     */
    @NonNull
    private MockA11yImeCommand callCommandInternal(@NonNull String commandName,
            @NonNull Bundle params) {
        final MockA11yImeCommand command = new MockA11yImeCommand(
                commandName, SystemClock.elapsedRealtimeNanos(), true, params);
        final Intent intent = new Intent();
        intent.setPackage(MockA11yImeConstants.COMPONENT_NAME.getPackageName());
        intent.setAction(mActionName);
        intent.setFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        intent.putExtras(command.toBundle());
        mContext.sendBroadcast(intent);
        return command;
    }

    /**
     * Lets MockA11yIme call
     * {@link android.accessibilityservice.AccessibilityService#getInputMethod()} then
     * {@link android.accessibilityservice.InputMethod#getCurrentInputConnection()} to
     * memorize it for later
     * {@link android.accessibilityservice.InputMethod.AccessibilityInputConnection}-related
     * operations.
     *
     * <p>Only the last one will be memorized if this method gets called multiple times.</p>
     *
     * @return {@link MockA11yImeCommand} object that can be passed to
     *         {@link MockA11yImeEventStreamUtils#expectA11yImeCommand(MockA11yImeEventStream,
     *         MockA11yImeCommand, long)} to wait until this event is handled by MockA11yIme.
     * @see #unmemorizeCurrentInputConnection()
     */
    @NonNull
    public MockA11yImeCommand memorizeCurrentInputConnection() {
        final Bundle params = new Bundle();
        return callCommandInternal("memorizeCurrentInputConnection", params);
    }

    /**
     * Lets MockA11yIme forget memorized
     * {@link android.accessibilityservice.InputMethod.AccessibilityInputConnection} if any. Does
     * nothing otherwise.
     *
     * @return {@link MockA11yImeCommand} object that can be passed to
     *         {@link MockA11yImeEventStreamUtils#expectA11yImeCommand(MockA11yImeEventStream,
     *         MockA11yImeCommand, long)} to wait until this event is handled by MockA11yIme.
     * @see #memorizeCurrentInputConnection()
     */
    @NonNull
    public MockA11yImeCommand unmemorizeCurrentInputConnection() {
        final Bundle params = new Bundle();
        return callCommandInternal("unmemorizeCurrentInputConnection", params);
    }

    /**
     * Lets MockA11yIme call
     * {@link android.accessibilityservice.AccessibilityService#getInputMethod()} then
     * {@link android.accessibilityservice.InputMethod#getCurrentInputStarted()}.
     *
     * <p>Use {@link MockA11yImeEvent#getReturnBooleanValue()} for {@link MockA11yImeEvent}
     * returned from {@link MockA11yImeEventStreamUtils#expectA11yImeCommand(MockA11yImeEventStream,
     * MockA11yImeCommand, long)} to see the value returned from the API.</p>
     *
     * @return {@link MockA11yImeCommand} object that can be passed to
     *         {@link MockA11yImeEventStreamUtils#expectA11yImeCommand(MockA11yImeEventStream,
     *         MockA11yImeCommand, long)} to wait until this event is handled by MockA11yIme.
     */
    @NonNull
    public MockA11yImeCommand callGetCurrentInputStarted() {
        final Bundle params = new Bundle();
        return callCommandInternal("getCurrentInputStarted", params);
    }

    /**
     * Lets MockA11yIme call
     * {@link android.accessibilityservice.AccessibilityService#getInputMethod()} then
     * {@link android.accessibilityservice.InputMethod#getCurrentInputEditorInfo()}.
     *
     * <p>Use {@link MockA11yImeEvent#getReturnParcelableValue()} for {@link MockA11yImeEvent}
     * returned from {@link MockA11yImeEventStreamUtils#expectA11yImeCommand(MockA11yImeEventStream,
     * MockA11yImeCommand, long)} to see the value returned from the API.</p>
     *
     * @return {@link MockA11yImeCommand} object that can be passed to
     *         {@link MockA11yImeEventStreamUtils#expectA11yImeCommand(MockA11yImeEventStream,
     *         MockA11yImeCommand, long)} to wait until this event is handled by MockA11yIme.
     */
    @NonNull
    public MockA11yImeCommand callGetCurrentInputEditorInfo() {
        final Bundle params = new Bundle();
        return callCommandInternal("getCurrentInputEditorInfo", params);
    }

    /**
     * Lets MockA11yIme call
     * {@link android.accessibilityservice.AccessibilityService#getInputMethod()} then
     * {@link android.accessibilityservice.InputMethod#getCurrentInputConnection()}.
     *
     * <p>Use {@link MockA11yImeEvent#isNullReturnValue()} for {@link MockA11yImeEvent}
     * returned from {@link MockA11yImeEventStreamUtils#expectA11yImeCommand(MockA11yImeEventStream,
     * MockA11yImeCommand, long)} to see the value returned from the API was null or not.</p>
     *
     * @return {@link MockA11yImeCommand} object that can be passed to
     *         {@link MockA11yImeEventStreamUtils#expectA11yImeCommand(MockA11yImeEventStream,
     *         MockA11yImeCommand, long)} to wait until this event is handled by MockA11yIme.
     */
    @NonNull
    public MockA11yImeCommand callGetCurrentInputConnection() {
        final Bundle params = new Bundle();
        return callCommandInternal("getCurrentInputConnection", params);
    }

    /**
     * Lets MockA11yIme call {@link
     * android.accessibilityservice.InputMethod.AccessibilityInputConnection#commitText(
     * CharSequence, int, TextAttribute)} with the given parameters.
     *
     * @param text to be passed as the {@code text} parameter
     * @param newCursorPosition to be passed as the {@code newCursorPosition} parameter
     * @return {@link MockA11yImeCommand} object that can be passed to
     *         {@link MockA11yImeEventStreamUtils#expectA11yImeCommand(MockA11yImeEventStream,
     *         MockA11yImeCommand, long)} to wait until this event is handled by MockA11yIme.
     */
    @NonNull
    public MockA11yImeCommand callCommitText(@Nullable CharSequence text, int newCursorPosition,
            @Nullable TextAttribute textAttribute) {
        final Bundle params = new Bundle();
        params.putCharSequence("text", text);
        params.putInt("newCursorPosition", newCursorPosition);
        params.putParcelable("textAttribute", textAttribute);
        return callCommandInternal("commitText", params);
    }

    /**
     * Lets MockA11yIme call {@link
     * android.accessibilityservice.InputMethod.AccessibilityInputConnection#setSelection(int, int)}
     * with the given parameters.
     *
     * @param start to be passed as the {@code start} parameter
     * @param end to be passed as the {@code end} parameter
     * @return {@link MockA11yImeCommand} object that can be passed to
     *         {@link MockA11yImeEventStreamUtils#expectA11yImeCommand(MockA11yImeEventStream,
     *         MockA11yImeCommand, long)} to wait until this event is handled by MockA11yIme.
     */
    @NonNull
    public MockA11yImeCommand callSetSelection(int start, int end) {
        final Bundle params = new Bundle();
        params.putInt("start", start);
        params.putInt("end", end);
        return callCommandInternal("setSelection", params);
    }

    /**
     * Lets MockA11yIme call {@link
     * android.accessibilityservice.InputMethod.AccessibilityInputConnection#getSurroundingText(int,
     * int, int)} with the given parameters.
     *
     * <p>Use {@link MockA11yImeEvent#getReturnParcelableValue()} for {@link MockA11yImeEvent}
     * returned from {@link MockA11yImeEventStreamUtils#expectA11yImeCommand(MockA11yImeEventStream,
     * MockA11yImeCommand, long)} to see the value returned from the API.</p>
     *
     * @param beforeLength The expected length of the text before the cursor.
     * @param afterLength The expected length of the text after the cursor.
     * @param flags Supplies additional options controlling how the text is returned. May be either
     *              {@code 0} or
     *              {@link android.view.inputmethod.InputConnection#GET_TEXT_WITH_STYLES}.
     * @return {@link MockA11yImeCommand} object that can be passed to
     *         {@link MockA11yImeEventStreamUtils#expectA11yImeCommand(MockA11yImeEventStream,
     *         MockA11yImeCommand, long)} to wait until this event is handled by MockA11yIme.
     */
    @NonNull
    public MockA11yImeCommand callGetSurroundingText(@IntRange(from = 0) int beforeLength,
            @IntRange(from = 0) int afterLength, int flags) {
        final Bundle params = new Bundle();
        params.putInt("beforeLength", beforeLength);
        params.putInt("afterLength", afterLength);
        params.putInt("flags", flags);
        return callCommandInternal("getSurroundingText", params);
    }

    /**
     * Lets MockA11yIme call {@link
     * android.accessibilityservice.InputMethod.AccessibilityInputConnection#deleteSurroundingText(
     * int, int)} with the given parameters.
     *
     * @param beforeLength to be passed as the {@code beforeLength} parameter
     * @param afterLength to be passed as the {@code afterLength} parameter
     * @return {@link MockA11yImeCommand} object that can be passed to
     *         {@link MockA11yImeEventStreamUtils#expectA11yImeCommand(MockA11yImeEventStream,
     *         MockA11yImeCommand, long)} to wait until this event is handled by MockA11yIme.
     */
    @NonNull
    public MockA11yImeCommand callDeleteSurroundingText(int beforeLength, int afterLength) {
        final Bundle params = new Bundle();
        params.putInt("beforeLength", beforeLength);
        params.putInt("afterLength", afterLength);
        return callCommandInternal("deleteSurroundingText", params);
    }

    /**
     * Lets MockA11yIme call {@link
     * android.accessibilityservice.InputMethod.AccessibilityInputConnection#sendKeyEvent(KeyEvent)}
     * with the given parameters.
     *
     * @param event to be passed as the {@code event} parameter
     * @return {@link MockA11yImeCommand} object that can be passed to
     *         {@link MockA11yImeEventStreamUtils#expectA11yImeCommand(MockA11yImeEventStream,
     *         MockA11yImeCommand, long)} to wait until this event is handled by MockA11yIme
     */
    @NonNull
    public MockA11yImeCommand callSendKeyEvent(@Nullable KeyEvent event) {
        final Bundle params = new Bundle();
        params.putParcelable("event", event);
        return callCommandInternal("sendKeyEvent", params);
    }

    /**
     * Lets MockA11yIme call {@link
     * android.accessibilityservice.InputMethod.AccessibilityInputConnection#performEditorAction(
     * int)} with the given parameters.
     *
     * @param editorAction to be passed as the {@code editorAction} parameter
     * @return {@link MockA11yImeCommand} object that can be passed to
     *         {@link MockA11yImeEventStreamUtils#expectA11yImeCommand(MockA11yImeEventStream,
     *         MockA11yImeCommand, long)} to wait until this event is handled by MockA11yIme
     */
    @NonNull
    public MockA11yImeCommand callPerformEditorAction(int editorAction) {
        final Bundle params = new Bundle();
        params.putInt("editorAction", editorAction);
        return callCommandInternal("performEditorAction", params);
    }

    /**
     * Lets MockA11yIme call {@code performContextMenuAction(id)}.
     *
     * @param id to be passed as the {@code id} parameter
     * @return {@link MockA11yImeCommand} object that can be passed to
     *         {@link MockA11yImeEventStreamUtils#expectA11yImeCommand(MockA11yImeEventStream,
     *         MockA11yImeCommand, long)} to wait until this event is handled by MockA11yIme.
     */
    @NonNull
    public MockA11yImeCommand callPerformContextMenuAction(int id) {
        final Bundle params = new Bundle();
        params.putInt("id", id);
        return callCommandInternal("performContextMenuAction", params);
    }

    /**
     * Lets MockA11yIme call {@link
     * android.accessibilityservice.InputMethod.AccessibilityInputConnection#getCursorCapsMode(int)}
     * with the given parameters.
     *
     * <p>This triggers {@code getCurrentInputConnection().getCursorCapsMode(reqModes)}.</p>
     *
     * <p>Use {@link MockA11yImeEvent#getReturnIntegerValue()} for {@link MockA11yImeEvent} returned
     * from {@link MockA11yImeEventStreamUtils#expectA11yImeCommand(MockA11yImeEventStream,
     * MockA11yImeCommand, long)} to see the value returned from the API.</p>
     *
     * @param reqModes to be passed as the {@code reqModes} parameter.
     * @return {@link MockA11yImeCommand} object that can be passed to
     *         {@link MockA11yImeEventStreamUtils#expectA11yImeCommand(MockA11yImeEventStream,
     *         MockA11yImeCommand, long)} to wait until this event is handled by MockA11yIme.
     */
    @NonNull
    public MockA11yImeCommand callGetCursorCapsMode(int reqModes) {
        final Bundle params = new Bundle();
        params.putInt("reqModes", reqModes);
        return callCommandInternal("getCursorCapsMode", params);
    }

    /**
     * Lets MockA11yIme call {@link
     * android.accessibilityservice.InputMethod.AccessibilityInputConnection#clearMetaKeyStates(int)
     * } with the given parameters.
     *
     * <p>This can be affected by {@link #memorizeCurrentInputConnection()}.</p>
     *
     * @param states to be passed as the {@code states} parameter
     * @return {@link MockA11yImeCommand} object that can be passed to
     *         {@link MockA11yImeEventStreamUtils#expectA11yImeCommand(MockA11yImeEventStream,
     *         MockA11yImeCommand, long)} to wait until this event is handled by MockA11yIme.
     */
    @NonNull
    public MockA11yImeCommand callClearMetaKeyStates(int states) {
        final Bundle params = new Bundle();
        params.putInt("states", states);
        return callCommandInternal("clearMetaKeyStates", params);
    }
}
