/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.net

import android.util.SystemConfigFileCommitEventLogger
import com.android.testutils.DevSdkIgnoreRule.IgnoreUpTo
import com.android.testutils.DevSdkIgnoreRunner
import com.android.testutils.SC_V2
import com.android.testutils.assertThrows
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE
import java.nio.file.attribute.PosixFilePermission.OWNER_READ
import java.nio.file.attribute.PosixFilePermission.OWNER_WRITE
import java.util.Random
import kotlin.test.assertEquals

@RunWith(DevSdkIgnoreRunner::class)
@IgnoreUpTo(SC_V2)
class PersistentIntTest {
    val tempFilesCreated = mutableSetOf<Path>()
    lateinit var tempDir: Path

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("tmp.PersistentIntTest.")
    }

    @After
    fun tearDown() {
        var permissions = setOf(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE)
        Files.setPosixFilePermissions(tempDir, permissions)

        for (file in tempFilesCreated) {
            Files.deleteIfExists(file)
        }
        Files.delete(tempDir)
    }

    @Test
    fun testNormalReadWrite() {
        // New, initialized to 0.
        val pi = createPersistentInt()
        assertEquals(0, pi.get())
        pi.set(12345)
        assertEquals(12345, pi.get())

        // Existing.
        val pi2 = createPersistentInt(pathOf(pi))
        assertEquals(12345, pi2.get())
    }

    @Test
    fun testReadOrWriteFailsInCreate() {
        setWritable(tempDir, false)
        assertThrows(IOException::class.java) {
            createPersistentInt()
        }
    }

    @Test
    fun testReadOrWriteFailsAfterCreate() {
        val pi = createPersistentInt()
        pi.set(42)
        assertEquals(42, pi.get())

        val path = pathOf(pi)
        setReadable(path, false)
        assertThrows(IOException::class.java) { pi.get() }
        pi.set(77)

        setReadable(path, true)
        setWritable(path, false)
        setWritable(tempDir, false) // Writing creates a new file+renames, make this fail.
        assertThrows(IOException::class.java) { pi.set(99) }
        assertEquals(77, pi.get())
    }

    fun addOrRemovePermission(p: Path, permission: PosixFilePermission, add: Boolean) {
        val permissions = Files.getPosixFilePermissions(p)
        if (add) {
            permissions.add(permission)
        } else {
            permissions.remove(permission)
        }
        Files.setPosixFilePermissions(p, permissions)
    }

    fun setReadable(p: Path, readable: Boolean) {
        addOrRemovePermission(p, OWNER_READ, readable)
    }

    fun setWritable(p: Path, writable: Boolean) {
        addOrRemovePermission(p, OWNER_WRITE, writable)
    }

    fun pathOf(pi: PersistentInt): Path {
        return File(pi.path).toPath()
    }

    fun createPersistentInt(path: Path = randomTempPath()): PersistentInt {
        tempFilesCreated.add(path)
        return PersistentInt(path.toString(),
                SystemConfigFileCommitEventLogger("PersistentIntTest"))
    }

    fun randomTempPath(): Path {
        return tempDir.resolve(Integer.toHexString(Random().nextInt())).also {
            tempFilesCreated.add(it)
        }
    }
}
