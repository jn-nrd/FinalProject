package com.example.finalproject;

import java.util.List;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.imgproc.Imgproc;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnTouchListener;
import android.view.SurfaceView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class detectobjbycam extends AppCompatActivity implements OnTouchListener, CvCameraViewListener2 {
    private static final String TAG = "detectobjbycam";

    private boolean mIsColorSelected = false;
    private Mat mRgba;
    private Scalar mBlobColorRgba;
    private Scalar mBlobColorHsv;
    private ColorBlobDetector mDetector;
    private Mat mSpectrum;
    private Size SPECTRUM_SIZE;
    private Scalar CONTOUR_COLOR; //Scalar กำหนดเป็นพื้นที่สีอื่น

    private TextView mResultTv;
    private TextView rgb;
    private TextView HexCode;
    private TextView Name;
    private Button colorBtn;
    Bitmap bitmap;
    private ClipData clipData;
    private ClipboardManager clipboardManager;

    private CameraBridgeViewBase mOpenCvCameraView;


    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                    mOpenCvCameraView.setOnTouchListener(detectobjbycam.this);
                }

                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };

    public detectobjbycam() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_detectobjbycam);

        mOpenCvCameraView = findViewById(R.id.color_blob_detection_activity_surface_view);
        mOpenCvCameraView.setVisibility(SurfaceView.VISIBLE);
        mOpenCvCameraView.setCvCameraViewListener(this);

        mOpenCvCameraView.setDrawingCacheEnabled(true);
        mOpenCvCameraView.buildDrawingCache(true);

        rgb = findViewById(R.id.resultTv);
        HexCode = findViewById(R.id.hex);
        Name = findViewById(R.id.name);

//        mResultTv = findViewById(R.id.resultTv);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (mOpenCvCameraView != null)
            mOpenCvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mDetector = new ColorBlobDetector();
        mSpectrum = new Mat();
        mBlobColorRgba = new Scalar(255);
        mBlobColorHsv = new Scalar(255);
        SPECTRUM_SIZE = new Size(200, 64);
        CONTOUR_COLOR = new Scalar(255, 0, 0, 255);
    }

    public void onCameraViewStopped() {
        mRgba.release();
    }

    // When a motion event happens (someone touches the device)
    public boolean onTouch(View v, MotionEvent event) {
        int cols = mRgba.cols(); //get resolution of display
        int rows = mRgba.rows(); //get resolution of display

        int xOffset = (mOpenCvCameraView.getWidth() - cols) / 2; //get resolution of display
        int yOffset = (mOpenCvCameraView.getHeight() - rows) / 2; //get resolution of display

        int x = (int) event.getX() - xOffset;
        int y = (int) event.getY() - yOffset;

        //The place where the screen was touched
        Log.i(TAG, "Touch image coordinates: (" + x + ", " + y + ")");

        if ((x < 0) || (y < 0) || (x > cols) || (y > rows)) return false;

        Rect touchedRect = new Rect();

        //Ensure it is a multiple of 4
        touchedRect.x = (x > 4) ? x - 4 : 0;
        touchedRect.y = (y > 4) ? y - 4 : 0;

        // If  x+4 < cols then ?"" else :""
        touchedRect.width = (x + 4 < cols) ? x + 4 - touchedRect.x : cols - touchedRect.x;
        touchedRect.height = (y + 4 < rows) ? y + 4 - touchedRect.y : rows - touchedRect.y;

        //create a touched regionmat from the image created from the touches
        Mat touchedRegionRgba = mRgba.submat(touchedRect);

        //Convert the new mat to HSV colour space
        Mat touchedRegionHsv = new Mat();
        Imgproc.cvtColor(touchedRegionRgba, touchedRegionHsv, Imgproc.COLOR_RGB2HSV_FULL);

        // Calculate average color of touched region
        mBlobColorHsv = Core.sumElems(touchedRegionHsv);
        int pointCount = touchedRect.width * touchedRect.height;
        for (int i = 0; i < mBlobColorHsv.val.length; i++)
            mBlobColorHsv.val[i] /= pointCount;

        //converts scalar to hsv to RGB
        mBlobColorRgba = converScalarHsv2Rgba(mBlobColorHsv);

        Log.i(TAG, "Touched rgba color: (" + mBlobColorRgba.val[0] + ", " + mBlobColorRgba.val[1] +
                ", " + mBlobColorRgba.val[2] + ", " + mBlobColorRgba.val[3] + ")");

        mDetector.setHsvColor(mBlobColorHsv);

        // Resize the image to specture size
        Imgproc.resize(mDetector.getSpectrum(), mSpectrum, SPECTRUM_SIZE, 0, 0, Imgproc.INTER_LINEAR_EXACT);

        mIsColorSelected = true;

        // Release all mats
        touchedRegionRgba.release();
        touchedRegionHsv.release();

        String hex = String.format("#%02x%02x%02x", (int)mBlobColorRgba.val[0], (int)mBlobColorRgba.val[1], (int)mBlobColorRgba.val[2]);
//        mResultTv.setText("RGB: " + (int)mBlobColorRgba.val[0] + ", " + (int)mBlobColorRgba.val[1] + ", " + (int)mBlobColorRgba.val[2] + "\nHex Code: " + hex.toUpperCase());
//


        rgb.setText("RGB: " + (int)mBlobColorRgba.val[0] + ", " + (int)mBlobColorRgba.val[1] + ", " + (int)mBlobColorRgba.val[2]);
        HexCode.setText("Hex Code: " + hex.toUpperCase());
//        Name.setText("\nColor name: " + colorNames[i]);

        final Button copyText = (Button) findViewById(R.id.copy);
        TextView rgb = (TextView)findViewById(R.id.resultTv);
        clipboardManager = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        copyText.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                String txtcopy = rgb.getText().toString();
                String copy = txtcopy.substring(5);
                clipData = ClipData.newPlainText("text",copy);
                clipboardManager.setPrimaryClip(clipData);
                Toast.makeText(getApplicationContext(),copy, Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(detectobjbycam.this,FindColorFromCamera.class);
                intent.putExtra("text", copy);
                startActivity(intent);
            }
        });

