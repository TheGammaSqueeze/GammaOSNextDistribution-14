/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.net.apf;

import static android.net.apf.BaseApfGenerator.Rbit.Rbit0;
import static android.net.apf.BaseApfGenerator.Rbit.Rbit1;
import static android.net.apf.BaseApfGenerator.Register.R0;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * The base class for APF assembler/generator.
 *
 * @hide
 */
public abstract class BaseApfGenerator {

    public BaseApfGenerator(int mVersion) {
        this.mVersion = mVersion;
    }

    /**
     * This exception is thrown when an attempt is made to generate an illegal instruction.
     */
    public static class IllegalInstructionException extends Exception {
        IllegalInstructionException(String msg) {
            super(msg);
        }
    }
    enum Opcodes {
        LABEL(-1),
        // Unconditionally pass (if R=0) or drop (if R=1) packet.
        // An optional unsigned immediate value can be provided to encode the counter number.
        // If the value is non-zero, the instruction increments the counter.
        // The counter is located (-4 * counter number) bytes from the end of the data region.
        // It is a U32 big-endian value and is always incremented by 1.
        // This is more or less equivalent to: lddw R0, -N4; add R0,1; stdw R0, -N4; {pass,drop}
        // e.g. "pass", "pass 1", "drop", "drop 1"
        PASSDROP(0),
        LDB(1),    // Load 1 byte from immediate offset, e.g. "ldb R0, [5]"
        LDH(2),    // Load 2 bytes from immediate offset, e.g. "ldh R0, [5]"
        LDW(3),    // Load 4 bytes from immediate offset, e.g. "ldw R0, [5]"
        LDBX(4),   // Load 1 byte from immediate offset plus register, e.g. "ldbx R0, [5]R0"
        LDHX(5),   // Load 2 byte from immediate offset plus register, e.g. "ldhx R0, [5]R0"
        LDWX(6),   // Load 4 byte from immediate offset plus register, e.g. "ldwx R0, [5]R0"
        ADD(7),    // Add, e.g. "add R0,5"
        MUL(8),    // Multiply, e.g. "mul R0,5"
        DIV(9),    // Divide, e.g. "div R0,5"
        AND(10),   // And, e.g. "and R0,5"
        OR(11),    // Or, e.g. "or R0,5"
        SH(12),    // Left shift, e.g. "sh R0, 5" or "sh R0, -5" (shifts right)
        LI(13),    // Load immediate, e.g. "li R0,5" (immediate encoded as signed value)
        // Jump, e.g. "jmp label"
        // In APFv6, we use JMP(R=1) to encode the DATA instruction. DATA is executed as a jump.
        // It tells how many bytes of the program regions are used to store the data and followed
        // by the actual data bytes.
        // "e.g. data 5, abcde"
        JMP(14),
        JEQ(15),   // Compare equal and branch, e.g. "jeq R0,5,label"
        JNE(16),   // Compare not equal and branch, e.g. "jne R0,5,label"
        JGT(17),   // Compare greater than and branch, e.g. "jgt R0,5,label"
        JLT(18),   // Compare less than and branch, e.g. "jlt R0,5,label"
        JSET(19),  // Compare any bits set and branch, e.g. "jset R0,5,label"
        JNEBS(20), // Compare not equal byte sequence, e.g. "jnebs R0,5,label,0x1122334455"
        EXT(21),   // Followed by immediate indicating ExtendedOpcodes.
        LDDW(22),  // Load 4 bytes from data memory address (register + immediate): "lddw R0, [5]R1"
        STDW(23),  // Store 4 bytes to data memory address (register + immediate): "stdw R0, [5]R1"
        // Write 1, 2 or 4 bytes immediate to the output buffer and auto-increment the pointer to
        // write. e.g. "write 5"
        WRITE(24),
        // Copy bytes from input packet/APF program/data region to output buffer and
        // auto-increment the output buffer pointer.
        // Register bit is used to specify the source of data copy.
        // R=0 means copy from packet.
        // R=1 means copy from APF program/data region.
        // The copy length is stored in (u8)imm2.
        // e.g. "pktcopy 5, 5" "datacopy 5, 5"
        PKTDATACOPY(25);

