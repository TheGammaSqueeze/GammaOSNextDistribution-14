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

import android.adservices.test.longevity.concurrent.proto.Configuration.Scenario;
import android.adservices.test.longevity.concurrent.proto.Configuration.Scenario.Journey;
import android.util.Log;

import com.android.adservices.shared.testing.junit.EasilyExtensibleBlockJUnit4ClassRunner;
import com.android.adservices.shared.testing.junit.RuleContainer;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.internal.runners.statements.RunAfters;
import org.junit.internal.runners.statements.RunBefores;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.InitializationError;
import org.junit.runners.model.MultipleFailureException;
import org.junit.runners.model.Statement;
import org.junit.runners.model.TestClass;

import java.util.ArrayList;
import java.util.List;

/**
 * This class runs a particular scenario which has multiple tests present and this tests are run
 * concurrently.
 *
 * <p>Overrides the methodBlock method which changes the execution of {@link Before}, {@link After},
 * {@link Test}, {@link Rule} execution.
 */
public class ConcurrentRunner extends EasilyExtensibleBlockJUnit4ClassRunner {

    private static final String TAG = ConcurrentRunner.class.getSimpleName();
    private static final ThreadLocal<RuleContainer> sCurrentRuleContainer = new ThreadLocal<>();

    protected final Scenario mScenario;
    private final String mScheduleIdx;
    private final List<TestClass> mTestClasses = new ArrayList<>();
    private final List<Object> mJourneyClasses = new ArrayList<>();
    private final List<FrameworkMethod> mFrameworkMethods = new ArrayList<>();
    private Description mDescription = null;

    public ConcurrentRunner(Scenario scenario, String scheduleIdx) throws InitializationError {
        // Using {@link DummyTestClass} class here as parent class of ConcurrentRunner invokes
        // ParentRunner<>
        // which validates test class has a single constructor and annotations. This does not
        // have any functional impact.
        super(DummyTestClass.class);
        mScenario = scenario;
        mScheduleIdx = scheduleIdx;
        initialize();
    }

