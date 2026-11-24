package scapeprobe;
import java.io.*;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15;
public class DrawProbe {
    private static PrintStream stream;
    private static int n;
    public static void draw(int mode, int count, int type, long indices) {
        try {
            if(stream==null) stream=new PrintStream(new FileOutputStream("/data/user/0/net.kdt.pojavlaunch.debug/files/draw-probe.log"), true);
            ++n;
            if (n<=3000 || n%100==0) {
                int ebo=GL11.glGetInteger(0x8895);
                int bytes=ebo==0?0:GL15.glGetBufferParameteri(0x8893,0x8764);
                stream.printf("draw=%d mode=%x count=%d type=%x ptr=%x ebo=%d bytes=%d%n",n,mode,count,type,indices,ebo,bytes);
                if (n<12 || count==3 && n<=3000) new Throwable("indexed draw").printStackTrace(stream);
            }
        } catch(Throwable failure) {failure.printStackTrace();}
        GL11C.nglDrawElements(mode,count,type,indices);
    }
}
