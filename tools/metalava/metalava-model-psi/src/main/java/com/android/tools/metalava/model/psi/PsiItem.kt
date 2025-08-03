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

import com.android.tools.metalava.model.DefaultItem
import com.android.tools.metalava.model.DefaultModifierList
import com.android.tools.metalava.model.ParameterItem
import com.android.tools.metalava.model.source.utils.LazyDelegate
import com.android.tools.metalava.reporter.FileLocation
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.uast.UElement
import org.jetbrains.uast.sourcePsiElement

abstract class PsiItem
internal constructor(
    override val codebase: PsiBasedCodebase,
    element: PsiElement,
    modifiers: DefaultModifierList,
    override var documentation: String,
) : DefaultItem(modifiers) {

    @Suppress(
        "LeakingThis"
    ) // Documentation can change, but we don't want to pick up subsequent @docOnly mutations
    override var docOnly = documentation.contains("@doconly")
    @Suppress("LeakingThis") override var removed = documentation.contains("@removed")

    /** The source PSI provided by UAST */
    internal val sourcePsi: PsiElement? = (element as? UElement)?.sourcePsi

    override var originallyHidden: Boolean by LazyDelegate {
        documentation.contains('@') &&
            (documentation.contains("@hide") ||
                documentation.contains("@pending") ||
                // KDoc:
                documentation.contains("@suppress")) || hasHideAnnotation()
    }

    override var hidden: Boolean by LazyDelegate { originallyHidden && !hasShowAnnotation() }

    /** Returns the PSI element for this item */
    abstract fun psi(): PsiElement

    override val fileLocation: FileLocation
        get() = PsiLocationProvider.elementToFileLocation(psi())

    override fun isFromClassPath(): Boolean {
        return codebase.fromClasspath || containingClass()?.isFromClassPath() ?: false
    }

    override fun findTagDocumentation(tag: String, value: String?): String? {
        if (psi() is PsiCompiledElement) {
            return null
        }
        if (documentation.isBlank()) {
            return null
        }

        // We can't just use element.docComment here because we may have modified
        // the comment and then the comment snapshot in PSI isn't up to date with our
        // latest changes
        val docComment = codebase.getComment(documentation)
        val tagComment =
            if (value == null) {
                docComment.findTagByName(tag)
            } else {
                docComment.findTagsByName(tag).firstOrNull { it.valueElement?.text == value }
            }

        if (tagComment == null) {
            return null
        }

        val text = tagComment.text
        // Trim trailing next line (javadoc *)
        var index = text.length - 1
        while (index > 0) {
            val c = text[index]
            if (!(c == '*' || c.isWhitespace())) {
                break
            }
            index--
        }
        index++
        if (index < text.length) {
            return text.substring(0, index)
        } else {
            return text
        }
    }

    override fun appendDocumentation(comment: String, tagSection: String?, append: Boolean) {
        if (comment.isBlank()) {
            return
        }

        // TODO: Figure out if an annotation should go on the return value, or on the method.
        // For example; threading: on the method, range: on the return value.
        // TODO: Find a good way to add or append to a given tag (@param <something>, @return, etc)

        if (this is ParameterItem) {
            // For parameters, the documentation goes into the surrounding method's documentation!
            // Find the right parameter location!
            val parameterName = name()
            val target = containingMethod()
            target.appendDocumentation(comment, parameterName)
            return
        }

        // Micro-optimization: we're very often going to be merging @apiSince and to a lesser
        // extend @deprecatedSince into existing comments, since we're flagging every single
        // public API. Normally merging into documentation has to be done carefully, since
        // there could be existing versions of the tag we have to append to, and some parts
        // of the comment needs to be present in certain places. For example, you can't
        // just append to the description of a method by inserting something right before "*/"
        // since you could be appending to a javadoc tag like @return.
        //
        // However, for @apiSince and @deprecatedSince specifically, in addition to being frequent,
        // they will (a) never appear in existing docs, and (b) they're separate tags, which means
        // it's safe to append them at the end. So we'll special case these two tags here, to
        // help speed up the builds since these tags are inserted 30,000+ times for each framework
        // API target (there are many), and each time would have involved constructing a full
        // javadoc
        // AST with lexical tokens using IntelliJ's javadoc parsing APIs. Instead, we'll just
        // do some simple string heuristics.
        if (
            tagSection == "@apiSince" ||
                tagSection == "@deprecatedSince" ||
                tagSection == "@sdkExtSince"
        ) {
            documentation = addUniqueTag(documentation, tagSection, comment)
            return
        }

        documentation = mergeDocumentation(documentation, psi(), comment.trim(), tagSection, append)
    }

    private fun addUniqueTag(
        documentation: String,
        tagSection: String,
        commentLine: String
    ): String {
        assert(commentLine.indexOf('\n') == -1) // Not meant for multi-line comments

        if (documentation.isBlank()) {
            return "/** $tagSection $commentLine */"
        }

        // Already single line?
        if (documentation.indexOf('\n') == -1) {
            val end = documentation.lastIndexOf("*/")
            return "/**\n *" +
                documentation.substring(3, end) +
                "\n * $tagSection $commentLine\n */"
        }

        var end = documentation.lastIndexOf("*/")
        while (end > 0 && documentation[end - 1].isWhitespace() && documentation[end - 1] != '\n') {
            end--
        }
        // The comment ends with:
        // * some comment here */
        val insertNewLine: Boolean = documentation[end - 1] != '\n'

        val indent: String
        var linePrefix = ""
        val secondLine = documentation.indexOf('\n')
        if (secondLine == -1) {
            // Single line comment
            indent = "\n * "
        } else {
            val indentStart = secondLine + 1
            var indentEnd = indentStart
            while (indentEnd < documentation.length) {
                if (!documentation[indentEnd].isWhitespace()) {
                    break
                }
                indentEnd++
            }
            indent = documentation.substring(indentStart, indentEnd)
            // TODO: If it starts with "* " follow that
            if (documentation.startsWith("* ", indentEnd)) {
                linePrefix = "* "
            }
        }
        return documentation.substring(0, end) +
            (if (insertNewLine) "\n" else "") +
            indent +
            linePrefix +
            tagSection +
            " " +
            commentLine +
            "\n" +
            indent +
            " */"
    }

    override fun fullyQualifiedDocumentation(): String {
        return fullyQualifiedDocumentation(documentation)
    }

    override fun fullyQualifiedDocumentation(documentation: String): String {
        return codebase.docQualifier.toFullyQualifiedDocumentation(this, documentation)
    }

    /** Finish initialization of the item */
    open fun finishInitialization() {}

    override fun isJava(): Boolean {
        return !isKotlin()
    }

    override fun isKotlin(): Boolean {
        return psi().isKotlin()
    }

    companion object {
        internal fun javadoc(element: PsiElement): String {
            if (element is PsiCompiledElement) {
                return ""
            }

            if (element is KtDeclaration) {
                return element.docComment?.text.orEmpty()
            }

            if (element is UElement) {
                val comments = element.comments
                if (comments.isNotEmpty()) {
                    val sb = StringBuilder()
                    comments.asSequence().joinTo(buffer = sb, separator = "\n") { it.text }
                    return sb.toString()
                } else {
                    // Temporary workaround: UAST seems to not return document nodes
                    // https://youtrack.jetbrains.com/issue/KT-22135
                    val first = element.sourcePsiElement?.firstChild
                    if (first is KDoc) {
                        return first.text
                    }
                }
            }

            if (element is PsiDocCommentOwner && element.docComment !is PsiCompiledElement) {
                return element.docComment?.text ?: ""
            }

            return ""
        }

        internal fun modifiers(
            codebase: PsiBasedCodebase,
            element: PsiModifierListOwner,
            documentation: String? = null,
        ): DefaultModifierList {
            return PsiModifierItem.create(codebase, element, documentation)
        }
    }
}

/** Check whether a [PsiElement] is Kotlin or not. */
fun PsiElement.isKotlin(): Boolean {
    return language === KotlinLanguage.INSTANCE
}
