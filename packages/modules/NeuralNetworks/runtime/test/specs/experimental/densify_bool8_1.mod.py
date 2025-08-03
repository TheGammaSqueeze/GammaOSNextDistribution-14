# model
model = Model()
sparseData = Input("sparseData", "TENSOR_BOOL8", "{16}")
traversalOrder = Parameter("traversalOrder", "TENSOR_INT32", "{4}", [0, 1, 2, 3])
blockMap = Parameter("blockMap", "TENSOR_INT32", "{2}", [0, 1])
dimFormat = Parameter("dimFormat", "TENSOR_INT32", "{4}", [0, 0, 0, 0])
dimensions = Parameter("dimensions", "TENSOR_INT32", "{4}", [2, 2, 2, 2])
d0ArrSegments = Parameter("d0ArrSegments", "TENSOR_INT32", "{0}", [])
d0ArrIndices = Parameter("d0ArrIndices", "TENSOR_INT32", "{0}", [])
d1ArrSegments = Parameter("d1ArrSegments", "TENSOR_INT32", "{0}", [])
d1ArrIndices = Parameter("d1ArrIndices", "TENSOR_INT32", "{0}", [])
d2ArrSegments = Parameter("d2ArrSegments", "TENSOR_INT32", "{0}", [])
d2ArrIndices = Parameter("d2ArrIndices", "TENSOR_INT32", "{0}", [])
d3ArrSegments = Parameter("d3ArrSegments", "TENSOR_INT32", "{0}", [])
d3ArrIndices = Parameter("d3ArrIndices", "TENSOR_INT32", "{0}", [])
denseOut = Output("denseOut", "TENSOR_BOOL8", "{4, 4}")
model = model.Operation("DENSIFY", sparseData, traversalOrder, blockMap,
                        dimFormat, dimensions, d0ArrSegments, d0ArrIndices, d1ArrSegments,
                        d1ArrIndices, d2ArrSegments, d2ArrIndices, d3ArrSegments,
                        d3ArrIndices).To(denseOut)

# Example 1. Input in operand 0,
input0 = {sparseData: # input 0
          [1, 0, 0, 1, 1, 1, 0, 0, 0, 0, 0, 0, 1, 0, 0, 1]}

output0 = {denseOut: # output 0
           [1, 0, 1, 1, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]}

# Instantiate an example
Example((input0, output0))
