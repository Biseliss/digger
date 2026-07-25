package ui;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Base class for every UI element.
 *
 * draw()/tick() are final and handle bookkeeping (offset, visibility);
 * subclasses override onDraw()/onTick() instead. A child never calls
 * its parent's draw() - the parent (Container) calls draw() on its children.
 */
public abstract class UIObject {
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);

    private final int id = NEXT_ID.getAndIncrement();
    private final Set<String> tags = new HashSet<>();

    protected int x, y, width, height;
    protected boolean visible = true;

    Container parent;
    UIManager manager;

    protected UIObject() {
        this(0, 0, 0, 0);
    }

    protected UIObject(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public final void draw(DrawCtx ctx) {
        if (!visible) return;
        onDraw(ctx.translated(x, y));
    }

    protected void onDraw(DrawCtx ctx) {
        // no-op by default
    }

    public final void tick(double dt) {
        onTick(dt);
    }

    protected void onTick(double dt) {
        // no-op by default
    }

    /**
     * Mouse hit-testing/dispatch. localX/localY are already relative to this
     * object's own top-left corner. Return true to consume the event (stops
     * propagation to siblings below).
     */
    public boolean handleMousePressed(int localX, int localY, int button) {
        return false;
    }

    public boolean handleMouseReleased(int localX, int localY, int button) {
        return false;
    }

    public int getId() {
        return id;
    }

    public int getX() { return x; }
    public int getY() { return y; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }

    public void setPosition(int x, int y) { this.x = x; this.y = y; }
    public void setSize(int width, int height) { this.width = width; this.height = height; }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }

    public Container getParent() { return parent; }

    public void addTag(String tag) {
        tags.add(tag);
        if (manager != null) manager.registerTag(tag, this);
    }

    public void removeTag(String tag) {
        tags.remove(tag);
        if (manager != null) manager.unregisterTag(tag, this);
    }

    public Set<String> getTags() {
        return Collections.unmodifiableSet(tags);
    }

    void attach(UIManager m) {
        manager = m;
        m.register(this);
        for (String tag : tags) m.registerTag(tag, this);
    }

    void detach() {
        if (manager == null) return;
        for (String tag : tags) manager.unregisterTag(tag, this);
        manager.unregister(this);
        manager = null;
    }
}
