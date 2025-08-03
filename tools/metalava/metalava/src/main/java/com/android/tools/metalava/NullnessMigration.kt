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

package com.android.tools.metalava

import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.SUPPORT_TYPE_USE_ANNOTATIONS
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.findAnnotation
import com.android.tools.metalava.model.hasAnnotation

/**
 * Performs null migration analysis, looking at previous API signature files and new signature
 * files, and replacing new @Nullable and @NonNull annotations with @RecentlyNullable
 * and @RecentlyNonNull.
 *
 * TODO: Enforce compatibility across type use annotations, e.g. changing parameter value from
 *   {@code @NonNull List<@Nullable String>} to {@code @NonNull List<@NonNull String>} is forbidden.
 */
class NullnessMigration : ComparisonVisitor(visitAddedItemsRecursively = true) {
    override fun compare(old: Item, new: Item) {
        if (hasNullnessInformation(new) && !hasNullnessInformation(old)) {
            new.markRecent()
        }
    }

    // Note: We don't override added(new: Item) to mark newly added methods as newly
    // having nullness annotations: those APIs are themselves new, so there's no reason
    // to mark the nullness contract as migration (warning- rather than error-severity)

    override fun compare(old: MethodItem, new: MethodItem) {
        @Suppress("ConstantConditionIf")
        if (SUPPORT_TYPE_USE_ANNOTATIONS) {
            val newType = new.returnType()
            val oldType = old.returnType()
            checkType(oldType, newType)
        }
    }

    override fun compare(old: FieldItem, new: FieldItem) {
        @Suppress("ConstantConditionIf")
        if (SUPPORT_TYPE_USE_ANNOTATIONS) {
            val newType = new.type()
            val oldType = old.type()
            checkType(oldType, newType)
        }
    }

    override fun compare(old: ParameterItem, new: ParameterItem) {
        @Suppress("ConstantConditionIf")
        if (SUPPORT_TYPE_USE_ANNOTATIONS) {
            val newType = new.type()
            val oldType = old.type()
            checkType(oldType, newType)
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun hasNullnessInformation(type: TypeItem): Boolean {
        return if (SUPPORT_TYPE_USE_ANNOTATIONS) {
            // TODO: support type use
            false
        } else {
            false
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun checkType(old: TypeItem, new: TypeItem) {
        if (hasNullnessInformation(new)) {
            assert(SUPPORT_TYPE_USE_ANNOTATIONS)
            // TODO: support type use
        }
    }

    companion object {
        fun migrateNulls(codebase: Codebase, previous: Codebase) {
            CodebaseComparator(
                    apiVisitorConfig = @Suppress("DEPRECATION") options.apiVisitorConfig,
                )
                .compare(NullnessMigration(), previous, codebase)
        }

        fun hasNullnessInformation(item: Item): Boolean {
            return isNullable(item) || isNonNull(item)
        }

        fun findNullnessAnnotation(item: Item): AnnotationItem? {
            return item.modifiers.findAnnotation(AnnotationItem::isNullnessAnnotation)
        }

        fun isNullable(item: Item): Boolean {
            return item.modifiers.hasAnnotation(AnnotationItem::isNullable)
        }

        private fun isNonNull(item: Item): Boolean {
            return item.modifiers.hasAnnotation(AnnotationItem::isNonNull)
        }
    }
}

/**
 * Marks the nullability of this Item as Recent. That is, replaces @Nullable/@NonNull
 * with @RecentlyNullable/@RecentlyNonNull
 */
fun Item.markRecent() {
    val annotation = NullnessMigration.findNullnessAnnotation(this) ?: return
    // Nullness information change: Add migration annotation
    val annotationClass = if (annotation.isNullable()) RECENTLY_NULLABLE else RECENTLY_NONNULL

    val modifiers = mutableModifiers()
    modifiers.removeAnnotation(annotation)

    modifiers.addAnnotation(codebase.createAnnotation("@$annotationClass", this))
}
