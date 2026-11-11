package com.example.uniagraria;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Enlace de la app con el ESP32 a través de un broker MQTT en internet.
 * Funciona igual si el celular y el ESP32 están en la misma red o en redes distintas.
 */
public class NubeMqtt implements MqttCallbackExtended {

    // ===== DATOS DE TU BROKER (los entrega EMQX Cloud) =====
    public static final String HOST = "x1de9a11.ala.us-east-1.emqxsl.com";      // ej: "xxxxxxxx.ala.us-east-1.emqxsl.com"
    public static final int PUERTO = 8883;
    public static final String USUARIO = "PECERA";
    public static final String CLAVE = "44244";
    public static final String BASE = "pecera/uniagraria";   // igual que TOPIC_BASE del ESP32
    // =========================================================

    private static final String T_DATOS = BASE + "/datos";
    private static final String T_ESTADO = BASE + "/estado";
    private static final String T_CONFIG = BASE + "/config";
    private static final String T_CONFIG_SET = BASE + "/config/set";
    private static final String T_CALIBRAR = BASE + "/calibrar";
    private static final String T_RESPUESTA = BASE + "/respuesta";
    private static final String T_VIVO = BASE + "/vivo";

    /** Última lectura enviada por el ESP32. Los valores sin lectura vienen como NaN. */
    public static class Datos {
        public double temp = Double.NaN, ph = Double.NaN, o2 = Double.NaN;
        public double phV = Double.NaN, tempRaw = Double.NaN;
        public String eTemp = "sin_dato", ePh = "sin_dato", eO2 = "sin_dato";
        public long recibido;
    }

    /** Las pantallas se enteran de los cambios implementando solo lo que necesiten. */
    public interface Escucha {
        default void onNube(boolean conectada) {}
        default void onEsp32(boolean enLinea) {}
        default void onDatos(Datos datos) {}
        default void onConfig() {}
        default void onRespuesta(boolean ok, String mensaje) {}
    }

    /** 0 = normal, 1 = bajo, 2 = alto, 3 = sin dato */
    public static int estadoComoEntero(String estado) {
        if ("ok".equals(estado)) return 0;
        if ("bajo".equals(estado)) return 1;
        if ("alto".equals(estado)) return 2;
        return 3;
    }

    private static NubeMqtt instancia;

    public static synchronized NubeMqtt get(Context contexto) {
        if (instancia == null) instancia = new NubeMqtt(contexto.getApplicationContext());
        return instancia;
    }

    private final Config config;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArrayList<Escucha> escuchas = new CopyOnWriteArrayList<>();
    private MqttAsyncClient cliente;
    private volatile boolean nubeConectada = false;
    private volatile boolean esp32EnLinea = false;
    private volatile Datos ultimos = null;

    // Latido: mientras haya una pantalla abierta, avisa al ESP32 para que publique rápido.
    // Sin latido el ESP32 publica solo una vez por minuto y se ahorra cuota de datos.
    private final Runnable latido = new Runnable() {
        @Override
        public void run() {
            if (escuchas.isEmpty()) return;
            enviarLatido();
            ui.postDelayed(this, 10000);
        }
    };

    private void enviarLatido() {
        if (cliente == null || !cliente.isConnected()) return;
        try {
            MqttMessage m = new MqttMessage("1".getBytes(StandardCharsets.UTF_8));
            m.setQos(0);
            cliente.publish(T_VIVO, m);
        } catch (MqttException ignorada) {
        }
    }

    private void reiniciarLatido() {
        ui.removeCallbacks(latido);
        if (!escuchas.isEmpty()) ui.post(latido);
    }

    private NubeMqtt(Context contexto) {
        config = new Config(contexto);
    }

    public boolean configurada() {
        return !HOST.isEmpty();
    }

    public boolean nubeConectada() {
        return nubeConectada;
    }

    public boolean esp32EnLinea() {
        return esp32EnLinea;
    }

    public boolean listoParaEnviar() {
        return nubeConectada && esp32EnLinea;
    }

    public Datos ultimosDatos() {
        return ultimos;
    }

    // ---------- Conexión ----------
    public synchronized void iniciar() {
        if (cliente != null || !configurada()) return;
        try {
            String id = "pecera-app-" + UUID.randomUUID().toString().substring(0, 8);
            cliente = new MqttAsyncClient("ssl://" + HOST + ":" + PUERTO, id, new MemoryPersistence());
            cliente.setCallback(this);
            conectar();
        } catch (MqttException e) {
            cliente = null;
        }
    }

