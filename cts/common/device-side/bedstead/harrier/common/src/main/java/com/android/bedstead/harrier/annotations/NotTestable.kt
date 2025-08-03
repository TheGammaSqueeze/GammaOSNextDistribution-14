package com.android.bedstead.harrier.annotations

import org.junit.Ignore

/**
 * Mark that a test is not possible to write because of some missing test infrastructure.
 */
@Target(
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.CLASS
)
@Retention(
    AnnotationRetention.RUNTIME
)
@Ignore
annotation class NotTestable(
    val reason: String
)
