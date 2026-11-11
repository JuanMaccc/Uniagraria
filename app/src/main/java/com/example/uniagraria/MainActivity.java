package com.example.uniagraria;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
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

public class MainActivity extends AppCompatActivity {

    private Config config;
    private NubeMqtt nube;

    // Última lectura recibida del ESP32 y estado de los enlaces
    private NubeMqtt.Datos ultimo = null;
    private boolean nubeOk = false;
    private boolean esp32Ok = false;

    private final List<String> registro = new ArrayList<>();
    private final int[] estadoPrevio = {-1, -1, -1};

    private TextView tvAlerta, tvTemp, tvTempEstado, tvPh, tvPhEstado, tvO2, tvO2Estado;
    private TextView tvResumen, tvAlertas, tvConexion;
    private AnilloView anillo;
    private GraficaView grafica;

    private final NubeMqtt.Escucha escucha = new NubeMqtt.Escucha() {
        @Override
        public void onNube(boolean conectada) {
            nubeOk = conectada;
            actualizarConexion();
            mostrar();
        }

        @Override
        public void onEsp32(boolean enLinea) {
            esp32Ok = enLinea;
            actualizarConexion();
            mostrar();
        }

        @Override
        public void onDatos(NubeMqtt.Datos datos) {
            ultimo = datos;
            mostrar();
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
        nube = NubeMqtt.get(this);
        nube.iniciar();

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
        tvConexion = findViewById(R.id.tvConexion);
        anillo = findViewById(R.id.anillo);
        grafica = findViewById(R.id.grafica);

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

        actualizarConexion();
        mostrar();
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
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private String fmt(double v, String formato) {
        return Double.isNaN(v) ? "--" : String.format(Locale.US, formato, v);
    }

    /** Etiqueta de la cabecera: dice si hay datos reales llegando. */
    private void actualizarConexion() {
        if (tvConexion == null) return;
        String texto;
        String color;
        if (!nube.configurada()) {
            texto = "● FALTA CONFIGURAR LA NUBE";
            color = "#FB4903";
        } else if (!nubeOk) {
            texto = "● CONECTANDO A LA NUBE…";
            color = "#BDBDBD";
        } else if (!esp32Ok) {
            texto = "● ESP32 DESCONECTADO";
            color = "#FB4903";
        } else {
            texto = "● EN LÍNEA · TIEMPO REAL";
            color = "#55DB9C";
        }
        tvConexion.setText(texto);
        tvConexion.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor(color)));
    }

    // 0 = normal, 1 = bajo, 2 = alto, 3 = sin dato
    private void pintarEstado(TextView chip, int estado) {
        String[] textos = {"NORMAL", "BAJO", "ALTO", "SIN DATO"};
        chip.setText(textos[estado]);
        GradientDrawable fondo = new GradientDrawable();
        fondo.setCornerRadius(dp(999));
        fondo.setStroke(Math.max(1, Math.round(1.5f * getResources().getDisplayMetrics().density)), Color.parseColor("#000000"));
        if (estado == 0) {
            fondo.setColor(Color.parseColor("#55DB9C")); // Mint Pop
            chip.setTextColor(Color.parseColor("#000000"));
        } else if (estado == 3) {
            fondo.setColor(Color.parseColor("#E0E0E0"));
            chip.setTextColor(Color.parseColor("#000000"));
        } else {
            fondo.setColor(Color.parseColor("#FB4903")); // Ember
            chip.setTextColor(Color.parseColor("#FFFFFF"));
        }
        chip.setBackground(fondo);
    }

    private void registrarCambios(int[] estado) {
        String[] nombres = {"Temperatura", "pH", "Oxígeno"};
        String[] palabras = {"normal", "baja", "alta", "sin lectura"};
        String hora = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());

        for (int i = 0; i < 3; i++) {
            if (estado[i] != estadoPrevio[i]) {
                boolean primeraLecturaNormal = estadoPrevio[i] == -1 && estado[i] == 0;
                if (!primeraLecturaNormal) {
                    String palabra = palabras[estado[i]];
                    if (i == 1 && (estado[i] == 1 || estado[i] == 2)) {
                        palabra = (estado[i] == 1) ? "ácido" : "alcalino";
                    }
                    registro.add(0, hora + "  " + nombres[i] + ": " + palabra);
                    if (registro.size() > 5) registro.remove(registro.size() - 1);
                }
                estadoPrevio[i] = estado[i];
            }
        }

