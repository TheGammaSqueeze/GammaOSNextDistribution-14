package com.google.android.enterprise.connectedapps.testapp.types;

import com.google.android.enterprise.connectedapps.annotations.CrossProfileProvider;

@CrossProfileProvider
public class ExceptionsSuppressingTestProvider {

  @CrossProfileProvider
  public ExceptionsSuppressingCrossProfileType provideExceptionsSuppressingCrossProfileType() {
    return new ExceptionsSuppressingCrossProfileType();
  }
}
