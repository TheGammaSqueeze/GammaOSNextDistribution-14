<?xml version="1.0"?> 
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:math="http://exslt.org/math"
                extension-element-prefixes="math">

<!-- Test math:constant() -->

<xsl:variable name="cstant1" select='"PI"'/>
<xsl:variable name="cstant2" select='"E"'/>
<xsl:variable name="cstant3" select='"SQRRT2"'/>
<xsl:variable name="cstant4" select='"LN2"'/>
<xsl:variable name="cstant5" select='"LN10"'/>
<xsl:variable name="cstant6" select='"LOG2E"'/>
<xsl:variable name="cstant7" select='"SQRT1_2"'/>

<xsl:variable name="precision1" select="0"/>
<xsl:variable name="precision2" select="1"/>
<xsl:variable name="precision3" select="2"/>
<xsl:variable name="precision4" select="3"/>
<xsl:variable name="precision5" select="10"/>
<xsl:variable name="precision6" select="25"/>
<xsl:variable name="precision7" select="50"/>

<xsl:variable name="precision8" select="number(//number[1])"/>
<xsl:variable name="precision9" select="number(//number[2])"/>
<xsl:variable name="precision10" select="$precision8 div $precision1"/>

<xsl:template match="/">
   <group1>
      These are for PI:
      <xsl:value-of select="math:constant($cstant1,$precision1)"/>, 
      <xsl:value-of select="math:constant($cstant1,$precision2)"/>, 
      <xsl:value-of select="math:constant($cstant1,$precision3)"/>, 
      <xsl:value-of select="math:constant($cstant1,$precision4)"/>, 
      <xsl:value-of select="math:constant($cstant1,$precision5)"/>, 
      <xsl:value-of select="math:constant($cstant1,$precision6)"/>, 
      <xsl:value-of select="math:constant($cstant1,$precision7)"/>, 
      <xsl:value-of select="math:constant($cstant1,$precision8)"/>, 
      <xsl:value-of select="math:constant($cstant1,$precision9)"/>, 
      <xsl:value-of select="math:constant($cstant1,$precision10)"/>

   </group1>
   <group2>
      These are for E:
      <xsl:value-of select="math:constant($cstant2,$precision1)"/>, 
      <xsl:value-of select="math:constant($cstant2,$precision2)"/>, 
      <xsl:value-of select="math:constant($cstant2,$precision3)"/>, 
      <xsl:value-of select="math:constant($cstant2,$precision4)"/>, 
      <xsl:value-of select="math:constant($cstant2,$precision5)"/>, 
      <xsl:value-of select="math:constant($cstant2,$precision6)"/>, 
      <xsl:value-of select="math:constant($cstant2,$precision7)"/>, 
      <xsl:value-of select="math:constant($cstant2,$precision8)"/>, 
      <xsl:value-of select="math:constant($cstant2,$precision9)"/>, 
      <xsl:value-of select="math:constant($cstant2,$precision10)"/>, 

   </group2>
   <group3>
      These are for SQRRT2
      <xsl:value-of select="math:constant($cstant3,$precision1)"/>, 
      <xsl:value-of select="math:constant($cstant3,$precision2)"/>, 
      <xsl:value-of select="math:constant($cstant3,$precision3)"/>, 
      <xsl:value-of select="math:constant($cstant3,$precision4)"/>, 
      <xsl:value-of select="math:constant($cstant3,$precision5)"/>, 
      <xsl:value-of select="math:constant($cstant3,$precision6)"/>, 
      <xsl:value-of select="math:constant($cstant3,$precision7)"/>, 
      <xsl:value-of select="math:constant($cstant3,$precision8)"/>, 
      <xsl:value-of select="math:constant($cstant3,$precision9)"/>, 
      <xsl:value-of select="math:constant($cstant3,$precision10)"/>, 

   </group3>
   <group4>
      These are for LN2
      <xsl:value-of select="math:constant($cstant4,$precision1)"/>, 
      <xsl:value-of select="math:constant($cstant4,$precision2)"/>, 
      <xsl:value-of select="math:constant($cstant4,$precision3)"/>, 
      <xsl:value-of select="math:constant($cstant4,$precision4)"/>, 
      <xsl:value-of select="math:constant($cstant4,$precision5)"/>, 
      <xsl:value-of select="math:constant($cstant4,$precision6)"/>, 
      <xsl:value-of select="math:constant($cstant4,$precision7)"/>, 
      <xsl:value-of select="math:constant($cstant4,$precision8)"/>, 
      <xsl:value-of select="math:constant($cstant4,$precision9)"/>, 
      <xsl:value-of select="math:constant($cstant4,$precision10)"/>, 

   </group4>
   <group5>
      These are for LN10
      <xsl:value-of select="math:constant($cstant5,$precision1)"/>, 
      <xsl:value-of select="math:constant($cstant5,$precision2)"/>, 
      <xsl:value-of select="math:constant($cstant5,$precision3)"/>, 
      <xsl:value-of select="math:constant($cstant5,$precision4)"/>, 
      <xsl:value-of select="math:constant($cstant5,$precision5)"/>, 
      <xsl:value-of select="math:constant($cstant5,$precision6)"/>, 
      <xsl:value-of select="math:constant($cstant5,$precision7)"/>, 
      <xsl:value-of select="math:constant($cstant5,$precision8)"/>, 
      <xsl:value-of select="math:constant($cstant5,$precision9)"/>, 
      <xsl:value-of select="math:constant($cstant5,$precision10)"/>, 

   </group5>
   <group6>
      These are for LOG2E
      <xsl:value-of select="math:constant($cstant6,$precision1)"/>, 
      <xsl:value-of select="math:constant($cstant6,$precision2)"/>, 
      <xsl:value-of select="math:constant($cstant6,$precision3)"/>, 
      <xsl:value-of select="math:constant($cstant6,$precision4)"/>, 
      <xsl:value-of select="math:constant($cstant6,$precision5)"/>, 
      <xsl:value-of select="math:constant($cstant6,$precision6)"/>, 
      <xsl:value-of select="math:constant($cstant6,$precision7)"/>, 
      <xsl:value-of select="math:constant($cstant6,$precision8)"/>, 
      <xsl:value-of select="math:constant($cstant6,$precision9)"/>, 
      <xsl:value-of select="math:constant($cstant6,$precision10)"/>, 

   </group6>

   <group7>
      These are for SQRT1_2
      <xsl:value-of select="math:constant($cstant7,$precision1)"/>, 
      <xsl:value-of select="math:constant($cstant7,$precision2)"/>, 
      <xsl:value-of select="math:constant($cstant7,$precision3)"/>, 
      <xsl:value-of select="math:constant($cstant7,$precision4)"/>, 
      <xsl:value-of select="math:constant($cstant7,$precision5)"/>, 
      <xsl:value-of select="math:constant($cstant7,$precision6)"/>, 
      <xsl:value-of select="math:constant($cstant7,$precision7)"/>, 
      <xsl:value-of select="math:constant($cstant7,$precision8)"/>, 
      <xsl:value-of select="math:constant($cstant7,$precision9)"/>, 
      <xsl:value-of select="math:constant($cstant7,$precision10)"/>, 

   </group7>

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
