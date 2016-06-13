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

// Portions Copyright [2016] [C2B2 Consulting Ltd and/or its affiliates]

package org.glassfish.enterprise.concurrent;

import java.util.concurrent.*;
import javax.enterprise.concurrent.ManagedExecutorService;
import javax.enterprise.concurrent.ManagedScheduledExecutorService;
import javax.enterprise.concurrent.Trigger;
import org.glassfish.enterprise.concurrent.internal.ManagedFutureTask;
import org.glassfish.enterprise.concurrent.internal.ManagedScheduledThreadPoolExecutor;

/**
 * Implementation of ManagedScheduledExecutorService interface
 */
public class ManagedScheduledExecutorServiceImpl extends AbstractManagedExecutorService 
    implements ManagedScheduledExecutorService {

    protected ManagedScheduledThreadPoolExecutor threadPoolExecutor;
    protected final ManagedScheduledExecutorServiceAdapter adapter;
    

    public ManagedScheduledExecutorServiceImpl(String name, 
            ManagedThreadFactoryImpl managedThreadFactory, 
            long hungTaskThreshold, 
            boolean longRunningTasks, 
            int corePoolSize, 
            long keepAliveTime, 
            TimeUnit keepAliveTimeUnit,
            long threadLifeTime,
            ContextServiceImpl contextService,
            RejectPolicy rejectPolicy) {
        super(name, managedThreadFactory, hungTaskThreshold, longRunningTasks,
                contextService,
                contextService != null? contextService.getContextSetupProvider(): null,
                rejectPolicy);

        threadPoolExecutor = new ManagedScheduledThreadPoolExecutor(corePoolSize, 
                this.managedThreadFactory);
        threadPoolExecutor.setKeepAliveTime(keepAliveTime, keepAliveTimeUnit);
        threadPoolExecutor.setThreadLifeTime(threadLifeTime);
        adapter = new ManagedScheduledExecutorServiceAdapter(this);
    }
    
    @Override
    public ScheduledFuture<?> schedule(Runnable command, Trigger trigger) {
        return threadPoolExecutor.schedule(this, command, trigger);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, Trigger trigger) {
        return threadPoolExecutor.schedule(this, callable, trigger);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return threadPoolExecutor.schedule(this, command, null, delay, unit);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return threadPoolExecutor.schedule(this, callable, delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return threadPoolExecutor.scheduleAtFixedRate(this, command, initialDelay, period, unit);
    }


    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return threadPoolExecutor.scheduleWithFixedDelay(this, command, initialDelay, delay, unit);
    }

    @Override
    public void execute(Runnable command) {
        threadPoolExecutor.schedule(this, command, null, 0L, TimeUnit.NANOSECONDS);
    }
    
    @Override
    public Future<?> submit(Runnable task) {
        return threadPoolExecutor.schedule(this, task, null, 0L, TimeUnit.NANOSECONDS);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        return threadPoolExecutor.schedule(this, task, result, 0L, TimeUnit.NANOSECONDS);
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        return threadPoolExecutor.schedule(this, task, 0L, TimeUnit.NANOSECONDS);
    }

    @Override
    protected ExecutorService getThreadPoolExecutor() {
        return threadPoolExecutor;
    }

   /**
     * Returns an adapter for the ManagedScheduledExceutorService instance which
     * has its life cycle operations disabled.
     * 
     * @return The ManagedScheduledExecutorService instance with life cycle 
     *         operations disabled for use by application components.
     */
    public ManagedScheduledExecutorServiceAdapter getAdapter() {
        return adapter  ;
    }

    @Override
    public ManagedExecutorService getExecutorForTaskListener() {
        return adapter;
    }

    @Override
    protected <V> ManagedFutureTask<V> getNewTaskFor(Runnable r, V result) {
        return threadPoolExecutor.newTaskFor(this, r, result);
    }

    @Override
    protected <V> ManagedFutureTask<V> getNewTaskFor(Callable<V> callable) {
        return threadPoolExecutor.newTaskFor(this, callable);
    }
    
    @Override
    protected void executeManagedFutureTask(ManagedFutureTask task) {
        // task.submitted() will be called from threadPoolExecutor.delayExecute()
        threadPoolExecutor.executeManagedTask(task);
    }

    @Override
    public long getTaskCount() {
        return threadPoolExecutor.getTaskCount();
    }
    
    @Override
    public long getCompletedTaskCount() {
        return threadPoolExecutor.getCompletedTaskCount();
    }
    
    @Override
    public int getCorePoolSize() {
        return threadPoolExecutor.getCorePoolSize();
    }
    
    @Override
    public int getActiveCount() {
        return threadPoolExecutor.getActiveCount();
    }
    
    @Override
    public long getKeepAliveTime() {
        return threadPoolExecutor.getKeepAliveTime(TimeUnit.MILLISECONDS);
    }
    
    @Override
    public int getLargestPoolSize() {
        return threadPoolExecutor.getLargestPoolSize();
    }
    
    @Override
    public int getMaximumPoolSize() {
        return threadPoolExecutor.getMaximumPoolSize();
    }
    
    @Override
    public int getPoolSize() {
        return threadPoolExecutor.getPoolSize();
    }
    
    @Override
    public BlockingQueue getBlockingQueue() {
        return threadPoolExecutor.getQueue();
    }
    
    @Override
    public RejectedExecutionHandler getRejectedExecutionHandler() {
        return threadPoolExecutor.getRejectedExecutionHandler();
    }
    
    @Override
    public ThreadFactory getThreadFactory() {
        return threadPoolExecutor.getThreadFactory();
    }
    
    /**
     * Gets the policy on whether to continue executing existing periodic tasks 
     * even when this executor has been shutdown. In this case, these tasks will
     * only terminate upon shutdownNow or after setting the policy to false when
     * already shutdown. This value is by default false.
     * @return true if will continue after shutdown.
     */
    public boolean getContinueExistingPeriodicTasksAfterShutdownPolicy() {
        return threadPoolExecutor.getContinueExistingPeriodicTasksAfterShutdownPolicy();
    }
    
    /**
     * Gets the policy on whether to execute existing delayed tasks even when 
     * this executor has been shutdown. In this case, these tasks will only 
     * terminate upon shutdownNow, or after setting the policy to false when 
     * already shutdown. This value is by default true.
     * @return true if will execute after shutdown.
     */
    public boolean getExecuteExistingDelayedTasksAfterShutdownPolicy() {
        return threadPoolExecutor.getExecuteExistingDelayedTasksAfterShutdownPolicy();
    }
    
    /**
     * Gets the policy on whether cancelled tasks should be immediately removed 
     * from the work queue at time of cancellation. This value is by default 
     * false.
     * @return true if cancelled tasks are immediately removed from the queue
     */
    public boolean getRemoveOnCancelPolicy() {
        return threadPoolExecutor.getRemoveOnCancelPolicy();
    }
}
