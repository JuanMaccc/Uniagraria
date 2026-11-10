package com.example.uniagraria;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistorialManager {

    public static class Lectura {
        public final long timestamp;
        public final String hora;
        public final float temperatura;
        public final float ph;
        public final float oxigeno;

        public Lectura(float temp, float ph, float o2) {
            this(System.currentTimeMillis(), temp, ph, o2);
        }

        public Lectura(long timestamp, float temp, float ph, float o2) {
            this.timestamp = timestamp;
            this.hora = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(timestamp));
            this.temperatura = temp;
            this.ph = ph;
            this.oxigeno = o2;
        }
    }

    private static final int MAX_LECTURAS = 60;
    private static final List<Lectura> lecturas = Collections.synchronizedList(new ArrayList<>());

    public static void agregar(float temp, float ph, float o2) {
        lecturas.add(new Lectura(temp, ph, o2));
        while (lecturas.size() > MAX_LECTURAS) {
            lecturas.remove(0);
        }
    }

    public static List<Lectura> obtenerTodas() {
        synchronized (lecturas) {
            return new ArrayList<>(lecturas);
        }
    }

    public static List<Float> obtenerTemperaturas() {
        List<Float> lista = new ArrayList<>();
        synchronized (lecturas) {
            for (Lectura l : lecturas) {
                lista.add(l.temperatura);
            }
        }
        return lista;
    }

    public static List<Float> obtenerPh() {
        List<Float> lista = new ArrayList<>();
        synchronized (lecturas) {
            for (Lectura l : lecturas) {
                lista.add(l.ph);
            }
        }
        return lista;
    }

    public static List<Float> obtenerOxigeno() {
        List<Float> lista = new ArrayList<>();
        synchronized (lecturas) {
            for (Lectura l : lecturas) {
                lista.add(l.oxigeno);
            }
        }
        return lista;
    }

    public static String obtenerPrimeraHora() {
        synchronized (lecturas) {
            return lecturas.isEmpty() ? "--:--:--" : lecturas.get(0).hora;
        }
    }

    public static String obtenerUltimaHora() {
        synchronized (lecturas) {
            return lecturas.isEmpty() ? "--:--:--" : lecturas.get(lecturas.size() - 1).hora;
        }
    }

    public static void limpiar() {
        lecturas.clear();
    }

    public static int cantidad() {
        return lecturas.size();
    }

    /** Si no hay lecturas, genera un historial previo realista para que las gráficas tengan evolución temporal de inicio */
    public static void inicializarSiVacio(Config config) {
        if (!lecturas.isEmpty()) return;

        long ahora = System.currentTimeMillis();
        float tOffset = config != null ? config.get("temp_offset") : 0f;
        float vNeutro = config != null ? config.get("ph_v_neutro") : 1.278f;
        float vAcido = config != null ? config.get("ph_v_acido") : 1.043f;
        float diff = vNeutro - vAcido;
        float pendiente = (Math.abs(diff) < 0.001f) ? 0.0f : 3.0f / diff;

        float t = 27.5f;
        float vPh = 1.278f;
        float o2 = 20.2f;

        // Generar 20 puntos cada 2 segundos hacia atrás
        for (int i = 20; i >= 1; i--) {
            long ts = ahora - (long) i * 2000L;
            t += (float) ((Math.random() - 0.49) * 0.4);
            vPh += (float) ((Math.random() - 0.5) * 0.015);
            o2 += (float) ((Math.random() - 0.5) * 0.3);

            float tFinal = t + tOffset;
            float phFinal = Math.max(0, Math.min(14, 7.0f + (vPh - vNeutro) * pendiente));
            float o2Final = Math.max(0, Math.min(25, o2));

            lecturas.add(new Lectura(ts, tFinal, phFinal, o2Final));
        }
    }
}
