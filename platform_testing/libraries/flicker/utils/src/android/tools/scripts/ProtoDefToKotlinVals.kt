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

package com.android.server.common.scripts

/**
 * The purpose of this script is to aid in creating flicker representations of proto objects by
 * transforming a property/variable from its proto definition format to its Kotlin format.
 *
 * Example: From a proto definition file, we have a property 'optional bool views_created = 1; ' We
 * want the output to be 'val viewsCreated: Boolean,' This should work for multi-line input, where
 * each line is one proto property.
 *
 * Usage: install kotlin on terminal and build into a jar file using the command `kotlinc
 * proto_def_to_kotlin_vals.kt -include-runtime -d <outputFileName.jar>` then running the output jar
 * file with `java -jar <outputFileName.jar> "<input string>"`
 */
fun mapProtoTypeToKotlinType(type: String): String? {
    val mapOfKnownProtoToKotlinTypes =
        mapOf("bool" to "Boolean", "int32" to "Int", "string" to "String")
    if (mapOfKnownProtoToKotlinTypes.containsKey(type)) return mapOfKnownProtoToKotlinTypes[type]

    if (type.contains("Proto")) return type.removeSuffix("Proto")

    return type
}

val snakeRegex = "_[a-zA-Z]".toRegex()

fun String.snakeToLowerCamelCase(): String {
    return snakeRegex.replace(this) { it.value.replace("_", "").uppercase() }
}

fun main(args: Array<String>) {
    val splitByNewLine = mutableListOf<String>()
    for (arg in args) {
        splitByNewLine.addAll(arg.split("\n")) // to work with multi-line proto properties
    }
    for (s in splitByNewLine) {
        val editedS = s.trim().removePrefix("optional ")
        // bool views_created = 1;
        val removedNumber = editedS.split("=")[0].trim() // bool views_created
        val splitBySpace = removedNumber.split(" ") // [bool, views_created]
        val kotlinType = mapProtoTypeToKotlinType(splitBySpace[0])
        val kotlinName = splitBySpace[1].snakeToLowerCamelCase()
        println("val $kotlinName: $kotlinType,")
    }
}
