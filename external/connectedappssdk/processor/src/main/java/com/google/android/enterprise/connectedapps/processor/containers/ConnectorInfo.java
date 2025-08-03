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
package com.google.android.enterprise.connectedapps.processor.containers;

import com.google.android.enterprise.connectedapps.annotations.UncaughtExceptionsPolicy;
import com.google.android.enterprise.connectedapps.processor.SupportedTypes;
import com.google.auto.value.AutoValue;
import com.squareup.javapoet.ClassName;
import java.util.Optional;
import java.util.function.Function;
import javax.lang.model.element.TypeElement;

/** Wrapper of the connectors specified for a connected app. */
@AutoValue
public abstract class ConnectorInfo {

  private static final String CROSS_PROFILE_CONNECTOR_QUALIFIED_NAME =
      "com.google.android.enterprise.connectedapps.CrossProfileConnector";
  private static final String CROSS_USER_CONNECTOR_QUALIFIED_NAME =
      "com.google.android.enterprise.connectedapps.CrossUserConnector";
  private static final String PROFILE_CONNECTOR_QUALIFIED_NAME =
      "com.google.android.enterprise.connectedapps.ProfileConnector";
  private static final String USER_CONNECTOR_QUALIFIED_NAME =
      "com.google.android.enterprise.connectedapps.UserConnector";

  public static boolean isProfileConnector(Context context, TypeElement connectorElement) {
    return isConnectorOfType(context, connectorElement, PROFILE_CONNECTOR_QUALIFIED_NAME);
  }

  public static boolean isUserConnector(Context context, TypeElement connectorElement) {
    return isConnectorOfType(context, connectorElement, USER_CONNECTOR_QUALIFIED_NAME);
  }

  private static boolean isConnectorOfType(
      Context context, TypeElement connectorElement, String requiredType) {
    return context
        .types()
        .isAssignable(
            connectorElement.asType(), context.elements().getTypeElement(requiredType).asType());
  }

  public boolean hasCrossProfileConnector() {
    return profileConnector().isPresent();
  }

  public boolean hasCrossUserConnector() {
    return userConnector().isPresent();
  }

  public abstract Optional<ProfileConnectorInfo> profileConnector();

  public abstract Optional<UserConnectorInfo> userConnector();

  public TypeElement connectorElement() {
    return getElement(ProfileConnectorInfo::connectorElement, UserConnectorInfo::connectorElement);
  }

  public ClassName connectorClassName() {
    return getElement(
        ProfileConnectorInfo::connectorClassName, UserConnectorInfo::connectorClassName);
  }

  public ClassName serviceName() {
    return getElement(ProfileConnectorInfo::serviceName, UserConnectorInfo::serviceName);
  }

  public SupportedTypes supportedTypes() {
    return getElement(ProfileConnectorInfo::supportedTypes, UserConnectorInfo::supportedTypes);
  }

  public UncaughtExceptionsPolicy uncaughtExceptionsPolicy() {
    return profileConnector()
        .map(ProfileConnectorInfo::uncaughtExceptionsPolicy)
        .orElse(UncaughtExceptionsPolicy.NOTIFY_RETHROW);
  }

  /**
   * Tries to get an element from {@link #profileConnector()} if present, or from {@link
   * #userConnector()} otherwise.
   *
   * <p>Throws an exception if no connectors are specified, but this should not be possible (now and
   * in the future).
   */
  private <T> T getElement(
      Function<ProfileConnectorInfo, T> getFromProfileConnector,
      Function<UserConnectorInfo, T> getFromUserConnector) {
    return profileConnector()
        .map(getFromProfileConnector)
        .orElseGet(
            () ->
                userConnector()
                    .map(getFromUserConnector)
                    .orElseThrow(
                        () -> new UnsupportedOperationException("No connectors specified")));
  }

  public static ConnectorInfo invalid(
      Context context, TypeElement connector, SupportedTypes globalSupportedTypes) {
    return noSpecificConnector(context, globalSupportedTypes, connector, connector);
  }

  public static ConnectorInfo unspecified(Context context, SupportedTypes globalSupportedTypes) {
    return noSpecificConnector(
        context,
        globalSupportedTypes,
        context.elements().getTypeElement(CROSS_PROFILE_CONNECTOR_QUALIFIED_NAME),
        context.elements().getTypeElement(CROSS_USER_CONNECTOR_QUALIFIED_NAME));
  }

  private static ConnectorInfo noSpecificConnector(
      Context context,
      SupportedTypes globalSupportedTypes,
      TypeElement profileConnector,
      TypeElement userConnector) {
    return new AutoValue_ConnectorInfo(
        Optional.of(ProfileConnectorInfo.create(context, profileConnector, globalSupportedTypes)),
        Optional.of(UserConnectorInfo.create(context, userConnector, globalSupportedTypes)));
  }

  public static ConnectorInfo forProfileConnector(
      Context context, TypeElement connectorElement, SupportedTypes globalSupportedTypes) {
    return new AutoValue_ConnectorInfo(
        Optional.of(ProfileConnectorInfo.create(context, connectorElement, globalSupportedTypes)),
        Optional.empty());
  }

  public static ConnectorInfo forUserConnector(
      Context context, TypeElement connectorElement, SupportedTypes globalSupportedTypes) {
    return new AutoValue_ConnectorInfo(
        Optional.empty(),
        Optional.of(UserConnectorInfo.create(context, connectorElement, globalSupportedTypes)));
  }
}
