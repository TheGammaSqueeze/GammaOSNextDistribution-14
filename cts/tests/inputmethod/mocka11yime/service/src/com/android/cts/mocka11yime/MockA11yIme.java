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

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.InputMethod;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.TextAttribute;

import androidx.annotation.AnyThread;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Mock {@link AccessibilityService} for end-to-end tests of {@link InputMethod}.
 *
 * @implNote {@link InputMethod} is available only when a special flag
 *           {@code "flagInputMethodEditor"} is set to {@code "android:accessibilityFlags"} in
 *           AndroidManifest.xml.
 */
public final class MockA11yIme extends AccessibilityService {
    private static final String TAG = "MockA11yIme";

    private final HandlerThread mHandlerThread = new HandlerThread("CommandReceiver");

    @Nullable
    volatile String mEventActionName;

    @Nullable
    volatile String mClientPackageName;

    @Nullable
    volatile MockA11yImeSettings mSettings;
    volatile boolean mDestroying = false;

    private static final class CommandReceiver extends BroadcastReceiver {
        @NonNull
        private final String mActionName;
        @NonNull
        private final Consumer<MockA11yImeCommand> mOnReceiveCommand;

        CommandReceiver(@NonNull String actionName,
                @NonNull Consumer<MockA11yImeCommand> onReceiveCommand) {
            mActionName = actionName;
            mOnReceiveCommand = onReceiveCommand;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (TextUtils.equals(mActionName, intent.getAction())) {
                mOnReceiveCommand.accept(MockA11yImeCommand.fromBundle(intent.getExtras()));
            }
        }
    }

    @Nullable
    private CommandReceiver mCommandReceiver;

    private final ThreadLocal<Tracer> mThreadLocalTracer = new ThreadLocal<>();

    private Tracer getTracer() {
        Tracer tracer = mThreadLocalTracer.get();
        if (tracer == null) {
            tracer = new Tracer(this);
            mThreadLocalTracer.set(tracer);
        }
        return tracer;
    }

    @Override
    public void onCreate() {
        mSettings = MockA11yImeContentProvider.getSettings();
        if (mSettings == null) {
            throw new IllegalStateException("settings can never be null here. "
                    + "Make sure A11yMockImeSession.create() is used to launch MockA11yIme.");
        }

        final String actionName =  MockA11yImeContentProvider.getEventCallbackActionName();
        if (actionName == null) {
            throw new IllegalStateException("actionName can never be null here. "
                    + "Make sure A11yMockImeSession.create() is used to launch MockA11yIme.");
        }
        mEventActionName = actionName;

        mClientPackageName = MockA11yImeContentProvider.getClientPackageName();
        if (mClientPackageName == null) {
            throw new IllegalStateException("clientPackageName can never be null here. "
                    + "Make sure A11yMockImeSession.create() is used to launch MockA11yIme.");
        }

        getTracer().onCreate(() -> {
            super.onCreate();
            final Handler handler = Handler.createAsync(getMainLooper());
            mHandlerThread.start();
            mCommandReceiver = new CommandReceiver(actionName, command -> {
                if (command.shouldDispatchToMainThread()) {
                    handler.post(() -> onHandleCommand(command));
                } else {
                    onHandleCommand(command);
                }
            });
            final IntentFilter filter = new IntentFilter(actionName);
            registerReceiver(mCommandReceiver, filter, null /* broadcastPermission */,
                    new Handler(mHandlerThread.getLooper()),
                    Context.RECEIVER_VISIBLE_TO_INSTANT_APPS | Context.RECEIVER_EXPORTED);
        });
    }

    @Override
    protected void onServiceConnected() {
        getTracer().onServiceConnected(() -> {});
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        getTracer().onAccessibilityEvent(event, () -> {});
    }

    @Override
    public void onInterrupt() {
        getTracer().onInterrupt(() -> {});
    }

    private final class InputMethodImpl extends InputMethod {
        InputMethodImpl(AccessibilityService service) {
            super(service);
        }

        @Override
        public void onStartInput(EditorInfo editorInfo, boolean restarting) {
            getTracer().onStartInput(editorInfo, restarting,
                    () -> super.onStartInput(editorInfo, restarting));
        }

