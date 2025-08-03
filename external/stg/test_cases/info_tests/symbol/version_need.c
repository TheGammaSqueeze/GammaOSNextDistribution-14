// Test for versioned symbol dependency.
//
// Currently version information is unsupported by ELF reader, so tests may
// produce wrong results.
// TODO: remove statement above after support is implemented

__asm__(".symver versioned_foo_v1, versioned_foo@VERS_2");
void versioned_foo_v1(void);

__asm__(".symver versioned_foo_v2, versioned_foo@VERS_3");
void versioned_foo_v2(void);

void test() {
  versioned_foo_v1();
  versioned_foo_v2();
}
