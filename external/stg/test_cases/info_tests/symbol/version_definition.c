// Test for versioned symbol
//
// Currently version information is unsupported by ELF reader, so tests may
// produce wrong results.
// TODO: remove statement above after support is implemented

void tweak(int dummy);

void versioned_foo(void) { tweak(1); }

__asm__(".symver versioned_foo_v1, versioned_foo@@VERS_1");
void versioned_foo_v1(void) { tweak(2); }

__asm__(".symver versioned_foo_v2, versioned_foo@VERS_2");
void versioned_foo_v2(void) { tweak(3); }

__asm__(".symver versioned_foo_v3, versioned_foo@VERS_3");
void versioned_foo_v3(void) { tweak(4); }

// Using a libc function helps to add the "version needs" section
// in addition to the "version definitions". This helps to catch
// bugs when a file has both of these sections.
int printf(const char *format, ...);

int test() {
  printf("test");
  return 0;
}
