/*
 * Copyright (C) 2023 The Android Open Source Project
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
import com.android.tools.metalava.model.Import
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.SourceFile
import com.google.turbine.diag.LineMap
import com.google.turbine.tree.Tree.CompUnit
import java.util.TreeSet
import java.util.function.Predicate

internal class TurbineSourceFile(
    val codebase: TurbineBasedCodebase,
    val compUnit: CompUnit,
) : SourceFile {

    override fun getHeaderComments() = getHeaderComments(compUnit.source().source())

    override fun classes(): Sequence<ClassItem> {
        val pkgName = getPackageName(compUnit)
        val classDecls = compUnit.decls() // Top level class declarations
        val classNames = classDecls.map { pkgName + "." + it.name().value() }
        return classNames.asSequence().mapNotNull { codebase.findClass(it) }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is TurbineSourceFile && compUnit == other.compUnit
    }

    override fun hashCode(): Int {
        return compUnit.hashCode()
    }

    override fun getImports(predicate: Predicate<Item>): Collection<Import> {
        val imports = TreeSet<Import>(compareBy { it.pattern })

        for (import in compUnit.imports()) {
            val resolvedName = extractNameFromIdent(import.type())
            // Package import
            if (import.wild()) {
                val pkgItem = codebase.findPackage(resolvedName) ?: continue
                if (
                    predicate.test(pkgItem) &&
                        // Also make sure it isn't an empty package (after applying the
                        // filter)
                        // since in that case we'd have an invalid import
                        pkgItem.topLevelClasses().any { it.emit && predicate.test(it) }
                ) {
                    imports.add(Import(pkgItem))
                }
            }
            // Not static member import i.e. class import
            else if (!import.stat()) {
                val classItem = codebase.findClass(resolvedName) ?: continue
                if (predicate.test(classItem)) {
                    imports.add(Import(classItem))
                }
            }
        }

        // Next only keep those that are present in any docs; those are the only ones
        // we need to import
        if (imports.isNotEmpty()) {
            return filterImports(imports, predicate)
        }

        return emptyList()
    }

    /**
     * The [LineMap] used to map positions in the source file into line numbers.
     *
     * Created lazily as it can be expensive to create.
     */
    private val lineMap by
        lazy(LazyThreadSafetyMode.NONE) { LineMap.create(compUnit.source().source()) }

    /**
     * Get the line number for [position] which was retrieved from
     * [com.google.turbine.tree.Tree.position].
     */
    fun lineForPosition(position: Int) = lineMap.lineNumber(position)
}
