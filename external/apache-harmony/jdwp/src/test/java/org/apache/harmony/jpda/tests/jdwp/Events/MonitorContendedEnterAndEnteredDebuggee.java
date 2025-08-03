/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.harmony.jpda.tests.jdwp.Events;

import org.apache.harmony.jpda.tests.framework.LogWriter;
import org.apache.harmony.jpda.tests.share.JPDADebuggeeSynchronizer;
import org.apache.harmony.jpda.tests.share.SyncDebuggee;

public class MonitorContendedEnterAndEnteredDebuggee extends SyncDebuggee {

    static final String TESTED_THREAD = "BLOCKED_THREAD";

    static Object lock = new MonitorWaitMockMonitor();

    BlockedThread thread;

    @Override
    public void run() {
        thread = new BlockedThread(logWriter,TESTED_THREAD);

        // Inform debugger test is ready for testing
        logWriter.println("--> Main thread : started");
        synchronizer.sendMessage(JPDADebuggeeSynchronizer.SGNL_READY);

        synchronized (lock) {
            // Request has been set by Debugger
            synchronizer.receiveMessage(JPDADebuggeeSynchronizer.SGNL_CONTINUE);

            logWriter.println("main thread: start tested thread");
            thread.start();

            // wait and hold the lock until the second thread blocks
            while (!thread.getState().equals(Thread.State.valueOf("BLOCKED"))){
                Thread.yield();
                logWriter.println("main thread: Waiting for second thread to attempt to lock a monitor");
            }
        
            // We think the monitor is contended.
            synchronizer.sendMessage(JPDADebuggeeSynchronizer.SGNL_READY);
            // Make sure we're good to finish.
            synchronizer.receiveMessage(JPDADebuggeeSynchronizer.SGNL_CONTINUE);
            logWriter.println("--> main thread: finish test");
        }

        // Wait for the blocked thread to join. This makes sure we entered the synchronized section
        // in the blocked thread and hence the MonitorContentedEntered message should be sent. If we
        // don't wait here, there is a possibility we exit the process before the blocked thread
        // gets a chance to enter the monitor lock.
        boolean done = false;
        while (!done) {
          try {
            thread.join();
            done = true;
          } catch(InterruptedException e) {
            System.out.println("Thread interrupted when joining, giving another chance");
          }
        }
    }

    static class BlockedThread extends Thread {
        private LogWriter logWriter;

        public BlockedThread(LogWriter writer, String name){
            logWriter = writer;
            this.setName(name);
        }

        @Override
        public void run() {
            logWriter.println("--> BlockedThread: start to run");
            synchronized (lock) {
                this.getName().trim();
                logWriter.println("--> BlockedThread: get lock");
            }
        }
    }

    public static void main(String[] args) {
        runDebuggee(MonitorContendedEnterAndEnteredDebuggee.class);
    }

}

