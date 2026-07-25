package ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A UIObject that owns children and forwards draw/tick/input to them.
 * Nesting works automatically: each level just adds its own (x, y) offset.
 */
public class Container extends UIObject {
    protected final List<UIObject> children = new ArrayList<>();

    public Container() {
        super();
    }

    public Container(int x, int y, int width, int height) {
        super(x, y, width, height);
    }

    public void addChild(UIObject child) {
        if (child.parent != null) {
            throw new IllegalStateException("UIObject #" + child.getId() + " already has a parent");
        }
        children.add(child);
        child.parent = this;
        if (manager != null) child.attach(manager);
    }

    public void removeChild(UIObject child) {
        if (children.remove(child)) {
            child.detach();
            child.parent = null;
        }
    }

    public List<UIObject> getChildren() {
        return Collections.unmodifiableList(children);
    }

    @Override
    protected void onDraw(DrawCtx ctx) {
        for (UIObject child : children) {
            child.draw(ctx);
        }
    }

    @Override
    protected void onTick(double dt) {
        for (UIObject child : children) {
            child.tick(dt);
        }
    }

    @Override
    void attach(UIManager m) {
        super.attach(m);
        for (UIObject child : children) child.attach(m);
    }

    @Override
    void detach() {
        for (UIObject child : children) child.detach();
        super.detach();
    }

    /**
     * Кто «съел» нажатие: ему же уходят drag и release, даже если курсор
     * успел уехать за границы элемента (мышиный захват).
     */
    private UIObject mouseCapture;

    // Input dispatch: reverse child order, so the element drawn last
    // (topmost, on top of everything else) gets the first chance to consume.
    @Override
    public boolean handleMousePressed(int localX, int localY, int button) {
        for (int i = children.size() - 1; i >= 0; i--) {
            UIObject child = children.get(i);
            if (!child.isVisible()) continue;
            if (contains(child, localX, localY)) {
                if (child.handleMousePressed(localX - child.getX(), localY - child.getY(), button)) {
                    mouseCapture = child;
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean handleMouseDragged(int localX, int localY) {
        if (mouseCapture == null) return false;
        return mouseCapture.handleMouseDragged(
                localX - mouseCapture.getX(), localY - mouseCapture.getY());
    }

    @Override
    public boolean handleMouseReleased(int localX, int localY, int button) {
        // захваченный элемент получает release в любом случае — иначе кнопка
        // осталась бы «вдавленной», если мышь отпустили мимо неё
        if (mouseCapture != null) {
            UIObject target = mouseCapture;
            mouseCapture = null;
            return target.handleMouseReleased(
                    localX - target.getX(), localY - target.getY(), button);
        }

        for (int i = children.size() - 1; i >= 0; i--) {
            UIObject child = children.get(i);
            if (!child.isVisible()) continue;
            if (contains(child, localX, localY)) {
                if (child.handleMouseReleased(localX - child.getX(), localY - child.getY(), button)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean contains(UIObject o, int px, int py) {
        return px >= o.getX() && px < o.getX() + o.getWidth()
                && py >= o.getY() && py < o.getY() + o.getHeight();
    }
}
