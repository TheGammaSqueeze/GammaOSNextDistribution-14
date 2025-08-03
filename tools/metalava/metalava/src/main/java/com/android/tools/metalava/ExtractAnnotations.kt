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

package com.android.tools.metalava

import com.android.tools.lint.annotations.Extractor
import com.android.tools.metalava.model.ANDROIDX_ANNOTATION_PREFIX
import com.android.tools.metalava.model.ANDROID_ANNOTATION_PREFIX
import com.android.tools.metalava.model.ANNOTATION_ATTR_VALUE
import com.android.tools.metalava.model.AnnotationItem
import com.android.tools.metalava.model.AnnotationRetention
import com.android.tools.metalava.model.AnnotationTarget
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.Codebase
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.JAVA_LANG_PREFIX
import com.android.tools.metalava.model.MemberItem
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.findAnnotation
import com.android.tools.metalava.model.psi.CodePrinter
import com.android.tools.metalava.model.psi.PsiAnnotationItem
import com.android.tools.metalava.model.psi.PsiClassItem
import com.android.tools.metalava.model.psi.PsiMethodItem
import com.android.tools.metalava.model.psi.UAnnotationItem
import com.android.tools.metalava.model.psi.report
import com.android.tools.metalava.model.visitors.ApiVisitor
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import com.google.common.xml.XmlEscapers
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiNameValuePair
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.PsiReturnStatement
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.StringWriter
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.text.Charsets.UTF_8
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.USimpleNameReferenceExpression
import org.jetbrains.uast.UastEmptyExpression
import org.jetbrains.uast.UastFacade
import org.jetbrains.uast.toUElement

