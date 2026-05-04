package org.example;

import net.sourceforge.tess4j.Tesseract;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_core.Point;
import java.io.File;
import static org.bytedeco.opencv.global.opencv_core.minMaxLoc;

public class VisionEngine {
    private final Tesseract tesseract;

    public VisionEngine(String tessdataPath) {
        this.tesseract = new Tesseract();
        this.tesseract.setDatapath(new File(tessdataPath).getAbsolutePath());
        this.tesseract.setLanguage("eng");
        this.tesseract.setPageSegMode(7);
        this.tesseract.setTessVariable("tessedit_char_whitelist", "0123456789,.MWmw");
    }

    // Метод для поиска углов (твой findLocation)
    public Point findTemplate(Mat scene, Mat template) {
        double[] scales = {0.6, 0.7, 0.8, 0.9, 1.0, 1.1, 1.2, 1.3, 1.4};
        double bestMaxVal = -1;
        Point bestMaxLoc = null;

        for (double scale : scales) {
            Mat resT = new Mat();
            opencv_imgproc.resize(template, resT, new Size((int)(template.cols()*scale), (int)(template.rows()*scale)));
            Mat result = new Mat();
            opencv_imgproc.matchTemplate(scene, resT, result, opencv_imgproc.TM_CCOEFF_NORMED);
            DoublePointer maxVal = new DoublePointer(1);
            Point maxLoc = new Point();
            minMaxLoc(result, null, maxVal, null, maxLoc, null);
            if (maxVal.get() > bestMaxVal) {
                bestMaxVal = maxVal.get();
                bestMaxLoc = new Point(maxLoc.x(), maxLoc.y());
            }
        }
        return (bestMaxVal > 0.7) ? bestMaxLoc : null;
    }

    // Метод для OCR с твоей предобработкой
    public String recognizeText(Mat crop, int sliceLastDigit) throws Exception {
        Mat processed = new Mat();
        opencv_imgproc.resize(crop, processed, new Size(crop.cols() * 5, crop.rows() * 5));
        Mat hsv = new Mat();
        opencv_imgproc.cvtColor(processed, hsv, opencv_imgproc.COLOR_BGR2HSV);

        Mat mG = new Mat(), mB = new Mat(), res = new Mat();
        opencv_core.inRange(hsv, new Mat(new Scalar(35, 50, 50, 0)), new Mat(new Scalar(90, 255, 255, 0)), mG);
        opencv_core.inRange(hsv, new Mat(new Scalar(90, 50, 50, 0)), new Mat(new Scalar(130, 255, 255, 0)), mB);
        opencv_core.bitwise_or(mG, mB, res);
        opencv_core.bitwise_not(res, res);
        opencv_imgproc.GaussianBlur(res, res, new Size(3, 3), 0);

        File tmp = new File("ocr_tmp.png");
        opencv_imgcodecs.imwrite(tmp.getAbsolutePath(), res);

        // Убираем всё лишнее, кроме цифр
        String result = tesseract.doOCR(tmp).replaceAll("[^0-9]", "");

        if (sliceLastDigit > 0) {
            // Превращаем "448" в "44.8"
            result = result.substring(0, result.length() - sliceLastDigit) + "." + result.substring(result.length() - sliceLastDigit);
        }
        return result;

//        return tesseract.doOCR(tmp).replaceAll("\\s+", "");
    }

    // Метод для сохранения отладочного кадра
    public void saveDebugCrop(Mat crop, String fieldName) {
        // Убираем пробелы из имени для названия файла
        String fileName = "debug_" + fieldName.replaceAll("\\s+", "_") + ".png";
        opencv_imgcodecs.imwrite(fileName, crop);
    }
}