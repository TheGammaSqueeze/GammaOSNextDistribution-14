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

package com.android.car.tool.apibuilder.tests;

import static com.google.common.truth.Truth.assertThat;

import com.android.car.tool.apibuilder.ParsedDataBuilder;
import com.android.car.tool.data.AnnotationData;
import com.android.car.tool.data.ClassData;
import com.android.car.tool.data.ConstructorData;
import com.android.car.tool.data.FieldData;
import com.android.car.tool.data.MethodData;
import com.android.car.tool.data.PackageData;
import com.android.car.tool.data.ParsedData;

import com.github.javaparser.ast.expr.MethodCallExpr;

import org.junit.Test;

public final class ParsedDataBuilderTest extends TestHelper {

    @Test
    public void testClassData() throws Exception {
        ParsedData data = new ParsedData();
        String packageName = "android.car.user";
        ParsedDataBuilder.populateParsedDataForFile(getResourceFile("Test1.txt"), data);

        // Assert values
        PackageData packageData = data.getPackageData(packageName);
        assertThat(packageData).isNotNull();
        String className = "android.car.user.Test1";
        ClassData classData = packageData.classes.get(className);
        assertThat(classData).isNotNull();
        assertThat(classData.isClassHidden).isTrue();
        assertThat(classData.fullyQualifiedClassName).isEqualTo(className);
        assertThat(classData.onlyClassName).isEqualTo("Test1");
        assertThat(classData.useableClassName).isEqualTo(className);
        assertThat(classData.isInterface).isFalse();
        assertThat(classData.annotationData.isSystemApi).isTrue();
        assertThat(classData.constructors.size()).isEqualTo(1);
        ConstructorData constructorData = classData.constructors
                .get("Test1(Car car, IBinder service)");
        assertThat(constructorData.isHidden).isTrue();
        assertThat(constructorData.annotationData.isSystemApi).isFalse();

        className = "android.car.user.Test1.UserLifecycleEvent";
        classData = packageData.classes.get(className);
        assertThat(classData).isNotNull();
        assertThat(classData.isClassHidden).isFalse();
        assertThat(classData.fullyQualifiedClassName).isEqualTo(className);
        assertThat(classData.onlyClassName).isEqualTo("Test1.UserLifecycleEvent");
        assertThat(classData.useableClassName)
                .isEqualTo("android.car.user.Test1$UserLifecycleEvent");
        assertThat(classData.isInterface).isFalse();
        assertThat(classData.annotationData.isSystemApi).isFalse();
        assertThat(classData.constructors.size()).isEqualTo(2);
        constructorData = classData.constructors
                .get("UserLifecycleEvent(int eventType, int from, int to)");
        assertThat(constructorData.isHidden).isTrue();
        assertThat(constructorData.annotationData.isSystemApi).isFalse();
        constructorData = classData.constructors.get("UserLifecycleEvent(int eventType, int to)");
        assertThat(constructorData.isHidden).isFalse();
        assertThat(constructorData.annotationData.isSystemApi).isFalse();

        className = "android.car.user.Test1.UserLifecycleEvent.UserLifecycleListener2";
        classData = packageData.classes.get(className);
        assertThat(classData).isNotNull();
        assertThat(classData.isClassHidden).isTrue();
        assertThat(classData.fullyQualifiedClassName).isEqualTo(className);
        assertThat(classData.onlyClassName)
                .isEqualTo("Test1.UserLifecycleEvent.UserLifecycleListener2");
        assertThat(classData.useableClassName)
                .isEqualTo("android.car.user.Test1$UserLifecycleEvent$UserLifecycleListener2");
        assertThat(classData.isInterface).isTrue();
        assertThat(classData.annotationData.isSystemApi).isTrue();
        assertThat(classData.constructors.size()).isEqualTo(0);

        className = "android.car.user.Test1.UserLifecycleListener";
        classData = packageData.classes.get(className);
        assertThat(classData).isNotNull();
        assertThat(classData.isClassHidden).isTrue();
        assertThat(classData.fullyQualifiedClassName).isEqualTo(className);
        assertThat(classData.onlyClassName).isEqualTo("Test1.UserLifecycleListener");
        assertThat(classData.useableClassName)
                .isEqualTo("android.car.user.Test1$UserLifecycleListener");
        assertThat(classData.isInterface).isTrue();
        assertThat(classData.annotationData.isSystemApi).isTrue();
        assertThat(classData.constructors.size()).isEqualTo(0);
    }

