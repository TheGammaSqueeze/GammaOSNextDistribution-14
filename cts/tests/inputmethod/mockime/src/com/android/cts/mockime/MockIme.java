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

import static android.server.wm.jetpack.extensions.util.ExtensionsUtil.getWindowExtensions;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
import static android.view.WindowInsets.Type.captionBar;
import static android.view.WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.inputmethodservice.ExtractEditText;
import android.inputmethodservice.InputMethodService;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.ResultReceiver;
import android.os.StrictMode;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;
import android.util.TypedValue;
import android.view.Display;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.inputmethod.CancellableHandwritingGesture;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.CursorAnchorInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.HandwritingGesture;
import android.view.inputmethod.InlineSuggestion;
import android.view.inputmethod.InlineSuggestionsRequest;
import android.view.inputmethod.InlineSuggestionsResponse;
import android.view.inputmethod.InputBinding;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputContentInfo;
import android.view.inputmethod.InputMethod;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.view.inputmethod.PreviewableHandwritingGesture;
import android.view.inputmethod.TextAttribute;
import android.view.inputmethod.TextBoundsInfoResult;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.inline.InlinePresentationSpec;

import androidx.annotation.AnyThread;
import androidx.annotation.CallSuper;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.autofill.inline.UiVersions;
import androidx.autofill.inline.UiVersions.StylesBuilder;
import androidx.autofill.inline.v1.InlineSuggestionUi;
import androidx.window.extensions.WindowExtensions;
import androidx.window.extensions.layout.WindowLayoutComponent;
import androidx.window.extensions.layout.WindowLayoutInfo;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Mock IME for end-to-end tests.
 */
public final class MockIme extends InputMethodService {

    private static final String TAG = "MockIme";

    private static final long DELAY_CANCELLATION_SIGNAL_MILLIS = 500;

    /** Default label for the custom extract text view. */
    public static final String CUSTOM_EXTRACT_EDIT_TEXT_LABEL =
            "MockIme Custom Extract Edit Text Label";

    private ArrayList<MotionEvent> mEvents;

    private View mExtractView;

    @Nullable
    private final WindowExtensions mWindowExtensions = getWindowExtensions();
    @Nullable
    private final WindowLayoutComponent mWindowLayoutComponent =
            mWindowExtensions != null ? mWindowExtensions.getWindowLayoutComponent() : null;

    /** The extensions core Consumer to receive {@link WindowLayoutInfo} updates. */
    private androidx.window.extensions.core.util.function.Consumer<WindowLayoutInfo>
            mWindowLayoutInfoConsumer;

    private final HandlerThread mHandlerThread = new HandlerThread("CommandReceiver");

    private Handler mHandlerThreadHandler;

    private final Handler mMainHandler = new Handler();

    private final Consumer<Bundle> mCommandHandler = (bundle) -> {
        mHandlerThreadHandler.post(() -> {
            onReceiveCommand(ImeCommand.fromBundle(bundle));
        });
    };

    private final Configuration mLastDispatchedConfiguration = new Configuration();

    @Nullable
    private InputConnection mMemorizedInputConnection = null;

    @Nullable
    @MainThread
    private InputConnection getMemorizedOrCurrentInputConnection() {
        return mMemorizedInputConnection != null
                ? mMemorizedInputConnection : getCurrentInputConnection();
    }

    @WorkerThread
    private void onReceiveCommand(@NonNull ImeCommand command) {
        getTracer().onReceiveCommand(command, () -> {
            if (command.shouldDispatchToMainThread()) {
                mMainHandler.post(() -> onHandleCommand(command));
            } else {
                onHandleCommand(command);
            }
        });
    }

