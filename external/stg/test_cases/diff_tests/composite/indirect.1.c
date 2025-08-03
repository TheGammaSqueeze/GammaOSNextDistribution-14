struct leaf {
  int numbers[3];
};

struct node {
  struct leaf* ptr;
};

void foo(struct node *n) { (void) n; }
