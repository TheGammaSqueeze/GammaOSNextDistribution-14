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

package com.android.tools.metalava.model

const val JAVA_ENUM_VALUES = "values"
const val JAVA_ENUM_VALUE_OF = "valueOf"

const val JAVA_LANG_PREFIX = "java.lang."

const val JAVA_LANG_ANNOTATION = "java.lang.annotation.Annotation"
const val JAVA_LANG_DEPRECATED = "java.lang.Deprecated"
const val JAVA_LANG_ENUM = "java.lang.Enum"
const val JAVA_LANG_OBJECT = "java.lang.Object"
const val JAVA_LANG_STRING = "java.lang.String"
const val JAVA_LANG_THROWABLE = "java.lang.Throwable"

const val JAVA_LANG_ANNOTATION_TARGET = "java.lang.annotation.Target"
const val JAVA_LANG_TYPE_USE_TARGET = "java.lang.annotation.ElementType.TYPE_USE"
const val JAVA_LANG_METHOD_TARGET = "java.lang.annotation.ElementType.METHOD"
const val JAVA_LANG_FIELD_TARGET = "java.lang.annotation.ElementType.FIELD"
const val JAVA_LANG_PARAMETER_TARGET = "java.lang.annotation.ElementType.PARAMETER"

const val JAVA_RETENTION = "java.lang.annotation.Retention"
const val KT_RETENTION = "kotlin.annotation.Retention"

/** True if the annotation name represents @Retention (either the Java or Kotlin version) */
fun isRetention(qualifiedName: String?): Boolean =
    JAVA_RETENTION == qualifiedName || KT_RETENTION == qualifiedName