    @AnyThread
    private void onHandleCommand(@NonNull ImeCommand command) {
        getTracer().onHandleCommand(command, () -> {
            if (command.shouldDispatchToMainThread()) {
                if (Looper.myLooper() != Looper.getMainLooper()) {
                    throw new IllegalStateException("command " + command
                            + " should be handled on the main thread");
                }
                // The context which created from InputMethodService#createXXXContext must behave
                // like an UI context, which can obtain a display, a window manager,
                // a view configuration and a gesture detector instance without strict mode
                // violation.
                final Configuration testConfig = new Configuration();
                testConfig.setToDefaults();
                final Context configContext = createConfigurationContext(testConfig);
                final Context attrContext = createAttributionContext(null /* attributionTag */);
                // UI component accesses on a display context must throw strict mode violations.
                final Context displayContext = createDisplayContext(getDisplay());
                switch (command.getName()) {
                    case "suspendCreateSession": {
                        if (!Looper.getMainLooper().isCurrentThread()) {
                            return new UnsupportedOperationException("suspendCreateSession can be"
                                    + " requested only for the main thread.");
                        }
                        mSuspendCreateSession = true;
                        return ImeEvent.RETURN_VALUE_UNAVAILABLE;
                    }
                    case "resumeCreateSession": {
                        if (!Looper.getMainLooper().isCurrentThread()) {
                            return new UnsupportedOperationException("resumeCreateSession can be"
                                    + " requested only for the main thread.");
                        }
                        if (mSuspendedCreateSession != null) {
                            mSuspendedCreateSession.run();
                            mSuspendedCreateSession = null;
                        }
                        mSuspendCreateSession = false;
                        return ImeEvent.RETURN_VALUE_UNAVAILABLE;
                    }
                    case "memorizeCurrentInputConnection": {
                        if (!Looper.getMainLooper().isCurrentThread()) {
                            return new UnsupportedOperationException(
                                    "memorizeCurrentInputConnection can be requested only for the"
                                            + " main thread.");
                        }
                        mMemorizedInputConnection = getCurrentInputConnection();
                        return ImeEvent.RETURN_VALUE_UNAVAILABLE;
                    }
                    case "unmemorizeCurrentInputConnection": {
                        if (!Looper.getMainLooper().isCurrentThread()) {
                            return new UnsupportedOperationException(
                                    "unmemorizeCurrentInputConnection can be requested only for the"
                                            + " main thread.");
                        }
                        mMemorizedInputConnection = null;
                        return ImeEvent.RETURN_VALUE_UNAVAILABLE;
                    }
                    case "getTextBeforeCursor": {
                        final int n = command.getExtras().getInt("n");
                        final int flag = command.getExtras().getInt("flag");
                        return getMemorizedOrCurrentInputConnection().getTextBeforeCursor(n, flag);
                    }
                    case "getTextAfterCursor": {
                        final int n = command.getExtras().getInt("n");
                        final int flag = command.getExtras().getInt("flag");
                        return getMemorizedOrCurrentInputConnection().getTextAfterCursor(n, flag);
                    }
                    case "getSelectedText": {
                        final int flag = command.getExtras().getInt("flag");
                        return getMemorizedOrCurrentInputConnection().getSelectedText(flag);
                    }
                    case "getSurroundingText": {
                        final int beforeLength = command.getExtras().getInt("beforeLength");
                        final int afterLength = command.getExtras().getInt("afterLength");
                        final int flags = command.getExtras().getInt("flags");
                        return getMemorizedOrCurrentInputConnection().getSurroundingText(
                                beforeLength, afterLength, flags);
                    }
                    case "getCursorCapsMode": {
                        final int reqModes = command.getExtras().getInt("reqModes");
                        return getMemorizedOrCurrentInputConnection().getCursorCapsMode(reqModes);
                    }
                    case "getExtractedText": {
                        final ExtractedTextRequest request =
                                command.getExtras().getParcelable("request");
                        final int flags = command.getExtras().getInt("flags");
                        return getMemorizedOrCurrentInputConnection().getExtractedText(request,
                                flags);
                    }
                    case "deleteSurroundingText": {
                        final int beforeLength = command.getExtras().getInt("beforeLength");
                        final int afterLength = command.getExtras().getInt("afterLength");
                        return getMemorizedOrCurrentInputConnection().deleteSurroundingText(
                                beforeLength, afterLength);
                    }
                    case "deleteSurroundingTextInCodePoints": {
                        final int beforeLength = command.getExtras().getInt("beforeLength");
                        final int afterLength = command.getExtras().getInt("afterLength");
                        return getMemorizedOrCurrentInputConnection()
                                .deleteSurroundingTextInCodePoints(beforeLength, afterLength);
                    }
                    case "setComposingText(CharSequence,int)": {
                        final CharSequence text = command.getExtras().getCharSequence("text");
                        final int newCursorPosition =
                                command.getExtras().getInt("newCursorPosition");
                        return getMemorizedOrCurrentInputConnection().setComposingText(
                                text, newCursorPosition);
                    }
                    case "setComposingText(CharSequence,int,TextAttribute)": {
                        final CharSequence text = command.getExtras().getCharSequence("text");
                        final int newCursorPosition =
                                command.getExtras().getInt("newCursorPosition");
                        final TextAttribute textAttribute =
                                command.getExtras().getParcelable("textAttribute");
                        return getMemorizedOrCurrentInputConnection()
                                .setComposingText(text, newCursorPosition, textAttribute);
                    }
                    case "setComposingRegion(int,int)": {
                        final int start = command.getExtras().getInt("start");
                        final int end = command.getExtras().getInt("end");
                        return getMemorizedOrCurrentInputConnection().setComposingRegion(start,
                                end);
                    }
                    case "setComposingRegion(int,int,TextAttribute)": {
                        final int start = command.getExtras().getInt("start");
                        final int end = command.getExtras().getInt("end");
                        final TextAttribute textAttribute =
                                command.getExtras().getParcelable("textAttribute");
                        return getMemorizedOrCurrentInputConnection()
                                .setComposingRegion(start, end, textAttribute);
                    }
                    case "finishComposingText":
                        return getMemorizedOrCurrentInputConnection().finishComposingText();
                    case "commitText(CharSequence,int)": {
                        final CharSequence text = command.getExtras().getCharSequence("text");
                        final int newCursorPosition =
                                command.getExtras().getInt("newCursorPosition");
                        return getMemorizedOrCurrentInputConnection().commitText(text,
                                newCursorPosition);
                    }
                    case "commitText(CharSequence,int,TextAttribute)": {
                        final CharSequence text = command.getExtras().getCharSequence("text");
                        final int newCursorPosition =
                                command.getExtras().getInt("newCursorPosition");
                        final TextAttribute textAttribute =
                                command.getExtras().getParcelable("textAttribute");
                        return getMemorizedOrCurrentInputConnection()
                                .commitText(text, newCursorPosition, textAttribute);
                    }
                    case "commitCompletion": {
                        final CompletionInfo text = command.getExtras().getParcelable("text");
                        return getMemorizedOrCurrentInputConnection().commitCompletion(text);
                    }
                    case "commitCorrection": {
                        final CorrectionInfo correctionInfo =
                                command.getExtras().getParcelable("correctionInfo");
                        return getMemorizedOrCurrentInputConnection().commitCorrection(
                                correctionInfo);
                    }
                    case "setSelection": {
                        final int start = command.getExtras().getInt("start");
                        final int end = command.getExtras().getInt("end");
                        return getMemorizedOrCurrentInputConnection().setSelection(start, end);
                    }
                    case "performEditorAction": {
                        final int editorAction = command.getExtras().getInt("editorAction");
                        return getMemorizedOrCurrentInputConnection().performEditorAction(
                                editorAction);
                    }
                    case "performContextMenuAction": {
                        final int id = command.getExtras().getInt("id");
                        return getMemorizedOrCurrentInputConnection().performContextMenuAction(id);
                    }
                    case "beginBatchEdit":
                        return getMemorizedOrCurrentInputConnection().beginBatchEdit();
                    case "endBatchEdit":
                        return getMemorizedOrCurrentInputConnection().endBatchEdit();
                    case "sendKeyEvent": {
                        final KeyEvent event = command.getExtras().getParcelable("event");
                        return getMemorizedOrCurrentInputConnection().sendKeyEvent(event);
                    }
                    case "clearMetaKeyStates": {
                        final int states = command.getExtras().getInt("states");
                        return getMemorizedOrCurrentInputConnection().clearMetaKeyStates(states);
                    }
                    case "reportFullscreenMode": {
                        final boolean enabled = command.getExtras().getBoolean("enabled");
                        return getMemorizedOrCurrentInputConnection().reportFullscreenMode(enabled);
                    }
                    case "performSpellCheck": {
                        return getMemorizedOrCurrentInputConnection().performSpellCheck();
                    }
                    case "takeSnapshot": {
                        return getMemorizedOrCurrentInputConnection().takeSnapshot();
                    }
                    case "performPrivateCommand": {
                        final String action = command.getExtras().getString("action");
                        final Bundle data = command.getExtras().getBundle("data");
                        return getMemorizedOrCurrentInputConnection().performPrivateCommand(action,
                                data);
                    }
                    case "previewHandwritingGesture": {
                        final PreviewableHandwritingGesture gesture =
                                (PreviewableHandwritingGesture) HandwritingGesture.fromByteArray(
                                        command.getExtras().getByteArray("gesture"));

                        boolean useDelayedCancellation =
                                command.getExtras().getBoolean("useDelayedCancellation");
                        final CancellationSignal cs =
                                useDelayedCancellation ? new CancellationSignal() : null;
                        if (useDelayedCancellation) {
                            new Handler().postDelayed(() -> cs.cancel(),
                                    DELAY_CANCELLATION_SIGNAL_MILLIS);
                        }

                        getMemorizedOrCurrentInputConnection().previewHandwritingGesture(
                                gesture, cs);
                        return ImeEvent.RETURN_VALUE_UNAVAILABLE;
                    }
                    case "performHandwritingGesture": {
                        final HandwritingGesture gesture =
                                HandwritingGesture.fromByteArray(
                                        command.getExtras().getByteArray("gesture"));

                        boolean useDelayedCancellation =
                                command.getExtras().getBoolean("useDelayedCancellation");
                        if (useDelayedCancellation
                                && gesture instanceof CancellableHandwritingGesture) {
                            final CancellationSignal cs = new CancellationSignal();
                            ((CancellableHandwritingGesture) gesture).setCancellationSignal(cs);
                            new Handler().postDelayed(() -> cs.cancel(),
                                    DELAY_CANCELLATION_SIGNAL_MILLIS);
                        }

                        IntConsumer consumer = value ->
                                getTracer().onPerformHandwritingGestureResult(
                                        value, command.getId(), () -> {});
                        getMemorizedOrCurrentInputConnection()
                                .performHandwritingGesture(gesture, mMainHandler::post, consumer);
                        return ImeEvent.RETURN_VALUE_UNAVAILABLE;
                    }
                    case "requestTextBoundsInfo": {
                        var rectF = command.getExtras().getParcelable("rectF", RectF.class);
                        Consumer<TextBoundsInfoResult> consumer = value ->
                                getTracer().onRequestTextBoundsInfoResult(
                                        value, command.getId());
                        getMemorizedOrCurrentInputConnection().requestTextBoundsInfo(
                                rectF, mMainHandler::post, consumer);
                        return ImeEvent.RETURN_VALUE_UNAVAILABLE;
                    }
                    case "requestCursorUpdates": {
                        final int cursorUpdateMode = command.getExtras().getInt("cursorUpdateMode");
                        final int cursorUpdateFilter =
                                command.getExtras().getInt("cursorUpdateFilter", 0);
                        return getMemorizedOrCurrentInputConnection().requestCursorUpdates(
                                cursorUpdateMode, cursorUpdateFilter);
                    }
                    case "getHandler":
                        return getMemorizedOrCurrentInputConnection().getHandler();
                    case "closeConnection":
                        getMemorizedOrCurrentInputConnection().closeConnection();
                        return ImeEvent.RETURN_VALUE_UNAVAILABLE;
                    case "commitContent": {
                        final InputContentInfo inputContentInfo =
                                command.getExtras().getParcelable("inputContentInfo");
                        final int flags = command.getExtras().getInt("flags");
                        final Bundle opts = command.getExtras().getBundle("opts");
                        return getMemorizedOrCurrentInputConnection().commitContent(
                                inputContentInfo, flags, opts);
                    }
                    case "setImeConsumesInput": {
                        final boolean imeConsumesInput =
                                command.getExtras().getBoolean("imeConsumesInput");
                        return getMemorizedOrCurrentInputConnection().setImeConsumesInput(
                                imeConsumesInput);
                    }
                    case "replaceText": {
                        final int start = command.getExtras().getInt("start");
                        final int end = command.getExtras().getInt("end");
                        final CharSequence text = command.getExtras().getCharSequence("text");
                        final int newCursorPosition =
                                command.getExtras().getInt("newCursorPosition");
                        final TextAttribute textAttribute =
                                command.getExtras().getParcelable("textAttribute");
                        return getMemorizedOrCurrentInputConnection()
                                .replaceText(start, end, text, newCursorPosition, textAttribute);
                    }
                    case "setExplicitlyEnabledInputMethodSubtypes": {
                        final String imeId = command.getExtras().getString("imeId");
                        final int[] subtypeHashCodes =
                                command.getExtras().getIntArray("subtypeHashCodes");
                        getSystemService(InputMethodManager.class)
                                .setExplicitlyEnabledInputMethodSubtypes(imeId, subtypeHashCodes);
                        return ImeEvent.RETURN_VALUE_UNAVAILABLE;
                    }
                    case "setAdditionalInputMethodSubtypes": {
                        final String imeId = command.getExtras().getString("imeId");
                        final InputMethodSubtype[] subtypes =
                                command.getExtras().getParcelableArray("subtypes",
                                        InputMethodSubtype.class);
                        getSystemService(InputMethodManager.class)
                                .setAdditionalInputMethodSubtypes(imeId, subtypes);
                        return ImeEvent.RETURN_VALUE_UNAVAILABLE;
                    }
                    case "switchInputMethod": {
                        final String id = command.getExtras().getString("id");
                        try {
                            switchInputMethod(id);
                        } catch (Exception e) {
                            return e;
                        }
                        return ImeEvent.RETURN_VALUE_UNAVAILABLE;
                    }
                    case "switchInputMethod(String,InputMethodSubtype)": {
                        final String id = command.getExtras().getString("id");
                        final InputMethodSubtype subtype = command.getExtras().getParcelable(
                                "subtype", InputMethodSubtype.class);
                        try {
                            switchInputMethod(id, subtype);
                        } catch (Exception e) {
                            return e;
                        }
                        return ImeEvent.RETURN_VALUE_UNAVAILABLE;
                    }
                    case "setBackDisposition": {
                        final int backDisposition =
                                command.getExtras().getInt("backDisposition");
                        setBackDisposition(backDisposition);
                        return ImeEvent.RETURN_VALUE_UNAVAILABLE;
                    }
                    case "requestHideSelf": {
                        final int flags = command.getExtras().getInt("flags");
                        requestHideSelf(flags);
                        return ImeEvent.RETURN_VALUE_UNAVAILABLE;
                    }
                    case "requestShowSelf": {
                        final int flags = command.getExtras().getInt("flags");
                        requestShowSelf(flags);
                        return ImeEvent.RETURN_VALUE_UNAVAILABLE;
                    }
                    case "sendDownUpKeyEvents": {
                        final int keyEventCode = command.getExtras().getInt("keyEventCode");
                        sendDownUpKeyEvents(keyEventCode);
                        return ImeEvent.RETURN_VALUE_UNAVAILABLE;
                    }
                    case "getApplicationInfo": {
                        final String packageName = command.getExtras().getString("packageName");
                        final int flags = command.getExtras().getInt("flags");
                        try {
                            return getPackageManager().getApplicationInfo(packageName, flags);
                        } catch (PackageManager.NameNotFoundException e) {
                            return e;
                        }
                    }
                    case "getDisplayId":
                        return getDisplay().getDisplayId();
                    case "verifyLayoutInflaterContext":
                        return getLayoutInflater().getContext() == this;
                    case "setHeight":
                        final int height = command.getExtras().getInt("height");
                        mView.setHeight(height);
                        return ImeEvent.RETURN_VALUE_UNAVAILABLE;
                    case "setExtractView":
                        final String label = command.getExtras().getString("label");
                        setExtractView(createCustomExtractTextView(label));
                        return ImeEvent.RETURN_VALUE_UNAVAILABLE;
                    case "verifyExtractViewNotNull":
                        if (mExtractView == null) {
                            return false;
                        } else {
                            return mExtractView.findViewById(android.R.id.inputExtractAction)
                                    != null
                                    && mExtractView.findViewById(
                                            android.R.id.inputExtractAccessories) != null
                                    && mExtractView.findViewById(
                                            android.R.id.inputExtractEditText) != null;
                        }
                    case "verifyGetDisplay":
                        try {
                            return verifyGetDisplay();
                        } catch (UnsupportedOperationException e) {
                            return e;
                        }
                    case "verifyIsUiContext":
                        return verifyIsUiContext();
                    case "verifyGetWindowManager": {
                        final WindowManager imsWm = getSystemService(WindowManager.class);
                        final WindowManager configContextWm =
                                configContext.getSystemService(WindowManager.class);
                        final WindowManager attrContextWm =
                                attrContext.getSystemService(WindowManager.class);
                        return ImeEvent.RETURN_VALUE_UNAVAILABLE;
                    }
                    case "verifyGetViewConfiguration": {
                        final ViewConfiguration imsViewConfig = ViewConfiguration.get(this);
                        final ViewConfiguration configContextViewConfig =
                                ViewConfiguration.get(configContext);
                        final ViewConfiguration attrContextViewConfig =
                                ViewConfiguration.get(attrContext);
                        return ImeEvent.RETURN_VALUE_UNAVAILABLE;
                    }
                    case "verifyGetGestureDetector": {
                        GestureDetector.SimpleOnGestureListener listener =
                                new GestureDetector.SimpleOnGestureListener();
                        final GestureDetector imsGestureDetector =
                                new GestureDetector(this, listener);
                        final GestureDetector configContextGestureDetector =
                                new GestureDetector(configContext, listener);
                        final GestureDetector attrGestureDetector =
                                new GestureDetector(attrContext, listener);
                        return ImeEvent.RETURN_VALUE_UNAVAILABLE;
                    }
                    case "verifyGetWindowManagerOnDisplayContext": {
                        // Obtaining a WindowManager on a display context must throw a strict mode
                        // violation.
                        final WindowManager wm = displayContext
                                .getSystemService(WindowManager.class);

                        return ImeEvent.RETURN_VALUE_UNAVAILABLE;
                    }
                    case "verifyGetViewConfigurationOnDisplayContext": {
                        // Obtaining a ViewConfiguration on a display context must throw a strict
                        // mode violation.
                        final ViewConfiguration viewConfiguration =
                                ViewConfiguration.get(displayContext);

                        return ImeEvent.RETURN_VALUE_UNAVAILABLE;
                    }
                    case "verifyGetGestureDetectorOnDisplayContext": {
                        // Obtaining a GestureDetector on a display context must throw a strict mode
                        // violation.
                        GestureDetector.SimpleOnGestureListener listener =
                                new GestureDetector.SimpleOnGestureListener();
                        final GestureDetector gestureDetector =
                                new GestureDetector(displayContext, listener);

                        return ImeEvent.RETURN_VALUE_UNAVAILABLE;
                    }
                    case "getStylusHandwritingWindowVisibility": {
                        if (getStylusHandwritingWindow() == null) {
                            return false;
                        }
                        View decorView = getStylusHandwritingWindow().getDecorView();
                        return decorView != null && decorView.isAttachedToWindow()
                                && decorView.getVisibility() == View.VISIBLE;
                    }
                    case "hasStylusHandwritingWindow": {
                        return getStylusHandwritingWindow() != null;
                    }
                    case "setStylusHandwritingInkView": {
                        View inkView = new View(attrContext);
                        getStylusHandwritingWindow().setContentView(inkView);
                        mEvents = new ArrayList<>();
                        inkView.setOnTouchListener((view, event) ->
                                mEvents.add(MotionEvent.obtain(event)));
                        return true;
                    }
                    case "setStylusHandwritingTimeout": {
                        setStylusHandwritingSessionTimeout(
                                Duration.ofMillis(command.getExtras().getLong("timeoutMs")));
                        return true;
                    }
                    case "getStylusHandwritingTimeout": {
                        return getStylusHandwritingSessionTimeout().toMillis();
                    }
                    case "getStylusHandwritingEvents": {
                        return mEvents;
                    }
                    case "finishStylusHandwriting": {
                        finishStylusHandwriting();
                        return ImeEvent.RETURN_VALUE_UNAVAILABLE;
                    }
                    case "finishConnectionlessStylusHandwriting": {
                        finishConnectionlessStylusHandwriting(
                                command.getExtras().getCharSequence("text"));
                        return ImeEvent.RETURN_VALUE_UNAVAILABLE;
                    }
                    case "getCurrentWindowMetricsBounds": {
                        return getSystemService(WindowManager.class)
                                .getCurrentWindowMetrics().getBounds();
                    }
                    case "setImeCaptionBarVisible": {
                        final boolean visible = command.getExtras().getBoolean("visible");
                        if (visible) {
                            mView.getRootView().getWindowInsetsController().show(captionBar());
                        } else {
                            mView.getRootView().getWindowInsetsController().hide(captionBar());
                        }
                        return ImeEvent.RETURN_VALUE_UNAVAILABLE;
                    }
                }
            }
            return ImeEvent.RETURN_VALUE_UNAVAILABLE;
        });
    }

