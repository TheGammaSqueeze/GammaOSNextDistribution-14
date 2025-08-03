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

package com.android.tools.metalava

import com.android.tools.metalava.DefaultAnnotationManager.Config
import com.android.tools.metalava.model.ANDROIDX_ANNOTATION_PREFIX
import com.android.tools.metalava.model.ANDROIDX_NONNULL
import com.android.tools.metalava.model.ANDROIDX_NULLABLE
import com.android.tools.metalava.model.ANDROID_ANNOTATION_PREFIX
import com.android.tools.metalava.model.ANDROID_DEPRECATED_FOR_SDK
import com.android.tools.metalava.model.ANNOTATION_EXTERNAL
import com.android.tools.metalava.model.ANNOTATION_EXTERNAL_ONLY
import com.android.tools.metalava.model.ANNOTATION_IN_ALL_STUBS
import com.android.tools.metalava.model.ANNOTATION_IN_DOC_STUBS_AND_EXTERNAL
import com.android.tools.metalava.model.ANNOTATION_SDK_STUBS_ONLY
import com.android.tools.metalava.model.ANNOTATION_SIGNATURE_ONLY
import com.android.tools.metalava.model.ANNOTATION_STUBS_ONLY
import com.android.tools.metalava.model.AnnotationInfo
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.AnnotationRetention
import com.android.tools.metalava.model.AnnotationTarget
import com.android.tools.metalava.model.BaseAnnotationManager
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.JAVA_LANG_PREFIX
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.ModifierList
import com.android.tools.metalava.model.NO_ANNOTATION_TARGETS
import com.android.tools.metalava.model.SUPPRESS_COMPATIBILITY_ANNOTATION
import com.android.tools.metalava.model.ShowOrHide
import com.android.tools.metalava.model.Showability
import com.android.tools.metalava.model.TypedefMode
import com.android.tools.metalava.model.hasAnnotation
import com.android.tools.metalava.model.isNonNullAnnotation
import com.android.tools.metalava.model.isNullableAnnotation
import java.util.function.Predicate

/** The type of lambda that can construct a key from an [AnnotationItem] */
typealias KeyFactory = (annotationItem: AnnotationItem) -> String

class DefaultAnnotationManager(private val config: Config = Config()) : BaseAnnotationManager() {

    data class Config(
        val passThroughAnnotations: Set<String> = emptySet(),
        val allShowAnnotations: AnnotationFilter = AnnotationFilter.emptyFilter(),
        val showAnnotations: AnnotationFilter = AnnotationFilter.emptyFilter(),
        val showSingleAnnotations: AnnotationFilter = AnnotationFilter.emptyFilter(),
        val showForStubPurposesAnnotations: AnnotationFilter = AnnotationFilter.emptyFilter(),
        val hideAnnotations: AnnotationFilter = AnnotationFilter.emptyFilter(),
        val revertAnnotations: AnnotationFilter = AnnotationFilter.emptyFilter(),
        val suppressCompatibilityMetaAnnotations: Set<String> = emptySet(),
        val excludeAnnotations: Set<String> = emptySet(),
        val typedefMode: TypedefMode = TypedefMode.NONE,
        val apiPredicate: Predicate<Item> = Predicate { true },
        /**
         * Provider of a [List] of [Codebase] objects that will be used when reverting flagged APIs.
         */
        val previouslyReleasedCodebasesProvider: () -> List<Codebase> = { emptyList() },
    )

    /**
     * Map from annotation name to the [KeyFactory] to use to create a key.
     *
     * See [getKeyForAnnotationItem] to see how this is used.
     */
    private val annotationNameToKeyFactory: Map<String, KeyFactory>

