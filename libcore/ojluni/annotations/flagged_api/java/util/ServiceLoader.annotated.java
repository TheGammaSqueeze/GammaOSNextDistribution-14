/*
 * Copyright (C) 2014 The Android Open Source Project
 * Copyright (c) 2005, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */


package java.util;

@SuppressWarnings({"unchecked", "deprecation", "all"})
public final class ServiceLoader<S> implements java.lang.Iterable<S> {

ServiceLoader() { throw new RuntimeException("Stub!"); }

public java.util.Iterator<S> iterator() { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public java.util.stream.Stream<java.util.ServiceLoader.Provider<S>> stream() { throw new RuntimeException("Stub!"); }

public static <S> java.util.ServiceLoader<S> load(java.lang.Class<S> service, java.lang.ClassLoader loader) { throw new RuntimeException("Stub!"); }

public static <S> java.util.ServiceLoader<S> load(java.lang.Class<S> service) { throw new RuntimeException("Stub!"); }

public static <S> java.util.ServiceLoader<S> loadInstalled(java.lang.Class<S> service) { throw new RuntimeException("Stub!"); }

public java.util.Optional<S> findFirst() { throw new RuntimeException("Stub!"); }

public void reload() { throw new RuntimeException("Stub!"); }

public java.lang.String toString() { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
@SuppressWarnings({"unchecked", "deprecation", "all"})
public static interface Provider<S> extends java.util.function.Supplier<S> {

public java.lang.Class<? extends S> type();

public S get();
}

}

