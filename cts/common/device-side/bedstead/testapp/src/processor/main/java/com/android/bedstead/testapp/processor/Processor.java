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

package com.android.bedstead.testapp.processor;


import com.android.bedstead.remoteframeworkclasses.processor.MethodSignature;
import com.android.bedstead.testapp.processor.annotations.FrameworkClass;
import com.android.bedstead.testapp.processor.annotations.TestAppReceiver;
import com.android.bedstead.testapp.processor.annotations.TestAppSender;

import com.google.android.enterprise.connectedapps.annotations.CrossProfile;
import com.google.android.enterprise.connectedapps.annotations.CrossProfileConfiguration;
import com.google.android.enterprise.connectedapps.annotations.CrossProfileProvider;
import com.google.android.enterprise.connectedapps.annotations.CrossUser;
import com.google.auto.service.AutoService;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.MirroredTypesException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.JavaFileObject;

/** Processor for generating TestApp API for remote execution. */
@SupportedAnnotationTypes({
        "com.android.bedstead.testapp.processor.annotations.TestAppSender",
        "com.android.bedstead.testapp.processor.annotations.TestAppReceiver",
})
@AutoService(javax.annotation.processing.Processor.class)
public final class Processor extends AbstractProcessor {
    public static final String PACKAGE_NAME = "com.android.bedstead.testapp";
    private static final ClassName USER_REFERENCE_CLASSNAME =
            ClassName.get("com.android.bedstead.nene.users", "UserReference");
    private static final ClassName PACKAGE_CLASSNAME =
            ClassName.get("com.android.bedstead.nene.packages", "Package");
    private static final ClassName RETRY_CLASSNAME =
            ClassName.get("com.android.bedstead.nene.utils", "Retry");
    private static final ClassName CONTEXT_CLASSNAME =
            ClassName.get("android.content", "Context");
    private static final ClassName REMOTE_ACTIVITY_CLASSNAME =
            ClassName.get(
                    "android.app",
                    "RemoteActivity");
    private static final ClassName TEST_APP_ACTIVITY_CLASSNAME =
            ClassName.get(
                    "com.android.bedstead.testapp",
                    "TestAppActivity");
    private static final ClassName TEST_APP_ACTIVITY_IMPL_CLASSNAME =
            ClassName.get(
                    "com.android.bedstead.testapp",
                    "TestAppActivityImpl");
    private static final ClassName PROFILE_TARGETED_REMOTE_ACTIVITY_CLASSNAME =
            ClassName.get(
                    "com.android.bedstead.testapp",
                    "ProfileTargetedRemoteActivity");
    private static final ClassName TARGETED_REMOTE_ACTIVITY_CLASSNAME =
            ClassName.get(
                    "com.android.bedstead.testapp",
                    "TargetedRemoteActivity");
    private static final ClassName TARGETED_REMOTE_ACTIVITY_IMPL_CLASSNAME =
            ClassName.get(
                    "com.android.bedstead.testapp",
                    "TargetedRemoteActivityImpl");
    private static final ClassName TEST_APP_CONTROLLER_CLASSNAME =
            ClassName.get(
                    "com.android.bedstead.testapp",
                    "TestAppController");
    private static final ClassName TARGETED_REMOTE_ACTIVITY_WRAPPER_CLASSNAME =
            ClassName.get(
                    "com.android.bedstead.testapp",
                    "TargetedRemoteActivityWrapper");
    private static final ClassName TEST_APP_CONNECTOR_CLASSNAME =
            ClassName.get("com.android.bedstead.testapp",
                    "TestAppConnector");
    private static final ClassName PROFILE_RUNTIME_EXCEPTION_CLASSNAME =
            ClassName.get(
                    "com.google.android.enterprise.connectedapps.exceptions",
                    "ProfileRuntimeException");
    private static final ClassName PROFILE_CONNECTION_HOLDER_CLASSNAME =
            ClassName.get(
                    "com.google.android.enterprise.connectedapps",
                    "ProfileConnectionHolder");

    private static final ClassName TESTAPP_UTILS_CLASSNAME =
            ClassName.get(
                    "com.android.bedstead.testapp",
                    "Utils");
    private static final ClassName NENE_EXCEPTION_CLASSNAME =
            ClassName.get(
                    "com.android.bedstead.nene.exceptions",
                    "NeneException");
    private static final ClassName TEST_APP_INSTANCE_CLASSNAME =
            ClassName.get("com.android.bedstead.testapp", "TestAppInstance");
    private static final ClassName COMPONENT_REFERENCE_CLASSNAME =
            ClassName.get("com.android.bedstead.nene.packages",
                    "ComponentReference");
    private static final ClassName REMOTE_DEVICE_POLICY_MANAGER_PARENT_CLASSNAME =
            ClassName.get("android.app.admin", "RemoteDevicePolicyManagerParent");
    private static final ClassName DEVICE_POLICY_MANAGER_CLASSNAME =
            ClassName.get("android.app.admin", "DevicePolicyManager");
    private static final ClassName COMPONENT_NAME_CLASSNAME =
            ClassName.get("android.content", "ComponentName");
    private static final ClassName REMOTE_DEVICE_POLICY_MANAGER_PARENT_WRAPPER_CLASSNAME =
            ClassName.get("android.app.admin",
                    "RemoteDevicePolicyManagerParentWrapper");
    private static final ClassName REMOTE_CONTENT_RESOLVER_WRAPPER_CLASSNAME =
            ClassName.get("android.content",
                    "RemoteContentResolverWrapper");
    private static final ClassName REMOTE_BLUETOOTH_ADAPTER_WRAPPER_CLASSNAME =
            ClassName.get("android.bluetooth",
                    "RemoteBluetoothAdapterWrapper");