    private boolean verifyGetDisplay() throws UnsupportedOperationException {
        final Display display;
        final Display configContextDisplay;
        final Configuration config = new Configuration();
        config.setToDefaults();
        final Context configContext = createConfigurationContext(config);
        display = getDisplay();
        configContextDisplay = configContext.getDisplay();
        return display != null && configContextDisplay != null;
    }

    private boolean verifyIsUiContext() {
        final Configuration config = new Configuration();
        config.setToDefaults();
        final Context configContext = createConfigurationContext(config);
        // The value must be true because ConfigurationContext is derived from InputMethodService,
        // which is a UI Context.
        final boolean imeDerivedConfigContext = configContext.isUiContext();
        // The value must be false because DisplayContext won't receive any config update from
        // server.
        final boolean imeDerivedDisplayContext = createDisplayContext(getDisplay()).isUiContext();
        return isUiContext() && imeDerivedConfigContext && !imeDerivedDisplayContext;
    }

    @Nullable
    private ImeSettings mSettings;

    private final AtomicReference<String> mClientPackageName = new AtomicReference<>();

    @Nullable
    String getClientPackageName() {
        return mClientPackageName.get();
    }

    private boolean mDestroying;

    /**
     * {@code true} if {@link MockInputMethodImpl#createSession(InputMethod.SessionCallback)}
     * needs to be suspended.
     *
     * <p>Must be touched from the main thread.</p>
     */
    private boolean mSuspendCreateSession = false;

