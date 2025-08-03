import com.github.javaparser.utils.Log;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StatsGenerator {
    private static final Logger LOGGER = Logger.getLogger(StatsGenerator.class.toString());
    private static final boolean DEBUG = false;
    private static final Pattern SINGLE_ENTRY = Pattern.compile(
            "^\\s*((optional|repeated) )?(([a-zA-Z_0-9\\.]+)\\s+)?([a-zA-Z_0-9]+)\\s*=\\s*(\\-?\\d+)([^\\d;][^\\;]*)?;$");

    private final List<String> mAllImports = new ArrayList<>();
    private final File mRootPath;

    public StatsGenerator(File rootPath) {
        mRootPath = rootPath;
    }

    public void process(File atomFile, Set<File> atomExtensions, String module, String packageName,
            File outputFile) throws IOException {
        PrintStream output = new PrintStream(new FileOutputStream(outputFile));

        output.println("package " + packageName + ";");
        output.println();
        output.println("import android.util.StatsEvent;");
        output.println();

        String className = outputFile.getName();
        className = className.substring(0, className.indexOf("."));
        output.println("public class " + className + " { ");
        output.println();

        GroupEntry out = new GroupEntry(null);
        parseFile(out, atomFile);

        if (atomExtensions != null) {
            for (File ext : atomExtensions) {
                parseExtension(out, ext);
            }
        }

        GroupEntry atom = out.findGroup("atom");
        GroupEntry pulledGroup = atom.findGroup("pulled");
        List<SingleEntry> children = new ArrayList<>();
        children.addAll(atom.findGroup("pushed").getSingles());
        children.addAll(atom.findGroup("pulled").getSingles());

        for (SingleEntry e : atom.getSingles()) {
            if (e.extra.contains(module)) {
                e.writeTo("", output);
                output.println();

                System.out.println(">> " + out.findGroup(e.type) + "  " + e.type);
                printGroup(out.findGroup(e.type), output, convertToSymbolGroupPrefix(e.type));
                output.println();
                output.println();
            }
        }

        for (SingleEntry e : children) {
            if (e.extra.contains(module)) {
                e.writeTo("", output);
                output.println();

                if (DEBUG) System.out.println(">> " + out.findGroup(e.type) + "  " + e.type);
                printGroup(out.findGroup(e.type), output, convertToSymbolGroupPrefix(e.type));
                output.println();
                output.println();
            }
        }

        for (SingleEntry e : pulledGroup.getSingles()) {
            if (e.extra.contains(module)) {
                GroupEntry group = out.findGroup(e.type);
                output.println(group.constructBuildStatsEventMethod());
            }
        }

        // Add a Placeholder write method
        output.println("  // Placeholder code for local development only");
        output.println("  public static void write(int code, Object... params) { }");
        output.println();
        output.println("}");
        output.close();
    }

    private static void printGroup(GroupEntry entry, PrintStream output, String prefix) {
        for (SingleEntry e : entry.getSingles()) {
            GroupEntry subGroup = entry.findGroup(e.type);
            if (subGroup != null) {
                printGroup(subGroup, output, prefix + convertToSymbolGroupPrefix(e.name));
            } else {
                switch (e.type) {
                    case "bool":
                    case "int32":
                    case "int64":
                    case "float":
                    case "string":
                    case "null":    // In case of enum
                        e.writeTo(prefix, output);
                        break;
                    default:
                        LOGGER.warning("Type not found " + e);
                }
            }
        }
    }

    private static String convertToSymbolGroupPrefix(String name) {
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            name = name.substring(dot + 1);
        }
        return name.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase() + "__";
    }

    private String parseFile(GroupEntry out, File path) throws IOException {
        ArrayList<String> outImports = new ArrayList<>();
        String outerPath;
        try (MyReader reader = new MyReader(new BufferedReader(new FileReader(path)), outImports)) {
            parseGroup(out, reader, "");
            out.javaPackage = reader.javaPackage;
            outerPath = reader.rootPrefix;
        }
        parseImports(outImports, out, false);
        return outerPath;
    }

    private void parseImports(ArrayList<String> imports, GroupEntry out,
            boolean skipDuplicate) throws IOException {
        for (String p : imports) {
            if (mAllImports.contains(p) && skipDuplicate) {
                System.err.println("Importing already parsed file " + p);
                continue;
            }
            mAllImports.add(p);
            File importFile = new File(mRootPath, p);
            if (importFile.exists()) {
                GroupEntry grp = new GroupEntry(null);
                String pkg = parseFile(grp, importFile);

                GroupEntry grp2 = out.imports.get(pkg);
                if (grp2 == null) {
                    out.imports.put(pkg, grp);
                } else {
                    grp2.children.addAll(grp.children);
                    grp2.imports.putAll(grp.imports);
                }
            }
        }
    }

    private void parseExtension(GroupEntry out, File path) throws IOException {
        ArrayList<String> outImports = new ArrayList<>();
        try (MyReader reader = new MyReader(new BufferedReader(new FileReader(path)), outImports)) {
            String line = null;
            try {
                while (!(line = reader.getEntry()).startsWith("}")) {
                    if (line.endsWith("{")) {
                        String prefix = "";
                        if (DEBUG) System.out.println(prefix + " :: " + line);
                        String[] parts = line.split(" ", 3);

                        GroupEntry group = new GroupEntry(out.root);
                        group.name = parts[1];
                        group.type = parts[0];

                        GroupEntry existing = out.findGroup(group.name);
                        if (existing != null) {
                            if (!"extend".equals(group.type)) {
                                System.out.println("Found duplicated entry without extension");
                                continue;
                            }
                            parseGroup(existing, reader, prefix + "   ");
                        } else {
                            parseGroup(group, reader, prefix + "   ");
                            out.children.add(group);
                        }
                    }
                }
            } catch (RuntimeException e) {
                LOGGER.warning("Error at line " + line);
                throw e;
            }

            parseGroup(out, reader, "");
        }

        parseImports(outImports, out, true);
    }

    private static void parseGroup(GroupEntry out, MyReader reader, String prefix)
            throws IOException {
        String line = null;
        try {
            while (!(line = reader.getEntry()).startsWith("}")) {
                Entry entry;
                if (line.endsWith("{")) {
                    if (DEBUG) System.out.println(prefix + " :: " + line);
                    String[] parts = line.split(" ", 3);

                    GroupEntry group = new GroupEntry(out.root);
                    group.name = parts[1];
                    group.type = parts[0];

                    parseGroup(group, reader, prefix + "   ");
                    entry = group;
                } else {
                    String ot = line;
                    Matcher m = SINGLE_ENTRY.matcher(line.trim());
                    if (!m.matches()) {
                        continue;
                    }
                    SingleEntry singleEntry = new SingleEntry();
                    singleEntry.type = m.group(4) + "";
                    singleEntry.name = m.group(5);
                    singleEntry.value = m.group(6);
                    singleEntry.extra = m.group(7) + "";
                    entry = singleEntry;
                    if (DEBUG) System.out.println(prefix + " -- " + line);
                }

                out.children.add(entry);
            }
        } catch (RuntimeException e) {
            LOGGER.warning("Error at line " + line);
            throw e;
        }
    }

    private static class Entry {
        String type;
        String name;

        public String javaType() {
            switch (type) {
                case "bool":
                    return "boolean";
                case "int32":
                    return "int";
                case "int64":
                    return "long";
                case "float":
                    return "float";
                case "string":
                    return "String";
                default:
                    return "Object";
            }
        }

        /**
         * Convert {@code name} from lower_underscore_case to lowerCamelCase.
         *
         * The equivalent in guava would be {@code LOWER_UNDERSCORE.to(LOWER_CAMEL, name)}, but to
         * keep the build system simple we don't want to depend on guava.
         */
        public String javaName() {
            if (name.length() == 0) {
                return "";
            }
            StringBuilder sb = new StringBuilder(name.length());
            sb.append(name.charAt(0));
            boolean upperCaseNext = false;
            for (int i = 1; i < name.length(); i++) {
                char c = name.charAt(i);
                if (c == '_') {
                    upperCaseNext = true;
                } else {
                    if (upperCaseNext) {
                        c = Character.toUpperCase(c);
                    }
                    sb.append(c);
                    upperCaseNext = false;
                }
            }
            return sb.toString();
        }

        @Override
        public String toString() {
            return name + ":" + type;
        }
    }

    private static class SingleEntry extends Entry {
        String value;
        String extra;

        public void writeTo(String prefix, PrintStream output) {
            output.println("  public static final int "
                    + prefix + name.toUpperCase() + " = " + value + ";");
        }

        public String constructStatsEventWriter(String builderName, GroupEntry g) {
            switch (type) {
                case "bool":
                    return String.format("%s.writeBoolean(%s);", builderName, javaName());
                case "int32":
                    return String.format("%s.writeInt(%s);", builderName, javaName());
                case "int64":
                    return String.format("%s.writeLong(%s);", builderName, javaName());
                case "float":
                    return String.format("%s.writeFloat(%s);", builderName, javaName());
                case "string":
                    return String.format("%s.writeString(%s);", builderName, javaName());
                default:
                    LOGGER.warning("Type not found " + type + "  " + g.name);
                    return ";";
            }
        }
    }

    private static class GroupEntry extends Entry {
        final HashMap<String, GroupEntry> imports;
        final GroupEntry root;
        final ArrayList<Entry> children = new ArrayList<>();

        String javaPackage = "";

        public GroupEntry(GroupEntry root) {
            if (root == null) {
                this.root = this;
                this.imports = new HashMap<>();
            } else {
                this.root = root;
                this.imports = root.imports;
            }
        }

        public GroupEntry findGroup(String name) {
            for (Entry e : children) {
                if (e.name.equalsIgnoreCase(name)) {
                    return (GroupEntry) e;
                }
            }
            if (root != this) {
                GroupEntry e = root.findGroup(name);
                if (e != null) {
                    return e;
                }
            }
            if (name.indexOf(".") >= 0) {
                // Look in imports
                String pkg = name.substring(0, name.lastIndexOf(".") + 1);
                String key = name.substring(name.lastIndexOf(".") + 1);

                GroupEntry imp = imports.get(pkg);
                if (imp != null) {
                    return imp.findGroup(key);
                }
                // Try import with a subclass packageName
                if (javaPackage != null) {
                    imp = imports.get(javaPackage + pkg);
                    if (imp != null) {
                        return imp.findGroup(key);
                    }
                }
            }
            return null;
        }

        public List<SingleEntry> getSingles() {
            List<SingleEntry> result = new ArrayList<>();
            for (Entry e : children) {
                if (e instanceof SingleEntry) {
                    result.add((SingleEntry) e);
                }
            }
            return result;
        }

        public String constructBuildStatsEventMethod() {
            StringJoiner responseBuilder = new StringJoiner("\n");
            responseBuilder.add("  // Placeholder code for local development only");
            StringJoiner argBuilder = new StringJoiner(", ");
            getSingles().forEach(entry -> argBuilder.add(
                    entry.javaType() + " " + entry.javaName()));


            String signature = String.format(
                    "  public static StatsEvent buildStatsEvent(int code, %s){", argBuilder);

            responseBuilder.add(signature)
                    .add("      final StatsEvent.Builder builder = StatsEvent.newBuilder();")
                    .add("      builder.setAtomId(code);");
            getSingles().stream().map(
                    entry -> entry.constructStatsEventWriter("      builder",this)).forEach(
                    responseBuilder::add);

            return responseBuilder.add("      return builder.build();")
                    .add("  }").toString();
        }
    }

    private static class MyReader implements Closeable {

        final List<String> outImports;
        final BufferedReader reader;

        String rootPrefix = "";
        String javaPackage = "";
        String javaOuterClassName = "";
        boolean javaMultipleFiles = false;

        boolean started = false;
        boolean finished = false;

        MyReader(BufferedReader reader, List<String> outImport) {
            this.reader = reader;
            this.outImports = outImport;
        }

        private String extractQuotes(String line) {
            Pattern p = Pattern.compile("\"([^\"]*)\"");
            Matcher m = p.matcher(line);
            return m.find() ? m.group(1) : "";
        }

        private String parseHeaders() throws IOException {
            String line = getEntry();
            if (line.startsWith("message") || line.equals("}")
                    || line.startsWith("enum") || line.startsWith("extend")) {
                return line;
            }
            if (line.startsWith("import")) {
                String impSrc = extractQuotes(line);
                if (!impSrc.isEmpty()) {
                    outImports.add(impSrc);
                }
            } else if (line.startsWith("option")) {
                if (line.contains(" java_package ")) {
                    rootPrefix = extractQuotes(line) + ".";
                    javaPackage = rootPrefix;
                } else if (line.contains(" java_outer_classname ")) {
                    javaOuterClassName = extractQuotes(line);
                } else if (line.contains(" java_multiple_files ")) {
                    javaMultipleFiles = line.contains("true");
                }
            } else if (line.startsWith("package")) {
                rootPrefix = line.split(" ")[1].split(";")[0].trim() + ".";
                javaPackage = rootPrefix;
            }
            return parseHeaders();
        }

        private void onHeaderParseComplete() {
            if (!javaMultipleFiles && !javaOuterClassName.isEmpty()) {
                rootPrefix = rootPrefix + javaOuterClassName + ".";
            }
        }

        String getEntry() throws IOException {
            if (!started) {
                started = true;
                String entry = parseHeaders();
                onHeaderParseComplete();
                return entry;
            }
            String line = reader.readLine();

            if (line == null) {
                // Finished everything
                finished = true;
                return "}";
            }

            line = line.trim();

            // Skip comments
            int commentIndex = line.indexOf("//");
            if (commentIndex > -1) {
                line = line.substring(0, commentIndex).trim();
            }

            if (line.startsWith("/*")) {
                while (!line.contains("*/")) line = reader.readLine().trim();
                line = getEntry();
            }

            if (!line.endsWith("{") && !line.endsWith(";") && !line.endsWith("}")) {
                line = line + " " + getEntry();
            }
            return line.trim();
        }

        @Override
        public void close() throws IOException {
            reader.close();
        }
    }
}
