struct Foo {
  static int bar;
  static int baz();
  int m;
};
int Foo::baz() { return bar; }

Foo foo;
int Foo::bar;
