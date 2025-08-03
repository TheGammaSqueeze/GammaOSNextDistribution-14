/**
 * Compares a (Q)NAME starting at udp[*ofs] with the target name.
 *
 * @param needle - non-NULL - pointer to DNS encoded target name to match against.
 *   example: [11]_googlecast[4]_tcp[5]local[0]  (where [11] is a byte with value 11)
 * @param needle_bound - non-NULL - points at first invalid byte past needle.
 * @param udp - non-NULL - pointer to the start of the UDP payload (DNS header).
 * @param udp_len - length of the UDP payload.
 * @param ofs - non-NULL - pointer to the offset of the beginning of the (Q)NAME.
 *   On non-error return will be updated to point to the first unread offset,
 *   ie. the next position after the (Q)NAME.
 *
 * @return 1 if matched, 0 if not matched, -1 if error in packet, -2 if error in program.
 */
FUNC(match_result_type match_single_name(const u8* needle,
                                    const u8* const needle_bound,
                                    const u8* const udp,
                                    const u32 udp_len,
                                    u32* const ofs)) {
    u32 first_unread_offset = *ofs;
    bool is_qname_match = true;
    int lvl;

    /* DNS names are <= 255 characters including terminating 0, since >= 1 char + '.' per level => max. 127 levels */
    for (lvl = 1; lvl <= 127; ++lvl) {
        if (*ofs >= udp_len) return error_packet;
        u8 v = udp[(*ofs)++];
        if (v >= 0xC0) { /* RFC 1035 4.1.4 - handle message compression */
            if (*ofs >= udp_len) return error_packet;
            u8 w = udp[(*ofs)++];
            if (*ofs > first_unread_offset) first_unread_offset = *ofs;
            u32 new_ofs = (v - 0xC0) * 256 + w;
            if (new_ofs >= *ofs) return error_packet;  /* RFC 1035 4.1.4 allows only backward pointers */
            *ofs = new_ofs;
        } else if (v > 63) {
            return error_packet;  /* RFC 1035 2.3.4 - label size is 1..63. */
        } else if (v) {
            u8 label_size = v;
            if (*ofs + label_size > udp_len) return error_packet;
            if (needle >= needle_bound) return error_program;
            if (is_qname_match) {
                u8 len = *needle++;
                if (len == label_size) {
                    if (needle + label_size > needle_bound) return error_program;
                    while (label_size--) {
                        u8 w = udp[(*ofs)++];
                        is_qname_match &= (uppercase(w) == *needle++);
                    }
                } else {
                    if (len != 0xFF) is_qname_match = false;
                    *ofs += label_size;
                }
            } else {
                is_qname_match = false;
                *ofs += label_size;
            }
        } else { /* reached the end of the name */
            if (first_unread_offset > *ofs) *ofs = first_unread_offset;
            return (is_qname_match && *needle == 0) ? match : nomatch;
        }
    }
    return error_packet;  /* too many dns domain name levels */
}

/**
 * Check if DNS packet contains any of the target names with the provided
 * question_type.
 *
 * @param needles - non-NULL - pointer to DNS encoded target nameS to match against.
 *   example: [3]foo[3]com[0][3]bar[3]net[0][0]  -- note ends with an extra NULL byte.
 * @param needle_bound - non-NULL - points at first invalid byte past needles.
 * @param udp - non-NULL - pointer to the start of the UDP payload (DNS header).
 * @param udp_len - length of the UDP payload.
 * @param question_type - question type to match against or -1 to match answers.
 *
 * @return 1 if matched, 0 if not matched, -1 if error in packet, -2 if error in program.
 */
FUNC(match_result_type match_names(const u8* needles,
                              const u8* const needle_bound,
                              const u8* const udp,
                              const u32 udp_len,
                              const int question_type)) {
    if (udp_len < 12) return error_packet;  /* lack of dns header */

    /* dns header: be16 tid, flags, num_{questions,answers,authority,additional} */
    u32 num_questions = read_be16(udp + 4);
    u32 num_answers = read_be16(udp + 6) + read_be16(udp + 8) + read_be16(udp + 10);

    /* loop until we hit final needle, which is a null byte */
    while (true) {
        if (needles >= needle_bound) return error_program;
        if (!*needles) return nomatch;  /* we've run out of needles without finding a match */
        u32 ofs = 12;  /* dns header is 12 bytes */
        u32 i;
        /* match questions */
        for (i = 0; i < num_questions; ++i) {
            match_result_type m = match_single_name(needles, needle_bound, udp, udp_len, &ofs);
            if (m < nomatch) return m;
            if (ofs + 2 > udp_len) return error_packet;
            int qtype = (int)read_be16(udp + ofs);
            ofs += 4; /* skip be16 qtype & qclass */
            if (question_type == -1) continue;
            if (m == nomatch) continue;
            if (qtype == 0xFF /* QTYPE_ANY */ || qtype == question_type) return match;
        }
        /* match answers */
        if (question_type == -1) for (i = 0; i < num_answers; ++i) {
            match_result_type m = match_single_name(needles, needle_bound, udp, udp_len, &ofs);
            if (m < nomatch) return m;
            ofs += 8; /* skip be16 type, class & be32 ttl */
            if (ofs + 2 > udp_len) return error_packet;
            ofs += 2 + read_be16(udp + ofs);  /* skip be16 rdata length field, plus length bytes */
            if (m == match) return match;
        }
        /* move needles pointer to the next needle. */
        do {
            u8 len = *needles++;
            if (len == 0xFF) continue;
            if (len > 63) return error_program;
            needles += len;
            if (needles >= needle_bound) return error_program;
        } while (*needles);
        needles++;  /* skip the NULL byte at the end of *a* DNS name */
    }
}
