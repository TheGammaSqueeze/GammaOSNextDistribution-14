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

@file:Suppress("PrivatePropertyName", "MayBeConstant")

package com.android.tools.metalava.model.text

private val FILE_FORMAT_PROPERTY_NAMES =
    listOf(
        "add-additional-overrides",
        "concise-default-values",
        "include-type-use-annotations",
        "kotlin-name-type-order",
        "kotlin-style-nulls",
        "language",
        "migrating",
        "name",
        "overloaded-method-order",
        "sort-whole-extends-list",
        "surface",
    )

val FILE_FORMAT_PROPERTIES = FILE_FORMAT_PROPERTY_NAMES.joinToString { "'$it'" }
