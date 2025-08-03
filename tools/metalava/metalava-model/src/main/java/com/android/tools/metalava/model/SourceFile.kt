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

import java.util.TreeSet
import java.util.function.Predicate

/** Represents a Kotlin/Java source file */
interface SourceFile {
    /** Top level classes contained in this file */
    fun classes(): Sequence<ClassItem>

    fun getHeaderComments(): String? = null

    fun getImports(predicate: Predicate<Item>): Collection<Import> = emptyList()

    /**
     * Compute set of import statements that are actually referenced from the documentation (we do
     * inexact matching here; we don't need to have an exact set of imports since it's okay to have
     * some extras). This isn't a big problem since our code style forbids/discourages wildcards, so
     * it shows up in fewer places, but we need to handle it when it does -- such as in ojluni.
     */
    fun filterImports(imports: TreeSet<Import>, predicate: Predicate<Item>): TreeSet<Import> {
        // Create a map from the short name for the import to a list of the items imported. A
        // list is needed because classes and members could be imported with the same short
        // name.
        val remainingImports = mutableMapOf<String, MutableList<Import>>()
        imports.groupByTo(remainingImports) { it.name }

        val result = TreeSet<Import>(compareBy { it.pattern })

        // We keep the wildcard imports since we don't know which ones of those are relevant
        imports.filter { it.name == "*" }.forEach { result.add(it) }

        for (cls in classes().filter { predicate.test(it) }) {
            cls.accept(
                object : TraversingVisitor() {
                    override fun visitItem(item: Item): TraversalAction {
                        // Do not let documentation on hidden items affect the imports.
                        if (!predicate.test(item)) {
                            // Just because an item like a class is hidden does not mean
                            // that its child items are so make sure to visit them.
                            return TraversalAction.CONTINUE
                        }
                        val doc = item.documentation
                        if (doc.isNotBlank()) {
                            // Scan the documentation text to see if it contains any of the
                            // short names imported. It does not check whether the names
                            // are actually used as part of a link, so they could just be in
                            // as text but having extra imports should not be an issue.
                            var found: MutableList<String>? = null
                            for (name in remainingImports.keys) {
                                if (docContainsWord(doc, name)) {
                                    if (found == null) {
                                        found = mutableListOf()
                                    }
                                    found.add(name)
                                }
                            }

                            // For every imported name add all the matching imports and then
                            // remove them from the available imports as there is no need to
                            // check them again.
                            found?.let {
                                for (name in found) {
                                    val all = remainingImports.remove(name) ?: continue
                                    result.addAll(all)
                                }

                                if (remainingImports.isEmpty()) {
                                    // There is nothing to do if the map of imports to add
                                    // is empty.
                                    return TraversalAction.SKIP_TRAVERSAL
                                }
                            }
                        }

                        return TraversalAction.CONTINUE
                    }
                }
            )
        }
        return result
    }

    fun docContainsWord(doc: String, word: String): Boolean {
        // Cache pattern compilation across source files
        val regexMap = HashMap<String, Regex>()

        if (!doc.contains(word)) {
            return false
        }

        val regex =
            regexMap[word]
                ?: run {
                    val new = Regex("""\b$word\b""")
                    regexMap[word] = new
                    new
                }
        return regex.find(doc) != null
    }
}

/** Encapsulates information about the imports used in a [SourceFile]. */
data class Import
internal constructor(
    /**
     * The import pattern, i.e. the whole part of the import statement after `import static? ` and
     * before the optional `;`, excluding any whitespace.
     */
    val pattern: String,

    /**
     * The name that is being imported, i.e. the part after the last `.`. Is `*` for wildcard
     * imports.
     */
    val name: String,

    /**
     * True if the item that is being imported is a member of a class. Corresponds to the `static`
     * keyword in Java, has no effect on Kotlin import statements.
     */
    val isMember: Boolean,
) {
    /** Import a whole [PackageItem], i.e. uses a wildcard. */
    constructor(pkgItem: PackageItem) : this("${pkgItem.qualifiedName()}.*", "*", false)

    /** Import a [ClassItem]. */
    constructor(
        classItem: ClassItem
    ) : this(
        classItem.qualifiedName(),
        classItem.simpleName(),
        false,
    )

    /** Import a [MemberItem]. */
    constructor(
        memberItem: MemberItem
    ) : this(
        "${memberItem.containingClass().qualifiedName()}.${memberItem.name()}",
        memberItem.name(),
        true,
    )
}
