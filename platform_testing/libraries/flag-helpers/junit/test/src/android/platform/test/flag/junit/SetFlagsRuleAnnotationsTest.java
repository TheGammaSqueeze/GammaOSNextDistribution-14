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

package android.platform.test.flag.junit;

import static android.platform.test.flag.junit.SetFlagsRule.DefaultInitValueType.DEVICE_DEFAULT;
import static android.platform.test.flag.junit.SetFlagsRule.DefaultInitValueType.NULL_DEFAULT;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.util.FlagSetException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;

/** Unit tests for {@code SetFlagsRule} being used with annotations. */
@RunWith(JUnit4.class)
public final class SetFlagsRuleAnnotationsTest {

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @EnableFlags(Flags.FLAG_FLAG_NAME4)
    public @interface EnableFlag4 {}

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @EnableFlags({Flags.FLAG_FLAG_NAME3, Flags.FLAG_FLAG_NAME4})
    public @interface EnableFlags3And4 {}

    @Test
    public void emptyTestWithoutAnnotationsPasses() {
        new AnnotationTestRuleHelper(new SetFlagsRule()).prepareTest().assertPasses();
    }

    @Test
    public void throwingTestWithoutAnnotationsThrows() {
        new AnnotationTestRuleHelper(new SetFlagsRule())
                .setTestCode(
                        () -> {
                            throw new IOException("a test about foo");
                        })
                .prepareTest()
                .assertFailsWithTypeAndMessage(IOException.class, "foo");
    }

    @Test
    public void unsetFlagsWithNullDefaultPassIfNoFlagsInPackageAreSet() {
        new AnnotationTestRuleHelper(new SetFlagsRule(NULL_DEFAULT))
                .setTestCode(
                        () -> {
                            assertFalse(Flags.flagName3());
                        })
                .prepareTest()
                .assertPasses();
    }

    @Test
    public void setFlagsWithNullDefaultCanBeRead() {
        new AnnotationTestRuleHelper(new SetFlagsRule(NULL_DEFAULT))
                .addEnableFlags(Flags.FLAG_FLAG_NAME3)
                .setTestCode(
                        () -> {
                            assertTrue(Flags.flagName3());
                        })
                .prepareTest()
                .assertPasses();
    }

    @Test
    public void unsetFlagsWithNullDefaultFailToRead() {
        new AnnotationTestRuleHelper(new SetFlagsRule(NULL_DEFAULT))
                .addEnableFlags(Flags.FLAG_FLAG_NAME4)
                .setTestCode(
                        () -> {
                            Flags.flagName3();
                        })
                .prepareTest()
                .assertFailsWithType(NullPointerException.class);
    }

    @Test
    public void classAnnotationsAreHandled() {
        @EnableFlags(Flags.FLAG_FLAG_NAME3)
        class SomeClass {}
        new AnnotationTestRuleHelper(new SetFlagsRule(NULL_DEFAULT))
                .setTestClass(SomeClass.class)
                .setTestCode(
                        () -> {
                            assertTrue(Flags.flagName3());
                        })
                .prepareTest()
                .assertPasses();
    }

    @Test
    public void allowMultipleAnnotations() {
        @EnableFlags(Flags.FLAG_FLAG_NAME3)
        @EnableFlag4
        class SomeClass {}
        new AnnotationTestRuleHelper(new SetFlagsRule(NULL_DEFAULT))
                .setTestClass(SomeClass.class)
                .setTestCode(
                        () -> {
                            assertTrue(Flags.flagName3());
                            assertTrue(Flags.flagName4());
                        })
                .prepareTest()
                .assertPasses();
    }

    @Test
    public void allowMultipleOverlapingAnnotations() {
        @EnableFlags(Flags.FLAG_FLAG_NAME3)
        @EnableFlags3And4
        class SomeClass {}
        new AnnotationTestRuleHelper(new SetFlagsRule(NULL_DEFAULT))
                .setTestClass(SomeClass.class)
                .setTestCode(
                        () -> {
                            assertTrue(Flags.flagName3());
                            assertTrue(Flags.flagName4());
                        })
                .prepareTest()
                .assertPasses();
    }

