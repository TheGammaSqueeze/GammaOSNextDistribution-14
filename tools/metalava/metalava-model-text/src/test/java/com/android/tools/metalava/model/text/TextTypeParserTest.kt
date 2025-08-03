/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.DefaultAnnotationItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.TypeParameterScope
import com.google.common.truth.Truth.assertThat
import org.junit.Assert
import org.junit.Test

class TextTypeParserTest : BaseTextCodebaseTest() {
    private val typeParser = TextTypeParser(ApiFile.parseApi("test", ""))

    private fun parseType(type: String) =
        typeParser.obtainTypeFromString(type, TypeParameterScope.empty)

    @Test
    fun `Test type parameter strings`() {
        assertThat(TextTypeParser.typeParameterStrings(null).toString()).isEqualTo("[]")
        assertThat(TextTypeParser.typeParameterStrings("").toString()).isEqualTo("[]")
        assertThat(TextTypeParser.typeParameterStrings("<X>").toString()).isEqualTo("[X]")
        assertThat(TextTypeParser.typeParameterStrings("<ABC,DEF extends T>").toString())
            .isEqualTo("[ABC, DEF extends T]")
        assertThat(
                TextTypeParser.typeParameterStrings("<T extends java.lang.Comparable<? super T>>")
                    .toString()
            )
            .isEqualTo("[T extends java.lang.Comparable<? super T>]")
        assertThat(
                TextTypeParser.typeParameterStrings("<java.util.List<java.lang.String>[]>")
                    .toString()
            )
            .isEqualTo("[java.util.List<java.lang.String>[]]")
    }

    @Test
    fun `Test type parameter strings with annotations`() {
        assertThat(
                TextTypeParser.typeParameterStrings(
                    "<java.lang.@androidx.annotation.IntRange(from=5,to=10) Integer>"
                )
            )
            .containsExactly("java.lang.@androidx.annotation.IntRange(from=5,to=10) Integer")
        assertThat(TextTypeParser.typeParameterStrings("<@test.pkg.C String>"))
            .containsExactly("@test.pkg.C String")
        assertThat(
                TextTypeParser.typeParameterStrings(
                    "<java.lang.@androidx.annotation.IntRange(from=5,to=10) Integer, @test.pkg.C String>"
                )
            )
            .containsExactly(
                "java.lang.@androidx.annotation.IntRange(from=5,to=10) Integer",
                "@test.pkg.C String"
            )
    }

    @Test
    fun `Test type parameter strings with remainder`() {
        assertThat(TextTypeParser.typeParameterStringsWithRemainder(null))
            .isEqualTo(Pair(emptyList<String>(), null))
        assertThat(TextTypeParser.typeParameterStringsWithRemainder(""))
            .isEqualTo(Pair(emptyList<String>(), ""))
        assertThat(TextTypeParser.typeParameterStringsWithRemainder("<X>"))
            .isEqualTo(Pair(listOf("X"), null))
        assertThat(TextTypeParser.typeParameterStringsWithRemainder("<X>.Inner"))
            .isEqualTo(Pair(listOf("X"), ".Inner"))
        assertThat(TextTypeParser.typeParameterStringsWithRemainder("<X, Y, Z>.Inner<A, B, C>"))
            .isEqualTo(Pair(listOf("X", "Y", "Z"), ".Inner<A, B, C>"))
    }

    @Test
    fun `Test splitting Kotlin nullability suffix`() {
        assertThat(TextTypeParser.splitNullabilitySuffix("String!", true))
            .isEqualTo(Pair("String", TypeNullability.PLATFORM))
        assertThat(TextTypeParser.splitNullabilitySuffix("String?", true))
            .isEqualTo(Pair("String", TypeNullability.NULLABLE))
        assertThat(TextTypeParser.splitNullabilitySuffix("String", true))
            .isEqualTo(Pair("String", TypeNullability.NONNULL))
        // Check that wildcards work
        assertThat(TextTypeParser.splitNullabilitySuffix("?", true))
            .isEqualTo(Pair("?", TypeNullability.UNDEFINED))
        assertThat(TextTypeParser.splitNullabilitySuffix("T", true))
            .isEqualTo(Pair("T", TypeNullability.NONNULL))
    }

