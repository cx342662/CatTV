// SPDX-License-Identifier: AGPL-3.0-only
package cattv.ratings;

import android.app.Activity;
import android.graphics.Rect;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import java.lang.reflect.Method;

public final class HomePosters {
    private static final int POSTERS = 0x7f0b0371;
    private static final int AREA = 0x7f0b033d;
    private static final int HERO = 0x7f0b01f3;
    private static final int CATEGORIES = 0x7f0b0485;
    private static final int NAV = 0x7f0b02d1;

    private final ViewGroup grid;
    private final View area;
    private final View hero;
    private final View categories;
    private final View nav;
    private boolean expanded;
    private boolean tracking;
    private float startX;
    private float startY;
    private int columns;
    private int areaHeight;
    private int gridHeight;
    private int heroVisibility;
    private int nextUp;
    private int consumedKey = KeyEvent.KEYCODE_UNKNOWN;

    private HomePosters(Activity activity, ViewGroup grid) {
        this.grid = grid;
        area = activity.findViewById(AREA);
        hero = activity.findViewById(HERO);
        categories = activity.findViewById(CATEGORIES);
        nav = activity.findViewById(NAV);
    }

    private static HomePosters find(Activity activity) {
        View view = activity.findViewById(POSTERS);
        if (!(view instanceof ViewGroup)) return null;
        Object existing = view.getTag(AREA);
        if (existing instanceof HomePosters) return (HomePosters) existing;
        HomePosters posters = new HomePosters(activity, (ViewGroup) view);
        if (posters.area == null || posters.hero == null || posters.categories == null
            || posters.nav == null) return null;
        view.setTag(AREA, posters);
        return posters;
    }

    public static boolean onKey(Activity activity, KeyEvent event) {
        int key = event.getKeyCode();
        if (key != KeyEvent.KEYCODE_BACK && key != KeyEvent.KEYCODE_DPAD_DOWN
            && key != KeyEvent.KEYCODE_DPAD_UP) return false;
        HomePosters posters = find(activity);
        if (posters == null) return false;
        if (key == posters.consumedKey) {
            if (event.getAction() == KeyEvent.ACTION_UP) posters.consumedKey = KeyEvent.KEYCODE_UNKNOWN;
            return true;
        }
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() != 0) return false;
        boolean handled = false;
        if (posters.expanded && (key == KeyEvent.KEYCODE_BACK ||
            (key == KeyEvent.KEYCODE_DPAD_UP && posters.grid.hasFocus()
                && posters.selectedPosition() < posters.columns))) {
            handled = posters.collapse();
        } else if (key == KeyEvent.KEYCODE_DPAD_DOWN && posters.grid.hasFocus()) {
            handled = posters.expand(true);
        }
        if (handled) posters.consumedKey = key;
        return handled;
    }

    public static boolean onTouch(Activity activity, MotionEvent event) {
        HomePosters posters = find(activity);
        if (posters == null || posters.expanded) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                Rect bounds = new Rect();
                posters.tracking = posters.grid.getGlobalVisibleRect(bounds)
                    && bounds.contains((int) event.getRawX(), (int) event.getRawY());
                posters.startX = event.getRawX();
                posters.startY = event.getRawY();
                return false;
            case MotionEvent.ACTION_UP:
                if (!posters.tracking) return false;
                posters.tracking = false;
                float upward = posters.startY - event.getRawY();
                float sideways = Math.abs(posters.startX - event.getRawX());
                int threshold = ViewConfiguration.get(activity).getScaledTouchSlop() * 3;
                return upward > threshold && upward > sideways * 1.5f && posters.expand(false);
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_CANCEL:
                posters.tracking = false;
                return false;
            default:
                return false;
        }
    }

    private int selectedPosition() {
        try {
            return Math.max(0, (Integer) grid.getClass().getMethod("getSelectedPosition").invoke(grid));
        } catch (ReflectiveOperationException error) {
            return 0;
        }
    }

    private boolean expand(boolean fromRemote) {
        if (expanded || !grid.isShown() || grid.getChildCount() == 0) return false;
        int itemWidth = grid.getChildAt(0).getWidth();
        if (itemWidth <= 0 || grid.getHeight() == 0) return false;
        int width = grid.getWidth() - grid.getPaddingLeft() - grid.getPaddingRight();
        columns = Math.max(1, width / itemWidth);
        View content = (View) area.getParent().getParent();
        int categorySpace = categories.isShown() ? categories.getHeight() + dp(12) : 0;
        int height = content.getHeight() - nav.getHeight() - categorySpace - dp(12);
        if (height <= grid.getHeight()) return false;

        areaHeight = area.getLayoutParams().height;
        gridHeight = grid.getLayoutParams().height;
        heroVisibility = hero.getVisibility();
        nextUp = grid.getNextFocusUpId();
        int selected = selectedPosition();
        int count = itemCount();
        if (count <= columns) return false;
        if (!setGridDirection(1, columns)) return false;
        expanded = true;
        hero.setVisibility(View.GONE);
        resize(area, height);
        resize(grid, height);
        grid.setNextFocusUpId(View.NO_ID);
        select(fromRemote && selected + columns < count ? selected + columns : selected);
        return true;
    }

    private boolean collapse() {
        int selected = selectedPosition();
        if (!setGridDirection(0, 1)) return false;
        expanded = false;
        hero.setVisibility(heroVisibility);
        resize(area, areaHeight);
        resize(grid, gridHeight);
        grid.setNextFocusUpId(nextUp);
        select(selected);
        return true;
    }

    private int itemCount() {
        try {
            Object adapter = grid.getClass().getMethod("getAdapter").invoke(grid);
            return adapter == null ? 0 : (Integer) adapter.getClass().getMethod("getItemCount").invoke(adapter);
        } catch (ReflectiveOperationException error) {
            return 0;
        }
    }

    private boolean setGridDirection(int direction, int rowCount) {
        try {
            Object manager = grid.getClass().getMethod("getLayoutManager").invoke(grid);
            if (manager == null) return false;
            Method orientation = manager.getClass().getMethod("setOrientation", int.class);
            Method rows = grid.getClass().getMethod("setNumRows", int.class);
            orientation.invoke(manager, direction);
            rows.invoke(grid, rowCount);
            return true;
        } catch (ReflectiveOperationException error) {
            return false;
        }
    }

    private void select(int position) {
        try {
            grid.getClass().getMethod("setSelectedPosition", int.class).invoke(grid, position);
            grid.requestFocus();
        } catch (ReflectiveOperationException ignored) {
            grid.requestFocus();
        }
    }

    private int dp(int value) {
        return Math.round(grid.getResources().getDisplayMetrics().density * value);
    }

    private static void resize(View view, int height) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        params.height = height;
        view.setLayoutParams(params);
    }
}
