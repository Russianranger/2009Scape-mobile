import ViewDistance.plugin;
import rt4.GlRenderer;
import rt4.GlobalConfig;
import rt4.SceneGraph;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;

class SoftwareViewDistanceTest {
    public static void main(String[] args) throws Exception {
        // The verifier runs this in an isolated working directory, without a GL context.
        Files.write(Paths.get("viewdistance.properties"), Collections.singletonList("tiles=37"));
        if (GlRenderer.enabled) throw new AssertionError("Expected software rendering");

        plugin viewDistance = new plugin();
        viewDistance.Init();
        viewDistance.Draw(20);
        viewDistance.LateDraw(20);
        if (GlobalConfig.TILE_DISTANCE != 37 || SceneGraph.visibility != 37) {
            throw new AssertionError("The software renderer did not apply the saved view distance");
        }
    }
}
