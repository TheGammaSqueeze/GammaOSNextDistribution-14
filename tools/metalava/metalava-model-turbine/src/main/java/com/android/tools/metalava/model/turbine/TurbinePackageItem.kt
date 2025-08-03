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

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.VisibilityLevel
import com.android.tools.metalava.reporter.FileLocation

internal class TurbinePackageItem(
    codebase: TurbineBasedCodebase,
    fileLocation: FileLocation,
    private val qualifiedName: String,
    modifiers: DefaultModifierList,
    documentation: String,
) : TurbineItem(codebase, fileLocation, modifiers, documentation), PackageItem {

    private var topClasses = mutableListOf<TurbineClassItem>()

    private var containingPackage: PackageItem? = null

    companion object {
        fun create(
            codebase: TurbineBasedCodebase,
            fileLocation: FileLocation,
            qualifiedName: String,
            modifiers: DefaultModifierList,
            documentation: String,
        ): TurbinePackageItem {
            if (modifiers.isPackagePrivate()) {
                // packages are always public (if not hidden explicitly with private)
                modifiers.setVisibilityLevel(VisibilityLevel.PUBLIC)
            }
            return TurbinePackageItem(
                codebase,
                fileLocation,
                qualifiedName,
                modifiers,
                documentation
            )
        }
    }
    // N.A. a package cannot be contained in a class
    override fun containingClass(): ClassItem? = null

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        return other is PackageItem && qualifiedName == other.qualifiedName()
    }

    override fun hashCode(): Int = qualifiedName.hashCode()

    override fun qualifiedName(): String = qualifiedName

    override fun topLevelClasses(): Sequence<ClassItem> = topClasses.asSequence()

    internal fun addTopClass(classItem: TurbineClassItem) {
        topClasses.add(classItem)
    }

    override fun containingPackage(): PackageItem? {
        // if this package is root package, then return null
        return if (qualifiedName.isEmpty()) null
        else {
            if (containingPackage == null) {
                // If package is of the form A.B then the containing package is A
                // If package is top level, then containing package is the root package
                val name = qualifiedName()
                val lastDot = name.lastIndexOf('.')
                containingPackage =
                    if (lastDot != -1) codebase.findPackage(name.substring(0, lastDot))
                    else codebase.findPackage("")
            }
            return containingPackage
        }
    }
}
