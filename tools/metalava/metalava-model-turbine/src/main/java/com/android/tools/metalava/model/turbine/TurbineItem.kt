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

import com.android.tools.metalava.model.DefaultItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.source.utils.LazyDelegate
import com.android.tools.metalava.reporter.FileLocation

internal abstract class TurbineItem(
    override val codebase: TurbineBasedCodebase,
    override val fileLocation: FileLocation,
    modifiers: DefaultModifierList,
    final override var documentation: String,
) : DefaultItem(modifiers) {

    override var docOnly: Boolean = documentation.contains("@doconly")

    override var hidden: Boolean by LazyDelegate { originallyHidden && !hasShowAnnotation() }

    override var originallyHidden: Boolean by LazyDelegate {
        documentation.contains("@hide") || documentation.contains("@pending") || hasHideAnnotation()
    }

    override var removed: Boolean = false

    override fun appendDocumentation(comment: String, tagSection: String?, append: Boolean) {
        TODO("b/295800205")
    }

    override fun findTagDocumentation(tag: String, value: String?): String? {
        TODO("b/295800205")
    }
}