    init {
        /** Use the complete source representation of the item as the key. */
        fun useSourceAsKey(annotationItem: AnnotationItem): String {
            val qualifiedName = annotationItem.qualifiedName!!
            val attributes = annotationItem.attributes
            if (attributes.isEmpty()) {
                return qualifiedName
            }
            return buildString {
                append(qualifiedName)
                append("(")
                attributes.forEachIndexed { index, attribute ->
                    if (index > 0) {
                        append(",")
                    }
                    append(attribute)
                }
                append(")")
            }
        }

        // Iterate over all the annotation names matched by all the filters currently used by
        // [LazyAnnotationInfo] and associate them with a [KeyFactory] that will use the complete
        // source representation of the annotation as the key. This is needed because filters can
        // match on attribute values as well as the name.
        val filters =
            arrayOf(
                config.allShowAnnotations,
                config.showSingleAnnotations,
                config.showForStubPurposesAnnotations,
                config.hideAnnotations,
                config.revertAnnotations,
            )
        annotationNameToKeyFactory =
            filters
                .asSequence()
                .flatMap { it.getIncludedAnnotationNames().asSequence() }
                .associate { Pair(it, ::useSourceAsKey) }
    }

    override fun getKeyForAnnotationItem(annotationItem: AnnotationItem): String {
        val qualifiedName = annotationItem.qualifiedName!!

        // Check to see if this requires a special [KeyFactory] and use it if it does.
        val keyFactory = annotationNameToKeyFactory.get(qualifiedName)
        if (keyFactory != null) {
            return keyFactory(annotationItem)
        }

        // No special key factory is needed so just use the qualified name as the key.
        return qualifiedName
    }

    override fun computeAnnotationInfo(annotationItem: AnnotationItem): AnnotationInfo {
        return LazyAnnotationInfo(config, annotationItem)
    }

