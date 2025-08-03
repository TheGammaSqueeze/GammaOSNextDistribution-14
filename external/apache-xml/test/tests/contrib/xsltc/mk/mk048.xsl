<xsl:transform
 xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
 version="1.0"
>

  <!-- Test FileName: mk048.xsl -->
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
  <!-- Example: soccer.xml, league.xsl -->
  <!-- Chapter/Page: 7-521 -->
  <!-- Purpose: Creates scorecard for soccer league using count() and sum() functions -->
  
<xsl:variable name="teams" select="//team[not(.=preceding::team)]"/>
<xsl:variable name="matches" select="//match"/>

<xsl:template match="results">
<html><body>
   <h1>Results of Group <xsl:value-of select="@group"/></h1>

   <table cellpadding="5">
      <tr>
        <td>Team</td>
        <td>Played</td>
        <td>Won</td>
        <td>Drawn</td>
        <td>Lost</td>
        <td>For</td>
        <td>Against</td>
     </tr>
   <xsl:for-each select="$teams">
        <xsl:variable name="this" select="."/>
        <xsl:variable name="played" select="count($matches[team=$this])"/>
        <xsl:variable name="won" 
            select="count($matches[team[.=$this]/@score &gt; team[.!=$this]/@score])"/>
        <xsl:variable name="lost"
            select="count($matches[team[.=$this]/@score &lt; team[.!=$this]/@score])"/>
        <xsl:variable name="drawn"
            select="count($matches[team[.=$this]/@score = team[.!=$this]/@score])"/>
        <xsl:variable name="for"
            select="sum($matches/team[.=current()]/@score)"/>
        <xsl:variable name="against"
            select="sum($matches[team=current()]/team/@score) - $for"/>

        <tr>
        <td><xsl:value-of select="."/></td>
        <td><xsl:value-of select="$played"/></td>
        <td><xsl:value-of select="$won"/></td>
        <td><xsl:value-of select="$drawn"/></td>
        <td><xsl:value-of select="$lost"/></td>
        <td><xsl:value-of select="$for"/></td>
        <td><xsl:value-of select="$against"/></td>
        </tr>
   </xsl:for-each>
   </table>
</body></html>
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
