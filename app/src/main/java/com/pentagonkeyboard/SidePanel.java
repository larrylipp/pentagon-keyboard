package com.pentagonkeyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class SidePanel extends View {

    public interface KeyListener {
        void onKey(String value);
        void onDelete();
        void onSpace();
        void onEnter();
        void onShift();
    }

    private KeyListener listener;
    public void setKeyListener(KeyListener l) { this.listener = l; }

    private int tab = 0; // 0=num, 1=punct, 2=emoji
    private boolean shifted = false;
    public void setShifted(boolean s) { shifted = s; invalidate(); }

    private static final String[] TAB_LABELS = {"123", "&?!", "😊"};

    // Key layouts per tab — each row is an array of key labels
    // null = skip a cell, "SPACE"/"↵"/"⌫" are special
    private static final String[][][] LAYOUTS = {
        // NUM tab - standard layout, no space (space is full-width bar below)
        {
            {"⌫", "⇧", "@"},
            {"7", "8", "9"},
            {"4", "5", "6"},
            {"1", "2", "3"},
            {"↵", "0", "."}
        },
        // PUNCT tab
        {
            {"⌫", "!", "?"},
            {"\"", "'", "("},
            {")", "-", "_"},
            {"@", "#", "$"},
            {"%", "&", "*"},
            {"#", null, "↵"}
        },
        // EMOJI tab
        {
            {"⌫", "😀", "😂"},
            {"😍", "😎", "🤔"},
            {"😢", "😡", "❤️"},
            {"👍", "👎", "🔥"},
            {"✨", "🎉", "💯"},
            {"🙌", null, "😊"}
        }
    };

    private static class Key {
        RectF rect;
        String label;
        String action; // null = regular key, else "del","space","enter","shift","tab0/1/2"
        Key(RectF r, String lbl, String act) { rect = r; label = lbl; action = act; }
    }

    private final List<Key> keys = new ArrayList<>();
    private Key pressedKey = null;

    // Paints
    private Paint pBg, pTabBg, pTabActive, pKeyBg, pKeyDel, pKeySpec;
    private Paint pBorder, pBorderDel;
    private Paint pTextNorm, pTextGold, pTextRed, pTextGreen;
    private float dp;

    public SidePanel(Context context, AttributeSet attrs) { super(context, attrs); init(context); }
    public SidePanel(Context context)                      { super(context);       init(context); }

    private void init(Context ctx) {
        dp = ctx.getResources().getDisplayMetrics().density;
        pBg        = fill(0xFF090D15);
        pTabBg     = fill(0xFF0A0E18);
        pTabActive = fill(0xFF0E1520);
        pKeyBg     = fill(0xFF0E1520);
        pKeyDel    = fill(0xFF1E0E0E);
        pKeySpec   = fill(0xFF161208);
        pBorder    = stroke(0xFF2A3A5A, dp);
        pBorderDel = stroke(0xFF3A1A1A, dp);
        pTextNorm  = text(0xFF8AC0F0, 16f * dp);
        pTextGold  = text(0xFFC9A84C, 13f * dp);
        pTextRed   = text(0xFFA06060, 18f * dp);
        pTextGreen = text(0xFF6ADDB0, 16f * dp);
    }

    private Paint fill(int c)   { Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setStyle(Paint.Style.FILL);   p.setColor(c); return p; }
    private Paint stroke(int c, float w) { Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setStyle(Paint.Style.STROKE); p.setColor(c); p.setStrokeWidth(w); return p; }
    private Paint text(int c, float s)   { Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); p.setColor(c); p.setTextSize(s); p.setTextAlign(Paint.Align.CENTER); return p; }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        buildLayout(w, h);
    }

    private void buildLayout(int W, int H) {
        keys.clear();
        float tabH = H * 0.16f;
        float pad  = 2f * dp;
        float gap  = 2f * dp;

        // Tab bar — 3 equal tabs
        float tabW = W / 3f;
        for (int i = 0; i < 3; i++) {
            RectF r = new RectF(i * tabW, 0, (i + 1) * tabW, tabH);
            keys.add(new Key(r, TAB_LABELS[i], "tab" + i));
        }

        // Key grid
        String[][] grid = LAYOUTS[tab];
        int rows = grid.length;
        float gridH = H - tabH;
        float rowH = (gridH - pad * 2 - gap * (rows - 1)) / rows;
        float y = tabH + pad;

        for (String[] row : grid) {
            // Count non-null cells; SPACE takes 2 columns
            int nonNull = 0;
            boolean hasSpace = false;
            for (String k : row) {
                if (k != null) nonNull++;
                if ("SPACE".equals(k)) hasSpace = true;
            }
            float totalCols = hasSpace ? nonNull - 1 + 2 : nonNull;
            float colW = (W - pad * 2 - gap * (totalCols - 1)) / totalCols;
            float x = pad;

            for (String k : row) {
                if (k == null) { x += colW + gap; continue; }
                float kw = "SPACE".equals(k) ? colW * 2 + gap : colW;
                RectF r = new RectF(x, y, x + kw, y + rowH);
                String act = "SPACE".equals(k) ? "space"
                           : "↵".equals(k)     ? "enter"
                           : "⌫".equals(k)     ? "del"
                           : null;
                keys.add(new Key(r, k, act));
                x += kw + gap;
            }
            y += rowH + gap;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int W = getWidth(), H = getHeight();
        canvas.drawRect(0, 0, W, H, pBg);
        float tabH = H * 0.16f;
        float tabW = W / 3f;

        // Tab dividers
        for (int i = 1; i < 3; i++) canvas.drawLine(i * tabW, 0, i * tabW, tabH, pBorder);
        canvas.drawLine(0, tabH, W, tabH, pBorder);

        float corner = 4f * dp;

        for (Key k : keys) {
            boolean isTab    = k.action != null && k.action.startsWith("tab");
            boolean isDel    = "del".equals(k.action);
            boolean isSpace  = "space".equals(k.action);
            boolean isEnter  = "enter".equals(k.action);
            boolean pressed  = k == pressedKey;

            Paint bg;
            if (isTab) {
                int ti = Integer.parseInt(k.action.substring(3));
                bg = (ti == tab) ? pTabActive : pTabBg;
            } else if (isDel) {
                bg = pKeyDel;
            } else if (isSpace || isEnter) {
                bg = pKeySpec;
            } else {
                bg = pKeyBg;
            }

            RectF rr = new RectF(k.rect.left + 0.5f, k.rect.top + 0.5f,
                                 k.rect.right - 0.5f, k.rect.bottom - 0.5f);

            if (pressed) {
                Paint pr = new Paint(bg);
                pr.setColor(0xFF1A2A3A);
                canvas.drawRoundRect(rr, corner, corner, pr);
            } else {
                canvas.drawRoundRect(rr, corner, corner, bg);
            }
            canvas.drawRoundRect(rr, corner, corner, isDel ? pBorderDel : pBorder);

            // Label
            Paint tp;
            if (isTab) {
                int ti = Integer.parseInt(k.action.substring(3));
                tp = (ti == tab) ? pTextGold : pTextNorm;
            } else if (isDel) {
                tp = pTextRed;
            } else if (isEnter) {
                tp = pTextGreen;
            } else if (isSpace) {
                tp = pTextGold;
            } else {
                tp = pTextNorm;
            }

            float tx = k.rect.centerX();
            float ty = k.rect.centerY() - (tp.descent() + tp.ascent()) / 2f;
            String lbl = isSpace ? "SPACE" : k.label;
            canvas.drawText(lbl, tx, ty, tp);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pressedKey = keyAt(x, y);
                // Fire tab switches immediately on down for instant feedback
                if (pressedKey != null && pressedKey.action != null
                        && pressedKey.action.startsWith("tab")) {
                    fireKey(pressedKey);
                    pressedKey = null;
                }
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                Key k = keyAt(x, y);
                if (k != null && k == pressedKey) fireKey(k);
                pressedKey = null;
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                pressedKey = null;
                invalidate();
                return true;
        }
        return false;
    }

    private Key keyAt(float x, float y) {
        for (Key k : keys) if (k.rect.contains(x, y)) return k;
        return null;
    }

    private void fireKey(Key k) {
        if (listener == null) return;
        if (k.action != null) {
            if (k.action.startsWith("tab")) {
                tab = Integer.parseInt(k.action.substring(3));
                buildLayout(getWidth(), getHeight());
                return;
            }
            switch (k.action) {
                case "del":   listener.onDelete(); return;
                case "space": listener.onSpace();  return;
                case "enter": listener.onEnter();  return;
            }
        }
        // Shift key (⇧ label, no action string)
        if ("⇧".equals(k.label)) { listener.onShift(); return; }
        listener.onKey(k.label);
    }
}
