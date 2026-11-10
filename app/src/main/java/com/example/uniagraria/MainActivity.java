package com.example.uniagraria;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private Config config;

    // Datos simulados (luego vendrán del maestro)
    private double tempRaw = 28, phV = 1.278, o2 = 20.5;
    private double temp = 28, ph = 7.0;
    private final List<Float> historial = new ArrayList<>();
    private final List<String> registro = new ArrayList<>();
    private final int[] estadoPrevio = {-1, -1, -1};

    private TextView tvAlerta, tvTemp, tvTempEstado, tvPh, tvPhEstado, tvO2, tvO2Estado;
    private TextView tvResumen, tvAlertas;
    private AnilloView anillo;
    private GraficaView grafica;

    private final Runnable tarea = new Runnable() {
        @Override
        public void run() {
            simular();
            mostrar();
            handler.postDelayed(this, 2000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        super.onCreate(savedInstanceState);
        WindowCompat.enableEdgeToEdge(getWindow());
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(true);
        setContentView(R.layout.activity_main);

        config = new Config(this);

        View root = findViewById(R.id.main);
        View header = findViewById(R.id.header);
        final int paddingSuperior = header.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets b = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(b.left, 0, b.right, b.bottom);
            header.setPadding(header.getPaddingLeft(), paddingSuperior + b.top,
                    header.getPaddingRight(), header.getPaddingBottom());
            return insets;
        });

        tvAlerta = findViewById(R.id.tvAlerta);
        tvTemp = findViewById(R.id.tvTemp);
        tvTempEstado = findViewById(R.id.tvTempEstado);
        tvPh = findViewById(R.id.tvPh);
        tvPhEstado = findViewById(R.id.tvPhEstado);
        tvO2 = findViewById(R.id.tvO2);
        tvO2Estado = findViewById(R.id.tvO2Estado);
        tvResumen = findViewById(R.id.tvResumen);
        tvAlertas = findViewById(R.id.tvAlertas);
        anillo = findViewById(R.id.anillo);
        grafica = findViewById(R.id.grafica);

        HistorialManager.inicializarSiVacio(config);

        View.OnClickListener irAjustes = x -> startActivity(new Intent(this, AjustesActivity.class));
        findViewById(R.id.btnAjustes).setOnClickListener(irAjustes);

        View.OnClickListener irHistorial = x -> startActivity(new Intent(this, HistorialActivity.class));
        View btnHistorial = findViewById(R.id.btnHistorial);
        if (btnHistorial != null) {
            btnHistorial.setOnClickListener(irHistorial);
        }

        // Inicializar notificaciones del sistema
        Notificador.inicializarCanal(this);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, 100);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.post(tarea);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tarea);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private double limitar(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private void simular() {
        // Lecturas "crudas" del sensor
        tempRaw = limitar(tempRaw + (random.nextDouble() - 0.5) * 1.2, 22, 35);
        phV = limitar(phV + (random.nextDouble() - 0.5) * 0.03, 1.0, 1.6);
        o2 = limitar(o2 + (random.nextDouble() - 0.5) * 0.6, 16, 22);

        // Se las pasamos a la pantalla de ajustes para poder calibrar
        Config.ultimoTempRaw = (float) tempRaw;
        Config.ultimoPhV = (float) phV;

        // Se aplica la calibración (igual que en el ESP32)
        temp = tempRaw + config.get("temp_offset");
        double vNeutro = config.get("ph_v_neutro");
        double vAcido = config.get("ph_v_acido");
        double diff = vNeutro - vAcido;
        double pendiente = (Math.abs(diff) < 0.001) ? 0.0 : 3.0 / diff;
        ph = limitar(7.0 + (phV - vNeutro) * pendiente, 0, 14);

        historial.add((float) temp);
        if (historial.size() > 30) historial.remove(0);
        HistorialManager.agregar((float) temp, (float) ph, (float) o2);
    }

    // 0 = normal, 1 = bajo, 2 = alto
    private int evaluar(double valor, double min, double max) {
        if (valor < min) return 1;
        if (valor > max) return 2;
        return 0;
    }

    private void pintarEstado(TextView chip, int estado) {
        String[] textos = {"NORMAL", "BAJO", "ALTO"};
        chip.setText(textos[estado]);
        GradientDrawable fondo = new GradientDrawable();
        fondo.setCornerRadius(dp(999));
        fondo.setStroke(Math.max(1, Math.round(1.5f * getResources().getDisplayMetrics().density)), Color.parseColor("#000000"));
        if (estado == 0) {
            fondo.setColor(Color.parseColor("#55DB9C")); // Mint Pop
            chip.setTextColor(Color.parseColor("#000000"));
        } else {
            fondo.setColor(Color.parseColor("#FB4903")); // Ember
            chip.setTextColor(Color.parseColor("#FFFFFF"));
        }
        chip.setBackground(fondo);
    }

    private void registrarCambios(int[] estado) {
        String[] nombres = {"Temperatura", "pH", "Oxígeno"};
        String[] palabras = {"normal", "baja", "alta"};
        String hora = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());

        for (int i = 0; i < 3; i++) {
            if (estado[i] != estadoPrevio[i]) {
                boolean primeraLecturaNormal = estadoPrevio[i] == -1 && estado[i] == 0;
                if (!primeraLecturaNormal) {
                    String palabra = palabras[estado[i]];
                    if (i == 1 && estado[i] != 0) palabra = (estado[i] == 1) ? "ácido" : "alcalino";
                    registro.add(0, hora + "  " + nombres[i] + ": " + palabra);
                    if (registro.size() > 5) registro.remove(registro.size() - 1);
                }
                estadoPrevio[i] = estado[i];
            }
        }

        tvAlertas.setText(registro.isEmpty() ? "Sin alertas" : TextUtils.join("\n", registro));
    }

    private void mostrar() {
        // Los umbrales ahora vienen de la configuración guardada
        float tMin = config.get("t_min"), tMax = config.get("t_max");
        float phMin = config.get("ph_min"), phMax = config.get("ph_max");
        float o2Min = config.get("o2_min");

        tvTemp.setText(String.format(Locale.US, "%.1f", temp));
        tvPh.setText(String.format(Locale.US, "%.2f", ph));
        tvO2.setText(String.format(Locale.US, "%.1f", o2));

        int eT = evaluar(temp, tMin, tMax);
        int eP = evaluar(ph, phMin, phMax);
        int eO = evaluar(o2, o2Min, Double.MAX_VALUE);

        pintarEstado(tvTempEstado, eT);
        pintarEstado(tvPhEstado, eP);
        pintarEstado(tvO2Estado, eO);

        int[] estado = {eT, eP, eO};
        int enRango = 0;
        for (int e : estado) if (e == 0) enRango++;

        anillo.setProgreso(enRango, 3);
        tvResumen.setText(enRango == 3 ? "Todo en rango" : (3 - enRango) + " fuera de rango");
        grafica.setHoras(HistorialManager.obtenerPrimeraHora(), HistorialManager.obtenerUltimaHora());
        grafica.setDatos(historial, tMin, tMax);
        registrarCambios(estado);

        StringBuilder alerta = new StringBuilder();
        if (eT != 0) alerta.append("Temperatura fuera de rango  ");
        if (eP != 0) alerta.append("pH fuera de rango  ");
        if (eO != 0) alerta.append("Oxígeno bajo");

        if (alerta.length() > 0) {
            tvAlerta.setText("⚠ " + alerta.toString().trim());
            tvAlerta.setVisibility(View.VISIBLE);
        } else {
            tvAlerta.setVisibility(View.GONE);
        }

        // Notificaciones al celular cuando cualquier lectura se sale del rango
        if (eT == 1) {
            Notificador.notificarAlerta(this, Notificador.ID_NOTIF_TEMP,
                    "🚨 Alerta: Temperatura Baja",
                    String.format(Locale.US, "La temperatura cayó a %.1f °C (mínimo permitido: %.1f °C)", temp, tMin));
        } else if (eT == 2) {
            Notificador.notificarAlerta(this, Notificador.ID_NOTIF_TEMP,
                    "🚨 Alerta: Temperatura Alta",
                    String.format(Locale.US, "La temperatura subió a %.1f °C (máximo permitido: %.1f °C)", temp, tMax));
        } else {
            Notificador.cancelarAlerta(this, Notificador.ID_NOTIF_TEMP);
        }

        if (eP == 1) {
            Notificador.notificarAlerta(this, Notificador.ID_NOTIF_PH,
                    "🚨 Alerta: pH Ácido",
                    String.format(Locale.US, "El agua está ácida: pH %.2f (mínimo permitido: %.2f)", ph, phMin));
        } else if (eP == 2) {
            Notificador.notificarAlerta(this, Notificador.ID_NOTIF_PH,
                    "🚨 Alerta: pH Alcalino",
                    String.format(Locale.US, "El agua está alcalina: pH %.2f (máximo permitido: %.2f)", ph, phMax));
        } else {
            Notificador.cancelarAlerta(this, Notificador.ID_NOTIF_PH);
        }

        if (eO == 1) {
            Notificador.notificarAlerta(this, Notificador.ID_NOTIF_O2,
                    "🚨 Alerta: Oxígeno Disuelto Bajo",
                    String.format(Locale.US, "El oxígeno cayó a %.1f %% (mínimo vital: %.1f %%)", o2, o2Min));
        } else {
            Notificador.cancelarAlerta(this, Notificador.ID_NOTIF_O2);
        }
    }
}