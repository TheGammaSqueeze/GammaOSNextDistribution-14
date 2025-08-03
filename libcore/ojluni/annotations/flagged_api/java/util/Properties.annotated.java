/*
 * Copyright (C) 2014 The Android Open Source Project
 * Copyright (c) 1995, 2020, Oracle and/or its affiliates. All rights reserved.
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
public class Properties extends java.util.Hashtable<java.lang.Object,java.lang.Object> {

public Properties() { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public Properties(int initialCapacity) { throw new RuntimeException("Stub!"); }

public Properties(java.util.Properties defaults) { throw new RuntimeException("Stub!"); }

public synchronized java.lang.Object setProperty(java.lang.String key, java.lang.String value) { throw new RuntimeException("Stub!"); }

public synchronized void load(java.io.Reader reader) throws java.io.IOException { throw new RuntimeException("Stub!"); }

public synchronized void load(java.io.InputStream inStream) throws java.io.IOException { throw new RuntimeException("Stub!"); }

@Deprecated
public void save(java.io.OutputStream out, java.lang.String comments) { throw new RuntimeException("Stub!"); }

public void store(java.io.Writer writer, java.lang.String comments) throws java.io.IOException { throw new RuntimeException("Stub!"); }

public void store(java.io.OutputStream out, java.lang.String comments) throws java.io.IOException { throw new RuntimeException("Stub!"); }

public synchronized void loadFromXML(java.io.InputStream in) throws java.io.IOException, java.util.InvalidPropertiesFormatException { throw new RuntimeException("Stub!"); }

public void storeToXML(java.io.OutputStream os, java.lang.String comment) throws java.io.IOException { throw new RuntimeException("Stub!"); }

public void storeToXML(java.io.OutputStream os, java.lang.String comment, java.lang.String encoding) throws java.io.IOException { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public void storeToXML(java.io.OutputStream os, java.lang.String comment, java.nio.charset.Charset charset) throws java.io.IOException { throw new RuntimeException("Stub!"); }

public java.lang.String getProperty(java.lang.String key) { throw new RuntimeException("Stub!"); }

public java.lang.String getProperty(java.lang.String key, java.lang.String defaultValue) { throw new RuntimeException("Stub!"); }

public java.util.Enumeration<?> propertyNames() { throw new RuntimeException("Stub!"); }

public java.util.Set<java.lang.String> stringPropertyNames() { throw new RuntimeException("Stub!"); }

public void list(java.io.PrintStream out) { throw new RuntimeException("Stub!"); }

public void list(java.io.PrintWriter out) { throw new RuntimeException("Stub!"); }

public int size() { throw new RuntimeException("Stub!"); }

public boolean isEmpty() { throw new RuntimeException("Stub!"); }

public java.util.Enumeration<java.lang.Object> keys() { throw new RuntimeException("Stub!"); }

public java.util.Enumeration<java.lang.Object> elements() { throw new RuntimeException("Stub!"); }

public boolean contains(java.lang.Object value) { throw new RuntimeException("Stub!"); }

public boolean containsValue(java.lang.Object value) { throw new RuntimeException("Stub!"); }

public boolean containsKey(java.lang.Object key) { throw new RuntimeException("Stub!"); }

public java.lang.Object get(java.lang.Object key) { throw new RuntimeException("Stub!"); }

public synchronized java.lang.Object put(java.lang.Object key, java.lang.Object value) { throw new RuntimeException("Stub!"); }

public synchronized java.lang.Object remove(java.lang.Object key) { throw new RuntimeException("Stub!"); }

public synchronized void putAll(java.util.Map<?,?> t) { throw new RuntimeException("Stub!"); }

public synchronized void clear() { throw new RuntimeException("Stub!"); }

public synchronized java.lang.String toString() { throw new RuntimeException("Stub!"); }

public java.util.Set<java.lang.Object> keySet() { throw new RuntimeException("Stub!"); }

public java.util.Collection<java.lang.Object> values() { throw new RuntimeException("Stub!"); }

public java.util.Set<java.util.Map.Entry<java.lang.Object,java.lang.Object>> entrySet() { throw new RuntimeException("Stub!"); }

public synchronized boolean equals(java.lang.Object o) { throw new RuntimeException("Stub!"); }

public synchronized int hashCode() { throw new RuntimeException("Stub!"); }

public java.lang.Object getOrDefault(java.lang.Object key, java.lang.Object defaultValue) { throw new RuntimeException("Stub!"); }

public synchronized void forEach(java.util.function.BiConsumer<? super java.lang.Object,? super java.lang.Object> action) { throw new RuntimeException("Stub!"); }

public synchronized void replaceAll(java.util.function.BiFunction<? super java.lang.Object,? super java.lang.Object,?> function) { throw new RuntimeException("Stub!"); }

public synchronized java.lang.Object putIfAbsent(java.lang.Object key, java.lang.Object value) { throw new RuntimeException("Stub!"); }

public synchronized boolean remove(java.lang.Object key, java.lang.Object value) { throw new RuntimeException("Stub!"); }

public synchronized boolean replace(java.lang.Object key, java.lang.Object oldValue, java.lang.Object newValue) { throw new RuntimeException("Stub!"); }

public synchronized java.lang.Object replace(java.lang.Object key, java.lang.Object value) { throw new RuntimeException("Stub!"); }

public synchronized java.lang.Object computeIfAbsent(java.lang.Object key, java.util.function.Function<? super java.lang.Object,?> mappingFunction) { throw new RuntimeException("Stub!"); }

public synchronized java.lang.Object computeIfPresent(java.lang.Object key, java.util.function.BiFunction<? super java.lang.Object,? super java.lang.Object,?> remappingFunction) { throw new RuntimeException("Stub!"); }

public synchronized java.lang.Object compute(java.lang.Object key, java.util.function.BiFunction<? super java.lang.Object,? super java.lang.Object,?> remappingFunction) { throw new RuntimeException("Stub!"); }

public synchronized java.lang.Object merge(java.lang.Object key, java.lang.Object value, java.util.function.BiFunction<? super java.lang.Object,? super java.lang.Object,?> remappingFunction) { throw new RuntimeException("Stub!"); }

protected void rehash() { throw new RuntimeException("Stub!"); }

public synchronized java.lang.Object clone() { throw new RuntimeException("Stub!"); }

protected volatile java.util.Properties defaults;
}

