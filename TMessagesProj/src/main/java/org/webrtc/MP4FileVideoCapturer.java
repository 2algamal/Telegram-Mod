package org.webrtc;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

import java.nio.ByteBuffer;

public class MP4FileVideoCapturer implements VideoCapturer {
    private final Context context;
    private final Uri videoUri;
    private HandlerThread thread;
    private Handler handler;
    private CapturerObserver capturerObserver;
    private MediaMetadataRetriever retriever;
    private volatile boolean isRunning = false;
    private volatile boolean isDisposed = false;
    private long durationMs = 10000;
    private long startTimeMs = 0;
    private static final int TARGET_W = 320;

    public MP4FileVideoCapturer(Context context, Uri videoUri) {
        this.context = context.getApplicationContext();
        this.videoUri = videoUri;
    }

    @Override
    public void initialize(SurfaceTextureHelper ste, Context ctx, CapturerObserver obs) {
        this.capturerObserver = obs;
        thread = new HandlerThread("MP4FileVideoCapturer");
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    @Override
    public void startCapture(int w, int h, int fps) {
        if (isRunning || isDisposed) return;
        isRunning = true;
        handler.post(this::initAndStart);
    }

    private void initAndStart() {
        try {
            retriever = new MediaMetadataRetriever();
            retriever.setDataSource(context, videoUri);
            String dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            durationMs = dur != null ? Long.parseLong(dur) : 10000;
            startTimeMs = SystemClock.elapsedRealtime();
            Logging.d("MP4FileVideoCapturer", "duration=" + durationMs + "ms");
            scheduleNext();
        } catch (Exception e) {
            Logging.e("MP4FileVideoCapturer", "init failed", e);
        }
    }

    private void scheduleNext() {
        if (!isRunning || isDisposed) return;
        handler.postDelayed(this::tick, 40); // 25 fps
    }

    private void tick() {
        if (!isRunning || isDisposed || retriever == null) return;
        try {
            long elapsed = SystemClock.elapsedRealtime() - startTimeMs;
            if (elapsed >= durationMs) {
                startTimeMs = SystemClock.elapsedRealtime();
                elapsed = 0;
            }
            Bitmap bmp = retriever.getFrameAtTime(elapsed * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
            if (bmp != null) {
                deliverBitmap(bmp);
                bmp.recycle();
            }
        } catch (Exception e) {
            Logging.e("MP4FileVideoCapturer", "tick failed", e);
        }
        scheduleNext();
    }

    private void deliverBitmap(Bitmap bmp) {
        try {
            int w = TARGET_W;
            int h = (int)((float)bmp.getHeight() / bmp.getWidth() * w);
            if (h % 2 != 0) h++;

            Bitmap scaled = Bitmap.createScaledBitmap(bmp, w, h, false);
            int[] pixels = new int[w * h];
            scaled.getPixels(pixels, 0, w, 0, 0, w, h);

            JavaI420Buffer i420 = JavaI420Buffer.allocate(w, h);
            ByteBuffer yBuf = i420.getDataY();
            ByteBuffer uBuf = i420.getDataU();
            ByteBuffer vBuf = i420.getDataV();
            int strideY = i420.getStrideY();
            int strideU = i420.getStrideU();
            int strideV = i420.getStrideV();

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int px = pixels[y * w + x];
                    int r = (px >> 16) & 0xFF;
                    int g = (px >> 8) & 0xFF;
                    int b = px & 0xFF;
                    int yv = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
                    yBuf.put(y * strideY + x, (byte)Math.max(0, Math.min(255, yv)));
                    if ((y & 1) == 0 && (x & 1) == 0) {
                        int u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                        int v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
                        uBuf.put((y / 2) * strideU + (x / 2), (byte)Math.max(0, Math.min(255, u)));
                        vBuf.put((y / 2) * strideV + (x / 2), (byte)Math.max(0, Math.min(255, v)));
                    }
                }
            }

            VideoFrame frame = new VideoFrame(i420, 0, SystemClock.elapsedRealtimeNanos());
            if (capturerObserver != null) capturerObserver.onFrameCaptured(frame);
            frame.release();
            i420.release();
            if (scaled != bmp) scaled.recycle();
        } catch (Exception e) {
            Logging.e("MP4FileVideoCapturer", "deliver failed", e);
        }
    }

    @Override
    public void stopCapture() { isRunning = false; }

    @Override
    public void changeCaptureFormat(int w, int h, int fps) {}

    @Override
    public void dispose() {
        isDisposed = true;
        isRunning = false;
        if (thread != null) { thread.quitSafely(); thread = null; }
        handler = null;
        capturerObserver = null;
        if (retriever != null) {
            try { retriever.release(); } catch (Exception ignored) {}
            retriever = null;
        }
    }

    @Override
    public boolean isScreencast() { return false; }
}
