<?xml version="1.0"?> 
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:math="http://exslt.org/math"
                extension-element-prefixes="math">

<!-- Test math:tan() -->

<xsl:variable name="zero" select="0"/>
<xsl:variable name="nzero" select="-0"/>
<xsl:variable name="num1" select="1.99"/>
<xsl:variable name="num2" select="3.1428475"/>
<xsl:variable name="temp1" select="-7"/>
<xsl:variable name="temp2" select="-9.99999"/>
<xsl:variable name="rad1" select="1.0"/>
<xsl:variable name="rad2" select="25"/>
<xsl:variable name="rad3" select="0.253"/>
<xsl:variable name="rad4" select="-0.888"/>
<xsl:variable name="input1" select="number(//number[1])"/>
<xsl:variable name="input2" select="number(//number[2])"/>
<xsl:variable name="input3" select="$input1 div $zero"/>

<xsl:template match="/">
   <out>
      Tan value of zero is:
      <xsl:value-of select="math:tan($zero)"/><br/>
      Tan value of nzero is:
      <xsl:value-of select="math:tan($nzero)"/><br/>
      Tan value of num1 is:
      <xsl:value-of select="math:tan($num1)"/><br/>
      Tan value of num2 is:
      <xsl:value-of select="math:tan($num2)"/><br/>
      Tan value of temp1 is:
      <xsl:value-of select="math:tan($temp1)"/><br/>
      Tan value of temp2 is:
      <xsl:value-of select="math:tan($temp2)"/><br/>
      Tan value of rad1 is:
      <xsl:value-of select="math:tan($rad1)"/><br/>
      Tan value of rad2 is:
      <xsl:value-of select="math:tan($rad2)"/><br/>
      Tan value of rad3 is:
      <xsl:value-of select="math:tan($rad3)"/><br/>
      Tan value of rad4 is:
      <xsl:value-of select="math:tan($rad4)"/><br/>
      Tan value of input1 is:
      <xsl:value-of select="math:tan($input1)"/><br/>
      Tan value of input2 is:
      <xsl:value-of select="math:tan($input2)"/><br/>
      Tan value of input3 is:
      <xsl:value-of select="math:tan($input3)"/>
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
