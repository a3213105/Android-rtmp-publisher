package com.a3213105.publisher;

import android.hardware.Camera;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;
import android.view.SurfaceHolder;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by a3213105 on 15/9/8.
 */
public class VideoProcessor extends CameraHelper {
    private byte[] curframe;
    private int buffersize;
    private MediaCodec encoder;
    private MediaCodec.BufferInfo bi;
    private MediaCodecInfo mci;
    private int color;

    private int kbps = 200;
    private int fps = 15;
    private int gop = 2;
    private int complexity = 1;
    private int bitrat_mode = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR;
    private long vframes = 0;
    private int duration = 1000 / fps;

    private static final String CODEC = "video/avc";// H264
    private static final String TAG = "gs-video";

    private long presentationTimeUs;

    private Thread worker;
    private boolean loop;

    private final RTMPSender sender;

    public VideoProcessor(SurfaceHolder hd, RTMPSender ss, int w, int h, int f, int r) {
        super(hd,w,h,f,r);
        sender = ss;
        duration = 1000 / f;
    }

    private int getVideoEncoder() {
        List<MediaCodecInfo> vmcis = new ArrayList<MediaCodecInfo>();
        mci = chooseVideoEncoder(null, null);// 获取编码器
        mci = chooseVideoEncoder("google", mci);
        mci = chooseVideoEncoder("qcom", mci); // 确定使用qcom编码器

        int matchedColorFormat = 0;
        MediaCodecInfo.CodecCapabilities cc = mci.getCapabilitiesForType(CODEC);
        for (int i = 0; i < cc.colorFormats.length; i++) {
            int cf = cc.colorFormats[i];

            if ((cf >= MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV411Planar
                    && cf <= MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV422SemiPlanar)) {
                if (cf == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
                    matchedColorFormat = cf;
                    break;
                }
                if (cf > matchedColorFormat) {
                    matchedColorFormat = cf;
                }
            }
        }

//        MediaCodecInfo.EncoderCapabilities ec = cc.getEncoderCapabilities();
//        Range<Integer> cmpl =  ec.getComplexityRange();
//        complexity = (cmpl.getLower().intValue() + cmpl.getUpper().intValue()) / 2;
//
//        if(ec.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR))
//            bitrat_mode = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR;
//        else if(ec.isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR))
//            bitrat_mode = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR;
//        else
//            bitrat_mode = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ;


        for (int i = 0; i < cc.profileLevels.length; i++) {
            MediaCodecInfo.CodecProfileLevel pl = cc.profileLevels[i];
            Log.i(TAG, String.format("vencoder %s support profile %d, level %d",
                            mci.getName(), pl.profile, pl.level));
        }

        Log.i(TAG, String.format("vencoder %s choose color format 0x%x(%d)",
                mci.getName(), matchedColorFormat, matchedColorFormat));
        return matchedColorFormat;
    }

