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

import com.android.tools.metalava.model.MergedCodebase
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.text.ApiFile
import com.android.tools.metalava.model.visitors.ApiVisitor
import org.junit.Assert.assertEquals
import org.junit.Test

class ComparisonVisitorTest {
    @Test
    fun `prefer first's real children even when first is only implied`() {
        val new =
            MergedCodebase(
                listOf(
                    ApiFile.parseApi(
                        "first.txt",
                        """
                        // Signature format: 2.0
                        package pkg {
                            public class Outer.Inner {
                                method public TypeInFirst foobar();
                            }
                        }
                        """
                            .trimIndent()
                    ),
                    ApiFile.parseApi(
                        "second.txt",
                        """
                        // Signature format: 2.0
                        package pkg {
                            public class Outer {
                            }
                            public class Outer.Inner {
                                method public TypeInSecond foobar();
                            }
                        }
                        """
                            .trimIndent()
                    )
                )
            )
        val old =
            MergedCodebase(
                listOf(
                    ApiFile.parseApi(
                        "old.txt",
                        """
                        // Signature format: 2.0
                        package pkg {
                            public class Outer {
                            }
                            public class Outer.Inner {
                            }
                        }
                        """
                            .trimIndent()
                    ),
                )
            )
        var methodType: String? = null
        CodebaseComparator(ApiVisitor.Config())
            .compare(
                object : ComparisonVisitor() {
                    override fun added(new: MethodItem) {
                        methodType = new.type().toSimpleType()
                    }
                },
                old,
                new
            )
        assertEquals("TypeInFirst", methodType)
    }
}
