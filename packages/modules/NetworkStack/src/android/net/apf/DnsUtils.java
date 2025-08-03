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
import static android.net.apf.BaseApfGenerator.Register.R1;

import static com.android.net.module.util.NetworkStackConstants.ETHER_HEADER_LEN;
import static com.android.net.module.util.NetworkStackConstants.UDP_HEADER_LEN;

import androidx.annotation.NonNull;

/**
 * Utility class that generates generating APF filters for DNS packets.
 */
public class DnsUtils {

    /** Length of the DNS header. */
    private static final int DNS_HEADER_LEN = 12;
    /** Offset of the qdcount field within the DNS header. */
    private static final int DNS_QDCOUNT_OFFSET = 4;

    // Static labels
    private static final String LABEL_START_MATCH = "start_match";
    private static final String LABEL_PARSE_DNS_LABEL = "parse_dns_label";
    private static final String LABEL_FIND_NEXT_DNS_QUESTION = "find_next_dns_question";

    // Length of the pointers used by compressed names.
    private static final int LABEL_SIZE = Byte.BYTES;
    private static final int POINTER_SIZE = Short.BYTES;
    private static final int QUESTION_HEADER_SIZE = Short.BYTES + Short.BYTES;
    private static final int LABEL_AND_QUESTION_HEADER_SIZE = LABEL_SIZE + QUESTION_HEADER_SIZE;
    private static final int POINTER_AND_QUESTION_HEADER_SIZE = POINTER_SIZE + QUESTION_HEADER_SIZE;

    /** Memory slot that stores the offset within the packet of the DNS header. */
    private static final int SLOT_DNS_HEADER_OFFSET = 1;
    /** Memory slot that stores the current parsing offset. */
    private static final int SLOT_CURRENT_PARSE_OFFSET = 2;
    /**
     * Memory slot that stores the offset after the current question, if the code is currently
     * parsing a pointer, or 0 if it is not.
     */
    private static final int SLOT_AFTER_POINTER_OFFSET = 3;
    /**
     * Contains qdcount remaining, as a negative number. For example, will be -1 when starting to
     * parse a DNS packet with one question in it. It's stored as a negative number because adding 1
     * is much easier than subtracting 1 (which can't be done just by adding -1, because that just
     * adds 254).
     */
    private static final int SLOT_NEGATIVE_QDCOUNT_REMAINING = 6;
    /** Memory slot used by the jump table. */
    private static final int SLOT_RETURN_VALUE_INDEX = 10;

