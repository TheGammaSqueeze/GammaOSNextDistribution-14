/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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


package java.lang.invoke;

@SuppressWarnings({"unchecked", "deprecation", "all"})
public abstract class VarHandle {

VarHandle() { throw new RuntimeException("Stub!"); }

public final native java.lang.Object get(java.lang.Object... args);

public final native void set(java.lang.Object... args);

public final native java.lang.Object getVolatile(java.lang.Object... args);

public final native void setVolatile(java.lang.Object... args);

public final native java.lang.Object getOpaque(java.lang.Object... args);

public final native void setOpaque(java.lang.Object... args);

public final native java.lang.Object getAcquire(java.lang.Object... args);

public final native void setRelease(java.lang.Object... args);

public final native boolean compareAndSet(java.lang.Object... args);

public final native java.lang.Object compareAndExchange(java.lang.Object... args);

public final native java.lang.Object compareAndExchangeAcquire(java.lang.Object... args);

public final native java.lang.Object compareAndExchangeRelease(java.lang.Object... args);

public final native boolean weakCompareAndSetPlain(java.lang.Object... args);

public final native boolean weakCompareAndSet(java.lang.Object... args);

public final native boolean weakCompareAndSetAcquire(java.lang.Object... args);

public final native boolean weakCompareAndSetRelease(java.lang.Object... args);

public final native java.lang.Object getAndSet(java.lang.Object... args);

public final native java.lang.Object getAndSetAcquire(java.lang.Object... args);

public final native java.lang.Object getAndSetRelease(java.lang.Object... args);

public final native java.lang.Object getAndAdd(java.lang.Object... args);

public final native java.lang.Object getAndAddAcquire(java.lang.Object... args);

public final native java.lang.Object getAndAddRelease(java.lang.Object... args);

public final native java.lang.Object getAndBitwiseOr(java.lang.Object... args);

public final native java.lang.Object getAndBitwiseOrAcquire(java.lang.Object... args);

public final native java.lang.Object getAndBitwiseOrRelease(java.lang.Object... args);

public final native java.lang.Object getAndBitwiseAnd(java.lang.Object... args);

public final native java.lang.Object getAndBitwiseAndAcquire(java.lang.Object... args);

public final native java.lang.Object getAndBitwiseAndRelease(java.lang.Object... args);

public final native java.lang.Object getAndBitwiseXor(java.lang.Object... args);

public final native java.lang.Object getAndBitwiseXorAcquire(java.lang.Object... args);

public final native java.lang.Object getAndBitwiseXorRelease(java.lang.Object... args);

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public final java.lang.String toString() { throw new RuntimeException("Stub!"); }

public final java.lang.Class<?> varType() { throw new RuntimeException("Stub!"); }

public final java.util.List<java.lang.Class<?>> coordinateTypes() { throw new RuntimeException("Stub!"); }

public final java.lang.invoke.MethodType accessModeType(java.lang.invoke.VarHandle.AccessMode accessMode) { throw new RuntimeException("Stub!"); }

public final boolean isAccessModeSupported(java.lang.invoke.VarHandle.AccessMode accessMode) { throw new RuntimeException("Stub!"); }

public final java.lang.invoke.MethodHandle toMethodHandle(java.lang.invoke.VarHandle.AccessMode accessMode) { throw new RuntimeException("Stub!"); }

public static void fullFence() { throw new RuntimeException("Stub!"); }

public static void acquireFence() { throw new RuntimeException("Stub!"); }

public static void releaseFence() { throw new RuntimeException("Stub!"); }

public static void loadLoadFence() { throw new RuntimeException("Stub!"); }

public static void storeStoreFence() { throw new RuntimeException("Stub!"); }
@SuppressWarnings({"unchecked", "deprecation", "all"})
public enum AccessMode {
GET,
SET,
GET_VOLATILE,
SET_VOLATILE,
GET_ACQUIRE,
SET_RELEASE,
GET_OPAQUE,
SET_OPAQUE,
COMPARE_AND_SET,
COMPARE_AND_EXCHANGE,
COMPARE_AND_EXCHANGE_ACQUIRE,
COMPARE_AND_EXCHANGE_RELEASE,
WEAK_COMPARE_AND_SET_PLAIN,
WEAK_COMPARE_AND_SET,
WEAK_COMPARE_AND_SET_ACQUIRE,
WEAK_COMPARE_AND_SET_RELEASE,
GET_AND_SET,
GET_AND_SET_ACQUIRE,
GET_AND_SET_RELEASE,
GET_AND_ADD,
GET_AND_ADD_ACQUIRE,
GET_AND_ADD_RELEASE,
GET_AND_BITWISE_OR,
GET_AND_BITWISE_OR_RELEASE,
GET_AND_BITWISE_OR_ACQUIRE,
GET_AND_BITWISE_AND,
GET_AND_BITWISE_AND_RELEASE,
GET_AND_BITWISE_AND_ACQUIRE,
GET_AND_BITWISE_XOR,
GET_AND_BITWISE_XOR_RELEASE,
GET_AND_BITWISE_XOR_ACQUIRE;

public java.lang.String methodName() { throw new RuntimeException("Stub!"); }

public static java.lang.invoke.VarHandle.AccessMode valueFromMethodName(java.lang.String methodName) { throw new RuntimeException("Stub!"); }
}

}

