package android.platform.test.rule

/** Checks if the class, or any of its superclasses, have [annotation]. */
fun <T> Class<T>?.hasAnnotation(annotation: Class<out Annotation>): Boolean =
    getLowestAncestorClassAnnotation(this, annotation) != null

/**
 * Return the lowest ancestor annotation matching [annotationClass].
 *
 * This assumes that a class is an ancestor of itself.
 */
fun <T, V : Annotation> getLowestAncestorClassAnnotation(
    testClass: Class<T>?,
    annotationClass: Class<V>,
): V? {
    return if (testClass == null) {
        null
    } else {
        testClass.getAnnotation(annotationClass)
            ?: getLowestAncestorClassAnnotation(testClass.superclass, annotationClass)
    }
}
