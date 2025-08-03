/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.android.enterprise.connectedapps.processor;

import com.squareup.javapoet.ClassName;

/**
 * {@link ClassName} instances shared across the processor.
 *
 * <p>This is required as most classes are not available to the processor so need to be referenced
 * through {@link ClassName}
 */
public class CommonClassNames {
  static final ClassName CONTEXT_CLASSNAME = ClassName.get("android.content", "Context");
  static final ClassName BUNDLE_CLASSNAME = ClassName.get("android.os", "Bundle");
  static final ClassName PARCEL_CLASSNAME = ClassName.get("android.os", "Parcel");
  static final ClassName PARCELABLE_CLASSNAME = ClassName.get("android.os", "Parcelable");
  static final ClassName USER_HANDLE_CLASSNAME = ClassName.get("android.os", "UserHandle");
  static final ClassName PROCESS_CLASSNAME = ClassName.get("android.os", "Process");
  static final ClassName CROSS_PROFILE_FUTURE_RESULT_WRITER =
      ClassName.get(
          "com.google.android.enterprise.connectedapps.internal", "CrossProfileFutureResultWriter");
  static final ClassName IF_AVAILABLE_FUTURE_RESULT_WRITER =
      ClassName.get(
          "com.google.android.enterprise.connectedapps.internal", "IfAvailableFutureResultWriter");
  static final ClassName PARCELABLE_CREATOR_CLASSNAME =
      ClassName.get("android.os.Parcelable", "Creator");
  static final ClassName UNAVAILABLE_PROFILE_EXCEPTION_CLASSNAME =
      ClassName.get(
          "com.google.android.enterprise.connectedapps.exceptions", "UnavailableProfileException");
  static final ClassName AVAILABILITY_RESTRICTIONS_CLASSNAME =
      ClassName.get(
          "com.google.android.enterprise.connectedapps.annotations", "AvailabilityRestrictions");
  static final ClassName PROFILE_RUNTIME_EXCEPTION_CLASSNAME =
      ClassName.get(
          "com.google.android.enterprise.connectedapps.exceptions", "ProfileRuntimeException");
  static final ClassName PROFILE_AWARE_UTILS_CLASSNAME =
      ClassName.get("com.google.android.enterprise.connectedapps", "ConnectedAppsUtils");
  static final ClassName EXCEPTION_THROWER_CLASSNAME =
      ClassName.get("com.google.android.enterprise.connectedapps.internal", "ExceptionThrower");
  static final ClassName BUNDLE_UTILITIES_CLASSNAME =
      ClassName.get("com.google.android.enterprise.connectedapps.internal", "BundleUtilities");
  static final ClassName METHOD_RUNNER_CLASSNAME =
      ClassName.get("com.google.android.enterprise.connectedapps.internal", "MethodRunner");
  static final ClassName BUNDLER_CLASSNAME =
      ClassName.get("com.google.android.enterprise.connectedapps.internal", "Bundler");
  static final ClassName BUNDLER_TYPE_CLASSNAME =
      ClassName.get("com.google.android.enterprise.connectedapps.internal", "BundlerType");
  static final ClassName BUNDLE_CALL_RECEIVER_CLASSNAME =
      ClassName.get("com.google.android.enterprise.connectedapps.internal", "BundleCallReceiver");
  public static final ClassName BINDER_CLASSNAME = ClassName.get("android.os", "Binder");
  public static final ClassName INTENT_CLASSNAME = ClassName.get("android.content", "Intent");
  static final ClassName CROSS_PROFILE_SENDER_CLASSNAME =
      ClassName.get("com.google.android.enterprise.connectedapps", "CrossProfileSender");
  public static final ClassName CROSSPROFILESERVICE_STUB_CLASSNAME =
      ClassName.get("com.google.android.enterprise.connectedapps.ICrossProfileService", "Stub");
  static final ClassName INVALID_PROTOCOL_BUFFER_EXCEPTION_CLASSNAME =
      ClassName.get("com.google.protobuf", "InvalidProtocolBufferException");
  static final ClassName PROFILE_CLASSNAME =
      ClassName.get("com.google.android.enterprise.connectedapps", "Profile");
  static final ClassName LOCAL_CALLBACK_CLASSNAME =
      ClassName.get("com.google.android.enterprise.connectedapps", "LocalCallback");
  public static final ClassName CROSS_PROFILE_CALLBACK_CLASSNAME =
      ClassName.get("com.google.android.enterprise.connectedapps", "ICrossProfileCallback");
  static final ClassName ASYNC_CALLBACK_PARAM_MULTIMERGER_CLASSNAME =
      ClassName.get(
          "com.google.android.enterprise.connectedapps.internal",
          "CrossProfileCallbackMultiMerger");
  static final ClassName CROSS_PROFILE_CALLBACK_BUNDLE_CALL_SENDER_CLASSNAME =
      ClassName.get(
          "com.google.android.enterprise.connectedapps.internal",
          "CrossProfileCallbackBundleCallSender");
  static final ClassName CROSS_PROFILE_CALLBACK_EXCEPTION_BUNDLE_CALL_SENDER_CLASSNAME =
      ClassName.get(
          "com.google.android.enterprise.connectedapps.internal",
          "CrossProfileCallbackExceptionBundleCallSender");
  static final ClassName ASYNC_CALLBACK_PARAM_MULTIMERGER_COMPLETE_LISTENER_CLASSNAME =
      ClassName.get(
          "com.google.android.enterprise.connectedapps.internal.CrossProfileCallbackMultiMerger",
          "CrossProfileCallbackMultiMergerCompleteListener");

