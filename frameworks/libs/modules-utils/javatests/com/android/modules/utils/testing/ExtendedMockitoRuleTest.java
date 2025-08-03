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
package com.android.modules.utils.testing;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.util.Log;

import androidx.annotation.Nullable;

import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.model.Statement;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockitoFramework;
import org.mockito.MockitoSession;
import org.mockito.exceptions.misusing.UnnecessaryStubbingException;
import org.mockito.invocation.InvocationFactory;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.listeners.MockitoListener;
import org.mockito.plugins.MockitoPlugins;
import org.mockito.quality.Strictness;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

@RunWith(MockitoJUnitRunner.class)
public final class ExtendedMockitoRuleTest {

    public static final String TAG = ExtendedMockitoRuleTest.class.getSimpleName();

    // Not a real test (i.e., it doesn't exist on this class), but it's passed to Description
    private static final String TEST_METHOD_BEING_EXECUTED = "testAmI..OrNot";

    private @Mock Statement mStatement;
    private @Mock Runnable mRunnable;
    private @Mock StaticMockFixture mStaticMockFixture1;
    private @Mock StaticMockFixture mStaticMockFixture2;
    private @Mock StaticMockFixture mStaticMockFixture3;

    private final Description mDescription = newTestMethod();
    private final ClassUnderTest mClassUnderTest = new ClassUnderTest();
    private final ExtendedMockitoRule.Builder mBuilder =
            new ExtendedMockitoRule.Builder(mClassUnderTest);
    // Builder that doesn't clear inline methods at the end - should be used in methods that
    // need to verify mocks
    private final ExtendedMockitoRule.Builder mUnsafeBuilder =
            new ExtendedMockitoRule.Builder(mClassUnderTest).dontClearInlineMocks();

    private final Supplier<StaticMockFixture> mSupplier1 = () -> {
        return mStaticMockFixture1;
    };
    private final Supplier<StaticMockFixture> mSupplier2 = () -> {
        return mStaticMockFixture2;
    };
    private final Supplier<StaticMockFixture> mSupplier3 = () -> {
        return mStaticMockFixture3;
    };

    @Test
    public void testBuilder_constructor_null() {
        assertThrows(NullPointerException.class, () -> new ExtendedMockitoRule.Builder(null));
    }

    @Test
    public void testBuilder_setStrictness_null() {
        assertThrows(NullPointerException.class, () -> mBuilder.setStrictness(null));
    }

    @Test
    public void testBuilder_mockStatic_null() {
        assertThrows(NullPointerException.class, () -> mBuilder.mockStatic(null));
    }

    @Test
    public void testBuilder_spyStatic_null() {
        assertThrows(NullPointerException.class, () -> mBuilder.spyStatic(null));
    }

    @Test
    public void testBuilder_addStaticMockFixtures_null() {
        assertThrows(NullPointerException.class,
                () -> mBuilder.addStaticMockFixtures((Supplier<StaticMockFixture>) null));
    }

    @Test
    public void testBuilder_afterSessionFinished_null() {
        assertThrows(NullPointerException.class, () -> mBuilder.afterSessionFinished(null));
    }

    @Test
    public void testBuilder_setMockitoFrameworkForTesting_null() {
        assertThrows(NullPointerException.class,
                () -> mBuilder.setMockitoFrameworkForTesting(null));
    }

    @Test
    public void testBuilder_setMockitoSessionForTesting_null() {
        assertThrows(NullPointerException.class,
                () -> mBuilder.setMockitoSessionForTesting(null));
    }

    @Test
    public void testMocksInitialized() throws Throwable {
        mBuilder.build().apply(mStatement, mDescription).evaluate();

        assertWithMessage("@Mock object").that(mClassUnderTest.mMock).isNotNull();
    }

    @Test
    public void testMocksNotInitialized() throws Throwable {
        new ExtendedMockitoRule.Builder().build().apply(mStatement, mDescription).evaluate();

        assertWithMessage("@Mock object").that(mClassUnderTest.mMock).isNull();
    }

    @Test
    public void testStrictness_lenientByDefault() throws Throwable {
        applyRuleOnTestThatDoesntUseExpectation(/* strictness= */ null);
    }

    @Test
    public void testStrictness_lenient() throws Throwable {
        applyRuleOnTestThatDoesntUseExpectation(Strictness.LENIENT);
    }

