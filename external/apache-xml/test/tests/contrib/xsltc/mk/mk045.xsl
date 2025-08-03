<xsl:transform
 xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
 version="1.0"
>

  <!-- Test FileName: mk045.xsl -->
  <!-- Source Attribution: 
       This test was written by Michael Kay and is taken from 
       'XSLT Programmer's Reference' published by Wrox Press Limited in 2000;
       ISBN 1-861003-12-9; copyright Wrox Press Limited 2000; all rights reserved. 
       Now updated in the second edition (ISBN 1861005067), http://www.wrox.com.
       No part of this book may be reproduced, stored in a retrieval system or 
       transmitted in any form or by any means - electronic, electrostatic, mechanical, 
       photocopying, recording or otherwise - without the prior written permission of 
       the publisher, except in the case of brief quotations embodied in critical articles or reviews.
  -->
  <!-- Example: shapes.xml, area.xsl -->
  <!-- Chapter/Page: 7-502 -->
  <!-- Purpose: Using position() to process node set recursively -->
  
<xsl:template match="rectangle" mode="area">
    <xsl:value-of select="@width * @height"/>
</xsl:template>

<xsl:template match="square" mode="area">
    <xsl:value-of select="@side * @side"/>
</xsl:template>

<xsl:template match="circle" mode="area">
    <xsl:value-of select="3.14159 * @radius * @radius"/>
</xsl:template>

<xsl:template name="total-area">
    <xsl:param name="set-of-shapes"/>
    <xsl:choose>
    <xsl:when test="$set-of-shapes">
        <xsl:variable name="first">
            <xsl:apply-templates select="$set-of-shapes[1]" mode="area"/>
        </xsl:variable>
        <xsl:variable name="rest">
            <xsl:call-template name="total-area">
                <xsl:with-param name="set-of-shapes"
                    select="$set-of-shapes[position()!=1]"/>
            </xsl:call-template>
        </xsl:variable>
        <xsl:value-of select="$first + $rest"/>
    </xsl:when>
    <xsl:otherwise>0</xsl:otherwise>
    </xsl:choose>
</xsl:template>

<xsl:template match="shapes">
    <xsl:call-template name="total-area">
        <xsl:with-param name="set-of-shapes" select="*"/>
    </xsl:call-template>
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

</xsl:transform>
