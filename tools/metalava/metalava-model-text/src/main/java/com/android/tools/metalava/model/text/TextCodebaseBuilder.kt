/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.android.tools.metalava.model.AnnotationManager
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PropertyItem
import com.android.tools.metalava.reporter.FileLocation
import java.io.File

/**
 * Supports building a [TextCodebase] that is a subset of another [TextCodebase].
 *
 * The purposely uses generic model classes in the API and down casts any items provided to the
 * appropriate text model item. That is to avoid external dependencies on the text model item
 * implementation classes.
 */
class TextCodebaseBuilder private constructor(private val codebase: TextCodebase) {

    companion object {
        fun build(
            location: File,
            annotationManager: AnnotationManager,
            block: TextCodebaseBuilder.() -> Unit
        ): Codebase {
            val codebase =
                TextCodebase(
                    location = location,
                    annotationManager = annotationManager,
                    classResolver = null,
                )
            val builder = TextCodebaseBuilder(codebase)
            builder.block()

            return codebase
        }
    }

    var description by codebase::description

    private fun getOrAddPackage(pkgName: String): TextPackageItem {
        val pkg = codebase.findPackage(pkgName)
        if (pkg != null) {
            return pkg
        }
        val newPkg =
            TextPackageItem(
                codebase,
                pkgName,
                DefaultModifierList(codebase, DefaultModifierList.PUBLIC),
                FileLocation.UNKNOWN
            )
        codebase.addPackage(newPkg)
        return newPkg
    }

    fun addPackage(pkg: PackageItem) {
        codebase.addPackage(pkg as TextPackageItem)
    }

    fun addClass(cls: ClassItem) {
        val pkg = getOrAddPackage(cls.containingPackage().qualifiedName())
        pkg.addClass(cls as TextClassItem)
    }

    fun addConstructor(ctor: ConstructorItem) {
        val cls = getOrAddClass(ctor.containingClass())
        cls.addConstructor(ctor as TextConstructorItem)
    }

    fun addMethod(method: MethodItem) {
        val cls = getOrAddClass(method.containingClass())
        cls.addMethod(method as TextMethodItem)
    }

    fun addField(field: FieldItem) {
        val cls = getOrAddClass(field.containingClass())
        cls.addField(field as TextFieldItem)
    }

    fun addProperty(property: PropertyItem) {
        val cls = getOrAddClass(property.containingClass())
        cls.addProperty(property as TextPropertyItem)
    }

    private fun getOrAddClass(fullClass: ClassItem): TextClassItem {
        val cls = codebase.findClassInCodebase(fullClass.qualifiedName())
        if (cls != null) {
            return cls
        }
        val textClass = fullClass as TextClassItem
        val newClass =
            TextClassItem(
                codebase = codebase,
                fileLocation = FileLocation.UNKNOWN,
                modifiers = textClass.modifiers,
                classKind = textClass.classKind,
                qualifiedName = textClass.qualifiedName,
                simpleName = textClass.simpleName,
                fullName = textClass.fullName,
                typeParameterList = textClass.typeParameterList,
            )

        newClass.setSuperClassType(textClass.superClassType())

        val pkg = getOrAddPackage(fullClass.containingPackage().qualifiedName())
        pkg.addClass(newClass)
        newClass.setContainingPackage(pkg)
        codebase.registerClass(newClass)
        return newClass
    }
}
