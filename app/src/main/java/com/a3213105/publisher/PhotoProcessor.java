package com.a3213105.publisher;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceHolder;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by a3213105 on 15/9/15.
 */
public class PhotoProcessor extends CameraHelper {
    private static final String TAG = "gs-photo";
    private static final File parentPath = Environment.getExternalStorageDirectory();
    private static String storagePath = "";
    private static final String DST_FOLDER_NAME = "a3213105";

    public PhotoProcessor(SurfaceHolder hd, int w, int h, int f, int r) {
        super(hd,w,h,f,r);
    }

    private static String initPath(){
        if(storagePath.equals("")){
            storagePath = parentPath.getAbsolutePath()+"/" + DST_FOLDER_NAME;
            File f = new File(storagePath);
            if(!f.exists()){
                f.mkdir();
            }
        }
        return storagePath;
    }

    private Camera.PictureCallback picture() {
        return new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                if(data==null)
                    return;

                stopCamera();
                String filename = initPath() + "/" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".jpg";

                //Toast.makeText(Ma, filename.subSequence(0, filename.length()), Toast.LENGTH_LONG ).show();

                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                Matrix matrix = new Matrix();
                matrix.postRotate(90);
                Bitmap newbitmap = Bitmap.createBitmap(bitmap,0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

                try {
                    FileOutputStream fout = new FileOutputStream(filename);
                    BufferedOutputStream bos = new BufferedOutputStream(fout);
                    newbitmap.compress(Bitmap.CompressFormat.JPEG, 100, bos);
                    bos.flush();
                    bos.close();
                    Log.i(TAG, "saveBitmap success");
                } catch (FileNotFoundException e) {
                    Log.i(TAG, "saveBitmap: failed");
                    e.printStackTrace();
                } catch (IOException e) {
                    Log.i(TAG, "saveBitmap: failed");
                    e.printStackTrace();
                }
            }
        };
    }

    public boolean isStart() {
        return preview_state;
    }

    public int initPhoto(){
        return initCamera(ImageFormat.JPEG);
    }

    public void startPhoto() {
        startCamera();
    }

    public void stopPhoto() {
        releaseCamera();
    }

    public void takePhoto() {
        camera.takePicture(null,null,picture());
    }
}