    private static final ClassName NULL_PARCELABLE_REMOTE_DEVICE_POLICY_MANAGER_CLASSNAME =
            ClassName.get("com.android.bedstead.remoteframeworkclasses",
                    "NullParcelableRemoteDevicePolicyManager");
    private static final ClassName NULL_PARCELABLE_REMOTE_CONTENT_RESOLVER_CLASSNAME =
            ClassName.get("com.android.bedstead.remoteframeworkclasses",
                    "NullParcelableRemoteContentResolver");
    private static final ClassName NULL_PARCELABLE_REMOTE_BLUETOOTH_ADAPTER_CLASSNAME =
            ClassName.get("com.android.bedstead.remoteframeworkclasses",
                    "NullParcelableRemoteBluetoothAdapter");

    // TODO(b/205562849): These only support passing null, which is fine for existing tests but
    //  will be misleading
    private static final ClassName NULL_PARCELABLE_ACTIVITY_CLASSNAME =
            ClassName.get("com.android.bedstead.remoteframeworkclasses",
                    "NullParcelableActivity");
    private static final ClassName NULL_PARCELABLE_ACCOUNT_MANAGER_CALLBACK_CLASSNAME =
            ClassName.get("com.android.bedstead.remoteframeworkclasses",
                    "NullParcelableAccountManagerCallback");
    private static final ClassName NULL_HANDLER_CALLBACK_CLASSNAME =
            ClassName.get("com.android.bedstead.remoteframeworkclasses",
                    "NullParcelableHandler");

    private static final ClassName ACCOUNT_MANAGE_FUTURE_WRAPPER_CLASSNAME =
            ClassName.get(
                    "com.android.bedstead.remoteframeworkclasses", "AccountManagerFutureWrapper");

    private static final String PARENT_PROFILE_INSTANCE =
            "public android.app.admin.DevicePolicyManager getParentProfileInstance(android"
                    + ".content.ComponentName)";
    private static final String GET_CONTENT_RESOLVER =
            "public android.content.ContentResolver getContentResolver()";

    private static final String GET_REMOTE_CONTENT_RESOLVER =
            "public android.content.RemoteContentResolver getContentResolver()";
    private static final String GET_ADAPTER =
            "public android.bluetooth.BluetoothAdapter getAdapter()";
    private static final String GET_DEFAULT_ADAPTER =
            "public static android.bluetooth.BluetoothAdapter getDefaultAdapter()";


    /**
     * Extract classes provided in an annotation.
     *
     * <p>The {@code runnable} should call the annotation method that the classes are being
     * extracted for.
     */
    public static List<TypeElement> extractClassesFromAnnotation(Types types, Runnable runnable) {
        try {
            runnable.run();
        } catch (MirroredTypesException e) {
            return e.getTypeMirrors().stream()
                    .map(t -> (TypeElement) types.asElement(t))
                    .sorted()
                    .collect(Collectors.toList());
        }
        throw new AssertionError("Could not extract classes from annotation");
    }

    /**
     * Extract a class provided in an annotation.
     *
     * <p>The {@code runnable} should call the annotation method that the class is being extracted
     * for.
     */
    public static TypeElement extractClassFromAnnotation(Types types, Runnable runnable) {
        try {
            runnable.run();
        } catch (MirroredTypeException e) {
            return e.getTypeMirrors().stream()
                    .map(t -> (TypeElement) types.asElement(t))
                    .findFirst()
                    .get();
        }
        throw new AssertionError("Could not extract class from annotation");
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations,
            RoundEnvironment roundEnv) {

        TypeElement neneActivityInterface =
                processingEnv.getElementUtils().getTypeElement(
                        REMOTE_ACTIVITY_CLASSNAME.canonicalName());

        Set<? extends Element> receiverAnnotatedElements =
                roundEnv.getElementsAnnotatedWith(TestAppReceiver.class);

        if (receiverAnnotatedElements.size() > 1) {
            throw new IllegalStateException(
                    "Cannot have more than one @TestAppReceiver annotation");
        }

        if (!receiverAnnotatedElements.isEmpty()) {
            TestAppReceiver testAppReceiver = receiverAnnotatedElements.iterator().next()
                    .getAnnotation(TestAppReceiver.class);

            FrameworkClass[] frameworkClasses = testAppReceiver.frameworkClasses();

            generateTargetedRemoteActivityInterface(neneActivityInterface);
            generateTargetedRemoteActivityImpl(neneActivityInterface);
            generateTargetedRemoteActivityWrapper(neneActivityInterface);
            generateProvider(frameworkClasses);
            generateConfiguration();

            generateDpmParentWrapper(processingEnv.getElementUtils());
            for (FrameworkClass frameworkClass : frameworkClasses) {
                generateRemoteFrameworkClassWrapper(
                        extractClassFromAnnotation(processingEnv.getTypeUtils(),
                                frameworkClass::frameworkClass));
            }
        }

        if (!roundEnv.getElementsAnnotatedWith(TestAppSender.class).isEmpty()) {
            generateTestAppActivityImpl(neneActivityInterface);
        }

        return true;
    }

