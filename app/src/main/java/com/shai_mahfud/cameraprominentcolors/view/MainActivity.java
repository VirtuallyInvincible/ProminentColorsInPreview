/*
  All rights reserved to Shai Mahfud.
 */

package com.shai_mahfud.cameraprominentcolors.view;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatButton;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import com.shai_mahfud.cameraprominentcolors.R;

import java.io.IOException;

// The new (non-deprecated) Camera2 API is reported to be broken and people advice to stick with
// the older API
@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback,
        View.OnClickListener {
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 1;
    private static final int APP_PERMISSIONS_REQUEST_CODE = 2;
    private static final int NUM_OF_COLOR_DISTRIBUTIONS = 5;


    private Camera camera;
    private SurfaceView surfaceView;
    private CameraColorDistributionView colorDistributionContainer;
    private FloatingActionButton fab;
    private boolean snackbarShown = false;
    private boolean resumePlaying = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        surfaceView = findViewById(R.id.activity_main_surface_view);
        colorDistributionContainer = findViewById(R.id.activity_main_color_distribution);
        fab = findViewById(R.id.fab);

        fab.setOnClickListener(this);

        colorDistributionContainer.insertItems(this, NUM_OF_COLOR_DISTRIBUTIONS);

        tryLaunchCamera();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            for (int i = 0; i < permissions.length; i++) {
                String permission = permissions[i];
                if (android.Manifest.permission.CAMERA.equals(permission)) {
                    int grantResult = grantResults[i];
                    if (grantResult == 0) {
                        launchCamera();
                    } else {
                        snackbarShown = true;
                        Snackbar.make(surfaceView, R.string.no_permission_message,
                                Snackbar.LENGTH_INDEFINITE)
                            .setAction(R.string.go_to_permissions, this)
                            .show();
                    }
                    break;
                }
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == APP_PERMISSIONS_REQUEST_CODE) {
            tryLaunchCamera();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (camera == null) {
            tryLaunchCamera();
        }
    }

    @Override
    protected void onPause() {
        releaseCameraAndPreview();
        super.onPause();
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        try {
            camera.setPreviewDisplay(surfaceHolder);
            camera.setPreviewCallback(colorDistributionContainer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        camera.startPreview();
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        releaseCameraAndPreview();
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        switch (id) {
            case R.id.fab:
                resumePlaying = !resumePlaying;
                int newIcon = resumePlaying ? R.drawable.play : R.drawable.pause;
                fab.setImageResource(newIcon);
                if (resumePlaying) {
                    camera.setPreviewCallback(null);
                    camera.stopPreview();
                } else {
                    camera.setPreviewCallback(colorDistributionContainer);
                    camera.startPreview();
                }
                break;
            default:
                if (view != null && view instanceof AppCompatButton) {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", getPackageName(), null));
                    startActivityForResult(intent, APP_PERMISSIONS_REQUEST_CODE);
                    snackbarShown = false;
                }
                break;
        }
    }

    private void tryLaunchCamera() {
        if (snackbarShown) {
            return; // No access until the user grants permissions. Don't request the permission
                    // with requestPermission(), because the user may have checked the don't show
                    // again button, in which case the dialog will be shown and immediately closed,
                    // causing an infinite loop between onPause() and onResume().
        }

        if (ContextCompat.checkSelfPermission(getApplicationContext(),
                android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            launchCamera();
        }
    }

    private void launchCamera() {
        initSurfaceHolder();
        safeCameraOpen();
    }

    private void initSurfaceHolder() {
        SurfaceHolder surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    private void safeCameraOpen() {
        int cameraId = getFrontFacingCamera();
        try {
            releaseCameraAndPreview();
            camera = Camera.open(cameraId);
        } catch (Exception e) {
            Log.e(getString(R.string.app_name), "failed to open Camera");
            e.printStackTrace();
        }
    }

    private void releaseCameraAndPreview() {
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    private int getFrontFacingCamera() {
        int cameraId = -1;
        int numberOfCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numberOfCameras; i++) {
            Camera.CameraInfo info = new Camera.CameraInfo();
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                cameraId = i;
                break;
            }
        }
        return cameraId;
    }
}
