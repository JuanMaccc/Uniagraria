package com.example.uniagraria;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class AnilloView extends View {

    private final Paint fondo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arco = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bordeBorde = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint texto = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private int enRango = 0, total = 3;

    public AnilloView(Context context, AttributeSet attrs) {
        super(context, attrs);
        float d = getResources().getDisplayMetrics().density;

        fondo.setStyle(Paint.Style.STROKE);
        fondo.setColor(Color.parseColor("#E9E9E9")); // Soft mist

        bordeBorde.setStyle(Paint.Style.STROKE);
        bordeBorde.setColor(Color.parseColor("#000000")); // Carbon

        arco.setStyle(Paint.Style.STROKE);
        arco.setStrokeCap(Paint.Cap.ROUND);

        texto.setColor(Color.parseColor("#000000")); // Carbon
        texto.setTextAlign(Paint.Align.CENTER);
        texto.setTextSize(22 * d);
        texto.setFakeBoldText(true);
    }

    public void setProgreso(int enRango, int total) {
        this.enRango = enRango;
        this.total = total;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        float s = Math.min(w, h);
        float grosor = s * 0.14f;
        fondo.setStrokeWidth(grosor);
        arco.setStrokeWidth(grosor);

        // Slush sticker palette: Mint Pop (#55DB9C) si todo OK, Ember (#FB4903) si hay alerta
        arco.setColor(Color.parseColor(enRango == total ? "#55DB9C" : "#FB4903"));

        float izq = (w - s) / 2 + grosor / 2, arr = (h - s) / 2 + grosor / 2;
        rect.set(izq, arr, izq + s - grosor, arr + s - grosor);

        canvas.drawArc(rect, 0, 360, false, fondo);
        if (enRango > 0) {
            canvas.drawArc(rect, -90, 360f * enRango / total, false, arco);
        }
        float y = h / 2 - (texto.ascent() + texto.descent()) / 2;
        canvas.drawText(enRango + "/" + total, w / 2, y, texto);
    }
}
