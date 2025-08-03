typedef int A[7];
typedef A B;
typedef const B C;
typedef C D;
typedef volatile D E;
typedef E F;

struct S {
  A c_a;
  A v_a;
  B c_b;
  B v_b;
  C c_c;
  C v_c;
  D c_d;
  D v_d;
  E c_e;
  E v_e;
  F c_f;
  F v_f;
};

void fun(struct S * s) { (void) s; }
