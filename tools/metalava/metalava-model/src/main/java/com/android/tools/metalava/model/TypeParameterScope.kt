/*
 * Copyright (C) 2024 The Android Open Source Project
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

import java.lang.StringBuilder

/**
 * The set of [TypeParameterItem]s that are in scope.
 *
 * This is used to resolve a reference to a type parameter to the appropriate type parameter. They
 * are in order from closest to most distant. e.g. When resolving the type parameters for `method`
 * this will contain the type parameters from `method` then from `Inner`, then from `Outer`, i.e.
 * `[M, X, I, X, O, X]`. That ensures that when searching for a type parameter whose name shadows
 * one from an outer scope, e.g. `X`, that the inner one is used.
 *
 * ```
 *     public class Outer<O, X> {
 *     }
 *
 *     public class Outer.Inner<I, X> {
 *       method public <M, X> M method(O o, I i);
 *     }
 * ```
 *
 * This does not implement [equals] and [hashCode] as the identity comparison is sufficient and
 * necessary.
 *
 * It is sufficient because [TypeParameterScope]s have a one-to-one correspondence with a type
 * parameter list owner.
 *
 * It is necessary because [TypeParameterItem.equals] is currently only based on the name. So,
 * unless great care is taken to use identity comparison for [MapWrapper.nameToTypeParameterItem]
 * contents it would break caching and have every type `T` reference the same type parameter.
 */
sealed class TypeParameterScope private constructor() {

    /** True if there are no type parameters in scope. */
    fun isEmpty() = this === empty

    /**
     * Create a nested [TypeParameterScope] that will delegate to this one for any
     * [TypeParameterItem]s that it cannot find.
     *
     * @param description is some helpful information about this scope that will be useful to track
     *   down issues when a type parameter could not be found.
     * @param typeParameters if this is empty then this method will return [this], otherwise it will
     *   create a new [TypeParameterScope] that delegates to this one.
     */
    fun nestedScope(
        description: String,
        typeParameters: List<TypeParameterItem>,
    ): TypeParameterScope =
        // If the typeParameters is empty then just reuse this one, otherwise create a new scope
        // delegating to this.
        if (typeParameters.isEmpty()) this else MapWrapper(description, typeParameters, this)

    /**
     * Finds the closest [TypeParameterItem] with the specified name.
     *
     * If none exists then returns `null`. This should only be used when it is not known if the name
     * is a type parameter. Otherwise, call [getTypeParameter].
     */
    abstract fun findTypeParameter(name: String): TypeParameterItem?

    /**
     * Finds the closest [TypeParameterItem] with the specified name.
     *
     * If none exists then fail. This should only be used when it is not known that the name is a
     * type parameter. Otherwise, call [findTypeParameter].
     */
    fun getTypeParameter(name: String) =
        findTypeParameter(name) ?: error("Could not find type parameter $name in $this")

    /** Finds the scope that provides at least one of the supplied [names] or [empty] otherwise. */
    abstract fun findSignificantScope(names: Set<String>): TypeParameterScope

    companion object {
        val empty: TypeParameterScope = Empty

        /**
         * Collect all the type parameters in scope for the given [classItem] then wrap them in an
         * [TypeParameterScope].
         */
        fun from(classItem: ClassItem?): TypeParameterScope {
            return if (classItem == null) empty
            else {
                // Construct a scope from the owner.
                from(classItem.containingClass())
                    // Nest this inside it.
                    .nestedScope(
                        description = "class ${classItem.qualifiedName()}",
                        classItem.typeParameterList,
                    )
            }
        }

        /**
         * Collect all the type parameters in scope for the given [methodItem] then wrap them in an
         * [TypeParameterScope].
         */
        fun from(methodItem: MethodItem): TypeParameterScope {
            // Construct a scope from the owner.
            return from(methodItem.containingClass())
                // Nest this inside it.
                .nestedScope(
                    description = "method ${methodItem.name()}",
                    methodItem.typeParameterList,
                )
        }
    }

    private class MapWrapper(
        private val description: String,
        list: List<TypeParameterItem>,
        private val enclosingScope: TypeParameterScope
    ) : TypeParameterScope() {

        /**
         * A mapping from name to [TypeParameterItem].
         *
         * Includes any [TypeParameterItem]s from the [enclosingScope] that are not shadowed by an
         * item in the list.
         */
        private val nameToTypeParameterItem: Map<String, TypeParameterItem>

        /**
         * The set of type parameter names added by this scope; does not include names from
         * enclosing scopes but does include any shadows of those names added in this scope.
         */
        private val namesAddedInThisScope: Set<String>

        init {
            namesAddedInThisScope = list.map { it.name() }.toSet()

            // Construct a map by taking a mutable copy of the map from the enclosing scope, if
            // available, otherwise creating an empty map. Then adding all the type parameters that
            // are part of this, replacing (i.e. shadowing) any type parameters with the same name
            // from the enclosing scope.
            val mutableMap =
                if (enclosingScope is MapWrapper)
                    enclosingScope.nameToTypeParameterItem.toMutableMap()
                else mutableMapOf()
            list.associateByTo(mutableMap) { it.name() }
            nameToTypeParameterItem = mutableMap.toMap()
        }

        override fun findTypeParameter(name: String) = nameToTypeParameterItem[name]

        override fun findSignificantScope(names: Set<String>): TypeParameterScope {
            // Fast path to avoid recursing up enclosing scopes.
            if (names.isEmpty()) return empty

            // If any of the supplied names are added in this scope then use this scope.
            if (names.any { it in namesAddedInThisScope }) return this

            // Otherwise, check the enclosing scope.
            return enclosingScope.findSignificantScope(names)
        }

        override fun toString(): String {
            return buildString {
                appendTo(this)
                var scope = enclosingScope
                while (scope is MapWrapper) {
                    append(" -> ")
                    scope.appendTo(this)
                    scope = scope.enclosingScope
                }
            }
        }

        /** Append information about this scope to the [builder], for debug purposes. */
        private fun appendTo(builder: StringBuilder) {
            builder.apply {
                append("Scope(<")
                nameToTypeParameterItem.keys.joinTo(this)
                append("> for ")
                append(description)
                append(")")
            }
        }
    }

    private object Empty : TypeParameterScope() {

        override fun findTypeParameter(name: String) = null

        override fun findSignificantScope(names: Set<String>) = this

        override fun toString(): String {
            return "Scope()"
        }
    }
}
