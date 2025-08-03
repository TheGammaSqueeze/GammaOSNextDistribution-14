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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

/**
 * Represents a minimal representation of a method for comparison purposes
 */
public final class MethodSignature {

    /** Create a {@link MethodSignature} for the given {@link ExecutableElement}. */
    public static MethodSignature forMethod(ExecutableElement method, Elements elements) {
        List<TypeMirror> parameters = method.getParameters().stream()
                .map(Element::asType)
                .map(m -> rawType(m, elements))
                .collect(Collectors.toList());

        Set<TypeMirror> exceptions = method.getThrownTypes()
                .stream().map(m -> rawType(m, elements))
                .collect(Collectors.toSet());

        return new MethodSignature(Visibility.ofMethod(method),
                rawType(method.getReturnType(), elements),
                method.getSimpleName().toString(), parameters, exceptions);
    }

    private static TypeMirror rawType(TypeMirror type, Elements elements) {
        if (type instanceof DeclaredType) {
            DeclaredType t = (DeclaredType) type;
            if (!t.getTypeArguments().isEmpty()) {
                type = elements.getTypeElement(t.toString().split("<", 2)[0]).asType();
            }
        }
        return type;
    }

    /**
     * Create a {@link MethodSignature} for the given string from an API file.
     */
    public static /* @Nullable */ MethodSignature forApiString(
            String string, Types types, Elements elements) {
        // Strip annotations
        string = string.replaceAll("@\\w+?\\(.+?\\) ", "");
        string = string.replaceAll("@.+? ", "");

        String[] parts = string.split(" ", 2);
        Visibility visibility;
        try {
            visibility = Visibility.valueOf(parts[0].toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Error finding visibility in string " + string);
        }
        string = parts[1];
        parts = string.split(" ", 2);

        TypeMirror returnType;
        while (parts[0].equals("abstract") || parts[0].equals("final")
                || parts[0].equals("static")) {
            // These don't affect the signature in ways we care about
            string = parts[1];
            parts = string.split(" ", 2);
        }

        if (string.startsWith("<")) {
            // This includes type arguments, for now we ignore this method
            return null;
        }

        returnType = typeForString(parts[0], types, elements);

        string = parts[1];
        parts = string.split("\\(", 2);
        String methodName = parts[0];
        string = parts[1];
        parts = string.split("\\)", 2);
        // Remove generic types as we don't need to care about them at this point
        String parametersString = parts[0].replaceAll("<.*>", "");
        // Remove varargs
        parametersString = parametersString.replaceAll("\\.\\.\\.", "");
        List<TypeMirror> parameters;
        try {
            parameters = Arrays.stream(parametersString.split(","))
                    .map(String::trim)
                    .filter(t -> !t.isEmpty())
                    .map(t -> typeForString(t, types, elements))
                    .collect(Collectors.toList());
        } catch (IllegalStateException e) {
            throw new IllegalStateException("Error parsing types from string " + parametersString);
        }
        string = parts[1];
        Set<TypeMirror> exceptions = new HashSet<>();
        if (string.contains("throws")) {
            exceptions = Arrays.stream(string.split("throws ", 2)[1].split(","))
                    .map(t -> t.trim())
                    .filter(t -> !t.isEmpty())
                    .map(t -> typeForString(t, types, elements))
                    .collect(Collectors.toSet());
        }

        return new MethodSignature(visibility, returnType, methodName, parameters, exceptions);
    }

    private static TypeMirror typeForString(String typeName, Types types, Elements elements) {
        if (typeName.equals("void")) {
            return types.getNoType(TypeKind.VOID);
        }

        if (typeName.contains("<")) {
            // Because of type erasure we can just drop the type argument
            return typeForString(typeName.split("<", 2)[0], types, elements);
        }

        if (typeName.endsWith("[]")) {
            return types.getArrayType(
                    typeForString(typeName.substring(0, typeName.length() - 2), types, elements));
        }

        try {
            return types.getPrimitiveType(TypeKind.valueOf(typeName.toUpperCase()));
        } catch (IllegalArgumentException e) {
            // Not a primitive
        }

        TypeElement element = elements.getTypeElement(typeName);
        if (element == null) {
            // It could be java.lang
            element = elements.getTypeElement("java.lang." + typeName);
        }

        if (element == null) {
            throw new IllegalStateException("Unknown type: " + typeName);
        }

        return element.asType();
    }

    enum Visibility {
        PUBLIC,
        PROTECTED;

        static Visibility ofMethod(ExecutableElement method) {
            if (method.getModifiers().contains(Modifier.PUBLIC)) {
                return PUBLIC;
            } else if (method.getModifiers().contains(Modifier.PROTECTED)) {
                return PROTECTED;
            }

            throw new IllegalArgumentException("Only public and protected are visible in APIs");
        }
    }

    private final Visibility mVisibility;
    public final String mReturnType;
    private final String mName;
    public final ImmutableList<String> mParameterTypes;
    public final ImmutableSet<String> mExceptions;
    public MethodSignature(
            Visibility visibility, TypeMirror returnType, String name,
            List<TypeMirror> parameterTypes, Set<TypeMirror> exceptions) {
        mVisibility = visibility;
        mReturnType = returnType.toString();
        mName = name;
        mParameterTypes = ImmutableList.copyOf(parameterTypes.stream()
                .map(TypeMirror::toString)
                .collect(Collectors.toList()));
        mExceptions = ImmutableSet.copyOf(exceptions.stream().map(TypeMirror::toString).collect(
                Collectors.toSet()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MethodSignature)) return false;
        MethodSignature that = (MethodSignature) o;
        return mVisibility == that.mVisibility && mReturnType.equals(that.mReturnType)
                && mName.equals(
                that.mName) && mParameterTypes.equals(that.mParameterTypes) && mExceptions.equals(
                that.mExceptions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mVisibility, mReturnType, mName, mParameterTypes, mExceptions);
    }

    @Override
    public String toString() {
        return "MethodSignature{"
                + "mVisibility="
                + mVisibility
                + ", mReturnType='" + mReturnType + '\''
                + ", mName='" + mName + '\''
                + ", mParameterTypes=" + mParameterTypes
                + ", mExceptions=" + mExceptions
                + '}';
    }
}
