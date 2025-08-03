struct foo {
  long* ptr;
  long& lref;
  long&& rref;
};

long func(foo x) {
  return *x.ptr + x.lref + x.rref;
}
