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

import static org.junit.Assert.assertEquals;

import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;

import com.google.auto.value.AutoAnnotation;

import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Map;

@RunWith(JUnit4.class)
public class AnnotationsRetrieverTest {
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.METHOD, ElementType.TYPE})
    @RequiresFlagsEnabled("flag1")
    @RequiresFlagsDisabled("flag2")
    public @interface CompositeFlagRequirements {}

    static class TestClassHasNoAnnotation {}

    @RequiresFlagsEnabled({"flag1", "flag2"})
    static class TestClassHasRequiresFlagsEnabled {
    }

    @RequiresFlagsDisabled({"flag3", "flag4"})
    static class TestClassHasRequiresFlagsDisabled {
    }

    @RequiresFlagsEnabled({"flag1", "flag2"})
    @RequiresFlagsDisabled({"flag3", "flag4"})
    static class TestClassHasAllAnnotations {}

    @RequiresFlagsEnabled({"flag1"})
    @RequiresFlagsDisabled({"flag1"})
    static class TestClassHasConflictingAnnotations {}

    private final RequiresFlagsEnabled mRequiresFlagsEnabled =
            createRequiresFlagsEnabled(new String[]{"flag5"});

    private final RequiresFlagsDisabled mRequiresFlagsDisabled =
            createRequiresFlagsDisabled(new String[]{"flag6"});

    @Test
    public void noAnnotation() {
        AnnotationsRetriever.FlagAnnotations flagAnnotations =
                getFlagAnnotations(TestClassHasNoAnnotation.class);

        assertEquals(Map.of(), flagAnnotations.mRequiredFlagValues);
    }

    @Test
    public void oneAnnotationFromMethod() {
        AnnotationsRetriever.FlagAnnotations flagAnnotations1 =
                getFlagAnnotations(TestClassHasNoAnnotation.class, mRequiresFlagsEnabled);
        AnnotationsRetriever.FlagAnnotations flagAnnotations2 =
                getFlagAnnotations(TestClassHasNoAnnotation.class, mRequiresFlagsDisabled);

        assertEquals(Map.of("flag5", true), flagAnnotations1.mRequiredFlagValues);
        assertEquals(Map.of("flag6", false), flagAnnotations2.mRequiredFlagValues);
    }

    @Test
    public void methodAnnotationsMergeWithClass() {
        AnnotationsRetriever.FlagAnnotations flagAnnotations1 =
                getFlagAnnotations(TestClassHasRequiresFlagsEnabled.class, mRequiresFlagsEnabled);
        AnnotationsRetriever.FlagAnnotations flagAnnotations2 =
                getFlagAnnotations(TestClassHasRequiresFlagsDisabled.class, mRequiresFlagsDisabled);

        assertEquals(
                Map.of("flag1", true, "flag2", true, "flag5", true),
                flagAnnotations1.mRequiredFlagValues);
        assertEquals(
                Map.of("flag3", false, "flag4", false, "flag6", false),
                flagAnnotations2.mRequiredFlagValues);
    }

    @Test
    public void getFlagAnnotations_oneAnnotationFromClass() {
        AnnotationsRetriever.FlagAnnotations flagAnnotations1 =
                getFlagAnnotations(TestClassHasRequiresFlagsEnabled.class);
        AnnotationsRetriever.FlagAnnotations flagAnnotations2 =
                getFlagAnnotations(TestClassHasRequiresFlagsDisabled.class);

        assertEquals(Map.of("flag1", true, "flag2", true), flagAnnotations1.mRequiredFlagValues);
        assertEquals(Map.of("flag3", false, "flag4", false), flagAnnotations2.mRequiredFlagValues);
    }

    @Test
    public void bothAnnotationsFromMethod() {
        AnnotationsRetriever.FlagAnnotations flagAnnotations =
                getFlagAnnotations(
                        TestClassHasNoAnnotation.class,
                        mRequiresFlagsEnabled,
                        mRequiresFlagsDisabled);

        assertEquals(Map.of("flag5", true, "flag6", false), flagAnnotations.mRequiredFlagValues);
    }

    @Test
    public void bothAnnotationsFromMethodMergesWithClass() {
        AnnotationsRetriever.FlagAnnotations flagAnnotations =
                getFlagAnnotations(
                        TestClassHasAllAnnotations.class,
                        mRequiresFlagsEnabled,
                        mRequiresFlagsDisabled);

        assertEquals(
                Map.of(
                        "flag1", true, "flag2", true, "flag3", false, "flag4", false, "flag5", true,
                        "flag6", false),
                flagAnnotations.mRequiredFlagValues);
    }

    @Test
    public void bothAnnotationsFromClass() {
        AnnotationsRetriever.FlagAnnotations flagAnnotations =
                getFlagAnnotations(TestClassHasAllAnnotations.class);

        assertEquals(
                Map.of("flag1", true, "flag2", true, "flag3", false, "flag4", false),
                flagAnnotations.mRequiredFlagValues);
    }

    @Test
    public void bothAnnotationsFromClassAndOneFromMethod() {
        AnnotationsRetriever.FlagAnnotations flagAnnotations =
                getFlagAnnotations(TestClassHasAllAnnotations.class, mRequiresFlagsEnabled);

        assertEquals(
                Map.of("flag1", true, "flag2", true, "flag3", false, "flag4", false, "flag5", true),
                flagAnnotations.mRequiredFlagValues);
    }

    @Test(expected = AssertionError.class)
    public void conflictingClassAnnotationsThrows() {
        getFlagAnnotations(TestClassHasConflictingAnnotations.class);
    }

    @Test(expected = AssertionError.class)
    public void conflictingMethodAnnotationsThrows() {
        getFlagAnnotations(
                TestClassHasNoAnnotation.class,
                createRequiresFlagsEnabled(new String[] {"flag1"}),
                createRequiresFlagsDisabled(new String[] {"flag1"}));
    }

    @Test(expected = AssertionError.class)
    public void methodValuesFailsOnOverrideClassValues() {
        getFlagAnnotations(
                TestClassHasAllAnnotations.class,
                createRequiresFlagsEnabled(new String[] {"flag3"}),
                createRequiresFlagsDisabled(new String[] {"flag1"}));
    }

    @Test
    public void getFlagAnnotationsRecursively() {
        AnnotationsRetriever.FlagAnnotations flagAnnotations =
                getFlagAnnotations(
                        TestClassHasNoAnnotation.class, createCompositeFlagRequirements());

        assertEquals(Map.of("flag1", true, "flag2", false), flagAnnotations.mRequiredFlagValues);
    }

    @Test(expected = AssertionError.class)
    public void getFlagAnnotationsRecursivelyFailsOnOverride() {
        getFlagAnnotations(TestClassHasAllAnnotations.class, createCompositeFlagRequirements());
    }

    @Test
    public void getFlagAnnotationsRecursivelyMergesWithClass() {
        AnnotationsRetriever.FlagAnnotations flagAnnotations =
                getFlagAnnotations(
                        TestClassHasRequiresFlagsDisabled.class, createCompositeFlagRequirements());

        assertEquals(
                Map.of("flag1", true, "flag2", false, "flag3", false, "flag4", false),
                flagAnnotations.mRequiredFlagValues);
    }

    private AnnotationsRetriever.FlagAnnotations getFlagAnnotations(
            Class<?> testClass, Annotation... annotations) {
        Description description =
                Description.createTestDescription(testClass, "testMethod", annotations);
        return AnnotationsRetriever.getFlagAnnotations(description);
    }

    @AutoAnnotation
    private static RequiresFlagsEnabled createRequiresFlagsEnabled(String[] value) {
        return new AutoAnnotation_AnnotationsRetrieverTest_createRequiresFlagsEnabled(value);
    }

    @AutoAnnotation
    private static RequiresFlagsDisabled createRequiresFlagsDisabled(String[] value) {
        return new AutoAnnotation_AnnotationsRetrieverTest_createRequiresFlagsDisabled(value);
    }

    @AutoAnnotation
    private static CompositeFlagRequirements createCompositeFlagRequirements() {
        return new AutoAnnotation_AnnotationsRetrieverTest_createCompositeFlagRequirements();
    }
}
