// tweaked due to https://gcc.gnu.org/bugzilla/show_bug.cgi?id=112372
void tweak(int);
void u(char c) { (void)c; tweak(0); }
void v(unsigned char c) { (void)c; tweak(1); }
void w(signed char c) { (void)c; tweak(2); }
void x(char c) { (void)c; tweak(3); }
void y(unsigned char c) { (void)c; tweak(4); }
void z(signed char c) { (void)c; tweak(5); }
