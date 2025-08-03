struct StaticToVirtual {
  static int print();
  int m;
};
int StaticToVirtual::print() { return 0; };

struct VirtualToStatic {
  virtual int print();
  int m;
};
int VirtualToStatic::print() { return 0; }

StaticToVirtual static_to_virtual;
VirtualToStatic virtual_to_static;
