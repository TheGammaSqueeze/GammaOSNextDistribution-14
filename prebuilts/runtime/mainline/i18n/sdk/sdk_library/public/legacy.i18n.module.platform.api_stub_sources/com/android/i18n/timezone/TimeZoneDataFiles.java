/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * Utility methods associated with finding updateable time zone data files. ICU4C and ICU4J also
 * read files affected by time zone updates. That logic is kept in {@link android.icu.platform.AndroidDataFiles AndroidDataFiles}
 * and should be updated if file locations or lookup order changes.
 *
 * @hide
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class TimeZoneDataFiles {

private TimeZoneDataFiles() { throw new RuntimeException("Stub!"); }

public static java.lang.String getDataTimeZoneRootDir() { throw new RuntimeException("Stub!"); }

public static java.lang.String getDataTimeZoneFile(java.lang.String fileName) { throw new RuntimeException("Stub!"); }

public static java.lang.String getTimeZoneModuleTzVersionFile() { throw new RuntimeException("Stub!"); }

/**
 * Reads the version of time zone data supplied by the time zone data module.
 */

public static com.android.i18n.timezone.TzDataSetVersion readTimeZoneModuleVersion() throws java.io.IOException, com.android.i18n.timezone.TzDataSetVersion.TzDataSetException { throw new RuntimeException("Stub!"); }
}

