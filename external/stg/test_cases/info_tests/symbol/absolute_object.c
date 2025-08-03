// Create ELF symbol that is global object and has absolute value
__asm__(
    ".global bar\n"
    ".type bar,object\n"
    "bar = 0x1\n");

long x, y;