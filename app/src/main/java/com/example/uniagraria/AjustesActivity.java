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
    private TextView tvMsg, tvMsgCal, tvPhV, tvCalInfo;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Muestra el voltaje de pH en vivo, para poder calibrar
    private final Runnable refrescar = new Runnable() {
        @Override
        public void run() {
            float v = Config.ultimoPhV;
            tvPhV.setText(Float.isNaN(v) ? "-- V" : String.format(Locale.US, "%.3f V", v));
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
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
        tvCalInfo = findViewById(R.id.tvCalInfo);

        findViewById(R.id.btnVolver).setOnClickListener(x -> finish());
        findViewById(R.id.btnGuardar).setOnClickListener(x -> guardarUmbrales());
        findViewById(R.id.btnPh7).setOnClickListener(x -> calibrarPh("ph_v_neutro"));
        findViewById(R.id.btnPh4).setOnClickListener(x -> calibrarPh("ph_v_acido"));
        findViewById(R.id.btnTemp).setOnClickListener(x -> calibrarTemperatura());

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

    private String fmt(float v) {
        return String.format(Locale.US, "%.1f", v);
    }

    private void llenarUmbrales() {
        etTMin.setText(fmt(config.get("t_min")));
        etTMax.setText(fmt(config.get("t_max")));
        etPhMin.setText(fmt(config.get("ph_min")));
        etPhMax.setText(fmt(config.get("ph_max")));
        etO2Min.setText(fmt(config.get("o2_min")));
    }

    private void actualizarInfo() {
        tvCalInfo.setText(String.format(Locale.US,
                "Corrección de temperatura: %.2f °C · V pH 7: %.3f · V pH 4: %.3f",
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
        tv.setTextColor(Color.parseColor(ok ? "#4CC07F" : "#EF6B6B"));
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
                aviso(tvMsg, "Revisa que todos los campos tengan un número", false);
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
        Map<String, Float> cambios = new HashMap<>();
        cambios.put(clave, v);
        String error = config.guardar(cambios);
        if (error != null) {
            aviso(tvMsgCal, error, false);
        } else {
            actualizarInfo();
            aviso(tvMsgCal, "Calibración guardada ✔", true);
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
            aviso(tvMsgCal, "Calibración guardada ✔", true);
        }
    }
}