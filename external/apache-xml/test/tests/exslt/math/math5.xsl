<?xml version="1.0"?> 
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:math="http://exslt.org/math"
                extension-element-prefixes="math">

<!-- Test math:atan2() -->

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

      ArcTan2 value of zero is:
      <xsl:value-of select="math:atan2($zero)"/><br/>
      ArcTan2 value of nzero is:
      <xsl:value-of select="math:atan2($nzero)"/><br/>
      ArcTan2 value of num1 is:
      <xsl:value-of select="math:atan2($num1)"/><br/>
      ArcTan2 value of num2 is:
      <xsl:value-of select="math:atan2($num2)"/><br/>
      ArcTan2 value of temp1 is:
      <xsl:value-of select="math:atan2($temp1)"/><br/>
      ArcTan2 value of temp2 is:
      <xsl:value-of select="math:atan2($temp2)"/><br/>
      ArcTan2 value of rad1 is:
      <xsl:value-of select="math:atan2($rad1)"/><br/>
      ArcTan2 value of rad2 is:
      <xsl:value-of select="math:atan2($rad2)"/><br/>
      ArcTan2 value of rad3 is:
      <xsl:value-of select="math:atan2($rad3)"/><br/>
      ArcTan2 value of rad4 is:
      <xsl:value-of select="math:atan2($rad4)"/><br/>
      ArcTan2 value of input1 number is:
      <xsl:value-of select="math:atan2($input1)"/><br/>
      ArcTan2 value of input2 number is:
      <xsl:value-of select="math:atan2($input2)"/><br/>
      ArcTan2 value of input3 number is:
      <xsl:value-of select="math:atan2($input3)"/>
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
