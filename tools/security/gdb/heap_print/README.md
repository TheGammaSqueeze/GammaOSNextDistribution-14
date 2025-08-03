Script supports 2 custom commands:

1) watch_heap : sets break point at dynamic memory allocation and keeps track of it
2) print_ptr : prints the memory pointed by raw pointer in hex format.

                eg:
                (gdb) print_ptr malloc_ptr
                Type : int *
                Starting Address: 0x55555556aeb0
                Length : 40
                0x01 0x00 0x00 0x00 0x02 0x00 0x00 0x00 0x03 0x00 0x00 0x00
                0x04 0x00 0x00 0x00 0x05 0x00 0x00 0x00 0x06 0x00 0x00 0x00
                0x07 0x00 0x00 0x00 0x08 0x00 0x00 0x00 0x09 0x00 0x00 0x00
                0x0a 0x00 0x00 0x00

    If print_ptr is used after free/delete[], then it would print "No address mapping found!"


Tests:

To run the test

In the heap_print dir,

Compile :

g++ -O0 -g test/sample_heap_test.cc -o test/sample_heap_test.o

And then Run:

$ gdb
$ source test/test_heap_print_script.py


Future Goals:

To handle pointer offset, for eg, (gdb) print_ptr malloc_ptr + 3

To handle custom allacator, may be watch_heap command could take in arguements
and sets additional break points.




