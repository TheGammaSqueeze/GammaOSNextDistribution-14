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

import java.util.function.Predicate

@MetalavaApi
interface MethodItem : MemberItem, TypeParameterListOwner {
    /**
     * The property this method is an accessor for; inverse of [PropertyItem.getter] and
     * [PropertyItem.setter]
     */
    val property: PropertyItem?
        get() = null

    /** Whether this method is a constructor */
    @MetalavaApi fun isConstructor(): Boolean

    /** The type of this field. Returns the containing class for constructors */
    @MetalavaApi fun returnType(): TypeItem

    /** The list of parameters */
    @MetalavaApi fun parameters(): List<ParameterItem>

    /** Returns true if this method is a Kotlin extension method */
    fun isExtensionMethod(): Boolean

    /** Returns the super methods that this method is overriding */
    fun superMethods(): List<MethodItem>

    override fun type() = returnType()

    override fun findCorrespondingItemIn(codebase: Codebase) =
        containingClass().findCorrespondingItemIn(codebase)?.findMethod(this)

    /** Returns the main documentation for the method (the documentation before any tags). */
    fun findMainDocumentation(): String

    fun allSuperMethods(): Sequence<MethodItem> {
        val original = superMethods().firstOrNull() ?: return emptySequence()
        return generateSequence(original) { item ->
            val superMethods = item.superMethods()
            superMethods.firstOrNull()
        }
    }

    /** Types of exceptions that this method can throw */
    fun throwsTypes(): List<ExceptionTypeItem>

    /** Returns true if this method throws the given exception */
    fun throws(qualifiedName: String): Boolean {
        for (type in throwsTypes()) {
            val throwableClass = type.erasedClass ?: continue
            if (throwableClass.extends(qualifiedName)) {
                return true
            }
        }

        return false
    }

    fun filteredThrowsTypes(predicate: Predicate<Item>): Collection<ExceptionTypeItem> {
        if (throwsTypes().isEmpty()) {
            return emptyList()
        }
        return filteredThrowsTypes(predicate, LinkedHashSet())
    }

    private fun filteredThrowsTypes(
        predicate: Predicate<Item>,
        throwsTypes: LinkedHashSet<ExceptionTypeItem>
    ): LinkedHashSet<ExceptionTypeItem> {
        for (exceptionType in throwsTypes()) {
            if (exceptionType is VariableTypeItem) {
                throwsTypes.add(exceptionType)
            } else {
                val classItem = exceptionType.erasedClass ?: continue
                if (predicate.test(classItem)) {
                    throwsTypes.add(exceptionType)
                } else {
                    // Excluded, but it may have super class throwables that are included; if so,
                    // include those.
                    classItem
                        .allSuperClasses()
                        .firstOrNull { superClass -> predicate.test(superClass) }
                        ?.let { superClass -> throwsTypes.add(superClass.type()) }
                }
            }
        }
        return throwsTypes
    }

    /**
     * If this method requires override in the child class to prevent error when compiling the stubs
     */
    @Deprecated("This property should not be accessed directly.") var _requiresOverride: Boolean?

    /**
     * Duplicates this method item.
     *
     * Override to specialize the return type.
     */
    override fun duplicate(targetContainingClass: ClassItem): MethodItem

    fun findPredicateSuperMethod(predicate: Predicate<Item>): MethodItem? {
        if (isConstructor()) {
            return null
        }

        val superMethods = superMethods()
        for (method in superMethods) {
            if (predicate.test(method)) {
                return method
            }
        }

        for (method in superMethods) {
            val found = method.findPredicateSuperMethod(predicate)
            if (found != null) {
                return found
            }
        }

        return null
    }

    override fun baselineElementId() = buildString {
        append(containingClass().qualifiedName())
        append("#")
        append(name())
        append("(")
        parameters().joinTo(this) { it.type().toSimpleType() }
        append(")")
    }

    override fun accept(visitor: ItemVisitor) {
        visitor.visit(this)
    }

