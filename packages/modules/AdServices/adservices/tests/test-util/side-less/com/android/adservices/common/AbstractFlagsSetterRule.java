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
package com.android.adservices.common;

import com.android.adservices.common.DeviceConfigHelper.SyncDisabledModeForTest;
import com.android.adservices.common.Logger.RealLogger;
import com.android.adservices.common.NameValuePair.Matcher;
import com.android.adservices.common.annotations.SetDoubleFlag;
import com.android.adservices.common.annotations.SetDoubleFlags;
import com.android.adservices.common.annotations.SetFlagDisabled;
import com.android.adservices.common.annotations.SetFlagEnabled;
import com.android.adservices.common.annotations.SetFlagsDisabled;
import com.android.adservices.common.annotations.SetFlagsEnabled;
import com.android.adservices.common.annotations.SetFloatFlag;
import com.android.adservices.common.annotations.SetFloatFlags;
import com.android.adservices.common.annotations.SetIntegerFlag;
import com.android.adservices.common.annotations.SetIntegerFlags;
import com.android.adservices.common.annotations.SetLongFlag;
import com.android.adservices.common.annotations.SetLongFlags;
import com.android.adservices.common.annotations.SetStringFlag;
import com.android.adservices.common.annotations.SetStringFlags;

import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import org.junit.AssumptionViolatedException;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

// TODO(b/294423183): add unit tests for the most relevant / less repetitive stuff (don't need to
// test all setters / getters, for example)
/**
 * Rule used to properly set "Android flags"- it will take care of permissions, restoring values at
 * the end, setting {@link android.provider.DeviceConfig} or {@link android.os.SystemProperties},
 * etc...
 */
abstract class AbstractFlagsSetterRule<T extends AbstractFlagsSetterRule<T>> implements TestRule {

    protected static final String SYSTEM_PROPERTY_FOR_LOGCAT_TAGS_PREFIX = "log.tag.";

    protected static final String LOGCAT_LEVEL_VERBOSE = "VERBOSE";

    private final String mDeviceConfigNamespace;
    private final DeviceConfigHelper mDeviceConfig;
    // TODO(b/328101177): move system properties to its own rule
    private final String mSystemPropertiesPrefix;
    private final SystemPropertiesHelper mSystemProperties;

    // Cache methods that were called before the test started, so the rule can be
    // instantiated using a builder-like approach.
    private final List<Command> mInitialCommands = new ArrayList<>();

    // Name of flags that were changed by the test
    private final Set<String> mChangedFlags = new LinkedHashSet<>();
    // Name of system properties that were changed by the test
    private final Set<String> mChangedSystemProperties = new LinkedHashSet<>();

    private final List<NameValuePair> mPreTestFlags = new ArrayList<>();
    private final List<NameValuePair> mPreTestSystemProperties = new ArrayList<>();

    private final SyncDisabledModeForTest mPreviousSyncDisabledModeForTest;

    private boolean mIsRunning;
    private boolean mFlagsClearedByTest;

    protected final Logger mLog;

    protected AbstractFlagsSetterRule(
            RealLogger logger,
            String deviceConfigNamespace,
            String systemPropertiesPrefix,
            DeviceConfigHelper.InterfaceFactory deviceConfigInterfaceFactory,
            SystemPropertiesHelper.Interface systemPropertiesInterface) {
        mLog = new Logger(Objects.requireNonNull(logger), "FlagsSetterRule");
        mDeviceConfigNamespace = Objects.requireNonNull(deviceConfigNamespace);
        mSystemPropertiesPrefix = Objects.requireNonNull(systemPropertiesPrefix);
        mDeviceConfig =
                new DeviceConfigHelper(deviceConfigInterfaceFactory, deviceConfigNamespace, logger);
        mSystemProperties = new SystemPropertiesHelper(systemPropertiesInterface, logger);
        // TODO(b/294423183): ideally the current mode should be returned by
        // setSyncDisabledMode(),
        // but unfortunately getting the current mode is not straightforward due to
        // different
        // behaviors:
        // - T+ provides get_sync_disabled_for_tests
        // - S provides is_sync_disabled_for_tests
        // - R doesn't provide anything
        mPreviousSyncDisabledModeForTest = SyncDisabledModeForTest.NONE;
        // Must set right away to avoid race conditions (for example, backend setting flags before
        // apply() is called)
        setSyncDisabledMode(SyncDisabledModeForTest.PERSISTENT);

        mLog.v(
                "Constructor: mDeviceConfigNamespace=%s,"
                        + " mSystemPropertiesPrefix=%s,mDeviceConfig=%s, mSystemProperties=%s,"
                        + " mPreviousSyncDisabledModeForTest=%s",
                mDeviceConfigNamespace,
                mSystemPropertiesPrefix,
                mDeviceConfig,
                mSystemProperties,
                mPreviousSyncDisabledModeForTest);
    }

