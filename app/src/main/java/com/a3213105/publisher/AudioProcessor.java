package com.a3213105.publisher;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.util.Log;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Date;

/**
 * Created by a3213105 on 15/9/8.
 */
public class AudioProcessor {
    private static int kbps = 32;
    private static final String TAG = "gs-audio";
    private static final String CODEC = "audio/mp4a-latm"; //aac

    private AudioRecord mic;
    private byte[] buffer;
    private MediaCodec encoder;
    private MediaCodec.BufferInfo bi;
    private int sample_rate;
    private int channel;
    private int bits;
    private int track;

    private long presentationTimeUs;

    private Thread worker;
    private boolean loop;
    private final RTMPSender sender;

    public AudioProcessor(RTMPSender ss) {
        sender = ss;
    }

    private AudioRecord chooseAudioDevice() {

        int[] sampleRates = { 44100, 22050, 11025 }; // 采样率 , 单位千赫兹
        for (int sampleRate : sampleRates) {
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
            int channelConfig = AudioFormat.CHANNEL_CONFIGURATION_STEREO;

            int bSamples = 8;
            if (audioFormat == AudioFormat.ENCODING_PCM_16BIT) {
                bSamples = 16;// 量化大小
            }

            int nChannels = 2;// 声道
            if (channelConfig == AudioFormat.CHANNEL_CONFIGURATION_MONO) {
                nChannels = 1;
            }

            // int bufferSize = 2 * bSamples * nChannels / 8;
            int bufferSize = 5 * AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
            AudioRecord audioRecorder = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, bufferSize);

            if (audioRecorder.getState() != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "initialize the mic failed.");
                continue;
            }

            sample_rate = sampleRate;
            bits = audioFormat;
            channel = nChannels;
            mic = audioRecorder;
            buffer = new byte[Math.min(4096, bufferSize)];
            // abuffer = new byte[bufferSize];
            Log.i(TAG, String.format("mic open rate=%dHZ, channels=%d, bits=%d, buffer=%d/%d, state=%d",
                    sampleRate, nChannels, bSamples, bufferSize, buffer.length, audioRecorder.getState()));
            break;
        }

        return mic;
    }

    private void onGetPcmFrame(byte[] data) {
        ByteBuffer[] inBuffers = encoder.getInputBuffers();
        ByteBuffer[] outBuffers = encoder.getOutputBuffers();

        if (true) {
            int inBufferIndex = encoder.dequeueInputBuffer(-1);
            if (inBufferIndex >= 0) {
                ByteBuffer bb = inBuffers[inBufferIndex];
                bb.clear();
                bb.put(data, 0, data.length);
                long pts = new Date().getTime() - presentationTimeUs;
                encoder.queueInputBuffer(inBufferIndex, 0, data.length, pts, 0);
            }
        }

        for (;;) {
            int outBufferIndex = encoder.dequeueOutputBuffer(bi, 0);
            if (outBufferIndex >= 0) {
                ByteBuffer bb = outBuffers[outBufferIndex];
                try {
                    sender.writeAudioSample(bb,bi);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                encoder.releaseOutputBuffer(outBufferIndex, false);
            } else {
                break;
            }
        }
    }

    private void fetchAudioFromMic() {

        while (loop && mic != null && !Thread.interrupted()) {
            int size = mic.read(buffer, 0, buffer.length); // PCM 原轨数据
            if (size <= 0) {
                Log.i(TAG, "audio ignore, no data to read.");
                break;
            }
            onGetPcmFrame(buffer);
        }
    }

    public int initAudio() {
        if ((mic = chooseAudioDevice()) == null) {
            Log.e(TAG, String.format("mic find device mode failed."));
            return 1;
        }

        try {
            encoder = MediaCodec.createEncoderByType(CODEC);
        } catch (IOException e) {
            Log.e(TAG, "create aencoder failed.");
            e.printStackTrace();
            return 2;
        }
        bi = new MediaCodec.BufferInfo();

        MediaFormat aformat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sample_rate, channel);
        aformat.setInteger(MediaFormat.KEY_BIT_RATE, 1000 * kbps);
        aformat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 0);
        try {
            encoder.configure(aformat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        }
//        catch (MediaCodec.CodecException e) {
//            e.printStackTrace();
//            return 3;
//        }
        catch (Exception e) {
            e.printStackTrace();
            return 4;
        }
        return 0;
    }

    public void startAudio(long us) {
        presentationTimeUs = us;
        encoder.start();
        mic.startRecording();

        worker = new Thread(new Runnable() {

            // @Override
            @Override
            public void run() {
                fetchAudioFromMic();
            }
        });

        // Log.i(TAG, "start audio worker thread.");
        loop = true;
        worker.start();
    }

    public void stopAudio() {
        loop = false;

        if (worker != null) {
            Log.i(TAG, "stop audio worker thread");
            worker.interrupt();
            try {
                worker.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            worker = null;
        }

        if (mic != null) {
            Log.i(TAG, "stop mic");
            mic.setRecordPositionUpdateListener(null);
            mic.stop();
            mic.release();
            mic = null;
        }

        if (encoder != null) {
            Log.i(TAG, "stop aencoder");
            encoder.stop();
            encoder.release();
            encoder = null;
        }

    }
}
