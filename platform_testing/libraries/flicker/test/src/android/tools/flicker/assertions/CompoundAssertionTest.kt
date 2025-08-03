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

package android.tools.flicker.assertions

import org.junit.Assert
import org.junit.Test

class CompoundAssertionTest {
    @Test
    fun correctNameSingle() {
        val assertion = assertion1()
        Assert.assertEquals(NAME, assertion.name)
    }

    @Test
    fun correctNamePair() {
        val assertion = assertion1()
        Assert.assertEquals(NAME, assertion.name)
        assertion.add({}, OTHER, optional = false)
        Assert.assertEquals("$NAME and $OTHER", assertion.name)
    }

    @Test
    fun correctNameTriple() {
        val assertion = assertion1()

        Assert.assertEquals(NAME, assertion.name)
        assertion.add({}, OTHER, optional = false)
        assertion.add({}, OTHER, optional = false)
        Assert.assertEquals("$NAME and $OTHER and $OTHER", assertion.name)
    }

    @Test
    fun executes() {
        var count = 0
        val assertion = CompoundAssertion<String>({ count++ }, NAME, optional = false)
        assertion.invoke("")
        Assert.assertEquals(1, count)
        assertion.add({ count++ }, NAME, optional = false)
        assertion.invoke("")
        Assert.assertEquals(3, count)
    }

    @Test
    fun assertionsPass() {
        assertion1().invoke("")
    }

    @Test(expected = IllegalStateException::class)
    fun oneAssertionFail() {
        val assertion = assertion1()
        assertion.add({ error("EXPECTED") }, OTHER, optional = false)
        assertion.invoke("")
    }

    @Test
    fun oneOptionalAssertionFail() {
        val assertion = assertion1()
        assertion.add({ error("EXPECTED") }, OTHER, optional = true)
        assertion1().invoke("")
    }

    companion object {
        private const val NAME = "Name"
        private const val OTHER = "Other"

        private fun assertion1(optional: Boolean = false) =
            CompoundAssertion<String>({}, NAME, optional)
    }
}
