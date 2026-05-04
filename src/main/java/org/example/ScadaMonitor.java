package org.example;

import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class ScadaMonitor implements Runnable {
    private final VisionEngine engine = new VisionEngine("tessdata");

    @Override
    public void run() {
        while (true) {
            try {
                // Вся логика опроса здесь, но она компактная,
                // потому что сложные вещи делает engine
                executeCycle();
                Thread.sleep(5000);
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    private void executeCycle() throws Exception {
        // 1. Получаем область второго монитора
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] screens = ge.getScreenDevices();
        int screenIndex = (screens.length > 1) ? 1 : 0;
        Rectangle screenRect = screens[screenIndex].getDefaultConfiguration().getBounds();

        // 2. Делаем скриншот через Robot
        Robot robot = new Robot();
        BufferedImage capture = robot.createScreenCapture(screenRect);
        File screenFile = new File("current_screen.png");
        javax.imageio.ImageIO.write(capture, "png", screenFile);

        // 3. Загружаем скриншот и шаблоны углов для OpenCV
        Mat scene = opencv_imgcodecs.imread(screenFile.getAbsolutePath());
        Mat tL = opencv_imgcodecs.imread("scada_top_left.png");
        Mat bR = opencv_imgcodecs.imread("scada_bottom_right.png");

        if (scene.empty() || tL.empty() || bR.empty()) return;

        // 4. Используем "Двигатель" для поиска углов
        org.bytedeco.opencv.opencv_core.Point ptTopLeft = engine.findTemplate(scene, tL);
        org.bytedeco.opencv.opencv_core.Point ptBottomRight = engine.findTemplate(scene, bR);

        if (ptTopLeft != null && ptBottomRight != null) {
            // Считаем актуальный масштаб (базовые размеры те же: 1071x625)
            double kX = (ptBottomRight.x() - ptTopLeft.x()) / 1071.0;
            double kY = (ptBottomRight.y() - ptTopLeft.y()) / 625.0;

            // 5. Вырезаем зону ТГ-8 (базовые координаты: 670, 540)
            Rect tg8Rect = new Rect(
                    ptTopLeft.x() + (int) (670 * kX),
                    ptTopLeft.y() + (int) (540 * kY),
                    (int) (60 * kX),
                    (int) (35 * kY)
            );

            // Проверка, чтобы не выйти за границы кадра
            if (tg8Rect.x() + tg8Rect.width() > scene.cols()) tg8Rect.width(scene.cols() - tg8Rect.x());

            Mat mwCrop = new Mat(scene, tg8Rect);

            // 6. Отдаем вырезанный кусок "Двигателю" на чистку и распознавание
            String result = engine.recognizeText(mwCrop);

            // Финальная чистка символов (MW, запятые и т.д.)
            String clean = result.replace("mw", "MW").replace("Mw", "MW")
                    .replace("a", "4").replace("I", "1")
                    .replace("s", "5").replace("‘", "")
                    .replace("]", "").replace(")", "").replace(",", ".");

            System.out.println("[" + java.time.LocalTime.now().withNano(0) + "] ТГ-8 Нагрузка, МВт: " + clean);
        } else {
            System.out.println("Схема не найдена (не совпали углы).");
        }
    }
}