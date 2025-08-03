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

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.RemoteCallback;
import android.view.inputmethod.InputMethodSubtype;

import androidx.annotation.ColorInt;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.window.extensions.layout.WindowLayoutInfo;

import java.lang.annotation.Retention;

/**
 * An immutable data store to control the behavior of {@link MockIme}.
 */
public class ImeSettings {

    @NonNull
    private final String mClientPackageName;

    @NonNull
    private final String mEventCallbackActionName;

    private static final String EVENT_CALLBACK_INTENT_ACTION_KEY = "eventCallbackActionName";
    private static final String CHANNEL_KEY = "channel";
    private static final String DATA_KEY = "data";

    private static final String BACKGROUND_COLOR_KEY = "BackgroundColor";
    private static final String NAVIGATION_BAR_COLOR_KEY = "NavigationBarColor";
    private static final String INPUT_VIEW_HEIGHT =
            "InputViewHeightWithoutSystemWindowInset";
    private static final String DRAWS_BEHIND_NAV_BAR = "drawsBehindNavBar";
    private static final String WINDOW_FLAGS = "WindowFlags";
    private static final String WINDOW_FLAGS_MASK = "WindowFlagsMask";
    private static final String FULLSCREEN_MODE_POLICY = "FullscreenModePolicy";
    private static final String INPUT_VIEW_SYSTEM_UI_VISIBILITY = "InputViewSystemUiVisibility";
    private static final String WATERMARK_ENABLED = "WatermarkEnabled";
    private static final String WATERMARK_GRAVITY = "WatermarkGravity";
    private static final String HARD_KEYBOARD_CONFIGURATION_BEHAVIOR_ALLOWED =
            "HardKeyboardConfigurationBehaviorAllowed";
    private static final String INLINE_SUGGESTIONS_ENABLED = "InlineSuggestionsEnabled";
    private static final String INLINE_SUGGESTION_VIEW_CONTENT_DESC =
            "InlineSuggestionViewContentDesc";
    private static final String STRICT_MODE_ENABLED = "StrictModeEnabled";
    private static final String VERIFY_CONTEXT_APIS_IN_ON_CREATE = "VerifyContextApisInOnCreate";
    private static final String WINDOW_LAYOUT_INFO_CALLBACK_ENABLED =
            "WindowLayoutInfoCallbackEnabled";
    private static final String CONNECTIONLESS_HANDWRITING_ENABLED =
            "ConnectionlessHandwritingEnabled";

    /**
     * Simulate the manifest flag enableOnBackInvokedCallback being true for the IME.
     */
    private static final String ON_BACK_CALLBACK_ENABLED = "onBackCallbackEnabled";

    private static final String USE_CUSTOM_EXTRACT_TEXT_VIEW = "useCustomExtractTextView";

    @NonNull
    private final PersistableBundle mBundle;
    private final SessionChannel mChannel;

    @Retention(SOURCE)
    @IntDef(value = {
            FullscreenModePolicy.NO_FULLSCREEN,
            FullscreenModePolicy.FORCE_FULLSCREEN,
            FullscreenModePolicy.OS_DEFAULT,
    })
    public @interface FullscreenModePolicy {
        /**
         * Let {@link MockIme} always return {@code false} from
         * {@link android.inputmethodservice.InputMethodService#onEvaluateFullscreenMode()}.
         *
         * <p>This is chosen to be the default behavior of {@link MockIme} to make CTS tests most
         * deterministic.</p>
         */
        int NO_FULLSCREEN = 0;

        /**
         * Let {@link MockIme} always return {@code true} from
         * {@link android.inputmethodservice.InputMethodService#onEvaluateFullscreenMode()}.
         *
         * <p>This can be used to test the behaviors when a full-screen IME is running.</p>
         */
        int FORCE_FULLSCREEN = 1;

        /**
         * Let {@link MockIme} always return the default behavior of
         * {@link android.inputmethodservice.InputMethodService#onEvaluateFullscreenMode()}.
         *
         * <p>This can be used to test the default behavior of that public API.</p>
         */
        int OS_DEFAULT = 2;
    }

    ImeSettings(@NonNull String clientPackageName, @NonNull Bundle bundle) {
        mClientPackageName = clientPackageName;
        mEventCallbackActionName = bundle.getString(EVENT_CALLBACK_INTENT_ACTION_KEY);
        mBundle = bundle.getParcelable(DATA_KEY);
        mChannel = new SessionChannel(bundle.getParcelable(CHANNEL_KEY, RemoteCallback.class));
    }

    @Nullable
    String getEventCallbackActionName() {
        return mEventCallbackActionName;
    }

    SessionChannel getChannel() {
        return mChannel;
    }

