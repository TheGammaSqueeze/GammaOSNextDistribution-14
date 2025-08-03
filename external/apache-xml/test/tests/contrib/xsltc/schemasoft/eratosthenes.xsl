<?xml version="1.0"?>

<!-- 
   The Sieve of Eratosthenes
   GPL (c) Oliver Becker, 2000-06-13
   obecker@informatik.hu-berlin.de
-->

<xslt:transform xmlns:xslt="http://www.w3.org/1999/XSL/Transform"
                version="1.0">

<xslt:output method="text" />

<xslt:param name="bound" select="1000" />

<xslt:template match="/">
   <xslt:call-template name="eratosthenes">
      <xslt:with-param name="pos" select="2" />
      <xslt:with-param name="array">
         <xslt:call-template name="init-array">
            <xslt:with-param name="length" select="$bound" />
         </xslt:call-template>
      </xslt:with-param>
   </xslt:call-template>
   <xslt:text>&#xA;</xslt:text>
</xslt:template>


<!-- Initialize the array (string) with length $length -->
<xslt:template name="init-array">
   <xslt:param name="length" />
   <xslt:if test="$length &gt; 0">
      <xslt:text>-</xslt:text>
      <xslt:call-template name="init-array">
         <xslt:with-param name="length" select="$length - 1" />
      </xslt:call-template>
   </xslt:if>
</xslt:template>


<!-- Sieve of Eratosthenes: If the number at position $pos isn't 
     marked then it's a prime (and printed). If the position of the
     prime is lower or equal then the square root of $bound then the 
     new array will be computed by marking all multiples of $pos. -->
<xslt:template name="eratosthenes">
   <xslt:param name="array" />
   <xslt:param name="pos" />
   <xslt:if test="$pos &lt; $bound">
      <xslt:variable name="is-prime" 
                     select="substring($array,$pos,1) = '-'" />
      <xslt:if test="$is-prime">
         <xslt:value-of select="$pos" />, <xslt:text />
      </xslt:if>
      <xslt:variable name="new-array">
         <xslt:choose>
            <xslt:when test="$is-prime and $pos*$pos &lt;= $bound">
               <xslt:call-template name="mark">
                  <xslt:with-param name="array" select="$array" />
                  <xslt:with-param name="number" select="$pos" />
               </xslt:call-template>
            </xslt:when>
            <xslt:otherwise>
               <xslt:value-of select="$array" />
            </xslt:otherwise>
         </xslt:choose>
      </xslt:variable>
      <xslt:call-template name="eratosthenes">
         <xslt:with-param name="array" select="$new-array" />
         <xslt:with-param name="pos" select="$pos + 1" />
      </xslt:call-template>
   </xslt:if>
</xslt:template>


<!-- Mark all multiples of $number in $array with '*' -->
<xslt:template name="mark">
   <xslt:param name="array" />
   <xslt:param name="number" />
   <xslt:choose>
      <xslt:when test="string-length($array) &gt; $number">
         <xslt:value-of select="substring ($array, 1, $number - 1)" />
         <xslt:text>*</xslt:text>
         <xslt:call-template name="mark">
            <xslt:with-param name="array" 
                             select="substring ($array, $number + 1)" />
            <xslt:with-param name="number" select="$number" />
         </xslt:call-template>
      </xslt:when>
      <xslt:otherwise>
         <xslt:value-of select="$array" />
      </xslt:otherwise>
   </xslt:choose>
</xslt:template>

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

</xslt:transform>
