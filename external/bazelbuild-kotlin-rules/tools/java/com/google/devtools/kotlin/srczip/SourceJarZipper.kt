/*
 * * Copyright 2022 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the License);
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.devtools.kotlin.srczip

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.system.exitProcess
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Option
import picocli.CommandLine.ParameterException
import picocli.CommandLine.Parameters
import picocli.CommandLine.Spec

@Command(
  name = "source-jar-zipper",
  subcommands = [Unzip::class, Zip::class, ZipResources::class],
  description = ["A tool to pack and unpack srcjar files, and to zip resource files"],
)
class SourceJarZipper : Runnable {
  @Spec private lateinit var spec: CommandSpec

  override fun run() {
    throw ParameterException(spec.commandLine(), "Specify a command: zip, zip_resources or unzip")
  }
}

fun main(args: Array<String>) {
  val exitCode = CommandLine(SourceJarZipper()).execute(*args)
  exitProcess(exitCode)
}

/**
 * Checks for duplicates and adds an entry into [errors] if one is found, otherwise adds a pair of
 * [zipPath] and [sourcePath] to the receiver
 *
 * @param[zipPath] relative path inside the jar, built either from package name (e.g. package
 *   com.google.foo -> com/google/foo/FileName.kt) or by resolving the file name relative to the
 *   directory it came from (e.g. foo/bar/1/2.txt came from foo/bar -> 1/2.txt)
 * @param[sourcePath] full path of file into its file system
 * @param[errors] list of strings describing catched errors
 * @receiver a mutable map of path to path, where keys are relative paths of files inside the
 *   resulting .jar, and values are full paths of files
 */
fun MutableMap<Path, Path>.checkForDuplicatesAndSetFilePathToPathInsideJar(
  zipPath: Path,
  sourcePath: Path,
  errors: MutableList<String>,
) {
  val duplicatedSourcePath: Path? = this[zipPath]
  if (duplicatedSourcePath == null) {
    this[zipPath] = sourcePath
  } else {
    errors.add(
      "${sourcePath} has the same path inside .jar as ${duplicatedSourcePath}! " +
        "If it is intended behavior rename one or both of them."
    )
  }
}

private fun clearSingletonEmptyPath(list: MutableList<Path>) {
  if (list.size == 1 && list[0].toString() == "") {
    list.clear()
  }
}

// Normalize timestamps
val DEFAULT_TIMESTAMP = LocalDateTime.of(2010, 1, 1, 0, 0, 0)

fun MutableMap<Path, Path>.writeToStream(
  zipper: ZipOutputStream,
  prefix: String = "",
) {
  for ((zipPath, sourcePath) in this) {
    BufferedInputStream(Files.newInputStream(sourcePath)).use { inputStream ->
      val entry = ZipEntry(Paths.get(prefix).resolve(zipPath).toString())
      entry.timeLocal = DEFAULT_TIMESTAMP
      zipper.putNextEntry(entry)
      inputStream.copyTo(zipper, bufferSize = 1024)
    }
  }
}

@Command(name = "zip", description = ["Zip source files into a source jar file"])
class Zip : Runnable {

  @Parameters(index = "0", paramLabel = "outputJar", description = ["Output jar"])
  lateinit var outputJar: Path

  @Option(
    names = ["-i", "--ignore_not_allowed_files"],
    description = ["Ignore not .kt, .java or invalid file paths without raising an exception"],
  )
  var ignoreNotAllowedFiles = false

  @Option(
    names = ["--kotlin_srcs"],
    split = ",",
    description = ["Kotlin source files"],
  )
  val kotlinSrcs = mutableListOf<Path>()

  @Option(
    names = ["--common_srcs"],
    split = ",",
    description = ["Common source files"],
  )
  val commonSrcs = mutableListOf<Path>()

  private companion object {
    const val PACKAGE_SPACE = "package "
    // can't start with digit and can't be all underscores
    val IDENTIFIER_REGEX = Regex("(?:[a-zA-Z]|_+[a-zA-Z0-9])\\w*")
    val PACKAGE_NAME_REGEX = Regex("$IDENTIFIER_REGEX(?:\\.$IDENTIFIER_REGEX)*")
  }

