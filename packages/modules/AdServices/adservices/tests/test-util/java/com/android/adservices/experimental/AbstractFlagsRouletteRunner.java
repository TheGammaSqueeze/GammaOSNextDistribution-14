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
package com.android.adservices.experimental;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import com.android.adservices.common.Logger;
import com.android.adservices.common.Nullable;

import com.google.common.collect.ImmutableList;

import org.junit.AssumptionViolatedException;
import org.junit.runner.Description;
import org.junit.runner.notification.RunNotifier;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

// TODO(b/284971005): move to module-utils
// TODO(b/284971005): rename / make it clear it's for boolean flags (and/or make it genric?)
// TODO(b/284971005): improve javadoc / add  examples
// TODO(b/284971005): add unit tests
/**
 * Test runner used to run tests with all combinations of a set of boolean flags (the {@link
 * #getFlagsRoulette(TestClass, List) "roulette"}).
 *
 * <p>For example, if a test depends on flags {@code [foo, bar]}, the runner will run the tests with
 * 4 combinations:
 *
 * <ol>
 *   <li>foo=false, bar=false
 *   <li>foo=false, bar=true
 *   <li>foo=true, bar=false
 *   <li>foo=true, bar=true
 * </ol>
 *
 * <p>The runner also support test-level optimizations; for example, if a test only needs to run
 * when the {@code foo} flag is on, it can be annotated with the {@link RequiresFlagOn} annotation
 * (and value {@code "foo"}).
 */
public abstract class AbstractFlagsRouletteRunner extends BlockJUnit4ClassRunner {

    // TODO(b/284971005): currently class only support boolean flags - extend it to be more generic?
    private static final boolean[] FLAG_VALUES = {false, true};

    // NOTE: super() calls collectInitializationErrors(), so that method (or methods called by it)
    // will throw a NPE if they access a field that's not initialized yet (that's why this class
    // doesn't provide a mLog but requires a log(), for example)
    private final FlagsManager mFlagsManager;
    private List<String> mFlagsRoulette; // see getFlagsRoulette()
    private List<FrameworkMethod> mModifiedMethods;

    private static final ThreadLocal<FlagsRouletteState> sCurrentRunner = new ThreadLocal<>();

    protected AbstractFlagsRouletteRunner(Class<?> testClass, FlagsManager manager)
            throws InitializationError {
        super(testClass);

        mFlagsManager = Objects.requireNonNull(manager);
        log().i(
                        "AbstractFlagsRouletteRunner(): mFlagsManager=%s, mFlagsRoulette=%s",
                        mFlagsManager, mFlagsRoulette);
    }

    /** Provides the {@link Logger}. */
    protected abstract Logger log();

    /**
     * Defines which flags the runner will set before running the tests.
     *
     * <p>By default, it will try to infer it from the following annotations:
     *
     * <ol>
     *   <li>{@link FlagsProviderClass}
     *   <li>{@link FlagsRoulette}
     * </ol>
     *
     * <p>But sub-classes can override it to simplify the tests (for, example to return a
     * pre-defined combination of flags).
     *
     * @param testClass test being run
     * @param errors used to report errors back to JUNit
     * @return which flags the runner will set before running the tests
     */
    protected String[] getFlagsRoulette(TestClass testClass, List<Throwable> errors) {
        // First try provider...
        FlagsProviderClass flagsProviderClass = testClass.getAnnotation(FlagsProviderClass.class);
        if (flagsProviderClass != null) {
            return getFlagsFromProvider(
                    flagsProviderClass.value(), testClass.getJavaClass(), errors);
        }
        // ...then annotation
        FlagsRoulette annotation = testClass.getAnnotation(FlagsRoulette.class);
        if (annotation != null) {
            String[] flags = annotation.value();
            log().i(
                            "getRouletteFlags(): returning flags from @%s: %s",
                            FlagsRoulette.class, Arrays.toString(flags));
            return flags;
        }
        // ...or fail!
        errors.add(
                new IllegalStateException(
                        "Could not infer roulette flags: "
                                + getClass()
                                + " doesn't override getRouletteFlags() and "
                                + testClass
                                + "is not annotated with @"
                                + FlagsRoulette.class
                                + " or @"
                                + FlagsProvider.class));
        return null;
    }