    // TODO(b/294423183): should not be necessary, but integrated with setLogcatTag()
    protected Matcher getSystemPropertiesMatcher() {
        return (prop) ->
                prop.name.startsWith(mSystemPropertiesPrefix)
                        || prop.name.startsWith(SYSTEM_PROPERTY_FOR_LOGCAT_TAGS_PREFIX);
    }

    @Override
    public final Statement apply(Statement base, Description description) {
        String testName = description.getDisplayName();
        Matcher systemPropertiesMatcher = getSystemPropertiesMatcher();
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                mIsRunning = true;
                List<Throwable> cleanUpErrors = new ArrayList<>();

                // TODO(b/294423183): ideally should be "setupErrors", but it's not used yet (other
                // than logging), so it doesn't matter
                runSafely(cleanUpErrors, () -> mPreTestFlags.addAll(mDeviceConfig.getAll()));
                runSafely(
                        cleanUpErrors,
                        () ->
                                mPreTestSystemProperties.addAll(
                                        mSystemProperties.getAll(systemPropertiesMatcher)));

                setAnnotatedFlags(description);
                runInitialCommands(testName);
                List<NameValuePair> postTestFlags = new ArrayList<>();
                List<NameValuePair> postTestSystemProperties = new ArrayList<>();
                Throwable testError = null;

                try {
                    base.evaluate();
                } catch (Throwable t) {
                    testError = t;
                    runSafely(cleanUpErrors, () -> postTestFlags.addAll(mDeviceConfig.getAll()));
                    runSafely(
                            cleanUpErrors,
                            () ->
                                    postTestSystemProperties.addAll(
                                            mSystemProperties.getAll(systemPropertiesMatcher)));
                } finally {
                    runSafely(cleanUpErrors, () -> resetFlags(testName));
                    runSafely(cleanUpErrors, () -> resetSystemProperties(testName));
                    runSafely(
                            cleanUpErrors,
                            () -> setSyncDisabledMode(mPreviousSyncDisabledModeForTest));
                    mIsRunning = false;
                }
                // TODO(b/294423183): ideally it should throw an exception if cleanUpErrors is not
                // empty, but it's better to wait until this class is unit tested to do so (for now,
                // it's just logging it)
                throwIfNecessary(testName, testError, postTestFlags, postTestSystemProperties);
            }
        };
    }

    private void setSyncDisabledMode(SyncDisabledModeForTest mode) {
        runOrCache(
                "setSyncDisabledMode(" + mode + ")", () -> mDeviceConfig.setSyncDisabledMode(mode));
    }

    private void throwIfNecessary(
            String testName,
            @Nullable Throwable testError,
            List<NameValuePair> postTestFlags,
            List<NameValuePair> postTestSystemProperties)
            throws Throwable {
        if (testError == null) {
            mLog.v("Good News, Everyone! %s passed.", testName);
            return;
        }
        if (testError instanceof AssumptionViolatedException) {
            mLog.i("%s is being ignored: %s", testName, testError);
            throw testError;
        }

        StringBuilder dump = new StringBuilder();
        if (mFlagsClearedByTest) {
            dump.append("NOTE: test explicitly cleared all flags.\n");
        }

        logAllAndDumpDiff("flags", dump, mChangedFlags, mPreTestFlags, postTestFlags);
        logAllAndDumpDiff(
                "system properties",
                dump,
                mChangedSystemProperties,
                mPreTestSystemProperties,
                postTestSystemProperties);

        mLog.e("%s failed with %s.\n%s", testName, testError, dump);
        throw new TestFailure(testError, dump);
    }

    private void logAllAndDumpDiff(
            String what,
            StringBuilder dump,
            Set<String> changedNames,
            List<NameValuePair> preTest,
            List<NameValuePair> postTest) {
        // Log all values
        log(preTest, "%s before the test", what);
        log(postTest, "%s after the test", what);

        // Dump only what was change
        appendChanges(dump, what, changedNames, preTest, postTest);
    }

    private void appendChanges(
            StringBuilder dump,
            String what,
            Set<String> changedNames,
            List<NameValuePair> preTest,
            List<NameValuePair> postTest) {
        if (changedNames.isEmpty()) {
            dump.append("Tested didn't change any ").append(what).append('\n');
            return;
        }
        dump.append("Test changed ")
                .append(changedNames.size())
                .append(' ')
                .append(what)
                .append(" (see log for all changes): \n");

        for (String name : changedNames) {
            String before = getValue("before", preTest, name);
            String after = getValue("after", postTest, name);
            dump.append('\t')
                    .append(name)
                    .append(": ")
                    .append(before)
                    .append(", ")
                    .append(after)
                    .append('\n');
        }
    }

    private String getValue(String when, List<NameValuePair> list, String name) {
        for (NameValuePair candidate : list) {
            if (candidate.name.equals(name)) {
                return when + "=" + candidate.value;
            }
        }
        return "(not set " + when + ")";
    }

    /**
     * Dumps all flags using the {@value #TAG} tag.
     *
     * <p>Typically use for temporary debugging purposes like {@code dumpFlags("getFoo(%s)", bar)}.
     */
    @FormatMethod
    public final void dumpFlags(@FormatString String reasonFmt, @Nullable Object... reasonArgs) {
        log(mDeviceConfig.getAll(), "flags (Reason: %s)", String.format(reasonFmt, reasonArgs));
    }

    /**
     * Dumps all system properties using the {@value #TAG} tag.
     *
     * <p>Typically use for temporary debugging purposes like {@code
     * dumpSystemProperties("getFoo(%s)", bar)}.
     */
    @FormatMethod
    public final void dumpSystemProperties(
            @FormatString String reasonFmt, @Nullable Object... reasonArgs) {
        log(
                mSystemProperties.getAll(getSystemPropertiesMatcher()),
                "system properties (Reason: %s)",
                String.format(reasonFmt, reasonArgs));
    }

    @FormatMethod
    private void log(
            List<NameValuePair> values,
            @FormatString String whatFmt,
            @Nullable Object... whatArgs) {
        String what = String.format(whatFmt, whatArgs);
        if (values.isEmpty()) {
            mLog.e("%s: empty", what);
            return;
        }
        mLog.i("Logging name/value of %d %s:", values.size(), what);
        values.forEach(value -> mLog.i("\t%s", value));
    }

    /** Clear all flags from the {@code AdServices} namespace */
    public final T clearFlags() {
        return runOrCache(
                "clearFlags()",
                () -> {
                    mLog.i("Clearing all flags. mIsRunning=%b", mIsRunning);
                    mDeviceConfig.clearFlags();
                    // TODO(b/294423183): ideally we should save the flags and restore - possibly
                    // using DeviceConfig properties - but for now let's just clear it.
                    mFlagsClearedByTest = true;
                });
    }

    /** Sets the flag with the given value. */
    public final T setFlag(String name, boolean value) {
        return setOrCacheFlag(name, Boolean.toString(value));
    }

    /** Sets the flag with the given value. */
    public final T setFlag(String name, int value) {
        return setOrCacheFlag(name, Integer.toString(value));
    }

    /** Sets the flag with the given value. */
    public final T setFlag(String name, long value) {
        return setOrCacheFlag(name, Long.toString(value));
    }

    /** Sets the flag with the given value. */
    public final T setFlag(String name, float value) {
        return setOrCacheFlag(name, Float.toString(value));
    }

    /** Sets the flag with the given value. */
    public final T setFlag(String name, double value) {
        return setOrCacheFlag(name, Double.toString(value));
    }

    /** Sets the flag with the given value. */
    public final T setFlag(String name, String value) {
        return setOrCacheFlag(name, value);
    }

    /** Sets a {@code logcat} tag. */
    public final T setLogcatTag(String tag, String level) {
        setOrCacheLogtagSystemProperty(tag, level);
        return getThis();
    }

    /** Gets the value of the given flag. */
    @Nullable
    public final String getFlag(String flag) {
        return mDeviceConfig.get(flag);
    }

    // TODO(295007931): abstract SDK-related methods in a new SdkLevelHelper and reuse them on
    // SdkLevelSupportRule
    /** Gets the device's SDK level. */
    protected abstract int getDeviceSdk();

    protected boolean isAtLeastR() {
        return getDeviceSdk() >= 30;
    }

    protected boolean isAtLeastS() {
        return getDeviceSdk() >= 31;
    }

    protected boolean isAtLeastT() {
        return getDeviceSdk() > 32;
    }

    // Helper to get a reference to this object, taking care of the generic casting.
    @SuppressWarnings("unchecked")
    protected final T getThis() {
        return (T) this;
    }

    // Set the annotated flags with the specified value for a particular test method.
    protected void setAnnotatedFlags(Description description) {
        List<Annotation> annotations = getAllFlagAnnotations(description);

        // Apply the annotations in the reverse order. First apply from the super classes, test
        // class and then test method. If same annotated flag is present in class and test
        // method, test method takes higher priority.
        for (int i = annotations.size() - 1; i >= 0; i--) {
            Annotation annotation = annotations.get(i);
            if (annotation instanceof SetFlagEnabled) {
                setAnnotatedFlag((SetFlagEnabled) annotation);
            } else if (annotation instanceof SetFlagsEnabled) {
                setAnnotatedFlag((SetFlagsEnabled) annotation);
            } else if (annotation instanceof SetFlagDisabled) {
                setAnnotatedFlag((SetFlagDisabled) annotation);
            } else if (annotation instanceof SetFlagsDisabled) {
                setAnnotatedFlag((SetFlagsDisabled) annotation);
            } else if (annotation instanceof SetIntegerFlag) {
                setAnnotatedFlag((SetIntegerFlag) annotation);
            } else if (annotation instanceof SetIntegerFlags) {
                setAnnotatedFlag((SetIntegerFlags) annotation);
            } else if (annotation instanceof SetLongFlag) {
                setAnnotatedFlag((SetLongFlag) annotation);
            } else if (annotation instanceof SetLongFlags) {
                setAnnotatedFlag((SetLongFlags) annotation);
            } else if (annotation instanceof SetFloatFlag) {
                setAnnotatedFlag((SetFloatFlag) annotation);
            } else if (annotation instanceof SetFloatFlags) {
                setAnnotatedFlag((SetFloatFlags) annotation);
            } else if (annotation instanceof SetDoubleFlag) {
                setAnnotatedFlag((SetDoubleFlag) annotation);
            } else if (annotation instanceof SetDoubleFlags) {
                setAnnotatedFlag((SetDoubleFlags) annotation);
            } else if (annotation instanceof SetStringFlag) {
                setAnnotatedFlag((SetStringFlag) annotation);
            } else if (annotation instanceof SetStringFlags) {
                setAnnotatedFlag((SetStringFlags) annotation);
            }
        }
    }

    private T setOrCacheFlag(String name, String value) {
        return setOrCacheFlag(name, value, /* separator= */ null);
    }

    // TODO(b/294423183): need to add unit test for setters that call this
    protected final T setOrCacheFlag(String name, String value, @Nullable String separator) {
        NameValuePair flag = new NameValuePair(name, value, separator);
        if (!mIsRunning) {
            if (isFlagManagedByRunner(name)) {
                return getThis();
            }
            cacheCommand(new SetFlagCommand(flag));
            return getThis();
        }
        return setFlag(flag);
    }

    // TODO(b/295321663): need to provide a more elegant way to integrate it with the custom runners
    protected boolean isFlagManagedByRunner(String flag) {
        return false;
    }

    private T setFlag(NameValuePair flag) {
        mLog.d("Setting flag: %s", flag);
        if (flag.separator == null) {
            mDeviceConfig.set(flag.name, flag.value);
        } else {
            mDeviceConfig.setWithSeparator(flag.name, flag.value, flag.separator);
        }
        mChangedFlags.add(flag.name);
        return getThis();
    }

    private void resetFlags(String testName) {
        mLog.d("Resetting flags after %s", testName);
        mDeviceConfig.reset();
    }

    public T setSystemProperty(String name, boolean value) {
        return setOrCacheSystemProperty(mSystemPropertiesPrefix + name, Boolean.toString(value));
    }

    private T setOrCacheLogtagSystemProperty(String name, String value) {
        return setOrCacheSystemProperty(SYSTEM_PROPERTY_FOR_LOGCAT_TAGS_PREFIX + name, value);
    }

    private T setOrCacheSystemProperty(String name, String value) {
        NameValuePair systemProperty = new NameValuePair(name, value);
        if (!mIsRunning) {
            cacheCommand(new SetSystemPropertyCommand(systemProperty));
            return getThis();
        }
        return setSystemProperty(systemProperty);
    }

    private T setSystemProperty(NameValuePair systemProperty) {
        mLog.d("Setting system property: %s", systemProperty);
        mSystemProperties.set(systemProperty.name, systemProperty.value);
        mChangedSystemProperties.add(systemProperty.name);
        return getThis();
    }

    private void resetSystemProperties(String testName) {
        mLog.d("Resetting SystemProperties after %s", testName);
        mSystemProperties.reset();
    }

    protected T runOrCache(String description, Runnable r) {
        RunnableCommand command = new RunnableCommand(description, r);
        if (!mIsRunning) {
            cacheCommand(command);
            return getThis();
        }
        command.execute();
        return getThis();
    }

    private void cacheCommand(Command command) {
        if (mIsRunning) {
            throw new IllegalStateException(
                    "Cannot cache " + command + " as test is already running");
        }
        mLog.v("Caching %s as test is not running yet", command);
        mInitialCommands.add(command);
    }

    private void runCommand(String description, Runnable runnable) {
        mLog.v("Running runnable for %s", description);
        runnable.run();
    }

    // TODO(b/294423183): make private once not used by subclass for legacy methods
    protected final void runInitialCommands(String testName) {
        if (mInitialCommands.isEmpty()) {
            mLog.d("Not executing any command before %s", testName);
        } else {
            int size = mInitialCommands.size();
            mLog.d("Executing %d commands before %s", size, testName);
            for (int i = 0; i < mInitialCommands.size(); i++) {
                Command command = mInitialCommands.get(i);
                mLog.v("\t%d: %s", i, command);
                command.execute();
            }
        }
    }

    private void runSafely(List<Throwable> errors, Runnable r) {
        try {
            r.run();
        } catch (Throwable e) {
            mLog.e(e, "runSafely() failed");
            errors.add(e);
        }
    }

    private boolean isFlagAnnotationPresent(Annotation annotation) {
        return (annotation instanceof SetFlagEnabled)
                || (annotation instanceof SetFlagsEnabled)
                || (annotation instanceof SetFlagDisabled)
                || (annotation instanceof SetFlagsDisabled)
                || (annotation instanceof SetIntegerFlag)
                || (annotation instanceof SetIntegerFlags)
                || (annotation instanceof SetLongFlag)
                || (annotation instanceof SetLongFlags)
                || (annotation instanceof SetFloatFlag)
                || (annotation instanceof SetFloatFlags)
                || (annotation instanceof SetDoubleFlag)
                || (annotation instanceof SetDoubleFlags)
                || (annotation instanceof SetStringFlag)
                || (annotation instanceof SetStringFlags);
    }

    private List<Annotation> getAllFlagAnnotations(Description description) {
        // TODO(b/318893752): Move this to a helper function to scan test method, class and
        //  superclasses for annotations.
        List<Annotation> result = new ArrayList<>();
        for (Annotation testMethodAnnotation : description.getAnnotations()) {
            if (isFlagAnnotationPresent(testMethodAnnotation)) {
                result.add(testMethodAnnotation);
            }
        }

        // Get all the flag based annotations from test class and super classes
        Class<?> clazz = description.getTestClass();
        do {
            Annotation[] classAnnotations = clazz.getAnnotations();
            if (classAnnotations != null) {
                for (Annotation annotation : classAnnotations) {
                    if (isFlagAnnotationPresent(annotation)) {
                        result.add(annotation);
                    }
                }
            }
            clazz = clazz.getSuperclass();
        } while (clazz != null);

        return result;
    }

    // Single SetFlagEnabled annotations present
    private void setAnnotatedFlag(SetFlagEnabled annotation) {
        setFlag(annotation.value(), true);
    }

    // Multiple SetFlagEnabled annotations present
    private void setAnnotatedFlag(SetFlagsEnabled repeatedAnnotation) {
        for (SetFlagEnabled annotation : repeatedAnnotation.value()) {
            setAnnotatedFlag(annotation);
        }
    }

    // Single SetFlagDisabled annotations present
    private void setAnnotatedFlag(SetFlagDisabled annotation) {
        setFlag(annotation.value(), false);
    }

    // Multiple SetFlagDisabled annotations present
    private void setAnnotatedFlag(SetFlagsDisabled repeatedAnnotation) {
        for (SetFlagDisabled annotation : repeatedAnnotation.value()) {
            setAnnotatedFlag(annotation);
        }
    }

    // Single SetIntegerFlag annotations present
    private void setAnnotatedFlag(SetIntegerFlag annotation) {
        setFlag(annotation.name(), annotation.value());
    }

    // Multiple SetIntegerFlag annotations present
    private void setAnnotatedFlag(SetIntegerFlags repeatedAnnotation) {
        for (SetIntegerFlag annotation : repeatedAnnotation.value()) {
            setAnnotatedFlag(annotation);
        }
    }

    // Single SetLongFlag annotations present
    private void setAnnotatedFlag(SetLongFlag annotation) {
        setFlag(annotation.name(), annotation.value());
    }

    // Multiple SetLongFlag annotations present
    private void setAnnotatedFlag(SetLongFlags repeatedAnnotation) {
        for (SetLongFlag annotation : repeatedAnnotation.value()) {
            setAnnotatedFlag(annotation);
        }
    }

    // Single SetLongFlag annotations present
    private void setAnnotatedFlag(SetFloatFlag annotation) {
        setFlag(annotation.name(), annotation.value());
    }

    // Multiple SetLongFlag annotations present
    private void setAnnotatedFlag(SetFloatFlags repeatedAnnotation) {
        for (SetFloatFlag annotation : repeatedAnnotation.value()) {
            setAnnotatedFlag(annotation);
        }
    }

    // Single SetDoubleFlag annotations present
    private void setAnnotatedFlag(SetDoubleFlag annotation) {
        setFlag(annotation.name(), annotation.value());
    }

    // Multiple SetDoubleFlag annotations present
    private void setAnnotatedFlag(SetDoubleFlags repeatedAnnotation) {
        for (SetDoubleFlag annotation : repeatedAnnotation.value()) {
            setAnnotatedFlag(annotation);
        }
    }

    // Single SetStringFlag annotations present
    private void setAnnotatedFlag(SetStringFlag annotation) {
        setFlag(annotation.name(), annotation.value());
    }

    // Multiple SetStringFlag annotations present
    private void setAnnotatedFlag(SetStringFlags repeatedAnnotation) {
        for (SetStringFlag annotation : repeatedAnnotation.value()) {
            setAnnotatedFlag(annotation);
        }
    }

    // toString() is overridden to remove the AbstractAdServicesFlagsSetterRule$ from the name
    @SuppressWarnings("OverrideThrowableToString")
    public static final class TestFailure extends Exception {

        private final String mDump;

        TestFailure(Throwable cause, StringBuilder dump) {
            super(
                    "Test failed (see flags / system properties state below the stack trace)",
                    cause,
                    /* enableSuppression= */ false,
                    /* writableStackTrace= */ false);
            mDump = "\n" + dump;
            setStackTrace(cause.getStackTrace());
        }

        @Override
        public void printStackTrace(PrintWriter s) {
            super.printStackTrace(s);
            s.println(mDump);
        }

        @Override
        public void printStackTrace(PrintStream s) {
            super.printStackTrace(s);
            s.println(mDump);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + ": " + getMessage();
        }

        /** Gets the flags / system properties state. */
        public String getFlagsState() {
            return mDump;
        }
    }

    @SuppressWarnings("ClassCanBeStatic") // Subclasses reference enclosing class
    private abstract class Command {
        protected final String mDescription;

        Command(String description) {
            mDescription = description;
        }

        abstract void execute();

        @Override
        public final String toString() {
            return mDescription;
        }
    }

    private final class RunnableCommand extends Command {
        private final Runnable mRunnable;

        RunnableCommand(String description, Runnable runnable) {
            super(description);
            mRunnable = runnable;
        }

        @Override
        void execute() {
            runCommand(mDescription, mRunnable);
        }
    }

    private abstract class SetFlagOrSystemPropertyCommand extends Command {
        protected final NameValuePair mFlagOrSystemProperty;

        SetFlagOrSystemPropertyCommand(String description, NameValuePair flagOrSystemProperty) {
            super(description + "(" + flagOrSystemProperty + ")");
            mFlagOrSystemProperty = flagOrSystemProperty;
        }
    }

    private final class SetFlagCommand extends SetFlagOrSystemPropertyCommand {
        SetFlagCommand(NameValuePair flag) {
            super("SetFlag", flag);
        }

        @Override
        void execute() {
            setFlag(mFlagOrSystemProperty);
        }
    }

    private final class SetSystemPropertyCommand extends SetFlagOrSystemPropertyCommand {
        SetSystemPropertyCommand(NameValuePair flag) {
            super("SetSystemProperty", flag);
        }

        @Override
        void execute() {
            setSystemProperty(mFlagOrSystemProperty);
        }
    }
}
