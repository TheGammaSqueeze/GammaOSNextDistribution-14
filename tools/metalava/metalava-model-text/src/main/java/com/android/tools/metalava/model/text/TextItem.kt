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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.DefaultItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.reporter.FileLocation

internal abstract class TextItem(
    override val codebase: TextCodebase,
    override val fileLocation: FileLocation,
    override var docOnly: Boolean = false,
    override var documentation: String = "",
    modifiers: DefaultModifierList,
) : DefaultItem(modifiers) {

    override var originallyHidden = false
    override var hidden = false
    override var removed = false

    override fun findTagDocumentation(tag: String, value: String?): String? = null

    override fun appendDocumentation(comment: String, tagSection: String?, append: Boolean) =
        codebase.unsupported()

    override fun isJava(): Boolean =
        codebase.unsupported() // source language not recorded in signature files

    override fun isKotlin(): Boolean =
        codebase.unsupported() // source language not recorded in signature files
}
