/*
 * Copyright (C) 2020 The Android Open Source Project
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


package com.android.i18n.system;


/**
 * Provides hooks for {@link dalvik.system.ZygoteHooks} to call into this class to initialize
 * in the Zygote.
 *
 * @hide
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class ZygoteHooks {

private ZygoteHooks() { throw new RuntimeException("Stub!"); }

/**
 * Called when the Zygote begins preloading classes and data.
 * It caches some ICU objects before the Zygote forks, and tune some behaviors during preload,
 * e.g. enabling strong cache for preloaded classes.
 */

public static void onBeginPreload() { throw new RuntimeException("Stub!"); }

/**
 * Called when the Zygote has completed preloading classes and data.
 */

public static void onEndPreload() { throw new RuntimeException("Stub!"); }
}