  override fun run() {
    clearSingletonEmptyPath(kotlinSrcs)
    clearSingletonEmptyPath(commonSrcs)

    // Validating files and getting paths for resulting .jar in one cycle
    // for each _srcs list
    val ktZipPathToSourcePath = mutableMapOf<Path, Path>()
    val commonZipPathToSourcePath = mutableMapOf<Path, Path>()
    val errors = mutableListOf<String>()

    fun Path.getPackagePath(): Path {
      this.toFile().bufferedReader().use { stream ->
        while (true) {
          val line = stream.readLine() ?: return this.fileName

          if (line.startsWith(PACKAGE_SPACE)) {
            // Kotlin allows usage of reserved words in package names framing them
            // with backquote symbol "`"
            val packageName =
              line
                .removePrefix(PACKAGE_SPACE)
                .substringBefore("//")
                .trim()
                .removeSuffix(";")
                .replace(Regex("\\B`(.+?)`\\B"), "$1")
            if (!PACKAGE_NAME_REGEX.matches(packageName)) {
              errors.add("$this contains an invalid package name")
              return this.fileName
            }
            return Paths.get(packageName.replace(".", "/")).resolve(this.fileName)
          }
        }
      }
    }

    fun Path.validateFile(): Boolean {
      when {
        !Files.isRegularFile(this) -> {
          if (!ignoreNotAllowedFiles) errors.add("${this} is not a file")
          return false
        }
        !this.toString().endsWith(".kt") && !this.toString().endsWith(".java") -> {
          if (!ignoreNotAllowedFiles) errors.add("${this} is not a Kotlin file")
          return false
        }
        else -> return true
      }
    }

    for (sourcePath in kotlinSrcs) {
      if (sourcePath.validateFile()) {
        ktZipPathToSourcePath.checkForDuplicatesAndSetFilePathToPathInsideJar(
          sourcePath.getPackagePath(),
          sourcePath,
          errors,
        )
      }
    }

    for (sourcePath in commonSrcs) {
      if (sourcePath.validateFile()) {
        commonZipPathToSourcePath.checkForDuplicatesAndSetFilePathToPathInsideJar(
          sourcePath.getPackagePath(),
          sourcePath,
          errors,
        )
      }
    }

    check(errors.isEmpty()) { errors.joinToString("\n") }

    ZipOutputStream(BufferedOutputStream(Files.newOutputStream(outputJar))).use { zipper ->
      commonZipPathToSourcePath.writeToStream(zipper, "common-srcs")
      ktZipPathToSourcePath.writeToStream(zipper)
    }
  }
}

@Command(name = "unzip", description = ["Unzip a jar archive into a specified directory"])
class Unzip : Runnable {

  @Parameters(index = "0", paramLabel = "inputJar", description = ["Jar archive to unzip"])
  lateinit var inputJar: Path

  @Parameters(index = "1", paramLabel = "outputDir", description = ["Output directory"])
  lateinit var outputDir: Path

  override fun run() {
    ZipInputStream(Files.newInputStream(inputJar)).use { unzipper ->
      while (true) {
        val zipEntry: ZipEntry? = unzipper.nextEntry
        if (zipEntry == null) return

        val entryName = zipEntry.name
        check(!entryName.contains("./")) { "Cannot unpack srcjar with relative path ${entryName}" }

        if (!entryName.endsWith(".kt") && !entryName.endsWith(".java")) continue

        val entryPath = outputDir.resolve(entryName)
        if (!Files.exists(entryPath.parent)) Files.createDirectories(entryPath.parent)
        Files.copy(unzipper, entryPath, StandardCopyOption.REPLACE_EXISTING)
      }
    }
  }
}

@Command(name = "zip_resources", description = ["Zip resources"])
class ZipResources : Runnable {

  @Parameters(index = "0", paramLabel = "outputJar", description = ["Output jar"])
  lateinit var outputJar: Path

  @Option(
    names = ["--input_dirs"],
    split = ",",
    description = ["Input files directories"],
    required = true,
  )
  val inputDirs = mutableListOf<Path>()

  override fun run() {
    clearSingletonEmptyPath(inputDirs)

    val filePathToOutputPath = mutableMapOf<Path, Path>()
    val errors = mutableListOf<String>()

    // inputDirs has filter checking if the dir exists, because some empty dirs generated by blaze
    // may not exist from Kotlin compiler's side. It turned out to be safer to apply a filter then
    // to rely that generated directories are always directories, not just path names
    for (dirPath in inputDirs.filter { curDirPath -> Files.exists(curDirPath) }) {
      if (!Files.isDirectory(dirPath)) {
        errors.add("${dirPath} is not a directory")
      } else {
        Files.walk(dirPath)
          .filter { fileOrDir -> !Files.isDirectory(fileOrDir) }
          .forEach { filePath ->
            filePathToOutputPath.checkForDuplicatesAndSetFilePathToPathInsideJar(
              dirPath.relativize(filePath),
              filePath,
              errors
            )
          }
      }
    }

    check(errors.isEmpty()) { errors.joinToString("\n") }

    ZipOutputStream(BufferedOutputStream(Files.newOutputStream(outputJar))).use { zipper ->
      filePathToOutputPath.writeToStream(zipper)
    }
  }
}
