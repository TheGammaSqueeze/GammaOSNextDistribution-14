/*
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
 */
/*
 * $Id$
 */

package org.apache.qetest;

import java.io.BufferedReader;
import java.io.IOException;

/**
 * Utility class for reading buffered streams on a thread.
 *
 * <p>Common usage case:
 * <pre>
 * ThreadedStreamReader errReader = new ThreadedStreamReader();
 * Process p = Runtime.exec(cmdLine, environment);
 * errReader.setInputStream(
 *     new BufferedReader(
 *         new InputStreamReader(proc.getErrorStream()), bufSize));
 * errReader.start();
 * proc.waitFor();
 * errReader.join();
 * String sysErr = errReader.getBuffer().toString();
 * </pre>
 *
 * @author Shane_Curcuru@lotus.com
 * @version $Id$
 */
public class ThreadedStreamReader extends Thread
{

    /** Reader we pull data from.  */
    BufferedReader is = null;

    /** Buffer we store the data in.  */
    StringBuffer sb = null;

    /**
     * Asks us to capture data from the provided stream.
     * @param set BufferedReader we should pull data from
     */
    public void setInputStream(BufferedReader set)
    {
        is = set;
    }

    /**
     * Implement Thread.run().  
     * This asks us to start reading data from our setInputStream()
     * and storing it in our getBuffer() area.  Note you probably 
     * should have called setInputStream() first.
     */
    public void run()
    {
        sb = new StringBuffer();
        sb.append(READER_START_MARKER);

        if (null == is)
        {
            sb.append("ERROR! setInputStream(null)");
            sb.append(READER_END_MARKER);
            return;
        }

        String i = null;
        try
        {
            i = is.readLine();
        }
        catch (IOException ioe1)
        {
            sb.append("ERROR! no data to read, threw: " + ioe1.toString());
            i = null;
        }

        while (i != null)
        {
            sb.append(i);
            // Note: also append the implicit newline that readLine grabbed
            sb.append('\n');

            try
            {
                i = is.readLine();
            }
            catch (IOException ioe2)
            {
                sb.append("WARNING! readLine() threw: " + ioe2.toString());
                i = null;
            }
        }
        sb.append(READER_END_MARKER);
    }

    /**
     * Get the buffer of data we've captured. 
     *
     * @return our internal StringBuffer; will be null if we 
     * have not been run yet.
     */
    public StringBuffer getBuffer()
    {
        return sb;
    }

    public static final String READER_START_MARKER = "<stream>\n";
    public static final String READER_END_MARKER = "</stream>\n";

}  // end of class ThreadedStreamReader

