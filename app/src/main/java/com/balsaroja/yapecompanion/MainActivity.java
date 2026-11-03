package com.balsaroja.yapecompanion;

import android.content.Intent;
import android.net.Uri;
import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.graphics.Typeface;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ProgressBar;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.Query;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private static final String CHANNEL_ID = "yape_requests";
    private static final int NOTIFICATION_PERMISSION_REQUEST = 1001;
    private LinearLayout requestsContainer;
    private Query requestsRef;
    private DatabaseReference deviceLinkRef;
    private ValueEventListener deviceLinkListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        if (!getIntent().getBooleanExtra("welcome_completed", false)) {
            showWelcomeScreen();
            return;
        }
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            TextView loading = new TextView(this);
            loading.setText("Preparando vinculación segura…");
            loading.setGravity(android.view.Gravity.CENTER);
            loading.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            setContentView(loading);
            FirebaseAuth.getInstance().signInAnonymously().addOnCompleteListener(task -> {
                if (task.isSuccessful()) recreate();
                else {
                    loading.setText("No se pudo iniciar la vinculación. Activa el acceso anónimo en Firebase e intenta nuevamente.");
                    Toast.makeText(this, "Firebase Authentication no está habilitado", Toast.LENGTH_LONG).show();
                }
            });
            return;
        }
        if (pairedDeviceId().isEmpty()) {
            showPairingScreen();
            return;
        }
        listenForDeviceLink();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(24));
        root.setBackgroundColor(0xFFF6F8FC);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            view.setPadding(dp(20), dp(20) + top, dp(20), dp(24) + bottom);
            return insets;
        });

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.balsa_roja_company_logo);
        logo.setAdjustViewBounds(true);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        header.addView(logo, new LinearLayout.LayoutParams(dp(86), dp(86)));

        TextView title = new TextView(this);
        title.setText("Balsa Roja POS");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(0xFF10213F);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1);
        titleParams.setMargins(dp(10), 0, dp(8), 0);
        header.addView(title, titleParams);

        ImageButton notificationAccess = new ImageButton(this);
        notificationAccess.setImageResource(android.R.drawable.ic_popup_reminder);
        notificationAccess.setContentDescription("Acceso a notificaciones");
        notificationAccess.setColorFilter(Color.rgb(23, 105, 224));
        notificationAccess.setPadding(dp(12), dp(12), dp(12), dp(12));
        GradientDrawable notificationBackground = new GradientDrawable();
        notificationBackground.setColor(Color.WHITE);
        notificationBackground.setStroke(dp(1), Color.rgb(218, 226, 240));
        notificationBackground.setCornerRadius(dp(12));
        notificationAccess.setBackground(notificationBackground);
        notificationAccess.setOnClickListener(v -> startActivity(
                new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")));
        header.addView(notificationAccess, new LinearLayout.LayoutParams(dp(48), dp(48)));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        TextView explanation = new TextView(this);
        explanation.setText("Esta app permite responder solicitudes de pagos virtuales del POS. La venta se confirma en caja.");
        explanation.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        explanation.setTextColor(0xFF53627A);
        explanation.setPadding(0, dp(10), 0, dp(14));
        root.addView(explanation, new LinearLayout.LayoutParams(-1, -2));

        TextView pendingTitle = new TextView(this);
        pendingTitle.setText("Solicitudes recientes");
        pendingTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        pendingTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        pendingTitle.setTextColor(0xFF10213F);
        pendingTitle.setPadding(0, dp(22), 0, dp(8));

        requestsContainer = new LinearLayout(this);
        requestsContainer.setOrientation(LinearLayout.VERTICAL);

        TextView methodsTitle = new TextView(this);
        methodsTitle.setText("Medios de pago");
        methodsTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        methodsTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        methodsTitle.setTextColor(0xFF10213F);
        methodsTitle.setPadding(0, dp(22), 0, dp(4));

        root.addView(pendingTitle, new LinearLayout.LayoutParams(-1, -2));
        root.addView(requestsContainer, new LinearLayout.LayoutParams(-1, -2));
        root.addView(methodsTitle);
        TextView methodsHelp = new TextView(this);
        methodsHelp.setText("Selecciona un medio para revisar sus solicitudes e historial.");
        methodsHelp.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        methodsHelp.setTextColor(0xFF53627A);
        methodsHelp.setPadding(0, 0, 0, dp(8));
        root.addView(methodsHelp);
        LinearLayout methodGrid = new LinearLayout(this);
        methodGrid.setOrientation(LinearLayout.VERTICAL);
        for (int index = 0; index < RemotePayment.METHODS.length; index += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int offset = 0; offset < 2 && index + offset < RemotePayment.METHODS.length; offset++) {
                String method = RemotePayment.METHODS[index + offset];
                Button methodButton = new Button(this);
                methodButton.setText(RemotePayment.label(method));
                styleOutline(methodButton);
                methodButton.setOnClickListener(v -> openAudit(method, null));
                LinearLayout.LayoutParams methodParams = new LinearLayout.LayoutParams(0, dp(50), 1);
                methodParams.setMargins(dp(4), dp(4), dp(4), dp(4));
                row.addView(methodButton, methodParams);
            }
            methodGrid.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        GradientDrawable methodGridBackground = new GradientDrawable();
        methodGridBackground.setColor(Color.WHITE);
        methodGridBackground.setStroke(dp(1), Color.rgb(218, 226, 240));
        methodGridBackground.setCornerRadius(dp(14));
        methodGrid.setBackground(methodGridBackground);
        methodGrid.setPadding(dp(8), dp(8), dp(8), dp(8));
        root.addView(methodGrid, new LinearLayout.LayoutParams(-1, -2));

        Button unlinkButton = new Button(this);
        unlinkButton.setText("Desvincular del POS");
        unlinkButton.setAllCaps(false);
        unlinkButton.setTextColor(Color.rgb(190, 24, 55));
        unlinkButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        GradientDrawable unlinkBackground = new GradientDrawable();
        unlinkBackground.setColor(Color.WHITE);
        unlinkBackground.setStroke(dp(1), Color.rgb(244, 63, 94));
        unlinkBackground.setCornerRadius(dp(10));
        unlinkButton.setBackground(unlinkBackground);
        LinearLayout.LayoutParams unlinkParams = buttonParams();
        unlinkParams.setMargins(0, dp(26), 0, dp(8));
        root.addView(unlinkButton, unlinkParams);
        unlinkButton.setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Desvincular del POS")
                .setMessage("Este teléfono dejará de recibir solicitudes. También deberás desvincularlo en Ajustes del POS.")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Desvincular", (dialog, which) -> unlinkFromPos()).show());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(root);
        setContentView(scroll);
        createNotificationChannel();
        requestNotificationPermission();
        startYapeRequestService();
        listenForRequests();
    }

    private void showWelcomeScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(android.view.Gravity.CENTER);
        root.setPadding(dp(26), dp(42), dp(26), dp(32));
        root.setBackgroundColor(0xFFF6F8FC);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.app_icon);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        root.addView(logo, new LinearLayout.LayoutParams(-1, dp(118)));
        AlphaAnimation pulse = new AlphaAnimation(0.45f, 1f);
        pulse.setDuration(850);
        pulse.setRepeatMode(Animation.REVERSE);
        pulse.setRepeatCount(Animation.INFINITE);
        logo.startAnimation(pulse);

        TextView title = new TextView(this);
        title.setText("Multiservicios Generales\nBalsa Roja POS");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 25);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(0xFF10213F);
        title.setGravity(android.view.Gravity.CENTER);
        title.setPadding(0, dp(18), 0, dp(12));
        root.addView(title);

        LinearLayout welcomeCard = new LinearLayout(this);
        welcomeCard.setOrientation(LinearLayout.VERTICAL);
        welcomeCard.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        welcomeCard.setPadding(dp(20), dp(20), dp(20), dp(20));
        GradientDrawable welcomeBackground = new GradientDrawable();
        welcomeBackground.setColor(Color.WHITE);
        welcomeBackground.setStroke(dp(1), Color.rgb(218, 226, 240));
        welcomeBackground.setCornerRadius(dp(16));
        welcomeCard.setBackground(welcomeBackground);
        welcomeCard.setElevation(dp(2));

        TextView summary = new TextView(this);
        summary.setText("Recibe las solicitudes de pagos virtuales del POS, permite verificar Yape, Net Empresa, CPF y Bradesco, y conserva un historial de aprobaciones, pagos no realizados y cancelaciones.");
        summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        summary.setTextColor(0xFF53627A);
        summary.setGravity(android.view.Gravity.CENTER);
        summary.setLineSpacing(dp(2), 1.08f);
        welcomeCard.addView(summary, new LinearLayout.LayoutParams(-1, -2));

        ProgressBar progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        progressParams.setMargins(0, dp(24), 0, dp(12));
        welcomeCard.addView(progress, progressParams);

        TextView loadingText = new TextView(this);
        loadingText.setText("Preparando conexión segura…");
        loadingText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        loadingText.setTextColor(0xFF53627A);
        loadingText.setGravity(android.view.Gravity.CENTER);
        welcomeCard.addView(loadingText);

        Button startButton = new Button(this);
        startButton.setText("Iniciar");
        startButton.setEnabled(true);
        stylePrimary(startButton);
        LinearLayout.LayoutParams startParams = buttonParams();
        startParams.setMargins(0, dp(20), 0, 0);
        welcomeCard.addView(startButton, startParams);
        LinearLayout.LayoutParams welcomeParams = new LinearLayout.LayoutParams(-1, -2);
        welcomeParams.setMargins(0, dp(8), 0, dp(8));
        root.addView(welcomeCard, welcomeParams);
        startButton.setOnClickListener(v -> {
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                getIntent().putExtra("welcome_completed", true);
                recreate();
                return;
            }
            startButton.setEnabled(false);
            progress.setVisibility(View.VISIBLE);
            loadingText.setText("Conectando con el POS…");
            FirebaseAuth.getInstance().signInAnonymously().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    getIntent().putExtra("welcome_completed", true);
                    recreate();
                } else {
                    startButton.setEnabled(true);
                    progress.setVisibility(View.GONE);
                    loadingText.setText("No se pudo conectar. Revisa Firebase e inténtalo otra vez.");
                    Toast.makeText(this, "No fue posible iniciar la conexión segura", Toast.LENGTH_LONG).show();
                }
            });
        });
        ScrollView welcomeScroll = new ScrollView(this);
        welcomeScroll.setFillViewport(true);
        welcomeScroll.addView(root);
        setContentView(welcomeScroll);

        Runnable ready = () -> new android.os.Handler(getMainLooper()).postDelayed(() -> {
            progress.setVisibility(View.GONE);
            loadingText.setText("Aplicación lista");
            startButton.setEnabled(true);
            logo.clearAnimation();
            logo.setAlpha(1f);
        }, 900);
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            ready.run();
        } else {
            FirebaseAuth.getInstance().signInAnonymously().addOnCompleteListener(task -> {
                if (task.isSuccessful()) ready.run();
                else {
                    progress.setVisibility(View.GONE);
                    logo.clearAnimation();
                    loadingText.setText("No se pudo conectar. Activa el acceso anónimo en Firebase.");
                }
            });
        }
    }

    private String pairedDeviceId() {
        return getSharedPreferences("pos_pairing", MODE_PRIVATE).getString("device_id", "");
    }

    private void showPairingScreen() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(48), dp(24), dp(24));
        root.setBackgroundColor(0xFFF6F8FC);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            view.setPadding(dp(24), dp(28) + top, dp(24), dp(24) + bottom);
            return insets;
        });
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.app_icon);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        root.addView(logo, new LinearLayout.LayoutParams(-1, dp(92)));
        AlphaAnimation pairingPulse = new AlphaAnimation(0.55f, 1f);
        pairingPulse.setDuration(900);
        pairingPulse.setRepeatMode(Animation.REVERSE);
        pairingPulse.setRepeatCount(Animation.INFINITE);
        logo.startAnimation(pairingPulse);
        TextView title = new TextView(this);
        title.setText("Vincular con Balsa Roja POS");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 23);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(0xFF10213F);
        title.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams pairingTitleParams = new LinearLayout.LayoutParams(-1, -2);
        pairingTitleParams.setMargins(0, dp(18), 0, dp(6));
        root.addView(title, pairingTitleParams);
        TextView help = new TextView(this);
        help.setText("En el POS abre Configuración > POS > Vincular teléfono y escribe aquí el código de 6 dígitos.");
        help.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        help.setTextColor(0xFF53627A);
        help.setGravity(android.view.Gravity.CENTER);
        help.setPadding(0, dp(12), 0, dp(18));
        LinearLayout.LayoutParams helpParams = new LinearLayout.LayoutParams(-1, -2);
        helpParams.setMargins(0, dp(4), 0, dp(18));
        root.addView(help, helpParams);
        LinearLayout waitingRow = new LinearLayout(this);
        waitingRow.setOrientation(LinearLayout.HORIZONTAL);
        waitingRow.setGravity(android.view.Gravity.CENTER);
        ProgressBar waitingProgress = new ProgressBar(this);
        waitingProgress.setIndeterminate(true);
        waitingRow.addView(waitingProgress, new LinearLayout.LayoutParams(dp(28), dp(28)));
        TextView waitingText = new TextView(this);
        waitingText.setText("Esperando el código del POS…");
        waitingText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        waitingText.setTextColor(0xFF1769E0);
        waitingText.setPadding(dp(10), 0, 0, 0);
        waitingRow.addView(waitingText);
        LinearLayout.LayoutParams waitingParams = new LinearLayout.LayoutParams(-1, -2);
        waitingParams.setMargins(0, 0, 0, dp(14));
        root.addView(waitingRow, waitingParams);
        EditText codeInput = new EditText(this);
        codeInput.setHint("Código de 16 dígitos");
        codeInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        codeInput.setMaxLines(1);
        codeInput.setFilters(new android.text.InputFilter[] {
                new android.text.InputFilter.LengthFilter(16)
        });
        codeInput.setGravity(android.view.Gravity.CENTER);
        codeInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 19);
        root.addView(codeInput, new LinearLayout.LayoutParams(-1, dp(56)));
        Button pairButton = new Button(this);
        pairButton.setText("Vincular teléfono");
        stylePrimary(pairButton);
        LinearLayout.LayoutParams params = buttonParams();
        params.setMargins(0, dp(14), 0, 0);
        root.addView(pairButton, params);
        ProgressBar pairingProgress = new ProgressBar(this);
        pairingProgress.setIndeterminate(true);
        pairingProgress.setVisibility(View.GONE);
        LinearLayout.LayoutParams pairingProgressParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        pairingProgressParams.setMargins(0, dp(14), 0, 0);
        root.addView(pairingProgress, pairingProgressParams);
        pairButton.setOnClickListener(v -> {
            waitingRow.setVisibility(View.GONE);
            pairWithCode(codeInput, pairButton, pairingProgress, waitingRow);
        });
        setContentView(root);
    }

    private void pairWithCode(EditText input, Button button, ProgressBar progress, View waitingView) {
        String code = input.getText().toString().trim();
        if (!code.matches("\\d{16}")) {
            input.setError("Escribe los 16 dígitos");
            waitingView.setVisibility(View.VISIBLE);
            return;
        }
        button.setEnabled(false);
        button.setText("Vinculando…");
        progress.setVisibility(View.VISIBLE);
        DatabaseReference session = FirebaseDatabase.getInstance()
                .getReference("paymentPairingSessions").child(code);
        session.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snapshot) {
                String status = String.valueOf(snapshot.child("status").getValue());
                Long expiresAt = snapshot.child("expiresAt").getValue(Long.class);
                if (!"waiting".equals(status) || expiresAt == null || expiresAt < System.currentTimeMillis()) {
                    button.setEnabled(true);
                    button.setText("Vincular teléfono");
                    progress.setVisibility(View.GONE);
                    waitingView.setVisibility(View.VISIBLE);
                    Toast.makeText(MainActivity.this, "Código inválido o vencido", Toast.LENGTH_LONG).show();
                    return;
                }
                String deviceId = UUID.randomUUID().toString();
                String deviceUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
                String posUid = String.valueOf(snapshot.child("posUid").getValue());
                String deviceName = Build.MANUFACTURER + " " + Build.MODEL;
                long pairedAt = System.currentTimeMillis();
                Map<String, Object> update = new HashMap<>();
                String sessionPath = "paymentPairingSessions/" + code + "/";
                update.put(sessionPath + "status", "paired");
                update.put(sessionPath + "deviceId", deviceId);
                update.put(sessionPath + "deviceUid", deviceUid);
                update.put(sessionPath + "pairedAt", pairedAt);
                update.put(sessionPath + "deviceName", deviceName);
                String devicePath = "paymentDevices/" + deviceId + "/";
                update.put(devicePath + "deviceId", deviceId);
                update.put(devicePath + "deviceUid", deviceUid);
                update.put(devicePath + "posUid", posUid);
                update.put(devicePath + "deviceName", deviceName);
                update.put(devicePath + "status", "linked");
                update.put(devicePath + "pairedAt", pairedAt);
                FirebaseDatabase.getInstance().getReference().updateChildren(update).addOnSuccessListener(ignored -> {
                    getSharedPreferences("pos_pairing", MODE_PRIVATE).edit()
                            .putString("device_id", deviceId).apply();
                    Toast.makeText(MainActivity.this, "Teléfono vinculado", Toast.LENGTH_SHORT).show();
                    recreate();
                }).addOnFailureListener(error -> {
                    button.setEnabled(true);
                    button.setText("Vincular teléfono");
                    progress.setVisibility(View.GONE);
                    waitingView.setVisibility(View.VISIBLE);
                    Toast.makeText(MainActivity.this, "No se pudo vincular", Toast.LENGTH_LONG).show();
                });
            }
            @Override public void onCancelled(DatabaseError error) {
                button.setEnabled(true);
                button.setText("Vincular teléfono");
                progress.setVisibility(View.GONE);
                waitingView.setVisibility(View.VISIBLE);
                Toast.makeText(MainActivity.this, "No se pudo consultar el código", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void listenForDeviceLink() {
        String deviceId = pairedDeviceId();
        if (deviceId.isEmpty()) return;
        deviceLinkRef = FirebaseDatabase.getInstance().getReference("paymentDevices").child(deviceId);
        deviceLinkListener = new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                Object status = snapshot.child("status").getValue();
                if (status != null && "unlinked".equals(String.valueOf(status))) {
                    getSharedPreferences("pos_pairing", MODE_PRIVATE).edit().clear().apply();
                    Toast.makeText(MainActivity.this, "El teléfono fue desvinculado desde el POS", Toast.LENGTH_LONG).show();
                    recreate();
                }
            }

            @Override public void onCancelled(DatabaseError error) {
                // Un fallo temporal no debe cerrar una vinculación válida.
            }
        };
        deviceLinkRef.addValueEventListener(deviceLinkListener);
    }

    private void unlinkFromPos() {
        String deviceId = pairedDeviceId();
        if (deviceId.isEmpty()) return;
        DatabaseReference link = FirebaseDatabase.getInstance()
                .getReference("paymentDevices").child(deviceId);
        link.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    finishLocalUnlink();
                    return;
                }
                Map<String, Object> update = new HashMap<>();
                update.put("status", "unlinked");
                update.put("unlinkedAt", System.currentTimeMillis());
                update.put("unlinkedBy", "android");
                link.updateChildren(update)
                        .addOnSuccessListener(ignored -> finishLocalUnlink())
                        .addOnFailureListener(error -> Toast.makeText(
                                MainActivity.this, "No se pudo desvincular. Revisa la conexión.", Toast.LENGTH_LONG).show());
            }

            @Override public void onCancelled(DatabaseError error) {
                Toast.makeText(MainActivity.this,
                        "No se pudo desvincular. Revisa la conexión.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void finishLocalUnlink() {
        getSharedPreferences("pos_pairing", MODE_PRIVATE).edit().clear().apply();
        Toast.makeText(this, "Teléfono desvinculado", Toast.LENGTH_SHORT).show();
        recreate();
    }

    @Override
    protected void onDestroy() {
        if (deviceLinkRef != null && deviceLinkListener != null) {
            deviceLinkRef.removeEventListener(deviceLinkListener);
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(52));
        params.setMargins(0, dp(6), 0, dp(6));
        return params;
    }

    private void stylePrimary(Button button) {
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setPadding(dp(12), 0, dp(12), 0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(23, 105, 224));
        background.setCornerRadius(dp(10));
        button.setBackground(background);
    }

    private void styleOutline(Button button) {
        button.setAllCaps(false);
        button.setTextColor(Color.rgb(23, 105, 224));
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setPadding(dp(12), 0, dp(12), 0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setStroke(dp(1), Color.rgb(23, 105, 224));
        background.setCornerRadius(dp(10));
        button.setBackground(background);
    }

    private void styleCard(TextView view) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setStroke(dp(1), Color.rgb(218, 226, 240));
        background.setCornerRadius(dp(12));
        view.setBackground(background);
        view.setTextColor(Color.rgb(30, 43, 68));
    }

    private void startYapeRequestService() {
        Intent serviceIntent = new Intent(this, YapeRequestService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
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
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST
            );
        }
    }

    private void notifyPendingRequest(String requestId, String amount, String method, String currency) {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
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

    private void openAudit(String method, String requestId) {
        Intent intent = new Intent(this, AuditActivity.class);
        intent.putExtra("payment_method", method);
        if (requestId != null) intent.putExtra("focus_request_id", requestId);
        startActivity(intent);
    }

    private void listenForRequests() {
        requestsRef = FirebaseDatabase.getInstance().getReference("yapePaymentRequests")
                .orderByChild("targetDeviceUid").equalTo(FirebaseAuth.getInstance().getUid());
        requestsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                requestsContainer.removeAllViews();
                boolean hasPending = false;
                for (DataSnapshot child : snapshot.getChildren()) {
                    if (!RemotePayment.belongsToThisDevice(MainActivity.this, child)) continue;
                    String status = String.valueOf(child.child("status").getValue());
                    if (!"pending".equals(status) || hasOwnerDecision(child)) continue;
                    hasPending = true;
                    String requestId = String.valueOf(child.child("requestId").getValue());
                    String amount = String.valueOf(child.child("amount").getValue());
                    String requestedBy = String.valueOf(child.child("requestedBy").getValue());
                    String notifiedKey = "notified_" + requestId;
                    if (!getSharedPreferences("yape_notifications", MODE_PRIVATE)
                            .getBoolean(notifiedKey, false)) {
                        notifyPendingRequest(requestId, amount, RemotePayment.label(child), RemotePayment.currency(child));
                        getSharedPreferences("yape_notifications", MODE_PRIVATE)
                                .edit().putBoolean(notifiedKey, true).apply();
                    }
                    TextView row = new TextView(MainActivity.this);
                    row.setText(String.format(Locale.US, "Pago %s pendiente\nMonto: %s %s\nCódigo: %s",
                            RemotePayment.label(child), RemotePayment.currency(child), amount, requestId));
                    row.setTextSize(16);
                    row.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
                    row.setLineSpacing(dp(2), 1.08f);
                    row.setPadding(dp(16), dp(16), dp(16), dp(16));
                    styleCard(row);
                    if (!"null".equalsIgnoreCase(requestedBy) && !"-".equals(requestedBy)) {
                        row.append("\nSolicitado por: " + requestedBy);
                    }
                    row.append("\n\nToca para abrir la solicitud");
                    row.setOnClickListener(v -> openAudit(RemotePayment.method(child), requestId));
                    row.setFocusable(true);
                    requestsContainer.addView(row, new LinearLayout.LayoutParams(-1, -2));
                }
                if (!hasPending) {
                    TextView empty = new TextView(MainActivity.this);
                    empty.setText("No hay solicitudes pendientes.");
                    empty.setTextSize(16);
                    requestsContainer.addView(empty, new LinearLayout.LayoutParams(-1, -2));
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                TextView errorText = new TextView(MainActivity.this);
                errorText.setText("No se pudo consultar Firebase: " + error.getMessage());
                requestsContainer.removeAllViews();
                requestsContainer.addView(errorText, new LinearLayout.LayoutParams(-1, -2));
            }
        });
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
}