    @Test
    fun `Test splitting Kotlin nullability suffix when kotlinStyleNulls is false`() {
        assertThat(TextTypeParser.splitNullabilitySuffix("String", false))
            .isEqualTo(Pair("String", null))
        assertThat(TextTypeParser.splitNullabilitySuffix("?", false)).isEqualTo(Pair("?", null))

        Assert.assertThrows(
            "Format does not support Kotlin-style null type syntax: String!",
            ApiParseException::class.java
        ) {
            TextTypeParser.splitNullabilitySuffix("String!", false)
        }
        Assert.assertThrows(
            "Format does not support Kotlin-style null type syntax: String?",
            ApiParseException::class.java
        ) {
            TextTypeParser.splitNullabilitySuffix("String?", false)
        }
    }

    /**
     * Tests that calling [annotationFunction] on [original] splits the string into a pair
     * containing the [expectedType] and [expectedAnnotations]
     */
    private fun testAnnotations(
        original: String,
        expectedType: String,
        expectedAnnotations: List<String>,
        annotationFunction: (String) -> Pair<String, List<AnnotationItem>>
    ) {
        val (type, annotations) = annotationFunction(original)
        assertThat(type).isEqualTo(expectedType)
        val expectedAnnotationItems =
            expectedAnnotations.map { DefaultAnnotationItem.create(typeParser.codebase, it) }
        assertThat(annotations).isEqualTo(expectedAnnotationItems)
    }

    @Test
    fun `Test trimming annotations from the front of a type`() {
        // Works with no annotations
        testAnnotations(
            original = "java.util.List",
            expectedType = "java.util.List",
            expectedAnnotations = emptyList(),
            typeParser::trimLeadingAnnotations
        )

        // Annotations not at the start of the type aren't trimmed
        testAnnotations(
            original = "java.util.@libcore.util.Nullable List",
            expectedType = "java.util.@libcore.util.Nullable List",
            expectedAnnotations = emptyList(),
            typeParser::trimLeadingAnnotations
        )
        testAnnotations(
            original = "java.util.List @libcore.util.Nullable",
            expectedType = "java.util.List @libcore.util.Nullable",
            expectedAnnotations = emptyList(),
            typeParser::trimLeadingAnnotations
        )

        // Trimming annotations from the start
        testAnnotations(
            original = "@libcore.util.Nullable java.util.List",
            expectedType = "java.util.List",
            expectedAnnotations = listOf("@libcore.util.Nullable"),
            typeParser::trimLeadingAnnotations
        )
        testAnnotations(
            original = " @libcore.util.Nullable java.util.List ",
            expectedType = "java.util.List",
            expectedAnnotations = listOf("@libcore.util.Nullable"),
            typeParser::trimLeadingAnnotations
        )
        testAnnotations(
            original = "@test.pkg.A @test.pkg.B java.util.List",
            expectedType = "java.util.List",
            expectedAnnotations = listOf("@test.pkg.A", "@test.pkg.B"),
            typeParser::trimLeadingAnnotations
        )
        testAnnotations(
            original = "@test.pkg.A(a = \"hi@\", b = 0) java.util.List",
            expectedType = "java.util.List",
            expectedAnnotations = listOf("@test.pkg.A(a = \"hi@\", b = 0)"),
            typeParser::trimLeadingAnnotations
        )
        testAnnotations(
            original = "@test.pkg.A(a = \"hi@\", b = 0) @test.pkg.B(v = \"\") java.util.List",
            expectedType = "java.util.List",
            expectedAnnotations =
                listOf("@test.pkg.A(a = \"hi@\", b = 0)", "@test.pkg.B(v = \"\")"),
            typeParser::trimLeadingAnnotations
        )
        testAnnotations(
            original = "@test.pkg.A @test.pkg.B java.util.List<java.lang.@test.pkg.C String>",
            expectedType = "java.util.List<java.lang.@test.pkg.C String>",
            expectedAnnotations = listOf("@test.pkg.A", "@test.pkg.B"),
            typeParser::trimLeadingAnnotations
        )
    }

