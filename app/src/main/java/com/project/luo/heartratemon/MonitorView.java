package com.project.luo.heartratemon;

import android.content.Context;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.util.Log;

import org.opencv.android.JavaCameraView;

/**
 * Created by luo on 11/1/15.
 */
public class MonitorView extends JavaCameraView {

    private static final String TAG = "MonitorView";
    //private String mPictureFileName;
    private boolean isFlashLightON = false;

    public MonitorView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    // Setup the camera
    public void setupCameraFlashLight() {
        Camera camera = mCamera;
        if (camera != null) {
            Camera.Parameters params = camera.getParameters();

            if (params != null) {
                if (isFlashLightON) {
                    isFlashLightON = false;
                    params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                    camera.setParameters(params);
                    camera.startPreview();
                } else {
                    isFlashLightON = true;
                    params.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                    camera.setParameters(params);
                    camera.startPreview();

                }
            }
            Log.i(TAG, "Camera");
        }
        Log.i(TAG, "Camera is null");
    }
}
