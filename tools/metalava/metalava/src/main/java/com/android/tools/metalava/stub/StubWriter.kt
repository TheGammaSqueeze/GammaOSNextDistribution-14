/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tools.metalava.stub

import com.android.tools.metalava.ApiPredicate
import com.android.tools.metalava.FilterPredicate
import com.android.tools.metalava.model.BaseItemVisitor
import com.android.tools.metalava.model.ClassItem
import com.android.tools.metalava.model.ConstructorItem
import com.android.tools.metalava.model.FieldItem
import com.android.tools.metalava.model.Item
import com.android.tools.metalava.model.Language
import com.android.tools.metalava.model.MethodItem
import com.android.tools.metalava.model.ModifierListWriter
import com.android.tools.metalava.model.PackageItem
import com.android.tools.metalava.model.psi.trimDocIndent
import com.android.tools.metalava.model.visitors.ApiVisitor
import com.android.tools.metalava.reporter.Issues
import com.android.tools.metalava.reporter.Reporter
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.PrintWriter
import java.io.Writer

internal class StubWriter(
    private val stubsDir: File,
    private val generateAnnotations: Boolean = false,
    private val preFiltered: Boolean = true,
    private val docStubs: Boolean,
    private val reporter: Reporter,
    private val config: StubWriterConfig,
) :
    ApiVisitor(
        visitConstructorsAsMethods = false,
        nestInnerClasses = true,
        inlineInheritedFields = true,
        // Methods are by default sorted in source order in stubs, to encourage methods
        // that are near each other in the source to show up near each other in the documentation
        methodComparator = MethodItem.sourceOrderComparator,
        filterEmit = FilterPredicate(apiPredicate(docStubs, config)),
        filterReference = apiPredicate(docStubs, config),
        includeEmptyOuterClasses = true,
        config = config.apiVisitorConfig,
    ) {

    override fun visitPackage(pkg: PackageItem) {
        getPackageDir(pkg, create = true)

        writePackageInfo(pkg)

        if (docStubs) {
            pkg.overviewDocumentation?.let { writeDocOverview(pkg, it) }
        }
    }

    fun writeDocOverview(pkg: PackageItem, content: String) {
        if (content.isBlank()) {
            return
        }

        val sourceFile = File(getPackageDir(pkg), "overview.html")
        val overviewWriter =
            try {
                PrintWriter(BufferedWriter(FileWriter(sourceFile)))
            } catch (e: IOException) {
                reporter.report(Issues.IO_ERROR, sourceFile, "Cannot open file for write.")
                return
            }

        // Should we include this in our stub list?
        //     startFile(sourceFile)

        overviewWriter.println(content)
        overviewWriter.flush()
        overviewWriter.close()
    }

    private fun writePackageInfo(pkg: PackageItem) {
        val annotations = pkg.modifiers.annotations()
        val writeAnnotations = annotations.isNotEmpty() && generateAnnotations
        val writeDocumentation =
            config.includeDocumentationInStubs && pkg.documentation.isNotBlank()
        if (writeAnnotations || writeDocumentation) {
            val sourceFile = File(getPackageDir(pkg), "package-info.java")
            val packageInfoWriter =
                try {
                    PrintWriter(BufferedWriter(FileWriter(sourceFile)))
                } catch (e: IOException) {
                    reporter.report(Issues.IO_ERROR, sourceFile, "Cannot open file for write.")
                    return
                }

            appendDocumentation(pkg, packageInfoWriter, config)

            if (annotations.isNotEmpty()) {
                // Write the modifier list even though the package info does not actually have
                // modifiers as that will write the annotations which it does have and ignore the
                // modifiers.
                ModifierListWriter.forStubs(
                        writer = packageInfoWriter,
                        docStubs = docStubs,
                    )
                    .write(pkg)
            }
            packageInfoWriter.println("package ${pkg.qualifiedName()};")

            packageInfoWriter.flush()
            packageInfoWriter.close()
        }
    }

    private fun getPackageDir(packageItem: PackageItem, create: Boolean = true): File {
        val relative = packageItem.qualifiedName().replace('.', File.separatorChar)
        val dir = File(stubsDir, relative)
        if (create && !dir.isDirectory) {
            val ok = dir.mkdirs()
            if (!ok) {
                throw IOException("Could not create $dir")
            }
        }

        return dir
    }

    private fun getClassFile(classItem: ClassItem): File {
        assert(classItem.containingClass() == null) { "Should only be called on top level classes" }
        val packageDir = getPackageDir(classItem.containingPackage())

        // Kotlin From-text stub generation is not supported.
        // This method will raise an error if
        // config.kotlinStubs == true and classItem is TextClassItem.
        return if (config.kotlinStubs && classItem.isKotlin()) {
            File(packageDir, "${classItem.simpleName()}.kt")
        } else {
            File(packageDir, "${classItem.simpleName()}.java")
        }
    }

    /**
     * Between top level class files the [textWriter] field doesn't point to a real file; it points
     * to this writer, which redirects to the error output. Nothing should be written to the writer
     * at that time.
     */
    private var errorTextWriter =
        PrintWriter(
            object : Writer() {
                override fun close() {
                    throw IllegalStateException(
                        "Attempt to close 'textWriter' outside top level class"
                    )
                }

                override fun flush() {
                    throw IllegalStateException(
                        "Attempt to flush 'textWriter' outside top level class"
                    )
                }

                override fun write(cbuf: CharArray, off: Int, len: Int) {
                    throw IllegalStateException(
                        "Attempt to write to 'textWriter' outside top level class\n'${String(cbuf, off, len)}'"
                    )
                }
            }
        )

    /** The writer to write the stubs file to */
    private var textWriter: PrintWriter = errorTextWriter

    private var stubWriter: BaseItemVisitor? = null

    override fun visitClass(cls: ClassItem) {
        if (cls.isTopLevelClass()) {
            val sourceFile = getClassFile(cls)
            textWriter =
                try {
                    PrintWriter(BufferedWriter(FileWriter(sourceFile)))
                } catch (e: IOException) {
                    reporter.report(Issues.IO_ERROR, sourceFile, "Cannot open file for write.")
                    errorTextWriter
                }

            val kotlin = config.kotlinStubs && cls.isKotlin()
            val language = if (kotlin) Language.KOTLIN else Language.JAVA

            val modifierListWriter =
                ModifierListWriter.forStubs(
                    writer = textWriter,
                    docStubs = docStubs,
                    runtimeAnnotationsOnly = !generateAnnotations,
                    language = language,
                )

            stubWriter =
                if (kotlin) {
                    KotlinStubWriter(
                        textWriter,
                        modifierListWriter,
                        filterReference,
                        preFiltered,
                        config,
                    )
                } else {
                    JavaStubWriter(
                        textWriter,
                        modifierListWriter,
                        filterEmit,
                        filterReference,
                        preFiltered,
                        config,
                    )
                }

            // Copyright statements from the original file?
            cls.getSourceFile()?.getHeaderComments()?.let { textWriter.println(it) }
        }
        stubWriter?.visitClass(cls)
    }

    override fun afterVisitClass(cls: ClassItem) {
        stubWriter?.afterVisitClass(cls)

        if (cls.isTopLevelClass()) {
            textWriter.flush()
            textWriter.close()
            textWriter = errorTextWriter
            stubWriter = null
        }
    }

    override fun visitConstructor(constructor: ConstructorItem) {
        stubWriter?.visitConstructor(constructor)
    }

    override fun afterVisitConstructor(constructor: ConstructorItem) {
        stubWriter?.afterVisitConstructor(constructor)
    }

    override fun visitMethod(method: MethodItem) {
        stubWriter?.visitMethod(method)
    }

    override fun afterVisitMethod(method: MethodItem) {
        stubWriter?.afterVisitMethod(method)
    }

    override fun visitField(field: FieldItem) {
        stubWriter?.visitField(field)
    }

    override fun afterVisitField(field: FieldItem) {
        stubWriter?.afterVisitField(field)
    }
}

private fun apiPredicate(docStubs: Boolean, config: StubWriterConfig) =
    ApiPredicate(
        includeDocOnly = docStubs,
        config = config.apiVisitorConfig.apiPredicateConfig.copy(ignoreShown = true),
    )

internal fun appendDocumentation(item: Item, writer: PrintWriter, config: StubWriterConfig) {
    if (config.includeDocumentationInStubs) {
        val documentation = item.fullyQualifiedDocumentation()
        if (documentation.isNotBlank()) {
            val trimmed = trimDocIndent(documentation)
            writer.println(trimmed)
            writer.println()
        }
    }
}
