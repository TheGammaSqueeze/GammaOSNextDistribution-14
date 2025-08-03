<xsl:transform
 xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
 version="1.0"
>

  <!-- Test FileName: mk011.xsl -->
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
  <!-- Example: scene.xml, naming-lines.xsl -->
  <!-- Chapter/Page: 4-173 -->
  <!-- Purpose: Using recursion to process a separated string -->

<xsl:variable name="speakers" select="//SPEAKER"/>

<xsl:template name="contains-name">
   <xsl:param name="line"/>
   <xsl:variable name="line1" 
        select="translate($line, 'abcdefghijklmnopqrstuvwxyz.,:?!;',
                    'ABCDEFGHIJKLMNOPQRSTUVWXYZ      ')"/>
   <xsl:variable name="line2" select="concat(normalize-space($line1), ' ')"/>
   <xsl:variable name="first" select="substring-before($line2,' ')"/>
   <xsl:choose>
   <xsl:when test="$first">
      <xsl:choose>
      <xsl:when test="$speakers[.=$first]">true</xsl:when>
      <xsl:otherwise>
         <xsl:variable name="rest" select="substring-after($line2,' ')"/>    
         <xsl:call-template name="contains-name">
            <xsl:with-param name="line" select="$rest"/>
         </xsl:call-template>
      </xsl:otherwise>
      </xsl:choose>
   </xsl:when>
   <xsl:otherwise>false</xsl:otherwise>
   </xsl:choose>
</xsl:template>

<xsl:template match="/">

<xsl:for-each select="//LINE">
    <xsl:variable name="contains-name">
        <xsl:call-template name="contains-name">
            <xsl:with-param name="line" select="."/>
        </xsl:call-template>
    </xsl:variable>
    <xsl:if test="$contains-name='true'">
        <xsl:copy-of select="."/>;
    </xsl:if>
</xsl:for-each>
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
