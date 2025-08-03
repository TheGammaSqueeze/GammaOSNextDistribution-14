<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
				xmlns:cnn="http://www.cnn.com">
	

<xsl:output method="xml"/>

  <!-- FileName:  -->
  <!-- Document: http://www.w3.org/TR/xpath -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 2.2 -->
  <!-- Purpose: Test has no exceptions. -->

<xsl:template match="/">
<root>
<data1 attr1="hello" attr2="goodbye">
<data2 attr1="goodbye" attr2="hello">
<data3 attr1="hello" attr2="goodbye">
<data4 attr1="goodbye" attr2="hello">
<data5 attr1="hello" attr2="goodbye">
<data6 attr1="goodbye" attr2="hello">
<data7 attr1="hello" attr2="goodbye">
<data8 attr1="goodbye" attr2="hello">
<data9 attr1="hello" attr2="bad">
<data10 attr1="goodbye" attr2="hello">
<data11 attr1="hello" attr2="goodbye">
<data12 attr1="goodbye" attr2="hello">
<data13 attr1="What in the world">
<data14 attr1="olleh" xyz:attr2="eybdoog" xmlns:xyz="http://www.xyz.com">Duck</data14>
</data13>
</data12>
</data11>
</data10>
</data9>
</data8>
</data7>
</data6>
</data5>
</data4>
</data3>
</data2>
</data1>
</root>
</xsl:template>


  <!--
   * Licensed to the Apache Software Foundation (ASF) under one
   * or more contributor license agreements. See the NOTICE file
   * distributed with this work for additional information
   * regarding copyright ownership. The ASF licenses this file
   * to you under the Apache License, Version 2.0 (the  "License");
   * you may not use this file except in compliance with the License.
   * You may obtain a copy of the License at
   *
   *     http://www.apache.org/licenses/LICENSE-2.0
   *
   * Unless required by applicable law or agreed to in writing, software
   * distributed under the License is distributed on an "AS IS" BASIS,
   * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   * See the License for the specific language governing permissions and
   * limitations under the License.
  -->

</xsl:stylesheet>