        final int value;

        Opcodes(int value) {
            this.value = value;
        }
    }
    // Extended opcodes. Primary opcode is Opcodes.EXT. ExtendedOpcodes are encoded in the immediate
    // field.
    enum ExtendedOpcodes {
        LDM(0),   // Load from memory, e.g. "ldm R0,5"
        STM(16),  // Store to memory, e.g. "stm R0,5"
        NOT(32),  // Not, e.g. "not R0"
        NEG(33),  // Negate, e.g. "neg R0"
        SWAP(34), // Swap, e.g. "swap R0,R1"
        MOVE(35),  // Move, e.g. "move R0,R1"
        // Allocate writable output buffer.
        // R=0, use register R0 to store the length. R=1, encode the length in the u16 int imm2.
        // "e.g. allocate R0"
        // "e.g. allocate 123"
        ALLOCATE(36),
        // Transmit and deallocate the buffer (transmission can be delayed until the program
        // terminates).  Length of buffer is the output buffer pointer (0 means discard).
        // R=1 iff udp style L4 checksum
        // u8 imm2 - ip header offset from start of buffer (255 for non-ip packets)
        // u8 imm3 - offset from start of buffer to store L4 checksum (255 for no L4 checksum)
        // u8 imm4 - offset from start of buffer to begin L4 checksum calc (present iff imm3 != 255)
        // u16 imm5 - partial checksum value to include in L4 checksum (present iff imm3 != 255)
        // "e.g. transmit"
        TRANSMIT(37),
        // Write 1, 2 or 4 byte value from register to the output buffer and auto-increment the
        // output buffer pointer.
        // e.g. "ewrite1 r0"
        EWRITE1(38),
        EWRITE2(39),
        EWRITE4(40),
        // Copy bytes from input packet/APF program/data region to output buffer and
        // auto-increment the output buffer pointer.
        // Register bit is used to specify the source of data copy.
        // R=0 means copy from packet.
        // R=1 means copy from APF program/data region.
        // The source offset is stored in R0, copy length is stored in u8 imm2 or R1.
        // e.g. "epktcopy r0, 16", "edatacopy r0, 16", "epktcopy r0, r1", "edatacopy r0, r1"
        EPKTDATACOPYIMM(41),
        EPKTDATACOPYR1(42),
        // Jumps if the UDP payload content (starting at R0) does [not] match one
        // of the specified QNAMEs in question records, applying case insensitivity.
        // SAFE version PASSES corrupt packets, while the other one DROPS.
        // R=0/1 meaning 'does not match'/'matches'
        // R0: Offset to UDP payload content
        // imm1: Extended opcode
        // imm2: Jump label offset
        // imm3(u8): Question type (PTR/SRV/TXT/A/AAAA)
        // imm4(bytes): null terminated list of null terminated LV-encoded QNAMEs
        // e.g.: "jdnsqeq R0,label,0xc,\002aa\005local\0\0",
        //       "jdnsqne R0,label,0xc,\002aa\005local\0\0"
        JDNSQMATCH(43),
        JDNSQMATCHSAFE(45),
        // Jumps if the UDP payload content (starting at R0) does [not] match one
        // of the specified NAMEs in answers/authority/additional records, applying
        // case insensitivity.
        // SAFE version PASSES corrupt packets, while the other one DROPS.
        // R=0/1 meaning 'does not match'/'matches'
        // R0: Offset to UDP payload content
        // imm1: Extended opcode
        // imm2: Jump label offset
        // imm3(bytes): null terminated list of null terminated LV-encoded NAMEs
        // e.g.: "jdnsaeq R0,label,0xc,\002aa\005local\0\0",
        //       "jdnsane R0,label,0xc,\002aa\005local\0\0"

        JDNSAMATCH(44),
        JDNSAMATCHSAFE(46);

        final int value;

        ExtendedOpcodes(int value) {
            this.value = value;
        }
    }
    public enum Register {
        R0,
        R1;
    }

