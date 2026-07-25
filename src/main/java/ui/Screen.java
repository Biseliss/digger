package ui;

/**
 * Root of a UI tree. Owns the UIManager (id/tag registry) and behaves
 * like a plain Container positioned at (0, 0).
 */
public class Screen extends Container {
    private final UIManager manager = new UIManager();

    public Screen(int width, int height) {
        super(0, 0, width, height);
        attach(manager);
    }

    public UIManager getManager() {
        return manager;
    }
}
