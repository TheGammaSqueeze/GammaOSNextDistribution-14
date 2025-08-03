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

package android.tools.flicker.junit

import java.util.Collections
import java.util.IdentityHashMap
import org.junit.Rule
import org.junit.rules.MethodRule
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.FrameworkMethod
import org.junit.runners.model.Statement

/**
 * Data structure for ordering of [TestRule]/[MethodRule] instances.
 *
 * @since 4.13
 */
internal class RuleContainer {
    private val orderValues = IdentityHashMap<Any, Int>()
    private val testRules = mutableListOf<TestRule>()
    private val methodRules = mutableListOf<MethodRule>()

    /** Sets order value for the specified rule. */
    fun setOrder(rule: Any, order: Int) {
        orderValues[rule] = order
    }

    fun add(methodRule: MethodRule) {
        methodRules.add(methodRule)
    }

    fun add(testRule: TestRule) {
        testRules.add(testRule)
    }

    /** Returns entries in the order how they should be applied, i.e. inner-to-outer. */
    private val sortedEntries: List<RuleEntry>
        get() {
            val ruleEntries: MutableList<RuleEntry> = ArrayList(methodRules.size + testRules.size)
            for (rule in methodRules) {
                ruleEntries.add(RuleEntry(rule, RuleEntry.TYPE_METHOD_RULE, orderValues[rule]))
            }
            for (rule in testRules) {
                ruleEntries.add(RuleEntry(rule, RuleEntry.TYPE_TEST_RULE, orderValues[rule]))
            }
            Collections.sort(ruleEntries, ENTRY_COMPARATOR)
            return ruleEntries
        }

    /** Applies all the rules ordered accordingly to the specified `statement`. */
    fun apply(
        method: FrameworkMethod?,
        description: Description?,
        target: Any?,
        statement: Statement
    ): Statement {
        if (methodRules.isEmpty() && testRules.isEmpty()) {
            return statement
        }
        var result = statement
        for (ruleEntry in sortedEntries) {
            result =
                if (ruleEntry.type == RuleEntry.TYPE_TEST_RULE) {
                    (ruleEntry.rule as TestRule).apply(result, description)
                } else {
                    (ruleEntry.rule as MethodRule).apply(result, method, target)
                }
        }
        return result
    }

    /**
     * Returns rule instances in the order how they should be applied, i.e. inner-to-outer.
     * VisibleForTesting
     */
    val sortedRules: List<Any>
        get() {
            val result = mutableListOf<Any>()
            for (entry in sortedEntries) {
                result.add(entry.rule)
            }
            return result
        }

    internal class RuleEntry(val rule: Any, val type: Int, order: Int?) {
        val order: Int

        init {
            this.order = order ?: Rule.DEFAULT_ORDER
        }

        companion object {
            const val TYPE_TEST_RULE = 1
            const val TYPE_METHOD_RULE = 0
        }
    }

    companion object {
        val ENTRY_COMPARATOR: Comparator<RuleEntry> =
            object : Comparator<RuleEntry> {
                override fun compare(o1: RuleEntry, o2: RuleEntry): Int {
                    val result = compareInt(o1.order, o2.order)
                    return if (result != 0) result else o1.type - o2.type
                }

                private fun compareInt(a: Int, b: Int): Int {
                    return if (a < b) 1 else if (a == b) 0 else -1
                }
            }
    }
}
