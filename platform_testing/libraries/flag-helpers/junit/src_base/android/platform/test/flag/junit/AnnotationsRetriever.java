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

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.platform.test.annotations.RequiresFlagsDisabled;
import android.platform.test.annotations.RequiresFlagsEnabled;

import com.google.common.collect.Sets;

import org.junit.Test;
import org.junit.runner.Description;

import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

import javax.annotation.Nonnull;

/**
 * Retrieves feature flag related annotations from a given {@code Description}.
 *
 * <p>For each annotation, it trys to get it from the method first, then trys to get it from the
 * test class if the method has no such annotation.
 */
public class AnnotationsRetriever {
    private static final Set<Class<?>> KNOWN_UNRELATED_ANNOTATIONS =
            Set.of(Test.class, Retention.class, Target.class, Documented.class);

    private AnnotationsRetriever() {}

    /** Gets all feature flag related annotations. */
    public static FlagAnnotations getFlagAnnotations(Description description) {
        final Map<String, Boolean> requiresFlagValues =
                getMergedFlagValues(sRequiresFlagsEnabled, sRequiresFlagsDisabled, description);
        final Map<String, Boolean> setsFlagValues =
                getMergedFlagValues(sEnableFlags, sDisableFlags, description);

        // Assert that no flag is defined in both maps
        Set<String> inconsistentFlags =
                Sets.intersection(requiresFlagValues.keySet(), setsFlagValues.keySet());
        if (!inconsistentFlags.isEmpty()) {
            throw new AssertionError(
                    "The following flags are both required and set: " + inconsistentFlags);
        }

        return new FlagAnnotations(requiresFlagValues, setsFlagValues);
    }

    private static Map<String, Boolean> getMergedFlagValues(
            FlagsAnnotation<? extends Annotation> enabledAnnotation,
            FlagsAnnotation<? extends Annotation> disabledAnnotation,
            Description description) {
        final Map<String, Boolean> methodFlagValues =
                getFlagValues(
                        description.getMethodName(),
                        enabledAnnotation,
                        disabledAnnotation,
                        description.getAnnotations());
        Class<?> testClass = description.getTestClass();
        final Map<String, Boolean> classFlagValues =
                testClass == null
                        ? new HashMap<>()
                        : getFlagValues(
                                testClass.getName(),
                                enabledAnnotation,
                                disabledAnnotation,
                                List.of(testClass.getAnnotations()));
        Sets.SetView<String> doublyDefinedFlags =
                Sets.intersection(classFlagValues.keySet(), methodFlagValues.keySet());
        if (!doublyDefinedFlags.isEmpty()) {
            List<String> mismatchedFlags =
                    doublyDefinedFlags.stream()
                            .filter(
                                    flag ->
                                            !Objects.equals(
                                                    classFlagValues.get(flag),
                                                    methodFlagValues.get(flag)))
                            .toList();
            if (!mismatchedFlags.isEmpty()) {
                throw new AssertionError(
                        "The following flags are required by "
                                + description.getMethodName()
                                + " and "
                                + description.getClassName()
                                + " to be both enabled and disabled: "
                                + mismatchedFlags);
            }
        }
        // Now override the class values with the method values to produce a merged map
        classFlagValues.putAll(methodFlagValues);
        return classFlagValues;
    }

    private static Map<String, Boolean> getFlagValues(
            @Nonnull String annotationTarget,
            @Nonnull FlagsAnnotation<? extends Annotation> enabledAnnotation,
            @Nonnull FlagsAnnotation<? extends Annotation> disabledAnnotation,
            @Nonnull Collection<Annotation> annotations) {
        Set<String> enabledFlags = getFlagsForAnnotation(enabledAnnotation, annotations);
        Set<String> disabledFlags = getFlagsForAnnotation(disabledAnnotation, annotations);
        if (enabledFlags.isEmpty() && disabledFlags.isEmpty()) {
            return new HashMap<>();
        }
        Set<String> inconsistentFlags = Sets.intersection(enabledFlags, disabledFlags);
        if (!inconsistentFlags.isEmpty()) {
            throw new AssertionError(
                    "The following flags are required by "
                            + annotationTarget
                            + " to be both enabled and disabled: "
                            + inconsistentFlags);
        }
        HashMap<String, Boolean> result = new HashMap<>();
        for (String enabledFlag : enabledFlags) {
            result.put(enabledFlag, true);
        }
        for (String disabledFlag : disabledFlags) {
            result.put(disabledFlag, false);
        }
        return result;
    }