    public enum Rbit {
        Rbit0(0),
        Rbit1(1);

        final int value;

        Rbit(int value) {
            this.value = value;
        }
    }

    private enum IntImmediateType {
        INDETERMINATE_SIZE_SIGNED,
        INDETERMINATE_SIZE_UNSIGNED,
        SIGNED_8,
        UNSIGNED_8,
        SIGNED_BE16,
        UNSIGNED_BE16,
        SIGNED_BE32,
        UNSIGNED_BE32;
    }

    private static class IntImmediate {
        public final IntImmediateType mImmediateType;
        public final int mValue;

        IntImmediate(int value, IntImmediateType type) {
            mImmediateType = type;
            mValue = value;
        }

        private int calculateIndeterminateSize() {
            switch (mImmediateType) {
                case INDETERMINATE_SIZE_SIGNED:
                    return calculateImmSize(mValue, true /* signed */);
                case INDETERMINATE_SIZE_UNSIGNED:
                    return calculateImmSize(mValue, false /* signed */);
                default:
                    // For IMM with determinate size, return 0 to allow Math.max() calculation in
                    // caller function.
                    return 0;
            }
        }

        private int getEncodingSize(int immFieldSize) {
            switch (mImmediateType) {
                case SIGNED_8:
                case UNSIGNED_8:
                    return 1;
                case SIGNED_BE16:
                case UNSIGNED_BE16:
                    return 2;
                case SIGNED_BE32:
                case UNSIGNED_BE32:
                    return 4;
                case INDETERMINATE_SIZE_SIGNED:
                case INDETERMINATE_SIZE_UNSIGNED: {
                    int minSizeRequired = calculateIndeterminateSize();
                    if (minSizeRequired > immFieldSize) {
                        throw new IllegalStateException(
                                String.format("immFieldSize: %d is too small to encode value %d",
                                        immFieldSize, mValue));
                    }
                    return immFieldSize;
                }
            }
            throw new IllegalStateException("UnhandledInvalid IntImmediateType: " + mImmediateType);
        }

        private int writeValue(byte[] bytecode, Integer writingOffset, int immFieldSize) {
            return Instruction.writeValue(mValue, bytecode, writingOffset,
                    getEncodingSize(immFieldSize));
        }

        public static IntImmediate newSigned(int imm) {
            return new IntImmediate(imm, IntImmediateType.INDETERMINATE_SIZE_SIGNED);
        }

        public static IntImmediate newUnsigned(long imm) {
            // upperBound is 2^32 - 1
            checkRange("Unsigned IMM", imm, 0 /* lowerBound */,
                    4294967295L /* upperBound */);
            return new IntImmediate((int) imm, IntImmediateType.INDETERMINATE_SIZE_UNSIGNED);
        }

        public static IntImmediate newTwosComplementUnsigned(long imm) {
            checkRange("Unsigned TwosComplement IMM", imm, Integer.MIN_VALUE,
                    4294967295L /* upperBound */);
            return new IntImmediate((int) imm, IntImmediateType.INDETERMINATE_SIZE_UNSIGNED);
        }

        public static IntImmediate newTwosComplementSigned(long imm) {
            checkRange("Signed TwosComplement IMM", imm, Integer.MIN_VALUE,
                    4294967295L /* upperBound */);
            return new IntImmediate((int) imm, IntImmediateType.INDETERMINATE_SIZE_SIGNED);
        }

        public static IntImmediate newS8(byte imm) {
            checkRange("S8 IMM", imm, Byte.MIN_VALUE, Byte.MAX_VALUE);
            return new IntImmediate(imm, IntImmediateType.SIGNED_8);
        }

        public static IntImmediate newU8(int imm) {
            checkRange("U8 IMM", imm, 0, 255);
            return new IntImmediate(imm, IntImmediateType.UNSIGNED_8);
        }

        public static IntImmediate newS16(short imm) {
            return new IntImmediate(imm, IntImmediateType.SIGNED_BE16);
        }

