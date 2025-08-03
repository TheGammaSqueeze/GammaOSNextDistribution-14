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

package libcore.tools.analyzer.openjdk;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import libcore.tools.analyzer.openjdk.DependencyAnalyzer.Result.MethodDependency;
import libcore.tools.analyzer.openjdk.SignaturesCollector.SignaturesCollection;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.TraceClassVisitor;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Main {

    private static class MainArgs {

        @Parameter(names = "-h", help = true, description = "Shows this help message")
        public boolean help = false;
    }

    @Parameters(commandNames = CommandDump.NAME, commandDescription = "Dump a class from the "
            + "classpath. This tool is similar to javap tool provided in the OpenJDK.")
    private static class CommandDump {
        public static final String NAME = "dump";

        @Parameter(names = {"-cp", "--classpath"}, description = "file path to a .jmod or .jar file"
                + " or one of the following java version: oj, 8, 9, 11, 17, 21")
        public String classpathFile = "21";

        @Parameter(required = true, arity = 1,
                description = "<class>. The fully qualified name of the class in the classpath. "
                        + "Note that inner class uses $ separator and $ has to be escaped in shell,"
                        + " e.g. java.lang.Character\\$Subset")
        public List<String> classNames;

        @Parameter(names = "-h", help = true, description = "Shows this help message")
        public boolean help = false;

        private void run() throws UncheckedIOException {
            Path jmod = AndroidHostEnvUtil.parseInputClasspath(classpathFile);
            String className = classNames.get(0);
            try (ZipFile zipFile = new ZipFile(jmod.toFile())) {
                ZipEntry e = ClassFileUtil.getEntryFromClassNameOrThrow(zipFile, className);
                try (InputStream in = zipFile.getInputStream(e)) {
                    ClassReader classReader = new ClassReader(in);
                    PrintWriter printer = new PrintWriter(System.out);
                    TraceClassVisitor traceClassVisitor = new TraceClassVisitor(printer);
                    classReader.accept(traceClassVisitor, 0);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Parameters(commandNames = CommandApiDiff.NAME, commandDescription = "List the new and "
            + "removed APIs by comparing the older and newer implementation in 2 jar/jmod files")
    private static class CommandApiDiff {
        public static final String NAME = "api-diff";

        @Parameter(names = {"-b", "--base"}, description = "file path to a .jmod or .jar file or "
                + "one of the following java version: oj, 8, 9, 11, 17, 21")
        public String baseClasspath = "11";

        @Parameter(names = {"-n", "--new"}, description = "file path to a .jmod or .jar file or "
                + "one of the following java version: oj, 8, 9, 11, 17, 21")
        public String newClasspath = "21";

        @Parameter(required = true, arity = 1,
                description = "<class>. The fully qualified name of the class in the classpath. "
                        + "Note that inner class uses $ separator and $ has to be escaped in shell,"
                        + " e.g. java.lang.Character\\$Subset")
        public List<String> classNames;

        @Parameter(names = "-h", help = true, description = "Shows this help message")
        public boolean help = false;

        private void run() throws UncheckedIOException {
            Path basePath = AndroidHostEnvUtil.parseInputClasspath(baseClasspath);
            Path newPath = AndroidHostEnvUtil.parseInputClasspath(newClasspath);
            String className = classNames.get(0);
            DiffAnalyzer analyzer;
            try (ZipFile baseZip = new ZipFile(basePath.toFile());
                 ZipFile newZip = new ZipFile(newPath.toFile())) {
                ZipEntry baseEntry = ClassFileUtil.getEntryFromClassNameOrThrow(baseZip, className);
                ZipEntry newEntry = ClassFileUtil.getEntryFromClassNameOrThrow(newZip, className);
                try (InputStream baseIn = baseZip.getInputStream(baseEntry);
                     InputStream newIn = newZip.getInputStream(newEntry)) {
                    analyzer = DiffAnalyzer.analyze(baseIn, newIn);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            System.out.println("Class:" + className);
            analyzer.print(System.out);
        }
    }

    private static class DiffAnalyzer {
        List<MethodNode> newMethods = new ArrayList<>();

        List<MethodNode> removedMethods = new ArrayList<>();

        List<MethodNode> newlyDeprecatedMethods = new ArrayList<>();
        List<FieldNode> newFields = new ArrayList<>();

        List<FieldNode> removedFields = new ArrayList<>();

        List<FieldNode> newlyDeprecatedFields = new ArrayList<>();

        private static DiffAnalyzer analyze(InputStream baseIn, InputStream newIn)
                throws IOException {
            ClassNode baseClass = ClassFileUtil.parseClass(baseIn);
            ClassNode newClass = ClassFileUtil.parseClass(newIn);
            return analyze(baseClass, newClass);
        }

        private static DiffAnalyzer analyze(ClassNode baseClass, ClassNode newClass) {
            Map<String, MethodNode> baseMethods = getExposedMethods(baseClass)
                    .collect(toMap(DiffAnalyzer::toApiSignature, node -> node));
            Map<String, MethodNode> newMethods = getExposedMethods(newClass)
                    .collect(toMap(DiffAnalyzer::toApiSignature, node -> node));

            DiffAnalyzer result = new DiffAnalyzer();
            result.newMethods = getExposedMethods(newClass)
                    .filter(node -> !baseMethods.containsKey(toApiSignature(node)))
                    .collect(toList());
            result.removedMethods = getExposedMethods(baseClass)
                    .filter(node -> !newMethods.containsKey(toApiSignature(node)))
                    .collect(toList());
            result.newlyDeprecatedMethods = getExposedMethods(newClass)
                    .filter(DiffAnalyzer::isDeprecated)
                    .filter(node -> !baseMethods.containsKey(toApiSignature(node))
                            || !isDeprecated(baseMethods.get(toApiSignature(node))) )
                    .collect(toList());


            Map<String, FieldNode> baseFields = getExposedFields(baseClass)
                    .collect(toMap(node -> node.name, node -> node));
            Map<String, FieldNode> newFields = getExposedFields(newClass)
                    .collect(toMap(node -> node.name, node -> node));

            result.newFields = getExposedFields(newClass)
                    .filter(node -> !baseFields.containsKey(node.name))
                    .collect(toList());
            result.removedFields = getExposedFields(baseClass)
                    .filter(node -> !newFields.containsKey(node.name))
                    .collect(toList());
            result.newlyDeprecatedFields = getExposedFields(newClass)
                    .filter(DiffAnalyzer::isDeprecated)
                    .filter(node -> !baseFields.containsKey(node.name)
                            || !isDeprecated(baseFields.get(node.name)) )
                    .collect(toList());

            return result;
        }

        /**
         * Known issue: this signature doesn't differentiate static and virtual methods.
         */
        private static String toApiSignature(MethodNode node) {
            return node.name + node.desc;
        }

        private static Stream<MethodNode> getExposedMethods(ClassNode classNode) {
            if (!isExposed(classNode.access)) {
                return Stream.empty();
            }

            return classNode.methods.stream()
                    .filter(m -> isExposed(m.access))
                    .flatMap(DiffAnalyzer::getImpliedMethods);
        }

        /**
         * Get all implied methods that are not generated by javac.
         *
         * For example, {@link dalvik.annotation.codegen.CovariantReturnType} is used by dexers
         * to generate synthetic methods with a different return type.
         */
        private static Stream<MethodNode> getImpliedMethods(MethodNode node) {
            if (node.invisibleAnnotations == null
                    // Synthetic methods generated by javac can be annotated with
                    // @CovariantReturnType, but can be safely ignored to avoid double counting.
                    || (node.access & Opcodes.ACC_SYNTHETIC) != 0) {
                return Stream.of(node);
            }

            Stream<MethodNode> syntheticMethods = node.invisibleAnnotations.stream()
                    .flatMap(a -> { // flatten CovariantReturnTypes.value
                        if (!"Ldalvik/annotation/codegen/CovariantReturnType$CovariantReturnTypes;"
                                .equals(a.desc)) {
                            return Stream.of(a);
                        }
                        for (int i = 0; i < a.values.size() - 1; i++) {
                            if ("value".equals(a.values.get(i))) {
                                Object type = a.values.get(i + 1);
                                if (type instanceof List nodes) {
                                    return Stream.concat(Stream.of(a),
                                            (Stream<AnnotationNode>) nodes.stream());
                                }
                            }
                        }
                        return Stream.of(a);

                    })
                    .filter(a -> "Ldalvik/annotation/codegen/CovariantReturnType;".equals(a.desc))
                    .map(a -> {
                        for (int i = 0; i < a.values.size() - 1; i++) {
                            if ("returnType".equals(a.values.get(i))) {
                                Object type = a.values.get(i + 1);
                                if (type instanceof Type) {
                                    return (Type) type;
                                }
                            }
                        }
                        return null;
                    })
                    .filter(Predicate.not(Objects::isNull))
                    .map(returnType -> {
                        String desc = Type.getMethodDescriptor(returnType,
                                Type.getArgumentTypes(node.desc));
                        // It doesn't copy everything, e.g. annotations, but we only need
                        // access, name and desc for matching purpose.
                        return new MethodNode(node.access, node.name, desc, null,
                                node.exceptions.toArray(new String[0]));

                    });

            return Stream.concat(Stream.of(node), syntheticMethods);
        }


        private static Stream<FieldNode> getExposedFields(ClassNode classNode) {
            if (!isExposed(classNode.access)) {
                return Stream.empty();
            }

            return classNode.fields.stream()
                    .filter(m -> isExposed(m.access));
        }

        private static boolean isExposed(int flags) {
            return (flags & (Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) != 0;
        }

        private static boolean isDeprecated(MethodNode node) {
            return node.visibleAnnotations != null &&
                    node.visibleAnnotations.stream()
                            .anyMatch(anno ->"Ljava/lang/Deprecated;".equals(anno.desc));
        }

        private static boolean isDeprecated(FieldNode node) {
            return node.visibleAnnotations != null &&
                    node.visibleAnnotations.stream()
                            .anyMatch(anno ->"Ljava/lang/Deprecated;".equals(anno.desc));
        }

        void print(OutputStream out) {
            PrintWriter writer = new PrintWriter(out, /*autoFlush=*/true);

            printMethods(writer, "New methods", newMethods);
            printMethods(writer, "Removed methods", removedMethods);
            printMethods(writer, "Newly deprecated methods", newlyDeprecatedMethods);

            printFields(writer, "New fields", newFields);
            printFields(writer, "Removed fields", removedFields);
            printFields(writer, "Newly deprecated fields", newlyDeprecatedFields);
        }

        private static void printMethods(PrintWriter w, String header, List<MethodNode> nodes) {
            if (nodes.isEmpty()) {
                return;
            }

            w.println(" " + header + ":");
            for (MethodNode node : nodes) {
                w.println("  " + toApiSignature(node));
            }
        }

        private static void printFields(PrintWriter w, String header, List<FieldNode> nodes) {
            if (nodes.isEmpty()) {
                return;
            }

            w.println(" " + header + ":");
            for (FieldNode node : nodes) {
                w.println("  " + node.name + ": " + node.desc);
            }
        }
    }

    @Parameters(commandNames = CommandShowDeps.NAME, commandDescription = "Show the dependencies of"
        + " classes or packages.")
    private static class CommandShowDeps {

        public static final String NAME = "show-deps";

        @Parameter(names = {"-cp"}, description = "file path to a .jmod or .jar file or "
            + "one of the following java version: oj, 8, 9, 11, 17, 21")
        public String classpath = "21";

        /**
         * @see DependencyAnalyzer.ExcludeClasspathFilter
         */
        @Parameter(names = {"-x", "--exclude-deps-in-classpath"}, description = "a file path to a "
                + ".jmod or .jar file or one of the following java version: oj, 8, 9, 11, 17, 21. "
                + "The classes, methods and fields that exist in this classpath are "
                + "excluded from the output list of dependencies.")
        public String excludeClasspath = "oj";

        @Parameter(required = true,
            description = "(<classes>|<packages>). The class or package names in the classpath. "
                + "Note that inner class uses $ separator and $ has to be escaped in shell,"
                + " e.g. java.lang.Character\\$Subset")
        public List<String> classesOrPackages;

        @Parameter(names = "-i", description = "Show the dependencies within the provided "
                + "<classes> / <packages>")
        public boolean includeInternal = false;

        /**
         * @see DependencyAnalyzer.ExpectedUpstreamFilter
         */
        @Parameter(names = "-e", description = "Exclude the existing dependencies in the OpenJDK "
                + "version of the file specified in the libcore/EXPECTED_UPSTREAM file. Such "
                + "dependencies are likely to be eliminated / replaced in the libcore version "
                + "already even though the new OpenJDK version still depends on them.")
        public boolean usesExpectedUpstreamAsBaseDeps = false;

        @Parameter(names = "-c", description = "Only show class-level dependencies, not "
                + "field-level or method-level dependency details.")
        public boolean classOnly = false;

        @Parameter(names = "-h", help = true, description = "Shows this help message")
        public boolean help = false;

        private final PrintWriter mWriter = new PrintWriter(System.out, /*autoFlush=*/true);

        private void run()  {
            Path cp = AndroidHostEnvUtil.parseInputClasspath(classpath);
            Path ecp = excludeClasspath == null || excludeClasspath.isEmpty() ? null
                    : AndroidHostEnvUtil.parseInputClasspath(excludeClasspath);
            DependencyAnalyzer analyzer = new DependencyAnalyzer(cp, ecp,
                    includeInternal, usesExpectedUpstreamAsBaseDeps);

            DependencyAnalyzer.Result result = analyzer.analyze(classesOrPackages);
            var details = result.getDetails();
            boolean isSingleClass = isInputSingleClass();
            if (isSingleClass) {
                mWriter.println("Input Class: " + classesOrPackages.get(0));
            }

            for (var classEntry : details.entrySet()) {
                printMethodDependencies(isSingleClass, classEntry.getKey(), classEntry.getValue());
            }
            mWriter.println(" Summary:");
            var collection = result.getAggregated();
            printInsn(null, collection);
        }

        private void printMethodDependencies(boolean isSingleClass, String className,
                List<MethodDependency> deps) {
            boolean isClassNamePrinted = false;
            for (var dep : deps) {
                // Try not to flood and print tons of methods which have no dependency.
                // A native method has no method and field dependency in java code, and thus
                // print it when the user intends to understand the dependency of a single
                // class.
                if (!dep.mDependency.isEmpty()
                        || (isSingleClass && (dep.mNode.access & Opcodes.ACC_NATIVE) != 0)) {
                    if (!isClassNamePrinted) {
                        mWriter.println("Class: " + className);
                        isClassNamePrinted = true;
                    }
                    printInsn(dep.mNode, dep.mDependency);
                }
            }

        }

        private boolean isInputSingleClass() {
            if (classesOrPackages.size() != 1) {
                return false;
            }

            String[] split = classesOrPackages.get(0).split("\\.");
            String last = split[split.length - 1];
            return last.length() >= 1 && Character.isUpperCase(last.charAt(0));
        }


        private void printInsn(MethodNode method, SignaturesCollection collection) {
            PrintWriter writer = mWriter;

            if (method != null) {
                String printedMethod = "";
                printedMethod += (method.access & Opcodes.ACC_PUBLIC) != 0 ? "public " : "";
                printedMethod += (method.access & Opcodes.ACC_PROTECTED) != 0 ? "protected " : "";
                printedMethod += (method.access & Opcodes.ACC_PRIVATE) != 0 ? "private " : "";
                printedMethod += (method.access & Opcodes.ACC_STATIC) != 0 ? "static " : "";
                printedMethod += (method.access & Opcodes.ACC_ABSTRACT) != 0 ? "abstract " : "";
                printedMethod += (method.access & Opcodes.ACC_NATIVE) != 0 ? "native " : "";
                printedMethod += (method.access & Opcodes.ACC_SYNTHETIC) != 0 ? "synthetic " : "";
                printedMethod += method.name + method.desc;
                writer.println(" Method: " + printedMethod);
            }

            collection.getClassStream()
                    .findAny()
                    .ifPresent(s -> writer.println("  Type dependencies:"));
            collection.getClassStream()
                    .forEach(s -> writer.println("   " + s));

            var methodStrs = collection.getMethodStream()
                    .map(m -> classOnly ? m.getOwner() : m.toString())
                    .sorted()
                    .distinct()
                    .collect(Collectors.toUnmodifiableList());
            if (!methodStrs.isEmpty()) {
                writer.println("  Method invokes:");
                for (String s : methodStrs) {
                    writer.println("   " + s);
                }
            }
            var fieldStrs = collection.getFieldStream()
                    .map(f -> classOnly ? f.getOwner() : f.toString())
                    .sorted()
                    .distinct()
                    .collect(Collectors.toUnmodifiableList());
            if (!fieldStrs.isEmpty()) {
                writer.println("  Field accesses:");
                for (String s : fieldStrs) {
                    writer.println("   " + s);
                }
            }
            writer.println();
        }
    }

    @Parameters(commandNames = CommandListNoDeps.NAME,
            commandDescription = "List classes without any dependency in the target version. "
                    + "These classes in libcore/ojluni/ can be upgraded to the target version "
                    + "in a standalone way without upgrading the other classes.")
    private static class CommandListNoDeps {

        public static final String NAME = "list-no-deps";

        @Parameter(names = {"-t", "--target"}, description = "file path to a .jmod or .jar file or "
                + "one of the following OpenJDK version: 9, 11, 17, 21")
        public String classpath = "21";

        @Parameter(names = "-h", help = true, description = "Shows this help message")
        public boolean help = false;

        private void run() throws UncheckedIOException {
            if (!List.of("9", "11", "17", "21").contains(classpath)) {
                throw new IllegalArgumentException("Only 9, 11, 17, 21 java version is supported. "
                        + "This java version isn't supported: " + classpath);
            }
            int targetVersion = Integer.parseInt(classpath);

            Path cp = AndroidHostEnvUtil.parseInputClasspath(classpath);
            Path excludeClasspath = AndroidHostEnvUtil.parseInputClasspath("oj");
            DependencyAnalyzer analyzer = new DependencyAnalyzer(cp, excludeClasspath,
                    false, true);

            List<ExpectedUpstreamFile.ExpectedUpstreamEntry> entries;
            try {
                entries = new ExpectedUpstreamFile().readAllEntries();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            List<String> noDepClasses = new ArrayList<>();
            PrintWriter writer = new PrintWriter(System.out, /*autoFlush=*/true);
            for (ExpectedUpstreamFile.ExpectedUpstreamEntry entry : entries) {
                String path = entry.dstPath;
                if (!path.startsWith("ojluni/src/main/java/") || !path.endsWith(".java")) {
                    continue;
                }
                String jdkStr = entry.gitRef.split("/")[0];
                if (jdkStr.length() < 5) {
                    // ignore unparsable entry
                    continue;
                }

                int jdkVersion;
                try {
                    jdkVersion = Integer.parseInt(jdkStr.substring(
                            "jdk".length(), jdkStr.length() - 1));
                } catch (NumberFormatException e) {
                    // ignore unparsable entry
                    continue;
                }

                if (jdkVersion >= targetVersion) {
                    continue;
                }

                String className = path.substring("ojluni/src/main/java/".length(),
                        path.length() - ".java".length()).replace('/', '.');
                try {
                    DependencyAnalyzer.Result classResult = analyzer.analyze(List.of(className));
                    if (classResult.getAggregated().isEmpty()) {
                        noDepClasses.add(className);
                    }
                } catch (IllegalArgumentException e) {
                    // Print the classes not found. The classes are either not in java.base
                    // or removed in the target OpenJDK version.
                    writer.println("Class not found: " + className + " in " + cp.toString());
                }
            }

            writer.println("Classes with no deps: ");
            for (String name : noDepClasses) {
                writer.println(" " + name);
            }
        }
    }
    @Parameters(commandNames = CommandListNewApis.NAME,
            commandDescription = "List the new classes / methods / fields in java.base version.")
    private static class CommandListNewApis {

        public static final String NAME = "list-new-apis";

        @Parameter(names = {"-b", "--base"},
                description = "file path to a .jmod or .jar file or"
                        + "one of the following OpenJDK version: 8, 9, 11, 17, 21")
        public String base = "oj";

        @Parameter(names = {"-t", "--target"},
                description = "file path to a .jmod or .jar file or"
                        + "one of the following OpenJDK version: 8, 9, 11, 17, 21")
        public String classpath = "21";

        @Parameter(names = {"-d"},
                description = "Disable the API filters read from " + UnsupportedNewApis.FILE_NAME)
        public boolean disableFilter = false;

        @Parameter(names = "-h", help = true, description = "Shows this help message")
        public boolean help = false;

        private void run() throws UncheckedIOException {
            Path newClassPath = AndroidHostEnvUtil.parseInputClasspath(classpath);
            Path baseClasspath = AndroidHostEnvUtil.parseInputClasspath(base);

            SignaturesCollector collector = new SignaturesCollector();
            // Set up filter if it's enabled.
            Predicate<String> classNamePredicate;
            if (disableFilter) {
                classNamePredicate = (s) -> true;
            } else {
                UnsupportedNewApis unsupportedApis = UnsupportedNewApis.getInstance();
                classNamePredicate = Predicate.not(unsupportedApis::contains);
                collector.setFieldFilter(Predicate.not(unsupportedApis::contains));
                collector.setMethodFilter(Predicate.not(unsupportedApis::contains));
            }

            try (ZipFile baseZip = new ZipFile(baseClasspath.toFile());
                 ZipFile newZip = new ZipFile(newClassPath.toFile())) {

                Predicate<String> nameTest = Pattern.compile(
                        "^(classes/)?java(x)?/.*\\.class$").asMatchPredicate();
                var zipEntries = newZip.entries();
                while (zipEntries.hasMoreElements()) {
                    ZipEntry zipEntry = zipEntries.nextElement();
                    if (!nameTest.test(zipEntry.getName())) {
                        continue;
                    }

                    ClassNode newNode = readClassNode(newZip, zipEntry);
                    if (!isClassExposed(newZip, newNode, classNamePredicate)) {
                        continue;
                    }

                    ZipEntry baseEntry = ClassFileUtil.getEntryFromClassName(baseZip,
                            newNode.name, true);
                    String internalClassName = newNode.name;
                    // Add the class name if the entire class is missing.
                    if (baseEntry == null) {
                        collector.addClass(internalClassName);
                        continue;
                    }

                    ClassNode baseNode = readClassNode(baseZip, baseEntry);
                    DiffAnalyzer analyzer = DiffAnalyzer.analyze(baseNode, newNode);
                    for (FieldNode fieldNode : analyzer.newFields) {
                        collector.add(internalClassName, fieldNode);
                    }
                    for (MethodNode methodNode : analyzer.newMethods) {
                        collector.add(internalClassName, methodNode);
                    }
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            PrintWriter writer = new PrintWriter(System.out, true);
            SignaturesCollection collection = collector.getCollection();
            collection.getClassStream()
                    .forEach(writer::println);
            Stream.concat(
                collection.getMethodStream().map(SignaturesCollector.Method::toString),
                collection.getFieldStream().map(
                        f -> f.getOwner() + "#" + f.getName() + ":" + f.getDesc())
            ).sorted().forEach(writer::println);
        }

        private static ClassNode readClassNode(ZipFile zipFile, ZipEntry entry) throws IOException {
            try (InputStream in = zipFile.getInputStream(entry)) {
                return ClassFileUtil.parseClass(in);
            }
        }

        /**
         * Return true if the class is public / protected. However, if it's inner class, it returns
         * true only if the outer classes are all public / protected as well.
         */
        private static boolean isClassExposed(ZipFile zipFile, ClassNode node,
                Predicate<String> classNamePredicate) throws IOException {
            if (!DiffAnalyzer.isExposed(node.access) || !classNamePredicate.test(node.name)) {
                return false;
            }

            Optional<String> outerClass = node.innerClasses.stream()
                    .filter(inner -> node.name.equals(inner.name) && inner.outerName != null)
                    .map(innerClassNode -> innerClassNode.outerName)
                    .findFirst();

            if (outerClass.isEmpty()) {
                return true;
            }

            ZipEntry zipEntry = ClassFileUtil.getEntryFromClassName(zipFile, outerClass.get(),
                    true);
            if (zipEntry == null) {
                return true;
            }

            // TODO: Lookup in a cache before parsing the .class file.
            try (InputStream in = zipFile.getInputStream(zipEntry)) {
                ClassNode outerNode = ClassFileUtil.parseClass(in);
                return isClassExposed(zipFile, outerNode, classNamePredicate);
            }
        }
    }

    public static void main(String[] argv) {
        MainArgs mainArgs = new MainArgs();
        CommandDump commandDump = new CommandDump();
        CommandApiDiff commandApiDiff = new CommandApiDiff();
        CommandShowDeps commandShowDeps = new CommandShowDeps();
        CommandListNoDeps commandListNoDeps = new CommandListNoDeps();
        CommandListNewApis commandListNewApis = new CommandListNewApis();
        JCommander jCommander = JCommander.newBuilder()
                .addObject(mainArgs)
                .addCommand(commandDump)
                .addCommand(commandApiDiff)
                .addCommand(commandShowDeps)
                .addCommand(commandListNoDeps)
                .addCommand(commandListNewApis)
                .build();
        jCommander.parse(argv);

        if (mainArgs.help || jCommander.getParsedCommand() == null) {
            jCommander.usage();
            return;
        }

        switch (jCommander.getParsedCommand()) {
            case CommandDump.NAME:
                if (commandDump.help) {
                    jCommander.usage(CommandDump.NAME);
                } else {
                    commandDump.run();
                }
                break;
            case CommandApiDiff.NAME:
                if (commandApiDiff.help) {
                    jCommander.usage(CommandApiDiff.NAME);
                } else {
                    commandApiDiff.run();
                }
                break;
            case CommandShowDeps.NAME:
                if (commandShowDeps.help) {
                    jCommander.usage(CommandShowDeps.NAME);
                } else {
                    commandShowDeps.run();
                }
                break;
            case CommandListNoDeps.NAME:
                if (commandShowDeps.help) {
                    jCommander.usage(CommandShowDeps.NAME);
                } else {
                    commandListNoDeps.run();
                }
                break;
            case CommandListNewApis.NAME:
                if (commandListNewApis.help) {
                    jCommander.usage(CommandListNewApis.NAME);
                } else {
                    commandListNewApis.run();
                }
                break;
            default:
                throw new IllegalArgumentException("Unknown sub-command: " +
                        jCommander.getParsedCommand());
        }
    }
}
