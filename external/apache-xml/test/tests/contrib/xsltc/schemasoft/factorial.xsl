<?xml version="1.0"?>

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">



<!-- Author:  Chris Rathman -->

<!-- Reference:  http://www.angelfire.com/tx4/cus/notes/xslfactorial.html -->

<!-- Description:  Computes factorial as a demonstration of recursion -->



<xsl:output method="text"/>



<!-- x := 5 -->

<xsl:variable name="x" select="5"/>



<!-- y := factorial(x) -->

<xsl:variable name="y">

<xsl:call-template name="factorial">

<xsl:with-param name="n" select="$x"/>

</xsl:call-template>

</xsl:variable>



<!-- factorial(n) - compute the factorial of a number -->

<xsl:template name="factorial">

<xsl:param name="n" select="1"/>

<xsl:variable name="sum">

<xsl:if test="$n = 1">1</xsl:if>

<xsl:if test="$n != 1">

<xsl:call-template name="factorial">

<xsl:with-param name="n" select="$n - 1"/>

</xsl:call-template>

</xsl:if>

</xsl:variable>

<xsl:value-of select="$sum * $n"/>

</xsl:template>



<!-- output the results -->

<xsl:template match="/">

<xsl:text>factorial(</xsl:text>

<xsl:value-of select="$x"/>

<xsl:text>) = </xsl:text>

<xsl:value-of select="$y"/>

<xsl:text>&#xA;</xsl:text>



<!-- calculate another factorial for grins -->

<xsl:text>factorial(4) = </xsl:text>

<xsl:call-template name="factorial">

<xsl:with-param name="n" select="5"/>

</xsl:call-template>

<xsl:text>&#xA;</xsl:text>

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
