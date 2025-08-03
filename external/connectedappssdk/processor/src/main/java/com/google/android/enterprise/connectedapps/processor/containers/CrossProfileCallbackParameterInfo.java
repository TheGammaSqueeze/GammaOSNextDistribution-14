package com.google.android.enterprise.connectedapps.processor.containers;

import com.google.android.enterprise.connectedapps.annotations.CrossProfileCallback;
import com.google.auto.value.AutoValue;
import javax.lang.model.element.Name;
import javax.lang.model.element.VariableElement;

/** Wrapper of a {@link CrossProfileCallback} parameter on a method. */
@AutoValue
public abstract class CrossProfileCallbackParameterInfo {

  public abstract CrossProfileCallbackInterfaceInfo crossProfileCallbackInterface();

  public abstract VariableElement variable();

  public Name getSimpleName() {
    return variable().getSimpleName();
  }

  public static CrossProfileCallbackParameterInfo create(
      Context context, VariableElement variableElement) {
    return new AutoValue_CrossProfileCallbackParameterInfo(
        CrossProfileCallbackInterfaceInfo.create(
            context.elements().getTypeElement(variableElement.asType().toString())),
        variableElement);
  }
}
