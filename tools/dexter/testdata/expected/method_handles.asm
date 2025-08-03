
method ExampleJavaJniFuzzer.<init>():void
{
	.src "ExampleJavaJniFuzzer.java"
	.line 18
	.line 18
	    0| invoke-direct {v1}, java.lang.Object.<init>():void
	    3| const/4 v0, #+0 (0x00000000 | 0.00000)
	    4| invoke-static {v0}, com.code_intelligence.jazzer.runtime.CoverageMap.recordCoverage(int):void
	    7| return-void
}

method ExampleJavaJniFuzzer.fuzzerTestOneInput(com.code_intelligence.jazzer.api.FuzzedDataProvider):void
{
	.params "data"
	.src "ExampleJavaJniFuzzer.java"
	.line 21
	.line 21
	    0| invoke-interface {v5}, com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeRemainingAsAsciiString():java.lang.String
	    3| move-result-object v0
	    4| const/4 v1, #+1 (0x00000001 | 1.40130e-45)
	.try_begin_1
	    5| invoke-static {v1}, com.code_intelligence.jazzer.runtime.CoverageMap.recordCoverage(int):void
	.try_end_1
	  catch(java.lang.ClassNotFoundException) : Label_2
	  catch(java.lang.InstantiationException) : Label_2
	  catch(java.lang.IllegalAccessException) : Label_2
	.try_begin_2
	.line 23
	.local v0, "input", java.lang.String
	    8| const-class v1, ExampleJavaJniFuzzer
	   10| invoke-virtual {v1}, java.lang.Class.getClassLoader():java.lang.ClassLoader
	   13| move-result-object v1
	   14| filled-new-array {v0}, java.lang.Object[]
	   17| move-result-object v2
	   18| const-method-handle v3, loadClass
	   20| const v4, #+1696909080 (0x6524c718 | 4.86338e+22)
	   23| invoke-static {v3,v1,v2,v4}, com.code_intelligence.jazzer.sanitizers.ReflectiveCall.loadClassHook(java.lang.invoke.MethodHandle, java.lang.Object, java.lang.Object[], int):void
	   26| const/4 v3, #+0 (0x00000000 | 0.00000)
	   27| aget-object v2, v2, v3
	   29| check-cast v2, java.lang.String
	   31| invoke-virtual {v1,v2}, java.lang.ClassLoader.loadClass(java.lang.String):java.lang.Class
	   34| move-result-object v1
	   35| invoke-virtual {v1}, java.lang.Class.newInstance():java.lang.Object
	.try_end_2
	  catch(java.lang.ClassNotFoundException) : Label_1
	  catch(java.lang.InstantiationException) : Label_1
	  catch(java.lang.IllegalAccessException) : Label_1
	.line 25
	   38| const/4 v1, #+2 (0x00000002 | 2.80260e-45)
	   39| invoke-static {v1}, com.code_intelligence.jazzer.runtime.CoverageMap.recordCoverage(int):void
	   42| goto Label_4
Label_1:
	.line 24
	   43| move-exception v1
	   44| goto Label_3
Label_2:
	.end_local v0
	   45| move-exception v1
Label_3:
	.restart_local v0
	   46| const/4 v1, #+3 (0x00000003 | 4.20390e-45)
	   47| invoke-static {v1}, com.code_intelligence.jazzer.runtime.CoverageMap.recordCoverage(int):void
Label_4:
	.line 26
	   50| const/4 v1, #+4 (0x00000004 | 5.60519e-45)
	   51| invoke-static {v1}, com.code_intelligence.jazzer.runtime.CoverageMap.recordCoverage(int):void
	   54| return-void
}

method com.code_intelligence.jazzer.api.AutofuzzConstructionException.<init>():void
{
	.src "AutofuzzConstructionException.java"
	.line 24
	.line 24
	    0| invoke-direct {v0}, java.lang.RuntimeException.<init>():void
	.line 25
	    3| return-void
}

method com.code_intelligence.jazzer.api.AutofuzzConstructionException.<init>(java.lang.String):void
{
	.params "message"
	.src "AutofuzzConstructionException.java"
	.line 27
	.line 27
	    0| invoke-direct {v0,v1}, java.lang.RuntimeException.<init>(java.lang.String):void
	.line 28
	    3| return-void
}

method com.code_intelligence.jazzer.api.AutofuzzConstructionException.<init>(java.lang.Throwable):void
{
	.params "cause"
	.src "AutofuzzConstructionException.java"
	.line 30
	.line 30
	    0| invoke-direct {v0,v1}, java.lang.RuntimeException.<init>(java.lang.Throwable):void
	.line 31
	    3| return-void
}

method com.code_intelligence.jazzer.api.AutofuzzInvocationException.<init>(java.lang.Throwable):void
{
	.params "cause"
	.src "AutofuzzInvocationException.java"
	.line 24
	.line 24
	    0| invoke-direct {v0,v1}, java.lang.RuntimeException.<init>(java.lang.Throwable):void
	.line 25
	    3| return-void
}

method com.code_intelligence.jazzer.api.Consumer1.accept(java.lang.Object):void
{
}

method com.code_intelligence.jazzer.api.Consumer2.accept(java.lang.Object, java.lang.Object):void
{
}

method com.code_intelligence.jazzer.api.Consumer3.accept(java.lang.Object, java.lang.Object, java.lang.Object):void
{
}

method com.code_intelligence.jazzer.api.Consumer4.accept(java.lang.Object, java.lang.Object, java.lang.Object, java.lang.Object):void
{
}

method com.code_intelligence.jazzer.api.Consumer5.accept(java.lang.Object, java.lang.Object, java.lang.Object, java.lang.Object, java.lang.Object):void
{
}

method com.code_intelligence.jazzer.api.Function1.apply(java.lang.Object):java.lang.Object
{
}

method com.code_intelligence.jazzer.api.Function2.apply(java.lang.Object, java.lang.Object):java.lang.Object
{
}

method com.code_intelligence.jazzer.api.Function3.apply(java.lang.Object, java.lang.Object, java.lang.Object):java.lang.Object
{
}

method com.code_intelligence.jazzer.api.Function4.apply(java.lang.Object, java.lang.Object, java.lang.Object, java.lang.Object):java.lang.Object
{
}

