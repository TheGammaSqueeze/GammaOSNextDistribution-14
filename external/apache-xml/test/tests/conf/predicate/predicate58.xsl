<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  version="1.0">

  <!-- FileName: predicate58 -->
  <!-- Document: http://www.w3.org/TR/xpath -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 2.4 -->
  <!-- Creator: David Marston -->
  <!-- Purpose: Compare non-existent attributes to ones containing the null string. -->

<xsl:output method="xml" encoding="UTF-8"/>

<xsl:template match="doc">
  <out>
    <xsl:text>has ex: </xsl:text><xsl:value-of select="count(a[@ex])"/>
    <xsl:text>
has ex, eq null: </xsl:text><xsl:value-of select="count(a[@ex=''])"/>
    <xsl:text>
has ex, measure null: </xsl:text><xsl:value-of select="count(a[string-length(@ex)=0])"/>
    <xsl:text>
has ex, neq null: </xsl:text><xsl:value-of select="count(a[@ex!=''])"/>
    <xsl:text>
has ex, measure non-null: </xsl:text><xsl:value-of select="count(a[string-length(@ex) &gt; 0])"/>
    <xsl:text>
not has ex: </xsl:text><xsl:value-of select="count(a[not(@ex)])"/>
    <xsl:text>
not has ex, eq null: </xsl:text><xsl:value-of select="count(a[not(@ex='')])"/>
    <xsl:text>
not has ex, measure null: </xsl:text><xsl:value-of select="count(a[not(string-length(@ex)=0)])"/>
    <xsl:text>
has why, eq value: </xsl:text><xsl:value-of select="count(a[@why='value'])"/>
    <xsl:text>
has why, neq value: </xsl:text><xsl:value-of select="count(a[@why!='value'])"/>
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