        public static IntImmediate newU16(int imm) {
            checkRange("U16 IMM", imm, 0, 65535);
            return new IntImmediate(imm, IntImmediateType.UNSIGNED_BE16);
        }

        public static IntImmediate newS32(int imm) {
            return new IntImmediate(imm, IntImmediateType.SIGNED_BE32);
        }

        public static IntImmediate newU32(long imm) {
            // upperBound is 2^32 - 1
            checkRange("U32 IMM", imm, 0 /* lowerBound */,
                    4294967295L /* upperBound */);
            return new IntImmediate((int) imm, IntImmediateType.UNSIGNED_BE32);
        }

        @Override
        public String toString() {
            return "IntImmediate{" + "mImmediateType=" + mImmediateType + ", mValue=" + mValue
                    + '}';
        }
    }

    class Instruction {
        private final Opcodes mOpcode;
        private final Rbit mRbit;
        public final List<IntImmediate> mIntImms = new ArrayList<>();
        // When mOpcode is a jump:
        private int mTargetLabelSize;
        private int mLenFieldOverride = -1;
        private String mTargetLabel;
        // When mOpcode == Opcodes.LABEL:
        private String mLabel;
        private byte[] mBytesImm;
        // Offset in bytes from the beginning of this program.
        // Set by {@link BaseApfGenerator#generate}.
        int offset;

        Instruction(Opcodes opcode, Rbit rbit) {
            mOpcode = opcode;
            mRbit = rbit;
        }

        Instruction(Opcodes opcode, Register register) {
            this(opcode, register == R0 ? Rbit0 : Rbit1);
        }

        Instruction(ExtendedOpcodes extendedOpcodes, Rbit rbit) {
            this(Opcodes.EXT, rbit);
            addUnsigned(extendedOpcodes.value);
        }

        Instruction(ExtendedOpcodes extendedOpcodes, Register register) {
            this(Opcodes.EXT, register);
            addUnsigned(extendedOpcodes.value);
        }

        Instruction(ExtendedOpcodes extendedOpcodes, int slot, Register register)
                throws IllegalInstructionException {
            this(Opcodes.EXT, register);
            if (slot < 0 || slot >= MEMORY_SLOTS) {
                throw new IllegalInstructionException("illegal memory slot number: " + slot);
            }
            addUnsigned(extendedOpcodes.value + slot);
        }

        Instruction(Opcodes opcode) {
            this(opcode, R0);
        }

        Instruction(ExtendedOpcodes extendedOpcodes) {
            this(extendedOpcodes, R0);
        }

        Instruction addSigned(int imm) {
            mIntImms.add(IntImmediate.newSigned(imm));
            return this;
        }

        Instruction addUnsigned(long imm) {
            mIntImms.add(IntImmediate.newUnsigned(imm));
            return this;
        }

        // in practice, 'int' always enough for packet offset
        Instruction addPacketOffset(int imm) {
            return addUnsigned(imm);
        }

        // in practice, 'int' always enough for data offset
        Instruction addDataOffset(int imm) {
            return addUnsigned(imm);
        }

        Instruction addTwosCompSigned(long imm) {
            mIntImms.add(IntImmediate.newTwosComplementSigned(imm));
            return this;
        }


        Instruction addTwosCompUnsigned(long imm) {
            mIntImms.add(IntImmediate.newTwosComplementUnsigned(imm));
            return this;
        }

        Instruction addS8(byte imm) {
            mIntImms.add(IntImmediate.newS8(imm));
            return this;
        }

        Instruction addU8(int imm) {
            mIntImms.add(IntImmediate.newU8(imm));
            return this;
        }

        Instruction addS16(short imm) {
            mIntImms.add(IntImmediate.newS16(imm));
            return this;
        }

        Instruction addU16(int imm) {
            mIntImms.add(IntImmediate.newU16(imm));
            return this;
        }

        Instruction addS32(int imm) {
            mIntImms.add(IntImmediate.newS32(imm));
            return this;
        }

        Instruction addU32(long imm) {
            mIntImms.add(IntImmediate.newU32(imm));
            return this;
        }