    @Test
    public void testFieldDataAndAnnotationData() throws Exception {
        ParsedData data = new ParsedData();
        String packageName = "android.car.user";
        ParsedDataBuilder.populateParsedDataForFile(getResourceFile("Test1.txt"), data);

        PackageData packageData = data.getPackageData(packageName);
        String className = "android.car.user.Test1";
        ClassData classData = packageData.classes.get(className);
        assertThat(classData.fields.size()).isEqualTo(6);

        FieldData fieldData = classData.fields.get("FIELD_1");
        assertThat(fieldData.isHidden).isTrue();
        assertThat(fieldData.fieldInitializedValue).isEqualTo("\"value1\"");
        assertThat(fieldData.fieldName).isEqualTo("FIELD_1");
        assertThat(fieldData.fieldType).isEqualTo("String");
        assertThat(fieldData.isFieldInitialized).isTrue();
        AnnotationData annotationData = fieldData.annotationData;
        assertThat(annotationData.isSystemApi).isFalse();
        assertThat(annotationData.hasAddedInOrBefore).isTrue();
        assertThat(annotationData.addedInOrBeforeMajorVersion).isEqualTo(33);
        assertThat(annotationData.addedInOrBeforeMinorVersion).isEqualTo(0);
        assertThat(annotationData.hasDeprecatedAddedInAnnotation).isFalse();
        assertThat(annotationData.hasAddedInAnnotation).isFalse();
        assertThat(annotationData.addedInPlatformVersion).isEmpty();
        assertThat(annotationData.hasApiRequirementAnnotation).isFalse();
        assertThat(annotationData.minPlatformVersion).isEmpty();
        assertThat(annotationData.minCarVersion).isEmpty();
        assertThat(annotationData.hasRequiresApiAnnotation).isFalse();
        assertThat(annotationData.requiresApiVersion).isEmpty();

        fieldData = classData.fields.get("FIELD_2");
        assertThat(fieldData.isHidden).isFalse();
        assertThat(fieldData.fieldInitializedValue).isEqualTo("500");
        assertThat(fieldData.fieldName).isEqualTo("FIELD_2");
        assertThat(fieldData.fieldType).isEqualTo("int");
        assertThat(fieldData.isFieldInitialized).isTrue();
        annotationData = fieldData.annotationData;
        assertThat(annotationData.isSystemApi).isFalse();
        assertThat(annotationData.hasAddedInOrBefore).isFalse();
        assertThat(annotationData.addedInOrBeforeMajorVersion).isEqualTo(0);
        assertThat(annotationData.addedInOrBeforeMinorVersion).isEqualTo(0);
        assertThat(annotationData.hasDeprecatedAddedInAnnotation).isFalse();
        assertThat(annotationData.hasAddedInAnnotation).isFalse();
        assertThat(annotationData.addedInPlatformVersion).isEmpty();
        assertThat(annotationData.hasApiRequirementAnnotation).isFalse();
        assertThat(annotationData.minPlatformVersion).isEmpty();
        assertThat(annotationData.minCarVersion).isEmpty();
        assertThat(annotationData.hasRequiresApiAnnotation).isFalse();
        assertThat(annotationData.requiresApiVersion).isEmpty();

        fieldData = classData.fields.get("FIELD_3");
        assertThat(fieldData.isHidden).isTrue();
        assertThat(fieldData.fieldInitializedValue).isEmpty();
        assertThat(fieldData.fieldName).isEqualTo("FIELD_3");
        assertThat(fieldData.fieldType).isEqualTo("int");
        assertThat(fieldData.isFieldInitialized).isFalse();
        annotationData = fieldData.annotationData;
        assertThat(annotationData.isSystemApi).isTrue();
        assertThat(annotationData.hasAddedInOrBefore).isFalse();
        assertThat(annotationData.addedInOrBeforeMajorVersion).isEqualTo(0);
        assertThat(annotationData.addedInOrBeforeMinorVersion).isEqualTo(0);
        assertThat(annotationData.hasDeprecatedAddedInAnnotation).isTrue();
        assertThat(annotationData.hasAddedInAnnotation).isFalse();
        assertThat(annotationData.addedInPlatformVersion).isEmpty();
        assertThat(annotationData.hasApiRequirementAnnotation).isFalse();
        assertThat(annotationData.minPlatformVersion).isEmpty();
        assertThat(annotationData.minCarVersion).isEmpty();
        assertThat(annotationData.hasRequiresApiAnnotation).isTrue();
        assertThat(annotationData.requiresApiVersion).isEqualTo("34");

        fieldData = classData.fields.get("FIELD_4");
        assertThat(fieldData.isHidden).isTrue();
        assertThat(fieldData.fieldInitializedValue).isEqualTo("2");
        assertThat(fieldData.fieldName).isEqualTo("FIELD_4");
        assertThat(fieldData.fieldType).isEqualTo("int");
        assertThat(fieldData.isFieldInitialized).isTrue();
        annotationData = fieldData.annotationData;
        assertThat(annotationData.isSystemApi).isTrue();
        assertThat(annotationData.hasAddedInOrBefore).isFalse();
        assertThat(annotationData.addedInOrBeforeMajorVersion).isEqualTo(0);
        assertThat(annotationData.addedInOrBeforeMinorVersion).isEqualTo(0);
        assertThat(annotationData.hasDeprecatedAddedInAnnotation).isFalse();
        assertThat(annotationData.hasAddedInAnnotation).isTrue();
        assertThat(annotationData.addedInPlatformVersion).isEqualTo("TIRAMISU_0");
        assertThat(annotationData.hasApiRequirementAnnotation).isFalse();
        assertThat(annotationData.minPlatformVersion).isEmpty();
        assertThat(annotationData.minCarVersion).isEmpty();
        assertThat(annotationData.hasRequiresApiAnnotation).isTrue();
        assertThat(annotationData.requiresApiVersion).isEqualTo("UPSIDE_DOWN_CAKE");

        fieldData = classData.fields.get("FIELD_5");
        assertThat(fieldData.isHidden).isTrue();
        assertThat(fieldData.fieldInitializedValue).isEqualTo("2");
        assertThat(fieldData.fieldName).isEqualTo("FIELD_5");
        assertThat(fieldData.fieldType).isEqualTo("int");
        assertThat(fieldData.isFieldInitialized).isTrue();
        annotationData = fieldData.annotationData;
        assertThat(annotationData.isSystemApi).isTrue();
        assertThat(annotationData.hasAddedInOrBefore).isFalse();
        assertThat(annotationData.addedInOrBeforeMajorVersion).isEqualTo(0);
        assertThat(annotationData.addedInOrBeforeMinorVersion).isEqualTo(0);
        assertThat(annotationData.hasDeprecatedAddedInAnnotation).isFalse();
        assertThat(annotationData.hasAddedInAnnotation).isFalse();
        assertThat(annotationData.addedInPlatformVersion).isEmpty();
        assertThat(annotationData.hasApiRequirementAnnotation).isTrue();
        assertThat(annotationData.minPlatformVersion).isEqualTo("UPSIDE_DOWN_CAKE_0");
        assertThat(annotationData.minCarVersion).isEqualTo("UPSIDE_DOWN_CAKE_0");
        assertThat(annotationData.hasRequiresApiAnnotation).isTrue();
        assertThat(annotationData.requiresApiVersion).isEqualTo("34");

        fieldData = classData.fields.get("FIELD_6");
        assertThat(fieldData.isHidden).isTrue();
        assertThat(fieldData.fieldInitializedValue).isEqualTo("2");
        assertThat(fieldData.fieldName).isEqualTo("FIELD_6");
        assertThat(fieldData.fieldType).isEqualTo("int");
        assertThat(fieldData.isFieldInitialized).isTrue();
        annotationData = fieldData.annotationData;
        assertThat(annotationData.isSystemApi).isTrue();
        assertThat(annotationData.hasAddedInOrBefore).isTrue();
        assertThat(annotationData.addedInOrBeforeMajorVersion).isEqualTo(33);
        assertThat(annotationData.addedInOrBeforeMinorVersion).isEqualTo(3);
        assertThat(annotationData.hasDeprecatedAddedInAnnotation).isTrue();
        assertThat(annotationData.hasAddedInAnnotation).isTrue();
        assertThat(annotationData.addedInPlatformVersion).isEqualTo("TIRAMISU_0");
        assertThat(annotationData.hasApiRequirementAnnotation).isTrue();
        assertThat(annotationData.minPlatformVersion).isEqualTo("UPSIDE_DOWN_CAKE_0");
        assertThat(annotationData.minCarVersion).isEqualTo("UPSIDE_DOWN_CAKE_0");
        assertThat(annotationData.hasRequiresApiAnnotation).isTrue();
        assertThat(annotationData.requiresApiVersion).isEqualTo("UPSIDE_DOWN_CAKE");
    }

