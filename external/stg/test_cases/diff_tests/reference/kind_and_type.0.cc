struct foo {
  int& lref_to_ptr;
  int* ptr_to_lref;

  int&& rref_to_ptr;
  int* ptr_to_rref;

  int& lref_to_rref;
  int&& rref_to_lref;
};

int func(foo x) {
  return x.lref_to_ptr + x.rref_to_lref;
}
