/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tools.metalava.binarycompatibility

import com.android.tools.metalava.DriverTest
import com.android.tools.metalava.model.provider.Capability
import com.android.tools.metalava.model.testing.RequiresCapabilities
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.testing.kotlin
import org.junit.Test

class BinaryCompatibilityPackagesTest : DriverTest() {
    @Test
    fun `Add API Package (Compatible)`() {
        check(
            signatureSource =
                """
                package test.pkg {
                  public class Foo {}
                }
                package test.pkg.added {
                  public class Foo {}
                }
            """
                    .trimIndent(),
            format = FileFormat.V4,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Foo {}
                }
            """
                    .trimIndent()
        )
    }

    @Test
    fun `Delete API Package (Incompatible)`() {
        check(
            expectedIssues =
                """
                released-api.txt:5: error: Removed package test.pkg.removed [RemovedPackage]
            """
                    .trimIndent(),
            signatureSource =
                """
                package test.pkg {
                  public class Foo {}
                }
            """
                    .trimIndent(),
            format = FileFormat.V4,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Foo {}
                }
                package test.pkg.removed {
                  public class Foo {}
                }
            """
                    .trimIndent()
        )
    }

    @Test
    fun `Add API Type to API Package (Compatible)`() {
        check(
            signatureSource =
                """
                package test.pkg {
                  public class Foo {}
                  public class Bar {}
                }
            """
                    .trimIndent(),
            format = FileFormat.V4,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Foo {}
                }
            """
                    .trimIndent()
        )
    }

    @Test
    fun `Delete API Type from API Package (Incompatible)`() {
        check(
            expectedIssues =
                """
                released-api.txt:4: error: Removed class test.pkg.Bar [RemovedClass]
            """
                    .trimIndent(),
            signatureSource =
                """
                package test.pkg {
                  public class Foo {}
                }
            """
                    .trimIndent(),
            format = FileFormat.V4,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Foo {}
                  public class Bar {}
                }
            """
                    .trimIndent()
        )
    }

    @RequiresCapabilities(Capability.KOTLIN)
    @Test
    fun `Add non-public (non-API) type to API package (Compatible)`() {
        check(
            sourceFiles =
                arrayOf(
                    kotlin(
                        """
                        package test.pkg
                        private class Bar
                        class Foo
                    """
                            .trimIndent()
                    )
                ),
            format = FileFormat.V4,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Foo {}
                }
            """
                    .trimIndent()
        )
    }

    @Test
    fun `Change public type in API package to make non-public (Incompatible)`() {
        check(
            expectedIssues =
                """
                load-api.txt:3: error: Class test.pkg.Foo changed visibility from public to private [ChangedScope]
            """
                    .trimIndent(),
            signatureSource =
                """
                package test.pkg {
                  private class Foo {}
                  public class Bar {}
                }
            """
                    .trimIndent(),
            format = FileFormat.V4,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class Foo {}
                  public class Bar {}
                }
            """
                    .trimIndent()
        )
    }

    @Test
    fun `Change kind of API type (class, interface, enum, or annotation type) (Incompatible)`() {
        check(
            expectedIssues =
                """
                load-api.txt:12: error: Class test.pkg.AnnotationToClass changed class/interface declaration [ChangedClass]
                load-api.txt:14: error: Class test.pkg.AnnotationToEnum changed class/interface declaration [ChangedClass]
                load-api.txt:13: error: Class test.pkg.AnnotationToInterface changed class/interface declaration [ChangedClass]
                load-api.txt:5: error: Class test.pkg.ClassToAnnotation changed class/interface declaration [ChangedClass]
                load-api.txt:3: error: Class test.pkg.ClassToEnum changed class/interface declaration [ChangedClass]
                load-api.txt:4: error: Class test.pkg.ClassToInterface changed class/interface declaration [ChangedClass]
                load-api.txt:8: error: Class test.pkg.EnumToAnnotation changed class/interface declaration [ChangedClass]
                load-api.txt:6: error: Class test.pkg.EnumToClass changed class/interface declaration [ChangedClass]
                load-api.txt:7: error: Class test.pkg.EnumToInterface changed class/interface declaration [ChangedClass]
                load-api.txt:11: error: Class test.pkg.InterfaceToAnnotation changed class/interface declaration [ChangedClass]
                load-api.txt:9: error: Class test.pkg.InterfaceToClass changed class/interface declaration [ChangedClass]
                load-api.txt:10: error: Class test.pkg.InterfaceToEnum changed class/interface declaration [ChangedClass]
            """
                    .trimIndent(),
            signatureSource =
                """
                package test.pkg {
                  public enum ClassToEnum {}
                  public interface ClassToInterface {}
                  public @interface ClassToAnnotation {}
                  public class EnumToClass {}
                  public interface EnumToInterface {}
                  public @interface EnumToAnnotation {}
                  public class InterfaceToClass {}
                  public enum InterfaceToEnum {}
                  public @interface InterfaceToAnnotation {}
                  public class  AnnotationToClass {}
                  public interface AnnotationToInterface {}
                  public enum AnnotationToEnum {}
                }
            """
                    .trimIndent(),
            format = FileFormat.V4,
            checkCompatibilityApiReleased =
                """
                package test.pkg {
                  public class ClassToEnum {}
                  public class ClassToInterface {}
                  public class ClassToAnnotation {}
                  public enum EnumToClass {}
                  public enum EnumToInterface {}
                  public enum EnumToAnnotation {}
                  public interface InterfaceToClass {}
                  public interface InterfaceToEnum {}
                  public interface InterfaceToAnnotation {}
                  public @interface  AnnotationToClass {}
                  public @interface AnnotationToInterface {}
                  public @interface AnnotationToEnum {}
                }
            """
                    .trimIndent()
        )
    }
}
