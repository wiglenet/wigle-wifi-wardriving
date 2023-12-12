package net.wigle.wigleandroid.background;

import net.wigle.wigleandroid.util.Logging;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class UniqueTaskExecutorService extends ThreadPoolExecutor {
    private final Set<Class> jobClasses;
    private Runnable current;
    public UniqueTaskExecutorService(int nThreads) {
        super (nThreads, nThreads,
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>());
        jobClasses = new HashSet<>();
    }

    @Override
    public Future<?> submit(Runnable task) throws IllegalArgumentException {
        if (jobClasses.contains(task.getClass())) {
            throw new IllegalArgumentException("instance of "+task.getClass()+" already in queue");
        }
        Logging.debug("===>SUBMITTED: "+task.getClass());
        jobClasses.add(task.getClass());
        return super.submit(task);
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        Logging.debug("===>EXECUTING: "+r.getClass());
        current = r;
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        jobClasses.remove(r.getClass());
        current = null;
        Logging.debug("<===EXECUTED: "+r.getClass());
        super.afterExecute(r, t);
    }
}
