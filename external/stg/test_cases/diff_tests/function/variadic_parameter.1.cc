int foo1(int x, int y, ...) { return x + y + 1; }
int foo2(int x, ...) { return x + 2; }
int foo3(int x, int y) { return x + y + 3; }
// GCC omits the ...: https://gcc.gnu.org/bugzilla/show_bug.cgi?id=111788
int foo4(...) { return 4; }
