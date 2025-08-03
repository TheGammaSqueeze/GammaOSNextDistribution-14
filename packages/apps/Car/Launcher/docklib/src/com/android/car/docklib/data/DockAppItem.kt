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

package com.android.car.docklib.data

import android.content.ComponentName
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import com.android.internal.graphics.ColorUtils
import com.android.launcher3.icons.FastBitmapDrawable
import com.android.launcher3.icons.GraphicsUtils
import java.util.UUID

/**
 * Data class that describes an app being showed on Dock.
 *
 * @param iconColor dominant color to be used with the [icon].
 * @param iconColorScrim color to be used to create a slightly dull/bright color variation of
 * [iconColor]. Uses the default scrim if not provided.
 */
data class DockAppItem(
        val id: @DockItemId UUID = UUID.randomUUID(),
        val type: Type,
        val component: ComponentName,
        val name: String,
        val icon: Drawable,
        @ColorInt val iconColor: Int,
        @ColorInt private val iconColorScrim: Int = defaultIconColorScrim,
        val isDistractionOptimized: Boolean,
        val isMediaApp: Boolean,
) {
    companion object{
        private val defaultIconColorScrim = GraphicsUtils.setColorAlphaBound(
                Color.WHITE,
                FastBitmapDrawable.WHITE_SCRIM_ALPHA
        )

        /**
         * Composes the [iconColor] with the [iconColorScrim].
         *
         * @param iconColorScrim color to be used to create a slightly dull/bright color variation
         * of [iconColor]. Uses the default scrim if not provided.
         */
        fun getIconColorWithScrim(
                iconColor: Int,
                iconColorScrim: Int = defaultIconColorScrim
        ): Int {
            return ColorUtils.compositeColors(iconColorScrim, iconColor)
        }
    }

    @ColorInt val iconColorWithScrim = getIconColorWithScrim(iconColor, iconColorScrim)

    enum class Type(val value: String) {
        DYNAMIC("DYNAMIC"),
        STATIC("STATIC");

        override fun toString(): String {
            return value
        }
    }

    override fun equals(other: Any?): Boolean {
        return (this === other) ||
                (other is DockAppItem &&
                        this.id == other.id &&
                        this.name == other.name &&
                        this.type == other.type &&
                        this.component == other.component &&
                        this.icon.constantState == other.icon.constantState &&
                        this.iconColor == other.iconColor &&
                        this.iconColorWithScrim == other.iconColorWithScrim &&
                        this.isDistractionOptimized == other.isDistractionOptimized &&
                        this.isMediaApp == other.isMediaApp)
    }

    override fun toString(): String {
        return ("DockAppItem#${hashCode()}{id: $id, name: $name, component: $component, " +
                "type: $type, isDistractionOptimized: $isDistractionOptimized, icon: $icon, " +
                "iconColor: $iconColor, iconColorScrim: $iconColorScrim, " +
                "iconColorWithScrim: $iconColorWithScrim}")
    }
}
