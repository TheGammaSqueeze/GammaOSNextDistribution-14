package com.google.android.apifinder

import com.android.tools.lint.checks.infrastructure.LintDetectorTest
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Issue

@Suppress("UnstableApiUsage")
class ApiFinderDetectorTest : LintDetectorTest() {
    fun testJava() {
        lint()
            .files(
                java(
// TODO: Remove the explicit constructors once UCallExpression.resolve() can resolve generated
//  default constructors in Java.
                    """
package com.android.apifinder;

public class TestClass {
    public class PublicSubclass {
        public PublicSubclass() {}
        public void publicMethod() {}
        private void privateMethod() {}
    }

    private class PrivateSubclass {
        public PrivateSubclass() {}
        public void publicMethod() {}
    }

    public void testMethod() {
        PublicSubclass publicSubclass = new PublicSubclass();
        publicSubclass.publicMethod();
        publicSubclass.privateMethod();
        PrivateSubclass privateSubclass = new PrivateSubclass();
        privateSubclass.publicMethod();
    }
}
                    """
                ).indented()
            )
            .run()
            .expect(
                """
src/com/android/apifinder/TestClass.java:16: Information: ModuleMethod:com.android.apifinder.TestClass.PublicSubclass.TestClass.PublicSubclass() [JavaKotlinApiUsedByModule]
        PublicSubclass publicSubclass = new PublicSubclass();
                                        ~~~~~~~~~~~~~~~~~~~~
src/com/android/apifinder/TestClass.java:17: Information: ModuleMethod:com.android.apifinder.TestClass.PublicSubclass.publicMethod() [JavaKotlinApiUsedByModule]
        publicSubclass.publicMethod();
        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
0 errors, 2 warnings
                """
            )
    }

    fun testKotlin() {
        lint()
            .files(
                kotlin(
                    """
package com.android.apifinder

class TestClass {
    class PublicSubclass {
        fun publicMethod() {}
        private fun privateMethod() {}
    }

    private class PrivateSubclass {
        fun publicMethod() {}
    }

    fun testMethod() {
        val publicSubclass = PublicSubclass()
        publicSubclass.publicMethod()
        publicSubclass.privateMethod()
        val privateSubclass = PrivateSubclass()
        privateSubclass.publicMethod()
    }
}
                    """
                ).indented()
            )
            .run()
            .expect(
                """
src/com/android/apifinder/TestClass.kt:14: Information: ModuleMethod:com.android.apifinder.TestClass.PublicSubclass.TestClass.PublicSubclass() [JavaKotlinApiUsedByModule]
        val publicSubclass = PublicSubclass()
                             ~~~~~~~~~~~~~~~~
src/com/android/apifinder/TestClass.kt:15: Warning: ModuleMethod:com.android.apifinder.TestClass.PublicSubclass.publicMethod() [JavaKotlinApiUsedByModule]
        publicSubclass.publicMethod()
        ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
0 errors, 2 warnings
                """
            )
    }

    override fun getDetector(): Detector {
        return ApiFinderDetector()
    }

    override fun getIssues(): List<Issue> {
        return listOf(ApiFinderDetector.ISSUE)
    }
}
