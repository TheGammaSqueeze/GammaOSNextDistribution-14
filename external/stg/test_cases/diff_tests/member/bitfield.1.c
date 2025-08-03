struct B {
  unsigned long long a : 1;
  unsigned long long b : 2;
  unsigned long long c : 3;
  unsigned long long d : 5;
  unsigned long long e : 8;
  unsigned long long f : 13;
  unsigned long long g : 21;
  unsigned long long h : 34;
  unsigned long long i : 55;
};

void fun(const struct B * b) { (void) b; }
