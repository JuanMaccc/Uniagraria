package com.example.uniagraria;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.Map;

public class Config {

    public static final String[] CLAVES = {
            "t_min", "t_max", "ph_min", "ph_max", "o2_min",
            "temp_offset", "ph_v_neutro", "ph_v_acido"};

    // Últimos valores "crudos" de los sensores con valores base realistas
    public static volatile float ultimoTempRaw = 28.0f;
    public static volatile float ultimoPhV = 1.278f;

    private final SharedPreferences sp;

    public Config(Context contexto) {
        sp = contexto.getSharedPreferences("config", Context.MODE_PRIVATE);
    }

    public float get(String clave) {
        return sp.getFloat(clave, defecto(clave));
    }

    public static float defecto(String clave) {
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

    /** Restablece todos los valores a los valores de fábrica. */
    public void restablecerPorDefecto() {
        SharedPreferences.Editor ed = sp.edit();
        for (String k : CLAVES) {
            ed.putFloat(k, defecto(k));
        }
        ed.apply();
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

        // Validar coherencia solo sobre lo que se está modificando
        boolean cambioTempUmbrales = cambios.containsKey("t_min") || cambios.containsKey("t_max");
        Float tMin = nueva.get("t_min");
        Float tMax = nueva.get("t_max");
        if (cambioTempUmbrales && tMin != null && tMax != null && tMin >= tMax) {
            return "La temperatura mínima debe ser menor que la máxima";
        }

        boolean cambioPhUmbrales = cambios.containsKey("ph_min") || cambios.containsKey("ph_max");
        Float phMin = nueva.get("ph_min");
        Float phMax = nueva.get("ph_max");
        if (cambioPhUmbrales && phMin != null && phMax != null && phMin >= phMax) {
            return "El pH mínimo debe ser menor que el máximo";
        }

        boolean cambioPhCalibracion = cambios.containsKey("ph_v_neutro") || cambios.containsKey("ph_v_acido");
        if (cambioPhCalibracion) {
            Float vNeutro = nueva.get("ph_v_neutro");
            Float vAcido = nueva.get("ph_v_acido");
            if (vNeutro != null && vAcido != null && Math.abs(vNeutro - vAcido) < 0.05f) {
                return "Los voltajes de pH 7 y pH 4 son casi iguales (deben variar al menos 0.05 V)";
            }
        }

        SharedPreferences.Editor ed = sp.edit();
        for (Map.Entry<String, Float> e : cambios.entrySet()) {
            ed.putFloat(e.getKey(), e.getValue());
        }
        ed.apply();
        return null;
    }
}
