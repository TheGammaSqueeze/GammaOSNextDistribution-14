void tweak(int);

struct VirtualToNormal {
  void print();
} virtual_to_normal;
void VirtualToNormal::print() { tweak(0); }

struct NormalToVirtual {
  virtual void print();
} normal_to_virtual;
void NormalToVirtual::print() { tweak(1); }
