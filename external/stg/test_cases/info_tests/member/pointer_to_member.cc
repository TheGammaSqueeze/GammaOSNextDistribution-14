struct S;

// declare s0 as pointer to member of class S int
int S::*s0;
// declare s1 as pointer to pointer to member of class S int
int S::**s1;
// declare s2 as function returning pointer to member of class S int
int S::*s2();
int S::*s2() { return 0; }
// declare s3 as pointer to function returning pointer to member of class S int
int S::*(*s3)();
// declare s4 as array 7 of pointer to member of class S int
int S::*s4[7];
// declare s5 as pointer to member of class S pointer to int
int *S::*s5;
// declare s6 as pointer to member of class S pointer to function returning int
int (*S::*s6)();
// declare s7 as pointer to member of class S function returning int
int (S::*s7)();
// declare s8 as pointer to member of class S array 7 of int
int (S::*s8)[7];
// declare s9 as volatile pointer to member of class S const int
const int S::* volatile s9;
// declare s10 as function (pointer to member of class S int) returning int
int s10(int S::*);
int s10(int S::*) { return 0; }

struct X {
  void f(int);
  int a;
};
struct Y;

int X::* pmi = &X::a;
void (X::* pmf)(int) = &X::f;
double X::* pmd;
char Y::* pmc;

typedef struct { int t; } T;
auto pmy = &T::t;

namespace {
struct Z { int z; };
}
int Z::* pmz = &Z::z;
void pmz_fun() { (void) pmz; }

union U { int u; };
auto pmu = &U::u;

typedef const U CU;
auto pmcu = &CU::u;
