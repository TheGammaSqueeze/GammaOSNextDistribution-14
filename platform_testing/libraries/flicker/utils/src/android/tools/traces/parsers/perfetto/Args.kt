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

package android.tools.traces.parsers.perfetto

class Args {
    private var child: MutableMap<String, Args>? = null
    private var children: MutableMap<String, MutableList<Args>>? = null

    private var value: Any? = null

    fun getChild(key: String): Args? = child?.get(key)

    fun getChildren(key: String): List<Args>? = children?.get(key)

    fun getBoolean(): Boolean {
        require(value !== null) { "Cannot access value of a non-leaf args" }
        return value as Boolean
    }

    fun getInt(): Int {
        require(value !== null) { "Cannot access value of a non-leaf args" }
        return (value as Long).toInt()
    }

    fun getLong(): Long {
        require(value !== null) { "Cannot access value of a non-leaf args" }
        return value as Long
    }

    fun getFloat(): Float {
        require(value !== null) { "Cannot access value of a non-leaf args" }
        return (value as Double).toFloat()
    }

    fun isString(): Boolean {
        return value is String
    }

    fun getString(): String {
        require(value !== null) { "Cannot access value of a non-leaf args" }
        return value as String
    }

    fun add(key: String, value: String, valueType: String) {
        val keyTokens = key.split(".")
        val parsedValue = parseValue(value, valueType)
        add(keyTokens, 0, parsedValue)
    }

    private fun add(keyTokens: List<String>, currentToken: Int, value: Any) {
        if (currentToken == keyTokens.size) {
            require(child == null && children == null) {
                "Cannot assign a value to a non-leaf args"
            }
            this.value = value
            return
        }

        require(this.value == null) { "Cannot add child/children to a leaf args" }

        val keyAndIndex = tryParseKeyAndIndex(keyTokens[currentToken])
        if (keyAndIndex != null) {
            addToChildrenMap(keyAndIndex, keyTokens, currentToken, value)
            return
        }

        addToChildMap(keyTokens, currentToken, value)
    }

    private fun addToChildrenMap(
        keyAndIndex: Pair<String, Int>,
        keyTokens: List<String>,
        currentToken: Int,
        value: Any
    ) {
        val (key, index) = keyAndIndex
        if (children == null) {
            children = HashMap()
        }
        if (children!![key] == null) {
            children!![key] = ArrayList()
        }
        while (index >= children!![key]!!.size) {
            children!![key]!!.add(Args())
        }
        children!![key]!![index].add(keyTokens, currentToken + 1, value)
    }

    private fun addToChildMap(keyTokens: List<String>, currentToken: Int, value: Any) {
        val key = keyTokens[currentToken]
        if (child == null) {
            child = HashMap()
        }
        if (child!![key] != null) {
            child!![key]!!.add(keyTokens, currentToken + 1, value)
        } else {
            child!![key] = Args().apply { add(keyTokens, currentToken + 1, value) }
        }
    }

    private fun parseValue(value: String, valueType: String): Any {
        val result: Any? =
            when (valueType) {
                "bool" -> value.toBooleanStrict()
                "int" -> value.toLong()
                "uint" -> value.toLong()
                "real" -> value.toDouble()
                "string" -> value
                else -> null
            }
        require(result != null) { "Unrecognized args value type '$valueType'" }
        return result
    }

    private fun tryParseKeyAndIndex(token: String): Pair<String, Int>? {
        require(token.length > 0) { "Key token cannot have length 0" }

        if (token[token.length - 1] != ']') {
            return null
        }

        val indexOpeningBracket = token.indexOf('[')
        require(indexOpeningBracket != -1) {
            "Key token contains closing bracket ']' but not an opening one"
        }

        val key = token.substring(0, indexOpeningBracket)
        val index = token.substring(indexOpeningBracket + 1, token.length - 1).toInt()
        return Pair(key, index)
    }

    companion object {
        fun build(rows: List<Row>): Args {
            val args = Args()
            rows.forEach {
                val key = it.get("key")
                val value = it.get("value")
                val valueType = it.get("value_type")
                if (valueType != "null" && value != null) {
                    args.add(key as String, value as String, valueType as String)
                }
            }
            return args
        }
    }
}