    override fun toStringForItem(): String {
        return "${if (isConstructor()) "constructor" else "method"} ${
            containingClass().qualifiedName()}.${name()}(${parameters().joinToString { it.type().toSimpleType() }})"
    }

    companion object {
        private fun compareMethods(
            o1: MethodItem,
            o2: MethodItem,
            overloadsInSourceOrder: Boolean
        ): Int {
            val name1 = o1.name()
            val name2 = o2.name()
            if (name1 == name2) {
                if (overloadsInSourceOrder) {
                    val rankDelta = o1.sortingRank - o2.sortingRank
                    if (rankDelta != 0) {
                        return rankDelta
                    }
                }

                // Compare by the rest of the signature to ensure stable output (we don't need to
                // sort
                // by return value or modifiers or modifiers or throws-lists since methods can't be
                // overloaded
                // by just those attributes
                val p1 = o1.parameters()
                val p2 = o2.parameters()
                val p1n = p1.size
                val p2n = p2.size
                for (i in 0 until minOf(p1n, p2n)) {
                    val compareTypes =
                        p1[i]
                            .type()
                            .toTypeString()
                            .compareTo(p2[i].type().toTypeString(), ignoreCase = true)
                    if (compareTypes != 0) {
                        return compareTypes
                    }
                    // (Don't compare names; they're not part of the signatures)
                }
                return p1n.compareTo(p2n)
            }

            return name1.compareTo(name2)
        }

        val comparator: Comparator<MethodItem> = Comparator { o1, o2 ->
            compareMethods(o1, o2, false)
        }
        val sourceOrderComparator: Comparator<MethodItem> = Comparator { o1, o2 ->
            val delta = o1.sortingRank - o2.sortingRank
            if (delta == 0) {
                // Within a source file all the items will have unique sorting ranks, but since
                // we copy methods in from hidden super classes it's possible for ranks to clash,
                // and in that case we'll revert to a signature based comparison
                comparator.compare(o1, o2)
            } else {
                delta
            }
        }
        val sourceOrderForOverloadedMethodsComparator: Comparator<MethodItem> =
            Comparator { o1, o2 ->
                compareMethods(o1, o2, true)
            }

        /**
         * Compare two types to see if they are considered the same.
         *
         * Same means, functionally equivalent at both compile time and runtime.
         *
         * TODO: Compare annotations to see for example whether you've refined the nullness policy;
         *   if so, that should be included
         */
        private fun sameType(
            t1: TypeItem,
            t2: TypeItem,
            addAdditionalOverrides: Boolean,
        ): Boolean {
            // Compare the types in two ways.
            // 1. Using `TypeItem.equals(TypeItem)` which is basically a textual comparison that
            //    ignores type parameter bounds but includes everuthing else that is present in the
            //    string representation of the type apart from white space differences. This is
            //    needed to preserve methods that change annotations, e.g. adding `@NonNull`, which
            //    are significant to the API, and also to preserver legacy behavior to reduce churn
            //    in API signature files.
            // 2. Comparing their erased types which takes into account type parameter bounds but
            //    ignores annotations and generic types. Comparing erased types will retain more
            //    methods overrides in the signature file so only do it when adding additional
            //    overrides.
            return t1 == t2 &&
                (!addAdditionalOverrides || t1.toErasedTypeString() == t2.toErasedTypeString())
        }

        fun sameSignature(
            method: MethodItem,
            superMethod: MethodItem,
            addAdditionalOverrides: Boolean,
        ): Boolean {
            // If the return types differ, override it (e.g. parent implements clone(),
            // subclass overrides with more specific return type)
            if (
                !sameType(
                    method.returnType(),
                    superMethod.returnType(),
                    addAdditionalOverrides = addAdditionalOverrides
                )
            ) {
                return false
            }

            if (method.deprecated != superMethod.deprecated && !method.deprecated) {
                return false
            }

            // Compare modifier lists; note that here we need to
            // skip modifiers that don't apply in compat mode if set
            if (!method.modifiers.equivalentTo(superMethod.modifiers)) {
                return false
            }

            val parameterList1 = method.parameters()
            val parameterList2 = superMethod.parameters()

            if (parameterList1.size != parameterList2.size) {
                return false
            }

            assert(parameterList1.size == parameterList2.size)
            for (i in parameterList1.indices) {
                val p1 = parameterList1[i]
                val p2 = parameterList2[i]
                val pt1 = p1.type()
                val pt2 = p2.type()

                if (!sameType(pt1, pt2, addAdditionalOverrides)) {
                    return false
                }
            }

            // Also compare throws lists
            val throwsList12 = method.throwsTypes()
            val throwsList2 = superMethod.throwsTypes()

            if (throwsList12.size != throwsList2.size) {
                return false
            }

            assert(throwsList12.size == throwsList2.size)
            for (i in throwsList12.indices) {
                val p1 = throwsList12[i]
                val p2 = throwsList2[i]
                val pt1 = p1.toTypeString()
                val pt2 = p2.toTypeString()
                if (pt1 != pt2) { // assumes throws lists are sorted!
                    return false
                }
            }

            return true
        }
    }

    fun formatParameters(): String? {
        // TODO: Generalize, allow callers to control whether to include annotations, whether to
        // erase types,
        // whether to include names, etc
        if (parameters().isEmpty()) {
            return ""
        }
        val sb = StringBuilder()
        for (parameter in parameters()) {
            if (sb.isNotEmpty()) {
                sb.append(", ")
            }
            sb.append(parameter.type().toTypeString())
        }

        return sb.toString()
    }

    override fun requiresNullnessInfo(): Boolean {
        return when {
            modifiers.hasJvmSyntheticAnnotation() -> false
            isConstructor() -> false
            (returnType() !is PrimitiveTypeItem) -> true
            parameters().any { it.type() !is PrimitiveTypeItem } -> true
            else -> false
        }
    }

    override fun hasNullnessInfo(): Boolean {
        if (!requiresNullnessInfo()) {
            return true
        }

        if (!isConstructor() && returnType() !is PrimitiveTypeItem) {
            if (!modifiers.hasNullnessInfo()) {
                return false
            }
        }

        @Suppress("LoopToCallChain") // The quickfix is wrong! (covered by AnnotationStatisticsTest)
        for (parameter in parameters()) {
            if (!parameter.hasNullnessInfo()) {
                return false
            }
        }

        return true
    }

    override fun implicitNullness(): TypeNullability? {
        // Delegate to the super class, only dropping through if it did not determine an implicit
        // nullness.
        super.implicitNullness()?.let { nullable ->
            return nullable
        }

        // toString has known nullness
        if (name() == "toString" && parameters().isEmpty()) {
            return TypeNullability.NONNULL
        }

        return null
    }

    fun isImplicitConstructor(): Boolean {
        return isConstructor() && modifiers.isPublic() && parameters().isEmpty()
    }

    /**
     * Finds uncaught exceptions actually thrown inside this method (as opposed to ones declared in
     * the signature)
     */
    fun findThrownExceptions(): Set<ClassItem> = codebase.unsupported()

    /** If annotation method, returns the default value as a source expression */
    fun defaultValue(): String = ""

    fun hasDefaultValue(): Boolean {
        return defaultValue() != ""
    }

    /**
     * Returns true if overloads of the method should be checked separately when checking signature
     * of the method.
     *
     * This works around the issue of actual method not generating overloads for @JvmOverloads
     * annotation when the default is specified on expect side
     * (https://youtrack.jetbrains.com/issue/KT-57537).
     */
    fun shouldExpandOverloads(): Boolean = false

    /**
     * Returns true if this method is a signature match for the given method (e.g. can be
     * overriding). This checks that the name and parameter lists match, but ignores differences in
     * parameter names, return value types and throws list types.
     */
    fun matches(other: MethodItem): Boolean {
        if (this === other) return true

        if (name() != other.name()) {
            return false
        }

        val parameters1 = parameters()
        val parameters2 = other.parameters()

        if (parameters1.size != parameters2.size) {
            return false
        }

        for (i in parameters1.indices) {
            val parameter1Type = parameters1[i].type()
            val parameter2Type = parameters2[i].type()
            if (parameter1Type == parameter2Type) continue
            if (parameter1Type.toErasedTypeString() == parameter2Type.toErasedTypeString()) continue

            val convertedType =
                parameter1Type.convertType(other.containingClass(), containingClass())
            if (convertedType != parameter2Type) return false
        }
        return true
    }

    /**
     * Returns whether this method has any types in its signature that does not match the given
     * filter
     */
    fun hasHiddenType(filterReference: Predicate<Item>): Boolean {
        for (parameter in parameters()) {
            if (parameter.type().hasHiddenType(filterReference)) return true
        }

        if (returnType().hasHiddenType(filterReference)) return true

        for (typeParameter in typeParameterList) {
            if (typeParameter.typeBounds().any { it.hasHiddenType(filterReference) }) return true
        }

        return false
    }

    /** Checks if there is a reference to a hidden class anywhere in the type. */
    private fun TypeItem.hasHiddenType(filterReference: Predicate<Item>): Boolean {
        return when (this) {
            is PrimitiveTypeItem -> false
            is ArrayTypeItem -> componentType.hasHiddenType(filterReference)
            is ClassTypeItem ->
                asClass()?.let { !filterReference.test(it) } == true ||
                    outerClassType?.hasHiddenType(filterReference) == true ||
                    arguments.any { it.hasHiddenType(filterReference) }
            is VariableTypeItem -> !filterReference.test(asTypeParameter)
            is WildcardTypeItem ->
                extendsBound?.hasHiddenType(filterReference) == true ||
                    superBound?.hasHiddenType(filterReference) == true
            else -> throw IllegalStateException("Unrecognized type: $this")
        }
    }

    /** Whether this method is a getter/setter for an underlying Kotlin property (val/var) */
    fun isKotlinProperty(): Boolean = false

    /**
     * Determines if the method is a method that needs to be overridden in any child classes that
     * extend this [MethodItem] in order to prevent errors when compiling the stubs or the reverse
     * dependencies of stubs.
     *
     * @return Boolean value indicating whether the method needs to be overridden in the child
     *   classes
     */
    @Suppress("DEPRECATION")
    private fun requiresOverride(): Boolean {
        _requiresOverride?.let {
            return _requiresOverride as Boolean
        }

        _requiresOverride = computeRequiresOverride()

        return _requiresOverride as Boolean
    }

    private fun computeRequiresOverride(): Boolean {
        val isVisible = !hidden || hasShowAnnotation()

        // When the method is a concrete, non-default method, its overriding method is not required
        // to be shown in the signature file.
        return if (!modifiers.isAbstract() && !modifiers.isDefault()) {
            false
        } else if (superMethods().isEmpty()) {
            // If the method is abstract and is not overriding any parent methods,
            // it requires override in the child class if it is visible
            isVisible
        } else {
            // If the method is abstract and is overriding any visible parent methods:
            // it needs to be overridden if:
            // - it is visible or
            // - all super methods are either java.lang.Object method or requires override
            isVisible ||
                superMethods().all {
                    it.containingClass().isJavaLangObject() || it.requiresOverride()
                }
        }
    }

    private fun getUniqueSuperInterfaceMethods(
        superInterfaceMethods: List<MethodItem>
    ): List<MethodItem> {
        val visitCountMap = mutableMapOf<ClassItem, Int>()

        // perform BFS on all super interfaces of each super interface methods'
        // containing interface to determine the leaf interface of each unique hierarchy.
        superInterfaceMethods.forEach {
            val superInterface = it.containingClass()
            val queue = mutableListOf(superInterface)
            while (queue.isNotEmpty()) {
                val s = queue.removeFirst()
                visitCountMap[s] = visitCountMap.getOrDefault(s, 0) + 1
                queue.addAll(
                    s.interfaceTypes().mapNotNull { interfaceType -> interfaceType.asClass() }
                )
            }
        }

        // If visit count is greater than 1, it means the interface is within the hierarchy of
        // another method, thus filter out.
        return superInterfaceMethods.filter { visitCountMap[it.containingClass()]!! == 1 }
    }

    /**
     * Determines if the method needs to be added to the signature file in order to prevent errors
     * when compiling the stubs or the reverse dependencies of the stubs.
     *
     * @return Boolean value indicating whether the method needs to be added to the signature file
     */
    fun isRequiredOverridingMethodForTextStub(): Boolean {
        return (containingClass().isClass() &&
            !modifiers.isAbstract() &&
            superMethods().isNotEmpty() &&
            superMethods().let {
                if (it.size == 1 && it.first().containingClass().isJavaLangObject()) {
                    // If the method is extending a java.lang.Object method,
                    // it only required override when it is directly (not transitively) overriding
                    // it and the signature differs (e.g. visibility or modifier
                    // changes)
                    !sameSignature(
                        this,
                        it.first(),
                        // This method is only called when add-additional-overrides=yes.
                        addAdditionalOverrides = true,
                    )
                } else {
                    // Since a class can extend a single class except Object,
                    // there is only one non-Object super class method at max.
                    val superClassMethods =
                        it.firstOrNull { superMethod ->
                            superMethod.containingClass().isClass() &&
                                !superMethod.containingClass().isJavaLangObject()
                        }

                    // Assume a class implements two interfaces A and B;
                    // A provides a default super method, and B provides an abstract super method.
                    // In such case, the child method is a required overriding method when:
                    // - A and B do not extend each other or
                    // - A is a super interface of B
                    // On the other hand, the child method is not a required overriding method when:
                    // - B is a super interface of A
                    // Given this, we should make decisions only based on the leaf interface of each
                    // unique hierarchy.
                    val uniqueSuperInterfaceMethods =
                        getUniqueSuperInterfaceMethods(
                            it.filter { superMethod -> superMethod.containingClass().isInterface() }
                        )

                    // If super method is non-null, whether this method is required
                    // is determined by whether the super method requires override.
                    // If super method is null, this method is required if there is a
                    // unique super interface that requires override.
                    superClassMethods?.requiresOverride()
                        ?: uniqueSuperInterfaceMethods.any { s -> s.requiresOverride() }
                }
            }) ||
            // To inherit methods with override-equivalent signatures
            // See https://docs.oracle.com/javase/specs/jls/se8/html/jls-9.html#jls-9.4.1.3
            (containingClass().isInterface() &&
                superMethods().count { it.modifiers.isAbstract() || it.modifiers.isDefault() } > 1)
    }
}

