/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tools.metalava

import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.testing.java
import com.android.tools.metalava.testing.kotlin
import org.junit.Test

class HideAnnotationTest : DriverTest() {
    // Regression test for b/133364476 crash
    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Using hide annotation with Kotlin source`() {
        check(
            expectedIssues =
                """
                src/test/pkg/ExtendingMyHiddenClass.kt:3: warning: Public class test.pkg.ExtendingMyHiddenClass stripped of unavailable superclass test.pkg.MyHiddenClass [HiddenSuperclass]
            """,
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                    package test.pkg
                    annotation class RegularHide
                """
                    ),
                    kotlin(
                        """
                    package test.pkg
                    @RegularHide
                    open class MyHiddenClass<T> {
                        @RegularHide
                        fun myHiddenFun(target: T, name: String) {}
                    }
                """
                    ),
                    kotlin(
                        """
                    package test.pkg
                    @OptIn(MyHiddenClass::class)
                    open class ExtendingMyHiddenClass<Float> : MyHiddenClass<Float>() {
                    }
                """
                    )
                ),
            hideAnnotations = arrayOf("test.pkg.RegularHide"),
            api =
                """
                package test.pkg {
                  public class ExtendingMyHiddenClass<Float> {
                    ctor public ExtendingMyHiddenClass();
                  }
                  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME) public @interface RegularHide {
                  }
                }
                """
        )
    }

    @Test
    fun `Using hide annotation interface order`() {
        check(
            expectedIssues =
                """
                src/test/pkg/InterfaceWithHiddenInterfaceFirst.java:2: warning: Public class test.pkg.InterfaceWithHiddenInterfaceFirst stripped of unavailable superclass test.pkg.HiddenInterface [HiddenSuperclass]
                src/test/pkg/InterfaceWithVisibleInterfaceFirst.java:2: warning: Public class test.pkg.InterfaceWithVisibleInterfaceFirst stripped of unavailable superclass test.pkg.HiddenInterface [HiddenSuperclass]
            """,
            sourceFiles =
                arrayOf(
                    java(
                        """
                    package test.pkg;
                    @Hide
                    public @interface Hide {}
                """
                    ),
                    java(
                        """
                    package test.pkg;
                    @Hide
                    public interface HiddenInterface {
                      void hiddenInterfaceMethod();
                    }
                """
                    ),
                    java(
                        """
                    package test.pkg;
                    public interface VisibleInterface {
                      void visibleInterfaceMethod();
                    }
                """
                    ),
                    java(
                        """
                    package test.pkg;
                    public interface InterfaceWithVisibleInterfaceFirst
                        extends VisibleInterface, HiddenInterface {}
                    """
                    ),
                    java(
                        """
                    package test.pkg;
                    public interface InterfaceWithHiddenInterfaceFirst
                        extends HiddenInterface, VisibleInterface {}
                    """
                    )
                ),
            hideAnnotations = arrayOf("test.pkg.Hide"),
            includeStrippedSuperclassWarnings = true,
            api =
                """
                package test.pkg {
                  public interface InterfaceWithHiddenInterfaceFirst extends test.pkg.VisibleInterface {
                  }
                  public interface InterfaceWithVisibleInterfaceFirst extends test.pkg.VisibleInterface {
                  }
                  public interface VisibleInterface {
                    method public void visibleInterfaceMethod();
                  }
                }
                """
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Using hide annotation on file scope`() {
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        @Target(AnnotationTarget.FILE)
                        annotation class HideFile
                    """
                    ),
                    kotlin(
                        """
                        @file:HideFile
                        package test.pkg

                        fun hiddenTopLevelFunction() = 1
                        var hiddenTopLevelProperty = 2
                        class VisibleTopLevelClass
                    """
                    )
                ),
            hideAnnotations = arrayOf("test.pkg.HideFile"),
            api =
                """
                package test.pkg {
                  @kotlin.annotation.Target(allowedTargets=kotlin.annotation.AnnotationTarget.FILE) public @interface HideFile {
                  }
                  public final class VisibleTopLevelClass {
                    ctor public VisibleTopLevelClass();
                  }
                }
                """
        )
    }
}
