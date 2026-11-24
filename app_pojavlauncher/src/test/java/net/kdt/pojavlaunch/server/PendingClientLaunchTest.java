package net.kdt.pojavlaunch.server;

import org.junit.Test;
import java.io.IOException;
import java.util.ArrayDeque;
import static org.junit.Assert.*;

public class PendingClientLaunchTest {
    @Test public void repeatedStatusAndTapsUseOneWorkerAndDeliverOneIntent() throws Exception {
        PendingClientLaunch<String> launch = new PendingClientLaunch<>();
        ArrayDeque<Runnable> workers = new ArrayDeque<>();
        launch.select("hd");
        assertNull(launch.poll(workers::add, () -> "HD intent"));
        launch.select("hd");
        assertNull(launch.poll(workers::add, () -> "duplicate"));
        assertEquals(1, workers.size());
        workers.remove().run();
        assertEquals("HD intent", launch.poll(workers::add, () -> "duplicate"));
        assertNull(launch.poll(workers::add, () -> "duplicate"));
        assertTrue(workers.isEmpty());
    }

    @Test public void stoppedOrSupersededRequestCannotOpenStaleClient() throws Exception {
        PendingClientLaunch<String> launch = new PendingClientLaunch<>();
        ArrayDeque<Runnable> workers = new ArrayDeque<>();
        launch.select("hd");
        launch.poll(workers::add, () -> "HD intent");
        workers.remove().run();
        launch.select("sd");
        assertNull(launch.poll(workers::add, () -> "SD intent"));
        launch.select("");
        workers.remove().run();
        assertNull(launch.poll(workers::add, () -> "late"));
        assertTrue(workers.isEmpty());
    }

    @Test public void originalPreparationErrorIsReportedOnceAndCanRetry() throws Exception {
        PendingClientLaunch<String> launch = new PendingClientLaunch<>();
        launch.select("hd");
        IOException expected = new IOException("Could not write profile");
        try {
            launch.poll(Runnable::run, () -> { throw expected; });
            fail();
        } catch (IOException e) { assertSame(expected, e); }
        assertEquals("", launch.mode());
        launch.select("hd");
        assertEquals("retry", launch.poll(Runnable::run, () -> "retry"));
    }
}
