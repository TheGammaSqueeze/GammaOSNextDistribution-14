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

package com.android.tools.metalava.model.psi

import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.ExceptionTypeItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeParameterList
import com.android.tools.metalava.model.computeSuperMethods
import com.android.tools.metalava.model.fixUpTypeNullability
import com.intellij.psi.PsiAnnotationMethod
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import org.jetbrains.kotlin.name.JvmStandardClassIds
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPropertyAccessor
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UAnnotationMethod
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UThrowExpression
import org.jetbrains.uast.UTryExpression
import org.jetbrains.uast.getParentOfType
import org.jetbrains.uast.kotlin.KotlinUMethodWithFakeLightDelegateBase
import org.jetbrains.uast.toUElement
import org.jetbrains.uast.visitor.AbstractUastVisitor

open class PsiMethodItem(
    codebase: PsiBasedCodebase,
    val psiMethod: PsiMethod,
    // Takes ClassItem as this may be duplicated from a PsiBasedCodebase on the classpath into a
    // TextClassItem.
    containingClass: ClassItem,
    name: String,
    modifiers: DefaultModifierList,
    documentation: String,
    private val returnType: TypeItem,
    private val parameters: List<PsiParameterItem>,
    override val typeParameterList: TypeParameterList,
    private val throwsTypes: List<ExceptionTypeItem>
) :
    PsiMemberItem(
        codebase = codebase,
        modifiers = modifiers,
        documentation = documentation,
        element = psiMethod,
        containingClass = containingClass,
        name = name,
    ),
    MethodItem {

    init {
        for (parameter in parameters) {
            @Suppress("LeakingThis")
            parameter.containingMethod = this
        }
    }

    override var emit: Boolean = !modifiers.isExpect()

    override var inheritedFrom: ClassItem? = null

    override var property: PsiPropertyItem? = null

    @Deprecated("This property should not be accessed directly.")
    override var _requiresOverride: Boolean? = null

    override fun equals(other: Any?): Boolean {
        // TODO: Allow mix and matching with other MethodItems?
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PsiMethodItem

        if (psiMethod != other.psiMethod) return false

        return true
    }

    override fun hashCode(): Int {
        return psiMethod.hashCode()
    }

    override fun findMainDocumentation(): String {
        if (documentation == "") return documentation
        val comment = codebase.getComment(documentation)
        val end = findFirstTag(comment)?.textRange?.startOffset ?: documentation.length
        return comment.text.substring(0, end)
    }

    override fun isConstructor(): Boolean = false

    override fun isImplicitConstructor(): Boolean = false

    override fun returnType(): TypeItem = returnType

    override fun parameters(): List<PsiParameterItem> = parameters

    override fun psi() = psiMethod

    private var superMethods: List<MethodItem>? = null

    override fun superMethods(): List<MethodItem> {
        if (superMethods == null) {
            superMethods = computeSuperMethods()
        }

        return superMethods!!
    }

    override fun throwsTypes() = throwsTypes

    override fun isExtensionMethod(): Boolean {
        if (isKotlin()) {
            val ktParameters =
                ((psiMethod as? UMethod)?.sourcePsi as? KtNamedFunction)?.valueParameters
                    ?: return false
            return ktParameters.size < parameters.size
        }

        return false
    }

    override fun isKotlinProperty(): Boolean {
        return psiMethod is UMethod &&
            (psiMethod.sourcePsi is KtProperty ||
                psiMethod.sourcePsi is KtPropertyAccessor ||
                psiMethod.sourcePsi is KtParameter &&
                    (psiMethod.sourcePsi as KtParameter).hasValOrVar())
    }

    override fun findThrownExceptions(): Set<ClassItem> {
        val method = psiMethod as? UMethod ?: return emptySet()
        if (!isKotlin()) {
            return emptySet()
        }

        val exceptions = mutableSetOf<ClassItem>()

        method.accept(
            object : AbstractUastVisitor() {
                override fun visitThrowExpression(node: UThrowExpression): Boolean {
                    val type = node.thrownExpression.getExpressionType()
                    if (type != null) {
                        val typeItemFactory =
                            codebase.globalTypeItemFactory.from(this@PsiMethodItem)
                        val exceptionClass = typeItemFactory.getType(type).asClass()
                        if (exceptionClass != null && !isCaught(exceptionClass, node)) {
                            exceptions.add(exceptionClass)
                        }
                    }
                    return super.visitThrowExpression(node)
                }

                private fun isCaught(exceptionClass: ClassItem, node: UThrowExpression): Boolean {
                    var current: UElement = node
                    while (true) {
                        val tryExpression =
                            current.getParentOfType<UTryExpression>(
                                UTryExpression::class.java,
                                true,
                                UMethod::class.java
                            )
                                ?: return false

                        for (catchClause in tryExpression.catchClauses) {
                            for (type in catchClause.types) {
                                val qualifiedName = type.canonicalText
                                if (exceptionClass.extends(qualifiedName)) {
                                    return true
                                }
                            }
                        }

                        current = tryExpression
                    }
                }
            }
        )

        return exceptions
    }

    internal fun areAllParametersOptional(): Boolean {
        for (param in parameters) {
            if (!param.hasDefaultValue()) {
                return false
            }
        }
        return true
    }

    override fun defaultValue(): String {
        return when (psiMethod) {
            is UAnnotationMethod -> {
                psiMethod.uastDefaultValue?.let { codebase.printer.toSourceString(it) } ?: ""
            }
            is PsiAnnotationMethod -> {
                psiMethod.defaultValue?.let { codebase.printer.toSourceExpression(it, this) }
                    ?: super.defaultValue()
            }
            else -> super.defaultValue()
        }
    }

    override fun duplicate(targetContainingClass: ClassItem): PsiMethodItem {
        val duplicated =
            create(
                codebase,
                targetContainingClass,
                psiMethod,
                // Use the scope from this class to resolve type parameter references as the target
                // class may have a completely different set.
                codebase.globalTypeItemFactory.from(containingClass)
            )

        duplicated.inheritedFrom = containingClass

        // Preserve flags that may have been inherited (propagated) from surrounding packages
        if (targetContainingClass.hidden) {
            duplicated.hidden = true
        }
        if (targetContainingClass.removed) {
            duplicated.removed = true
        }
        if (targetContainingClass.docOnly) {
            duplicated.docOnly = true
        }
        if (targetContainingClass.deprecated) {
            duplicated.deprecated = true
        }
        return duplicated
    }

    /* Call corresponding PSI utility method -- if I can find it!
    override fun matches(other: MethodItem): Boolean {
        if (other !is PsiMethodItem) {
            return super.matches(other)
        }

        // TODO: Find better API: this also checks surrounding class which we don't want!
        return psiMethod.isEquivalentTo(other.psiMethod)
    }
    */

    override fun shouldExpandOverloads(): Boolean {
        val ktFunction = (psiMethod as? UMethod)?.sourcePsi as? KtFunction ?: return false
        return modifiers.isActual() &&
            psiMethod.hasAnnotation(JvmStandardClassIds.JVM_OVERLOADS_FQ_NAME.asString()) &&
            // It is /technically/ invalid to have actual functions with default values, but
            // some places suppress the compiler error, so we should handle it here too.
            ktFunction.valueParameters.none { it.hasDefaultValue() } &&
            parameters.any { it.hasDefaultValue() }
    }

    override fun finishInitialization() {
        super.finishInitialization()

        returnType.fixUpTypeNullability(this)
        parameters.forEach { it.finishInitialization() }
    }

    companion object {
        /**
         * Create a [PsiMethodItem].
         *
         * The [containingClass] is not necessarily a [PsiClassItem] as this is used to implement
         * [MethodItem.duplicate] as well as create [PsiMethodItem] from the underlying Psi source
         * model.
         */
        internal fun create(
            codebase: PsiBasedCodebase,
            containingClass: ClassItem,
            psiMethod: PsiMethod,
            enclosingClassTypeItemFactory: PsiTypeItemFactory,
        ): PsiMethodItem {
            assert(!psiMethod.isConstructor)
            // UAST workaround: @JvmName for UMethod with fake LC PSI
            // TODO: https://youtrack.jetbrains.com/issue/KTIJ-25133
            val name =
                if (psiMethod is KotlinUMethodWithFakeLightDelegateBase<*>) {
                    psiMethod.sourcePsi
                        ?.annotationEntries
                        ?.find { annoEntry ->
                            val text = annoEntry.typeReference?.text ?: return@find false
                            JvmName::class.qualifiedName?.contains(text) == true
                        }
                        ?.toUElement(UAnnotation::class.java)
                        ?.takeIf {
                            // Above `find` deliberately avoids resolution and uses verbatim text.
                            // Below, we need annotation value anyway, but just double-check
                            // if the converted annotation is indeed the resolved @JvmName
                            it.qualifiedName == JvmName::class.qualifiedName
                        }
                        ?.findAttributeValue("name")
                        ?.evaluate() as? String
                        ?: psiMethod.name
                } else {
                    psiMethod.name
                }
            val commentText = javadoc(psiMethod)
            val modifiers = modifiers(codebase, psiMethod, commentText)
            // Create the TypeParameterList for this before wrapping any of the other types used by
            // it as they may reference a type parameter in the list.
            val (typeParameterList, methodTypeItemFactory) =
                PsiTypeParameterList.create(
                    codebase,
                    enclosingClassTypeItemFactory,
                    "method $name",
                    psiMethod
                )
            val parameters = parameterList(codebase, psiMethod, methodTypeItemFactory)
            val psiReturnType = psiMethod.returnType
            val returnType = methodTypeItemFactory.getType(psiReturnType!!, psiMethod)
            val method =
                PsiMethodItem(
                    codebase = codebase,
                    psiMethod = psiMethod,
                    containingClass = containingClass,
                    name = name,
                    documentation = commentText,
                    modifiers = modifiers,
                    returnType = returnType,
                    parameters = parameters,
                    typeParameterList = typeParameterList,
                    throwsTypes = throwsTypes(psiMethod, methodTypeItemFactory),
                )
            if (modifiers.isFinal() && containingClass.modifiers.isFinal()) {
                // The containing class is final, so it is implied that every method is final as
                // well.
                // No need to apply 'final' to each method. (We do it here rather than just in the
                // signature emit code since we want to make sure that the signature comparison
                // methods with super methods also consider this method non-final.)
                modifiers.setFinal(false)
            }

            return method
        }

        /**
         * Create a [PsiMethodItem] from a [PsiMethodItem] in a hidden super class.
         *
         * @see ClassItem.inheritMethodFromNonApiAncestor
         */
        internal fun create(containingClass: PsiClassItem, original: PsiMethodItem): PsiMethodItem {
            val typeParameterBindings = containingClass.mapTypeVariables(original.containingClass())
            val returnType = original.returnType.convertType(typeParameterBindings) as PsiTypeItem

            // This results in a PsiMethodItem that is inconsistent, compared with other
            // PsiMethodItem. PsiMethodItems created directly from the source are such that:
            //
            //    psiMethod.containingClass === containingClass().psiClass
            //
            // However, the PsiMethodItem created here contains a psiMethod from a different class,
            // usually the super class, so:
            //
            //    psiMethod.containingClass !== containingClass().psiClass
            //
            // If the method was created from the super class then:
            //
            //    psiMethod.containingClass === containingClass().superClass().psiClass
            //
            // The consequence of this is that the PsiMethodItem does not behave as might be
            // expected. e.g. superMethods() will find super methods of the method in the super
            // class, not the PsiMethodItem's containing class.
            val method =
                PsiMethodItem(
                    codebase = original.codebase,
                    psiMethod = original.psiMethod,
                    containingClass = containingClass,
                    name = original.name(),
                    documentation = original.documentation,
                    modifiers = original.modifiers.duplicate(),
                    returnType = returnType,
                    parameters =
                        PsiParameterItem.create(original.parameters(), typeParameterBindings),
                    // This is probably incorrect as the type parameter bindings probably need
                    // applying here but this is the same behavior as before.
                    // TODO: Investigate whether the above comment is correct and fix if necessary.
                    typeParameterList = original.typeParameterList,
                    throwsTypes = original.throwsTypes,
                )

            return method
        }

        internal fun parameterList(
            codebase: PsiBasedCodebase,
            psiMethod: PsiMethod,
            enclosingTypeItemFactory: PsiTypeItemFactory,
        ) =
            psiMethod.psiParameters.mapIndexed { index, parameter ->
                PsiParameterItem.create(codebase, parameter, index, enclosingTypeItemFactory)
            }

        internal fun throwsTypes(
            psiMethod: PsiMethod,
            enclosingTypeItemFactory: PsiTypeItemFactory,
        ): List<ExceptionTypeItem> {
            val throwsClassTypes = psiMethod.throwsList.referencedTypes
            if (throwsClassTypes.isEmpty()) {
                return emptyList()
            }

            return throwsClassTypes
                // Convert the PsiType to an ExceptionTypeItem and wrap it in a ThrowableType.
                .map { psiType -> enclosingTypeItemFactory.getExceptionType(PsiTypeInfo(psiType)) }
                // We're sorting the names here even though outputs typically do their own sorting,
                // since for example the MethodItem.sameSignature check wants to do an
                // element-by-element comparison to see if the signature matches, and that should
                // match overrides even if they specify their elements in different orders.
                .sortedWith(ExceptionTypeItem.fullNameComparator)
        }
    }
}

/** Get the [PsiParameter]s for a [PsiMethod]. */
val PsiMethod.psiParameters: List<PsiParameter>
    get() = if (this is UMethod) uastParameters else parameterList.parameters.toList()
