class Bar {
    fun replacementBarMethod(): Bar { return Bar() }

    fun badBarMethod(): DeprecatedBar { return DeprecatedBar() }

    /**
     * This method has been deprecated in favor of replacementBarMethod().
     */
    @Deprecated("Obsolete method", ReplaceWith("replacementBarMethod()"))
    fun goodBarMethod(): DeprecatedBar { return DeprecatedBar() }
}

/**
 * This class has been deprecated in favor of Bar.
 */
@Deprecated("Obsolete class", ReplaceWith("Bar"))
class DeprecatedBar