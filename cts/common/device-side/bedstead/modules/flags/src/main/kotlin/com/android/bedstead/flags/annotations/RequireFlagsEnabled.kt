package com.android.bedstead.flags.annotations

import com.android.bedstead.flags.FlagsAnnotationExecutor
import com.android.bedstead.harrier.annotations.UsesAnnotationExecutor

/**
 * Indicates that a specific test or class should be run only if all of the given feature flags are
 * enabled in the device's current state.
 *
 * <p>This annotation works together with [RequiresFlagsDisabled] to define the value that is
 * required of the flag by the test for the test to run. It is an error for either a method or class
 * to require that a particular flag be both enabled and disabled.
 *
 * <p>If the value of a particular flag is required (by either [RequireFlagsEnabled] or
 * [RequiresFlagsDisabled]) by both the class and test method, then the values must be
 * consistent.
 *
 * <p>If the value of a one flag is required by an annotation on the class, and the value of a
 * different flag is required by an annotation of the method, then both requirements apply.
 *
 * This is a replacement for [android.platform.test.annotations.RequiresFlagsEnabled] which can be
 * enforced by default using [DeviceState] rather than requiring a separate rule.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE, AnnotationTarget.CLASS)
@UsesAnnotationExecutor(FlagsAnnotationExecutor::class)
annotation class RequireFlagsEnabled(
    /**
     * The list of the feature flags that require to be enabled. Each item is the full flag name
     * with the format {package_name}.{flag_name}.
     */
    vararg val value: String
)