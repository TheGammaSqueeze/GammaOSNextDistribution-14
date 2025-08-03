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

package libcore.tools.analyzer.openjdk;

import static java.util.stream.Collectors.toMap;

import libcore.tools.analyzer.openjdk.SignaturesCollector.MemberInfo;
import libcore.tools.analyzer.openjdk.SignaturesCollector.SignaturesCollection;

import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Dependency analyzer of a class in a OpenJDK version specified in the classpath.
 *
 * @see #analyze(List)
 *
 */
public class DependencyAnalyzer {
    private final Path mClassPath;
    private final Path mExcludeClasspath;
    private final boolean mIncludeInternal;
    private final boolean mUsesExpectedUpstreamAsBaseDeps;

    /**
     * The public constructor with the analyzing configs.
     *
     * @param classpath must contains the classes in {@code classesOrPackages}
     * @param excludeClasspath see {@link ExcludeClasspathFilter}
     * @param includeInternal Dependency on the {@code classesOrPackages} are not included in the
     *                        {@link Result}
     * @param usesExpectedUpstreamAsBaseDeps see {@link ExpectedUpstreamFilter}
     *
     * @see #analyze(List)
     */
    public DependencyAnalyzer(Path classpath, Path excludeClasspath, boolean includeInternal,
            boolean usesExpectedUpstreamAsBaseDeps) {
        mClassPath = classpath;
        mExcludeClasspath = excludeClasspath;
        mIncludeInternal = includeInternal;
        mUsesExpectedUpstreamAsBaseDeps = usesExpectedUpstreamAsBaseDeps;
    }

