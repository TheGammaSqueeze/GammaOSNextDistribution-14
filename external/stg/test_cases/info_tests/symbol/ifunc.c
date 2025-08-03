static void my_func() {}

__attribute__((__used__))
static void (*resolve_func(void))(void) {
  return my_func;
}

void func() __attribute__((ifunc("resolve_func")));
