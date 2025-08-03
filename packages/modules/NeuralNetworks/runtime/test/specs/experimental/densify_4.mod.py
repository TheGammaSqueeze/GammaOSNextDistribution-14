# model
model = Model()
sparseData = Input("sparseData", "TENSOR_FLOAT32", "{5}")
traversalOrder = Parameter("traversalOrder", "TENSOR_INT32", "{3}", [0, 1, 2])
blockMap = Parameter("blockMap", "TENSOR_INT32", "{0}", [])
dimFormat = Parameter("dimFormat", "TENSOR_INT32", "{3}", [1, 0, 1])
dimensions = Parameter("dimensions", "TENSOR_INT32", "{3}", [3, 2, 2])
d0ArrSegments = Parameter("d0ArrSegments", "TENSOR_INT32", "{2}", [0, 2])
d0ArrIndices = Parameter("d0ArrIndices", "TENSOR_INT32", "{2}", [0, 2])
d1ArrSegments = Parameter("d1ArrSegments", "TENSOR_INT32", "{0}", [])
d1ArrIndices = Parameter("d1ArrIndices", "TENSOR_INT32", "{0}", [])
d2ArrSegments = Parameter("d2ArrSegments", "TENSOR_INT32", "{5}", [0, 1, 3, 4, 5])
d2ArrIndices = Parameter("d2ArrIndices", "TENSOR_INT32", "{5}", [0, 0, 1, 0, 1])
denseOut = Output("denseOut", "TENSOR_FLOAT32", "{3, 2, 2}")
model = model.Operation("DENSIFY", sparseData, traversalOrder, blockMap, dimFormat,
                        dimensions, d0ArrSegments, d0ArrIndices, d1ArrSegments,
                        d1ArrIndices, d2ArrSegments, d2ArrIndices).To(denseOut)

# Example 1. Input in operand 0,
input0 = {sparseData: # input 0
          [6.0, 9.0, 8.0, 5.0, 7.0]}

output0 = {denseOut: # output 0
           [6.0, 0.0, 9.0, 8.0, 0.0, 0.0, 0.0, 0.0, 5.0, 0.0, 0.0, 7.0]}

quant8_symm = DataTypeConverter().Identify({
    sparseData: ("TENSOR_QUANT8_SYMM", 4.375),
    denseOut: ("TENSOR_QUANT8_SYMM", 4.375)
})

quant8_asymm = DataTypeConverter().Identify({
    sparseData: ("TENSOR_QUANT8_ASYMM", 3.5, 5),
    denseOut: ("TENSOR_QUANT8_ASYMM", 3.5, 5)
})

quant8_asymm_signed = DataTypeConverter().Identify({
    sparseData: ("TENSOR_QUANT8_ASYMM_SIGNED", 2.0, -3),
    denseOut: ("TENSOR_QUANT8_ASYMM_SIGNED", 2.0, -3)
})

quant16_symm = DataTypeConverter().Identify({
    sparseData: ("TENSOR_QUANT16_SYMM", 3.25),
    denseOut: ("TENSOR_QUANT16_SYMM", 3.25)
})

quant16_asymm = DataTypeConverter().Identify({
    sparseData: ("TENSOR_QUANT16_ASYMM", 1.625, 10),
    denseOut: ("TENSOR_QUANT16_ASYMM", 1.625, 10)
})

# Instantiate an example
Example((input0, output0)).AddVariations("relaxed", "float16", "int32", quant8_symm,
                                        quant8_asymm, quant8_asymm_signed, quant16_symm,
                                        quant16_asymm)
