struct leaf {
  int numbers[2];
};

struct node {
  struct leaf* ptr;
};

void foo(struct node *n) { (void) n; }
