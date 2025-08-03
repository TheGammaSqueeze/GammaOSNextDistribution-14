#!/usr/bin/env python3
#
# Copyright 2020 - The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""The iml/xml templates of AIDEgen."""



# Content of iml file.
FILE_IML = """\
<?xml version="1.0" encoding="UTF-8"?>
<module type="JAVA_MODULE" version="4">
@FACETS@
    <component name="NewModuleRootManager" inherit-compiler-output="true">
        <exclude-output />
@SOURCES@
@SRCJAR@
        <orderEntry type="sourceFolder" forTests="false" />
@MODULE_DEPENDENCIES@
        <orderEntry type="inheritedJdk" />
    </component>
</module>
"""
# TODO(b/153704028): Refactor to create iml file.
IML = """\
<?xml version="1.0" encoding="UTF-8"?>
<module type="JAVA_MODULE" version="4">{FACET}
    <component name="NewModuleRootManager" inherit-compiler-output="true">
        <exclude-output />{SOURCES}
        <orderEntry type="sourceFolder" forTests="false" />{SRCJARS}{DEPENDENCIES}{JARS}
        <orderEntry type="inheritedJdk" />
    </component>
</module>
"""
FACET = """
    <facet type="android" name="Android">
        <configuration />
    </facet>"""
CONTENT = """
        <content url="file://{MODULE_PATH}">{EXCLUDES}{SOURCES}
        </content>"""
SOURCE = """
            <sourceFolder url="file://{SRC}" isTestSource="{IS_TEST}" />"""
OTHER_SOURCE = """
        <content url="file://{SRC}">
            <sourceFolder url="file://{SRC}" isTestSource="{IS_TEST}" />
        </content>"""
SRCJAR = """
        <content url="jar://{SRCJAR}!/">
            <sourceFolder url="jar://{SRCJAR}!/" isTestSource="False" />
        </content>"""
JAR = """
        <orderEntry type="module-library" exported="">
          <library>
            <CLASSES>
              <root url="jar://{JAR}!/" />
            </CLASSES>
            <JAVADOC />
            <SOURCES />
          </library>
        </orderEntry>"""
DEPENDENCIES = """
        <orderEntry type="module" module-name="{MODULE}" />"""

# The template content of modules.xml.
XML_MODULES = """\
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
    <component name="ProjectModuleManager">
        <modules>
@MODULES@
@ENABLE_DEBUGGER_MODULE@
        </modules>
    </component>
</project>
"""

# The template content of vcs.xml.
XML_VCS = """\
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
    <component name="VcsDirectoryMappings">
{GIT_MAPPINGS}
    </component>
</project>
"""

# The template content of misc.xml
XML_MISC = """\
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
    <component name="ConfigCheckProjectState">
        <option name="disabledCheckers">
            <list>
                <option value="com.google.devtools.intellig.configcheck.JavacHeapChecker"/>
                <option value="com.google.devtools.intellig.configcheck.VcsMappingsChecker"/>
            </list>
        </option>
    </component>
    <component name="FrameworkDetectionExcludesConfiguration">
        <type id="android" />
    </component>
    <component name="ContinuousBuildConfigurationComponent">
        <builds>
            <build intervalToCheckBuild="1" buildKey="" buildLabel=""
                   enabled="false" tapBuild="false"/>
        </builds>
    </component>
    <component name="DependencyValidationManager">
        <option name="SKIP_IMPORT_STATEMENTS" value="false"/>
    </component>
    <component name="EntryPointsManager">
        <entry_points version="2.0"/>
    </component>
    <component name="JavadocGenerationManager">
        <option name="HEAP_SIZE"/>
        <option name="LOCALE"/>
        <option name="OPEN_IN_BROWSER" value="true"/>
        <option name="OPTION_DEPRECATED_LIST" value="true"/>
        <option name="OPTION_DOCUMENT_TAG_AUTHOR" value="false"/>
        <option name="OPTION_DOCUMENT_TAG_DEPRECATED" value="true"/>
        <option name="OPTION_DOCUMENT_TAG_USE" value="false"/>
        <option name="OPTION_DOCUMENT_TAG_VERSION" value="false"/>
        <option name="OPTION_HIERARCHY" value="true"/>
        <option name="OPTION_INDEX" value="true"/>
        <option name="OPTION_NAVIGATOR" value="true"/>
        <option name="OPTION_SCOPE" value="protected"/>
        <option name="OPTION_SEPARATE_INDEX" value="true"/>
        <option name="OTHER_OPTIONS" value=""/>
        <option name="OUTPUT_DIRECTORY"/>
    </component>
    <component name="Mach LOCAL_PREFIX stripper" stripping="true"/>
    <component name="ProjectResources">
        <default-html-doctype>http://www.w3.org/1999/xhtml
        </default-html-doctype>
    </component>
    <component name="ProjectRootManager" version="2" languageLevel="JDK_17"
               assert-keyword="true" project-jdk-name="JDK17"
               project-jdk-type="JavaSDK"/>
    <component name="WebServicesPlugin" addRequiredLibraries="true"/>
</project>
"""

