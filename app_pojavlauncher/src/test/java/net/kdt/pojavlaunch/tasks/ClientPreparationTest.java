package net.kdt.pojavlaunch.tasks;

import org.junit.Test;
import java.io.IOException;
import java.util.ArrayDeque;
import static org.junit.Assert.*;

public class ClientPreparationTest {
    @Test public void launcherAndNotificationShareOneInstallAndWaitForIt() throws Exception {
        ClientPreparation preparation = new ClientPreparation();
        ArrayDeque<Runnable> queued = new ArrayDeque<>();
        preparation.start(queued::add, () -> null);
        preparation.start(queued::add, () -> { throw new AssertionError("duplicate install"); });
        assertFalse(preparation.ready());
        assertEquals(1, queued.size());
        queued.remove().run();
        assertTrue(preparation.ready());
        preparation.start(queued::add, () -> null);
        assertTrue(queued.isEmpty());
    }

    @Test public void failedInstallShowsCauseAndPlayCanRetry() throws Exception {
        ClientPreparation preparation = new ClientPreparation();
        preparation.start(Runnable::run, () -> { throw new IOException("components/lwjgl3/version"); });
        try {
            preparation.ready();
            fail("Failed asset extraction must block launch");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("components/lwjgl3/version"));
        }
        preparation.start(Runnable::run, () -> null);
        assertTrue(preparation.ready());
    }
}