    @Test
    public void enablingFlagEnabledByAnnotationFails() {
        SetFlagsRule setFlagsRule = new SetFlagsRule();
        new AnnotationTestRuleHelper(setFlagsRule)
                .addEnableFlags(Flags.FLAG_FLAG_NAME3)
                .setTestCode(
                        () -> {
                            setFlagsRule.enableFlags(Flags.FLAG_FLAG_NAME3);
                        })
                .prepareTest()
                .assertFailsWithType(FlagSetException.class);
    }

    @Test
    public void disablingFlagEnabledByAnnotationFails() {
        SetFlagsRule setFlagsRule = new SetFlagsRule();
        new AnnotationTestRuleHelper(setFlagsRule)
                .addEnableFlags(Flags.FLAG_FLAG_NAME3)
                .setTestCode(
                        () -> {
                            setFlagsRule.disableFlags(Flags.FLAG_FLAG_NAME3);
                        })
                .prepareTest()
                .assertFailsWithType(FlagSetException.class);
    }

    @Test
    public void enablingFlagDisabledByAnnotationFails() {
        SetFlagsRule setFlagsRule = new SetFlagsRule();
        new AnnotationTestRuleHelper(setFlagsRule)
                .addDisableFlags(Flags.FLAG_FLAG_NAME3)
                .setTestCode(
                        () -> {
                            setFlagsRule.enableFlags(Flags.FLAG_FLAG_NAME3);
                        })
                .prepareTest()
                .assertFailsWithType(FlagSetException.class);
    }

    @Test
    public void disablingFlagDisabledByAnnotationFails() {
        SetFlagsRule setFlagsRule = new SetFlagsRule();
        new AnnotationTestRuleHelper(setFlagsRule)
                .addDisableFlags(Flags.FLAG_FLAG_NAME3)
                .setTestCode(
                        () -> {
                            setFlagsRule.disableFlags(Flags.FLAG_FLAG_NAME3);
                        })
                .prepareTest()
                .assertFailsWithType(FlagSetException.class);
    }

    @Test
    public void enablingDifferentFlagThanAnnotationPasses() {
        SetFlagsRule setFlagsRule = new SetFlagsRule();
        new AnnotationTestRuleHelper(setFlagsRule)
                .addDisableFlags(Flags.FLAG_FLAG_NAME3)
                .setTestCode(
                        () -> {
                            setFlagsRule.enableFlags(Flags.FLAG_FLAG_NAME4);
                            assertFalse(Flags.flagName3());
                            assertTrue(Flags.flagName4());
                        })
                .prepareTest()
                .assertPasses();
    }

    @Test
    public void disablingDifferentFlagThanAnnotationPasses() {
        SetFlagsRule setFlagsRule = new SetFlagsRule();
        new AnnotationTestRuleHelper(setFlagsRule)
                .addDisableFlags(Flags.FLAG_FLAG_NAME3)
                .setTestCode(
                        () -> {
                            setFlagsRule.disableFlags(Flags.FLAG_FLAG_NAME4);
                            assertFalse(Flags.flagName3());
                            assertFalse(Flags.flagName4());
                        })
                .prepareTest()
                .assertPasses();
    }

    @Test
    public void conflictingClassAnnotationsFails() {
        @EnableFlags({Flags.FLAG_FLAG_NAME3, Flags.FLAG_FLAG_NAME4})
        @DisableFlags(Flags.FLAG_FLAG_NAME3)
        class SomeClass {}
        new AnnotationTestRuleHelper(new SetFlagsRule())
                .setTestClass(SomeClass.class)
                .prepareTest()
                .assertFails();
    }

    @Test
    public void conflictingMethodAnnotationsFails() {
        new AnnotationTestRuleHelper(new SetFlagsRule())
                .addEnableFlags(Flags.FLAG_FLAG_NAME3, Flags.FLAG_FLAG_NAME4)
                .addDisableFlags(Flags.FLAG_FLAG_NAME3)
                .prepareTest()
                .assertFails();
    }

    @Test
    public void conflictingAnnotationsAcrossMethodAndClassFails() {
        @DisableFlags(Flags.FLAG_FLAG_NAME3)
        class SomeClass {}
        new AnnotationTestRuleHelper(new SetFlagsRule())
                .setTestClass(SomeClass.class)
                .addEnableFlags(Flags.FLAG_FLAG_NAME3, Flags.FLAG_FLAG_NAME4)
                .prepareTest()
                .assertFails();
    }

