// tweaked due to https://gcc.gnu.org/bugzilla/show_bug.cgi?id=112372
void tweak(int);
void u(unsigned char c) { (void)c; tweak(0); }
void v(signed char c) { (void)c; tweak(1); }
void w(char c) { (void)c; tweak(2); }
void x(signed char c) { (void)c; tweak(3); }
void y(char c) { (void)c; tweak(4); }
void z(unsigned char c) { (void)c; tweak(5); }
