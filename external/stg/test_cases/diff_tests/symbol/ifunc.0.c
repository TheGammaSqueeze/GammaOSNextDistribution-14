static void my_func() {}

__attribute__((__used__))
static void (*resolve_func(void))(void) {
  return my_func;
}

void func_changed() __attribute__((ifunc("resolve_func")));

void func_removed() __attribute__((ifunc("resolve_func")));
