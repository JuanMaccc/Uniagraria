package com.example.uniagraria;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

public class Notificador {

    public static final String CANAL_ID = "canal_alertas_pecera";
    private static final String CANAL_NOMBRE = "Alertas de Pecera";
    private static final String CANAL_DESCRIPCION = "Notificaciones de parámetros del agua fuera de rango";

    public static final int ID_NOTIF_TEMP = 1001;
    public static final int ID_NOTIF_PH = 1002;
    public static final int ID_NOTIF_O2 = 1003;

    private static long ultimaNotifTemp = 0;
    private static long ultimaNotifPh = 0;
    private static long ultimaNotifO2 = 0;
    private static final long COOLDOWN_MS = 25000; // 25 segundos de espera mínima entre notificaciones del mismo sensor

    public static void inicializarCanal(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel canal = new NotificationChannel(
                    CANAL_ID,
                    CANAL_NOMBRE,
                    NotificationManager.IMPORTANCE_HIGH
            );
            canal.setDescription(CANAL_DESCRIPCION);
            canal.enableVibration(true);
            canal.enableLights(true);

            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(canal);
            }
        }
    }

    public static void notificarAlerta(Context context, int notifId, String titulo, String mensaje) {
        long ahora = System.currentTimeMillis();

        if (notifId == ID_NOTIF_TEMP) {
            if (ahora - ultimaNotifTemp < COOLDOWN_MS) return;
            ultimaNotifTemp = ahora;
        } else if (notifId == ID_NOTIF_PH) {
            if (ahora - ultimaNotifPh < COOLDOWN_MS) return;
            ultimaNotifPh = ahora;
        } else if (notifId == ID_NOTIF_O2) {
            if (ahora - ultimaNotifO2 < COOLDOWN_MS) return;
            ultimaNotifO2 = ahora;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                context,
                notifId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CANAL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(titulo)
                .setContentText(mensaje)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(mensaje))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pi);

        try {
            NotificationManagerCompat.from(context).notify(notifId, builder.build());
        } catch (SecurityException ignored) {
            // Permiso no concedido
        }
    }

    public static void cancelarAlerta(Context context, int notifId) {
        try {
            NotificationManagerCompat.from(context).cancel(notifId);
        } catch (Exception ignored) {
        }
    }
}
