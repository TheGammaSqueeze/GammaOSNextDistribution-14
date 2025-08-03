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

package android.tools

class LoggerBuilder {
    private var onV: (tag: String, msg: String, error: Throwable?) -> Unit = { tag, msg, error ->
        println("(V) $tag $msg")
        error?.printStackTrace()
    }
    private var onD: (tag: String, msg: String, error: Throwable?) -> Unit = { tag, msg, error ->
        println("(D) $tag $msg")
        error?.printStackTrace()
    }
    private var onI: (tag: String, msg: String, error: Throwable?) -> Unit = { tag, msg, error ->
        println("(I) $tag $msg")
        error?.printStackTrace()
    }
    private var onW: (tag: String, msg: String, error: Throwable?) -> Unit = { tag, msg, error ->
        println("(W) $tag $msg")
        error?.printStackTrace()
    }
    private var onE: (tag: String, msg: String, error: Throwable?) -> Unit = { tag, msg, error ->
        println("(e) $tag $msg $error")
        error?.printStackTrace()
    }
    private var onTracing: (name: String, predicate: () -> Any) -> Any = { name, predicate ->
        try {
            println("(withTracing#start) $name")
            predicate()
        } finally {
            println("(withTracing#end) $name")
        }
    }

    fun setV(predicate: (tag: String, msg: String, error: Throwable?) -> Unit): LoggerBuilder =
        apply {
            onV = predicate
        }

    fun setD(predicate: (tag: String, msg: String, error: Throwable?) -> Unit): LoggerBuilder =
        apply {
            onD = predicate
        }

    fun setI(predicate: (tag: String, msg: String, error: Throwable?) -> Unit): LoggerBuilder =
        apply {
            onI = predicate
        }

    fun setW(predicate: (tag: String, msg: String, error: Throwable?) -> Unit): LoggerBuilder =
        apply {
            onW = predicate
        }

    fun setE(predicate: (tag: String, msg: String, error: Throwable?) -> Unit): LoggerBuilder =
        apply {
            onE = predicate
        }

    fun setOnTracing(predicate: (name: String, predicate: () -> Any) -> Any): LoggerBuilder =
        apply {
            onTracing = predicate
        }

    fun build(): ILogger {
        return object : ILogger {
            override fun d(tag: String, msg: String, error: Throwable?) = onD(tag, msg, error)

            override fun e(tag: String, msg: String, error: Throwable?) = onE(tag, msg, error)

            override fun i(tag: String, msg: String, error: Throwable?) = onI(tag, msg, error)

            override fun v(tag: String, msg: String, error: Throwable?) = onV(tag, msg, error)

            override fun w(tag: String, msg: String, error: Throwable?) = onW(tag, msg, error)

            override fun <T> withTracing(name: String, predicate: () -> T): T {
                return onTracing(name, predicate as () -> Any) as T
            }
        }
    }
}
