struct S {
  virtual void z() { }
  virtual void y() { }
  virtual void x() { }
  virtual void w() { }
};

void fun() {
  S s;
  s.w();
  s.x();
  s.y();
  s.z();
}