    @Test
    public void canDuplicateFlagAcrossMethodAndClassAnnotations() {
        @EnableFlags(Flags.FLAG_FLAG_NAME3)
        class SomeClass {}
        new AnnotationTestRuleHelper(new SetFlagsRule())
                .setTestClass(SomeClass.class)
                .addEnableFlags(Flags.FLAG_FLAG_NAME3, Flags.FLAG_FLAG_NAME4)
                .setTestCode(
                        () -> {
                            assertTrue(Flags.flagName3());
                            assertTrue(Flags.flagName4());
                        })
                .prepareTest()
                .assertPasses();
    }

    @Test
    public void canSetFlagsWithClass() {
        @EnableFlags(Flags.FLAG_FLAG_NAME3)
        @DisableFlags(Flags.FLAG_FLAG_NAME4)
        class SomeClass {}
        new AnnotationTestRuleHelper(new SetFlagsRule())
                .setTestClass(SomeClass.class)
                .setTestCode(
                        () -> {
                            assertTrue(Flags.flagName3());
                            assertFalse(Flags.flagName4());
                        })
                .prepareTest()
                .assertPasses();
    }

    @Test
    public void canSetFlagsWithMethod() {
        new AnnotationTestRuleHelper(new SetFlagsRule())
                .addEnableFlags(Flags.FLAG_FLAG_NAME3)
                .addDisableFlags(Flags.FLAG_FLAG_NAME4)
                .setTestCode(
                        () -> {
                            assertTrue(Flags.flagName3());
                            assertFalse(Flags.flagName4());
                        })
                .prepareTest()
                .assertPasses();
    }

    @Test
    public void canSetFlagsWithClassAndMethod() {
        @EnableFlags(Flags.FLAG_FLAG_NAME3)
        @DisableFlags(Flags.FLAG_FLAG_NAME4)
        class SomeClass {}
        new AnnotationTestRuleHelper(new SetFlagsRule())
                .setTestClass(SomeClass.class)
                .addEnableFlags(Flags.FLAG_FLAG_NAME3)
                .addDisableFlags(Flags.FLAG_FLAG_NAME4)
                .setTestCode(
                        () -> {
                            assertTrue(Flags.flagName3());
                            assertFalse(Flags.flagName4());
                        })
                .prepareTest()
                .assertPasses();
    }

    @Test
    public void requiresFlagsOnClassPasses() {
        @RequiresFlagsEnabled(Flags.FLAG_FLAG_NAME3)
        @RequiresFlagsDisabled(Flags.FLAG_FLAG_NAME4)
        class SomeClass {}
        new AnnotationTestRuleHelper(new SetFlagsRule())
                .setTestClass(SomeClass.class)
                .prepareTest()
                .assertPasses();
    }

    @Test
    public void requiresFlagsOnMethodPasses() {
        new AnnotationTestRuleHelper(new SetFlagsRule())
                .addRequiresFlagsEnabled(Flags.FLAG_FLAG_NAME3)
                .addRequiresFlagsDisabled(Flags.FLAG_FLAG_NAME4)
                .prepareTest()
                .assertPasses();
    }

    @Test
    public void settingByAnnotationRequiredFlagsFails() {
        SetFlagsRule rule = new SetFlagsRule();
        new AnnotationTestRuleHelper(rule)
                .addRequiresFlagsEnabled(Flags.FLAG_FLAG_NAME3)
                .addEnableFlags(Flags.FLAG_FLAG_NAME3)
                .prepareTest()
                .assertFails();
    }

    @Test
    public void settingDirectlyRequiredFlagsFails() {
        SetFlagsRule rule = new SetFlagsRule();
        new AnnotationTestRuleHelper(rule)
                .addRequiresFlagsEnabled(Flags.FLAG_FLAG_NAME3)
                .setTestCode(
                        () -> {
                            rule.enableFlags(Flags.FLAG_FLAG_NAME3);
                        })
                .prepareTest()
                .assertFailsWithType(FlagSetException.class);
    }

    @Test
    public void settingAnyFlagBeforeRuleStartsThrows() {
        SetFlagsRule setFlagsRule = new SetFlagsRule(DEVICE_DEFAULT);
        try {
            setFlagsRule.enableFlags(Flags.FLAG_FLAG_NAME4);
        } catch (IllegalStateException ex) {
            return; // Test passes
        }
        fail("Should not be allowed to set flags before test starts.");
    }

