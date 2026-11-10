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
    private final Paint puntoBorde = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rejilla = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textoTiempo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;

    // Slush tokens: Electric Blue por defecto
    private int colorLinea = Color.parseColor("#4DA2FF");
    private int colorRellenoInicio = Color.parseColor("#404DA2FF");
    private int colorRellenoFin = Color.parseColor("#084DA2FF");

    private List<Float> datos;
    private float minLim = Float.NaN, maxLim = Float.NaN;
    private String horaInicio = null, horaFin = null;

    public GraficaView(Context context, AttributeSet attrs) {
        super(context, attrs);
        d = getResources().getDisplayMetrics().density;

        linea.setStyle(Paint.Style.STROKE);
        linea.setStrokeWidth(3.5f * d);
        linea.setStrokeCap(Paint.Cap.ROUND);
        linea.setStrokeJoin(Paint.Join.ROUND);
        linea.setColor(colorLinea);

        relleno.setStyle(Paint.Style.FILL);

        limite.setStyle(Paint.Style.STROKE);
        limite.setStrokeWidth(1.5f * d);
        limite.setColor(Color.parseColor("#FB4903")); // Ember Slush
        limite.setPathEffect(new DashPathEffect(new float[]{8 * d, 6 * d}, 0));

        rejilla.setStyle(Paint.Style.STROKE);
        rejilla.setStrokeWidth(1 * d);
        rejilla.setColor(Color.parseColor("#DCEEFF")); // Sky wash rejilla
        rejilla.setPathEffect(new DashPathEffect(new float[]{4 * d, 4 * d}, 0));

        punto.setStyle(Paint.Style.FILL);
        punto.setColor(Color.WHITE);

        puntoBorde.setStyle(Paint.Style.STROKE);
        puntoBorde.setStrokeWidth(1.5f * d);
        puntoBorde.setColor(Color.parseColor("#000000")); // Carbon

        textoTiempo.setColor(Color.parseColor("#000000")); // Carbon
        textoTiempo.setTextSize(11 * d);
        textoTiempo.setFakeBoldText(true);
    }

    public void setColores(int colorLinea, int colorRellenoInicio, int colorRellenoFin) {
        this.colorLinea = colorLinea;
        this.colorRellenoInicio = colorRellenoInicio;
        this.colorRellenoFin = colorRellenoFin;
        linea.setColor(colorLinea);
        invalidate();
    }

    public void setHoras(String horaInicio, String horaFin) {
        this.horaInicio = horaInicio;
        this.horaFin = horaFin;
        invalidate();
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

        float w = getWidth(), h = getHeight();
        boolean tieneHoras = (horaInicio != null && horaFin != null);
        float padBottom = tieneHoras ? (20 * d) : (10 * d);
        float padTop = 10 * d;
        float padX = 12 * d;
        float altoUtil = h - padTop - padBottom;

        float lo = Float.MAX_VALUE;
        float hi = -Float.MAX_VALUE;
        for (float v : datos) {
            if (v < lo) lo = v;
            if (v > hi) hi = v;
        }

        boolean tieneMinValido = !Float.isNaN(minLim) && minLim < 1000f;
        boolean tieneMaxValido = !Float.isNaN(maxLim) && maxLim < 1000f && maxLim > minLim;

        if (tieneMinValido) lo = Math.min(lo, minLim);
        if (tieneMaxValido) hi = Math.max(hi, maxLim);

        if (hi <= lo || (hi - lo < 0.5f)) {
            lo -= 0.5f;
            hi += 0.5f;
        }

        // Líneas de rejilla guía de fondo
        for (int step = 1; step <= 3; step++) {
            float yGrid = padTop + step * (altoUtil / 4f);
            canvas.drawLine(0, yGrid, w, yGrid, rejilla);
        }

        // Líneas de umbral (Ember #FB4903 punteadas)
        if (tieneMinValido) {
            float yMin = padTop + altoUtil - (minLim - lo) / (hi - lo) * altoUtil;
            canvas.drawLine(0, yMin, w, yMin, limite);
        }
        if (tieneMaxValido) {
            float yMax = padTop + altoUtil - (maxLim - lo) / (hi - lo) * altoUtil;
            canvas.drawLine(0, yMax, w, yMax, limite);
        }

        // Trazo de la gráfica
        int n = datos.size();
        Path trazo = new Path();
        float x = 0, y = 0, primerX = 0;
        for (int i = 0; i < n; i++) {
            x = padX + i * (w - 2 * padX) / (n - 1);
            y = padTop + altoUtil - (datos.get(i) - lo) / (hi - lo) * altoUtil;
            if (i == 0) {
                trazo.moveTo(x, y);
                primerX = x;
            } else {
                trazo.lineTo(x, y);
            }
        }

        // Área bajo la curva con lavado pastel
        Path area = new Path(trazo);
        area.lineTo(x, padTop + altoUtil);
        area.lineTo(primerX, padTop + altoUtil);
        area.close();

        relleno.setShader(new LinearGradient(0, padTop, 0, padTop + altoUtil,
                colorRellenoInicio, colorRellenoFin, Shader.TileMode.CLAMP));

        canvas.drawPath(area, relleno);
        canvas.drawPath(trazo, linea);

        // Punto final estilo sticker con borde negro
        canvas.drawCircle(x, y, 5.5f * d, punto);
        canvas.drawCircle(x, y, 5.5f * d, puntoBorde);

        // Etiquetas de tiempo en los extremos
        if (tieneHoras) {
            textoTiempo.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("⏱ " + horaInicio, padX, h - 4 * d, textoTiempo);

            textoTiempo.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText(horaFin + " (ahora)", w - padX, h - 4 * d, textoTiempo);
        }
    }
}
