package org.webrtc;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;

public class MP4FileVideoCapturer implements VideoCapturer {
    private static final String TAG = "MP4FileVideoCapturer";
    private final Context context;
    private final Uri videoUri;
    private HandlerThread thread;
    private Handler handler;
    private CapturerObserver capturerObserver;
    private MediaPlayer mediaPlayer;
    private SurfaceTexture surfaceTexture;
    private Surface surface;
    private volatile boolean isRunning = false;
    private volatile boolean isDisposed = false;

    public MP4FileVideoCapturer(Context context, Uri videoUri) {
        this.context = context.getApplicationContext();
        this.videoUri = videoUri;
    }

    @Override
    public void initialize(SurfaceTextureHelper surfaceTextureHelper, Context applicationContext, CapturerObserver capturerObserver) {
        this.capturerObserver = capturerObserver;
        thread = new HandlerThread("MP4FileVideoCapturerThread");
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    @Override
    public void startCapture(int width, int height, int framerate) {
        if (isRunning || isDisposed) return;
        isRunning = true;
        handler.post(this::startPlayback);
    }

    @Override
    public void stopCapture() {
        if (!isRunning) return;
        isRunning = false;
        handler.post(this::stopPlayback);
    }

    @Override
    public void changeCaptureFormat(int width, int height, int framerate) {}

    @Override
    public void dispose() {
        isDisposed = true;
        stopCapture();
        if (thread != null) {
            thread.quitSafely();
            thread = null;
        }
        handler = null;
        capturerObserver = null;
    }

    @Override
    public boolean isScreencast() { return false; }

    private void startPlayback() {
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(context, videoUri);
            surfaceTexture = new SurfaceTexture(0);
            surfaceTexture.setDefaultBufferSize(640, 480);
            surface = new Surface(surfaceTexture);
            mediaPlayer.setSurface(surface);
            mediaPlayer.setLooping(true);
            mediaPlayer.setOnPreparedListener(mp -> {
                mp.start();
                startFrameDelivery();
            });
            mediaPlayer.prepareAsync();
        } catch (Exception e) {
            Logging.e(TAG, "startPlayback failed", e);
        }
    }

    private void startFrameDelivery() {
        long frameIntervalMs = 40;
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (!isRunning || isDisposed) return;
                try {
                    if (capturerObserver != null) {
                        JavaI420Buffer buffer = JavaI420Buffer.allocate(640, 480);
                        VideoFrame frame = new VideoFrame(buffer, 0, System.nanoTime());
                        capturerObserver.onFrameCaptured(frame);
                        frame.release();
                        buffer.release();
                    }
                } catch (Exception e) {
                    Logging.e(TAG, "frame delivery error", e);
                }
                handler.postDelayed(this, frameIntervalMs);
            }
        });
    }

    private void stopPlayback() {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            }
            if (surfaceTexture != null) {
                surfaceTexture.release();
                surfaceTexture = null;
            }
            if (surface != null) {
                surface.release();
                surface = null;
            }
        } catch (Exception ignored) {}
    }
}