    override fun normalizeInputName(qualifiedName: String?): String? {
        qualifiedName ?: return null
        if (passThroughAnnotation(qualifiedName)) {
            return qualifiedName
        }

        if (config.excludeAnnotations.contains(qualifiedName)) {
            return null
        }

        when (qualifiedName) {
            // Resource annotations
            "android.annotation.AnimRes" -> return "androidx.annotation.AnimRes"
            "android.annotation.AnimatorRes" -> return "androidx.annotation.AnimatorRes"
            "android.annotation.AnyRes" -> return "androidx.annotation.AnyRes"
            "android.annotation.ArrayRes" -> return "androidx.annotation.ArrayRes"
            "android.annotation.AttrRes" -> return "androidx.annotation.AttrRes"
            "android.annotation.BoolRes" -> return "androidx.annotation.BoolRes"
            "android.annotation.ColorRes" -> return "androidx.annotation.ColorRes"
            "android.annotation.DimenRes" -> return "androidx.annotation.DimenRes"
            "android.annotation.DrawableRes" -> return "androidx.annotation.DrawableRes"
            "android.annotation.FontRes" -> return "androidx.annotation.FontRes"
            "android.annotation.FractionRes" -> return "androidx.annotation.FractionRes"
            "android.annotation.IdRes" -> return "androidx.annotation.IdRes"
            "android.annotation.IntegerRes" -> return "androidx.annotation.IntegerRes"
            "android.annotation.InterpolatorRes" -> return "androidx.annotation.InterpolatorRes"
            "android.annotation.LayoutRes" -> return "androidx.annotation.LayoutRes"
            "android.annotation.MenuRes" -> return "androidx.annotation.MenuRes"
            "android.annotation.PluralsRes" -> return "androidx.annotation.PluralsRes"
            "android.annotation.RawRes" -> return "androidx.annotation.RawRes"
            "android.annotation.StringRes" -> return "androidx.annotation.StringRes"
            "android.annotation.StyleRes" -> return "androidx.annotation.StyleRes"
            "android.annotation.StyleableRes" -> return "androidx.annotation.StyleableRes"
            "android.annotation.TransitionRes" -> return "androidx.annotation.TransitionRes"
            "android.annotation.XmlRes" -> return "androidx.annotation.XmlRes"

            // Threading
            "android.annotation.AnyThread" -> return "androidx.annotation.AnyThread"
            "android.annotation.BinderThread" -> return "androidx.annotation.BinderThread"
            "android.annotation.MainThread" -> return "androidx.annotation.MainThread"
            "android.annotation.UiThread" -> return "androidx.annotation.UiThread"
            "android.annotation.WorkerThread" -> return "androidx.annotation.WorkerThread"

            // Colors
            "android.annotation.ColorInt" -> return "androidx.annotation.ColorInt"
            "android.annotation.ColorLong" -> return "androidx.annotation.ColorLong"
            "android.annotation.HalfFloat" -> return "androidx.annotation.HalfFloat"

            // Ranges and sizes
            "android.annotation.FloatRange" -> return "androidx.annotation.FloatRange"
            "android.annotation.IntRange" -> return "androidx.annotation.IntRange"
            "android.annotation.Size" -> return "androidx.annotation.Size"
            "android.annotation.Px" -> return "androidx.annotation.Px"
            "android.annotation.Dimension" -> return "androidx.annotation.Dimension"

            // Null
            // Preserve recently/newly nullable annotation as they need to be passed through to
            // stubs. They will be treated as nullable/non-null just as if they were mapped to
            // ANDROIDX_NULLABLE or ANDROIDX_NONNULL.
            RECENTLY_NULLABLE -> return qualifiedName
            RECENTLY_NONNULL -> return qualifiedName

            // Normalize the known nullable annotations to ANDROIDX_NULLABLE
            ANDROIDX_NULLABLE,
            ANDROID_NULLABLE,
            "libcore.util.Nullable",
            "org.jetbrains.annotations.Nullable" -> return ANDROIDX_NULLABLE

            // Normalize the known non-null annotations to ANDROIDX_NONNULL
            ANDROIDX_NONNULL,
            ANDROID_NONNULL,
            "libcore.util.NonNull",
            "org.jetbrains.annotations.NotNull" -> return ANDROIDX_NONNULL

            // Typedefs
            "android.annotation.IntDef" -> return "androidx.annotation.IntDef"
            "android.annotation.StringDef" -> return "androidx.annotation.StringDef"
            "android.annotation.LongDef" -> return "androidx.annotation.LongDef"

            // Context Types
            "android.annotation.UiContext" -> return "androidx.annotation.UiContext"
            "android.annotation.DisplayContext" -> return "androidx.annotation.DisplayContext"
            "android.annotation.NonUiContext" -> return "androidx.annotation.NonUiContext"

            // Misc
            ANDROID_DEPRECATED_FOR_SDK -> return ANDROID_DEPRECATED_FOR_SDK
            "android.annotation.CallSuper" -> return "androidx.annotation.CallSuper"
            "android.annotation.CheckResult" -> return "androidx.annotation.CheckResult"
            "android.annotation.Discouraged" -> return "androidx.annotation.Discouraged"
            "android.annotation.RequiresPermission" ->
                return "androidx.annotation.RequiresPermission"
            "android.annotation.RequiresPermission.Read" ->
                return "androidx.annotation.RequiresPermission.Read"
            "android.annotation.RequiresPermission.Write" ->
                return "androidx.annotation.RequiresPermission.Write"

            // These aren't support annotations, but could/should be:
            "android.annotation.CurrentTimeMillisLong",
            "android.annotation.DurationMillisLong",
            "android.annotation.ElapsedRealtimeLong",
            "android.annotation.UserIdInt",
            "android.annotation.BytesLong",

            // These aren't support annotations
            "android.annotation.AppIdInt",
            "android.annotation.SuppressAutoDoc",
            ANDROID_SYSTEM_API,
            ANDROID_TEST_API,
            "android.annotation.CallbackExecutor",
            "android.annotation.Condemned",
            "android.annotation.Hide",
            "android.annotation.Widget" -> return qualifiedName

            // Included for analysis, but should not be exported:
            "android.annotation.BroadcastBehavior",
            "android.annotation.SdkConstant",
            "android.annotation.RequiresFeature",
            "android.annotation.SystemService" -> return qualifiedName

            // Should not be mapped to a different package name:
            "android.annotation.TargetApi",
            "android.annotation.SuppressLint" -> return qualifiedName
            ANDROID_FLAGGED_API -> return qualifiedName

            // This implementation only annotation shouldn't be used by metalava at all.
            "dalvik.annotation.codegen.CovariantReturnType" -> return null
            else -> {
                // Some new annotations added to the platform: assume they are support
                // annotations?
                return when {
                    // Special Kotlin annotations recognized by the compiler: map to supported
                    // package name
                    qualifiedName.endsWith(".ParameterName") ||
                        qualifiedName.endsWith(".DefaultValue") ->
                        "kotlin.annotations.jvm.internal${qualifiedName.substring(qualifiedName.lastIndexOf('.'))}"

                    // Other third party nullness annotations?
                    isNullableAnnotation(qualifiedName) -> ANDROIDX_NULLABLE
                    isNonNullAnnotation(qualifiedName) -> ANDROIDX_NONNULL

                    // AndroidX annotations are all included, as is the built-in stuff like
                    // @Retention
                    qualifiedName.startsWith(ANDROIDX_ANNOTATION_PREFIX) -> return qualifiedName
                    qualifiedName.startsWith(JAVA_LANG_PREFIX) -> return qualifiedName

                    // Unknown Android platform annotations
                    qualifiedName.startsWith(ANDROID_ANNOTATION_PREFIX) -> {
                        return qualifiedName
                    }
                    else -> qualifiedName
                }
            }
        }
    }