    private void conectar() {
        if (cliente == null || cliente.isConnected()) return;
        MqttConnectOptions op = new MqttConnectOptions();
        op.setUserName(USUARIO);
        op.setPassword(CLAVE.toCharArray());
        op.setCleanSession(true);
        op.setAutomaticReconnect(true);   // reconecta solo si se cae después de conectar
        op.setKeepAliveInterval(30);
        op.setConnectionTimeout(15);
        try {
            cliente.connect(op, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken token) {
                    suscribir();
                }

                @Override
                public void onFailure(IMqttToken token, Throwable error) {
                    // Sin internet o credenciales incorrectas: reintenta
                    ui.postDelayed(NubeMqtt.this::conectar, 5000);
                }
            });
        } catch (MqttException e) {
            ui.postDelayed(this::conectar, 5000);
        }
    }

    private void suscribir() {
        try {
            cliente.subscribe(
                    new String[]{T_DATOS, T_ESTADO, T_CONFIG, T_RESPUESTA},
                    new int[]{0, 1, 1, 1});
        } catch (MqttException ignorada) {
            // se reintenta en la próxima reconexión
        }
        nubeConectada = true;
        avisar(e -> e.onNube(true));
        ui.post(this::reiniciarLatido);
    }

    @Override
    public void connectComplete(boolean reconexion, String servidor) {
        suscribir();
    }

    @Override
    public void connectionLost(Throwable causa) {
        nubeConectada = false;
        esp32EnLinea = false;
        avisar(e -> e.onNube(false));
        avisar(e -> e.onEsp32(false));
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    // ---------- Mensajes que llegan ----------
    @Override
    public void messageArrived(String tema, MqttMessage mensaje) {
        String texto = new String(mensaje.getPayload(), StandardCharsets.UTF_8);
        try {
            if (T_DATOS.equals(tema)) {
                procesarDatos(texto, mensaje.isRetained());
            } else if (T_ESTADO.equals(tema)) {
                final boolean enLinea = "online".equals(texto.trim());
                esp32EnLinea = enLinea;
                avisar(e -> e.onEsp32(enLinea));
            } else if (T_CONFIG.equals(tema)) {
                procesarConfig(texto);
            } else if (T_RESPUESTA.equals(tema)) {
                JSONObject o = new JSONObject(texto);
                final boolean ok = o.optBoolean("ok", false);
                final String msg = o.optString("msg", "");
                avisar(e -> e.onRespuesta(ok, msg));
            }
        } catch (JSONException ignorada) {
            // mensaje mal formado: se descarta
        }
    }

    private void procesarDatos(String texto, boolean retenido) throws JSONException {
        JSONObject o = new JSONObject(texto);
        final Datos d = new Datos();
        d.temp = o.optDouble("temp", Double.NaN);
        d.tempRaw = o.optDouble("temp_raw", Double.NaN);
        d.ph = o.optDouble("ph", Double.NaN);
        d.phV = o.optDouble("ph_v", Double.NaN);
        d.o2 = o.optDouble("o2", Double.NaN);
        JSONObject est = o.optJSONObject("estados");
        if (est != null) {
            d.eTemp = est.optString("temp", "sin_dato");
            d.ePh = est.optString("ph", "sin_dato");
            d.eO2 = est.optString("o2", "sin_dato");
        }
        d.recibido = System.currentTimeMillis();
        ultimos = d;

        // El mensaje retenido es la última lectura guardada por el broker, no una lectura nueva
        if (!retenido && !Double.isNaN(d.temp) && !Double.isNaN(d.ph) && !Double.isNaN(d.o2)) {
            HistorialManager.agregar((float) d.temp, (float) d.ph, (float) d.o2);
        }
        avisar(e -> e.onDatos(d));
    }

    private void procesarConfig(String texto) throws JSONException {
        JSONObject o = new JSONObject(texto);
        Map<String, Float> valores = new HashMap<>();
        for (String clave : Config.CLAVES) {
            double v = o.optDouble(clave, Double.NaN);
            if (!Double.isNaN(v)) valores.put(clave, (float) v);
        }
        config.aplicarRemota(valores);   // el ESP32 manda: se guarda tal cual
        avisar(e -> e.onConfig());
    }

    // ---------- Órdenes hacia el ESP32 ----------
    public boolean enviarConfig(Map<String, Float> cambios) {
        try {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, Float> e : cambios.entrySet()) {
                o.put(e.getKey(), Double.parseDouble(Float.toString(e.getValue())));
            }
            return publicar(T_CONFIG_SET, o.toString());
        } catch (JSONException e) {
            return false;
        }
    }

    /** punto: "ph7", "ph4" o "temp" (en este último, referencia = lo que marca el termómetro). */
    public boolean calibrar(String punto, Float referencia) {
        try {
            JSONObject o = new JSONObject();
            o.put("punto", punto);
            if (referencia != null) {
                o.put("referencia", Double.parseDouble(Float.toString(referencia)));
            }
            return publicar(T_CALIBRAR, o.toString());
        } catch (JSONException e) {
            return false;
        }
    }

    private boolean publicar(String tema, String texto) {
        if (cliente == null || !cliente.isConnected()) return false;
        try {
            MqttMessage m = new MqttMessage(texto.getBytes(StandardCharsets.UTF_8));
            m.setQos(1);
            cliente.publish(tema, m);
            return true;
        } catch (MqttException e) {
            return false;
        }
    }

    // ---------- Pantallas que escuchan ----------
    public void agregarEscucha(Escucha escucha) {
        escuchas.add(escucha);
        reiniciarLatido();
        escucha.onNube(nubeConectada);
        escucha.onEsp32(esp32EnLinea);
        Datos d = ultimos;
        if (d != null) escucha.onDatos(d);
    }

    public void quitarEscucha(Escucha escucha) {
        escuchas.remove(escucha);
        if (escuchas.isEmpty()) ui.removeCallbacks(latido);
    }

    private void avisar(Consumer<Escucha> accion) {
        ui.post(() -> {
            for (Escucha e : escuchas) accion.accept(e);
        });
    }
}