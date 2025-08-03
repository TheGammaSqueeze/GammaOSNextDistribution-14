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

import static org.junit.Assert.fail;

import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.util.FlagReadException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@code CheckFlagsRule}. */
@RunWith(JUnit4.class)
public final class CheckFlagsRuleTest {
    private final CheckFlagsRule mRule =
            new CheckFlagsRule(
                    new IFlagsValueProvider() {
                        @Override
                        public boolean getBoolean(String flag) throws FlagReadException {
                            switch (flag) {
                                case "flag0":
                                    return false;
                                case "flag1":
                                case "flag2":
                                    return true;
                                default:
                                    throw new FlagReadException(flag, "flag not defined");
                            }
                        }
                    });

    @Test
    public void emptyTestWithoutAnnotationsPasses() {
        new AnnotationTestRuleHelper(mRule).prepareTest().assertPasses();
    }

    @Test
    public void emptyFailingWithoutAnnotationsFails() {
        new AnnotationTestRuleHelper(mRule)
                .setTestCode(
                        () -> {
                            fail();
                        })
                .prepareTest()
                .assertFails();
    }

    @Test
    public void usingEnableFlagsOnClassForDifferentFlagPasses() {
        @EnableFlags("flag0")
        class SomeClass {}
        new AnnotationTestRuleHelper(mRule)
                .setTestClass(SomeClass.class)
                .addRequiresFlagsEnabled("flag1")
                .prepareTest()
                .assertPasses();
    }

    @Test
    public void usingDisableFlagsOnClassForDifferentFlagPasses() {
        @DisableFlags("flag0")
        class SomeClass {}
        new AnnotationTestRuleHelper(mRule)
                .setTestClass(SomeClass.class)
                .addRequiresFlagsEnabled("flag1")
                .prepareTest()
                .assertPasses();
    }

    @Test
    public void usingEnableFlagsOnMethodForDifferentFlagPasses() {
        new AnnotationTestRuleHelper(mRule)
                .addEnableFlags("flag0")
                .addRequiresFlagsEnabled("flag1")
                .prepareTest()
                .assertPasses();
    }

    @Test
    public void usingDisableFlagsOnMethodForDifferentFlagPasses() {
        new AnnotationTestRuleHelper(mRule)
                .addDisableFlags("flag0")
                .addRequiresFlagsEnabled("flag1")
                .prepareTest()
                .assertPasses();
    }

    @Test
    public void usingEnableFlagsOnClassForSameFlagFails() {
        @EnableFlags("flag1")
        class SomeClass {}
        new AnnotationTestRuleHelper(mRule)
                .setTestClass(SomeClass.class)
                .addRequiresFlagsEnabled("flag1")
                .prepareTest()
                .assertFails();
    }

    @Test
    public void usingDisableFlagsOnClassForSameFlagFails() {
        @DisableFlags("flag1")
        class SomeClass {}
        new AnnotationTestRuleHelper(mRule)
                .setTestClass(SomeClass.class)
                .addRequiresFlagsEnabled("flag1")
                .prepareTest()
                .assertFails();
    }

    @Test
    public void usingEnableFlagsOnMethodForSameFlagFails() {
        new AnnotationTestRuleHelper(mRule)
                .addEnableFlags("flag1")
                .addRequiresFlagsEnabled("flag1")
                .prepareTest()
                .assertFails();
    }

    @Test
    public void usingDisableFlagsOnMethodForSameFlagFails() {
        new AnnotationTestRuleHelper(mRule)
                .addDisableFlags("flag1")
                .addRequiresFlagsEnabled("flag1")
                .prepareTest()
                .assertFails();
    }

    @Test
    public void usingRequiresFlagsEnabledFlag1OnClassPasses() {
        @RequiresFlagsEnabled("flag1")
        class SomeClass {}
        new AnnotationTestRuleHelper(mRule)
                .setTestClass(SomeClass.class)
                .prepareTest()
                .assertPasses();
    }

    @Test
    public void usingRequiresFlagsDisabledFlag1OnClassSkipped() {
        @RequiresFlagsDisabled("flag1")
        class SomeClass {}
        new AnnotationTestRuleHelper(mRule)
                .setTestClass(SomeClass.class)
                .prepareTest()
                .assertSkipped();
    }

    @Test
    public void usingRequiresFlagsEnabledFlag1OnMethodPasses() {
        new AnnotationTestRuleHelper(mRule)
                .addRequiresFlagsEnabled("flag1")
                .prepareTest()
                .assertPasses();
    }

    @Test
    public void usingRequiresFlagsDisabledFlag1OnMethodSkipped() {
        new AnnotationTestRuleHelper(mRule)
                .addRequiresFlagsDisabled("flag1")
                .prepareTest()
                .assertSkipped();
    }

    @Test
    public void requiringNonexistentFlagEnabledFails() {
        new AnnotationTestRuleHelper(mRule)
                .addRequiresFlagsEnabled("nonexistent")
                .prepareTest()
                .assertFailsWithTypeAndMessage(FlagReadException.class, "nonexistent");
    }

    @Test
    public void requiringNonexistentFlagDisabledFails() {
        @RequiresFlagsDisabled("nonexistent")
        class SomeClass {}
        new AnnotationTestRuleHelper(mRule)
                .setTestClass(SomeClass.class)
                .prepareTest()
                .assertFailsWithTypeAndMessage(FlagReadException.class, "nonexistent");
    }

    @Test
    public void conflictingClassAnnotationsFails() {
        @RequiresFlagsEnabled({"flag1", "flag0"})
        @RequiresFlagsDisabled("flag1")
        class SomeClass {}
        new AnnotationTestRuleHelper(mRule)
                .setTestClass(SomeClass.class)
                .prepareTest()
                .assertFails();
    }

    @Test
    public void conflictingMethodAnnotationsFails() {
        new AnnotationTestRuleHelper(mRule)
                .addRequiresFlagsEnabled("flag1", "flag0")
                .addRequiresFlagsDisabled("flag1")
                .prepareTest()
                .assertFails();
    }

    @Test
    public void conflictingAnnotationsAcrossMethodAndClassFails() {
        @RequiresFlagsDisabled("flag1")
        class SomeClass {}
        new AnnotationTestRuleHelper(mRule)
                .setTestClass(SomeClass.class)
                .addRequiresFlagsEnabled("flag1", "flag0")
                .prepareTest()
                .assertFails();
    }

    @Test
    public void canDuplicateFlagAcrossMethodAndClassAnnotations() {
        @RequiresFlagsEnabled("flag1")
        class SomeClass {}
        new AnnotationTestRuleHelper(mRule)
                .setTestClass(SomeClass.class)
                .addRequiresFlagsEnabled("flag1", "flag2")
                .prepareTest()
                .assertPasses();
    }

    @Test
    public void requiringAllFlagsEnabledSkipped() {
        new AnnotationTestRuleHelper(mRule)
                .addRequiresFlagsEnabled("flag0", "flag1", "flag2")
                .prepareTest()
                .assertSkipped();
    }

    @Test
    public void mixedRequirementsWithOneMissedSkipped() {
        new AnnotationTestRuleHelper(mRule)
                .addRequiresFlagsEnabled("flag1")
                .addRequiresFlagsDisabled("flag0", "flag2")
                .prepareTest()
                .assertSkipped();
    }
}
