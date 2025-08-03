struct X {
  virtual int f();
  virtual int g();
};

struct Y : X {
  int f() override;
  int g() override;
};

int Y::f() {
  return 99;
}

int Y::g() {
  return 101;
}

int fun(X& x) {
  return x.f() + x.g();
}

int foo() {
  X x;
  Y y;
  return fun(y) - fun(x) + sizeof(X);
}
