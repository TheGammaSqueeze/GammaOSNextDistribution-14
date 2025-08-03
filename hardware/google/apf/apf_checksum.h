/**
 * Calculate big endian 16-bit sum of a buffer (max 128kB),
 * then fold and negate it, producing a 16-bit result in [0..FFFE].
 */
FUNC(u16 calc_csum(u32 sum, const u8* const buf, const s32 len)) {
    s32 i;
    for (i = 0; i < len; ++i) sum += buf[i] * ((i & 1) ? 1 : 256);

    sum = (sum & 0xFFFF) + (sum >> 16);  /* max after this is 1FFFE */
    u16 csum = sum + (sum >> 16);
    return ~csum;  /* assuming sum > 0 on input, this is in [0..FFFE] */
}

static u16 fix_udp_csum(u16 csum) {
    return csum ? csum : 0xFFFF;
}

/**
 * Calculate and store packet checksums and return dscp.
 *
 * @param pkt - pointer to the very start of the to-be-transmitted packet,
 *              ie. the start of the ethernet header (if one is present)
 *     WARNING: at minimum 266 bytes of buffer pointed to by 'pkt' pointer
 *              *MUST* be writable.
 * (IPv4 header checksum is a 2 byte value, 10 bytes after ip_ofs,
 * which has a maximum value of 254.  Thus 254[ip_ofs] + 10 + 2[u16] = 266)
 *
 * @param len - length of the packet (this may be < 266).
 * @param ip_ofs - offset from beginning of pkt to IPv4 or IPv6 header:
 *                 IP version detected based on top nibble of this byte,
 *                 for IPv4 we will calculate and store IP header checksum,
 *                 but only for the first 20 bytes of the header,
 *                 prior to calling this the IPv4 header checksum field
 *                 must be initialized to the partial checksum of the IPv4
 *                 options (0 if none)
 *                 255 means there is no IP header (for example ARP)
 *                 DSCP will be retrieved from this IP header (0 if none).
 * @param partial_csum - additional value to include in L4 checksum
 * @param csum_start - offset from beginning of pkt to begin L4 checksum
 *                     calculation (until end of pkt specified by len)
 * @param csum_ofs - offset from beginning of pkt to store L4 checksum
 *                   255 means do not calculate/store L4 checksum
 * @param udp - true iff we should generate a UDP style L4 checksum (0 -> 0xFFFF)
 *
 * @return 6-bit DSCP value [0..63], garbage on parse error.
 */
FUNC(int csum_and_return_dscp(u8* const pkt, const s32 len, const u8 ip_ofs,
  const u16 partial_csum, const u8 csum_start, const u8 csum_ofs, const bool udp)) {
    if (csum_ofs < 255) {
        // note that calc_csum() treats negative lengths as zero
        u32 csum = calc_csum(partial_csum, pkt + csum_start, len - csum_start);
        if (udp) csum = fix_udp_csum(csum);
        store_be16(pkt + csum_ofs, csum);
    }
    if (ip_ofs < 255) {
        u8 ip = pkt[ip_ofs] >> 4;
        if (ip == 4) {
            store_be16(pkt + ip_ofs + 10, calc_csum(0, pkt + ip_ofs, IPV4_HLEN));
            return pkt[ip_ofs + 1] >> 2;  /* DSCP */
        } else if (ip == 6) {
            return (read_be16(pkt + ip_ofs) >> 6) & 0x3F;  /* DSCP */
        }
    }
    return 0;
}
