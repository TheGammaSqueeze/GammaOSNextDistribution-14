/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.google.turbine.tree.Tree.CompUnit
import com.google.turbine.tree.Tree.Ident

/**
 * Extracts the package name from a provided compilation unit.
 *
 * @param unit The compilation unit from which to extract the package.
 * @return The extracted package name (e.g., "com.example.project"), or an empty string if no
 *   package is present.
 */
internal fun getPackageName(unit: CompUnit): String {
    val optPkg = unit.pkg()
    val pkg = if (optPkg.isPresent()) optPkg.get() else null
    return pkg?.let { extractNameFromIdent(it.name()) } ?: ""
}

/**
 * Extracts a dot-separated name from a list of Ident objects. This is often used for constructing
 * fully qualified names or package structures.
 *
 * @param identNameList The list of Ident objects representing name segments.
 * @return The combined name with segments joined by "." (e.g., "java.util.List")
 */
internal fun extractNameFromIdent(identNameList: List<Ident>): String {
    val nameList = identNameList.map { it.value() }
    return nameList.joinToString(separator = ".")
}

/**
 * Extracts header comments from a source file string. Header comments are defined as any content
 * appearing before the "package" keyword.
 *
 * @param source The source file string.
 * @return The extracted header comments, or an empty string if no "package" keyword or comments are
 *   found.
 */
internal fun getHeaderComments(source: String): String {
    val packageIndex = source.indexOf("package")
    // Return everything before "package" keyword
    return if (packageIndex == -1) "" else source.substring(0, packageIndex)
}
