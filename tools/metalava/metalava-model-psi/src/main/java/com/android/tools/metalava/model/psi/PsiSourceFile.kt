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

package com.android.tools.metalava.model.psi

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Import
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.SourceFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiWhiteSpace
import java.util.TreeSet
import java.util.function.Predicate
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.startOffset
import org.jetbrains.uast.UFile

/** Whether we should limit import statements to symbols found in class docs */
private const val ONLY_IMPORT_CLASSES_REFERENCED_IN_DOCS = true

internal class PsiSourceFile(
    val codebase: PsiBasedCodebase,
    val file: PsiFile,
    val uFile: UFile? = null
) : SourceFile {
    override fun getHeaderComments(): String? {
        if (uFile != null) {
            var comment: String? = null
            for (uComment in uFile.allCommentsInFile) {
                val text = uComment.text
                comment =
                    if (comment != null) {
                        comment + "\n" + text
                    } else {
                        text
                    }
            }
            return comment
        }

        // https://youtrack.jetbrains.com/issue/KT-22135
        if (file is PsiJavaFile) {
            val pkg = file.packageStatement ?: return null
            return file.text.substring(0, pkg.startOffset)
        } else if (file is KtFile) {
            var curr: PsiElement? = file.firstChild
            var comment: String? = null
            while (curr != null) {
                if (curr is PsiComment || curr is KDoc) {
                    val text = curr.text
                    comment =
                        if (comment != null) {
                            comment + "\n" + text
                        } else {
                            text
                        }
                } else if (curr !is PsiWhiteSpace) {
                    break
                }
                curr = curr.nextSibling
            }
            return comment
        }

        return super.getHeaderComments()
    }

    override fun getImports(predicate: Predicate<Item>): Collection<Import> {
        val imports = TreeSet<Import>(compareBy { it.pattern })

        if (file is PsiJavaFile) {
            val importList = file.importList
            if (importList != null) {
                for (importStatement in importList.importStatements) {
                    val resolved = importStatement.resolve() ?: continue
                    if (resolved is PsiClass) {
                        val classItem = codebase.findClass(resolved) ?: continue
                        if (predicate.test(classItem)) {
                            imports.add(Import(classItem))
                        }
                    } else if (resolved is PsiPackage) {
                        val pkgItem = codebase.findPackage(resolved.qualifiedName) ?: continue
                        if (
                            predicate.test(pkgItem) &&
                                // Also make sure it isn't an empty package (after applying the
                                // filter)
                                // since in that case we'd have an invalid import
                                pkgItem.topLevelClasses().any { it.emit && predicate.test(it) }
                        ) {
                            imports.add(Import(pkgItem))
                        }
                    } else if (resolved is PsiMethod) {
                        codebase.findClass(resolved.containingClass ?: continue) ?: continue
                        val methodItem = codebase.findMethod(resolved)
                        if (predicate.test(methodItem)) {
                            imports.add(Import(methodItem))
                        }
                    } else if (resolved is PsiField) {
                        val classItem =
                            codebase.findClass(resolved.containingClass ?: continue) ?: continue
                        val fieldItem =
                            classItem.findField(
                                resolved.name,
                                includeSuperClasses = true,
                                includeInterfaces = false
                            )
                                ?: continue
                        if (predicate.test(fieldItem)) {
                            imports.add(Import(fieldItem))
                        }
                    }
                }
            }
        } else if (file is KtFile) {
            for (importDirective in file.importDirectives) {
                val resolved = importDirective.reference?.resolve() ?: continue
                if (resolved is PsiClass) {
                    val classItem = codebase.findClass(resolved) ?: continue
                    if (predicate.test(classItem)) {
                        imports.add(Import(classItem))
                    }
                }
            }
        }

        // Next only keep those that are present in any docs; those are the only ones
        // we need to import
        if (imports.isNotEmpty()) {
            @Suppress("ConstantConditionIf")
            return if (ONLY_IMPORT_CLASSES_REFERENCED_IN_DOCS) {
                filterImports(imports, predicate)
            } else {
                imports
            }
        }

        return emptyList()
    }

    override fun classes(): Sequence<ClassItem> {
        return (file as? PsiClassOwner)
            ?.classes
            ?.asSequence()
            ?.mapNotNull { codebase.findClass(it) }
            .orEmpty()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return other is PsiSourceFile && file == other.file
    }

    override fun hashCode(): Int = file.hashCode()

    override fun toString(): String = "file ${file.virtualFile?.path}"
}
