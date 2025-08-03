<?xml version="1.0"?> 
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:math="http://exslt.org/math"
                extension-element-prefixes="math">

<!-- Test math:exp() -->
<xsl:variable name="power1" select="5"/>
<xsl:variable name="power2" select="0.0"/>
<xsl:variable name="power3" select="-3"/>
<xsl:variable name="power4" select="math:constant(PI,$power1)"/>
<xsl:variable name="input1" select="number(//number[1])"/>
<xsl:variable name="input2" select="number(//number[2])"/>
<xsl:variable name="input3" select="$input1 div $zero"/>


<xsl:template match="/">
  <out>
     This is for testing the Euler constant to the power of <xsl:value-of select="$power1"/>:
     <xsl:value-of select="math:exp($power1)"/>
     This is for testing the Euler constant to the power of <xsl:value-of select="$power2"/>:
     <xsl:value-of select="math:exp($power2)"/>
     This is for testing the Euler constant to the power of <xsl:value-of select="$power3"/>:
     <xsl:value-of select="math:exp($power3)"/>
     This is for testing the Euler constant to the power of <xsl:value-of select="$power4"/>:
     <xsl:value-of select="math:exp($power4)"/>
     This is testing Euler constant to the power of <xsl:value-of select="$input1"/>:
     <xsl:value-of select="math:exp($input1)"/>
     This is testing Euler constant to the power of <xsl:value-of select="$input2"/>:
     <xsl:value-of select="math:exp($input2)"/>
     This is testing Euler constant to the power of <xsl:value-of select="$input3"/>:
     <xsl:value-of select="math:exp($input3)"/>

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
