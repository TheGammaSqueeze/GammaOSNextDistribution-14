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

import androidx.annotation.NonNull;

import com.android.net.module.util.HexDump;

/**
 * The abstract class for APFv6 assembler/generator.
 *
 * @param <Type> the generator class
 *
 * @hide
 */
public abstract class ApfV6GeneratorBase<Type extends ApfV6GeneratorBase<Type>> extends
        ApfV4GeneratorBase<Type> {

    /**
     * Creates an ApfV6GeneratorBase instance which is able to emit instructions for the specified
     * {@code version} of the APF interpreter. Throws {@code IllegalInstructionException} if
     * the requested version is unsupported.
     *
     */
    public ApfV6GeneratorBase() throws IllegalInstructionException {
        super(MIN_APF_VERSION_IN_DEV);
    }

    /**
     * Add an instruction to the end of the program to increment the counter value and
     * immediately return PASS.
     */
    public Type addCountAndPass(int cnt) {
        checkRange("CounterNumber", cnt /* value */, 1 /* lowerBound */,
                1000 /* upperBound */);
        // PASS requires using Rbit0 because it shares opcode with DROP
        return append(new Instruction(Opcodes.PASSDROP, Rbit0).addUnsigned(cnt));
    }

    /**
     * Add an instruction to the end of the program to let the program immediately return DROP.
     */
    public Type addDrop() {
        // DROP requires using Rbit1 because it shares opcode with PASS
        return append(new Instruction(Opcodes.PASSDROP, Rbit1));
    }

    /**
     * Add an instruction to the end of the program to increment the counter value and
     * immediately return DROP.
     */
    public Type addCountAndDrop(int cnt) {
        checkRange("CounterNumber", cnt /* value */, 1 /* lowerBound */,
                1000 /* upperBound */);
        // DROP requires using Rbit1 because it shares opcode with PASS
        return append(new Instruction(Opcodes.PASSDROP, Rbit1).addUnsigned(cnt));
    }

    /**
     * Add an instruction to the end of the program to call the apf_allocate_buffer() function.
     * Buffer length to be allocated is stored in register 0.
     */
    public Type addAllocateR0() {
        return append(new Instruction(ExtendedOpcodes.ALLOCATE));
    }

    /**
     * Add an instruction to the end of the program to call the apf_allocate_buffer() function.
     *
     * @param size the buffer length to be allocated.
     */
    public Type addAllocate(int size) {
        // Rbit1 means the extra be16 immediate is present
        return append(new Instruction(ExtendedOpcodes.ALLOCATE, Rbit1).addU16(size));
    }

    /**
     * Add an instruction to the beginning of the program to reserve the data region.
     * @param data the actual data byte
     */
    public Type addData(byte[] data) throws IllegalInstructionException {
        if (!mInstructions.isEmpty()) {
            throw new IllegalInstructionException("data instruction has to come first");
        }
        return append(new Instruction(Opcodes.JMP, Rbit1).addUnsigned(data.length)
                .setBytesImm(data));
    }

    /**
     * Add an instruction to the end of the program to transmit the allocated buffer without
     * checksum.
     */
    public Type addTransmitWithoutChecksum() {
        return addTransmit(-1 /* ipOfs */);
    }

    /**
     * Add an instruction to the end of the program to transmit the allocated buffer.
     */
    public Type addTransmit(int ipOfs) {
        if (ipOfs >= 255) {
            throw new IllegalArgumentException("IP offset of " + ipOfs + " must be < 255");
        }
        if (ipOfs == -1) ipOfs = 255;
        return append(new Instruction(ExtendedOpcodes.TRANSMIT, Rbit0).addU8(ipOfs).addU8(255));
    }

    /**
     * Add an instruction to the end of the program to transmit the allocated buffer.
     */
    public Type addTransmitL4(int ipOfs, int csumOfs, int csumStart, int partialCsum,
                                        boolean isUdp) {
        if (ipOfs >= 255) {
            throw new IllegalArgumentException("IP offset of " + ipOfs + " must be < 255");
        }
        if (ipOfs == -1) ipOfs = 255;
        if (csumOfs >= 255) {
            throw new IllegalArgumentException("L4 checksum requires csum offset of "
                                               + csumOfs + " < 255");
        }
        return append(new Instruction(ExtendedOpcodes.TRANSMIT, isUdp ? Rbit1 : Rbit0)
                .addU8(ipOfs).addU8(csumOfs).addU8(csumStart).addU16(partialCsum));
    }

    /**
     * Add an instruction to the end of the program to write 1 byte value to output buffer.
     */
    public Type addWriteU8(int val) {
        return append(new Instruction(Opcodes.WRITE).overrideLenField(1).addU8(val));
    }

    /**
     * Add an instruction to the end of the program to write 2 bytes value to output buffer.
     */
    public Type addWriteU16(int val) {
        return append(new Instruction(Opcodes.WRITE).overrideLenField(2).addU16(val));
    }

    /**
     * Add an instruction to the end of the program to write 4 bytes value to output buffer.
     */
    public Type addWriteU32(long val) {
        return append(new Instruction(Opcodes.WRITE).overrideLenField(4).addU32(val));
    }

    /**
     * Add an instruction to the end of the program to write 1 byte value from register to output
     * buffer.
     */
    public Type addWriteU8(Register reg) {
        return append(new Instruction(ExtendedOpcodes.EWRITE1, reg));
    }

    /**
     * Add an instruction to the end of the program to write 2 byte value from register to output
     * buffer.
     */
    public Type addWriteU16(Register reg) {
        return append(new Instruction(ExtendedOpcodes.EWRITE2, reg));
    }

    /**
     * Add an instruction to the end of the program to write 4 byte value from register to output
     * buffer.
     */
    public Type addWriteU32(Register reg) {
        return append(new Instruction(ExtendedOpcodes.EWRITE4, reg));
    }

    /**
     * Add an instruction to the end of the program to copy data from APF program/data region to
     * output buffer and auto-increment the output buffer pointer.
     *
     * @param src the offset inside the APF program/data region for where to start copy.
     * @param len the length of bytes needed to be copied, only <= 255 bytes can be copied at
     *               one time.
     * @return the Type object
     */
    public Type addDataCopy(int src, int len) {
        return append(new Instruction(Opcodes.PKTDATACOPY, Rbit1).addDataOffset(src).addU8(len));
    }

    /**
     * Add an instruction to the end of the program to copy data from input packet to output
     * buffer and auto-increment the output buffer pointer.
     *
     * @param src the offset inside the input packet for where to start copy.
     * @param len the length of bytes needed to be copied, only <= 255 bytes can be copied at
     *               one time.
     * @return the Type object
     */
    public Type addPacketCopy(int src, int len) {
        return append(new Instruction(Opcodes.PKTDATACOPY, Rbit0).addPacketOffset(src).addU8(len));
    }

    /**
     * Add an instruction to the end of the program to copy data from APF program/data region to
     * output buffer and auto-increment the output buffer pointer.
     * Source offset is stored in R0.
     *
     * @param len the number of bytes to be copied, only <= 255 bytes can be copied at once.
     * @return the Type object
     */
    public Type addDataCopyFromR0(int len) {
        return append(new Instruction(ExtendedOpcodes.EPKTDATACOPYIMM, Rbit1).addU8(len));
    }

    /**
     * Add an instruction to the end of the program to copy data from input packet to output
     * buffer and auto-increment the output buffer pointer.
     * Source offset is stored in R0.
     *
     * @param len the number of bytes to be copied, only <= 255 bytes can be copied at once.
     * @return the Type object
     */
    public Type addPacketCopyFromR0(int len) {
        return append(new Instruction(ExtendedOpcodes.EPKTDATACOPYIMM, Rbit0).addU8(len));
    }

    /**
     * Add an instruction to the end of the program to copy data from APF program/data region to
     * output buffer and auto-increment the output buffer pointer.
     * Source offset is stored in R0.
     * Copy length is stored in R1.
     *
     * @return the Type object
     */
    public Type addDataCopyFromR0LenR1() {
        return append(new Instruction(ExtendedOpcodes.EPKTDATACOPYR1, Rbit1));
    }

    /**
     * Add an instruction to the end of the program to copy data from input packet to output
     * buffer and auto-increment the output buffer pointer.
     * Source offset is stored in R0.
     * Copy length is stored in R1.
     *
     * @return the Type object
     */
    public Type addPacketCopyFromR0LenR1() {
        return append(new Instruction(ExtendedOpcodes.EPKTDATACOPYR1, Rbit0));
    }

    /**
     * Appends a conditional jump instruction to the program: Jumps to {@code tgt} if the UDP
     * payload's DNS questions do NOT contain the QNAMEs specified in {@code qnames} and qtype
     * equals {@code qtype}. Examines the payload starting at the offset in R0.
     * R = 0 means check for "does not contain".
     * Drops packets if packets are corrupted.
     */
    public Type addJumpIfPktAtR0DoesNotContainDnsQ(@NonNull byte[] qnames, int qtype,
                                                             @NonNull String tgt) {
        validateNames(qnames);
        return append(new Instruction(ExtendedOpcodes.JDNSQMATCH, Rbit0).setTargetLabel(tgt).addU8(
                qtype).setBytesImm(qnames));
    }

    /**
     * Same as {@link #addJumpIfPktAtR0DoesNotContainDnsQ} except passes packets if packets are
     * corrupted.
     */
    public Type addJumpIfPktAtR0DoesNotContainDnsQSafe(@NonNull byte[] qnames, int qtype,
            @NonNull String tgt) {
        validateNames(qnames);
        return append(new Instruction(ExtendedOpcodes.JDNSQMATCHSAFE, Rbit0).setTargetLabel(
                tgt).addU8(qtype).setBytesImm(qnames));
    }

    /**
     * Appends a conditional jump instruction to the program: Jumps to {@code tgt} if the UDP
     * payload's DNS questions contain the QNAMEs specified in {@code qnames} and qtype
     * equals {@code qtype}. Examines the payload starting at the offset in R0.
     * R = 1 means check for "contain".
     * Drops packets if packets are corrupted.
     */
    public Type addJumpIfPktAtR0ContainDnsQ(@NonNull byte[] qnames, int qtype,
                                                      @NonNull String tgt) {
        validateNames(qnames);
        return append(new Instruction(ExtendedOpcodes.JDNSQMATCH, Rbit1).setTargetLabel(tgt).addU8(
                qtype).setBytesImm(qnames));
    }

    /**
     * Same as {@link #addJumpIfPktAtR0ContainDnsQ} except passes packets if packets are
     * corrupted.
     */
    public Type addJumpIfPktAtR0ContainDnsQSafe(@NonNull byte[] qnames, int qtype,
            @NonNull String tgt) {
        validateNames(qnames);
        return append(new Instruction(ExtendedOpcodes.JDNSQMATCHSAFE, Rbit1).setTargetLabel(
                tgt).addU8(qtype).setBytesImm(qnames));
    }

    /**
     * Appends a conditional jump instruction to the program: Jumps to {@code tgt} if the UDP
     * payload's DNS answers/authority/additional records do NOT contain the NAMEs
     * specified in {@code Names}. Examines the payload starting at the offset in R0.
     * R = 0 means check for "does not contain".
     * Drops packets if packets are corrupted.
     */
    public Type addJumpIfPktAtR0DoesNotContainDnsA(@NonNull byte[] names,
                                                             @NonNull String tgt) {
        validateNames(names);
        return append(new Instruction(ExtendedOpcodes.JDNSAMATCH, Rbit0).setTargetLabel(tgt)
                        .setBytesImm(names));
    }

    /**
     * Same as {@link #addJumpIfPktAtR0DoesNotContainDnsA} except passes packets if packets are
     * corrupted.
     */
    public Type addJumpIfPktAtR0DoesNotContainDnsASafe(@NonNull byte[] names,
            @NonNull String tgt) {
        validateNames(names);
        return append(new Instruction(ExtendedOpcodes.JDNSAMATCHSAFE, Rbit0).setTargetLabel(tgt)
                .setBytesImm(names));
    }

    /**
     * Appends a conditional jump instruction to the program: Jumps to {@code tgt} if the UDP
     * payload's DNS answers/authority/additional records contain the NAMEs
     * specified in {@code Names}. Examines the payload starting at the offset in R0.
     * R = 1 means check for "contain".
     * Drops packets if packets are corrupted.
     */
    public Type addJumpIfPktAtR0ContainDnsA(@NonNull byte[] names,
                                                      @NonNull String tgt) {
        validateNames(names);
        return append(new Instruction(ExtendedOpcodes.JDNSAMATCH, Rbit1).setTargetLabel(
                tgt).setBytesImm(names));
    }

    /**
     * Same as {@link #addJumpIfPktAtR0ContainDnsA} except passes packets if packets are
     * corrupted.
     */
    public Type addJumpIfPktAtR0ContainDnsASafe(@NonNull byte[] names,
            @NonNull String tgt) {
        validateNames(names);
        return append(new Instruction(ExtendedOpcodes.JDNSAMATCHSAFE, Rbit1).setTargetLabel(
                tgt).setBytesImm(names));
    }

    /**
     * Check if the byte is valid dns character: A-Z,0-9,-,_
     */
    private static boolean isValidDnsCharacter(byte c) {
        return (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '%';
    }

    private static void validateNames(@NonNull byte[] names) {
        final int len = names.length;
        if (len < 4) {
            throw new IllegalArgumentException("qnames must have at least length 4");
        }
        final String errorMessage = "qname: " + HexDump.toHexString(names)
                + "is not null-terminated list of TLV-encoded names";
        int i = 0;
        while (i < len - 1) {
            int label_len = names[i++];
            // byte == 0xff means it is a '*' wildcard
            if (label_len == -1) continue;
            if (label_len < 1 || label_len > 63) {
                throw new IllegalArgumentException(
                        "label len: " + label_len + " must be between 1 and 63");
            }
            if (i + label_len >= len - 1) {
                throw new IllegalArgumentException(errorMessage);
            }
            while (label_len-- > 0) {
                if (!isValidDnsCharacter(names[i++])) {
                    throw new IllegalArgumentException("qname: " + HexDump.toHexString(names)
                            + " contains invalid character");
                }
            }
            if (names[i] == 0) {
                i++; // skip null terminator.
            }
        }
        if (names[len - 1] != 0) {
            throw new IllegalArgumentException(errorMessage);
        }
    }
}