    /**
     * Override this method and return {@code false} to disable the runner (so it will run every
     * tests "as-is").
     */
    protected boolean isDisabled() {
        return false;
    }

    /**
     * Gets the flag states that are required by a test method, so the method is skipped when the
     * test is run with flags that don't match it.
     *
     * <p>By default it returns the values provided by the {@link RequiresFlag}, but subclasses can
     * override it to use other annotations.
     *
     * @return flag states that are required by a test method or {@code null} if test doesn't have
     *     any flag restriction.
     */
    protected @Nullable FlagState[] getRequiredFlagStates(FrameworkMethod method) {
        // Try individual annotation first
        RequiresFlag singleAnnotation = method.getAnnotation(RequiresFlag.class);
        if (singleAnnotation != null) {
            return new FlagState[] {new FlagState(singleAnnotation)};
        }
        RequiresFlags groupAnnotation = method.getAnnotation(RequiresFlags.class);
        return groupAnnotation == null
                ? null
                : Arrays.stream(groupAnnotation.value())
                        .map((annotation -> new FlagState(annotation)))
                        .toArray(FlagState[]::new);
    }

    // TODO(b/284971005):  this method (which is called by the constructor) is not only collecting
    // errors but also initializing mFlagsRoulette, ideally we should split (and move the roulette
    // initialization to some later stages)
    @Override
    protected final void collectInitializationErrors(List<Throwable> errors) {
        if (isDisabled()) {
            log().v("collectInitializationErrors(): ignoring because isDisabled() returned true");
            super.collectInitializationErrors(errors);
            return;
        }
        TestClass testClass = getTestClass();
        String[] flagsRoulette = getFlagsRoulette(testClass, errors);
        log().v(
                        "collectInitializationErrors(): flags=%s, errors=%s",
                        Arrays.toString(flagsRoulette), errors);
        if (flagsRoulette == null || flagsRoulette.length == 0) {
            // Test run will eventually fail due to errors, but it's better to set mFlags anyways,
            // just in case (to avoid NPE)
            mFlagsRoulette = Collections.emptyList();
            if (errors.isEmpty()) {
                errors.add(
                        new IllegalStateException(
                                getClass() + " overrode getRouletteFlags() but returned null"));
            }
        } else {
            mFlagsRoulette =
                    new ArrayList<>(Arrays.stream(flagsRoulette).collect(Collectors.toSet()));
        }

        super.collectInitializationErrors(errors);
    }

    /**
     * {@inheritDoc}
     *
     * <p>This method will create multiple {@link FlagSnapshotMethod FlagSnapshotMethods} for each
     * "real" method, one with each of the required flag snapshots. For example, if the roulette
     * contains flags {@code FlagA} and {@code FlagB}, it would by default return 4 {@link
     * FlagSnapshotMethod FlagSnapshotMethods} for each method (for the 4 combinations of {@code
     * true} and {@code false}, but it has some additional logic:
     *
     * <ul>
     *   <li>Returns the original methods if the runner is {@link #isDisabled() disabled}.
     *   <li>Ignores snapshots whose flags values would invalidate the flag requirements the method
     *       is annotated with (for example, if method requires that {@code FlagA=true}, then it
     *       would just return 2 snapshots for that method - {@code FlagA=true, FlagB=false} and
     *       {@code FlagA=true, FlagB=true}).
     * </ul>
     */
    @Override
    protected final List<FrameworkMethod> computeTestMethods() {
        if (isDisabled()) {
            log().v("computeTestMethods(): ignoring because isDisabled() returned true");
            return super.computeTestMethods();
        }
        log().d(
                        "computeTestMethods(): mFlags=%s, mModifiedMethods=%s",
                        mFlagsRoulette,
                        mModifiedMethods == null ? "null" : "" + mModifiedMethods.size());

        if (mModifiedMethods != null) {
            // TODO(b/284971005): this method is called twice (by validate(), which is called by the
            // constructor, and by getChildren(), which is called by runChildren()), so we're
            // caching the results because it seems to be unnecessary to re-calculate them. But we
            // need to investigate it further to make sure (for example, it's called by
            // validateInstanceMethods(), which is @Deprecated)
            return mModifiedMethods;
        }
        List<FrameworkMethod> originalMethods = super.computeTestMethods();
        mModifiedMethods = new ArrayList<>();
        List<List<FlagState>> flagsSnapshots = getFlagsSnapshots();
        log().v(
                        "computeTestMethods(): %d flags snapshots: %s",
                        flagsSnapshots.size(), flagsSnapshots);

        for (FrameworkMethod method : originalMethods) {
            FlagState[] requiredFlagStates = getRequiredFlagStates(method);
            for (List<FlagState> snapshot : flagsSnapshots) {
                FrameworkMethod modifiedMethod = null;
                if (requiredFlagStates != null) {
                    modifiedMethod =
                            FlagSnapshotMethod.forRequiredFlagStates(
                                    method, requiredFlagStates, snapshot, log());
                }
                if (modifiedMethod == null) {
                    modifiedMethod =
                            new FlagSnapshotMethod(method, snapshot, /* skippedException= */ null);
                }
                mModifiedMethods.add(modifiedMethod);
            }
        }

        sortMethodsByFlagSnapshotsAndName(mModifiedMethods);

        log().d(
                        "computeTestMethods(): %d originalMethods, %d modifiedMethods",
                        originalMethods.size(), mModifiedMethods.size());
        return mModifiedMethods;
    }