//        final Button copyText = (Button) findViewById(R.id.copy);
//        TextView hexcode = (TextView)findViewById(R.id.hex);
//        clipboardManager = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
//        copyText.setOnClickListener(new View.OnClickListener() {
//
//            @Override
//            public void onClick(View v) {
//                String txtcopy = hexcode.getText().toString();
//                String copy = txtcopy.substring(10);
//                clipData = ClipData.newPlainText("text",copy);
//                clipboardManager.setPrimaryClip(clipData);
//                Toast.makeText(getApplicationContext(),"Data Copied to Clipboard", Toast.LENGTH_SHORT).show();
//            }
//        });


        return false; // don't need subsequent touch events
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) { //colors in camera frame
        mRgba = inputFrame.rgba();

        if (mIsColorSelected) { //if selected new color then re process again
            mDetector.process(mRgba);

            List<MatOfPoint> contours = mDetector.getContours();
            Log.e(TAG, "Contours count: " + contours.size());

            MatOfPoint2f approxCurve = new MatOfPoint2f();
            //For each contour found
            for (int i=0; i<contours.size(); i++) {

//                Imgproc.drawContours(mRgba, contours, -1, CONTOUR_COLOR);
                Mat colorLabel = mRgba.submat(4, 68, 4, 68);
                colorLabel.setTo(mBlobColorRgba);
//                Mat spectrumLabel = mRgba.submat(4, 4 + mSpectrum.rows(), 70, 70 + mSpectrum.cols());
//                mSpectrum.copyTo(spectrumLabel);

                //Convert contours(i) from MatOfPoint to MatOfPoint2f
                MatOfPoint2f contour2f = new MatOfPoint2f( contours.get(i).toArray() );
                //Processing on mMOP2f1 which is in type MatOfPoint2f
                double approxDistance = Imgproc.arcLength(contour2f, true)*0.02;
                Imgproc.approxPolyDP(contour2f, approxCurve, approxDistance, true);

                //Convert back to MatOfPoint
                MatOfPoint points = new MatOfPoint( approxCurve.toArray() );

                // Get bounding rect of contour
                Rect rect = Imgproc.boundingRect(points);

                // draw enclosing rectangle (all same color, but you could use variable i to make them unique)
//                Core.rectangle(contoursFrame, new Point(rect.x,rect.y), new Point(rect.x+rect.width,rect.y+rect.height), (255, 0, 0, 255), 3);
                Imgproc.rectangle(mRgba, new Point(rect.x, rect.y), new Point(rect.x + rect.width, rect.y + rect.height),new Scalar(0, 255, 0));
            }
        }
        return mRgba;
    }

    //final conversion
    private Scalar converScalarHsv2Rgba(Scalar hsvColor) {
        Mat pointMatRgba = new Mat();
        Mat pointMatHsv = new Mat(1, 1, CvType.CV_8UC3, hsvColor);
        Imgproc.cvtColor(pointMatHsv, pointMatRgba, Imgproc.COLOR_HSV2RGB_FULL, 4);

        return new Scalar(pointMatRgba.get(0, 0));
    }
}
