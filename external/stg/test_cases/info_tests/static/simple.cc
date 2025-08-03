struct Foo {
  int bar;
  static int st;
  static Foo Default();
};
Foo Foo::Default() { return {.bar = st}; }

int Foo::st;
