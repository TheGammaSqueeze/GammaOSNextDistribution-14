@Target(AnnotationTarget.TYPE)
@Retention(AnnotationRetention.SOURCE)
@MustBeDocumented
public annotation class Fancy

fun function(notInlined: @Fancy () -> Unit) {
}
