struct foo {
  int& lref_to_ptr;
  int* ptr_to_lref;

  int&& rref_to_ptr;
  int* ptr_to_rref;

  int& lref_to_rref;
  int&& rref_to_lref;
};

void func(foo a) {
  (void) a;
}
