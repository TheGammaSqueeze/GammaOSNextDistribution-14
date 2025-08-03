/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.testing.java
import org.junit.Test

class NormalizeOutputAnnotationTest : DriverTest() {
    @Test
    fun `Normalize nested permission annotations (java)`() {
        check(
            sourceFiles =
                arrayOf(
                    java(
                            """
                    package test.pkg;

                    import android.annotation.RequiresPermission;

                    public final class PermissionsTest {
                        @RequiresPermission(Manifest.permission.MY_PERMISSION)
                        public void myMethod() {
                        }
                        @RequiresPermission(anyOf={Manifest.permission.MY_PERMISSION,Manifest.permission.MY_PERMISSION2})
                        public void myMethod2() {
                        }

                        @RequiresPermission.Read(@RequiresPermission(Manifest.permission.MY_READ_PERMISSION))
                        @RequiresPermission.Write(@RequiresPermission(Manifest.permission.MY_WRITE_PERMISSION))
                        public static final String CONTENT_URI = "";
                    }
                    """
                        )
                        .indented(),
                    java(
                            """
                    package test.pkg;

                    public class Manifest {
                        public static final class permission {
                            public static final String MY_PERMISSION = "android.permission.MY_PERMISSION_STRING";
                            public static final String MY_PERMISSION2 = "android.permission.MY_PERMISSION_STRING2";
                            public static final String MY_READ_PERMISSION = "android.permission.MY_READ_PERMISSION_STRING";
                            public static final String MY_WRITE_PERMISSION = "android.permission.MY_WRITE_PERMISSION_STRING";
                        }
                    }
                    """
                        )
                        .indented(),
                    requiresPermissionSource
                ),
            extraArguments =
                arrayOf(
                    ARG_HIDE_PACKAGE,
                    "android.annotation",
                ),
            format = FileFormat.V4,
            api =
                """
                // Signature format: 4.0
                package test.pkg {
                  public class Manifest {
                    ctor public Manifest();
                  }
                  public static final class Manifest.permission {
                    ctor public Manifest.permission();
                    field public static final String MY_PERMISSION = "android.permission.MY_PERMISSION_STRING";
                    field public static final String MY_PERMISSION2 = "android.permission.MY_PERMISSION_STRING2";
                    field public static final String MY_READ_PERMISSION = "android.permission.MY_READ_PERMISSION_STRING";
                    field public static final String MY_WRITE_PERMISSION = "android.permission.MY_WRITE_PERMISSION_STRING";
                  }
                  public final class PermissionsTest {
                    ctor public PermissionsTest();
                    method @RequiresPermission(test.pkg.Manifest.permission.MY_PERMISSION) public void myMethod();
                    method @RequiresPermission(anyOf={test.pkg.Manifest.permission.MY_PERMISSION, test.pkg.Manifest.permission.MY_PERMISSION2}) public void myMethod2();
                    field @RequiresPermission.Read(@androidx.annotation.RequiresPermission(test.pkg.Manifest.permission.MY_READ_PERMISSION)) @RequiresPermission.Write(@androidx.annotation.RequiresPermission(test.pkg.Manifest.permission.MY_WRITE_PERMISSION)) public static final String CONTENT_URI = "";
                  }
                }
            """
                    .trimIndent(),
        )
    }
}