    /**
     * Analyze the dependencies of classes.
     *
     * @param classesOrPackages a list of classes and / or packages.
     * @throws IllegalArgumentException {@code classesOrPackages} is not found in the class path.
     * @throws UncheckedIOException error occurs when reading and parsing the .class files in
     * the given classpaths.
     */
    public Result analyze(List<String> classesOrPackages) throws IllegalArgumentException,
            UncheckedIOException {
        try (ZipFile zipFile = new ZipFile(mClassPath.toFile());
             ExcludeClasspathFilter excludeFilter = ExcludeClasspathFilter
                     .getInstance(mExcludeClasspath);
             ExpectedUpstreamFilters expectedUpstreamFilters =
                     ExpectedUpstreamFilters.getInstance(mUsesExpectedUpstreamAsBaseDeps)) {
            List<ZipEntry> zipEntries = new ArrayList<>(classesOrPackages.size());
            for (String classOrPackage : classesOrPackages) {
                zipEntries.addAll(getEntriesFromNameOrThrow(zipFile, mClassPath, classOrPackage));
            }
            // Collect all class names by reading zip entry name.
            // We could also read the class names to acquire the true class names, but
            // it will take extra I/O and .class file parsing.
            Set<String> classNames = zipEntries.stream()
                    .map(ZipEntry::getName)
                    .map(s -> s.substring(0, s.length() - ".class".length()))
                    .map(s -> s.startsWith("classes/") ? s.substring("classes/".length()) : s)
                    .collect(Collectors.toSet());

            Predicate<String> baseClassNamePredicate = (s) ->
                    mIncludeInternal || !classNames.contains(s);
            baseClassNamePredicate = baseClassNamePredicate.and(excludeFilter::testClass);

            Predicate<SignaturesCollector.Method> baseMethodPredicate = (m) ->
                    mIncludeInternal || !classNames.contains(m.getOwner());
            baseMethodPredicate = baseMethodPredicate.and(excludeFilter);

            Predicate<SignaturesCollector.Field> baseFieldPredicate = (m) ->
                    mIncludeInternal || !classNames.contains(m.getOwner());
            baseFieldPredicate = baseFieldPredicate.and(excludeFilter);

            Result result = new Result();
            SignaturesCollector topCollector = new SignaturesCollector();
            for (ZipEntry entry : zipEntries) {
                try (InputStream in = zipFile.getInputStream(entry)) {
                    ClassNode classNode = ClassFileUtil.parseClass(in);
                    // Create predicates using the expectedUpstreamFilter filter
                    ExpectedUpstreamFilter expectedUpstreamFilter = expectedUpstreamFilters
                            .getFilter(classNode.name);
                    Predicate<String> classNamePredicate = baseClassNamePredicate
                            .and(expectedUpstreamFilter::testClass);
                    topCollector.setClassFilter(classNamePredicate);

                    Predicate<SignaturesCollector.Method> methodPredicate = baseMethodPredicate
                            .and(expectedUpstreamFilter);

                    Predicate<SignaturesCollector.Field> fieldPredicate = baseFieldPredicate
                            .and(expectedUpstreamFilter);

                    // Scan the super classes / interfaces and field types.
                    topCollector.addClassesFromClassNode(classNode);
                    List<MethodNode> methods = new ArrayList<>(classNode.methods);
                    methods.sort(Comparator.comparing(n -> n.name + n.desc));
                    for (MethodNode method : methods) {
                        SignaturesCollector collector = new SignaturesCollector();
                        collector.setMethodFilter(methodPredicate);
                        collector.setFieldFilter(fieldPredicate);
                        for (AbstractInsnNode insn : method.instructions) {
                            if (insn instanceof MethodInsnNode) {
                                collector.add((MethodInsnNode) insn);
                            } else if (insn instanceof FieldInsnNode) {
                                collector.add((FieldInsnNode) insn);
                            } else if (insn instanceof InvokeDynamicInsnNode) {
                                collector.add((InvokeDynamicInsnNode) insn);
                            }
                        }
                        SignaturesCollection collection = collector.getCollection();
                        result.addMethodDependency(classNode, method, collection);
                        topCollector.add(collection);
                    }
                }
            }
            result.setAggregatedDependency(topCollector.getCollection());

            return result;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * {@link #getDetails()} and {@link #getAggregated()} return the dependencies.
     */
    public static class Result {

        public static class MethodDependency {
            public final MethodNode mNode;
            public final SignaturesCollection mDependency;

            private MethodDependency(MethodNode node, SignaturesCollection dependency) {
                mNode = node;
                mDependency = dependency;
            }
        }

        /**
         * @return dependencies of every method in each class. The returned
         * {@link SignaturesCollection} doesn't contain type dependencies, i.e.
         * {@link SignaturesCollection#getClassStream()} returns an empty stream.
         */
        public Map<String, List<MethodDependency>> getDetails() {
            return mDetails;
        }

        public SignaturesCollection getAggregated() {
            return mAggregated;
        }

        private SignaturesCollection mAggregated;

        private final Map<String, List<MethodDependency>> mDetails =
                new LinkedHashMap<>();

        private Result() {}

        private void setAggregatedDependency(SignaturesCollection dependency) {
            this.mAggregated = dependency;
        }

        private void addMethodDependency(ClassNode classNode, MethodNode node,
                SignaturesCollection dependency) {
            List<MethodDependency> list = mDetails.computeIfAbsent(classNode.name,
                    k -> new ArrayList<>());

            list.add(new MethodDependency(node, dependency));
        }
    }

    private static List<ZipEntry> getEntriesFromNameOrThrow(ZipFile zipFile, Path zipPath,
            String classOrPackage) throws IllegalArgumentException {
        String part = classOrPackage.replaceAll("\\.", "/");
        String[] prefixes = new String[] {
                part + ".class",
                "classes/" + part + ".class",
                // inner class
                part + "$",
                "classes/" + part + "$",
                // package
                part + "/",
                "classes/" + part + "/",

        };
        List<ZipEntry> entries = zipFile.stream()
                .filter(zipEntry -> zipEntry.getName().endsWith(".class"))
                .filter(zipEntry -> Arrays.stream(prefixes)
                        .anyMatch(prefix -> zipEntry.getName().startsWith(prefix)))
                .collect(Collectors.toUnmodifiableList());
        if (entries.isEmpty()) {
            throw new IllegalArgumentException(String.format(Locale.US,
                    "%s has no class files with a prefix of %s", zipPath.toString(),
                    String.join(", ", prefixes)));
        }
        return entries;
    }

    /**
     * Filter the classes / methods / fields that exists in the given {@link classpath}.
     */
    static class ExcludeClasspathFilter implements Predicate<MemberInfo>, Closeable {
        protected final Path classpath;

        public static ExcludeClasspathFilter getInstance(Path classpath)
                throws IOException {
            if (classpath == null) {
                return new ExcludeClasspathFilter(null);
            }
            return new Impl(classpath);
        }

        private ExcludeClasspathFilter(Path classpath) {
            this.classpath = classpath;
        }

        @Override
        public void close() throws IOException {
        }

        @Override
        public boolean test(MemberInfo methodOrField) {
            return true;
        }

        protected boolean hasClass(String internalClassName) {
            return false;
        }

        /**
         * Return true if the class is not found in the classpath. Return false if the class
         * is a primitive type or null.
         */
        public final boolean testClass(String internalClassName) {
            return !hasClass(internalClassName);
        }

        private static class Impl extends ExcludeClasspathFilter {

            private static final ClassNode PLACEHOLDER = new ClassNode();

            private final ZipFile zipFile;

            private final HashMap<String, ClassNode> classNodes = new HashMap<>();

            private Impl(Path classpath) throws IOException {
                super(classpath);
                this.zipFile = new ZipFile(classpath.toFile());
            }

            @Override
            public void close() throws IOException {
                zipFile.close();
            }

            /**
             * {@inheritDoc}
             */
            @Override
            public boolean test(MemberInfo methodOrField) {
                String className = methodOrField.getOwner();

                List<ClassNode> nodes = getClassNodes(className);
                if (nodes.isEmpty()) {
                    return true;
                }

                if (methodOrField instanceof SignaturesCollector.Method) {
                    SignaturesCollector.Method i = (SignaturesCollector.Method) methodOrField;
                    return nodes.stream()
                            .flatMap(n -> n.methods.stream())
                            .noneMatch(m -> i.getName().equals(m.name) && i.getDesc().equals(m.desc));
                } else {
                    SignaturesCollector.Field i = (SignaturesCollector.Field) methodOrField;
                    return nodes.stream()
                            .flatMap(n -> n.fields.stream())
                            .noneMatch(f -> i.getName().equals(f.name) && i.getDesc().equals(f.desc));
                }
            }

            /**
             * @return all {@link ClassNode} in the class hierarchy.
             */
            private List<ClassNode> getClassNodes(String className) {
                // There isn't a .class file for an Array class.
                // Look for an Object class instead.
                if (className.startsWith("[")) {
                    className = "java/lang/Object";
                }
                Set<String> visited = new HashSet<>();
                Queue<String> queue = new LinkedList<>();
                List<ClassNode> results = new ArrayList<>();
                queue.add(className);
                while (!queue.isEmpty()) {
                    String next = queue.poll();
                    if (visited.contains(next)) {
                        continue;
                    }
                    visited.add(next);
                    ClassNode node = getClassNode(next);
                    if (node == null) {
                        continue;
                    }
                    results.add(node);
                    if (node.superName != null) {
                        queue.add(node.superName);
                    }
                    queue.addAll(node.interfaces);
                }
                return results;
            }

            private ClassNode getClassNode(String className) {
                ClassNode val = classNodes.get(className);
                if (val == PLACEHOLDER) {
                    return null;
                } else if (val != null) {
                    return val;
                }

                ZipEntry entry = ClassFileUtil.getEntryFromClassName(zipFile, className, true);
                if (entry == null) {
                    classNodes.put(className, PLACEHOLDER);
                    return null;
                }

                try {
                    val = ClassFileUtil.parseClass(zipFile.getInputStream(entry));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }

                classNodes.put(className, val);
                return val;
            }

            @Override
            protected boolean hasClass(String className) {
                ClassNode val = classNodes.get(className);
                if (val == PLACEHOLDER) {
                    return false;
                } else if (val != null) {
                    return true;
                }

                ZipEntry e = ClassFileUtil.getEntryFromClassName(zipFile, className, true);
                if (e == null) {
                    classNodes.put(className, PLACEHOLDER);
                    return false;
                }

                return true;
            }
        }
    }

    private static class ExpectedUpstreamFilters implements Closeable {

        private static final ExpectedUpstreamFilter PASS_THROUGH_FILTER =
                new ExpectedUpstreamFilter(null);

        private final Map<String, ZipFile> mZipFiles = new HashMap<>();
        private final Map<String, ExpectedUpstreamFilter> mFiltersCache = new HashMap<>();
        private final Map<String, ExpectedUpstreamFile.ExpectedUpstreamEntry> mEntries;
        private final boolean mEnabled;

        public static ExpectedUpstreamFilters getInstance(boolean usesExpectedUpstreamAsBaseDeps)
                throws IOException {
            return new ExpectedUpstreamFilters(usesExpectedUpstreamAsBaseDeps);
        }

        private ExpectedUpstreamFilters(boolean usesExpectedUpstreamAsBaseDeps) throws IOException {
            this.mEnabled = usesExpectedUpstreamAsBaseDeps;
            List<ExpectedUpstreamFile.ExpectedUpstreamEntry> entries =
                    new ExpectedUpstreamFile().readAllEntries();
            this.mEntries = entries.stream()
                    .collect(toMap(e -> e.dstPath, e -> e));
        }

        /**
         * @return non-null instance
         */
        public ExpectedUpstreamFilter getFilter(String internalClassName)
                throws UncheckedIOException {
            if (!mEnabled) {
                return PASS_THROUGH_FILTER;
            }

            ExpectedUpstreamFilter filter = mFiltersCache.get(internalClassName);
            if (filter != null) {
                return filter;
            }

            SignaturesCollection signatures;
            try {
                signatures = getSignatureCollection(internalClassName);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            if (signatures == null) {
                filter = PASS_THROUGH_FILTER;
            } else {
                filter = new ExpectedUpstreamFilter(signatures);
            }

            mFiltersCache.put(internalClassName, filter);
            return filter;
        }

        private ZipFile getZipFile(String jdkVersion) throws IOException {
            ZipFile result = mZipFiles.get(jdkVersion);
            if (result != null) {
                return result;
            }

            Path classpath;
            switch (jdkVersion) {
                case "jdk8u":
                    classpath = AndroidHostEnvUtil.parseInputClasspath("8");
                    break;
                case "jdk9u":
                    classpath = AndroidHostEnvUtil.parseInputClasspath("9");
                    break;
                case "jdk11u":
                    classpath = AndroidHostEnvUtil.parseInputClasspath("11");
                    break;
                case "jdk17u":
                    classpath = AndroidHostEnvUtil.parseInputClasspath("17");
                    break;
                case "jdk21u":
                    classpath = AndroidHostEnvUtil.parseInputClasspath("21");
                    break;
                default:
                    // unrecognized java version. Not supported until we obtain a specific
                    // java.base.jmod file from the specific git revision.
                    return null;
            }

            result = new ZipFile(classpath.toFile());
            mZipFiles.put(jdkVersion, result);
            return result;
        }
        /**
         *
         * @param internalClassName class name with '/' separator
         */
        private ClassNode getClassNode(String internalClassName) throws IOException {
            String outerClassName = internalClassName.split("\\$")[0];
            String dstPath = "ojluni/src/main/java/" + outerClassName + ".java";

            ExpectedUpstreamFile.ExpectedUpstreamEntry e = mEntries.get(dstPath);
            if (e == null) {
                return null;
            }

            String jdkVersion = e.gitRef.split("/")[0];
            ZipFile zipFile = getZipFile(jdkVersion);
            if (zipFile == null) {
                return null;
            }

            ZipEntry zipEntry = ClassFileUtil.getEntryFromClassName(zipFile,
                    internalClassName, true);
            if (zipEntry == null) {
                return null;
            }
            try (InputStream in = zipFile.getInputStream(zipEntry)) {
                return ClassFileUtil.parseClass(in);
            }
        }

        private SignaturesCollection getSignatureCollection(String internalClassName)
                throws IOException {
            ClassNode node = getClassNode(internalClassName);
            if (node == null) {
                return null;
            }

            SignaturesCollector collector = new SignaturesCollector();
            collector.addClassesFromClassNode(node);
            for (MethodNode method : node.methods) {
                for (AbstractInsnNode inst : method.instructions) {
                    if (inst instanceof FieldInsnNode) {
                        collector.add((FieldInsnNode) inst);
                    } else if (inst instanceof MethodInsnNode) {
                        collector.add((MethodInsnNode) inst);
                    } else if (inst instanceof InvokeDynamicInsnNode) {
                        collector.add((InvokeDynamicInsnNode) inst);
                    }
                }
            }

            return collector.getCollection();
        }

        @Override
        public void close() throws IOException {
            for (ZipFile zip : mZipFiles.values()) {
                zip.close();
            }
            mZipFiles.clear();
        }
    }

    /**
     * This class filter the dependency that exist in the expected upstream version of
     * the given class, specified in the libcore/EXPECTED_UPSTREAM file. Internally, the dependency
     * are stored as {@code mSignatures}.
     * Such dependencies are likely to be eliminated / replaced in the current libcore version
     * even though the new OpenJDK version still depends on them, and is likely not needed
     * when the class is upgraded to the target OpenJDK version.
     */
    static class ExpectedUpstreamFilter implements Predicate<MemberInfo> {

        /**
         * It contains mappings of classes and / or packages renamed in a new OpenJDK version.
         */
        private static final Map<String, String> PREFIX_RENAMES = new HashMap<>() {{
            put("sun/", "jdk/internal/");
        }};

        /**
         * Contains the dependency of a given class in the expected upstream version.
         */
        private final SignaturesCollection mSignatures;

        public ExpectedUpstreamFilter(SignaturesCollection signatures) {
            this.mSignatures = signatures;
        }

        /**
         * Return false if the type dependency exists in the expected upstream version.
         */
        public boolean testClass(String internalClassName) {
            if (mSignatures == null) {
                return true;
            }

            if (mSignatures.containsClass(internalClassName)) {
                return false;
            }


            for (var e : PREFIX_RENAMES.entrySet()) {
                String oldPrefix = e.getKey();
                String newPrefix = e.getValue();
                String className = internalClassName;
                if (!className.startsWith(newPrefix)) {
                    continue;
                }
                className = oldPrefix + className.substring(newPrefix.length());
                if (mSignatures.containsClass(className)) {
                    return false;
                }
            }

            return true;
        }

        /**
         * Returns false if the method / field dependency exist in the expected upstream version.
         */
        @Override
        public boolean test(MemberInfo member) {
            if (mSignatures == null) {
                return true;
            }

            if (mSignatures.contains(member)) {
                return false;
            }

            for (var e : PREFIX_RENAMES.entrySet()) {
                String newPrefix = e.getValue();
                boolean hasPrefixInOwner = member.getOwner().startsWith(newPrefix);
                if (!hasPrefixInOwner && !member.getDesc().contains(newPrefix)) {
                    continue;
                }
                String oldPrefix = e.getKey();
                String owner = member.getOwner();
                if (hasPrefixInOwner) {
                    owner = oldPrefix + owner.substring(newPrefix.length());
                }
                String desc = member.getDesc().replace(newPrefix, oldPrefix);
                String name = member.getName();

                if (member instanceof SignaturesCollector.Method) {
                    if (mSignatures.containsMethod(owner, name, desc)) {
                        return false;
                    }
                } else {
                    if (mSignatures.containsField(owner, name, desc)) {
                        return false;
                    }
                }
            }

            return true;
        }
    }
}