        Instruction setLabel(String label) throws IllegalInstructionException {
            if (mLabels.containsKey(label)) {
                throw new IllegalInstructionException("duplicate label " + label);
            }
            if (mOpcode != Opcodes.LABEL) {
                throw new IllegalStateException("adding label to non-label instruction");
            }
            mLabel = label;
            mLabels.put(label, this);
            return this;
        }

        Instruction setTargetLabel(String label) {
            mTargetLabel = label;
            mTargetLabelSize = 4; // May shrink later on in generate().
            return this;
        }

        Instruction overrideLenField(int size) {
            mLenFieldOverride = size;
            return this;
        }

        Instruction setBytesImm(byte[] bytes) {
            mBytesImm = bytes;
            return this;
        }

        /**
         * @return size of instruction in bytes.
         */
        int size() {
            if (mOpcode == Opcodes.LABEL) {
                return 0;
            }
            int size = 1;
            int indeterminateSize = calculateRequiredIndeterminateSize();
            for (IntImmediate imm : mIntImms) {
                size += imm.getEncodingSize(indeterminateSize);
            }
            if (mTargetLabel != null) {
                size += indeterminateSize;
            }
            if (mBytesImm != null) {
                size += mBytesImm.length;
            }
            return size;
        }

        /**
         * Resize immediate value field so that it's only as big as required to
         * contain the offset of the jump destination.
         * @return {@code true} if shrunk.
         */
        boolean shrink() throws IllegalInstructionException {
            if (mTargetLabel == null) {
                return false;
            }
            int oldTargetLabelSize = mTargetLabelSize;
            mTargetLabelSize = calculateImmSize(calculateTargetLabelOffset(), false);
            if (mTargetLabelSize > oldTargetLabelSize) {
                throw new IllegalStateException("instruction grew");
            }
            return mTargetLabelSize < oldTargetLabelSize;
        }

        /**
         * Assemble value for instruction size field.
         */
        private int generateImmSizeField() {
            // If we already know the size the length field, just use it
            switch (mLenFieldOverride) {
                case -1:
                    break;
                case 1:
                    return 1;
                case 2:
                    return 2;
                case 4:
                    return 3;
                default:
                    throw new IllegalStateException(
                            "mLenFieldOverride has invalid value: " + mLenFieldOverride);
            }
            // Otherwise, calculate
            int immSize = calculateRequiredIndeterminateSize();
            // Encode size field to fit in 2 bits: 0->0, 1->1, 2->2, 3->4.
            return immSize == 4 ? 3 : immSize;
        }

        /**
         * Assemble first byte of generated instruction.
         */
        private byte generateInstructionByte() {
            int sizeField = generateImmSizeField();
            return (byte) ((mOpcode.value << 3) | (sizeField << 1) | (byte) mRbit.value);
        }

        /**
         * Write {@code value} at offset {@code writingOffset} into {@code bytecode}.
         * {@code immSize} bytes are written. {@code value} is truncated to
         * {@code immSize} bytes. {@code value} is treated simply as a
         * 32-bit value, so unsigned values should be zero extended and the truncation
         * should simply throw away their zero-ed upper bits, and signed values should
         * be sign extended and the truncation should simply throw away their signed
         * upper bits.
         */
        private static int writeValue(int value, byte[] bytecode, int writingOffset, int immSize) {
            for (int i = immSize - 1; i >= 0; i--) {
                bytecode[writingOffset++] = (byte) ((value >> (i * 8)) & 255);
            }
            return writingOffset;
        }

