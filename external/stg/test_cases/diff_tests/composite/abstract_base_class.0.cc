struct X {
  virtual int f() = 0;
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
  Y y;
  return fun(y) + sizeof(X);
}