        @Override
        public void onFinishInput() {
            getTracer().onFinishInput(super::onFinishInput);
        }

        @Override
        public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart,
                int newSelEnd, int candidatesStart, int candidatesEnd) {
            getTracer().onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                    candidatesStart, candidatesEnd,
                    () -> super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                            candidatesStart, candidatesEnd));
        }
    }

    @Override
    public void onDestroy() {
        getTracer().onDestroy(() -> {
            mDestroying = true;
            super.onDestroy();
            unregisterReceiver(mCommandReceiver);
            mHandlerThread.quitSafely();
        });
    }

    @Override
    public InputMethod onCreateInputMethod() {
        return getTracer().onCreateInputMethod(() -> new InputMethodImpl(this));
    }

    @AnyThread
    private void onHandleCommand(@NonNull MockA11yImeCommand command) {
        getTracer().onHandleCommand(command, () -> {
            if (command.shouldDispatchToMainThread()) {
                if (Looper.myLooper() != Looper.getMainLooper()) {
                    throw new IllegalStateException("command " + command
                            + " should be handled on the main thread");
                }
                switch (command.getName()) {
                    case "memorizeCurrentInputConnection": {
                        if (!Looper.getMainLooper().isCurrentThread()) {
                            return new UnsupportedOperationException(
                                    "memorizeCurrentInputConnection can be requested only for the"
                                            + " main thread.");
                        }
                        mMemorizedInputConnection = getInputMethod().getCurrentInputConnection();
                        return MockA11yImeEvent.RETURN_VALUE_UNAVAILABLE;
                    }
                    case "unmemorizeCurrentInputConnection": {
                        if (!Looper.getMainLooper().isCurrentThread()) {
                            return new UnsupportedOperationException(
                                    "unmemorizeCurrentInputConnection can be requested only for the"
                                            + " main thread.");
                        }
                        mMemorizedInputConnection = null;
                        return MockA11yImeEvent.RETURN_VALUE_UNAVAILABLE;
                    }

                    case "getCurrentInputStarted": {
                        if (!Looper.getMainLooper().isCurrentThread()) {
                            return new UnsupportedOperationException(
                                    "getCurrentInputStarted() can be requested only for the main"
                                            + " thread.");
                        }
                        return getInputMethod().getCurrentInputStarted();
                    }
                    case "getCurrentInputEditorInfo": {
                        if (!Looper.getMainLooper().isCurrentThread()) {
                            return new UnsupportedOperationException(
                                    "getCurrentInputEditorInfo() can be requested only for the main"
                                            + " thread.");
                        }
                        return getInputMethod().getCurrentInputEditorInfo();
                    }
                    case "getCurrentInputConnection": {
                        if (!Looper.getMainLooper().isCurrentThread()) {
                            return new UnsupportedOperationException(
                                    "getCurrentInputConnection() can be requested only for the main"
                                            + " thread.");
                        }
                        return getInputMethod().getCurrentInputConnection();
                    }

                    case "commitText": {
                        final CharSequence text = command.getExtras().getCharSequence("text");
                        final int newCursorPosition =
                                command.getExtras().getInt("newCursorPosition");
                        final TextAttribute textAttribute = command.getExtras().getParcelable(
                                "textAttribute", TextAttribute.class);
                        getMemorizedOrCurrentInputConnection().commitText(
                                text, newCursorPosition, textAttribute);
                        return MockA11yImeEvent.RETURN_VALUE_UNAVAILABLE;
                    }
                    case "setSelection": {
                        final int start = command.getExtras().getInt("start");
                        final int end = command.getExtras().getInt("end");
                        getMemorizedOrCurrentInputConnection().setSelection(start, end);
                        return MockA11yImeEvent.RETURN_VALUE_UNAVAILABLE;
                    }
                    case "getSurroundingText": {
                        final int beforeLength = command.getExtras().getInt("beforeLength");
                        final int afterLength = command.getExtras().getInt("afterLength");
                        final int flags = command.getExtras().getInt("flags");
                        return getMemorizedOrCurrentInputConnection().getSurroundingText(
                                beforeLength, afterLength, flags);
                    }
                    case "deleteSurroundingText": {
                        final int beforeLength = command.getExtras().getInt("beforeLength");
                        final int afterLength = command.getExtras().getInt("afterLength");
                        getMemorizedOrCurrentInputConnection().deleteSurroundingText(
                                beforeLength, afterLength);
                        return MockA11yImeEvent.RETURN_VALUE_UNAVAILABLE;
                    }
                    case "sendKeyEvent": {
                        final KeyEvent event = command.getExtras().getParcelable(
                                "event", KeyEvent.class);
                        getMemorizedOrCurrentInputConnection().sendKeyEvent(event);
                        return MockA11yImeEvent.RETURN_VALUE_UNAVAILABLE;
                    }
                    case "performEditorAction": {
                        final int editorAction = command.getExtras().getInt("editorAction");
                        getMemorizedOrCurrentInputConnection().performEditorAction(
                                editorAction);
                        return MockA11yImeEvent.RETURN_VALUE_UNAVAILABLE;
                    }
                    case "performContextMenuAction": {
                        final int id = command.getExtras().getInt("id");
                        getMemorizedOrCurrentInputConnection().performContextMenuAction(id);
                        return MockA11yImeEvent.RETURN_VALUE_UNAVAILABLE;
                    }
                    case "getCursorCapsMode": {
                        final int reqModes = command.getExtras().getInt("reqModes");
                        return getMemorizedOrCurrentInputConnection().getCursorCapsMode(reqModes);
                    }
                    case "clearMetaKeyStates": {
                        final int states = command.getExtras().getInt("states");
                        getMemorizedOrCurrentInputConnection().clearMetaKeyStates(states);
                        return MockA11yImeEvent.RETURN_VALUE_UNAVAILABLE;
                    }

                    default:
                        return MockA11yImeEvent.RETURN_VALUE_UNAVAILABLE;
                }
            }
            return MockA11yImeEvent.RETURN_VALUE_UNAVAILABLE;
        });
    }

    @Nullable
    private InputMethod.AccessibilityInputConnection mMemorizedInputConnection = null;

    @Nullable
    @MainThread
    private InputMethod.AccessibilityInputConnection getMemorizedOrCurrentInputConnection() {
        return mMemorizedInputConnection != null
                ? mMemorizedInputConnection : getInputMethod().getCurrentInputConnection();
    }

    /**
     * Event tracing helper class for {@link MockA11yIme}.
     */
    private static final class Tracer {
        @NonNull
        private final MockA11yIme mMockA11yIme;

        private final int mThreadId = Process.myTid();

        @NonNull
        private final String mThreadName =
                Thread.currentThread().getName() != null ? Thread.currentThread().getName() : "";

        private final boolean mIsMainThread =
                Looper.getMainLooper().getThread() == Thread.currentThread();

        private int mNestLevel = 0;

        private String mImeEventActionName;

        private String mClientPackageName;

        Tracer(@NonNull MockA11yIme mockA11yIme) {
            mMockA11yIme = mockA11yIme;
        }

        private void sendEventInternal(@NonNull MockA11yImeEvent event) {
            if (mImeEventActionName == null) {
                mImeEventActionName = mMockA11yIme.mEventActionName;
            }
            if (mClientPackageName == null) {
                mClientPackageName = mMockA11yIme.mClientPackageName;
            }
            if (mImeEventActionName == null || mClientPackageName == null) {
                Log.e(TAG, "Tracer cannot be used before onCreate()");
                return;
            }
            final Intent intent = new Intent()
                    .setAction(mImeEventActionName)
                    .setPackage(mClientPackageName)
                    .putExtras(event.toBundle())
                    .addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY
                            | Intent.FLAG_RECEIVER_VISIBLE_TO_INSTANT_APPS);
            mMockA11yIme.sendBroadcast(intent);
        }

        private void recordEventInternal(@NonNull String eventName, @NonNull Runnable runnable) {
            recordEventInternal(eventName, runnable, new Bundle());
        }

        private void recordEventInternal(@NonNull String eventName, @NonNull Runnable runnable,
                @NonNull Bundle arguments) {
            recordEventInternal(eventName, () -> {
                runnable.run(); return MockA11yImeEvent.RETURN_VALUE_UNAVAILABLE;
            }, arguments);
        }

        private <T> T recordEventInternal(@NonNull String eventName,
                @NonNull Supplier<T> supplier) {
            return recordEventInternal(eventName, supplier, new Bundle());
        }

        private <T> T recordEventInternal(@NonNull String eventName,
                @NonNull Supplier<T> supplier, @NonNull Bundle arguments) {
            {
                final StringBuilder sb = new StringBuilder();
                sb.append(eventName).append(": ");
                MockA11yImeBundleUtils.dumpBundle(sb, arguments);
                Log.d(TAG, sb.toString());
            }
            final long enterTimestamp = SystemClock.elapsedRealtimeNanos();
            final long enterWallTime = System.currentTimeMillis();
            final int nestLevel = mNestLevel;
            // Send enter event
            sendEventInternal(new MockA11yImeEvent(eventName, nestLevel, mThreadName,
                    mThreadId, mIsMainThread, enterTimestamp, 0, enterWallTime,
                    0, true /* isEnter */, arguments,
                    MockA11yImeEvent.RETURN_VALUE_UNAVAILABLE));
            ++mNestLevel;
            T result;
            try {
                result = supplier.get();
            } finally {
                --mNestLevel;
            }
            final long exitTimestamp = SystemClock.elapsedRealtimeNanos();
            final long exitWallTime = System.currentTimeMillis();
            // Send exit event
            sendEventInternal(new MockA11yImeEvent(eventName, nestLevel, mThreadName,
                    mThreadId, mIsMainThread, enterTimestamp, exitTimestamp, enterWallTime,
                    exitWallTime, false /* isEnter */, arguments, result));
            return result;
        }

        void onHandleCommand(
                @NonNull MockA11yImeCommand command, @NonNull Supplier<Object> resultSupplier) {
            final Bundle arguments = new Bundle();
            arguments.putBundle("command", command.toBundle());
            recordEventInternal("onHandleCommand", resultSupplier, arguments);
        }

        void onCreate(@NonNull Runnable runnable) {
            recordEventInternal("onCreate", runnable);
        }

        void onServiceConnected(@NonNull Runnable runnable) {
            recordEventInternal("onServiceCreated", runnable);
        }

        void onAccessibilityEvent(@NonNull AccessibilityEvent accessibilityEvent,
                @NonNull Runnable runnable) {
            final Bundle arguments = new Bundle();
            arguments.putParcelable("accessibilityEvent", accessibilityEvent);
            recordEventInternal("onAccessibilityEvent", runnable, arguments);
        }

        void onInterrupt(@NonNull Runnable runnable) {
            recordEventInternal("onInterrupt", runnable);
        }

        void onDestroy(@NonNull Runnable runnable) {
            recordEventInternal("onDestroy", runnable);
        }

        void onStartInput(EditorInfo editorInfo, boolean restarting, @NonNull Runnable runnable) {
            final Bundle arguments = new Bundle();
            arguments.putParcelable("editorInfo", editorInfo);
            arguments.putBoolean("restarting", restarting);
            recordEventInternal("onStartInput", runnable, arguments);
        }

        void onFinishInput(@NonNull Runnable runnable) {
            recordEventInternal("onFinishInput", runnable);
        }

        void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
                int candidatesStart, int candidatesEnd, @NonNull Runnable runnable) {
            final Bundle arguments = new Bundle();
            arguments.putInt("oldSelStart", oldSelStart);
            arguments.putInt("oldSelEnd", oldSelEnd);
            arguments.putInt("newSelStart", newSelStart);
            arguments.putInt("newSelEnd", newSelEnd);
            arguments.putInt("candidatesStart", candidatesStart);
            arguments.putInt("candidatesEnd", candidatesEnd);
            recordEventInternal("onUpdateSelection", runnable, arguments);
        }

        InputMethod onCreateInputMethod(@NonNull Supplier<InputMethod> supplier) {
            final Bundle arguments = new Bundle();
            return recordEventInternal("onCreateInputMethod", supplier, arguments);
        }
    }
}
