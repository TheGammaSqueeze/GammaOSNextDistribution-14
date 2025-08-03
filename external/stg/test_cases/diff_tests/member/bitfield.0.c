struct B {
  unsigned long long a : 1;
  unsigned long long b : 1;
  unsigned long long c : 2;
  unsigned long long d : 3;
  unsigned long long e : 5;
  unsigned long long f : 8;
  unsigned long long g : 13;
  unsigned long long h : 21;
  unsigned long long i : 34;
  unsigned long long j : 55;
};

void fun(const struct B * b) { (void) b; }
