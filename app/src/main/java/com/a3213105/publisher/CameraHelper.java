package com.a3213105.publisher;

import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.util.Log;
import android.view.SurfaceHolder;
import java.io.IOException;
import java.util.List;

/**
 * Created by a3213105 on 15/9/15.
 */
public class CameraHelper {
    protected Camera camera;
    protected final SurfaceHolder holder;
    protected int width = 640;
    protected int height = 480;
    protected int fps = 25;
    private int rotation = 0;
    private static final String TAG = "gs-Camera";
    protected boolean preview_state = false;

    public CameraHelper(SurfaceHolder hd, int w, int h, int f, int r) {
        holder = hd;
        width = w;
        height = h;
        fps = f;
        rotation = r;
    }

    private Camera.Size getSize(List<Camera.Size> szlist) {
        Camera.Size sz = null;
        for(int i=0; i<szlist.size(); i++) {
            sz = szlist.get(i);
            if(sz.height <= height && sz.width <= width)
                return sz;
        }
        return sz;
    }

    private int[] getFps(List<int[]> fpslist) {
        int fff = fps * 1000;
        int[] f = new int[2];
        f[0] = 0;
        f[1] = fff;
        for(int i=0;i<fpslist.size();i++){
            int[] f_pair = fpslist.get(i);
            if(f_pair[0] <= fff && f_pair[1] >= fff) {
                if( (f[1]-f[0]) > (f_pair[1]-f_pair[0])) {
                    f[0] = f_pair[0];
                    f[1] = f_pair[1];
                }
            }
        }
        return f;
    }

    protected int initCamera(int fmt){
        int num = Camera.getNumberOfCameras();
        if(num==0)
            return -1;
        Camera.CameraInfo cinfo = new Camera.CameraInfo();
        for(num--;num>=0;num--) {
            Camera.getCameraInfo(num, cinfo);
            if(cinfo.facing== Camera.CameraInfo.CAMERA_FACING_BACK)
                break;
        }
        try {
            camera = Camera.open(num);// N ， 0- (n-1)
        }
        catch (Exception e) {
            e.printStackTrace();
            return 1;
        }
        Camera.Parameters parameters = camera.getParameters();

        if(fmt==ImageFormat.JPEG)
            parameters.setPictureFormat(fmt);

        List<String> focusModes = parameters.getSupportedFocusModes();
        if(focusModes.contains("continuous-video")){
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        }

        Camera.Size size = null;
        List<Camera.Size> sizes = parameters.getSupportedPictureSizes();
        size = getSize(sizes);
        parameters.setPictureSize(size.width,size.height);

        size = null;
        sizes = parameters.getSupportedPreviewSizes();
        size = getSize(sizes);

        parameters.setPreviewSize(size.width, size.height);
        parameters.setPreviewFormat(ImageFormat.YV12); // 图像格式

        List<int[]> fpsrange = parameters.getSupportedPreviewFpsRange();
        int[] f = getFps(fpsrange);
        //parameters.setPreviewFpsRange(f[0], f[1]);
        parameters.setPreviewFrameRate(fps);// 视频帧率

        if(parameters.isVideoStabilizationSupported())
            parameters.setVideoStabilization(true);

        int result;
        if (cinfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (cinfo.orientation + rotation) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (cinfo.orientation - rotation + 360) % 360;
        }
        parameters.setRotation(result);
        camera.setDisplayOrientation(result);

        try {
            camera.setParameters(parameters);
        } catch (Exception e) {
            e.printStackTrace();
            return 1;
        }

        try {
            camera.setPreviewDisplay(holder);
        } catch (IOException e) {
            Log.e(TAG, "preview video failed.");
            e.printStackTrace();
            return 3;
        }
        return 0;
    }

    protected void startCamera() {
        camera.startPreview();
        preview_state = true;
    }

    protected void stopCamera() {

        if (camera != null) {
            Log.i(TAG, "stop preview");
            camera.stopPreview();
            preview_state = false;
        }
    }

    protected void releaseCamera() {
        if (camera != null) {
            Log.i(TAG, "stop preview");
            camera.setPreviewCallbackWithBuffer(null);
            camera.stopPreview();
            preview_state = false;
            camera.release();
            camera = null;
        }
    }
}