    /**
     * APF function: parse_dns_label
     *
     * Parses a label potentially containing a pointer, and calculates the label length and the
     * offset of the label data.
     *
     * Inputs:
     * - m[SLOT_DNS_HEADER_OFFSET]: offset of DNS header
     * - m[SLOT_CURRENT_PARSE_OFFSET]: current parsing offset
     * - m[SLOT_AFTER_POINTER_OFFSET]: offset after the question (e.g., offset of the next question,
     *        or offset of the answer section) if a pointer is being chased, 0 otherwise
     * - m[SLOT_RETURN_VALUE_INDEX]: index into return jump table
     *
     * Outputs:
     * - R1: label length
     * - m[SLOT_CURRENT_PARSE_OFFSET]: offset of label text
     */
    private static void genParseDnsLabel(ApfV4Generator gen, JumpTable jumpTable) throws Exception {
        final String labelParseDnsLabelReal = "parse_dns_label_real";
        final String labelPointerOffsetStored = "pointer_offset_stored";

        /**
         * :parse_dns_label
         * // Load parsing offset.
         * LDM R1, 2                        // R1 = parsing offset. (All indexed loads use R1.)
         */
        gen.defineLabel(LABEL_PARSE_DNS_LABEL);
        gen.addLoadFromMemory(R1, SLOT_CURRENT_PARSE_OFFSET);


        /**
         * // Check that we’re in the DNS packet, i.e., that R1 >= m[SLOT_DNS_HEADER_OFFSET].
         * LDM R0, 1                        // R0 = DNS header offset
         * JGT R0, R1, DROP                 // Bad pointer. Drop.
         */
        gen.addLoadFromMemory(R0, SLOT_DNS_HEADER_OFFSET);
        gen.addJumpIfR0GreaterThanR1(ApfV4Generator.DROP_LABEL);

        /**
         * // Now parse the label.
         * LDBX R0, [R1]                    // R0 = label length, R1 = parsing offset
         * AND R0, 0xc0                     // Is this a pointer?
         *
         * JEQ R0, 0, :parse_dns_label_real
         */
        gen.addLoad8Indexed(R0, 0);
        gen.addAnd(0xc0);
        gen.addJumpIfR0Equals(0, labelParseDnsLabelReal);


        /**
         * // If we’re not already chasing a pointer, store offset after pointer into
         * // m[SLOT_AFTER_POINTER_OFFSET].
         * LDM R0, 3                        // R0 = previous offset after pointer
         * JNE 0, :pointer_offset_stored
         * MOV R0, R1                       // R0 = R1
         * ADD R0, 6                        // R0 = offset after pointer and record
         * STM R0, 3                        // Store offset after pointer
         */
        gen.addLoadFromMemory(R0, SLOT_AFTER_POINTER_OFFSET);
        gen.addJumpIfR0NotEquals(0, labelPointerOffsetStored);
        gen.addMove(R0);
        gen.addAdd(POINTER_AND_QUESTION_HEADER_SIZE);
        gen.addStoreToMemory(R0, SLOT_AFTER_POINTER_OFFSET);

        /**
         * :pointer_offset_stored
         * LDHX R0, [R1]                    // R0 = 2-byte pointer value
         * AND R0, 0x3ff                    // R0 = pointer destination offset (from DNS header)
         * LDM R1, 1                        // R1 = offset in packet of DNS header
         * ADD R0, R1                       // R0 = pointer destination offset
         * LDM R1, 2                        // R1 = current parsing offset
         * JEQ R0, R1, DROP                 // Drop if pointer points here...
         * JGT R0, R1, DROP                 // ... or after here (must point backwards)
         * STM R0, 2                        // Set next parsing offset to pointer destination
         */
        gen.defineLabel(labelPointerOffsetStored);
        gen.addLoad16Indexed(R0, 0);
        gen.addAnd(0x3ff);
        gen.addLoadFromMemory(R1, SLOT_DNS_HEADER_OFFSET);
        gen.addAddR1();
        gen.addLoadFromMemory(R1, SLOT_CURRENT_PARSE_OFFSET);
        gen.addJumpIfR0EqualsR1(ApfV4Generator.DROP_LABEL);
        gen.addJumpIfR0GreaterThanR1(ApfV4Generator.DROP_LABEL);
        gen.addStoreToMemory(R0, SLOT_CURRENT_PARSE_OFFSET);

        /** // Pointer chased. Parse starting from the pointer destination (which may also be a
         * pointer).
         * JMP :parse_dns_label
         */
        gen.addJump(LABEL_PARSE_DNS_LABEL);

        /**
         * :parse_real_label
         * // This is where the real (non-pointer) label starts.
         * // Load label length into R1, and return to caller.
         * // m[SLOT_CURRENT_PARSE_OFFSET] already contains label offset.
         * LDHX R1, [R1]                    // R1 = label length
         */
        gen.defineLabel(labelParseDnsLabelReal);
        gen.addLoad8Indexed(R1, 0);

        /** // Return
         * LDM R0, 10
         * JMP :jump_table
         */
        gen.addLoadFromMemory(R0, SLOT_RETURN_VALUE_INDEX);
        gen.addJump(jumpTable.getStartLabel());
    }

