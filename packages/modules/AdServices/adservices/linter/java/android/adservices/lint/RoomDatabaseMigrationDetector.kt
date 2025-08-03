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

package android.adservices.lint

import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.ConstantEvaluator
import com.android.tools.lint.detector.api.Context
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import com.android.tools.lint.detector.api.UastLintUtils.Companion.tryResolveUDeclaration
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UField
import org.jetbrains.uast.UNamedExpression
import org.jetbrains.uast.UastVisibility

class RoomDatabaseMigrationDetector : Detector(), SourceCodeScanner {

    private val databaseList: MutableList<UClass> = mutableListOf()
    private var registrationClassFound: Boolean = false

    override fun applicableSuperClasses(): List<String> {
        return listOf(java.lang.Object::class.java.canonicalName)
    }

    override fun afterCheckRootProject(context: Context) {
        if (context.phase == 1 && databaseList.isNotEmpty()) {
            context.driver.requestRepeat(this, Scope.JAVA_FILE_SCOPE)
        }
        if (context.phase == 2) {
            if (!registrationClassFound) {
                context.report(
                    issue = ISSUE,
                    location = context.getLocation(ROOM_DATABASE_REGISTRATION_CLASS_NAME),
                    message = DATABASE_REGISTRATION_CLASS_MISSING_ERROR,
                )
            }
        }
    }

    override fun visitClass(context: JavaContext, declaration: UClass) {
        when (context.phase) {
            1 -> visitClassPhaseOne(context, declaration)
            2 -> visitClassPhaseTwo(context, declaration)
        }
    }

    private fun visitClassPhaseOne(context: JavaContext, declaration: UClass) {
        val qualifiedName: String = declaration.qualifiedName ?: return
        if (
            qualifiedName == ROOM_DATABASE_CLASS_NAME ||
                declaration.supers.find { it.qualifiedName == ROOM_DATABASE_CLASS_NAME } == null
        ) {
            return
        }
        databaseList.add(declaration)
        val databaseAnnotation = getDatabaseAnnotation(context, declaration) ?: return

        checkSchemaExportInAnnotation(context, databaseAnnotation)

        val databaseVersion =
            getAndValidateDatabaseVersion(context, declaration, databaseAnnotation) ?: return

        validateMigrationPath(databaseAnnotation, context, databaseVersion)
    }

    private fun visitClassPhaseTwo(context: JavaContext, declaration: UClass) {
        if (declaration.qualifiedName?.contains(ROOM_DATABASE_REGISTRATION_CLASS_NAME) != true) {
            return
        }

        registrationClassFound = true
        val registeredDatabase =
            declaration.fields
                .mapNotNull { (it.type as PsiClassType).resolve() }
                .map { it.qualifiedName }
        for (database in databaseList) {
            if (database.qualifiedName !in registeredDatabase) {
                context.report(
                    issue = ISSUE,
                    location = context.getNameLocation(declaration),
                    message = DATABASE_NOT_REGISTERED_ERROR.format(database.qualifiedName),
                )
            }
        }
    }

    private fun validateMigrationPath(
        databaseAnnotation: UAnnotation,
        context: JavaContext,
        databaseVersion: Int
    ) {
        // If database version is 1, it is not necessary to set up any migration plan.
        if (databaseVersion == 1) {
            return
        }
        val autoMigrationsAttribute =
            databaseAnnotation.findDeclaredAttributeValue(
                DATABASE_ANNOTATION_ATTRIBUTE_AUTO_MIGRATIONS
            )
        if (autoMigrationsAttribute == null) {
            context.report(
                issue = ISSUE,
                location = context.getNameLocation(databaseAnnotation),
                message = MISSING_AUTO_MIGRATION_ATTRIBUTE_ERROR,
            )
            return
        }

        val autoMigrations = (autoMigrationsAttribute as UCallExpression).valueArguments

        if (!isAutoMigrationPathComplete(autoMigrations, databaseVersion)) {
            context.report(
                issue = ISSUE,
                location = context.getNameLocation(databaseAnnotation),
                message = INCOMPLETE_MIGRATION_PATH_ERROR
            )
        }
    }

    private fun checkSchemaExportInAnnotation(
        context: JavaContext,
        databaseAnnotation: UAnnotation
    ) {
        val exportSchemaAttribute = databaseAnnotation.findDeclaredAttributeValue("exportSchema")
        if (
            exportSchemaAttribute != null &&
                !(exportSchemaAttribute.let { ConstantEvaluator.evaluate(null, it) } as Boolean)
        ) {
            context.report(
                issue = ISSUE,
                location = context.getNameLocation(databaseAnnotation),
                message = SCHEMA_EXPORT_FALSE_ERROR,
            )
        }
    }

    private fun getDatabaseAnnotation(context: JavaContext, declaration: UClass): UAnnotation? {
        for (annotation in declaration.uAnnotations) {
            if (DATABASE_ANNOTATION_CLASS_NAME == annotation.qualifiedName) {
                return annotation
            }
        }
        context.report(
            issue = ISSUE,
            location = context.getNameLocation(declaration),
            message = MISSING_DATABASE_ANNOTATION_ERROR,
        )
        return null
    }