    @Test
    fun `Test trimming annotations from the end of a type`() {
        // Works with no annotations
        testAnnotations(
            original = "java.util.List",
            expectedType = "java.util.List",
            expectedAnnotations = emptyList(),
            typeParser::trimTrailingAnnotations
        )

        // Annotations that aren't at the end aren't trimmed
        testAnnotations(
            original = "java.util.@libcore.util.Nullable List",
            expectedType = "java.util.@libcore.util.Nullable List",
            expectedAnnotations = emptyList(),
            typeParser::trimTrailingAnnotations
        )
        testAnnotations(
            original = "@libcore.util.Nullable java.util.List",
            expectedType = "@libcore.util.Nullable java.util.List",
            expectedAnnotations = emptyList(),
            typeParser::trimTrailingAnnotations
        )

        // Trimming annotations from the end
        testAnnotations(
            original = "java.util.List @libcore.util.Nullable",
            expectedType = "java.util.List",
            expectedAnnotations = listOf("@libcore.util.Nullable"),
            typeParser::trimTrailingAnnotations
        )
        testAnnotations(
            original = " java.util.List @libcore.util.Nullable ",
            expectedType = "java.util.List",
            expectedAnnotations = listOf("@libcore.util.Nullable"),
            typeParser::trimTrailingAnnotations
        )
        testAnnotations(
            original = "java.util.List @test.pkg.A @test.pkg.B",
            expectedType = "java.util.List",
            expectedAnnotations = listOf("@test.pkg.A", "@test.pkg.B"),
            typeParser::trimTrailingAnnotations
        )

        // Verify that annotations at the end with `@`s in them work correctly.
        testAnnotations(
            original = "java.util.List @test.pkg.A(a = \"hi@\", b = 0)",
            expectedType = "java.util.List",
            expectedAnnotations = listOf("@test.pkg.A(a = \"hi@\", b = 0)"),
            typeParser::trimTrailingAnnotations
        )
        testAnnotations(
            original = "java.util.List @test.pkg.A(a = \"hi@\", b = 0) @test.pkg.B(v = \"\")",
            expectedType = "java.util.List",
            expectedAnnotations =
                listOf("@test.pkg.A(a = \"hi@\", b = 0)", "@test.pkg.B(v = \"\")"),
            typeParser::trimTrailingAnnotations
        )
        testAnnotations(
            original = "java.util.@test.pkg.A List<java.lang.@text.pkg.B String> @test.pkg.C",
            expectedType = "java.util.@test.pkg.A List<java.lang.@text.pkg.B String>",
            expectedAnnotations = listOf("@test.pkg.C"),
            typeParser::trimTrailingAnnotations
        )
    }

    /**
     * Verifies that calling [TextTypeParser.splitClassType] returns the triple of
     * [expectedClassName], [expectedParams], [expectedAnnotations].
     */
    private fun testClassAnnotations(
        original: String,
        expectedClassName: String,
        expectedParams: String?,
        expectedAnnotations: List<String>
    ) {
        val (className, params, annotations) = typeParser.splitClassType(original)
        assertThat(className).isEqualTo(expectedClassName)
        assertThat(params).isEqualTo(expectedParams)
        val expectedAnnotationItems =
            expectedAnnotations.map { DefaultAnnotationItem.create(typeParser.codebase, it) }
        assertThat(annotations).isEqualTo(expectedAnnotationItems)
    }

