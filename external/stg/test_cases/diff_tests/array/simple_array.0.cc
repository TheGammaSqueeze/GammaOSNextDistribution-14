struct leaf {
  unsigned int numbers[2];
};

void foo(leaf * x) { x->numbers[1] = x->numbers[0]; }
