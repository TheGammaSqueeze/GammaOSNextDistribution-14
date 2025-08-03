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

interface MemberItem : Item {
    /**
     * The name of this method/field. Constructors have the same name as their containing class'
     * simple name
     */
    fun name(): String

    /** Returns the internal name of the method, as seen in bytecode */
    fun internalName(): String = name()

    /** The containing class */
    @MetalavaApi override fun containingClass(): ClassItem

    override fun containingPackage(): PackageItem = containingClass().containingPackage()

    override fun parent(): ClassItem? = containingClass()

    /**
     * Returns true if this member is effectively final: it's either final itself, or implied to be
     * final because its containing class is final
     */
    fun isEffectivelyFinal(): Boolean {
        return modifiers.isFinal() ||
            containingClass().modifiers.isFinal() ||
            containingClass().modifiers.isSealed()
    }

    override fun implicitNullness(): TypeNullability? {
        // Delegate to the super class, only dropping through if it did not determine an implicit
        // nullness.
        super.implicitNullness()?.let { nullable ->
            return nullable
        }

        // Annotation type members cannot be null
        if (containingClass().isAnnotationType()) {
            return TypeNullability.NONNULL
        }

        return null
    }

    /** True if this member was inherited from an ancestor class or interface. */
    val inheritedFromAncestor
        get() = inheritedFrom != null

    /**
     * If this member is inherited from a super class (typically via [duplicate]) this field points
     * to the original class it was inherited from
     */
    val inheritedFrom: ClassItem?

    /**
     * Duplicates this member item.
     *
     * This is only used when comparing two [Codebase]s, in which case it is called to inherit a
     * member from a super class/interface when it exists in the other [Codebase]. The resulting
     * [MemberItem] is expected to behave as if it was part of the [targetContainingClass] but is
     * otherwise identical to `this`, e.g. if [targetContainingClass] is [hidden] then so should the
     * returned [MemberItem].
     *
     * The [MemberItem.inheritedFrom] property in the returned [MemberItem] is set to
     * [containingClass] of this [MemberItem].
     *
     * @param targetContainingClass the [ClassItem] that will be used as
     *   [MemberItem.containingClass]. Note, this may be from a different [Codebase] implementation
     *   than the [MemberItem] so implementations must be careful to avoid an unconditional
     *   downcast.
     */
    fun duplicate(targetContainingClass: ClassItem): MemberItem
}
