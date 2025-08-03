-keep @interface android.annotation.SystemApi
-keep @android.annotation.SystemApi public class * {
    public protected *;
}
-keepclasseswithmembers public class * {
    @android.annotation.SystemApi public protected <fields>;
}
-keepclasseswithmembers public class * {
    @android.annotation.SystemApi public protected <init>(...);
}
-keepclasseswithmembers public class * {
    @android.annotation.SystemApi public protected <methods>;
}
