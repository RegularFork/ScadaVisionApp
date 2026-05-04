package org.example;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.bytedeco.javacpp.DoublePointer;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.global.opencv_imgproc;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_core.Point;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import static org.bytedeco.opencv.global.opencv_core.minMaxLoc;

public class Main {
    // Твои эталонные замеры (на которых "прицел" был идеальным)
    private static final double BASE_WIDTH = 1071.0;
    private static final double BASE_HEIGHT = 625.0;

    // Координаты ТГ-8 относительно ВЕРХНЕГО ЛЕВОГО угла СХЕМЫ
    // Если будет косить при смене размера — подправь эти цифры
    private static final int BASE_TG8_X = 670;
    private static final int BASE_TG8_Y = 540;

    public static void main(String[] args) throws AWTException, IOException, TesseractException {
        // 1. Делаем скриншот второго монитора
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] screens = ge.getScreenDevices();
        int screenIndex = (screens.length > 1) ? 1 : 0;
        Rectangle screenRect = screens[screenIndex].getDefaultConfiguration().getBounds();

        Robot robot = new Robot();
        BufferedImage screenCapture = robot.createScreenCapture(screenRect);
        File screenFile = new File("current_screen.png");
        ImageIO.write(screenCapture, "png", screenFile);

        // 2. Загружаем сцену и шаблоны углов
        Mat scene = opencv_imgcodecs.imread("current_screen.png");
        Mat topLeftAnchor = opencv_imgcodecs.imread("scada_top_left.png");
        Mat bottomRightAnchor = opencv_imgcodecs.imread("scada_bottom_right.png");

        if (scene.empty() || topLeftAnchor.empty() || bottomRightAnchor.empty()) {
            System.err.println("Ошибка: Проверь, лежат ли scada_top_left.png и scada_bottom_right.png в корне проекта!");
            return;
        }

        // 3. Ищем углы схемы
        Point ptTopLeft = findLocation(scene, topLeftAnchor, 0.7);
        Point ptBottomRight = findLocation(scene, bottomRightAnchor, 0.7);

        if (ptTopLeft != null && ptBottomRight != null) {
            int currentWidth = ptBottomRight.x() - ptTopLeft.x();
            int currentHeight = ptBottomRight.y() - ptTopLeft.y();
            System.out.println("Схема найдена! Размер: " + currentWidth + "x" + currentHeight);

            double kX = currentWidth / BASE_WIDTH;
            double kY = currentHeight / BASE_HEIGHT;

            // 4. Расчет зоны ТГ-8
            int tg8X = ptTopLeft.x() + (int) (BASE_TG8_X * kX);
            int tg8Y = ptTopLeft.y() + (int) (BASE_TG8_Y * kY);
            int tg8W = (int) (60 * kX);
            int tg8H = (int) (35 * kY);

            Rect tg8Rect = new Rect(tg8X, tg8Y, tg8W, tg8H);
            if (tg8X + tg8W > scene.cols()) tg8Rect.width(scene.cols() - tg8X);
            if (tg8Y + tg8H > scene.rows()) tg8Rect.height(scene.rows() - tg8Y);

            Mat mwCrop = new Mat(scene, tg8Rect);

            // 5. Предобработка (Зеленый фильтр)
            // 1. Увеличение
            Mat processed = new Mat();
            opencv_imgproc.resize(mwCrop, processed, new Size(mwCrop.cols() * 5, mwCrop.rows() * 5));

            // 2. Переход в HSV
            Mat hsv = new Mat();
            opencv_imgproc.cvtColor(processed, hsv, opencv_imgproc.COLOR_BGR2HSV);

            // --- СОЗДАЕМ ЦВЕТОВЫЕ МАСКИ ---
            Mat maskGreen = new Mat();
            Mat maskBlue = new Mat();
            Mat finalMask = new Mat();

            // Зеленый диапазон (твой рабочий)
            opencv_core.inRange(hsv, new Mat(new Scalar(35, 50, 50, 0)), new Mat(new Scalar(90, 255, 255, 0)), maskGreen);

            // Голубой диапазон (для тех самых "других" чисел)
            opencv_core.inRange(hsv, new Mat(new Scalar(90, 50, 50, 0)), new Mat(new Scalar(130, 255, 255, 0)), maskBlue);

            // Складываем маски (Логическое ИЛИ)
            // Теперь finalMask видит И зеленый, И голубой цвет одновременно
            opencv_core.bitwise_or(maskGreen, maskBlue, finalMask);

            // 3. Инверсия для Тессеракта (черный текст на белом)
            opencv_core.bitwise_not(finalMask, finalMask);


            // 4. Небольшое размытие, чтобы убрать "лесенку" на буквах
            opencv_imgproc.GaussianBlur(finalMask, finalMask, new Size(3, 3), 0);

            opencv_imgcodecs.imwrite("tg8_processed.png", finalMask);

            // 6. Tesseract OCR
            String cleanText = getString();

            System.out.println("\n--- РЕЗУЛЬТАТ СИСТЕМЫ ---");
            System.out.println("Параметр: ТГ-8 Нагрузка");
            System.out.println("Значение: " + cleanText);
            System.out.println("------------------------\n");

        } else {
            System.out.println("Не вижу схему. Проверь, не закрыто ли окно Telegram другими окнами.");
        }
    }

    private static String getString() throws TesseractException {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(new File("tessdata").getAbsolutePath());
        tesseract.setLanguage("eng");
        tesseract.setPageSegMode(7);
        tesseract.setTessVariable("tessedit_char_whitelist", "0123456789,.MWmw");

        String rawText = tesseract.doOCR(new File("tg8_processed.png"));

        // 7. Чистка текста
        String cleanText = rawText.replaceAll("\\s+", "").replace("mw", "MW").replace("Mw", "MW").replace("a", "4").replace("I", "1").replace("s", "5").replace("‘", "").replace("]", "").replace(")", "");
        return cleanText;
    }

    private static Point findLocation(Mat scene, Mat template, double threshold) {
        // Список масштабов: от 60% до 140% от оригинального размера шаблона
        double[] scales = {0.6, 0.7, 0.8, 0.9, 1.0, 1.1, 1.2, 1.3, 1.4};

        double bestMaxVal = -1;
        Point bestMaxLoc = null;

        for (double scale : scales) {
            Mat resizedTemplate = new Mat();
            int width = (int) (template.cols() * scale);
            int height = (int) (template.rows() * scale);

            // Пропускаем слишком мелкие или огромные варианты
            if (width < 5 || height < 5 || width > scene.cols() || height > scene.rows()) continue;

            opencv_imgproc.resize(template, resizedTemplate, new Size(width, height));

            Mat result = new Mat();
            opencv_imgproc.matchTemplate(scene, resizedTemplate, result, opencv_imgproc.TM_CCOEFF_NORMED);

            DoublePointer maxVal = new DoublePointer(1);
            Point maxLoc = new Point();
            minMaxLoc(result, null, maxVal, null, maxLoc, null);

            if (maxVal.get() > bestMaxVal) {
                bestMaxVal = maxVal.get();
                bestMaxLoc = new Point(maxLoc.x(), maxLoc.y());
            }
        }

        if (bestMaxVal > threshold) {
            System.out.printf("Шаблон найден! Качество: %.2f%%\n", bestMaxVal * 100);
            return bestMaxLoc;
        }
        return null;
    }
}