    override fun normalizeOutputName(qualifiedName: String?, target: AnnotationTarget): String? {
        qualifiedName ?: return null
        if (passThroughAnnotation(qualifiedName)) {
            return qualifiedName
        }

        when (qualifiedName) {
            ANDROIDX_NULLABLE ->
                return if (target == AnnotationTarget.SDK_STUBS_FILE) ANDROID_NULLABLE
                else qualifiedName
            ANDROIDX_NONNULL ->
                return if (target == AnnotationTarget.SDK_STUBS_FILE) ANDROID_NONNULL
                else qualifiedName
            RECENTLY_NULLABLE ->
                return if (target == AnnotationTarget.SDK_STUBS_FILE) qualifiedName
                else ANDROIDX_NULLABLE
            RECENTLY_NONNULL ->
                return if (target == AnnotationTarget.SDK_STUBS_FILE) qualifiedName
                else ANDROIDX_NONNULL
        }

        return qualifiedName
    }

    private fun passThroughAnnotation(qualifiedName: String) =
        config.passThroughAnnotations.contains(qualifiedName) ||
            config.allShowAnnotations.matches(qualifiedName) ||
            config.hideAnnotations.matches(qualifiedName)

    private val TYPEDEF_ANNOTATION_TARGETS =
        if (
            config.typedefMode == TypedefMode.INLINE || config.typedefMode == TypedefMode.NONE
        ) // just here for compatibility purposes
         ANNOTATION_EXTERNAL
        else ANNOTATION_EXTERNAL_ONLY

