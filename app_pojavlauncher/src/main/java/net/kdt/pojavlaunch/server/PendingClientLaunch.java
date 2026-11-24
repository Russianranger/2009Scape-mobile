package net.kdt.pojavlaunch.server;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;

/** Main-thread state for one Play request while the server screen receives status updates. */
final class PendingClientLaunch<T> {
    private String mode = "";
    private FutureTask<T> task;

    String mode() { return mode; }

    void select(String requested) {
        if (mode.equals(requested)) return;
        if (task != null) task.cancel(false);
        task = null;
        mode = requested;
    }

    T poll(Executor worker, Callable<T> prepare) throws Exception {
        if (mode.isEmpty()) return null;
        if (task == null) {
            task = new FutureTask<>(prepare);
            worker.execute(task);
        }
        if (!task.isDone()) return null;
        try {
            return task.get();
        } catch (ExecutionException e) {
            if (e.getCause() instanceof Exception) throw (Exception)e.getCause();
            throw e;
        } finally {
            task = null;
            mode = "";
        }
    }
}
