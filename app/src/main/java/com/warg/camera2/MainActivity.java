package com.warg.camera2;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private static final String TAG = "WarHammeR";
    String cameraID;
    Size cameraSize;
    CameraDevice camera;
    TextureView txv;
    CaptureRequest.Builder captureRequestBuilder;
    CameraCaptureSession cameraCaptureSession;
    HandlerThread handlerThread;
    Handler handler;


    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
            try {
                openCamera();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };

    CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(CameraDevice cameraDevice) {
            MainActivity.this.camera = cameraDevice;
            try {
                createCameraPreview();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {

        }

        @Override
        public void onError(CameraDevice cameraDevice, int i) {

        }
    };

    private void createCameraPreview() throws CameraAccessException {
        SurfaceTexture texture = txv.getSurfaceTexture();
        if (texture == null) return;
        texture.setDefaultBufferSize(cameraSize.getWidth(), cameraSize.getHeight());
        Surface surface = new Surface(texture);
        captureRequestBuilder = camera.createCaptureRequest(camera.TEMPLATE_PREVIEW);
        captureRequestBuilder.addTarget(surface);
        camera.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                if (camera == null) return;
                MainActivity.this.cameraCaptureSession = cameraCaptureSession;
                captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
            }

            @Override
            public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {

            }
        }, handler);
        cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, handler);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btn = (Button) findViewById(R.id.btnTakePicture);
        txv = (TextureView) findViewById(R.id.txvOutput);

        if (txv != null) {
            txv.setSurfaceTextureListener(textureListener);
        }
    }

    private void openCamera() throws CameraAccessException {
        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        cameraID = manager.getCameraIdList()[0];
        Log.d(TAG, "Open Camera " + cameraID);
        CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraID);
        StreamConfigurationMap map = characteristics.get(characteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (map != null) {
            cameraSize = map.getOutputSizes(SurfaceTexture.class)[0];
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
            return;
        }
        manager.openCamera(cameraID, stateCallback, null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handlerThread = new HandlerThread("Camera");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
        if (txv.isAvailable()) {
            try {
                openCamera();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        } else {
            txv.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                finish();
            }
        }
    }

    public void onClick(View view) throws CameraAccessException {
        takePicture();
    }

    private void takePicture() throws CameraAccessException {
        Size[] jpgSizes = null;
        int width = 640;
        int height = 480;

        if (camera == null) return;
        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        CameraCharacteristics characteristics = manager.getCameraCharacteristics(camera.getId());
        if (characteristics != null) {
            jpgSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
        }
        if (jpgSizes != null && jpgSizes.length > 0) {
            width = jpgSizes[0].getWidth();
            height = jpgSizes[0].getHeight();
        }
        final ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
        final CaptureRequest.Builder captureBuilder = camera.createCaptureRequest(camera.TEMPLATE_STILL_CAPTURE);
        captureBuilder.addTarget(reader.getSurface());
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, rotation);
        final File file = new File(Environment.getExternalStorageDirectory() + "/picture.jpg");
        reader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader imageReader) {
                Image image = null;
                image = reader.acquireLatestImage();
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] bytes = new byte[buffer.capacity()];
                buffer.get(bytes);
                try {
                    OutputStream output = new FileOutputStream(file);
                    output.write(bytes);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }, handler);
        List<Surface> outputSurface = new ArrayList<Surface>(2);
        outputSurface.add(reader.getSurface());
        outputSurface.add(new Surface(txv.getSurfaceTexture()));
        final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
            @Override
            public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                super.onCaptureCompleted(session, request, result);
                try {
                    createCameraPreview();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        };
        camera.createCaptureSession(outputSurface, new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                try {
                    cameraCaptureSession.capture(captureBuilder.build(),captureListener,handler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {

            }
        }, handler);
    }
}