// Like the tools/base Extractor class, but limited to our own (mapped) AnnotationItems,
// and only those with source retention (and in particular right now that just means the
// typedef annotations.)
class ExtractAnnotations(
    private val codebase: Codebase,
    private val reporter: Reporter,
    private val outputFile: File,
) : ApiVisitor() {
    // Used linked hash map for order such that we always emit parameters after their surrounding
    // method etc
    private val packageToAnnotationPairs =
        LinkedHashMap<PackageItem, MutableList<Pair<Item, AnnotationHolder>>>()

    private data class AnnotationHolder(
        val annotationClass: ClassItem?,
        val annotationItem: AnnotationItem,
        val uAnnotation: UAnnotation?
    )

    private val fieldNamePrinter =
        CodePrinter(
            codebase = codebase,
            reporter = reporter,
            filterReference = filterReference,
            inlineFieldValues = false,
            skipUnknown = true,
        )

    private val fieldValuePrinter =
        CodePrinter(
            codebase = codebase,
            reporter = reporter,
            filterReference = filterReference,
            inlineFieldValues = true,
            skipUnknown = true,
        )

    private val classToAnnotationHolder = mutableMapOf<String, AnnotationHolder>()

    fun extractAnnotations() {
        codebase.accept(this)

        // Write external annotations
        FileOutputStream(outputFile).use { fileOutputStream ->
            JarOutputStream(BufferedOutputStream(fileOutputStream)).use { zos ->
                val sortedPackages =
                    packageToAnnotationPairs.keys
                        .asSequence()
                        .sortedBy { it.qualifiedName() }
                        .toList()

                for (pkg in sortedPackages) {
                    // Note: Using / rather than File.separator: jar lib requires it
                    val name = pkg.qualifiedName().replace('.', '/') + "/annotations.xml"

                    val outEntry = JarEntry(name)
                    outEntry.time = 0
                    zos.putNextEntry(outEntry)

                    val pairs = packageToAnnotationPairs[pkg] ?: continue

                    // Ensure stable output
                    if (pairs.size > 1) {
                        pairs.sortBy { it.first.getExternalAnnotationSignature() }
                    }

                    StringPrintWriter.create().use { writer ->
                        writer.println("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root>")

                        var open = false
                        var prev: Item? = null
                        for ((item, annotation) in pairs) {
                            if (item != prev) {
                                if (open) {
                                    writer.print("  </item>")
                                    writer.println()
                                }
                                writer.print("  <item name=\"")
                                writer.print(item.getExternalAnnotationSignature())
                                writer.println("\">")
                                open = true
                            }
                            prev = item

                            writeAnnotation(writer, item, annotation)
                        }
                        if (open) {
                            writer.print("  </item>")
                            writer.println()
                        }
                        writer.println("</root>\n")
                        writer.close()
                        val bytes = writer.contents.toByteArray(UTF_8)
                        zos.write(bytes)
                        zos.closeEntry()
                    }
                }
            }
        }
    }

    private fun addItem(item: Item, annotation: AnnotationHolder) {
        val pkg =
            when (item) {
                is MemberItem -> item.containingClass().containingPackage()
                is ParameterItem -> item.containingMethod().containingClass().containingPackage()
                else -> return
            }

        val list =
            packageToAnnotationPairs[pkg]
                ?: run {
                    val new = mutableListOf<Pair<Item, AnnotationHolder>>()
                    packageToAnnotationPairs[pkg] = new
                    new
                }
        list.add(Pair(item, annotation))
    }

    override fun visitField(field: FieldItem) {
        checkItem(field)
    }

    override fun visitMethod(method: MethodItem) {
        checkItem(method)
    }

    override fun visitParameter(parameter: ParameterItem) {
        checkItem(parameter)
    }

    /** For a given item, extract the relevant annotations for that item */
    private fun checkItem(item: Item) {
        for (annotation in item.modifiers.annotations()) {
            val qualifiedName = annotation.qualifiedName ?: continue
            if (
                qualifiedName.startsWith(JAVA_LANG_PREFIX) ||
                    qualifiedName.startsWith(ANDROIDX_ANNOTATION_PREFIX) ||
                    qualifiedName.startsWith(ANDROID_ANNOTATION_PREFIX)
            ) {
                if (annotation.isTypeDefAnnotation()) {
                    // Imported typedef
                    addItem(item, AnnotationHolder(null, annotation, null))
                } else if (
                    annotation.targets.contains(AnnotationTarget.EXTERNAL_ANNOTATIONS_FILE)
                ) {
                    addItem(item, AnnotationHolder(null, annotation, null))
                }

                continue
            } else if (
                qualifiedName.startsWith(ORG_JETBRAINS_ANNOTATIONS_PREFIX) ||
                    qualifiedName.startsWith(ORG_INTELLIJ_LANG_ANNOTATIONS_PREFIX)
            ) {
                // Externally merged metadata, like @Contract and @Language
                addItem(item, AnnotationHolder(null, annotation, null))
                continue
            }

            val typeDefClass = annotation.resolve() ?: continue
            val className = typeDefClass.qualifiedName()
            if (typeDefClass.isAnnotationType()) {
                val cached = classToAnnotationHolder[className]
                if (cached != null) {
                    addItem(item, cached)
                    continue
                }

                val typeDefAnnotation =
                    typeDefClass.modifiers.findAnnotation(AnnotationItem::isTypeDefAnnotation)
                if (typeDefAnnotation != null) {
                    // Make sure it has the right retention
                    if (typeDefClass.getRetention() != AnnotationRetention.SOURCE) {
                        reporter.report(
                            Issues.ANNOTATION_EXTRACTION,
                            typeDefClass,
                            "This typedef annotation class should have @Retention(RetentionPolicy.SOURCE)"
                        )
                    }

                    if (filterEmit.test(typeDefClass)) {
                        reporter.report(
                            Issues.ANNOTATION_EXTRACTION,
                            typeDefClass,
                            "This typedef annotation class should be marked @hide or should not be marked public"
                        )
                    }

                    val result =
                        if (
                            typeDefAnnotation is PsiAnnotationItem && typeDefClass is PsiClassItem
                        ) {
                            AnnotationHolder(
                                typeDefClass,
                                typeDefAnnotation,
                                UastFacade.convertElement(
                                    typeDefAnnotation.psiAnnotation,
                                    null,
                                    UAnnotation::class.java
                                ) as UAnnotation
                            )
                        } else if (
                            typeDefAnnotation is UAnnotationItem && typeDefClass is PsiClassItem
                        ) {
                            AnnotationHolder(
                                typeDefClass,
                                typeDefAnnotation,
                                typeDefAnnotation.uAnnotation
                            )
                        } else {
                            continue
                        }

                    classToAnnotationHolder[className] = result
                    addItem(item, result)

                    if (
                        item is PsiMethodItem &&
                            result.uAnnotation != null &&
                            !reporter.isSuppressed(Issues.RETURNING_UNEXPECTED_CONSTANT)
                    ) {
                        verifyReturnedConstants(item, result.uAnnotation, result, className)
                    }
                    continue
                }
            }
        }
    }

    /**
     * Given a method whose return value is annotated with a typedef, runs checks on the typedef and
     * flags any returned constants not in the list.
     */
    private fun verifyReturnedConstants(
        item: PsiMethodItem,
        uAnnotation: UAnnotation,
        result: AnnotationHolder,
        className: String
    ) {
        val method = item.psiMethod
        if (method.body != null) {
            method.body?.accept(
                object : JavaRecursiveElementVisitor() {
                    private var constants: List<String>? = null

                    override fun visitReturnStatement(statement: PsiReturnStatement) {
                        val value = statement.returnValue
                        if (value is PsiReferenceExpression) {
                            val resolved = value.resolve() as? PsiField ?: return
                            val modifiers = resolved.modifierList ?: return
                            if (
                                modifiers.hasModifierProperty(PsiModifier.STATIC) &&
                                    modifiers.hasModifierProperty(PsiModifier.FINAL)
                            ) {
                                if (resolved.type.arrayDimensions > 0) {
                                    return
                                }
                                val name = resolved.name

                                // Make sure this is one of the allowed annotations
                                val names =
                                    constants
                                        ?: run {
                                            constants = computeValidConstantNames(uAnnotation)
                                            constants!!
                                        }
                                if (names.isNotEmpty() && !names.contains(name)) {
                                    val expected = names.joinToString { it }
                                    reporter.report(
                                        Issues.RETURNING_UNEXPECTED_CONSTANT,
                                        value as PsiElement,
                                        "Returning unexpected constant $name; is @${result.annotationClass?.simpleName()
                                        ?: className} missing this constant? Expected one of $expected"
                                    )
                                }
                            }
                        }
                    }
                }
            )
        }
    }

    private fun computeValidConstantNames(annotation: UAnnotation): List<String> {
        val constants = annotation.findAttributeValue(ANNOTATION_ATTR_VALUE) ?: return emptyList()
        if (constants is UCallExpression) {
            return constants.valueArguments
                .mapNotNull { (it as? USimpleNameReferenceExpression)?.identifier }
                .toList()
        }

        return emptyList()
    }

    /**
     * A writer which stores all its contents into a string and has the ability to mark a certain
     * freeze point and then reset back to it
     */
    private class StringPrintWriter constructor(private val stringWriter: StringWriter) :
        PrintWriter(stringWriter) {
        private var mark: Int = 0

        val contents: String
            get() = stringWriter.toString()

        fun mark() {
            flush()
            mark = stringWriter.buffer.length
        }

        fun reset() {
            stringWriter.buffer.setLength(mark)
        }

        override fun toString(): String {
            return contents
        }

        companion object {
            fun create(): StringPrintWriter {
                return StringPrintWriter(StringWriter(1000))
            }
        }
    }

    private fun escapeXml(unescaped: String): String {
        return XmlEscapers.xmlAttributeEscaper().escape(unescaped)
    }

    private fun Item.getExternalAnnotationSignature(): String? {
        when (this) {
            is PackageItem -> {
                return escapeXml(qualifiedName())
            }
            is ClassItem -> {
                return escapeXml(qualifiedName())
            }
            is MethodItem -> {
                val sb = StringBuilder(100)
                sb.append(escapeXml(containingClass().qualifiedName()))
                sb.append(' ')

                if (isConstructor()) {
                    sb.append(escapeXml(containingClass().simpleName()))
                } else {
                    sb.append(escapeXml(returnType().toTypeString()))
                    sb.append(' ')
                    sb.append(escapeXml(name()))
                }

                sb.append('(')

                // The signature must match *exactly* the formatting used by IDEA,
                // since it looks up external annotations in a map by this key.
                // Therefore, it is vital that the parameter list uses exactly one
                // space after each comma between parameters, and *no* spaces between
                // generics variables, e.g. foo(Map<A,B>, int)
                var i = 0
                val parameterList = parameters()
                val n = parameterList.size
                while (i < n) {
                    if (i > 0) {
                        sb.append(',').append(' ')
                    }
                    val type =
                        parameterList[i]
                            .type()
                            .toTypeString()
                            .replace(" ", "")
                            .replace("?extends", "? extends ")
                            .replace("?super", "? super ")
                    sb.append(escapeXml(type))
                    i++
                }
                sb.append(')')
                return sb.toString()
            }
            is FieldItem -> {
                return escapeXml(containingClass().qualifiedName()) + " " + name()
            }
            is ParameterItem -> {
                return containingMethod().getExternalAnnotationSignature() +
                    " " +
                    this.parameterIndex
            }
        }

        return null
    }

    private fun writeAnnotation(
        writer: StringPrintWriter,
        item: Item,
        annotationHolder: AnnotationHolder
    ) {
        val annotationItem = annotationHolder.annotationItem
        val uAnnotation =
            annotationHolder.uAnnotation
                ?: when (annotationItem) {
                    is UAnnotationItem -> annotationItem.uAnnotation
                    is PsiAnnotationItem ->
                        // Imported annotation
                        annotationItem.psiAnnotation.toUElement(UAnnotation::class.java) ?: return
                    else -> return
                }
        val qualifiedName = annotationItem.qualifiedName

        writer.mark()
        writer.print("    <annotation name=\"")
        writer.print(qualifiedName)

        var attributes = uAnnotation.attributeValues
        if (attributes.isEmpty()) {
            writer.print("\"/>")
            writer.println()
            return
        }

        writer.print("\">")
        writer.println()

        // noinspection PointlessBooleanExpression,ConstantConditions
        if (sortAnnotations) {
            // Ensure that the value attribute is written first
            attributes =
                attributes.sortedWith(
                    compareBy(
                        { (it.name ?: ANNOTATION_ATTR_VALUE) != ANNOTATION_ATTR_VALUE },
                        { it.name }
                    )
                )
        }

        if (attributes.size == 1 && Extractor.REQUIRES_PERMISSION.isPrefix(qualifiedName, true)) {
            val expression = attributes[0].expression
            if (expression is UAnnotation) {
                // The external annotations format does not allow for nested/complex annotations.
                // However, these special annotations (@RequiresPermission.Read,
                // @RequiresPermission.Write, etc) are known to only be simple containers with a
                // single permission child, so instead we "inline" the content:
                //  @Read(@RequiresPermission(allOf={P1,P2},conditional=true)
                //     =>
                //      @RequiresPermission.Read(allOf({P1,P2},conditional=true)
                // That's setting attributes that don't actually exist on the container permission,
                // but we'll counteract that on the read-annotations side.
                val annotation = expression as UAnnotation
                attributes = annotation.attributeValues
            } else if (expression is UCallExpression) {
                val nestedPsi = expression.sourcePsi as? PsiAnnotation
                val annotation =
                    nestedPsi?.let {
                        UastFacade.convertElement(it, expression, UAnnotation::class.java)
                    } as? UAnnotation
                annotation?.attributeValues?.let { attributes = it }
            } else if (
                expression is UastEmptyExpression && attributes[0].sourcePsi is PsiNameValuePair
            ) {
                val memberValue = (attributes[0].sourcePsi as PsiNameValuePair).value
                if (memberValue is PsiAnnotation) {
                    val annotation = memberValue.toUElement(UAnnotation::class.java)
                    annotation?.attributeValues?.let { attributes = it }
                }
            }
        }

        val inlineConstants = isInlinedConstant(annotationItem)
        var empty = true
        for (pair in attributes) {
            val expression = pair.expression
            val value = attributeString(expression, inlineConstants) ?: continue
            empty = false
            var name = pair.name
            if (name == null) {
                name = ANNOTATION_ATTR_VALUE // default name
            }

            // Platform typedef annotations declare prefix/suffix attributes for historical reasons
            // and they are no longer necessary; they should also not be part of the extracted
            // metadata.
            if (("prefix" == name || "suffix" == name) && annotationItem.isTypeDefAnnotation()) {
                reporter.report(
                    Issues.SUPERFLUOUS_PREFIX,
                    item,
                    "Superfluous $name attribute on typedef"
                )
                continue
            }

            writer.print("      <val name=\"")
            writer.print(name)
            writer.print("\" val=\"")
            writer.print(escapeXml(value))
            writer.println("\" />")
        }

        if (empty && attributes.isNotEmpty()) {
            // All items were filtered out: don't write the annotation at all
            writer.reset()
            return
        }

        writer.println("    </annotation>")
    }

    private fun attributeString(value: UExpression?, inlineConstants: Boolean): String? {
        val printer =
            if (inlineConstants) {
                fieldValuePrinter
            } else {
                fieldNamePrinter
            }

        return printer.toSourceString(value)
    }

    private fun isInlinedConstant(annotationItem: AnnotationItem): Boolean {
        return annotationItem.isTypeDefAnnotation()
    }

    /** Whether to sort annotation attributes (otherwise their declaration order is used) */
    private val sortAnnotations: Boolean = true

    companion object {
        private const val SOURCE = "SOURCE"
    }
}
