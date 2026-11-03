package com.balsaroja.yapecompanion;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.net.Uri;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.Query;
import com.google.firebase.auth.FirebaseAuth;

public class YapeRequestService extends Service {
    private static final String CHANNEL_ID = "yape_requests";
    private static final int SERVICE_NOTIFICATION_ID = 9001;
    private Query requestsRef;
    private ValueEventListener requestsListener;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startPersistentNotification();
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            FirebaseAuth.getInstance().signInAnonymously().addOnSuccessListener(result -> listenForRequests());
        } else {
            listenForRequests();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Solicitudes de pagos virtuales",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Avisos de solicitudes de pagos virtuales del POS");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private void startPersistentNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        Notification notification = builder
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Balsa Roja POS activo")
                .setContentText("Esperando solicitudes de pago del POS")
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
        startForeground(SERVICE_NOTIFICATION_ID, notification);
    }

    private void listenForRequests() {
        requestsRef = FirebaseDatabase.getInstance().getReference("yapePaymentRequests")
                .orderByChild("targetDeviceUid").equalTo(FirebaseAuth.getInstance().getUid());
        requestsListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                for (DataSnapshot child : snapshot.getChildren()) {
                    if (!RemotePayment.belongsToThisDevice(YapeRequestService.this, child)) continue;
                    String requestId = String.valueOf(child.child("requestId").getValue());
                    String amount = String.valueOf(child.child("amount").getValue());
                    String status = String.valueOf(child.child("status").getValue());
                    if ("pending".equals(status) && !hasOwnerDecision(child)) {
                        String key = "notified_" + requestId;
                        if (getSharedPreferences("yape_notifications", MODE_PRIVATE)
                                .getBoolean(key, false)) continue;
                        notifyRequest(requestId, amount, RemotePayment.label(child), RemotePayment.currency(child));
                        getSharedPreferences("yape_notifications", MODE_PRIVATE)
                                .edit().putBoolean(key, true).apply();
                    } else if ("cancelled".equals(status) && !hasOwnerDecision(child)) {
                        String key = "cancelled_notified_" + requestId;
                        if (getSharedPreferences("yape_notifications", MODE_PRIVATE)
                                .getBoolean(key, false)) continue;
                        notifyCancelledRequest(requestId, amount, RemotePayment.label(child), RemotePayment.currency(child));
                        getSharedPreferences("yape_notifications", MODE_PRIVATE)
                                .edit().putBoolean(key, true).apply();
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // El POS mantiene la confirmación manual como fuente de verdad.
            }
        };
        requestsRef.addValueEventListener(requestsListener);
    }

    private boolean hasOwnerDecision(DataSnapshot request) {
        Object raw = request.child("ownerDecision").getValue();
        if (raw == null) return false;
        String decision = String.valueOf(raw).trim();
        return "approved".equalsIgnoreCase(decision)
                || "received".equalsIgnoreCase(decision)
                || "paid".equalsIgnoreCase(decision)
                || "not_received".equalsIgnoreCase(decision)
                || "rejected".equalsIgnoreCase(decision)
                || "not_paid".equalsIgnoreCase(decision);
    }

    private void notifyRequest(String requestId, String amount, String method, String currency) {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) return;
        Intent intent = new Intent(this, AuditActivity.class);
        intent.putExtra("focus_request_id", requestId);
        intent.setData(Uri.parse("balsaroja://yape/request/" + Uri.encode(requestId)));
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, requestId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Nueva solicitud " + method)
                .setContentText("Verifica el pago de " + currency + " " + amount + " en tu celular")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);
        getSystemService(NotificationManager.class).notify(requestId.hashCode(), builder.build());
    }

    private void notifyCancelledRequest(String requestId, String amount, String method, String currency) {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) return;
        Intent intent = new Intent(this, AuditActivity.class);
        intent.putExtra("focus_request_id", requestId);
        intent.setData(Uri.parse("balsaroja://yape/request/" + Uri.encode(requestId)));
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, requestId.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Solicitud " + method + " cancelada")
                .setContentText("Se canceló la solicitud de " + currency + " " + amount)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);
        getSystemService(NotificationManager.class).notify(
                requestId.hashCode() ^ 0x5A5A5A5A, builder.build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (requestsRef != null && requestsListener != null) {
            requestsRef.removeEventListener(requestsListener);
        }
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
