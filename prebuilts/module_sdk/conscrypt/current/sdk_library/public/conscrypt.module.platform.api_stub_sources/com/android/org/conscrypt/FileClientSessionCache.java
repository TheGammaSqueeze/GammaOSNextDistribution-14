/* GENERATED SOURCE. DO NOT MODIFY. */
/*
 * Copyright (C) 2009 The Android Open Source Project
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


package com.android.org.conscrypt;

import java.io.File;
import java.io.IOException;

/**
 * File-based cache implementation. Only one process should access the
 * underlying directory at a time.
 * @hide This class is not part of the Android public SDK API
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class FileClientSessionCache {

private FileClientSessionCache() { throw new RuntimeException("Stub!"); }

/**
 * Returns a cache backed by the given directory. Creates the directory
 * (including parent directories) if necessary. This cache should have
 * exclusive access to the given directory.
 *
 * @param directory to store files in
 * @return a cache backed by the given directory
 * @throws java.io.IOException if the file exists and is not a directory or if
 *  creating the directories fails
 */

public static synchronized com.android.org.conscrypt.SSLClientSessionCache usingDirectory(java.io.File directory) throws java.io.IOException { throw new RuntimeException("Stub!"); }
}

