package emu;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

public class menu extends JFrame {
    private JComboBox gamesList;
    private JPanel panel1;
    private JButton loadButton;
    private String game;

    public menu() {

        setTitle("Chip 8 Emulator");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setContentPane(panel1);
        pack();

        File root = new File("./");
        File[] files = root.listFiles();
        for (File file : files) {
            if (file.getName().endsWith(".c8")) {
                gamesList.addItem(file.getName());
            }
        }

        loadButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                game = (String) gamesList.getSelectedItem();
                dispose(); // Close the menu window
            }
        });
    }

    public String getGame() {
        return game;
    }
}