    /** The applicable targets for this annotation */
    override fun computeTargets(
        annotation: AnnotationItem,
        classFinder: (String) -> ClassItem?
    ): Set<AnnotationTarget> {
        val qualifiedName = annotation.qualifiedName ?: return NO_ANNOTATION_TARGETS
        if (config.passThroughAnnotations.contains(qualifiedName)) {
            return ANNOTATION_IN_ALL_STUBS
        }
        when (qualifiedName) {
            // The typedef annotations are special: they should not be in the signature
            // files, but we want to include them in the external annotations file such that
            // tools
            // can enforce them.
            "android.annotation.IntDef",
            "androidx.annotation.IntDef",
            "android.annotation.StringDef",
            "androidx.annotation.StringDef",
            "android.annotation.LongDef",
            "androidx.annotation.LongDef" -> return TYPEDEF_ANNOTATION_TARGETS

            // Not directly API relevant
            "android.view.ViewDebug.ExportedProperty",
            "android.view.ViewDebug.CapturedViewProperty" -> return ANNOTATION_STUBS_ONLY

            // Retained in the sdk/jar stub source code so that SdkConstant files can be
            // extracted
            // from those. This is useful for modularizing the main SDK stubs without having to
            // add a separate module SDK artifact for sdk constants.
            "android.annotation.SdkConstant" -> return ANNOTATION_SDK_STUBS_ONLY
            ANDROID_FLAGGED_API ->
                // If FlaggedApi annotations are being reverted in general then do not output them
                // at all. This means that if some FlaggedApi annotations with specific flags are
                // not reverted then the annotations will not be written out to the signature files.
                // That is expected as those APIs are intended to be released and should look like
                // any other API.
                if (config.revertAnnotations.matchesAnnotationName(ANDROID_FLAGGED_API)) {
                    return NO_ANNOTATION_TARGETS
                } else {
                    return ANNOTATION_SIGNATURE_ONLY
                }

            // Skip known annotations that we (a) never want in external annotations and (b) we
            // are
            // specially overwriting anyway in the stubs (and which are (c) not API significant)
            "com.android.modules.annotation.MinSdk",
            "java.lang.annotation.Native",
            "java.lang.SuppressWarnings",
            "java.lang.Override",
            "kotlin.Suppress",
            "androidx.annotation.experimental.UseExperimental",
            "androidx.annotation.OptIn",
            "kotlin.UseExperimental",
            "kotlin.OptIn" -> return NO_ANNOTATION_TARGETS

            // These optimization-related annotations shouldn't be exported.
            "dalvik.annotation.optimization.CriticalNative",
            "dalvik.annotation.optimization.FastNative",
            "dalvik.annotation.optimization.NeverCompile",
            "dalvik.annotation.optimization.NeverInline",
            "dalvik.annotation.optimization.ReachabilitySensitive" -> return NO_ANNOTATION_TARGETS

            // TODO(aurimas): consider using annotation directly instead of modifiers
            ANDROID_DEPRECATED_FOR_SDK,
            "kotlin.Deprecated" ->
                return NO_ANNOTATION_TARGETS // tracked separately as a pseudo-modifier
            "java.lang.Deprecated", // tracked separately as a pseudo-modifier

            // Below this when-statement we perform the correct lookup: check API predicate, and
            // check
            // that retention is class or runtime, but we've hardcoded the answers here
            // for some common annotations.

            "android.widget.RemoteViews.RemoteView",
            "kotlin.annotation.Target",
            "kotlin.annotation.Retention",
            "kotlin.annotation.Repeatable",
            "kotlin.annotation.MustBeDocumented",
            "kotlin.DslMarker",
            "kotlin.PublishedApi",
            "kotlin.ExtensionFunctionType",
            "java.lang.FunctionalInterface",
            "java.lang.SafeVarargs",
            "java.lang.annotation.Documented",
            "java.lang.annotation.Inherited",
            "java.lang.annotation.Repeatable",
            "java.lang.annotation.Retention",
            "java.lang.annotation.Target" -> return ANNOTATION_IN_ALL_STUBS

            // Metalava already tracks all the methods that get generated due to these
            // annotations.
            "kotlin.jvm.JvmOverloads",
            "kotlin.jvm.JvmField",
            "kotlin.jvm.JvmStatic",
            "kotlin.jvm.JvmName" -> return NO_ANNOTATION_TARGETS
        }

        // @android.annotation.Nullable and NonNullable specially recognized annotations by the
        // Kotlin
        // compiler 1.3 and above: they always go in the stubs.
        if (
            qualifiedName == ANDROID_NULLABLE ||
                qualifiedName == ANDROID_NONNULL ||
                qualifiedName == ANDROIDX_NULLABLE ||
                qualifiedName == ANDROIDX_NONNULL
        ) {
            return ANNOTATION_IN_ALL_STUBS
        }

        if (qualifiedName.startsWith("android.annotation.")) {
            // internal annotations not mapped to androidx: things like @SystemApi. Skip from
            // stubs, external annotations, signature files, etc.
            return NO_ANNOTATION_TARGETS
        }

        // @RecentlyNullable and @RecentlyNonNull are specially recognized annotations by the
        // Kotlin
        // compiler: they always go in the stubs.
        if (qualifiedName == RECENTLY_NULLABLE || qualifiedName == RECENTLY_NONNULL) {
            return ANNOTATION_IN_ALL_STUBS
        }

        // Determine the retention of the annotation: source retention annotations go
        // in the external annotations file, class and runtime annotations go in
        // the stubs files (except for the androidx annotations which are not included
        // in the SDK and therefore cannot be referenced from it due to apt's unfortunate
        // habit of loading all annotation classes it encounters.)

        if (qualifiedName.startsWith("androidx.annotation.")) {
            if (qualifiedName == ANDROIDX_NULLABLE || qualifiedName == ANDROIDX_NONNULL) {
                // Right now, nullness annotations (other than @RecentlyNullable and
                // @RecentlyNonNull)
                // have to go in external annotations since they aren't in the class path for
                // annotation processors. However, we do want them showing up in the
                // documentation using
                // their real annotation names.
                return ANNOTATION_IN_DOC_STUBS_AND_EXTERNAL
            }

            return ANNOTATION_EXTERNAL
        }

        // See if the annotation is pointing to an annotation class that is part of the API; if
        // not, skip it.
        val cls = classFinder(qualifiedName) ?: return NO_ANNOTATION_TARGETS
        if (!config.apiPredicate.test(cls)) {
            if (config.typedefMode != TypedefMode.NONE) {
                if (cls.modifiers.hasAnnotation(AnnotationItem::isTypeDefAnnotation)) {
                    return ANNOTATION_SIGNATURE_ONLY
                }
            }

            return NO_ANNOTATION_TARGETS
        }

        if (cls.isAnnotationType()) {
            val retention = cls.getRetention()
            if (
                retention == AnnotationRetention.RUNTIME ||
                    retention == AnnotationRetention.CLASS ||
                    retention == AnnotationRetention.BINARY
            ) {
                return ANNOTATION_IN_ALL_STUBS
            }
        }

        return ANNOTATION_EXTERNAL
    }

