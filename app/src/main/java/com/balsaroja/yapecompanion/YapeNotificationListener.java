package com.balsaroja.yapecompanion;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.content.pm.PackageManager;
import android.os.Build;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.os.Bundle;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.Query;
import com.google.firebase.auth.FirebaseAuth;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YapeNotificationListener extends NotificationListenerService {
    private static final Pattern PEN_AMOUNT = Pattern.compile("(?:S\\/|PEN)\\s*([0-9]+(?:[.,][0-9]{1,2})?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BRL_AMOUNT = Pattern.compile("(?:R\\$|BRL|R/)\\s*([0-9][0-9.,]*)", Pattern.CASE_INSENSITIVE);
    private static final String CHANNEL_ID = "yape_requests";
    private Query requestsRef;
    private ValueEventListener requestsListener;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            FirebaseAuth.getInstance().signInAnonymously()
                    .addOnSuccessListener(result -> listenForPendingRequests());
        } else {
            listenForPendingRequests();
        }
    }

    @Override
    public void onDestroy() {
        if (requestsRef != null && requestsListener != null) {
            requestsRef.removeEventListener(requestsListener);
        }
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Solicitudes de pagos virtuales",
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("Avisos de solicitudes de pagos virtuales del POS");
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }

    private void listenForPendingRequests() {
        requestsRef = FirebaseDatabase.getInstance().getReference("yapePaymentRequests")
                .orderByChild("targetDeviceUid").equalTo(FirebaseAuth.getInstance().getUid());
        requestsListener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                for (DataSnapshot child : snapshot.getChildren()) {
                    if (!RemotePayment.belongsToThisDevice(YapeNotificationListener.this, child)) continue;
                    if (!"pending".equals(String.valueOf(child.child("status").getValue()))
                            || hasOwnerDecision(child)) continue;
                    String requestId = String.valueOf(child.child("requestId").getValue());
                    String amount = String.valueOf(child.child("amount").getValue());
                    String notifiedKey = "notified_" + requestId;
                    if (getSharedPreferences("yape_notifications", MODE_PRIVATE)
                            .getBoolean(notifiedKey, false)) continue;
                    notifyPendingRequest(requestId, amount, RemotePayment.label(child), RemotePayment.currency(child));
                    getSharedPreferences("yape_notifications", MODE_PRIVATE)
                            .edit().putBoolean(notifiedKey, true).apply();
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // El POS sigue siendo la fuente de verdad si el teléfono está offline.
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

    private void notifyPendingRequest(String requestId, String amount, String method, String currency) {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) return;

        Intent intent = new Intent(this, AuditActivity.class);
        intent.putExtra("focus_request_id", requestId);
        intent.setData(Uri.parse("balsaroja://yape/request/" + Uri.encode(requestId)));
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                requestId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Nueva solicitud " + method)
                .setContentText("Verifica el pago de " + currency + " " + amount + " en tu celular")
                .setStyle(new Notification.BigTextStyle().bigText(
                        "El POS está esperando verificar un pago " + method + " de " + currency + " " + amount
                ))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.notify(requestId.hashCode(), builder.build());
    }

    @Override
    public void onNotificationPosted(StatusBarNotification notification) {
        String packageName = notification.getPackageName() == null ? "" : notification.getPackageName();
        Bundle extras = notification.getNotification().extras;
        String title = String.valueOf(extras.getCharSequence(Notification.EXTRA_TITLE));
        String text = String.valueOf(extras.getCharSequence(Notification.EXTRA_TEXT));
        String content = (title + " " + text).trim();
        if (packageName.equals(getPackageName())) return;
        String method = identifyMethod(packageName, content);
        if (method == null) return;
        Matcher matcher = ("yape".equals(method) ? PEN_AMOUNT : BRL_AMOUNT).matcher(content);
        if (!matcher.find()) return;
        BigDecimal amount;
        try {
            amount = parseAmount(matcher.group(1), "yape".equals(method));
        } catch (NumberFormatException ignored) {
            return;
        }
        long postedAt = notification.getPostTime();

        Query requests = FirebaseDatabase.getInstance().getReference("yapePaymentRequests")
                .orderByChild("targetDeviceUid").equalTo(FirebaseAuth.getInstance().getUid());
        requests.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                DataSnapshot candidate = null;
                for (DataSnapshot child : snapshot.getChildren()) {
                    if (!RemotePayment.belongsToThisDevice(YapeNotificationListener.this, child)) continue;
                    if (!"pending".equals(String.valueOf(child.child("status").getValue()))) continue;
                    if (!method.equals(RemotePayment.method(child)) || hasOwnerDecision(child)) continue;
                    Object rawExpected = child.child("amount").getValue();
                    if (!(rawExpected instanceof Number)) continue;
                    BigDecimal expected = new BigDecimal(rawExpected.toString());
                    if (amount.subtract(expected).abs()
                            .compareTo(new BigDecimal("0.01")) >= 0) continue;
                    Object createdAt = child.child("createdAt").getValue();
                    if (createdAt != null) {
                        try {
                            if (postedAt < java.time.Instant.parse(String.valueOf(createdAt)).toEpochMilli()) continue;
                        } catch (java.time.format.DateTimeParseException ignored) { continue; }
                    }
                    if (candidate != null) return; // Mismo monto en dos solicitudes: no asignar el aviso arbitrariamente.
                    candidate = child;
                }
                if (candidate != null) {
                    candidate.getRef().child("phoneNotification").setValue(new PhoneNotification(
                            true, postedAt, packageName,
                            "Aviso de posible pago " + RemotePayment.label(method) + " por "
                                    + ("yape".equals(method) ? "S/ " : "R/ ") + amount));
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                // The POS remains the source of truth if the phone is offline.
            }
        });
    }

    private String identifyMethod(String packageName, String content) {
        String source = normalize(packageName + " " + content);
        if (source.contains("yape")) return "yape";
        // CPF no identifica por sí mismo una app bancaria: exigir además un aviso de ingreso.
        boolean received = source.contains("recebid") || source.contains("creditad")
                || source.contains("depositad") || source.contains("transferencia recebida")
                || source.contains("pagamento recebido");
        if (!received) return null;
        if (source.contains("net empresa") || source.contains("netempresa")) return "net_empresa";
        if (source.contains("bradesco")) return "bradesco";
        if (source.contains("cpf")) return "cpf";
        return null;
    }

    private String normalize(String value) {
        return Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
    }

    private BigDecimal parseAmount(String raw, boolean pen) {
        String value = raw;
        if (!pen && value.contains(",")) value = value.replace(".", "").replace(',', '.');
        else value = value.replace(',', '.');
        return new BigDecimal(value);
    }

    public static class PhoneNotification {
        public boolean detected;
        public long detectedAt;
        public String packageName;
        public String preview;

        public PhoneNotification() { }

        PhoneNotification(boolean detected, long detectedAt, String packageName, String preview) {
            this.detected = detected;
            this.detectedAt = detectedAt;
            this.packageName = packageName;
            this.preview = preview;
        }
    }
}
