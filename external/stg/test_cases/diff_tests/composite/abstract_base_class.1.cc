struct X {
  virtual int f() = 0;
  virtual int g() = 0;
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
  Y y;
  return fun(y) + sizeof(X);
}
