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

package com.android.tools.metalava.apilevels

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.visitors.ApiVisitor
import java.util.function.Predicate

/**
 * Visits the API codebase and inserts into the [Api] the classes, methods and fields. If
 * [providedFilterEmit] and [providedFilterReference] are non-null, they are used to determine which
 * [Item]s should be added to the [api]. Otherwise, the [ApiVisitor] default filters are used.
 */
fun addApisFromCodebase(
    api: Api,
    apiLevel: Int,
    codebase: Codebase,
    useInternalNames: Boolean,
    providedFilterEmit: Predicate<Item>? = null,
    providedFilterReference: Predicate<Item>? = null
) {
    codebase.accept(
        object :
            ApiVisitor(
                visitConstructorsAsMethods = true,
                nestInnerClasses = false,
                filterEmit = providedFilterEmit,
                filterReference = providedFilterReference
            ) {

            var currentClass: ApiClass? = null

            override fun afterVisitClass(cls: ClassItem) {
                currentClass = null
            }

            override fun visitClass(cls: ClassItem) {
                val newClass = api.addClass(cls.nameInApi(), apiLevel, cls.deprecated)
                currentClass = newClass

                if (cls.isClass()) {
                    // The jar files historically contain package private parents instead of
                    // the real API so we need to correct the data we've already read in

                    val filteredSuperClass = cls.filteredSuperclass(filterReference)
                    val superClass = cls.superClass()
                    if (filteredSuperClass != superClass && filteredSuperClass != null) {
                        val existing = newClass.superClasses.firstOrNull()?.name
                        val superName = superClass?.nameInApi()
                        if (existing == superName) {
                            // The bytecode used to point to the old hidden super class. Point
                            // to the real one (that the signature files referenced) instead.
                            val removed = superName?.let { newClass.removeSuperClass(it) }
                            val since = removed?.since ?: apiLevel
                            val entry =
                                newClass.addSuperClass(filteredSuperClass.nameInApi(), since)
                            // Show that it's also seen here
                            entry.update(apiLevel)

                            // Also inherit the interfaces from that API level, unless it was added
                            // later
                            val superClassEntry = api.findClass(superName)
                            if (superClassEntry != null) {
                                for (interfaceType in
                                    superClass!!.filteredInterfaceTypes(filterReference)) {
                                    val interfaceClass = interfaceType.asClass() ?: return
                                    var mergedSince = since
                                    val interfaceName = interfaceClass.nameInApi()
                                    for (itf in superClassEntry.interfaces) {
                                        val currentInterface = itf.name
                                        if (interfaceName == currentInterface) {
                                            mergedSince = itf.since
                                            break
                                        }
                                    }
                                    newClass.addInterface(interfaceClass.nameInApi(), mergedSince)
                                }
                            }
                        } else {
                            newClass.addSuperClass(filteredSuperClass.nameInApi(), apiLevel)
                        }
                    } else if (superClass != null) {
                        newClass.addSuperClass(superClass.nameInApi(), apiLevel)
                    }
                } else if (cls.isInterface()) {
                    val superClass = cls.superClass()
                    if (superClass != null && !superClass.isJavaLangObject()) {
                        newClass.addInterface(superClass.nameInApi(), apiLevel)
                    }
                } else if (cls.isEnum()) {
                    // Implicit super class; match convention from bytecode
                    if (newClass.name != enumClass) {
                        newClass.addSuperClass(enumClass, apiLevel)
                    }

                    // Mimic doclava enum methods
                    enumMethodNames(newClass.name).forEach { name ->
                        newClass.addMethod(name, apiLevel, false)
                    }
                } else if (cls.isAnnotationType()) {
                    // Implicit super class; match convention from bytecode
                    if (newClass.name != annotationClass) {
                        newClass.addSuperClass(objectClass, apiLevel)
                        newClass.addInterface(annotationClass, apiLevel)
                    }
                }

                // Ensure we don't end up with
                //    -  <extends name="java/lang/Object"/>
                //    +  <extends name="java/lang/Object" removed="29"/>
                // which can happen because the bytecode always explicitly contains extends
                // java.lang.Object
                // but in the source code we don't see it, and the lack of presence of this
                // shouldn't be
                // taken as a sign that we no longer extend object. But only do this if the class
                // didn't
                // previously extend object and now extends something else.
                if (
                    (cls.isClass() || cls.isInterface()) &&
                        newClass.superClasses.size == 1 &&
                        newClass.superClasses[0].name == objectClass
                ) {
                    newClass.addSuperClass(objectClass, apiLevel)
                }

                for (interfaceType in cls.filteredInterfaceTypes(filterReference)) {
                    val interfaceClass = interfaceType.asClass() ?: return
                    newClass.addInterface(interfaceClass.nameInApi(), apiLevel)
                }
            }

            override fun visitMethod(method: MethodItem) {
                if (method.isPrivate || method.isPackagePrivate) {
                    return
                }
                currentClass?.addMethod(method.nameInApi(), apiLevel, method.deprecated)
            }

            override fun visitField(field: FieldItem) {
                if (field.isPrivate || field.isPackagePrivate) {
                    return
                }

                currentClass?.addField(field.nameInApi(), apiLevel, field.deprecated)
            }

            /** The name of the field in this [Api], based on [useInternalNames] */
            fun FieldItem.nameInApi(): String {
                return if (useInternalNames) {
                    internalName()
                } else {
                    name()
                }
            }

            /** The name of the method in this [Api], based on [useInternalNames] */
            fun MethodItem.nameInApi(): String {
                return if (useInternalNames) {
                    internalName() +
                        // Use "V" instead of the type of the constructor for backwards
                        // compatibility
                        // with the older bytecode
                        internalDesc(voidConstructorTypes = true)
                } else {
                    val paramString = parameters().joinToString(",") { it.type().toTypeString() }
                    name() + typeParameterList + "(" + paramString + ")"
                }
            }

            /** The name of the class in this [Api], based on [useInternalNames] */
            fun ClassItem.nameInApi(): String {
                return if (useInternalNames) {
                    internalName()
                } else {
                    qualifiedName()
                }
            }

            // The names of some common classes, based on [useInternalNames]
            val objectClass = nameForClass("java", "lang", "Object")
            val annotationClass = nameForClass("java", "lang", "annotation", "Annotation")
            val enumClass = nameForClass("java", "lang", "Enum")

            /** Generates a class name from the package and class names in [nameParts] */
            fun nameForClass(vararg nameParts: String): String {
                val separator = if (useInternalNames) "/" else "."
                return nameParts.joinToString(separator)
            }

            /** The names of the doclava enum methods, based on [useInternalNames] */
            fun enumMethodNames(className: String): List<String> {
                return if (useInternalNames) {
                    listOf("valueOf(Ljava/lang/String;)L$className;", "values()[L$className;")
                } else {
                    listOf("valueOf(java.lang.String)", "values()")
                }
            }
        }
    )
}

/**
 * Like [MethodItem.internalName] but is the desc-portion of the internal signature, e.g. for the
 * method "void create(int x, int y)" the internal name of the constructor is "create" and the desc
 * is "(II)V"
 */
fun MethodItem.internalDesc(voidConstructorTypes: Boolean = false): String {
    val sb = StringBuilder()
    sb.append("(")

    // Non-static inner classes get an implicit constructor parameter for the
    // outer type
    if (
        isConstructor() &&
            containingClass().containingClass() != null &&
            !containingClass().modifiers.isStatic()
    ) {
        sb.append(containingClass().containingClass()?.type()?.internalName() ?: "")
    }

    for (parameter in parameters()) {
        sb.append(parameter.type().internalName())
    }

    sb.append(")")
    sb.append(if (voidConstructorTypes && isConstructor()) "V" else returnType().internalName())
    return sb.toString()
}
