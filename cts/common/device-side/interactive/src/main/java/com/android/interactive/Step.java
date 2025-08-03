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

package com.android.interactive;

import static com.android.bedstead.nene.permissions.CommonPermissions.INTERNAL_SYSTEM_WINDOW;
import static com.android.bedstead.nene.permissions.CommonPermissions.SYSTEM_ALERT_WINDOW;
import static com.android.bedstead.nene.permissions.CommonPermissions.SYSTEM_APPLICATION_OVERLAY;
import static com.android.interactive.Automator.AUTOMATION_FILE;

import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.TextView;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.TestLifecycleListener;
import com.android.bedstead.harrier.exceptions.RestartTestException;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.utils.Poll;
import com.android.interactive.annotations.CacheableStep;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * An atomic manual interaction step.
 *
 * <p>Steps can return data to the test (the return type is {@code E}. {@code Void} should be used
 * for steps with no return value.
 */
public abstract class Step<E> {

    private static final TestLifecycleListener sLifecycleListener =
            new TestLifecycleListener() {
                @Override
                public void testFinished(String testName) {
                    sForceManual.set(false);
                }
            };

    private static final String LOG_TAG = "Interactive.Step";

    // If set to true, we will skip automation for one test run - then reset it to false
    private static final AtomicBoolean sForceManual = new AtomicBoolean(false);
    // TODO: We need to reset mForceManual after the test run

    // We timeout 10 seconds before the infra would timeout
    private static final Duration MAX_STEP_DURATION =
            Duration.ofMillis(
                    Long.parseLong(
                                    TestApis.instrumentation()
                                            .arguments()
                                            .getString("timeout_msec", "600000"))
                            - 10000);

    private static final Automator sAutomator = new Automator(AUTOMATION_FILE);

    private View mInstructionView;
    private Button mCollapseButton;

    private static final WindowManager sWindowManager =
            TestApis.context().instrumentedContext().getSystemService(WindowManager.class);

    private Optional<E> mValue = Optional.empty();
    private boolean mFailed = false;
    // Whether there's a screenshot taken for this step.
    // In case multiple close() calls creating multiple screenshot files.
    private boolean mHasTakenScreenshot = false;

    private static Map<Class<? extends Step<?>>, Object> sStepCache = new HashMap<>();

    /**
     * Executes a step.
     *
     * <p>This will first try to execute the step automatically, falling back to manual interaction
     * if that fails.
     */
    public static <E> E execute(Class<? extends Step<E>> stepClass) throws Exception {
        Step<E> step;
        try {
            step = stepClass.newInstance();
        } catch (InstantiationException | IllegalAccessException e) {
            throw new AssertionError("Error preparing step", e);
        }

        // Check if is cached...
        if (sStepCache.containsKey(stepClass)) {
            return (E) sStepCache.get(stepClass);
        }

        if (!sForceManual.get()
                && TestApis.instrumentation().arguments().getBoolean("ENABLE_AUTOMATION", true)) {
            if (sAutomator.canAutomate(step)) {
                AutomatingStep automatingStep =
                        new AutomatingStep("Automating " + stepClass.getCanonicalName());
                try {
                    automatingStep.interact();

                    E returnValue = sAutomator.automate(step);

                    // If it reaches this point then it has passed
                    Log.i(LOG_TAG, "Succeeded with automatic resolution of " + step);

                    boolean stepIsCacheable =
                            stepClass.getAnnotationsByType(CacheableStep.class).length > 0;
                    if (stepIsCacheable) {
                        sStepCache.put(stepClass, returnValue);
                    }
                    return returnValue;
                } catch (Exception t) {
                    Log.e(LOG_TAG, "Error attempting automation of " + step, t);

                    if (TestApis.instrumentation().arguments().getBoolean("ENABLE_MANUAL", false)) {
                        AutomatingFailedStep automatingFailedStep =
                                new AutomatingFailedStep(
                                        "Automation "
                                                + stepClass.getCanonicalName()
                                                + " Failed due to "
                                                + t.toString());
                        automatingFailedStep.interact();

                        Integer value =
                                Poll.forValue("value", automatingFailedStep::getValue)
                                        .toMeet(Optional::isPresent)
                                        .terminalValue((b) -> step.hasFailed())
                                        .errorOnFail(
                                                "Expected value from step. No value provided or"
                                                        + " step failed.")
                                        .timeout(MAX_STEP_DURATION)
                                        .await()
                                        .get();

                        if (value == AutomatingFailedStep.FAIL) {
                            throw (t);
                        } else if (value == AutomatingFailedStep.CONTINUE_MANUALLY) {
                            // Do nothing - we will fall through to the manual resolution
                        } else if (value == AutomatingFailedStep.RETRY) {
                            return Step.execute(stepClass);
                        } else if (value == AutomatingFailedStep.RESTART) {
                            throw new RestartTestException("Retrying after automatic failure");
                        } else if (value == AutomatingFailedStep.RESTART_MANUALLY) {
                            sForceManual.set(true);
                            BedsteadJUnit4.addLifecycleListener(sLifecycleListener);
                            throw new RestartTestException(
                                    "Restarting manually after automatic failure");
                        }
                    } else {
                        throw (t);
                    }
                } finally {
                    automatingStep.close();
                }
            } else {
                Log.i(LOG_TAG, "No automation for " + step);
            }
        }

        if (TestApis.instrumentation().arguments().getBoolean("ENABLE_MANUAL", false)) {
            step.interact();

            // Wait until we've reached a valid ending point
            try {
                Optional<E> valueOptional =
                        Poll.forValue("value", step::getValue)
                                .toMeet(Optional::isPresent)
                                .terminalValue((b) -> step.hasFailed())
                                .timeout(MAX_STEP_DURATION)
                                .await();

                if (step.hasFailed()) {
                    throw new StepFailedException(stepClass);
                }
                if (!valueOptional.isPresent()) {
                    throw new StepTimeoutException(stepClass);
                }

                E value = valueOptional.get();

                // After the test has been marked passed, we validate ourselves
                E returnValue =
                        Poll.forValue("validated", () -> step.validate(value))
                                .toMeet(Optional::isPresent)
                                .errorOnFail("Step did not pass validation.")
                                .timeout(MAX_STEP_DURATION)
                                .await()
                                .get();

                boolean stepIsCacheable =
                        stepClass.getAnnotationsByType(CacheableStep.class).length > 0;
                if (stepIsCacheable) {
                    sStepCache.put(stepClass, returnValue);
                }

                return returnValue;
            } finally {
                step.close();
            }
        }
        throw new AssertionError("Could not automatically or manually pass test");
    }

    protected final void pass() {
        try {
            pass((E) Nothing.NOTHING);
        } catch (ClassCastException e) {
            throw new IllegalStateException(
                    "You cannot call pass() for a step which requires a return value. If no return"
                            + " value is required, the step should use Nothing (not Void)");
        }
    }

    protected final void pass(E value) {
        mValue = Optional.of(value);
        close();
    }

    protected final void fail(String reason) {
        mFailed = true; // TODO: Use reason
        close();
    }

    /** Returns present if the manual step has concluded successfully. */
    public Optional<E> getValue() {
        return mValue;
    }

    /** Returns true if the manual step has failed. */
    public boolean hasFailed() {
        return mFailed;
    }

    /** Adds a button to the interaction prompt. */
    protected void addButton(String title, Runnable onClick) {
        // Push to UI thread to avoid animation issues when adding the button
        new Handler(Looper.getMainLooper()).post(() -> {
            Button btn = new Button(TestApis.context().instrumentedContext());
            btn.setText(title);
            btn.setOnClickListener(v -> onClick.run());

            GridLayout layout = mInstructionView.findViewById(R.id.buttons);
            layout.addView(btn);
        });
    }

    /**
     * Adds small button with a single up/down arrow, used for moving the text box to the bottom of
     * the screen in case it covers some critical area of the app
     */
    protected void addSwapButton() {
        // Push to UI thread to avoid animation issues when adding the button
        new Handler(Looper.getMainLooper()).post(() -> {
            Button btn = new Button(TestApis.context().instrumentedContext());
            // up/down arrow
            btn.setText("\u21F5");
            btn.setOnClickListener(v -> swap());

            GridLayout layout = mInstructionView.findViewById(R.id.buttons);
            layout.addView(btn);
        });
    }

    /**
     * Adds a small button that allows users to collapse the instructions.
     */
    protected void addCollapseInstructionsButton() {
        mCollapseButton = new Button(TestApis.context().instrumentedContext());
        mCollapseButton.setText("\u21F1");
        mCollapseButton.setOnClickListener(v -> collapse());
        GridLayout layout = mInstructionView.findViewById(R.id.buttons);
        layout.addView(mCollapseButton);
    }

    private void collapse() {
        TextView instructionsTextView = mInstructionView.findViewById(R.id.text);
        if (instructionsTextView.getVisibility() != View.GONE) {
            instructionsTextView.setVisibility(View.GONE);
            mCollapseButton.setText("\u21F2");
        } else {
            instructionsTextView.setVisibility(View.VISIBLE);
            mCollapseButton.setText("\u21F1");
        }
    }

    /**
     * Adds a button to immediately mark the test as failed and request the tester to provide the
     * reason for failure.
     */
    protected void addFailButton() {
        addButton("Fail", () -> mFailed = true);
    }

    /**
     * Shows the prompt with the given instruction.
     *
     * <p>This should be called before any other methods on this class.
     */
    protected void show(String instruction) {
        mInstructionView =
                LayoutInflater.from(TestApis.context().instrumentationContext())
                        .inflate(R.layout.instruction, null);

        TextView text = mInstructionView.findViewById(R.id.text);
        text.setText(instruction);

        WindowManager.LayoutParams params =
                new WindowManager.LayoutParams(
                        WindowManager.LayoutParams.MATCH_PARENT,
                        WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP; // TMP
        params.x = 0;
        params.y = 0;

        TestApis.context()
                .instrumentationContext()
                .getMainExecutor()
                .execute(
                        () -> {
                            try (PermissionContext p =
                                    TestApis.permissions()
                                            .withPermission(
                                                    SYSTEM_ALERT_WINDOW,
                                                    SYSTEM_APPLICATION_OVERLAY,
                                                    INTERNAL_SYSTEM_WINDOW)) {
                                params.setSystemApplicationOverlay(true);
                                params.privateFlags =
                                        WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS;
                                sWindowManager.addView(mInstructionView, params);
                            }
                        });
    }

    /** Swaps the prompt from the top to the bottom of the user screen */
    protected void swap() {
        WindowManager.LayoutParams params =
                (WindowManager.LayoutParams) mInstructionView.getLayoutParams();
        if (params.gravity == Gravity.TOP) {
            params.gravity = Gravity.BOTTOM;
        } else {
            params.gravity = Gravity.TOP;
        }
        sWindowManager.updateViewLayout(mInstructionView, params);
    }

    /**
     * Closes the step, takes a screenshot of the device if the feature is enabled, and removes the
     * instruction view if it's still there.
     */
    protected void close() {
        if (!mHasTakenScreenshot
                && TestApis.instrumentation().arguments().getBoolean("TAKE_SCREENSHOT", false)) {
            mHasTakenScreenshot = true;
            ScreenshotUtil.captureScreenshot(getClass().getCanonicalName());
        }
        if (mInstructionView != null) {
            TestApis.context()
                    .instrumentationContext()
                    .getMainExecutor()
                    .execute(
                            () -> {
                                try {
                                    sWindowManager.removeViewImmediate(mInstructionView);
                                    mInstructionView = null;
                                } catch (IllegalArgumentException e) {
                                    // This can happen if the view is no longer attached
                                    Log.i(LOG_TAG, "Error removing instruction view", e);
                                }
                            });
        }
    }

    /** Executes the manual step. */
    public abstract void interact();

    /**
     * Validate that the step has been complete.
     *
     * <p>This implementation must apply to all Android devices.
     */
    public Optional<E> validate(E value) {
        // By default there is no validation
        return Optional.of(value);
    }
}