# The template content of compiler.xml
XML_COMPILER = """\
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
    <component name="CompilerConfiguration">
        <option name="DEFAULT_COMPILER" value="Javac"/>
        <resourceExtensions/>
        <wildcardResourcePatterns>
            <entry name="?*.dtd"/>
            <entry name="?*.ftl"/>
            <entry name="?*.gif"/>
            <entry name="?*.html"/>
            <entry name="?*.jpeg"/>
            <entry name="?*.jpg"/>
            <entry name="?*.png"/>
            <entry name="?*.properties"/>
            <entry name="?*.tld"/>
            <entry name="?*.xml"/>
        </wildcardResourcePatterns>
        <annotationProcessing enabled="false" useClasspath="true"/>
    </component>
    <component name="JavacSettings">
        <option name="MAXIMUM_HEAP_SIZE" value="1024"/>
    </component>
</project>
"""

# The template content of codeStyleConfig.xml
XML_CODE_STYLE_CONFIG = """\
<component name="ProjectCodeStyleConfiguration">
  <state>
    <option name="USE_PER_PROJECT_SETTINGS" value="true" />
  </state>
</component>
"""

# The template content of Apache_2.xml
XML_APACHE_2 = """\
<component name="CopyrightManager">
    <copyright>
        <option name="notice"
                value="Copyright (C) &amp;#36;today.year The Android Open Source Project&#10;&#10;Licensed under the Apache License, Version 2.0 (the &quot;License&quot;);&#10;you may not use this file except in compliance with the License.&#10;You may obtain a copy of the License at&#10;&#10;     http://www.apache.org/licenses/LICENSE-2.0&#10;&#10;Unless required by applicable law or agreed to in writing, software&#10;distributed under the License is distributed on an &quot;AS IS&quot; BASIS,&#10;WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.&#10;See the License for the specific language governing permissions and&#10;limitations under the License."/>
        <option name="keyword" value="Copyright"/>
        <option name="allowReplaceKeyword" value=""/>
        <option name="myName" value="Apache 2"/>
        <option name="myLocal" value="true"/>
    </copyright>
</component>
"""

# The template content of copyright/profiles_settings.xml
XML_COPYRIGHT_PROFILES_SETTINGS = """\
<component name="CopyrightManager">
    <settings default="">
        <module2copyright>
            <element module="Project Files" copyright="Apache 2"/>
        </module2copyright>
    </settings>
</component>
"""

# The template content of inspectionProfiles/profiles_settings.xml
XML_INSPECTION_PROFILES_SETTINGS = """\
<component name="InspectionProjectProfileManager">
  <settings>
    <option name="PROJECT_PROFILE" value="Aidegen_Inspections" />
    <version value="1.0" />
  </settings>
</component>
"""