    @Override
    protected void runChild(FrameworkMethod method, RunNotifier notifier) {
        if (isDisabled()) {
            log().v("runChild(): ignoring because isDisabled() returned true");
            super.runChild(method, notifier);
            return;
        }

        FlagSnapshotMethod fsMethod = castFlagSnapshotMethod(method);
        if (fsMethod.mSkippedException != null) {
            Description description = describeChild(method);
            Statement statement =
                    new Statement() {
                        @Override
                        public void evaluate() throws Throwable {
                            throw fsMethod.mSkippedException;
                        }
                    };
            runLeaf(statement, description, notifier);
            return;
        }

        mFlagsManager.setFlags(fsMethod.getFlagsSnapshot());
        sCurrentRunner.set(new FlagsRouletteState(this));
        try {
            super.runChild(method, notifier);
        } finally {
            sCurrentRunner.remove();
            resetFlags();
        }
    }

    @Nullable
    private String[] getFlagsFromProvider(
            Class<? extends FlagsProvider> providerClass,
            Class<?> testClass,
            List<Throwable> errors) {
        FlagsProvider provider = null;
        try {
            provider = providerClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            errors.add(e);
            return null;
        }
        String[] flags = provider.getFlags(testClass);
        if (flags == null || flags.length == 0) {
            errors.add(
                    new IllegalStateException("Provider " + provider + " didn't return any flag"));
            return null;
        }
        log().d("Flags from %s for %s: %s", provider, testClass, Arrays.toString(flags));
        return flags;
    }

    private void sortMethodsByFlagSnapshotsAndName(List<FrameworkMethod> genericMethods) {
        genericMethods.sort(
                (o1, o2) -> {
                    FlagSnapshotMethod method1 = castFlagSnapshotMethod(o1);
                    FlagSnapshotMethod method2 = castFlagSnapshotMethod(o2);
                    List<FlagState> snapshots1 = method1.getFlagsSnapshot();
                    List<FlagState> snapshots2 = method2.getFlagsSnapshot();
                    int size1 = snapshots1.size();
                    int size2 = snapshots2.size();
                    if (size1 != size2) {
                        // Shouldn't happen
                        log().e(
                                        "sortMethodsByFlagSnapshots(): sizes mismatch (%d != %d),"
                                                + " method1=%s, method2=%s",
                                        size1, size2, o1, o2);
                        return size1 - size2;
                    }
                    for (int i = 0; i < size1; i++) {
                        boolean value1 = snapshots1.get(i).value;
                        boolean value2 = snapshots2.get(i).value;
                        if (value1 != value2) {
                            return sortMethodsByFlags(value1, value2);
                        }
                    }
                    // Tests have same flag snapshots - sort by name
                    return sortMethodsByName(
                            method1.getMethod().getName(), method2.getMethod().getName());
                });
    }

    /** Defines the order of the test execution based on flags values. */
    protected int sortMethodsByFlags(boolean flag1, boolean flag2) {
        return flag1 ? 1 : -1;
    }

