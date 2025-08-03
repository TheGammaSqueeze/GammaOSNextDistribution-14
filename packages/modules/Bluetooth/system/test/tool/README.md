

*** mock-C-ify

Any legacy code that is incapable of mocking may be a candidate for the
use of the mockCify tool to mock code in order to provide symbolic endpoints during the linkage
phase with additonal run time configurability to provide crafted testing solution.


** To use

<mockcify_tool> <namespace> < <source_code>

mockcify_tool: mockcify.pl
namespace: A brief single token namespace conventionally used from the path and filename
source_code: The C code being mocked.

```
e.g.
mockcify.pl btif_sock_rfc < btif/src/btif_sock_rfc.cc
```

NOTE: This tool is does not handle Cpp code well and is recommended to use gmock for those solutions.

Other Cpp language syntax, such as move semantics and lambdas also do not parse well and may need hand
crafting for those last few pieces.

