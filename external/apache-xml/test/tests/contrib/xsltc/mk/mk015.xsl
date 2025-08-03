<xsl:stylesheet version="1.0"
     xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <!-- Test FileName: mk015.xsl -->
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
  <!-- Example: copy-of/soccer.xml, copy-of/soccer.xsl -->
  <!-- Chapter/Page: 4-185 -->
  <!-- Purpose: Using copy-of for repeated output -->

<xsl:variable name="table-heading">
    <tr>
        <td><b>Date</b></td>
        <td><b>Home Team</b></td>
        <td><b>Away Team</b></td>
        <td><b>Result</b></td>
    </tr>
</xsl:variable>

<xsl:template match="/">
<html><body>
    <h1>Matches in Group <xsl:value-of select="/*/@group"/></h1>

    <xsl:for-each select="//match">

    <h2><xsl:value-of select="concat(team[1], ' versus ', team[2])"/></h2>

    <table bgcolor="#cccccc" border="1" cellpadding="5">
        <xsl:copy-of select="$table-heading"/>        
        <tr>
        <td><xsl:value-of select="date"/>&#xa0;</td>
        <td><xsl:value-of select="team[1]"/>&#xa0;</td>
        <td><xsl:value-of select="team[2]"/>&#xa0;</td>   
        <td><xsl:value-of select="concat(team[1]/@score, '-', team[2]/@score)"/>&#xa0;</td>   
        </tr>        
    </table>
    </xsl:for-each>   
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

</xsl:stylesheet>