    @NonNull
    String getClientPackageName() {
        return mClientPackageName;
    }

    @FullscreenModePolicy
    public int fullscreenModePolicy() {
        return mBundle.getInt(FULLSCREEN_MODE_POLICY);
    }

    @ColorInt
    public int getBackgroundColor(@ColorInt int defaultColor) {
        return mBundle.getInt(BACKGROUND_COLOR_KEY, defaultColor);
    }

    public boolean hasNavigationBarColor() {
        return mBundle.keySet().contains(NAVIGATION_BAR_COLOR_KEY);
    }

    @ColorInt
    public int getNavigationBarColor() {
        return mBundle.getInt(NAVIGATION_BAR_COLOR_KEY);
    }

    public int getInputViewHeight(int defaultHeight) {
        return mBundle.getInt(INPUT_VIEW_HEIGHT, defaultHeight);
    }

    public boolean getDrawsBehindNavBar() {
        return mBundle.getBoolean(DRAWS_BEHIND_NAV_BAR, false);
    }

    public int getWindowFlags(int defaultFlags) {
        return mBundle.getInt(WINDOW_FLAGS, defaultFlags);
    }

    public int getWindowFlagsMask(int defaultFlags) {
        return mBundle.getInt(WINDOW_FLAGS_MASK, defaultFlags);
    }

    public int getInputViewSystemUiVisibility(int defaultFlags) {
        return mBundle.getInt(INPUT_VIEW_SYSTEM_UI_VISIBILITY, defaultFlags);
    }

    public boolean isWatermarkEnabled(boolean defaultValue) {
        return mBundle.getBoolean(WATERMARK_ENABLED, defaultValue);
    }

    public int getWatermarkGravity(int defaultValue) {
        return mBundle.getInt(WATERMARK_GRAVITY, defaultValue);
    }

    public boolean getHardKeyboardConfigurationBehaviorAllowed(boolean defaultValue) {
        return mBundle.getBoolean(HARD_KEYBOARD_CONFIGURATION_BEHAVIOR_ALLOWED, defaultValue);
    }

    public boolean getInlineSuggestionsEnabled() {
        return mBundle.getBoolean(INLINE_SUGGESTIONS_ENABLED);
    }

    @Nullable
    public String getInlineSuggestionViewContentDesc(@Nullable String defaultValue) {
        return mBundle.getString(INLINE_SUGGESTION_VIEW_CONTENT_DESC, defaultValue);
    }

    public boolean isStrictModeEnabled() {
        return mBundle.getBoolean(STRICT_MODE_ENABLED, false);
    }

    public boolean isVerifyContextApisInOnCreate() {
        return mBundle.getBoolean(VERIFY_CONTEXT_APIS_IN_ON_CREATE, false);
    }

    public boolean isWindowLayoutInfoCallbackEnabled() {
        return mBundle.getBoolean(WINDOW_LAYOUT_INFO_CALLBACK_ENABLED, false);
    }

    public boolean isConnectionlessHandwritingEnabled() {
        return mBundle.getBoolean(CONNECTIONLESS_HANDWRITING_ENABLED, false);
    }

    public boolean isOnBackCallbackEnabled() {
        return mBundle.getBoolean(ON_BACK_CALLBACK_ENABLED, false);
    }

    public void close() {
        if (mChannel != null) {
            mChannel.close();
        }
    }

    /** Whether or not custom extract view hierarchy should be used. */
    public boolean isCustomExtractTextViewEnabled() {
        return mBundle.getBoolean(USE_CUSTOM_EXTRACT_TEXT_VIEW, false);
    }

    static Bundle serializeToBundle(@NonNull String eventCallbackActionName,
            @Nullable Builder builder, @NonNull RemoteCallback channel) {
        final Bundle result = new Bundle();
        result.putString(EVENT_CALLBACK_INTENT_ACTION_KEY, eventCallbackActionName);
        result.putParcelable(DATA_KEY, builder != null ? builder.mBundle : PersistableBundle.EMPTY);
        result.putParcelable(CHANNEL_KEY, channel);
        return result;
    }

    /**
     * The builder class for {@link ImeSettings}.
     */
    public static final class Builder {
        private final PersistableBundle mBundle = new PersistableBundle();

        @MockImePackageNames
        @NonNull
        String mMockImePackageName = MockImePackageNames.MockIme1;

        /**
         * Specifies a non-default {@link MockIme} package name, which is by default
         * {@code com.android.cts.mockime}.
         *
         * <p>You can use this to interact with multiple {@link MockIme} sessions at the same time.
         * </p>
         *
         * @param packageName One of {@link MockImePackageNames}.
         * @return this {@link Builder} object
         */
        public Builder setMockImePackageName(@MockImePackageNames String packageName) {
            mMockImePackageName = packageName;
            return this;
        }

