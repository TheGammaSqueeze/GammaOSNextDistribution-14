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

package com.google.doclava.javadoc;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import javax.lang.model.SourceVersion;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;

/**
 * Doclet for unit testing implementation classes {@link com.google.doclava.javadoc}. Its sole
 * purpose is to produce {@link DocletEnvironment} from a given source path and cache it, so it can
 * be used by the rest of the unit tests.
 *
 * @see BaseTest
 */
public class EmptyDoclet implements Doclet {

    // Has to be static, otherwise gc'ed before tests are run.
    private static DocletEnvironment environment;

    // jdk.javadoc.internal.tool.Main.execute(...) requires empty constructor.
    public EmptyDoclet() {
    }

    public EmptyDoclet(String sourcesPath) {
        try {
            PrintWriter out = new PrintWriter(Writer.nullWriter());
            StringWriter sw = new StringWriter();
            PrintWriter err = new PrintWriter(new BufferedWriter(sw));

            Method execute = jdk.javadoc.internal.tool.Main.class.getMethod("execute",
                    String[].class, PrintWriter.class, PrintWriter.class);
            execute.setAccessible(true);

            List<String> args = new ArrayList<>(List.of(
                    "-encoding", "UTF-8",
                    "-doclet", "com.google.doclava.javadoc.EmptyDoclet"
            ));

            try (Stream<Path> walk = Files.walk(Path.of(sourcesPath))) {
                var files = walk
                        .filter(f -> Files.isRegularFile(f) && f.toString().endsWith(".java"))
                        .map(Path::toString)
                        .toList();
                args.addAll(files);
            } catch (IOException e) {
                throw new RuntimeException("Invalid path to run doclet onm: " + e);
            }

            String[] stringArgs = args.toArray(String[]::new);

            Integer result = (Integer) execute.invoke(null, stringArgs, out, err);
            if (result != 0) {
                System.err.println(sw);
                throw new RuntimeException("Failed to run Doclet: " + result);
            }
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new RuntimeException("Failed to run Doclet: " + e);
        }
    }

    @Override
    public void init(Locale locale, Reporter reporter) {
    }

    @Override
    public String getName() {
        return "Stub doclet for unit testing of the com.google.doclava.Doclava doclet";
    }

    @Override
    public Set<? extends Option> getSupportedOptions() {
        return Set.of();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_17;
    }

    @Override
    public boolean run(DocletEnvironment environment) {
        EmptyDoclet.environment = environment;
        return true;
    }

    public DocletEnvironment getEnvironment() {
        return environment;
    }
}
