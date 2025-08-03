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

import java.io.IOException;

/**
 * A class that can find time zone-related information about telephony networks by loading data from
 * the telephonylookup.xml file.
 *
 * @hide
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class TelephonyLookup {

private TelephonyLookup() { throw new RuntimeException("Stub!"); }

/**
 * Obtains an instance for use when resolving networks. This method handles using the correct
 * file when there are several to choose from. This method never returns {@code null}. No
 * in-depth validation is performed on the file content, see {@link #validate()}.
 */

public static com.android.i18n.timezone.TelephonyLookup getInstance() { throw new RuntimeException("Stub!"); }

/**
 * Obtains an instance using a specific data file, throwing an IOException if the file does not
 * exist or is not readable. This method never returns {@code null}. No in-depth validation is
 * performed on the file content, see {@link #validate()}.
 */

public static com.android.i18n.timezone.TelephonyLookup createInstance(java.lang.String path) throws java.io.IOException { throw new RuntimeException("Stub!"); }

/**
 * Parses the data file, throws an exception if it is invalid or cannot be read.
 */

public void validate() throws java.io.IOException { throw new RuntimeException("Stub!"); }

/**
 * Loads all the network &lt;-&gt; country mapping data into memory. This method can return
 * {@code null} in the event of an error while reading the underlying data files.
 */

public com.android.i18n.timezone.TelephonyNetworkFinder getTelephonyNetworkFinder() { throw new RuntimeException("Stub!"); }
}

