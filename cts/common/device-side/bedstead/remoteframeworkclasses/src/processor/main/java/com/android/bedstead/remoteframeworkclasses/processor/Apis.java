/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.bedstead.remoteframeworkclasses.processor;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * A collection of {@link MethodSignature} for accessible methods.
 */
public final class Apis {

    private static final String[] API_FILES =
            {"current.txt", "test-current.txt", "wifi-current.txt",
                    "bluetooth-current.txt", "system-current.txt"};

    private static final Map<String, String> API_TXTS = initialiseApiTxts();
    private static final Map<String, Apis> sPackageToApi = new HashMap<>();

    private static Map<String, String> initialiseApiTxts() {
        return Arrays.stream(API_FILES)
                .collect(Collectors.toMap(f -> f, f -> {
                    try {
                        return Resources.toString(Processor.class.getResource("/apis/" + f),
                                StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        throw new IllegalStateException("Could not read file " + f);
                    }
                }));
    }

    /**
     * Get public and test APIs for a given class name.
     */
    public static Apis forClass(String className, Types types, Elements elements) {
        if (sPackageToApi.containsKey(className)) {
            return sPackageToApi.get(className);
        }
        ImmutableSet.Builder<MethodSignature> methods = ImmutableSet.builder();
        Set<String> parents = new HashSet<>();
        findParents(parents, className, elements);
        for (String c : parents) {
            for (Map.Entry<String, String> apiTxt : API_TXTS.entrySet()) {
                methods.addAll(
                        parseApiTxt(apiTxt.getKey(), apiTxt.getValue(), c, types, elements));
            }
        }

        return new Apis(methods.build());
    }

    private static void findParents(Set<String> parents, String className, Elements elements) {
        parents.add(className);

        if (className.equals("java.lang.Object")) {
            return;
        }

        TypeElement element = elements.getTypeElement(className);
        System.out.println("Checking " + className + " got " + element);

        TypeMirror superClass = element.getSuperclass();
        if (!superClass.getKind().equals(TypeKind.NONE)) {
            findParents(parents, superClass.toString(), elements);
        }

        element.getInterfaces().stream().map(TypeMirror::toString)
                .forEach(c -> findParents(parents, c, elements));
    }

    private static Set<MethodSignature> parseApiTxt(
            String filename, String apiTxt, String className, Types types, Elements elements) {
        System.out.println("Parsing for " + className);

        int separatorPosition = className.lastIndexOf(".");
        String packageName = className.substring(0, separatorPosition);
        String simpleClassName = className.substring(separatorPosition + 1);

        String[] packageSplit = apiTxt.split("package " + packageName + " \\{", 2);
        if (packageSplit.length < 2) {
            System.out.println("Package " + packageName + " not in file " + filename);
            // Package not in this file
            return new HashSet<>();
        }
        String[] classSplit = packageSplit[1].split("class " + simpleClassName + " .*?\n", 2);
        if (classSplit.length < 2) {
            System.out.println("Class " + simpleClassName + " not in file " + filename);
            // Class not in this file
            return new HashSet<>();
        }
        String[] lines = classSplit[1].split("\n");
        Set<MethodSignature> methodSignatures = new HashSet<>();

        for (String line : lines) {
            String methodLine = line.trim();
            if (methodLine.isEmpty()) {
                continue;
            }

            if (methodLine.startsWith("ctor")) {
                // Skip constructors
                continue;
            }

            if (!methodLine.startsWith("method")) {
                return methodSignatures;
            }

            try {
                // Strip "method" and semicolon
                methodLine = methodLine.substring(7, methodLine.length() - 1);
                MethodSignature signature =
                        MethodSignature.forApiString(methodLine, types, elements);
                if (signature != null) {
                    methodSignatures.add(signature);
                }
            } catch (RuntimeException e) {
                throw new IllegalStateException("Error parsing method " + line, e);
            }
        }

        return methodSignatures;
    }

    private final ImmutableSet<MethodSignature> mMethods;

    private Apis(ImmutableSet<MethodSignature> methods) {
        mMethods = methods;
    }

    /**
     * Get methods in the API set.
     */
    public ImmutableSet<MethodSignature> methods() {
        return mMethods;
    }
}
