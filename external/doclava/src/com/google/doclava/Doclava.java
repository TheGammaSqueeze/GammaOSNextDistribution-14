/*
 * Copyright (C) 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.doclava;

import static java.util.stream.Collectors.toList;

import com.google.clearsilver.jsilver.JSilver;
import com.google.clearsilver.jsilver.data.Data;
import com.google.clearsilver.jsilver.resourceloader.ClassResourceLoader;
import com.google.clearsilver.jsilver.resourceloader.CompositeResourceLoader;
import com.google.clearsilver.jsilver.resourceloader.FileSystemResourceLoader;
import com.google.clearsilver.jsilver.resourceloader.ResourceLoader;
import com.google.doclava.Errors.ErrorMessage;
import com.google.doclava.Errors.LintBaselineEntry;
import com.google.doclava.javadoc.RootDocImpl;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.Doc;
import com.sun.javadoc.MemberDoc;
import com.sun.javadoc.RootDoc;
import com.sun.javadoc.Type;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.lang.model.SourceVersion;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;

public class Doclava implements Doclet {

  private static final String SDK_CONSTANT_ANNOTATION = "android.annotation.SdkConstant";
  private static final String SDK_CONSTANT_TYPE_ACTIVITY_ACTION =
      "android.annotation.SdkConstant.SdkConstantType.ACTIVITY_INTENT_ACTION";
  private static final String SDK_CONSTANT_TYPE_BROADCAST_ACTION =
      "android.annotation.SdkConstant.SdkConstantType.BROADCAST_INTENT_ACTION";
  private static final String SDK_CONSTANT_TYPE_SERVICE_ACTION =
      "android.annotation.SdkConstant.SdkConstantType.SERVICE_ACTION";
  private static final String SDK_CONSTANT_TYPE_CATEGORY =
      "android.annotation.SdkConstant.SdkConstantType.INTENT_CATEGORY";
  private static final String SDK_CONSTANT_TYPE_FEATURE =
      "android.annotation.SdkConstant.SdkConstantType.FEATURE";
  private static final String SDK_WIDGET_ANNOTATION = "android.annotation.Widget";
  private static final String SDK_LAYOUT_ANNOTATION = "android.annotation.Layout";

  private static final int TYPE_NONE = 0;
  private static final int TYPE_WIDGET = 1;
  private static final int TYPE_LAYOUT = 2;
  private static final int TYPE_LAYOUT_PARAM = 3;

  public static final int SHOW_PUBLIC = 0x00000001;
  public static final int SHOW_PROTECTED = 0x00000003;
  public static final int SHOW_PACKAGE = 0x00000007;
  public static final int SHOW_PRIVATE = 0x0000000f;
  public static final int SHOW_HIDDEN = 0x0000001f;

  public static int showLevel = SHOW_PROTECTED;

  public static final boolean SORT_BY_NAV_GROUPS = true;
  /* Debug output for PageMetadata, format urls from site root */
  public static boolean META_DBG=false;
  /* Generate the static html docs with devsite tempating only */
  public static boolean DEVSITE_STATIC_ONLY = false;
  /* Don't resolve @link refs found in devsite pages */
  public static boolean DEVSITE_IGNORE_JDLINKS = false;
  /* Show Preview navigation and process preview docs */
  public static boolean INCLUDE_PREVIEW = false;
  /* output en, es, ja without parent intl/ container */
  public static boolean USE_DEVSITE_LOCALE_OUTPUT_PATHS = false;
  /* generate navtree.js without other docs */
  public static boolean NAVTREE_ONLY = false;
  /* Generate reference navtree.js with all inherited members */
  public static boolean AT_LINKS_NAVTREE = false;
  public static boolean METALAVA_API_SINCE = false;
  public static String outputPathBase = "/";
  public static ArrayList<String> inputPathHtmlDirs = new ArrayList<String>();
  public static ArrayList<String> inputPathHtmlDir2 = new ArrayList<String>();
  public static String inputPathResourcesDir;
  public static String outputPathResourcesDir;
  public static String outputPathHtmlDirs;
  public static String outputPathHtmlDir2;
  /* Javadoc output directory and included in url path */
  public static String javadocDir = "reference/";
  public static String htmlExtension;

  public static RootDoc root;
  public static ArrayList<String[]> mHDFData = new ArrayList<String[]>();
  public static List<PageMetadata.Node> sTaglist = new ArrayList<PageMetadata.Node>();
  public static ArrayList<SampleCode> sampleCodes = new ArrayList<SampleCode>();
  public static ArrayList<SampleCode> sampleCodeGroups = new ArrayList<SampleCode>();
  public static Data samplesNavTree;
  public static Map<Character, String> escapeChars = new HashMap<Character, String>();
  public static String title = "";
  public static SinceTagger sinceTagger = new SinceTagger();
  public static ArtifactTagger artifactTagger = new ArtifactTagger();
  public static HashSet<String> knownTags = new HashSet<String>();
  public static FederationTagger federationTagger = new FederationTagger();
  public static boolean showUnannotated = false;
  public static Set<String> showAnnotations = new HashSet<String>();
  public static Set<String> hideAnnotations = new HashSet<String>();
  public static boolean showAnnotationOverridesVisibility = false;
  public static Set<String> hiddenPackages = new HashSet<String>();
  public static boolean includeAssets = true;
  public static boolean includeDefaultAssets = true;
  private static boolean generateDocs = true;
  private static boolean parseComments = false;
  private static String yamlNavFile = null;
  public static boolean documentAnnotations = false;
  public static String documentAnnotationsPath = null;
  public static Map<String, String> annotationDocumentationMap = null;
  public static boolean referenceOnly = false;
  public static boolean staticOnly = false;
  public static boolean yamlV2 = false; /* whether to build the new version of the yaml file */
  public static boolean devsite = false; /* whether to build docs for devsite */
  public static AuxSource auxSource = new EmptyAuxSource();
  public static Linter linter = new EmptyLinter();
  public static boolean android = false;
  public static String manifestFile = null;
  public static String compatConfig = null;
  public static Map<String, String> manifestPermissions = new HashMap<>();

  public static JSilver jSilver = null;

  //API reference extensions
  private static boolean gmsRef = false;
  private static boolean gcmRef = false;
  public static String libraryRoot = null;
  private static boolean samplesRef = false;
  private static boolean sac = false;

    private static ArrayList<String> knownTagsFiles = new ArrayList<>();
    private static String keepListFile;
    private static String proguardFile;
    private static String proofreadFile;
    private static String todoFile;
    private static String lintBaselineFile;
    private static String stubsDir;
    private static HashSet<String> stubPackages;
    private static HashSet<String> stubImportPackages;
    private static boolean stubSourceOnly;
    private static boolean keepStubComments;
    private static String sdkValuePath;
    private static String apiFile;
    private static String dexApiFile;
    private static String removedApiFile;
    private static String removedDexApiFile;
    private static String exactApiFile;
    private static String privateApiFile;
    private static String privateDexApiFile;
    private static String apiMappingFile;
    private static boolean offlineMode;

    @Override
    public void init(Locale locale, Reporter reporter) {
        keepListFile = null;
        proguardFile = null;
        proofreadFile = null;
        todoFile = null;
        sdkValuePath = null;
        stubsDir = null;
        // Create the dependency graph for the stubs  directory
        offlineMode = false;
        apiFile = null;
        dexApiFile = null;
        removedApiFile = null;
        removedDexApiFile = null;
        exactApiFile = null;
        privateApiFile = null;
        privateDexApiFile = null;
        apiMappingFile = null;
        stubPackages = null;
        stubImportPackages = null;
        stubSourceOnly = false;
        keepStubComments = false;
    }

    @Override
    public String getName() {
        return "Doclava";
    }

    /**
     * @implNote
     * {@code -overview} option used to be a built-in parameter in javadoc
     * tool, and with new Doclet APIs it was moved to
     * {@link jdk.javadoc.doclet.StandardDoclet}, so we have to implement this
     * functionality by ourselves.
     */
    @Override
    public Set<? extends Option> getSupportedOptions() {
        Set<Doclet.Option> options = new HashSet<>();

        options.add(
                new Option() {
                    private final List<String> names = List.of("-overview");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() {
                        return "Pick overview documentation from HTML file";
                    }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<file>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        // TODO(nikitai): implement "overview" file inclusion.
                        //  This used to be built in javadoc tool but in new Doclet APIs it was
                        //  removed from default functionality and moved to StandardDoclet
                        //  implementation. In our case we need to implement this on our own.
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-d");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() {
                        return "Destination directory for output files";
                    }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<directory>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        outputPathBase = outputPathHtmlDirs = ClearPage.outputDir
                                = arguments.get(0);
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-templatedir");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() {
                        return "Templates for jSilver template engine used to generate docs";
                    }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<directory>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        ClearPage.addTemplateDir(arguments.get(0));
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-hdf");
                    @Override public int          getArgumentCount() { return 2; }
                    @Override public String       getDescription() {
                        return """
                                Doclava uses the jSilver template engine to render docs. This
                                option adds a key-value pair to the global data holder object which
                                is passed to all render calls. Think of it as a list of default
                                parameters for jSilver.""";
                    }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<key> <value>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        mHDFData.add(new String[] { arguments.get(0), arguments.get(1) });
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-knowntags");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() {
                        return """
                                List of non-standard tags used in sources.
                                Example: ${ANDROID_BUILD_TOP}/libcore/known_oj_tags.txt""";
                    }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<file>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        knownTagsFiles.add(arguments.get(0));
                        return true; }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-apidocsdir");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() {
                        return """
                                Javadoc output directory path relative to root, which is specified \
                                with '-d root'

                                Default value: 'reference/'""";
                    }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<path>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        javadocDir = arguments.get(0);
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-toroot");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() {
                        return """
                                Relative path to documentation root.
                                If set, use <path> as a (relative or absolute) link to \
                                documentation root in .html pages.

                                If not set, an auto-generated path traversal links will be used, \
                                e.g. “../../../”.
                                """;
                    }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<path>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        ClearPage.toroot = arguments.get(0);
                        return true; }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-samplecode");
                    @Override public int          getArgumentCount() { return 3; }
                    @Override public String       getDescription() {
                        return """
                                Adds a browsable sample code project from <source> directory under \
                                <dest> path relative to root (specified with '-d' <directory>) and \
                                named <title>.
                                """;
                    }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() {
                        return "<source> <dest> <title>";
                    }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        sampleCodes.add(new SampleCode(arguments.get(0), arguments.get(1), arguments.get(2)));
                        samplesRef = true;
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-samplegroup");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() {
                        return "Add a sample code project group";
                    }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<group>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        sampleCodeGroups.add(new SampleCode(null, null, arguments.get(0)));
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-samplesdir");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() {
                        return """
                                Directory where to look for samples. Android uses \
                                ${ANDROID_BUILD_TOP}/development/samples/browseable.
                                """;
                    }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<directory>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        samplesRef = true;
                        getSampleProjects(new File(arguments.get(0)));
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-htmldir");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<path>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        inputPathHtmlDirs.add(arguments.get(0));
                        ClearPage.htmlDirs = inputPathHtmlDirs;
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-htmldir2");
                    @Override public int          getArgumentCount() { return 2; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() {
                        return "<input_path> <output_path>";
                    }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        if (arguments.get(1).equals("default")) {
                            inputPathHtmlDir2.add(arguments.get(0));
                        } else {
                            inputPathHtmlDir2.add(arguments.get(0));
                            outputPathHtmlDir2 = arguments.get(1);
                        }
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-resourcesdir");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<path>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        inputPathResourcesDir = arguments.get(0);
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-resourcesoutdir");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<path>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        outputPathResourcesDir = arguments.get(0);
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-title");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<title>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        Doclava.title = arguments.get(0);
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-werror");
                    @Override public int          getArgumentCount() { return 0; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return ""; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        Errors.setWarningsAreErrors(true);
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-lerror");
                    @Override public int          getArgumentCount() { return 0; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return ""; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        Errors.setLintsAreErrors(true);
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-lintbaseline");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() { return "Allowed lint errors"; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<file>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        lintBaselineFile = arguments.get(0);
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-error");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<code_value>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        try {
                            int level = Integer.parseInt(arguments.get(0));
                            Errors.setErrorLevel(level, Errors.ERROR);
                            return true;
                        } catch (NumberFormatException e) {
                            return false;
                        }
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-warning");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<code_value>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        try {
                            int level = Integer.parseInt(arguments.get(0));
                            Errors.setErrorLevel(level, Errors.WARNING);
                            return true;
                        } catch (NumberFormatException e) {
                            return false;
                        }
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-lint");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<code_value>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        try {
                            int level = Integer.parseInt(arguments.get(0));
                            Errors.setErrorLevel(level, Errors.LINT);
                            return true;
                        } catch (NumberFormatException e) {
                            return false;
                        }
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-hide");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<code_value>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        try {
                            int level = Integer.parseInt(arguments.get(0));
                            Errors.setErrorLevel(level, Errors.HIDDEN);
                            return true;
                        } catch (NumberFormatException e) {
                            return false;
                        }
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-keeplist");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<list>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        keepListFile = arguments.get(0);
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-showUnannotated");
                    @Override public int          getArgumentCount() { return 0; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return ""; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        showUnannotated = true;
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-showAnnotation");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<annotation>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        showAnnotations.add(arguments.get(0));
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-hideAnnotation");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<annotation>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        hideAnnotations.add(arguments.get(0));
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-showAnnotationOverridesVisibility");
                    @Override public int          getArgumentCount() { return 0; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return ""; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        showAnnotationOverridesVisibility = true;
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-hidePackage");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<package>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        hiddenPackages.add(arguments.get(0));
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-proguard");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<arg>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        proguardFile = arguments.get(0);
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-proofread");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<arg>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        proofreadFile = arguments.get(0);
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-todo");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<file>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        todoFile = arguments.get(0);
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-public");
                    @Override public int          getArgumentCount() { return 0; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return ""; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        showLevel = SHOW_PUBLIC;
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-protected");
                    @Override public int          getArgumentCount() { return 0; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return ""; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        showLevel = SHOW_PROTECTED;
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-package");
                    @Override public int          getArgumentCount() { return 0; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return ""; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        showLevel = SHOW_PACKAGE;
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-private");
                    @Override public int          getArgumentCount() { return 0; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return ""; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        showLevel = SHOW_PRIVATE;
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-hidden");
                    @Override public int          getArgumentCount() { return 0; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return ""; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        showLevel = SHOW_HIDDEN;
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-stubs");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<stubs>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        stubsDir = arguments.get(0);
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-stubpackages");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<packages>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        stubPackages = new HashSet<>();
                        stubPackages.addAll(Arrays.asList(arguments.get(0).split(":")));
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-stubimportpackages");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<packages>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        stubImportPackages = new HashSet<>();
                        for (String pkg : arguments.get(0).split(":")) {
                            stubImportPackages.add(pkg);
                            hiddenPackages.add(pkg);
                        }
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-stubsourceonly");
                    @Override public int          getArgumentCount() { return 0; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return ""; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        stubSourceOnly = true;
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-keepstubcomments");
                    @Override public int          getArgumentCount() { return 0; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return ""; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        keepStubComments = true;
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-sdkvalues");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<path>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        sdkValuePath = arguments.get(0);
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-api");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<file>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        apiFile = arguments.get(0);
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-dexApi");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<file>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        dexApiFile = arguments.get(0);
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-removedApi");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<file>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        removedApiFile = arguments.get(0);
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-removedDexApi");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<file>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        removedDexApiFile = arguments.get(0);
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-exactApi");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<file>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        exactApiFile = arguments.get(0);
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-privateApi");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<file>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        privateApiFile = arguments.get(0);
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-privateDexApi");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<file>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        privateDexApiFile = arguments.get(0);
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-apiMapping");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<file>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        apiMappingFile = arguments.get(0);
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-nodocs");
                    @Override public int          getArgumentCount() { return 0; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return ""; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        generateDocs = false;
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-noassets");
                    @Override public int          getArgumentCount() { return 0; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return ""; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        includeAssets = false;
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-nodefaultassets");
                    @Override public int          getArgumentCount() { return 0; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return ""; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        includeDefaultAssets = false;
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-parsecomments");
                    @Override public int          getArgumentCount() { return 0; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return ""; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        parseComments = true;
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-metalavaApiSince");
                    @Override public int          getArgumentCount() { return 0; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return ""; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        METALAVA_API_SINCE = true;
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-since");
                    @Override public int          getArgumentCount() { return 2; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<major> <minor>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        sinceTagger.addVersion(arguments.get(0), arguments.get(1));
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-artifact");
                    @Override public int          getArgumentCount() { return 2; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<arg1> <arg2>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        artifactTagger.addArtifact(arguments.get(0), arguments.get(1));
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-offlinemode");
                    @Override public int          getArgumentCount() { return 0; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return ""; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        // TODO(nikitai): This option is not used anywhere, consider removing.
                        offlineMode = true;
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-metadataDebug");
                    @Override public int          getArgumentCount() { return 0; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return ""; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        META_DBG = true;
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-includePreview");
                    @Override public int          getArgumentCount() { return 0; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return ""; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        INCLUDE_PREVIEW = true;
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-ignoreJdLinks");
                    @Override public int          getArgumentCount() { return 0; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return ""; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        if (DEVSITE_STATIC_ONLY) {
                            DEVSITE_IGNORE_JDLINKS = true;
                        }
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-federate");
                    @Override public int          getArgumentCount() { return 2; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<name> <URL>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        try {
                            String name = arguments.get(0);
                            URL federationURL = new URL(arguments.get(1));
                            federationTagger.addSiteUrl(name, federationURL);
                        } catch (MalformedURLException e) {
                            System.err.println("Could not parse URL for federation: " + arguments.get(0));
                            return false;
                        }
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-federationapi");
                    @Override public int          getArgumentCount() { return 2; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<name> <file>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        String name = arguments.get(0);
                        String file = arguments.get(1);
                        federationTagger.addSiteApi(name, file);
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-gmsref");
                    @Override public int          getArgumentCount() { return 0; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return ""; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        gmsRef = true;
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-gcmref");
                    @Override public int          getArgumentCount() { return 0; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return ""; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        gcmRef = true;
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-yaml");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<file>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        yamlNavFile = arguments.get(0);
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-dac_libraryroot");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<library_root>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        libraryRoot = ensureSlash(arguments.get(0));
                        mHDFData.add(new String[] {"library.root", arguments.get(0)});
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-dac_dataname");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<data_name>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        mHDFData.add(new String[] {"dac_dataname", arguments.get(0)});
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-documentannotations");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<path>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        documentAnnotations = true;
                        documentAnnotationsPath = arguments.get(0);
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-referenceonly");
                    @Override public int          getArgumentCount() { return 0; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return ""; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        referenceOnly = true;
                        mHDFData.add(new String[] {"referenceonly", "1"});
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-staticonly");
                    @Override public int          getArgumentCount() { return 0; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return ""; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        staticOnly = true;
                        mHDFData.add(new String[] {"staticonly", "1"});
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-navtreeonly");
                    @Override public int          getArgumentCount() { return 0; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return ""; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        NAVTREE_ONLY = true;
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-atLinksNavtree");
                    @Override public int          getArgumentCount() { return 0; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return ""; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        AT_LINKS_NAVTREE = true;
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-yamlV2");
                    @Override public int          getArgumentCount() { return 0; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return ""; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        yamlV2 = true;
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-devsite");
                    @Override public int          getArgumentCount() { return 0; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return ""; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        devsite = true;
                        // Don't copy any assets to devsite output
                        includeAssets = false;
                        USE_DEVSITE_LOCALE_OUTPUT_PATHS = true;
                        mHDFData.add(new String[] {"devsite", "1"});
                        if (staticOnly) {
                            DEVSITE_STATIC_ONLY = true;
                            System.out.println("  ... Generating static html only for devsite");
                        }
                        if (yamlNavFile == null) {
                            // Use _toc.yaml as default to avoid clobbering possible manual _book.yaml files
                            yamlNavFile = "_toc.yaml";
                        }
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-android");
                    @Override public int          getArgumentCount() { return 0; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return ""; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        auxSource = new AndroidAuxSource();
                        linter = new AndroidLinter();
                        android = true;
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-manifest");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<file>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        manifestFile = arguments.get(0);
                        return true;
                    }
                }
        );

        options.add(
                new Option() {
                    private final List<String> names = List.of("-compatconfig");
                    @Override public int          getArgumentCount() { return 1; }
                    @Override public String       getDescription() { return ""; }
                    @Override public Option.Kind  getKind() { return Option.Kind.STANDARD; }
                    @Override public List<String> getNames() { return names; }
                    @Override public String       getParameters() { return "<config>"; }
                    @Override public boolean      process(String opt, List<String> arguments) {
                        compatConfig = arguments.get(0);
                        return true;
                    }
                }
        );

        return options;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean run(DocletEnvironment environment) {
        return start(environment);
    }

    public static boolean checkLevel(int level) {
        return (showLevel & level) == level;
    }

  /**
   * Returns true if we should parse javadoc comments,
   * reporting errors in the process.
   */
  public static boolean parseComments() {
    return generateDocs || parseComments;
  }

  public static boolean checkLevel(boolean pub, boolean prot, boolean pkgp, boolean priv,
      boolean hidden) {
    if (hidden && !checkLevel(SHOW_HIDDEN)) {
      return false;
    }
    if (pub && checkLevel(SHOW_PUBLIC)) {
      return true;
    }
    if (prot && checkLevel(SHOW_PROTECTED)) {
      return true;
    }
    if (pkgp && checkLevel(SHOW_PACKAGE)) {
      return true;
    }
    if (priv && checkLevel(SHOW_PRIVATE)) {
      return true;
    }
    return false;
  }

  public static void main(String[] args) {
    throw new UnsupportedOperationException("Not yet implemented");
  }

  public static boolean start(DocletEnvironment environment) {
    root = new RootDocImpl(environment);

    // If the caller has not explicitly requested that unannotated classes and members should be
    // shown in the output then only show them if no annotations were provided.
    if (!showUnannotated && showAnnotations.isEmpty()) {
      showUnannotated = true;
    }

    if (!readKnownTagsFiles(knownTags, knownTagsFiles)) {
      return false;
    }
    if (!readManifest()) {
      return false;
    }
    if (!readLintBaselineFile(lintBaselineFile)) {
      return false;
    }

    // Set up the data structures
    Converter.makeInfo(root);

    if (generateDocs) {
      ClearPage.addBundledTemplateDir("assets/customizations");
      ClearPage.addBundledTemplateDir("assets/templates");

      List<ResourceLoader> resourceLoaders = new ArrayList<ResourceLoader>();
      List<String> templates = ClearPage.getTemplateDirs();
      for (String tmpl : templates) {
        resourceLoaders.add(new FileSystemResourceLoader(tmpl));
      }
      // If no custom template path is provided, and this is a devsite build,
      // then use the bundled templates-sdk/ files by default
      if (templates.isEmpty() && devsite) {
        resourceLoaders.add(new ClassResourceLoader(Doclava.class, "/assets/templates-sdk"));
      }

      templates = ClearPage.getBundledTemplateDirs();
      for (String tmpl : templates) {
          // TODO - remove commented line - it's here for debugging purposes
        //  resourceLoaders.add(new FileSystemResourceLoader("/Volumes/Android/master/external/doclava/res/" + tmpl));
        resourceLoaders.add(new ClassResourceLoader(Doclava.class, '/'+tmpl));
      }

      ResourceLoader compositeResourceLoader = new CompositeResourceLoader(resourceLoaders);
      jSilver = new JSilver(compositeResourceLoader);

      if (!Doclava.readTemplateSettings()) {
        return false;
      }

      // if requested, only generate the navtree for ds use-case
      if (NAVTREE_ONLY) {
        if (AT_LINKS_NAVTREE) {
          AtLinksNavTree.writeAtLinksNavTree(javadocDir);
        } else if (yamlV2) {
          // Generate DAC-formatted left-nav for devsite
          NavTree.writeYamlTree2(javadocDir, yamlNavFile);
        } else {
          // This shouldn't happen; this is the legacy DAC left nav file
          NavTree.writeNavTree(javadocDir, "");
        }
        return true;
      }

      // don't do ref doc tasks in devsite static-only builds
      if (!DEVSITE_STATIC_ONLY) {
        // Load additional data structures from federated sites.
        for(FederatedSite site : federationTagger.getSites()) {
          Converter.addApiInfo(site.apiInfo());
        }

        // Apply @since tags from the XML file
        sinceTagger.tagAll(Converter.rootClasses());

        // Apply @artifact tags from the XML file
        artifactTagger.tagAll(Converter.rootClasses());

        // Apply details of federated documentation
        federationTagger.tagAll(Converter.rootClasses());

        // Files for proofreading
        if (proofreadFile != null) {
          Proofread.initProofread(proofreadFile);
        }
        if (todoFile != null) {
          TodoFile.writeTodoFile(todoFile);
        }

        if (samplesRef) {
          // always write samples without offlineMode behaviors
          writeSamples(false, sampleCodes, SORT_BY_NAV_GROUPS);
        }
      }
      if (!referenceOnly) {
        // HTML2 Pages -- Generate Pages from optional secondary dir
        if (!inputPathHtmlDir2.isEmpty()) {
          if (!outputPathHtmlDir2.isEmpty()) {
            ClearPage.outputDir = outputPathBase + "/" + outputPathHtmlDir2;
          }
          ClearPage.htmlDirs = inputPathHtmlDir2;
          writeHTMLPages();
          ClearPage.htmlDirs = inputPathHtmlDirs;
        }

        // HTML Pages
        if (!ClearPage.htmlDirs.isEmpty()) {
          ClearPage.htmlDirs = inputPathHtmlDirs;
          if (USE_DEVSITE_LOCALE_OUTPUT_PATHS) {
            ClearPage.outputDir = outputPathHtmlDirs + "/en/";
          } else {
            ClearPage.outputDir = outputPathHtmlDirs;
          }
          writeHTMLPages();
        }
      }

      writeResources();

      writeAssets();

      // don't do ref doc tasks in devsite static-only builds
      if (!DEVSITE_STATIC_ONLY) {
        // Navigation tree
        String refPrefix = new String();
        if(gmsRef){
          refPrefix = "gms-";
        } else if(gcmRef){
          refPrefix = "gcm-";
        }

        // Packages Pages
        writePackages(refPrefix + "packages" + htmlExtension);

        // Classes
        writeClassLists();
        writeClasses();
        writeHierarchy();
        writeCompatConfig();
        // writeKeywords();

        // Write yaml tree.
        if (yamlNavFile != null) {
          if (yamlV2) {
            // Generate DAC-formatted left-nav for devsite
            NavTree.writeYamlTree2(javadocDir, yamlNavFile);
          } else {
            // Generate legacy devsite left-nav (shows sub-classes nested under parent class)
            NavTree.writeYamlTree(javadocDir, yamlNavFile);
          }
        } else {
          // This shouldn't happen; this is the legacy DAC left nav file
          NavTree.writeNavTree(javadocDir, refPrefix);
        }

        // Lists for JavaScript
        writeLists();
        if (keepListFile != null) {
          writeKeepList(keepListFile);
        }

        Proofread.finishProofread(proofreadFile);

        if (sdkValuePath != null) {
          writeSdkValues(sdkValuePath);
        }
      }
      // Write metadata for all processed files to jd_lists_unified in out dir
      if (!sTaglist.isEmpty()) {
        PageMetadata.WriteListByLang(sTaglist);
        // For devsite (ds) reference only, write samples_metadata to out dir
        if ((devsite) && (!DEVSITE_STATIC_ONLY)) {
          PageMetadata.WriteSamplesListByLang(sTaglist);
        }
      }
    }

    // Stubs
    if (stubsDir != null || apiFile != null || dexApiFile != null || proguardFile != null
        || removedApiFile != null || removedDexApiFile != null || exactApiFile != null
        || privateApiFile != null || privateDexApiFile != null || apiMappingFile != null) {
      Stubs.writeStubsAndApi(stubsDir, apiFile, dexApiFile, proguardFile, removedApiFile,
          removedDexApiFile, exactApiFile, privateApiFile, privateDexApiFile, apiMappingFile,
          stubPackages, stubImportPackages, stubSourceOnly, keepStubComments);
    }

    Errors.printErrors();

    return !Errors.hadError;
  }

  private static void writeIndex(String dir) {
    Data data = makeHDF();
    ClearPage.write(data, "index.cs", dir + "index" + htmlExtension);
  }

  private static boolean readTemplateSettings() {
    Data data = makeHDF();

    // The .html extension is hard-coded in several .cs files,
    // and so you cannot currently set it as a property.
    htmlExtension = ".html";
    // htmlExtension = data.getValue("template.extension", ".html");
    int i = 0;
    while (true) {
      String k = data.getValue("template.escape." + i + ".key", "");
      String v = data.getValue("template.escape." + i + ".value", "");
      if ("".equals(k)) {
        break;
      }
      if (k.length() != 1) {
        System.err.println("template.escape." + i + ".key must have a length of 1: " + k);
        return false;
      }
      escapeChars.put(k.charAt(0), v);
      i++;
    }
    return true;
  }

    private static boolean readKnownTagsFiles(HashSet<String> knownTags,
            ArrayList<String> knownTagsFiles) {
        for (String fn: knownTagsFiles) {
           BufferedReader in = null;
           try {
               in = new BufferedReader(new FileReader(fn));
               int lineno = 0;
               boolean fail = false;
               while (true) {
                   lineno++;
                   String line = in.readLine();
                   if (line == null) {
                       break;
                   }
                   line = line.trim();
                   if (line.length() == 0) {
                       continue;
                   } else if (line.charAt(0) == '#') {
                       continue;
                   }
                   String[] words = line.split("\\s+", 2);
                   if (words.length == 2) {
                       if (words[1].charAt(0) != '#') {
                           System.err.println(fn + ":" + lineno
                                   + ": Only one tag allowed per line: " + line);
                           fail = true;
                           continue;
                       }
                   }
                   knownTags.add(words[0]);
               }
               if (fail) {
                   return false;
               }
           } catch (IOException ex) {
               System.err.println("Error reading file: " + fn + " (" + ex.getMessage() + ")");
               return false;
           } finally {
               if (in != null) {
                   try {
                       in.close();
                   } catch (IOException e) {
                   }
               }
           }
        }
        return true;
    }

    private static boolean readLintBaselineFile(String lintBaselineFile) {
        if (lintBaselineFile == null) {
          return true;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(lintBaselineFile))) {
            List<LintBaselineEntry> baseline =
                    reader.lines()
                        .filter(l -> !l.trim().isEmpty() && !l.startsWith("//"))
                        .map(ErrorMessage::parse)
                        .filter(e -> e != null)
                        .collect(toList());
            Errors.setLintBaseline(baseline);
            return true;
        } catch (IOException exception) {
            exception.printStackTrace(System.err);
            return false;
        }
    }

  private static boolean readManifest() {
    manifestPermissions.clear();
    if (manifestFile == null) {
      return true;
    }
    try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(manifestFile));
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] buffer = new byte[1024];
      int count;
      while ((count = in.read(buffer)) != -1) {
        out.write(buffer, 0, count);
      }
      final Matcher m = Pattern.compile("(?s)<permission "
          + "[^>]*android:name=\"([^\">]+)\""
          + "[^>]*android:protectionLevel=\"([^\">]+)\"").matcher(out.toString());
      while (m.find()) {
        manifestPermissions.put(m.group(1), m.group(2));
      }
    } catch (IOException e) {
      Errors.error(Errors.PARSE_ERROR, (SourcePositionInfo) null,
          "Failed to parse " + manifestFile + ": " + e);
      return false;
    }
    return true;
  }

  public static String escape(String s) {
    if (escapeChars.size() == 0) {
      return s;
    }
    StringBuffer b = null;
    int begin = 0;
    final int N = s.length();
    for (int i = 0; i < N; i++) {
      char c = s.charAt(i);
      String mapped = escapeChars.get(c);
      if (mapped != null) {
        if (b == null) {
          b = new StringBuffer(s.length() + mapped.length());
        }
        if (begin != i) {
          b.append(s.substring(begin, i));
        }
        b.append(mapped);
        begin = i + 1;
      }
    }
    if (b != null) {
      if (begin != N) {
        b.append(s.substring(begin, N));
      }
      return b.toString();
    }
    return s;
  }

  public static void setPageTitle(Data data, String title) {
    String s = title;
    if (Doclava.title.length() > 0) {
      s += " - " + Doclava.title;
    }
    data.setValue("page.title", s);
  }

  public static Data makeHDF() {
    Data data = jSilver.createData();

    for (String[] p : mHDFData) {
      data.setValue(p[0], p[1]);
    }

    return data;
  }

  public static Data makePackageHDF() {
    Data data = makeHDF();
    Collection<ClassInfo> classes = Converter.rootClasses();

    SortedMap<String, PackageInfo> sorted = new TreeMap<String, PackageInfo>();
    for (ClassInfo cl : classes) {
      PackageInfo pkg = cl.containingPackage();
      String name;
      if (pkg == null) {
        name = "";
      } else {
        name = pkg.name();
      }
      sorted.put(name, pkg);
    }

    int i = 0;
    for (Map.Entry<String, PackageInfo> entry : sorted.entrySet()) {
      String s = entry.getKey();
      PackageInfo pkg = entry.getValue();

      if (pkg.isHiddenOrRemoved()) {
        continue;
      }
      boolean allHiddenOrRemoved = true;
      int pass = 0;
      ClassInfo[] classesToCheck = null;
      while (pass < 6) {
        switch (pass) {
          case 0:
            classesToCheck = pkg.ordinaryClasses();
            break;
          case 1:
            classesToCheck = pkg.enums();
            break;
          case 2:
            classesToCheck = pkg.errors();
            break;
          case 3:
            classesToCheck = pkg.exceptions();
            break;
          case 4:
            classesToCheck = pkg.interfaces();
            break;
          case 5:
            classesToCheck = pkg.annotations();
            break;
          default:
            System.err.println("Error reading package: " + pkg.name());
            break;
        }
        for (ClassInfo cl : classesToCheck) {
          if (!cl.isHiddenOrRemoved()) {
            allHiddenOrRemoved = false;
            break;
          }
        }
        if (!allHiddenOrRemoved) {
          break;
        }
        pass++;
      }
      if (allHiddenOrRemoved) {
        continue;
      }
      if(gmsRef){
          data.setValue("reference.gms", "true");
      } else if(gcmRef){
          data.setValue("reference.gcm", "true");
      }
      data.setValue("reference", "1");
      if (METALAVA_API_SINCE) {
        data.setValue("reference.apilevels", (pkg.getSince() != null) ? "1" : "0");
      } else {
        data.setValue("reference.apilevels", sinceTagger.hasVersions() ? "1" : "0");
      }
      data.setValue("reference.artifacts", artifactTagger.hasArtifacts() ? "1" : "0");
      data.setValue("docs.packages." + i + ".name", s);
      data.setValue("docs.packages." + i + ".link", pkg.htmlPage());
      data.setValue("docs.packages." + i + ".since", pkg.getSince());
      TagInfo.makeHDF(data, "docs.packages." + i + ".shortDescr", pkg.firstSentenceTags());
      i++;
    }

    sinceTagger.writeVersionNames(data);
    return data;
  }

  private static void writeDirectory(File dir, String relative, JSilver js) {
    File[] files = dir.listFiles();
    int i, count = files.length;
    for (i = 0; i < count; i++) {
      File f = files[i];
      if (f.isFile()) {
        String templ = relative + f.getName();
        int len = templ.length();
        if (len > 3 && ".cs".equals(templ.substring(len - 3))) {
          Data data = makePackageHDF();
          String filename = templ.substring(0, len - 3) + htmlExtension;
          ClearPage.write(data, templ, filename, js);
        } else if (len > 3 && ".jd".equals(templ.substring(len - 3))) {
          Data data = makePackageHDF();
          String filename = templ.substring(0, len - 3) + htmlExtension;
          DocFile.writePage(f.getAbsolutePath(), relative, filename, data);
        } else if(!f.getName().equals(".DS_Store")){
              Data data = makeHDF();
              String hdfValue = data.getValue("sac") == null ? "" : data.getValue("sac");
              boolean allowExcepted = hdfValue.equals("true") ? true : false;
              boolean append = false;
              ClearPage.copyFile(allowExcepted, f, templ, append);
        }
      } else if (f.isDirectory()) {
        writeDirectory(f, relative + f.getName() + "/", js);
      }
    }
  }

  public static void writeHTMLPages() {
    for (String htmlDir : ClearPage.htmlDirs) {
      File f = new File(htmlDir);
      if (!f.isDirectory()) {
        System.err.println("htmlDir not a directory: " + htmlDir);
        continue;
      }

      ResourceLoader loader = new FileSystemResourceLoader(f);
      JSilver js = new JSilver(loader);
      writeDirectory(f, "", js);
    }
  }

  /* copy files supplied by the -resourcesdir flag */
  public static void writeResources() {
    if (inputPathResourcesDir != null && !inputPathResourcesDir.isEmpty()) {
      try {
        File f = new File(inputPathResourcesDir);
        if (!f.isDirectory()) {
          System.err.println("resourcesdir is not a directory: " + inputPathResourcesDir);
          return;
        }

        ResourceLoader loader = new FileSystemResourceLoader(f);
        JSilver js = new JSilver(loader);
        writeDirectory(f, outputPathResourcesDir, js);
      } catch(Exception e) {
        System.err.println("Could not copy resourcesdir: " + e);
      }
    }
  }

  public static void writeAssets() {
    if (!includeAssets) return;
    JarFile thisJar = JarUtils.jarForClass(Doclava.class, null);
    if ((thisJar != null) && (includeDefaultAssets)) {
      try {
        List<String> templateDirs = ClearPage.getBundledTemplateDirs();
        for (String templateDir : templateDirs) {
          String assetsDir = templateDir + "/assets";
          JarUtils.copyResourcesToDirectory(thisJar, assetsDir, ClearPage.outputDir + "/assets");
        }
      } catch (IOException e) {
        System.err.println("Error copying assets directory.");
        e.printStackTrace();
        return;
      }
    }

    //write the project-specific assets
    List<String> templateDirs = ClearPage.getTemplateDirs();
    for (String templateDir : templateDirs) {
      File assets = new File(templateDir + "/assets");
      if (assets.isDirectory()) {
        writeDirectory(assets, "assets/", null);
      }
    }

    // Create the timestamp.js file based on .cs file
    Data timedata = Doclava.makeHDF();
    ClearPage.write(timedata, "timestamp.cs", "timestamp.js");
  }

  /** Go through the docs and generate meta-data about each
      page to use in search suggestions */
  public static void writeLists() {

    // Write the lists for API references
    Data data = makeHDF();

    Collection<ClassInfo> classes = Converter.rootClasses();

    SortedMap<String, Object> sorted = new TreeMap<String, Object>();
    for (ClassInfo cl : classes) {
      if (cl.isHiddenOrRemoved()) {
        continue;
      }
      sorted.put(cl.qualifiedName(), cl);
      PackageInfo pkg = cl.containingPackage();
      String name;
      if (pkg == null) {
        name = "";
      } else {
        name = pkg.name();
      }
      sorted.put(name, pkg);
    }

    int i = 0;
    String listDir = javadocDir;
    if (USE_DEVSITE_LOCALE_OUTPUT_PATHS) {
      if (libraryRoot != null) {
        listDir = listDir + libraryRoot;
      }
    }
    for (String s : sorted.keySet()) {
      data.setValue("docs.pages." + i + ".id", "" + i);
      data.setValue("docs.pages." + i + ".label", s);

      Object o = sorted.get(s);
      if (o instanceof PackageInfo) {
        PackageInfo pkg = (PackageInfo) o;
        data.setValue("docs.pages." + i + ".link", pkg.htmlPage());
        data.setValue("docs.pages." + i + ".type", "package");
        data.setValue("docs.pages." + i + ".deprecated", pkg.isDeprecated() ? "true" : "false");
      } else if (o instanceof ClassInfo) {
        ClassInfo cl = (ClassInfo) o;
        data.setValue("docs.pages." + i + ".link", cl.htmlPage());
        data.setValue("docs.pages." + i + ".type", "class");
        data.setValue("docs.pages." + i + ".deprecated", cl.isDeprecated() ? "true" : "false");
      }
      i++;
    }
    ClearPage.write(data, "lists.cs", listDir + "lists.js");


    // Write the lists for JD documents (if there are HTML directories to process)
    // Skip this for devsite builds
    if ((inputPathHtmlDirs.size() > 0) && (!devsite)) {
      Data jddata = makeHDF();
      Iterator counter = new Iterator();
      for (String htmlDir : inputPathHtmlDirs) {
        File dir = new File(htmlDir);
        if (!dir.isDirectory()) {
          continue;
        }
        writeJdDirList(dir, jddata, counter);
      }
      ClearPage.write(jddata, "jd_lists.cs", javadocDir + "jd_lists.js");
    }
  }

  private static class Iterator {
    int i = 0;
  }

  /** Write meta-data for a JD file, used for search suggestions */
  private static void writeJdDirList(File dir, Data data, Iterator counter) {
    File[] files = dir.listFiles();
    int i, count = files.length;
    // Loop all files in given directory
    for (i = 0; i < count; i++) {
      File f = files[i];
      if (f.isFile()) {
        String filePath = f.getAbsolutePath();
        String templ = f.getName();
        int len = templ.length();
        // If it's a .jd file we want to process
        if (len > 3 && ".jd".equals(templ.substring(len - 3))) {
          // remove the directories below the site root
          String webPath = filePath.substring(filePath.indexOf("docs/html/") + 10,
              filePath.length());
          // replace .jd with .html
          webPath = webPath.substring(0, webPath.length() - 3) + htmlExtension;
          // Parse the .jd file for properties data at top of page
          Data hdf = Doclava.makeHDF();
          String filedata = DocFile.readFile(filePath);
          Matcher lines = DocFile.LINE.matcher(filedata);
          String line = null;
          // Get each line to add the key-value to hdf
          while (lines.find()) {
            line = lines.group(1);
            if (line.length() > 0) {
              // Stop when we hit the body
              if (line.equals("@jd:body")) {
                break;
              }
              Matcher prop = DocFile.PROP.matcher(line);
              if (prop.matches()) {
                String key = prop.group(1);
                String value = prop.group(2);
                hdf.setValue(key, value);
              } else {
                break;
              }
            }
          } // done gathering page properties

          // Insert the goods into HDF data (title, link, tags, type)
          String title = hdf.getValue("page.title", "");
          title = title.replaceAll("\"", "'");
          // if there's a <span> in the title, get rid of it
          if (title.indexOf("<span") != -1) {
            String[] splitTitle = title.split("<span(.*?)</span>");
            title = splitTitle[0];
            for (int j = 1; j < splitTitle.length; j++) {
              title.concat(splitTitle[j]);
            }
          }

          StringBuilder tags =  new StringBuilder();
          String tagsList = hdf.getValue("page.tags", "");
          if (!tagsList.equals("")) {
            tagsList = tagsList.replaceAll("\"", "");
            String[] tagParts = tagsList.split(",");
            for (int iter = 0; iter < tagParts.length; iter++) {
              tags.append("\"");
              tags.append(tagParts[iter].trim());
              tags.append("\"");
              if (iter < tagParts.length - 1) {
                tags.append(",");
              }
            }
          }

          String dirName = (webPath.indexOf("/") != -1)
                  ? webPath.substring(0, webPath.indexOf("/")) : "";

          if (!"".equals(title) &&
              !"intl".equals(dirName) &&
              !hdf.getBooleanValue("excludeFromSuggestions")) {
            data.setValue("docs.pages." + counter.i + ".label", title);
            data.setValue("docs.pages." + counter.i + ".link", webPath);
            data.setValue("docs.pages." + counter.i + ".tags", tags.toString());
            data.setValue("docs.pages." + counter.i + ".type", dirName);
            counter.i++;
          }
        }
      } else if (f.isDirectory()) {
        writeJdDirList(f, data, counter);
      }
    }
  }

  public static void cantStripThis(ClassInfo cl, HashSet<ClassInfo> notStrippable) {
    if (!notStrippable.add(cl)) {
      // slight optimization: if it already contains cl, it already contains
      // all of cl's parents
      return;
    }
    ClassInfo supr = cl.superclass();
    if (supr != null) {
      cantStripThis(supr, notStrippable);
    }
    for (ClassInfo iface : cl.interfaces()) {
      cantStripThis(iface, notStrippable);
    }
  }

  private static String getPrintableName(ClassInfo cl) {
    ClassInfo containingClass = cl.containingClass();
    if (containingClass != null) {
      // This is an inner class.
      String baseName = cl.name();
      baseName = baseName.substring(baseName.lastIndexOf('.') + 1);
      return getPrintableName(containingClass) + '$' + baseName;
    }
    return cl.qualifiedName();
  }

  /**
   * Writes the list of classes that must be present in order to provide the non-hidden APIs known
   * to javadoc.
   *
   * @param filename the path to the file to write the list to
   */
  public static void writeKeepList(String filename) {
    HashSet<ClassInfo> notStrippable = new HashSet<ClassInfo>();
    Collection<ClassInfo> all = Converter.allClasses().stream().sorted(ClassInfo.comparator)
        .collect(Collectors.toList());

    // If a class is public and not hidden, then it and everything it derives
    // from cannot be stripped. Otherwise we can strip it.
    for (ClassInfo cl : all) {
      if (cl.isPublic() && !cl.isHiddenOrRemoved()) {
        cantStripThis(cl, notStrippable);
      }
    }
    PrintStream stream = null;
    try {
      stream = new PrintStream(new BufferedOutputStream(new FileOutputStream(filename)));
      for (ClassInfo cl : notStrippable) {
        stream.println(getPrintableName(cl));
      }
    } catch (FileNotFoundException e) {
      System.err.println("error writing file: " + filename);
    } finally {
      if (stream != null) {
        stream.close();
      }
    }
  }

  private static PackageInfo[] sVisiblePackages = null;

  public static PackageInfo[] choosePackages() {
    if (sVisiblePackages != null) {
      return sVisiblePackages;
    }

    Collection<ClassInfo> classes = Converter.rootClasses();
    SortedMap<String, PackageInfo> sorted = new TreeMap<String, PackageInfo>();
    for (ClassInfo cl : classes) {
      PackageInfo pkg = cl.containingPackage();
      String name;
      if (pkg == null) {
        name = "";
      } else {
        name = pkg.name();
      }
      sorted.put(name, pkg);
    }

    ArrayList<PackageInfo> result = new ArrayList<PackageInfo>();

    for (String s : sorted.keySet()) {
      PackageInfo pkg = sorted.get(s);

      if (pkg.isHiddenOrRemoved()) {
        continue;
      }

      boolean allHiddenOrRemoved = true;
      int pass = 0;
      ClassInfo[] classesToCheck = null;
      while (pass < 6) {
        switch (pass) {
          case 0:
            classesToCheck = pkg.ordinaryClasses();
            break;
          case 1:
            classesToCheck = pkg.enums();
            break;
          case 2:
            classesToCheck = pkg.errors();
            break;
          case 3:
            classesToCheck = pkg.exceptions();
            break;
          case 4:
            classesToCheck = pkg.interfaces();
            break;
          case 5:
            classesToCheck = pkg.annotations();
            break;
          default:
            System.err.println("Error reading package: " + pkg.name());
            break;
        }
        for (ClassInfo cl : classesToCheck) {
          if (!cl.isHiddenOrRemoved()) {
            allHiddenOrRemoved = false;
            break;
          }
        }
        if (!allHiddenOrRemoved) {
          break;
        }
        pass++;
      }
      if (allHiddenOrRemoved) {
        continue;
      }

      result.add(pkg);
    }

    sVisiblePackages = result.toArray(new PackageInfo[result.size()]);
    return sVisiblePackages;
  }

  public static void writePackages(String filename) {
    Data data = makePackageHDF();

    int i = 0;
    for (PackageInfo pkg : choosePackages()) {
      writePackage(pkg);

      data.setValue("docs.packages." + i + ".name", pkg.name());
      data.setValue("docs.packages." + i + ".link", pkg.htmlPage());
      TagInfo.makeHDF(data, "docs.packages." + i + ".shortDescr", pkg.firstSentenceTags());

      i++;
    }

    setPageTitle(data, "Package Index");

    TagInfo.makeHDF(data, "root.descr", Converter.convertTags(root.inlineTags(), null));

    String packageDir = javadocDir;
    if (USE_DEVSITE_LOCALE_OUTPUT_PATHS) {
      if (libraryRoot != null) {
        packageDir = packageDir + libraryRoot;
      }
    }
    data.setValue("page.not-api", "true");
    ClearPage.write(data, "packages.cs", packageDir + filename);
    ClearPage.write(data, "package-list.cs", packageDir + "package-list");

    Proofread.writePackages(filename, Converter.convertTags(root.inlineTags(), null));
  }

  public static void writePackage(PackageInfo pkg) {
    // these this and the description are in the same directory,
    // so it's okay
    Data data = makePackageHDF();

    String name = pkg.name();

    data.setValue("package.name", name);
    data.setValue("package.since", pkg.getSince());
    data.setValue("package.descr", "...description...");
    pkg.setFederatedReferences(data, "package");

    makeClassListHDF(data, "package.annotations", ClassInfo.sortByName(pkg.annotations()));
    makeClassListHDF(data, "package.interfaces", ClassInfo.sortByName(pkg.interfaces()));
    makeClassListHDF(data, "package.classes", ClassInfo.sortByName(pkg.ordinaryClasses()));
    makeClassListHDF(data, "package.enums", ClassInfo.sortByName(pkg.enums()));
    makeClassListHDF(data, "package.exceptions", ClassInfo.sortByName(pkg.exceptions()));
    makeClassListHDF(data, "package.errors", ClassInfo.sortByName(pkg.errors()));
    TagInfo.makeHDF(data, "package.shortDescr", pkg.firstSentenceTags());
    TagInfo.makeHDF(data, "package.descr", pkg.inlineTags());

    String filename = pkg.htmlPage();
    setPageTitle(data, name);
    ClearPage.write(data, "package.cs", filename);

    Proofread.writePackage(filename, pkg.inlineTags());
  }

  public static void writeClassLists() {
    int i;
    Data data = makePackageHDF();

    ClassInfo[] classes = PackageInfo.filterHiddenAndRemoved(
        Converter.convertClasses(root.classes()));
    if (classes.length == 0) {
      return;
    }

    Sorter[] sorted = new Sorter[classes.length];
    for (i = 0; i < sorted.length; i++) {
      ClassInfo cl = classes[i];
      String name = cl.name();
      sorted[i] = new Sorter(name, cl);
    }

    Arrays.sort(sorted);

    // make a pass and resolve ones that have the same name
    int firstMatch = 0;
    String lastName = sorted[0].label;
    for (i = 1; i < sorted.length; i++) {
      String s = sorted[i].label;
      if (!lastName.equals(s)) {
        if (firstMatch != i - 1) {
          // there were duplicates
          for (int j = firstMatch; j < i; j++) {
            PackageInfo pkg = ((ClassInfo) sorted[j].data).containingPackage();
            if (pkg != null) {
              sorted[j].label = sorted[j].label + " (" + pkg.name() + ")";
            }
          }
        }
        firstMatch = i;
        lastName = s;
      }
    }

    // and sort again
    Arrays.sort(sorted);

    for (i = 0; i < sorted.length; i++) {
      String s = sorted[i].label;
      ClassInfo cl = (ClassInfo) sorted[i].data;
      char first = Character.toUpperCase(s.charAt(0));
      cl.makeShortDescrHDF(data, "docs.classes." + first + '.' + i);
    }

    String packageDir = javadocDir;
    if (USE_DEVSITE_LOCALE_OUTPUT_PATHS) {
      if (libraryRoot != null) {
        packageDir = packageDir + libraryRoot;
      }
    }

    data.setValue("page.not-api", "true");
    setPageTitle(data, "Class Index");
    ClearPage.write(data, "classes.cs", packageDir + "classes" + htmlExtension);

    if (!devsite) {
      // Index page redirects to the classes.html page, so use the same directory
      // This page is not needed for devsite builds, which should instead use _redirects.yaml
      writeIndex(packageDir);
    }
  }

  // we use the word keywords because "index" means something else in html land
  // the user only ever sees the word index
  /*
   * public static void writeKeywords() { ArrayList<KeywordEntry> keywords = new
   * ArrayList<KeywordEntry>();
   *
   * ClassInfo[] classes = PackageInfo.filterHiddenAndRemoved(Converter.convertClasses(root.classes()));
   *
   * for (ClassInfo cl: classes) { cl.makeKeywordEntries(keywords); }
   *
   * HDF data = makeHDF();
   *
   * Collections.sort(keywords);
   *
   * int i=0; for (KeywordEntry entry: keywords) { String base = "keywords." + entry.firstChar() +
   * "." + i; entry.makeHDF(data, base); i++; }
   *
   * setPageTitle(data, "Index"); ClearPage.write(data, "keywords.cs", javadocDir + "keywords" +
   * htmlExtension); }
   */

  public static void writeHierarchy() {
    Collection<ClassInfo> classes = Converter.rootClasses();
    ArrayList<ClassInfo> info = new ArrayList<ClassInfo>();
    for (ClassInfo cl : classes) {
      if (!cl.isHiddenOrRemoved()) {
        info.add(cl);
      }
    }
    Data data = makePackageHDF();
    Hierarchy.makeHierarchy(data, info.toArray(new ClassInfo[info.size()]));
    setPageTitle(data, "Class Hierarchy");
    ClearPage.write(data, "hierarchy.cs", javadocDir + "hierarchy" + htmlExtension);
  }

  public static void writeClasses() {
    Collection<ClassInfo> classes = Converter.rootClasses();

    for (ClassInfo cl : classes) {
      Data data = makePackageHDF();
      if (!cl.isHiddenOrRemoved()) {
        writeClass(cl, data);
      }
    }
  }

  public static void writeClass(ClassInfo cl, Data data) {
    cl.makeHDF(data);
    setPageTitle(data, cl.name());
    String outfile = cl.htmlPage();
    ClearPage.write(data, "class.cs", outfile);
    Proofread.writeClass(cl.htmlPage(), cl);
  }

  public static void makeClassListHDF(Data data, String base, ClassInfo[] classes) {
    for (int i = 0; i < classes.length; i++) {
      ClassInfo cl = classes[i];
      if (!cl.isHiddenOrRemoved()) {
        cl.makeShortDescrHDF(data, base + "." + i);
      }
    }
  }

  public static String linkTarget(String source, String target) {
    String[] src = source.split("/");
    String[] tgt = target.split("/");

    int srclen = src.length;
    int tgtlen = tgt.length;

    int same = 0;
    while (same < (srclen - 1) && same < (tgtlen - 1) && (src[same].equals(tgt[same]))) {
      same++;
    }

    String s = "";

    int up = srclen - same - 1;
    for (int i = 0; i < up; i++) {
      s += "../";
    }


    int N = tgtlen - 1;
    for (int i = same; i < N; i++) {
      s += tgt[i] + '/';
    }
    s += tgt[tgtlen - 1];

    return s;
  }

  /**
   * Returns true if the given element has an @hide, @removed or @pending annotation.
   */
  private static boolean hasHideOrRemovedAnnotation(Doc doc) {
    String comment = doc.getRawCommentText();
    return comment.indexOf("@hide") != -1 || comment.indexOf("@pending") != -1 ||
        comment.indexOf("@removed") != -1;
  }

  /**
   * Returns true if the given element is hidden.
   */
  private static boolean isHiddenOrRemoved(Doc doc) {
    // Methods, fields, constructors.
    if (doc instanceof MemberDoc) {
      return hasHideOrRemovedAnnotation(doc);
    }

    // Classes, interfaces, enums, annotation types.
    if (doc instanceof ClassDoc) {
      ClassDoc classDoc = (ClassDoc) doc;

      // Check the containing package.
      if (hasHideOrRemovedAnnotation(classDoc.containingPackage())) {
        return true;
      }

      // Check the class doc and containing class docs if this is a
      // nested class.
      ClassDoc current = classDoc;
      do {
        if (hasHideOrRemovedAnnotation(current)) {
          return true;
        }

        current = current.containingClass();
      } while (current != null);
    }

    return false;
  }

  /**
   * Filters out hidden and removed elements.
   */
  private static Object filterHiddenAndRemoved(Object o, Class<?> expected) {
    if (o == null) {
      return null;
    }

    Class type = o.getClass();
    if (type.getName().startsWith("com.sun.")) {
      // TODO: Implement interfaces from superclasses, too.
      return Proxy
          .newProxyInstance(type.getClassLoader(), type.getInterfaces(), new HideHandler(o));
    } else if (o instanceof Object[]) {
      Class<?> componentType = expected.getComponentType();
      Object[] array = (Object[]) o;
      List<Object> list = new ArrayList<Object>(array.length);
      for (Object entry : array) {
        if ((entry instanceof Doc) && isHiddenOrRemoved((Doc) entry)) {
          continue;
        }
        list.add(filterHiddenAndRemoved(entry, componentType));
      }
      return list.toArray((Object[]) Array.newInstance(componentType, list.size()));
    } else {
      return o;
    }
  }

  /**
   * Filters hidden elements out of method return values.
   */
  private static class HideHandler implements InvocationHandler {

    private final Object target;

    public HideHandler(Object target) {
      this.target = target;
    }

    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      String methodName = method.getName();
      if (args != null) {
        if (methodName.equals("compareTo") || methodName.equals("equals")
            || methodName.equals("overrides") || methodName.equals("subclassOf")) {
          args[0] = unwrap(args[0]);
        }
      }

      if (methodName.equals("getRawCommentText")) {
        return filterComment((String) method.invoke(target, args));
      }

      // escape "&" in disjunctive types.
      if (proxy instanceof Type && methodName.equals("toString")) {
        return ((String) method.invoke(target, args)).replace("&", "&amp;");
      }

      try {
        return filterHiddenAndRemoved(method.invoke(target, args), method.getReturnType());
      } catch (InvocationTargetException e) {
        throw e.getTargetException();
      }
    }

    private String filterComment(String s) {
      if (s == null) {
        return null;
      }

      s = s.trim();

      // Work around off by one error
      while (s.length() >= 5 && s.charAt(s.length() - 5) == '{') {
        s += "&nbsp;";
      }

      return s;
    }

    private static Object unwrap(Object proxy) {
      if (proxy instanceof Proxy) return ((HideHandler) Proxy.getInvocationHandler(proxy)).target;
      return proxy;
    }
  }

  /**
   * Collect the values used by the Dev tools and write them in files packaged with the SDK
   *
   * @param output the ouput directory for the files.
   */
  private static void writeSdkValues(String output) {
    ArrayList<String> activityActions = new ArrayList<String>();
    ArrayList<String> broadcastActions = new ArrayList<String>();
    ArrayList<String> serviceActions = new ArrayList<String>();
    ArrayList<String> categories = new ArrayList<String>();
    ArrayList<String> features = new ArrayList<String>();

    ArrayList<ClassInfo> layouts = new ArrayList<ClassInfo>();
    ArrayList<ClassInfo> widgets = new ArrayList<ClassInfo>();
    ArrayList<ClassInfo> layoutParams = new ArrayList<ClassInfo>();

    Collection<ClassInfo> classes = Converter.allClasses();

    // The topmost LayoutParams class - android.view.ViewGroup.LayoutParams
    ClassInfo topLayoutParams = null;

    // Go through all the fields of all the classes, looking SDK stuff.
    for (ClassInfo clazz : classes) {

      // first check constant fields for the SdkConstant annotation.
      ArrayList<FieldInfo> fields = clazz.allSelfFields();
      for (FieldInfo field : fields) {
        Object cValue = field.constantValue();
        if (cValue != null) {
            ArrayList<AnnotationInstanceInfo> annotations = field.annotations();
          if (!annotations.isEmpty()) {
            for (AnnotationInstanceInfo annotation : annotations) {
              if (SDK_CONSTANT_ANNOTATION.equals(annotation.type().qualifiedName())) {
                if (!annotation.elementValues().isEmpty()) {
                  String type = annotation.elementValues().get(0).valueString();
                  if (SDK_CONSTANT_TYPE_ACTIVITY_ACTION.equals(type)) {
                    activityActions.add(cValue.toString());
                  } else if (SDK_CONSTANT_TYPE_BROADCAST_ACTION.equals(type)) {
                    broadcastActions.add(cValue.toString());
                  } else if (SDK_CONSTANT_TYPE_SERVICE_ACTION.equals(type)) {
                    serviceActions.add(cValue.toString());
                  } else if (SDK_CONSTANT_TYPE_CATEGORY.equals(type)) {
                    categories.add(cValue.toString());
                  } else if (SDK_CONSTANT_TYPE_FEATURE.equals(type)) {
                    features.add(cValue.toString());
                  }
                }
                break;
              }
            }
          }
        }
      }

      // Now check the class for @Widget or if its in the android.widget package
      // (unless the class is hidden or abstract, or non public)
      if (clazz.isHiddenOrRemoved() == false && clazz.isPublic() && clazz.isAbstract() == false) {
        boolean annotated = false;
        ArrayList<AnnotationInstanceInfo> annotations = clazz.annotations();
        if (!annotations.isEmpty()) {
          for (AnnotationInstanceInfo annotation : annotations) {
            if (SDK_WIDGET_ANNOTATION.equals(annotation.type().qualifiedName())) {
              widgets.add(clazz);
              annotated = true;
              break;
            } else if (SDK_LAYOUT_ANNOTATION.equals(annotation.type().qualifiedName())) {
              layouts.add(clazz);
              annotated = true;
              break;
            }
          }
        }

        if (annotated == false) {
          if (topLayoutParams == null
              && "android.view.ViewGroup.LayoutParams".equals(clazz.qualifiedName())) {
            topLayoutParams = clazz;
          }
          // let's check if this is inside android.widget or android.view
          if (isIncludedPackage(clazz)) {
            // now we check what this class inherits either from android.view.ViewGroup
            // or android.view.View, or android.view.ViewGroup.LayoutParams
            int type = checkInheritance(clazz);
            switch (type) {
              case TYPE_WIDGET:
                widgets.add(clazz);
                break;
              case TYPE_LAYOUT:
                layouts.add(clazz);
                break;
              case TYPE_LAYOUT_PARAM:
                layoutParams.add(clazz);
                break;
            }
          }
        }
      }
    }

    // now write the files, whether or not the list are empty.
    // the SDK built requires those files to be present.

    Collections.sort(activityActions);
    writeValues(output + "/activity_actions.txt", activityActions);

    Collections.sort(broadcastActions);
    writeValues(output + "/broadcast_actions.txt", broadcastActions);

    Collections.sort(serviceActions);
    writeValues(output + "/service_actions.txt", serviceActions);

    Collections.sort(categories);
    writeValues(output + "/categories.txt", categories);

    Collections.sort(features);
    writeValues(output + "/features.txt", features);

    // before writing the list of classes, we do some checks, to make sure the layout params
    // are enclosed by a layout class (and not one that has been declared as a widget)
    for (int i = 0; i < layoutParams.size();) {
      ClassInfo clazz = layoutParams.get(i);
      ClassInfo containingClass = clazz.containingClass();
      boolean remove = containingClass == null || layouts.indexOf(containingClass) == -1;
      // Also ensure that super classes of the layout params are in android.widget or android.view.
      while (!remove && (clazz = clazz.superclass()) != null && !clazz.equals(topLayoutParams)) {
        remove = !isIncludedPackage(clazz);
      }
      if (remove) {
        layoutParams.remove(i);
      } else {
        i++;
      }
    }

    writeClasses(output + "/widgets.txt", widgets, layouts, layoutParams);
  }

  /**
   * Check if the clazz is in package android.view or android.widget
   */
  private static boolean isIncludedPackage(ClassInfo clazz) {
    String pckg = clazz.containingPackage().name();
    return "android.widget".equals(pckg) || "android.view".equals(pckg);
  }

  /**
   * Writes a list of values into a text files.
   *
   * @param pathname the absolute os path of the output file.
   * @param values the list of values to write.
   */
  private static void writeValues(String pathname, ArrayList<String> values) {
    FileWriter fw = null;
    BufferedWriter bw = null;
    try {
      fw = new FileWriter(pathname, false);
      bw = new BufferedWriter(fw);

      for (String value : values) {
        bw.append(value).append('\n');
      }
    } catch (IOException e) {
      // pass for now
    } finally {
      try {
        if (bw != null) bw.close();
      } catch (IOException e) {
        // pass for now
      }
      try {
        if (fw != null) fw.close();
      } catch (IOException e) {
        // pass for now
      }
    }
  }

  /**
   * Writes the widget/layout/layout param classes into a text files.
   *
   * @param pathname the absolute os path of the output file.
   * @param widgets the list of widget classes to write.
   * @param layouts the list of layout classes to write.
   * @param layoutParams the list of layout param classes to write.
   */
  private static void writeClasses(String pathname, ArrayList<ClassInfo> widgets,
      ArrayList<ClassInfo> layouts, ArrayList<ClassInfo> layoutParams) {
    FileWriter fw = null;
    BufferedWriter bw = null;
    try {
      fw = new FileWriter(pathname, false);
      bw = new BufferedWriter(fw);

      // write the 3 types of classes.
      for (ClassInfo clazz : widgets) {
        writeClass(bw, clazz, 'W');
      }
      for (ClassInfo clazz : layoutParams) {
        writeClass(bw, clazz, 'P');
      }
      for (ClassInfo clazz : layouts) {
        writeClass(bw, clazz, 'L');
      }
    } catch (IOException e) {
      // pass for now
    } finally {
      try {
        if (bw != null) bw.close();
      } catch (IOException e) {
        // pass for now
      }
      try {
        if (fw != null) fw.close();
      } catch (IOException e) {
        // pass for now
      }
    }
  }

  /**
   * Writes a class name and its super class names into a {@link BufferedWriter}.
   *
   * @param writer the BufferedWriter to write into
   * @param clazz the class to write
   * @param prefix the prefix to put at the beginning of the line.
   * @throws IOException
   */
  private static void writeClass(BufferedWriter writer, ClassInfo clazz, char prefix)
      throws IOException {
    writer.append(prefix).append(clazz.qualifiedName());
    ClassInfo superClass = clazz;
    while ((superClass = superClass.superclass()) != null) {
      writer.append(' ').append(superClass.qualifiedName());
    }
    writer.append('\n');
  }

  /**
   * Checks the inheritance of {@link ClassInfo} objects. This method return
   * <ul>
   * <li>{@link #TYPE_LAYOUT}: if the class extends <code>android.view.ViewGroup</code></li>
   * <li>{@link #TYPE_WIDGET}: if the class extends <code>android.view.View</code></li>
   * <li>{@link #TYPE_LAYOUT_PARAM}: if the class extends
   * <code>android.view.ViewGroup$LayoutParams</code></li>
   * <li>{@link #TYPE_NONE}: in all other cases</li>
   * </ul>
   *
   * @param clazz the {@link ClassInfo} to check.
   */
  private static int checkInheritance(ClassInfo clazz) {
    if ("android.view.ViewGroup".equals(clazz.qualifiedName())) {
      return TYPE_LAYOUT;
    } else if ("android.view.View".equals(clazz.qualifiedName())) {
      return TYPE_WIDGET;
    } else if ("android.view.ViewGroup.LayoutParams".equals(clazz.qualifiedName())) {
      return TYPE_LAYOUT_PARAM;
    }

    ClassInfo parent = clazz.superclass();
    if (parent != null) {
      return checkInheritance(parent);
    }

    return TYPE_NONE;
  }

  /**
   * Ensures a trailing '/' at the end of a string.
   */
  static String ensureSlash(String path) {
    return path.endsWith("/") ? path : path + "/";
  }

  /**
  * Process sample projects. Generate the TOC for the samples groups and project
  * and write it to a cs var, which is then written to files during templating to
  * html output. Collect metadata from sample project _index.jd files. Copy html
  * and specific source file types to the output directory.
  */
  public static void writeSamples(boolean offlineMode, ArrayList<SampleCode> sampleCodes,
      boolean sortNavByGroups) {
    samplesNavTree = makeHDF();

    // Go through samples processing files. Create a root list for SC nodes,
    // pass it to SCs for their NavTree children and append them.
    List<SampleCode.Node> samplesList = new ArrayList<SampleCode.Node>();
    List<SampleCode.Node> sampleGroupsRootNodes = null;
    for (SampleCode sc : sampleCodes) {
      samplesList.add(sc.setSamplesTOC(offlineMode));
     }
    if (sortNavByGroups) {
      sampleGroupsRootNodes = new ArrayList<SampleCode.Node>();
      for (SampleCode gsc : sampleCodeGroups) {
        String link =  ClearPage.toroot + "samples/" + gsc.mTitle.replaceAll(" ", "").trim().toLowerCase() + ".html";
        sampleGroupsRootNodes.add(new SampleCode.Node.Builder().setLabel(gsc.mTitle).setLink(link).setType("groupholder").build());
      }
    }
    // Pass full samplesList to SC to render the samples TOC to sampleNavTree hdf
    if (!offlineMode) {
      SampleCode.writeSamplesNavTree(samplesList, sampleGroupsRootNodes);
    }
    // Iterate the samplecode projects writing the files to out
    for (SampleCode sc : sampleCodes) {
      sc.writeSamplesFiles(offlineMode);
    }
  }

  /**
  * Given an initial samples directory root, walk through the directory collecting
  * sample code project roots and adding them to an array of SampleCodes.
  * @param rootDir Root directory holding all browseable sample code projects,
  *        defined in frameworks/base/Android.mk as "-sampleDir path".
  */
  public static void getSampleProjects(File rootDir) {
    for (File f : rootDir.listFiles()) {
      String name = f.getName();
      if (f.isDirectory()) {
        if (isValidSampleProjectRoot(f)) {
          sampleCodes.add(new SampleCode(f.getAbsolutePath(), "samples/" + name, name));
        } else {
          getSampleProjects(f);
        }
      }
    }
  }

  /**
  * Test whether a given directory is the root directory for a sample code project.
  * Root directories must contain a valid _index.jd file and a src/ directory
  * or a module directory that contains a src/ directory.
  */
  public static boolean isValidSampleProjectRoot(File dir) {
    File indexJd = new File(dir, "_index.jd");
    if (!indexJd.exists()) {
      return false;
    }
    File srcDir = new File(dir, "src");
    if (srcDir.exists()) {
      return true;
    } else {
      // Look for a src/ directory one level below the root directory, so
      // modules are supported.
      for (File childDir : dir.listFiles()) {
        if (childDir.isDirectory()) {
          srcDir = new File(childDir, "src");
          if (srcDir.exists()) {
            return true;
          }
        }
      }
      return false;
    }
  }

  public static String getDocumentationStringForAnnotation(String annotationName) {
    if (!documentAnnotations) return null;
    if (annotationDocumentationMap == null) {
      annotationDocumentationMap = new HashMap<String, String>();
      // parse the file for map
      try {
        BufferedReader in = new BufferedReader(
            new FileReader(documentAnnotationsPath));
        try {
          String line = in.readLine();
          String[] split;
          while (line != null) {
            split = line.split(":");
            annotationDocumentationMap.put(split[0], split[1]);
            line = in.readLine();
          }
        } finally {
          in.close();
        }
      } catch (IOException e) {
        System.err.println("Unable to open annotations documentation file for reading: "
            + documentAnnotationsPath);
      }
    }
    return annotationDocumentationMap.get(annotationName);
  }

  public static void writeCompatConfig() {
    if (compatConfig == null) {
      return;
    }
    CompatInfo config = CompatInfo.readCompatConfig(compatConfig);
    Data data = makeHDF();
    config.makeHDF(data);
    setPageTitle(data, "Compatibility changes");
    // TODO - should we write the output to some other path?
    String outfile = "compatchanges.html";
    ClearPage.write(data, "compatchanges.cs", outfile);
  }

}
