package com.ansub.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

// Small flat weather pictures drawn from shapes (sun, moon, cloud, rain, lightning), so the app needs
// no image files or emoji fonts. Coordinates are on a 100x100 grid.
final class WeatherIcon extends Drawable {
    static final int SUN = 0, PARTLY = 1, CLOUD = 2, FOG = 3, DRIZZLE = 4, RAIN = 5, HEAVY = 6, STORM = 7,
            SNOW = 8, MOON = 9, MOON_PARTLY = 10;

    private static final int YELLOW = 0xFFFFB830, CLOUD_LIGHT = 0xFFDCE4F2, CLOUD_DARK = 0xFF9AA8C4,
            DROP = 0xFF5B9CFF, MOON_COLOR = 0xFFF2E6B8;

    private final int kind;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    WeatherIcon(int kind) {
        this.kind = kind;
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
    }

    // WMO weather code to a picture; night swaps the sun for the moon.
    static int kindFor(int code, boolean night) {
        if (code <= 1) return night ? MOON : SUN;
        if (code == 2) return night ? MOON_PARTLY : PARTLY;
        if (code == 3) return CLOUD;
        if (code == 45 || code == 48) return FOG;
        if (code >= 51 && code <= 57) return DRIZZLE;
        if (code == 65 || code == 67 || code == 82) return HEAVY;
        if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return RAIN;
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return SNOW;
        if (code >= 95) return STORM;
        return CLOUD;
    }

    @Override
    public void draw(Canvas c) {
        float s = Math.min(getBounds().width(), getBounds().height()) / 100f;
        c.save();
        c.translate(getBounds().left, getBounds().top);
        c.scale(s, s);
        line.setStrokeWidth(6);
        switch (kind) {
            case SUN: sun(c, 50, 50, 20, true); break;
            case MOON: moon(c, 50, 50, 26); break;
            case PARTLY: sun(c, 36, 36, 16, true); cloud(c, 58, 64, 0.8f, CLOUD_LIGHT); break;
            case MOON_PARTLY: moon(c, 38, 36, 20); cloud(c, 58, 64, 0.8f, CLOUD_LIGHT); break;
            case CLOUD: cloud(c, 50, 54, 1f, CLOUD_LIGHT); break;
            case FOG:
                line.setColor(CLOUD_DARK);
                for (int y = 34; y <= 74; y += 20) c.drawLine(18, y, 82, y, line);
                break;
            case DRIZZLE: cloud(c, 50, 42, 0.9f, CLOUD_LIGHT); drops(c, 2, 12); break;
            case RAIN: cloud(c, 50, 42, 0.9f, CLOUD_LIGHT); drops(c, 3, 16); break;
            case HEAVY: cloud(c, 50, 42, 0.9f, CLOUD_DARK); drops(c, 4, 20); break;
            case STORM:
                cloud(c, 50, 40, 0.9f, CLOUD_DARK);
                fill.setColor(YELLOW);
                path.reset();
                path.moveTo(54, 56); path.lineTo(38, 78); path.lineTo(50, 78); path.lineTo(44, 96);
                path.lineTo(64, 70); path.lineTo(52, 70); path.lineTo(60, 56); path.close();
                c.drawPath(path, fill);
                break;
            case SNOW:
                cloud(c, 50, 42, 0.9f, CLOUD_LIGHT);
                fill.setColor(0xFFFFFFFF);
                for (int i = 0; i < 3; i++) c.drawCircle(32 + i * 18, 80 + (i % 2) * 8, 5, fill);
                break;
            default: cloud(c, 50, 54, 1f, CLOUD_LIGHT);
        }
        c.restore();
    }

    private void sun(Canvas c, float cx, float cy, float r, boolean rays) {
        fill.setColor(YELLOW);
        c.drawCircle(cx, cy, r, fill);
        if (!rays) return;
        line.setColor(YELLOW);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            float x1 = (float) (cx + Math.cos(a) * (r + 7)), y1 = (float) (cy + Math.sin(a) * (r + 7));
            float x2 = (float) (cx + Math.cos(a) * (r + 15)), y2 = (float) (cy + Math.sin(a) * (r + 15));
            c.drawLine(x1, y1, x2, y2, line);
        }
    }

    // A crescent: a disc with a smaller offset disc cut out of it.
    private void moon(Canvas c, float cx, float cy, float r) {
        Path disc = new Path(), cut = new Path();
        disc.addCircle(cx, cy, r, Path.Direction.CW);
        cut.addCircle(cx + r * 0.55f, cy - r * 0.35f, r * 0.85f, Path.Direction.CW);
        disc.op(cut, Path.Op.DIFFERENCE);
        fill.setColor(MOON_COLOR);
        c.drawPath(disc, fill);
    }

    // A cloud centred at (cx, cy), about 70 wide at size 1.
    private void cloud(Canvas c, float cx, float cy, float k, int color) {
        fill.setColor(color);
        c.drawCircle(cx - 16 * k, cy + 2 * k, 14 * k, fill);
        c.drawCircle(cx + 4 * k, cy - 8 * k, 20 * k, fill);
        c.drawCircle(cx + 22 * k, cy + 4 * k, 12 * k, fill);
        c.drawRoundRect(cx - 32 * k, cy + 2 * k, cx + 34 * k, cy + 16 * k, 7 * k, 7 * k, fill);
    }

    private void drops(Canvas c, int n, float len) {
        line.setColor(DROP);
        float gap = 60f / (n + 1);
        for (int i = 1; i <= n; i++) {
            float x = 20 + gap * i;
            c.drawLine(x + 4, 66, x - 4, 66 + len, line);
        }
    }

    @Override public void setAlpha(int a) { fill.setAlpha(a); line.setAlpha(a); }
    @Override public void setColorFilter(ColorFilter cf) { fill.setColorFilter(cf); line.setColorFilter(cf); }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    @Override public int getIntrinsicWidth() { return 64; }
    @Override public int getIntrinsicHeight() { return 64; }
}