    private void initialize() {
        for (Journey journey : mScenario.getJourneysList()) {
            try {
                String journeyStr = journey.getJourney();
                TestClass testClass = new TestClass(Class.forName(journeyStr));
                mTestClasses.add(testClass);
                mFrameworkMethods.add(getFrameworkMethod(journey, testClass));
                mJourneyClasses.add(testClass.getJavaClass().getConstructor().newInstance());
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize test classes and methods");
                throw new IllegalArgumentException(e);
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overrides the test class description and describes which all tests we are running in
     * parallel for a particular scenario.
     */
    @Override
    protected Description describeChild(FrameworkMethod method) {
        if (mDescription != null) {
            return mDescription;
        }
        StringBuilder builder = new StringBuilder();
        builder.append(mScheduleIdx + ": ");
        for (int i = 0; i < mTestClasses.size(); i++) {
            builder.append(
                    mTestClasses.get(i).getName()
                            + "#"
                            + mJourneyClasses.get(i).getClass().getSimpleName()
                            + ", ");
        }
        mDescription = Description.createTestDescription(builder.toString(), /* arg = */ "");
        return mDescription;
    }

    /**
     * Returns a Statement that, when executed, either returns normally if {@code method} passes, or
     * throws an exception if {@code method} fails.
     *
     * <p>Here is an outline of the implementation:
     *
     * <ul>
     *   <li>Invoke each {@code journey} and {@code method} present in {@code mJourneyClasses} and
     *       {@code mFrameworkMethods} respectively on a separate thread and runs it concurrently.
     *       Throws any exceptions thrown by either operation.
     *   <li>ALWAYS run all non-overridden {@code @Before} methods on this class and superclasses
     *       before any of the previous steps; if any throws an Exception, stop execution and pass
     *       the exception on. Combines all {@link Before} annotation of all the journeys present in
     *       a scenario to run it.
     *   <li>ALWAYS run all non-overridden {@code @After} methods on this class and superclasses
     *       after any of the previous steps; all After methods are always executed: exceptions
     *       thrown by previous steps are combined, if necessary, with exceptions from After methods
     *       into a {@link MultipleFailureException}. Combines all {@link After} annotation of all
     *       the journeys present in a scenario to run it serially.
     *   <li>ALWAYS allow {@code @Rule} fields to modify the execution of the above steps. A {@code
     *       Rule} may prevent all execution of the above steps, or add additional behavior before
     *       and after, or modify thrown exceptions. For more information, see {@link TestRule}
     * </ul>
     *
     * This can be overridden in subclasses, either by overriding this method, or the
     * implementations creating each sub-statement.
     *
     * <p>For example, lets say a scenario has 2 tests and we want to execute this in parallel.
     * TestClass1 with methods (rule1, beforeClass1, before1, testMethod1, after1, afterClass1).
     * TestClass2 with methods (rule2, beforeClass2, before2, testMethod2, after2, afterClass2).
     *
     * <p>These methods below will run serially. rule1 -> rule2 ->beforeClass1 -> before1
     * ->beforeClass2 -> before2
     *
     * <p>testMethod1 and testMethod2 will run in parallel.
     *
     * <p>Once both the test methods finishes, we will execute remaining methods serially.
     *
     * <p>after1 -> afterClass1 -> after2 -> afterClass2 -> rule2 (remaining code after base
     * .evaluate()) -> rule1 (remaining code after base.evaluate()).
     */
    @Override
    protected Statement methodBlock(final FrameworkMethod method) {
        // Runs the test present in the scenario concurrently.
        Statement statement = new ConcurrentScenariosStatement(mJourneyClasses, mFrameworkMethods);
        statement = withBefores(method, /* target = */ null, statement);
        statement = withAfters(method, /* target = */ null, statement);
        statement = withAllRules(statement);
        statement = withInterruptIsolation(statement);
        return statement;
    }

    /**
     * Runs the {@link AfterClass} methods after running all the {@link After} methods of the test
     * class.
     *
     * <p>Combines all {@link After} annotation of all the journeys present in a scenario to run it
     * serially.
     */
    @Override
    protected Statement withAfters(FrameworkMethod method, Object target, Statement statement) {
        for (int i = 0; i < mTestClasses.size(); i++) {
            TestClass testClass = mTestClasses.get(i);
            final List<FrameworkMethod> afterMethods = new ArrayList<>();
            afterMethods.addAll(testClass.getAnnotatedMethods(After.class));
            afterMethods.addAll(testClass.getAnnotatedMethods(AfterClass.class));
            if (!afterMethods.isEmpty()) {
                statement = addRunAfters(statement, afterMethods, mJourneyClasses.get(i));
            }
        }
        return statement;
    }

    /**
     * Runs the {@link BeforeClass} methods before running all the {@link Before} methods of the
     * test class.
     *
     * <p>Combines all {@link Before} annotation of all the journeys present in a scenario to run it
     * serially.
     */
    @Override
    protected Statement withBefores(FrameworkMethod method, Object target, Statement statement) {
        for (int i = 0; i < mTestClasses.size(); i++) {
            TestClass testClass = mTestClasses.get(i);
            List<FrameworkMethod> allBeforeMethods = new ArrayList<>();
            allBeforeMethods.addAll(testClass.getAnnotatedMethods(BeforeClass.class));
            allBeforeMethods.addAll(testClass.getAnnotatedMethods(Before.class));
            if (!allBeforeMethods.isEmpty()) {
                statement = addRunBefores(statement, allBeforeMethods, mJourneyClasses.get(i));
            }
        }
        return statement;
    }

    /**
     * Override the parent {@code withBeforeClasses} method to be a no-op.
     *
     * <p>The {@link BeforeClass} methods will be included later as {@link Before} methods.
     */
    @Override
    protected Statement withBeforeClasses(Statement statement) {
        return statement;
    }

    /**
     * Override the parent {@code withAfterClasses} method to be a no-op.
     *
     * <p>The {@link AfterClass} methods will be included later as {@link After} methods.
     */
    @Override
    protected Statement withAfterClasses(Statement statement) {
        return statement;
    }

    protected RunBefores addRunBefores(
            Statement statement, List<FrameworkMethod> befores, Object target) {
        return new RunBefores(statement, befores, target);
    }

    protected RunAfters addRunAfters(
            Statement statement, List<FrameworkMethod> afters, Object target) {
        return new RunAfters(statement, afters, target);
    }

    private FrameworkMethod getFrameworkMethod(Journey journey, TestClass testClass)
            throws Exception {
        if (!journey.hasMethodName()) {
            return testClass.getAnnotatedMethods(Test.class).get(0);
        }
        for (FrameworkMethod method : testClass.getAnnotatedMethods(Test.class)) {
            if (method.getName().equals(journey.getMethodName())) {
                return method;
            }
        }
        throw new Exception(
                String.format(
                        "Method name: %s not found in the testclass", journey.getMethodName()));
    }

    private Statement withAllRules(Statement statement) {
        for (int i = 0; i < mTestClasses.size(); i++) {
            statement =
                    withRules(
                            mFrameworkMethods.get(i),
                            mTestClasses.get(i),
                            mJourneyClasses.get(i),
                            statement);
        }
        return statement;
    }

    /*
    This class is used when we instantiate the {@link ConcurrentRunner}. Parent class of
    ConcurrentRunner invoke ParentRunner<> which validates test class has a single constructor
    and annotations. This does not have any functional impact on the overall code.
    ConcurrentRunner override various methods such as methodBlock which explicitly invokes the
    method on a particular test class.
    */
    @android.platform.test.scenario.annotation.Scenario
    @RunWith(JUnit4.class)
    public static class DummyTestClass {
        @Test
        public void testDummyClass() throws Exception {}
    }
}
