/*
 * Copyright (C) 2019 The Android Open Source Project
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


package com.android.i18n.timezone;


/**
 * Information about a telephony network.
 *
 * @hide
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class TelephonyNetwork {

private TelephonyNetwork() { throw new RuntimeException("Stub!"); }

/**
 * Returns the Mobile Country Code of the network.
 */

public java.lang.String getMcc() { throw new RuntimeException("Stub!"); }

/**
 * Returns the Mobile Network Code of the network.
 */

public java.lang.String getMnc() { throw new RuntimeException("Stub!"); }

/**
 * Returns the country in which the network operates as an ISO 3166 alpha-2 (lower case).
 */

public java.lang.String getCountryIsoCode() { throw new RuntimeException("Stub!"); }

public boolean equals(java.lang.Object o) { throw new RuntimeException("Stub!"); }

public int hashCode() { throw new RuntimeException("Stub!"); }

public java.lang.String toString() { throw new RuntimeException("Stub!"); }
}

