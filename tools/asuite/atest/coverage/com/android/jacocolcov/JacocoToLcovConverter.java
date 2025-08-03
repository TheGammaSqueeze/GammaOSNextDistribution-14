/*
 * Copyright 2022 The Android Open Source Project
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.jacocolcov;

import static org.jacoco.core.analysis.ICounter.EMPTY;
import static org.jacoco.core.analysis.ISourceNode.UNKNOWN_LINE;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.errorprone.annotations.FormatMethod;
import com.google.errorprone.annotations.FormatString;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IClassCoverage;
import org.jacoco.core.analysis.ILine;
import org.jacoco.core.analysis.IMethodCoverage;
import org.jacoco.core.tools.ExecFileLoader;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

/** Converts JaCoCo measurements and class files into a lcov-format coverage report. */
final class JacocoToLcovConverter {

    // Command line flags.
    private static final String CLASSFILES_OPTION = "classfiles";
    private static final String SOURCEPATH_OPTION = "sourcepath";
    private static final String OUTPUT_OPTION = "o";
    private static final String VERBOSE_OPTION = "v";
    private static final String STRICT_OPTION = "strict";

    public static void main(String[] args) {
        Options options = new Options();

        options.addOption(
                OptionBuilder.hasArg()
                        .withArgName("<path>")
                        .withDescription("location of the Java class files")
                        .create(CLASSFILES_OPTION));

        options.addOption(
                OptionBuilder.hasArg()
                        .withArgName("<path>")
                        .withDescription("location of the source files")
                        .create(SOURCEPATH_OPTION));

        options.addOption(
                OptionBuilder.isRequired()
                        .hasArg()
                        .withArgName("<destfile>")
                        .withDescription("location to write lcov data")
                        .create(OUTPUT_OPTION));

        options.addOption(OptionBuilder.withDescription("verbose logging").create(VERBOSE_OPTION));

        options.addOption(
                OptionBuilder.withDescription("fail if any error is encountered")
                        .create(STRICT_OPTION));

        CommandLineParser parser = new PosixParser();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            logError("error parsing command line options: %s", e.getMessage());
            System.exit(1);
            return;
        }

        String[] classFiles = cmd.getOptionValues(CLASSFILES_OPTION);
        String[] sourcePaths = cmd.getOptionValues(SOURCEPATH_OPTION);
        String outputFile = cmd.getOptionValue(OUTPUT_OPTION);
        boolean verbose = cmd.hasOption(VERBOSE_OPTION);
        boolean strict = cmd.hasOption(STRICT_OPTION);
        String[] execFiles = cmd.getArgs();

        JacocoToLcovConverter converter = new JacocoToLcovConverter(verbose, strict);