        boolean mSuppressResetIme = false;
        /**
         * Specifies whether {@code adb shell ime reset} should be suppressed or not on
         * {@link MockImeSession#create(android.content.Context)} and
         * {@link MockImeSession#close()}.
         *
         * <p>The default value is {@code false}.</p>
         *
         * @param suppressResetIme {@code true} to suppress {@code adb shell ime reset} upon
         *                         initialize and cleanup processes of {@link MockImeSession}.
         * @return this {@link Builder} object
         */
        public Builder setSuppressResetIme(boolean suppressResetIme) {
            mSuppressResetIme = suppressResetIme;
            return this;
        }

        @Nullable
        InputMethodSubtype[] mAdditionalSubtypes;

        /**
         * Specifies additional {@link InputMethodSubtype}s to be set before launching
         * {@link MockIme} by using
         * {@link android.view.inputmethod.InputMethodManager#setAdditionalInputMethodSubtypes(
         * String, InputMethodSubtype[])}.
         *
         * @param subtypes An array of {@link InputMethodSubtype}.
         * @return this {@link Builder} object
         */
        public Builder setAdditionalSubtypes(InputMethodSubtype... subtypes) {
            mAdditionalSubtypes = subtypes;
            return this;
        }

        /**
         * Controls how MockIme reacts to
         * {@link android.inputmethodservice.InputMethodService#onEvaluateFullscreenMode()}.
         *
         * @param policy one of {@link FullscreenModePolicy}
         * @see MockIme#onEvaluateFullscreenMode()
         */
        public Builder setFullscreenModePolicy(@FullscreenModePolicy int policy) {
            mBundle.putInt(FULLSCREEN_MODE_POLICY, policy);
            return this;
        }

        /**
         * Sets the background color of the {@link MockIme}.
         * @param color background color to be used
         */
        public Builder setBackgroundColor(@ColorInt int color) {
            mBundle.putInt(BACKGROUND_COLOR_KEY, color);
            return this;
        }

        /**
         * Sets the color to be passed to {@link android.view.Window#setNavigationBarColor(int)}.
         *
         * @param color color to be passed to {@link android.view.Window#setNavigationBarColor(int)}
         * @see android.view.View
         */
        public Builder setNavigationBarColor(@ColorInt int color) {
            mBundle.putInt(NAVIGATION_BAR_COLOR_KEY, color);
            return this;
        }

        /**
         * Sets the input view height measured from the bottom of the screen.
         *
         * @param height height of the soft input view. This includes the system window inset such
         *               as navigation bar.
         */
        public Builder setInputViewHeight(int height) {
            mBundle.putInt(INPUT_VIEW_HEIGHT, height);
            return this;
        }

        /**
         * Sets whether IME draws behind navigation bar.
         */
        public Builder setDrawsBehindNavBar(boolean drawsBehindNavBar) {
            mBundle.putBoolean(DRAWS_BEHIND_NAV_BAR, drawsBehindNavBar);
            return this;
        }

        /**
         * Sets window flags to be specified to {@link android.view.Window#setFlags(int, int)} of
         * the main {@link MockIme} window.
         *
         * <p>When {@link android.view.WindowManager.LayoutParams#FLAG_LAYOUT_IN_OVERSCAN} is set,
         * {@link MockIme} tries to render the navigation bar by itself.</p>
         *
         * @param flags flags to be specified
         * @param flagsMask mask bits that specify what bits need to be cleared before setting
         *                  {@code flags}
         * @see android.view.WindowManager
         */
        public Builder setWindowFlags(int flags, int flagsMask) {
            mBundle.putInt(WINDOW_FLAGS, flags);
            mBundle.putInt(WINDOW_FLAGS_MASK, flagsMask);
            return this;
        }

        /**
         * Sets flags to be specified to {@link android.view.View#setSystemUiVisibility(int)} of
         * the main soft input view (the returned view from {@link MockIme#onCreateInputView()}).
         *
         * @param visibilityFlags flags to be specified
         * @see android.view.View
         */
        public Builder setInputViewSystemUiVisibility(int visibilityFlags) {
            mBundle.putInt(INPUT_VIEW_SYSTEM_UI_VISIBILITY, visibilityFlags);
            return this;
        }

        /**
         * Sets whether a unique watermark image needs to be shown on the software keyboard or not.
         *
         * <p>This needs to be enabled to use</p>
         *
         * @param enabled {@code true} when such a watermark image is requested.
         */
        public Builder setWatermarkEnabled(boolean enabled) {
            mBundle.putBoolean(WATERMARK_ENABLED, enabled);
            return this;
        }

