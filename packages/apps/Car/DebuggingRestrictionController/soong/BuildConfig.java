/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.car.debuggingrestrictioncontroller;

public final class BuildConfig {

  /** Toggle if the app should use a CA cert provided below */
  public static final boolean TOKEN_USES_SELF_SIGNED_CA = true;

  /** Contents of the self-signed CA root cert in PEM format */
  public static final String ROOT_CA_CERT = "[please specify]";

  /** Name of the Token Issuer API, e.g. "requestAccessToken" */
  public static final String TOKEN_ISSUER_API_NAME = "[please specify]";

  /** Exptected Hostname/SNI in the token signing certificate */
  public static final String TOKEN_ISSUER_HOST_NAME = "[please specify]";

  /** Test account for instrumented tests */
  public static final String DRC_TEST_EMAIL = "[please specify]";

  /** Test account for instrumented tests */
  public static final String DRC_TEST_PASSWORD = "[please specify]";
}
