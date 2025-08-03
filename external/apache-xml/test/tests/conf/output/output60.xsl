<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <xsl:output method="html" indent="no" doctype-public="-//W3C//DTD HTML 4.0 Transitional"/>

  <!-- FileName: OUTP60 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 16.2 HTML Output Method -->
  <!-- Purpose: Do everything inside an HTML element. Note first item in this file. -->

<xsl:template match="/">
  <html lang="en">
    <head>
      <title>Sales Results By Division</title>
    </head>
    <body>
      <table border="1">
	<tr>
          <th>Division</th>
          <th>Revenue</th>
          <th>Growth</th>
          <th>Bonus</th>
        </tr>
        <xsl:for-each select="sales/division">
          <!-- order the result by revenue -->
          <xsl:sort select="revenue"
            data-type="number"
            order="descending"/>
          <tr>
            <td>
              <em><xsl:value-of select="@id"/></em>
            </td>
            <td>
              <xsl:value-of select="revenue"/>
            </td>
            <td>
              <!-- highlight negative growth in red -->
              <xsl:if test="growth &lt; 0">
                <xsl:attribute name="style">
                  <xsl:text>color:red</xsl:text>
                </xsl:attribute>
              </xsl:if>
              <xsl:value-of select="growth"/>
            </td>
            <td>
              <xsl:value-of select="bonus"/>
            </td>
          </tr>
        </xsl:for-each>
      </table>
    </body>
  </html>
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