  public static final ClassName SERVICE_CLASSNAME = ClassName.get("android.app", "Service");
  public static final ClassName EXCEPTION_CALLBACK_CLASSNAME =
      ClassName.get("com.google.android.enterprise.connectedapps", "ExceptionCallback");
  public static final ClassName CALLBACK_MERGER_EXCEPTION_CALLBACK_CLASSNAME =
      ClassName.get(
          "com.google.android.enterprise.connectedapps.internal",
          "CallbackMergerExceptionCallback");
  public static final ClassName PROFILE_CONNECTOR_CLASSNAME =
      ClassName.get("com.google.android.enterprise.connectedapps", "ProfileConnector");
  public static final ClassName ABSTRACT_PROFILE_CONNECTOR_CLASSNAME =
      ClassName.get("com.google.android.enterprise.connectedapps", "AbstractProfileConnector");
  public static final ClassName USER_CONNECTOR_CLASSNAME =
      ClassName.get("com.google.android.enterprise.connectedapps", "UserConnector");
  public static final ClassName ABSTRACT_USER_CONNECTOR_CLASSNAME =
      ClassName.get("com.google.android.enterprise.connectedapps", "AbstractUserConnector");
  public static final ClassName USER_CONNECTOR_WRAPPER_CLASSNAME =
      ClassName.get("com.google.android.enterprise.connectedapps", "UserConnectorWrapper");
  public static final ClassName ABSTRACT_PROFILE_CONNECTOR_BUILDER_CLASSNAME =
      ClassName.get(
          "com.google.android.enterprise.connectedapps.AbstractProfileConnector", "Builder");
  public static final ClassName ABSTRACT_USER_CONNECTOR_BUILDER_CLASSNAME =
      ClassName.get("com.google.android.enterprise.connectedapps.AbstractUserConnector", "Builder");
  public static final ClassName CONNECTION_BINDER_CLASSNAME =
      ClassName.get("com.google.android.enterprise.connectedapps", "ConnectionBinder");
  public static final ClassName USER_BINDER_FACTORY_CLASSNAME =
      ClassName.get("com.google.android.enterprise.connectedapps", "UserBinderFactory");
  public static final ClassName SCHEDULED_EXECUTOR_SERVICE_CLASSNAME =
      ClassName.get("java.util.concurrent", "ScheduledExecutorService");
  public static final ClassName FAKE_PROFILE_CONNECTOR_CLASSNAME =
      ClassName.get("com.google.android.enterprise.connectedapps.testing", "FakeProfileConnector");
  public static final ClassName ABSTRACT_FAKE_PROFILE_CONNECTOR_CLASSNAME =
      ClassName.get(
          "com.google.android.enterprise.connectedapps.testing", "AbstractFakeProfileConnector");
  public static final ClassName ABSTRACT_FAKE_USER_CONNECTOR_CLASSNAME =
      ClassName.get(
          "com.google.android.enterprise.connectedapps.testing", "AbstractFakeUserConnector");
  public static final ClassName FAKE_USER_CONNECTOR_WRAPPER_CLASSNAME =
      ClassName.get(
          "com.google.android.enterprise.connectedapps.testing", "FakeUserConnectorWrapper");

  public static final ClassName VERSION_CLASSNAME = ClassName.get("android.os.Build", "VERSION");
  public static final ClassName VERSION_CODES_CLASSNAME =
      ClassName.get("android.os.Build", "VERSION_CODES");

  private CommonClassNames() {}
}
