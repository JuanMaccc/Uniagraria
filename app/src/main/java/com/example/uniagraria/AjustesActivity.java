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
    private NubeMqtt nube;
    private EditText etTMin, etTMax, etPhMin, etPhMax, etO2Min, etTref;
    private TextView tvMsg, tvMsgCal, tvPhV, tvTempRaw, tvCalInfo;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Dónde mostrar la respuesta del ESP32 a la última orden enviada
    private TextView mensajePendiente = null;

    private final Runnable sinRespuesta = () -> {
        if (mensajePendiente != null) {
            aviso(mensajePendiente,
                    "El ESP32 no respondió. Revisa que esté encendido y con internet.", false);
            mensajePendiente = null;
        }
    };

    private final NubeMqtt.Escucha escucha = new NubeMqtt.Escucha() {
        @Override
        public void onDatos(NubeMqtt.Datos d) {
            // Lecturas crudas en vivo, para poder calibrar
            tvPhV.setText(Double.isNaN(d.phV) ? "-- V" : String.format(Locale.US, "%.3f V", d.phV));
            tvTempRaw.setText(Double.isNaN(d.tempRaw) ? "-- °C" : String.format(Locale.US, "%.1f °C", d.tempRaw));
        }

        @Override
        public void onConfig() {
            llenarUmbrales();
            actualizarInfo();
        }

        @Override
        public void onRespuesta(boolean ok, String mensaje) {
            handler.removeCallbacks(sinRespuesta);
            if (mensajePendiente != null) {
                aviso(mensajePendiente, ok ? mensaje + " ✔" : mensaje, ok);
                mensajePendiente = null;
            }
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
        nube = NubeMqtt.get(this);

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
        findViewById(R.id.btnPh7).setOnClickListener(x -> calibrarPh("ph7"));
        findViewById(R.id.btnPh4).setOnClickListener(x -> calibrarPh("ph4"));
        findViewById(R.id.btnTemp).setOnClickListener(x -> calibrarTemperatura());
        findViewById(R.id.btnReset).setOnClickListener(x -> restablecerValores());

        llenarUmbrales();
        actualizarInfo();
    }

    @Override
    protected void onResume() {
        super.onResume();
        nube.agregarEscucha(escucha);
    }

    @Override
    protected void onPause() {
        super.onPause();
        nube.quitarEscucha(escucha);
        handler.removeCallbacks(sinRespuesta);
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

    /** Comprueba que se pueda hablar con el ESP32 antes de enviar una orden. */
    private boolean enLinea(TextView destino) {
        if (!nube.configurada()) {
            aviso(destino, "Falta escribir los datos del broker en NubeMqtt.java", false);
            return false;
        }
        if (!nube.listoParaEnviar()) {
            aviso(destino, "ESP32 sin conexión: no se pueden enviar cambios ahora", false);
            return false;
        }
        return true;
    }

    private void esperarRespuesta(TextView destino) {
        mensajePendiente = destino;
        aviso(destino, "Enviando al ESP32…", true);
        handler.removeCallbacks(sinRespuesta);
        handler.postDelayed(sinRespuesta, 6000);
    }

    private void enviarCambios(TextView destino, Map<String, Float> cambios) {
        if (nube.enviarConfig(cambios)) esperarRespuesta(destino);
        else aviso(destino, "No se pudo enviar la orden", false);
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

        String error = config.validar(cambios);
        if (error != null) {
            aviso(tvMsg, error, false);
            return;
        }
        if (!enLinea(tvMsg)) return;
        enviarCambios(tvMsg, cambios);
    }

    private void calibrarPh(String punto) {
        NubeMqtt.Datos d = nube.ultimosDatos();
        if (d == null || Double.isNaN(d.phV)) {
            aviso(tvMsgCal, "El sensor de pH no entrega lectura", false);
            return;
        }
        float v = (float) d.phV;

        // Evita fijar el mismo buffer en los dos puntos
        if ("ph4".equals(punto) && Math.abs(v - config.get("ph_v_neutro")) < 0.05f) {
            aviso(tvMsgCal, "Sumerge la sonda en la solución pH 4 (voltaje casi igual a pH 7)", false);
            return;
        }
        if ("ph7".equals(punto) && Math.abs(v - config.get("ph_v_acido")) < 0.05f) {
            aviso(tvMsgCal, "Sumerge la sonda en la solución pH 7 (voltaje casi igual a pH 4)", false);
            return;
        }

        if (!enLinea(tvMsgCal)) return;
        // El ESP32 usa su propia lectura en el momento de recibir la orden
        if (nube.calibrar(punto, null)) esperarRespuesta(tvMsgCal);
        else aviso(tvMsgCal, "No se pudo enviar la orden", false);
    }

    private void calibrarTemperatura() {
        float ref = num(etTref);
        if (Float.isNaN(ref)) {
            aviso(tvMsgCal, "Escribe la temperatura de referencia", false);
            return;
        }
        NubeMqtt.Datos d = nube.ultimosDatos();
        if (d == null || Double.isNaN(d.tempRaw)) {
            aviso(tvMsgCal, "El sensor de temperatura no entrega lectura", false);
            return;
        }
        if (!enLinea(tvMsgCal)) return;
        if (nube.calibrar("temp", ref)) esperarRespuesta(tvMsgCal);
        else aviso(tvMsgCal, "No se pudo enviar la orden", false);
    }

    private void restablecerValores() {
        if (!enLinea(tvMsg)) return;
        Map<String, Float> fabrica = new HashMap<>();
        for (String k : Config.CLAVES) fabrica.put(k, Config.defecto(k));
        etTref.setText("");
        enviarCambios(tvMsg, fabrica);
    }
}