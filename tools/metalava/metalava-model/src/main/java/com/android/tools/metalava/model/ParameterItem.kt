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

@MetalavaApi
interface ParameterItem : Item {
    /** The name of this field */
    fun name(): String

    /** The type of this field */
    @MetalavaApi override fun type(): TypeItem

    override fun findCorrespondingItemIn(codebase: Codebase) =
        containingMethod()
            .findCorrespondingItemIn(codebase)
            ?.parameters()
            ?.getOrNull(parameterIndex)

    /** The containing method */
    fun containingMethod(): MethodItem

    /** Index of this parameter in the parameter list (0-based) */
    val parameterIndex: Int

    /**
     * The public name of this parameter. In Kotlin, names are part of the public API; in Java they
     * are not. In Java, you can annotate a parameter with {@literal @ParameterName("foo")} to name
     * the parameter something (potentially different from the actual code parameter name).
     */
    fun publicName(): String?

    /**
     * Returns whether this parameter has a default value. In Kotlin, this is supported directly; in
     * Java, it's supported via a special annotation, {@literal @DefaultValue("source"). This does
     * not necessarily imply that the default value is accessible, and we know the body of the
     * default value.
     *
     * @see isDefaultValueKnown
     */
    fun hasDefaultValue(): Boolean

    /**
     * Returns whether this parameter has an accessible default value that we plan to keep. This is
     * a superset of [hasDefaultValue] - if we are not writing the default values to the signature
     * file, then the default value might not be available, even though the parameter does have a
     * default.
     *
     * @see hasDefaultValue
     */
    fun isDefaultValueKnown(): Boolean

    /**
     * Returns the default value.
     *
     * **This method should only be called if [isDefaultValueKnown] returned true!** (This is
     * necessary since the null return value is a valid default value separate from no default value
     * specified.)
     *
     * The default value is the source string literal representation of the value, e.g. strings
     * would be surrounded by quotes, Booleans are the strings "true" or "false", and so on.
     */
    fun defaultValue(): String?

    /** Whether this is a varargs parameter */
    fun isVarArgs(): Boolean

    /** The property declared by this parameter; inverse of [PropertyItem.constructorParameter] */
    val property: PropertyItem?
        get() = null

    override fun parent(): MethodItem? = containingMethod()

    override fun baselineElementId() =
        containingMethod().baselineElementId() + " parameter #" + parameterIndex

    override fun accept(visitor: ItemVisitor) {
        visitor.visit(this)
    }

    override fun toStringForItem() = "parameter ${name()}"

    override fun requiresNullnessInfo(): Boolean {
        return type() !is PrimitiveTypeItem
    }

    override fun hasNullnessInfo(): Boolean {
        if (!requiresNullnessInfo()) {
            return true
        }

        return modifiers.hasNullnessInfo()
    }

    override fun implicitNullness(): TypeNullability? {
        // Delegate to the super class, only dropping through if it did not determine an implicit
        // nullness.
        super.implicitNullness()?.let { nullable ->
            return nullable
        }

        // Equals has known nullness
        val method = containingMethod()
        if (method.name() == "equals" && method.parameters().size == 1) {
            return TypeNullability.NULLABLE
        }

        return null
    }

    override fun containingClass(): ClassItem? = containingMethod().containingClass()

    override fun containingPackage(): PackageItem? = containingMethod().containingPackage()

    // TODO: modifier list
}