    /**
     * The suspended {@link MockInputMethodImpl#createSession(InputMethod.SessionCallback)} callback
     * operation.
     *
     * <p>Must be touched from the main thread.</p>
     */
    @Nullable
    Runnable mSuspendedCreateSession;

    private class MockInputMethodImpl extends InputMethodImpl {
        @Override
        public void showSoftInput(int flags, ResultReceiver resultReceiver) {
            getTracer().showSoftInput(flags, resultReceiver,
                    () -> super.showSoftInput(flags, resultReceiver));
        }

        @Override
        public void hideSoftInput(int flags, ResultReceiver resultReceiver) {
            getTracer().hideSoftInput(flags, resultReceiver,
                    () -> super.hideSoftInput(flags, resultReceiver));
        }

        @Override
        public void createSession(SessionCallback callback) {
            getTracer().createSession(() -> {
                if (mSuspendCreateSession) {
                    if (mSuspendedCreateSession != null) {
                        throw new IllegalStateException("suspendCreateSession() must be "
                                + "called before receiving another createSession()");
                    }
                    mSuspendedCreateSession = () -> {
                        if (!mDestroying) {
                            super.createSession(callback);
                        }
                    };
                } else {
                    super.createSession(callback);
                }
            });
        }

        @Override
        public void attachToken(IBinder token) {
            getTracer().attachToken(token, () -> super.attachToken(token));
        }

        @Override
        public void bindInput(InputBinding binding) {
            getTracer().bindInput(binding, () -> super.bindInput(binding));
        }

        @Override
        public void unbindInput() {
            getTracer().unbindInput(() -> super.unbindInput());
        }
    }

    @Override
    public void onCreate() {
        // Initialize minimum settings to send events in Tracer#onCreate().
        mSettings = SettingsProvider.getSettings();
        if (mSettings == null) {
            throw new IllegalStateException("Settings file is not found. "
                    + "Make sure MockImeSession.create() is used to launch Mock IME.");
        }
        mClientPackageName.set(mSettings.getClientPackageName());

        // TODO(b/159593676): consider to detect more violations
        if (mSettings.isStrictModeEnabled()) {
            StrictMode.setVmPolicy(
                    new StrictMode.VmPolicy.Builder()
                            .detectIncorrectContextUse()
                            .penaltyLog()
                            .penaltyListener(Runnable::run,
                                    v -> getTracer().onStrictModeViolated(() -> { }))
                            .build());
        }

        if (mSettings.isOnBackCallbackEnabled()) {
            getApplicationInfo().setEnableOnBackInvokedCallback(true);
        }

        getTracer().onCreate(() -> {

            // TODO(b/309578419): Remove this when the MockIme can handle insets properly.
            setTheme(R.style.MockImeTheme);

            super.onCreate();
            mHandlerThread.start();
            mHandlerThreadHandler = new Handler(mHandlerThread.getLooper());
            mSettings.getChannel().registerListener(mCommandHandler);
            // If Extension components are not loaded successfully, notify Test app.
            if (mSettings.isWindowLayoutInfoCallbackEnabled()) {
                getTracer().onVerify("windowLayoutComponentLoaded",
                        () -> mWindowLayoutComponent != null);
            }
            if (mSettings.isVerifyContextApisInOnCreate()) {
                getTracer().onVerify("isUiContext", this::verifyIsUiContext);
                getTracer().onVerify("getDisplay", this::verifyGetDisplay);
            }
            final int windowFlags = mSettings.getWindowFlags(0);
            final int windowFlagsMask = mSettings.getWindowFlagsMask(0);
            if (windowFlags != 0 || windowFlagsMask != 0) {
                final int prevFlags = getWindow().getWindow().getAttributes().flags;
                getWindow().getWindow().setFlags(windowFlags, windowFlagsMask);
                // For some reasons, seems that we need to post another requestLayout() when
                // FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS bit is changed.
                // TODO: Investigate the reason.
                if ((windowFlagsMask & FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS) != 0) {
                    final boolean hadFlag = (prevFlags & FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS) != 0;
                    final boolean hasFlag = (windowFlags & FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS) != 0;
                    if (hadFlag != hasFlag) {
                        final View decorView = getWindow().getWindow().getDecorView();
                        decorView.post(() -> decorView.requestLayout());
                    }
                }
            }

            // Ensuring bar contrast interferes with the tests.
            getWindow().getWindow().setStatusBarContrastEnforced(false);
            getWindow().getWindow().setNavigationBarContrastEnforced(false);

            if (mSettings.hasNavigationBarColor()) {
                getWindow().getWindow().setNavigationBarColor(mSettings.getNavigationBarColor());
            }

            // Initialize to current Configuration to prevent unexpected configDiff value dispatched
            // in IME event.
            mLastDispatchedConfiguration.setTo(getResources().getConfiguration());
        });

        if (mSettings.isWindowLayoutInfoCallbackEnabled() && mWindowLayoutComponent != null) {
            mWindowLayoutInfoConsumer = (windowLayoutInfo) -> getTracer().getWindowLayoutInfo(
                    windowLayoutInfo, () -> {});
            mWindowLayoutComponent.addWindowLayoutInfoListener(this, mWindowLayoutInfoConsumer);
        }
    }

    @Override
    protected void onCurrentInputMethodSubtypeChanged(InputMethodSubtype newSubtype) {
        getTracer().onCurrentInputMethodSubtypeChanged(newSubtype,
                () -> super.onCurrentInputMethodSubtypeChanged(newSubtype));
    }

    @Override
    public void onConfigureWindow(Window win, boolean isFullscreen, boolean isCandidatesOnly) {
        getTracer().onConfigureWindow(win, isFullscreen, isCandidatesOnly,
                () -> super.onConfigureWindow(win, isFullscreen, isCandidatesOnly));
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return getTracer().onEvaluateFullscreenMode(() -> {
            final int policy = mSettings.fullscreenModePolicy();
            switch (policy) {
                case ImeSettings.FullscreenModePolicy.NO_FULLSCREEN:
                    return false;
                case ImeSettings.FullscreenModePolicy.FORCE_FULLSCREEN:
                    return true;
                case ImeSettings.FullscreenModePolicy.OS_DEFAULT:
                    return super.onEvaluateFullscreenMode();
                default:
                    Log.e(TAG, "unknown FullscreenModePolicy=" + policy);
                    return false;
            }
        });
    }

