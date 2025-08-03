<?xml version="1.0" ?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
   version="1.0">

  <!-- Test FileName: mk049.xsl -->
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
  <!-- Example: orgchart.xml, orgchart.xsl -->
  <!-- Chapter/Page: 8-534 -->
  <!-- Purpose: Illustrate a fill-in the blanks stylesheet -->
  
<xsl:template match="/">
<html>
<head>
   <title>Management Structure</title>
</head>
<body>
   <h1>Management Structure</h1>
   <p>The following responsibilies were announced on 
      <xsl:value-of select="/orgchart/@date"/>:</p>
   <table border="2" cellpadding="5">
   <tr>
      <th>Name</th><th>Role</th><th>Reporting to</th>
   </tr>
   <xsl:for-each select="//person">
      <tr>
         <td><xsl:value-of select="name"/></td>
         <td><xsl:value-of select="title"/></td>
         <td><xsl:value-of select="ancestor::person[1]/name"/></td>
      </tr>
   </xsl:for-each>
   </table>
   <hr/>
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
