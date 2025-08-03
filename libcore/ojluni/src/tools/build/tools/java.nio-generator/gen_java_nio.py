#!/usr/bin/env python
#
# This code is derived from GensrcBuffer.gmk which carries the following copyright.
#
# Copyright (c) 2011, 2014, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#

"""
Prints out the commands for generating the buffer classes in the java.nio package from templates.

Run with "gen_java_nio.py <input directory> <output directory>"

"""

from enum import Enum, auto
import math
import os.path
import argparse
import sys

PRIMITIVE_BYTE = "byte"
PRIMITIVE_CHAR = "char"
PRIMITIVE_SHORT = "short"
PRIMITIVE_INT = "int"
PRIMITIVE_LONG = "long"
PRIMITIVE_FLOAT = "float"
PRIMITIVE_DOUBLE = "double"

PRIMITIVE_TO_BUFFER_PREFIX = {
    PRIMITIVE_BYTE: "Byte",
    PRIMITIVE_CHAR: "Char",
    PRIMITIVE_SHORT: "Short",
    PRIMITIVE_INT: "Int",
    PRIMITIVE_LONG: "Long",
    PRIMITIVE_FLOAT: "Float",
    PRIMITIVE_DOUBLE: "Double",
}

PRIMITIVES = PRIMITIVE_TO_BUFFER_PREFIX.keys()

PRIMITIVE_TO_REFERENCE_TYPE = {
    PRIMITIVE_BYTE: "Byte",
    PRIMITIVE_CHAR: "Character",
    PRIMITIVE_SHORT: "Short",
    PRIMITIVE_INT: "Integer",
    PRIMITIVE_LONG: "Long",
    PRIMITIVE_FLOAT: "Float",
    PRIMITIVE_DOUBLE: "Double",
}

PRIMITIVE_TO_BYTES_PER_VALUE = {
    PRIMITIVE_BYTE: 1,
    PRIMITIVE_CHAR: 2,
    PRIMITIVE_SHORT: 2,
    PRIMITIVE_INT: 4,
    PRIMITIVE_LONG: 8,
    PRIMITIVE_FLOAT: 4,
    PRIMITIVE_DOUBLE: 8,
}

BYTE_ORDER_DEFAULT = ""
BYTE_ORDER_LITTLE_ENDIAN = "L"
BYTE_ORDER_BIG_ENDIAN = "B"
BYTE_ORDER_UNSWAPPED = "U"
BYTE_ORDER_SWAPPED = "S"

SPP = "java build.tools.spp.Spp "

def generate_buffer(template_file, output_file, primitive_type,
                    bin_ops="", read_only=False, byte_order=BYTE_ORDER_DEFAULT, append=" > "):
    # fixRW
    the_rw_key = "ro" if read_only else "rw"

    # typesAndBits
    the_a = "a"
    the_A = "A"
    the_x = primitive_type[0]
    the_Type = primitive_type
    the_type = the_Type.lower()
    the_Fulltype = PRIMITIVE_TO_REFERENCE_TYPE[primitive_type.lower()]
    the_fulltype = the_Fulltype.lower()
    the_category = "integralType"
    the_streams = ""
    the_streamtype = ""
    the_Streamtype = ""
    the_BPV = PRIMITIVE_TO_BYTES_PER_VALUE[primitive_type.lower()]
    the_LBPV = math.log2(the_BPV)
    the_RW = "R" if read_only else ""

    if primitive_type == PRIMITIVE_CHAR:
        the_streams = "streamableType"
        the_streamtype = "int"
        the_Streamtype = "Int"
    elif primitive_type == PRIMITIVE_INT:
        the_a = "an"
        the_A = "An"
    elif primitive_type == PRIMITIVE_FLOAT or primitive_type == PRIMITIVE_DOUBLE:
        the_category = "floatingPointType"

    the_Swaptype = the_Type
    the_memType = the_type
    the_MemType = the_Type
    the_fromBits = ""
    the_toBits = ""
    if primitive_type == PRIMITIVE_FLOAT:
        the_fromBits = "Float.intBitsToFloat"
        the_toBits = "Float.floatToRawIntBits"
    elif primitive_type == PRIMITIVE_DOUBLE:
        the_fromBits = "Double.longBitsToDouble"
        the_toBits = "Double.doubleToRawLongBits"

    the_swap = ""

    command = SPP
    command = command + " -K" + the_type
    command = command + " -K" + the_category
    command = command + " -K" + the_streams
    command = command + " -Dtype=" + the_type
    command = command + " -DType=" + the_Type
    command = command + " -Dfulltype=" + the_fulltype
    command = command + " -DFulltype=" + the_Fulltype
    command = command + " -Dstreamtype=" + the_streamtype
    command = command + " -DStreamtype=" + the_Streamtype
    command = command + " -Dx=" + the_x
    command = command + " -Dmemtype=" + the_memType
    command = command + " -DMemtype=" + the_MemType
    command = command + " -DSwaptype=" + the_Swaptype
    command = command + " -DfromBits=" + the_fromBits
    command = command + " -DtoBits=" + the_toBits
    command = command + " -DLG_BYTES_PER_VALUE=" + str(the_LBPV)
    command = command + " -DBYTES_PER_VALUE=" + str(the_BPV)
    command = command + " -DBO=" + byte_order
    command = command + " -Dswap=" + the_swap
    command = command + " -DRW=" + the_RW
    command = command + " -K" + the_rw_key
    command = command + " -Da=" + the_a
    command = command + " -DA=" + the_A
    if (append == " >> "):
        command = command + " -be"
    if byte_order:
        command = command + " -Kbo" + byte_order
    command = command + " < " + template_file
    command = command + append + output_file
    print(command)

    if bin_ops:
        for primitive in PRIMITIVES:
            if (primitive == PRIMITIVE_BYTE): continue
            buffer_prefix = PRIMITIVE_TO_BUFFER_PREFIX[primitive]
            generate_buffer(bin_ops, output_file, buffer_prefix, "", False, "", " >> ")