    @Test
    public void settingAnyFlagAfterRuleFinishesThrows() {
        SetFlagsRule setFlagsRule = new SetFlagsRule(DEVICE_DEFAULT);
        new AnnotationTestRuleHelper(setFlagsRule).prepareTest().assertPasses();
        try {
            setFlagsRule.enableFlags(Flags.FLAG_FLAG_NAME4);
        } catch (IllegalStateException ex) {
            return; // Test passes
        }
        fail("Should not be allowed to set flags after test ends.");
    }

    @Test
    public void initAllFlagsToReleaseConfigDefault_worksOutsideOfTestCode() {
        SetFlagsRule setFlagsRule = new SetFlagsRule(NULL_DEFAULT);
        setFlagsRule.initAllFlagsToReleaseConfigDefault();
        new AnnotationTestRuleHelper(setFlagsRule)
                .setTestCode(
                        () -> {
                            setFlagsRule.enableFlags(Flags.FLAG_FLAG_NAME4);
                            assertFalse(Flags.flagName3());
                        })
                .prepareTest()
                .assertPasses();
    }

    @Test
    public void initAllFlagsToReleaseConfigDefault_worksInsideTestCode() {
        SetFlagsRule setFlagsRule = new SetFlagsRule(NULL_DEFAULT);
        new AnnotationTestRuleHelper(setFlagsRule)
                .setTestCode(
                        () -> {
                            setFlagsRule.initAllFlagsToReleaseConfigDefault();
                            setFlagsRule.enableFlags(Flags.FLAG_FLAG_NAME4);
                            assertFalse(Flags.flagName3());
                        })
                .prepareTest()
                .assertPasses();
    }

    @Test
    public void initAllFlagsToReleaseConfigDefault_failsAfterFlagIsSet() {
        SetFlagsRule setFlagsRule = new SetFlagsRule(NULL_DEFAULT);
        new AnnotationTestRuleHelper(setFlagsRule)
                .setTestCode(
                        () -> {
                            setFlagsRule.enableFlags(Flags.FLAG_FLAG_NAME4);
                            setFlagsRule.initAllFlagsToReleaseConfigDefault();
                        })
                .prepareTest()
                .assertFailsWithType(IllegalStateException.class);
    }

    @Test
    public void paramEnabledFlagsGetEnabled() {
        FlagsParameterization params =
                new FlagsParameterization(Map.of(Flags.FLAG_FLAG_NAME3, true));
        new AnnotationTestRuleHelper(new SetFlagsRule(DEVICE_DEFAULT, params))
                .setTestCode(
                        () -> {
                            assertTrue(Flags.flagName3());
                        })
                .prepareTest()
                .assertPasses();
    }

    @Test
    public void paramDisabledFlagsGetDisabled() {
        FlagsParameterization params =
                new FlagsParameterization(Map.of(Flags.FLAG_FLAG_NAME3, false));
        new AnnotationTestRuleHelper(new SetFlagsRule(DEVICE_DEFAULT, params))
                .setTestCode(
                        () -> {
                            assertFalse(Flags.flagName3());
                        })
                .prepareTest()
                .assertPasses();
    }

    @Test
    public void paramSetFlagsGetSet() {
        FlagsParameterization params =
                new FlagsParameterization(
                        Map.of(Flags.FLAG_FLAG_NAME3, true, Flags.FLAG_FLAG_NAME4, false));
        new AnnotationTestRuleHelper(new SetFlagsRule(DEVICE_DEFAULT, params))
                .setTestCode(
                        () -> {
                            assertTrue(Flags.flagName3());
                            assertFalse(Flags.flagName4());
                        })
                .prepareTest()
                .assertPasses();
    }

    @Test
    public void settingParameterizedFlagFails() {
        FlagsParameterization params =
                new FlagsParameterization(Map.of(Flags.FLAG_FLAG_NAME3, true));
        SetFlagsRule setFlagsRule = new SetFlagsRule(DEVICE_DEFAULT, params);
        new AnnotationTestRuleHelper(setFlagsRule)
                .setTestCode(
                        () -> {
                            setFlagsRule.enableFlags(Flags.FLAG_FLAG_NAME3);
                        })
                .prepareTest()
                .assertFailsWithType(FlagSetException.class);
    }

