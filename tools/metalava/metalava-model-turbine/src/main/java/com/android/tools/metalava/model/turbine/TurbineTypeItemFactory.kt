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

package com.android.tools.metalava.model.turbine

import com.android.tools.metalava.model.ClassTypeItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.ReferenceTypeItem
import com.android.tools.metalava.model.TypeArgumentTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeModifiers
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.TypeParameterScope
import com.android.tools.metalava.model.type.ContextNullability
import com.android.tools.metalava.model.type.DefaultArrayTypeItem
import com.android.tools.metalava.model.type.DefaultClassTypeItem
import com.android.tools.metalava.model.type.DefaultPrimitiveTypeItem
import com.android.tools.metalava.model.type.DefaultTypeItemFactory
import com.android.tools.metalava.model.type.DefaultTypeModifiers
import com.android.tools.metalava.model.type.DefaultVariableTypeItem
import com.android.tools.metalava.model.type.DefaultWildcardTypeItem
import com.google.turbine.model.TurbineConstantTypeKind
import com.google.turbine.type.AnnoInfo
import com.google.turbine.type.Type
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeKind

/** Creates [TypeItem]s from [Type]s. */
internal class TurbineTypeItemFactory(
    private val codebase: TurbineBasedCodebase,
    private val initializer: TurbineCodebaseInitialiser,
    typeParameterScope: TypeParameterScope,
) : DefaultTypeItemFactory<Type, TurbineTypeItemFactory>(typeParameterScope) {

    override fun self() = this

    override fun createNestedFactory(scope: TypeParameterScope) =
        TurbineTypeItemFactory(codebase, initializer, scope)

    override fun getType(underlyingType: Type, contextNullability: ContextNullability) =
        createType(underlyingType, false, contextNullability)

    private fun createModifiers(
        annos: List<AnnoInfo>,
        contextNullability: ContextNullability,
    ): TypeModifiers {
        val typeAnnotations = initializer.createAnnotations(annos)
        // Compute the nullability, factoring in any context nullability and type annotations.
        // Turbine does not support kotlin so the kotlin nullability is always null.
        val nullability = contextNullability.compute(null, typeAnnotations)
        return DefaultTypeModifiers.create(typeAnnotations.toMutableList(), nullability)
    }

    internal fun createType(
        type: Type,
        isVarArg: Boolean,
        contextNullability: ContextNullability = ContextNullability.none,
    ): TypeItem {
        return when (val kind = type.tyKind()) {
            Type.TyKind.PRIM_TY -> {
                type as Type.PrimTy
                // Primitives are always non-null.
                val modifiers = createModifiers(type.annos(), ContextNullability.forceNonNull)
                when (type.primkind()) {
                    TurbineConstantTypeKind.BOOLEAN ->
                        DefaultPrimitiveTypeItem(modifiers, PrimitiveTypeItem.Primitive.BOOLEAN)
                    TurbineConstantTypeKind.BYTE ->
                        DefaultPrimitiveTypeItem(modifiers, PrimitiveTypeItem.Primitive.BYTE)
                    TurbineConstantTypeKind.CHAR ->
                        DefaultPrimitiveTypeItem(modifiers, PrimitiveTypeItem.Primitive.CHAR)
                    TurbineConstantTypeKind.DOUBLE ->
                        DefaultPrimitiveTypeItem(modifiers, PrimitiveTypeItem.Primitive.DOUBLE)
                    TurbineConstantTypeKind.FLOAT ->
                        DefaultPrimitiveTypeItem(modifiers, PrimitiveTypeItem.Primitive.FLOAT)
                    TurbineConstantTypeKind.INT ->
                        DefaultPrimitiveTypeItem(modifiers, PrimitiveTypeItem.Primitive.INT)
                    TurbineConstantTypeKind.LONG ->
                        DefaultPrimitiveTypeItem(modifiers, PrimitiveTypeItem.Primitive.LONG)
                    TurbineConstantTypeKind.SHORT ->
                        DefaultPrimitiveTypeItem(modifiers, PrimitiveTypeItem.Primitive.SHORT)
                    else ->
                        throw IllegalStateException("Invalid primitive type in API surface: $type")
                }
            }
            Type.TyKind.ARRAY_TY -> {
                createArrayType(type as Type.ArrayTy, isVarArg, contextNullability)
            }
            Type.TyKind.CLASS_TY -> {
                type as Type.ClassTy
                var outerClass: ClassTypeItem? = null
                // A ClassTy is represented by list of SimpleClassTY each representing an inner
                // class. e.g. , Outer.Inner.Inner1 will be represented by three simple classes
                // Outer, Outer.Inner and Outer.Inner.Inner1
                for (simpleClass in type.classes()) {
                    // For all outer class types, set the nullability to non-null.
                    outerClass?.modifiers?.setNullability(TypeNullability.NONNULL)
                    outerClass = createInnerClassType(simpleClass, outerClass, contextNullability)
                }
                outerClass!!
            }
            Type.TyKind.TY_VAR -> {
                type as Type.TyVar
                val modifiers = createModifiers(type.annos(), contextNullability)
                val typeParameter = typeParameterScope.getTypeParameter(type.sym().name())
                DefaultVariableTypeItem(modifiers, typeParameter)
            }
            Type.TyKind.WILD_TY -> {
                type as Type.WildTy
                // Wildcards themselves don't have a defined nullability.
                val modifiers =
                    createModifiers(type.annotations(), ContextNullability.forceUndefined)
                when (type.boundKind()) {
                    Type.WildTy.BoundKind.UPPER -> {
                        val upperBound = createWildcardBound(type.bound())
                        DefaultWildcardTypeItem(modifiers, upperBound, null)
                    }
                    Type.WildTy.BoundKind.LOWER -> {
                        // LowerBounded types have java.lang.Object as upper bound
                        val upperBound = createWildcardBound(Type.ClassTy.OBJECT)
                        val lowerBound = createWildcardBound(type.bound())
                        DefaultWildcardTypeItem(modifiers, upperBound, lowerBound)
                    }
                    Type.WildTy.BoundKind.NONE -> {
                        // Unbounded types have java.lang.Object as upper bound
                        val upperBound = createWildcardBound(Type.ClassTy.OBJECT)
                        DefaultWildcardTypeItem(modifiers, upperBound, null)
                    }
                    else ->
                        throw IllegalStateException("Invalid wildcard type in API surface: $type")
                }
            }
            Type.TyKind.VOID_TY ->
                DefaultPrimitiveTypeItem(
                    // Primitives are always non-null.
                    createModifiers(emptyList(), ContextNullability.forceNonNull),
                    PrimitiveTypeItem.Primitive.VOID
                )
            Type.TyKind.NONE_TY ->
                DefaultPrimitiveTypeItem(
                    // Primitives are always non-null.
                    DefaultTypeModifiers.create(emptyList(), TypeNullability.NONNULL),
                    PrimitiveTypeItem.Primitive.VOID
                )
            Type.TyKind.ERROR_TY -> {
                // This is case of unresolved superclass or implemented interface
                type as Type.ErrorTy
                DefaultClassTypeItem(
                    codebase,
                    DefaultTypeModifiers.create(emptyList(), TypeNullability.UNDEFINED),
                    type.name(),
                    emptyList(),
                    null,
                )
            }
            else -> throw IllegalStateException("Invalid type in API surface: $kind")
        }
    }

    private fun createWildcardBound(type: Type) = getGeneralType(type) as ReferenceTypeItem

    private fun createArrayType(
        type: Type.ArrayTy,
        isVarArg: Boolean,
        contextNullability: ContextNullability,
    ): TypeItem {
        // For Turbine's ArrayTy, due to a bug in Turbine, the annotations for multidimensional
        // arrays are in the wrong order so this works around the issue.

        // First, traverse from the outermost array to the innermost component type and add the
        // [AnnoInfo]s to the list. Ending up with the innermost component type. Due to the bug the
        // list contains [AnnoInfo]s from the innermost component type to the outermost types.
        val annosList = mutableListOf<List<AnnoInfo>>()
        var curr: Type = type
        while (curr.tyKind() == Type.TyKind.ARRAY_TY) {
            curr as Type.ArrayTy
            annosList.add(curr.annos())
            curr = curr.elementType()
        }

        // Then, get the type for the innermost component, it has the correct annotations.
        val componentType = getGeneralType(curr)

        // Finally, traverse over the annotations from the innermost component type to the outermost
        // array and construct a [DefaultArrayTypeItem] around the inner component type using its
        // `List<AnnoInfo>`. The last `List<AnnoInfo>` is for the outermost array, and it needs to
        // be tagged with the [isVarArg] value and [contextNullability].
        val lastIndex = annosList.size - 1
        return annosList.foldIndexed(componentType) { index, typeItem, annos ->
            val (arrayContextNullability, arrayVarArg) =
                if (index == lastIndex) {
                    // Outermost array. Should be called with correct value of isVarArg and
                    // the contextual nullability.
                    Pair(contextNullability, isVarArg)
                } else {
                    Pair(ContextNullability.none, false)
                }

            val modifiers = createModifiers(annos, arrayContextNullability)
            DefaultArrayTypeItem(modifiers, typeItem, arrayVarArg)
        }
    }

    /**
     * Retrieves the `ClassTypeItem` representation of the outer class associated with a given
     * nested class type. Intended for types that are not explicitly mentioned within the source
     * code.
     *
     * @param type The `Type.ClassTy.SimpleClassTy` object representing the nested class.
     * @return The `ClassTypeItem` representing the outer class.
     */
    private fun getOuterClassType(type: Type.ClassTy.SimpleClassTy): ClassTypeItem {
        val className = initializer.getQualifiedName(type.sym().binaryName())
        val classTypeElement = initializer.getTypeElement(className)!!
        return createOuterClassType(classTypeElement.enclosingElement!!)!!
    }

    /**
     * Constructs a `ClassTypeItem` representation from a type element. Intended for types that are
     * not explicitly mentioned within the source code.
     *
     * @param element The `Element` object representing the type.
     * @return The corresponding `ClassTypeItem`, or null if the `element` does not represent a
     *   declared type.
     */
    private fun createOuterClassType(element: Element): ClassTypeItem? {
        if (element.asType().kind != TypeKind.DECLARED) return null

        val outerClassElement = element.enclosingElement!!
        val outerClassTypeItem = createOuterClassType(outerClassElement)

        element as TypeElement

        // Since this type was never part of source , it won't have any annotation or arguments
        val modifiers = DefaultTypeModifiers.create(emptyList(), TypeNullability.NONNULL)
        val classTypeItem =
            DefaultClassTypeItem(
                codebase,
                modifiers,
                element.qualifiedName.toString(), // Assuming qualifiedName is available on element
                emptyList(),
                outerClassTypeItem
            )
        return classTypeItem
    }

    private fun createInnerClassType(
        type: Type.ClassTy.SimpleClassTy,
        outerClass: ClassTypeItem?,
        contextNullability: ContextNullability,
    ): ClassTypeItem {
        val outerClassItem =
            if (type.sym().binaryName().contains("$") && outerClass == null) {
                getOuterClassType(type)
            } else {
                outerClass
            }

        val modifiers = createModifiers(type.annos(), contextNullability)
        val qualifiedName = initializer.getQualifiedName(type.sym().binaryName())
        val parameters = type.targs().map { getGeneralType(it) as TypeArgumentTypeItem }
        return DefaultClassTypeItem(codebase, modifiers, qualifiedName, parameters, outerClassItem)
    }
}
