/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.adservices.test.longevity.concurrent;

import android.adservices.test.longevity.concurrent.proto.Configuration.Scenario;
import android.adservices.test.longevity.concurrent.proto.Configuration.Scenario.Journey.ExtraArg;
import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import android.platform.test.longevity.listener.TimeoutTerminator;
import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.internal.builders.IgnoredClassRunner;
import org.junit.runner.Runner;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.Suite;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.RunnerBuilder;

import java.util.List;
import java.util.Map;

/**
 * {@inheritDoc}
 *
 * <p>This class is used for constructing multi-threaded longevity suites that run on an Android
 * device based on a profile.
 */
public class ProfileSuite extends Suite {

    // Profile instance for scheduling tests.
    private final ConcurrentProfile mProfile;
    // Cached {@link TimeoutTerminator} instance.
    private TimeoutTerminator mTimeoutTerminator;
    private final Map<String, String> mArguments;
    private static final String TAG = ProfileSuite.class.getSimpleName();

    /** Called reflectively on classes annotated with {@code @RunWith(ProfileSuite.class)} */
    public ProfileSuite(Class<?> klass, RunnerBuilder builder) throws InitializationError {
        this(
                klass,
                builder,
                InstrumentationRegistry.getInstrumentation(),
                InstrumentationRegistry.getInstrumentation().getContext(),
                InstrumentationRegistry.getArguments());
    }

    /** Used to pass in mock-able Android features for testing. */
    @VisibleForTesting
    public ProfileSuite(
            Class<?> klass,
            RunnerBuilder builder,
            Instrumentation instrumentation,
            Context context,
            Bundle arguments)
            throws InitializationError {
        super(klass, constructClassRunners(klass, builder, arguments));
        mProfile = new ConcurrentProfile(arguments);
        mArguments = SuiteUtils.bundleToMap(arguments);
    }

    /** Constructs the sequence of {@link Runner}s using platform composers. */
    private static List<Runner> constructClassRunners(
            Class<?> suite, RunnerBuilder builder, Bundle args) throws InitializationError {
        validateSuiteAndScenarios(suite, builder);
        SuiteClasses annotation = suite.getAnnotation(SuiteClasses.class);
        return new ConcurrentProfile(args)
                .getRunnerSequence(builder.runners(suite, annotation.value()));
    }