    /**
     * APF function: find_next_dns_question
     *
     * Finds the next question in the question section, or drops the packet if there is none.
     *
     * Inputs:
     * - m[SLOT_CURRENT_PARSE_OFFSET]: current parsing offset
     * - m[SLOT_AFTER_POINTER_OFFSET]: offset after first pointer in name, or 0 if not chasing a
     *           pointer
     * - m[SLOT_NEGATIVE_QDCOUNT_REMAINING]: qdcount remaining, as a negative number. This is
     *           because adding 1 is much easier than subtracting 1 (which can't be done just by
     *           adding -1, because that just adds 254)
     * - m[SLOT_RETURN_VALUE_INDEX]: index into return jump table
     *
     * Outputs:
     * None
     */
    private static void genFindNextDnsQuestion(ApfV4Generator gen, JumpTable jumpTable)
            throws Exception {
        final String labelFindNextDnsQuestionFollow = "find_next_dns_question_follow";
        final String labelFindNextDnsQuestionLabel = "find_next_dns_question_label";
        final String labelFindNextDnsQuestionLoop = "find_next_dns_question_loop";
        final String labelFindNextDnsQuestionNoPointer = "find_next_dns_question_no_pointer";
        final String labelFindNextDnsQuestionReturn = "find_next_dns_question_return";

        // Function entry point.
        gen.defineLabel(LABEL_FIND_NEXT_DNS_QUESTION);

        // Are we chasing a pointer?
        gen.addLoadFromMemory(R0, SLOT_AFTER_POINTER_OFFSET);
        gen.addJumpIfR0Equals(0, labelFindNextDnsQuestionFollow);

        // If so, offset after the pointer and question is stored in m[SLOT_AFTER_POINTER_OFFSET].
        // Move parsing offset there, clear m[SLOT_AFTER_POINTER_OFFSET], and return.
        gen.addStoreToMemory(R0, SLOT_CURRENT_PARSE_OFFSET);
        gen.addLoadImmediate(R0, 0);
        gen.addStoreToMemory(R0, SLOT_AFTER_POINTER_OFFSET);
        gen.addJump(labelFindNextDnsQuestionReturn);

        // We weren't chasing a pointer. Loop, following the label chain, until we reach a
        // zero-length label or a pointer. At the beginning of the loop, the current parsing offset
        // is m[SLOT_CURRENT_PARSE_OFFSET]. Move it to R1 and keep it in R1 throughout the loop.
        gen.defineLabel(labelFindNextDnsQuestionFollow);
        gen.addLoadFromMemory(R1, SLOT_CURRENT_PARSE_OFFSET);

        // Load label length.
        gen.defineLabel(labelFindNextDnsQuestionLoop);
        gen.addLoad8Indexed(R0, 0);
        // Is it a pointer?
        gen.addAnd(0xc0);
        gen.addJumpIfR0Equals(0, labelFindNextDnsQuestionNoPointer);
        // It's a pointer. Skip the pointer and question, and return.
        gen.addLoadImmediate(R0, POINTER_AND_QUESTION_HEADER_SIZE);
        gen.addAddR1();
        gen.addStoreToMemory(R0, SLOT_CURRENT_PARSE_OFFSET);
        gen.addJump(labelFindNextDnsQuestionReturn);

        // R1 still contains parsing offset.
        gen.defineLabel(labelFindNextDnsQuestionNoPointer);
        gen.addLoad8Indexed(R0, 0);

        // Zero-length label? We're done.
        // Skip the label (1 byte) and query (2 bytes qtype, 2 bytes qclass) and return.
        gen.addJumpIfR0NotEquals(0, labelFindNextDnsQuestionLabel);
        gen.addLoadImmediate(R0, LABEL_AND_QUESTION_HEADER_SIZE);
        gen.addAddR1();
        gen.addStoreToMemory(R0, SLOT_CURRENT_PARSE_OFFSET);
        gen.addJump(labelFindNextDnsQuestionReturn);

        // Non-zero length label. Consume it and continue.
        gen.defineLabel(labelFindNextDnsQuestionLabel);
        gen.addAdd(1);
        gen.addAddR1();
        gen.addMove(R1);
        gen.addJump(labelFindNextDnsQuestionLoop);

        gen.defineLabel(labelFindNextDnsQuestionReturn);

        // Is this the last question? If so, drop.
        gen.addLoadFromMemory(R0, SLOT_NEGATIVE_QDCOUNT_REMAINING);
        gen.addAdd(1);
        gen.addStoreToMemory(R0, SLOT_NEGATIVE_QDCOUNT_REMAINING);
        gen.addJumpIfR0Equals(0, ApfV4Generator.DROP_LABEL);

        // If not, return.
        gen.addJump(jumpTable.getStartLabel());
    }

