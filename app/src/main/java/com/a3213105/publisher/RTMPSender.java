package com.a3213105.publisher;

import android.media.MediaCodec;
import android.util.Log;
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;

/**
 * Created by a3213105 on 15/9/8.
 */
public class RTMPSender {
    public long mNativeRTMP = 0;
    private Thread worker;
    private static final String TAG = "gs-sender";
    private boolean loop;
    private long btye_sended = 0;

    public RTMPSender() {
        loadLibs();
    }

    public long getByteSended() {
        return btye_sended;
    }

    public void writeAudioSample(final ByteBuffer bb, MediaCodec.BufferInfo bi) throws Exception {

        int pts = (int) (bi.presentationTimeUs);
        int size = bi.size;
        byte[] dst = null;
        try {
            dst = bb.array();
        } catch (ReadOnlyBufferException e) {

        } catch (Exception e) {
            dst = new byte[size];
            bb.get(dst);
        }

        int ret = _write_rtmp_audio(dst, size, pts);
        if(ret < 0 ) {
            Log.w(TAG, "write audio data error:" + ret);
        }
    }

    public void writeVideoSample(final ByteBuffer bb, MediaCodec.BufferInfo bi) throws Exception {

        int pts = (int) (bi.presentationTimeUs);
        int size = bi.size;
        byte[] dst = null;
        try {
            dst = bb.array();
        } catch (ReadOnlyBufferException e) {

        } catch (Exception e) {
            dst = new byte[size];
            bb.get(dst);
        }
        //Log.i(TAG,"_write_rtmp_video pts=" + pts + " size=" + size);
        int ret = _write_rtmp_video(dst, size, pts);
        if(ret < 0 ) {
            Log.w(TAG, "write video data error:" + ret);
        }
    }

    public void initSender(boolean has_video, boolean has_audio, String url) {
        _set_output_url(has_video ? 1 : 0, has_audio ? 1 : 0, url);
    }

    public void startSender() {
        int ret = _open();;
        if(ret!=0) {
            Log.e(TAG,"connect to rtmp server error" + ret);
            return ;
        }
        worker = new Thread(new Runnable() {

            // @Override
            @Override
            public void run() {
                send_loop();
            }
        });
        loop = true;
        worker.start();
    }

    public void stopSender() {
        loop = false;

        if (worker != null) {
            Log.i(TAG, "stop video worker thread");
            worker.interrupt();
            try {
                worker.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            worker = null;
        }
    }

    private void send_loop() {
        int ret = 0;
        while (loop && !Thread.interrupted()) {
            ret = _loop();
            if(ret<0) {
                Log.w(TAG, "send rtmp packet error " + ret);
            } else {
                btye_sended += ret;
                //Log.i(TAG, "send rtmp packet total " + btye_sended);
            }
        }
    }

    private void loadLibs() {
        System.loadLibrary("rtmp");
        Log.i(TAG, "rtmp.so loaded");
        System.loadLibrary("rtmpjni");
        Log.i(TAG, "rtmpjni.so loaded");
    }

    private native int _set_output_url(int havV, int hasA, String url);
    private native int _open();
    private native int _close();
    private native int _loop();
    private native int _write_rtmp_audio(byte[] dts, int size, int pts);
    private native int _write_rtmp_video(byte[] dts, int size, int pts);
}