    override fun isShowAnnotationName(annotationName: String): Boolean =
        config.allShowAnnotations.matchesAnnotationName(annotationName)

    override fun hasAnyStubPurposesAnnotations(): Boolean {
        return config.showForStubPurposesAnnotations.isNotEmpty()
    }

    override fun hasHideAnnotations(modifiers: ModifierList): Boolean {
        if (config.hideAnnotations.isEmpty()) {
            return false
        }
        return modifiers.hasAnnotation(AnnotationItem::isHideAnnotation)
    }

    override fun hasSuppressCompatibilityMetaAnnotations(modifiers: ModifierList): Boolean {
        if (config.suppressCompatibilityMetaAnnotations.isEmpty()) {
            return false
        }
        return modifiers.hasAnnotation(AnnotationItem::isSuppressCompatibilityAnnotation)
    }

    override fun getShowabilityForItem(item: Item): Showability {
        // Iterates over the annotations on the item and computes the showability for the item by
        // combining the showability of each annotation. The basic rules are:
        // * `show=true` beats `show=false`
        // * `recurse=true` beats `recurse=false`
        // * `forStubsOnly=false` beats `forStubsOnly=true`

        // The resulting showability of the item.
        var itemShowability = Showability.NO_EFFECT

        for (annotation in item.modifiers.annotations()) {
            val showability = annotation.showability
            if (showability == Showability.NO_EFFECT) {
                // NO_EFFECT has no effect on the result so just ignore it.
                continue
            }
            itemShowability = itemShowability.combineWith(showability)
        }

        if (item is MethodItem) {
            // If any of a method's super methods are part of a unstable API that needs to be
            // reverted then treat the method as if it is too.
            val revertUnstableApi =
                item.superMethods().any { methodItem -> methodItem.showability.revertUnstableApi() }
            if (revertUnstableApi) {
                itemShowability =
                    itemShowability.combineWith(LazyAnnotationInfo.REVERT_UNSTABLE_API)
            }
        }

        val containingClass = item.containingClass()
        if (containingClass != null) {
            if (containingClass.showability.revertUnstableApi()) {
                itemShowability =
                    itemShowability.combineWith(LazyAnnotationInfo.REVERT_UNSTABLE_API)
            }
        }

        // If the item is to be reverted then find the [Item] to which it will be reverted, if any,
        // and incorporate that into the [Showability].
        if (itemShowability == LazyAnnotationInfo.REVERT_UNSTABLE_API) {
            val revertItem = findRevertItem(item)

            // If the [revertItem] cannot be found then there is no need to modify the item
            // showability as it is already in the correct state.
            if (revertItem != null) {
                // Update the item showability to revert to the [revertItem]. This intentionally
                // does not modify it to use `SHOW` or `HIDE` but keeps it using
                // `REVERT_UNSTABLE_API` so that it can be propagated down onto overriding methods
                // and nested members if applicable.
                itemShowability =
                    itemShowability.copy(
                        // When reverting to a previous item the item must be in the current API
                        // surface not one that it extends. If the API surface is a complete API
                        // (like public) then it does not extend another surface. If the API surface
                        // is a partial API then so was the previously released API and so the
                        // reverted item must be part of this surface. In either case it must not
                        // be treated as part of an extended API so `forStubsOnly` is set to no
                        // effect.
                        forStubsOnly = ShowOrHide.NO_EFFECT,
                        // Incorporate the item to be reverted into the [Showability].
                        revertItem = revertItem,
                    )
            }
        }

        return itemShowability
    }

