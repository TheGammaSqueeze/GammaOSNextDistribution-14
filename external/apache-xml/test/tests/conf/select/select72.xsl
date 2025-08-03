<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: select72 -->
  <!-- Document: http://www.w3.org/TR/Xpath -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 3.3 Node Sets -->
  <!-- Purpose: NodeSet union using two copies of same node, and variables -->
  <!-- Creator: David Marston -->
  <!-- This could be used to establish that the variable is of the node-set type,
     as opposed to string or one-node RTF, where the count would go up. -->

<xsl:template match="/">
  <out>
    <xsl:variable name = "var1" select = "doc/sub1/child1" />
    <xsl:text>
Count of node-set: </xsl:text>
    <xsl:value-of select = "count($var1)"/>
    <xsl:variable name = "var2" select = "$var1|$var1" />
    <xsl:text>
Count of union: </xsl:text>
    <xsl:value-of select = "count($var2)"/>
  </out>
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
