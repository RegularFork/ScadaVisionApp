package org.example;

import org.bytedeco.opencv.global.opencv_imgcodecs;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Rect;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.ArrayList;

public class ScadaMonitor implements Runnable {
    private final VisionEngine engine = new VisionEngine("tessdata");
    private final List<DataField> fields = new ArrayList<>();

    private static final double BASE_WIDTH = 1143.0;
    private static final double BASE_HEIGHT = 695.0;
    // Соотношение сторон, которое мы будем поддерживать
    private static final double ASPECT_RATIO = BASE_HEIGHT / BASE_WIDTH;

    private boolean running = true; // флаг для завершения цикла в методе создания прицелочного png

    public ScadaMonitor() {
        // Просто добавляем поля в список. Расширять систему теперь можно одной строчкой!
        fields.add(new DataField("TG-8 Load", 670, 564, 90, 33, 1, false));
        fields.add(new DataField("TG-9 Load", 963, 550, 90, 33, 1, false)); // Пример нового поля
//        fields.add(new DataField("Т окр. среды", 900, 50, 50, 30));   // Еще одно
    }

    @Override
    public void run() {
        while (running) {
            try {
                // Вся логика опроса здесь, но она компактная,
                // потому что сложные вещи делает engine
                executeCycle();
                Thread.sleep(5000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        System.out.println("Программа завершена после калибровки.");
        System.exit(0); // Полное завершение приложения
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
        Mat tL = opencv_imgcodecs.imread("anchors/scada_top_left.png");
        Mat bR = opencv_imgcodecs.imread("anchors/scada_top_right.png");

        if (scene.empty() || tL.empty() || bR.empty()) return;

        // 4. Используем "Двигатель" для поиска углов
        org.bytedeco.opencv.opencv_core.Point ptTopLeft = engine.findTemplate(scene, tL);
//        org.bytedeco.opencv.opencv_core.Point ptBottomRight = engine.findTemplate(scene, bR);
        org.bytedeco.opencv.opencv_core.Point ptTopRight = engine.findTemplate(scene, bR);

        if (ptTopLeft != null && ptTopRight != null) {
            double currentWidth = ptTopRight.x() -ptTopLeft.x();

            // Вычисляем коэффициент масштабирования kX
            // ВАЖНО: 1040.0 — это примерное расстояние между TREI и ПТК в пикселях на твоем эталоне.
            // Замерь его точно в Paint на скриншоте 1143х695!
            double scale = currentWidth / 1040.0;

            double kX = scale;
            // Масштаб по Y теперь жестко привязан к X
            double kY = scale;

            for (DataField field : fields) {
                // 1. Получаем масштабированный Rect для конкретного поля
                Rect targetRect = field.getScaledRect(ptTopLeft.x(), ptTopLeft.y(), kX, kY);

                // 2. Проверка границ (чтобы не вылететь за край сцены)
                if (targetRect.x() + targetRect.width() > scene.cols())
                    targetRect.width(scene.cols() - targetRect.x());
                if (targetRect.y() + targetRect.height() > scene.rows())
                    targetRect.height(scene.rows() - targetRect.y());

                // 3. Вырезаем и распознаем
                Mat mwCrop = new Mat(scene, targetRect);
                if (field.savePngBox) {
                    engine.saveDebugCrop(mwCrop, field.getName()); // Добавь геттер getName() в DataField
                    this.running = false;
                }
                String result = engine.recognizeText(mwCrop, field.decimalPlaces);

                // 4. Чистим текст
                String clean = result.replace("mw", "MW").replace("Mw", "MW")
                        .replace("a", "4").replace("I", "1")
                        .replace("s", "5").replace(",", ".");

                // 5. Печатаем результат через метод объекта
                field.printState(clean);
            }
        }else{
            System.out.println("Схема не найдена (не совпали углы).");
        }
        System.out.println("= = = = = = = = = =");
    }
}