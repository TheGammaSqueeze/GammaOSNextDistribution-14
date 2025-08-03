signed char x(signed char c) { return c; }
// tweaked due to https://gcc.gnu.org/bugzilla/show_bug.cgi?id=112372
char y(char c) { return ~c; }
unsigned char z(unsigned char c) { return c; }
