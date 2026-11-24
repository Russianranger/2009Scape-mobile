package net.kdt.pojavlaunch;

import android.content.*;
import android.graphics.*;
import android.util.*;
import android.view.*;


import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.*;

public class AWTCanvasView extends TextureView implements TextureView.SurfaceTextureListener {
    public static final int AWT_CANVAS_WIDTH = 765;
    public static final int AWT_CANVAS_HEIGHT = 503;
    private static final double NANOS = 1000000000.0;
    private volatile int surfaceGeneration;

    public AWTCanvasView(Context ctx) {
        this(ctx, null);
    }

    public AWTCanvasView(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
        setSurfaceTextureListener(this);
        post(this::refreshSize);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture texture, int w, int h) {
        getSurfaceTexture().setDefaultBufferSize(AWT_CANVAS_WIDTH, AWT_CANVAS_HEIGHT);
        int generation = ++surfaceGeneration;
        Surface surface = new Surface(texture);
        new Thread(() -> render(surface, generation), "AndroidAWTRenderer").start();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
        surfaceGeneration++;
        return true;
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int w, int h) {
        getSurfaceTexture().setDefaultBufferSize(AWT_CANVAS_WIDTH, AWT_CANVAS_HEIGHT);
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        getSurfaceTexture().setDefaultBufferSize(AWT_CANVAS_WIDTH, AWT_CANVAS_HEIGHT);
    }

    private void render(Surface surface, int generation) {
        Bitmap bitmap = null;
        try {
            bitmap = Bitmap.createBitmap(AWT_CANVAS_WIDTH, AWT_CANVAS_HEIGHT, Bitmap.Config.ARGB_8888);
            int[] pixels = new int[AWT_CANVAS_WIDTH * AWT_CANVAS_HEIGHT];
            Paint paint = new Paint();
            paint.setFilterBitmap(false);
            final long frameTimeNanos = (long)(NANOS / 60);
            while (surfaceGeneration == generation && surface.isValid()) {
                long start = System.nanoTime();
                boolean hasFrame = JREUtils.renderAWTScreenFrame(pixels);
                if (hasFrame) bitmap.setPixels(pixels, 0, AWT_CANVAS_WIDTH, 0, 0, AWT_CANVAS_WIDTH, AWT_CANVAS_HEIGHT);
                if (surfaceGeneration != generation) break;
                Canvas canvas = surface.lockCanvas(null);
                try {
                    canvas.drawColor(Color.BLACK);
                    if (hasFrame) canvas.drawBitmap(bitmap, 0, 0, paint);
                } finally {
                    surface.unlockCanvasAndPost(canvas);
                }
                long remaining = frameTimeNanos - (System.nanoTime() - start);
                if (remaining > 0) Thread.sleep(remaining / 1000000, (int)(remaining % 1000000));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable e) {
            if (surfaceGeneration == generation) Tools.showError(getContext(), e);
        } finally {
            JREUtils.releaseAWTFrameReader();
            if (bitmap != null) bitmap.recycle();
            surface.release();
        }
    }

    /** Make the view fit the proper aspect ratio of the surface */
    private void refreshSize(){
        ViewGroup.LayoutParams layoutParams = getLayoutParams();


        /** Note: In the future this is a good way to stretch the aspect ratio too. Like for
         * 16:9 widescreen in SD mode like mudkip osrs videos */
        if(getHeight() < getWidth()){
            layoutParams.width = AWT_CANVAS_WIDTH * getHeight() / AWT_CANVAS_HEIGHT;
        }else{
            layoutParams.height = AWT_CANVAS_HEIGHT * getWidth() / AWT_CANVAS_WIDTH;
        }

        setLayoutParams(layoutParams);
    }

}
