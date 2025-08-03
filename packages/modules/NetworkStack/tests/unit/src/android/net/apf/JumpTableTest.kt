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

package android.net.apf

import android.net.apf.BaseApfGenerator.Register.R0
import androidx.test.filters.SmallTest
import androidx.test.runner.AndroidJUnit4
import com.android.testutils.assertThrows
import java.util.NoSuchElementException
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.inOrder
import org.mockito.MockitoAnnotations

@RunWith(AndroidJUnit4::class)
@SmallTest
class JumpTableTest {

    @Mock
    lateinit var gen: ApfV4Generator

    @Before
    fun setUp() {
        MockitoAnnotations.initMocks(this)
    }

    @Test(expected = NullPointerException::class)
    fun testNullStartLabel() {
        // Can't use "null" because the method is @NonNull.
        JumpTable(AtomicReference<String>(null).get(), 10)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testNegativeSlot() {
        JumpTable("my_jump_table", -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testSlotTooLarge() {
        JumpTable("my_jump_table", 13)
    }

    @Test
    fun testValidSlotNumbers() {
        JumpTable("my_jump_table", 1)
        JumpTable("my_jump_table", 10)
        JumpTable("my_jump_table", 12)
    }

    @Test
    fun testGetStartLabel() {
        assertEquals("xyz", JumpTable("xyz", 3).startLabel)
        assertEquals("abc", JumpTable("abc", 9).startLabel)
    }

    @Test
    fun testCodeGeneration() {
        val name = "my_jump_table"
        val slot = 7

        val j = JumpTable(name, slot)
        j.addLabel("foo")
        j.addLabel("bar")
        j.addLabel("bar")
        j.addLabel("baz")

        assertEquals(0, j.getIndex("foo"))
        assertEquals(1, j.getIndex("bar"))
        assertEquals(2, j.getIndex("baz"))

        assertThrows(NoSuchElementException::class.java) {
            j.getIndex("nonexistent")
        }

        val inOrder = inOrder(gen)

        j.generate(gen)

        inOrder.verify(gen).defineLabel(name)
        inOrder.verify(gen).addLoadFromMemory(R0, slot)
        inOrder.verify(gen).addJumpIfR0Equals(0, "foo")
        inOrder.verify(gen).addJumpIfR0Equals(1, "bar")
        inOrder.verify(gen).addJumpIfR0Equals(2, "baz")
        inOrder.verify(gen).addJump(ApfV4Generator.PASS_LABEL)
        inOrder.verifyNoMoreInteractions()
    }
}
