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

package com.android.tools.metalava

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.ModifierListWriter
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.text.FileFormat
import com.android.tools.metalava.model.visitors.ApiVisitor
import java.io.PrintWriter
import java.util.BitSet
import java.util.function.Predicate

class SignatureWriter(
    private val writer: PrintWriter,
    filterEmit: Predicate<Item>,
    filterReference: Predicate<Item>,
    private val preFiltered: Boolean,
    private var emitHeader: EmitFileHeader = EmitFileHeader.ALWAYS,
    private val fileFormat: FileFormat,
    showUnannotated: Boolean,
    apiVisitorConfig: Config,
) :
    ApiVisitor(
        visitConstructorsAsMethods = false,
        nestInnerClasses = false,
        inlineInheritedFields = true,
        methodComparator = fileFormat.overloadedMethodOrder.comparator,
        filterEmit = filterEmit,
        filterReference = filterReference,
        showUnannotated = showUnannotated,
        config = apiVisitorConfig,
    ) {
    init {
        // If a header must always be written out (even if the file is empty) then write it here.
        if (emitHeader == EmitFileHeader.ALWAYS) {
            writer.print(fileFormat.header())
        }
    }

    private val modifierListWriter =
        ModifierListWriter.forSignature(
            writer = writer,
            skipNullnessAnnotations = fileFormat.kotlinStyleNulls,
        )

    internal fun write(text: String) {
        // If a header must only be written out when the file is not empty then write it here as
        // this is not called
        if (emitHeader == EmitFileHeader.IF_NONEMPTY_FILE) {
            writer.print(fileFormat.header())
            // Remember that the header was written out, so it will not be written again.
            emitHeader = EmitFileHeader.NEVER
        }
        writer.print(text)
    }

    override fun visitPackage(pkg: PackageItem) {
        write("package ")
        writeModifiers(pkg)
        write("${pkg.qualifiedName()} {\n\n")
    }

    override fun afterVisitPackage(pkg: PackageItem) {
        write("}\n\n")
    }

    override fun visitConstructor(constructor: ConstructorItem) {
        fun writeConstructor(skipMask: BitSet? = null) {
            write("    ctor ")
            writeModifiers(constructor)
            writeTypeParameterList(constructor.typeParameterList, addSpace = true)
            write(constructor.containingClass().fullName())
            writeParameterList(constructor, skipMask)
            writeThrowsList(constructor)
            write(";\n")
        }

        // Workaround for https://youtrack.jetbrains.com/issue/KT-57537
        if (constructor.shouldExpandOverloads()) {
            val parameters = constructor.parameters()
            val defaultMask = BitSet(parameters.size)

            // fill the bitmask for all parameters
            parameters.forEachIndexed { i, item -> defaultMask.set(i, item.hasDefaultValue()) }

            // expand overloads ordered by number of parameters, skipping last parameters first
            for (i in parameters.indices) {
                if (!defaultMask.get(i)) continue
                writeConstructor(defaultMask)
                defaultMask.clear(i)
            }
        }

        writeConstructor()
    }

    override fun visitField(field: FieldItem) {
        val name = if (field.isEnumConstant()) "enum_constant" else "field"
        write("    ")
        write(name)
        write(" ")
        writeModifiers(field)

        if (fileFormat.kotlinNameTypeOrder) {
            // Kotlin style: write the name of the field, then the type.
            write(field.name())
            write(": ")
            writeType(field.type())
        } else {
            // Java style: write the type, then the name of the field.
            writeType(field.type())
            write(" ")
            write(field.name())
        }

        field.writeValueWithSemicolon(
            writer,
            allowDefaultValue = false,
            requireInitialValue = false
        )
        write("\n")
    }

    override fun visitProperty(property: PropertyItem) {
        write("    property ")
        writeModifiers(property)
        if (fileFormat.kotlinNameTypeOrder) {
            // Kotlin style: write the name of the property, then the type.
            write(property.name())
            write(": ")
            writeType(property.type())
        } else {
            // Java style: write the type, then the name of the property.
            writeType(property.type())
            write(" ")
            write(property.name())
        }
        write(";\n")
    }

    override fun visitMethod(method: MethodItem) {
        write("    method ")
        writeModifiers(method)
        writeTypeParameterList(method.typeParameterList, addSpace = true)

        if (fileFormat.kotlinNameTypeOrder) {
            // Kotlin style: write the name of the method and the parameters, then the type.
            write(method.name())
            writeParameterList(method)
            write(": ")
            writeType(method.returnType())
        } else {
            // Java style: write the type, then the name of the method and the parameters.
            writeType(method.returnType())
            write(" ")
            write(method.name())
            writeParameterList(method)
        }

        writeThrowsList(method)

        if (method.containingClass().isAnnotationType()) {
            val default = method.defaultValue()
            if (default.isNotEmpty()) {
                write(" default ")
                write(default)
            }
        }

        write(";\n")
    }

    override fun visitClass(cls: ClassItem) {
        write("  ")

        writeModifiers(cls)

        if (cls.isAnnotationType()) {
            write("@interface")
        } else if (cls.isInterface()) {
            write("interface")
        } else if (cls.isEnum()) {
            write("enum")
        } else {
            write("class")
        }
        write(" ")
        write(cls.fullName())
        writeTypeParameterList(cls.typeParameterList, addSpace = false)
        writeSuperClassStatement(cls)
        writeInterfaceList(cls)

        write(" {\n")
    }

    override fun afterVisitClass(cls: ClassItem) {
        write("  }\n\n")
    }

    private fun writeModifiers(item: Item) {
        modifierListWriter.write(item)
    }

    /** Get the filtered super class type, ignoring java.lang.Object. */
    private fun getFilteredSuperClassTypeFor(cls: ClassItem): TypeItem? {
        val superClassItem =
            if (preFiltered) cls.superClassType() else cls.filteredSuperClassType(filterReference)
        return if (superClassItem == null || superClassItem.isJavaLangObject()) null
        else superClassItem
    }

    private fun writeSuperClassStatement(cls: ClassItem) {
        if (cls.isEnum() || cls.isAnnotationType() || cls.isInterface()) {
            return
        }

        getFilteredSuperClassTypeFor(cls)?.let { superClassType ->
            write(" extends")
            writeExtendsOrImplementsType(superClassType)
        }
    }

    private fun writeExtendsOrImplementsType(typeItem: TypeItem) {
        val superClassString =
            typeItem.toTypeString(
                annotations = fileFormat.includeTypeUseAnnotations,
                kotlinStyleNulls = false,
                filter = filterReference
            )
        write(" ")
        write(superClassString)
    }

    private fun writeInterfaceList(cls: ClassItem) {
        if (cls.isAnnotationType()) {
            return
        }
        val isInterface = cls.isInterface()

        val unfilteredInterfaceTypes = cls.interfaceTypes()
        val interfaces =
            if (preFiltered) unfilteredInterfaceTypes
            else cls.filteredInterfaceTypes(filterReference)
        if (interfaces.isEmpty()) {
            return
        }

        // Sort before prepending the super class (if this is an interface) as the super class
        // always comes first because it was previously written out by writeSuperClassStatement.
        @Suppress("DEPRECATION")
        val comparator =
            if (fileFormat.sortWholeExtendsList) TypeItem.totalComparator
            else TypeItem.partialComparator
        val sortedInterfaces = interfaces.sortedWith(comparator)

        // Combine the super class and interfaces into a full list of them.
        val fullInterfaces =
            if (isInterface && !fileFormat.sortWholeExtendsList) {
                // Previously, when the first interface in the extends list was stored in
                // superClass, if that interface was visible in the signature then it would always
                // be first even though the other interfaces are sorted in alphabetical order. This
                // implements similar logic.
                val firstUnfilteredInterfaceType = unfilteredInterfaceTypes.first()
                val firstFilteredInterfaceType = interfaces.first()
                if (firstFilteredInterfaceType == firstUnfilteredInterfaceType) {
                    buildList {
                        // The first interface in the interfaces list is also the first interface in
                        // the filtered interfaces list so add it first.
                        add(firstFilteredInterfaceType)

                        // Add the remaining interfaces in sorted order.
                        if (sortedInterfaces.size > 1) {
                            for (interfaceType in sortedInterfaces) {
                                if (interfaceType != firstFilteredInterfaceType) {
                                    add(interfaceType)
                                }
                            }
                        }
                    }
                } else {
                    sortedInterfaces
                }
            } else sortedInterfaces

        val label = if (isInterface) " extends" else " implements"
        write(label)

        fullInterfaces.forEach { typeItem -> writeExtendsOrImplementsType(typeItem) }
    }

    private fun writeTypeParameterList(typeList: TypeParameterList, addSpace: Boolean) {
        val typeListString = typeList.toString()
        if (typeListString.isNotEmpty()) {
            write(typeListString)
            if (addSpace) {
                write(" ")
            }
        }
    }

    private fun writeParameterList(method: MethodItem, skipMask: BitSet? = null) {
        write("(")
        var writtenParams = 0
        method.parameters().asSequence().forEachIndexed { i, parameter ->
            // skip over defaults when generating @JvmOverloads permutations
            if (skipMask != null && skipMask.get(i)) return@forEachIndexed

            if (writtenParams > 0) {
                write(", ")
            }
            if (parameter.hasDefaultValue() && fileFormat.conciseDefaultValues) {
                // Concise representation of a parameter with a default
                write("optional ")
            }
            writeModifiers(parameter)

            if (fileFormat.kotlinNameTypeOrder) {
                // Kotlin style: the parameter must have a name (use `_` if it doesn't have a public
                // name). Write the name and then the type.
                val name = parameter.publicName() ?: "_"
                write(name)
                write(": ")
                writeType(parameter.type())
            } else {
                // Java style: write the type, then the name if it has a public name.
                writeType(parameter.type())
                val name = parameter.publicName()
                if (name != null) {
                    write(" ")
                    write(name)
                }
            }

            if (parameter.isDefaultValueKnown() && !fileFormat.conciseDefaultValues) {
                write(" = ")
                val defaultValue = parameter.defaultValue()
                if (defaultValue != null) {
                    write(defaultValue)
                } else {
                    // null is a valid default value!
                    write("null")
                }
            }
            writtenParams++
        }
        write(")")
    }

    private fun writeType(type: TypeItem?) {
        type ?: return

        var typeString =
            type.toTypeString(
                annotations = fileFormat.includeTypeUseAnnotations,
                kotlinStyleNulls = fileFormat.kotlinStyleNulls,
                filter = filterReference
            )

        // Strip java.lang. prefix
        typeString = TypeItem.shortenTypes(typeString)

        write(typeString)
    }

    private fun writeThrowsList(method: MethodItem) {
        val throws =
            when {
                preFiltered -> method.throwsTypes().asSequence()
                else -> method.filteredThrowsTypes(filterReference).asSequence()
            }
        if (throws.any()) {
            write(" throws ")
            throws.asSequence().sortedWith(ExceptionTypeItem.fullNameComparator).forEachIndexed {
                i,
                type ->
                if (i > 0) {
                    write(", ")
                }
                write(type.toTypeString())
            }
        }
    }
}

enum class EmitFileHeader {
    ALWAYS,
    NEVER,
    IF_NONEMPTY_FILE
}
