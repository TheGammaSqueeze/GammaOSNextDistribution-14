struct foo {
  int* ptr;
  int& lref;
  int&& rref;
};

int func(foo x) {
  return *x.ptr + x.lref + x.rref;
}
