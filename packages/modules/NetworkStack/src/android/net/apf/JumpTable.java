/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.net.apf.BaseApfGenerator.Register.R0;

import androidx.annotation.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * A table that stores program labels to jump to.
 *
 * This is needed to implement subroutines because APF jump targets must be known at compile
 * time and cannot be computed dynamically.
 *
 * At compile time, any code that calls a subroutine must:
 *
 * <ul>
 * <li>Define a label (via {@link ApfV4Generator#defineLabel}) immediately after the code that
 *     invokes the subroutine.
 * <li>Add the label to the jump table using {@link #addLabel}.
 * <li>Generate the jump table in the program.
 * </ul>
 *
 * <p>At runtime, before invoking the subroutine, the APF code must store the index of the return
 * label (obtained via {@link #getIndex}) into the jump table's return address memory slot, and then
 * jump to the subroutine. To return to the caller, the subroutine must jump to the label returned
 * by {@link #getStartLabel}, and the jump table will then jump to the return label.
 *
 * <p>Implementation details:
 * <ul>
 * <li>The jumps are added to the program in the same order as the labels were added.
 * <li>Using the jump table will overwrite the value of register R0.
 * <li>If, before calling a subroutine, the APF code stores a nonexistent return label index, then
 *     the jump table will pass the packet. This cannot happen if the code correctly obtains the
 *     label using {@link #getIndex}, as that would throw an exception when generating the program.
 * </ul>
 *
 * For example:
 * <pre>
 *     JumpTable t = new JumpTable("my_jump_table", 7);
 *     t.addLabel("jump_1");
 *     ...
 *     t.addLabel("after_parsing");
 *     ...
 *     t.addLabel("after_subroutine");
 *     t.generate(gen);
 *</pre>
 * generates the following APF code:
 * <pre>
 *     :my_jump_table
 *     ldm r0, 7
 *     jeq r0, 0, jump_1
 *     jeq r0, 1, after_parsing
 *     jeq r0, 2, after_subroutine
 *     jmp DROP
 * </pre>
 */
public class JumpTable {
    /** Maps jump indices to jump labels. LinkedHashMap guarantees iteration in insertion order. */
    private final Map<String, Integer> mJumpLabels = new LinkedHashMap<>();
    /** Label to jump to to execute this jump table. */
    private final String mStartLabel;
    /** Memory slot that contains the return value index. */
    private final int mReturnAddressMemorySlot;

    private int mIndex = 0;

    public JumpTable(@NonNull String startLabel, int returnAddressMemorySlot) {
        Objects.requireNonNull(startLabel);
        mStartLabel = startLabel;
        if (returnAddressMemorySlot < 0
                || returnAddressMemorySlot >= ApfV4Generator.FIRST_PREFILLED_MEMORY_SLOT) {
            throw new IllegalArgumentException("Invalid memory slot " + returnAddressMemorySlot);
        }
        mReturnAddressMemorySlot = returnAddressMemorySlot;
    }

    /** Returns the label to jump to to start executing the table. */
    @NonNull
    public String getStartLabel() {
        return mStartLabel;
    }

    /**
     * Adds a jump label to this table. Passing a label that was already added is not an error.
     *
     * @param label the label to add
     */
    public void addLabel(@NonNull String label) {
        Objects.requireNonNull(label);
        if (mJumpLabels.putIfAbsent(label, mIndex) == null) mIndex++;
    }

    /**
     * Gets the index of a previously-added label.
     * @return the label's index.
     * @throws NoSuchElementException if the label was never added.
     */
    public int getIndex(@NonNull String label) {
        final Integer index = mJumpLabels.get(label);
        if (index == null) throw new NoSuchElementException("Unknown label " + label);
        return index;
    }

    /** Generates APF code for this jump table */
    public void generate(@NonNull ApfV4Generator gen)
            throws ApfV4Generator.IllegalInstructionException {
        gen.defineLabel(mStartLabel);
        gen.addLoadFromMemory(R0, mReturnAddressMemorySlot);
        for (Map.Entry<String, Integer> e : mJumpLabels.entrySet()) {
            gen.addJumpIfR0Equals(e.getValue(), e.getKey());
        }
        // Cannot happen unless the program is malformed (i.e., the APF code loads an invalid return
        // label index before jumping to the subroutine.
        gen.addJump(ApfV4Generator.PASS_LABEL);
    }
}
