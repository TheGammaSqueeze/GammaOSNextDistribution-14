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
import static android.net.apf.BaseApfGenerator.Register.R0;
import static android.net.apf.BaseApfGenerator.Register.R1;

import com.android.internal.annotations.VisibleForTesting;

/**
 * APF assembler/generator.  A tool for generating an APF program.
 *
 * Call add*() functions to add instructions to the program, then call
 * {@link BaseApfGenerator#generate} to get the APF bytecode for the program.
 *
 * @param <Type> the generator class
 *
 * @hide
 */
public abstract class ApfV4GeneratorBase<Type extends ApfV4GeneratorBase<Type>> extends
        BaseApfGenerator {

    /**
     * Creates an ApfV4GeneratorBase instance which is able to emit instructions for the specified
     * {@code version} of the APF interpreter. Throws {@code IllegalInstructionException} if
     * the requested version is unsupported.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
    public ApfV4GeneratorBase(int version) throws IllegalInstructionException {
        super(version);
        requireApfVersion(MIN_APF_VERSION);
    }

    protected Type self() {
        return (Type) this;
    }

    Type append(Instruction instruction) {
        if (mGenerated) {
            throw new IllegalStateException("Program already generated");
        }
        mInstructions.add(instruction);
        return self();
    }

    /**
     * Define a label at the current end of the program. Jumps can jump to this label. Labels are
     * their own separate instructions, though with size 0. This facilitates having labels with
     * no corresponding code to execute, for example a label at the end of a program. For example
     * an {@link ApfV4GeneratorBase} might be passed to a function that adds a filter like so:
     * <pre>
     *   load from packet
     *   compare loaded data, jump if not equal to "next_filter"
     *   load from packet
     *   compare loaded data, jump if not equal to "next_filter"
     *   jump to drop label
     *   define "next_filter" here
     * </pre>
     * In this case "next_filter" may not have any generated code associated with it.
     */
    public Type defineLabel(String name) throws IllegalInstructionException {
        return append(new Instruction(Opcodes.LABEL).setLabel(name));
    }

    /**
     * Add an unconditional jump instruction to the end of the program.
     */
    public Type addJump(String target) {
        return append(new Instruction(Opcodes.JMP).setTargetLabel(target));
    }

    /**
     * Add an unconditional jump instruction to the next instruction - ie. a no-op.
     */
    public Type addNop() {
        return append(new Instruction(Opcodes.JMP).addUnsigned(0));
    }

    /**
     * Add an instruction to the end of the program to load the byte at offset {@code offset}
     * bytes from the beginning of the packet into {@code register}.
     */
    public Type addLoad8(Register r, int ofs) {
        return append(new Instruction(Opcodes.LDB, r).addPacketOffset(ofs));
    }

    /**
     * Add an instruction to the end of the program to load 16-bits at offset {@code offset}
     * bytes from the beginning of the packet into {@code register}.
     */
    public Type addLoad16(Register r, int ofs) {
        return append(new Instruction(Opcodes.LDH, r).addPacketOffset(ofs));
    }

    /**
     * Add an instruction to the end of the program to load 32-bits at offset {@code offset}
     * bytes from the beginning of the packet into {@code register}.
     */
    public Type addLoad32(Register r, int ofs) {
        return append(new Instruction(Opcodes.LDW, r).addPacketOffset(ofs));
    }

    /**
     * Add an instruction to the end of the program to load a byte from the packet into
     * {@code register}. The offset of the loaded byte from the beginning of the packet is
     * the sum of {@code offset} and the value in register R1.
     */
    public Type addLoad8Indexed(Register r, int ofs) {
        return append(new Instruction(Opcodes.LDBX, r).addPacketOffset(ofs));
    }

    /**
     * Add an instruction to the end of the program to load 16-bits from the packet into
     * {@code register}. The offset of the loaded 16-bits from the beginning of the packet is
     * the sum of {@code offset} and the value in register R1.
     */
    public Type addLoad16Indexed(Register r, int ofs) {
        return append(new Instruction(Opcodes.LDHX, r).addPacketOffset(ofs));
    }

    /**
     * Add an instruction to the end of the program to load 32-bits from the packet into
     * {@code register}. The offset of the loaded 32-bits from the beginning of the packet is
     * the sum of {@code offset} and the value in register R1.
     */
    public Type addLoad32Indexed(Register r, int ofs) {
        return append(new Instruction(Opcodes.LDWX, r).addPacketOffset(ofs));
    }

    /**
     * Add an instruction to the end of the program to add {@code value} to register R0.
     */
    public Type addAdd(long val) {
        if (val == 0) return self();  // nop, as APFv6 would '+= R1'
        return append(new Instruction(Opcodes.ADD).addTwosCompUnsigned(val));
    }

    /**
     * Add an instruction to the end of the program to subtract {@code value} from register R0.
     */
    public Type addSub(long val) {
        return addAdd(-val);  // note: addSub(4 billion) isn't valid, as addAdd(-4 billion) isn't
    }

    /**
     * Add an instruction to the end of the program to multiply register R0 by {@code value}.
     */
    public Type addMul(long val) {
        if (val == 0) return addLoadImmediate(R0, 0);  // equivalent, as APFv6 would '*= R1'
        return append(new Instruction(Opcodes.MUL).addUnsigned(val));
    }

    /**
     * Add an instruction to the end of the program to divide register R0 by {@code value}.
     */
    public Type addDiv(long val) {
        if (val == 0) return addPass();  // equivalent, as APFv6 would '/= R1'
        return append(new Instruction(Opcodes.DIV).addUnsigned(val));
    }

    /**
     * Add an instruction to the end of the program to logically and register R0 with {@code value}.
     */
    public Type addAnd(long val) {
        if (val == 0) return addLoadImmediate(R0, 0);  // equivalent, as APFv6 would '+= R1'
        return append(new Instruction(Opcodes.AND).addTwosCompUnsigned(val));
    }

    /**
     * Add an instruction to the end of the program to logically or register R0 with {@code value}.
     */
    public Type addOr(long val) {
        if (val == 0) return self();  // nop, as APFv6 would '|= R1'
        return append(new Instruction(Opcodes.OR).addTwosCompUnsigned(val));
    }

    /**
     * Add an instruction to the end of the program to shift left register R0 by {@code value} bits.
     */
    // TODO: consider whether should change the argument type to byte
    public Type addLeftShift(int val) {
        if (val == 0) return self();  // nop, as APFv6 would '<<= R1'
        return append(new Instruction(Opcodes.SH).addSigned(val));
    }

    /**
     * Add an instruction to the end of the program to shift right register R0 by {@code value}
     * bits.
     */
    // TODO: consider whether should change the argument type to byte
    public Type addRightShift(int val) {
        return addLeftShift(-val);
    }

    // Argument should be one of Opcodes.{ADD,MUL,DIV,AND,OR,SH}
    protected void addArithR1(Opcodes opcode) {
        append(new Instruction(opcode, R1));
    }

    /**
     * Add an instruction to the end of the program to add register R1 to register R0.
     */
    public Type addAddR1() {
        addArithR1(Opcodes.ADD);
        return self();
    }

    /**
     * Add an instruction to the end of the program to multiply register R0 by register R1.
     */
    public Type addMulR1() {
        addArithR1(Opcodes.MUL);
        return self();
    }

    /**
     * Add an instruction to the end of the program to divide register R0 by register R1.
     */
    public Type addDivR1() {
        addArithR1(Opcodes.DIV);
        return self();
    }

    /**
     * Add an instruction to the end of the program to logically and register R0 with register R1
     * and store the result back into register R0.
     */
    public Type addAndR1() {
        addArithR1(Opcodes.AND);
        return self();
    }

    /**
     * Add an instruction to the end of the program to logically or register R0 with register R1
     * and store the result back into register R0.
     */
    public Type addOrR1() {
        addArithR1(Opcodes.OR);
        return self();
    }

    /**
     * Add an instruction to the end of the program to shift register R0 left by the value in
     * register R1.
     */
    public Type addLeftShiftR1() {
        addArithR1(Opcodes.SH);
        return self();
    }

    /**
     * Add an instruction to the end of the program to move {@code value} into {@code register}.
     */
    public Type addLoadImmediate(Register register, int value) {
        return append(new Instruction(Opcodes.LI, register).addSigned(value));
    }

    /**
     * Add an instruction to the end of the program to jump to {@code target} if register R0's
     * value equals {@code value}.
     */
    public Type addJumpIfR0Equals(int val, String tgt) {
        return append(new Instruction(Opcodes.JEQ).addTwosCompUnsigned(val).setTargetLabel(tgt));
    }

    /**
     * Add an instruction to the end of the program to jump to {@code target} if register R0's
     * value does not equal {@code value}.
     */
    public Type addJumpIfR0NotEquals(int val, String tgt) {
        return append(new Instruction(Opcodes.JNE).addTwosCompUnsigned(val).setTargetLabel(tgt));
    }

    /**
     * Add an instruction to the end of the program to jump to {@code target} if register R0's
     * value is greater than {@code value}.
     */
    public Type addJumpIfR0GreaterThan(long val, String tgt) {
        return append(new Instruction(Opcodes.JGT).addUnsigned(val).setTargetLabel(tgt));
    }

    /**
     * Add an instruction to the end of the program to jump to {@code target} if register R0's
     * value is less than {@code value}.
     */
    public Type addJumpIfR0LessThan(long val, String tgt) {
        return append(new Instruction(Opcodes.JLT).addUnsigned(val).setTargetLabel(tgt));
    }

    /**
     * Add an instruction to the end of the program to jump to {@code target} if register R0's
     * value has any bits set that are also set in {@code value}.
     */
    public Type addJumpIfR0AnyBitsSet(int val, String tgt) {
        return append(new Instruction(Opcodes.JSET).addTwosCompUnsigned(val).setTargetLabel(tgt));
    }
    /**
     * Add an instruction to the end of the program to jump to {@code target} if register R0's
     * value equals register R1's value.
     */
    public Type addJumpIfR0EqualsR1(String tgt) {
        return append(new Instruction(Opcodes.JEQ, R1).setTargetLabel(tgt));
    }

    /**
     * Add an instruction to the end of the program to jump to {@code target} if register R0's
     * value does not equal register R1's value.
     */
    public Type addJumpIfR0NotEqualsR1(String tgt) {
        return append(new Instruction(Opcodes.JNE, R1).setTargetLabel(tgt));
    }

    /**
     * Add an instruction to the end of the program to jump to {@code target} if register R0's
     * value is greater than register R1's value.
     */
    public Type addJumpIfR0GreaterThanR1(String tgt) {
        return append(new Instruction(Opcodes.JGT, R1).setTargetLabel(tgt));
    }

    /**
     * Add an instruction to the end of the program to jump to {@code target} if register R0's
     * value is less than register R1's value.
     */
    public Type addJumpIfR0LessThanR1(String target) {
        return append(new Instruction(Opcodes.JLT, R1).setTargetLabel(target));
    }

    /**
     * Add an instruction to the end of the program to jump to {@code target} if register R0's
     * value has any bits set that are also set in R1's value.
     */
    public Type addJumpIfR0AnyBitsSetR1(String tgt) {
        return append(new Instruction(Opcodes.JSET, R1).setTargetLabel(tgt));
    }

    /**
     * Add an instruction to the end of the program to jump to {@code tgt} if the bytes of the
     * packet at an offset specified by {@code register} don't match {@code bytes}
     * R=0 means check for not equal
     */
    public Type addJumpIfBytesAtR0NotEqual(byte[] bytes, String tgt) {
        return append(new Instruction(Opcodes.JNEBS).addUnsigned(
                bytes.length).setTargetLabel(tgt).setBytesImm(bytes));
    }

    /**
     * Add an instruction to the end of the program to jump to {@code tgt} if the bytes of the
     * packet at an offset specified by {@code register} match {@code bytes}
     * R=1 means check for equal.
     */
    public Type addJumpIfBytesAtR0Equal(byte[] bytes, String tgt)
            throws IllegalInstructionException {
        requireApfVersion(MIN_APF_VERSION_IN_DEV);
        return append(new Instruction(Opcodes.JNEBS, R1).addUnsigned(
                bytes.length).setTargetLabel(tgt).setBytesImm(bytes));
    }

    /**
     * Add an instruction to the end of the program to load memory slot {@code slot} into
     * {@code register}.
     */
    public Type addLoadFromMemory(Register r, int slot)
            throws IllegalInstructionException {
        return append(new BaseApfGenerator.Instruction(ExtendedOpcodes.LDM, slot, r));
    }

    /**
     * Add an instruction to the end of the program to store {@code register} into memory slot
     * {@code slot}.
     */
    public Type addStoreToMemory(Register r, int slot)
            throws IllegalInstructionException {
        return append(new Instruction(ExtendedOpcodes.STM, slot, r));
    }

    /**
     * Add an instruction to the end of the program to logically not {@code register}.
     */
    public Type addNot(Register r) {
        return append(new Instruction(ExtendedOpcodes.NOT, r));
    }

    /**
     * Add an instruction to the end of the program to negate {@code register}.
     */
    public Type addNeg(Register r) {
        return append(new Instruction(ExtendedOpcodes.NEG, r));
    }

    /**
     * Add an instruction to swap the values in register R0 and register R1.
     */
    public Type addSwap() {
        return append(new Instruction(ExtendedOpcodes.SWAP));
    }

    /**
     * Add an instruction to the end of the program to move the value into
     * {@code register} from the other register.
     */
    public Type addMove(Register r) {
        return append(new Instruction(ExtendedOpcodes.MOVE, r));
    }

    /**
     * Add an instruction to the end of the program to let the program immediately return PASS.
     */
    public Type addPass() {
        // PASS requires using Rbit0 because it shares opcode with DROP
        return append(new Instruction(Opcodes.PASSDROP, Rbit0));
    }

    /**
     * Add an instruction to the end of the program to load 32 bits from the data memory into
     * {@code register}. The source address is computed by adding the signed immediate
     * @{code offset} to the other register.
     * Requires APF v4 or greater.
     */
    public Type addLoadData(Register dst, int ofs)
            throws IllegalInstructionException {
        requireApfVersion(APF_VERSION_4);
        return append(new Instruction(Opcodes.LDDW, dst).addSigned(ofs));
    }

    /**
     * Add an instruction to the end of the program to store 32 bits from {@code register} into the
     * data memory. The destination address is computed by adding the signed immediate
     * @{code offset} to the other register.
     * Requires APF v4 or greater.
     */
    public Type addStoreData(Register src, int ofs)
            throws IllegalInstructionException {
        requireApfVersion(APF_VERSION_4);
        return append(new Instruction(Opcodes.STDW, src).addSigned(ofs));
    }

}

