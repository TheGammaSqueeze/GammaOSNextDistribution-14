struct Z {
  _Bool a_bool;
  char a_char;
  signed char a_signed_char;
  unsigned char an_unsigned_char;
  short a_short;
  short int a_short_int;
  signed short a_signed_short;
  signed short int a_signed_short_int;
  unsigned short an_unsigned_short;
  unsigned short int an_unsigned_short_int;
  int an_int;
  signed a_signed;
  signed int a_signed_int;
  unsigned an_unsigned;
  unsigned int an_unsigned_int;
  long a_long;
  long int a_long_int;
  signed long a_signed_long;
  signed long int a_signed_long_int;
  unsigned long an_unsigned_long;
  unsigned long int an_unsigned_long_int;
  long long a_long_long;
  long long int a_long_long_int;
  signed long long a_signed_long_long;
  signed long long int a_signed_long_long_int;
  unsigned long long an_unsigned_long_long;
  unsigned long long int an_unsigned_long_long_int;
  float a_float;
  double a_double;
  long double a_long_double;
};

struct Z var;
void fun(struct Z z) { (void)z; }
