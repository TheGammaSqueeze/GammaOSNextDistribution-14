package com.google.android.enterprise.connectedapps.testapp.types;

import com.google.android.enterprise.connectedapps.annotations.CrossProfile;
import com.google.android.enterprise.connectedapps.testapp.CustomRuntimeException;
import com.google.android.enterprise.connectedapps.testapp.connector.ExceptionsSuppressingConnector;

@CrossProfile(connector = ExceptionsSuppressingConnector.class)
public class ExceptionsSuppressingCrossProfileType {
  @CrossProfile
  public String methodWhichThrowsRuntimeException() {
    throw new CustomRuntimeException("Exception");
  }
}
