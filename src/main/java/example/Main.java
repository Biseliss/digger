package example;

import core.GameWindow;
import ui.Screen;
import ui.widgets.Button;
import ui.widgets.Label;
import ui.widgets.Panel;

import java.awt.Color;

/** Minimal usage example: a panel with a label and a button that counts clicks. */
public class Main {
    public static void main(String[] args) {
        GameWindow window = new GameWindow("UI Demo", 480, 320);
        Screen screen = new Screen(480, 320);

        Panel panel = new Panel(40, 40, 400, 240, new Color(40, 40, 55));
        screen.addChild(panel);

        Label counterLabel = new Label(20, 40, "Clicks: 0");
        panel.addChild(counterLabel);

        int[] clicks = {0};
        Button button = new Button(20, 60, 140, 40, "Click me", () -> {
            clicks[0]++;
            counterLabel.setText("Clicks: " + clicks[0]);
        });
        panel.addChild(button);

        window.setScreen(screen);
        window.start();
    }
}
