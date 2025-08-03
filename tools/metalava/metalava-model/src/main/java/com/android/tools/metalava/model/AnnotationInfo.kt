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

package com.android.tools.metalava.model

/**
 * Encapsulates information that metalava needs to know about a specific annotation type.
 *
 * Instances of [AnnotationInfo] will be shared across [AnnotationItem]s that have the same
 * qualified name and (where applicable) the same attributes. That will allow the information in
 * [AnnotationInfo] to be computed once and then reused whenever needed.
 *
 * This class just sets the properties that can be determined simply by looking at the
 * [qualifiedName]. Any other properties are set to the default, usually `false`. Subclasses can
 * change that behavior.
 */
open class AnnotationInfo(
    /** The fully qualified and normalized name of the annotation class. */
    val qualifiedName: String,
) {

    /**
     * Determines whether the annotation is nullability related.
     *
     * If this is null then the annotation is not a nullability annotation, otherwise this
     * determines whether it is nullable or non-null.
     */
    internal val typeNullability: TypeNullability? =
        when {
            isNullableAnnotation(qualifiedName) -> TypeNullability.NULLABLE
            isNonNullAnnotation(qualifiedName) -> TypeNullability.NONNULL
            else -> null
        }

    /**
     * Determines whether this annotation affects whether the annotated item is shown or hidden and
     * if so how.
     */
    open val showability: Showability
        get() = Showability.NO_EFFECT

    open val suppressCompatibility: Boolean
        get() = false
}

/**
 * The set of possible effects on whether an `Item` is part of an API.
 *
 * They are in order from the lowest priority to the highest priority, see [highestPriority].
 */
enum class ShowOrHide(private val show: Boolean?) {
    /** No effect either way. */
    NO_EFFECT(show = null),

    /** Hide an item from the API. */
    HIDE(show = false),

    /** Show an item as part of the API. */
    SHOW(show = true),

    /**
     * Revert an unstable API.
     *
     * The effect of reverting an unstable API depends on what the previously released API contains
     * but in the case when the item is new and does not exist in the previously released API
     * reverting requires hiding the API. As the items being hidden could have show annotations
     * (which override hide annotations) then in order for the item to be hidden then this needs to
     * come after [SHOW].
     */
    REVERT_UNSTABLE_API(show = null) {
        /** If the [revertItem] is not null then reverting will still show this item. */
        override fun show(revertItem: Item?): Boolean {
            return revertItem != null
        }

        /** If the [revertItem] is null then reverting will hide this item. */
        override fun hide(revertItem: Item?): Boolean {
            return revertItem == null
        }
    },
    ;

    /**
     * Return true if this shows an `Item` as part of the API.
     *
     * @param revertItem the optional [Item] in the previously released API to which this will be
     *   reverted. This is only set for, and only has an effect on, [REVERT_UNSTABLE_API], see
     *   [REVERT_UNSTABLE_API.show] for details.
     */
    open fun show(revertItem: Item?): Boolean = show == true

    /**
     * Return true if this hides an `Item` from the API.
     *
     * @param revertItem the optional [Item] in the previously released API to which this will be
     *   reverted. This is only set for, and only has an effect on, [REVERT_UNSTABLE_API], see
     *   [REVERT_UNSTABLE_API.show] for details.
     */
    open fun hide(revertItem: Item?): Boolean = show == false

    /** Return the highest priority between this and another [ShowOrHide]. */
    fun highestPriority(other: ShowOrHide): ShowOrHide = maxOf(this, other)
}

/**
 * Determines how an annotation will affect whether [Item]s annotated with it are part of the API or
 * not and also determines whether an [Item] is part of the API or not.
 */
