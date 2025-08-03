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

import static java.util.stream.Collectors.toList;

import com.google.android.enterprise.connectedapps.annotations.CrossProfileCallback;
import com.google.auto.value.AutoValue;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

/** Wrapper of a {@link CrossProfileCallback} annotated interface. */
@AutoValue
public abstract class CrossProfileCallbackInterfaceInfo {

  public abstract TypeElement interfaceElement();

  public Name simpleName() {
    return interfaceElement().getSimpleName();
  }

  public boolean isSimple() {
    List<ExecutableElement> methods = methods();
    return methods.size() == 1 && methods.get(0).getParameters().size() < 2;
  }

  public List<ExecutableElement> methods() {
    return interfaceElement().getEnclosedElements().stream()
        .filter(e -> e instanceof ExecutableElement)
        .map(e -> (ExecutableElement) e)
        .filter(e -> e.getKind() == ElementKind.METHOD)
        .sorted(Comparator.comparing(e -> e.getSimpleName().toString()))
        .collect(toList());
  }

  public int getIdentifier(ExecutableElement method) {
    return methods().indexOf(method);
  }

  /** Get all types used by methods on this interface. */
  public Set<TypeMirror> argumentTypes() {
    return methods().stream()
        .flatMap(m -> m.getParameters().stream())
        .map(Element::asType)
        .collect(Collectors.toSet());
  }

  public static CrossProfileCallbackInterfaceInfo create(TypeElement interfaceElement) {
    return new AutoValue_CrossProfileCallbackInterfaceInfo(interfaceElement);
  }
}
