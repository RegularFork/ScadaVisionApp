package org.example;

import org.bytedeco.opencv.opencv_core.Rect;

public class DataField {
    private final String name;    // Название (например, "ТГ-8 Нагрузка")
    private final int baseX, baseY, baseW, baseH; // Базовые координаты (из Paint)
    public int decimalPlaces;
    public boolean savePngBox;

    public DataField(String name, int x, int y, int w, int h, int decimalPlaces, boolean savePngBox) {
        this.name = name;
        this.baseX = x;
        this.baseY = y;
        this.baseW = w;
        this.baseH = h;
        this.decimalPlaces = decimalPlaces;
        this.savePngBox = savePngBox;
    }

    // Метод для расчета актуального Rect с учетом масштаба (kX, kY) и смещения (topLeft)
    public Rect getScaledRect(int offsetX, int offsetY, double kX, double kY) {
        return new Rect(
                offsetX + (int) (baseX * kX),
                offsetY + (int) (baseY * kY),
                (int) (baseW * kX),
                (int) (baseH * kY)
        );
    }

    public void printState(String value) {
        System.out.printf("[%s]: %s%n", name, value);
    }

    public String getName() {
    return this.name;
    }
}