        /**
         * Generate bytecode for this instruction at offset {@link Instruction#offset}.
         */
        void generate(byte[] bytecode) throws IllegalInstructionException {
            if (mOpcode == Opcodes.LABEL) {
                return;
            }
            int writingOffset = offset;
            bytecode[writingOffset++] = generateInstructionByte();
            int indeterminateSize = calculateRequiredIndeterminateSize();
            int startOffset = 0;
            if (mOpcode == Opcodes.EXT) {
                // For extend opcode, always write the actual opcode first.
                writingOffset = mIntImms.get(startOffset++).writeValue(bytecode, writingOffset,
                        indeterminateSize);
            }
            if (mTargetLabel != null) {
                writingOffset = writeValue(calculateTargetLabelOffset(), bytecode, writingOffset,
                        indeterminateSize);
            }
            for (int i = startOffset; i < mIntImms.size(); ++i) {
                writingOffset = mIntImms.get(i).writeValue(bytecode, writingOffset,
                        indeterminateSize);
            }
            if (mBytesImm != null) {
                System.arraycopy(mBytesImm, 0, bytecode, writingOffset, mBytesImm.length);
                writingOffset += mBytesImm.length;
            }
            if ((writingOffset - offset) != size()) {
                throw new IllegalStateException("wrote " + (writingOffset - offset)
                        + " but should have written " + size());
            }
        }

        /**
         * Calculates the maximum indeterminate size of all IMMs in this instruction.
         * <p>
         * This method finds the largest size needed to encode any indeterminate-sized IMMs in
         * the instruction. This size will be stored in the immLen field.
         */
        private int calculateRequiredIndeterminateSize() {
            int maxSize = mTargetLabelSize;
            for (IntImmediate imm : mIntImms) {
                maxSize = Math.max(maxSize, imm.calculateIndeterminateSize());
            }
            return maxSize;
        }

        private int calculateTargetLabelOffset() throws IllegalInstructionException {
            Instruction targetLabelInstruction;
            if (mTargetLabel == DROP_LABEL) {
                targetLabelInstruction = mDropLabel;
            } else if (mTargetLabel == PASS_LABEL) {
                targetLabelInstruction = mPassLabel;
            } else {
                targetLabelInstruction = mLabels.get(mTargetLabel);
            }
            if (targetLabelInstruction == null) {
                throw new IllegalInstructionException("label not found: " + mTargetLabel);
            }
            // Calculate distance from end of this instruction to instruction.offset.
            final int targetLabelOffset = targetLabelInstruction.offset - (offset + size());
            return targetLabelOffset;
        }
    }

    /**
     * Updates instruction offset fields using latest instruction sizes.
     * @return current program length in bytes.
     */
    private int updateInstructionOffsets() {
        int offset = 0;
        for (Instruction instruction : mInstructions) {
            instruction.offset = offset;
            offset += instruction.size();
        }
        return offset;
    }

    /**
     * Calculate the size of the imm.
     */
    private static int calculateImmSize(int imm, boolean signed) {
        if (imm == 0) {
            return 0;
        }
        if (signed && (imm >= -128 && imm <= 127) || !signed && (imm >= 0 && imm <= 255)) {
            return 1;
        }
        if (signed && (imm >= -32768 && imm <= 32767) || !signed && (imm >= 0 && imm <= 65535)) {
            return 2;
        }
        return 4;
    }

    static void checkRange(@NonNull String variableName, long value, long lowerBound,
                           long upperBound) {
        if (value >= lowerBound && value <= upperBound) {
            return;
        }
        throw new IllegalArgumentException(
                String.format("%s: %d, must be in range [%d, %d]", variableName, value, lowerBound,
                        upperBound));
    }

    /**
     * Returns an overestimate of the size of the generated program. {@link #generate} may return
     * a program that is smaller.
     */
    public int programLengthOverEstimate() {
        return updateInstructionOffsets();
    }