    @Test
    public void testMethodData() throws Exception {
        ParsedData data = new ParsedData();
        String packageName = "android.car.user";
        ParsedDataBuilder.populateParsedDataForFile(getResourceFile("Test1.txt"), data);

        // Assert values
        PackageData packageData = data.getPackageData(packageName);
        String className = "android.car.user.Test1";
        ClassData classData = packageData.classes.get(className);
        assertThat(classData.methods.size()).isEqualTo(4);

        MethodData methodData = classData.methods.get("method_1(UserStopRequest request, "
                + "Executor executor, ResultCallback<UserStopResponse> callback)");
        assertThat(methodData.isHidden).isTrue();
        assertThat(methodData.methodName).isEqualTo("method_1");
        assertThat(methodData.fullMethodname).isEqualTo("method_1(UserStopRequest request, "
                + "Executor executor, ResultCallback<UserStopResponse> callback)");
        assertThat(methodData.returnType).isEqualTo("void");
        assertThat(methodData.firstBodyStatement.isExpressionStmt()).isTrue();

        methodData = classData.methods.get("method_2()");
        assertThat(methodData.isHidden).isTrue();
        assertThat(methodData.methodName).isEqualTo("method_2");
        assertThat(methodData.fullMethodname).isEqualTo("method_2()");
        assertThat(methodData.returnType).isEqualTo("void");
        assertThat(methodData.firstBodyStatement.isExpressionStmt()).isTrue();
        MethodCallExpr methodCallExpr = (MethodCallExpr)
                methodData.firstBodyStatement.asExpressionStmt().getExpression();
        assertThat(methodCallExpr.getName().toString()).isEqualTo("w");

        methodData = classData.methods.get("method_3()");
        assertThat(methodData.isHidden).isFalse();
        assertThat(methodData.methodName).isEqualTo("method_3");
        assertThat(methodData.fullMethodname).isEqualTo("method_3()");
        assertThat(methodData.returnType).isEqualTo("int");
        assertThat(methodData.firstBodyStatement.isExpressionStmt()).isTrue();
        methodCallExpr = (MethodCallExpr)
                methodData.firstBodyStatement.asExpressionStmt().getExpression();
        assertThat(methodCallExpr.getName().toString()).isEqualTo("w");
    }
}
