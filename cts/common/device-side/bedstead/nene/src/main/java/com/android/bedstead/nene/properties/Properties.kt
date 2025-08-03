package com.android.bedstead.nene.properties

import com.android.bedstead.nene.utils.ShellCommand
import com.android.bedstead.nene.utils.UndoableContext

/** Test APIs related to system properties. */
object Properties {

    /** Set a system property. */
    fun set(key: String, value: String): UndoableContext {
        val existingValue = get(key)

        ShellCommand.builder("setprop")
            .addOperand(key)
            .addOperand(value)
            .validate { it == "" }
            .executeOrThrowNeneException("Error setting property $key to $value")

        return UndoableContext {
            set(key, existingValue)
        }
    }

    /** Get the value of a system property. */
    fun get(key: String): String = ShellCommand.builder("getprop").addOperand(key)
        .executeAndParseOutput { it.stripTrailing() }
}