    /**
     * Generate the bytecode for the APF program.
     * @return the bytecode.
     * @throws IllegalStateException if a label is referenced but not defined.
     */
    public byte[] generate() throws IllegalInstructionException {
        // Enforce that we can only generate once because we cannot unshrink instructions and
        // PASS/DROP labels may move further away requiring unshrinking if we add further
        // instructions.
        if (mGenerated) {
            throw new IllegalStateException("Can only generate() once!");
        }
        mGenerated = true;
        int total_size;
        boolean shrunk;
        // Shrink the immediate value fields of instructions.
        // As we shrink the instructions some branch offset
        // fields may shrink also, thereby shrinking the
        // instructions further. Loop until we've reached the
        // minimum size. Rarely will this loop more than a few times.
        // Limit iterations to avoid O(n^2) behavior.
        int iterations_remaining = 10;
        do {
            total_size = updateInstructionOffsets();
            // Update drop and pass label offsets.
            mDropLabel.offset = total_size + 1;
            mPassLabel.offset = total_size;
            // Limit run-time in aberant circumstances.
            if (iterations_remaining-- == 0) break;
            // Attempt to shrink instructions.
            shrunk = false;
            for (Instruction instruction : mInstructions) {
                if (instruction.shrink()) {
                    shrunk = true;
                }
            }
        } while (shrunk);
        // Generate bytecode for instructions.
        byte[] bytecode = new byte[total_size];
        for (Instruction instruction : mInstructions) {
            instruction.generate(bytecode);
        }
        return bytecode;
    }

    /**
     * Returns true if the BaseApfGenerator supports the specified {@code version}, otherwise false.
     */
    public static boolean supportsVersion(int version) {
        return version >= MIN_APF_VERSION;
    }

    void requireApfVersion(int minimumVersion) throws IllegalInstructionException {
        if (mVersion < minimumVersion) {
            throw new IllegalInstructionException("Requires APF >= " + minimumVersion);
        }
    }

    /**
     * Jump to this label to terminate the program and indicate the packet
     * should be dropped.
     */
    public static final String DROP_LABEL = "__DROP__";

    /**
     * Jump to this label to terminate the program and indicate the packet
     * should be passed to the AP.
     */
    public static final String PASS_LABEL = "__PASS__";

    /**
     * Number of memory slots available for access via APF stores to memory and loads from memory.
     * The memory slots are numbered 0 to {@code MEMORY_SLOTS} - 1. This must be kept in sync with
     * the APF interpreter.
     */
    public static final int MEMORY_SLOTS = 16;

    /**
     * Memory slot number that is prefilled with the IPv4 header length.
     * Note that this memory slot may be overwritten by a program that
     * executes stores to this memory slot. This must be kept in sync with
     * the APF interpreter.
     */
    public static final int IPV4_HEADER_SIZE_MEMORY_SLOT = 13;

    /**
     * Memory slot number that is prefilled with the size of the packet being filtered in bytes.
     * Note that this memory slot may be overwritten by a program that
     * executes stores to this memory slot. This must be kept in sync with the APF interpreter.
     */
    public static final int PACKET_SIZE_MEMORY_SLOT = 14;

    /**
     * Memory slot number that is prefilled with the age of the filter in seconds. The age of the
     * filter is the time since the filter was installed until now.
     * Note that this memory slot may be overwritten by a program that
     * executes stores to this memory slot. This must be kept in sync with the APF interpreter.
     */
    public static final int FILTER_AGE_MEMORY_SLOT = 15;

    /**
     * First memory slot containing prefilled values. Can be used in range comparisons to determine
     * if memory slot index is within prefilled slots.
     */
    public static final int FIRST_PREFILLED_MEMORY_SLOT = IPV4_HEADER_SIZE_MEMORY_SLOT;

    /**
     * Last memory slot containing prefilled values. Can be used in range comparisons to determine
     * if memory slot index is within prefilled slots.
     */
    public static final int LAST_PREFILLED_MEMORY_SLOT = FILTER_AGE_MEMORY_SLOT;

    // This version number syncs up with APF_VERSION in hardware/google/apf/apf_interpreter.h
    public static final int MIN_APF_VERSION = 2;
    public static final int MIN_APF_VERSION_IN_DEV = 5;
    public static final int APF_VERSION_4 = 4;


    final ArrayList<Instruction> mInstructions = new ArrayList<Instruction>();
    private final HashMap<String, Instruction> mLabels = new HashMap<String, Instruction>();
    private final Instruction mDropLabel = new Instruction(Opcodes.LABEL);
    private final Instruction mPassLabel = new Instruction(Opcodes.LABEL);
    private final int mVersion;
    public boolean mGenerated;
}
