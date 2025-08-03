package android.signature.cts;

/**
 * Define the type of the signature check failures.
 */
public enum FailureType {
    MISSING_ANNOTATION,
    MISSING_CLASS,
    MISSING_INTERFACE,
    MISSING_CONSTRUCTOR,
    MISSING_METHOD,
    MISSING_FIELD,
    MISMATCH_CLASS,
    MISMATCH_INTERFACE,
    MISMATCH_INTERFACE_METHOD,
    MISMATCH_METHOD,
    MISMATCH_FIELD,
    UNEXPECTED_CLASS,
    EXTRA_CLASS,
    EXTRA_INTERFACE,
    EXTRA_CONSTRUCTOR,
    EXTRA_METHOD,
    EXTRA_FIELD,
    CAUGHT_EXCEPTION;

    static FailureType mismatch(JDiffClassDescription description) {
        return JDiffClassDescription.JDiffType.INTERFACE.equals(description.getClassType())
                ? FailureType.MISMATCH_INTERFACE : FailureType.MISMATCH_CLASS;
    }

    static FailureType missing(JDiffClassDescription description) {
        return JDiffClassDescription.JDiffType.INTERFACE.equals(description.getClassType())
                ? FailureType.MISSING_INTERFACE : FailureType.MISSING_CLASS;
    }

    static FailureType extra(Class<?> clazz) {
        return clazz.isInterface() ? FailureType.EXTRA_INTERFACE : FailureType.EXTRA_CLASS;
    }
}
