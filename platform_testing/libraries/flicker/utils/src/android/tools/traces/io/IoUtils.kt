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

package android.tools.traces.io

import android.tools.traces.executeShellCommand
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

object IoUtils {
    private fun copyFile(src: File, dst: File) {
        executeShellCommand("cp $src $dst")
        executeShellCommand("chmod a+r $dst")
    }

    fun moveFile(src: File, dst: File) {
        if (src.isDirectory) {
            moveDirectory(src, dst)
        }
        // Move the  file to the output directory
        // Note: Due to b/141386109, certain devices do not allow moving the files between
        //       directories with different encryption policies, so manually copy and then
        //       remove the original file
        //       Moreover, the copied trace file may end up with different permissions, resulting
        //       in b/162072200, to prevent this, ensure the files are readable after copying
        copyFile(src, dst)
        executeShellCommand("rm $src")
    }

    private fun moveDirectory(src: File, dst: File) {
        require(src.isDirectory) { "$src is not a directory" }

        Files.createDirectories(Paths.get(dst.path))

        src.listFiles()?.forEach {
            if (it.isDirectory) {
                moveDirectory(src, dst.resolve(it.name))
            } else {
                moveFile(it, dst.resolve(it.name))
            }
        }
    }
}
