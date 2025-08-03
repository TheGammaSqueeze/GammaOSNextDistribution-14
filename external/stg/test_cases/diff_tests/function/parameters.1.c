int f01(int b, int c) { return b + c + 1; }
int f02(int a, int c) { return a + c + 2; }
int f03(int a, int b) { return a + b + 3; }
int f04(int b, int a, int c) { return a + b + c + 4; }
int f05(int a, int c, int b) { return a + b + c + 5; }
int f06(int c, int b, int a) { return a + b + c + 6; }
int f07(int b, int c, int a) { return a + b + c + 7; }
int f08(int c, int a, int b) { return a + b + c + 8; }
int f09(int d, int a, int b, int c) { return a + b + c + d + 9; }
int f10(int a, int d, int b, int c) { return a + b + c + d + 10; }
int f11(int a, int b, int d, int c) { return a + b + c + d + 11; }
int f12(int a, int b, int c, int d) { return a + b + c + d + 12; }

struct S {
  int (*f01)(int b, int c);
  int (*f02)(int a, int c);
  int (*f03)(int a, int b);
  int (*f04)(int b, int a, int c);
  int (*f05)(int a, int c, int b);
  int (*f06)(int c, int b, int a);
  int (*f07)(int b, int c, int a);
  int (*f08)(int c, int a, int b);
  int (*f09)(int d, int a, int b, int c);
  int (*f10)(int a, int d, int b, int c);
  int (*f11)(int a, int b, int d, int c);
  int (*f12)(int a, int b, int c, int d);
};

struct S s;
