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

package android.view.inputmethod.cts.util;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
import static android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import static org.junit.Assert.fail;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SystemUtil;

import com.google.common.util.concurrent.SettableFuture;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class TestActivity extends Activity {

    public static final String OVERLAY_WINDOW_NAME = "TestActivity.APP_OVERLAY_WINDOW";
    private static final AtomicReference<Function<TestActivity, View>> sInitializer =
            new AtomicReference<>();

    private Function<TestActivity, View> mInitializer = null;

    private static final AtomicReference<SettableFuture<TestActivity>> sFutureRef =
            new AtomicReference<>();
    private static final long WAIT_TIMEOUT_MS = 5000;

    private AtomicBoolean mIgnoreBackKey = new AtomicBoolean();

    private long mOnBackPressedCallCount;

    private TextView mOverlayView;
    private OnBackInvokedCallback mIgnoreBackKeyCallback = () -> {
        // Ignore back.
    };
    private Boolean mIgnoreBackKeyCallbackRegistered = false;

    private static final Starter DEFAULT_STARTER = new Starter();

    /**
     * Controls how {@link #onBackPressed()} behaves.
     *
     * <p>TODO: Use {@link android.app.AppComponentFactory} instead to customise the behavior of
     * {@link TestActivity}.</p>
     *
     * @param ignore {@code true} when {@link TestActivity} should do nothing when
     *               {@link #onBackPressed()} is called
     */
    @AnyThread
    public void setIgnoreBackKey(boolean ignore) {
        mIgnoreBackKey.set(ignore);
        if (ignore) {
            if (!mIgnoreBackKeyCallbackRegistered) {
                getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                        OnBackInvokedDispatcher.PRIORITY_DEFAULT, mIgnoreBackKeyCallback);
                mIgnoreBackKeyCallbackRegistered = true;
            }
        } else {
            if (mIgnoreBackKeyCallbackRegistered) {
                getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(
                        mIgnoreBackKeyCallback);
                mIgnoreBackKeyCallbackRegistered = false;
            }
        }
    }

    @UiThread
    public long getOnBackPressedCallCount() {
        return mOnBackPressedCallCount;
    }

    @Override
    public void onEnterAnimationComplete() {
        super.onEnterAnimationComplete();

        final SettableFuture<TestActivity> future = sFutureRef.getAndSet(null);
        if (future != null) {
            future.set(this);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (mInitializer == null) {
            mInitializer = sInitializer.get();
        }
        // Currently SOFT_INPUT_STATE_UNSPECIFIED isn't appropriate for CTS test because there is no
        // clear spec about how it behaves.  In order to make our tests deterministic, currently we
        // must use SOFT_INPUT_STATE_UNCHANGED.
        // TODO(Bug 77152727): Remove the following code once we define how
        // SOFT_INPUT_STATE_UNSPECIFIED actually behaves.
        setSoftInputState(SOFT_INPUT_STATE_UNCHANGED);
        setContentView(mInitializer.apply(this));
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mOverlayView != null) {
            mOverlayView.getContext()
                    .getSystemService(WindowManager.class).removeView(mOverlayView);
            mOverlayView = null;
        }
        if (mIgnoreBackKeyCallbackRegistered) {
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(mIgnoreBackKeyCallback);
            mIgnoreBackKeyCallbackRegistered = false;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onBackPressed() {
        ++mOnBackPressedCallCount;
        if (mIgnoreBackKey.get()) {
            return;
        }
        super.onBackPressed();
    }

    public void showOverlayWindow() {
        showOverlayWindow(false /* imeFocusable */);
    }
    public void showOverlayWindow(boolean imeFocusable) {
        if (mOverlayView != null) {
            throw new IllegalStateException("can only show one overlay at a time.");
        }
        SystemUtil.runWithShellPermissionIdentity(() -> {
            Context overlayContext = getApplicationContext().createWindowContext(getDisplay(),
                    TYPE_APPLICATION_OVERLAY, null);
            mOverlayView = new TextView(overlayContext);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(MATCH_PARENT,
                    MATCH_PARENT, TYPE_APPLICATION_OVERLAY,
                    imeFocusable ? FLAG_NOT_FOCUSABLE | FLAG_ALT_FOCUSABLE_IM : FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
            params.setTitle(OVERLAY_WINDOW_NAME);
            mOverlayView.setLayoutParams(params);
            mOverlayView.setText("IME CTS TestActivity OverlayView");
            mOverlayView.setBackgroundColor(0x77FFFF00);
            overlayContext.getSystemService(WindowManager.class).addView(mOverlayView, params);
        });
    }

    /**
     * Launches {@link TestActivity} with the given initialization logic for content view.
     *
     * When you need to configure launch options, use {@link Starter} class.
     *
     * <p>As long as you are using {@link androidx.test.runner.AndroidJUnitRunner}, the test
     * runner automatically calls {@link Activity#finish()} for the {@link Activity} launched when
     * the test finished.  You do not need to explicitly call {@link Activity#finish()}.</p>
     *
     * @param activityInitializer initializer to supply {@link View} to be passed to
     *                           {@link Activity#setContentView(View)}
     * @return {@link TestActivity} launched
     */
    public static TestActivity startSync(
            @NonNull Function<TestActivity, View> activityInitializer) {
        return DEFAULT_STARTER.startSync(activityInitializer, TestActivity.class);
    }

    /**
     * Updates {@link WindowManager.LayoutParams#softInputMode}.
     *
     * @param newState One of {@link WindowManager.LayoutParams#SOFT_INPUT_STATE_UNSPECIFIED},
     *                 {@link WindowManager.LayoutParams#SOFT_INPUT_STATE_UNCHANGED},
     *                 {@link WindowManager.LayoutParams#SOFT_INPUT_STATE_HIDDEN},
     *                 {@link WindowManager.LayoutParams#SOFT_INPUT_STATE_ALWAYS_HIDDEN},
     *                 {@link WindowManager.LayoutParams#SOFT_INPUT_STATE_VISIBLE},
     *                 {@link WindowManager.LayoutParams#SOFT_INPUT_STATE_ALWAYS_VISIBLE}
     */
    private void setSoftInputState(int newState) {
        final Window window = getWindow();
        final int currentSoftInputMode = window.getAttributes().softInputMode;
        final int newSoftInputMode =
                (currentSoftInputMode & ~WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE)
                        | newState;
        window.setSoftInputMode(newSoftInputMode);
    }

    /**
     * Starts TestActivity with given options such as windowing mode, launch target display, etc.
     *
     * By default, {@link Intent#FLAG_ACTIVITY_NEW_TASK} and {@link Intent#FLAG_ACTIVITY_CLEAR_TASK}
     * are given to {@link Intent#setFlags(int)}. This can be changed by using some methods.
     */
    public static class Starter {
        private static final int DEFAULT_FLAGS =
                Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK;

        private int mFlags = 0;
        private int mAdditionalFlags = 0;
        private ActivityOptions mOptions = null;
        private boolean mRequireShellPermission = false;

        public Starter() {
        }

        /**
         * Specifies an additional flags to be given to {@link Intent#setFlags(int)}.
         */
        public Starter withAdditionalFlags(int additionalFlags) {
            mAdditionalFlags |= additionalFlags;
            return this;
        }

        /**
         * Specifies {@link android.app.WindowConfiguration.WindowingMode a windowing mode} that the
         * activity is launched in.
         */
        public Starter withWindowingMode(int windowingMode) {
            if (mOptions == null) {
                mOptions = ActivityOptions.makeBasic();
            }
            mOptions.setLaunchWindowingMode(windowingMode);
            return this;
        }

        /**
         * Specifies a target display ID that the activity is launched in.
         */
        public Starter withDisplayId(int displayId) {
            if (mOptions == null) {
                mOptions = ActivityOptions.makeBasic();
            }
            mOptions.setLaunchDisplayId(displayId);
            mRequireShellPermission = true;
            return this;
        }

        /**
         * Uses {@link Intent#FLAG_ACTIVITY_NEW_TASK} and {@link Intent#FLAG_ACTIVITY_NEW_DOCUMENT}
         * for {@link Intent#setFlags(int)}.
         */
        public Starter asNewTask() {
            if (mFlags != 0) {
                throw new IllegalStateException("Conflicting flags are specified.");
            }
            mFlags = Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NEW_DOCUMENT;
            return this;
        }

        /**
         * Uses {@link Intent#FLAG_ACTIVITY_NEW_TASK} and {@link Intent#FLAG_ACTIVITY_MULTIPLE_TASK}
         * for {@link Intent#setFlags(int)}.
         */
        public Starter asMultipleTask() {
            if (mFlags != 0) {
                throw new IllegalStateException("Conflicting flags are specified.");
            }
            mFlags = Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
            return this;
        }

        /**
         * Uses {@link Intent#FLAG_ACTIVITY_NEW_TASK} and {@link Intent#FLAG_ACTIVITY_CLEAR_TOP}
         * for {@link Intent#setFlags(int)}.
         */
        public Starter asSameTaskAndClearTop() {
            if (mFlags != 0) {
                throw new IllegalStateException("Conflicting flags are specified.");
            }
            mFlags = Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP;
            return this;
        }

        /**
         * Launches {@link TestActivity} with the given initialization logic for content view
         * with already specified parameters.
         *
         * <p>As long as you are using {@link androidx.test.runner.AndroidJUnitRunner}, the test
         * runner automatically calls {@link Activity#finish()} for the {@link Activity} launched
         * when the test finished. You do not need to explicitly call {@link Activity#finish()}.</p>
         *
         * @param activityInitializer initializer to supply {@link View} to be passed to
         *                            {@link Activity#setContentView(View)}
         * @param activityClass the target class to start, which extends {@link TestActivity}
         * @return {@link TestActivity} launched
         */
        public TestActivity startSync(@NonNull Function<TestActivity, View> activityInitializer,
                Class<? extends TestActivity> activityClass) {
            final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
            sInitializer.set(activityInitializer);

            if (mFlags == 0) {
                mFlags = DEFAULT_FLAGS;
            }
            final Intent intent = new Intent()
                    .setAction(Intent.ACTION_MAIN)
                    .setClass(instrumentation.getContext(), activityClass)
                    .addFlags(mFlags | mAdditionalFlags);
            final Callable<TestActivity> launcher =
                    () -> (TestActivity) instrumentation.startActivitySync(
                            intent, mOptions == null ? null : mOptions.toBundle());

            try {
                if (mRequireShellPermission) {
                    return SystemUtil.callWithShellPermissionIdentity(launcher);
                } else {
                    return launcher.call();
                }
            } catch (Exception e) {
                fail("Failed to start TestActivity: " + e);
                return null;
            }
        }

        /**
         * Launches {@link TestActivity} from the given source activity with the given
         * initialization logic for content view with already specified parameters.
         *
         * <p>As long as you are using {@link androidx.test.runner.AndroidJUnitRunner}, the test
         * runner automatically calls {@link Activity#finish()} for the {@link Activity} launched
         * when the test finished. You do not need to explicitly call {@link Activity#finish()}.</p>
         *
         * @param fromActivity the source activity requests launching the target
         * @param activityInitializer initializer to supply {@link View} to be passed to
         *                            {@link Activity#setContentView(View)}
         * @param activityClass the target class to start, which extends {@link TestActivity}
         * @return {@link TestActivity} launched
         */
        public TestActivity startSync(@NonNull Activity fromActivity,
                @NonNull Function<TestActivity, View> activityInitializer,
                Class<? extends TestActivity> activityClass) {
            sInitializer.set(activityInitializer);

            if (mFlags == 0) {
                mFlags = DEFAULT_FLAGS;
            }
            final Intent intent = new Intent()
                    .setAction(Intent.ACTION_MAIN)
                    .setClass(fromActivity, activityClass)
                    .addFlags(mFlags | mAdditionalFlags);
            final Callable<TestActivity> launcher = () -> {
                fromActivity.startActivity(intent, mOptions == null ? null : mOptions.toBundle());
                final SettableFuture<TestActivity> future = SettableFuture.create();
                sFutureRef.set(future);
                return future.get(WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            };
            try {
                if (mRequireShellPermission) {
                    return SystemUtil.callWithShellPermissionIdentity(launcher);
                } else {
                    return launcher.call();
                }
            } catch (Exception e) {
                fail("Failed to start TestActivity: " + e);
                return null;
            }
        }
    }
}
