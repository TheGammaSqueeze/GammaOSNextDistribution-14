/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates. All rights reserved.
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


package java.security;

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
@SuppressWarnings({"unchecked", "deprecation", "all"})
public class DrbgParameters {

DrbgParameters() { throw new RuntimeException("Stub!"); }

public static java.security.DrbgParameters.Instantiation instantiation(int strength, java.security.DrbgParameters.Capability capability, byte[] personalizationString) { throw new RuntimeException("Stub!"); }

public static java.security.DrbgParameters.NextBytes nextBytes(int strength, boolean predictionResistance, byte[] additionalInput) { throw new RuntimeException("Stub!"); }

public static java.security.DrbgParameters.Reseed reseed(boolean predictionResistance, byte[] additionalInput) { throw new RuntimeException("Stub!"); }

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
@SuppressWarnings({"unchecked", "deprecation", "all"})
public enum Capability {
PR_AND_RESEED,
RESEED_ONLY,
NONE;

public java.lang.String toString() { throw new RuntimeException("Stub!"); }

public boolean supportsReseeding() { throw new RuntimeException("Stub!"); }

public boolean supportsPredictionResistance() { throw new RuntimeException("Stub!"); }
}

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
@SuppressWarnings({"unchecked", "deprecation", "all"})
public static final class Instantiation implements java.security.SecureRandomParameters {

Instantiation() { throw new RuntimeException("Stub!"); }

public int getStrength() { throw new RuntimeException("Stub!"); }

public java.security.DrbgParameters.Capability getCapability() { throw new RuntimeException("Stub!"); }

public byte[] getPersonalizationString() { throw new RuntimeException("Stub!"); }

public java.lang.String toString() { throw new RuntimeException("Stub!"); }
}

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
@SuppressWarnings({"unchecked", "deprecation", "all"})
public static final class NextBytes implements java.security.SecureRandomParameters {

NextBytes() { throw new RuntimeException("Stub!"); }

public int getStrength() { throw new RuntimeException("Stub!"); }

public boolean getPredictionResistance() { throw new RuntimeException("Stub!"); }

public byte[] getAdditionalInput() { throw new RuntimeException("Stub!"); }
}

@android.annotation.FlaggedApi(com.android.libcore.Flags.FLAG_V_APIS)
@SuppressWarnings({"unchecked", "deprecation", "all"})
public static final class Reseed implements java.security.SecureRandomParameters {

Reseed() { throw new RuntimeException("Stub!"); }

public boolean getPredictionResistance() { throw new RuntimeException("Stub!"); }

public byte[] getAdditionalInput() { throw new RuntimeException("Stub!"); }
}

}