# The template content of inspectionProfiles/Aidegen_Inspections.xml
# N.b. this minimal configuration leaves most of the options unspecified,
# which means that they will be filled with default values set by Jetbrains.
XML_INSPECTIONS = """\
<component name="InspectionProjectProfileManager">
  <profile version="1.0">
    <option name="myName" value="Aidegen_Inspections" />
    <inspection_tool class="JavaDoc" enabled="true" level="WARNING" enabled_by_default="true">
      <option name="myAdditionalJavadocTags" value="hide,attr" />
    </inspection_tool>
    <inspection_tool class="MissortedModifiers" enabled="true" level="WARNING" enabled_by_default="true">
      <option name="m_requireAnnotationsFirst" value="true" />
    </inspection_tool>
  </profile>
</component>
"""

# The configuration of JDK on Linux.
LINUX_JDK_XML = """\
    <jdk version="2">
      <name value="JDK17" />
      <type value="JavaSDK" />
      <version value="java version &quot;17.0.4&quot;" />
      <homePath value="{JDKpath}" />
      <roots>
        <annotationsPath>
          <root type="composite">
            <root url="jar://$APPLICATION_HOME_DIR$/plugins/java/lib/jdkAnnotations.jar!/" type="simple" />
          </root>
        </annotationsPath>
        <classPath>
          <root type="composite">
            <root url="jrt://{JDKpath}!/java.base" type="simple" />
            <root url="jrt://{JDKpath}!/java.compiler" type="simple" />
            <root url="jrt://{JDKpath}!/java.datatransfer" type="simple" />
            <root url="jrt://{JDKpath}!/java.desktop" type="simple" />
            <root url="jrt://{JDKpath}!/java.instrument" type="simple" />
            <root url="jrt://{JDKpath}!/java.logging" type="simple" />
            <root url="jrt://{JDKpath}!/java.management" type="simple" />
            <root url="jrt://{JDKpath}!/java.management.rmi" type="simple" />
            <root url="jrt://{JDKpath}!/java.naming" type="simple" />
            <root url="jrt://{JDKpath}!/java.net.http" type="simple" />
            <root url="jrt://{JDKpath}!/java.prefs" type="simple" />
            <root url="jrt://{JDKpath}!/java.rmi" type="simple" />
            <root url="jrt://{JDKpath}!/java.scripting" type="simple" />
            <root url="jrt://{JDKpath}!/java.se" type="simple" />
            <root url="jrt://{JDKpath}!/java.security.jgss" type="simple" />
            <root url="jrt://{JDKpath}!/java.security.sasl" type="simple" />
            <root url="jrt://{JDKpath}!/java.smartcardio" type="simple" />
            <root url="jrt://{JDKpath}!/java.sql" type="simple" />
            <root url="jrt://{JDKpath}!/java.sql.rowset" type="simple" />
            <root url="jrt://{JDKpath}!/java.transaction.xa" type="simple" />
            <root url="jrt://{JDKpath}!/java.xml" type="simple" />
            <root url="jrt://{JDKpath}!/java.xml.crypto" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.accessibility" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.attach" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.charsets" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.compiler" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.crypto.cryptoki" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.crypto.ec" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.dynalink" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.editpad" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.hotspot.agent" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.httpserver" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.incubator.foreign" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.incubator.vector" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.internal.ed" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.internal.jvmstat" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.internal.le" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.internal.opt" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.internal.vm.ci" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.internal.vm.compiler" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.internal.vm.compiler.management" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.jartool" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.javadoc" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.jcmd" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.jconsole" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.jdeps" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.jdi" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.jdwp.agent" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.jfr" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.jlink" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.jpackage" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.jshell" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.jsobject" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.jstatd" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.localedata" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.management" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.management.agent" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.management.jfr" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.naming.dns" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.naming.rmi" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.net" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.nio.mapmode" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.random" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.sctp" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.security.auth" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.security.jgss" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.unsupported" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.unsupported.desktop" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.xml.dom" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.zipfs" type="simple" />
          </root>
        </classPath>
        <javadocPath>
          <root type="composite" />
        </javadocPath>
        <sourcePath>
          <root type="composite">
            <root url="jar://{JDKpath}/lib/src.zip!/java.se" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.jdi" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.jfr" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.net" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.rmi" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.sql" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.xml" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.jcmd" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.sctp" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.base" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.jdeps" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.jlink" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.zipfs" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.prefs" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.attach" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.jshell" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.jstatd" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.random" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.naming" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.editpad" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.jartool" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.javadoc" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.xml.dom" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.desktop" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.logging" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.charsets" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.compiler" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.dynalink" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.jconsole" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.jpackage" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.jsobject" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.compiler" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.net.http" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.crypto.ec" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.scripting" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.httpserver" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.jdwp.agent" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.localedata" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.management" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.naming.dns" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.naming.rmi" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.instrument" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.management" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.sql.rowset" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.xml.crypto" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.internal.ed" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.internal.le" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.nio.mapmode" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.unsupported" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.smartcardio" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.internal.opt" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.datatransfer" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.accessibility" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.hotspot.agent" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.security.auth" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.security.jgss" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.security.jgss" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.security.sasl" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.internal.vm.ci" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.management.jfr" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.management.rmi" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.transaction.xa" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.crypto.cryptoki" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.incubator.vector" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.internal.jvmstat" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.management.agent" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.incubator.foreign" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.unsupported.desktop" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.internal.vm.compiler" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.internal.vm.compiler.management" type="simple" />
          </root>
        </sourcePath>
      </roots>
      <additional />
    </jdk>
"""