    private fun getAndValidateDatabaseVersion(
        context: JavaContext,
        declaration: UClass,
        databaseAnnotation: UAnnotation
    ): Int? {
        val versionInAnnotation =
            databaseAnnotation.findAttributeValue(DATABASE_ANNOTATION_ATTRIBUTE_VERSION)

        val versionField = getDatabaseVersionField(context, declaration)

        if (versionInAnnotation == null) {
            context.report(
                issue = ISSUE,
                location = context.getLocation(databaseAnnotation),
                message = MISSING_DATABASE_VERSION_ANNOTATION_ATTRIBUTE_ERROR,
            )
            return null
        }

        if (versionInAnnotation.tryResolveUDeclaration() != versionField) {
            context.report(
                issue = ISSUE,
                location = context.getLocation(versionInAnnotation),
                message = FAILED_REF_VERSION_FIELD_IN_ANNOTATION_ERROR,
            )
        }

        return versionInAnnotation.evaluate() as Int
    }

    private fun getDatabaseVersionField(context: JavaContext, declaration: UClass): UField? {
        var versionField: UField? = null
        for (field in declaration.fields) {
            if (DATABASE_VERSION_FIELD_NAME == field.name) {
                versionField = field
                if (
                    field.isFinal &&
                        field.isStatic &&
                        field.visibility == UastVisibility.PUBLIC &&
                        field.type == PsiType.INT
                ) {
                    return versionField
                }
            }
        }
        context.report(
            issue = ISSUE,
            location = context.getNameLocation(versionField ?: declaration),
            message = MISSING_DATABASE_VERSION_FIELD_ERROR,
        )
        return null
    }

    private fun isAutoMigrationPathComplete(
        autoMigrations: List<UExpression>,
        databaseVersion: Int
    ): Boolean {
        var i = 1
        val migrationPath =
            autoMigrations.map {
                Pair(
                    (it as UCallExpression)
                        .valueArguments
                        .find { va -> (va as UNamedExpression).name == "from" }
                        ?.evaluate() as Int,
                    it.valueArguments
                        .find { va -> (va as UNamedExpression).name == "to" }
                        ?.evaluate() as Int
                )
            }
        for (edge in migrationPath.sortedBy { it.first }) {
            val from = edge.first
            val to = edge.second
            if (from != i || to != i + 1) {
                return false
            }
            i++
        }
        return i == databaseVersion
    }

    companion object {
        const val ROOM_DATABASE_CLASS_NAME = "androidx.room.RoomDatabase"
        const val DATABASE_ANNOTATION_CLASS_NAME = "androidx.room.Database"
        const val DATABASE_VERSION_FIELD_NAME = "DATABASE_VERSION"
        const val DATABASE_ANNOTATION_ATTRIBUTE_AUTO_MIGRATIONS = "autoMigrations"
        const val DATABASE_ANNOTATION_ATTRIBUTE_VERSION = "version"
        const val ROOM_DATABASE_REGISTRATION_CLASS_NAME = "RoomDatabaseRegistration"

        const val MISSING_DATABASE_ANNOTATION_ERROR =
            "Class extends $ROOM_DATABASE_CLASS_NAME must have @$DATABASE_ANNOTATION_CLASS_NAME " +
                "annotation."
        const val MISSING_AUTO_MIGRATION_ATTRIBUTE_ERROR =
            "@$DATABASE_ANNOTATION_CLASS_NAME annotation attribute " +
                "$DATABASE_ANNOTATION_ATTRIBUTE_AUTO_MIGRATIONS missing for database version higher than 1."
        const val INCOMPLETE_MIGRATION_PATH_ERROR =
            "$DATABASE_ANNOTATION_ATTRIBUTE_AUTO_MIGRATIONS in $DATABASE_ANNOTATION_CLASS_NAME " +
                "should contain migration path in increment of 1 from 1 to " +
                "$DATABASE_VERSION_FIELD_NAME."
        const val MISSING_DATABASE_VERSION_FIELD_ERROR =
            "Must declare public static final int $DATABASE_VERSION_FIELD_NAME in file."
        const val MISSING_DATABASE_VERSION_ANNOTATION_ATTRIBUTE_ERROR =
            "@$DATABASE_ANNOTATION_CLASS_NAME must contain attribute $DATABASE_ANNOTATION_ATTRIBUTE_VERSION."
        const val FAILED_REF_VERSION_FIELD_IN_ANNOTATION_ERROR =
            "$DATABASE_ANNOTATION_ATTRIBUTE_VERSION in annotation $DATABASE_ANNOTATION_CLASS_NAME " +
                "must refer to the static field $DATABASE_VERSION_FIELD_NAME."
        const val SCHEMA_EXPORT_FALSE_ERROR = "Export schema must be set to true or absent"
        const val DATABASE_REGISTRATION_CLASS_MISSING_ERROR =
            "Class $ROOM_DATABASE_REGISTRATION_CLASS_NAME is required and should contain all DB classes as field."
        const val DATABASE_NOT_REGISTERED_ERROR = "Database class %s is missing from registration."

        val ISSUE =
            Issue.create(
                id = "RoomDatabaseChange",
                briefDescription = "Updated Room Database must have migration path and test.",
                explanation =
                    "Room database update requires migration path configuration and testing.",
                moreInfo = "http://go/rb-room-migration-enforcement",
                category = Category.COMPLIANCE,
                severity = Severity.ERROR,
                implementation =
                    Implementation(RoomDatabaseMigrationDetector::class.java, Scope.JAVA_FILE_SCOPE)
            )
    }
}
