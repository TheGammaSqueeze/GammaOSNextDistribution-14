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
package com.google.android.enterprise.connectedapps.instrumented.utils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Class used when building a service call command to be used on the shell.
 *
 * <p>These commands are likely to break in future android versions so should be replaced as soon as
 * possible.
 */
class ServiceCall {

  enum DataType {
    STRING,
    INT,
    BOOLEAN
  }

  /** This reflects a parameter which is passed in to complete the service call command */
  static class Parameter {

    final String name;

    public Parameter(String name) {
      this.name = name;
    }

    String placeholder() {
      return "{{" + name + "}}";
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof Parameter)) {
        return false;
      }
      Parameter parameter = (Parameter) o;
      return Objects.equals(name, parameter.name);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(name);
    }
  }

  static class PreparedServiceCall {
    private final ServiceCall serviceCall;
    private final Map<Parameter, String> setParameters = new HashMap<>();

    private PreparedServiceCall(ServiceCall serviceCall) {
      this.serviceCall = serviceCall;
    }

    public PreparedServiceCall setString(String key, String value) {
      Parameter keyParameter = new Parameter(key);
      if (!serviceCall.parameters.containsKey(keyParameter)) {
        throw new IllegalStateException("No such key " + key);
      }
      if (serviceCall.parameters.get(keyParameter) != DataType.STRING) {
        throw new IllegalStateException(key + " is not a String");
      }
      setParameters.put(keyParameter, value);
      return this;
    }

    public PreparedServiceCall setInt(String key, int value) {
      Parameter keyParameter = new Parameter(key);
      if (!serviceCall.parameters.containsKey(keyParameter)) {
        throw new IllegalStateException(
            "No such key " + key + " valid keys " + serviceCall.parameters.keySet());
      }
      if (serviceCall.parameters.get(keyParameter) != DataType.INT) {
        throw new IllegalStateException(key + " is not an int");
      }
      setParameters.put(keyParameter, Integer.toString(value));
      return this;
    }

    public PreparedServiceCall setBoolean(String key, boolean value) {
      Parameter keyParameter = new Parameter(key);
      if (!serviceCall.parameters.containsKey(keyParameter)) {
        throw new IllegalStateException("No such key " + key);
      }
      if (serviceCall.parameters.get(keyParameter) != DataType.BOOLEAN) {
        throw new IllegalStateException(key + " is not a boolean");
      }
      setParameters.put(keyParameter, value ? "1" : "0");
      return this;
    }

    public String getCommand() {
      Set<Parameter> parametersToSet = new HashSet<>(serviceCall.parameters.keySet());
      parametersToSet.removeAll(setParameters.keySet());

      if (!parametersToSet.isEmpty()) {
        throw new IllegalStateException("Unset parameters: " + parametersToSet);
      }

      String command = serviceCall.getCommandUnchecked();
      for (Map.Entry<Parameter, String> entry : setParameters.entrySet()) {
        command = command.replace(entry.getKey().placeholder(), entry.getValue());
      }

      return command;
    }
  }

  private Integer user;
  private final String serviceName;
  private final int methodId;
  private final Map<Parameter, DataType> parameters = new HashMap<>();

  private final StringBuilder commandBuilder = new StringBuilder();

  ServiceCall(String serviceName, int methodId) {
    this.serviceName = serviceName;
    this.methodId = methodId;
  }

  ServiceCall addStringParam(String value) {
    commandBuilder.append(" s16 ").append(value);
    return this;
  }

  ServiceCall addStringParam(Parameter value) {
    commandBuilder.append(" s16 ").append(value.placeholder());
    parameters.put(value, DataType.STRING);
    return this;
  }

  ServiceCall addIntParam(int value) {
    commandBuilder.append(" i32 ").append(value);
    return this;
  }

  ServiceCall addIntParam(Parameter value) {
    commandBuilder.append(" i32 ").append(value.placeholder());
    parameters.put(value, DataType.INT);
    return this;
  }

  ServiceCall addBooleanParam(boolean value) {
    return addIntParam(value ? 1 : 0);
  }

  ServiceCall addBooleanParam(Parameter value) {
    commandBuilder.append(" i32 ").append(value.placeholder());
    parameters.put(value, DataType.BOOLEAN);
    return this;
  }

  ServiceCall setUser(int user) {
    this.user = user;
    return this;
  }

  PreparedServiceCall prepare() {
    return new PreparedServiceCall(this);
  }

  String getCommand() {
    if (!parameters.isEmpty()) {
      throw new IllegalStateException("This ServiceCall requires parameters, use #prepare");
    }

    return getCommandUnchecked();
  }

  private String getCommandUnchecked() {
    String cmd = "service call " + serviceName + " " + methodId + commandBuilder;

    if (user != null) {
      cmd = "su " + user + " " + cmd;
    }

    return cmd;
  }
}
