package com.example.uniagraria;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.List;
import java.util.Locale;

public class HistorialActivity extends AppCompatActivity {

    private Config config;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private GraficaView graficaTemp, graficaPh, graficaO2;
    private TextView tvTempActual, tvTempStats, tvTempRango;
    private TextView tvPhActual, tvPhStats, tvPhRango;
    private TextView tvO2Actual, tvO2Stats, tvO2Rango;
    private LinearLayout contenedorTablaLecturas;

    private final Runnable refrescar = new Runnable() {
        @Override
        public void run() {
            actualizarDatos();
            handler.postDelayed(this, 2000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(true);
        setContentView(R.layout.activity_historial);

        config = new Config(this);

        View root = findViewById(R.id.rootHistorial);
        View header = findViewById(R.id.headerHistorial);
        final int paddingSuperior = header.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets b = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(b.left, 0, b.right, b.bottom);
            header.setPadding(header.getPaddingLeft(), paddingSuperior + b.top,
                    header.getPaddingRight(), header.getPaddingBottom());
            return insets;
        });

        graficaTemp = findViewById(R.id.graficaTemp);
        graficaPh = findViewById(R.id.graficaPh);
        graficaO2 = findViewById(R.id.graficaO2);

        tvTempActual = findViewById(R.id.tvTempActual);
        tvTempStats = findViewById(R.id.tvTempStats);
        tvTempRango = findViewById(R.id.tvTempRango);

        tvPhActual = findViewById(R.id.tvPhActual);
        tvPhStats = findViewById(R.id.tvPhStats);
        tvPhRango = findViewById(R.id.tvPhRango);

        tvO2Actual = findViewById(R.id.tvO2Actual);
        tvO2Stats = findViewById(R.id.tvO2Stats);
        tvO2Rango = findViewById(R.id.tvO2Rango);

        contenedorTablaLecturas = findViewById(R.id.contenedorTablaLecturas);

        // Configuración de colores de cada gráfica
        graficaTemp.setColores(
                Color.parseColor("#FFA726"),
                Color.parseColor("#66FFA726"),
                Color.parseColor("#00FFA726"));

        graficaPh.setColores(
                Color.parseColor("#26A69A"),
                Color.parseColor("#6626A69A"),
                Color.parseColor("#0026A69A"));

        graficaO2.setColores(
                Color.parseColor("#29B6F6"),
                Color.parseColor("#6629B6F6"),
                Color.parseColor("#0029B6F6"));

        findViewById(R.id.btnVolverHistorial).setOnClickListener(v -> finish());
        findViewById(R.id.btnLimpiarHistorial).setOnClickListener(v -> {
            HistorialManager.limpiar();
            actualizarDatos();
        });

        actualizarDatos();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(refrescar);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(refrescar);
    }

    private void actualizarDatos() {
        float tMin = config.get("t_min");
        float tMax = config.get("t_max");
        float phMin = config.get("ph_min");
        float phMax = config.get("ph_max");
        float o2Min = config.get("o2_min");

        tvTempRango.setText(String.format(Locale.US, "Rango: %.1f - %.1f °C", tMin, tMax));
        tvPhRango.setText(String.format(Locale.US, "Rango: %.2f - %.2f", phMin, phMax));
        tvO2Rango.setText(String.format(Locale.US, "Mínimo: >= %.1f %%", o2Min));

        List<HistorialManager.Lectura> todas = HistorialManager.obtenerTodas();
        if (todas.isEmpty()) {
            tvTempActual.setText("-- °C");
            tvTempStats.setText("Sin datos registrados");
            tvPhActual.setText("--");
            tvPhStats.setText("Sin datos registrados");
            tvO2Actual.setText("-- %");
            tvO2Stats.setText("Sin datos registrados");
            contenedorTablaLecturas.removeAllViews();
            return;
        }

        String horaInicio = HistorialManager.obtenerPrimeraHora();
        String horaFin = HistorialManager.obtenerUltimaHora();

        // 1. TEMPERATURA
        List<Float> temps = HistorialManager.obtenerTemperaturas();
        graficaTemp.setHoras(horaInicio, horaFin);
        graficaTemp.setDatos(temps, tMin, tMax);
        float tUltima = temps.get(temps.size() - 1);
        tvTempActual.setText(String.format(Locale.US, "%.1f °C", tUltima));
        tvTempActual.setTextColor((tUltima < tMin || tUltima > tMax)
                ? Color.parseColor("#FB4903") : Color.parseColor("#000000"));
        tvTempStats.setText(calcularEstadisticas(temps, "°C"));

        // 2. pH
        List<Float> phs = HistorialManager.obtenerPh();
        graficaPh.setHoras(horaInicio, horaFin);
        graficaPh.setDatos(phs, phMin, phMax);
        float phUltimo = phs.get(phs.size() - 1);
        tvPhActual.setText(String.format(Locale.US, "%.2f", phUltimo));
        tvPhActual.setTextColor((phUltimo < phMin || phUltimo > phMax)
                ? Color.parseColor("#FB4903") : Color.parseColor("#000000"));
        tvPhStats.setText(calcularEstadisticas(phs, ""));

        // 3. OXÍGENO
        List<Float> oxs = HistorialManager.obtenerOxigeno();
        graficaO2.setHoras(horaInicio, horaFin);
        graficaO2.setDatos(oxs, o2Min, Float.MAX_VALUE);
        float o2Ultimo = oxs.get(oxs.size() - 1);
        tvO2Actual.setText(String.format(Locale.US, "%.1f %%", o2Ultimo));
        tvO2Actual.setTextColor((o2Ultimo < o2Min)
                ? Color.parseColor("#FB4903") : Color.parseColor("#000000"));
        tvO2Stats.setText(calcularEstadisticas(oxs, "%"));

        // 4. TABLA DE LECTURAS
        actualizarTabla(todas, tMin, tMax, phMin, phMax, o2Min);
    }