    /** @return jump label that points to the start of a DNS label's parsing code. */
    private static String getStartMatchLabel(int labelIndex) {
        return "dns_parse_" + labelIndex;
    }

    /** @return jump label used while parsing the specified DNS label. */
    private static String getPostMatchJumpTargetForLabel(int labelIndex) {
        return "dns_parsed_" + labelIndex;
    }

    /** @return jump label used when the match for the specified DNS label fails. */
    private static String getNoMatchLabel(int labelIndex) {
        return "dns_nomatch_" + labelIndex;
    }

    private static void addMatchLabel(@NonNull ApfV4Generator gen, @NonNull JumpTable jumpTable,
            int labelIndex, @NonNull String label, @NonNull String nextLabel) throws Exception {
        final String parsedLabel = getPostMatchJumpTargetForLabel(labelIndex);
        final String noMatchLabel = getNoMatchLabel(labelIndex);
        gen.defineLabel(getStartMatchLabel(labelIndex));

        // Store return address.
        gen.addLoadImmediate(R0, jumpTable.getIndex(parsedLabel));
        gen.addStoreToMemory(R0, SLOT_RETURN_VALUE_INDEX);

        // Call the parse_label function.
        gen.addJump(LABEL_PARSE_DNS_LABEL);

        gen.defineLabel(parsedLabel);

        // If label length is 0, this is the end of the name and the match failed.
        gen.addSwap(); // Move label length from R1 to R0
        gen.addJumpIfR0Equals(0, noMatchLabel);

        // Label parsed, check it matches what we're looking for.
        gen.addJumpIfR0NotEquals(label.length(), noMatchLabel);
        gen.addLoadFromMemory(R0, SLOT_CURRENT_PARSE_OFFSET);
        gen.addAdd(1);
        gen.addJumpIfBytesAtR0NotEqual(label.getBytes(), noMatchLabel);

        // Prep offset of next label.
        gen.addAdd(label.length());
        gen.addStoreToMemory(R0, SLOT_CURRENT_PARSE_OFFSET);

        // Match, go to next label.
        gen.addJump(nextLabel);

        // Match failed. Go to next name, and restart from the first match.
        gen.defineLabel(noMatchLabel);
        gen.addLoadImmediate(R1, jumpTable.getIndex(LABEL_START_MATCH));
        gen.addStoreToMemory(R1, SLOT_RETURN_VALUE_INDEX);
        gen.addJump(LABEL_FIND_NEXT_DNS_QUESTION);
    }

