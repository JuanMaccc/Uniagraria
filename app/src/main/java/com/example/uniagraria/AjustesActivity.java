package com.example.uniagraria;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AjustesActivity extends AppCompatActivity {

    private Config config;
    private EditText etTMin, etTMax, etPhMin, etPhMax, etO2Min, etTref;
    private TextView tvMsg, tvMsgCal, tvPhV, tvTempRaw, tvCalInfo;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Muestra lecturas crudas en vivo para calibrar
    private final Runnable refrescar = new Runnable() {
        @Override
        public void run() {
            float v = Config.ultimoPhV;
            tvPhV.setText(Float.isNaN(v) ? "-- V" : String.format(Locale.US, "%.3f V", v));

            float raw = Config.ultimoTempRaw;
            tvTempRaw.setText(Float.isNaN(raw) ? "-- °C" : String.format(Locale.US, "%.1f °C", raw));

            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(true);
        setContentView(R.layout.activity_ajustes);

        View root = findViewById(R.id.rootAjustes);
        View header = findViewById(R.id.headerAjustes);
        final int paddingSuperior = header.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets b = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(b.left, 0, b.right, b.bottom);
            header.setPadding(header.getPaddingLeft(), paddingSuperior + b.top,
                    header.getPaddingRight(), header.getPaddingBottom());
            return insets;
        });

        config = new Config(this);

        etTMin = findViewById(R.id.etTMin);
        etTMax = findViewById(R.id.etTMax);
        etPhMin = findViewById(R.id.etPhMin);
        etPhMax = findViewById(R.id.etPhMax);
        etO2Min = findViewById(R.id.etO2Min);
        etTref = findViewById(R.id.etTref);
        tvMsg = findViewById(R.id.tvMsg);
        tvMsgCal = findViewById(R.id.tvMsgCal);
        tvPhV = findViewById(R.id.tvPhV);
        tvTempRaw = findViewById(R.id.tvTempRaw);
        tvCalInfo = findViewById(R.id.tvCalInfo);

        findViewById(R.id.btnVolver).setOnClickListener(x -> finish());
        findViewById(R.id.btnGuardar).setOnClickListener(x -> guardarUmbrales());
        findViewById(R.id.btnPh7).setOnClickListener(x -> calibrarPh("ph_v_neutro"));
        findViewById(R.id.btnPh4).setOnClickListener(x -> calibrarPh("ph_v_acido"));
        findViewById(R.id.btnTemp).setOnClickListener(x -> calibrarTemperatura());
        findViewById(R.id.btnReset).setOnClickListener(x -> restablecerValores());

        llenarUmbrales();
        actualizarInfo();
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

    private void llenarUmbrales() {
        etTMin.setText(String.format(Locale.US, "%.1f", config.get("t_min")));
        etTMax.setText(String.format(Locale.US, "%.1f", config.get("t_max")));
        etPhMin.setText(String.format(Locale.US, "%.2f", config.get("ph_min")));
        etPhMax.setText(String.format(Locale.US, "%.2f", config.get("ph_max")));
        etO2Min.setText(String.format(Locale.US, "%.1f", config.get("o2_min")));
    }

    private void actualizarInfo() {
        tvCalInfo.setText(String.format(Locale.US,
                "Corrección de temperatura: %+.2f °C · V pH 7: %.3f · V pH 4: %.3f",
                config.get("temp_offset"), config.get("ph_v_neutro"), config.get("ph_v_acido")));
    }

    private float num(EditText e) {
        try {
            return Float.parseFloat(e.getText().toString().trim().replace(',', '.'));
        } catch (NumberFormatException ex) {
            return Float.NaN;
        }
    }

    private void aviso(TextView tv, String texto, boolean ok) {
        tv.setText(texto);
        tv.setTextColor(Color.parseColor(ok ? "#2E8B57" : "#FB4903"));
    }

    private void guardarUmbrales() {
        Map<String, Float> cambios = new HashMap<>();
        cambios.put("t_min", num(etTMin));
        cambios.put("t_max", num(etTMax));
        cambios.put("ph_min", num(etPhMin));
        cambios.put("ph_max", num(etPhMax));
        cambios.put("o2_min", num(etO2Min));

        for (float v : cambios.values()) {
            if (Float.isNaN(v)) {
                aviso(tvMsg, "Revisa que todos los campos tengan un número válido", false);
                return;
            }
        }

        String error = config.guardar(cambios);
        if (error != null) {
            aviso(tvMsg, error, false);
        } else {
            llenarUmbrales();
            aviso(tvMsg, "Umbrales guardados ✔", true);
        }
    }

    private void calibrarPh(String clave) {
        float v = Config.ultimoPhV;
        if (Float.isNaN(v)) {
            aviso(tvMsgCal, "El sensor de pH no entrega lectura", false);
            return;
        }

        // Advertencia si intenta fijar pH 4 y el sensor marca exactamente lo mismo que pH 7
        if ("ph_v_acido".equals(clave) && Math.abs(v - config.get("ph_v_neutro")) < 0.05f) {
            aviso(tvMsgCal, "Sumerge la sonda en la solución pH 4 (voltaje casi igual a pH 7)", false);
            return;
        }
        if ("ph_v_neutro".equals(clave) && Math.abs(v - config.get("ph_v_acido")) < 0.05f) {
            aviso(tvMsgCal, "Sumerge la sonda en la solución pH 7 (voltaje casi igual a pH 4)", false);
            return;
        }

        Map<String, Float> cambios = new HashMap<>();
        cambios.put(clave, v);
        String error = config.guardar(cambios);
        if (error != null) {
            aviso(tvMsgCal, error, false);
        } else {
            actualizarInfo();
            aviso(tvMsgCal, "Calibración de pH guardada ✔", true);
        }
    }

    private void calibrarTemperatura() {
        float ref = num(etTref);
        if (Float.isNaN(ref)) {
            aviso(tvMsgCal, "Escribe la temperatura de referencia", false);
            return;
        }
        float raw = Config.ultimoTempRaw;
        if (Float.isNaN(raw)) {
            aviso(tvMsgCal, "El sensor de temperatura no entrega lectura", false);
            return;
        }
        Map<String, Float> cambios = new HashMap<>();
        cambios.put("temp_offset", ref - raw);
        String error = config.guardar(cambios);
        if (error != null) {
            aviso(tvMsgCal, error, false);
        } else {
            actualizarInfo();
            aviso(tvMsgCal, "Calibración de temperatura guardada ✔", true);
        }
    }

    private void restablecerValores() {
        config.restablecerPorDefecto();
        llenarUmbrales();
        actualizarInfo();
        etTref.setText("");
        aviso(tvMsg, "Umbrales restablecidos de fábrica ✔", true);
        aviso(tvMsgCal, "Calibración restablecida de fábrica ✔", true);
    }
}
