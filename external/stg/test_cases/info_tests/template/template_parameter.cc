template<typename A1> struct T1 {
  A1 a;
  int b;
};
template<typename B1, typename B2> struct T2 {
  B1 b;
  B2 c;
  int d;
};
template<int C1, typename C2> struct T3 {
  int e[C1];
  C2 f;
  int g;
};

template<template<typename> typename P1> union T4{
  P1<int> h;
  int i;
};
template<template<typename, typename> typename P2> union T5{
  P2<int, int> j;
  int k;
};
template<template<auto, typename> typename P3> union T6{
  P3<17, int> l;
  int m;
};

template<template<template<auto, typename> typename> typename P4> struct T7 {
  P4<T3> n;
  int o;
};

T4<T1> v1;
T5<T2> v2;
T6<T3> v3;
T7<T6> v4;