    @Test
    fun `Test trimming annotations from a class type`() {
        testClassAnnotations(
            original = "java.lang.String",
            expectedClassName = "java.lang.String",
            expectedParams = null,
            expectedAnnotations = emptyList()
        )
        testClassAnnotations(
            original = "java.util.List<java.lang.String>",
            expectedClassName = "java.util.List",
            expectedParams = "<java.lang.String>",
            expectedAnnotations = emptyList()
        )
        testClassAnnotations(
            original = "java.lang.@libcore.util.Nullable String",
            expectedClassName = "java.lang.String",
            expectedParams = null,
            expectedAnnotations = listOf("@libcore.util.Nullable")
        )
        testClassAnnotations(
            original = "java.util.@libcore.util.Nullable List<java.lang.String>",
            expectedClassName = "java.util.List",
            expectedParams = "<java.lang.String>",
            expectedAnnotations = listOf("@libcore.util.Nullable")
        )
        testClassAnnotations(
            original = "java.lang.annotation.@libcore.util.NonNull Annotation",
            expectedClassName = "java.lang.annotation.Annotation",
            expectedParams = null,
            expectedAnnotations = listOf("@libcore.util.NonNull")
        )
        testClassAnnotations(
            original = "java.util.@test.pkg.A @test.pkg.B List<java.lang.String>",
            expectedClassName = "java.util.List",
            expectedParams = "<java.lang.String>",
            expectedAnnotations = listOf("@test.pkg.A", "@test.pkg.B")
        )
        testClassAnnotations(
            original = "java.lang.@test.pkg.A(a = \"@hi\", b = 0) String",
            expectedClassName = "java.lang.String",
            expectedParams = null,
            expectedAnnotations = listOf("@test.pkg.A(a = \"@hi\", b = 0)")
        )
        testClassAnnotations(
            original = "java.lang.@test.pkg.A(a = \"<hi>\", b = 0) String",
            expectedClassName = "java.lang.String",
            expectedParams = null,
            expectedAnnotations = listOf("@test.pkg.A(a = \"<hi>\", b = 0)")
        )
        testClassAnnotations(
            original =
                "java.util.@test.pkg.A(a = \"<hi>\", b = 0) List<java.lang.@test.pkg.B String>",
            expectedClassName = "java.util.List",
            expectedParams = "<java.lang.@test.pkg.B String>",
            expectedAnnotations = listOf("@test.pkg.A(a = \"<hi>\", b = 0)")
        )
        testClassAnnotations(
            original =
                "java.util.@test.pkg.A(a = \"hi@\", b = 0) @test.pkg.B(v = \"\") List<java.lang.@test.pkg.B(v = \"@\") String>",
            expectedClassName = "java.util.List",
            expectedParams = "<java.lang.@test.pkg.B(v = \"@\") String>",
            expectedAnnotations = listOf("@test.pkg.A(a = \"hi@\", b = 0)", "@test.pkg.B(v = \"\")")
        )
        testClassAnnotations(
            original = "test.pkg.Outer<P1>.Inner<P2>",
            expectedClassName = "test.pkg.Outer",
            expectedParams = "<P1>.Inner<P2>",
            expectedAnnotations = emptyList()
        )
        testClassAnnotations(
            original = "test.pkg.Outer.Inner",
            expectedClassName = "test.pkg.Outer",
            expectedParams = ".Inner",
            expectedAnnotations = emptyList()
        )
        testClassAnnotations(
            original = "test.pkg.@test.pkg.A Outer<P1>.@test.pkg.A Inner<P2>",
            expectedClassName = "test.pkg.Outer",
            expectedParams = "<P1>.@test.pkg.A Inner<P2>",
            expectedAnnotations = listOf("@test.pkg.A")
        )
        testClassAnnotations(
            original = "Outer.Inner<P2>",
            expectedClassName = "Outer",
            expectedParams = ".Inner<P2>",
            expectedAnnotations = emptyList()
        )
        testClassAnnotations(
            original = "java.lang.@androidx.annotation.IntRange(from=5,to=10) Integer",
            expectedClassName = "java.lang.Integer",
            expectedParams = null,
            expectedAnnotations = listOf("@androidx.annotation.IntRange(from=5,to=10)")
        )
        testClassAnnotations(
            original =
                "java.util.List<java.lang.@androidx.annotation.IntRange(from=5,to=10) Integer>",
            expectedClassName = "java.util.List",
            expectedParams = "<java.lang.@androidx.annotation.IntRange(from=5,to=10) Integer>",
            expectedAnnotations = emptyList()
        )
    }