data class Showability(
    /**
     * Determines whether an API [Item] is shown as part of the API or hidden from the API.
     *
     * If [ShowOrHide.show] is `true` then the annotated [Item] will be shown as part of the API.
     * That is the case for annotations that match `--show-annotation`, or
     * `--show-single-annotation`, but not `--show-for-stub-purposes-annotation`.
     *
     * If [ShowOrHide.hide] is `true` then the annotated [Item] will NOT be shown as part of the
     * API. That is the case for annotations that match `--hide-annotation`.
     *
     * If neither of the above is then this has no effect on whether an annotated [Item] will be
     * shown or not, that decision will be determined by its container's [Showability.recursive]
     * setting.
     */
    private val show: ShowOrHide,

    /**
     * Determines whether the contents of an API [Item] is shown as part of the API or hidden from
     * the API.
     *
     * If [ShowOrHide.show] is `true` then the contents of the annotated [Item] will be included in
     * the API unless overridden by a closer annotation. That is the case for annotations that match
     * `--show-annotation`, but not `--show-single-annotation`, or
     * `--show-for-stub-purposes-annotation`.
     *
     * If [ShowOrHide.hide] is `true` then the contents of the annotated [Item] will be included in
     * the API unless overridden by a closer annotation. That is the case for annotations that match
     * `--hide-annotation`.
     */
    private val recursive: ShowOrHide,

    /**
     * Determines whether an API [Item] ands its contents is considered to be part of the base API
     * and so must be included in the stubs but not the signature files.
     *
     * If [ShowOrHide.show] is `true` then the API [Item] ands its contents are considered to be
     * part of the base API. That is the case for annotations that match
     * `--show-for-stub-purposes-annotation` but not `--show-annotation`, or
     * `--show-single-annotation`.
     */
    private val forStubsOnly: ShowOrHide,

    /** The item to which this item should be reverted. Null if no such item exists. */
    val revertItem: Item? = null,
) {
    /**
     * Check whether the annotated item should be considered part of the API or not.
     *
     * Returns `true` if the item is annotated with a `--show-annotation`,
     * `--show-single-annotation`, or `--show-for-stub-purposes-annotation`.
     */
    fun show() = show.show(revertItem) || forStubsOnly.show(revertItem)

    /**
     * Check whether the annotated item should only be considered part of the API when generating
     * stubs.
     *
     * Returns `true` if the item is annotated with a `--show-for-stub-purposes-annotation`. Such
     * items will be part of an API surface that the API being generated extends.
     */
    fun showForStubsOnly() = forStubsOnly.show(revertItem)

    /**
     * Check whether the annotations on this item only affect the current `Item`.
     *
     * Returns `true` if they do, `false` if they can also affect nested `Item`s.
     */
    fun showNonRecursive() =
        show.show(revertItem) && !recursive.show(revertItem) && !forStubsOnly.show(revertItem)

    /**
     * Check whether the annotated item should be hidden from the API.
     *
     * Returns `true` if the annotation matches an `--hide-annotation`.
     */
    fun hide() = show.hide(revertItem)

    /**
     * Check whether the annotated item is part of an unstable API that needs to be reverted.
     *
     * Returns `true` if the annotation matches `--hide-annotation android.annotation.FlaggedApi` or
     * if this is on an item then when the item is annotated with such an annotation or is a method
     * that overrides such an item or is contained within a class that is annotated with such an
     * annotation.
     */
    fun revertUnstableApi() = show == ShowOrHide.REVERT_UNSTABLE_API

    /** Combine this with [other] to produce a combination [Showability]. */
    fun combineWith(other: Showability): Showability {
        // Show wins over not showing.
        val newShow = show.highestPriority(other.show)

        // Recursive wins over not recursive.
        val newRecursive = recursive.highestPriority(other.recursive)

        // For everything wins over only for stubs.
        val forStubsOnly =
            if (newShow.show(revertItem)) {
                ShowOrHide.NO_EFFECT
            } else {
                forStubsOnly.highestPriority(other.forStubsOnly)
            }

        return Showability(newShow, newRecursive, forStubsOnly)
    }

    companion object {
        /** The annotation does not affect whether an annotated item is shown. */
        val NO_EFFECT =
            Showability(
                show = ShowOrHide.NO_EFFECT,
                recursive = ShowOrHide.NO_EFFECT,
                forStubsOnly = ShowOrHide.NO_EFFECT
            )
    }
}