# The configuration of JDK on Mac.
MAC_JDK_XML = """\
    <jdk version="2">
      <name value="JDK17" />
      <type value="JavaSDK" />
      <version value="java version &quot;17.0.4&quot;" />
      <homePath value="{JDKpath}" />
      <roots>
        <annotationsPath>
          <root type="composite">
            <root url="jar://$APPLICATION_HOME_DIR$/plugins/java/lib/jdkAnnotations.jar!/" type="simple" />
          </root>
        </annotationsPath>
        <classPath>
          <root type="composite">
            <root url="jrt://{JDKpath}!/java.base" type="simple" />
            <root url="jrt://{JDKpath}!/java.compiler" type="simple" />
            <root url="jrt://{JDKpath}!/java.datatransfer" type="simple" />
            <root url="jrt://{JDKpath}!/java.desktop" type="simple" />
            <root url="jrt://{JDKpath}!/java.instrument" type="simple" />
            <root url="jrt://{JDKpath}!/java.logging" type="simple" />
            <root url="jrt://{JDKpath}!/java.management" type="simple" />
            <root url="jrt://{JDKpath}!/java.management.rmi" type="simple" />
            <root url="jrt://{JDKpath}!/java.naming" type="simple" />
            <root url="jrt://{JDKpath}!/java.net.http" type="simple" />
            <root url="jrt://{JDKpath}!/java.prefs" type="simple" />
            <root url="jrt://{JDKpath}!/java.rmi" type="simple" />
            <root url="jrt://{JDKpath}!/java.scripting" type="simple" />
            <root url="jrt://{JDKpath}!/java.se" type="simple" />
            <root url="jrt://{JDKpath}!/java.security.jgss" type="simple" />
            <root url="jrt://{JDKpath}!/java.security.sasl" type="simple" />
            <root url="jrt://{JDKpath}!/java.smartcardio" type="simple" />
            <root url="jrt://{JDKpath}!/java.sql" type="simple" />
            <root url="jrt://{JDKpath}!/java.sql.rowset" type="simple" />
            <root url="jrt://{JDKpath}!/java.transaction.xa" type="simple" />
            <root url="jrt://{JDKpath}!/java.xml" type="simple" />
            <root url="jrt://{JDKpath}!/java.xml.crypto" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.accessibility" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.attach" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.charsets" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.compiler" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.crypto.cryptoki" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.crypto.ec" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.dynalink" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.editpad" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.hotspot.agent" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.httpserver" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.incubator.foreign" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.incubator.vector" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.internal.ed" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.internal.jvmstat" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.internal.le" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.internal.opt" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.internal.vm.ci" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.internal.vm.compiler" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.internal.vm.compiler.management" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.jartool" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.javadoc" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.jcmd" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.jconsole" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.jdeps" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.jdi" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.jdwp.agent" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.jfr" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.jlink" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.jpackage" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.jshell" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.jsobject" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.jstatd" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.localedata" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.management" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.management.agent" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.management.jfr" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.naming.dns" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.naming.rmi" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.net" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.nio.mapmode" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.random" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.sctp" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.security.auth" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.security.jgss" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.unsupported" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.unsupported.desktop" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.xml.dom" type="simple" />
            <root url="jrt://{JDKpath}!/jdk.zipfs" type="simple" />
          </root>
        </classPath>
        <javadocPath>
          <root type="composite" />
        </javadocPath>
        <sourcePath>
          <root type="composite">
            <root url="jar://{JDKpath}/lib/src.zip!/java.se" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.jdi" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.jfr" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.net" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.rmi" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.sql" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.xml" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.jcmd" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.sctp" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.base" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.jdeps" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.jlink" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.zipfs" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.prefs" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.attach" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.jshell" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.jstatd" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.random" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.naming" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.editpad" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.jartool" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.javadoc" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.xml.dom" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.desktop" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.logging" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.charsets" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.compiler" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.dynalink" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.jconsole" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.jpackage" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.jsobject" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.compiler" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.net.http" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.crypto.ec" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.scripting" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.httpserver" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.jdwp.agent" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.localedata" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.management" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.naming.dns" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.naming.rmi" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.instrument" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.management" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.sql.rowset" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.xml.crypto" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.internal.ed" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.internal.le" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.nio.mapmode" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.unsupported" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.smartcardio" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.internal.opt" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.datatransfer" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.accessibility" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.hotspot.agent" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.security.auth" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.security.jgss" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.security.jgss" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.security.sasl" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.internal.vm.ci" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.management.jfr" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.management.rmi" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/java.transaction.xa" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.crypto.cryptoki" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.incubator.vector" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.internal.jvmstat" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.management.agent" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.incubator.foreign" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.unsupported.desktop" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.internal.vm.compiler" type="simple" />
            <root url="jar://{JDKpath}/lib/src.zip!/jdk.internal.vm.compiler.management" type="simple" />
          </root>
        </sourcePath>
      </roots>
      <additional />
    </jdk>
"""

