static void my_func(int new_arg) {  // new argument!
  (void)new_arg;
}

__attribute__((__used__))
static void (*resolve_func(void))(int) {
  return my_func;
}

// TODO: Add support for tracking type information
void func_changed(int new_arg) __attribute__((ifunc("resolve_func")));

void func_added() __attribute__((ifunc("resolve_func")));