def iterate_through_primitives(input_dir, output_dir, template_name, output_name, byte_order):
    for primitive in PRIMITIVES:
        if (primitive == PRIMITIVE_BYTE): continue
        buffer_prefix = PRIMITIVE_TO_BUFFER_PREFIX[primitive]
        template_file = os.path.join(input_dir, template_name)
        output_file = os.path.join(output_dir, output_name + buffer_prefix + "Buffer" + byte_order + ".java")
        output_file_r = os.path.join(output_dir, output_name + buffer_prefix + "BufferR" + byte_order + ".java")
        generate_buffer(template_file, output_file, buffer_prefix, "", False, byte_order)
        print("\n")
        generate_buffer(template_file, output_file_r, buffer_prefix, "", True, byte_order)
        print("\n")

    # Generate X-Buffers
def main():
    parser = argparse.ArgumentParser(
        description='Parse input and output directories')
    parser.add_argument('input', help='Path to the directory that contains the template files')
    parser.add_argument('output', help='Path to the directory where the output files should be placed')
    args = parser.parse_args()

    template_dir = args.input
    output_dir = args.output
    print("\nX-Buffer\n")

    for primitive in PRIMITIVES:
        bin_ops = os.path.join(template_dir, "X-Buffer-bin.java.template") if primitive == PRIMITIVE_BYTE else ""
        buffer_prefix = PRIMITIVE_TO_BUFFER_PREFIX[primitive]
        template_file = os.path.join(template_dir, "X-Buffer.java.template")
        output_file = os.path.join(output_dir, buffer_prefix + "Buffer.java")
        generate_buffer(template_file, output_file, buffer_prefix, bin_ops)
        print("\n")

    print("\nHeap-X-Buffer\n")

    for primitive in PRIMITIVES:
        buffer_prefix = PRIMITIVE_TO_BUFFER_PREFIX[primitive]
        template_file = os.path.join(template_dir, "Heap-X-Buffer.java.template")
        output_file = os.path.join(output_dir, "Heap" + buffer_prefix + "Buffer.java")
        output_file_r = os.path.join(output_dir, "Heap" + buffer_prefix + "BufferR.java")
        generate_buffer(template_file, output_file, buffer_prefix, "")
        print("\n")
        generate_buffer(template_file, output_file_r, buffer_prefix, "", True)
        print("\n")

    print("\nDirect-X-Buffer\n")

    generate_buffer(os.path.join(template_dir, "Direct-X-Buffer.java.template"),
        os.path.join(output_dir, "DirectByteBuffer.java"), "Byte",
        os.path.join(template_dir, "Direct-X-Buffer-bin.java.template"))
    print("\n")
    generate_buffer(os.path.join(template_dir, "Direct-X-Buffer.java.template"),
        os.path.join(output_dir, "DirectByteBufferR.java"), "Byte",
        os.path.join(template_dir, "Direct-X-Buffer-bin.java.template"), True)
    print("\n")

    print("\nDirect-X-Buffer Unswapped\n")

    iterate_through_primitives(template_dir, output_dir, "Direct-X-Buffer.java.template", "Direct", "U")

    print("\nDirect-X-Buffer Swapped\n")

    iterate_through_primitives(template_dir, output_dir, "Direct-X-Buffer.java.template", "Direct", "S")

    print("\nByteBufferAs-X-Buffer BigEndian\n")

    iterate_through_primitives(template_dir, output_dir, "ByteBufferAs-X-Buffer.java.template", "ByteBufferAs", "B")

    print("\nByteBufferAs-X-Buffer LittleEndian\n")

    iterate_through_primitives(template_dir, output_dir, "ByteBufferAs-X-Buffer.java.template", "ByteBufferAs", "L")

    print("\nDONE\n")


main()