    /**
     * Tests that [inputType] is parsed as an [ArrayTypeItem] with component type equal to
     * [expectedInnerType] and vararg iff [expectedVarargs] is true.
     */
    private fun testArrayType(
        inputType: String,
        expectedInnerType: TypeItem,
        expectedVarargs: Boolean
    ) {
        val type = parseType(inputType)
        assertThat(type).isInstanceOf(ArrayTypeItem::class.java)
        assertThat((type as ArrayTypeItem).componentType).isEqualTo(expectedInnerType)
        assertThat(type.isVarargs).isEqualTo(expectedVarargs)
    }

    @Test
    fun `Test parsing of array types with annotations`() {
        testArrayType(
            inputType = "test.pkg.@A @B Foo @B @C []",
            expectedInnerType = parseType("test.pkg.@A @B Foo"),
            expectedVarargs = false
        )
        testArrayType(
            inputType = "java.lang.annotation.@NonNull Annotation @NonNull []",
            expectedInnerType = parseType("java.lang.annotation.@NonNull Annotation"),
            expectedVarargs = false
        )
        testArrayType(
            inputType = "char @NonNull []",
            expectedInnerType = parseType("char"),
            expectedVarargs = false
        )
    }

    /**
     * Tests that [inputType] is parsed as a [ClassTypeItem] with qualified name equal to
     * [expectedQualifiedName] and [ClassTypeItem.arguments] is equal to [expectedTypeArguments].
     */
    private fun testClassType(
        inputType: String,
        expectedQualifiedName: String,
        expectedTypeArguments: List<TypeItem>
    ) {
        val type = parseType(inputType)
        assertThat(type).isInstanceOf(ClassTypeItem::class.java)
        assertThat((type as ClassTypeItem).qualifiedName).isEqualTo(expectedQualifiedName)
        assertThat(type.arguments).isEqualTo(expectedTypeArguments)
    }

    @Test
    fun `Test parsing of abbreviated java lang types`() {
        testClassType(
            inputType = "String",
            expectedQualifiedName = "java.lang.String",
            expectedTypeArguments = emptyList()
        )
        testArrayType(
            inputType = "String[]",
            expectedInnerType = parseType("java.lang.String"),
            expectedVarargs = false
        )
        testArrayType(
            inputType = "String...",
            expectedInnerType = parseType("java.lang.String"),
            expectedVarargs = true
        )
    }

    @Test
    fun `Test parsing of class types with annotations`() {
        testClassType(
            inputType = "@A @B test.pkg.Foo",
            expectedQualifiedName = "test.pkg.Foo",
            expectedTypeArguments = emptyList()
        )
        testClassType(
            inputType = "@A @B test.pkg.Foo",
            expectedQualifiedName = "test.pkg.Foo",
            expectedTypeArguments = emptyList()
        )
        testClassType(
            inputType = "java.lang.annotation.@NonNull Annotation",
            expectedQualifiedName = "java.lang.annotation.Annotation",
            expectedTypeArguments = emptyList()
        )
        testClassType(
            inputType = "java.util.Map.@NonNull Entry<a.A,b.B>",
            expectedQualifiedName = "java.util.Map.Entry",
            expectedTypeArguments = listOf(parseType("a.A"), parseType("b.B"))
        )
        testClassType(
            inputType = "java.util.@NonNull Set<java.util.Map.@NonNull Entry<a.A,b.B>>",
            expectedQualifiedName = "java.util.Set",
            expectedTypeArguments = listOf(parseType("java.util.Map.@NonNull Entry<a.A,b.B>"))
        )
    }
}