    private String calcularEstadisticas(List<Float> valores, String unidad) {
        if (valores.isEmpty()) return "";
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        float suma = 0;
        for (float v : valores) {
            if (v < min) min = v;
            if (v > max) max = v;
            suma += v;
        }
        float prom = suma / valores.size();
        String u = unidad.isEmpty() ? "" : " " + unidad;
        return String.format(Locale.US, "Mín: %.1f%s  |  Máx: %.1f%s  |  Prom: %.1f%s",
                min, u, max, u, prom, u);
    }

    private void actualizarTabla(List<HistorialManager.Lectura> lecturas,
                                 float tMin, float tMax,
                                 float phMin, float phMax,
                                 float o2Min) {
        contenedorTablaLecturas.removeAllViews();

        int total = lecturas.size();
        int maxFilas = Math.min(15, total);

        float dp = getResources().getDisplayMetrics().density;
        int padVertical = (int) (6 * dp);

        // Mostrar de más reciente a más antiguo
        for (int i = 0; i < maxFilas; i++) {
            HistorialManager.Lectura l = lecturas.get(total - 1 - i);

            LinearLayout fila = new LinearLayout(this);
            fila.setOrientation(LinearLayout.HORIZONTAL);
            fila.setPadding(0, padVertical, 0, padVertical);
            fila.setBackgroundColor(i % 2 == 0
                    ? Color.parseColor("#FFFFFF")
                    : Color.parseColor("#F5F8FC"));

            boolean tempAlerta = l.temperatura < tMin || l.temperatura > tMax;
            boolean phAlerta = l.ph < phMin || l.ph > phMax;
            boolean o2Alerta = l.oxigeno < o2Min;

            fila.addView(crearCelda(l.hora, 1.2f, Color.parseColor("#555555")));
            fila.addView(crearCelda(String.format(Locale.US, "%.1f", l.temperatura), 1.0f,
                    tempAlerta ? Color.parseColor("#FB4903") : Color.parseColor("#000000")));
            fila.addView(crearCelda(String.format(Locale.US, "%.2f", l.ph), 1.0f,
                    phAlerta ? Color.parseColor("#FB4903") : Color.parseColor("#000000")));
            fila.addView(crearCelda(String.format(Locale.US, "%.1f", l.oxigeno), 1.0f,
                    o2Alerta ? Color.parseColor("#FB4903") : Color.parseColor("#000000")));

            contenedorTablaLecturas.addView(fila);
        }
    }

    private TextView crearCelda(String texto, float peso, int colorTexto) {
        TextView tv = new TextView(this);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, peso);
        tv.setLayoutParams(params);
        tv.setGravity(Gravity.CENTER);
        tv.setText(texto);
        tv.setTextColor(colorTexto);
        tv.setTextSize(12);
        return tv;
    }
}