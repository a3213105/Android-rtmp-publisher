package com.a3213105.publisher;

import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends AppCompatActivity  {

    private static final String rtmp_url = "rtmp://pushdl-live.genshuixue.com/mgclient/test_gs";
    private static final String TAG = "gs-main";
    private long presentationTimeUs;
    private VideoProcessor vp;
    private AudioProcessor ap;
    private RTMPSender sender;
    private boolean hasVideo = false;
    private boolean isAnnexbVideo = true;
    private boolean hasAudio = false;
    private boolean isADTSAudio = false;
    private TextView bitrate;
    private View btn;
    private long lastByteSended = 0;
    private PhotoProcessor ph;


    public MainActivity () {
        vp = null;
        ap = null;
        sender = null;
        ph = null;
    }

    private void publish() {
        final SurfaceView preview = (SurfaceView) findViewById(R.id.surfaceView);
        SurfaceHolder holder = preview.getHolder();
        // setType must be set in old version, otherwise may cause crash
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        }

        int rotation = this.getWindowManager().getDefaultDisplay().getRotation();
        switch (rotation) {
            case Surface.ROTATION_0: rotation = 0; break;
            case Surface.ROTATION_90: rotation = 90; break;
            case Surface.ROTATION_180: rotation = 180; break;
            case Surface.ROTATION_270: rotation = 270; break;
        }

        if(false) {
            ph = new PhotoProcessor(holder, 640, 480, 25, rotation);
            ph.initPhoto();
            ph.startPhoto();
        } else {
            if(sender==null)
                sender = new RTMPSender();
            if(vp==null)
                vp = new VideoProcessor(holder,sender,640,480,25, rotation);
            if(ap==null)
                ap = new AudioProcessor(sender);

            hasVideo = vp.initVideo()==0;
            hasAudio = ap.initAudio()==0;

            //sender.initSender(hasVideo, isAnnexbVideo, hasAudio, isADTSAudio, Environment.getExternalStorageDirectory().getAbsolutePath() + "/test.flv");
            sender.initSender(hasVideo, isAnnexbVideo, hasAudio, isADTSAudio, rtmp_url);

            presentationTimeUs = System.currentTimeMillis();
            if(hasVideo) {
                vp.startVideo(presentationTimeUs);
            }
            if(hasAudio) {
                ap.startAudio(presentationTimeUs);
            }
            sender.startSender();
        }
    }

    private void stop() {
        if(hasVideo && vp!=null) {
            vp.stopVideo();
        }
        if(hasAudio && ap!=null) {
            ap.stopAudio();
        }
        if(sender!=null)
            sender.stopSender();
        if(ph!=null) {
            ph.stopPhoto();
        }
        vp = null;
        ap = null;
        //sender = null;
        ph = null;
    }

    private void startBitrateTimer() {
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                bitrate.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            long tmp = lastByteSended;
                            if(sender!=null)
                                lastByteSended = sender.getByteSended();
                            tmp = (lastByteSended - tmp) / 128;
                            bitrate.setText(tmp + "kbps");
                            //Log.w(TAG, "current speed " + tmp + "kbps");
                        }
                    }, 1000);
                }
            }, 1000, 1000);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);
        //publish();
        btn = findViewById(R.id.button);
        btn.setOnClickListener(new PhotoOnClickListener());
        bitrate = (TextView) findViewById(R.id.bitrate);
        startBitrateTimer();
    }

    public class PhotoOnClickListener implements View.OnClickListener {
        public void onClick(View v) {
            if (ph!=null) {
                if(ph.isStart())
                    ph.takePhoto();
                else
                    ph.startPhoto();
            } else if (sender == null) {
                Log.i(TAG, "start to publish");
                publish();
                TextView bv = (TextView)v;
                bv.setText("stop");
            } else {
                Log.i(TAG,"stop publishing");
                stop();
                TextView bv = (TextView)v;
                bv.setText("start");
            }
        }
    }
}
