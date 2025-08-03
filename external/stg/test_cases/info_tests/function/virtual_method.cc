struct Foo {
  virtual void bar();
  virtual void baz();
} foo;

void tweak(int);
void Foo::bar() { tweak(0); }
void Foo::baz() { tweak(1); }