    @Override
    public View onCreateExtractTextView() {
        if (mSettings != null && mSettings.isCustomExtractTextViewEnabled()) {
            mExtractView = createCustomExtractTextView(CUSTOM_EXTRACT_EDIT_TEXT_LABEL);
        } else {
            mExtractView = super.onCreateExtractTextView();
        }
        return mExtractView;
    }

    private View createCustomExtractTextView(String label) {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);

        TextView labelView = new TextView(this);
        labelView.setText(label);
        container.addView(labelView, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        // Using a subclass of ExtractEditText should be allowed.
        ExtractEditText extractEditText = new ExtractEditText(this) {};
        Log.d(TAG, "Using custom ExtractEditText: " + extractEditText);
        extractEditText.setId(android.R.id.inputExtractEditText);
        container.addView(extractEditText, new LinearLayout.LayoutParams(
                MATCH_PARENT, 0 /* height */, 1f /* weight */
        ));

        FrameLayout accessories = new FrameLayout(this);
        accessories.setId(android.R.id.inputExtractAccessories);
        container.addView(accessories, new LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT));

        Button actionButton = new Button(this);
        actionButton.setId(android.R.id.inputExtractAction);
        actionButton.setText("inputExtractAction");
        accessories.addView(actionButton, new FrameLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT));

        return container;
    }

    private static final class KeyboardLayoutView extends LinearLayout {
        @NonNull
        private final MockIme mMockIme;
        @NonNull
        private final ImeSettings mSettings;
        @NonNull
        private final View.OnLayoutChangeListener mLayoutListener;

        private final LinearLayout mLayout;

        @Nullable
        private final LinearLayout mSuggestionView;

        private boolean mDrawsBehindNavBar = false;

        KeyboardLayoutView(MockIme mockIme, @NonNull ImeSettings imeSettings,
                @Nullable Consumer<ImeLayoutInfo> onInputViewLayoutChangedCallback) {
            super(mockIme);

            mMockIme = mockIme;
            mSettings = imeSettings;

            setOrientation(VERTICAL);

            final int defaultBackgroundColor =
                    getResources().getColor(android.R.color.holo_orange_dark, null);

            final int mainSpacerHeight = mSettings.getInputViewHeight(LayoutParams.WRAP_CONTENT);
            mLayout = new LinearLayout(getContext());
            mLayout.setOrientation(LinearLayout.VERTICAL);

            if (mSettings.getInlineSuggestionsEnabled()) {
                final HorizontalScrollView scrollView = new HorizontalScrollView(getContext());
                final LayoutParams scrollViewParams = new LayoutParams(MATCH_PARENT, 100);
                scrollView.setLayoutParams(scrollViewParams);

                final LinearLayout suggestionView = new LinearLayout(getContext());
                suggestionView.setBackgroundColor(0xFFEEEEEE);
                final String suggestionViewContentDesc =
                        mSettings.getInlineSuggestionViewContentDesc(null /* default */);
                if (suggestionViewContentDesc != null) {
                    suggestionView.setContentDescription(suggestionViewContentDesc);
                }
                scrollView.addView(suggestionView, new LayoutParams(MATCH_PARENT, MATCH_PARENT));
                mSuggestionView = suggestionView;

                mLayout.addView(scrollView);
            } else {
                mSuggestionView = null;
            }

            {
                final FrameLayout secondaryLayout = new FrameLayout(getContext());
                secondaryLayout.setForegroundGravity(Gravity.CENTER);

                final TextView textView = new TextView(getContext());
                textView.setLayoutParams(new LayoutParams(MATCH_PARENT, WRAP_CONTENT));
                textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                textView.setGravity(Gravity.CENTER);
                textView.setText(
                        new ComponentName(mMockIme.getApplicationContext().getPackageName(),
                                MockIme.class.getName()).flattenToShortString());
                textView.setBackgroundColor(
                        mSettings.getBackgroundColor(defaultBackgroundColor));
                secondaryLayout.addView(textView);

                if (mSettings.isWatermarkEnabled(true /* defaultValue */)) {
                    final ImageView imageView = new ImageView(getContext());
                    final Bitmap bitmap = Watermark.create();
                    imageView.setImageBitmap(bitmap);
                    secondaryLayout.addView(imageView,
                            new FrameLayout.LayoutParams(bitmap.getWidth(), bitmap.getHeight(),
                                    mSettings.getWatermarkGravity(Gravity.CENTER)));
                }

                mLayout.addView(secondaryLayout);
            }

            addView(mLayout, MATCH_PARENT, mainSpacerHeight);

            final int systemUiVisibility = mSettings.getInputViewSystemUiVisibility(0);
            if (systemUiVisibility != 0) {
                setSystemUiVisibility(systemUiVisibility);
            }

            if (mSettings.getDrawsBehindNavBar()) {
                mDrawsBehindNavBar = true;
                mMockIme.getWindow().getWindow().setDecorFitsSystemWindows(false);
                setSystemUiVisibility(getSystemUiVisibility()
                        | SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
            }

            mLayoutListener = (View v, int left, int top, int right, int bottom, int oldLeft,
                    int oldTop, int oldRight, int oldBottom) ->
                    onInputViewLayoutChangedCallback.accept(
                            ImeLayoutInfo.fromLayoutListenerCallback(
                                    v, left, top, right, bottom, oldLeft, oldTop, oldRight,
                                    oldBottom));
            this.addOnLayoutChangeListener(mLayoutListener);
        }

        private void setHeight(int height) {
            mLayout.getLayoutParams().height = height;
            mLayout.requestLayout();
        }

        private void updateBottomPaddingIfNecessary(int newPaddingBottom) {
            if (getPaddingBottom() != newPaddingBottom) {
                setPadding(getPaddingLeft(), getPaddingTop(), getPaddingRight(), newPaddingBottom);
            }
        }

        @Override
        public WindowInsets onApplyWindowInsets(WindowInsets insets) {
            if (insets.isConsumed()
                    || mDrawsBehindNavBar
                    || (getSystemUiVisibility() & SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION) == 0) {
                // In this case we are not interested in consuming NavBar region.
                // Make sure that the bottom padding is empty.
                updateBottomPaddingIfNecessary(0);
                return insets;
            }

            // In some cases the bottom system window inset is not a navigation bar. Wear devices
            // that have bottom chin are examples.  For now, assume that it's a navigation bar if it
            // has the same height as the root window's stable bottom inset.
            final WindowInsets rootWindowInsets = getRootWindowInsets();
            if (rootWindowInsets != null && (rootWindowInsets.getStableInsetBottom()
                    != insets.getSystemWindowInsetBottom())) {
                // This is probably not a NavBar.
                updateBottomPaddingIfNecessary(0);
                return insets;
            }

            final int possibleNavBarHeight = insets.getSystemWindowInsetBottom();
            updateBottomPaddingIfNecessary(possibleNavBarHeight);
            return possibleNavBarHeight <= 0
                    ? insets
                    : insets.replaceSystemWindowInsets(
                            insets.getSystemWindowInsetLeft(),
                            insets.getSystemWindowInsetTop(),
                            insets.getSystemWindowInsetRight(),
                            0 /* bottom */);
        }

        @Override
        protected void onWindowVisibilityChanged(int visibility) {
            mMockIme.getTracer().onWindowVisibilityChanged(() -> {
                super.onWindowVisibilityChanged(visibility);
            }, visibility);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            removeOnLayoutChangeListener(mLayoutListener);
        }

        @MainThread
        private void updateInlineSuggestions(
                @NonNull PendingInlineSuggestions pendingInlineSuggestions) {
            Log.d(TAG, "updateInlineSuggestions() called: " + pendingInlineSuggestions.mTotalCount);
            if (mSuggestionView == null || !pendingInlineSuggestions.mValid.get()) {
                return;
            }
            mSuggestionView.removeAllViews();
            for (int i = 0; i < pendingInlineSuggestions.mTotalCount; i++) {
                View view = pendingInlineSuggestions.mViews[i];
                if (view == null) {
                    continue;
                }
                mSuggestionView.addView(view);
            }
        }
    }

    KeyboardLayoutView mView;

    private void onInputViewLayoutChanged(@NonNull ImeLayoutInfo layoutInfo) {
        getTracer().onInputViewLayoutChanged(layoutInfo, () -> { });
    }

    @Override
    public View onCreateInputView() {
        return getTracer().onCreateInputView(() -> {
            mView = new KeyboardLayoutView(this, mSettings, this::onInputViewLayoutChanged);
            return mView;
        });
    }

    @Override
    public void onStartInput(EditorInfo editorInfo, boolean restarting) {
        getTracer().onStartInput(editorInfo, restarting,
                () -> super.onStartInput(editorInfo, restarting));
    }

    @Override
    public void onStartInputView(EditorInfo editorInfo, boolean restarting) {
        getTracer().onStartInputView(editorInfo, restarting,
                () -> super.onStartInputView(editorInfo, restarting));
    }


    @Override
    public void onPrepareStylusHandwriting() {
        getTracer().onPrepareStylusHandwriting(() -> super.onPrepareStylusHandwriting());
    }

    @Override
    public boolean onStartStylusHandwriting() {
        if (mEvents != null) {
            mEvents.clear();
        }
        getTracer().onStartStylusHandwriting(() -> super.onStartStylusHandwriting());
        return true;
    }

    @Override
    public boolean onStartConnectionlessStylusHandwriting(
            int inputType, @Nullable CursorAnchorInfo cursorAnchorInfo) {
        if (mEvents != null) {
            mEvents.clear();
        }
        getTracer().onStartConnectionlessStylusHandwriting(
                () -> super.onStartConnectionlessStylusHandwriting(inputType, cursorAnchorInfo));
        return mSettings.isConnectionlessHandwritingEnabled();
    }

    @Override
    public void onStylusHandwritingMotionEvent(@NonNull MotionEvent motionEvent) {
        if (mEvents == null) {
            mEvents = new ArrayList<>();
        }
        mEvents.add(MotionEvent.obtain(motionEvent));
        getTracer().onStylusHandwritingMotionEvent(()
                -> super.onStylusHandwritingMotionEvent(motionEvent));
    }

    @Override
    public void onUpdateEditorToolType(int toolType) {
        if (mEvents != null) {
            mEvents.clear();
        }
        getTracer().onUpdateEditorToolType(toolType, () -> super.onUpdateEditorToolType(toolType));
    }

    @Override
    public void onFinishStylusHandwriting() {
        getTracer().onFinishStylusHandwriting(() -> super.onFinishStylusHandwriting());
    }


    @Override
    public void onFinishInputView(boolean finishingInput) {
        getTracer().onFinishInputView(finishingInput,
                () -> super.onFinishInputView(finishingInput));
    }

    @Override
    public void onFinishInput() {
        getTracer().onFinishInput(() -> super.onFinishInput());
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return getTracer().onKeyDown(keyCode, event, () -> super.onKeyDown(keyCode, event));
    }

    @Override
    public void onUpdateCursorAnchorInfo(CursorAnchorInfo cursorAnchorInfo) {
        getTracer().onUpdateCursorAnchorInfo(cursorAnchorInfo,
                () -> super.onUpdateCursorAnchorInfo(cursorAnchorInfo));
    }

    @Override
    public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
            int candidatesStart, int candidatesEnd) {
        getTracer().onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                candidatesStart, candidatesEnd,
                () -> super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                        candidatesStart, candidatesEnd));
    }

    @CallSuper
    public boolean onEvaluateInputViewShown() {
        return getTracer().onEvaluateInputViewShown(() -> {
            // onShowInputRequested() is indeed @CallSuper so we always call this, even when the
            // result is ignored.
            final boolean originalResult = super.onEvaluateInputViewShown();
            if (!mSettings.getHardKeyboardConfigurationBehaviorAllowed(false)) {
                final Configuration config = getResources().getConfiguration();
                if (config.keyboard != Configuration.KEYBOARD_NOKEYS
                        && config.hardKeyboardHidden != Configuration.HARDKEYBOARDHIDDEN_YES) {
                    // Override the behavior of InputMethodService#onEvaluateInputViewShown()
                    return true;
                }
            }
            return originalResult;
        });
    }

    @Override
    public boolean onShowInputRequested(int flags, boolean configChange) {
        return getTracer().onShowInputRequested(flags, configChange, () -> {
            // onShowInputRequested() is not marked with @CallSuper, but just in case.
            final boolean originalResult = super.onShowInputRequested(flags, configChange);
            if (!mSettings.getHardKeyboardConfigurationBehaviorAllowed(false)) {
                if ((flags & InputMethod.SHOW_EXPLICIT) == 0
                        && getResources().getConfiguration().keyboard
                        != Configuration.KEYBOARD_NOKEYS) {
                    // Override the behavior of InputMethodService#onShowInputRequested()
                    return true;
                }
            }
            return originalResult;
        });
    }

    @Override
    public void onDestroy() {
        getTracer().onDestroy(() -> {
            mDestroying = true;
            super.onDestroy();
            if (mSettings.isWindowLayoutInfoCallbackEnabled() && mWindowLayoutComponent != null) {
                mWindowLayoutComponent.removeWindowLayoutInfoListener(mWindowLayoutInfoConsumer);
            }
            mSettings.getChannel().unregisterListener(mCommandHandler);
            mHandlerThread.quitSafely();
        });
    }

    @Override
    public AbstractInputMethodImpl onCreateInputMethodInterface() {
        return getTracer().onCreateInputMethodInterface(() -> new MockInputMethodImpl());
    }

    private final ThreadLocal<Tracer> mThreadLocalTracer = new ThreadLocal<>();

    private Tracer getTracer() {
        Tracer tracer = mThreadLocalTracer.get();
        if (tracer == null) {
            tracer = new Tracer(this);
            mThreadLocalTracer.set(tracer);
        }
        return tracer;
    }

    @NonNull
    private ImeState getState() {
        final boolean hasInputBinding = getCurrentInputBinding() != null;
        final boolean hasFallbackInputConnection =
                !hasInputBinding
                        || getCurrentInputConnection() == getCurrentInputBinding().getConnection();
        return new ImeState(hasInputBinding, hasFallbackInputConnection);
    }

    private PendingInlineSuggestions mPendingInlineSuggestions;

    private static final class PendingInlineSuggestions {
        final InlineSuggestionsResponse mResponse;
        final int mTotalCount;
        final View[] mViews;
        final AtomicInteger mInflatedViewCount;
        final AtomicBoolean mValid = new AtomicBoolean(true);

        PendingInlineSuggestions(InlineSuggestionsResponse response) {
            mResponse = response;
            mTotalCount = response.getInlineSuggestions().size();
            mViews = new View[mTotalCount];
            mInflatedViewCount = new AtomicInteger(0);
        }
    }

    @MainThread
    @Override
    public InlineSuggestionsRequest onCreateInlineSuggestionsRequest(Bundle uiExtras) {
        StylesBuilder stylesBuilder = UiVersions.newStylesBuilder();
        stylesBuilder.addStyle(InlineSuggestionUi.newStyleBuilder().build());
        Bundle styles = stylesBuilder.build();

        final boolean supportedInlineSuggestions;
        Bundle inlineSuggestionsExtras = SettingsProvider.getInlineSuggestionsExtras();
        if (inlineSuggestionsExtras != null) {
            styles.putAll(inlineSuggestionsExtras);
            supportedInlineSuggestions =
                    inlineSuggestionsExtras.getBoolean("InlineSuggestions", true);
        } else {
            supportedInlineSuggestions = true;
        }

        if (!supportedInlineSuggestions) {
            return null;
        }

        return getTracer().onCreateInlineSuggestionsRequest(() -> {
            final ArrayList<InlinePresentationSpec> presentationSpecs = new ArrayList<>();
            presentationSpecs.add(new InlinePresentationSpec.Builder(new Size(100, 100),
                    new Size(400, 100)).setStyle(styles).build());
            presentationSpecs.add(new InlinePresentationSpec.Builder(new Size(100, 100),
                    new Size(400, 100)).setStyle(styles).build());

            final InlinePresentationSpec tooltipSpec =
                    new InlinePresentationSpec.Builder(new Size(100, 100),
                            new Size(400, 100)).setStyle(styles).build();
            final InlineSuggestionsRequest.Builder builder =
                    new InlineSuggestionsRequest.Builder(presentationSpecs)
                            .setInlineTooltipPresentationSpec(tooltipSpec)
                            .setMaxSuggestionCount(6);
            if (inlineSuggestionsExtras != null) {
                builder.setExtras(inlineSuggestionsExtras.deepCopy());
            }
            return builder.build();
        });
    }

    @MainThread
    @Override
    public boolean onInlineSuggestionsResponse(@NonNull InlineSuggestionsResponse response) {
        return getTracer().onInlineSuggestionsResponse(response, () -> {
            final PendingInlineSuggestions pendingInlineSuggestions =
                    new PendingInlineSuggestions(response);
            if (mPendingInlineSuggestions != null) {
                mPendingInlineSuggestions.mValid.set(false);
            }
            mPendingInlineSuggestions = pendingInlineSuggestions;
            if (pendingInlineSuggestions.mTotalCount == 0) {
                if (mView != null) {
                    mView.updateInlineSuggestions(pendingInlineSuggestions);
                }
                return true;
            }

            final ExecutorService executorService = Executors.newCachedThreadPool();
            for (int i = 0; i < pendingInlineSuggestions.mTotalCount; i++) {
                final int index = i;
                InlineSuggestion inlineSuggestion =
                        pendingInlineSuggestions.mResponse.getInlineSuggestions().get(index);
                inlineSuggestion.inflate(
                        this,
                        new Size(WRAP_CONTENT, WRAP_CONTENT),
                        executorService,
                        suggestionView -> {
                            Log.d(TAG, "new inline suggestion view ready");
                            if (suggestionView != null) {
                                suggestionView.setOnClickListener((v) -> {
                                    getTracer().onInlineSuggestionClickedEvent(() -> { });
                                });
                                suggestionView.setOnLongClickListener((v) -> {
                                    getTracer().onInlineSuggestionLongClickedEvent(() -> { });
                                    return true;
                                });
                                pendingInlineSuggestions.mViews[index] = suggestionView;
                            }
                            if (pendingInlineSuggestions.mInflatedViewCount.incrementAndGet()
                                    == pendingInlineSuggestions.mTotalCount
                                    && pendingInlineSuggestions.mValid.get()) {
                                Log.d(TAG, "ready to display all suggestions");
                                mMainHandler.post(() ->
                                        mView.updateInlineSuggestions(pendingInlineSuggestions));
                            }
                        });
            }
            return true;
        });
    }

    @Override
    public void onConfigurationChanged(Configuration configuration) {
        // Broadcasting configuration change is implemented at WindowProviderService level.
        super.onConfigurationChanged(configuration);
        getTracer().onConfigurationChanged(() -> {}, configuration);
        mLastDispatchedConfiguration.setTo(configuration);
    }

    /**
     * Event tracing helper class for {@link MockIme}.
     */
    private static final class Tracer {

        @NonNull
        private final MockIme mIme;

        private final int mThreadId = Process.myTid();

        @NonNull
        private final String mThreadName =
                Thread.currentThread().getName() != null ? Thread.currentThread().getName() : "";

        private final boolean mIsMainThread =
                Looper.getMainLooper().getThread() == Thread.currentThread();

        private int mNestLevel = 0;

        Tracer(@NonNull MockIme mockIme) {
            mIme = mockIme;
        }

        private void sendEventInternal(@NonNull ImeEvent event) {
            if (mIme.mSettings == null) {
                Log.e(TAG, "Tracer cannot be used before onCreate()");
                return;
            }
            if (!mIme.mSettings.getChannel().send(event.toBundle())) {
                Log.w(TAG, "Channel already closed: " + event.getEventName(), new Throwable());
            }
        }

        private void recordEventInternal(@NonNull String eventName, @NonNull Runnable runnable) {
            recordEventInternal(eventName, runnable, new Bundle());
        }

        private void recordEventInternal(@NonNull String eventName, @NonNull Runnable runnable,
                @NonNull Bundle arguments) {
            recordEventInternal(eventName, () -> {
                runnable.run(); return ImeEvent.RETURN_VALUE_UNAVAILABLE;
            }, arguments);
        }

        private <T> T recordEventInternal(@NonNull String eventName,
                @NonNull Supplier<T> supplier) {
            return recordEventInternal(eventName, supplier, new Bundle());
        }

        private <T> T recordEventInternal(@NonNull String eventName,
                @NonNull Supplier<T> supplier, @NonNull Bundle arguments) {
            final ImeState enterState = mIme.getState();
            final long enterTimestamp = SystemClock.elapsedRealtimeNanos();
            final long enterWallTime = System.currentTimeMillis();
            final int nestLevel = mNestLevel;
            // Send enter event
            sendEventInternal(new ImeEvent(eventName, nestLevel, mThreadName,
                    mThreadId, mIsMainThread, enterTimestamp, 0, enterWallTime,
                    0, enterState, null, arguments,
                    ImeEvent.RETURN_VALUE_UNAVAILABLE));
            ++mNestLevel;
            T result;
            try {
                result = supplier.get();
            } finally {
                --mNestLevel;
            }
            final long exitTimestamp = SystemClock.elapsedRealtimeNanos();
            final long exitWallTime = System.currentTimeMillis();
            final ImeState exitState = mIme.getState();
            // Send exit event
            sendEventInternal(new ImeEvent(eventName, nestLevel, mThreadName,
                    mThreadId, mIsMainThread, enterTimestamp, exitTimestamp, enterWallTime,
                    exitWallTime, enterState, exitState, arguments, result));
            return result;
        }

        void onCreate(@NonNull Runnable runnable) {
            recordEventInternal("onCreate", runnable);
        }

        void createSession(@NonNull Runnable runnable) {
            recordEventInternal("createSession", runnable);
        }

        void onVerify(String name, @NonNull BooleanSupplier supplier) {
            final Bundle arguments = new Bundle();
            arguments.putString("name", name);
            recordEventInternal("onVerify", supplier::getAsBoolean, arguments);
        }

        void onCurrentInputMethodSubtypeChanged(InputMethodSubtype newSubtype,
                @NonNull Runnable runnable) {
            final Bundle arguments = new Bundle();
            arguments.putParcelable("newSubtype", newSubtype);
            recordEventInternal("onCurrentInputMethodSubtypeChanged", runnable, arguments);
        }

        void onConfigureWindow(Window win, boolean isFullscreen, boolean isCandidatesOnly,
                @NonNull Runnable runnable) {
            final Bundle arguments = new Bundle();
            arguments.putBoolean("isFullscreen", isFullscreen);
            arguments.putBoolean("isCandidatesOnly", isCandidatesOnly);
            recordEventInternal("onConfigureWindow", runnable, arguments);
        }

        boolean onEvaluateFullscreenMode(@NonNull BooleanSupplier supplier) {
            return recordEventInternal("onEvaluateFullscreenMode", supplier::getAsBoolean);
        }

        boolean onEvaluateInputViewShown(@NonNull BooleanSupplier supplier) {
            return recordEventInternal("onEvaluateInputViewShown", supplier::getAsBoolean);
        }

        View onCreateInputView(@NonNull Supplier<View> supplier) {
            return recordEventInternal("onCreateInputView", supplier);
        }

        void onStartInput(EditorInfo editorInfo, boolean restarting, @NonNull Runnable runnable) {
            final Bundle arguments = new Bundle();
            arguments.putParcelable("editorInfo", editorInfo);
            arguments.putBoolean("restarting", restarting);
            recordEventInternal("onStartInput", runnable, arguments);
        }

        void onWindowVisibilityChanged(@NonNull Runnable runnable, int visibility) {
            final Bundle arguments = new Bundle();
            arguments.putInt("visible", visibility);
            recordEventInternal("onWindowVisibilityChanged", runnable, arguments);
        }

        void onStartInputView(EditorInfo editorInfo, boolean restarting,
                @NonNull Runnable runnable) {
            final Bundle arguments = new Bundle();
            arguments.putParcelable("editorInfo", editorInfo);
            arguments.putBoolean("restarting", restarting);
            recordEventInternal("onStartInputView", runnable, arguments);
        }

        void onPrepareStylusHandwriting(@NonNull Runnable runnable) {
            final Bundle arguments = new Bundle();
            arguments.putParcelable("editorInfo", mIme.getCurrentInputEditorInfo());
            recordEventInternal("onPrepareStylusHandwriting", runnable, arguments);
        }

        void onStartStylusHandwriting(@NonNull Runnable runnable) {
            final Bundle arguments = new Bundle();
            arguments.putParcelable("editorInfo", mIme.getCurrentInputEditorInfo());
            recordEventInternal("onStartStylusHandwriting", runnable, arguments);
        }

        void onStartConnectionlessStylusHandwriting(@NonNull Runnable runnable) {
            recordEventInternal("onStartConnectionlessStylusHandwriting", runnable);
        }

        void onStylusHandwritingMotionEvent(@NonNull Runnable runnable) {
            recordEventInternal("onStylusMotionEvent", runnable);
        }

        void onFinishStylusHandwriting(@NonNull Runnable runnable) {
            final Bundle arguments = new Bundle();
            arguments.putParcelable("editorInfo", mIme.getCurrentInputEditorInfo());
            recordEventInternal("onFinishStylusHandwriting", runnable, arguments);
        }

        void onFinishInputView(boolean finishingInput, @NonNull Runnable runnable) {
            final Bundle arguments = new Bundle();
            arguments.putBoolean("finishingInput", finishingInput);
            recordEventInternal("onFinishInputView", runnable, arguments);
        }

        void onFinishInput(@NonNull Runnable runnable) {
            recordEventInternal("onFinishInput", runnable);
        }

        void onUpdateEditorToolType(int toolType, @NonNull Runnable runnable) {
            final Bundle arguments = new Bundle();
            arguments.putInt("toolType", toolType);
            recordEventInternal("onUpdateEditorToolType", runnable, arguments);
        }

        boolean onKeyDown(int keyCode, KeyEvent event, @NonNull BooleanSupplier supplier) {
            final Bundle arguments = new Bundle();
            arguments.putInt("keyCode", keyCode);
            arguments.putParcelable("event", event);
            return recordEventInternal("onKeyDown", supplier::getAsBoolean, arguments);
        }

        void onUpdateCursorAnchorInfo(CursorAnchorInfo cursorAnchorInfo,
                @NonNull Runnable runnable) {
            final Bundle arguments = new Bundle();
            arguments.putParcelable("cursorAnchorInfo", cursorAnchorInfo);
            recordEventInternal("onUpdateCursorAnchorInfo", runnable, arguments);
        }

        void onUpdateSelection(int oldSelStart,
                int oldSelEnd,
                int newSelStart,
                int newSelEnd,
                int candidatesStart,
                int candidatesEnd,
                @NonNull Runnable runnable) {
            final Bundle arguments = new Bundle();
            arguments.putInt("oldSelStart", oldSelStart);
            arguments.putInt("oldSelEnd", oldSelEnd);
            arguments.putInt("newSelStart", newSelStart);
            arguments.putInt("newSelEnd", newSelEnd);
            arguments.putInt("candidatesStart", candidatesStart);
            arguments.putInt("candidatesEnd", candidatesEnd);
            recordEventInternal("onUpdateSelection", runnable, arguments);
        }

        boolean onShowInputRequested(int flags, boolean configChange,
                @NonNull BooleanSupplier supplier) {
            final Bundle arguments = new Bundle();
            arguments.putInt("flags", flags);
            arguments.putBoolean("configChange", configChange);
            return recordEventInternal("onShowInputRequested", supplier::getAsBoolean, arguments);
        }

        void onDestroy(@NonNull Runnable runnable) {
            recordEventInternal("onDestroy", runnable);
        }

        void attachToken(IBinder token, @NonNull Runnable runnable) {
            final Bundle arguments = new Bundle();
            arguments.putBinder("token", token);
            recordEventInternal("attachToken", runnable, arguments);
        }

        void bindInput(InputBinding binding, @NonNull Runnable runnable) {
            final Bundle arguments = new Bundle();
            arguments.putParcelable("binding", binding);
            recordEventInternal("bindInput", runnable, arguments);
        }

        void unbindInput(@NonNull Runnable runnable) {
            recordEventInternal("unbindInput", runnable);
        }

        void showSoftInput(int flags, ResultReceiver resultReceiver, @NonNull Runnable runnable) {
            final Bundle arguments = new Bundle();
            arguments.putInt("flags", flags);
            arguments.putParcelable("resultReceiver", resultReceiver);
            recordEventInternal("showSoftInput", runnable, arguments);
        }

        void hideSoftInput(int flags, ResultReceiver resultReceiver, @NonNull Runnable runnable) {
            final Bundle arguments = new Bundle();
            arguments.putInt("flags", flags);
            arguments.putParcelable("resultReceiver", resultReceiver);
            recordEventInternal("hideSoftInput", runnable, arguments);
        }

        AbstractInputMethodImpl onCreateInputMethodInterface(
                @NonNull Supplier<AbstractInputMethodImpl> supplier) {
            return recordEventInternal("onCreateInputMethodInterface", supplier);
        }

        void onReceiveCommand(@NonNull ImeCommand command, @NonNull Runnable runnable) {
            final Bundle arguments = new Bundle();
            arguments.putBundle("command", command.toBundle());
            recordEventInternal("onReceiveCommand", runnable, arguments);
        }

        void onHandleCommand(
                @NonNull ImeCommand command, @NonNull Supplier<Object> resultSupplier) {
            final Bundle arguments = new Bundle();
            arguments.putBundle("command", command.toBundle());
            recordEventInternal("onHandleCommand", resultSupplier, arguments);
        }

        void onInputViewLayoutChanged(@NonNull ImeLayoutInfo imeLayoutInfo,
                @NonNull Runnable runnable) {
            final Bundle arguments = new Bundle();
            imeLayoutInfo.writeToBundle(arguments);
            recordEventInternal("onInputViewLayoutChanged", runnable, arguments);
        }

        void onStrictModeViolated(@NonNull Runnable runnable) {
            final Bundle arguments = new Bundle();
            recordEventInternal("onStrictModeViolated", runnable, arguments);
        }

        InlineSuggestionsRequest onCreateInlineSuggestionsRequest(
                @NonNull Supplier<InlineSuggestionsRequest> supplier) {
            return recordEventInternal("onCreateInlineSuggestionsRequest", supplier);
        }

        boolean onInlineSuggestionsResponse(@NonNull InlineSuggestionsResponse response,
                @NonNull BooleanSupplier supplier) {
            final Bundle arguments = new Bundle();
            arguments.putParcelable("response", response);
            return recordEventInternal("onInlineSuggestionsResponse", supplier::getAsBoolean,
                    arguments);
        }

        void onInlineSuggestionClickedEvent(@NonNull Runnable runnable) {
            final Bundle arguments = new Bundle();
            recordEventInternal("onInlineSuggestionClickedEvent", runnable, arguments);
        }

        void onInlineSuggestionLongClickedEvent(@NonNull Runnable runnable) {
            final Bundle arguments = new Bundle();
            recordEventInternal("onInlineSuggestionLongClickedEvent", runnable, arguments);
        }

        void onConfigurationChanged(@NonNull Runnable runnable, Configuration configuration) {
            final Bundle arguments = new Bundle();
            arguments.putParcelable("Configuration", configuration);
            arguments.putInt("ConfigUpdates", configuration.diff(
                    mIme.mLastDispatchedConfiguration));
            recordEventInternal("onConfigurationChanged", runnable, arguments);
        }

        void onPerformHandwritingGestureResult(int result, long requestId, Runnable runnable) {
            final Bundle arguments = new Bundle();
            arguments.putInt("result", result);
            arguments.putLong("requestId", requestId);
            recordEventInternal("onPerformHandwritingGestureResult", runnable, arguments);
        }

        public void onRequestTextBoundsInfoResult(TextBoundsInfoResult result, long requestId) {
            final Bundle arguments = new Bundle();
            arguments.putInt("resultCode", result.getResultCode());
            arguments.putParcelable("boundsInfo", result.getTextBoundsInfo());
            arguments.putLong("requestId", requestId);
            recordEventInternal("onRequestTextBoundsInfoResult", () -> {}, arguments);
        }

        void getWindowLayoutInfo(@NonNull WindowLayoutInfo windowLayoutInfo,
                @NonNull Runnable runnable) {
            final Bundle arguments = new Bundle();
            ImeEventStreamTestUtils.WindowLayoutInfoParcelable parcel =
                    new ImeEventStreamTestUtils.WindowLayoutInfoParcelable(windowLayoutInfo);
            arguments.putParcelable("WindowLayoutInfo", parcel);
            recordEventInternal("getWindowLayoutInfo", runnable, arguments);
        }
    }
}
