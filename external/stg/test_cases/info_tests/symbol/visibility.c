void tweak(int);
__attribute__ ((visibility ("default"))) void a() { tweak(0); }
__attribute__ ((visibility ("protected"))) void b() { tweak(1); }
__attribute__ ((visibility ("hidden"))) void c() { tweak(2); }
__attribute__ ((visibility ("internal"))) void d() { tweak(3); }