        try {
            if (sourcePaths != null) {
                for (String sourcePath : sourcePaths) {
                    converter.indexSourcePath(Paths.get(sourcePath));
                }
            }

            if (execFiles != null) {
                for (String execFile : execFiles) {
                    converter.loadExecFile(Paths.get(execFile));
                }
            }

            if (classFiles != null) {
                for (String classFile : classFiles) {
                    converter.loadClassFile(Paths.get(classFile));
                }
            }

            converter.write(Paths.get(outputFile));
        } catch (IOException e) {
            logError("failed to generate a coverage report: %s", e.getMessage());
            System.exit(2);
        }
    }

    private Analyzer analyzer;
    private final CoverageBuilder builder;
    private final ExecFileLoader loader;
    private final boolean verbose;
    private final boolean strict;

    private int execFilesLoaded;
    private int classFilesLoaded;
    private SetMultimap<String, Path> sourceFiles;

    JacocoToLcovConverter(final boolean verbose, final boolean strict) {
        this.verbose = verbose;
        this.strict = strict;
        analyzer = null;
        builder = new CoverageBuilder();
        loader = new ExecFileLoader();
        execFilesLoaded = 0;
        classFilesLoaded = 0;
        sourceFiles = HashMultimap.create();
    }

    /**
     * Indexes the path and all subdirectories for Java or Kotlin files.
     *
     * @param path the path to search for files
     */
    void indexSourcePath(final Path path) throws IOException {
        try (Stream<Path> stream = Files.walk(path)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".kt"))
                    .forEach(p -> sourceFiles.put(p.getFileName().toString(), p.toAbsolutePath()));
        }
    }

    /**
     * Loads JaCoCo execution data files.
     *
     * <p>If strict is not set, logs any exception thrown and returns. If strict is set, rethrows
     * any exception encountered while loading the file. Execution data files are occasionally
     * malformed and will cause the tool to fail if strict is set.
     *
     * @param execFile the file to load
     * @throws IOException on error reading file or incorrect file format
     */
    void loadExecFile(final Path execFile) throws IOException {
        try {
            logVerbose("Loading exec file %s", execFile);
            loader.load(execFile.toFile());
            execFilesLoaded++;
        } catch (IOException e) {
            logError("Failed to load exec file %s", execFile);
            if (strict) {
                throw e;
            }
            logError(e.getMessage());
        }
    }

    /**
     * Loads uninstrumented Java class files.
     *
     * <p>This should be run only after loading all exec files, otherwise coverage data may be
     * incorrect.
     *
     * @param classFile the class file or class file archive to load
     * @throws IOException on error reading file or incorrect file format
     */
    void loadClassFile(final Path classFile) throws IOException {
        if (analyzer == null) {
            analyzer = new Analyzer(loader.getExecutionDataStore(), builder);
        }

        logVerbose("Loading class file %s", classFile);
        analyzer.analyzeAll(classFile.toFile());
        classFilesLoaded++;
    }

    /**
     * Writes out the lcov format file based on the exec data and class files loaded.
     *
     * @param outputFile the file to write to
     * @throws IOException on error writing to the output file
     */
    void write(final Path outputFile) throws IOException {
        logVerbose(
                "%d exec files loaded and %d class files loaded.",
                execFilesLoaded, classFilesLoaded);

        try (BufferedWriter writer = Files.newBufferedWriter(outputFile, StandardCharsets.UTF_8)) {
            // Write lcov header test name: <test name>. Displayed on the front page but otherwise
            // not used for anything important.
            writeLine(writer, "TN:%s", outputFile.getFileName());

            for (IClassCoverage coverage : builder.getClasses()) {
                if (coverage.isNoMatch()) {
                    String message = "Mismatch in coverage data for " + coverage.getName();
                    logVerbose(message);
                    if (strict) {
                        throw new IOException(message);
                    }
                }
                // Looping over coverage.getMethods() is done multiple times below due to lcov
                // ordering requirements.
                // lcov was designed around native code, and uses functions rather than methods as
                // its terminology of choice. We use methods here as we are working with Java code.
                int methodsFound = 0;
                int methodsHit = 0;
                int linesFound = 0;
                int linesHit = 0;

                // Sourcefile information: <absolute path to sourcefile>. If the sourcefile does not
                // match any file given on --sourcepath, it will not be included in the coverage
                // report.
                String sourcefile = findSourceFileMatching(sourcefile(coverage));
                if (sourcefile == null) {
                    continue;
                }
                writeLine(writer, "SF:%s", sourcefile);

                // Function information: <starting line>,<name>.
                for (IMethodCoverage method : coverage.getMethods()) {
                    writeLine(writer, "FN:%d,%s", method.getFirstLine(), name(method));
                }

                // Function coverage information: <execution count>,<name>.
                for (IMethodCoverage method : coverage.getMethods()) {
                    int count = method.getMethodCounter().getCoveredCount();
                    writeLine(writer, "FNDA:%d,%s", count, name(method));

                    methodsFound++;
                    if (count > 0) {
                        methodsHit++;
                    }
                }

                // Write the count of methods(functions) found and hit.
                writeLine(writer, "FNF:%d", methodsFound);
                writeLine(writer, "FNH:%d", methodsHit);

                // TODO: Write branch coverage information.

                // Write line coverage information.
                for (IMethodCoverage method : coverage.getMethods()) {
                    int start = method.getFirstLine();
                    int end = method.getLastLine();

                    if (start == UNKNOWN_LINE || end == UNKNOWN_LINE) {
                        continue;
                    }

                    for (int i = start; i <= end; i++) {
                        ILine line = method.getLine(i);
                        if (line.getStatus() == EMPTY) {
                            continue;
                        }
                        int count = line.getInstructionCounter().getCoveredCount();
                        writeLine(writer, "DA:%d,%d", i, count);

                        linesFound++;
                        if (count > 0) {
                            linesHit++;
                        }
                    }
                }

                // Write the count of lines hit and found.
                writeLine(writer, "LH:%d", linesHit);
                writeLine(writer, "LF:%d", linesFound);

                // End of the sourcefile block.
                writeLine(writer, "end_of_record");
            }
        }

        log("Coverage data written to %s", outputFile);
    }

    /**
     * Finds the absolute path to the sourcefile that ends with the given file path.
     *
     * <p>Searches all the files indexed on -sourcepath and returns the first file that matches the
     * package and class name. The input is the full Java class name, separated by `/` rather than
     * `.`
     *
     * @param filename the filename to match
     * @return the absolute path to the file, or null if none was found
     */
    private String findSourceFileMatching(String filename) {
        String key = Paths.get(filename).getFileName().toString();
        for (Path path : sourceFiles.get(key)) {
            if (path.endsWith(filename)) {
                logVerbose("%s matched to %s", filename, path);
                return path.toAbsolutePath().toString();
            }
        }
        logVerbose("%s did not match any source path", filename);
        return null;
    }

    /** Writes a line to the file. */
    @FormatMethod
    private static void writeLine(
            BufferedWriter writer, @FormatString String format, Object... args) throws IOException {
        writer.write(String.format(format, args));
        writer.newLine();
    }

    /** Prints log message. */
    @FormatMethod
    private static void log(@FormatString String format, Object... args) {
        System.out.println(String.format(format, args));
    }

    /** Prints verbose log. */
    @FormatMethod
    private void logVerbose(@FormatString String format, Object... args) {
        logVerbose(String.format(format, args));
    }

    /** Prints verbose log. */
    private void logVerbose(String message) {
        if (verbose) {
            System.out.println(message);
        }
    }

    /** Prints format string error message. */
    @FormatMethod
    private static void logError(@FormatString String format, Object... args) {
        logError(String.format(format, args));
    }

    /** Prints error message. */
    private static void logError(String message) {
        System.err.println(message);
    }

    /** Converts IClassCoverage to a sourcefile path. */
    private static String sourcefile(IClassCoverage coverage) {
        return coverage.getPackageName() + "/" + coverage.getSourceFileName();
    }

    /** Converts IMethodCoverage to a unique method descriptor. */
    private static String name(IMethodCoverage coverage) {
        return coverage.getName() + coverage.getDesc();
    }
}