        tvAlertas.setText(registro.isEmpty() ? "Sin alertas" : TextUtils.join("\n", registro));
    }

    private void mostrarAlerta(String texto) {
        tvAlerta.setText(texto);
        tvAlerta.setVisibility(View.VISIBLE);
    }

    private void mostrar() {
        float tMin = config.get("t_min"), tMax = config.get("t_max");
        float phMin = config.get("ph_min"), phMax = config.get("ph_max");
        float o2Min = config.get("o2_min");

        NubeMqtt.Datos d = ultimo;
        double temp = d == null ? Double.NaN : d.temp;
        double ph = d == null ? Double.NaN : d.ph;
        double o2 = d == null ? Double.NaN : d.o2;

        tvTemp.setText(fmt(temp, "%.1f"));
        tvPh.setText(fmt(ph, "%.2f"));
        tvO2.setText(fmt(o2, "%.1f"));

        // Los estados los calcula el propio ESP32 con sus umbrales
        int eT = d == null ? 3 : NubeMqtt.estadoComoEntero(d.eTemp);
        int eP = d == null ? 3 : NubeMqtt.estadoComoEntero(d.ePh);
        int eO = d == null ? 3 : NubeMqtt.estadoComoEntero(d.eO2);

        pintarEstado(tvTempEstado, eT);
        pintarEstado(tvPhEstado, eP);
        pintarEstado(tvO2Estado, eO);

        int[] estado = {eT, eP, eO};
        int enRango = 0, fuera = 0, sinLectura = 0;
        for (int e : estado) {
            if (e == 0) enRango++;
            else if (e == 3) sinLectura++;
            else fuera++;
        }

        anillo.setProgreso(enRango, 3);
        if (enRango == 3) tvResumen.setText("Todo en rango");
        else if (fuera > 0) tvResumen.setText(fuera + " fuera de rango");
        else tvResumen.setText(sinLectura + " sin lectura");

        List<Float> temps = HistorialManager.obtenerTemperaturas();
        grafica.setHoras(HistorialManager.obtenerPrimeraHora(), HistorialManager.obtenerUltimaHora());
        grafica.setDatos(temps, tMin, tMax);

        if (esp32Ok && d != null) registrarCambios(estado);

        // Franja de alerta
        if (!nube.configurada()) {
            mostrarAlerta("⚠ Falta escribir los datos del broker en NubeMqtt.java");
        } else if (!nubeOk) {
            mostrarAlerta("⚠ Sin conexión con la nube. Se muestran los últimos datos.");
        } else if (!esp32Ok) {
            mostrarAlerta("⚠ ESP32 desconectado. Se muestran los últimos datos.");
        } else {
            StringBuilder alerta = new StringBuilder();
            if (eT == 1 || eT == 2) alerta.append("Temperatura fuera de rango  ");
            if (eP == 1 || eP == 2) alerta.append("pH fuera de rango  ");
            if (eO == 1) alerta.append("Oxígeno bajo");
            if (alerta.length() > 0) mostrarAlerta("⚠ " + alerta.toString().trim());
            else tvAlerta.setVisibility(View.GONE);
        }

        // Notificaciones al celular (solo con el ESP32 en línea y datos válidos)
        if (!esp32Ok || d == null) return;

        if (eT == 1) {
            Notificador.notificarAlerta(this, Notificador.ID_NOTIF_TEMP,
                    "🚨 Alerta: Temperatura Baja",
                    String.format(Locale.US, "La temperatura cayó a %.1f °C (mínimo permitido: %.1f °C)", temp, tMin));
        } else if (eT == 2) {
            Notificador.notificarAlerta(this, Notificador.ID_NOTIF_TEMP,
                    "🚨 Alerta: Temperatura Alta",
                    String.format(Locale.US, "La temperatura subió a %.1f °C (máximo permitido: %.1f °C)", temp, tMax));
        } else if (eT == 0) {
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
        } else if (eP == 0) {
            Notificador.cancelarAlerta(this, Notificador.ID_NOTIF_PH);
        }

        if (eO == 1) {
            Notificador.notificarAlerta(this, Notificador.ID_NOTIF_O2,
                    "🚨 Alerta: Oxígeno Disuelto Bajo",
                    String.format(Locale.US, "El oxígeno cayó a %.1f %% (mínimo vital: %.1f %%)", o2, o2Min));
        } else if (eO == 0) {
            Notificador.cancelarAlerta(this, Notificador.ID_NOTIF_O2);
        }
    }
}