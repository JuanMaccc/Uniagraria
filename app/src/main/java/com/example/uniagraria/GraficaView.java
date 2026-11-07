package com.example.uniagraria;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.List;

public class GraficaView extends View {

    private final Paint linea = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint relleno = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint limite = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint punto = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;

    private List<Float> datos;
    private float minLim, maxLim;

    public GraficaView(Context context, AttributeSet attrs) {
        super(context, attrs);
        d = getResources().getDisplayMetrics().density;

        linea.setStyle(Paint.Style.STROKE);
        linea.setStrokeWidth(3 * d);
        linea.setStrokeCap(Paint.Cap.ROUND);
        linea.setStrokeJoin(Paint.Join.ROUND);
        linea.setColor(Color.parseColor("#7CC4E8"));

        relleno.setStyle(Paint.Style.FILL);

        limite.setStyle(Paint.Style.STROKE);
        limite.setStrokeWidth(1.5f * d);
        limite.setColor(Color.parseColor("#D64545"));
        limite.setPathEffect(new DashPathEffect(new float[]{10 * d, 8 * d}, 0));

        punto.setStyle(Paint.Style.FILL);
        punto.setColor(Color.WHITE);
    }

    public void setDatos(List<Float> datos, float minLim, float maxLim) {
        this.datos = datos;
        this.minLim = minLim;
        this.maxLim = maxLim;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (datos == null || datos.size() < 2) return;

        float w = getWidth(), h = getHeight(), pad = 10 * d;
        float lo = minLim, hi = maxLim;
        for (float v : datos) {
            if (v < lo) lo = v;
            if (v > hi) hi = v;
        }
        if (hi - lo < 1) { lo -= 0.5f; hi += 0.5f; }

        // Líneas de umbral
        for (float t : new float[]{minLim, maxLim}) {
            float y = h - pad - (t - lo) / (hi - lo) * (h - 2 * pad);
            canvas.drawLine(0, y, w, y, limite);
        }

        int n = datos.size();
        Path trazo = new Path();
        float x = 0, y = 0, primerX = 0;
        for (int i = 0; i < n; i++) {
            x = pad + i * (w - 2 * pad) / (n - 1);
            y = h - pad - (datos.get(i) - lo) / (hi - lo) * (h - 2 * pad);
            if (i == 0) { trazo.moveTo(x, y); primerX = x; }
            else trazo.lineTo(x, y);
        }

        Path area = new Path(trazo);
        area.lineTo(x, h);
        area.lineTo(primerX, h);
        area.close();
        relleno.setShader(new LinearGradient(0, 0, 0, h,
                Color.parseColor("#667CC4E8"), Color.parseColor("#007CC4E8"),
                Shader.TileMode.CLAMP));

        canvas.drawPath(area, relleno);
        canvas.drawPath(trazo, linea);
        canvas.drawCircle(x, y, 5 * d, punto);
    }
}