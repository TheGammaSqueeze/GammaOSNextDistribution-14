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
package com.android.tools.metalava.apilevels

import com.google.common.collect.Iterables
import java.io.PrintStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.min

/**
 * Represents a class or an interface and its methods/fields. This is used to write the simplified
 * XML file containing all the public API.
 */
class ApiClass(name: String, version: Int, deprecated: Boolean) :
    ApiElement(name, version, deprecated) {
    private val mSuperClasses: MutableList<ApiElement> = ArrayList()
    private val mInterfaces: MutableList<ApiElement> = ArrayList()

    /**
     * If negative, never seen as public. The absolute value is the last api level it is seen as
     * hidden in. E.g. "-5" means a class that was hidden in api levels 1-5, then it was deleted,
     * and "8" means a class that was hidden in api levels 1-8 then made public in 9.
     */
    var hiddenUntil = 0 // Package private class?
    private val mFields: MutableMap<String, ApiElement> = ConcurrentHashMap()
    private val mMethods: MutableMap<String, ApiElement> = ConcurrentHashMap()

    fun addField(name: String, version: Int, deprecated: Boolean): ApiElement {
        return addToMap(mFields, name, version, deprecated)
    }

    val fields: Collection<ApiElement>
        get() = mFields.values

    fun addMethod(name: String, version: Int, deprecated: Boolean): ApiElement {
        // Correct historical mistake in android.jar files
        var correctedName = name
        if (correctedName.endsWith(")Ljava/lang/AbstractStringBuilder;")) {
            correctedName =
                correctedName.substring(
                    0,
                    correctedName.length - ")Ljava/lang/AbstractStringBuilder;".length
                ) + ")L" + this.name + ";"
        }
        return addToMap(mMethods, correctedName, version, deprecated)
    }

    val methods: Collection<ApiElement>
        get() = mMethods.values

    fun addSuperClass(superClass: String, since: Int): ApiElement {
        return addToArray(mSuperClasses, superClass, since)
    }

    fun removeSuperClass(superClass: String): ApiElement? {
        val entry = findByName(mSuperClasses, superClass)
        if (entry != null) {
            mSuperClasses.remove(entry)
        }
        return entry
    }

    val superClasses: List<ApiElement>
        get() = mSuperClasses

    fun updateHidden(api: Int, hidden: Boolean) {
        hiddenUntil = if (hidden) -api else abs(api)
    }

    private fun alwaysHidden(): Boolean {
        return hiddenUntil < 0
    }

    fun addInterface(interfaceClass: String, since: Int) {
        addToArray(mInterfaces, interfaceClass, since)
    }

    val interfaces: List<ApiElement>
        get() = mInterfaces

    private fun addToMap(
        elements: MutableMap<String, ApiElement>,
        name: String,
        version: Int,
        deprecated: Boolean
    ): ApiElement {
        var element = elements[name]
        if (element == null) {
            element = ApiElement(name, version, deprecated)
            elements[name] = element
        } else {
            element.update(version, deprecated)
        }
        return element
    }

    private fun addToArray(
        elements: MutableCollection<ApiElement>,
        name: String,
        version: Int
    ): ApiElement {
        var element = findByName(elements, name)
        if (element == null) {
            element = ApiElement(name, version)
            elements.add(element)
        } else {
            element.update(version)
        }
        return element
    }

    private fun findByName(collection: Collection<ApiElement>, name: String): ApiElement? {
        for (element in collection) {
            if (element.name == name) {
                return element
            }
        }
        return null
    }

    override fun print(
        tag: String?,
        parentElement: ApiElement,
        indent: String,
        stream: PrintStream
    ) {
        if (hiddenUntil < 0) {
            return
        }
        super.print(tag, false, parentElement, indent, stream)
        val innerIndent = indent + '\t'
        print(mSuperClasses, "extends", innerIndent, stream)
        print(mInterfaces, "implements", innerIndent, stream)
        print(mMethods.values, "method", innerIndent, stream)
        print(mFields.values, "field", innerIndent, stream)
        printClosingTag(tag, indent, stream)
    }

    /**
     * Removes all interfaces that are also implemented by superclasses or extended by interfaces
     * this class implements.
     *
     * @param allClasses all classes keyed by their names.
     */
    fun removeImplicitInterfaces(allClasses: Map<String, ApiClass>) {
        if (mInterfaces.isEmpty() || mSuperClasses.isEmpty()) {
            return
        }
        val iterator = mInterfaces.iterator()
        while (iterator.hasNext()) {
            val interfaceElement = iterator.next()
            for (superClass in mSuperClasses) {
                if (superClass.introducedNotLaterThan(interfaceElement)) {
                    val cls = allClasses[superClass.name]
                    if (cls != null && cls.implementsInterface(interfaceElement, allClasses)) {
                        iterator.remove()
                        break
                    }
                }
            }
        }
    }

    private fun implementsInterface(
        interfaceElement: ApiElement,
        allClasses: Map<String, ApiClass>
    ): Boolean {
        for (localInterface in mInterfaces) {
            if (localInterface.introducedNotLaterThan(interfaceElement)) {
                if (interfaceElement.name == localInterface.name) {
                    return true
                }
                // Check parent interface.
                val cls = allClasses[localInterface.name]
                if (cls != null && cls.implementsInterface(interfaceElement, allClasses)) {
                    return true
                }
            }
        }
        for (superClass in mSuperClasses) {
            if (superClass.introducedNotLaterThan(interfaceElement)) {
                val cls = allClasses[superClass.name]
                if (cls != null && cls.implementsInterface(interfaceElement, allClasses)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Removes all methods that override method declared by superclasses and interfaces of this
     * class.
     *
     * @param allClasses all classes keyed by their names.
     */
    fun removeOverridingMethods(allClasses: Map<String, ApiClass>) {
        val it: MutableIterator<Map.Entry<String, ApiElement>> = mMethods.entries.iterator()
        while (it.hasNext()) {
            val (_, method) = it.next()
            if (!method.name.startsWith("<init>(") && isOverrideOfInherited(method, allClasses)) {
                it.remove()
            }
        }
    }

    /**
     * Checks if the given method overrides one of the methods defined by this class or its
     * superclasses or interfaces.
     *
     * @param method the method to check
     * @param allClasses the map containing all API classes
     * @return true if the method is an override
     */
    private fun isOverride(method: ApiElement, allClasses: Map<String, ApiClass>): Boolean {
        val name = method.name
        val localMethod = mMethods[name]
        return if (localMethod != null && localMethod.introducedNotLaterThan(method)) {
            // This class has the method, and it was introduced in at the same api level
            // as the child method, or before.
            true
        } else {
            isOverrideOfInherited(method, allClasses)
        }
    }

    /**
     * Checks if the given method overrides one of the methods declared by ancestors of this class.
     */
    private fun isOverrideOfInherited(
        method: ApiElement,
        allClasses: Map<String, ApiClass>
    ): Boolean {
        // Check this class' parents.
        for (parent in Iterables.concat(mSuperClasses, mInterfaces)) {
            // Only check the parent if it was a parent class at the introduction of the method.
            if (parent!!.introducedNotLaterThan(method)) {
                val cls = allClasses[parent.name]
                if (cls != null && cls.isOverride(method, allClasses)) {
                    return true
                }
            }
        }
        return false
    }

    override fun toString(): String {
        return name
    }

    private var haveInlined = false

    fun inlineFromHiddenSuperClasses(hidden: Map<String, ApiClass>) {
        if (haveInlined) {
            return
        }
        haveInlined = true
        for (superClass in superClasses) {
            val hiddenSuper = hidden[superClass.name]
            if (hiddenSuper != null) {
                hiddenSuper.inlineFromHiddenSuperClasses(hidden)
                val myMethods = mMethods
                val myFields = mFields
                for ((name, value) in hiddenSuper.mMethods) {
                    if (!myMethods.containsKey(name)) {
                        myMethods[name] = value
                    }
                }
                for ((name, value) in hiddenSuper.mFields) {
                    if (!myFields.containsKey(name)) {
                        myFields[name] = value
                    }
                }
            }
        }
    }

    fun removeHiddenSuperClasses(api: Map<String, ApiClass>) {
        // If we've included a package private class in the super class map (from the older
        // android.jar files)
        // remove these here and replace with the filtered super classes, updating API levels in the
        // process
        val iterator = mSuperClasses.listIterator()
        var min = Int.MAX_VALUE
        while (iterator.hasNext()) {
            val next = iterator.next()
            min = min(min, next.since)
            val extendsClass = api[next.name]
            if (extendsClass != null && extendsClass.alwaysHidden()) {
                val since = extendsClass.since
                iterator.remove()
                for (other in mSuperClasses) {
                    if (other.since >= since) {
                        other.update(min)
                    }
                }
                break
            }
        }
    }

    // Ensure this class doesn't extend/implement any other classes/interfaces that are
    // not in the provided api. This can happen when a class in an android.jar file
    // encodes the inheritance, but the class that is inherited is not present in any
    // android.jar file. The class would instead be present in an apex's stub jar file.
    // An example of this is the QosSessionAttributes interface being provided by the
    // Connectivity apex, but being implemented by NrQosSessionAttributes from
    // frameworks/base/telephony.
    fun removeMissingClasses(api: Map<String, ApiClass>) {
        val superClassIter = mSuperClasses.iterator()
        while (superClassIter.hasNext()) {
            val scls = superClassIter.next()
            if (!api.containsKey(scls.name)) {
                superClassIter.remove()
            }
        }
        val interfacesIter = mInterfaces.iterator()
        while (interfacesIter.hasNext()) {
            val intf = interfacesIter.next()
            if (!api.containsKey(intf.name)) {
                interfacesIter.remove()
            }
        }
    }

    // Returns the set of superclasses or interfaces are not present in the provided api map
    fun findMissingClasses(api: Map<String, ApiClass>): Set<ApiElement> {
        val result: MutableSet<ApiElement> = HashSet()
        for (scls in mSuperClasses) {
            if (!api.containsKey(scls.name)) {
                result.add(scls)
            }
        }
        for (intf in mInterfaces) {
            if (!api.containsKey(intf.name)) {
                result.add(intf)
            }
        }
        return result
    }

    val fieldIterator: Iterator<ApiElement>
        get() = mFields.values.iterator()

    val methodIterator: Iterator<ApiElement>
        get() = mMethods.values.iterator()

    fun getMethod(name: String?): ApiElement? {
        return mMethods[name]
    }
}
