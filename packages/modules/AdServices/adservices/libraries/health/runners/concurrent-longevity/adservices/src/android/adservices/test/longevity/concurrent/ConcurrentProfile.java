/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.google.common.base.Strings.isNullOrEmpty;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

import android.adservices.test.longevity.concurrent.proto.Configuration;
import android.adservices.test.longevity.concurrent.proto.Configuration.Scenario;
import android.adservices.test.longevity.concurrent.proto.Configuration.Scenario.Journey;
import android.adservices.test.longevity.concurrent.proto.Configuration.Scenario.Journey.ExtraArg;
import android.adservices.test.longevity.concurrent.proto.Configuration.Schedule;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.Runner;
import org.junit.runner.notification.RunListener;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.TestClass;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.function.Function;

/**
 * A concurrent profile composer for device-side testing.
 *
 * <p>Parses the Configuration textproto and validates it's arguments. Sorts the scenarios based on
 * index, timestamp.
 *
 * <p>Extends the {@link RunListener} class which updates {@code mScenarioIndex} when the test is
 * finished or ignored.
 *
 * <p>ProfileSuite class instantiates this to get the current scenario.
 */
public final class ConcurrentProfile extends RunListener {
    @VisibleForTesting private static final String PROFILE_OPTION_NAME = "concurrent_profile";
    private static final String PROFILE_EXTENSION = ".pb";

    private static final String TAG = ConcurrentProfile.class.getSimpleName();

    // Parser for parsing "at" timestamps in profiles.
    private final SimpleDateFormat mTimestampFormatter = new SimpleDateFormat("HH:mm:ss", Locale.US);

    // Keeps track of the current scenario being run; updated at the end of a scenario.
    private int mScenarioIndex;
    // A list of scenarios in the order that they will be run.
    private List<Scenario> mOrderedScenariosList;
    // Timestamp when the test run starts, defaults to time when the ProfileBase object is
    // constructed. Can be overridden by {@link setTestRunStartTimeMs}.
    private long mRunStartTimeMs = SystemClock.elapsedRealtime();
    // The profile configuration.
    private final Configuration mConfiguration;
    // The timestamp of the first scenario in milliseconds. All scenarios will be scheduled relative
    // to this timestamp.
    private long mFirstScenarioTimestampMs;

    public ConcurrentProfile(Bundle args) {
        // Set the timestamp parser to UTC to get test timestamps as "time elapsed since zero".
        mTimestampFormatter.setTimeZone(TimeZone.getTimeZone("UTC"));

        // Load configuration from arguments and stored the list of scenarios sorted according to
        // their timestamps.
        mConfiguration = getConfigurationArgument(args);

        if (mConfiguration == null) {
            throw new IllegalArgumentException("Configuration should not be null");
        }
        List<Scenario> orderedScenarios = new ArrayList<>(mConfiguration.getScenariosList());
        if (orderedScenarios.isEmpty()) {
            throw new IllegalArgumentException("Profile must have at least one scenario.");
        }
        if (mConfiguration.getSchedule().equals(Schedule.TIMESTAMPED)) {
            if (mConfiguration.getRepetitions() != 1) {
                throw new IllegalArgumentException(
                        "Repetitions param not supported for TIMESTAMPED scheduler");
            }
            Collections.sort(orderedScenarios, new ScenarioTimestampComparator());
            try {
                mFirstScenarioTimestampMs =
                        mTimestampFormatter.parse(orderedScenarios.get(0).getAt()).getTime();
            } catch (ParseException e) {
                throw new IllegalArgumentException(
                        String.format(
                                "Cannot parse the timestamp %s of the first scenario.",
                                orderedScenarios.get(0).getAt()),
                        e);
            }
        } else if (mConfiguration.getSchedule().equals(Schedule.INDEXED)) {
            Collections.sort(orderedScenarios, new ScenarioIndexedComparator());
        } else if (mConfiguration.getSchedule().equals(Schedule.SEQUENTIAL)) {
            // Do nothing. Rely on the natural ordering specified in the profile.
        } else {
            throw new UnsupportedOperationException(
                    "Only scheduled profiles are currently supported.");
        }

        mOrderedScenariosList =
                new ArrayList<>(orderedScenarios.size() * mConfiguration.getRepetitions());

        // Repeat the entire profile based repetitions field in the config.
        for (int i = 0; i < mConfiguration.getRepetitions(); i++) {
            mOrderedScenariosList.addAll(orderedScenarios);
        }
    }

    @Override
    public void testRunStarted(Description description) {
        mRunStartTimeMs = SystemClock.elapsedRealtime();
    }

    @Override
    public void testFinished(Description description) {
        // Increments the index to move onto the next scenario.
        mScenarioIndex += 1;
    }

