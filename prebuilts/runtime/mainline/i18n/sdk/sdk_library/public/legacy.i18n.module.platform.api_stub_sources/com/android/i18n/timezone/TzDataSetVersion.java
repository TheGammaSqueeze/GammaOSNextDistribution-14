/*
 * Copyright (C) 2016 The Android Open Source Project
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

import java.io.File;

/**
 * Version information associated with the set of time zone data on a device.
 *
 * <p>Time Zone Data Sets have a major ({@link #getFormatMajorVersion()}) and minor
 * ({@link #currentFormatMinorVersion()}) version number:
 * <ul>
 *   <li>Major version numbers are mutually incompatible. e.g. v2 is not compatible with a v1 or a
 *   v3 device.</li>
 *   <li>Minor version numbers are backwards compatible. e.g. a v2.2 data set will work
 *   on a v2.1 device but not a v2.3 device. The minor version is reset to 1 when the major version
 *   is incremented.</li>
 * </ul>
 *
 * <p>Data sets contain time zone rules and other data associated wtih a tzdb release
 * ({@link #getRulesVersion()}) and an additional Android-specific revision number
 * ({@link #getRevision()}).
 *
 * <p>See platform/system/timezone/README.android for more information.
 * @hide
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class TzDataSetVersion {

public TzDataSetVersion(int formatMajorVersion, int formatMinorVersion, java.lang.String rulesVersion, int revision) throws com.android.i18n.timezone.TzDataSetVersion.TzDataSetException { throw new RuntimeException("Stub!"); }

/**
 * Returns the major tz data format version supported by this device.
 */

public static int currentFormatMajorVersion() { throw new RuntimeException("Stub!"); }

/**
 * Returns the minor tz data format version supported by this device.
 */

public static int currentFormatMinorVersion() { throw new RuntimeException("Stub!"); }

public static com.android.i18n.timezone.TzDataSetVersion readFromFile(java.io.File file) throws java.io.IOException, com.android.i18n.timezone.TzDataSetVersion.TzDataSetException { throw new RuntimeException("Stub!"); }

/** Returns the major version number. See {@link com.android.i18n.timezone.TzDataSetVersion TzDataSetVersion}. */

public int getFormatMajorVersion() { throw new RuntimeException("Stub!"); }

/** Returns the minor version number. See {@link com.android.i18n.timezone.TzDataSetVersion TzDataSetVersion}. */

public int getFormatMinorVersion() { throw new RuntimeException("Stub!"); }

/** Returns the tzdb version string. See {@link com.android.i18n.timezone.TzDataSetVersion TzDataSetVersion}. */

public java.lang.String getRulesVersion() { throw new RuntimeException("Stub!"); }

/** Returns the Android revision. See {@link com.android.i18n.timezone.TzDataSetVersion TzDataSetVersion}. */

public int getRevision() { throw new RuntimeException("Stub!"); }

public byte[] toBytes() { throw new RuntimeException("Stub!"); }

public static boolean isCompatibleWithThisDevice(com.android.i18n.timezone.TzDataSetVersion tzDataVersion) { throw new RuntimeException("Stub!"); }

public boolean equals(java.lang.Object o) { throw new RuntimeException("Stub!"); }

public int hashCode() { throw new RuntimeException("Stub!"); }

public java.lang.String toString() { throw new RuntimeException("Stub!"); }

/**
 * The name typically given to the {@link com.android.i18n.timezone.TzDataSetVersion TzDataSetVersion} file. See
 * {@link com.android.i18n.timezone.TzDataSetVersion#readFromFile(java.io.File) TzDataSetVersion#readFromFile(File)}.
 */

public static final java.lang.String DEFAULT_FILE_NAME = "tz_version";
/**
 * A checked exception used in connection with time zone data sets.
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public static class TzDataSetException extends java.lang.Exception {

public TzDataSetException(java.lang.String message) { throw new RuntimeException("Stub!"); }

public TzDataSetException(java.lang.String message, java.lang.Throwable cause) { throw new RuntimeException("Stub!"); }
}

}

