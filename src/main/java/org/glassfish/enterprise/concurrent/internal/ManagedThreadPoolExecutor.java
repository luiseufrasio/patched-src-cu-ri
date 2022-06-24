/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2013 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.enterprise.concurrent.internal;

import java.util.concurrent.*;
import org.glassfish.enterprise.concurrent.AbstractManagedThread;

/**
 * ThreadPoolExecutor for running tasks submitted to ManagedExecutorServiceImpl.
 */
public class ManagedThreadPoolExecutor extends ThreadPoolExecutor implements ManagedExecutor {

    private long threadLifeTime = 0L; // in seconds
    
    public ManagedThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
    }

    public ManagedThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
    }

    public ManagedThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory);
    }

    public ManagedThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue);
    }

    public void setThreadLifeTime(long threadLifeTime) {
        this.threadLifeTime = threadLifeTime;
        if (threadLifeTime > 0) {
            long keepAliveTime = getKeepAliveTime(TimeUnit.SECONDS);
            if (keepAliveTime == 0 || threadLifeTime < keepAliveTime) {
                setKeepAliveTime(threadLifeTime, TimeUnit.SECONDS);
                allowCoreThreadTimeOut(true);
            }
        }
    }

    
    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        super.afterExecute(r, t);

        ManagedFutureTask task = (ManagedFutureTask) r;
        try {
            task.done(t);
        }
        finally {
            task.resetContext();
            // Kill thread if thread older than threadLifeTime
            if (threadLifeTime > 0) {
                Thread thread = Thread.currentThread();
                if (thread instanceof AbstractManagedThread) {
                    long threadStartTime = ((AbstractManagedThread)thread).getThreadStartTime();
                    if ((System.currentTimeMillis() - threadStartTime)/1000 > threadLifeTime) {
                        throw new ThreadExpiredException();
                    }
                }
            }
        }
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        super.beforeExecute(t, r);
        
        ManagedFutureTask task = (ManagedFutureTask) r;
        task.setupContext();
        task.starting(t);
    }
    
}
