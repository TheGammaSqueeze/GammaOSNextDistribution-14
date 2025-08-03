/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tools.metalava.stub

import com.android.tools.metalava.model.BaseItemVisitor
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.ModifierListWriter
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.psi.PsiClassItem
import java.io.PrintWriter
import java.util.function.Predicate

internal class KotlinStubWriter(
    private val writer: PrintWriter,
    private val modifierListWriter: ModifierListWriter,
    private val filterReference: Predicate<Item>,
    private val preFiltered: Boolean = true,
    private val config: StubWriterConfig,
) : BaseItemVisitor() {

    override fun visitClass(cls: ClassItem) {
        if (cls.isTopLevelClass()) {
            val qualifiedName = cls.containingPackage().qualifiedName()
            if (qualifiedName.isNotBlank()) {
                writer.println("package $qualifiedName")
                writer.println()
            }
            cls.getSourceFile()?.getImports(filterReference)?.let {
                for (item in it) {
                    writer.println("import ${item.pattern}")
                }
                writer.println()
            }
        }
        appendDocumentation(cls, writer, config)

        writer.println("@file:Suppress(\"ALL\")")

        appendModifiers(cls)

        when {
            cls.isAnnotationType() -> writer.print("annotation class")
            cls.isInterface() -> writer.print("interface")
            cls.isEnum() -> writer.print("enum class")
            else -> writer.print("class")
        }

        writer.print(" ")
        writer.print(cls.simpleName())

        generateTypeParameterList(typeList = cls.typeParameterList, addSpace = false)
        val printedSuperClass = generateSuperClassDeclaration(cls)
        generateInterfaceList(cls, printedSuperClass)
        writer.print(" {\n")
    }

    private fun generateTypeParameterList(typeList: TypeParameterList, addSpace: Boolean) {
        val typeListString = typeList.toString()
        if (typeListString.isNotEmpty()) {
            writer.print(typeListString)

            if (addSpace) {
                writer.print(' ')
            }
        }
    }

    private fun appendModifiers(item: Item) = modifierListWriter.write(item)

    private fun generateSuperClassDeclaration(cls: ClassItem): Boolean {
        if (cls.isEnum() || cls.isAnnotationType()) {
            // No extends statement for enums and annotations; it's implied by the "enum" and
            // "@interface" keywords
            return false
        }

        val superClass =
            if (preFiltered) cls.superClassType() else cls.filteredSuperClassType(filterReference)

        if (superClass != null && !superClass.isJavaLangObject()) {
            val qualifiedName =
                superClass.toTypeString() // TODO start passing language = Language.KOTLIN
            writer.print(" : ")

            if (qualifiedName.contains("<")) {
                // TODO: push this into the model at filter-time such that clients don't need
                // to remember to do this!!
                val s = superClass.asClass()
                if (s != null) {
                    val replaced = superClass.convertType(cls, s)
                    writer.print(replaced.toTypeString())
                    return true
                }
            }
            (cls as PsiClassItem).psiClass.superClassType
            writer.print(qualifiedName)
            // TODO: print out arguments to the parent constructor
            writer.print("()")
            return true
        }
        return false
    }

    private fun generateInterfaceList(cls: ClassItem, printedSuperClass: Boolean) {
        if (cls.isAnnotationType()) {
            // No extends statement for annotations; it's implied by the "@interface" keyword
            return
        }

        val interfaces =
            if (preFiltered) cls.interfaceTypes().asSequence()
            else cls.filteredInterfaceTypes(filterReference).asSequence()

        if (interfaces.any()) {
            if (printedSuperClass) {
                writer.print(",")
            } else {
                writer.print(" :")
            }
            interfaces.forEachIndexed { index, type ->
                if (index > 0) {
                    writer.print(",")
                }
                writer.print(" ")
                writer.print(type.toTypeString()) // TODO start passing language = Language.KOTLIN
            }
        }
    }

    private fun writeType(type: TypeItem?) {
        type ?: return

        val typeString =
            type.toTypeString(
                annotations = false,
                kotlinStyleNulls = true,
                filter = filterReference
                // TODO pass in language = Language.KOTLIN
            )

        writer.print(typeString)
    }

    override fun visitMethod(method: MethodItem) {
        if (method.isKotlinProperty()) return // will be handled by visitProperty

        writer.println()
        appendDocumentation(method, writer, config)

        // TODO: Should be an annotation
        generateThrowsList(method)

        appendModifiers(method)
        generateTypeParameterList(typeList = method.typeParameterList, addSpace = true)

        writer.print("fun ")
        writer.print(method.name())
        generateParameterList(method)

        writer.print(": ")
        val returnType = method.returnType()
        writeType(returnType)

        if (method.containingClass().isAnnotationType()) {
            val default = method.defaultValue()
            if (default.isNotEmpty()) {
                writer.print(" default ")
                writer.print(default)
            }
        }

        if (ModifierListWriter.requiresMethodBodyInStubs(method)) {
            writer.print(" = ")
            writeThrowStub()
        }
        writer.println()
    }

    override fun afterVisitClass(cls: ClassItem) {
        writer.println("}\n\n")
    }

    private fun writeThrowStub() {
        writer.write("error(\"Stub!\")")
    }

    private fun generateParameterList(method: MethodItem) {
        writer.print("(")
        method.parameters().asSequence().forEachIndexed { i, parameter ->
            if (i > 0) {
                writer.print(", ")
            }
            appendModifiers(parameter)
            val name = parameter.publicName() ?: parameter.name()
            writer.print(name)
            writer.print(": ")
            writeType(parameter.type())
        }
        writer.print(")")
    }

    private fun generateThrowsList(method: MethodItem) {
        // Note that throws types are already sorted internally to help comparison matching
        val throws =
            if (preFiltered) {
                method.throwsTypes().asSequence()
            } else {
                method.filteredThrowsTypes(filterReference).asSequence()
            }
        if (throws.any()) {
            writer.print("@Throws(")
            throws.asSequence().sortedWith(ExceptionTypeItem.fullNameComparator).forEachIndexed {
                i,
                type ->
                if (i > 0) {
                    writer.print(",")
                }
                writer.print(type.toTypeString())
                writer.print("::class")
            }
            writer.print(")")
        }
    }
}
