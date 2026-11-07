package com.example.uniagraria;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;

public class Config {

    public static final String[] CLAVES = {
            "t_min", "t_max", "ph_min", "ph_max", "o2_min",
            "temp_offset", "ph_v_neutro", "ph_v_acido"};

    // Últimos valores "crudos" de los sensores (simulados por ahora)
    public static volatile float ultimoTempRaw = Float.NaN;
    public static volatile float ultimoPhV = Float.NaN;

    private final SharedPreferences sp;

    public Config(Context contexto) {
        sp = contexto.getSharedPreferences("config", Context.MODE_PRIVATE);
    }

    public float get(String clave) {
        return sp.getFloat(clave, defecto(clave));
    }

    private static float defecto(String clave) {
        switch (clave) {
            case "t_min": return 25f;
            case "t_max": return 31f;
            case "ph_min": return 6.5f;
            case "ph_max": return 8.5f;
            case "o2_min": return 19f;
            case "ph_v_neutro": return 1.278f;
            case "ph_v_acido": return 1.043f;
            default: return 0f;
        }
    }

    private static float[] limites(String clave) {
        switch (clave) {
            case "t_min":
            case "t_max": return new float[]{0, 50};
            case "ph_min":
            case "ph_max": return new float[]{0, 14};
            case "o2_min": return new float[]{0, 25};
            case "temp_offset": return new float[]{-20, 20};
            default: return new float[]{0, 3.3f};
        }
    }

    private static String nombre(String clave) {
        switch (clave) {
            case "t_min": return "Temperatura mínima";
            case "t_max": return "Temperatura máxima";
            case "ph_min": return "pH mínimo";
            case "ph_max": return "pH máximo";
            case "o2_min": return "Oxígeno mínimo";
            case "temp_offset": return "Corrección de temperatura";
            case "ph_v_neutro": return "Voltaje de pH 7";
            default: return "Voltaje de pH 4";
        }
    }

    /** Valida y guarda. Devuelve null si todo salió bien, o el mensaje de error. */
    public String guardar(Map<String, Float> cambios) {
        Map<String, Float> nueva = new HashMap<>();
        for (String k : CLAVES) nueva.put(k, get(k));

        for (Map.Entry<String, Float> e : cambios.entrySet()) {
            String k = e.getKey();
            float v = e.getValue();
            if (Float.isNaN(v) || Float.isInfinite(v)) {
                return "Valor no numérico en " + nombre(k);
            }
            float[] lim = limites(k);
            if (v < lim[0] || v > lim[1]) {
                return nombre(k) + " fuera de rango (" + lim[0] + " a " + lim[1] + ")";
            }
            nueva.put(k, v);
        }

        if (nueva.get("t_min") >= nueva.get("t_max")) {
            return "La temperatura mínima debe ser menor que la máxima";
        }
        if (nueva.get("ph_min") >= nueva.get("ph_max")) {
            return "El pH mínimo debe ser menor que el máximo";
        }
        if (Math.abs(nueva.get("ph_v_neutro") - nueva.get("ph_v_acido")) < 0.05f) {
            return "Los voltajes de calibración de pH son casi iguales";
        }

        SharedPreferences.Editor ed = sp.edit();
        for (String k : CLAVES) ed.putFloat(k, nueva.get(k));
        ed.apply();
        return null;
    }
}