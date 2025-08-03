# model
model = Model()
sparseData = Input("sparseData", "TENSOR_BOOL8", "{8}")
traversalOrder = Parameter("traversalOrder", "TENSOR_INT32", "{3}", [0, 2, 1])
blockMap = Parameter("blockMap", "TENSOR_INT32", "{0}", [])
dimFormat = Parameter("dimFormat", "TENSOR_INT32", "{3}", [1, 1, 1])
dimensions = Parameter("dimensions", "TENSOR_INT32", "{3}", [3, 4, 2])
d0ArrSegments = Parameter("d0ArrSegments", "TENSOR_INT32", "{2}", [0, 2])
d0ArrIndices = Parameter("d0ArrIndices", "TENSOR_INT32", "{2}", [0, 2])
d1ArrSegments = Parameter("d1ArrSegments", "TENSOR_INT32", "{3}", [0, 2, 5])
d1ArrIndices = Parameter("d1ArrIndices", "TENSOR_INT32", "{5}", [0, 2, 0, 2, 3])
d2ArrSegments = Parameter("d2ArrSegments", "TENSOR_INT32", "{6}", [0, 2, 3, 4, 6, 8])
d2ArrIndices = Parameter("d2ArrIndices", "TENSOR_INT32", "{8}", [0, 1, 1, 1, 0, 1, 0, 1])
denseOut = Output("denseOut", "TENSOR_BOOL8", "{3, 2, 4}")
model = model.Operation("DENSIFY", sparseData, traversalOrder, blockMap, dimFormat,
                        dimensions, d0ArrSegments, d0ArrIndices, d1ArrSegments,
                        d1ArrIndices, d2ArrSegments, d2ArrIndices).To(denseOut)

# Example 1. Input in operand 0,
input0 = {sparseData: # input 0
          [1, 1, 1, 1, 1, 1, 1, 1]}

output0 = {denseOut: # output 0
           [1, 0, 0, 0, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0,
           0, 0, 0, 1, 1, 1, 0, 1, 1]}

# Instantiate an example
Example((input0, output0))
