/*
 *   DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 *   Copyright (c) [2022] Payara Foundation and/or its affiliates.
 *   All rights reserved.
 *
 *   The contents of this file are subject to the terms of either the GNU
 *   General Public License Version 2 only ("GPL") or the Common Development
 *   and Distribution License("CDDL") (collectively, the "License").  You
 *   may not use this file except in compliance with the License.  You can
 *   obtain a copy of the License at
 *   https://github.com/payara/Payara/blob/master/LICENSE.txt
 *   See the License for the specific
 *   language governing permissions and limitations under the License.
 *
 *   When distributing the software, include this License Header Notice in each
 *   file and include the License file at glassfish/legal/LICENSE.txt.
 *
 *   GPL Classpath Exception:
 *   The Payara Foundation designates this particular file as subject to the
 *   "Classpath" exception as provided by the Payara Foundation in the GPL
 *   Version 2 section of the License file that accompanied this code.
 *
 *   Modifications:
 *   If applicable, add the following below the License Header, with the fields
 *   enclosed by brackets [] replaced by your own identifying information:
 *   "Portions Copyright [year] [name of copyright owner]"
 *
 *   Contributor(s):
 *   If you wish your version of this file to be governed by only the CDDL or
 *   only the GPL Version 2, indicate your decision by adding "[Contributor]
 *   elects to include this software in this distribution under the [CDDL or GPL
 *   Version 2] license."  If you don't indicate a single choice of license, a
 *   recipient has the option to distribute your version of this file under
 *   either the CDDL, the GPL Version 2 or to extend the choice of license to
 *   its licensees as provided above.  However, if you add GPL Version 2 code
 *   and therefore, elected the GPL Version 2 license, then the option applies
 *   only if the new code is made subject to such option by the copyright
 *   holder.
 */
package org.glassfish.enterprise.concurrent.internal;

import org.glassfish.enterprise.concurrent.AbstractManagedExecutorService;

import javax.enterprise.concurrent.Trigger;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ForkJoinPool for running tasks submitted to ManagedScheduledExecutorServiceImpl.
 */
public class ManagedScheduledForkJoinPool extends ForkJoinPool implements ManagedScheduledExecutor {

    public ManagedScheduledForkJoinPool() {
        super();
    }

    public ManagedScheduledForkJoinPool(int parallelism) {
        super(parallelism);
    }

    public ManagedScheduledForkJoinPool(int parallelism,
                               ForkJoinWorkerThreadFactory factory,
                               Thread.UncaughtExceptionHandler handler,
                               boolean asyncMode) {
        super(parallelism, factory, handler, asyncMode);
    }

    /**
     * Sequence number to break scheduling ties, and in turn to
     * guarantee FIFO order among tied entries.
     */
    private static final AtomicLong sequencer = new AtomicLong(0);

    /**
     * Returns current nanosecond time.
     */
    final long now() {
        return System.nanoTime();
    }

    /**
     * Returns true if can run a task given current run state
     * and run-after-shutdown parameters.
     *
     * @param periodic true if this task periodic, false if delayed
     */
    boolean canRunInCurrentRunState(boolean periodic) {
        return !isShutdown();
    }

    public <V> ManagedFutureTask<V> newTaskFor(
            AbstractManagedExecutorService executor,
            Runnable r,
            V result) {
        return new org.glassfish.enterprise.concurrent.internal.ManagedScheduledForkJoinPool.ManagedScheduledFutureTask<>(executor, r, result, 0L);
    }

    public ManagedFutureTask newTaskFor(
            AbstractManagedExecutorService executor,
            Callable callable) {
        return new org.glassfish.enterprise.concurrent.internal.ManagedScheduledForkJoinPool.ManagedScheduledFutureTask(executor, callable, 0L);
    }

    @Override
    public void executeManagedTask(ManagedFutureTask task) {}

    @Override
    public long getTaskCount() {
        return super.getQueuedTaskCount();
    }

    @Override
    public long getCompletedTaskCount() {
        return 0;
    }

    @Override
    public int getCorePoolSize() {
        return 0;
    }

    @Override
    public int getActiveCount() {
        return 0;
    }

    @Override
    public long getKeepAliveTime(TimeUnit unit) {
        return 0;
    }

    @Override
    public int getLargestPoolSize() {
        return 0;
    }

    @Override
    public int getMaximumPoolSize() {
        return 0;
    }

    @Override
    public BlockingQueue getQueue() {
        return null;
    }

    @Override
    public RejectedExecutionHandler getRejectedExecutionHandler() {
        return null;
    }

    @Override
    public ThreadFactory getThreadFactory() {
        return null;
    }