    @Test
    public void settingNonParameterizedFlagDirectlyWorks() {
        FlagsParameterization params =
                new FlagsParameterization(Map.of(Flags.FLAG_FLAG_NAME3, true));
        SetFlagsRule setFlagsRule = new SetFlagsRule(DEVICE_DEFAULT, params);
        new AnnotationTestRuleHelper(setFlagsRule)
                .setTestCode(
                        () -> {
                            setFlagsRule.enableFlags(Flags.FLAG_FLAG_NAME4);
                            assertTrue(Flags.flagName3());
                            assertTrue(Flags.flagName4());
                        })
                .prepareTest()
                .assertPasses();
    }

    @Test
    public void settingNonParameterizedFlagByAnnotationWorks() {
        FlagsParameterization params =
                new FlagsParameterization(Map.of(Flags.FLAG_FLAG_NAME3, true));
        new AnnotationTestRuleHelper(new SetFlagsRule(DEVICE_DEFAULT, params))
                .addEnableFlags(Flags.FLAG_FLAG_NAME4)
                .setTestCode(
                        () -> {
                            assertTrue(Flags.flagName3());
                            assertTrue(Flags.flagName4());
                        })
                .prepareTest()
                .assertPasses();
    }

    @Test
    public void paramEnabledFlagsCantBeRequiredEnabledByAnnotation() {
        FlagsParameterization params =
                new FlagsParameterization(Map.of(Flags.FLAG_FLAG_NAME3, true));
        new AnnotationTestRuleHelper(new SetFlagsRule(DEVICE_DEFAULT, params))
                .addRequiresFlagsEnabled(Flags.FLAG_FLAG_NAME3)
                .prepareTest()
                .assertFails();
    }

    @Test
    public void paramDisabledFlagsCantBeRequiredEnabledByAnnotation() {
        FlagsParameterization params =
                new FlagsParameterization(Map.of(Flags.FLAG_FLAG_NAME3, false));
        new AnnotationTestRuleHelper(new SetFlagsRule(DEVICE_DEFAULT, params))
                .addRequiresFlagsEnabled(Flags.FLAG_FLAG_NAME3)
                .prepareTest()
                .assertFails();
    }

    @Test
    public void paramEnabledFlagsRunWhenAnnotationEnablesFlag() {
        FlagsParameterization params =
                new FlagsParameterization(Map.of(Flags.FLAG_FLAG_NAME3, true));
        new AnnotationTestRuleHelper(new SetFlagsRule(DEVICE_DEFAULT, params))
                .addEnableFlags(Flags.FLAG_FLAG_NAME3)
                .setTestCode(
                        () -> {
                            assertTrue(Flags.flagName3());
                        })
                .prepareTest()
                .assertPasses();
    }

    @Test
    public void paramDisabledFlagsRunWhenAnnotationDisablesFlag() {
        FlagsParameterization params =
                new FlagsParameterization(Map.of(Flags.FLAG_FLAG_NAME3, false));
        new AnnotationTestRuleHelper(new SetFlagsRule(DEVICE_DEFAULT, params))
                .addDisableFlags(Flags.FLAG_FLAG_NAME3)
                .setTestCode(
                        () -> {
                            assertFalse(Flags.flagName3());
                        })
                .prepareTest()
                .assertPasses();
    }

    @Test
    public void paramDisabledFlagsSkipWhenAnnotationEnablesFlag() {
        FlagsParameterization params =
                new FlagsParameterization(Map.of(Flags.FLAG_FLAG_NAME3, false));
        new AnnotationTestRuleHelper(new SetFlagsRule(DEVICE_DEFAULT, params))
                .addEnableFlags(Flags.FLAG_FLAG_NAME3)
                .prepareTest()
                .assertSkipped();
    }

    @Test
    public void paramEnabledFlagsSkipWhenAnnotationDisablesFlag() {
        FlagsParameterization params =
                new FlagsParameterization(Map.of(Flags.FLAG_FLAG_NAME3, true));
        new AnnotationTestRuleHelper(new SetFlagsRule(DEVICE_DEFAULT, params))
                .addDisableFlags(Flags.FLAG_FLAG_NAME3)
                .prepareTest()
                .assertSkipped();
    }
}
