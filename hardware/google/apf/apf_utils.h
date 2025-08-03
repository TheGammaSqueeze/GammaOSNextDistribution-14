static u32 read_be16(const u8* buf) {
    return buf[0] * 256u + buf[1];
}

static void store_be16(u8* const buf, const u16 v) {
    buf[0] = (u8)(v >> 8);
    buf[1] = (u8)v;
}

static u8 uppercase(u8 c) {
    return (c >= 'a') && (c <= 'z') ? c - ('a' - 'A') : c;
}
