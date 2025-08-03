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

package com.android.tools.metalava.model

open class BaseItemVisitor(
    /**
     * Whether constructors should be visited as part of a [#visitMethod] call instead of just a
     * [#visitConstructor] call. Helps simplify visitors that don't care to distinguish between the
     * two cases. Defaults to true.
     */
    val visitConstructorsAsMethods: Boolean = true,
    /**
     * Whether inner classes should be visited "inside" a class; when this property is true, inner
     * classes are visited before the [#afterVisitClass] method is called; when false, it's done
     * afterwards. Defaults to false.
     */
    val nestInnerClasses: Boolean = false,
) : ItemVisitor {
    override fun visit(cls: ClassItem) {
        if (skip(cls)) {
            return
        }

        visitItem(cls)
        visitClass(cls)

        for (constructor in cls.constructors()) {
            constructor.accept(this)
        }

        for (method in cls.methods()) {
            method.accept(this)
        }

        for (property in cls.properties()) {
            property.accept(this)
        }

        if (cls.isEnum()) {
            // In enums, visit the enum constants first, then the fields
            for (field in cls.fields()) {
                if (field.isEnumConstant()) {
                    field.accept(this)
                }
            }
            for (field in cls.fields()) {
                if (!field.isEnumConstant()) {
                    field.accept(this)
                }
            }
        } else {
            for (field in cls.fields()) {
                field.accept(this)
            }
        }

        if (nestInnerClasses) {
            for (innerCls in cls.innerClasses()) {
                innerCls.accept(this)
            }
        } // otherwise done below

        afterVisitClass(cls)
        afterVisitItem(cls)

        if (!nestInnerClasses) {
            for (innerCls in cls.innerClasses()) {
                innerCls.accept(this)
            }
        }
    }

    override fun visit(field: FieldItem) {
        if (skip(field)) {
            return
        }

        visitItem(field)
        visitField(field)

        afterVisitField(field)
        afterVisitItem(field)
    }

    override fun visit(method: MethodItem) {
        if (skip(method)) {
            return
        }

        visitItem(method)
        if (method.isConstructor()) {
            visitConstructor(method as ConstructorItem)
        } else {
            visitMethod(method)
        }

        for (parameter in method.parameters()) {
            parameter.accept(this)
        }

        if (method.isConstructor()) {
            afterVisitConstructor(method as ConstructorItem)
        } else {
            afterVisitMethod(method)
        }
        afterVisitItem(method)
    }

    override fun visit(pkg: PackageItem) {
        if (skip(pkg)) {
            return
        }

        visitItem(pkg)
        visitPackage(pkg)

        for (cls in pkg.topLevelClasses()) {
            cls.accept(this)
        }

        afterVisitPackage(pkg)
        afterVisitItem(pkg)
    }

    override fun visit(packageList: PackageList) {
        visitCodebase(packageList.codebase)
        packageList.packages.forEach { it.accept(this) }
        afterVisitCodebase(packageList.codebase)
    }

    override fun visit(parameter: ParameterItem) {
        if (skip(parameter)) {
            return
        }

        visitItem(parameter)
        visitParameter(parameter)

        afterVisitParameter(parameter)
        afterVisitItem(parameter)
    }

    override fun visit(property: PropertyItem) {
        if (skip(property)) {
            return
        }

        visitItem(property)
        visitProperty(property)

        afterVisitProperty(property)
        afterVisitItem(property)
    }

    open fun skip(item: Item): Boolean = false

    /**
     * Visits the item. This is always called before other more specialized visit methods, such as
     * [visitClass].
     */
    open fun visitItem(item: Item) {}

    open fun visitCodebase(codebase: Codebase) {}

    open fun visitPackage(pkg: PackageItem) {}

    open fun visitClass(cls: ClassItem) {}

    open fun visitConstructor(constructor: ConstructorItem) {
        if (visitConstructorsAsMethods) {
            visitMethod(constructor)
        }
    }

    open fun visitField(field: FieldItem) {}

    open fun visitMethod(method: MethodItem) {}

    open fun visitParameter(parameter: ParameterItem) {}

    open fun visitProperty(property: PropertyItem) {}

    open fun afterVisitItem(item: Item) {}

    open fun afterVisitCodebase(codebase: Codebase) {}

    open fun afterVisitPackage(pkg: PackageItem) {}

    open fun afterVisitClass(cls: ClassItem) {}

    open fun afterVisitConstructor(constructor: ConstructorItem) {
        if (visitConstructorsAsMethods) {
            afterVisitMethod(constructor)
        }
    }

    open fun afterVisitField(field: FieldItem) {}

    open fun afterVisitMethod(method: MethodItem) {}

    open fun afterVisitParameter(parameter: ParameterItem) {}

    open fun afterVisitProperty(property: PropertyItem) {}
}
