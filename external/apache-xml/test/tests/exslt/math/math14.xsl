<?xml version="1.0"?> 
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:math="http://exslt.org/math"
                extension-element-prefixes="math">

<!-- Test math:power() -->
<xsl:variable name="base1" select="10"/>
<xsl:variable name="base2" select="2.5"/>
<xsl:variable name="base3" select="1"/>
<xsl:variable name="base4" select="0"/>
<xsl:variable name="base5" select="-10"/>
<xsl:variable name="expon1" select="0"/>
<xsl:variable name="expon2" select="-0"/>
<xsl:variable name="expon3" select="10"/>
<xsl:variable name="expon4" select="2.5"/>
<xsl:variable name="expon5" select="-5"/>
<xsl:variable name="input1" select="number(//number[1])"/>
<xsl:variable name="input2" select="number(//number[2])"/>
<xsl:variable name="input3" select="$input1 div $base4"/>

<xsl:template match="/">
   <out>
   Using variable bases with power 0:<br/>
   <xsl:value-of select="$base1"/><xsl:text> to the power of </xsl:text><xsl:value-of select="$expon1"/><xsl:text> is </xsl:text>
   <xsl:value-of select="math:power($base1,$expon1)"/><br/>
   <xsl:value-of select="$base2"/><xsl:text> to the power of </xsl:text><xsl:value-of select="$expon1"/><xsl:text> is </xsl:text>
   <xsl:value-of select="math:power($base2,$expon1)"/><br/>
   <xsl:value-of select="$base3"/><xsl:text> to the power of </xsl:text><xsl:value-of select="$expon1"/><xsl:text> is </xsl:text>
   <xsl:value-of select="math:power($base3,$expon1)"/><br/>
   <xsl:value-of select="$base4"/><xsl:text> to the power of </xsl:text><xsl:value-of select="$expon1"/><xsl:text> is </xsl:text>
   <xsl:value-of select="math:power($base4,$expon1)"/><br/>
   <xsl:value-of select="$base5"/><xsl:text> to the power of </xsl:text><xsl:value-of select="$expon1"/><xsl:text> is </xsl:text>
   <xsl:value-of select="math:power($base5,$expon1)"/><br/>
   Using variable base with power -0:
   <xsl:value-of select="$base1"/><xsl:text> to the power of </xsl:text><xsl:value-of select="$expon2"/><xsl:text> is </xsl:text>
   <xsl:value-of select="math:power($base1,$expon2)"/><br/>
   <xsl:value-of select="$base2"/><xsl:text> to the power of </xsl:text><xsl:value-of select="$expon2"/><xsl:text> is </xsl:text>
   <xsl:value-of select="math:power($base2,$expon2)"/><br/>
   <xsl:value-of select="$base3"/><xsl:text> to the power of </xsl:text><xsl:value-of select="$expon2"/><xsl:text> is </xsl:text>
   <xsl:value-of select="math:power($base3,$expon2)"/><br/>
   <xsl:value-of select="$base4"/><xsl:text> to the power of </xsl:text><xsl:value-of select="$expon2"/><xsl:text> is </xsl:text>
   <xsl:value-of select="math:power($base4,$expon2)"/><br/>
   <xsl:value-of select="$base5"/><xsl:text> to the power of </xsl:text><xsl:value-of select="$expon2"/><xsl:text> is </xsl:text>
   <xsl:value-of select="math:power($base5,$expon2)"/><br/>
   Using variable base with power 10:
   <xsl:value-of select="$base1"/><xsl:text> to the power of </xsl:text><xsl:value-of select="$expon3"/><xsl:text> is </xsl:text>
   <xsl:value-of select="math:power($base1,$expon3)"/><br/>
   <xsl:value-of select="$base2"/><xsl:text> to the power of </xsl:text><xsl:value-of select="$expon3"/><xsl:text> is </xsl:text>
   <xsl:value-of select="math:power($base2,$expon3)"/><br/>
   <xsl:value-of select="$base3"/><xsl:text> to the power of </xsl:text><xsl:value-of select="$expon3"/><xsl:text> is </xsl:text>
   <xsl:value-of select="math:power($base3,$expon3)"/><br/>
   <xsl:value-of select="$base4"/><xsl:text> to the power of </xsl:text><xsl:value-of select="$expon3"/><xsl:text> is </xsl:text>
   <xsl:value-of select="math:power($base4,$expon3)"/><br/>
   <xsl:value-of select="$base5"/><xsl:text> to the power of </xsl:text><xsl:value-of select="$expon3"/><xsl:text> is </xsl:text>
   <xsl:value-of select="math:power($base5,$expon3)"/><br/>
   Using variable base with power 2:
   <xsl:value-of select="$base1"/><xsl:text> to the power of </xsl:text><xsl:value-of select="$expon4"/><xsl:text> is </xsl:text>
   <xsl:value-of select="math:power($base1,$expon4)"/><br/>
   <xsl:value-of select="$base2"/><xsl:text> to the power of </xsl:text><xsl:value-of select="$expon4"/><xsl:text> is </xsl:text>
   <xsl:value-of select="math:power($base2,$expon4)"/><br/>
   <xsl:value-of select="$base3"/><xsl:text> to the power of </xsl:text><xsl:value-of select="$expon4"/><xsl:text> is </xsl:text>
   <xsl:value-of select="math:power($base3,$expon4)"/><br/>
   <xsl:value-of select="$base4"/><xsl:text> to the power of </xsl:text><xsl:value-of select="$expon4"/><xsl:text> is </xsl:text>
   <xsl:value-of select="math:power($base4,$expon4)"/><br/>
   <xsl:value-of select="$base5"/><xsl:text> to the power of </xsl:text><xsl:value-of select="$expon4"/><xsl:text> is </xsl:text>
   <xsl:value-of select="math:power($base5,$expon4)"/><br/>
   Using variable base with power -5:
   <xsl:value-of select="$base1"/><xsl:text> to the power of </xsl:text><xsl:value-of select="$expon5"/><xsl:text> is </xsl:text>
   <xsl:value-of select="math:power($base1,$expon5)"/><br/>
   <xsl:value-of select="$base2"/><xsl:text> to the power of </xsl:text><xsl:value-of select="$expon5"/><xsl:text> is </xsl:text>
   <xsl:value-of select="math:power($base2,$expon5)"/><br/>
   <xsl:value-of select="$base3"/><xsl:text> to the power of </xsl:text><xsl:value-of select="$expon5"/><xsl:text> is </xsl:text>
   <xsl:value-of select="math:power($base3,$expon5)"/><br/>
   <xsl:value-of select="$base4"/><xsl:text> to the power of </xsl:text><xsl:value-of select="$expon5"/><xsl:text> is </xsl:text>
   <xsl:value-of select="math:power($base4,$expon5)"/><br/>
   <xsl:value-of select="$base5"/><xsl:text> to the power of </xsl:text><xsl:value-of select="$expon5"/><xsl:text> is </xsl:text>
   <xsl:value-of select="math:power($base5,$expon5)"/><br/>
   Using input as base with power <xsl:value-of select="$input1"/>:
   <xsl:value-of select="$input1"/><xsl:text> to the power of </xsl:text><xsl:value-of select="$input1"/><xsl:text> is </xsl:text>
   <xsl:value-of select="math:power($input1,$input1)"/><br/>
   <xsl:value-of select="$input2"/><xsl:text> to the power of </xsl:text><xsl:value-of select="$input1"/><xsl:text> is </xsl:text>
   <xsl:value-of select="math:power($input2,$input1)"/><br/>
   <xsl:value-of select="$input3"/><xsl:text> to the power of </xsl:text><xsl:value-of select="$input1"/><xsl:text> is </xsl:text>
   <xsl:value-of select="math:power($input3,$input1)"/><br/>
   Using input as base with power <xsl:value-of select="$input2"/>:
   <xsl:value-of select="$input1"/><xsl:text> to the power of </xsl:text><xsl:value-of select="$input2"/><xsl:text> is </xsl:text>
   <xsl:value-of select="math:power($input1,$input2)"/><br/>
   <xsl:value-of select="$input2"/><xsl:text> to the power of </xsl:text><xsl:value-of select="$input2"/><xsl:text> is </xsl:text>
   <xsl:value-of select="math:power($input2,$input2)"/><br/>
   <xsl:value-of select="$input3"/><xsl:text> to the power of </xsl:text><xsl:value-of select="$input2"/><xsl:text> is </xsl:text>
   <xsl:value-of select="math:power($input3,$input2)"/><br/>
   Using input as base with power <xsl:value-of select="$input3"/>:
   <xsl:value-of select="$input1"/><xsl:text> to the power of </xsl:text><xsl:value-of select="$input3"/><xsl:text> is </xsl:text>
   <xsl:value-of select="math:power($input1,$input3)"/><br/>
   <xsl:value-of select="$input2"/><xsl:text> to the power of </xsl:text><xsl:value-of select="$input3"/><xsl:text> is </xsl:text>
   <xsl:value-of select="math:power($input2,$input3)"/><br/>
   <xsl:value-of select="$input3"/><xsl:text> to the power of </xsl:text><xsl:value-of select="$input3"/><xsl:text> is </xsl:text>
   <xsl:value-of select="math:power($input3,$input3)"/><br/>
   
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
