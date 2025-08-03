<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  version="1.0">

  <!-- FileName: predicate57 -->
  <!-- Document: http://www.w3.org/TR/xpath -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 2.4 -->
  <!-- Creator: David Marston, based on an idea by Mukund Raghavachari -->
  <!-- Purpose: Test use of count() in a predicate to count children. -->

<xsl:output method="xml" encoding="UTF-8"/>

<xsl:template match="/">
  <out>
    <xsl:text>tr nodes: </xsl:text><xsl:value-of select="count(//tr)"/>
    <xsl:text>, tr nodes with 3 td children: </xsl:text>
    <xsl:value-of select="count(//tr[count(./td) = 3])"/><xsl:text>
</xsl:text>
    <nodes>
      <xsl:for-each select="//tr[count(./td) = 3]">
        <xsl:for-each select="td">
          <xsl:value-of select="."/>
          <xsl:if test="following-sibling::td">
            <xsl:text>, </xsl:text>
          </xsl:if>
        </xsl:for-each><xsl:text>
  </xsl:text>
      </xsl:for-each>
    </nodes>
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