    /**
     * Find the item to which [item] will be reverted.
     *
     * Searches first the previously released API (if present) and then the previously released
     * removed API (if present).
     */
    private fun findRevertItem(item: Item): Item? {
        for (oldCodebase in config.previouslyReleasedCodebasesProvider()) {
            item.findCorrespondingItemIn(oldCodebase)?.let {
                return it
            }
        }

        return null
    }

    override val typedefMode: TypedefMode = config.typedefMode
}

/**
 * Extension of [AnnotationInfo] that supports initializing properties based on the
 * [DefaultAnnotationManager.Config].
 *
 * The properties are initialized lazily to avoid doing more work than necessary.
 */
private class LazyAnnotationInfo(
    private val config: Config,
    private val annotationItem: AnnotationItem,
) : AnnotationInfo(annotationItem.qualifiedName!!) {

    /** Compute lazily to avoid doing any more work than strictly necessary. */
    override val showability: Showability by
        lazy(LazyThreadSafetyMode.NONE) {
            // The showAnnotations filter includes all the annotation patterns that are matched by
            // the first two filters plus 0 or more additional patterns. Excluding the patterns that
            // are purposely duplicated in showAnnotations the filters should not overlap, i.e. an
            // AnnotationItem should not be matched by multiple filters. However, the filters could
            // use the same annotation class (with different attributes). e.g. showAnnotations could
            // match `@SystemApi(client=MODULE_LIBRARIES)` and showForStubPurposesAnnotations could
            // match `@SystemApi(client=PRIVILEGED_APPS)`.
            //
            // Compare from most likely to match to least likely to match.
            when {
                config.showAnnotations.matches(annotationItem) -> SHOW
                config.showForStubPurposesAnnotations.matches(annotationItem) -> SHOW_FOR_STUBS
                config.showSingleAnnotations.matches(annotationItem) -> SHOW_SINGLE
                config.hideAnnotations.matches(annotationItem) -> HIDE
                config.revertAnnotations.matches(annotationItem) -> REVERT_UNSTABLE_API
                else -> Showability.NO_EFFECT
            }
        }

    companion object {
        /**
         * The annotation will cause the annotated item (and any enclosed items unless overridden by
         * a closer annotation) to be shown.
         */
        val SHOW =
            Showability(
                show = ShowOrHide.SHOW,
                recursive = ShowOrHide.SHOW,
                forStubsOnly = ShowOrHide.NO_EFFECT,
            )

        /**
         * The annotation will cause the annotated item (and any enclosed items unless overridden by
         * a closer annotation) to be shown in the stubs only.
         */
        val SHOW_FOR_STUBS =
            Showability(
                show = ShowOrHide.NO_EFFECT,
                recursive = ShowOrHide.NO_EFFECT,
                forStubsOnly = ShowOrHide.SHOW,
            )

        /** The annotation will cause the annotated item (but not enclosed items) to be shown. */
        val SHOW_SINGLE =
            Showability(
                show = ShowOrHide.SHOW,
                recursive = ShowOrHide.NO_EFFECT,
                forStubsOnly = ShowOrHide.NO_EFFECT,
            )

        /**
         * The annotation will cause the annotated item (and any enclosed items unless overridden by
         * a closer annotation) to not be shown.
         */
        val HIDE =
            Showability(
                show = ShowOrHide.HIDE,
                recursive = ShowOrHide.HIDE,
                forStubsOnly = ShowOrHide.NO_EFFECT,
            )

        /**
         * The annotation will cause the annotated item (and any enclosed items unless overridden by
         * a closer annotation) to not be shown.
         */
        val REVERT_UNSTABLE_API =
            Showability(
                show = ShowOrHide.REVERT_UNSTABLE_API,
                recursive = ShowOrHide.REVERT_UNSTABLE_API,
                forStubsOnly = ShowOrHide.REVERT_UNSTABLE_API,
            )

        /**
         * Fully-qualified version of [SUPPRESS_COMPATIBILITY_ANNOTATION].
         *
         * This is only used at run-time for matching against [AnnotationItem.qualifiedName], so it
         * doesn't need to maintain compatibility.
         */
        private val SUPPRESS_COMPATIBILITY_ANNOTATION_QUALIFIED =
            AnnotationItem.unshortenAnnotation("@$SUPPRESS_COMPATIBILITY_ANNOTATION").substring(1)
    }

    /** Resolve the [AnnotationItem] to a [ClassItem] lazily. */
    private val annotationClass: ClassItem? by
        lazy(LazyThreadSafetyMode.NONE, annotationItem::resolve)

    /** Flag to detect whether the [checkResolvedAnnotationClass] is in a cycle. */
    private var isCheckingResolvedAnnotationClass: Boolean = false

    /**
     * Check to see whether the resolved annotation class matches the supplied predicate.
     *
     * If the annotation class could not be resolved or the annotation is part of a cycle, e.g.
     * `java.lang.annotation.Retention` is annotated with itself, then returns false, otherwise it
     * returns the result of applying the supplied predicate to the resolved class.
     */
    private fun checkResolvedAnnotationClass(test: (ClassItem) -> Boolean): Boolean {
        if (isCheckingResolvedAnnotationClass) {
            return false
        }

        try {
            isCheckingResolvedAnnotationClass = true

            // Try and resolve this to the class to see if it has been annotated with hide meta
            // annotations. If it could not be resolved then assume it has not been annotated.
            val resolved = annotationClass ?: return false

            // Return the result of applying the test to the resolved class.
            return test(resolved)
        } finally {
            isCheckingResolvedAnnotationClass = false
        }
    }

    /**
     * If true then this annotation will suppress compatibility checking on annotated items.
     *
     * This is true if this annotation is
     */
    override val suppressCompatibility: Boolean by
        lazy(LazyThreadSafetyMode.NONE) {
            qualifiedName == SUPPRESS_COMPATIBILITY_ANNOTATION_QUALIFIED ||
                config.suppressCompatibilityMetaAnnotations.contains(qualifiedName) ||
                checkResolvedAnnotationClass { it.hasSuppressCompatibilityMetaAnnotation() }
        }
}
