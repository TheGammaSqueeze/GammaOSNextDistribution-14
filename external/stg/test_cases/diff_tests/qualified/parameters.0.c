struct S {
  int (*f01)(int);
  int (*f02)(const int*);
  int (*f03)(int* const);
  int (*f04)(int* restrict);
  int (*f05)(const int* restrict);
  int (*f06)(int* restrict const);
  int (*f07)(int* const restrict);
};

struct S s;