    private MediaCodecInfo chooseVideoEncoder(String name, MediaCodecInfo def) {

        int nbCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < nbCodecs; i++) {
            MediaCodecInfo mci = MediaCodecList.getCodecInfoAt(i);
            if (!mci.isEncoder()) {
                continue;
            }

            String[] types = mci.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(CODEC)) {
                    if (name == null) {
                        return mci;
                    }

                    if (mci.getName().contains(name)) {
                        return mci;
                    }
                }
            }
        }

        return def;
    }

    private void fetchFromEncode() {

        if (encoder == null)
            return;
        MediaCodec.BufferInfo vbi = new MediaCodec.BufferInfo();
        while (loop && encoder != null && !Thread.interrupted()) {
            ByteBuffer[] outBuffers = encoder.getOutputBuffers();
            int outBufferIndex = encoder.dequeueOutputBuffer(vbi, 10);
            if (outBufferIndex >= 0) {
                ByteBuffer bb = outBuffers[outBufferIndex];
                try {
                    //Log.i(TAG,"get encoeded frame from encoder pts=" + vbi.presentationTimeUs);
                    sender.writeVideoSample(bb,vbi);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                encoder.releaseOutputBuffer(outBufferIndex, false);
            }
        }
    }

    private static byte[] YV12toYUV420PackedSemiPlanar(final byte[] input, final byte[] output,
                                                       final int width, final int height) {

		/*
		 * COLOR_TI_FormatYUV420PackedSemiPlanar is NV12 We convert by putting
		 * the corresponding U and V bytes together (interleaved).
		 */
        final int frameSize = width * height;
        final int qFrameSize = frameSize / 4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y

        for (int i = 0; i < qFrameSize; i++) {
            output[frameSize + i * 2] = input[frameSize + i + qFrameSize]; // Cb
            // (U)
            output[frameSize + i * 2 + 1] = input[frameSize + i]; // Cr (V)
        }
        return output;
    }

    private static byte[] YV12toYUV420Planar(byte[] input, byte[] output, int width, int height) {

		/*
		 * COLOR_FormatYUV420Planar is I420 which is like YV12, but with U and V
		 * reversed. So we just have to reverse U and V.
		 */
        final int frameSize = width * height;
        final int qFrameSize = frameSize / 4;

        System.arraycopy(input, 0, output, 0, frameSize); // Y
        System.arraycopy(input, frameSize, output, frameSize + qFrameSize, qFrameSize); // Cr
        // (V)
        System.arraycopy(input, frameSize + qFrameSize, output, frameSize, qFrameSize); // Cb
        // (U)

        return output;
    }

    private void EncodeYuvFrame(byte[] data, long pts) {
        if (color == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
            YV12toYUV420Planar(data, curframe, width, height);
        } else if (color == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar) {
            YV12toYUV420PackedSemiPlanar(data, curframe, width, height);
        } else if (color == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
            YV12toYUV420PackedSemiPlanar(data, curframe, width, height);
        } else {
            System.arraycopy(data, 0, curframe, 0, data.length);
        }
        camera.addCallbackBuffer(data);

        ByteBuffer[] inBuffers = encoder.getInputBuffers();

        if (true) {
            int inBufferIndex = encoder.dequeueInputBuffer(-1);
            if (inBufferIndex >= 0) {
                ByteBuffer bb = inBuffers[inBufferIndex];
                bb.clear();
                bb.put(curframe, 0, curframe.length);
                encoder.queueInputBuffer(inBufferIndex, 0, curframe.length, pts, 0);
            }
        }
    }

    private Camera.PreviewCallback fetchVideoFromCamera() {

        return new Camera.PreviewCallback() {

            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                int curpts = (int) ((new Date().getTime() - presentationTimeUs));
                curpts = curpts / duration * duration;
                vframes++;
                //Log.i(TAG, "get yuv from camera pts=" + curpts + "   frames=" + vframes + "  duration=" + curpts / vframes);
                //camera.addCallbackBuffer(data);
                EncodeYuvFrame(data, curpts);
            }
        };
    }

    public int initVideo() {

        int ret = initCamera(0);
        if(ret!=0)
            return ret;

        buffersize = getYuvBuffer(width, height);
        for (int i = 0; i < fps; i++) {
            byte[] vbuffer = new byte[buffersize];
            camera.addCallbackBuffer(vbuffer);
        }
        curframe = new byte[buffersize];
        camera.setPreviewCallbackWithBuffer((Camera.PreviewCallback) fetchVideoFromCamera());

        // choose the right vencoder, perfer qcom then google.
        color = getVideoEncoder(); // 确定图像输入格式,优先高通
        try {
            encoder = MediaCodec.createByCodecName(mci.getName()); // 创建图像编码器
        } catch (IOException e) {
            Log.e(TAG, "create vEncoder failed.");
            e.printStackTrace();
            return 2;
        }
        bi = new MediaCodec.BufferInfo();


        MediaFormat vformat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
        vformat.setInteger(MediaFormat.KEY_COLOR_FORMAT, color);
        //vformat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);

        vformat.setInteger(MediaFormat.KEY_BIT_RATE, 1000 * kbps);
        vformat.setInteger(MediaFormat.KEY_BITRATE_MODE, bitrat_mode);

        vformat.setInteger(MediaFormat.KEY_FRAME_RATE, fps);
        vformat.setInteger(MediaFormat.KEY_CAPTURE_RATE, fps);

        vformat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, gop);
        vformat.setInteger(MediaFormat.KEY_COMPLEXITY,complexity);

        encoder.configure(vformat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);


        return 0;
    }

    private int getYuvBuffer(int width, int height) {

        // stride = ALIGN(width, 16)
        int stride = (int) Math.ceil(width / 16.0) * 16;
        // y_size = stride * height
        int y_size = stride * height;
        // c_stride = ALIGN(stride/2, 16)
        int c_stride = (int) Math.ceil(width / 32.0) * 16;
        // c_size = c_stride * height/2
        int c_size = c_stride * height / 2;
        // size = y_size + c_size * 2
        return y_size + c_size * 2;
    }

    public void startVideo(long us) {
        presentationTimeUs = us;
        encoder.start();

        worker = new Thread(new Runnable() {

            // @Override
            @Override
            public void run() {
                fetchFromEncode();
            }
        });

        // Log.i(TAG, "start audio worker thread.");
        loop = true;
        worker.start();

        startCamera();
    }

    public void stopVideo() {
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

        releaseCamera();

        if (encoder != null) {
            Log.i(TAG, "stop vencoder");
            encoder.stop();
            encoder.release();
            encoder = null;
        }

        curframe = null;
    }
}