/**
 * Check to see if the method is overrideable.
 *
 * Private and static methods cannot be overridden.
 */
private fun MethodItem.isOverrideable(): Boolean = !modifiers.isPrivate() && !modifiers.isStatic()

/**
 * Compute the super methods of this method.
 *
 * A super method is a method from a super class or super interface that is directly overridden by
 * this method.
 */
fun MethodItem.computeSuperMethods(): List<MethodItem> {
    // Constructors and methods that are not overrideable will have no super methods.
    if (isConstructor() || !isOverrideable()) {
        return emptyList()
    }

    // TODO(b/321216636): Remove this awful hack.
    // For some reason `psiMethod.findSuperMethods()` would return an empty list for this
    // specific method. That is incorrect as it clearly overrides a method in `DrawScope` in
    // the same package. However, it is unclear what makes this method distinct from any
    // other method including overloaded methods in the same class that also override
    // methods in`DrawScope`. Returning a correct non-empty list for that method results in
    // the method being removed from an API signature file even though the super method is
    // abstract and this is concrete. That is because AndroidX does not yet set
    // `add-additional-overrides=yes`. When it does then this hack can be removed.
    if (
        containingClass().qualifiedName() ==
            "androidx.compose.ui.graphics.drawscope.CanvasDrawScope" &&
            name() == "drawImage" &&
            toString() ==
                "method androidx.compose.ui.graphics.drawscope.CanvasDrawScope.drawImage(androidx.compose.ui.graphics.ImageBitmap, long, long, long, long, float, androidx.compose.ui.graphics.drawscope.DrawStyle, androidx.compose.ui.graphics.ColorFilter, int)"
    ) {
        return emptyList()
    }

    // Ideally, the search for super methods would start from this method's ClassItem.
    // Unfortunately, due to legacy reasons for methods that were inherited from another ClassItem
    // it is necessary to start the search from the original ClassItem. That is because the psi
    // model's implementation behaved this way and the code that is built of top of superMethods,
    // like the code to determine if overriding methods should be elided from the API signature file
    // relied on that behavior.
    val startingClass = inheritedFrom ?: containingClass()
    return buildSet { appendSuperMethods(this, startingClass) }.toList()
}

