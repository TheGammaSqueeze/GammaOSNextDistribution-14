struct Base {};

struct A : Base {};

struct B : Base {};

struct VirtualToNon : virtual A, virtual B {};

struct NonToVirtual : A, B {};

VirtualToNon virtual_to_non;
NonToVirtual non_to_virtual;