    @Override
    public boolean getContinueExistingPeriodicTasksAfterShutdownPolicy() {
        return false;
    }

    @Override
    public boolean getExecuteExistingDelayedTasksAfterShutdownPolicy() {
        return false;
    }

    @Override
    public boolean getRemoveOnCancelPolicy() {
        return false;
    }

    @Override
    public <V> ScheduledFuture<V> schedule(AbstractManagedExecutorService executor, Callable<V> callable, long delay, TimeUnit unit) {
        return null;
    }

    @Override
    public <V> ScheduledFuture<V> schedule(AbstractManagedExecutorService executor, Runnable command, V result, long delay, TimeUnit unit) {
        return null;
    }

    @Override
    public ScheduledFuture<?> schedule(AbstractManagedExecutorService executor, Runnable command, Trigger trigger) {
        return null;
    }

    @Override
    public <V> ScheduledFuture<V> schedule(AbstractManagedExecutorService executor, Callable<V> callable, Trigger trigger) {
        return null;
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(AbstractManagedExecutorService executor, Runnable command, long initialDelay, long period, TimeUnit unit) {
        return null;
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(AbstractManagedExecutorService executor, Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return null;
    }

    /**
     * Adopted from private class
     * java.util.concurrent.ScheduledThreadPoolExeuctor$ScheduledFutureTask<V>
     * to provide extended functionalities.
     */
    private class ManagedScheduledFutureTask<V>
            extends ManagedFutureTask<V> implements RunnableScheduledFuture<V> {

        /**
         * Sequence number to break ties FIFO
         */
        protected final long sequenceNumber;

        /**
         * The next task run time in nanoTime units
         */
        protected long nextRunTime;

        /**
         * Period in nanoseconds for repeating tasks.  A positive
         * value indicates fixed-rate execution.  A negative value
         * indicates fixed-delay execution.  A value of 0 indicates a
         * non-repeating task.
         */
        private final long period;

        /**
         * The actual task to be re-enqueued by reExecutePeriodic
         */
        RunnableScheduledFuture<V> outerTask = this;

        /**
         * Index into delay queue, to support faster cancellation.
         */
        int heapIndex;

        /**
         * Creates a one-shot action with given nanoTime-based execution time.
         */
        ManagedScheduledFutureTask(AbstractManagedExecutorService executor,
                                   Runnable r,
                                   V result,
                                   long ns) {
            this(executor, r, result, ns, 0L);
        }

        /**
         * Creates a one-shot action with given nanoTime-based execution time.
         */
        ManagedScheduledFutureTask(AbstractManagedExecutorService executor,
                                   Callable<V> callable,
                                   long ns) {
            this(executor, callable, ns, 0L);
        }

        /**
         * Creates a periodic action with given nano time and period.
         */
        ManagedScheduledFutureTask(AbstractManagedExecutorService executor,
                                   Runnable r,
                                   V result,
                                   long ns,
                                   long period) {
            super(executor, r, result);
            this.nextRunTime = ns;
            this.period = period;
            this.sequenceNumber = sequencer.getAndIncrement();
        }

        public ManagedScheduledFutureTask(AbstractManagedExecutorService executor, Callable callable, long ns, long period) {
            super(executor, callable);
            this.nextRunTime = ns;
            this.period = period;
            this.sequenceNumber = sequencer.getAndIncrement();
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(nextRunTime - now(), TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            if (other == this) {
                return 0;
            }
            if (other instanceof ManagedScheduledFutureTask) {
                ManagedScheduledFutureTask<?> x = (ManagedScheduledFutureTask<?>) other;
                long diff = nextRunTime - x.nextRunTime;
                if (diff < 0) {
                    return -1;
                } else if (diff > 0) {
                    return 1;
                } else if (sequenceNumber < x.sequenceNumber) {
                    return -1;
                } else {
                    return 1;
                }
            }
            long d = (getDelay(TimeUnit.NANOSECONDS) -
                    other.getDelay(TimeUnit.NANOSECONDS));
            return (d == 0) ? 0 : ((d < 0) ? -1 : 1);
        }

        @Override
        public boolean equals(Object other) {
            if (other instanceof ManagedScheduledFutureTask) {
                return compareTo((ManagedScheduledFutureTask) other) == 0;
            }
            return false;
        }

        @Override
        public int hashCode() {
            // using same logic as Long.hashCode()
            return (int) (sequenceNumber ^ (sequenceNumber >>> 32));
        }

        /**
         * Returns true if this is a periodic (not a one-shot) action.
         *
         * @return true if periodic
         */
        @Override
        public boolean isPeriodic() {
            return period != 0;
        }
    }
}