# The file's header of CLion project file.
CMAKELISTS_HEADER = """\
# THIS FILE WAS AUTOMATICALLY GENERATED!
# ANY MODIFICATION WILL BE OVERWRITTEN!

# To improve project view in Clion    :
# Tools > CMake > Change Project Root

cmake_minimum_required(VERSION @MINVERSION@)
project(@PROJNAME@)
set(ANDROID_ROOT @ANDROIDROOT@)
"""

# The configuration of Android SDK.
ANDROID_SDK_XML = """\
    <jdk version="2">
      <name value="Android API {CODE_NAME} Platform" />
      <type value="Android SDK" />
      <version value="java version &quot;17.0.4&quot;" />
      <homePath value="{ANDROID_SDK_PATH}" />
      <roots>
        <annotationsPath>
          <root type="composite" >
            <root url="jar://{ANDROID_SDK_PATH}/platforms/{FOLDER_NAME}/data/annotations.zip!/" type="simple" />
          </root>
        </annotationsPath>
        <classPath>
          <root type="composite">
            <root url="jar://{ANDROID_SDK_PATH}/platforms/{FOLDER_NAME}/android.jar!/" type="simple" />
            <root url="file://{ANDROID_SDK_PATH}/platforms/{FOLDER_NAME}/data/res" type="simple" />
          </root>
        </classPath>
        <javadocPath>
          <root type="composite" >
            <root url="http://developer.android.com/reference/" type="simple" />
          </root>
        </javadocPath>
        <sourcePath>
          <root type="composite" />
        </sourcePath>
      </roots>
      <additional jdk="JDK17" sdk="android-{CODE_NAME}" />
    </jdk>
"""

