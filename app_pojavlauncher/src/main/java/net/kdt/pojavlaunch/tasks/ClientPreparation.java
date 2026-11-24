package net.kdt.pojavlaunch.tasks;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;

/** Shared by the launcher and the local-server screen, including notification entry. */
final class ClientPreparation {
    private FutureTask<Void> task;

    synchronized void start(Executor worker, Callable<Void> install) {
        if (task != null) return;
        task = new FutureTask<>(install);
        worker.execute(task);
    }

    synchronized boolean ready() throws IOException {
        if (task == null || !task.isDone()) return false;
        try {
            task.get();
            return true;
        } catch (ExecutionException e) {
            task = null; // A later Play request can retry after the error is displayed.
            Throwable cause = e.getCause();
            throw new IOException("Client setup failed: " + cause.getMessage(), cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while checking client setup", e);
        }
    }
}
