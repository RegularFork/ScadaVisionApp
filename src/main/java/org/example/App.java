package org.example;

public class App {

    public static void main(String[] args) {

        new Thread(new ScadaMonitor()).start();
        System.out.println("Система мониторинга запущена в фоновом потоке.");

    }

}
