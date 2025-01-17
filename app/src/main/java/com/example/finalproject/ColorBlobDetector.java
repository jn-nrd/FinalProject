package com.example.finalproject;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class
ColorBlobDetector {
    // Lower and Upper bounds for range checking in HSV color space
    private Scalar mLowerBound = new Scalar(0);
    private Scalar mUpperBound = new Scalar(0);
    // Minimum contour area in percent for contours filtering
    private static double mMinContourArea = 0.1;
    // Color radius for range checking in HSV color space
    private Scalar mColorRadius = new Scalar(25,50,50,0);
    private Mat mSpectrum = new Mat();
    private List<MatOfPoint> mContours = new ArrayList<MatOfPoint>();

    // Cache
    Mat mPyrDownMat = new Mat();
    Mat mHsvMat = new Mat();
    Mat mMask = new Mat();
    Mat mDilatedMask = new Mat();
    Mat mHierarchy = new Mat();

    public void setColorRadius(Scalar radius) {
        mColorRadius = radius;
    }

    public void setHsvColor(Scalar hsvColor) {
        double minH = (hsvColor.val[0] >= mColorRadius.val[0]) ? hsvColor.val[0]-mColorRadius.val[0] : 0;
        double maxH = (hsvColor.val[0]+mColorRadius.val[0] <= 255) ? hsvColor.val[0]+mColorRadius.val[0] : 255;

        mLowerBound.val[0] = minH;
        mUpperBound.val[0] = maxH;

        mLowerBound.val[1] = hsvColor.val[1] - mColorRadius.val[1];
        mUpperBound.val[1] = hsvColor.val[1] + mColorRadius.val[1];

        mLowerBound.val[2] = hsvColor.val[2] - mColorRadius.val[2];
        mUpperBound.val[2] = hsvColor.val[2] + mColorRadius.val[2];

        mLowerBound.val[3] = 0;
        mUpperBound.val[3] = 255;

        Mat spectrumHsv = new Mat(1, (int)(maxH-minH), CvType.CV_8UC3);

        for (int j = 0; j < maxH-minH; j++) {
            byte[] tmp = {(byte)(minH+j), (byte)255, (byte)255};
            spectrumHsv.put(0, j, tmp);
        }

        Imgproc.cvtColor(spectrumHsv, mSpectrum, Imgproc.COLOR_HSV2RGB_FULL, 4);
    }

    public Mat getSpectrum() {
        return mSpectrum;
    }

    public void setMinContourArea(double area) {
        mMinContourArea = area;
    }

    public void process(Mat rgbaImage) {
        //resize ภาพ แล้วตัดสีที่ไม่ได้เลือกออกจากบริเวณที่เลือก
        //สร้างขอบให้วัตถุ กำหนด area ที่ต้องการจะ draw contours
        Imgproc.pyrDown(rgbaImage, mPyrDownMat);
        Imgproc.pyrDown(mPyrDownMat, mPyrDownMat);

        //แปลงรูปแบบสีจากระบบ RGB เป็น HSV
        Imgproc.cvtColor(mPyrDownMat, mHsvMat, Imgproc.COLOR_RGB2HSV_FULL);

        //สร้างภาพใหม่ โดยมีค่าสีเฉพาะที่อยู่ใน lower และ upper
        //ค่าสีจะถูกคำนวณเมื่อเรียกเมธอด 'setHsvColor' เมื่อผู้ใช้เลือกสีที่หน้าจอ
        //ไม่ด้สีที่เลือกทั้งหมด มีสีอื่นปน เช่น เลือกสีแดง แต่มีค่า H ของสีส้มถ้าดูจากรูปล่างที่ H = 0 มันจะออกแดงๆส้มๆ บวกกับการปรับค่า S กับ V ก็จะทำให้ได้ Pixel บริเวณที่ขาดหายไป
        Core.inRange(mHsvMat, mLowerBound, mUpperBound, mMask);

        //กำหนดให้สีเลือกไปเป็นสีขาว ส่วนที่ไม่ได้เลือกเป็นสีดำ
        Imgproc.dilate(mMask, mDilatedMask, new Mat());

        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();

        //ค้นหาขอบของวัตถุ
        Imgproc.findContours(mDilatedMask, contours, mHierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        // Find max contour area กำหนดขอบเขตสีที่เราจิ้มให้ได้มากที่สุด
        double maxArea = 0;
        Iterator<MatOfPoint> each = contours.iterator();
        while (each.hasNext()) {
            MatOfPoint wrapper = each.next();
            double area = Imgproc.contourArea(wrapper);
            if (area > maxArea)
                maxArea = area;
        }

        // Filter contours by area and resize to fit the original image size
        // กรองรูปทรงตามพื้นที่และปรับขนาดให้พอดีกับขนาดภาพต้นฉบับ ในที่นี้เรากำลังละทิ้งรูปทรงที่ต่ำกว่าขนาดต่ำสุดที่เคยเป็น
        // ตั้งค่าในเมธอด 'setMinContourArea' หรือค่าเริ่มต้นหากไม่ได้ตั้งค่าไว้ ในอื่น ๆ
        // เว้นวัตถุขนาดเล็กที่ตรวจพบ
        mContours.clear();
        each = contours.iterator();
        //return list ของ blobs ที่เหลือ
        while (each.hasNext()) {
            MatOfPoint contour = each.next();
            if (Imgproc.contourArea(contour) > mMinContourArea*maxArea) {
                Core.multiply(contour, new Scalar(4,4), contour);
                mContours.add(contour);
            }
        }
    }

    //ส่งคืนรายการของรูปทรง
    //แต่ละเส้นเป็นพื้นที่ปิดที่มีสีตามสีที่ผู้ใช้เลือกเมื่อสัมผัสกับวัตถุ
    //สีนี้เพื่อเป็นการเตือนความจำถูกตั้งค่าโดยการเรียกไปที่ 'setHsvColor'
    public List<MatOfPoint> getContours() {
        return mContours;
    }
}