    private void generateRemoteFrameworkClassWrapper(TypeElement systemServiceClass) {
        ClassName originalClassName = ClassName.get(systemServiceClass);
        ClassName interfaceClassName = ClassName.get(
                originalClassName.packageName(),
                "Remote" + originalClassName.simpleName());
        ClassName wrapperClassName = ClassName.get(
                originalClassName.packageName(),
                interfaceClassName.simpleName() + "Wrapper");
        ClassName profileClassName = ClassName.get(
                originalClassName.packageName(),
                "Profile" + interfaceClassName.simpleName());
        TypeElement interfaceElement =
                processingEnv.getElementUtils().getTypeElement(interfaceClassName.canonicalName());

        TypeSpec.Builder classBuilder =
                TypeSpec.classBuilder(
                        wrapperClassName)
                        .addSuperinterface(interfaceClassName)
                        .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        classBuilder.addField(
                FieldSpec.builder(profileClassName,
                        "mProfileClass")
                        .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                        .build());
        classBuilder.addField(
                FieldSpec.builder(TEST_APP_CONNECTOR_CLASSNAME, "mConnector")
                        .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                        .build());
        classBuilder.addField(
                FieldSpec.builder(USER_REFERENCE_CLASSNAME, "mUser")
                        .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                        .build());
        classBuilder.addField(
                FieldSpec.builder(PACKAGE_CLASSNAME, "mPackage")
                        .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                        .build());

        classBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(TEST_APP_CONNECTOR_CLASSNAME, "connector")
                .addParameter(USER_REFERENCE_CLASSNAME, "user")
                .addParameter(PACKAGE_CLASSNAME, "pkg")
                .addStatement("mConnector = connector")
                .addStatement("mUser = user")
                .addStatement("mPackage = pkg")
                .addStatement(
                        "mProfileClass = $T.create(connector)",
                        profileClassName)
                .build());

        for (ExecutableElement method : getMethods(
                interfaceElement, processingEnv.getElementUtils())) {
            MethodSpec.Builder methodBuilder =
                    MethodSpec.methodBuilder(method.getSimpleName().toString())
                            .returns(ClassName.get(method.getReturnType()))
                            .addModifiers(Modifier.PUBLIC)
                            .addAnnotation(Override.class);

            for (TypeMirror m : method.getThrownTypes()) {
                methodBuilder.addException(ClassName.get(m));
            }

            List<String> params = new ArrayList<>();

            for (VariableElement param : method.getParameters()) {

                ParameterSpec parameterSpec =
                        ParameterSpec.builder(ClassName.get(param.asType()),
                                param.getSimpleName().toString()).build();
                methodBuilder.addParameter(parameterSpec);

                if (param.asType().toString().equals("android.content.Context")) {
                    // Context is auto-provided so not passed in
                    continue;
                }

                params.add(param.getSimpleName().toString());
            }


            CodeBlock.Builder logicLambda = CodeBlock.builder()
                    .add("() -> {\n").indent()
                    .beginControlFlow("try ($T p = mConnector.connect())", PROFILE_CONNECTION_HOLDER_CLASSNAME);

            if (method.getReturnType().toString().equals(
                    "android.app.admin.RemoteDevicePolicyManager")
                    && method.getSimpleName().contentEquals("getParentProfileInstance")) {
                // Special case, we want to return a new parent wrapper, but still call through to
                // the other side for exceptions, etc.
                logicLambda.addStatement(
                        "mProfileClass.other().$L($L)",
                        method.getSimpleName(), String.join(", ", params));
                logicLambda.addStatement("return new $T(mConnector, $L, mUser, mPackage)",
                        REMOTE_DEVICE_POLICY_MANAGER_PARENT_WRAPPER_CLASSNAME,
                        String.join(", ", params));
            } else if (method.getReturnType().toString().equals(
                    "android.content.RemoteContentResolver")
                    && method.getSimpleName().contentEquals("getContentResolver")) {
                // Special case, we want to return a content resolver, but still call through to
                // the other side for exceptions, etc.
                logicLambda.addStatement(
                        "mProfileClass.other().$L($L)",
                        method.getSimpleName(), String.join(", ", params));
                logicLambda.addStatement("return new $T(mConnector, mUser, mPackage)",
                        REMOTE_CONTENT_RESOLVER_WRAPPER_CLASSNAME);
            } else if (method.getReturnType().toString().equals(
                    "android.bluetooth.RemoteBluetoothAdapter")
                    && (method.getSimpleName().contentEquals("getAdapter")
                    || method.getSimpleName().contentEquals("getDefaultAdapter"))) {
                // Special case, we want to return a bluetooth adapter, but still call through to
                // the other side for exceptions, etc.
                logicLambda.addStatement(
                        "mProfileClass.other().$L($L)",
                        method.getSimpleName(), String.join(", ", params));
                logicLambda.addStatement("return new $T(mConnector, mUser, mPackage)",
                        REMOTE_BLUETOOTH_ADAPTER_WRAPPER_CLASSNAME);
            } else if (method.getReturnType().getKind().equals(TypeKind.VOID)) {
                logicLambda.addStatement("mProfileClass.other().$L($L)", method.getSimpleName(),
                        String.join(", ", params));
            } else {
                logicLambda.addStatement("return mProfileClass.other().$L($L)",
                        method.getSimpleName(), String.join(", ", params));
            }
            logicLambda.endControlFlow().unindent().add("}");

            String terminalExceptionCode = Stream.concat(
                            Stream.of(CodeBlock.of("e instanceof $T",
                                    PROFILE_RUNTIME_EXCEPTION_CLASSNAME)),
                            method.getThrownTypes().stream().map(
                                    t -> CodeBlock.of("e instanceof $T", t)))
                    .map(CodeBlock::toString).collect(Collectors.joining(" || "));

            CodeBlock runLogic = CodeBlock.of(
                    "$1T.logic($2L).terminalException(e -> $3L).run()",
                    RETRY_CLASSNAME,
                    logicLambda.build().toString(), terminalExceptionCode);

            methodBuilder.beginControlFlow("try");

            if (method.getReturnType().getKind().equals(TypeKind.VOID)) {
                methodBuilder.addStatement(runLogic);
            } else {
                methodBuilder.addStatement("return $L", runLogic);
            }

            methodBuilder.nextControlFlow(
                    "catch ($T e)", PROFILE_RUNTIME_EXCEPTION_CLASSNAME)
                    .addStatement("throw ($T) e.getCause()", RuntimeException.class);

            for (TypeMirror m : method.getThrownTypes()) {
                methodBuilder.nextControlFlow("catch ($T e)", m)
                        .addStatement("throw e");
            }

            methodBuilder
                    .nextControlFlow("catch ($T e)", Throwable.class)
                    .addStatement("throw $T.dealWithConnectedAppsSdkException(mUser, mPackage, e)",
                            TESTAPP_UTILS_CLASSNAME)
                    .endControlFlow();

            classBuilder.addMethod(methodBuilder.build());
        }

