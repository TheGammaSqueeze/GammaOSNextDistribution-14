struct X {
  virtual int f();
};

struct Y : X {
  int f() override;
};

int Y::f() {
  return 99;
}

int fun(X& x) {
  return x.f();
}

int foo() {
  X x;
  Y y;
  return fun(y) - fun(x) + sizeof(X);
}
