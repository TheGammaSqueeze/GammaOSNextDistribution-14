<?xml version="1.0"?> 
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:math="http://exslt.org/math"
                extension-element-prefixes="math">

<!-- Test math:sqrt() -->
<xsl:variable name="num1" select="100"/>
<xsl:variable name="num2" select="20"/>
<xsl:variable name="num3" select="1"/>
<xsl:variable name="num4" select="0"/>
<xsl:variable name="num5" select="-10"/>

<xsl:template match="/">
   <out>
   Sqrt of <xsl:value-of select="$num1"/> is <xsl:value-of select="math:sqrt($num1)"/><br/> 
   Sqrt of <xsl:value-of select="$num2"/> is <xsl:value-of select="math:sqrt($num2)"/><br/> 
   Sqrt of <xsl:value-of select="$num3"/> is <xsl:value-of select="math:sqrt($num3)"/><br/> 
   Sqrt of <xsl:value-of select="$num4"/> is <xsl:value-of select="math:sqrt($num4)"/><br/> 
   Sqrt of <xsl:value-of select="$num5"/> is <xsl:value-of select="math:sqrt($num5)"/><br/> 
   Sqrt of <xsl:value-of select="number(//number[1])"/> is <xsl:value-of select="math:sqrt(number(//number[1]))"/><br/> 
   Sqrt of <xsl:value-of select="number(//number[2])"/> is <xsl:value-of select="math:sqrt(number(//number[2]))"/><br/> 
   Sqrt of <xsl:value-of select="number(//number[3])"/> is <xsl:value-of select="math:sqrt(number(//number[3]) div $num4)"/>
   
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
