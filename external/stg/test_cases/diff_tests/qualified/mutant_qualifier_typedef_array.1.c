typedef int A[7];
typedef A B;
typedef const B C;
typedef C D;
typedef volatile D E;
typedef E F;

struct S {
  const A c_a;
  volatile A v_a;
  const B c_b;
  volatile B v_b;
  const C c_c;
  volatile C v_c;
  const D c_d;
  volatile D v_d;
  const E c_e;
  volatile E v_e;
  const F c_f;
  volatile F v_f;
};

void fun(struct S * s) { (void) s; }
