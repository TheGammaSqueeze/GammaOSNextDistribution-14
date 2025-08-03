struct Base {};

struct A : Base {};

struct B : Base {};

struct VirtualToNon : A, B {};

struct NonToVirtual : virtual A, virtual B {};

VirtualToNon virtual_to_non;
NonToVirtual non_to_virtual;
