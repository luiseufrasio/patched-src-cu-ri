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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.*;
import javax.enterprise.concurrent.ContextService;
import javax.enterprise.concurrent.ManagedExecutorService;
import org.glassfish.enterprise.concurrent.internal.ManagedFutureTask;
import org.glassfish.enterprise.concurrent.spi.ContextSetupProvider;

/**
 * Abstract base class for {@code ManagedExecutorService} and 
 * {@code ManagedScheduledExecutorService}
 * implementation classes. Lifecycle operations are available for use by the
 * application server. Application components should be handed instances 
 * that extends from  AbstractManagedExecutorServiceAdapter instead, which have 
 * their lifecycle operations disabled.
 * Instances of subclasses of this class could be used by the Java EE
 * product provider to control the life cycle.
 */
public abstract class AbstractManagedExecutorService 
extends AbstractExecutorService implements ManagedExecutorService {

    public enum RejectPolicy {

        ABORT, RETRY_ABORT
    };

    protected final String name;
    protected final ContextSetupProvider contextSetupProvider;
    protected final ContextServiceImpl contextService;
    protected final ManagedThreadFactoryImpl managedThreadFactory;
    protected RejectPolicy rejectPolicy; // currently unused
    protected final boolean contextualCallback;
    protected boolean longRunningTasks;

    public AbstractManagedExecutorService(String name,
            ManagedThreadFactoryImpl managedThreadFactory,
            long hungTaskThreshold,
            boolean longRunningTasks,
            ContextServiceImpl contextService,
            ContextSetupProvider contextCallback,
            RejectPolicy rejectPolicy) {
        this.name = name;
        this.contextSetupProvider = contextCallback;
        this.contextService = contextService;
        this.rejectPolicy = rejectPolicy;
        this.contextualCallback = false;
        this.longRunningTasks = longRunningTasks;
        if (managedThreadFactory == null) {
            managedThreadFactory = new ManagedThreadFactoryImpl(
                    name + "-ManagedThreadFactory",
                    null,
                    Thread.NORM_PRIORITY);            
        }
        managedThreadFactory.setHungTaskThreshold(hungTaskThreshold);

        this.managedThreadFactory = managedThreadFactory;
    }

    protected <T> T doInvokeAny(Collection<? extends Callable<T>> tasks, boolean timed, long nanos) throws InterruptedException, ExecutionException, TimeoutException {
        // adopted from java.util.concurrent.AbstractExecutorService.doInvokeAny()
        if (tasks == null) {
            throw new NullPointerException();
        }
        int ntasks = tasks.size();
        if (ntasks == 0) {
            throw new IllegalArgumentException();
        }
        List<Future<T>> futures = new ArrayList<Future<T>>(ntasks);
        ManagedExecutorCompletionService<T> ecs = new ManagedExecutorCompletionService<T>(this);
        // For efficiency, especially in executors with limited
        // parallelism, check to see if previously submitted tasks are
        // done before submitting more of them. This interleaving
        // plus the exception mechanics account for messiness of main
        // loop.
        try {
            // Record exceptions so that if we fail to obtain any
            // result, we can throw the last exception we got.
            ExecutionException ee = null;
            long lastTime = timed ? System.nanoTime() : 0;
            Iterator<? extends Callable<T>> it = tasks.iterator();
            // Start one task for sure; the rest incrementally
            futures.add(ecs.submit(it.next()));
            --ntasks;
            int active = 1;
            for (;;) {
                Future<T> f = ecs.poll();
                if (f == null) {
                    if (ntasks > 0) {
                        --ntasks;
                        futures.add(ecs.submit(it.next()));
                        ++active;
                    } else if (active == 0) {
                        break;
                    } else if (timed) {
                        f = ecs.poll(nanos, TimeUnit.NANOSECONDS);
                        if (f == null) {
                            throw new TimeoutException();
                        }
                        long now = System.nanoTime();
                        nanos -= now - lastTime;
                        lastTime = now;
                    } else {
                        f = ecs.take();
                    }
                }
                if (f != null) {
                    --active;
                    try {
                        return f.get();
                    } catch (ExecutionException eex) {
                        ee = eex;
                    } catch (RuntimeException rex) {
                        ee = new ExecutionException(rex);
                    }
                }
            }
            if (ee == null) {
                ee = new ExecutionException(null);
            }
            throw ee;
        } finally {
            for (Future<T> f : futures) {
                f.cancel(true);
            }
        }
    }

    public ContextSetupProvider getContextSetupProvider() {
        return contextSetupProvider;
    }

    public ContextService getContextService() {
        return contextService;
    }

    public boolean isContextualCallback() {
        return contextualCallback;
    }

    public Collection<AbstractManagedThread> getHungThreads() {
        if (longRunningTasks) {
            return null;
        }
        Collection<AbstractManagedThread> hungThreads = null;
        Collection<AbstractManagedThread> allThreads = getThreads();
        if (allThreads != null) {
            long now = System.currentTimeMillis();
            for (AbstractManagedThread thread: allThreads) {
                if (thread.isTaskHung(now)) {
                    if (hungThreads == null) {
                        hungThreads = new ArrayList<>();
                    }
                    hungThreads.add(thread);
                }
            }
        }        
        return hungThreads;
    }

    public ManagedThreadFactoryImpl getManagedThreadFactory() {
        return managedThreadFactory;
    }

    public String getName() {
        return name;
    }

    public boolean isLongRunningTasks() {
        return longRunningTasks;
    }

    public RejectPolicy getRejectPolicy() {
        return rejectPolicy;
    }

    // managed objects methods
    public String getObjectName() {
        return null;
    }

    /**
     * Return an array of threads in this Managed[Scheduled]ExecutorService
     * @return an array of threads in this Managed[Scheduled]ExecutorService.
     *         It returns null if there is no thread.
     */
    public Collection<AbstractManagedThread> getThreads() {
        return managedThreadFactory.getThreads();
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        // code adopted from java.util.concurrent.AbstractExecutorService
        if (tasks == null) {
            throw new NullPointerException();
        }
        List<Future<T>> futures = new ArrayList<Future<T>>(tasks.size());
        boolean done = false;
        try {
            for (Callable<T> t : tasks) {
                ManagedFutureTask<T> f = getNewTaskFor(t);
                futures.add(f);
                executeManagedFutureTask(f);
            }
            for (Future<T> f : futures) {
                if (!f.isDone()) {
                    try {
                        f.get();
                    } catch (CancellationException ignore) {
                    } catch (ExecutionException ignore) {
                    }
                }
            }
            done = true;
            return futures;
        } finally {
            if (!done) {
                for (Future<T> f : futures) {
                    f.cancel(true);
                }
            }
        }
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        // code adopted from java.util.concurrent.AbstractExecutorService
        if (tasks == null || unit == null) {
            throw new NullPointerException();
        }
        long nanos = unit.toNanos(timeout);
        List<Future<T>> futures = new ArrayList<Future<T>>(tasks.size());
        boolean done = false;
        try {
            for (Callable<T> t : tasks) {
                futures.add(getNewTaskFor(t));
            }
            long lastTime = System.nanoTime();
            // Interleave time checks and calls to execute in case
            // executor doesn't have any/much parallelism.
            Iterator<Future<T>> it = futures.iterator();
            while (it.hasNext()) {
                executeManagedFutureTask((ManagedFutureTask) (it.next()));
                long now = System.nanoTime();
                nanos -= now - lastTime;
                lastTime = now;
                if (nanos <= 0) {
                    return futures;
                }
            }
            for (Future<T> f : futures) {
                if (!f.isDone()) {
                    if (nanos <= 0) {
                        return futures;
                    }
                    try {
                        f.get(nanos, TimeUnit.NANOSECONDS);
                    } catch (CancellationException ignore) {
                    } catch (ExecutionException ignore) {
                    } catch (TimeoutException toe) {
                        return futures;
                    }
                    long now = System.nanoTime();
                    nanos -= now - lastTime;
                    lastTime = now;
                }
            }
            done = true;
            return futures;
        } finally {
            if (!done) {
                for (Future<T> f : futures) {
                    f.cancel(true);
                }
            }
        }
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        try {
            return doInvokeAny(tasks, false, 0);
        } catch (TimeoutException cannotHappen) {
            assert false;
            return null;
        }
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return doInvokeAny(tasks, true, unit.toNanos(timeout));
    }

    public boolean isEventProvider() {
        return true;
    }

    public boolean isStateManageable() {
        return true;
    }

    public boolean isStatisticsProvider() {
        return true;
    }

    /**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    @Override
    public Future<?> submit(Runnable task) {
        if (task == null) {
            throw new NullPointerException();
        }
        ManagedFutureTask<Void> ftask = getNewTaskFor(task, null);
        executeManagedFutureTask(ftask);
        return ftask;
    }

    /**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        if (task == null) {
            throw new NullPointerException();
        }
        ManagedFutureTask<T> ftask = getNewTaskFor(task, result);
        executeManagedFutureTask(ftask);
        return ftask;
    }

    /**
     * @throws RejectedExecutionException {@inheritDoc}
     * @throws NullPointerException       {@inheritDoc}
     */
    @Override
    public <T> Future<T> submit(Callable<T> task) {
        if (task == null) {
            throw new NullPointerException();
        }
        ManagedFutureTask<T> ftask = getNewTaskFor(task);
        executeManagedFutureTask(ftask);
        return ftask;
    }

    protected abstract ExecutorService getExecutor();

    @Override
    public abstract void execute(Runnable command);
    
    /**
     * Executes a ManagedFutureTask created by getNewTaskFor()
     * 
     * @param task The ManagedFutureTask to be run
     */
    protected void executeManagedFutureTask(ManagedFutureTask<?> task) {
        task.submitted();
        getExecutor().execute(task);
    }
 
    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return getExecutor().awaitTermination(timeout, unit);
    }

    @Override
    public boolean isShutdown() {
        return getExecutor().isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return getExecutor().isTerminated();
    }

    @Override
    public void shutdown() {
        getExecutor().shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        List<Runnable> pendingTasks = getExecutor().shutdownNow();
        for (Runnable r: pendingTasks) {
            if (r instanceof ManagedFutureTask) {
                ((ManagedFutureTask) r).cancel(true);
            }
        }
        return pendingTasks;
    }

    /**
     * newTaskFor from super class java.util.concurrent.AbstractExecutorService
     * is protected access. This method allows newTaskFor to be called by
     * other methods in this package that is not a subclass of AbstractExecutorService,
     * and to return Object of type ManagedFutureTask, which extends from
     * the return type of newTaskFor of FutureRunnable.
     */
    protected abstract <V> ManagedFutureTask<V> getNewTaskFor(Runnable r, V result);
    
    /**
     * newTaskFor from super class java.util.concurrent.AbstractExecutorService
     * is protected access. This method allows newTaskFor to be called by
     * other methods in this package that is not a subclass of AbstractExecutorService,
     * and to return Object of type ManagedFutureTask, which extends from
     * the return type of newTaskFor of FutureRunnable.
     */
    protected abstract <V> ManagedFutureTask<V> getNewTaskFor(Callable<V> callable);

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Runnable r, T result) {
        return getNewTaskFor(r, result);
    }
    
    @Override
    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        return getNewTaskFor(callable);
    }
    

    /**
     * Returns the ManagedExecutorService instance that is used for passing
     * as the executor argument of ManagedTaskListener calls
     * 
     * @return the ManagedExecutorService instance that is used for passing
     * as the executor argument of ManagedTaskListener calls
     * 
     */
    public abstract ManagedExecutorService getExecutorForTaskListener();
    
    /**
     * Returns the approximate total number of tasks that have ever been
     * scheduled for execution. Because the states of tasks and
     * threads may change dynamically during computation, the returned
     * value is only an approximation.
     *
     * @return the number of tasks
     */
    public abstract long getTaskCount();
    
    /**
     * Returns the approximate total number of tasks that have
     * completed execution. Because the states of tasks and threads
     * may change dynamically during computation, the returned value
     * is only an approximation, but one that does not ever decrease
     * across successive calls.
     *
     * @return the number of tasks
     */
    public abstract long getCompletedTaskCount();
    
    /**
     * Returns the the core number of threads.
     * @return the core number of threads.
     */
    public abstract int getCorePoolSize();
    
    /**
     * Returns the approximate number of threads that are actively 
     * executing tasks.
     * @return the number of threads.
     */
    public abstract int getActiveCount();
    
    /**
     * Returns the thread keep-alive time in milliseconds, which is the amount 
     * of time that threads in excess of the core pool size may remain idle 
     * before being terminated.
     * @return the time in milliseconds.
     */
    public abstract long getKeepAliveTime();
    
    /**
     * Returns the largest number of threads that have ever simultaneously 
     * been in the pool.
     * @return the number of threads.
     */
    public abstract int getLargestPoolSize();
        
    /**
     * Returns the maximum allowed number of threads.
     * @return the number of threads.
     */
    public abstract int getMaximumPoolSize();
    
    /**
     * Returns the current number of threads in the pool.
     * @return the number of threads.
     */
    public abstract int getPoolSize();
    
    /**
     * Returns the task queue used by this executor. 
     * Each element of this queue is a ScheduledFuture, including those tasks 
     * submitted using execute which are for scheduling purposes used as the 
     * basis of a zero-delay ScheduledFuture. Iteration over this queue is not 
     * guaranteed to traverse tasks in the order in which they will execute.
     * @return the task queue.
     */
    public abstract BlockingQueue getBlockingQueue();
    
    /**
     * Returns the current handler for unexecutable tasks.
     * @return the current handler.
     */
    public abstract RejectedExecutionHandler getRejectedExecutionHandler();
    
    /**
     * Returns the thread factory used to create new threads.
     * @return the current thread factory.
     */
    public abstract ThreadFactory getThreadFactory();
}
