struct Func {
  // change
  int change_return_type();

  // add or remove
  int add_parameter();
  int remove_parameter(int);
  int change_parameter_type(int);
  int rename_old();

  // no diff
  int change_parameter_name(int);

  int x;
} var;

int Func::change_return_type() { return 0; }
int Func::add_parameter() { return 1; }
int Func::remove_parameter(int) { return 2; }
int Func::change_parameter_type(int) { return 3; }
int Func::rename_old() { return 4; }
int Func::change_parameter_name(int) { return 5; }
