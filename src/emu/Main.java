package emu;

import chip.Chip;

import javax.swing.*;
import java.awt.*;
import java.util.Scanner;

public class Main extends Thread{
private static Chip chip8;
private static ChipFrame frame;
private String game;
    public Main(String game) {
        chip8 = new Chip();
        chip8.init();
        chip8.loadProgram("./" +  game);
        frame = new ChipFrame(chip8);
    }

    public void run() {
        // 60 hz, 60 fps
        while (true) {
            chip8.setKeyBuffer(frame.getKeyBuffer());
            chip8.run();
            if(chip8.needsRedraw()) {
                frame.repaint();
                chip8.removeDrawFlag();
            }
            try {
                //sleep for 16 milliseconds, to get around 60 fps
                Thread.sleep(16);
            } catch (InterruptedException e) {}
        }
    }


    public static void main(String[] args) {
        menu menu = new menu();
        menu.setVisible(true);
        while(menu.isVisible()) {
            try {
                Thread.sleep(100); // Pause briefly
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        Main main = new Main(menu.getGame());
        main.start();

    }
}