    @Test
    public void testStrictness_warn() throws Throwable {
        applyRuleOnTestThatDoesntUseExpectation(Strictness.WARN);
    }

    @Test
    public void testStrictness_strict() throws Throwable {
        assertThrows(UnnecessaryStubbingException.class,
                () -> applyRuleOnTestThatDoesntUseExpectation(Strictness.STRICT_STUBS));
    }

    @Test
    public void testMocksStatic() throws Throwable {
        ExtendedMockitoRule rule = mBuilder.mockStatic(StaticClass.class).build();
        rule.apply(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                doReturn("mocko()").when(() -> StaticClass.marco());

                assertWithMessage("StaticClass.marco()")
                        .that(StaticClass.marco()).isEqualTo("mocko()");
                assertWithMessage("StaticClass.water()")
                        .that(StaticClass.water()).isNull(); // not mocked
            }
        }, mDescription).evaluate();

        Set<Class<?>> mockedClasses = rule.getMockedStaticClasses(mDescription);
        assertWithMessage("rule.getMockedStaticClasses()").that(mockedClasses)
                .containsExactly(StaticClass.class);
        assertThrows(RuntimeException.class,
                () -> mockedClasses.add(ExtendedMockitoRuleTest.class));
    }

    @Test
    public void testMockStatic_sameClass() throws Throwable {
        mBuilder.mockStatic(StaticClass.class);

        assertThrows(IllegalStateException.class, () -> mBuilder.mockStatic(StaticClass.class));
    }

    @Test
    public void testMocksStatic_multipleClasses() throws Throwable {
        ExtendedMockitoRule rule = mBuilder.mockStatic(StaticClass.class)
                .mockStatic(AnotherStaticClass.class).build();
        rule.apply(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                doReturn("mocko()").when(() -> StaticClass.marco());
                doReturn("MOCKO()").when(() -> AnotherStaticClass.marco());

                assertWithMessage("StaticClass.marco()")
                        .that(StaticClass.marco()).isEqualTo("mocko()");
                assertWithMessage("StaticClass.water()")
                        .that(StaticClass.water()).isNull(); // not mocked

                assertWithMessage("AnotherStaticClass.marco()")
                        .that(AnotherStaticClass.marco()).isEqualTo("MOCKO()");
                assertWithMessage("AnotherStaticClass.water()")
                        .that(AnotherStaticClass.water()).isNull(); // not mocked
            }
        }, mDescription).evaluate();

        Set<Class<?>> mockedClasses = rule.getMockedStaticClasses(mDescription);
        assertWithMessage("rule.getMockedStaticClasses()").that(mockedClasses)
                .containsExactly(StaticClass.class, AnotherStaticClass.class);
        assertThrows(RuntimeException.class,
                () -> mockedClasses.add(ExtendedMockitoRuleTest.class));
    }

    @Test
    public void testMockStatic_ruleAndAnnotation() throws Throwable {
        ExtendedMockitoRule rule = mBuilder.mockStatic(StaticClass.class).build();

        rule.apply(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                doReturn("mocko()").when(() -> StaticClass.marco());
                doReturn("MOCKO()").when(() -> AnotherStaticClass.marco());

                assertWithMessage("StaticClass.marco()")
                        .that(StaticClass.marco()).isEqualTo("mocko()");
                assertWithMessage("StaticClass.water()")
                        .that(StaticClass.water()).isNull(); // not mocked

                assertWithMessage("AnotherStaticClass.marco()")
                        .that(AnotherStaticClass.marco()).isEqualTo("MOCKO()");
                assertWithMessage("AnotherStaticClass.water()")
                        .that(AnotherStaticClass.water()).isNull(); // not mocked
            }
        }, newTestMethod(new MockStaticAnnotation(AnotherStaticClass.class))).evaluate();
    }

    // Ideally, we should test the annotations indirectly (i.e., by asserting their static classes
    // are properly mocked, but pragmatically speaking, testing the getSpiedStatic() is enough - and
    // much simpler
    @Test
    public void testMockStatic_fromEverywhere() throws Throwable {
        ExtendedMockitoRule rule = mBuilder.mockStatic(StaticClass.class).build();

        Set<Class<?>> mockedClasses = rule.getMockedStaticClasses(newTestMethod(SubClass.class,
                new MockStaticAnnotation(AnotherStaticClass.class)));

        assertWithMessage("rule.getMockedStaticClasses()").that(mockedClasses).containsExactly(
                StaticClass.class, AnotherStaticClass.class, StaticClassMockedBySuperClass.class,
                AnotherStaticClassMockedBySuperClass.class, StaticClassMockedBySubClass.class,
                AnotherStaticClassMockedBySubClass.class);
        assertThrows(RuntimeException.class,
                () -> mockedClasses.add(ExtendedMockitoRuleTest.class));
    }

    @Test
    public void testSpyStatic() throws Throwable {
        ExtendedMockitoRule rule = mBuilder.spyStatic(StaticClass.class).build();

        rule.apply(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                doReturn("mocko()").when(() -> StaticClass.marco());

                assertWithMessage("StaticClass.marco()")
                        .that(StaticClass.marco()).isEqualTo("mocko()");
                assertWithMessage("StaticClass.water()")
                        .that(StaticClass.water()).isEqualTo("polo");
            }
        }, mDescription).evaluate();

        Set<Class<?>> spiedClasses = rule.getSpiedStaticClasses(mDescription);
        assertWithMessage("rule.getSpiedStaticClasses()").that(spiedClasses)
                .containsExactly(StaticClass.class);
        assertThrows(RuntimeException.class, () -> spiedClasses.add(ExtendedMockitoRuleTest.class));
    }

    @Test
    public void testSpyStatic_sameClass() throws Throwable {
        mBuilder.spyStatic(StaticClass.class);

        assertThrows(IllegalStateException.class, () -> mBuilder.spyStatic(StaticClass.class));
    }

    @Test
    public void testSpyStatic_multipleClasses() throws Throwable {
        ExtendedMockitoRule rule = mBuilder.spyStatic(StaticClass.class)
                .spyStatic(AnotherStaticClass.class).build();

        rule.apply(new Statement() {
                    @Override
                    public void evaluate() throws Throwable {
                        doReturn("mocko()").when(() -> StaticClass.marco());
                        doReturn("MOCKO()").when(() -> AnotherStaticClass.marco());

                        assertWithMessage("StaticClass.marco()")
                                .that(StaticClass.marco()).isEqualTo("mocko()");
                        assertWithMessage("StaticClass.water()")
                                .that(StaticClass.water()).isEqualTo("polo");

                        assertWithMessage("AnotherStaticClass.marco()")
                                .that(AnotherStaticClass.marco()).isEqualTo("MOCKO()");
                        assertWithMessage("AnotherStaticClass.water()")
                                .that(AnotherStaticClass.water()).isEqualTo("POLO");
                    }
                }, mDescription).evaluate();

        Set<Class<?>> spiedClasses = rule.getSpiedStaticClasses(mDescription);
        assertWithMessage("rule.getSpiedStaticClasses()").that(spiedClasses)
                .containsExactly(StaticClass.class, AnotherStaticClass.class);
        assertThrows(RuntimeException.class, () -> spiedClasses.add(ExtendedMockitoRuleTest.class));
    }

    @Test
    public void testSpyStatic_ruleAndAnnotation() throws Throwable {
        ExtendedMockitoRule rule = mBuilder.spyStatic(StaticClass.class).build();
        rule.apply(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                doReturn("mocko()").when(() -> StaticClass.marco());
                doReturn("MOCKO()").when(() -> AnotherStaticClass.marco());

                assertWithMessage("StaticClass.marco()")
                        .that(StaticClass.marco()).isEqualTo("mocko()");
                assertWithMessage("StaticClass.water()")
                        .that(StaticClass.water()).isEqualTo("polo");

                assertWithMessage("AnotherStaticClass.marco()")
                        .that(AnotherStaticClass.marco()).isEqualTo("MOCKO()");
                assertWithMessage("AnotherStaticClass.water()")
                        .that(AnotherStaticClass.water()).isEqualTo("POLO");
            }
        }, newTestMethod(new SpyStaticAnnotation(AnotherStaticClass.class))).evaluate();
    }

    // Ideally, we should test the annotations indirectly (i.e., by asserting their static classes
    // are properly spied, but pragmatically speaking, testing the getSpiedStatic() is enough - and
    // much simpler
    @Test
    public void testSpyStatic_fromEverywhere() throws Throwable {
        ExtendedMockitoRule rule = mBuilder.spyStatic(StaticClass.class).build();

        Set<Class<?>> spiedClasses = rule.getSpiedStaticClasses(newTestMethod(SubClass.class,
                new SpyStaticAnnotation(AnotherStaticClass.class)));

        assertWithMessage("rule.getSpiedStaticClasses()").that(spiedClasses).containsExactly(
                StaticClass.class, AnotherStaticClass.class, StaticClassSpiedBySuperClass.class,
                AnotherStaticClassSpiedBySuperClass.class, StaticClassSpiedBySubClass.class,
                AnotherStaticClassSpiedBySubClass.class);
        assertThrows(RuntimeException.class, () -> spiedClasses.add(ExtendedMockitoRuleTest.class));
    }

    @Test
    public void testMockAndSpyStatic() throws Throwable {
        ExtendedMockitoRule rule = mBuilder.mockStatic(StaticClass.class)
                .spyStatic(AnotherStaticClass.class).build();

        rule.apply(new Statement() {
                    @Override
                    public void evaluate() throws Throwable {
                        doReturn("mocko()").when(() -> StaticClass.marco());
                        doReturn("MOCKO()").when(() -> AnotherStaticClass.marco());

                        assertWithMessage("StaticClass.marco()")
                                .that(StaticClass.marco()).isEqualTo("mocko()");
                        assertWithMessage("StaticClass.water()")
                                .that(StaticClass.water()).isNull(); // not mocked

                        assertWithMessage("AnotherStaticClass.marco()")
                                .that(AnotherStaticClass.marco()).isEqualTo("MOCKO()");
                        assertWithMessage("AnotherStaticClass.water()")
                                .that(AnotherStaticClass.water()).isEqualTo("POLO");
                    }
                }, mDescription).evaluate();

        Set<Class<?>> spiedStaticClasses = rule.getSpiedStaticClasses(mDescription);
        assertWithMessage("rule.getSpiedStaticClasses()").that(spiedStaticClasses)
                .containsExactly(AnotherStaticClass.class);
        assertThrows(RuntimeException.class,
                () -> spiedStaticClasses.add(ExtendedMockitoRuleTest.class));

        Set<Class<?>> mockedStaticClasses = rule.getMockedStaticClasses(mDescription);
        assertWithMessage("rule.getMockedStaticClasses()").that(mockedStaticClasses)
                .containsExactly(StaticClass.class);
        assertThrows(RuntimeException.class,
                () -> mockedStaticClasses.add(ExtendedMockitoRuleTest.class));
    }

    @Test
    public void testMockAndSpyStatic_sameClass() throws Throwable {
        mBuilder.mockStatic(StaticClass.class);

        assertThrows(IllegalStateException.class, () -> mBuilder.spyStatic(StaticClass.class));
    }

    @Test
    public void testSpyAndMockStatic_sameClass() throws Throwable {
        mBuilder.spyStatic(StaticClass.class);

        assertThrows(IllegalStateException.class, () -> mBuilder.mockStatic(StaticClass.class));
    }

    @Test
    public void testAddStaticMockFixtures_once() throws Throwable {
        InOrder inOrder = inOrder(mStaticMockFixture1, mStaticMockFixture2);

        mUnsafeBuilder
                .addStaticMockFixtures(mSupplier1, mSupplier2)
                .build().apply(mStatement, mDescription).evaluate();

        inOrder.verify(mStaticMockFixture1).setUpMockedClasses(any());
        inOrder.verify(mStaticMockFixture2).setUpMockedClasses(any());

        inOrder.verify(mStaticMockFixture1).setUpMockBehaviors();
        inOrder.verify(mStaticMockFixture2).setUpMockBehaviors();

        inOrder.verify(mStaticMockFixture2).tearDown();
        inOrder.verify(mStaticMockFixture1).tearDown();
    }

    @Test
    public void testAddStaticMockFixtures_twice() throws Throwable {
        InOrder inOrder = inOrder(mStaticMockFixture1, mStaticMockFixture2, mStaticMockFixture3,
                mStaticMockFixture3);

        mUnsafeBuilder
                .addStaticMockFixtures(mSupplier1, mSupplier2)
                .addStaticMockFixtures(mSupplier3)
                .build().apply(mStatement, mDescription).evaluate();

        inOrder.verify(mStaticMockFixture1).setUpMockedClasses(any());
        inOrder.verify(mStaticMockFixture2).setUpMockedClasses(any());
        inOrder.verify(mStaticMockFixture3).setUpMockedClasses(any());

        inOrder.verify(mStaticMockFixture1).setUpMockBehaviors();
        inOrder.verify(mStaticMockFixture2).setUpMockBehaviors();
        inOrder.verify(mStaticMockFixture3).setUpMockBehaviors();

        inOrder.verify(mStaticMockFixture3).tearDown();
        inOrder.verify(mStaticMockFixture2).tearDown();
        inOrder.verify(mStaticMockFixture1).tearDown();
    }

    @Test
    public void testMockAndSpyStaticAndAddStaticMockFixtures() throws Throwable {
        InOrder inOrder = inOrder(mStaticMockFixture1, mStaticMockFixture2, mStaticMockFixture3,
                mStaticMockFixture3);

        mUnsafeBuilder
                .mockStatic(StaticClass.class)
                .spyStatic(AnotherStaticClass.class)
                .addStaticMockFixtures(mSupplier1, mSupplier2)
                .addStaticMockFixtures(mSupplier3)
                .build()
                .apply(new Statement() {
                    @Override
                    public void evaluate() throws Throwable {
                        doReturn("mocko()").when(() -> StaticClass.marco());
                        doReturn("MOCKO()").when(() -> AnotherStaticClass.marco());

                        assertWithMessage("StaticClass.marco()")
                                .that(StaticClass.marco()).isEqualTo("mocko()");
                        assertWithMessage("StaticClass.water()")
                                .that(StaticClass.water()).isNull(); // not mocked

                        assertWithMessage("AnotherStaticClass.marco()")
                                .that(AnotherStaticClass.marco()).isEqualTo("MOCKO()");
                        assertWithMessage("AnotherStaticClass.water()")
                                .that(AnotherStaticClass.water()).isEqualTo("POLO");
                    }
                }, mDescription).evaluate();

        inOrder.verify(mStaticMockFixture1).setUpMockedClasses(any());
        inOrder.verify(mStaticMockFixture2).setUpMockedClasses(any());
        inOrder.verify(mStaticMockFixture3).setUpMockedClasses(any());

        inOrder.verify(mStaticMockFixture1).setUpMockBehaviors();
        inOrder.verify(mStaticMockFixture2).setUpMockBehaviors();
        inOrder.verify(mStaticMockFixture3).setUpMockBehaviors();

        inOrder.verify(mStaticMockFixture3).tearDown();
        inOrder.verify(mStaticMockFixture2).tearDown();
        inOrder.verify(mStaticMockFixture1).tearDown();
    }

    @Test
    public void testAfterSessionFinished() throws Throwable {
        mUnsafeBuilder.afterSessionFinished(mRunnable).build().apply(mStatement, mDescription)
                .evaluate();

        verify(mRunnable).run();
    }

    @Test
    public void testAfterSessionFinished_whenSessionFailsToFinish() throws Throwable {
        MockitoSessionThatFailsToFinish mockitoSession = new MockitoSessionThatFailsToFinish();

        Exception thrown = assertThrows(Exception.class,
                () -> mUnsafeBuilder.afterSessionFinished(mRunnable)
                        .setMockitoSessionForTesting(mockitoSession)
                        .build().apply(mStatement, mDescription).evaluate());

        assertWithMessage("exception").that(thrown).isSameInstanceAs(mockitoSession.e);
        verify(mRunnable).run();
    }

    @Test
    public void testMockitoFrameworkCleared() throws Throwable {
        MyMockitoFramework mockitoFramework = new MyMockitoFramework();

        mBuilder.setMockitoFrameworkForTesting(mockitoFramework).build()
                .apply(mStatement, mDescription)
                .evaluate();

        assertWithMessage("mockito framework cleared").that(mockitoFramework.called).isTrue();
    }

    @Test
    public void testMockitoFrameworkNotCleared_whenSetOnBuilder() throws Throwable {
        MyMockitoFramework mockitoFramework = new MyMockitoFramework();

        mBuilder.dontClearInlineMocks().setMockitoFrameworkForTesting(mockitoFramework).build()
                .apply(mStatement, mDescription)
                .evaluate();

        assertWithMessage("mockito framework cleared").that(mockitoFramework.called).isFalse();
    }

    @Test
    public void testMockitoFrameworkCleared_whenTestFails() throws Throwable {
        MyMockitoFramework mockitoFramework = new MyMockitoFramework();
        Exception exception = new Exception("D'OH!");

        Exception thrown = assertThrows(Exception.class,
                () -> mBuilder.setMockitoFrameworkForTesting(mockitoFramework).build()
                        .apply(new Statement() {
                            @Override
                            public void evaluate() throws Throwable {
                                throw exception;
                            }
                        }, mDescription).evaluate());

        assertWithMessage("exception").that(thrown).isSameInstanceAs(exception);
        assertWithMessage("mockito framework cleared").that(mockitoFramework.called).isTrue();
    }

    @Test
    public void testMockitoFrameworkCleared_whenAfterSessionFinished() throws Throwable {
        MyMockitoFramework mockitoFramework = new MyMockitoFramework();
        RuntimeException exception = new RuntimeException("D'OH!");

        Exception thrown = assertThrows(Exception.class,
                () -> mBuilder.setMockitoFrameworkForTesting(mockitoFramework)
                        .afterSessionFinished(() -> {
                            throw exception;
                        }).build().apply(mStatement, mDescription).evaluate());

        assertWithMessage("exception").that(thrown).isSameInstanceAs(exception);
        assertWithMessage("mockito framework cleared").that(mockitoFramework.called).isTrue();
    }

    @Test
    public void testMockitoFrameworkCleared_whenStaticMockFixturesFailed() throws Throwable {
        MyMockitoFramework mockitoFramework = new MyMockitoFramework();
        RuntimeException exception = new RuntimeException("D'OH!");
        doThrow(exception).when(mStaticMockFixture1).tearDown();

        Exception thrown = assertThrows(Exception.class,
                () -> mBuilder.setMockitoFrameworkForTesting(mockitoFramework)
                        .addStaticMockFixtures(mSupplier1).build().apply(mStatement, mDescription)
                        .evaluate());

        assertWithMessage("exception").that(thrown).isSameInstanceAs(exception);
        assertWithMessage("mockito framework cleared").that(mockitoFramework.called).isTrue();
    }

    @Test
    public void testMockitoFrameworkCleared_whenSessionFailsToFinish() throws Throwable {
        MyMockitoFramework mockitoFramework = new MyMockitoFramework();
        MockitoSessionThatFailsToFinish mockitoSession = new MockitoSessionThatFailsToFinish();

        Exception thrown = assertThrows(Exception.class,
                () -> mBuilder.setMockitoFrameworkForTesting(mockitoFramework)
                        .setMockitoSessionForTesting(mockitoSession).build()
                        .apply(mStatement, mDescription).evaluate());

        assertWithMessage("exception").that(thrown).isSameInstanceAs(mockitoSession.e);
        assertWithMessage("mockito framework cleared").that(mockitoFramework.called).isTrue();
    }

    @Test
    public void testGetClearInlineMethodsAtTheEnd() throws Throwable {
        assertWithMessage("getClearInlineMethodsAtTheEnd() by default")
                .that(mBuilder.build().getClearInlineMethodsAtTheEnd(mDescription)).isTrue();
        assertWithMessage("getClearInlineMethodsAtTheEnd() when built with dontClearInlineMocks()")
                .that(mBuilder.dontClearInlineMocks().build()
                        .getClearInlineMethodsAtTheEnd(mDescription))
                .isFalse();
    }

    private void applyRuleOnTestThatDoesntUseExpectation(@Nullable Strictness strictness)
            throws Throwable {
        Log.d(TAG, "applyRuleOnTestThatDoesntUseExpectation(): strictness= " + strictness);
        if (strictness != null) {
            mBuilder.setStrictness(strictness);
        }
        mBuilder.build().apply(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                when(mClassUnderTest.mMock.getMeaningOfLife()).thenReturn(42);
            }
        }, mDescription).evaluate();
    }

    private static Description newTestMethod(Annotation... annotations) {
        return newTestMethod(ClassUnderTest.class, annotations);
    }

    private static Description newTestMethod(Class<?> testClass, Annotation... annotations) {
        return Description.createTestDescription(testClass, TEST_METHOD_BEING_EXECUTED,
                annotations);
    }

    private static final class ClassUnderTest {
        @Mock
        public DumbObject mMock;
    }

    private static final class StaticClass {

        public static String marco() {
            Log.d(TAG, "StaticClass.marco() called");
            return "polo";
        }

        public static String water() {
            Log.d(TAG, "StaticClass.water() called");
            return "polo";
        }
    }

    private static final class AnotherStaticClass {

        public static String marco() {
            Log.d(TAG, "AnotherStaticClass.marco() called");
            return "POLO";
        }

        public static String water() {
            Log.d(TAG, "AnotherStaticClass.water() called");
            return "POLO";
        }
    }

    private static class DumbObject {

        public int getMeaningOfLife() {
            Log.d(TAG, "getMeaningOfLife() called");
            throw new UnsupportedOperationException("Ask Bard");
        }
    }

    // Used on tests that check if the clearInlineMocks() is called - such tests cannot mock a
    // MockitoFramework because of that
    private static final class MyMockitoFramework implements MockitoFramework {

        private static int sCounter;

        private final int mId = ++sCounter;
        public boolean called;

        @Override
        public MockitoFramework removeListener(MockitoListener arg0) {
            return null;
        }

        @Override
        public MockitoPlugins getPlugins() {
            return null;
        }

        @Override
        public InvocationFactory getInvocationFactory() {
            return null;
        }

        @Override
        public void clearInlineMocks() {
            called = true;
        }

        @Override
        public void clearInlineMock(Object arg0) {
        }

        @Override
        public MockitoFramework addListener(MockitoListener arg0) {
            return null;
        }

        @Override
        public String toString() {
            return "MyMockitoFramework-" + mId;
        }
    };

    // Used on tests that check if the clearInlineMocks() is called - such tests cannot mock a
    // MockitoSession because of that
    private static final class MockitoSessionThatFailsToFinish implements MockitoSession {
        public final RuntimeException e = new RuntimeException("D'OH!");

        @Override
        public void setStrictness(Strictness strictness) {
        }

        @Override
        public void finishMocking() {
            throw e;
        }

        @Override
        public void finishMocking(Throwable failure) {
            throw e;
        }
    }

    private abstract static class ClassAnnotation<A extends Annotation> implements Annotation {
        private Class<A> mAnnotationType;
        private Class<?> mClass;

        private ClassAnnotation(Class<A> annotationType, Class<?> clazz) {
            mAnnotationType = annotationType;
            mClass = clazz;
        }

        @Override
        public final Class<A> annotationType() {
            return mAnnotationType;
        }

        public final Class<?> value() {
            return mClass;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mAnnotationType, mClass);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            ClassAnnotation other = (ClassAnnotation) obj;
            return Objects.equals(mAnnotationType, other.mAnnotationType)
                    && Objects.equals(mClass, other.mClass);
        }

        @Override
        public String toString() {
            return getClass().getSimpleName() + "[" + mClass.getSimpleName() + "]";
        }
    }

    private static final class SpyStaticAnnotation extends ClassAnnotation<SpyStatic>
            implements SpyStatic {

        private SpyStaticAnnotation(Class<?> clazz) {
            super(SpyStatic.class, clazz);
        }
    }

    private static final class MockStaticAnnotation extends ClassAnnotation<MockStatic>
            implements MockStatic {

        private MockStaticAnnotation(Class<?> clazz) {
            super(MockStatic.class, clazz);
        }
    }

    private static final class StaticClassMockedBySuperClass {
    }

    private static final class AnotherStaticClassMockedBySuperClass {
    }
    private static final class StaticClassSpiedBySuperClass {
    }

    private static final class AnotherStaticClassSpiedBySuperClass {
    }

    @SpyStatic(StaticClassSpiedBySuperClass.class)
    @SpyStatic(AnotherStaticClassSpiedBySuperClass.class)
    @MockStatic(StaticClassMockedBySuperClass.class)
    @MockStatic(AnotherStaticClassMockedBySuperClass.class)
    private static class SuperClass {

    }

    private static final class StaticClassMockedBySubClass {
    }

    private static final class AnotherStaticClassMockedBySubClass {
    }

    private static final class StaticClassSpiedBySubClass {
    }

    private static final class AnotherStaticClassSpiedBySubClass {
    }

    @SpyStatic(StaticClassSpiedBySubClass.class)
    @SpyStatic(AnotherStaticClassSpiedBySubClass.class)
    @MockStatic(StaticClassMockedBySubClass.class)
    @MockStatic(AnotherStaticClassMockedBySubClass.class)
    private static final class SubClass extends SuperClass{
    }
}