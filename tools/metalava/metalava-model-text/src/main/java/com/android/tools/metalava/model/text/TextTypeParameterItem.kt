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

package com.android.tools.metalava.model.text

import com.android.tools.metalava.model.BoundsTypeItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.TypeParameterItem
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.type.DefaultTypeModifiers
import com.android.tools.metalava.model.type.DefaultVariableTypeItem
import com.android.tools.metalava.reporter.FileLocation

internal class TextTypeParameterItem(
    codebase: TextCodebase,
    private val name: String,
    private val isReified: Boolean,
) :
    TextItem(
        codebase = codebase,
        fileLocation = FileLocation.UNKNOWN,
        modifiers = DefaultModifierList(codebase, DefaultModifierList.PUBLIC),
    ),
    TypeParameterItem {

    lateinit var bounds: List<BoundsTypeItem>

    override fun name(): String {
        return name
    }

    override fun type(): VariableTypeItem {
        return DefaultVariableTypeItem(DefaultTypeModifiers.create(emptyList()), this)
    }

    override fun typeBounds(): List<BoundsTypeItem> = bounds

    override fun isReified(): Boolean = isReified

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TypeParameterItem) return false

        return name == other.name()
    }

    override fun hashCode(): Int {
        return name.hashCode()
    }

    companion object {

        /**
         * Create a partially initialized [TextTypeParameterItem].
         *
         * This extracts the [isReified] and [name] from the [typeParameterString] and creates a
         * [TextTypeParameterItem] with those properties initialized but the [bounds] is not.
         *
         * This must ONLY be used by [ApiFile.createTypeParameterList] as that will complete the
         * initialization of the [bounds] property.
         */
        fun create(
            codebase: TextCodebase,
            typeParameterString: String,
        ): TextTypeParameterItem {
            val length = typeParameterString.length
            var nameEnd = length

            val isReified = typeParameterString.startsWith("reified ")
            val nameStart =
                if (isReified) {
                    8 // "reified ".length
                } else {
                    0
                }

            for (i in nameStart until length) {
                val c = typeParameterString[i]
                if (!Character.isJavaIdentifierPart(c)) {
                    nameEnd = i
                    break
                }
            }
            val name = typeParameterString.substring(nameStart, nameEnd)

            return TextTypeParameterItem(
                codebase = codebase,
                name = name,
                isReified = isReified,
            )
        }
    }
}