    /** Defines the order of the test execution based on test name. */
    protected int sortMethodsByName(String name1, String name2) {
        return name1.compareTo(name2);
    }

    private void resetFlags() {
        mFlagsManager.resetFlags();
    }

    // TODO(b/284971005): this is not the most optional way to combine the 2^N values, but it's fine
    // for now
    private ImmutableList<List<FlagState>> getFlagsSnapshots() {
        if (mFlagsRoulette.isEmpty()) {
            return ImmutableList.of();
        }
        List<List<FlagState>> snapshots = new ArrayList<>();
        ArrayList<FlagState> flagsSnapshots = new ArrayList<>();
        getFlagSnapshots(snapshots, flagsSnapshots, 0);
        return ImmutableList.copyOf(snapshots);
    }

    private void getFlagSnapshots(
            List<List<FlagState>> snapshots, List<FlagState> previousSnapshots, int index) {
        String flag = mFlagsRoulette.get(index);
        for (boolean value : FLAG_VALUES) {
            List<FlagState> newSnapshots = new ArrayList<>(previousSnapshots);
            newSnapshots.add(new FlagState(flag, value));
            if (index < mFlagsRoulette.size()) {
                for (int i = index + 1; i < mFlagsRoulette.size(); i++) {
                    getFlagSnapshots(snapshots, newSnapshots, i);
                }
            }
            if (newSnapshots.size() == mFlagsRoulette.size()) {
                log().v("Adding new snapshots: %s", newSnapshots);
                snapshots.add(newSnapshots);
            }
        }
    }

    private static FlagSnapshotMethod castFlagSnapshotMethod(FrameworkMethod method) {
        if (!(method instanceof FlagSnapshotMethod)) {
            throw new IllegalStateException("Invalid method : " + method);
        }
        return (FlagSnapshotMethod) method;
    }

    private static String toString(List<FlagState> snapshots) {
        StringBuilder string = new StringBuilder().append('[');
        int size = snapshots.size();
        for (int i = 0; i < size; i++) {
            FlagState snapshot = snapshots.get(i);
            string.append(snapshot.name).append('=').append(snapshot.value);
            if (i < size - 1) {
                string.append(',');
            }
        }
        return string.append(']').toString();
    }

    // TODO(b/284971005): move to its own class?
    @SuppressWarnings("serial")
    public static final class FlagStateAssumptionViolatedException
            extends AssumptionViolatedException {

        private FlagStateAssumptionViolatedException(FlagState flagState, FrameworkMethod method) {
            super(
                    "Ignoring "
                            + method.getName()
                            + " when turning flag "
                            + flagState.name
                            + (flagState.value ? " ON" : " OFF"));
        }
    }

    // TODO(b/284971005): move to its own class?
    private static final class FlagSnapshotMethod extends FrameworkMethod {
        private final List<FlagState> mFlagsSnapshot;
        private final @Nullable AssumptionViolatedException mSkippedException;

        private FlagSnapshotMethod(
                FrameworkMethod method,
                List<FlagState> flagsSnapshot,
                AssumptionViolatedException skippedException) {
            super(method.getMethod());
            mFlagsSnapshot = flagsSnapshot;
            mSkippedException = skippedException;
        }

        @Nullable
        private static FlagSnapshotMethod forRequiredFlagStates(
                FrameworkMethod method,
                FlagState[] requiredFlagStates,
                List<FlagState> flagsSnapshot,
                Logger log) {
            if (requiredFlagStates == null) {
                return null;
            }
            log.v(
                    "forRequiredFlagStates(): method=%s, requiredFlagStates=%s, flagSnapshots=%s",
                    method.getName(), requiredFlagStates, flagsSnapshot);
            // TODO(b/284971005): optimize it (instead of O(N x M)
            for (FlagState flagSnapshot : flagsSnapshot) {
                for (FlagState requiredFlagState : requiredFlagStates) {
                    if (flagSnapshot.name.equals(requiredFlagState.name)) {
                        log.v(
                                "forRequiredFlags(): found required flag state (%s) on snapshot %s",
                                requiredFlagState, flagSnapshot);
                        boolean requiresOn = requiredFlagState.value;
                        boolean isOn = flagSnapshot.value;
                        if (requiresOn != isOn) {
                            return new FlagSnapshotMethod(
                                    method,
                                    flagsSnapshot,
                                    new FlagStateAssumptionViolatedException(flagSnapshot, method));
                        }
                    }
                }
            }
            log.v(
                    "forRequiredFlagStates(): method %s is annotated with required flag states"
                            + " (%s), but they were not found in the flags snapshot (%s)",
                    method.getName(), requiredFlagStates, flagsSnapshot);
            return null;
        }

