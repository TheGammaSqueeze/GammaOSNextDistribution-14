/*
 * Copyright (C) 2017 The Android Open Source Project
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
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.ModifierListWriter
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.VariableTypeItem
import java.io.PrintWriter
import java.util.function.Predicate

internal class JavaStubWriter(
    private val writer: PrintWriter,
    private val modifierListWriter: ModifierListWriter,
    private val filterEmit: Predicate<Item>,
    private val filterReference: Predicate<Item>,
    private val preFiltered: Boolean = true,
    private val config: StubWriterConfig,
) : BaseItemVisitor() {

    override fun visitClass(cls: ClassItem) {
        if (cls.isTopLevelClass()) {
            val qualifiedName = cls.containingPackage().qualifiedName()
            if (qualifiedName.isNotBlank()) {
                writer.println("package $qualifiedName;")
                writer.println()
            }
            if (config.includeDocumentationInStubs) {
                // All the classes referenced in the stubs are fully qualified, so no imports are
                // needed. However, in some cases for javadoc, replacement with fully qualified name
                // fails, and thus we need to include imports for the stubs to compile.
                cls.getSourceFile()?.getImports(filterReference)?.let {
                    for (item in it) {
                        if (item.isMember) {
                            writer.println("import static ${item.pattern};")
                        } else {
                            writer.println("import ${item.pattern};")
                        }
                    }
                    writer.println()
                }
            }
        }

        appendDocumentation(cls, writer, config)

        // "ALL" doesn't do it; compiler still warns unless you actually explicitly list "unchecked"
        writer.println("@SuppressWarnings({\"unchecked\", \"deprecation\", \"all\"})")

        appendModifiers(cls)

        when {
            cls.isAnnotationType() -> writer.print("@interface")
            cls.isInterface() -> writer.print("interface")
            cls.isEnum() -> writer.print("enum")
            else -> writer.print("class")
        }

        writer.print(" ")
        writer.print(cls.simpleName())

        generateTypeParameterList(typeList = cls.typeParameterList, addSpace = false)
        generateSuperClassDeclaration(cls)
        generateInterfaceList(cls)
        writer.print(" {\n")

        if (cls.isEnum()) {
            var first = true
            // Enums should preserve the original source order, not alphabetical etc. sort
            for (field in cls.filteredFields(filterReference, true).sortedBy { it.sortingRank }) {
                if (field.isEnumConstant()) {
                    if (first) {
                        first = false
                    } else {
                        writer.write(",\n")
                    }
                    appendDocumentation(field, writer, config)

                    // Append the modifier list even though the enum constant does not actually have
                    // modifiers as that will write the annotations which it does have and ignore
                    // the modifiers.
                    appendModifiers(field)

                    writer.write(field.name())
                }
            }
            writer.println(";")
        }

        generateMissingConstructors(cls)
    }

    override fun afterVisitClass(cls: ClassItem) {
        writer.print("}\n\n")
    }

    private fun appendModifiers(item: Item) = modifierListWriter.write(item)

    private fun generateSuperClassDeclaration(cls: ClassItem) {
        if (cls.isEnum() || cls.isAnnotationType() || cls.isInterface()) {
            // No extends statement for enums and annotations; it's implied by the "enum" and
            // "@interface" keywords. Normal interfaces do support an extends statement but it is
            // generated in [generateInterfaceList].
            return
        }

        val superClass =
            if (preFiltered) cls.superClassType() else cls.filteredSuperClassType(filterReference)

        if (superClass != null && !superClass.isJavaLangObject()) {
            writer.print(" extends ")
            writer.print(superClass.toTypeString())
        }
    }

    private fun generateInterfaceList(cls: ClassItem) {
        if (cls.isAnnotationType()) {
            // No extends statement for annotations; it's implied by the "@interface" keyword
            return
        }

        val interfaces =
            if (preFiltered) cls.interfaceTypes() else cls.filteredInterfaceTypes(filterReference)

        if (interfaces.any()) {
            val label = if (cls.isInterface()) " extends" else " implements"
            writer.print(label)
            interfaces.forEachIndexed { index, type ->
                if (index > 0) {
                    writer.print(",")
                }
                writer.print(" ")
                writer.print(type.toTypeString())
            }
        }
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

    override fun visitConstructor(constructor: ConstructorItem) {
        writeConstructor(constructor, constructor.superConstructor)
    }

    private fun writeConstructor(constructor: MethodItem, superConstructor: MethodItem?) {
        writer.println()
        appendDocumentation(constructor, writer, config)
        appendModifiers(constructor)
        generateTypeParameterList(typeList = constructor.typeParameterList, addSpace = true)
        writer.print(constructor.containingClass().simpleName())

        generateParameterList(constructor)
        generateThrowsList(constructor)

        writer.print(" { ")

        writeConstructorBody(constructor, superConstructor)
        writer.println(" }")
    }

    private fun writeConstructorBody(constructor: MethodItem, superConstructor: MethodItem?) {
        // Find any constructor in parent that we can compile against
        superConstructor?.let { it ->
            val parameters = it.parameters()
            if (parameters.isNotEmpty()) {
                val includeCasts =
                    it.containingClass().constructors().filter { filterReference.test(it) }.size > 1
                writer.print("super(")
                parameters.forEachIndexed { index, parameter ->
                    if (index > 0) {
                        writer.write(", ")
                    }
                    val type = parameter.type()
                    if (type !is PrimitiveTypeItem) {
                        if (includeCasts) {
                            // Casting to the erased type could lead to unchecked warnings (which
                            // are suppressed) but avoids having to deal with parameterized types
                            // and ensures that casting to a vararg parameter uses an array type.
                            val typeString = type.toErasedTypeString()
                            writer.write("(")
                            if (type is VariableTypeItem) {
                                // The super constructor's parameter is a type variable: so see if
                                // it should be mapped back to a type specified by this class. e.g.
                                // Given:
                                //   class Bar<T extends Number> {
                                //       public Bar(int i) {}
                                //       public Bar(T t) {}
                                //   }
                                //   class Foo extends Bar<Integer> {
                                //       public Foo(Integer i) { super(i); }
                                //   }
                                //
                                // The stub for Foo should use:
                                //     super((Integer) i);
                                // Not:
                                //     super((Number) i);
                                //
                                // However, if the super class is referenced as a raw type then
                                // there will be no mapping in which case fall back to the erased
                                // type which will use the type variable's lower bound. e.g.
                                // Given:
                                //   class Foo extends Bar {
                                //       public Foo(Integer i) { super(i); }
                                //   }
                                //
                                // The stub for Foo should use:
                                //     super((Number) i);
                                val map =
                                    constructor
                                        .containingClass()
                                        .mapTypeVariables(it.containingClass())
                                val cast = map[type.asTypeParameter]?.toTypeString() ?: typeString
                                writer.write(cast)
                            } else {
                                writer.write(typeString)
                            }
                            writer.write(")")
                        }
                        writer.write("null")
                    } else {
                        // Add cast for things like shorts and bytes
                        val typeString = type.toTypeString()
                        if (
                            typeString != "boolean" && typeString != "int" && typeString != "long"
                        ) {
                            writer.write("(")
                            writer.write(typeString)
                            writer.write(")")
                        }
                        writer.write(type.defaultValueString())
                    }
                }
                writer.print("); ")
            }
        }

        writeThrowStub()
    }

    private fun generateMissingConstructors(cls: ClassItem) {
        val clsStubConstructor = cls.stubConstructor
        val constructors = cls.filteredConstructors(filterEmit)
        // If the default stub constructor is not publicly visible then it won't be output during
        // the normal visiting
        // so visit it specially to ensure that it is output.
        if (clsStubConstructor != null && !constructors.contains(clsStubConstructor)) {
            visitConstructor(clsStubConstructor)
            return
        }
    }

    override fun visitMethod(method: MethodItem) {
        writeMethod(method.containingClass(), method)
    }

    private fun writeMethod(containingClass: ClassItem, method: MethodItem) {
        writer.println()
        appendDocumentation(method, writer, config)

        appendModifiers(method)
        generateTypeParameterList(typeList = method.typeParameterList, addSpace = true)

        val returnType = method.returnType()
        writer.print(returnType.toTypeString(annotations = false, filter = filterReference))

        writer.print(' ')
        writer.print(method.name())
        generateParameterList(method)
        generateThrowsList(method)

        if (containingClass.isAnnotationType()) {
            val default = method.defaultValue()
            if (default.isNotEmpty()) {
                writer.print(" default ")
                writer.print(default)
            }
        }

        if (ModifierListWriter.requiresMethodBodyInStubs(method)) {
            writer.print(" { ")
            writeThrowStub()
            writer.println(" }")
        } else {
            writer.println(";")
        }
    }

    override fun visitField(field: FieldItem) {
        // Handled earlier in visitClass
        if (field.isEnumConstant()) {
            return
        }

        writer.println()

        appendDocumentation(field, writer, config)
        appendModifiers(field)
        writer.print(field.type().toTypeString(annotations = false, filter = filterReference))
        writer.print(' ')
        writer.print(field.name())
        val needsInitialization =
            field.modifiers.isFinal() &&
                field.initialValue(true) == null &&
                field.containingClass().isClass()
        field.writeValueWithSemicolon(
            writer,
            allowDefaultValue = !needsInitialization,
            requireInitialValue = !needsInitialization
        )
        writer.print("\n")

        if (needsInitialization) {
            if (field.modifiers.isStatic()) {
                writer.print("static ")
            }
            writer.print("{ ${field.name()} = ${field.type().defaultValueString()}; }\n")
        }
    }

    private fun writeThrowStub() {
        writer.write("throw new RuntimeException(\"Stub!\");")
    }

    private fun generateParameterList(method: MethodItem) {
        writer.print("(")
        method.parameters().asSequence().forEachIndexed { i, parameter ->
            if (i > 0) {
                writer.print(", ")
            }
            appendModifiers(parameter)
            writer.print(
                parameter.type().toTypeString(annotations = false, filter = filterReference)
            )
            writer.print(' ')
            val name = parameter.publicName() ?: parameter.name()
            writer.print(name)
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
            writer.print(" throws ")
            throws.sortedWith(ExceptionTypeItem.fullNameComparator).forEachIndexed { i, type ->
                if (i > 0) {
                    writer.print(", ")
                }
                writer.print(type.toTypeString())
            }
        }
    }
}
