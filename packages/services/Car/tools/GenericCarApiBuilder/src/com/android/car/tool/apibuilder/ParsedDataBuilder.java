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

package com.android.car.tool.apibuilder;

import com.android.car.tool.data.AnnotationData;
import com.android.car.tool.data.ClassData;
import com.android.car.tool.data.ConstructorData;
import com.android.car.tool.data.FieldData;
import com.android.car.tool.data.MethodData;
import com.android.car.tool.data.PackageData;
import com.android.car.tool.data.ParsedData;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MemberValuePair;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ParsedDataBuilder {

    public static List<String> sErrors = new ArrayList<>();

    public static void populateParsedData(List<File> files, ParsedData data) throws Exception {
        if (data == null) {
            throw new NullPointerException("Parsed data object can't be null.");
        }

        for (File file : files) {
            populateParsedDataForFile(file, data);
        }
    }

    public static void populateParsedDataForFile(File file, ParsedData data) throws Exception {
        CompilationUnit cu = StaticJavaParser.parse(file);
        String packageName = cu.getPackageDeclaration().get().getNameAsString();

        PackageData packageData = data.getPackageData(packageName);

        new VoidVisitorAdapter<PackageData>() {
            @Override
            public void visit(ClassOrInterfaceDeclaration n, PackageData packageData) {

                ClassData classData = getClassData(n, packageName);

                if (classData == null) {
                    super.visit(n, packageData);
                    return;
                }

                // update constructor info
                List<ConstructorDeclaration> constructors = n.getConstructors();
                for (int i = 0; i < constructors.size(); i++) {
                    ConstructorDeclaration constructor = constructors.get(i);
                    ConstructorData constructorData = getConstructorData(n, constructor);
                    if (constructorData == null) {
                        continue;
                    }

                    constructorData.annotationData = getAnnotationData(
                            constructor.getAnnotations());
                    classData.constructors.put(constructorData.fullConstructorName,
                            constructorData);
                }

                // update field info
                List<FieldDeclaration> fields = n.getFields();
                for (int i = 0; i < fields.size(); i++) {
                    FieldDeclaration field = fields.get(i);
                    FieldData fieldData = getFieldData(n, field);
                    if (fieldData == null) {
                        continue;
                    }

                    fieldData.annotationData = getAnnotationData(field.getAnnotations());
                    classData.fields.put(fieldData.fieldName, fieldData);
                }

                // update method info
                List<MethodDeclaration> methods = n.getMethods();
                for (int i = 0; i < methods.size(); i++) {
                    MethodDeclaration method = methods.get(i);
                    MethodData methodData = getMethodData(n, method);
                    if (methodData == null) {
                        continue;
                    }

                    methodData.annotationData = getAnnotationData(method.getAnnotations());
                    CompilationUnit.Storage storage = cu.getStorage().get();
                    methodData.fileName = storage.getDirectory() + "/" + storage.getFileName();
                    classData.methods.put(methodData.fullMethodname, methodData);
                }

                packageData.addClass(classData);
                super.visit(n, packageData);
            }
        }.visit(cu, packageData);

    }

    public static ClassData getClassData(ClassOrInterfaceDeclaration n, String packageName) {
        // Don't need any data for the private or package-private classes.
        if (!n.isPublic()
                && !n.isProtected()) {
            return null;
        }

        String fullyQualifiedClasName = n.getFullyQualifiedName()
                .get();
        String onlyClassName = n.getFullyQualifiedName().get()
                .substring(packageName.length() + 1);
        String useableClassName = packageName + "." + onlyClassName.replace(".", "$");

        boolean isClassHidden = false;

        if (!n.getJavadoc().isEmpty()) {
            isClassHidden = n.getJavadoc().get().toText().contains("@hide");
        }

        ClassData classData = new ClassData();
        classData.fullyQualifiedClassName = fullyQualifiedClasName;
        classData.onlyClassName = onlyClassName;
        classData.useableClassName = useableClassName;
        classData.isClassHidden = isClassHidden;
        classData.isInterface = n.isInterface();
        classData.annotationData = getAnnotationData(n.getAnnotations());
        return classData;
    }

    public static String getVersion(AnnotationExpr annotationExpr, String parameterName) {
        List<MemberValuePair> children = annotationExpr
                .getChildNodesByType(MemberValuePair.class);
        for (MemberValuePair memberValuePair : children) {
            if (parameterName.equals(memberValuePair.getNameAsString())) {
                if (memberValuePair.getValue() == null) {
                    return "0";
                }
                return memberValuePair.getValue().toString();
            }
        }
        return "0";
    }

    public static FieldData getFieldData(ClassOrInterfaceDeclaration n, FieldDeclaration field) {
        // No need for private fields of interface
        if (n.isInterface() && field.isPrivate()) {
            return null;
        }

        // No need for the private or package-private field of class
        if (!n.isInterface() && !field.isPublic() && !field.isProtected()) {
            return null;
        }

        String fieldName = field.getVariables().get(0).getName().asString();
        String fieldType = field.getVariables().get(0).getTypeAsString();
        boolean fieldInitialized = !field.getVariables().get(0).getInitializer()
                .isEmpty();

        String fieldInitializedValue = "";
        if (fieldInitialized) {
            fieldInitializedValue = field.getVariables().get(0).getInitializer().get()
                    .toString();
        }

        boolean isHidden = false;
        if (!field.getJavadoc().isEmpty()) {
            isHidden = field.getJavadoc().get().toText().contains("@hide");
        }

        FieldData fieldData = new FieldData(fieldName, fieldType);
        fieldData.isFieldInitialized = fieldInitialized;
        fieldData.fieldInitializedValue = fieldInitializedValue;
        fieldData.isHidden = isHidden;
        return fieldData;
    }

    public static MethodData getMethodData(ClassOrInterfaceDeclaration n,
            MethodDeclaration method) {
        if (n.isInterface() && method.isPrivate()) {
            return null;
        }
        if (!n.isInterface() && !method.isPublic() && !method.isProtected()) {
            return null;
        }
        String returnType = method.getTypeAsString();
        String methodName = method.getName().asString();

        boolean isHidden = false;
        if (!method.getJavadoc().isEmpty()) {
            isHidden = method.getJavadoc().get().toText().contains("@hide");
        }

        StringBuilder fullMethodNameString = new StringBuilder();

        fullMethodNameString.append(methodName);
        fullMethodNameString.append("(");

        List<Parameter> parameters = method.getParameters();
        for (int k = 0; k < parameters.size(); k++) {
            Parameter parameter = parameters.get(k);
            fullMethodNameString.append(parameter.getTypeAsString());
            fullMethodNameString.append(" ");
            fullMethodNameString.append(parameter.getNameAsString());
            if (k < parameters.size() - 1) {
                fullMethodNameString.append(", ");
            }
        }
        fullMethodNameString.append(")");

        MethodData methodData = new MethodData(methodName, returnType);
        methodData.isHidden = isHidden;
        methodData.fullMethodname = fullMethodNameString.toString();
        methodData.firstBodyStatement = getFirstBodyStatement(method);
        return methodData;
    }

    public static AnnotationData getAnnotationData(NodeList<AnnotationExpr> annotations) {
        boolean isSystem = false;
        boolean hasAddedInOrBefore = false;
        int addedInOrBeforeMajorVersion = 0;
        int addedInOrBeforeMinorVersion = 0;
        boolean hasDeprecatedAddedInAnnotation = false;
        boolean hasAddedInAnnotation = false;
        String addedInPlatformVersion = "";
        boolean hasApiRequirementAnnotation = false;
        String minPlatformVersion = "";
        String minCarVersion = "";
        boolean hasRequiresApiAnnotation = false;
        String requiresApiVersion = "";

        for (int j = 0; j < annotations.size(); j++) {
            String annotationString = annotations.get(j).getName().asString();

            if (annotationString.contains("SystemApi")) {
                isSystem = true;
            }

            if (annotationString.contains("AddedInOrBefore")) {
                hasAddedInOrBefore = true;
                addedInOrBeforeMajorVersion = Integer
                        .parseInt(getVersion(annotations.get(j), "majorVersion"));
                if (addedInOrBeforeMajorVersion == 0) {
                    sErrors.add("Incorrect Annotation. annotationString: " + annotationString);
                }
                addedInOrBeforeMinorVersion = Integer
                        .parseInt(getVersion(annotations.get(j), "minorVersion"));
            }

            if (annotationString.contains("AddedIn")
                    && !annotationString.contains("AddedInOrBefore")) {
                // It may be old AddedIn which is deprecated. Chances are low as it is
                // no longer used but check.
                if (Integer.parseInt(
                        getVersion(annotations.get(j), "majorVersion")) != 0) {
                    hasDeprecatedAddedInAnnotation = true;
                    sErrors.add("Incorrect Annotation. annotationString: " + annotationString);
                } else {
                    sErrors.add("Incorrect Annotation. annotationString: " + annotationString);
                    hasAddedInAnnotation = true;
                    String fullPlatformVersion = getVersion(annotations.get(j),
                            "value");
                    if (Objects.equals(fullPlatformVersion, "0")) {
                        fullPlatformVersion = annotations.get(j).toString()
                                .split("\\(")[1].split("\\)")[0];
                    }
                    addedInPlatformVersion = getLastToken(fullPlatformVersion, "\\.");
                }
            }

            if (annotationString.contains("ApiRequirements")) {
                hasApiRequirementAnnotation = true;
                String fullCarVersion = getVersion(annotations.get(j), "minCarVersion");
                minCarVersion = getLastToken(fullCarVersion, "\\.");

                String fullplatformVersion = getVersion(annotations.get(j),
                        "minPlatformVersion");
                minPlatformVersion = getLastToken(fullplatformVersion, "\\.");
            }

            if (annotationString.contains("RequiresApi")) {
                hasRequiresApiAnnotation = true;
                String fullRequiresApi = getVersion(annotations.get(j), "api");

                // if RequiresApi doesn't have "api" parameter
                if (Objects.equals(fullRequiresApi, "0")) {
                    fullRequiresApi = getVersion(annotations.get(j), "value");
                }

                // if RequiresApi doesn't have "value" parameter. Means no parameter
                if (Objects.equals(fullRequiresApi, "0")) {
                    fullRequiresApi = annotations.get(j).toString()
                            .split("\\(")[1].split("\\)")[0];
                }

                requiresApiVersion = getLastToken(fullRequiresApi, "\\.");
            }

        }

        AnnotationData annotationData = new AnnotationData();
        annotationData.isSystemApi = isSystem;
        annotationData.hasAddedInOrBefore = hasAddedInOrBefore;
        annotationData.addedInOrBeforeMajorVersion = addedInOrBeforeMajorVersion;
        annotationData.addedInOrBeforeMinorVersion = addedInOrBeforeMinorVersion;
        annotationData.hasDeprecatedAddedInAnnotation = hasDeprecatedAddedInAnnotation;
        annotationData.hasAddedInAnnotation = hasAddedInAnnotation;
        annotationData.addedInPlatformVersion = addedInPlatformVersion;
        annotationData.hasApiRequirementAnnotation = hasApiRequirementAnnotation;
        annotationData.minPlatformVersion = minPlatformVersion;
        annotationData.minCarVersion = minCarVersion;
        annotationData.hasRequiresApiAnnotation = hasRequiresApiAnnotation;
        annotationData.requiresApiVersion = requiresApiVersion;
        return annotationData;
    }

    private static String getLastToken(String s, String pattern) {
        return s.split(pattern)[(s.split(pattern).length - 1)];
    }

    public static ConstructorData getConstructorData(ClassOrInterfaceDeclaration n,
            ConstructorDeclaration constructor) {
        if (!n.isInterface() && !constructor.isPublic() && !constructor.isProtected()) {
            return null;
        }

        String constructorName = constructor.getName().asString();

        StringBuilder parametersString = new StringBuilder();

        parametersString.append(constructorName);
        parametersString.append("(");

        List<Parameter> parameters = constructor.getParameters();
        for (int k = 0; k < parameters.size(); k++) {
            Parameter parameter = parameters.get(k);
            parametersString.append(parameter.getTypeAsString());
            parametersString.append(" ");
            parametersString.append(parameter.getNameAsString());
            if (k < parameters.size() - 1) {
                parametersString.append(", ");
            }
        }
        parametersString.append(")");

        boolean isHidden = false;

        if (!constructor.getJavadoc().isEmpty()) {
            isHidden = constructor.getJavadoc().get().toText().contains("@hide");
        }

        ConstructorData constructorData = new ConstructorData(constructorName);
        constructorData.isHidden = isHidden;
        constructorData.fullConstructorName = parametersString.toString();

        return constructorData;
    }

    private static Statement getFirstBodyStatement(MethodDeclaration method) {
        if (method.getBody().isEmpty() || method.getBody().get().isEmpty()) {
            return null;
        }
        return method.getBody().get().getStatement(0);
    }
}
