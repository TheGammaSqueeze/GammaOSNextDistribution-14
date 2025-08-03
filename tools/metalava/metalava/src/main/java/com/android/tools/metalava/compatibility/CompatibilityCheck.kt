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

package com.android.tools.metalava.compatibility

import com.android.tools.metalava.ANDROID_SYSTEM_API
import com.android.tools.metalava.ANDROID_TEST_API
import com.android.tools.metalava.ApiType
import com.android.tools.metalava.CodebaseComparator
import com.android.tools.metalava.ComparisonVisitor
import com.android.tools.metalava.JVM_DEFAULT_WITH_COMPATIBILITY
import com.android.tools.metalava.NullnessMigration.Companion.findNullnessAnnotation
import com.android.tools.metalava.NullnessMigration.Companion.isNullable
import com.android.tools.metalava.cli.common.MetalavaCliException
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.ArrayTypeItem
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.Item.Companion.describe
import com.android.tools.metalava.model.MergedCodebase
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.PrimitiveTypeItem
import com.android.tools.metalava.model.TypeItem
import com.android.tools.metalava.model.TypeNullability
import com.android.tools.metalava.model.VariableTypeItem
import com.android.tools.metalava.model.psi.PsiItem
import com.android.tools.metalava.options
import com.android.tools.metalava.reporter.IssueConfiguration
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Issues.Issue
import com.android.tools.metalava.reporter.Reporter
import com.android.tools.metalava.reporter.Severity
import com.intellij.psi.PsiField
import java.util.function.Predicate

/**
 * Compares the current API with a previous version and makes sure the changes are compatible. For
 * example, you can make a previously nullable parameter non null, but not vice versa.
 *
 * TODO: Only allow nullness changes on final classes!
 */