    @Override
    public void run(RunNotifier notifier) {
        // Add the profile listener.
        notifier.addListener(mProfile);

        // Register other listeners and continue with standard longevity run.
        notifier.addListener(getTimeoutTerminator(notifier));
        super.run(notifier);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The "extras" are injected here before the entire child test class is run to ensure that
     * test options passed in as class-level rules still work.
     */
    @Override
    protected void runChild(Runner runner, final RunNotifier notifier) {
        Bundle existingArguments = InstrumentationRegistry.getArguments().deepCopy();
        Bundle modifiedArguments = InstrumentationRegistry.getArguments().deepCopy();
        for (Scenario.Journey journey : mProfile.getCurrentScenario().getJourneysList()) {
            for (ExtraArg argPair : journey.getExtrasList()) {
                if (!argPair.hasKey() || !argPair.hasValue()) {
                    throw new IllegalArgumentException(
                            String.format(
                                    "Each extra arg entry in journey must have both a key and a "
                                            + "value,"
                                            + " but journey is %s.",
                                    journey.getJourney()));
                }
                modifiedArguments.putString(argPair.getKey(), argPair.getValue());
            }
        }
        // Swap the arguments, run the scenario, and then restore arguments.
        try {
            Log.i(
                    TAG,
                    String.format(
                            "Modified arguments: %s",
                            SuiteUtils.bundleToString(modifiedArguments)));
            InstrumentationRegistry.registerInstance(
                    InstrumentationRegistry.getInstrumentation(), modifiedArguments);
            Runner suiteRunner = getSuiteRunner(runner);

            super.runChild(suiteRunner, notifier);
        } finally {
            InstrumentationRegistry.registerInstance(
                    InstrumentationRegistry.getInstrumentation(), existingArguments);
        }
    }

    /**
     * Returns a runner suitable for the schedule that the profile uses.
     *
     * <p>Currently supports {@link ScheduledScenarioRunner} only, but support for more runners will
     * be added as profile features expand.
     */
    protected Runner getSuiteRunner(Runner runner) {
        switch (mProfile.getConfiguration().getSchedule()) {
            case TIMESTAMPED:
                long timeout =
                        mProfile.hasNextScheduledScenario()
                                ? mProfile.getTimeUntilNextScenarioMs()
                                : (getSuiteTimeoutMs() - mProfile.getTimeSinceRunStartedMs());
                return getScheduledRunner(
                        runner,
                        mProfile.getCurrentScenario(),
                        timeout,
                        mProfile.hasNextScheduledScenario());

            case INDEXED: // and
            case SEQUENTIAL:
                // Using Concurrent runner which is suitable for an indexed or sequential profile.
                return runner;
            default:
                throw new IllegalArgumentException(
                        String.format(
                                "Schedule type %s is not yet supported.",
                                mProfile.getConfiguration().getSchedule().name()));
        }
    }

    /**
     * Replaces a runner with {@link ScheduledScenarioRunner} for features specific to scheduled
     * profiles.
     */
    @VisibleForTesting
    protected ScheduledScenarioRunner getScheduledRunner(
            Runner runner, Scenario scenario, long timeout, boolean shouldIdle) {
        try {
            return new ScheduledScenarioRunner(scenario, timeout, shouldIdle);
        } catch (Exception e) {
            throw new RuntimeException(
                    String.format(
                            "Unable to run scenario %s with a scheduled runner.",
                            runner.getDescription().getDisplayName()),
                    e);
        }
    }

    protected static boolean isIgnoredRunner(Runner runner) {
        return runner instanceof IgnoredClassRunner;
    }

    private static void validateSuiteAndScenarios(Class<?> suite, RunnerBuilder builder)
            throws InitializationError {
        SuiteClasses annotation = suite.getAnnotation(SuiteClasses.class);
        if (annotation == null) {
            throw new InitializationError(
                    String.format(
                            "Longevity suite, '%s', must have a SuiteClasses annotation",
                            suite.getName()));
        }
        // Validate that runnable scenarios are passed into the suite.
        for (Class<?> scenario : annotation.value()) {
            Runner runner = null;
            try {
                runner = builder.runnerForClass(scenario);
            } catch (Throwable tr) {
                throw new IllegalStateException(
                        String.format(
                                "Failed to calculate the correct runner for a test class: %s",
                                scenario.getSimpleName()),
                        tr);
            }
            // All scenarios must be annotated with @Scenario.
            if (!scenario.isAnnotationPresent(
                    android.platform.test.scenario.annotation.Scenario.class)) {
                throw new InitializationError(
                        String.format(
                                "%s is not annotated with @Scenario.",
                                runner.getDescription().getDisplayName()));
            }
            // All scenarios must extend BlockJUnit4ClassRunner or be ignored.
            if (!(runner instanceof BlockJUnit4ClassRunner) && !isIgnoredRunner(runner)) {
                throw new InitializationError(
                        String.format(
                                "All runners must extend BlockJUnit4ClassRunner or be ignored."
                                        + " %s:%s doesn't.",
                                runner.getClass(), runner.getDescription().getDisplayName()));
            }
        }
    }

    /** Returns the timeout set on the suite in milliseconds. */
    public long getSuiteTimeoutMs() {
        if (mTimeoutTerminator == null) {
            throw new IllegalStateException("No suite timeout is set. This should never happen.");
        }
        return mTimeoutTerminator.getTotalSuiteTimeoutMs();
    }

    /**
     * Returns the platform-specific {@link TimeoutTerminator} for an Android device.
     *
     * <p>This method will always return the same {@link TimeoutTerminator} instance.
     */
    private TimeoutTerminator getTimeoutTerminator(RunNotifier notifier) {
        if (mTimeoutTerminator == null) {
            mTimeoutTerminator = new TimeoutTerminator(notifier, mArguments);
        }
        return mTimeoutTerminator;
    }
}
