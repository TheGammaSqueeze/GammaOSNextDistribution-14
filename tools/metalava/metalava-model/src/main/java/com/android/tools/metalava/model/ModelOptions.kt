/*
 * Copyright (C) 2024 The Android Open Source Project
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

import kotlin.reflect.KClass
import kotlin.reflect.cast

/**
 * A type safe, immutable set of model options.
 *
 * Used to allow a user of a model to pass model specific options through to a model.
 */
class ModelOptions
private constructor(
    /** Description of the model options. */
    private val description: String,
    /** The option settings. */
    private val settings: Map<Key<*>, Any>,
) {
    /** Get an option value using the type safe [Key]. */
    operator fun <T : Any> get(key: Key<T>): T {
        return settings[key]?.let { key.kClass.cast(it) } ?: key.default
    }

    override fun toString(): String {
        return description
    }

    /** Builder for [ModelOptionsBuilder]. */
    class ModelOptionsBuilder internal constructor() {
        /** The option settings. */
        internal val settings = mutableMapOf<Key<*>, Any>()

        /**
         * Map from [Key.name] to the [Key] used within this set of options. This is used to ensure
         * that two keys with the same name cannot be used within the same set of options.
         */
        private val keys = mutableMapOf<String, Key<*>>()

        /** Set an option value using the type safe [Key]. */
        operator fun <T : Any> set(key: Key<T>, value: T) {
            val existingKey = keys.put(key.name, key)
            if (existingKey != null)
                throw IllegalStateException(
                    "Duplicate keys with the same name $key and $existingKey"
                )

            settings[key] = value
        }
    }

    companion object {
        val empty = ModelOptions("empty", emptyMap())

        /** Build a [ModelOptions]. */
        fun build(description: String, block: ModelOptionsBuilder.() -> Unit): ModelOptions {
            val builder = ModelOptionsBuilder()
            builder.block()
            return ModelOptions(description, builder.settings.toMap())
        }
    }

    /** A type safe opaque key. */
    class Key<T : Any>
    @PublishedApi
    internal constructor(
        internal val name: String,
        internal val default: T,
        internal val kClass: KClass<T>
    ) {

        override fun toString() = "Key($name, $default, $kClass)"

        companion object {
            /**
             * Create a type safe key.
             *
             * @param name the name of the key, this should be unique across all key names to allow
             *   multiple different options from different models to be set together without
             *   conflict. It is recommended to prefix the name with the name of the model, e.g.
             *   `model.option`.
             * @param default the default value of the key.
             */
            inline fun <reified T : Any> of(name: String, default: T): Key<T> {
                return Key(name, default, T::class)
            }
        }
    }
}