    /**
     * Generates a filter that accepts DNS packet that ask for the specified name.
     *
     * The filter supports compressed DNS names and scanning through multiple questions in the same
     * packet, e.g., as used by MDNS. However, it currently only supports one DNS name.
     *
     * Limitations:
     * <ul>
     * <li>Filter size is just under 300 bytes for a typical question.
     * <li>Because the bytecode extensively uses backwards jumps, it can hit the APF interpreter
     *   instruction limit. This limit causes the APF interpreter to accept the packet once it has
     *   executed a number of instructions equal to the program length in bytes.
     *   A program that consists *only* of this filter will be able to execute just under 300
     *   instructions, and will be able to correctly drop packets with two questions but not three
     *   questions. In a real APF setup, there will be other code (e.g., RA filtering) which counts
     *   against the limit, so the filter should be able to parse packets with more questions.
     * <li>Matches are case-sensitive. This is due to the use of JNEBS to match DNS labels and is
     *   likely impossible to overcome without interpreter changes.
     * </ul>
     *
     * TODO:
     * <ul>
     * <li>Add unit tests for the parse_dns_label and find_next_dns_question functions.
     * <li>Support accepting more than one name.
     * <li>For devices where power saving is a priority (e.g., flat panel TVs), add support for
     *   dropping packets with more than X queries, to ensure the filter will drop the packet rather
     *   than hit the instruction limit.
     * </ul>
     */
    public static void generateFilter(ApfV4Generator gen, String[] labels) throws Exception {
        final int etherPlusUdpLen = ETHER_HEADER_LEN + UDP_HEADER_LEN;

        final String labelJumpTable = "jump_table";

        // Initialize parsing
        /**
         * - R1: length of IP header.
         * - m[SLOT_DNS_HEADER_OFFSET]: offset of DNS header
         * - m[SLOT_CURRENT_PARSE_OFFSET]: current parsing offset (start of question section)
         * - m[SLOT_AFTER_POINTER_OFFSET]: offset after first pointer in name, must be 0 when
         *                                 starting a new name
         * - m[SLOT_NEGATIVE_QDCOUNT_REMAINING]: negative qdcount
         */
        // Move IP header length to R0 and use it to find the DNS header offset.
        // TODO: this uses R1 for consistency with ApfFilter#generateMdnsFilterLocked. Evaluate
        // using R0 instead.
        gen.addMove(R0);
        gen.addAdd(etherPlusUdpLen);
        gen.addStoreToMemory(R0, SLOT_DNS_HEADER_OFFSET);

        gen.addAdd(DNS_QDCOUNT_OFFSET);
        gen.addMove(R1);
        gen.addLoad16Indexed(R1, 0);
        gen.addNeg(R1);
        gen.addStoreToMemory(R1, SLOT_NEGATIVE_QDCOUNT_REMAINING);

        gen.addAdd(DNS_HEADER_LEN - DNS_QDCOUNT_OFFSET);
        gen.addStoreToMemory(R0, SLOT_CURRENT_PARSE_OFFSET);

        gen.addLoadImmediate(R0, 0);
        gen.addStoreToMemory(R0, SLOT_AFTER_POINTER_OFFSET);

        gen.addJump(LABEL_START_MATCH);

        // Create JumpTable but
        final JumpTable table = new JumpTable(labelJumpTable, SLOT_RETURN_VALUE_INDEX);

        // Generate bytecode for parse_label function.
        genParseDnsLabel(gen, table);
        genFindNextDnsQuestion(gen, table);

        // Populate jump table. Should be before the code that calls to it (i.e., the addMatchLabel
        // calls below) because otherwise all the jumps are backwards, and backwards jumps are more
        // expensive (5 bytes of bytecode)
        for (int i = 0; i < labels.length; i++) {
            table.addLabel(getPostMatchJumpTargetForLabel(i));
        }
        table.addLabel(LABEL_START_MATCH);
        table.generate(gen);

        // Add match statements for name.
        gen.defineLabel(LABEL_START_MATCH);
        for (int i = 0; i < labels.length; i++) {
            final String nextLabel = (i == labels.length - 1)
                    ? ApfV4Generator.PASS_LABEL
                    : getStartMatchLabel(i + 1);
            addMatchLabel(gen, table, i, labels[i], nextLabel);
        }
        gen.addJump(ApfV4Generator.DROP_LABEL);
    }

    private DnsUtils() {
    }
}
