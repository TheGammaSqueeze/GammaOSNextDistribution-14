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

package com.android.tools.metalava.model.turbine

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.AnnotationManager
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.DefaultAnnotationItem
import com.android.tools.metalava.model.DefaultCodebase
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.PackageList
import com.android.tools.metalava.model.source.SourceCodebase
import com.google.turbine.tree.Tree.CompUnit
import java.io.File

const val PACKAGE_ESTIMATE = 500
const val CLASS_ESTIMATE = 15000

internal open class TurbineBasedCodebase(
    location: File,
    description: String = "Unknown",
    annotationManager: AnnotationManager,
) : DefaultCodebase(location, description, false, annotationManager), SourceCodebase {

    /**
     * Map from class name to class item. Classes are added via [populateClasses] while initialising
     * the codebase
     */
    private lateinit var classMap: MutableMap<String, TurbineClassItem>

    /** A list of all the classes. Primarily used by [iterateAllClasses]. */
    private lateinit var allClasses: MutableList<TurbineClassItem>

    /** Map from package name to the corresponding package item */
    private lateinit var packageMap: MutableMap<String, PackageItem>

    /**
     * A list of the top-level classes declared in the codebase's source (rather than on its
     * classpath).
     */
    private lateinit var topLevelClassesFromSource: MutableList<ClassItem>

    private lateinit var initializer: TurbineCodebaseInitialiser

    override fun createAnnotation(
        source: String,
        context: Item?,
    ): AnnotationItem {
        return DefaultAnnotationItem.create(this, source)
    }

    override fun findClass(className: String): TurbineClassItem? {
        return classMap[className]
    }

    override fun resolveClass(className: String) = findOrCreateClass(className)

    fun findOrCreateClass(className: String): TurbineClassItem? {
        return initializer.findOrCreateClass(className)
    }

    override fun findPackage(pkgName: String): PackageItem? {
        return packageMap[pkgName]
    }

    override fun getPackages(): PackageList {
        return PackageList(
            this,
            packageMap.values.toMutableList().sortedWith(PackageItem.comparator)
        )
    }

    override fun size(): Int {
        return packageMap.size
    }

    override fun supportsDocumentation(): Boolean = true

    override fun getTopLevelClassesFromSource(): List<ClassItem> {
        return topLevelClassesFromSource
    }

    fun addClass(classItem: TurbineClassItem, isTopClass: Boolean) {
        val qualifiedName = classItem.qualifiedName()
        val existing = classMap.put(qualifiedName, classItem)
        if (existing != null) {
            error(
                "Attempted to register $qualifiedName twice; once from ${existing.issueLocation.path} and this one from ${classItem.issueLocation.path}"
            )
        }

        if (isTopClass) {
            topLevelClassesFromSource.add(classItem)
        }

        allClasses.add(classItem)
    }

    fun addPackage(packageItem: TurbinePackageItem) {
        packageMap.put(packageItem.qualifiedName(), packageItem)
    }

    fun initialize(units: List<CompUnit>, classpath: List<File>) {
        topLevelClassesFromSource = ArrayList(CLASS_ESTIMATE)
        classMap = HashMap(CLASS_ESTIMATE)
        allClasses = ArrayList(CLASS_ESTIMATE)
        packageMap = HashMap(PACKAGE_ESTIMATE)
        initializer = TurbineCodebaseInitialiser(units, this, classpath)
        initializer.initialize()
    }

    /**
     * Iterate over all the [TurbineClassItem]s in the [TurbineBasedCodebase].
     *
     * If additional classes are added to the [TurbineBasedCodebase] by [body], e.g. by resolving a
     * `ClassTypeItem` to a class on the classpath that was not previously loaded, then they will be
     * included in the iteration.
     */
    fun iterateAllClasses(body: (TurbineClassItem) -> Unit) {
        // Iterate by index not using aan iterator to avoid `ConcurrentModificationException`s.
        // Limit the first round of iteration to just the classes that were present at the start.
        var start = 0
        var end = allClasses.size
        do {
            // Iterate over the classes in the selected range, invoking [body] pn each.
            for (i in start until end) {
                val classItem = allClasses[i]
                body(classItem)
            }

            // Move the range to include all the classes, if any, added during the previous round.
            start = end
            end = allClasses.size

            // Repeat until no new classes were added.
        } while (start < end)
    }
}
