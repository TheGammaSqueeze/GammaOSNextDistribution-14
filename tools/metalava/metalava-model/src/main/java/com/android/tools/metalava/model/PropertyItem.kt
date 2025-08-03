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

package com.android.tools.metalava.model

interface PropertyItem : MemberItem {
    /** The getter for this property, if it exists; inverse of [MethodItem.property] */
    val getter: MethodItem?
        get() = null

    /** The setter for this property, if it exists; inverse of [MethodItem.property] */
    val setter: MethodItem?
        get() = null

    /** The backing field for this property, if it exists; inverse of [FieldItem.property] */
    val backingField: FieldItem?
        get() = null

    /**
     * The constructor parameter for this property, if declared in a primary constructor; inverse of
     * [ParameterItem.property]
     */
    val constructorParameter: ParameterItem?
        get() = null

    /** The type of this property */
    override fun type(): TypeItem

    override fun findCorrespondingItemIn(codebase: Codebase) =
        containingClass().findCorrespondingItemIn(codebase)?.properties()?.find {
            it.name() == name()
        }

    /** [PropertyItem]s are never inherited. */
    override val inheritedFrom: ClassItem?
        get() = null

    /**
     * Duplicates this property item.
     *
     * Override to specialize the return type.
     */
    override fun duplicate(targetContainingClass: ClassItem): PropertyItem =
        codebase.unsupported("Not needed yet")

    override fun baselineElementId() =
        // TODO: Be consistent with other items.
        describe(false)

    override fun accept(visitor: ItemVisitor) {
        visitor.visit(this)
    }

    override fun toStringForItem(): String = "property ${containingClass().fullName()}.${name()}"

    override fun hasNullnessInfo(): Boolean {
        if (!requiresNullnessInfo()) {
            return true
        }

        return modifiers.hasNullnessInfo()
    }

    override fun requiresNullnessInfo(): Boolean {
        return type() !is PrimitiveTypeItem
    }

    companion object {
        val comparator: java.util.Comparator<PropertyItem> = Comparator { a, b ->
            a.name().compareTo(b.name())
        }
    }
}
