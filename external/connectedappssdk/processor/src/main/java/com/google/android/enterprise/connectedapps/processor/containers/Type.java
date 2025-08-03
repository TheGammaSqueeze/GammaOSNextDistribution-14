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

import com.google.android.enterprise.connectedapps.processor.TypeUtils;
import com.google.auto.value.AutoValue;
import java.util.Optional;
import javax.lang.model.type.TypeMirror;

/** A type which may be supported by a given {@code CrossProfileConfiguration}. */
@AutoValue
public abstract class Type {
  public static Builder builder() {
    return new AutoValue_Type.Builder()
        .setAcceptableParameterType(false)
        .setAcceptableReturnType(false)
        .setSupportedWithAnyGenericType(false)
        .setSupportedInsideWrapper(true)
        .setSupportedInsideCrossProfileCallback(true);
  }

  public abstract Builder toBuilder();

  public abstract TypeMirror getTypeMirror();

  public String getQualifiedName() {
    return getTypeMirror().toString();
  }

  public abstract boolean isAcceptableReturnType();

  public abstract boolean isAcceptableParameterType();

  public abstract Optional<String> getAutomaticallyResolvedReplacement();

  public boolean isArray() {
    return TypeUtils.isArray(getTypeMirror());
  }

  public boolean canBeBundled() {
    return getPutIntoBundleCode().isPresent() && getGetFromBundleCode().isPresent();
  }

  public boolean isPrimitive() {
    return getTypeMirror().getKind().isPrimitive();
  }

  public boolean isGeneric() {
    return TypeUtils.isGeneric(getTypeMirror());
  }

  /**
   * If this is set, then type arguments will not validated.
   *
   * <p>This allows for Parcelables which take responsibility for their own generics and do not use
   * Bundler.
   */
  public abstract boolean isSupportedWithAnyGenericType();

  /**
   * Can this type be used inside a wrapper type? For example a List or an array.
   *
   * <p>This allows for async listeners to only be acceptable as parameter types but not type
   * arguments
   */
  public abstract boolean isSupportedInsideWrapper();

  public abstract boolean isSupportedInsideCrossProfileCallback();

  public abstract Optional<FutureWrapper> getFutureWrapper();

  public boolean isFuture() {
    return getFutureWrapper().isPresent();
  }

  public abstract Optional<CrossProfileCallbackInterfaceInfo> getCrossProfileCallbackInterface();

  public boolean isCrossProfileCallbackInterface() {
    return getCrossProfileCallbackInterface().isPresent();
  }

  // If this is a generated Parcelable Wrapper then this will be set to the simple name
  // (e.g. ParcelableList)
  public abstract Optional<ParcelableWrapper> getParcelableWrapper();

  public abstract Optional<String> getPutIntoBundleCode();

  public abstract Optional<String> getGetFromBundleCode();

  public abstract Optional<String> getWriteToParcelCode();

  public abstract Optional<String> getReadFromParcelCode();

  /** A builder for {@link Type}. */
  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder setTypeMirror(TypeMirror typeMirror);

    public abstract Builder setAcceptableReturnType(boolean acceptableReturnType);

    public abstract Builder setAcceptableParameterType(boolean acceptableParameterType);

    public abstract Builder setAutomaticallyResolvedReplacement(
        String automaticallyResolvedReplacement);

    public abstract Builder setSupportedWithAnyGenericType(boolean supportedWithAnyGenericType);

    public abstract Builder setSupportedInsideWrapper(boolean supportedInsideWrapper);

    public abstract Builder setSupportedInsideCrossProfileCallback(
        boolean supportedInsideCrossProfileCallback);

    public abstract Builder setFutureWrapper(FutureWrapper futureWrapper);

    public abstract Builder setCrossProfileCallbackInterface(
        CrossProfileCallbackInterfaceInfo crossProfileCallbackInterface);

    public abstract Builder setPutIntoBundleCode(String putIntoBundleCode);

    public abstract Builder setGetFromBundleCode(String getFromBundleCode);

    public abstract Builder setWriteToParcelCode(String writeToParcelCode);

    public abstract Builder setReadFromParcelCode(String readFromParcelCode);

    public abstract Builder setParcelableWrapper(ParcelableWrapper parcelableWrapper);

    public abstract Type build();
  }
}
