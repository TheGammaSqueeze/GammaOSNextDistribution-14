/*
 * Copyright (C) 2020 Google LLC.
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

package android.linkerconfig.gts.utils.elements;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Section {
    public Section() {
        Namespace defaultNamespace = new Namespace();
        defaultNamespace.name = "default";

        namespaces.put("default", defaultNamespace);
    }

    static final String REGEX_ADDITIONAL_NAMESPACES =
            "additional\\.namespaces\\s*=\\s*((?:[\\w]+)(?:,[\\w]+)*)";
    static final String REGEX_NAMESPACE =
            "namespace\\.(\\w+)\\.([^\\s=]+)\\s*(?:=|\\+=)\\s*([^\\s]+)";

    static Pattern sAdditionalNamespacesPattern = Pattern.compile(REGEX_ADDITIONAL_NAMESPACES);
    static Pattern sNamespacePattern = Pattern.compile(REGEX_NAMESPACE);

    public String name;
    public Map<String, Namespace> namespaces = new HashMap<>();


    public Namespace getNamespaceFromName(String namespace) {
        assertTrue("Failed to find namespace " + namespace + " from section " + name,
                namespaces.containsKey(namespace));

        return namespaces.get(namespace);
    }

    public void parseConfiguration(String line) {
        Matcher additionalNamespacesMatcher = sAdditionalNamespacesPattern.matcher(line);
        if (additionalNamespacesMatcher.matches()) {
            assertEquals("Additional namespaces are already defined.", 1, namespaces.size());
            String additionalNamespaces = additionalNamespacesMatcher.group(1);
            for (String additionalNamespace : additionalNamespaces.split(",")) {
                Namespace ns = new Namespace();
                ns.name = additionalNamespace;
                namespaces.put(additionalNamespace, ns);
            }

            return;
        }

        Matcher namespaceMatcher = sNamespacePattern.matcher(line);
        assertTrue("Cannot parse : " + line, namespaceMatcher.matches());

        String targetNamespace = namespaceMatcher.group(1);
        String[] commands = namespaceMatcher.group(2).split("\\.");
        String value = namespaceMatcher.group(3);

        assertTrue("Cannot find namespace " + targetNamespace,
                namespaces.containsKey(targetNamespace));

        Namespace ns = getNamespaceFromName(targetNamespace);

        assertTrue("Invalid command : " + namespaceMatcher.group(2) + " from " + line,
                commands.length > 0);
        switch (commands[0]) {
            case "isolated":
                assertEquals("Invalid command : " + line, 1, commands.length);
                ns.isIsolated = Boolean.parseBoolean(value);
                return;
            case "visible":
                assertEquals("Invalid command : " + line, 1, commands.length);
                ns.isVisible = Boolean.parseBoolean(value);
                return;
            case "search":
                assertEquals("Invalid command : " + line, 2, commands.length);
                assertEquals("Invalid command : " + line, "paths", commands[1]);
                if (!value.isEmpty()) {
                    ns.searchPaths.add(value);
                }
                return;
            case "permitted":
                assertEquals("Invalid command : " + line, 2, commands.length);
                assertEquals("Invalid command : " + line, "paths", commands[1]);
                if (!value.isEmpty()) {
                    ns.permittedPaths.add(value);
                }
                return;
            case "asan":
            case "hwasan":
                // Do not parse configuration for hwasan or asan in this test
                assertEquals("Invalid command : " + line, 3, commands.length);
                assertTrue("Invalid command : " + line,
                        commands[1].equals("search") || commands[1].equals("permitted"));
                assertEquals("Invalid command : " + line, "paths", commands[2]);
                return;
            case "links":
                assertEquals("Invalid command : " + line, 1, commands.length);
                for (String linkTarget : value.split(",")) {
                    assertTrue("Invalid target namespace : " + linkTarget + " from " + line,
                            namespaces.containsKey(linkTarget));
                    Link link = new Link();
                    link.from = ns;
                    link.to = getNamespaceFromName(linkTarget);
                    ns.links.put(linkTarget, link);
                }
                return;
            case "link":
                assertEquals("Invalid command : " + line, 3, commands.length);
                String linkTarget = commands[1];
                assertTrue("Link not defined : " + linkTarget + " from " + line,
                        ns.links.containsKey(linkTarget));
                Link link = ns.links.get(linkTarget);

                if (commands[2].equals("allow_all_shared_libs")) {
                    link.allowAll = Boolean.parseBoolean(value);
                } else if (commands[2].equals("shared_libs")) {
                    String[] libs = value.split(":");
                    for (String lib : libs) {
                        link.libraries.add(lib);
                    }
                } else {
                    fail("Invalid link command : " + line);
                }
                return;
            case "whitelisted":
            case "allowed_libs":
                assertEquals("Invalid command : " + line, 1, commands.length);
                // Do not parse allowed library list in this test
                return;
        }


        fail("Unable to parse command : " + line);
        return;
    }
}
