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

import static com.google.android.enterprise.connectedapps.processor.ClassNameUtilities.prepend;
import static com.google.android.enterprise.connectedapps.processor.ClassNameUtilities.transformClassName;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.ABSTRACT_FAKE_PROFILE_CONNECTOR_CLASSNAME;
import static com.google.android.enterprise.connectedapps.processor.CommonClassNames.CONTEXT_CLASSNAME;
import static com.google.common.base.Preconditions.checkNotNull;

import com.google.android.enterprise.connectedapps.annotations.CustomProfileConnector.ProfileType;
import com.google.android.enterprise.connectedapps.processor.containers.GeneratorContext;
import com.google.android.enterprise.connectedapps.processor.containers.ProfileConnectorInfo;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import javax.lang.model.element.Modifier;

class FakeProfileConnectorGenerator {
  private boolean generated = false;
  private final GeneratorUtilities generatorUtilities;
  private final ProfileConnectorInfo profileConnector;

  public FakeProfileConnectorGenerator(
      GeneratorContext generatorContext, ProfileConnectorInfo connector) {
    this.generatorUtilities = new GeneratorUtilities(checkNotNull(generatorContext));
    this.profileConnector = checkNotNull(connector);
  }

  void generate() {
    if (generated) {
      throw new IllegalStateException(
          "FakeProfileConnectorGenerator#generate can only be called once");
    }
    generated = true;

    generateFakeProfileConnector();
  }

  private void generateFakeProfileConnector() {
    ClassName className = getFakeProfileConnectorClassName(profileConnector);

    TypeSpec.Builder classBuilder =
        TypeSpec.classBuilder(className)
            .addJavadoc(
                "Fake Profile Connector for {@link $1T}.\n\n"
                    + "<p>All functionality is implemented by {@link $2T}, this class is just used"
                    + " for compatibility with the {@link $1T} interface.\n",
                profileConnector.connectorClassName(),
                ABSTRACT_FAKE_PROFILE_CONNECTOR_CLASSNAME)
            .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
            .addSuperinterface(profileConnector.connectorClassName())
            .superclass(ABSTRACT_FAKE_PROFILE_CONNECTOR_CLASSNAME);

    if (profileConnector.primaryProfile().equals(ProfileType.UNKNOWN)) {
      // Special case - we need to provide the profile type to the fake.
      classBuilder.addMethod(
          MethodSpec.constructorBuilder()
              .addParameter(CONTEXT_CLASSNAME, "context")
              .addModifiers(Modifier.PUBLIC)
              .addStatement("super(context, $T.NONE)", ProfileType.class)
              .build());

      classBuilder.addMethod(
          MethodSpec.constructorBuilder()
              .addModifiers(Modifier.PUBLIC)
              .addParameter(CONTEXT_CLASSNAME, "context")
              .addParameter(ProfileType.class, "primaryProfile")
              .addStatement("super(context, primaryProfile)")
              .build());
    } else {
      classBuilder.addMethod(
          MethodSpec.constructorBuilder()
              .addModifiers(Modifier.PUBLIC)
              .addParameter(CONTEXT_CLASSNAME, "context")
              .addStatement(
                  "super(context, $T.$L)",
                  ProfileType.class,
                  profileConnector.primaryProfile().name())
              .build());
    }

    generatorUtilities.writeClassToFile(className.packageName(), classBuilder);
  }

  static ClassName getFakeProfileConnectorClassName(ProfileConnectorInfo profileConnector) {
    return transformClassName(profileConnector.connectorClassName(), prepend("Fake"));
  }
}
