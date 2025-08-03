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
package com.google.android.enterprise.connectedapps.exceptions;

/** An exception representing a problem accessing the other profile. */
// This can happen even if the developer has already checked available as the availability of the
// other profile can change at any time.
public class UnavailableProfileException extends Exception {
  public UnavailableProfileException(String message) {
    super(message);
  }

  public UnavailableProfileException(String message, Throwable cause) {
    super(message, cause);
  }
}
