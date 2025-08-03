/*
 * Copyright (c) 2001, 2020, Oracle and/or its affiliates. All rights reserved.
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


package java.security.spec;

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class PSSParameterSpec implements java.security.spec.AlgorithmParameterSpec {

public PSSParameterSpec(java.lang.String mdName, java.lang.String mgfName, java.security.spec.AlgorithmParameterSpec mgfSpec, int saltLen, int trailerField) { throw new RuntimeException("Stub!"); }

public PSSParameterSpec(int saltLen) { throw new RuntimeException("Stub!"); }

public java.lang.String getDigestAlgorithm() { throw new RuntimeException("Stub!"); }

public java.lang.String getMGFAlgorithm() { throw new RuntimeException("Stub!"); }

public java.security.spec.AlgorithmParameterSpec getMGFParameters() { throw new RuntimeException("Stub!"); }

public int getSaltLength() { throw new RuntimeException("Stub!"); }

public int getTrailerField() { throw new RuntimeException("Stub!"); }
@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public java.lang.String toString() { throw new RuntimeException("Stub!"); }

public static final java.security.spec.PSSParameterSpec DEFAULT;
static { DEFAULT = null; }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
public static final int TRAILER_FIELD_BC = 1; // 0x1
}