        /**
         * Sets the {@link android.view.Gravity} flags for the watermark image.
         *
         * <p>{@link android.view.Gravity#CENTER} will be used if not set.</p>
         *
         * @param gravity {@code true} {@link android.view.Gravity} flags to be set.
         */
        public Builder setWatermarkGravity(int gravity) {
            mBundle.putInt(WATERMARK_GRAVITY, gravity);
            return this;
        }

        /**
         * Controls whether {@link MockIme} is allowed to change the behavior based on
         * {@link android.content.res.Configuration#keyboard} and
         * {@link android.content.res.Configuration#hardKeyboardHidden}.
         *
         * <p>Methods in {@link android.inputmethodservice.InputMethodService} such as
         * {@link android.inputmethodservice.InputMethodService#onEvaluateInputViewShown()} and
         * {@link android.inputmethodservice.InputMethodService#onShowInputRequested(int, boolean)}
         * change their behaviors when a hardware keyboard is attached.  This is confusing when
         * writing tests so by default {@link MockIme} tries to cancel those behaviors.  This
         * settings re-enables such a behavior.</p>
         *
         * @param allowed {@code true} when {@link MockIme} is allowed to change the behavior when
         *                a hardware keyboard is attached
         *
         * @see android.inputmethodservice.InputMethodService#onEvaluateInputViewShown()
         * @see android.inputmethodservice.InputMethodService#onShowInputRequested(int, boolean)
         */
        public Builder setHardKeyboardConfigurationBehaviorAllowed(boolean allowed) {
            mBundle.putBoolean(HARD_KEYBOARD_CONFIGURATION_BEHAVIOR_ALLOWED, allowed);
            return this;
        }

        /**
         * Controls whether inline suggestions are enabled for {@link MockIme}. If enabled, a
         * suggestion strip will be rendered at the top of the keyboard.
         *
         * @param enabled {@code true} when {@link MockIme} is enabled to show inline suggestions.
         */
        public Builder setInlineSuggestionsEnabled(boolean enabled) {
            mBundle.putBoolean(INLINE_SUGGESTIONS_ENABLED, enabled);
            return this;
        }

        /**
         * Controls whether inline suggestions are enabled for {@link MockIme}. If enabled, a
         * suggestion strip will be rendered at the top of the keyboard.
         *
         * @param contentDesc content description to be set to the inline suggestion View.
         */
        public Builder setInlineSuggestionViewContentDesc(@NonNull String contentDesc) {
            mBundle.putString(INLINE_SUGGESTION_VIEW_CONTENT_DESC, contentDesc);
            return this;
        }

        /** Sets whether to enable {@link android.os.StrictMode} or not. */
        public Builder setStrictModeEnabled(boolean enabled) {
            mBundle.putBoolean(STRICT_MODE_ENABLED, enabled);
            return this;
        }

        /**
         * Sets whether to verify below {@link android.content.Context} APIs or not:
         * <ul>
         *     <li>{@link android.inputmethodservice.InputMethodService#getDisplay}</li>
         *     <li>{@link android.inputmethodservice.InputMethodService#isUiContext}</li>
         * </ul>
         */
        public Builder setVerifyUiContextApisInOnCreate(boolean enabled) {
            mBundle.putBoolean(VERIFY_CONTEXT_APIS_IN_ON_CREATE, enabled);
            return this;
        }

        /**
         * Sets whether to enable {@link WindowLayoutInfo} callbacks for {@link MockIme}.
         */
        public Builder setWindowLayoutInfoCallbackEnabled(boolean enabled) {
            mBundle.putBoolean(WINDOW_LAYOUT_INFO_CALLBACK_ENABLED, enabled);
            return this;
        }

        /**
         * Sets whether to enable {@link
         * android.inputmethodservice.InputMethodService#onStartConnectionlessStylusHandwriting}.
         */
        public Builder setConnectionlessHandwritingEnabled(boolean enabled) {
            mBundle.putBoolean(CONNECTIONLESS_HANDWRITING_ENABLED, enabled);
            return this;
        }

        /**
         * Sets whether the IME's
         * {@link android.content.pm.ApplicationInfo#isOnBackInvokedCallbackEnabled()}
         * should be set to {@code true}.
         */
        public Builder setOnBackCallbackEnabled(boolean enabled) {
            mBundle.putBoolean(ON_BACK_CALLBACK_ENABLED, enabled);
            return this;
        }

        /** Sets whether or not custom extract view hierarchy should be used. */
        public Builder setCustomExtractTextViewEnabled(boolean enabled) {
            mBundle.putBoolean(USE_CUSTOM_EXTRACT_TEXT_VIEW, enabled);
            return this;
        }
    }
}