# The configuration of TEST_MAPPING in jsonSchemas.xml.
TEST_MAPPING_SCHEMAS_XML = """\
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
  <component name="JsonSchemaMappingsProjectConfiguration">
    <state>
      <map>
        <entry key="TEST_MAPPING.config">
          <value>
            <SchemaInfo>
              <option name="name" value="TEST_MAPPING.config" />
              <option name="relativePathToSchema" value="{SCHEMA_PATH}" />
              <option name="schemaVersion" value="JSON schema version 7" />
              <option name="patterns">
                <list>
                  <Item>
                    <option name="path" value="TEST_MAPPING" />
                  </Item>
                </list>
              </option>
            </SchemaInfo>
          </value>
        </entry>
      </map>
    </state>
  </component>
</project>
"""

# The xml templates for Eclipse.
# .classpath template
ECLIPSE_CLASSPATH_XML = """<\
?xml version="1.0" encoding="UTF-8"?>
<classpath>
{SRC}
{LIB}
</classpath>
"""

# .project template
ECLIPSE_PROJECT_XML = """\
<?xml version="1.0" encoding="UTF-8"?>
<projectDescription>
        <name>{PROJECTNAME}</name>
        <comment></comment>
        <projects>
        </projects>
        <buildSpec>
                <buildCommand>
                        <name>org.eclipse.jdt.core.javabuilder</name>
                        <arguments>
                        </arguments>
                </buildCommand>
        </buildSpec>
        <natures>
                <nature>org.eclipse.jdt.core.javanature</nature>
        </natures>
        <linkedResources>
{LINKEDRESOURCES}
        </linkedResources>
</projectDescription>
"""

# The template of default AndroidManifest.xml.
ANDROID_MANIFEST_CONTENT = """\
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
          android:versionCode="1"
          android:versionName="1.0" >
</manifest>
"""

# The xml template for enabling debugger.
XML_ENABLE_DEBUGGER = """\
<?xml version="1.0" encoding="UTF-8"?>
<module type="JAVA_MODULE" version="4">
  <component name="FacetManager">
    <facet type="android" name="Android">
      <configuration />
    </facet>
  </component>
  <component name="NewModuleRootManager" inherit-compiler-output="true">
    <exclude-output />
    <content url="file://$MODULE_DIR$">
      <sourceFolder url="file://$MODULE_DIR$/src" isTestSource="false" />
      <sourceFolder url="file://$MODULE_DIR$/gen" isTestSource="false" generated="true" />
    </content>
    <orderEntry type="jdk" jdkName="{ANDROID_SDK_VERSION}" jdkType="Android SDK" />
    <orderEntry type="sourceFolder" forTests="false" />
  </component>
</module>
"""

# The default empty template of the jdk.table.xml.
JDK_TABLE_XML = """\
<application>
  <component name="ProjectJdkTable">
  </component>
</application>
"""

XML_WORKSPACE = """\
<?xml version="1.0" encoding="UTF-8"?>
<project version="4">
    <component name="VcsManagerConfiguration">
        <ignored-roots>
{GITS}
        </ignored-roots>
    </component>
</project>
"""

IGNORED_GITS = """\
<component name="VcsManagerConfiguration">
    <ignored-roots>{GITS}</ignored-roots>
  </component>
"""
