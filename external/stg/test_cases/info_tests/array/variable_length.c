int foo(void);

int bar(void) {
  int n = foo();
  int a[n];
  return a[n - 1];
}