/**
 * Append the super methods of this method from the [cls] hierarchy to the [methods] set.
 *
 * @param methods the mutable, order preserving set of super [MethodItem].
 * @param cls the [ClassItem] whose super class and implemented interfaces will be searched for
 *   matching methods.
 */
private fun MethodItem.appendSuperMethods(methods: MutableSet<MethodItem>, cls: ClassItem) {
    // Method from SuperClass or its ancestors
    cls.superClass()?.let { superClass ->
        // Search for a matching method in the super class.
        val superMethod = superClass.findMethod(this)
        if (superMethod == null) {
            // No matching method was found so continue searching in the super class.
            appendSuperMethods(methods, superClass)
        } else {
            // Matching does not check modifiers match so make sure that the matched method is
            // overrideable.
            if (superMethod.isOverrideable()) {
                methods.add(superMethod)
            }
        }
    }

    // Methods implemented from direct interfaces or its ancestors
    appendSuperMethodsFromInterfaces(methods, cls)
}

/**
 * Append the super methods of this method from the interface hierarchy of [cls] to the [methods]
 * set.
 *
 * @param methods the mutable, order preserving set of super [MethodItem].
 * @param cls the [ClassItem] whose implemented interfaces will be searched for matching methods.
 */
private fun MethodItem.appendSuperMethodsFromInterfaces(
    methods: MutableSet<MethodItem>,
    cls: ClassItem
) {
    for (itf in cls.interfaceTypes()) {
        val itfClass = itf.asClass() ?: continue

        // Find the method in the interface.
        itfClass.findMethod(this)?.let { superMethod ->
            // A matching method was found so add it to the super methods if it is overrideable.
            if (superMethod.isOverrideable()) {
                methods.add(superMethod)
            }
        }
        // A method could not be found in this interface so search its interfaces.
        ?: appendSuperMethodsFromInterfaces(methods, itfClass)
    }
}
