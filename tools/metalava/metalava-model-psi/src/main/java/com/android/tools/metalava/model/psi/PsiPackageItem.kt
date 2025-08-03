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

package com.android.tools.metalava.model.psi

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.VisibilityLevel
import com.intellij.psi.PsiPackage

class PsiPackageItem
internal constructor(
    codebase: PsiBasedCodebase,
    private val psiPackage: PsiPackage,
    private val qualifiedName: String,
    modifiers: DefaultModifierList,
    documentation: String,
    override val overviewDocumentation: String?,
    /** True if this package is from the classpath (dependencies). Exposed in [isFromClassPath]. */
    private val fromClassPath: Boolean
) :
    PsiItem(
        codebase = codebase,
        modifiers = modifiers,
        documentation = documentation,
        element = psiPackage
    ),
    PackageItem {

    // Note - top level classes only
    private val classes: MutableList<ClassItem> = mutableListOf()

    override fun topLevelClasses(): Sequence<ClassItem> =
        classes.toList().asSequence().filter { it.isTopLevelClass() }

    lateinit var containingPackageField: PsiPackageItem

    override fun containingClass(): ClassItem? = null

    override fun psi() = psiPackage

    override fun containingPackage(): PackageItem? {
        return if (qualifiedName.isEmpty()) null
        else {
            if (!::containingPackageField.isInitialized) {
                var parentPackage = qualifiedName
                while (true) {
                    val index = parentPackage.lastIndexOf('.')
                    if (index == -1) {
                        containingPackageField = codebase.findPackage("")!!
                        return containingPackageField
                    }
                    parentPackage = parentPackage.substring(0, index)
                    val pkg = codebase.findPackage(parentPackage)
                    if (pkg != null) {
                        containingPackageField = pkg
                        return pkg
                    }
                }

                @Suppress("UNREACHABLE_CODE") null
            } else {
                containingPackageField
            }
        }
    }

    fun addClass(cls: PsiClassItem) {
        if (!cls.isTopLevelClass()) {
            // TODO: Stash in a list somewhere to make allClasses() faster?
            return
        }

        /*
        // Temp debugging:
        val q = cls.qualifiedName()
        for (c in classes) {
            if (q == c.qualifiedName()) {
                assert(false, { "Unexpectedly found class $q already listed in $this" })
                return
            }
        }
        */

        classes.add(cls)
        cls.containingPackage = this
    }

    fun addClasses(classList: List<PsiClassItem>) {
        for (cls in classList) {
            addClass(cls)
        }
    }

    override fun qualifiedName(): String = qualifiedName

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        return other is PackageItem && qualifiedName == other.qualifiedName()
    }

    override fun hashCode(): Int = qualifiedName.hashCode()

    override fun finishInitialization() {
        super.finishInitialization()

        // Take a copy of the list just in case additional classes are added during iteration. Those
        // classes will have their [PsiClassItem.finishInitialization] called so there is no need to
        // handle them here.
        val initialClasses = ArrayList(classes)
        for (cls in initialClasses) {
            if (cls is PsiClassItem) cls.finishInitialization()
        }
    }

    override fun isFromClassPath(): Boolean = fromClassPath

    companion object {
        fun create(
            codebase: PsiBasedCodebase,
            psiPackage: PsiPackage,
            extraDocs: String?,
            overviewHtml: String?,
            fromClassPath: Boolean
        ): PsiPackageItem {
            val commentText = javadoc(psiPackage) + if (extraDocs != null) "\n$extraDocs" else ""
            val modifiers = modifiers(codebase, psiPackage, commentText)
            if (modifiers.isPackagePrivate()) {
                // packages are always public (if not hidden explicitly with private)
                modifiers.setVisibilityLevel(VisibilityLevel.PUBLIC)
            }
            val qualifiedName = psiPackage.qualifiedName

            val pkg =
                PsiPackageItem(
                    codebase = codebase,
                    psiPackage = psiPackage,
                    qualifiedName = qualifiedName,
                    documentation = commentText,
                    overviewDocumentation = overviewHtml,
                    modifiers = modifiers,
                    fromClassPath = fromClassPath
                )
            return pkg
        }
    }
}