        writeClassToFile(originalClassName.packageName(), classBuilder.build());
    }

    private void generateDpmParentWrapper(Elements elements) {
        ClassName interfaceClassName = ClassName.get(
                "android.app.admin", "RemoteDevicePolicyManager");
        ClassName profileClassName = ClassName.get(
                "android.app.admin", "ProfileRemoteDevicePolicyManagerParent");
        TypeElement interfaceElement = elements.getTypeElement(interfaceClassName.canonicalName());

        TypeSpec.Builder classBuilder =
                TypeSpec.classBuilder(
                        REMOTE_DEVICE_POLICY_MANAGER_PARENT_WRAPPER_CLASSNAME)
                        .addSuperinterface(interfaceClassName)
                        .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        classBuilder.addField(
                FieldSpec.builder(profileClassName,
                        "mProfileClass")
                        .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                        .build());
        classBuilder.addField(
                FieldSpec.builder(TEST_APP_CONNECTOR_CLASSNAME, "mConnector")
                        .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                        .build());
        classBuilder.addField(
                FieldSpec.builder(COMPONENT_NAME_CLASSNAME, "mProfileOwnerComponentName")
                        .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                        .build());
        classBuilder.addField(
                FieldSpec.builder(USER_REFERENCE_CLASSNAME, "mUser")
                        .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                        .build());
        classBuilder.addField(
                FieldSpec.builder(PACKAGE_CLASSNAME, "mPackage")
                        .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                        .build());

        classBuilder.addMethod(MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(TEST_APP_CONNECTOR_CLASSNAME, "connector")
                .addParameter(COMPONENT_NAME_CLASSNAME, "profileOwnerComponentName")
                .addParameter(USER_REFERENCE_CLASSNAME, "user")
                .addParameter(PACKAGE_CLASSNAME, "pkg")
                .addStatement("mConnector = connector")
                .addStatement("mUser = user")
                .addStatement("mPackage = pkg")
                .addStatement("mProfileOwnerComponentName = profileOwnerComponentName")
                .addStatement("mProfileClass = $T.create(connector)", profileClassName)
                .build());

        for (ExecutableElement method : getMethods(interfaceElement, elements)) {
            MethodSpec.Builder methodBuilder =
                    MethodSpec.methodBuilder(method.getSimpleName().toString())
                            .returns(ClassName.get(method.getReturnType()))
                            .addModifiers(Modifier.PUBLIC)
                            .addAnnotation(Override.class);

            for (TypeMirror m : method.getThrownTypes()) {
                methodBuilder.addException(ClassName.get(m));
            }

            List<String> params = new ArrayList<>();

            params.add("mProfileOwnerComponentName");

            for (VariableElement param : method.getParameters()) {
                ParameterSpec parameterSpec = ParameterSpec.builder(ClassName.get(param.asType()),
                        param.getSimpleName().toString()).build();

                params.add(param.getSimpleName().toString());

                methodBuilder.addParameter(parameterSpec);
            }

            CodeBlock.Builder logicLambda = CodeBlock.builder()
                    .add("() -> {\n").indent()
                    .beginControlFlow("try ($T p = mConnector.connect())", PROFILE_CONNECTION_HOLDER_CLASSNAME);

            if (method.getReturnType().getKind().equals(TypeKind.VOID)) {
                logicLambda.addStatement("mProfileClass.other().$L($L)", method.getSimpleName(),
                        String.join(", ", params));
            } else {
                logicLambda.addStatement("return mProfileClass.other().$L($L)",
                        method.getSimpleName(), String.join(", ", params));
            }
            logicLambda.endControlFlow().unindent().add("}");

            String terminalExceptionCode = Stream.concat(
                            Stream.of(CodeBlock.of("e instanceof $T",
                                    PROFILE_RUNTIME_EXCEPTION_CLASSNAME)),
                            method.getThrownTypes().stream().map(
                                    t -> CodeBlock.of("e instanceof $T", t)))
                    .map(CodeBlock::toString).collect(Collectors.joining(" || "));

            CodeBlock runLogic = CodeBlock.of(
                    "$1T.logic($2L).terminalException(e -> $3L).run()",
                    RETRY_CLASSNAME,
                    logicLambda.build().toString(), terminalExceptionCode);

            methodBuilder.beginControlFlow("try");

            if (method.getReturnType().getKind().equals(TypeKind.VOID)) {
                methodBuilder.addStatement(runLogic);
            } else {
                methodBuilder.addStatement("return $L", runLogic);
            }

            for (TypeMirror m : method.getThrownTypes()) {
                methodBuilder.nextControlFlow("catch ($T e)", m)
                        .addStatement("throw e");
            }

            methodBuilder.nextControlFlow(
                    "catch ($T e)", PROFILE_RUNTIME_EXCEPTION_CLASSNAME)
                    .addStatement("throw ($T) e.getCause()", RuntimeException.class)
                    .nextControlFlow("catch ($T e)", Throwable.class)
                    .addStatement("throw $T.dealWithConnectedAppsSdkException(mUser, mPackage, e)",
                            TESTAPP_UTILS_CLASSNAME)
                    .endControlFlow();

            classBuilder.addMethod(methodBuilder.build());
        }

        writeClassToFile("android.app.admin", classBuilder.build());
    }

    private void generateTargetedRemoteActivityImpl(TypeElement neneActivityInterface) {
        MethodSignature parentProfileInstanceSignature =
                MethodSignature.forApiString(PARENT_PROFILE_INSTANCE, processingEnv.getTypeUtils(),
                        processingEnv.getElementUtils());
        MethodSignature getContentResolverSignature =
                MethodSignature.forApiString(GET_CONTENT_RESOLVER, processingEnv.getTypeUtils(),
                        processingEnv.getElementUtils());
        MethodSignature getRemoteContentResolverSignature =
                MethodSignature.forApiString(GET_REMOTE_CONTENT_RESOLVER, processingEnv.getTypeUtils(),
                        processingEnv.getElementUtils());
        MethodSignature getAdapterSignature =
                MethodSignature.forApiString(GET_ADAPTER, processingEnv.getTypeUtils(),
                        processingEnv.getElementUtils());
        MethodSignature getDefaultAdapterSignature =
                MethodSignature.forApiString(GET_DEFAULT_ADAPTER, processingEnv.getTypeUtils(),
                        processingEnv.getElementUtils());

        Map<MethodSignature, ClassName> signatureReturnOverrides = new HashMap<>();
        signatureReturnOverrides.put(parentProfileInstanceSignature,
                ClassName.get("android.app.admin", "RemoteDevicePolicyManager"));
        signatureReturnOverrides.put(getContentResolverSignature,
                ClassName.get("android.content", "RemoteContentResolver"));
        signatureReturnOverrides.put(getRemoteContentResolverSignature,
                ClassName.get("android.content", "RemoteContentResolver"));
        signatureReturnOverrides.put(getAdapterSignature,
                ClassName.get("android.bluetooth", "RemoteBluetoothAdapter"));
        signatureReturnOverrides.put(getDefaultAdapterSignature,
                ClassName.get("android.bluetooth", "RemoteBluetoothAdapter"));

        TypeSpec.Builder classBuilder =
                TypeSpec.classBuilder(
                        TARGETED_REMOTE_ACTIVITY_IMPL_CLASSNAME)
                        .addSuperinterface(TARGETED_REMOTE_ACTIVITY_CLASSNAME)
                        .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        classBuilder.addField(FieldSpec.builder(CONTEXT_CLASSNAME, "mContext")
                        .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .build());

        classBuilder.addMethod(MethodSpec.constructorBuilder()
                        .addParameter(CONTEXT_CLASSNAME, "context")
                        .addStatement("mContext = context")
                .build());

        for (ExecutableElement method : getMethods(neneActivityInterface,
                processingEnv.getElementUtils())) {
            MethodSpec.Builder methodBuilder =
                    MethodSpec.methodBuilder(method.getSimpleName().toString())
                            .returns(ClassName.get(method.getReturnType()))
                            .addModifiers(Modifier.PUBLIC)
                            .addAnnotation(Override.class)
                            .addExceptions(
                                    method.getThrownTypes().stream().map(TypeName::get).collect(
                                            Collectors.toSet()));

            methodBuilder.addParameter(
                    ParameterSpec.builder(String.class, "activityClassName").build());

            List<String> paramNames = new ArrayList<>();

            for (VariableElement param : method.getParameters()) {
                ParameterSpec parameterSpec =
                        ParameterSpec.builder(ClassName.get(param.asType()),
                                param.getSimpleName().toString()).build();

                paramNames.add(param.getSimpleName().toString());

                methodBuilder.addParameter(parameterSpec);
            }

            if (method.getReturnType().getKind().equals(TypeKind.VOID)) {
                methodBuilder.addStatement(
                        "BaseTestAppActivity.findActivity(mContext, activityClassName).$L($L)",
                        method.getSimpleName(), String.join(", ", paramNames));
            } else {
                MethodSignature signature = MethodSignature.forMethod(method,
                        processingEnv.getElementUtils());
                if (signatureReturnOverrides.containsKey(signature)) {
                    // TODO: This is a hack and will cause weird bugs in future...
                    methodBuilder.addStatement("return null");
                } else {
                    methodBuilder.addStatement(
                            "return BaseTestAppActivity.findActivity(mContext, activityClassName).$L($L)",
                            method.getSimpleName(), String.join(", ", paramNames));
                }
            }

            classBuilder.addMethod(methodBuilder.build());
        }

        writeClassToFile(PACKAGE_NAME, classBuilder.build());
    }

    private void generateTargetedRemoteActivityWrapper(TypeElement neneActivityInterface) {
        TypeSpec.Builder classBuilder =
                TypeSpec.classBuilder(
                        TARGETED_REMOTE_ACTIVITY_WRAPPER_CLASSNAME)
                        .addSuperinterface(TARGETED_REMOTE_ACTIVITY_CLASSNAME)
                        .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        classBuilder.addField(
                FieldSpec.builder(PROFILE_TARGETED_REMOTE_ACTIVITY_CLASSNAME,
                        "mProfileTargetedRemoteActivity")
                        .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                        .build());
        classBuilder.addField(
                FieldSpec.builder(TEST_APP_CONNECTOR_CLASSNAME, "mConnector")
                        .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                        .build());
        classBuilder.addField(
                FieldSpec.builder(USER_REFERENCE_CLASSNAME, "mUser")
                        .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                        .build());
        classBuilder.addField(
                FieldSpec.builder(PACKAGE_CLASSNAME, "mPackage")
                        .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                        .build());

        classBuilder.addMethod(MethodSpec.constructorBuilder()
                .addParameter(TEST_APP_CONNECTOR_CLASSNAME, "connector")
                .addParameter(USER_REFERENCE_CLASSNAME, "user")
                .addParameter(PACKAGE_CLASSNAME, "pkg")
                .addStatement("mConnector = connector")
                .addStatement("mUser = user")
                .addStatement("mPackage = pkg")
                .addStatement(
                        "mProfileTargetedRemoteActivity = $T.create(connector)",
                        PROFILE_TARGETED_REMOTE_ACTIVITY_CLASSNAME)
                .build());

        for (ExecutableElement method : getMethods(neneActivityInterface,
                processingEnv.getElementUtils())) {
            MethodSpec.Builder methodBuilder =
                    MethodSpec.methodBuilder(method.getSimpleName().toString())
                            .returns(ClassName.get(method.getReturnType()))
                            .addModifiers(Modifier.PUBLIC)
                            .addAnnotation(Override.class);

            for (TypeMirror m : method.getThrownTypes()) {
                methodBuilder.addException(ClassName.get(m));
            }

            methodBuilder.addParameter(
                    ParameterSpec.builder(String.class, "activityClassName").build());

            String params = "activityClassName";

            for (VariableElement param : method.getParameters()) {
                ParameterSpec parameterSpec =
                        ParameterSpec.builder(ClassName.get(param.asType()),
                                param.getSimpleName().toString()).build();

                params += ", " + param.getSimpleName().toString();

                methodBuilder.addParameter(parameterSpec);
            }

            CodeBlock.Builder logicLambda = CodeBlock.builder()
                    .add("() -> {\n").indent()
                    .beginControlFlow("try ($T p = mConnector.connect())", PROFILE_CONNECTION_HOLDER_CLASSNAME);

            if (method.getReturnType().getKind().equals(TypeKind.VOID)) {
                logicLambda.addStatement(
                        "mProfileTargetedRemoteActivity.other().$L($L)", method.getSimpleName(),
                        String.join(", ", params));
            } else {
                logicLambda.addStatement("return mProfileTargetedRemoteActivity.other().$L($L)",
                        method.getSimpleName(), String.join(", ", params));
            }
            logicLambda.endControlFlow().unindent().add("}");

            String terminalExceptionCode = Stream.concat(
                            Stream.of(CodeBlock.of("e instanceof $T",
                                    PROFILE_RUNTIME_EXCEPTION_CLASSNAME)),
                            method.getThrownTypes().stream().map(
                                    t -> CodeBlock.of("e instanceof $T", t)))
                    .map(CodeBlock::toString).collect(Collectors.joining(" || "));

            CodeBlock runLogic = CodeBlock.of(
                    "$1T.logic($2L).terminalException(e -> $3L).run()",
                    RETRY_CLASSNAME,
                    logicLambda.build().toString(), terminalExceptionCode);

            methodBuilder.beginControlFlow("try");

            if (method.getReturnType().getKind().equals(TypeKind.VOID)) {
                methodBuilder.addStatement(runLogic);
            } else {
                methodBuilder.addStatement("return $L", runLogic);
            }

            methodBuilder.nextControlFlow(
                    "catch ($T e)", PROFILE_RUNTIME_EXCEPTION_CLASSNAME)
                    .beginControlFlow("if (e.getCause().getMessage().contains(\"No existing activity named\"))")
                    .addStatement("throw $T.dealWithNoExistingActivityException(activityClassName, e)",
                            TESTAPP_UTILS_CLASSNAME)
                    .endControlFlow()
                    .addStatement("throw ($T) e.getCause()", RuntimeException.class);

            for (TypeMirror m : method.getThrownTypes()) {
                methodBuilder.nextControlFlow("catch ($T e)", m)
                        .addStatement("throw e");
            }

            methodBuilder
                    .nextControlFlow("catch ($T e)", Throwable.class)
                    .addStatement("throw $T.dealWithConnectedAppsSdkException(mUser, mPackage, e)",
                            TESTAPP_UTILS_CLASSNAME)
                    .endControlFlow();

            classBuilder.addMethod(methodBuilder.build());
        }

        writeClassToFile(PACKAGE_NAME, classBuilder.build());
    }

    private void generateTestAppActivityImpl(TypeElement neneActivityInterface) {
        TypeSpec.Builder classBuilder =
                TypeSpec.classBuilder(
                        TEST_APP_ACTIVITY_IMPL_CLASSNAME)
                        .superclass(TEST_APP_ACTIVITY_CLASSNAME)
                        .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        classBuilder.addField(FieldSpec.builder(String.class, "mActivityClassName")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL).build());
        classBuilder.addField(FieldSpec.builder(
                TARGETED_REMOTE_ACTIVITY_CLASSNAME, "mTargetedRemoteActivity")
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL).build());

        classBuilder.addMethod(
                MethodSpec.constructorBuilder()
                        .addParameter(TEST_APP_INSTANCE_CLASSNAME, "instance")
                        .addParameter(
                                COMPONENT_REFERENCE_CLASSNAME, "component")
                        .addStatement("super(instance, component)")
                        .addStatement("mActivityClassName = component.className()")
                        .addStatement("mTargetedRemoteActivity = new $T(mInstance.connector(), mInstance.user(), mInstance.testApp().pkg())",
                                TARGETED_REMOTE_ACTIVITY_WRAPPER_CLASSNAME)
                        .build());


        for (ExecutableElement method : getMethods(neneActivityInterface,
                processingEnv.getElementUtils())) {
            MethodSpec.Builder methodBuilder =
                    MethodSpec.methodBuilder(method.getSimpleName().toString())
                            .returns(ClassName.get(method.getReturnType()))
                            .addModifiers(Modifier.PUBLIC)
                            .addAnnotation(Override.class)
                            .addExceptions(
                                    method.getThrownTypes().stream().map(TypeName::get).collect(
                                            Collectors.toSet()));

            String params = "mActivityClassName";

            for (VariableElement param : method.getParameters()) {
                ParameterSpec parameterSpec =
                        ParameterSpec.builder(ClassName.get(param.asType()),
                                param.getSimpleName().toString()).build();

                params += ", " + param.getSimpleName().toString();

                methodBuilder.addParameter(parameterSpec);
            }

            if (method.getReturnType().getKind().equals(TypeKind.VOID)) {
                methodBuilder.addStatement(
                        "mTargetedRemoteActivity.$L($L)", method.getSimpleName(), params);
            } else {
                methodBuilder.addStatement(
                        "return mTargetedRemoteActivity.$L($L)", method.getSimpleName(), params);
            }

            classBuilder.addMethod(methodBuilder.build());
        }

        writeClassToFile(PACKAGE_NAME, classBuilder.build());
    }

    private void generateTargetedRemoteActivityInterface(TypeElement neneActivityInterface) {
        TypeSpec.Builder classBuilder =
                TypeSpec.interfaceBuilder(
                        TARGETED_REMOTE_ACTIVITY_CLASSNAME)
                        .addModifiers(Modifier.PUBLIC);

        classBuilder.addAnnotation(AnnotationSpec.builder(CrossUser.class)
                .addMember("parcelableWrappers",
                        "{$T.class, $T.class, $T.class, $T.class, $T.class, $T.class}",
                        NULL_PARCELABLE_REMOTE_DEVICE_POLICY_MANAGER_CLASSNAME,
                        NULL_PARCELABLE_REMOTE_CONTENT_RESOLVER_CLASSNAME,
                        NULL_PARCELABLE_REMOTE_BLUETOOTH_ADAPTER_CLASSNAME,
                        NULL_PARCELABLE_ACTIVITY_CLASSNAME,
                        NULL_PARCELABLE_ACCOUNT_MANAGER_CALLBACK_CLASSNAME,
                        NULL_HANDLER_CALLBACK_CLASSNAME)
                .addMember("futureWrappers", "$T.class",
                        ACCOUNT_MANAGE_FUTURE_WRAPPER_CLASSNAME)
                .build());

        for (ExecutableElement method : getMethods(neneActivityInterface,
                processingEnv.getElementUtils())) {
            MethodSpec.Builder methodBuilder =
                    MethodSpec.methodBuilder(method.getSimpleName().toString())
                            .returns(ClassName.get(method.getReturnType()))
                            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                            .addAnnotation(CrossProfile.class)
                            .addExceptions(
                                    method.getThrownTypes().stream().map(TypeName::get).collect(
                                            Collectors.toSet()));

            methodBuilder.addParameter(
                    ParameterSpec.builder(String.class, "activityClassName").build());

            for (VariableElement param : method.getParameters()) {
                ParameterSpec parameterSpec =
                        ParameterSpec.builder(ClassName.get(param.asType()),
                                param.getSimpleName().toString()).build();

                methodBuilder.addParameter(parameterSpec);
            }

            classBuilder.addMethod(methodBuilder.build());
        }

        writeClassToFile(PACKAGE_NAME, classBuilder.build());
    }

    private void generateProvider(FrameworkClass[] frameworkClasses) {
        TypeSpec.Builder classBuilder =
                TypeSpec.classBuilder(
                        "Provider")
                        .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        classBuilder.addMethod(MethodSpec.methodBuilder("provideTargetedRemoteActivity")
                        .addParameter(CONTEXT_CLASSNAME, "context")
                .returns(TARGETED_REMOTE_ACTIVITY_CLASSNAME)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(CrossProfileProvider.class)
                .addCode("return new $T(context);", TARGETED_REMOTE_ACTIVITY_IMPL_CLASSNAME)
                .build());

        classBuilder.addMethod(MethodSpec.methodBuilder("provideTestAppController")
                .returns(TEST_APP_CONTROLLER_CLASSNAME)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(CrossProfileProvider.class)
                .addCode("return new $T();", TEST_APP_CONTROLLER_CLASSNAME)
                .build());

        classBuilder.addMethod(MethodSpec.methodBuilder(
                "provideRemoteDevicePolicyManagerParent")
                .returns(REMOTE_DEVICE_POLICY_MANAGER_PARENT_CLASSNAME)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(CrossProfileProvider.class)
                .addParameter(CONTEXT_CLASSNAME, "context")
                .addCode("return new $T(context.getSystemService($T.class));",
                        REMOTE_DEVICE_POLICY_MANAGER_PARENT_CLASSNAME,
                        DEVICE_POLICY_MANAGER_CLASSNAME)
                .build());

        for (FrameworkClass frameworkClass : frameworkClasses) {
            ClassName originalClassName = ClassName.get(extractClassFromAnnotation(
                    processingEnv.getTypeUtils(), frameworkClass::frameworkClass));
            ClassName interfaceClassName = ClassName.get(
                    originalClassName.packageName(), "Remote" + originalClassName.simpleName());
            ClassName implClassName = ClassName.get(
                    originalClassName.packageName(), interfaceClassName.simpleName() + "Impl");

            classBuilder.addMethod(
                    MethodSpec.methodBuilder("provide" + interfaceClassName.simpleName())
                            .returns(interfaceClassName)
                            .addModifiers(Modifier.PUBLIC)
                            .addAnnotation(CrossProfileProvider.class)
                            .addParameter(CONTEXT_CLASSNAME, "context")
                            .addCode("return new $T($L);",
                                    implClassName, frameworkClass.constructor())
                            .build());
        }

        writeClassToFile(PACKAGE_NAME, classBuilder.build());
    }

    private void generateConfiguration() {
        TypeSpec.Builder classBuilder =
                TypeSpec.classBuilder(
                        "Configuration")
                        .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                        .addAnnotation(AnnotationSpec.builder(CrossProfileConfiguration.class)
                                .addMember("providers", "Provider.class")
                                .addMember("connector", "$T.class", TEST_APP_CONNECTOR_CLASSNAME)
                                .build());

        writeClassToFile(PACKAGE_NAME, classBuilder.build());
    }

    private void writeClassToFile(String packageName, TypeSpec clazz) {
        String qualifiedClassName =
                packageName.isEmpty() ? clazz.name : packageName + "." + clazz.name;

        JavaFile javaFile = JavaFile.builder(packageName, clazz).build();
        try {
            JavaFileObject builderFile =
                    processingEnv.getFiler().createSourceFile(qualifiedClassName);
            try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {
                javaFile.writeTo(out);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Error writing " + qualifiedClassName + " to file", e);
        }
    }

    private List<ExecutableElement> getMethods(TypeElement interfaceClass, Elements elements) {
        Map<String, ExecutableElement> methods = new HashMap<>();
        getMethods(methods, interfaceClass, elements);
        ArrayList<ExecutableElement> methodList = new ArrayList<>(methods.values());
        methodList.sort((e1, e2) -> Integer.compare(e1.getSimpleName().hashCode(), e2.getSimpleName().hashCode()));
        return methodList;
    }

    private void getMethods(Map<String, ExecutableElement> methods, TypeElement interfaceClass,
            Elements elements) {
        interfaceClass.getEnclosedElements().stream()
                .filter(e -> e instanceof ExecutableElement)
                .map(e -> (ExecutableElement) e)
                .filter(e -> !methods.containsKey(e.getSimpleName().toString()))
                .filter(e -> e.getModifiers().contains(Modifier.PUBLIC))
                .forEach(e -> {
                    methods.put(methodHash(e), e);
                });

        interfaceClass.getInterfaces().stream()
                .map(m -> elements.getTypeElement(m.toString()))
                .forEach(m -> getMethods(methods, m, elements));

        TypeElement superclassElement = (TypeElement) processingEnv.getTypeUtils()
                .asElement(interfaceClass.getSuperclass());

        if (superclassElement != null) {
            getMethods(methods, superclassElement, elements);
        }
    }

    private String methodHash(ExecutableElement method) {
        return method.getSimpleName() + "(" + method.getParameters().stream()
                .map(p -> p.asType().toString()).collect(
                Collectors.joining(",")) + ")";
    }
}