    @Override
    public void testIgnored(Description description) {
        // Increments the index to move onto the next scenario.
        mScenarioIndex += 1;
    }

    /**
     * Returns {@code true} if there is a next scheduled scenario to run. If no profile is supplied,
     * return {@code false}.
     */
    public boolean hasNextScheduledScenario() {
        return (mOrderedScenariosList != null)
                && (mScenarioIndex < mOrderedScenariosList.size() - 1);
    }

    /** Returns time in milliseconds until the next scenario. */
    public long getTimeUntilNextScenarioMs() {
        Scenario nextScenario = mOrderedScenariosList.get(mScenarioIndex + 1);
        if (nextScenario.hasAt()) {
            try {
                // Calibrate the start time against the first scenario's timestamp.
                long startTimeMs =
                        mTimestampFormatter.parse(nextScenario.getAt()).getTime()
                                - mFirstScenarioTimestampMs;
                // Time in milliseconds from the start of the test run to the current point in time.
                long currentTimeMs = getTimeSinceRunStartedMs();
                // If the next test should not start yet, sleep until its start time. Otherwise,
                // start it immediately.
                if (startTimeMs > currentTimeMs) {
                    return startTimeMs - currentTimeMs;
                }
            } catch (ParseException e) {
                throw new IllegalArgumentException(
                        String.format(
                                "Timestamp %s from scenario could not be parsed",
                                nextScenario.getAt()));
            }
        }
        // For non-scheduled profiles (not a priority at this point), simply return 0.
        return 0L;
    }

    /** Return time in milliseconds since the test run started. */
    public long getTimeSinceRunStartedMs() {
        return SystemClock.elapsedRealtime() - mRunStartTimeMs;
    }

    /** Returns the Scenario object for the current scenario. */
    public Scenario getCurrentScenario() {
        return mOrderedScenariosList.get(mScenarioIndex);
    }

    /** Returns the profile configuration. */
    public Configuration getConfiguration() {
        return mConfiguration;
    }

    /*
     * Parses the arguments, reads the configuration file and returns the Configuration object.
     *
     *<p> If no profile option is found in the arguments, function should return null, in which case
     * the input sequence is returned without modification. Otherwise, function should parse the
     * profile according to the supplied argument and return the Configuration object or throw an
     * exception if the file is not available or cannot be parsed.
     *
     * <p> The configuration should be passed as either the name of a configuration bundled into
     * the APK
     * or a path to the configuration file.
     *
     */
    private Configuration getConfigurationArgument(Bundle args) {
        // profileValue is either the name of a profile bundled with an APK or a path to a
        // profile configuration file.
        String profileValue = args.getString(PROFILE_OPTION_NAME, "");
        if (profileValue.isEmpty()) {
            return null;
        }

        AssetManager manager =
                InstrumentationRegistry.getInstrumentation().getContext().getAssets();
        String profileName = profileValue + PROFILE_EXTENSION;

        // Look inside the APK assets for the profile; if this fails, try
        // using the profile argument as a path to a configuration file.
        try (InputStream configStream = manager.open(profileName)) {
            return parseConfiguration(configStream, profileValue);
        } catch (IOException e) {
            // Try using the profile argument it as a path to a configuration file.
            File configFile = new File(profileValue);
            if (!configFile.exists()) {
                throw new IllegalArgumentException(
                        String.format("Profile %s does not exist.", profileValue));
            }
            try (InputStream configStream = new FileInputStream(configFile)) {
                return parseConfiguration(configStream, profileValue);
            } catch (IOException f) {
                throw new IllegalArgumentException(
                        String.format("Profile %s cannot be opened.", profileValue));
            }
        }
    }

    private Configuration parseConfiguration(InputStream configStream, String profileValue) {
        try {
            // Parse the configuration from its input stream and return it.
            return Configuration.parseFrom(configStream);
        } catch (IOException e) {
            throw new IllegalArgumentException(
                    String.format("Cannot parse profile %s.", profileValue));
        }
    }

    public List<Runner> getRunnerSequence(List<Runner> input) throws InitializationError {
        validateConfig(input);
        return getTestSequenceFromConfiguration();
    }

    /**
     * Creates a runner for each scenario and returns the list of scenarios to be run.
     *
     * @throws InitializationError {@link ConcurrentRunner} construction fails.
     */
    protected List<Runner> getTestSequenceFromConfiguration() throws InitializationError {
        List<Runner> result = new ArrayList<>();
        for (int i = 0; i < mOrderedScenariosList.size(); i++) {
            Scenario scenario = mOrderedScenariosList.get(i);
            String scheduleIdx = scenario.hasAt() ? scenario.getAt() : String.valueOf(i + 1);
            result.add(new ConcurrentRunner(scenario, scheduleIdx));
        }
        return result;
    }