class CompatibilityCheck(
    val filterReference: Predicate<Item>,
    private val apiType: ApiType,
    private val reporter: Reporter,
    private val issueConfiguration: IssueConfiguration,
) : ComparisonVisitor() {

    var foundProblems = false

    private fun containingMethod(item: Item): MethodItem? {
        if (item is MethodItem) {
            return item
        }
        if (item is ParameterItem) {
            return item.containingMethod()
        }
        return null
    }

    private fun compareNullability(old: Item, new: Item) {
        val oldMethod = containingMethod(old)
        val newMethod = containingMethod(new)

        if (oldMethod != null && newMethod != null) {
            if (
                oldMethod.containingClass().qualifiedName() !=
                    newMethod.containingClass().qualifiedName() ||
                    ((oldMethod.inheritedFrom != null) != (newMethod.inheritedFrom != null))
            ) {
                // If the old method and new method are defined on different classes, then it's
                // possible
                // that the old method was previously overridden and we omitted it.
                // So, if the old method and new methods are defined on different classes, then we
                // skip
                // nullability checks
                return
            }
        }
        // Should not remove nullness information
        // Can't change information incompatibly
        val oldNullnessAnnotation = findNullnessAnnotation(old)
        if (oldNullnessAnnotation != null) {
            val newNullnessAnnotation = findNullnessAnnotation(new)
            if (newNullnessAnnotation == null) {
                val typeNullability = new.type()?.modifiers?.nullability()
                if (typeNullability == TypeNullability.NULLABLE && isNullable(old)) {
                    return
                }
                if (typeNullability == TypeNullability.NONNULL && !isNullable(old)) {
                    return
                }
                val name = AnnotationItem.simpleName(oldNullnessAnnotation)
                if (old.type() is PrimitiveTypeItem) {
                    return
                }
                report(
                    Issues.INVALID_NULL_CONVERSION,
                    new,
                    "Attempted to remove $name annotation from ${describe(new)}"
                )
            } else {
                val oldNullable = isNullable(old)
                val newNullable = isNullable(new)
                if (oldNullable != newNullable) {
                    // You can change a parameter from nonnull to nullable
                    // You can change a method from nullable to nonnull
                    // You cannot change a parameter from nullable to nonnull
                    // You cannot change a method from nonnull to nullable
                    if (oldNullable && old is ParameterItem) {
                        report(
                            Issues.INVALID_NULL_CONVERSION,
                            new,
                            "Attempted to change parameter from @Nullable to @NonNull: " +
                                "incompatible change for ${describe(new)}"
                        )
                    } else if (!oldNullable && old is MethodItem) {
                        report(
                            Issues.INVALID_NULL_CONVERSION,
                            new,
                            "Attempted to change method return from @NonNull to @Nullable: " +
                                "incompatible change for ${describe(new)}"
                        )
                    }
                }
            }
        }
    }

    override fun compare(old: Item, new: Item) {
        val oldModifiers = old.modifiers
        val newModifiers = new.modifiers
        if (oldModifiers.isOperator() && !newModifiers.isOperator()) {
            report(
                Issues.OPERATOR_REMOVAL,
                new,
                "Cannot remove `operator` modifier from ${describe(new)}: Incompatible change"
            )
        }

        if (oldModifiers.isInfix() && !newModifiers.isInfix()) {
            report(
                Issues.INFIX_REMOVAL,
                new,
                "Cannot remove `infix` modifier from ${describe(new)}: Incompatible change"
            )
        }

        if (!old.isCompatibilitySuppressed() && new.isCompatibilitySuppressed()) {
            report(
                Issues.BECAME_UNCHECKED,
                old,
                "Removed ${describe(old)} from compatibility checked API surface"
            )
        }

        compareNullability(old, new)
    }

    override fun compare(old: ParameterItem, new: ParameterItem) {
        val prevName = old.publicName()
        val newName = new.publicName()
        if (prevName != null) {
            if (newName == null) {
                report(
                    Issues.PARAMETER_NAME_CHANGE,
                    new,
                    "Attempted to remove parameter name from ${describe(new)}"
                )
            } else if (newName != prevName) {
                report(
                    Issues.PARAMETER_NAME_CHANGE,
                    new,
                    "Attempted to change parameter name from $prevName to $newName in ${describe(new.containingMethod())}"
                )
            }
        }

        if (old.hasDefaultValue() && !new.hasDefaultValue()) {
            report(
                Issues.DEFAULT_VALUE_CHANGE,
                new,
                "Attempted to remove default value from ${describe(new)}"
            )
        }

        if (old.isVarArgs() && !new.isVarArgs()) {
            // In Java, changing from array to varargs is a compatible change, but
            // not the other way around. Kotlin is the same, though in Kotlin
            // you have to change the parameter type as well to an array type; assuming you
            // do that it's the same situation as Java; otherwise the normal
            // signature check will catch the incompatibility.
            report(
                Issues.VARARG_REMOVAL,
                new,
                "Changing from varargs to array is an incompatible change: ${describe(
                    new,
                    includeParameterTypes = true,
                    includeParameterNames = true
                )}"
            )
        }
    }

    override fun compare(old: ClassItem, new: ClassItem) {
        val oldModifiers = old.modifiers
        val newModifiers = new.modifiers

        if (
            old.isInterface() != new.isInterface() ||
                old.isEnum() != new.isEnum() ||
                old.isAnnotationType() != new.isAnnotationType()
        ) {
            report(
                Issues.CHANGED_CLASS,
                new,
                "${describe(new, capitalize = true)} changed class/interface declaration"
            )
            return // Avoid further warnings like "has changed abstract qualifier" which is implicit
            // in this change
        }

        for (iface in old.interfaceTypes()) {
            val qualifiedName = iface.asClass()?.qualifiedName() ?: continue
            if (!new.implements(qualifiedName)) {
                report(
                    Issues.REMOVED_INTERFACE,
                    new,
                    "${describe(old, capitalize = true)} no longer implements $iface"
                )
            }
        }

        for (iface in new.filteredInterfaceTypes(filterReference)) {
            val qualifiedName = iface.asClass()?.qualifiedName() ?: continue
            if (!old.implements(qualifiedName)) {
                report(
                    Issues.ADDED_INTERFACE,
                    new,
                    "Added interface $iface to class ${describe(old)}"
                )
            }
        }

        if (!oldModifiers.isSealed() && newModifiers.isSealed()) {
            report(
                Issues.ADD_SEALED,
                new,
                "Cannot add 'sealed' modifier to ${describe(new)}: Incompatible change"
            )
        } else if (old.isClass() && !oldModifiers.isAbstract() && newModifiers.isAbstract()) {
            report(
                Issues.CHANGED_ABSTRACT,
                new,
                "${describe(new, capitalize = true)} changed 'abstract' qualifier"
            )
        }

        if (oldModifiers.isFunctional() && !newModifiers.isFunctional()) {
            report(
                Issues.FUN_REMOVAL,
                new,
                "Cannot remove 'fun' modifier from ${describe(new)}: source incompatible change"
            )
        }

        // Check for changes in final & static, but not in enums (since PSI and signature files
        // differ
        // a bit in whether they include these for enums
        if (!new.isEnum()) {
            if (!oldModifiers.isFinal() && newModifiers.isFinal()) {
                // It is safe to make a class final if was impossible for an application to create a
                // subclass.
                if (!old.isExtensible()) {
                    report(
                        Issues.ADDED_FINAL_UNINSTANTIABLE,
                        new,
                        "${
                            describe(
                                new,
                                capitalize = true
                            )
                        } added 'final' qualifier but was previously uninstantiable and therefore could not be subclassed"
                    )
                } else {
                    report(
                        Issues.ADDED_FINAL,
                        new,
                        "${describe(new, capitalize = true)} added 'final' qualifier"
                    )
                }
            }

            if (oldModifiers.isStatic() != newModifiers.isStatic()) {
                val hasPublicConstructor = old.constructors().any { it.isPublic }
                if (!old.isInnerClass() || hasPublicConstructor) {
                    report(
                        Issues.CHANGED_STATIC,
                        new,
                        "${describe(new, capitalize = true)} changed 'static' qualifier"
                    )
                }
            }
        }

        val oldVisibility = oldModifiers.getVisibilityString()
        val newVisibility = newModifiers.getVisibilityString()
        if (oldVisibility != newVisibility) {
            // TODO: Use newModifiers.asAccessibleAs(oldModifiers) to provide different error
            // messages
            // based on whether this seems like a reasonable change, e.g. making a private or final
            // method more
            // accessible is fine (no overridden method affected) but not making methods less
            // accessible etc
            report(
                Issues.CHANGED_SCOPE,
                new,
                "${describe(new, capitalize = true)} changed visibility from $oldVisibility to $newVisibility"
            )
        }

        if (!old.effectivelyDeprecated == new.effectivelyDeprecated) {
            report(
                Issues.CHANGED_DEPRECATED,
                new,
                "${describe(
                    new,
                    capitalize = true
                )} has changed deprecation state ${old.effectivelyDeprecated} --> ${new.effectivelyDeprecated}"
            )
        }

        val oldSuperClassName = old.superClass()?.qualifiedName()
        if (oldSuperClassName != null) { // java.lang.Object can't have a superclass.
            if (!new.extends(oldSuperClassName)) {
                report(
                    Issues.CHANGED_SUPERCLASS,
                    new,
                    "${describe(
                        new,
                        capitalize = true
                    )} superclass changed from $oldSuperClassName to ${new.superClass()?.qualifiedName()}"
                )
            }
        }

        if (old.hasTypeVariables() || new.hasTypeVariables()) {
            val oldTypeParamsCount = old.typeParameterList.size
            val newTypeParamsCount = new.typeParameterList.size
            if (oldTypeParamsCount > 0 && oldTypeParamsCount != newTypeParamsCount) {
                report(
                    Issues.CHANGED_TYPE,
                    new,
                    "${
                        describe(
                            old,
                            capitalize = true
                        )
                    } changed number of type parameters from $oldTypeParamsCount to $newTypeParamsCount"
                )
            }
        }

        if (
            old.modifiers.isAnnotatedWith(JVM_DEFAULT_WITH_COMPATIBILITY) &&
                !new.modifiers.isAnnotatedWith(JVM_DEFAULT_WITH_COMPATIBILITY)
        ) {
            report(
                Issues.REMOVED_JVM_DEFAULT_WITH_COMPATIBILITY,
                new,
                "Cannot remove @$JVM_DEFAULT_WITH_COMPATIBILITY annotation from " +
                    "${describe(new)}: Incompatible change"
            )
        }
    }

    /**
     * Return true if a [ClassItem] loaded from a signature file could be subclassed, i.e. is not
     * final, or sealed and has at least one accessible constructor.
     */
    private fun ClassItem.isExtensible() =
        !modifiers.isFinal() &&
            !modifiers.isSealed() &&
            constructors().any { it.isPublic || it.isProtected }

    /**
     * Check if the return types are compatible, which is true when:
     * - they're equal
     * - both are arrays, and the component types are compatible
     * - both are variable types, and they have equal bounds
     * - the new return type is a variable and has the old return type in its bounds
     *
     * TODO(b/111253910): could this also allow changes like List<T> to List<A> where A and T have
     *   equal bounds?
     */
    private fun compatibleReturnTypes(old: TypeItem, new: TypeItem): Boolean {
        when (new) {
            is ArrayTypeItem ->
                return old is ArrayTypeItem &&
                    compatibleReturnTypes(old.componentType, new.componentType)
            is VariableTypeItem -> {
                if (old is VariableTypeItem) {
                    // If both return types are parameterized then the constraints must be
                    // exactly the same.
                    return old.asTypeParameter.typeBounds() == new.asTypeParameter.typeBounds()
                } else {
                    // If the old return type was not parameterized but the new return type is,
                    // the new type parameter must have the old return type in its bounds
                    // (e.g. changing return type from `String` to `T extends String` is valid).
                    val constraints = new.asTypeParameter.typeBounds()
                    val oldClass = old.asClass()
                    for (constraint in constraints) {
                        val newClass = constraint.asClass()
                        if (
                            oldClass == null ||
                                newClass == null ||
                                !oldClass.extendsOrImplements(newClass.qualifiedName())
                        ) {
                            return false
                        }
                    }
                    return true
                }
            }
            else -> return old == new
        }
    }

    override fun compare(old: MethodItem, new: MethodItem) {
        val oldModifiers = old.modifiers
        val newModifiers = new.modifiers

        val oldReturnType = old.returnType()
        val newReturnType = new.returnType()
        if (!new.isConstructor()) {
            if (!compatibleReturnTypes(oldReturnType, newReturnType)) {
                // For incompatible type variable changes, include the type bounds in the string.
                val oldTypeString = describeBounds(oldReturnType)
                val newTypeString = describeBounds(newReturnType)
                val message =
                    "${describe(new, capitalize = true)} has changed return type from $oldTypeString to $newTypeString"
                report(Issues.CHANGED_TYPE, new, message)
            }

            // Annotation methods
            if (
                new.containingClass().isAnnotationType() &&
                    old.containingClass().isAnnotationType() &&
                    new.defaultValue() != old.defaultValue()
            ) {
                val prevValue = old.defaultValue()
                val prevString =
                    if (prevValue.isEmpty()) {
                        "nothing"
                    } else {
                        prevValue
                    }

                val newValue = new.defaultValue()
                val newString =
                    if (newValue.isEmpty()) {
                        "nothing"
                    } else {
                        newValue
                    }
                val message =
                    "${describe(
                    new,
                    capitalize = true
                )} has changed value from $prevString to $newString"

                // Adding a default value to an annotation method is safe
                val annotationMethodAddingDefaultValue =
                    new.containingClass().isAnnotationType() && old.defaultValue().isEmpty()

                if (!annotationMethodAddingDefaultValue) {
                    report(Issues.CHANGED_VALUE, new, message)
                }
            }
        }

        // Check for changes in abstract, but only for regular classes; older signature files
        // sometimes describe interface methods as abstract
        if (new.containingClass().isClass()) {
            if (!oldModifiers.isAbstract() && newModifiers.isAbstract()) {
                report(
                    Issues.CHANGED_ABSTRACT,
                    new,
                    "${describe(new, capitalize = true)} has changed 'abstract' qualifier"
                )
            }
        }

        if (new.containingClass().isInterface() || new.containingClass().isAnnotationType()) {
            if (oldModifiers.isDefault() && newModifiers.isAbstract()) {
                report(
                    Issues.CHANGED_DEFAULT,
                    new,
                    "${describe(new, capitalize = true)} has changed 'default' qualifier"
                )
            }
        }

        if (oldModifiers.isNative() != newModifiers.isNative()) {
            report(
                Issues.CHANGED_NATIVE,
                new,
                "${describe(new, capitalize = true)} has changed 'native' qualifier"
            )
        }

        // Check changes to final modifier. But skip enums where it varies between signature files
        // and PSI
        // whether the methods are considered final.
        if (!new.containingClass().isEnum() && !oldModifiers.isStatic()) {
            // Compiler-generated methods vary in their 'final' qualifier between versions of
            // the compiler, so this check needs to be quite narrow. A change in 'final'
            // status of a method is only relevant if (a) the method is not declared 'static'
            // and (b) the method is not already inferred to be 'final' by virtue of its class.
            if (!old.isEffectivelyFinal() && new.isEffectivelyFinal()) {
                if (!old.containingClass().isExtensible()) {
                    report(
                        Issues.ADDED_FINAL_UNINSTANTIABLE,
                        new,
                        "${
                            describe(
                                new,
                                capitalize = true
                            )
                        } added 'final' qualifier but containing ${old.containingClass().describe()} was previously uninstantiable and therefore could not be subclassed"
                    )
                } else {
                    report(
                        Issues.ADDED_FINAL,
                        new,
                        "${describe(new, capitalize = true)} has added 'final' qualifier"
                    )
                }
            } else if (old.isEffectivelyFinal() && !new.isEffectivelyFinal()) {
                // Disallowed removing final: If an app inherits the class and starts overriding
                // the method it's going to crash on earlier versions where the method is final
                // It doesn't break compatibility in the strict sense, but does make it very
                // difficult to extend this method in practice.
                report(
                    Issues.REMOVED_FINAL_STRICT,
                    new,
                    "${describe(new, capitalize = true)} has removed 'final' qualifier"
                )
            }
        }

        if (oldModifiers.isStatic() != newModifiers.isStatic()) {
            report(
                Issues.CHANGED_STATIC,
                new,
                "${describe(new, capitalize = true)} has changed 'static' qualifier"
            )
        }

        val oldVisibility = oldModifiers.getVisibilityString()
        val newVisibility = newModifiers.getVisibilityString()
        if (oldVisibility != newVisibility) {
            // Only report issue if the change is a decrease in access; e.g. public -> protected
            if (!newModifiers.asAccessibleAs(oldModifiers)) {
                report(
                    Issues.CHANGED_SCOPE,
                    new,
                    "${describe(new, capitalize = true)} changed visibility from $oldVisibility to $newVisibility"
                )
            }
        }

        if (old.effectivelyDeprecated != new.effectivelyDeprecated) {
            report(
                Issues.CHANGED_DEPRECATED,
                new,
                "${describe(
                    new,
                    capitalize = true
                )} has changed deprecation state ${old.effectivelyDeprecated} --> ${new.effectivelyDeprecated}"
            )
        }

        /*
        // see JLS 3 13.4.20 "Adding or deleting a synchronized modifier of a method does not break "
        // "compatibility with existing binaries."
        if (oldModifiers.isSynchronized() != newModifiers.isSynchronized()) {
            report(
                Errors.CHANGED_SYNCHRONIZED, new,
                "${describe(
                    new,
                    capitalize = true
                )} has changed 'synchronized' qualifier from ${oldModifiers.isSynchronized()} to ${newModifiers.isSynchronized()}"
            )
        }
        */

        for (throwType in old.throwsTypes()) {
            // Get the throwable class, if none could be found then it is either because there is an
            // error in the codebase or the codebase is incomplete, either way reporting an error
            // would be unhelpful.
            val throwableClass = throwType.erasedClass ?: continue
            if (!new.throws(throwableClass.qualifiedName())) {
                // exclude 'throws' changes to finalize() overrides with no arguments
                if (old.name() != "finalize" || old.parameters().isNotEmpty()) {
                    report(
                        Issues.CHANGED_THROWS,
                        new,
                        "${describe(new, capitalize = true)} no longer throws exception ${throwType.description()}"
                    )
                }
            }
        }

        for (throwType in new.filteredThrowsTypes(filterReference)) {
            // Get the throwable class, if none could be found then it is either because there is an
            // error in the codebase or the codebase is incomplete, either way reporting an error
            // would be unhelpful.
            val throwableClass = throwType.erasedClass ?: continue
            if (!old.throws(throwableClass.qualifiedName())) {
                // exclude 'throws' changes to finalize() overrides with no arguments
                if (!(old.name() == "finalize" && old.parameters().isEmpty())) {
                    val message =
                        "${describe(new, capitalize = true)} added thrown exception ${throwType.description()}"
                    report(Issues.CHANGED_THROWS, new, message)
                }
            }
        }

        if (new.modifiers.isInline()) {
            val oldTypes = old.typeParameterList
            val newTypes = new.typeParameterList
            for (i in oldTypes.indices) {
                if (i == newTypes.size) {
                    break
                }
                if (newTypes[i].isReified() && !oldTypes[i].isReified()) {
                    val message =
                        "${
                            describe(
                                new,
                                capitalize = true
                            )
                        } made type variable ${newTypes[i].name()} reified: incompatible change"
                    report(Issues.ADDED_REIFIED, new, message)
                }
            }
        }
    }

    /**
     * Returns a string representation of the type, including the bounds for a variable type or
     * array of variable types.
     *
     * TODO(b/111253910): combine into [TypeItem.toTypeString]
     */
    private fun describeBounds(type: TypeItem): String {
        return when (type) {
            is ArrayTypeItem -> describeBounds(type.componentType) + "[]"
            is VariableTypeItem -> {
                type.name +
                    if (type.asTypeParameter.typeBounds().isEmpty()) {
                        " (extends java.lang.Object)"
                    } else {
                        " (extends ${type.asTypeParameter.typeBounds().joinToString(separator = " & ") { it.toTypeString() }})"
                    }
            }
            else -> type.toTypeString()
        }
    }

    override fun compare(old: FieldItem, new: FieldItem) {
        val oldModifiers = old.modifiers
        val newModifiers = new.modifiers

        if (!old.isEnumConstant()) {
            val oldType = old.type()
            val newType = new.type()
            if (oldType != newType) {
                val message =
                    "${describe(new, capitalize = true)} has changed type from $oldType to $newType"
                report(Issues.CHANGED_TYPE, new, message)
            } else if (!old.hasSameValue(new)) {
                val prevValue = old.initialValue()
                val prevString =
                    if (prevValue == null && !old.modifiers.isFinal()) {
                        "nothing/not constant"
                    } else {
                        prevValue
                    }

                val newValue = new.initialValue()
                val newString =
                    if (newValue is PsiField) {
                        newValue.containingClass?.qualifiedName + "." + newValue.name
                    } else {
                        newValue
                    }
                val message =
                    "${describe(
                    new,
                    capitalize = true
                )} has changed value from $prevString to $newString"

                report(Issues.CHANGED_VALUE, new, message)
            }
        }

        val oldVisibility = oldModifiers.getVisibilityString()
        val newVisibility = newModifiers.getVisibilityString()
        if (oldVisibility != newVisibility) {
            // Only report issue if the change is a decrease in access; e.g. public -> protected
            if (!newModifiers.asAccessibleAs(oldModifiers)) {
                report(
                    Issues.CHANGED_SCOPE,
                    new,
                    "${
                    describe(
                        new,
                        capitalize = true
                    )
                    } changed visibility from $oldVisibility to $newVisibility"
                )
            }
        }

        if (oldModifiers.isStatic() != newModifiers.isStatic()) {
            report(
                Issues.CHANGED_STATIC,
                new,
                "${describe(new, capitalize = true)} has changed 'static' qualifier"
            )
        }

        if (!oldModifiers.isFinal() && newModifiers.isFinal()) {
            report(
                Issues.ADDED_FINAL,
                new,
                "${describe(new, capitalize = true)} has added 'final' qualifier"
            )
        } else if (
            // Final can't be removed if field is static with compile-time constant
            oldModifiers.isFinal() &&
                !newModifiers.isFinal() &&
                oldModifiers.isStatic() &&
                old.initialValue() != null
        ) {
            report(
                Issues.REMOVED_FINAL,
                new,
                "${describe(new, capitalize = true)} has removed 'final' qualifier"
            )
        }

        if (oldModifiers.isVolatile() != newModifiers.isVolatile()) {
            report(
                Issues.CHANGED_VOLATILE,
                new,
                "${describe(new, capitalize = true)} has changed 'volatile' qualifier"
            )
        }

        if (old.effectivelyDeprecated != new.effectivelyDeprecated) {
            report(
                Issues.CHANGED_DEPRECATED,
                new,
                "${describe(
                    new,
                    capitalize = true
                )} has changed deprecation state ${old.effectivelyDeprecated} --> ${new.effectivelyDeprecated}"
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun handleAdded(issue: Issue, item: Item) {
        if (item.originallyHidden) {
            // This is an element which is hidden but is referenced from
            // some public API. This is an error, but some existing code
            // is doing this. This is not an API addition.
            return
        }

        if (!filterReference.test(item)) {
            // This item is something we weren't asked to verify
            return
        }

        var message = "Added ${describe(item)}"

        // Clarify error message for removed API to make it less ambiguous
        if (apiType == ApiType.REMOVED) {
            message += " to the removed API"
        } else if (options.allShowAnnotations.isNotEmpty()) {
            if (options.allShowAnnotations.matchesAnnotationName(ANDROID_SYSTEM_API)) {
                message += " to the system API"
            } else if (options.allShowAnnotations.matchesAnnotationName(ANDROID_TEST_API)) {
                message += " to the test API"
            }
        }

        report(issue, item, message)
    }

    private fun handleRemoved(issue: Issue, item: Item) {
        if (!item.emit) {
            // It's a stub; this can happen when analyzing partial APIs
            // such as a signature file for a library referencing types
            // from the upstream library dependencies.
            return
        }

        report(
            issue,
            item,
            "Removed ${if (item.effectivelyDeprecated) "deprecated " else ""}${describe(item)}"
        )
    }

    override fun added(new: PackageItem) {
        handleAdded(Issues.ADDED_PACKAGE, new)
    }

    override fun added(new: ClassItem) {
        val error =
            if (new.isInterface()) {
                Issues.ADDED_INTERFACE
            } else {
                Issues.ADDED_CLASS
            }
        handleAdded(error, new)
    }

    override fun added(new: MethodItem) {
        // *Overriding* methods from super classes that are outside the
        // API is OK (e.g. overriding toString() from java.lang.Object)
        val superMethods = new.superMethods()
        for (superMethod in superMethods) {
            if (superMethod.isFromClassPath()) {
                return
            }
        }

        // Do not fail if this "new" method is really an override of an
        // existing superclass method, but we should fail if this is overriding
        // an abstract method, because method's abstractness affects how users use it.
        // See if there's a member from inherited class
        val inherited =
            if (new.isConstructor()) {
                null
            } else {
                new.containingClass()
                    .findMethod(new, includeSuperClasses = true, includeInterfaces = false)
            }

        // In most cases it is not permitted to add a new method to an interface, even with a
        // default implementation because it could could create ambiguity if client code implements
        // two interfaces that each now define methods with the same signature.
        // Annotation types cannot implement other interfaces, however, so it is permitted to add
        // add new default methods to annotation types.
        if (new.containingClass().isAnnotationType() && new.hasDefaultValue()) {
            return
        }

        // It is ok to add a new abstract method to a class that has no public constructors
        if (
            new.containingClass().isClass() &&
                !new.containingClass().constructors().any { it.isPublic && !it.hidden } &&
                new.modifiers.isAbstract()
        ) {
            return
        }

        if (inherited == null || inherited == new || !inherited.modifiers.isAbstract()) {
            val error =
                when {
                    new.modifiers.isAbstract() -> Issues.ADDED_ABSTRACT_METHOD
                    new.containingClass().isInterface() ->
                        when {
                            new.modifiers.isStatic() -> Issues.ADDED_METHOD
                            new.modifiers.isDefault() -> {
                                // Hack to always mark added Kotlin interface methods as abstract
                                // until
                                // we properly support JVM default methods for Kotlin. This has to
                                // check
                                // if it's a PsiItem because TextItem doesn't support isKotlin.
                                //
                                // TODO(b/200077254): Remove Kotlin special case
                                if (new is PsiItem && new.isKotlin()) {
                                    Issues.ADDED_ABSTRACT_METHOD
                                } else {
                                    Issues.ADDED_METHOD
                                }
                            }
                            else -> Issues.ADDED_ABSTRACT_METHOD
                        }
                    else -> Issues.ADDED_METHOD
                }
            handleAdded(error, new)
        }
    }

    override fun added(new: FieldItem) {
        handleAdded(Issues.ADDED_FIELD, new)
    }

    override fun removed(old: PackageItem, from: Item?) {
        handleRemoved(Issues.REMOVED_PACKAGE, old)
    }

    override fun removed(old: ClassItem, from: Item?) {
        val error =
            when {
                old.isInterface() -> Issues.REMOVED_INTERFACE
                old.effectivelyDeprecated -> Issues.REMOVED_DEPRECATED_CLASS
                else -> Issues.REMOVED_CLASS
            }

        handleRemoved(error, old)
    }

    override fun removed(old: MethodItem, from: ClassItem?) {
        // See if there's a member from inherited class
        val inherited =
            if (old.isConstructor()) {
                null
            } else {
                // This can also return self, specially handled below
                from?.findMethod(
                    old,
                    includeSuperClasses = true,
                    includeInterfaces = from.isInterface()
                )
            }
        if (inherited == null || inherited != old && inherited.isHiddenOrRemoved()) {
            val error =
                if (old.effectivelyDeprecated) Issues.REMOVED_DEPRECATED_METHOD
                else Issues.REMOVED_METHOD
            handleRemoved(error, old)
        }
    }

    override fun removed(old: FieldItem, from: ClassItem?) {
        val inherited =
            from?.findField(
                old.name(),
                includeSuperClasses = true,
                includeInterfaces = from.isInterface()
            )
        if (inherited == null) {
            val error =
                if (old.effectivelyDeprecated) Issues.REMOVED_DEPRECATED_FIELD
                else Issues.REMOVED_FIELD
            handleRemoved(error, old)
        }
    }

    private fun report(issue: Issue, item: Item, message: String) {
        if (item.isCompatibilitySuppressed()) {
            // Long-term, we should consider allowing meta-annotations to specify a different
            // `configuration` so it can use a separate set of severities. For now, though, we'll
            // treat all issues for all unchecked items as `Severity.IGNORE`.
            return
        }
        if (
            reporter.report(issue, item, message) &&
                issueConfiguration.getSeverity(issue) == Severity.ERROR
        ) {
            foundProblems = true
        }
    }

    companion object {
        @Suppress("DEPRECATION")
        fun checkCompatibility(
            newCodebase: Codebase,
            oldCodebases: MergedCodebase,
            apiType: ApiType,
            baseApi: Codebase?,
            reporter: Reporter,
            issueConfiguration: IssueConfiguration,
        ) {
            val filter =
                apiType
                    .getReferenceFilter(options.apiPredicateConfig)
                    .or(apiType.getEmitFilter(options.apiPredicateConfig))
                    .or(ApiType.PUBLIC_API.getReferenceFilter(options.apiPredicateConfig))
                    .or(ApiType.PUBLIC_API.getEmitFilter(options.apiPredicateConfig))

            val checker =
                CompatibilityCheck(
                    filter,
                    apiType,
                    reporter,
                    issueConfiguration,
                )

            val oldFullCodebase =
                if (options.showUnannotated && apiType == ApiType.PUBLIC_API) {
                    baseApi?.let { MergedCodebase(oldCodebases.children + baseApi) } ?: oldCodebases
                } else {
                    // To avoid issues with partial oldCodeBase we fill gaps with newCodebase, the
                    // first parameter is master, so we don't change values of oldCodeBase
                    MergedCodebase(oldCodebases.children + newCodebase)
                }
            val newFullCodebase = MergedCodebase(listOfNotNull(newCodebase, baseApi))

            CodebaseComparator(
                    apiVisitorConfig = @Suppress("DEPRECATION") options.apiVisitorConfig,
                )
                .compare(checker, oldFullCodebase, newFullCodebase, filter)

            val message =
                "Found compatibility problems checking " +
                    "the ${apiType.displayName} API (${newCodebase.location}) against the API in ${oldCodebases.children.last().location}"

            if (checker.foundProblems) {
                throw MetalavaCliException(exitCode = -1, stderr = message)
            }
        }
    }
}