        private List<FlagState> getFlagsSnapshot() {
            return mFlagsSnapshot;
        }

        @Override
        public String getName() {
            return super.getName() + AbstractFlagsRouletteRunner.toString(mFlagsSnapshot);
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = super.hashCode();
            result = prime * result + Objects.hash(mFlagsSnapshot);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!super.equals(obj)) return false;
            if (getClass() != obj.getClass()) return false;
            FlagSnapshotMethod other = (FlagSnapshotMethod) obj;
            return Objects.equals(mFlagsSnapshot, other.mFlagsSnapshot);
        }

        @Override
        public String toString() {
            return "["
                    + getClass().getSimpleName()
                    + ": method="
                    + getMethod()
                    + ", flags="
                    + AbstractFlagsRouletteRunner.toString(mFlagsSnapshot)
                    + ']';
        }
    }

    /**
     * Annotation used to indicate that a test method should once be run when the given {@link
     * RequiresFlag#name() name} is set with the given {@link RequiresFlag#value() value}.
     */
    @Retention(RUNTIME)
    @Target({TYPE, METHOD})
    @Repeatable(RequiresFlags.class)
    public static @interface RequiresFlag {
        /** Name of the flag. */
        String name();
        /** Value the flag should have when the test is running */
        boolean value() default true;
    }

    @Retention(RUNTIME)
    @Target({TYPE, METHOD})
    public static @interface RequiresFlags {
        RequiresFlag[] value();
    }

    // TODO(b/284971005): move to its own class?
    /** Provides the flags used by the test. */
    public interface FlagsProvider {
        String[] getFlags(Class<?> testClass);
    }

    /**
     * Annotation used to define which flags will be set when the tests are run - see {@link
     * AbstractFlagsRouletteRunner#getFlagsRoulette(TestClass, List)}.
     */
    @Retention(RUNTIME)
    @Target(TYPE)
    public static @interface FlagsRoulette {
        String[] value();
    }

    /**
     * Annotation used to define which flags will be set when the tests are run - see {@link
     * AbstractFlagsRouletteRunner#getFlagsRoulette(TestClass, List)}.
     */
    @Retention(RUNTIME)
    @Target(TYPE)
    public static @interface FlagsProviderClass {
        Class<? extends FlagsProvider> value();
    }

    /** Abstraction used to manage the values of flags when the tests are run. */
    public interface FlagsManager {

        /** Sets the values of the given flags. */
        void setFlags(Collection<FlagState> flags);

        /** Resets the value of all flags that were set by {@link #setFlags(Collection)}. */
        void resetFlags();
    }

    // TODO(b/284971005): move to its own class?
    /** POJO (Plain-Old Java Object) containing the value of a flag. */
    public static final class FlagState {
        public final String name;
        public final boolean value;

        public FlagState(String name, boolean value) {
            this.name = name;
            this.value = value;
        }

        private FlagState(RequiresFlag annotation) {
            this(annotation.name(), annotation.value());
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, value);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            FlagState other = (FlagState) obj;
            return Objects.equals(name, other.name) && value == other.value;
        }

        @Override
        public String toString() {
            return "[" + name + "=" + value + "]";
        }
    }

    /**
     * Gets the info about the runner running the current test (or {@code null} if no test is
     * running).
     */
    public static @Nullable FlagsRouletteState getFlagsRouletteState() {
        return sCurrentRunner.get();
    }

    /**
     * Info about the runner running the current test.
     *
     * <p>Obtained by {@link #getFlagsRouletteState()}
     */
    public static final class FlagsRouletteState {
        public final String runnerName;
        public final List<String> flagNames;

        private FlagsRouletteState(AbstractFlagsRouletteRunner runner) {
            runnerName = runner.getClass().getSimpleName();
            flagNames = Collections.unmodifiableList(new ArrayList<>(runner.mFlagsRoulette));
        }
    }
}