    private void validateConfig(List<Runner> input) {
        Map<String, Runner> nameToRunner =
                input.stream()
                        .collect(
                                toMap(
                                        r -> r.getDescription().getDisplayName(),
                                        Function.identity()));
        Log.i(
                TAG,
                String.format(
                        "Available journeys: %s",
                        nameToRunner.keySet().stream().collect(joining(", "))));
        for (Scenario scenario : mOrderedScenariosList) {
            if (scenario.getJourneysList().size() == 0) {
                throw new IllegalArgumentException(
                        String.format("Scenario has 0 journeys present"));
            }

            // Validates that there are no conflicting extra args present in a scenario as journeys
            // within a scenario are concurrently.
            validateExtraArgs(scenario);

            for (Journey journey : scenario.getJourneysList()) {
                if (!nameToRunner.containsKey(journey.getJourney())) {
                    String errorFmtMessage =
                            "Journey %s in profile not found. "
                                    + "Check logcat to see available journeys.";
                    throw new IllegalArgumentException(
                            String.format(errorFmtMessage, journey.getJourney()));
                }
                Runner runner = nameToRunner.get(journey.getJourney());
                validateMethodName(((BlockJUnit4ClassRunner) runner).getTestClass(), journey);
            }
        }
        // TODO (b/297108433) : Add logic to validate flags and no conflicting flags are set for a
        //  particular scenario.
    }

    /**
     * Validates that {@link TestClass} has {@link Test} annotated method present. If journey does
     * not have a method name, we check that {@link TestClass} has only one {@link Test} annotated
     * method. If journey has a method name present, we check that {@link TestClass} has the {@link
     * Test} annotated method.
     *
     * @throws IllegalArgumentException if validations fails.
     */
    private void validateMethodName(TestClass testClass, Journey journey) {
        List<FrameworkMethod> methods = testClass.getAnnotatedMethods(Test.class);
        if (methods.size() == 0) {
            throw new IllegalArgumentException(
                    String.format(
                            "No @Test annotated method present in the class: %s",
                            journey.getJourney()));
        }
        if (!journey.hasMethodName()) {
            if (methods.size() != 1) {
                throw new IllegalArgumentException(
                        String.format(
                                "More than 1 @Test annotated method present in the test class: %s."
                                        + " Please specify the method name in the config.",
                                journey.getJourney()));
            }
            return;
        }

        for (FrameworkMethod method : testClass.getAnnotatedMethods(Test.class)) {
            if (method.getName().equals(journey.getMethodName())) {
                return;
            }
        }
        throw new IllegalArgumentException(
                String.format(
                        "Specified method name: %s not found in the test class: %s ",
                        journey.getMethodName(), journey.getJourney()));
    }

    /**
     * Validates that repeated field {@link ExtraArg} which contains key,value for each journey does
     * not have any conflicting {@link ExtraArg} with other journey for the scenario.
     *
     * @throws IllegalArgumentException if the validation fails.
     */
    private void validateExtraArgs(Scenario scenario) {
        Map<String, String> argMap = new HashMap<>();
        for (Journey journey : scenario.getJourneysList()) {
            for (ExtraArg extraArg : journey.getExtrasList()) {
                if (isNullOrEmpty(extraArg.getKey())) {
                    throw new IllegalArgumentException(
                            String.format(
                                    "Extra Arg Key is either null or empty for journey: %s",
                                    journey.getJourney()));
                }

                String key = extraArg.getKey();
                String value = extraArg.getValue();
                if (argMap.containsKey(key) && !argMap.get(key).equals(value)) {
                    throw new IllegalArgumentException(
                            String.format(
                                    "Conflicting extraArg values present for a scenario: %s",
                                    journey.getJourney()));
                }
                argMap.put(key, value);
            }
        }
    }

    // Comparator for sorting timestamped CUJs.
    private static class ScenarioTimestampComparator implements Comparator<Scenario> {
        @Override
        public int compare(Scenario s1, Scenario s2) {
            if (!(s1.hasAt() && s2.hasAt())) {
                throw new IllegalArgumentException(
                        "Scenarios in scheduled profiles must have timestamps.");
            }
            return s1.getAt().compareTo(s2.getAt());
        }
    }

    // Comparator for sorting indexed CUJs.
    private static class ScenarioIndexedComparator implements Comparator<Scenario> {
        @Override
        public int compare(Scenario s1, Scenario s2) {
            if (!(s1.hasIndex() && s2.hasIndex())) {
                throw new IllegalArgumentException(
                        "Scenarios in indexed profiles must have indexes.");
            }
            return Integer.compare(s1.getIndex(), s2.getIndex());
        }
    }
}
