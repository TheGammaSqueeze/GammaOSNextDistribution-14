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

import com.android.tools.metalava.SdkIdentifier
import java.io.PrintStream
import java.util.Collections
import java.util.TreeMap
import java.util.TreeSet

/** Represents the whole Android API. */
class Api(private val mMin: Int) : ApiElement("Android API") {
    private val mClasses: MutableMap<String, ApiClass> = HashMap()

    /**
     * Prints the whole API definition to a stream.
     *
     * @param stream the stream to print the XML elements to
     */
    fun print(stream: PrintStream, sdkIdentifiers: Set<SdkIdentifier>) {
        stream.print("<api version=\"3\"")
        if (mMin > 1) {
            stream.print(" min=\"$mMin\"")
        }
        stream.println(">")
        for ((id, shortname, name, reference) in sdkIdentifiers) {
            stream.println(
                String.format(
                    "\t<sdk id=\"%d\" shortname=\"%s\" name=\"%s\" reference=\"%s\"/>",
                    id,
                    shortname,
                    name,
                    reference
                )
            )
        }
        print(mClasses.values, "class", "\t", stream)
        printClosingTag("api", "", stream)
    }

    /**
     * Adds or updates a class.
     *
     * @param name the name of the class
     * @param version an API version in which the class existed
     * @param deprecated whether the class was deprecated in the API version
     * @return the newly created or a previously existed class
     */
    fun addClass(name: String, version: Int, deprecated: Boolean): ApiClass {
        var classElement = mClasses[name]
        if (classElement == null) {
            classElement = ApiClass(name, version, deprecated)
            mClasses[name] = classElement
        } else {
            classElement.update(version, deprecated)
        }
        return classElement
    }

    fun findClass(name: String?): ApiClass? {
        return if (name == null) null else mClasses[name]
    }

    /** Cleans up the API surface for printing after all elements have been added. */
    fun clean() {
        inlineFromHiddenSuperClasses()
        removeImplicitInterfaces()
        removeOverridingMethods()
        prunePackagePrivateClasses()
    }

    val classes: Collection<ApiClass>
        get() = Collections.unmodifiableCollection(mClasses.values)

    fun backfillHistoricalFixes() {
        backfillSdkExtensions()
    }

    private fun backfillSdkExtensions() {
        // SdkExtensions.getExtensionVersion was added in 30/R, but was a SystemApi
        // to avoid publishing the versioning API publicly before there was any
        // valid use for it.
        // getAllExtensionsVersions was added as part of 31/S
        // The class and its APIs were made public between S and T, but we pretend
        // here like it was always public, for maximum backward compatibility.
        val sdkExtensions = findClass("android/os/ext/SdkExtensions")
        if (sdkExtensions != null && sdkExtensions.since != 30 && sdkExtensions.since != 33) {
            throw AssertionError("Received unexpected historical data")
        } else if (sdkExtensions == null || sdkExtensions.since == 30) {
            // This is the system API db (30), or module-lib/system-server dbs (null)
            // They don't need patching.
            return
        }
        sdkExtensions.update(30, false)
        sdkExtensions.addSuperClass("java/lang/Object", 30)
        sdkExtensions.getMethod("getExtensionVersion(I)I")!!.update(30, false)
        sdkExtensions.getMethod("getAllExtensionVersions()Ljava/util/Map;")!!.update(31, false)
    }

    /**
     * The bytecode visitor registers interfaces listed for a class. However, a class will **also**
     * implement interfaces implemented by the super classes. This isn't available in the class
     * file, so after all classes have been read in, we iterate through all classes, and for those
     * that have interfaces, we check up the inheritance chain to see if it has already been
     * introduced in a super class at an earlier API level.
     */
    fun removeImplicitInterfaces() {
        for (classElement in mClasses.values) {
            classElement.removeImplicitInterfaces(mClasses)
        }
    }

    /** @see ApiClass.removeOverridingMethods */
    fun removeOverridingMethods() {
        for (classElement in mClasses.values) {
            classElement.removeOverridingMethods(mClasses)
        }
    }

    fun inlineFromHiddenSuperClasses() {
        val hidden: MutableMap<String, ApiClass> = HashMap()
        for (classElement in mClasses.values) {
            if (
                classElement.hiddenUntil < 0
            ) { // hidden in the .jar files? (mMax==codebase, -1: jar files)
                hidden[classElement.name] = classElement
            }
        }
        for (classElement in mClasses.values) {
            classElement.inlineFromHiddenSuperClasses(hidden)
        }
    }

    fun prunePackagePrivateClasses() {
        for (cls in mClasses.values) {
            cls.removeHiddenSuperClasses(mClasses)
        }
    }

    fun removeMissingClasses() {
        for (cls in mClasses.values) {
            cls.removeMissingClasses(mClasses)
        }
    }

    fun verifyNoMissingClasses() {
        val results: MutableMap<String?, MutableSet<String?>> = TreeMap()
        for (cls in mClasses.values) {
            val missing = cls.findMissingClasses(mClasses)
            // Have the missing classes as keys, and the referencing classes as values.
            for (missingClass in missing) {
                val missingName = missingClass.name
                if (!results.containsKey(missingName)) {
                    results[missingName] = TreeSet()
                }
                results[missingName]!!.add(cls.name)
            }
        }
        if (results.isNotEmpty()) {
            var message = ""
            for ((key, value) in results) {
                message += """
  $key referenced by:"""
                for (referencer in value) {
                    message += "\n    $referencer"
                }
            }
            throw IllegalStateException(
                "There are classes in this API that reference other " +
                    "classes that do not exist in this API. " +
                    "This can happen when an api is provided by an apex, but referenced " +
                    "from non-updatable platform code. Use --remove-missing-classes-in-api-levels to " +
                    "make metalava remove these references instead of erroring out." +
                    message
            )
        }
    }
}
