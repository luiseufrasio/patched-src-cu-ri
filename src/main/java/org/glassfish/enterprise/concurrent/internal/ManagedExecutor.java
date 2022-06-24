package org.glassfish.enterprise.concurrent.internal;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public interface ManagedExecutor {
    public long getTaskCount();

    public long getCompletedTaskCount();

    public int getCorePoolSize();

    public int getActiveCount();

    public long getKeepAliveTime(TimeUnit timeUnit);

    public int getLargestPoolSize();

    public int getMaximumPoolSize();

    public int getPoolSize();

    public BlockingQueue getQueue();

    public RejectedExecutionHandler getRejectedExecutionHandler();

    public ThreadFactory getThreadFactory();
}