    @Nonnull
    private static <T extends Annotation> Set<String> getFlagsForAnnotation(
            FlagsAnnotation<T> flagsAnnotation, Collection<Annotation> annotations) {
        Class<T> annotationType = flagsAnnotation.mAnnotationType;
        Set<String> results = new HashSet<>();
        Queue<Annotation> annotationQueue = new ArrayDeque<>();
        Set<Class<? extends Annotation>> visitedAnnotations = new HashSet<>();
        annotationQueue.addAll(annotations);
        while (!annotationQueue.isEmpty()) {
            Annotation annotation = annotationQueue.poll();
            Class<? extends Annotation> currentAnnotationType = annotation.annotationType();
            if (currentAnnotationType.equals(annotationType)) {
                results.addAll(flagsAnnotation.getFlagsSet((T) annotation));
            } else if (!KNOWN_UNRELATED_ANNOTATIONS.contains(currentAnnotationType)
                    && !visitedAnnotations.contains(currentAnnotationType)) {
                annotationQueue.addAll(List.of(annotation.annotationType().getAnnotations()));
                visitedAnnotations.add(currentAnnotationType);
            }
        }
        return results;
    }

    /** Contains all feature flag related annotations. */
    public static class FlagAnnotations {

        /** The flag names which have required values, mapped to the value they require */
        public @Nonnull Map<String, Boolean> mRequiredFlagValues;

        /** The flag names which have values to be set, mapped to the value they set */
        public @Nonnull Map<String, Boolean> mSetFlagValues;

        FlagAnnotations(
                @Nonnull Map<String, Boolean> requiredFlagValues,
                @Nonnull Map<String, Boolean> setFlagValues) {
            mRequiredFlagValues = requiredFlagValues;
            mSetFlagValues = setFlagValues;
        }

        /**
         * Check that all @RequiresFlagsEnabled and @RequiresFlagsDisabled annotations match the
         * values from the provider, and if this is not true, throw {@link
         * org.junit.AssumptionViolatedException}
         *
         * @param valueProvider the value provider
         */
        public void assumeAllRequiredFlagsMatchProvider(IFlagsValueProvider valueProvider) {
            for (Map.Entry<String, Boolean> required : mRequiredFlagValues.entrySet()) {
                final String flag = required.getKey();
                if (required.getValue()) {
                    assumeTrue(
                            String.format("Flag %s required to be enabled, but is disabled", flag),
                            valueProvider.getBoolean(flag));
                } else {
                    assumeFalse(
                            String.format("Flag %s required to be disabled, but is enabled", flag),
                            valueProvider.getBoolean(flag));
                }
            }
        }

        /**
         * Check that all @EnableFlags and @DisableFlags annotations match the values contained in
         * the parameterization (if present), and if this is not true, throw {@link
         * org.junit.AssumptionViolatedException}
         *
         * @param params the parameterization to evaluate against (optional)
         */
        public void assumeAllSetFlagsMatchParameterization(FlagsParameterization params) {
            if (params == null) return;
            for (Map.Entry<String, Boolean> toSet : mSetFlagValues.entrySet()) {
                final String flag = toSet.getKey();
                final Boolean paramValue = params.mOverrides.get(flag);
                if (paramValue == null) continue;
                if (toSet.getValue()) {
                    assumeTrue(
                            String.format(
                                    "Flag %s is enabled by annotation but disabled by the current"
                                            + " FlagsParameterization; skipping test",
                                    flag),
                            paramValue);
                } else {
                    assumeFalse(
                            String.format(
                                    "Flag %s is disabled by annotation but enabled by the current"
                                            + " FlagsParameterization; skipping test",
                                    flag),
                            paramValue);
                }
            }
        }
    }

    private abstract static class FlagsAnnotation<T extends Annotation> {
        Class<T> mAnnotationType;

        FlagsAnnotation(Class<T> type) {
            mAnnotationType = type;
        }

        protected abstract String[] getFlags(T annotation);

        @Nonnull
        Set<String> getFlagsSet(T annotation) {
            String[] flags = getFlags(annotation);
            return flags == null ? Set.of() : Set.of(flags);
        }
    }

    private static final FlagsAnnotation<RequiresFlagsEnabled> sRequiresFlagsEnabled =
            new FlagsAnnotation<>(RequiresFlagsEnabled.class) {
                @Override
                protected String[] getFlags(RequiresFlagsEnabled annotation) {
                    return annotation.value();
                }
            };
    private static final FlagsAnnotation<RequiresFlagsDisabled> sRequiresFlagsDisabled =
            new FlagsAnnotation<>(RequiresFlagsDisabled.class) {
                @Override
                protected String[] getFlags(RequiresFlagsDisabled annotation) {
                    return annotation.value();
                }
            };
    private static final FlagsAnnotation<EnableFlags> sEnableFlags =
            new FlagsAnnotation<>(EnableFlags.class) {
                @Override
                protected String[] getFlags(EnableFlags annotation) {
                    return annotation.value();
                }
            };
    private static final FlagsAnnotation<DisableFlags> sDisableFlags =
            new FlagsAnnotation<>(DisableFlags.class) {
                @Override
                protected String[] getFlags(DisableFlags annotation) {
                    return annotation.value();
                }
            };
}