method com.code_intelligence.jazzer.api.Function5.apply(java.lang.Object, java.lang.Object, java.lang.Object, java.lang.Object, java.lang.Object):java.lang.Object
{
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeAsciiString(int):java.lang.String
{
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeBoolean():boolean
{
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeBooleans(int):boolean[]
{
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeByte():byte
{
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeByte(byte, byte):byte
{
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeBytes(int):byte[]
{
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeChar():char
{
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeChar(char, char):char
{
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeCharNoSurrogates():char
{
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeDouble():double
{
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeFloat():float
{
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeInt():int
{
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeInt(int, int):int
{
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeInts(int):int[]
{
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeLong():long
{
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeLong(long, long):long
{
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeLongs(int):long[]
{
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeProbabilityDouble():double
{
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeProbabilityFloat():float
{
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeRegularDouble():double
{
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeRegularDouble(double, double):double
{
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeRegularFloat():float
{
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeRegularFloat(float, float):float
{
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeRemainingAsAsciiString():java.lang.String
{
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeRemainingAsBytes():byte[]
{
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeRemainingAsString():java.lang.String
{
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeShort():short
{
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeShort(short, short):short
{
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeShorts(int):short[]
{
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeString(int):java.lang.String
{
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.pickValue(byte[]):byte
{
	.params "array"
	.src "FuzzedDataProvider.java"
	.line 333
	.line 333
	    0| array-length v0, v3
	    1| add-int/lit8 v0, v0, #-1 (0xffffffff | NaN)
	    3| const/4 v1, #+0 (0x00000000 | 0.00000)
	    4| invoke-interface {v2,v1,v0}, com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeInt(int, int):int
	    7| move-result v0
	    8| aget-byte v0, v3, v0
	   10| return v0
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.pickValue(char[]):char
{
	.params "array"
	.src "FuzzedDataProvider.java"
	.line 399
	.line 399
	    0| array-length v0, v3
	    1| add-int/lit8 v0, v0, #-1 (0xffffffff | NaN)
	    3| const/4 v1, #+0 (0x00000000 | 0.00000)
	    4| invoke-interface {v2,v1,v0}, com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeInt(int, int):int
	    7| move-result v0
	    8| aget-char v0, v3, v0
	   10| return v0
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.pickValue(double[]):double
{
	.params "array"
	.src "FuzzedDataProvider.java"
	.line 377
	.line 377
	    0| array-length v0, v3
	    1| add-int/lit8 v0, v0, #-1 (0xffffffff | NaN)
	    3| const/4 v1, #+0 (0x00000000 | 0.00000)
	    4| invoke-interface {v2,v1,v0}, com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeInt(int, int):int
	    7| move-result v0
	    8| aget-wide v0:v1, v3, v0
	   10| return-wide v0:v1
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.pickValue(float[]):float
{
	.params "array"
	.src "FuzzedDataProvider.java"
	.line 388
	.line 388
	    0| array-length v0, v3
	    1| add-int/lit8 v0, v0, #-1 (0xffffffff | NaN)
	    3| const/4 v1, #+0 (0x00000000 | 0.00000)
	    4| invoke-interface {v2,v1,v0}, com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeInt(int, int):int
	    7| move-result v0
	    8| aget v0, v3, v0
	   10| return v0
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.pickValue(int[]):int
{
	.params "array"
	.src "FuzzedDataProvider.java"
	.line 355
	.line 355
	    0| array-length v0, v3
	    1| add-int/lit8 v0, v0, #-1 (0xffffffff | NaN)
	    3| const/4 v1, #+0 (0x00000000 | 0.00000)
	    4| invoke-interface {v2,v1,v0}, com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeInt(int, int):int
	    7| move-result v0
	    8| aget v0, v3, v0
	   10| return v0
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.pickValue(long[]):long
{
	.params "array"
	.src "FuzzedDataProvider.java"
	.line 366
	.line 366
	    0| array-length v0, v3
	    1| add-int/lit8 v0, v0, #-1 (0xffffffff | NaN)
	    3| const/4 v1, #+0 (0x00000000 | 0.00000)
	    4| invoke-interface {v2,v1,v0}, com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeInt(int, int):int
	    7| move-result v0
	    8| aget-wide v0:v1, v3, v0
	   10| return-wide v0:v1
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.pickValue(java.util.Collection):java.lang.Object
{
	.params "?"
	.src "FuzzedDataProvider.java"
	.line 291
	.line 291
	.local_ex v5, "collection", java.util.Collection, "Ljava/util/Collection<TT;>;"
	    0| invoke-interface {v5}, java.util.Collection.size():int
	    3| move-result v0
	.line 292
	.local v0, "size", int
	    4| if-eqz v0, Label_2
	.line 295
	    6| instance-of v1, v5, java.util.List
	    8| if-eqz v1, Label_1
	.line 296
	   10| move-object v1, v5
	   11| check-cast v1, java.util.List
	   13| add-int/lit8 v2, v0, #-1 (0xffffffff | NaN)
	   15| const/4 v3, #+0 (0x00000000 | 0.00000)
	   16| invoke-interface {v4,v3,v2}, com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeInt(int, int):int
	   19| move-result v2
	   20| invoke-interface {v1,v2}, java.util.List.get(int):java.lang.Object
	   23| move-result-object v1
	   24| return-object v1
Label_1:
	.line 298
	   25| invoke-interface {v5}, java.util.Collection.toArray():java.lang.Object[]
	   28| move-result-object v1
	   29| invoke-interface {v4,v1}, com.code_intelligence.jazzer.api.FuzzedDataProvider.pickValue(java.lang.Object[]):java.lang.Object
	   32| move-result-object v1
	   33| return-object v1
Label_2:
	.line 293
	   34| new-instance v1, java.lang.IllegalArgumentException
	   36| const-string v2, "collection is empty"
	   38| invoke-direct {v1,v2}, java.lang.IllegalArgumentException.<init>(java.lang.String):void
	   41| throw v1
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.pickValue(java.lang.Object[]):java.lang.Object
{
	.params "?"
	.src "FuzzedDataProvider.java"
	.line 311
	.line 311
	.local_ex v3, "array", java.lang.Object[], "[TT;"
	    0| array-length v0, v3
	    1| add-int/lit8 v0, v0, #-1 (0xffffffff | NaN)
	    3| const/4 v1, #+0 (0x00000000 | 0.00000)
	    4| invoke-interface {v2,v1,v0}, com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeInt(int, int):int
	    7| move-result v0
	    8| aget-object v0, v3, v0
	   10| return-object v0
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.pickValue(short[]):short
{
	.params "array"
	.src "FuzzedDataProvider.java"
	.line 344
	.line 344
	    0| array-length v0, v3
	    1| add-int/lit8 v0, v0, #-1 (0xffffffff | NaN)
	    3| const/4 v1, #+0 (0x00000000 | 0.00000)
	    4| invoke-interface {v2,v1,v0}, com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeInt(int, int):int
	    7| move-result v0
	    8| aget-short v0, v3, v0
	   10| return v0
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.pickValue(boolean[]):boolean
{
	.params "array"
	.src "FuzzedDataProvider.java"
	.line 322
	.line 322
	    0| array-length v0, v3
	    1| add-int/lit8 v0, v0, #-1 (0xffffffff | NaN)
	    3| const/4 v1, #+0 (0x00000000 | 0.00000)
	    4| invoke-interface {v2,v1,v0}, com.code_intelligence.jazzer.api.FuzzedDataProvider.consumeInt(int, int):int
	    7| move-result v0
	    8| aget-boolean v0, v3, v0
	   10| return v0
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.pickValues(java.util.Collection, int):java.util.List
{
	.params "?", "numOfElements"
	.src "FuzzedDataProvider.java"
	.line 413
	.line 413
	.local_ex v6, "collection", java.util.Collection, "Ljava/util/Collection<TT;>;"
	    0| invoke-interface {v6}, java.util.Collection.size():int
	    3| move-result v0
	.line 414
	.local v0, "size", int
	    4| if-eqz v0, Label_4
	.line 417
	    6| invoke-interface {v6}, java.util.Collection.size():int
	    9| move-result v1
	   10| if-gt v7, v1, Label_3
	.line 421
	   12| new-instance v1, java.util.ArrayList
	   14| invoke-direct {v1,v6}, java.util.ArrayList.<init>(java.util.Collection):void
	.line 422
	.local_ex v1, "remainingElements", java.util.List, "Ljava/util/List<TT;>;"
	   17| new-instance v2, java.util.ArrayList
	   19| invoke-direct {v2}, java.util.ArrayList.<init>():void
	.line 423
	.local_ex v2, "pickedElements", java.util.List, "Ljava/util/List<TT;>;"
	   22| const/4 v3, #+0 (0x00000000 | 0.00000)
Label_1:
	.local v3, "i", int
	   23| if-ge v3, v7, Label_2
	.line 424
	   25| invoke-interface {v5,v1}, com.code_intelligence.jazzer.api.FuzzedDataProvider.pickValue(java.util.Collection):java.lang.Object
	   28| move-result-object v4
	.line 425
	.local_ex v4, "element", java.lang.Object, "TT;"
	   29| invoke-interface {v2,v4}, java.util.List.add(java.lang.Object):boolean
	.line 426
	   32| invoke-interface {v1,v4}, java.util.List.remove(java.lang.Object):boolean
	.line 423
	.end_local v4
	   35| add-int/lit8 v3, v3, #+1 (0x00000001 | 1.40130e-45)
	   37| goto Label_1
Label_2:
	.line 428
	.end_local v3
	   38| return-object v2
Label_3:
	.line 418
	.end_local v1
	.end_local v2
	   39| new-instance v1, java.lang.IllegalArgumentException
	   41| const-string v2, "numOfElements exceeds collection.size()"
	   43| invoke-direct {v1,v2}, java.lang.IllegalArgumentException.<init>(java.lang.String):void
	   46| throw v1
Label_4:
	.line 415
	   47| new-instance v1, java.lang.IllegalArgumentException
	   49| const-string v2, "collection is empty"
	   51| invoke-direct {v1,v2}, java.lang.IllegalArgumentException.<init>(java.lang.String):void
	   54| throw v1
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.pickValues(java.lang.Object[], int):java.util.List
{
	.params "?", "numOfElements"
	.src "FuzzedDataProvider.java"
	.line 442
	.line 442
	.local_ex v2, "array", java.lang.Object[], "[TT;"
	    0| invoke-static {v2}, java.util.Arrays.asList(java.lang.Object[]):java.util.List
	    3| move-result-object v0
	    4| invoke-interface {v1,v0,v3}, com.code_intelligence.jazzer.api.FuzzedDataProvider.pickValues(java.util.Collection, int):java.util.List
	    7| move-result-object v0
	    8| return-object v0
}

method com.code_intelligence.jazzer.api.FuzzedDataProvider.remainingBytes():int
{
}

method com.code_intelligence.jazzer.api.FuzzerSecurityIssueCritical.<init>():void
{
	.src "FuzzerSecurityIssueCritical.java"
	.line 26
	.line 26
	    0| invoke-direct {v0}, java.lang.RuntimeException.<init>():void
	    3| return-void
}

method com.code_intelligence.jazzer.api.FuzzerSecurityIssueCritical.<init>(java.lang.String):void
{
	.params "message"
	.src "FuzzerSecurityIssueCritical.java"
	.line 29
	.line 29
	    0| invoke-direct {v0,v1}, java.lang.RuntimeException.<init>(java.lang.String):void
	.line 30
	    3| return-void
}

method com.code_intelligence.jazzer.api.FuzzerSecurityIssueCritical.<init>(java.lang.String, java.lang.Throwable):void
{
	.params "message", "cause"
	.src "FuzzerSecurityIssueCritical.java"
	.line 33
	.line 33
	    0| invoke-direct {v0,v1,v2}, java.lang.RuntimeException.<init>(java.lang.String, java.lang.Throwable):void
	.line 34
	    3| return-void
}

method com.code_intelligence.jazzer.api.FuzzerSecurityIssueCritical.<init>(java.lang.Throwable):void
{
	.params "cause"
	.src "FuzzerSecurityIssueCritical.java"
	.line 37
	.line 37
	    0| invoke-direct {v0,v1}, java.lang.RuntimeException.<init>(java.lang.Throwable):void
	.line 38
	    3| return-void
}

method com.code_intelligence.jazzer.api.FuzzerSecurityIssueHigh.<init>():void
{
	.src "FuzzerSecurityIssueHigh.java"
	.line 26
	.line 26
	    0| invoke-direct {v0}, java.lang.RuntimeException.<init>():void
	    3| return-void
}

method com.code_intelligence.jazzer.api.FuzzerSecurityIssueHigh.<init>(java.lang.String):void
{
	.params "message"
	.src "FuzzerSecurityIssueHigh.java"
	.line 29
	.line 29
	    0| invoke-direct {v0,v1}, java.lang.RuntimeException.<init>(java.lang.String):void
	.line 30
	    3| return-void
}

method com.code_intelligence.jazzer.api.FuzzerSecurityIssueHigh.<init>(java.lang.String, java.lang.Throwable):void
{
	.params "message", "cause"
	.src "FuzzerSecurityIssueHigh.java"
	.line 33
	.line 33
	    0| invoke-direct {v0,v1,v2}, java.lang.RuntimeException.<init>(java.lang.String, java.lang.Throwable):void
	.line 34
	    3| return-void
}

method com.code_intelligence.jazzer.api.FuzzerSecurityIssueHigh.<init>(java.lang.Throwable):void
{
	.params "cause"
	.src "FuzzerSecurityIssueHigh.java"
	.line 37
	.line 37
	    0| invoke-direct {v0,v1}, java.lang.RuntimeException.<init>(java.lang.Throwable):void
	.line 38
	    3| return-void
}

method com.code_intelligence.jazzer.api.FuzzerSecurityIssueLow.<init>():void
{
	.src "FuzzerSecurityIssueLow.java"
	.line 26
	.line 26
	    0| invoke-direct {v0}, java.lang.RuntimeException.<init>():void
	    3| return-void
}

method com.code_intelligence.jazzer.api.FuzzerSecurityIssueLow.<init>(java.lang.String):void
{
	.params "message"
	.src "FuzzerSecurityIssueLow.java"
	.line 29
	.line 29
	    0| invoke-direct {v0,v1}, java.lang.RuntimeException.<init>(java.lang.String):void
	.line 30
	    3| return-void
}

method com.code_intelligence.jazzer.api.FuzzerSecurityIssueLow.<init>(java.lang.String, java.lang.Throwable):void
{
	.params "message", "cause"
	.src "FuzzerSecurityIssueLow.java"
	.line 33
	.line 33
	    0| invoke-direct {v0,v1,v2}, java.lang.RuntimeException.<init>(java.lang.String, java.lang.Throwable):void
	.line 34
	    3| return-void
}

method com.code_intelligence.jazzer.api.FuzzerSecurityIssueLow.<init>(java.lang.Throwable):void
{
	.params "cause"
	.src "FuzzerSecurityIssueLow.java"
	.line 37
	.line 37
	    0| invoke-direct {v0,v1}, java.lang.RuntimeException.<init>(java.lang.Throwable):void
	.line 38
	    3| return-void
}

method com.code_intelligence.jazzer.api.FuzzerSecurityIssueMedium.<init>():void
{
	.src "FuzzerSecurityIssueMedium.java"
	.line 26
	.line 26
	    0| invoke-direct {v0}, java.lang.RuntimeException.<init>():void
	    3| return-void
}

method com.code_intelligence.jazzer.api.FuzzerSecurityIssueMedium.<init>(java.lang.String):void
{
	.params "message"
	.src "FuzzerSecurityIssueMedium.java"
	.line 29
	.line 29
	    0| invoke-direct {v0,v1}, java.lang.RuntimeException.<init>(java.lang.String):void
	.line 30
	    3| return-void
}

method com.code_intelligence.jazzer.api.FuzzerSecurityIssueMedium.<init>(java.lang.String, java.lang.Throwable):void
{
	.params "message", "cause"
	.src "FuzzerSecurityIssueMedium.java"
	.line 33
	.line 33
	    0| invoke-direct {v0,v1,v2}, java.lang.RuntimeException.<init>(java.lang.String, java.lang.Throwable):void
	.line 34
	    3| return-void
}

method com.code_intelligence.jazzer.api.FuzzerSecurityIssueMedium.<init>(java.lang.Throwable):void
{
	.params "cause"
	.src "FuzzerSecurityIssueMedium.java"
	.line 37
	.line 37
	    0| invoke-direct {v0,v1}, java.lang.RuntimeException.<init>(java.lang.Throwable):void
	.line 38
	    3| return-void
}

method com.code_intelligence.jazzer.api.HookType.$values():com.code_intelligence.jazzer.api.HookType[]
{
	.src "HookType.java"
	.line 21
	.line 21
	    0| sget-object v0, com.code_intelligence.jazzer.api.HookType.BEFORE
	    2| sget-object v1, com.code_intelligence.jazzer.api.HookType.REPLACE
	    4| sget-object v2, com.code_intelligence.jazzer.api.HookType.AFTER
	    6| filled-new-array {v0,v1,v2}, com.code_intelligence.jazzer.api.HookType[]
	    9| move-result-object v0
	   10| return-object v0
}

method com.code_intelligence.jazzer.api.HookType.<clinit>():void
{
	.src "HookType.java"
	.line 22
	.line 22
	    0| new-instance v0, com.code_intelligence.jazzer.api.HookType
	    2| const-string v1, "BEFORE"
	    4| const/4 v2, #+0 (0x00000000 | 0.00000)
	    5| invoke-direct {v0,v1,v2}, com.code_intelligence.jazzer.api.HookType.<init>(java.lang.String, int):void
	    8| sput-object v0, com.code_intelligence.jazzer.api.HookType.BEFORE
	.line 23
	   10| new-instance v0, com.code_intelligence.jazzer.api.HookType
	   12| const-string v1, "REPLACE"
	   14| const/4 v2, #+1 (0x00000001 | 1.40130e-45)
	   15| invoke-direct {v0,v1,v2}, com.code_intelligence.jazzer.api.HookType.<init>(java.lang.String, int):void
	   18| sput-object v0, com.code_intelligence.jazzer.api.HookType.REPLACE
	.line 24
	   20| new-instance v0, com.code_intelligence.jazzer.api.HookType
	   22| const-string v1, "AFTER"
	   24| const/4 v2, #+2 (0x00000002 | 2.80260e-45)
	   25| invoke-direct {v0,v1,v2}, com.code_intelligence.jazzer.api.HookType.<init>(java.lang.String, int):void
	   28| sput-object v0, com.code_intelligence.jazzer.api.HookType.AFTER
	.line 21
	   30| invoke-static {}, com.code_intelligence.jazzer.api.HookType.$values():com.code_intelligence.jazzer.api.HookType[]
	   33| move-result-object v0
	   34| sput-object v0, com.code_intelligence.jazzer.api.HookType.$VALUES
	   36| return-void
}

method com.code_intelligence.jazzer.api.HookType.<init>(java.lang.String, int):void
{
	.params "?", "?"
	.src "HookType.java"
	.line 21
	.line 21
	    0| invoke-direct {v0,v1,v2}, java.lang.Enum.<init>(java.lang.String, int):void
	    3| return-void
}

method com.code_intelligence.jazzer.api.HookType.valueOf(java.lang.String):com.code_intelligence.jazzer.api.HookType
{
	.params "name"
	.src "HookType.java"
	.line 21
	.line 21
	    0| const-class v0, com.code_intelligence.jazzer.api.HookType
	    2| invoke-static {v0,v1}, java.lang.Enum.valueOf(java.lang.Class, java.lang.String):java.lang.Enum
	    5| move-result-object v0
	    6| check-cast v0, com.code_intelligence.jazzer.api.HookType
	    8| return-object v0
}

method com.code_intelligence.jazzer.api.HookType.values():com.code_intelligence.jazzer.api.HookType[]
{
	.src "HookType.java"
	.line 21
	.line 21
	    0| sget-object v0, com.code_intelligence.jazzer.api.HookType.$VALUES
	    2| invoke-virtual {v0}, com.code_intelligence.jazzer.api.HookType[].clone():java.lang.Object
	    5| move-result-object v0
	    6| check-cast v0, com.code_intelligence.jazzer.api.HookType[]
	    8| return-object v0
}

method com.code_intelligence.jazzer.api.Jazzer.<clinit>():void
{
	.src "Jazzer.java"
	.line 38
	.line 38
	    0| const-class v0, byte[]
	    2| const-string v1, "autofuzz"
	    4| invoke-static {}, com.code_intelligence.jazzer.api.Jazzer.getLibFuzzerSeed():int
	    7| move-result v2
	    8| sput v2, com.code_intelligence.jazzer.api.Jazzer.SEED
	.line 62
	   10| const/4 v2, #+0 (0x00000000 | 0.00000)
	.line 63
	.local_ex v2, "jazzerInternal", java.lang.Class, "Ljava/lang/Class<*>;"
	   11| const/4 v3, #+0 (0x00000000 | 0.00000)
	.line 64
	.local v3, "onFuzzTargetReady", java.lang.invoke.MethodHandle
	   12| const/4 v4, #+0 (0x00000000 | 0.00000)
	.line 65
	.local v4, "traceStrcmp", java.lang.invoke.MethodHandle
	   13| const/4 v5, #+0 (0x00000000 | 0.00000)
	.line 66
	.local v5, "traceStrstr", java.lang.invoke.MethodHandle
	   14| const/4 v6, #+0 (0x00000000 | 0.00000)
	.line 67
	.local v6, "traceMemcmp", java.lang.invoke.MethodHandle
	   15| const/4 v7, #+0 (0x00000000 | 0.00000)
	.line 68
	.local v7, "tracePcIndir", java.lang.invoke.MethodHandle
	   16| const/4 v8, #+0 (0x00000000 | 0.00000)
	.line 69
	.local v8, "consume", java.lang.invoke.MethodHandle
	   17| const/4 v9, #+0 (0x00000000 | 0.00000)
	.line 70
	.local v9, "autofuzzFunction1", java.lang.invoke.MethodHandle
	   18| const/4 v10, #+0 (0x00000000 | 0.00000)
	.line 71
	.local v10, "autofuzzFunction2", java.lang.invoke.MethodHandle
	   19| const/4 v11, #+0 (0x00000000 | 0.00000)
	.line 72
	.local v11, "autofuzzFunction3", java.lang.invoke.MethodHandle
	   20| const/4 v12, #+0 (0x00000000 | 0.00000)
	.line 73
	.local v12, "autofuzzFunction4", java.lang.invoke.MethodHandle
	   21| const/4 v13, #+0 (0x00000000 | 0.00000)
	.line 74
	.local v13, "autofuzzFunction5", java.lang.invoke.MethodHandle
	   22| const/4 v14, #+0 (0x00000000 | 0.00000)
	.line 75
	.local v14, "autofuzzConsumer1", java.lang.invoke.MethodHandle
	   23| const/4 v15, #+0 (0x00000000 | 0.00000)
	.line 76
	.local v15, "autofuzzConsumer2", java.lang.invoke.MethodHandle
	   24| const/16 v16, #+0 (0x00000000 | 0.00000)
	.line 77
	.local v16, "autofuzzConsumer3", java.lang.invoke.MethodHandle
	   26| const/16 v17, #+0 (0x00000000 | 0.00000)
	.line 78
	.local v17, "autofuzzConsumer4", java.lang.invoke.MethodHandle
	   28| const/16 v18, #+0 (0x00000000 | 0.00000)
	.line 80
	.local v18, "autofuzzConsumer5", java.lang.invoke.MethodHandle
	   30| move-object/from16 v19, v2
	.try_begin_1
	.end_local v2
	.local_ex v19, "jazzerInternal", java.lang.Class, "Ljava/lang/Class<*>;"
	   32| const-string v20, "com.code_intelligence.jazzer.runtime.JazzerInternal"
	   34| invoke-static/range {v20..v20}, java.lang.Class.forName(java.lang.String):java.lang.Class
	   37| move-result-object v20
	.try_end_1
	  catch(java.lang.ClassNotFoundException) : Label_25
	  catch(java.lang.NoSuchMethodException) : Label_23
	  catch(java.lang.IllegalAccessException) : Label_23
	   38| move-object/from16 v19, v20
	.try_begin_2
	.line 81
	   40| sget-object v2, java.lang.Void.TYPE
	.try_end_2
	  catch(java.lang.ClassNotFoundException) : Label_22
	  catch(java.lang.NoSuchMethodException) : Label_21
	  catch(java.lang.IllegalAccessException) : Label_21
	   42| move-object/from16 v21, v3
	.try_begin_3
	.end_local v3
	.local v21, "onFuzzTargetReady", java.lang.invoke.MethodHandle
	   44| const-class v3, java.lang.Runnable
	   46| invoke-static {v2,v3}, java.lang.invoke.MethodType.methodType(java.lang.Class, java.lang.Class):java.lang.invoke.MethodType
	   49| move-result-object v2
	.line 82
	.local v2, "onFuzzTargetReadyType", java.lang.invoke.MethodType
	   50| invoke-static {}, java.lang.invoke.MethodHandles.publicLookup():java.lang.invoke.MethodHandles$Lookup
	   53| move-result-object v3
	.try_end_3
	  catch(java.lang.ClassNotFoundException) : Label_20
	  catch(java.lang.NoSuchMethodException) : Label_19
	  catch(java.lang.IllegalAccessException) : Label_19
	   54| move-object/from16 v22, v4
	.try_begin_4
	.end_local v4
	.local v22, "traceStrcmp", java.lang.invoke.MethodHandle
	   56| const-string v4, "registerOnFuzzTargetReadyCallback"
	.try_end_4
	  catch(java.lang.ClassNotFoundException) : Label_18
	  catch(java.lang.NoSuchMethodException) : Label_17
	  catch(java.lang.IllegalAccessException) : Label_17
	   58| move-object/from16 v23, v5
	   60| move-object/from16 v5, v19
	.try_begin_5
	.end_local v19
	.local_ex v5, "jazzerInternal", java.lang.Class, "Ljava/lang/Class<*>;"
	.local v23, "traceStrstr", java.lang.invoke.MethodHandle
	   62| invoke-virtual {v3,v5,v4,v2}, java.lang.invoke.MethodHandles$Lookup.findStatic(java.lang.Class, java.lang.String, java.lang.invoke.MethodType):java.lang.invoke.MethodHandle
	   65| move-result-object v3
	.try_end_5
	  catch(java.lang.ClassNotFoundException) : Label_16
	  catch(java.lang.NoSuchMethodException) : Label_15
	  catch(java.lang.IllegalAccessException) : Label_15
	.try_begin_6
	.line 84
	.end_local v21
	.restart_local v3
	   66| const-string v4, "com.code_intelligence.jazzer.runtime.TraceDataFlowNativeCallbacks"
	.line 85
	   68| invoke-static {v4}, java.lang.Class.forName(java.lang.String):java.lang.Class
	   71| move-result-object v4
	.line 88
	.local_ex v4, "traceDataFlowNativeCallbacks", java.lang.Class, "Ljava/lang/Class<*>;"
	   72| move-object/from16 v19, v2
	.end_local v2
	.local v19, "onFuzzTargetReadyType", java.lang.invoke.MethodType
	   74| sget-object v2, java.lang.Void.TYPE
	.try_end_6
	  catch(java.lang.ClassNotFoundException) : Label_14
	  catch(java.lang.NoSuchMethodException) : Label_13
	  catch(java.lang.IllegalAccessException) : Label_13
	   76| move-object/from16 v21, v3
	.try_begin_7
	.end_local v3
	.restart_local v21
	   78| const-class v3, java.lang.String
	.try_end_7
	  catch(java.lang.ClassNotFoundException) : Label_16
	  catch(java.lang.NoSuchMethodException) : Label_15
	  catch(java.lang.IllegalAccessException) : Label_15
	   80| move-object/from16 v24, v5
	.end_local v5
	.local_ex v24, "jazzerInternal", java.lang.Class, "Ljava/lang/Class<*>;"
	   82| const/4 v5, #+3 (0x00000003 | 4.20390e-45)
	   83| move-object/from16 v25, v6
	.try_begin_8
	.end_local v6
	.local v25, "traceMemcmp", java.lang.invoke.MethodHandle
	   85| new-array v6, v5, java.lang.Class[]
	   87| const-class v26, java.lang.String
	   89| const/16 v27, #+0 (0x00000000 | 0.00000)
	   91| aput-object v26, v6, v27
	   93| sget-object v26, java.lang.Integer.TYPE
	   95| const/16 v20, #+1 (0x00000001 | 1.40130e-45)
	   97| aput-object v26, v6, v20
	   99| sget-object v26, java.lang.Integer.TYPE
	  101| const/4 v5, #+2 (0x00000002 | 2.80260e-45)
	  102| aput-object v26, v6, v5
	.line 89
	  104| invoke-static {v2,v3,v6}, java.lang.invoke.MethodType.methodType(java.lang.Class, java.lang.Class, java.lang.Class[]):java.lang.invoke.MethodType
	  107| move-result-object v2
	.line 90
	.local v2, "traceStrcmpType", java.lang.invoke.MethodType
	  108| invoke-static {}, java.lang.invoke.MethodHandles.publicLookup():java.lang.invoke.MethodHandles$Lookup
	  111| move-result-object v3
	  112| const-string v6, "traceStrcmp"
	  114| invoke-virtual {v3,v4,v6,v2}, java.lang.invoke.MethodHandles$Lookup.findStatic(java.lang.Class, java.lang.String, java.lang.invoke.MethodType):java.lang.invoke.MethodHandle
	  117| move-result-object v3
	.try_end_8
	  catch(java.lang.ClassNotFoundException) : Label_12
	  catch(java.lang.NoSuchMethodException) : Label_11
	  catch(java.lang.IllegalAccessException) : Label_11
	.try_begin_9
	.line 92
	.end_local v22
	.local v3, "traceStrcmp", java.lang.invoke.MethodHandle
	  118| sget-object v6, java.lang.Void.TYPE
	  120| const-class v5, java.lang.String
	.try_end_9
	  catch(java.lang.ClassNotFoundException) : Label_10
	  catch(java.lang.NoSuchMethodException) : Label_9
	  catch(java.lang.IllegalAccessException) : Label_9
	  122| move-object/from16 v29, v2
	  124| move-object/from16 v22, v3
	  126| const/4 v2, #+2 (0x00000002 | 2.80260e-45)
	.try_begin_10
	.end_local v2
	.end_local v3
	.restart_local v22
	.local v29, "traceStrcmpType", java.lang.invoke.MethodType
	  127| new-array v3, v2, java.lang.Class[]
	  129| const-class v2, java.lang.String
	  131| aput-object v2, v3, v27
	  133| sget-object v2, java.lang.Integer.TYPE
	  135| const/16 v20, #+1 (0x00000001 | 1.40130e-45)
	  137| aput-object v2, v3, v20
	.line 93
	  139| invoke-static {v6,v5,v3}, java.lang.invoke.MethodType.methodType(java.lang.Class, java.lang.Class, java.lang.Class[]):java.lang.invoke.MethodType
	  142| move-result-object v2
	.line 94
	.local v2, "traceStrstrType", java.lang.invoke.MethodType
	  143| invoke-static {}, java.lang.invoke.MethodHandles.publicLookup():java.lang.invoke.MethodHandles$Lookup
	  146| move-result-object v3
	  147| const-string v5, "traceStrstr"
	  149| invoke-virtual {v3,v4,v5,v2}, java.lang.invoke.MethodHandles$Lookup.findStatic(java.lang.Class, java.lang.String, java.lang.invoke.MethodType):java.lang.invoke.MethodHandle
	  152| move-result-object v3
	.try_end_10
	  catch(java.lang.ClassNotFoundException) : Label_12
	  catch(java.lang.NoSuchMethodException) : Label_11
	  catch(java.lang.IllegalAccessException) : Label_11
	  153| move-object v5, v3
	.try_begin_11
	.line 96
	.end_local v23
	.local v5, "traceStrstr", java.lang.invoke.MethodHandle
	  154| sget-object v3, java.lang.Void.TYPE
	  156| const/4 v6, #+3 (0x00000003 | 4.20390e-45)
	  157| new-array v6, v6, java.lang.Class[]
	  159| aput-object v0, v6, v27
	  161| sget-object v23, java.lang.Integer.TYPE
	  163| const/16 v20, #+1 (0x00000001 | 1.40130e-45)
	  165| aput-object v23, v6, v20
	  167| sget-object v23, java.lang.Integer.TYPE
	  169| const/16 v26, #+2 (0x00000002 | 2.80260e-45)
	  171| aput-object v23, v6, v26
	.line 97
	  173| invoke-static {v3,v0,v6}, java.lang.invoke.MethodType.methodType(java.lang.Class, java.lang.Class, java.lang.Class[]):java.lang.invoke.MethodType
	  176| move-result-object v0
	.line 98
	.local v0, "traceMemcmpType", java.lang.invoke.MethodType
	  177| invoke-static {}, java.lang.invoke.MethodHandles.publicLookup():java.lang.invoke.MethodHandles$Lookup
	  180| move-result-object v3
	  181| const-string v6, "traceMemcmp"
	  183| invoke-virtual {v3,v4,v6,v0}, java.lang.invoke.MethodHandles$Lookup.findStatic(java.lang.Class, java.lang.String, java.lang.invoke.MethodType):java.lang.invoke.MethodHandle
	  186| move-result-object v3
	.try_end_11
	  catch(java.lang.ClassNotFoundException) : Label_8
	  catch(java.lang.NoSuchMethodException) : Label_7
	  catch(java.lang.IllegalAccessException) : Label_7
	  187| move-object v6, v3
	.try_begin_12
	.line 100
	.end_local v25
	.restart_local v6
	  188| sget-object v3, java.lang.Void.TYPE
	  190| move-object/from16 v23, v0
	.end_local v0
	.local v23, "traceMemcmpType", java.lang.invoke.MethodType
	  192| sget-object v0, java.lang.Integer.TYPE
	.try_end_12
	  catch(java.lang.ClassNotFoundException) : Label_6
	  catch(java.lang.NoSuchMethodException) : Label_5
	  catch(java.lang.IllegalAccessException) : Label_5
	  194| move-object/from16 v26, v2
	  196| move-object/from16 v28, v5
	  198| const/4 v2, #+1 (0x00000001 | 1.40130e-45)
	.try_begin_13
	.end_local v2
	.end_local v5
	.local v26, "traceStrstrType", java.lang.invoke.MethodType
	.local v28, "traceStrstr", java.lang.invoke.MethodHandle
	  199| new-array v5, v2, java.lang.Class[]
	  201| sget-object v2, java.lang.Integer.TYPE
	  203| aput-object v2, v5, v27
	  205| invoke-static {v3,v0,v5}, java.lang.invoke.MethodType.methodType(java.lang.Class, java.lang.Class, java.lang.Class[]):java.lang.invoke.MethodType
	  208| move-result-object v0
	.line 101
	.local v0, "tracePcIndirType", java.lang.invoke.MethodType
	  209| invoke-static {}, java.lang.invoke.MethodHandles.publicLookup():java.lang.invoke.MethodHandles$Lookup
	  212| move-result-object v2
	  213| const-string v3, "tracePcIndir"
	  215| invoke-virtual {v2,v4,v3,v0}, java.lang.invoke.MethodHandles$Lookup.findStatic(java.lang.Class, java.lang.String, java.lang.invoke.MethodType):java.lang.invoke.MethodHandle
	  218| move-result-object v2
	  219| move-object v7, v2
	.line 104
	  220| const-string v2, "com.code_intelligence.jazzer.autofuzz.Meta"
	  222| invoke-static {v2}, java.lang.Class.forName(java.lang.String):java.lang.Class
	  225| move-result-object v2
	.line 105
	.local_ex v2, "metaClass", java.lang.Class, "Ljava/lang/Class<*>;"
	  226| const-class v3, java.lang.Object
	  228| const-class v5, com.code_intelligence.jazzer.api.FuzzedDataProvider
	  230| move-object/from16 v25, v0
	  232| move-object/from16 v30, v4
	  234| const/4 v4, #+1 (0x00000001 | 1.40130e-45)
	.end_local v0
	.end_local v4
	.local v25, "tracePcIndirType", java.lang.invoke.MethodType
	.local_ex v30, "traceDataFlowNativeCallbacks", java.lang.Class, "Ljava/lang/Class<*>;"
	  235| new-array v0, v4, java.lang.Class[]
	  237| const-class v4, java.lang.Class
	  239| aput-object v4, v0, v27
	.line 106
	  241| invoke-static {v3,v5,v0}, java.lang.invoke.MethodType.methodType(java.lang.Class, java.lang.Class, java.lang.Class[]):java.lang.invoke.MethodType
	  244| move-result-object v0
	.line 107
	.local v0, "consumeType", java.lang.invoke.MethodType
	  245| invoke-static {}, java.lang.invoke.MethodHandles.publicLookup():java.lang.invoke.MethodHandles$Lookup
	  248| move-result-object v3
	  249| const-string v4, "consume"
	  251| invoke-virtual {v3,v2,v4,v0}, java.lang.invoke.MethodHandles$Lookup.findStatic(java.lang.Class, java.lang.String, java.lang.invoke.MethodType):java.lang.invoke.MethodHandle
	  254| move-result-object v3
	  255| move-object v8, v3
	.line 109
	  256| invoke-static {}, java.lang.invoke.MethodHandles.publicLookup():java.lang.invoke.MethodHandles$Lookup
	  259| move-result-object v3
	  260| const-class v4, java.lang.Object
	  262| const-class v5, com.code_intelligence.jazzer.api.FuzzedDataProvider
	.try_end_13
	  catch(java.lang.ClassNotFoundException) : Label_4
	  catch(java.lang.NoSuchMethodException) : Label_3
	  catch(java.lang.IllegalAccessException) : Label_3
	  264| move-object/from16 v32, v0
	  266| move-object/from16 v31, v6
	  268| const/4 v6, #+1 (0x00000001 | 1.40130e-45)
	.try_begin_14
	.end_local v0
	.end_local v6
	.local v31, "traceMemcmp", java.lang.invoke.MethodHandle
	.local v32, "consumeType", java.lang.invoke.MethodType
	  269| new-array v0, v6, java.lang.Class[]
	  271| const-class v6, com.code_intelligence.jazzer.api.Function1
	  273| aput-object v6, v0, v27
	.line 110
	  275| invoke-static {v4,v5,v0}, java.lang.invoke.MethodType.methodType(java.lang.Class, java.lang.Class, java.lang.Class[]):java.lang.invoke.MethodType
	  278| move-result-object v0
	.line 109
	  279| invoke-virtual {v3,v2,v1,v0}, java.lang.invoke.MethodHandles$Lookup.findStatic(java.lang.Class, java.lang.String, java.lang.invoke.MethodType):java.lang.invoke.MethodHandle
	  282| move-result-object v0
	  283| move-object v9, v0
	.line 111
	  284| invoke-static {}, java.lang.invoke.MethodHandles.publicLookup():java.lang.invoke.MethodHandles$Lookup
	  287| move-result-object v0
	  288| const-class v3, java.lang.Object
	  290| const-class v4, com.code_intelligence.jazzer.api.FuzzedDataProvider
	  292| const/4 v5, #+1 (0x00000001 | 1.40130e-45)
	  293| new-array v6, v5, java.lang.Class[]
	  295| const-class v5, com.code_intelligence.jazzer.api.Function2
	  297| aput-object v5, v6, v27
	.line 112
	  299| invoke-static {v3,v4,v6}, java.lang.invoke.MethodType.methodType(java.lang.Class, java.lang.Class, java.lang.Class[]):java.lang.invoke.MethodType
	  302| move-result-object v3
	.line 111
	  303| invoke-virtual {v0,v2,v1,v3}, java.lang.invoke.MethodHandles$Lookup.findStatic(java.lang.Class, java.lang.String, java.lang.invoke.MethodType):java.lang.invoke.MethodHandle
	  306| move-result-object v0
	  307| move-object v10, v0
	.line 113
	  308| invoke-static {}, java.lang.invoke.MethodHandles.publicLookup():java.lang.invoke.MethodHandles$Lookup
	  311| move-result-object v0
	  312| const-class v3, java.lang.Object
	  314| const-class v4, com.code_intelligence.jazzer.api.FuzzedDataProvider
	  316| const/4 v5, #+1 (0x00000001 | 1.40130e-45)
	  317| new-array v6, v5, java.lang.Class[]
	  319| const-class v5, com.code_intelligence.jazzer.api.Function3
	  321| aput-object v5, v6, v27
	.line 114
	  323| invoke-static {v3,v4,v6}, java.lang.invoke.MethodType.methodType(java.lang.Class, java.lang.Class, java.lang.Class[]):java.lang.invoke.MethodType
	  326| move-result-object v3
	.line 113
	  327| invoke-virtual {v0,v2,v1,v3}, java.lang.invoke.MethodHandles$Lookup.findStatic(java.lang.Class, java.lang.String, java.lang.invoke.MethodType):java.lang.invoke.MethodHandle
	  330| move-result-object v0
	  331| move-object v11, v0
	.line 115
	  332| invoke-static {}, java.lang.invoke.MethodHandles.publicLookup():java.lang.invoke.MethodHandles$Lookup
	  335| move-result-object v0
	  336| const-class v3, java.lang.Object
	  338| const-class v4, com.code_intelligence.jazzer.api.FuzzedDataProvider
	  340| const/4 v5, #+1 (0x00000001 | 1.40130e-45)
	  341| new-array v6, v5, java.lang.Class[]
	  343| const-class v5, com.code_intelligence.jazzer.api.Function4
	  345| aput-object v5, v6, v27
	.line 116
	  347| invoke-static {v3,v4,v6}, java.lang.invoke.MethodType.methodType(java.lang.Class, java.lang.Class, java.lang.Class[]):java.lang.invoke.MethodType
	  350| move-result-object v3
	.line 115
	  351| invoke-virtual {v0,v2,v1,v3}, java.lang.invoke.MethodHandles$Lookup.findStatic(java.lang.Class, java.lang.String, java.lang.invoke.MethodType):java.lang.invoke.MethodHandle
	  354| move-result-object v0
	  355| move-object v12, v0
	.line 117
	  356| invoke-static {}, java.lang.invoke.MethodHandles.publicLookup():java.lang.invoke.MethodHandles$Lookup
	  359| move-result-object v0
	  360| const-class v3, java.lang.Object
	  362| const-class v4, com.code_intelligence.jazzer.api.FuzzedDataProvider
	  364| const/4 v5, #+1 (0x00000001 | 1.40130e-45)
	  365| new-array v6, v5, java.lang.Class[]
	  367| const-class v5, com.code_intelligence.jazzer.api.Function5
	  369| aput-object v5, v6, v27
	.line 118
	  371| invoke-static {v3,v4,v6}, java.lang.invoke.MethodType.methodType(java.lang.Class, java.lang.Class, java.lang.Class[]):java.lang.invoke.MethodType
	  374| move-result-object v3
	.line 117
	  375| invoke-virtual {v0,v2,v1,v3}, java.lang.invoke.MethodHandles$Lookup.findStatic(java.lang.Class, java.lang.String, java.lang.invoke.MethodType):java.lang.invoke.MethodHandle
	  378| move-result-object v0
	  379| move-object v13, v0
	.line 119
	  380| invoke-static {}, java.lang.invoke.MethodHandles.publicLookup():java.lang.invoke.MethodHandles$Lookup
	  383| move-result-object v0
	  384| sget-object v3, java.lang.Void.TYPE
	  386| const-class v4, com.code_intelligence.jazzer.api.FuzzedDataProvider
	  388| const/4 v5, #+1 (0x00000001 | 1.40130e-45)
	  389| new-array v6, v5, java.lang.Class[]
	  391| const-class v5, com.code_intelligence.jazzer.api.Consumer1
	  393| aput-object v5, v6, v27
	.line 120
	  395| invoke-static {v3,v4,v6}, java.lang.invoke.MethodType.methodType(java.lang.Class, java.lang.Class, java.lang.Class[]):java.lang.invoke.MethodType
	  398| move-result-object v3
	.line 119
	  399| invoke-virtual {v0,v2,v1,v3}, java.lang.invoke.MethodHandles$Lookup.findStatic(java.lang.Class, java.lang.String, java.lang.invoke.MethodType):java.lang.invoke.MethodHandle
	  402| move-result-object v0
	  403| move-object v14, v0
	.line 121
	  404| invoke-static {}, java.lang.invoke.MethodHandles.publicLookup():java.lang.invoke.MethodHandles$Lookup
	  407| move-result-object v0
	  408| sget-object v3, java.lang.Void.TYPE
	  410| const-class v4, com.code_intelligence.jazzer.api.FuzzedDataProvider
	  412| const/4 v5, #+1 (0x00000001 | 1.40130e-45)
	  413| new-array v6, v5, java.lang.Class[]
	  415| const-class v5, com.code_intelligence.jazzer.api.Consumer2
	  417| aput-object v5, v6, v27
	.line 122
	  419| invoke-static {v3,v4,v6}, java.lang.invoke.MethodType.methodType(java.lang.Class, java.lang.Class, java.lang.Class[]):java.lang.invoke.MethodType
	  422| move-result-object v3
	.line 121
	  423| invoke-virtual {v0,v2,v1,v3}, java.lang.invoke.MethodHandles$Lookup.findStatic(java.lang.Class, java.lang.String, java.lang.invoke.MethodType):java.lang.invoke.MethodHandle
	  426| move-result-object v0
	  427| move-object v15, v0
	.line 123
	  428| invoke-static {}, java.lang.invoke.MethodHandles.publicLookup():java.lang.invoke.MethodHandles$Lookup
	  431| move-result-object v0
	  432| sget-object v3, java.lang.Void.TYPE
	  434| const-class v4, com.code_intelligence.jazzer.api.FuzzedDataProvider
	  436| const/4 v5, #+1 (0x00000001 | 1.40130e-45)
	  437| new-array v6, v5, java.lang.Class[]
	  439| const-class v5, com.code_intelligence.jazzer.api.Consumer3
	  441| aput-object v5, v6, v27
	.line 124
	  443| invoke-static {v3,v4,v6}, java.lang.invoke.MethodType.methodType(java.lang.Class, java.lang.Class, java.lang.Class[]):java.lang.invoke.MethodType
	  446| move-result-object v3
	.line 123
	  447| invoke-virtual {v0,v2,v1,v3}, java.lang.invoke.MethodHandles$Lookup.findStatic(java.lang.Class, java.lang.String, java.lang.invoke.MethodType):java.lang.invoke.MethodHandle
	  450| move-result-object v0
	  451| move-object/from16 v16, v0
	.line 125
	  453| invoke-static {}, java.lang.invoke.MethodHandles.publicLookup():java.lang.invoke.MethodHandles$Lookup
	  456| move-result-object v0
	  457| sget-object v3, java.lang.Void.TYPE
	  459| const-class v4, com.code_intelligence.jazzer.api.FuzzedDataProvider
	  461| const/4 v5, #+1 (0x00000001 | 1.40130e-45)
	  462| new-array v6, v5, java.lang.Class[]
	  464| const-class v5, com.code_intelligence.jazzer.api.Consumer4
	  466| aput-object v5, v6, v27
	.line 126
	  468| invoke-static {v3,v4,v6}, java.lang.invoke.MethodType.methodType(java.lang.Class, java.lang.Class, java.lang.Class[]):java.lang.invoke.MethodType
	  471| move-result-object v3
	.line 125
	  472| invoke-virtual {v0,v2,v1,v3}, java.lang.invoke.MethodHandles$Lookup.findStatic(java.lang.Class, java.lang.String, java.lang.invoke.MethodType):java.lang.invoke.MethodHandle
	  475| move-result-object v0
	  476| move-object/from16 v17, v0
	.line 127
	  478| invoke-static {}, java.lang.invoke.MethodHandles.publicLookup():java.lang.invoke.MethodHandles$Lookup
	  481| move-result-object v0
	  482| sget-object v3, java.lang.Void.TYPE
	  484| const-class v4, com.code_intelligence.jazzer.api.FuzzedDataProvider
	  486| const/4 v5, #+1 (0x00000001 | 1.40130e-45)
	  487| new-array v6, v5, java.lang.Class[]
	  489| const-class v5, com.code_intelligence.jazzer.api.Consumer5
	  491| aput-object v5, v6, v27
	.line 128
	  493| invoke-static {v3,v4,v6}, java.lang.invoke.MethodType.methodType(java.lang.Class, java.lang.Class, java.lang.Class[]):java.lang.invoke.MethodType
	  496| move-result-object v3
	.line 127
	  497| invoke-virtual {v0,v2,v1,v3}, java.lang.invoke.MethodHandles$Lookup.findStatic(java.lang.Class, java.lang.String, java.lang.invoke.MethodType):java.lang.invoke.MethodHandle
	  500| move-result-object v0
	.try_end_14
	  catch(java.lang.ClassNotFoundException) : Label_2
	  catch(java.lang.NoSuchMethodException) : Label_1
	  catch(java.lang.IllegalAccessException) : Label_1
	  501| move-object/from16 v18, v0
	.line 138
	.end_local v2
	.end_local v19
	.end_local v23
	.end_local v25
	.end_local v26
	.end_local v29
	.end_local v30
	.end_local v32
	  503| move-object/from16 v3, v21
	  505| move-object/from16 v4, v22
	  507| move-object/from16 v19, v24
	  509| move-object/from16 v5, v28
	  511| move-object/from16 v6, v31
	  513| goto/16 Label_27
Label_1:
	.line 132
	  515| move-exception v0
	  516| move-object/from16 v3, v21
	  518| move-object/from16 v4, v22
	  520| move-object/from16 v2, v24
	  522| move-object/from16 v5, v28
	  524| move-object/from16 v6, v31
	  526| goto/16 Label_24
Label_2:
	.line 129
	  528| move-exception v0
	  529| move-object/from16 v3, v21
	  531| move-object/from16 v4, v22
	  533| move-object/from16 v2, v24
	  535| move-object/from16 v5, v28
	  537| move-object/from16 v6, v31
	  539| goto/16 Label_26
Label_3:
	.line 132
	.end_local v31
	.restart_local v6
	  541| move-exception v0
	  542| move-object/from16 v31, v6
	  544| move-object/from16 v3, v21
	  546| move-object/from16 v4, v22
	  548| move-object/from16 v2, v24
	  550| move-object/from16 v5, v28
	.end_local v6
	.restart_local v31
	  552| goto/16 Label_24
Label_4:
	.line 129
	.end_local v31
	.restart_local v6
	  554| move-exception v0
	  555| move-object/from16 v31, v6
	  557| move-object/from16 v3, v21
	  559| move-object/from16 v4, v22
	  561| move-object/from16 v2, v24
	  563| move-object/from16 v5, v28
	.end_local v6
	.restart_local v31
	  565| goto/16 Label_26
Label_5:
	.line 132
	.end_local v28
	.end_local v31
	.restart_local v5
	.restart_local v6
	  567| move-exception v0
	  568| move-object/from16 v28, v5
	  570| move-object/from16 v31, v6
	  572| move-object/from16 v3, v21
	  574| move-object/from16 v4, v22
	  576| move-object/from16 v2, v24
	.end_local v5
	.end_local v6
	.restart_local v28
	.restart_local v31
	  578| goto/16 Label_24
Label_6:
	.line 129
	.end_local v28
	.end_local v31
	.restart_local v5
	.restart_local v6
	  580| move-exception v0
	  581| move-object/from16 v28, v5
	  583| move-object/from16 v31, v6
	  585| move-object/from16 v3, v21
	  587| move-object/from16 v4, v22
	  589| move-object/from16 v2, v24
	.end_local v5
	.end_local v6
	.restart_local v28
	.restart_local v31
	  591| goto/16 Label_26
Label_7:
	.line 132
	.end_local v28
	.end_local v31
	.restart_local v5
	.local v25, "traceMemcmp", java.lang.invoke.MethodHandle
	  593| move-exception v0
	  594| move-object/from16 v28, v5
	  596| move-object/from16 v3, v21
	  598| move-object/from16 v4, v22
	  600| move-object/from16 v2, v24
	  602| move-object/from16 v6, v25
	.end_local v5
	.restart_local v28
	  604| goto/16 Label_24
Label_8:
	.line 129
	.end_local v28
	.restart_local v5
	  606| move-exception v0
	  607| move-object/from16 v28, v5
	  609| move-object/from16 v3, v21
	  611| move-object/from16 v4, v22
	  613| move-object/from16 v2, v24
	  615| move-object/from16 v6, v25
	.end_local v5
	.restart_local v28
	  617| goto/16 Label_26
Label_9:
	.line 132
	.end_local v22
	.end_local v28
	.restart_local v3
	.local v23, "traceStrstr", java.lang.invoke.MethodHandle
	  619| move-exception v0
	  620| move-object/from16 v22, v3
	  622| move-object/from16 v3, v21
	  624| move-object/from16 v4, v22
	  626| move-object/from16 v5, v23
	  628| move-object/from16 v2, v24
	  630| move-object/from16 v6, v25
	.end_local v3
	.restart_local v22
	  632| goto/16 Label_24
Label_10:
	.line 129
	.end_local v22
	.restart_local v3
	  634| move-exception v0
	  635| move-object/from16 v22, v3
	  637| move-object/from16 v3, v21
	  639| move-object/from16 v4, v22
	  641| move-object/from16 v5, v23
	  643| move-object/from16 v2, v24
	  645| move-object/from16 v6, v25
	.end_local v3
	.restart_local v22
	  647| goto/16 Label_26
Label_11:
	.line 132
	  649| move-exception v0
	  650| move-object/from16 v3, v21
	  652| move-object/from16 v4, v22
	  654| move-object/from16 v5, v23
	  656| move-object/from16 v2, v24
	  658| move-object/from16 v6, v25
	  660| goto/16 Label_24
Label_12:
	.line 129
	  662| move-exception v0
	  663| move-object/from16 v3, v21
	  665| move-object/from16 v4, v22
	  667| move-object/from16 v5, v23
	  669| move-object/from16 v2, v24
	  671| move-object/from16 v6, v25
	  673| goto/16 Label_26
Label_13:
	.line 132
	.end_local v21
	.end_local v24
	.end_local v25
	.local v3, "onFuzzTargetReady", java.lang.invoke.MethodHandle
	.local_ex v5, "jazzerInternal", java.lang.Class, "Ljava/lang/Class<*>;"
	.restart_local v6
	  675| move-exception v0
	  676| move-object/from16 v21, v3
	  678| move-object/from16 v24, v5
	  680| move-object/from16 v25, v6
	  682| move-object/from16 v4, v22
	  684| move-object/from16 v5, v23
	  686| move-object/from16 v2, v24
	.end_local v3
	.end_local v5
	.end_local v6
	.restart_local v21
	.restart_local v24
	.restart_local v25
	  688| goto/16 Label_24
Label_14:
	.line 129
	.end_local v21
	.end_local v24
	.end_local v25
	.restart_local v3
	.restart_local v5
	.restart_local v6
	  690| move-exception v0
	  691| move-object/from16 v21, v3
	  693| move-object/from16 v24, v5
	  695| move-object/from16 v25, v6
	  697| move-object/from16 v4, v22
	  699| move-object/from16 v5, v23
	  701| move-object/from16 v2, v24
	.end_local v3
	.end_local v5
	.end_local v6
	.restart_local v21
	.restart_local v24
	.restart_local v25
	  703| goto/16 Label_26
Label_15:
	.line 132
	.end_local v24
	.end_local v25
	.restart_local v5
	.restart_local v6
	  705| move-exception v0
	  706| move-object/from16 v24, v5
	  708| move-object/from16 v25, v6
	  710| move-object/from16 v3, v21
	  712| move-object/from16 v4, v22
	  714| move-object/from16 v5, v23
	  716| move-object/from16 v2, v24
	.end_local v5
	.end_local v6
	.restart_local v24
	.restart_local v25
	  718| goto/16 Label_24
Label_16:
	.line 129
	.end_local v24
	.end_local v25
	.restart_local v5
	.restart_local v6
	  720| move-exception v0
	  721| move-object/from16 v24, v5
	  723| move-object/from16 v25, v6
	  725| move-object/from16 v3, v21
	  727| move-object/from16 v4, v22
	  729| move-object/from16 v5, v23
	  731| move-object/from16 v2, v24
	.end_local v5
	.end_local v6
	.restart_local v24
	.restart_local v25
	  733| goto/16 Label_26
Label_17:
	.line 132
	.end_local v23
	.end_local v24
	.end_local v25
	.local v5, "traceStrstr", java.lang.invoke.MethodHandle
	.restart_local v6
	.local_ex v19, "jazzerInternal", java.lang.Class, "Ljava/lang/Class<*>;"
	  735| move-exception v0
	  736| move-object/from16 v23, v5
	  738| move-object/from16 v25, v6
	  740| move-object/from16 v24, v19
	  742| move-object/from16 v3, v21
	  744| move-object/from16 v4, v22
	  746| move-object/from16 v2, v24
	.end_local v5
	.end_local v6
	.end_local v19
	.restart_local v23
	.restart_local v24
	.restart_local v25
	  748| goto/16 Label_24
Label_18:
	.line 129
	.end_local v23
	.end_local v24
	.end_local v25
	.restart_local v5
	.restart_local v6
	.restart_local v19
	  750| move-exception v0
	  751| move-object/from16 v23, v5
	  753| move-object/from16 v25, v6
	  755| move-object/from16 v24, v19
	  757| move-object/from16 v3, v21
	  759| move-object/from16 v4, v22
	  761| move-object/from16 v2, v24
	.end_local v5
	.end_local v6
	.end_local v19
	.restart_local v23
	.restart_local v24
	.restart_local v25
	  763| goto/16 Label_26
Label_19:
	.line 132
	.end_local v22
	.end_local v23
	.end_local v24
	.end_local v25
	.local v4, "traceStrcmp", java.lang.invoke.MethodHandle
	.restart_local v5
	.restart_local v6
	.restart_local v19
	  765| move-exception v0
	  766| move-object/from16 v22, v4
	  768| move-object/from16 v23, v5
	  770| move-object/from16 v25, v6
	  772| move-object/from16 v24, v19
	  774| move-object/from16 v3, v21
	  776| move-object/from16 v2, v24
	.end_local v4
	.end_local v5
	.end_local v6
	.end_local v19
	.restart_local v22
	.restart_local v23
	.restart_local v24
	.restart_local v25
	  778| goto Label_24
Label_20:
	.line 129
	.end_local v22
	.end_local v23
	.end_local v24
	.end_local v25
	.restart_local v4
	.restart_local v5
	.restart_local v6
	.restart_local v19
	  779| move-exception v0
	  780| move-object/from16 v22, v4
	  782| move-object/from16 v23, v5
	  784| move-object/from16 v25, v6
	  786| move-object/from16 v24, v19
	  788| move-object/from16 v3, v21
	  790| move-object/from16 v2, v24
	.end_local v4
	.end_local v5
	.end_local v6
	.end_local v19
	.restart_local v22
	.restart_local v23
	.restart_local v24
	.restart_local v25
	  792| goto Label_26
Label_21:
	.line 132
	.end_local v21
	.end_local v22
	.end_local v23
	.end_local v24
	.end_local v25
	.restart_local v3
	.restart_local v4
	.restart_local v5
	.restart_local v6
	.restart_local v19
	  793| move-exception v0
	  794| move-object/from16 v21, v3
	  796| move-object/from16 v22, v4
	  798| move-object/from16 v23, v5
	  800| move-object/from16 v25, v6
	  802| move-object/from16 v24, v19
	  804| move-object/from16 v2, v24
	.end_local v3
	.end_local v4
	.end_local v5
	.end_local v6
	.end_local v19
	.restart_local v21
	.restart_local v22
	.restart_local v23
	.restart_local v24
	.restart_local v25
	  806| goto Label_24
Label_22:
	.line 129
	.end_local v21
	.end_local v22
	.end_local v23
	.end_local v24
	.end_local v25
	.restart_local v3
	.restart_local v4
	.restart_local v5
	.restart_local v6
	.restart_local v19
	  807| move-exception v0
	  808| move-object/from16 v21, v3
	  810| move-object/from16 v22, v4
	  812| move-object/from16 v23, v5
	  814| move-object/from16 v25, v6
	  816| move-object/from16 v24, v19
	  818| move-object/from16 v2, v24
	.end_local v3
	.end_local v4
	.end_local v5
	.end_local v6
	.end_local v19
	.restart_local v21
	.restart_local v22
	.restart_local v23
	.restart_local v24
	.restart_local v25
	  820| goto Label_26
Label_23:
	.line 132
	.end_local v21
	.end_local v22
	.end_local v23
	.end_local v24
	.end_local v25
	.restart_local v3
	.restart_local v4
	.restart_local v5
	.restart_local v6
	.restart_local v19
	  821| move-exception v0
	  822| move-object/from16 v21, v3
	  824| move-object/from16 v22, v4
	  826| move-object/from16 v23, v5
	  828| move-object/from16 v25, v6
	  830| move-object/from16 v2, v19
Label_24:
	.line 135
	.end_local v19
	.local v0, "e", java.lang.ReflectiveOperationException
	.local_ex v2, "jazzerInternal", java.lang.Class, "Ljava/lang/Class<*>;"
	  832| sget-object v1, java.lang.System.err
	  834| move-object/from16 v19, v2
	.end_local v2
	.restart_local v19
	  836| const-string v2, "ERROR: Incompatible version of the Jazzer API detected, please update."
	  838| invoke-virtual {v1,v2}, java.io.PrintStream.println(java.lang.String):void
	.line 136
	  841| invoke-virtual {v0}, java.lang.ReflectiveOperationException.printStackTrace():void
	.line 137
	  844| const/4 v1, #+1 (0x00000001 | 1.40130e-45)
	  845| invoke-static {v1}, java.lang.System.exit(int):void
	  848| goto Label_27
Label_25:
	.line 129
	.end_local v0
	  849| move-exception v0
	  850| move-object/from16 v21, v3
	  852| move-object/from16 v22, v4
	  854| move-object/from16 v23, v5
	  856| move-object/from16 v25, v6
	  858| move-object/from16 v2, v19
Label_26:
	.line 138
	.end_local v19
	.restart_local v2
	  860| move-object/from16 v19, v2
Label_27:
	.line 139
	.end_local v2
	.restart_local v19
	  862| sput-object v19, com.code_intelligence.jazzer.api.Jazzer.JAZZER_INTERNAL
	.line 140
	  864| sput-object v3, com.code_intelligence.jazzer.api.Jazzer.ON_FUZZ_TARGET_READY
	.line 141
	  866| sput-object v4, com.code_intelligence.jazzer.api.Jazzer.TRACE_STRCMP
	.line 142
	  868| sput-object v5, com.code_intelligence.jazzer.api.Jazzer.TRACE_STRSTR
	.line 143
	  870| sput-object v6, com.code_intelligence.jazzer.api.Jazzer.TRACE_MEMCMP
	.line 144
	  872| sput-object v7, com.code_intelligence.jazzer.api.Jazzer.TRACE_PC_INDIR
	.line 145
	  874| sput-object v8, com.code_intelligence.jazzer.api.Jazzer.CONSUME
	.line 146
	  876| sput-object v9, com.code_intelligence.jazzer.api.Jazzer.AUTOFUZZ_FUNCTION_1
	.line 147
	  878| sput-object v10, com.code_intelligence.jazzer.api.Jazzer.AUTOFUZZ_FUNCTION_2
	.line 148
	  880| sput-object v11, com.code_intelligence.jazzer.api.Jazzer.AUTOFUZZ_FUNCTION_3
	.line 149
	  882| sput-object v12, com.code_intelligence.jazzer.api.Jazzer.AUTOFUZZ_FUNCTION_4
	.line 150
	  884| sput-object v13, com.code_intelligence.jazzer.api.Jazzer.AUTOFUZZ_FUNCTION_5
	.line 151
	  886| sput-object v14, com.code_intelligence.jazzer.api.Jazzer.AUTOFUZZ_CONSUMER_1
	.line 152
	  888| sput-object v15, com.code_intelligence.jazzer.api.Jazzer.AUTOFUZZ_CONSUMER_2
	.line 153
	  890| sput-object v16, com.code_intelligence.jazzer.api.Jazzer.AUTOFUZZ_CONSUMER_3
	.line 154
	  892| sput-object v17, com.code_intelligence.jazzer.api.Jazzer.AUTOFUZZ_CONSUMER_4
	.line 155
	  894| sput-object v18, com.code_intelligence.jazzer.api.Jazzer.AUTOFUZZ_CONSUMER_5
	.line 156
	.end_local v3
	.end_local v4
	.end_local v5
	.end_local v6
	.end_local v7
	.end_local v8
	.end_local v9
	.end_local v10
	.end_local v11
	.end_local v12
	.end_local v13
	.end_local v14
	.end_local v15
	.end_local v16
	.end_local v17
	.end_local v18
	.end_local v19
	  896| return-void
}

method com.code_intelligence.jazzer.api.Jazzer.<init>():void
{
	.src "Jazzer.java"
	.line 158
	.line 158
	    0| invoke-direct {v0}, java.lang.Object.<init>():void
	    3| return-void
}

method com.code_intelligence.jazzer.api.Jazzer.autofuzz(com.code_intelligence.jazzer.api.FuzzedDataProvider, com.code_intelligence.jazzer.api.Function1):java.lang.Object
{
	.try_begin_1
	.params "data", "?"
	.src "Jazzer.java"
	.line 180
	.line 180
	.local_ex v3, "func", com.code_intelligence.jazzer.api.Function1, "Lcom/code_intelligence/jazzer/api/Function1<TT1;TR;>;"
	    0| sget-object v0, com.code_intelligence.jazzer.api.Jazzer.AUTOFUZZ_FUNCTION_1
	    2| invoke-polymorphic {v0,v2,v3}, java.lang.invoke.MethodHandle.invoke(java.lang.Object[]):java.lang.Object, (com.code_intelligence.jazzer.api.FuzzedDataProvider, com.code_intelligence.jazzer.api.Function1):java.lang.Object
	    6| move-result-object v0
	.try_end_1
	  catch(com.code_intelligence.jazzer.api.AutofuzzInvocationException) : Label_2
	  catch(...) : Label_1
	    7| return-object v0
Label_1:
	.line 183
	    8| move-exception v0
	.line 184
	.local v0, "t", java.lang.Throwable
	    9| invoke-static {v0}, com.code_intelligence.jazzer.api.Jazzer.rethrowUnchecked(java.lang.Throwable):void
	   12| goto Label_3
Label_2:
	.line 181
	.end_local v0
	   13| move-exception v0
	.line 182
	.local v0, "e", com.code_intelligence.jazzer.api.AutofuzzInvocationException
	   14| invoke-virtual {v0}, com.code_intelligence.jazzer.api.AutofuzzInvocationException.getCause():java.lang.Throwable
	   17| move-result-object v1
	   18| invoke-static {v1}, com.code_intelligence.jazzer.api.Jazzer.rethrowUnchecked(java.lang.Throwable):void
	.line 185
	.end_local v0
	   21| nop
Label_3:
	.line 187
	   22| const/4 v0, #+0 (0x00000000 | 0.00000)
	   23| return-object v0
}

method com.code_intelligence.jazzer.api.Jazzer.autofuzz(com.code_intelligence.jazzer.api.FuzzedDataProvider, com.code_intelligence.jazzer.api.Function2):java.lang.Object
{
	.try_begin_1
	.params "data", "?"
	.src "Jazzer.java"
	.line 210
	.line 210
	.local_ex v3, "func", com.code_intelligence.jazzer.api.Function2, "Lcom/code_intelligence/jazzer/api/Function2<TT1;TT2;TR;>;"
	    0| sget-object v0, com.code_intelligence.jazzer.api.Jazzer.AUTOFUZZ_FUNCTION_2
	    2| invoke-polymorphic {v0,v2,v3}, java.lang.invoke.MethodHandle.invoke(java.lang.Object[]):java.lang.Object, (com.code_intelligence.jazzer.api.FuzzedDataProvider, com.code_intelligence.jazzer.api.Function2):java.lang.Object
	    6| move-result-object v0
	.try_end_1
	  catch(com.code_intelligence.jazzer.api.AutofuzzInvocationException) : Label_2
	  catch(...) : Label_1
	    7| return-object v0
Label_1:
	.line 213
	    8| move-exception v0
	.line 214
	.local v0, "t", java.lang.Throwable
	    9| invoke-static {v0}, com.code_intelligence.jazzer.api.Jazzer.rethrowUnchecked(java.lang.Throwable):void
	   12| goto Label_3
Label_2:
	.line 211
	.end_local v0
	   13| move-exception v0
	.line 212
	.local v0, "e", com.code_intelligence.jazzer.api.AutofuzzInvocationException
	   14| invoke-virtual {v0}, com.code_intelligence.jazzer.api.AutofuzzInvocationException.getCause():java.lang.Throwable
	   17| move-result-object v1
	   18| invoke-static {v1}, com.code_intelligence.jazzer.api.Jazzer.rethrowUnchecked(java.lang.Throwable):void
	.line 215
	.end_local v0
	   21| nop
Label_3:
	.line 217
	   22| const/4 v0, #+0 (0x00000000 | 0.00000)
	   23| return-object v0
}

method com.code_intelligence.jazzer.api.Jazzer.autofuzz(com.code_intelligence.jazzer.api.FuzzedDataProvider, com.code_intelligence.jazzer.api.Function3):java.lang.Object
{
	.try_begin_1
	.params "data", "?"
	.src "Jazzer.java"
	.line 240
	.line 240
	.local_ex v3, "func", com.code_intelligence.jazzer.api.Function3, "Lcom/code_intelligence/jazzer/api/Function3<TT1;TT2;TT3;TR;>;"
	    0| sget-object v0, com.code_intelligence.jazzer.api.Jazzer.AUTOFUZZ_FUNCTION_3
	    2| invoke-polymorphic {v0,v2,v3}, java.lang.invoke.MethodHandle.invoke(java.lang.Object[]):java.lang.Object, (com.code_intelligence.jazzer.api.FuzzedDataProvider, com.code_intelligence.jazzer.api.Function3):java.lang.Object
	    6| move-result-object v0
	.try_end_1
	  catch(com.code_intelligence.jazzer.api.AutofuzzInvocationException) : Label_2
	  catch(...) : Label_1
	    7| return-object v0
Label_1:
	.line 243
	    8| move-exception v0
	.line 244
	.local v0, "t", java.lang.Throwable
	    9| invoke-static {v0}, com.code_intelligence.jazzer.api.Jazzer.rethrowUnchecked(java.lang.Throwable):void
	   12| goto Label_3
Label_2:
	.line 241
	.end_local v0
	   13| move-exception v0
	.line 242
	.local v0, "e", com.code_intelligence.jazzer.api.AutofuzzInvocationException
	   14| invoke-virtual {v0}, com.code_intelligence.jazzer.api.AutofuzzInvocationException.getCause():java.lang.Throwable
	   17| move-result-object v1
	   18| invoke-static {v1}, com.code_intelligence.jazzer.api.Jazzer.rethrowUnchecked(java.lang.Throwable):void
	.line 245
	.end_local v0
	   21| nop
Label_3:
	.line 247
	   22| const/4 v0, #+0 (0x00000000 | 0.00000)
	   23| return-object v0
}

method com.code_intelligence.jazzer.api.Jazzer.autofuzz(com.code_intelligence.jazzer.api.FuzzedDataProvider, com.code_intelligence.jazzer.api.Function4):java.lang.Object
{
	.try_begin_1
	.params "data", "?"
	.src "Jazzer.java"
	.line 271
	.line 271
	.local_ex v3, "func", com.code_intelligence.jazzer.api.Function4, "Lcom/code_intelligence/jazzer/api/Function4<TT1;TT2;TT3;TT4;TR;>;"
	    0| sget-object v0, com.code_intelligence.jazzer.api.Jazzer.AUTOFUZZ_FUNCTION_4
	    2| invoke-polymorphic {v0,v2,v3}, java.lang.invoke.MethodHandle.invoke(java.lang.Object[]):java.lang.Object, (com.code_intelligence.jazzer.api.FuzzedDataProvider, com.code_intelligence.jazzer.api.Function4):java.lang.Object
	    6| move-result-object v0
	.try_end_1
	  catch(com.code_intelligence.jazzer.api.AutofuzzInvocationException) : Label_2
	  catch(...) : Label_1
	    7| return-object v0
Label_1:
	.line 274
	    8| move-exception v0
	.line 275
	.local v0, "t", java.lang.Throwable
	    9| invoke-static {v0}, com.code_intelligence.jazzer.api.Jazzer.rethrowUnchecked(java.lang.Throwable):void
	   12| goto Label_3
Label_2:
	.line 272
	.end_local v0
	   13| move-exception v0
	.line 273
	.local v0, "e", com.code_intelligence.jazzer.api.AutofuzzInvocationException
	   14| invoke-virtual {v0}, com.code_intelligence.jazzer.api.AutofuzzInvocationException.getCause():java.lang.Throwable
	   17| move-result-object v1
	   18| invoke-static {v1}, com.code_intelligence.jazzer.api.Jazzer.rethrowUnchecked(java.lang.Throwable):void
	.line 276
	.end_local v0
	   21| nop
Label_3:
	.line 278
	   22| const/4 v0, #+0 (0x00000000 | 0.00000)
	   23| return-object v0
}

method com.code_intelligence.jazzer.api.Jazzer.autofuzz(com.code_intelligence.jazzer.api.FuzzedDataProvider, com.code_intelligence.jazzer.api.Function5):java.lang.Object
{
	.try_begin_1
	.params "data", "?"
	.src "Jazzer.java"
	.line 302
	.line 302
	.local_ex v3, "func", com.code_intelligence.jazzer.api.Function5, "Lcom/code_intelligence/jazzer/api/Function5<TT1;TT2;TT3;TT4;TT5;TR;>;"
	    0| sget-object v0, com.code_intelligence.jazzer.api.Jazzer.AUTOFUZZ_FUNCTION_5
	    2| invoke-polymorphic {v0,v2,v3}, java.lang.invoke.MethodHandle.invoke(java.lang.Object[]):java.lang.Object, (com.code_intelligence.jazzer.api.FuzzedDataProvider, com.code_intelligence.jazzer.api.Function5):java.lang.Object
	    6| move-result-object v0
	.try_end_1
	  catch(com.code_intelligence.jazzer.api.AutofuzzInvocationException) : Label_2
	  catch(...) : Label_1
	    7| return-object v0
Label_1:
	.line 305
	    8| move-exception v0
	.line 306
	.local v0, "t", java.lang.Throwable
	    9| invoke-static {v0}, com.code_intelligence.jazzer.api.Jazzer.rethrowUnchecked(java.lang.Throwable):void
	   12| goto Label_3
Label_2:
	.line 303
	.end_local v0
	   13| move-exception v0
	.line 304
	.local v0, "e", com.code_intelligence.jazzer.api.AutofuzzInvocationException
	   14| invoke-virtual {v0}, com.code_intelligence.jazzer.api.AutofuzzInvocationException.getCause():java.lang.Throwable
	   17| move-result-object v1
	   18| invoke-static {v1}, com.code_intelligence.jazzer.api.Jazzer.rethrowUnchecked(java.lang.Throwable):void
	.line 307
	.end_local v0
	   21| nop
Label_3:
	.line 309
	   22| const/4 v0, #+0 (0x00000000 | 0.00000)
	   23| return-object v0
}

method com.code_intelligence.jazzer.api.Jazzer.autofuzz(com.code_intelligence.jazzer.api.FuzzedDataProvider, com.code_intelligence.jazzer.api.Consumer1):void
{
	.try_begin_1
	.params "data", "?"
	.src "Jazzer.java"
	.line 329
	.line 329
	.local_ex v3, "func", com.code_intelligence.jazzer.api.Consumer1, "Lcom/code_intelligence/jazzer/api/Consumer1<TT1;>;"
	    0| sget-object v0, com.code_intelligence.jazzer.api.Jazzer.AUTOFUZZ_CONSUMER_1
	    2| invoke-polymorphic {v0,v2,v3}, java.lang.invoke.MethodHandle.invoke(java.lang.Object[]):java.lang.Object, (com.code_intelligence.jazzer.api.FuzzedDataProvider, com.code_intelligence.jazzer.api.Consumer1):void
	.try_end_1
	  catch(com.code_intelligence.jazzer.api.AutofuzzInvocationException) : Label_2
	  catch(...) : Label_1
	    6| goto Label_3
Label_1:
	.line 332
	    7| move-exception v0
	.line 333
	.local v0, "t", java.lang.Throwable
	    8| invoke-static {v0}, com.code_intelligence.jazzer.api.Jazzer.rethrowUnchecked(java.lang.Throwable):void
	   11| goto Label_4
Label_2:
	.line 330
	.end_local v0
	   12| move-exception v0
	.line 331
	.local v0, "e", com.code_intelligence.jazzer.api.AutofuzzInvocationException
	   13| invoke-virtual {v0}, com.code_intelligence.jazzer.api.AutofuzzInvocationException.getCause():java.lang.Throwable
	   16| move-result-object v1
	   17| invoke-static {v1}, com.code_intelligence.jazzer.api.Jazzer.rethrowUnchecked(java.lang.Throwable):void
Label_3:
	.line 334
	.end_local v0
	   20| nop
Label_4:
	.line 335
	   21| return-void
}

method com.code_intelligence.jazzer.api.Jazzer.autofuzz(com.code_intelligence.jazzer.api.FuzzedDataProvider, com.code_intelligence.jazzer.api.Consumer2):void
{
	.try_begin_1
	.params "data", "?"
	.src "Jazzer.java"
	.line 354
	.line 354
	.local_ex v3, "func", com.code_intelligence.jazzer.api.Consumer2, "Lcom/code_intelligence/jazzer/api/Consumer2<TT1;TT2;>;"
	    0| sget-object v0, com.code_intelligence.jazzer.api.Jazzer.AUTOFUZZ_CONSUMER_2
	    2| invoke-polymorphic {v0,v2,v3}, java.lang.invoke.MethodHandle.invoke(java.lang.Object[]):java.lang.Object, (com.code_intelligence.jazzer.api.FuzzedDataProvider, com.code_intelligence.jazzer.api.Consumer2):void
	.try_end_1
	  catch(com.code_intelligence.jazzer.api.AutofuzzInvocationException) : Label_2
	  catch(...) : Label_1
	    6| goto Label_3
Label_1:
	.line 357
	    7| move-exception v0
	.line 358
	.local v0, "t", java.lang.Throwable
	    8| invoke-static {v0}, com.code_intelligence.jazzer.api.Jazzer.rethrowUnchecked(java.lang.Throwable):void
	   11| goto Label_4
Label_2:
	.line 355
	.end_local v0
	   12| move-exception v0
	.line 356
	.local v0, "e", com.code_intelligence.jazzer.api.AutofuzzInvocationException
	   13| invoke-virtual {v0}, com.code_intelligence.jazzer.api.AutofuzzInvocationException.getCause():java.lang.Throwable
	   16| move-result-object v1
	   17| invoke-static {v1}, com.code_intelligence.jazzer.api.Jazzer.rethrowUnchecked(java.lang.Throwable):void
Label_3:
	.line 359
	.end_local v0
	   20| nop
Label_4:
	.line 360
	   21| return-void
}

method com.code_intelligence.jazzer.api.Jazzer.autofuzz(com.code_intelligence.jazzer.api.FuzzedDataProvider, com.code_intelligence.jazzer.api.Consumer3):void
{
	.try_begin_1
	.params "data", "?"
	.src "Jazzer.java"
	.line 379
	.line 379
	.local_ex v3, "func", com.code_intelligence.jazzer.api.Consumer3, "Lcom/code_intelligence/jazzer/api/Consumer3<TT1;TT2;TT3;>;"
	    0| sget-object v0, com.code_intelligence.jazzer.api.Jazzer.AUTOFUZZ_CONSUMER_3
	    2| invoke-polymorphic {v0,v2,v3}, java.lang.invoke.MethodHandle.invoke(java.lang.Object[]):java.lang.Object, (com.code_intelligence.jazzer.api.FuzzedDataProvider, com.code_intelligence.jazzer.api.Consumer3):void
	.try_end_1
	  catch(com.code_intelligence.jazzer.api.AutofuzzInvocationException) : Label_2
	  catch(...) : Label_1
	    6| goto Label_3
Label_1:
	.line 382
	    7| move-exception v0
	.line 383
	.local v0, "t", java.lang.Throwable
	    8| invoke-static {v0}, com.code_intelligence.jazzer.api.Jazzer.rethrowUnchecked(java.lang.Throwable):void
	   11| goto Label_4
Label_2:
	.line 380
	.end_local v0
	   12| move-exception v0
	.line 381
	.local v0, "e", com.code_intelligence.jazzer.api.AutofuzzInvocationException
	   13| invoke-virtual {v0}, com.code_intelligence.jazzer.api.AutofuzzInvocationException.getCause():java.lang.Throwable
	   16| move-result-object v1
	   17| invoke-static {v1}, com.code_intelligence.jazzer.api.Jazzer.rethrowUnchecked(java.lang.Throwable):void
Label_3:
	.line 384
	.end_local v0
	   20| nop
Label_4:
	.line 385
	   21| return-void
}

method com.code_intelligence.jazzer.api.Jazzer.autofuzz(com.code_intelligence.jazzer.api.FuzzedDataProvider, com.code_intelligence.jazzer.api.Consumer4):void
{
	.try_begin_1
	.params "data", "?"
	.src "Jazzer.java"
	.line 405
	.line 405
	.local_ex v3, "func", com.code_intelligence.jazzer.api.Consumer4, "Lcom/code_intelligence/jazzer/api/Consumer4<TT1;TT2;TT3;TT4;>;"
	    0| sget-object v0, com.code_intelligence.jazzer.api.Jazzer.AUTOFUZZ_CONSUMER_4
	    2| invoke-polymorphic {v0,v2,v3}, java.lang.invoke.MethodHandle.invoke(java.lang.Object[]):java.lang.Object, (com.code_intelligence.jazzer.api.FuzzedDataProvider, com.code_intelligence.jazzer.api.Consumer4):void
	.try_end_1
	  catch(com.code_intelligence.jazzer.api.AutofuzzInvocationException) : Label_2
	  catch(...) : Label_1
	    6| goto Label_3
Label_1:
	.line 408
	    7| move-exception v0
	.line 409
	.local v0, "t", java.lang.Throwable
	    8| invoke-static {v0}, com.code_intelligence.jazzer.api.Jazzer.rethrowUnchecked(java.lang.Throwable):void
	   11| goto Label_4
Label_2:
	.line 406
	.end_local v0
	   12| move-exception v0
	.line 407
	.local v0, "e", com.code_intelligence.jazzer.api.AutofuzzInvocationException
	   13| invoke-virtual {v0}, com.code_intelligence.jazzer.api.AutofuzzInvocationException.getCause():java.lang.Throwable
	   16| move-result-object v1
	   17| invoke-static {v1}, com.code_intelligence.jazzer.api.Jazzer.rethrowUnchecked(java.lang.Throwable):void
Label_3:
	.line 410
	.end_local v0
	   20| nop
Label_4:
	.line 411
	   21| return-void
}

method com.code_intelligence.jazzer.api.Jazzer.autofuzz(com.code_intelligence.jazzer.api.FuzzedDataProvider, com.code_intelligence.jazzer.api.Consumer5):void
{
	.try_begin_1
	.params "data", "?"
	.src "Jazzer.java"
	.line 431
	.line 431
	.local_ex v3, "func", com.code_intelligence.jazzer.api.Consumer5, "Lcom/code_intelligence/jazzer/api/Consumer5<TT1;TT2;TT3;TT4;TT5;>;"
	    0| sget-object v0, com.code_intelligence.jazzer.api.Jazzer.AUTOFUZZ_CONSUMER_5
	    2| invoke-polymorphic {v0,v2,v3}, java.lang.invoke.MethodHandle.invoke(java.lang.Object[]):java.lang.Object, (com.code_intelligence.jazzer.api.FuzzedDataProvider, com.code_intelligence.jazzer.api.Consumer5):void
	.try_end_1
	  catch(com.code_intelligence.jazzer.api.AutofuzzInvocationException) : Label_2
	  catch(...) : Label_1
	    6| goto Label_3
Label_1:
	.line 434
	    7| move-exception v0
	.line 435
	.local v0, "t", java.lang.Throwable
	    8| invoke-static {v0}, com.code_intelligence.jazzer.api.Jazzer.rethrowUnchecked(java.lang.Throwable):void
	   11| goto Label_4
Label_2:
	.line 432
	.end_local v0
	   12| move-exception v0
	.line 433
	.local v0, "e", com.code_intelligence.jazzer.api.AutofuzzInvocationException
	   13| invoke-virtual {v0}, com.code_intelligence.jazzer.api.AutofuzzInvocationException.getCause():java.lang.Throwable
	   16| move-result-object v1
	   17| invoke-static {v1}, com.code_intelligence.jazzer.api.Jazzer.rethrowUnchecked(java.lang.Throwable):void
Label_3:
	.line 436
	.end_local v0
	   20| nop
Label_4:
	.line 437
	   21| return-void
}

method com.code_intelligence.jazzer.api.Jazzer.consume(com.code_intelligence.jazzer.api.FuzzedDataProvider, java.lang.Class):java.lang.Object
{
	.params "data", "?"
	.src "Jazzer.java"
	.line 454
	.line 454
	.local_ex v3, "type", java.lang.Class, "Ljava/lang/Class<TT;>;"
	    0| const/4 v0, #+0 (0x00000000 | 0.00000)
	.try_begin_1
	    1| sget-object v1, com.code_intelligence.jazzer.api.Jazzer.CONSUME
	    3| invoke-polymorphic {v1,v2,v3}, java.lang.invoke.MethodHandle.invokeExact(java.lang.Object[]):java.lang.Object, (com.code_intelligence.jazzer.api.FuzzedDataProvider, java.lang.Class):java.lang.Object
	    7| move-result-object v0
	.try_end_1
	  catch(com.code_intelligence.jazzer.api.AutofuzzConstructionException) : Label_2
	  catch(...) : Label_1
	    8| return-object v0
Label_1:
	.line 457
	    9| move-exception v1
	.line 458
	.local v1, "t", java.lang.Throwable
	   10| invoke-static {v1}, com.code_intelligence.jazzer.api.Jazzer.rethrowUnchecked(java.lang.Throwable):void
	.line 460
	   13| return-object v0
Label_2:
	.line 455
	.end_local v1
	   14| move-exception v1
	.line 456
	.local v1, "ignored", com.code_intelligence.jazzer.api.AutofuzzConstructionException
	   15| return-object v0
}

method com.code_intelligence.jazzer.api.Jazzer.exploreState(byte, int):void
{
	.params "state", "id"
	.src "Jazzer.java"
	.line 550
	.line 550
	    0| sget-object v0, com.code_intelligence.jazzer.api.Jazzer.TRACE_PC_INDIR
	    2| if-nez v0, Label_1
	.line 551
	    4| return-void
Label_1:
	.line 576
	    5| and-int/lit8 v1, v3, #+127 (0x0000007f | 1.77965e-43)
	    7| shl-int/lit8 v2, v4, #+7 (0x00000007 | 9.80909e-45)
	    9| or-int/2addr v1, v2
	.line 577
	.local v1, "lowerBits", int
	   10| ushr-int/lit8 v2, v4, #+5 (0x00000005 | 7.00649e-45)
	.try_begin_1
	.line 579
	.local v2, "upperBits", int
	   12| invoke-polymorphic {v0,v2,v1}, java.lang.invoke.MethodHandle.invokeExact(java.lang.Object[]):java.lang.Object, (int, int):void
	.try_end_1
	  catch(...) : Label_2
	.line 582
	   16| goto Label_3
Label_2:
	.line 580
	   17| move-exception v0
	.line 581
	.local v0, "e", java.lang.Throwable
	   18| invoke-virtual {v0}, java.lang.Throwable.printStackTrace():void
Label_3:
	.line 583
	.end_local v0
	   21| return-void
}

method com.code_intelligence.jazzer.api.Jazzer.getLibFuzzerSeed():int
{
	.src "Jazzer.java"
	.line 630
	.line 630
	    0| const-string v0, "jazzer.seed"
	    2| invoke-static {v0}, java.lang.System.getProperty(java.lang.String):java.lang.String
	    5| move-result-object v0
	.line 631
	.local v0, "rawSeed", java.lang.String
	    6| if-nez v0, Label_1
	.line 632
	    8| new-instance v1, java.security.SecureRandom
	   10| invoke-direct {v1}, java.security.SecureRandom.<init>():void
	   13| invoke-virtual {v1}, java.security.SecureRandom.nextInt():int
	   16| move-result v1
	   17| return v1
Label_1:
	.line 635
	   18| invoke-static {v0}, java.lang.Integer.parseUnsignedInt(java.lang.String):int
	   21| move-result v1
	   22| return v1
}

method com.code_intelligence.jazzer.api.Jazzer.guideTowardsContainment(java.lang.String, java.lang.String, int):void
{
	.params "haystack", "needle", "id"
	.src "Jazzer.java"
	.line 524
	.line 524
	    0| sget-object v0, com.code_intelligence.jazzer.api.Jazzer.TRACE_STRSTR
	    2| if-nez v0, Label_1
	.line 525
	    4| return-void
Label_1:
	.try_begin_1
	.line 528
	    5| invoke-polymorphic {v0,v1,v2,v3}, java.lang.invoke.MethodHandle.invokeExact(java.lang.Object[]):java.lang.Object, (java.lang.String, java.lang.String, int):void
	.try_end_1
	  catch(...) : Label_2
	.line 531
	    9| goto Label_3
Label_2:
	.line 529
	   10| move-exception v0
	.line 530
	.local v0, "e", java.lang.Throwable
	   11| invoke-virtual {v0}, java.lang.Throwable.printStackTrace():void
Label_3:
	.line 532
	.end_local v0
	   14| return-void
}

method com.code_intelligence.jazzer.api.Jazzer.guideTowardsEquality(java.lang.String, java.lang.String, int):void
{
	.params "current", "target", "id"
	.src "Jazzer.java"
	.line 477
	.line 477
	    0| sget-object v0, com.code_intelligence.jazzer.api.Jazzer.TRACE_STRCMP
	    2| if-nez v0, Label_1
	.line 478
	    4| return-void
Label_1:
	.line 481
	    5| const/4 v1, #+1 (0x00000001 | 1.40130e-45)
	.try_begin_1
	    6| invoke-polymorphic {v0,v2,v3,v1,v4}, java.lang.invoke.MethodHandle.invokeExact(java.lang.Object[]):java.lang.Object, (java.lang.String, java.lang.String, int, int):void
	.try_end_1
	  catch(...) : Label_2
	.line 484
	   10| goto Label_3
Label_2:
	.line 482
	   11| move-exception v0
	.line 483
	.local v0, "e", java.lang.Throwable
	   12| invoke-virtual {v0}, java.lang.Throwable.printStackTrace():void
Label_3:
	.line 485
	.end_local v0
	   15| return-void
}

method com.code_intelligence.jazzer.api.Jazzer.guideTowardsEquality(byte[], byte[], int):void
{
	.params "current", "target", "id"
	.src "Jazzer.java"
	.line 500
	.line 500
	    0| sget-object v0, com.code_intelligence.jazzer.api.Jazzer.TRACE_MEMCMP
	    2| if-nez v0, Label_1
	.line 501
	    4| return-void
Label_1:
	.line 504
	    5| const/4 v1, #+1 (0x00000001 | 1.40130e-45)
	.try_begin_1
	    6| invoke-polymorphic {v0,v2,v3,v1,v4}, java.lang.invoke.MethodHandle.invokeExact(java.lang.Object[]):java.lang.Object, (byte[], byte[], int, int):void
	.try_end_1
	  catch(...) : Label_2
	.line 507
	   10| goto Label_3
Label_2:
	.line 505
	   11| move-exception v0
	.line 506
	.local v0, "e", java.lang.Throwable
	   12| invoke-virtual {v0}, java.lang.Throwable.printStackTrace():void
Label_3:
	.line 508
	.end_local v0
	   15| return-void
}

method com.code_intelligence.jazzer.api.Jazzer.onFuzzTargetReady(java.lang.Runnable):void
{
	.try_begin_1
	.params "callback"
	.src "Jazzer.java"
	.line 620
	.line 620
	    0| sget-object v0, com.code_intelligence.jazzer.api.Jazzer.ON_FUZZ_TARGET_READY
	    2| invoke-polymorphic {v0,v1}, java.lang.invoke.MethodHandle.invokeExact(java.lang.Object[]):java.lang.Object, (java.lang.Runnable):void
	.try_end_1
	  catch(...) : Label_1
	.line 623
	    6| goto Label_2
Label_1:
	.line 621
	    7| move-exception v0
	.line 622
	.local v0, "e", java.lang.Throwable
	    8| invoke-virtual {v0}, java.lang.Throwable.printStackTrace():void
Label_2:
	.line 624
	.end_local v0
	   11| return-void
}

method com.code_intelligence.jazzer.api.Jazzer.reportFindingFromHook(java.lang.Throwable):void
{
	.try_begin_1
	.params "finding"
	.src "Jazzer.java"
	.line 594
	.line 594
	    0| sget-object v0, com.code_intelligence.jazzer.api.Jazzer.JAZZER_INTERNAL
	    2| const-string v1, "reportFindingFromHook"
	    4| const/4 v2, #+1 (0x00000001 | 1.40130e-45)
	    5| new-array v2, v2, java.lang.Class[]
	    7| const-class v3, java.lang.Throwable
	    9| const/4 v4, #+0 (0x00000000 | 0.00000)
	   10| aput-object v3, v2, v4
	   12| invoke-virtual {v0,v1,v2}, java.lang.Class.getMethod(java.lang.String, java.lang.Class[]):java.lang.reflect.Method
	   15| move-result-object v0
	   16| filled-new-array {v5}, java.lang.Object[]
	   19| move-result-object v1
	   20| const/4 v2, #+0 (0x00000000 | 0.00000)
	   21| invoke-virtual {v0,v2,v1}, java.lang.reflect.Method.invoke(java.lang.Object, java.lang.Object[]):java.lang.Object
	.try_end_1
	  catch(java.lang.NullPointerException) : Label_3
	  catch(java.lang.IllegalAccessException) : Label_3
	  catch(java.lang.NoSuchMethodException) : Label_3
	  catch(java.lang.reflect.InvocationTargetException) : Label_1
	   24| goto Label_4
Label_1:
	.line 599
	   25| move-exception v0
	.line 602
	.local v0, "e", java.lang.reflect.InvocationTargetException
	   26| invoke-virtual {v0}, java.lang.reflect.InvocationTargetException.getCause():java.lang.Throwable
	   29| move-result-object v1
	   30| invoke-virtual {v1}, java.lang.Object.getClass():java.lang.Class
	   33| move-result-object v1
	   34| invoke-virtual {v1}, java.lang.Class.getName():java.lang.String
	   37| move-result-object v1
	   38| const-string v2, ".HardToCatchError"
	   40| invoke-virtual {v1,v2}, java.lang.String.endsWith(java.lang.String):boolean
	   43| move-result v1
	   44| if-nez v1, Label_2
	.line 605
	   46| invoke-virtual {v0}, java.lang.reflect.InvocationTargetException.printStackTrace():void
	   49| goto Label_5
Label_2:
	.line 603
	   50| invoke-virtual {v0}, java.lang.reflect.InvocationTargetException.getCause():java.lang.Throwable
	   53| move-result-object v1
	   54| check-cast v1, java.lang.Error
	   56| throw v1
Label_3:
	.line 595
	.end_local v0
	   57| move-exception v0
	.line 598
	.local v0, "e", java.lang.Exception
	   58| invoke-static {v5}, com.code_intelligence.jazzer.api.Jazzer.rethrowUnchecked(java.lang.Throwable):void
Label_4:
	.line 607
	.end_local v0
	   61| nop
Label_5:
	.line 608
	   62| return-void
}

method com.code_intelligence.jazzer.api.Jazzer.rethrowUnchecked(java.lang.Throwable):void
{
	.params "t"
	.src "Jazzer.java"
	.line 641
	.line 641
	    0| throw v0
}

method com.code_intelligence.jazzer.api.MethodHook.additionalClassesToHook():java.lang.String[]
{
}

method com.code_intelligence.jazzer.api.MethodHook.targetClassName():java.lang.String
{
}

method com.code_intelligence.jazzer.api.MethodHook.targetMethod():java.lang.String
{
}

method com.code_intelligence.jazzer.api.MethodHook.targetMethodDescriptor():java.lang.String
{
}

method com.code_intelligence.jazzer.api.MethodHook.type():com.code_intelligence.jazzer.api.HookType
{
}

method com.code_intelligence.jazzer.api.MethodHooks.value():com.code_intelligence.jazzer.api.MethodHook[]
{
}

method com.example.ExampleJavaHelper.<init>():void
{
	.src "ExampleJavaHelper.java"
	.line 7
	.line 7
	    0| invoke-direct {v1}, java.lang.Object.<init>():void
	    3| const/4 v0, #+5 (0x00000005 | 7.00649e-45)
	    4| invoke-static {v0}, com.code_intelligence.jazzer.runtime.CoverageMap.recordCoverage(int):void
	    7| return-void
}

method com.example.ExampleJavaHelper.fuzzerTestOneInput(byte[]):void
{
	.try_begin_1
	.params "data"
	.src "ExampleJavaHelper.java"
	.line 10
	.line 10
	    0| new-instance v0, java.io.ObjectInputStream
	    2| new-instance v1, java.io.ByteArrayInputStream
	    4| invoke-direct {v1,v5}, java.io.ByteArrayInputStream.<init>(byte[]):void
	    7| filled-new-array {v1}, java.lang.Object[]
	   10| move-result-object v1
	   11| const v2, #+590768839 (0x23366ac7 | 9.88885e-18)
	   14| const/4 v3, #+0 (0x00000000 | 0.00000)
	   15| invoke-static {v3,v3,v1,v2}, com.code_intelligence.jazzer.sanitizers.Deserialization.objectInputStreamInitBeforeHook(java.lang.invoke.MethodHandle, java.lang.Object, java.lang.Object[], int):void
	   18| const/4 v2, #+0 (0x00000000 | 0.00000)
	   19| aget-object v4, v1, v2
	   21| check-cast v4, java.io.InputStream
	   23| invoke-direct {v0,v4}, java.io.ObjectInputStream.<init>(java.io.InputStream):void
	   26| const v4, #-1337761839 (0xb0435fd1 | -7.10768e-10)
	   29| invoke-static {v3,v0,v1,v4,v3}, com.code_intelligence.jazzer.sanitizers.Deserialization.objectInputStreamInitAfterHook(java.lang.invoke.MethodHandle, java.io.ObjectInputStream, java.lang.Object[], int, java.lang.Object):void
	   32| const/4 v1, #+6 (0x00000006 | 8.40779e-45)
	   33| invoke-static {v1}, com.code_intelligence.jazzer.runtime.CoverageMap.recordCoverage(int):void
	.line 11
	.local v0, "ois", java.io.ObjectInputStream
	   36| new-array v1, v2, java.lang.Object[]
	   38| const-method-handle v2, readObject
	   40| const v3, #+1356244862 (0x50d6a77e | 2.88104e+10)
	   43| invoke-static {v2,v0,v1,v3}, com.code_intelligence.jazzer.sanitizers.Deserialization.readObjectBeforeHook(java.lang.invoke.MethodHandle, java.io.ObjectInputStream, java.lang.Object[], int):void
	   46| invoke-virtual {v0}, java.io.ObjectInputStream.readObject():java.lang.Object
	.try_end_1
	  catch(java.io.IOException) : Label_2
	  catch(java.lang.ClassNotFoundException) : Label_2
	  catch(java.lang.NullPointerException) : Label_1
	  catch(java.lang.NegativeArraySizeException) : Label_1
	.line 16
	   49| nop
	.end_local v0
	   50| const/4 v0, #+7 (0x00000007 | 9.80909e-45)
	   51| goto Label_3
Label_1:
	.line 14
	   52| move-exception v0
	   53| const/16 v0, #+9 (0x00000009 | 1.26117e-44)
	   55| invoke-static {v0}, com.code_intelligence.jazzer.runtime.CoverageMap.recordCoverage(int):void
	   58| goto Label_4
Label_2:
	.line 12
	   59| move-exception v0
	.line 16
	   60| const/16 v0, #+8 (0x00000008 | 1.12104e-44)
Label_3:
	   62| invoke-static {v0}, com.code_intelligence.jazzer.runtime.CoverageMap.recordCoverage(int):void
Label_4:
	.line 17
	   65| const/16 v0, #+10 (0x0000000a | 1.40130e-44)
	   67| invoke-static {v0}, com.code_intelligence.jazzer.runtime.CoverageMap.recordCoverage(int):void
	   70| return-void
}

method com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.<init>(java.lang.String):void
{
	.params "can"
	.src "CannedFuzzedDataProvider.java"
	.line 30
	.line 30
	    0| invoke-direct {v5}, java.lang.Object.<init>():void
	.line 31
	    3| invoke-static {}, java.util.Base64.getDecoder():java.util.Base64$Decoder
	    6| move-result-object v0
	    7| invoke-virtual {v0,v6}, java.util.Base64$Decoder.decode(java.lang.String):byte[]
	   10| move-result-object v0
	.try_begin_1
	.line 33
	.local v0, "rawIn", byte[]
	   11| new-instance v1, java.io.ByteArrayInputStream
	   13| invoke-direct {v1,v0}, java.io.ByteArrayInputStream.<init>(byte[]):void
	.try_end_1
	  catch(java.io.IOException) : Label_7
	  catch(java.lang.ClassNotFoundException) : Label_7
	.try_begin_2
	.line 34
	.local v1, "byteStream", java.io.ByteArrayInputStream
	   16| new-instance v2, java.io.ObjectInputStream
	   18| invoke-direct {v2,v1}, java.io.ObjectInputStream.<init>(java.io.InputStream):void
	.try_end_2
	  catch(...) : Label_4
	.try_begin_3
	.line 35
	.local v2, "objectStream", java.io.ObjectInputStream
	   21| invoke-virtual {v2}, java.io.ObjectInputStream.readObject():java.lang.Object
	   24| move-result-object v3
	   25| check-cast v3, java.util.ArrayList
	.try_end_3
	  catch(...) : Label_1
	.try_begin_4
	.line 36
	.local_ex v3, "recordedReplies", java.util.ArrayList, "Ljava/util/ArrayList<Ljava/lang/Object;>;"
	   27| invoke-virtual {v2}, java.io.ObjectInputStream.close():void
	.try_end_4
	  catch(...) : Label_4
	.try_begin_5
	.line 37
	.end_local v2
	   30| invoke-virtual {v1}, java.io.ByteArrayInputStream.close():void
	.try_end_5
	  catch(java.io.IOException) : Label_7
	  catch(java.lang.ClassNotFoundException) : Label_7
	.line 39
	.end_local v1
	   33| nop
	.line 40
	   34| invoke-virtual {v3}, java.util.ArrayList.iterator():java.util.Iterator
	   37| move-result-object v1
	   38| iput-object v1, v5, com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.nextReply
	.line 41
	   40| return-void
Label_1:
	.line 34
	.end_local v3
	.restart_local v1
	.restart_local v2
	   41| move-exception v3
	.try_begin_6
	   42| invoke-virtual {v2}, java.io.ObjectInputStream.close():void
	.try_end_6
	  catch(...) : Label_2
	   45| goto Label_3
Label_2:
	   46| move-exception v4
	.try_begin_7
	   47| invoke-virtual {v3,v4}, java.lang.Throwable.addSuppressed(java.lang.Throwable):void
Label_3:
	.end_local v0
	.end_local v1
	.end_local v5
	.end_local v6
	   50| throw v3
	.try_end_7
	  catch(...) : Label_4
Label_4:
	.line 33
	.end_local v2
	.restart_local v0
	.restart_local v1
	.restart_local v5
	.restart_local v6
	   51| move-exception v2
	.try_begin_8
	   52| invoke-virtual {v1}, java.io.ByteArrayInputStream.close():void
	.try_end_8
	  catch(...) : Label_5
	   55| goto Label_6
Label_5:
	   56| move-exception v3
	.try_begin_9
	   57| invoke-virtual {v2,v3}, java.lang.Throwable.addSuppressed(java.lang.Throwable):void
Label_6:
	.end_local v0
	.end_local v5
	.end_local v6
	   60| throw v2
	.try_end_9
	  catch(java.io.IOException) : Label_7
	  catch(java.lang.ClassNotFoundException) : Label_7
Label_7:
	.line 37
	.end_local v1
	.restart_local v0
	.restart_local v5
	.restart_local v6
	   61| move-exception v1
	.line 38
	.local v1, "e", java.lang.Exception
	   62| new-instance v2, java.lang.RuntimeException
	   64| invoke-direct {v2,v1}, java.lang.RuntimeException.<init>(java.lang.Throwable):void
	   67| throw v2
}

method com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.create(java.util.List):com.code_intelligence.jazzer.api.CannedFuzzedDataProvider
{
	.try_begin_1
	.params "?"
	.src "CannedFuzzedDataProvider.java"
	.line 45
	.line 45
	.local_ex v4, "objects", java.util.List, "Ljava/util/List<Ljava/lang/Object;>;"
	    0| new-instance v0, java.io.ByteArrayOutputStream
	    2| invoke-direct {v0}, java.io.ByteArrayOutputStream.<init>():void
	.try_end_1
	  catch(java.io.IOException) : Label_7
	.try_begin_2
	.line 46
	.local v0, "bout", java.io.ByteArrayOutputStream
	    5| new-instance v1, java.io.ObjectOutputStream
	    7| invoke-direct {v1,v0}, java.io.ObjectOutputStream.<init>(java.io.OutputStream):void
	.try_end_2
	  catch(...) : Label_4
	.try_begin_3
	.line 47
	.local v1, "out", java.io.ObjectOutputStream
	   10| new-instance v2, java.util.ArrayList
	   12| invoke-direct {v2,v4}, java.util.ArrayList.<init>(java.util.Collection):void
	   15| invoke-virtual {v1,v2}, java.io.ObjectOutputStream.writeObject(java.lang.Object):void
	.line 48
	   18| invoke-static {}, java.util.Base64.getEncoder():java.util.Base64$Encoder
	   21| move-result-object v2
	   22| invoke-virtual {v0}, java.io.ByteArrayOutputStream.toByteArray():byte[]
	   25| move-result-object v3
	   26| invoke-virtual {v2,v3}, java.util.Base64$Encoder.encodeToString(byte[]):java.lang.String
	   29| move-result-object v2
	.line 49
	.local v2, "base64", java.lang.String
	   30| new-instance v3, com.code_intelligence.jazzer.api.CannedFuzzedDataProvider
	   32| invoke-direct {v3,v2}, com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.<init>(java.lang.String):void
	.try_end_3
	  catch(...) : Label_1
	.try_begin_4
	.line 50
	   35| invoke-virtual {v1}, java.io.ObjectOutputStream.close():void
	.try_end_4
	  catch(...) : Label_4
	.try_begin_5
	.line 51
	   38| invoke-virtual {v0}, java.io.ByteArrayOutputStream.close():void
	.try_end_5
	  catch(java.io.IOException) : Label_7
	.line 49
	   41| return-object v3
Label_1:
	.line 46
	.end_local v2
	   42| move-exception v2
	.try_begin_6
	   43| invoke-virtual {v1}, java.io.ObjectOutputStream.close():void
	.try_end_6
	  catch(...) : Label_2
	   46| goto Label_3
Label_2:
	   47| move-exception v3
	.try_begin_7
	   48| invoke-virtual {v2,v3}, java.lang.Throwable.addSuppressed(java.lang.Throwable):void
Label_3:
	.end_local v0
	.end_local v4
	   51| throw v2
	.try_end_7
	  catch(...) : Label_4
Label_4:
	.line 45
	.end_local v1
	.restart_local v0
	.restart_local v4
	   52| move-exception v1
	.try_begin_8
	   53| invoke-virtual {v0}, java.io.ByteArrayOutputStream.close():void
	.try_end_8
	  catch(...) : Label_5
	   56| goto Label_6
Label_5:
	   57| move-exception v2
	.try_begin_9
	   58| invoke-virtual {v1,v2}, java.lang.Throwable.addSuppressed(java.lang.Throwable):void
Label_6:
	.end_local v4
	   61| throw v1
	.try_end_9
	  catch(java.io.IOException) : Label_7
Label_7:
	.line 52
	.end_local v0
	.restart_local v4
	   62| move-exception v0
	.line 53
	.local v0, "e", java.io.IOException
	   63| new-instance v1, java.lang.IllegalStateException
	   65| invoke-direct {v1,v0}, java.lang.IllegalStateException.<init>(java.lang.Throwable):void
	   68| throw v1
}

method com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.consumeAsciiString(int):java.lang.String
{
	.params "maxLength"
	.src "CannedFuzzedDataProvider.java"
	.line 179
	.line 179
	    0| iget-object v0, v1, com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.nextReply
	    2| invoke-interface {v0}, java.util.Iterator.next():java.lang.Object
	    5| move-result-object v0
	    6| check-cast v0, java.lang.String
	    8| return-object v0
}

method com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.consumeBoolean():boolean
{
	.src "CannedFuzzedDataProvider.java"
	.line 59
	.line 59
	    0| iget-object v0, v1, com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.nextReply
	    2| invoke-interface {v0}, java.util.Iterator.next():java.lang.Object
	    5| move-result-object v0
	    6| check-cast v0, java.lang.Boolean
	    8| invoke-virtual {v0}, java.lang.Boolean.booleanValue():boolean
	   11| move-result v0
	   12| return v0
}

method com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.consumeBooleans(int):boolean[]
{
	.params "maxLength"
	.src "CannedFuzzedDataProvider.java"
	.line 64
	.line 64
	    0| iget-object v0, v1, com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.nextReply
	    2| invoke-interface {v0}, java.util.Iterator.next():java.lang.Object
	    5| move-result-object v0
	    6| check-cast v0, boolean[]
	    8| return-object v0
}

method com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.consumeByte():byte
{
	.src "CannedFuzzedDataProvider.java"
	.line 69
	.line 69
	    0| iget-object v0, v1, com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.nextReply
	    2| invoke-interface {v0}, java.util.Iterator.next():java.lang.Object
	    5| move-result-object v0
	    6| check-cast v0, java.lang.Byte
	    8| invoke-virtual {v0}, java.lang.Byte.byteValue():byte
	   11| move-result v0
	   12| return v0
}

method com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.consumeByte(byte, byte):byte
{
	.params "min", "max"
	.src "CannedFuzzedDataProvider.java"
	.line 74
	.line 74
	    0| iget-object v0, v1, com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.nextReply
	    2| invoke-interface {v0}, java.util.Iterator.next():java.lang.Object
	    5| move-result-object v0
	    6| check-cast v0, java.lang.Byte
	    8| invoke-virtual {v0}, java.lang.Byte.byteValue():byte
	   11| move-result v0
	   12| return v0
}

method com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.consumeBytes(int):byte[]
{
	.params "maxLength"
	.src "CannedFuzzedDataProvider.java"
	.line 199
	.line 199
	    0| iget-object v0, v1, com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.nextReply
	    2| invoke-interface {v0}, java.util.Iterator.next():java.lang.Object
	    5| move-result-object v0
	    6| check-cast v0, byte[]
	    8| return-object v0
}

method com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.consumeChar():char
{
	.src "CannedFuzzedDataProvider.java"
	.line 164
	.line 164
	    0| iget-object v0, v1, com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.nextReply
	    2| invoke-interface {v0}, java.util.Iterator.next():java.lang.Object
	    5| move-result-object v0
	    6| check-cast v0, java.lang.Character
	    8| invoke-virtual {v0}, java.lang.Character.charValue():char
	   11| move-result v0
	   12| return v0
}

method com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.consumeChar(char, char):char
{
	.params "min", "max"
	.src "CannedFuzzedDataProvider.java"
	.line 169
	.line 169
	    0| iget-object v0, v1, com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.nextReply
	    2| invoke-interface {v0}, java.util.Iterator.next():java.lang.Object
	    5| move-result-object v0
	    6| check-cast v0, java.lang.Character
	    8| invoke-virtual {v0}, java.lang.Character.charValue():char
	   11| move-result v0
	   12| return v0
}

method com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.consumeCharNoSurrogates():char
{
	.src "CannedFuzzedDataProvider.java"
	.line 174
	.line 174
	    0| iget-object v0, v1, com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.nextReply
	    2| invoke-interface {v0}, java.util.Iterator.next():java.lang.Object
	    5| move-result-object v0
	    6| check-cast v0, java.lang.Character
	    8| invoke-virtual {v0}, java.lang.Character.charValue():char
	   11| move-result v0
	   12| return v0
}

method com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.consumeDouble():double
{
	.src "CannedFuzzedDataProvider.java"
	.line 144
	.line 144
	    0| iget-object v0, v2, com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.nextReply
	    2| invoke-interface {v0}, java.util.Iterator.next():java.lang.Object
	    5| move-result-object v0
	    6| check-cast v0, java.lang.Double
	    8| invoke-virtual {v0}, java.lang.Double.doubleValue():double
	   11| move-result-wide v0:v1
	   12| return-wide v0:v1
}

method com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.consumeFloat():float
{
	.src "CannedFuzzedDataProvider.java"
	.line 124
	.line 124
	    0| iget-object v0, v1, com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.nextReply
	    2| invoke-interface {v0}, java.util.Iterator.next():java.lang.Object
	    5| move-result-object v0
	    6| check-cast v0, java.lang.Float
	    8| invoke-virtual {v0}, java.lang.Float.floatValue():float
	   11| move-result v0
	   12| return v0
}

method com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.consumeInt():int
{
	.src "CannedFuzzedDataProvider.java"
	.line 94
	.line 94
	    0| iget-object v0, v1, com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.nextReply
	    2| invoke-interface {v0}, java.util.Iterator.next():java.lang.Object
	    5| move-result-object v0
	    6| check-cast v0, java.lang.Integer
	    8| invoke-virtual {v0}, java.lang.Integer.intValue():int
	   11| move-result v0
	   12| return v0
}

method com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.consumeInt(int, int):int
{
	.params "min", "max"
	.src "CannedFuzzedDataProvider.java"
	.line 99
	.line 99
	    0| iget-object v0, v1, com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.nextReply
	    2| invoke-interface {v0}, java.util.Iterator.next():java.lang.Object
	    5| move-result-object v0
	    6| check-cast v0, java.lang.Integer
	    8| invoke-virtual {v0}, java.lang.Integer.intValue():int
	   11| move-result v0
	   12| return v0
}

method com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.consumeInts(int):int[]
{
	.params "maxLength"
	.src "CannedFuzzedDataProvider.java"
	.line 104
	.line 104
	    0| iget-object v0, v1, com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.nextReply
	    2| invoke-interface {v0}, java.util.Iterator.next():java.lang.Object
	    5| move-result-object v0
	    6| check-cast v0, int[]
	    8| return-object v0
}

method com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.consumeLong():long
{
	.src "CannedFuzzedDataProvider.java"
	.line 109
	.line 109
	    0| iget-object v0, v2, com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.nextReply
	    2| invoke-interface {v0}, java.util.Iterator.next():java.lang.Object
	    5| move-result-object v0
	    6| check-cast v0, java.lang.Long
	    8| invoke-virtual {v0}, java.lang.Long.longValue():long
	   11| move-result-wide v0:v1
	   12| return-wide v0:v1
}

method com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.consumeLong(long, long):long
{
	.params "min", "max"
	.src "CannedFuzzedDataProvider.java"
	.line 114
	.line 114
	    0| iget-object v0, v2, com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.nextReply
	    2| invoke-interface {v0}, java.util.Iterator.next():java.lang.Object
	    5| move-result-object v0
	    6| check-cast v0, java.lang.Long
	    8| invoke-virtual {v0}, java.lang.Long.longValue():long
	   11| move-result-wide v0:v1
	   12| return-wide v0:v1
}

method com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.consumeLongs(int):long[]
{
	.params "maxLength"
	.src "CannedFuzzedDataProvider.java"
	.line 119
	.line 119
	    0| iget-object v0, v1, com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.nextReply
	    2| invoke-interface {v0}, java.util.Iterator.next():java.lang.Object
	    5| move-result-object v0
	    6| check-cast v0, long[]
	    8| return-object v0
}

method com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.consumeProbabilityDouble():double
{
	.src "CannedFuzzedDataProvider.java"
	.line 159
	.line 159
	    0| iget-object v0, v2, com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.nextReply
	    2| invoke-interface {v0}, java.util.Iterator.next():java.lang.Object
	    5| move-result-object v0
	    6| check-cast v0, java.lang.Double
	    8| invoke-virtual {v0}, java.lang.Double.doubleValue():double
	   11| move-result-wide v0:v1
	   12| return-wide v0:v1
}

method com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.consumeProbabilityFloat():float
{
	.src "CannedFuzzedDataProvider.java"
	.line 139
	.line 139
	    0| iget-object v0, v1, com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.nextReply
	    2| invoke-interface {v0}, java.util.Iterator.next():java.lang.Object
	    5| move-result-object v0
	    6| check-cast v0, java.lang.Float
	    8| invoke-virtual {v0}, java.lang.Float.floatValue():float
	   11| move-result v0
	   12| return v0
}

method com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.consumeRegularDouble():double
{
	.src "CannedFuzzedDataProvider.java"
	.line 154
	.line 154
	    0| iget-object v0, v2, com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.nextReply
	    2| invoke-interface {v0}, java.util.Iterator.next():java.lang.Object
	    5| move-result-object v0
	    6| check-cast v0, java.lang.Double
	    8| invoke-virtual {v0}, java.lang.Double.doubleValue():double
	   11| move-result-wide v0:v1
	   12| return-wide v0:v1
}

method com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.consumeRegularDouble(double, double):double
{
	.params "min", "max"
	.src "CannedFuzzedDataProvider.java"
	.line 149
	.line 149
	    0| iget-object v0, v2, com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.nextReply
	    2| invoke-interface {v0}, java.util.Iterator.next():java.lang.Object
	    5| move-result-object v0
	    6| check-cast v0, java.lang.Double
	    8| invoke-virtual {v0}, java.lang.Double.doubleValue():double
	   11| move-result-wide v0:v1
	   12| return-wide v0:v1
}

method com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.consumeRegularFloat():float
{
	.src "CannedFuzzedDataProvider.java"
	.line 129
	.line 129
	    0| iget-object v0, v1, com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.nextReply
	    2| invoke-interface {v0}, java.util.Iterator.next():java.lang.Object
	    5| move-result-object v0
	    6| check-cast v0, java.lang.Float
	    8| invoke-virtual {v0}, java.lang.Float.floatValue():float
	   11| move-result v0
	   12| return v0
}

method com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.consumeRegularFloat(float, float):float
{
	.params "min", "max"
	.src "CannedFuzzedDataProvider.java"
	.line 134
	.line 134
	    0| iget-object v0, v1, com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.nextReply
	    2| invoke-interface {v0}, java.util.Iterator.next():java.lang.Object
	    5| move-result-object v0
	    6| check-cast v0, java.lang.Float
	    8| invoke-virtual {v0}, java.lang.Float.floatValue():float
	   11| move-result v0
	   12| return v0
}

method com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.consumeRemainingAsAsciiString():java.lang.String
{
	.src "CannedFuzzedDataProvider.java"
	.line 189
	.line 189
	    0| iget-object v0, v1, com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.nextReply
	    2| invoke-interface {v0}, java.util.Iterator.next():java.lang.Object
	    5| move-result-object v0
	    6| check-cast v0, java.lang.String
	    8| return-object v0
}

method com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.consumeRemainingAsBytes():byte[]
{
	.src "CannedFuzzedDataProvider.java"
	.line 204
	.line 204
	    0| iget-object v0, v1, com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.nextReply
	    2| invoke-interface {v0}, java.util.Iterator.next():java.lang.Object
	    5| move-result-object v0
	    6| check-cast v0, byte[]
	    8| return-object v0
}

method com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.consumeRemainingAsString():java.lang.String
{
	.src "CannedFuzzedDataProvider.java"
	.line 194
	.line 194
	    0| iget-object v0, v1, com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.nextReply
	    2| invoke-interface {v0}, java.util.Iterator.next():java.lang.Object
	    5| move-result-object v0
	    6| check-cast v0, java.lang.String
	    8| return-object v0
}

method com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.consumeShort():short
{
	.src "CannedFuzzedDataProvider.java"
	.line 79
	.line 79
	    0| iget-object v0, v1, com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.nextReply
	    2| invoke-interface {v0}, java.util.Iterator.next():java.lang.Object
	    5| move-result-object v0
	    6| check-cast v0, java.lang.Short
	    8| invoke-virtual {v0}, java.lang.Short.shortValue():short
	   11| move-result v0
	   12| return v0
}

method com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.consumeShort(short, short):short
{
	.params "min", "max"
	.src "CannedFuzzedDataProvider.java"
	.line 84
	.line 84
	    0| iget-object v0, v1, com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.nextReply
	    2| invoke-interface {v0}, java.util.Iterator.next():java.lang.Object
	    5| move-result-object v0
	    6| check-cast v0, java.lang.Short
	    8| invoke-virtual {v0}, java.lang.Short.shortValue():short
	   11| move-result v0
	   12| return v0
}

method com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.consumeShorts(int):short[]
{
	.params "maxLength"
	.src "CannedFuzzedDataProvider.java"
	.line 89
	.line 89
	    0| iget-object v0, v1, com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.nextReply
	    2| invoke-interface {v0}, java.util.Iterator.next():java.lang.Object
	    5| move-result-object v0
	    6| check-cast v0, short[]
	    8| return-object v0
}

method com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.consumeString(int):java.lang.String
{
	.params "maxLength"
	.src "CannedFuzzedDataProvider.java"
	.line 184
	.line 184
	    0| iget-object v0, v1, com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.nextReply
	    2| invoke-interface {v0}, java.util.Iterator.next():java.lang.Object
	    5| move-result-object v0
	    6| check-cast v0, java.lang.String
	    8| return-object v0
}

method com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.remainingBytes():int
{
	.src "CannedFuzzedDataProvider.java"
	.line 209
	.line 209
	    0| iget-object v0, v1, com.code_intelligence.jazzer.api.CannedFuzzedDataProvider.nextReply
	    2| invoke-interface {v0}, java.util.Iterator.next():java.lang.Object
	    5| move-result-object v0
	    6| check-cast v0, java.lang.Integer
	    8| invoke-virtual {v0}, java.lang.Integer.intValue():int
	   11| move-result v0
	   12| return v0
}
