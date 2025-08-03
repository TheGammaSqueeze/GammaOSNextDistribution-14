/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.adservices.shared.common.flags;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Annotation used to indicate a constant defines the default value of a flag guarding a feature.
 */
@Retention(SOURCE)
@Target({FIELD})
public @interface FeatureFlag {
    Type value() default Type.DEFAULT;

    /** Types of feature flag - they define the (expected) value of the constant being annotated. */
    enum Type {
        /**
         * This is the default type for most feature flags - it means the feature flag guards a
         * feature at runtime - when the flag is {@code false}, the feature would be disabled.
         *
         * <p>The value of the field annotated by it should be {@code false}.
         */
        DEFAULT,

        /**
         * Used after a flag has been completely ramped up - its getter is always returning {@code
         * true} and eventually will be removed.
         *
         * <p>The value of the field annotated by it should be {@code true}, and should be marked as
         * {@code deprecated}.
         */
        RAMPED_UP,

        /**
         * The value of the field annotated by it should be dynamically set, based in the SDK level
         * of the device.
         */
        SDK_LEVEL_BASED,

        /**
         * Used on constants defined in the shared code package - the "real" runtime flag guarding
         * the feature will be independently set by different projects.
         *
         * <p>The value of the field annotated by it should be {@code false}.
         */
        SHARED,

        /**
         * Used for a legacy flags that followed the "kill-switch convention" (where the feature is
         * disabled when the flag is {@code true}.
         *
         * <p>Should only be used by the "global" flag that guards all features provide by the
         * mainline module.
         *
         * <p>The value of the field annotated by it should be {@code true}.
         */
        LEGACY_KILL_SWITCH_GLOBAL,

        /**
         * Used for legacy flags that followed the "kill-switch convention" (where the feature is
         * disabled when the flag is {@code true}.
         *
         * <p>Should only be used in flags that were already pushed to production (and are still
         * ramping up) and hence cannot be changed (into a "feature flag").
         *
         * <p>The value of the field annotated by it should be {@code true}.
         */
        LEGACY_KILL_SWITCH,

        /**
         * Same as {@link #LEGACY_KILL_SWITCH}, but used while its getters are converted to feature
         * flag getter and hence the value of the field annotated by it is {@code false} -
         * eventually the field will become a {@link #LEGACY_KILL_SWITCH}, and its value will be
         * {@code true}.
         *
         * @deprecated TODO(b/324077542) - remove once all kill-switches have been converted
         */
        @Deprecated
        LEGACY_KILL_SWITCH_BEING_CONVERTED,

        /**
         * Used for legacy flags that followed the "kill-switch convention" (where the feature is
         * disabled when the flag is {@code true}.
         *
         * <p>Should only be used in flags that were already pushed to production (and already
         * completely ramped up) and hence cannot be changed (into a "feature flag").
         *
         * <p>The value of the field annotated by it should be {@code false}.
         */
        LEGACY_KILL_SWITCH_RAMPED_UP,
    }
}
