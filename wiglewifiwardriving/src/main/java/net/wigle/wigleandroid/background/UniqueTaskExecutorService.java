package net.wigle.wigleandroid.background;

import net.wigle.wigleandroid.util.Logging;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
        if (!jobClasses.add(task.getClass())) {
            throw new IllegalArgumentException("instance of "+task.getClass()+" already in queue");
        }
        Logging.debug("===>SUBMITTED: "+task.getClass());
        // superclass will wrap by calling newTaskFor
        return super.submit(task);
    }

    @Override
    protected void beforeExecute(Thread t, Runnable r) {
        if (r instanceof WrappedTask) {
            WrappedTask wt = (WrappedTask)r;
            Logging.debug("===>EXECUTING: " + wt.getWrappedClass());
            current = r;
        } else {
            Logging.error("beforeExecute received non-WrappedTask runnable - cannot execute.");
        }
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        if (r instanceof  WrappedTask) {
            WrappedTask wt = (WrappedTask)r;
            jobClasses.remove(wt.getWrappedClass());
            current = null;
            Logging.debug("<===EXECUTED: " + wt.getWrappedClass());
        } else {
            Logging.error("afterExecute received non-WrappedTask runnable - cannot execute. (impossible case)");
        }
        super.afterExecute(r, t);
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value){
        return new WrappedTask(runnable,value, this);
    }

    final class WrappedTask<T> implements RunnableFuture<T> {
        /* where we're forwarding to */
        private final RunnableFuture<T> task;
        /* the type of the runnable we're wrapping */
        private final Class runnableType;

        /* the service we're running in, so we can tell it if we're canceled. */
        private final UniqueTaskExecutorService holder;
        public WrappedTask(Runnable runnable, T value, UniqueTaskExecutorService holder){
            this.runnableType = runnable.getClass();
            this.task = new FutureTask<>(runnable,value);
            this.holder = holder;
        }

        /**
         * get the type of the inner runnable
         */
        public Class getWrappedClass(){
            return runnableType;
        }

        @Override
        public void run() {
            task.run();
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            holder.jobClasses.remove(runnableType);
            return task.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return task.isCancelled();
        }

        @Override
        public boolean isDone() {
            return task.isDone();
        }

        @Override
        public T get() throws ExecutionException, InterruptedException {
            return task.get();
        }

        @Override
        public T get(long timeout, TimeUnit unit) throws ExecutionException, InterruptedException, TimeoutException {
            return task.get(timeout,unit);
        }
    }
}
