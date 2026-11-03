package com.balsaroja.yapecompanion;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.graphics.Typeface;
import android.content.res.ColorStateList;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.auth.FirebaseAuth;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public class AuditActivity extends Activity {
    private final List<DataSnapshot> requests = new ArrayList<>();
    private LinearLayout content;
    private ScrollView scrollView;
    private String focusRequestId;
    private String paymentMethod;
    private TextView pageTitle;
    private String statusFilter = "all";
    private boolean currentWeekOnly;
    private LocalDate selectedDate;
    private LocalTime selectedTime;
    private Button dateFilterButton;
    private Button timeFilterButton;
    private Spinner sortSpinner;
    private Spinner userSpinner;
    private String sortMode = "recent";
    private String selectedUser = "Todos los usuarios";
    private final List<Button> filterButtons = new ArrayList<>();
    private final DateTimeFormatter dateFormat =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        focusRequestId = getIntent().getStringExtra("focus_request_id");
        paymentMethod = getIntent().getStringExtra("payment_method");
        setTitle("Auditoría de pagos virtuales");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        root.setBackgroundColor(0xFFF6F8FC);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            view.setPadding(dp(16), dp(16) + top, dp(16), dp(24) + bottom);
            return insets;
        });

        TextView title = new TextView(this);
        pageTitle = title;
        title.setText("Auditoría de pagos virtuales");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(Color.rgb(16, 33, 63));
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        Button backButton = new Button(this);
        backButton.setText("← Medios de pago");
        styleDateFilter(backButton);
        backButton.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(-1, dp(42));
        backParams.setMargins(0, dp(8), 0, dp(4));
        root.addView(backButton, backParams);
        updatePageTitle();

        TextView description = new TextView(this);
        description.setText("Revisa quién aprobó o canceló cada solicitud, con fecha y hora.");
        description.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        description.setTextColor(Color.rgb(83, 98, 122));
        description.setPadding(0, dp(8), 0, dp(16));
        root.addView(description, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.VERTICAL);
        filters.setPadding(dp(10), dp(10), dp(10), dp(10));
        styleSection(filters);
        filters.addView(sectionLabel("Estado de la solicitud"));
        LinearLayout firstFilterRow = new LinearLayout(this);
        firstFilterRow.setOrientation(LinearLayout.HORIZONTAL);
        addFilter(firstFilterRow, "Todas", "all", false);
        addFilter(firstFilterRow, "Aprobadas", "confirmed", false);
        filters.addView(firstFilterRow, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout secondFilterRow = new LinearLayout(this);
        secondFilterRow.setOrientation(LinearLayout.HORIZONTAL);
        addFilter(secondFilterRow, "Canceladas", "cancelled", false);
        addFilter(secondFilterRow, "No realizados", "owner_not_received", false);
        filters.addView(secondFilterRow, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout thirdFilterRow = new LinearLayout(this);
        thirdFilterRow.setOrientation(LinearLayout.HORIZONTAL);
        addFilter(thirdFilterRow, "Esta semana", "all", true);
        filters.addView(thirdFilterRow, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams filtersParams = new LinearLayout.LayoutParams(-1, -2);
        filtersParams.setMargins(0, dp(4), 0, dp(12));
        root.addView(filters, filtersParams);
        updateFilterStyles();

        LinearLayout dateFilters = new LinearLayout(this);
        dateFilters.setOrientation(LinearLayout.VERTICAL);
        dateFilters.setPadding(dp(10), dp(10), dp(10), dp(10));
        styleSection(dateFilters);
        dateFilters.addView(sectionLabel("Fecha y hora"));
        LinearLayout dateTimeRow = new LinearLayout(this);
        dateTimeRow.setOrientation(LinearLayout.HORIZONTAL);
        dateFilterButton = new Button(this);
        dateFilterButton.setText("Elegir fecha");
        styleDateFilter(dateFilterButton);
        dateFilterButton.setOnClickListener(v -> openDatePicker());
        timeFilterButton = new Button(this);
        timeFilterButton.setText("Elegir hora");
        styleDateFilter(timeFilterButton);
        timeFilterButton.setOnClickListener(v -> openTimePicker());
        Button clearDateButton = new Button(this);
        clearDateButton.setText("Limpiar");
        styleDateFilter(clearDateButton);
        clearDateButton.setOnClickListener(v -> {
            selectedDate = null;
            selectedTime = null;
            updateDateFilterLabels();
            render();
        });
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(0, dp(44), 1);
        dateParams.setMargins(dp(3), dp(4), dp(3), dp(4));
        dateTimeRow.addView(dateFilterButton, dateParams);
        dateTimeRow.addView(timeFilterButton, dateParams);
        dateFilters.addView(dateTimeRow, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(-1, dp(42));
        clearParams.setMargins(dp(3), dp(4), dp(3), dp(2));
        dateFilters.addView(clearDateButton, clearParams);
        LinearLayout.LayoutParams dateGroupParams = new LinearLayout.LayoutParams(-1, -2);
        dateGroupParams.setMargins(0, 0, 0, dp(12));
        root.addView(dateFilters, dateGroupParams);
        updateDateFilterLabels();

        TextView organizationLabel = new TextView(this);
        organizationLabel.setText("Ordenar y filtrar registros");
        organizationLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        organizationLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        organizationLabel.setTextColor(Color.rgb(16, 33, 63));
        organizationLabel.setPadding(0, 0, 0, dp(8));

        LinearLayout organizationRow = new LinearLayout(this);
        organizationRow.setOrientation(LinearLayout.VERTICAL);
        organizationRow.setPadding(dp(12), dp(12), dp(12), dp(12));
        styleSection(organizationRow);
        organizationRow.addView(organizationLabel, new LinearLayout.LayoutParams(-1, -2));
        sortSpinner = createSpinner();
        String[] sortOptions = {"Más recientes", "Usuario A-Z", "Hora reciente", "Monto mayor"};
        setSpinnerItems(sortSpinner, sortOptions);
        sortSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                sortMode = position == 1 ? "user" : position == 2 ? "hour" : position == 3 ? "amount" : "recent";
                render();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        userSpinner = createSpinner();
        setSpinnerItems(userSpinner, new String[]{selectedUser});
        userSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                selectedUser = String.valueOf(parent.getItemAtPosition(position));
                render();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        organizationRow.addView(fieldLabel("Orden de los registros"));
        LinearLayout.LayoutParams spinnerParams = new LinearLayout.LayoutParams(-1, dp(48));
        spinnerParams.setMargins(0, dp(4), 0, dp(10));
        organizationRow.addView(sortSpinner, spinnerParams);
        organizationRow.addView(fieldLabel("Usuario que solicitó el pago"));
        organizationRow.addView(userSpinner, spinnerParams);
        LinearLayout.LayoutParams organizationParams = new LinearLayout.LayoutParams(-1, -2);
        organizationParams.setMargins(0, 0, 0, dp(16));
        root.addView(organizationRow, organizationParams);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content, new LinearLayout.LayoutParams(-1, -2));
        scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.addView(root);
        setContentView(scrollView);

        FirebaseDatabase.getInstance().getReference("yapePaymentRequests")
                .orderByChild("targetDeviceUid").equalTo(FirebaseAuth.getInstance().getUid())
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot snapshot) {
                        requests.clear();
                        for (DataSnapshot child : snapshot.getChildren()) {
                            if (RemotePayment.belongsToThisDevice(AuditActivity.this, child)) requests.add(child);
                        }
                        if (focusRequestId != null) {
                            for (DataSnapshot request : requests) {
                                if (focusRequestId.equals(value(request, "requestId"))) {
                                    paymentMethod = RemotePayment.method(request);
                                    break;
                                }
                            }
                        }
                        updatePageTitle();
                        updateUserFilterOptions();
                        render();
                    }

                    @Override
                    public void onCancelled(DatabaseError error) {
                        content.removeAllViews();
                        TextView message = new TextView(AuditActivity.this);
                        message.setText("No se pudo consultar la auditoría: " + error.getMessage());
                        content.addView(message);
                    }
                });
    }

    private void updatePageTitle() {
        String title = paymentMethod == null
                ? "Auditoría de pagos virtuales"
                : "Pagos " + RemotePayment.label(paymentMethod);
        setTitle(title);
        if (pageTitle != null) pageTitle.setText(title);
    }

    private void addFilter(LinearLayout container, String label, String status, boolean week) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        button.setTag(label);
        filterButtons.add(button);
        button.setOnClickListener(v -> {
            statusFilter = status;
            currentWeekOnly = week;
            updateFilterStyles();
            render();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        container.addView(button, params);
    }

    private TextView sectionLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setTextColor(Color.rgb(16, 33, 63));
        label.setPadding(dp(4), 0, dp(4), dp(6));
        return label;
    }

    private TextView fieldLabel(String text) {
        TextView label = new TextView(this);
        label.setText(text);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setTextColor(Color.rgb(83, 98, 122));
        label.setPadding(dp(2), dp(2), 0, 0);
        return label;
    }

    private void styleSection(LinearLayout section) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setStroke(dp(1), Color.rgb(218, 226, 240));
        background.setCornerRadius(dp(14));
        section.setBackground(background);
        section.setElevation(dp(1));
    }

    private void updateFilterStyles() {
        for (Button button : filterButtons) {
            String label = String.valueOf(button.getTag());
            boolean active = ("Esta semana".equals(label) && currentWeekOnly)
                    || (!"Esta semana".equals(label) && !currentWeekOnly
                    && (("Todas".equals(label) && "all".equals(statusFilter))
                    || ("Aprobadas".equals(label) && "confirmed".equals(statusFilter))
                    || ("Canceladas".equals(label) && "cancelled".equals(statusFilter))
                    || ("No realizados".equals(label) && "owner_not_received".equals(statusFilter))));
            button.setText(label);
            styleFilter(button, active);
        }
    }

    private void styleDateFilter(Button button) {
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        int posBlue = Color.rgb(23, 105, 224);
        button.setTextColor(new ColorStateList(new int[][]{new int[]{}}, new int[]{posBlue}));
        button.setBackgroundTintList(null);
        button.setStateListAnimator(null);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setStroke(dp(1), posBlue);
        background.setCornerRadius(dp(9));
        button.setBackground(background);
        button.setPadding(dp(4), 0, dp(4), 0);
    }

    private void updateDateFilterLabels() {
        if (dateFilterButton != null) {
            dateFilterButton.setText(selectedDate == null
                    ? "Elegir fecha"
                    : selectedDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        }
        if (timeFilterButton != null) {
            timeFilterButton.setText(selectedTime == null
                    ? "Elegir hora"
                    : selectedTime.format(DateTimeFormatter.ofPattern("HH:mm")));
        }
    }

    private void openDatePicker() {
        LocalDate initial = selectedDate == null ? LocalDate.now() : selectedDate;
        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, day) -> {
                    selectedDate = LocalDate.of(year, month + 1, day);
                    updateDateFilterLabels();
                    render();
                },
                initial.getYear(), initial.getMonthValue() - 1, initial.getDayOfMonth());
        dialog.show();
    }

    private void openTimePicker() {
        LocalTime initial = selectedTime == null ? LocalTime.now() : selectedTime;
        TimePickerDialog dialog = new TimePickerDialog(
                this,
                (view, hour, minute) -> {
                    selectedTime = LocalTime.of(hour, minute);
                    updateDateFilterLabels();
                    render();
                },
                initial.getHour(), initial.getMinute(), true);
        dialog.show();
    }

    private Spinner createSpinner() {
        Spinner spinner = new Spinner(this);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setStroke(dp(1), Color.rgb(23, 105, 224));
        background.setCornerRadius(dp(9));
        spinner.setBackground(background);
        return spinner;
    }

    private void setSpinnerItems(Spinner spinner, String[] items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_spinner_item, items) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView view = (TextView) super.getView(position, convertView, parent);
                view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                view.setTextColor(Color.rgb(23, 105, 224));
                view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                view.setPadding(dp(10), 0, dp(6), 0);
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void updateUserFilterOptions() {
        if (userSpinner == null) return;
        List<String> users = new ArrayList<>();
        users.add("Todos los usuarios");
        for (DataSnapshot request : requests) {
            if (paymentMethod != null && !paymentMethod.equals(RemotePayment.method(request))) continue;
            String user = value(request, "requestedBy");
            if (!"-".equals(user) && !"null".equalsIgnoreCase(user) && !users.contains(user)) {
                users.add(user);
            }
        }
        String[] options = users.toArray(new String[0]);
        setSpinnerItems(userSpinner, options);
        int selectedIndex = users.indexOf(selectedUser);
        userSpinner.setSelection(selectedIndex >= 0 ? selectedIndex : 0);
        if (selectedIndex < 0) selectedUser = "Todos los usuarios";
    }

    private List<DataSnapshot> getOrderedRequests() {
        List<DataSnapshot> ordered = new ArrayList<>();
        for (DataSnapshot request : requests) {
            if (paymentMethod != null && !paymentMethod.equals(RemotePayment.method(request))) continue;
            String requestedBy = value(request, "requestedBy");
            if (!isFocused(request) && !"Todos los usuarios".equals(selectedUser)
                    && !selectedUser.equals(requestedBy)) continue;
            ordered.add(request);
        }
        ordered.sort((first, second) -> {
            if ("user".equals(sortMode)) {
                return value(first, "requestedBy").compareToIgnoreCase(value(second, "requestedBy"));
            }
            if ("amount".equals(sortMode)) {
                return Double.compare(amount(second), amount(first));
            }
            return eventInstant(second).compareTo(eventInstant(first));
        });
        return ordered;
    }

    private double amount(DataSnapshot request) {
        try {
            return Double.parseDouble(value(request, "amount").replace(',', '.'));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private void render() {
        if (content == null) return;
        content.removeAllViews();
        List<DataSnapshot> orderedRequests = getOrderedRequests();
        int pendingCount = 0;
        for (DataSnapshot request : orderedRequests) {
            if (!"pending".equals(value(request, "status"))
                    || hasOwnerDecision(request)) continue;
            pendingCount++;
            TextView pending = new TextView(this);
            pending.setText(String.format(Locale.US,
                    "SOLICITUD %s PENDIENTE\nMonto: %s %s\nCódigo: %s",
                    RemotePayment.label(request), RemotePayment.currency(request),
                    value(request, "amount"), value(request, "requestId")));
            pending.setTextSize(15);
            pending.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
            pending.setLineSpacing(dp(2), 1.08f);
            pending.append("\nSolicitada: " + eventDate(request, "pending"));
            if (Boolean.TRUE.equals(request.child("phoneNotification").child("detected").getValue(Boolean.class))) {
                pending.append("\nAviso de posible pago detectado en el teléfono. Verifica el movimiento antes de responder.");
            }
            String requestedBy = value(request, "requestedBy");
            if (!"-".equals(requestedBy) && !"null".equalsIgnoreCase(requestedBy)) {
                pending.append("\nSolicitada por: " + requestedBy);
            }
            pending.setPadding(dp(16), dp(16), dp(16), dp(8));
            styleCard(pending);
            LinearLayout.LayoutParams pendingParams = new LinearLayout.LayoutParams(-1, -2);
            pendingParams.setMargins(0, dp(6), 0, 0);
            content.addView(pending, pendingParams);
            if (focusRequestId != null && focusRequestId.equals(value(request, "requestId"))) {
                pending.post(() -> scrollView.smoothScrollTo(0, Math.max(0, pending.getTop() - dp(16))));
            }

            String ownerDecision = value(request, "ownerDecision");
            if (!hasOwnerDecision(request)) {
                LinearLayout actions = new LinearLayout(this);
                actions.setOrientation(LinearLayout.HORIZONTAL);
                Button received = new Button(this);
                received.setText("Pago recibido");
                styleSuccess(received);
                received.setOnClickListener(v -> setOwnerDecision(request, "approved"));
                Button notReceived = new Button(this);
                notReceived.setText("No se realizó");
                styleDanger(notReceived);
                notReceived.setOnClickListener(v -> setOwnerDecision(request, "not_received"));
                LinearLayout.LayoutParams receivedParams = new LinearLayout.LayoutParams(0, dp(44), 1);
                receivedParams.setMargins(0, dp(4), dp(5), dp(8));
                LinearLayout.LayoutParams notReceivedParams = new LinearLayout.LayoutParams(0, dp(44), 1);
                notReceivedParams.setMargins(dp(5), dp(4), 0, dp(8));
                actions.addView(received, receivedParams);
                actions.addView(notReceived, notReceivedParams);
                content.addView(actions, new LinearLayout.LayoutParams(-1, -2));
            } else {
                TextView decision = new TextView(this);
                decision.setText("approved".equals(ownerDecision)
                        ? "Respuesta registrada: pago recibido"
                        : "Respuesta registrada: pago no realizado");
                decision.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                decision.setTextColor("approved".equals(ownerDecision)
                        ? Color.rgb(16, 145, 92)
                        : Color.rgb(210, 55, 55));
                decision.setPadding(dp(16), dp(8), dp(16), dp(12));
                content.addView(decision, new LinearLayout.LayoutParams(-1, -2));
            }
        }

        TextView historyTitle = new TextView(this);
        historyTitle.setText(pendingCount > 0 ? "Historial auditado" : "Historial auditado");
        historyTitle.setTextSize(18);
        historyTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        historyTitle.setTextColor(Color.rgb(16, 33, 63));
        historyTitle.setPadding(0, 20, 0, 8);
        content.addView(historyTitle, new LinearLayout.LayoutParams(-1, -2));

        int count = 0;
        for (DataSnapshot request : orderedRequests) {
            String status = auditStatus(request);
            if (status == null) continue;
            boolean approved = "confirmed".equals(status) || "owner_approved".equals(status);
            if (!isFocused(request)) {
                if ("confirmed".equals(statusFilter) && !approved) continue;
                if ("cancelled".equals(statusFilter) && !"cancelled".equals(status)) continue;
                if ("owner_not_received".equals(statusFilter)
                        && !"owner_not_received".equals(status)) continue;
                if (currentWeekOnly && !isCurrentWeek(request, status)) continue;
                if (!matchesSelectedDateTime(request, status)) continue;
            }

            String action = approved
                    ? ("confirmed".equals(status) ? "APROBADA Y REGISTRADA" : "PAGO APROBADO")
                    : ("cancelled".equals(status) ? "CANCELADA" : "PAGO NO REALIZADO");
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(14), dp(14), dp(14));
            styleHistoryCard(card);

            TextView statusLabel = new TextView(this);
            statusLabel.setText(action);
            statusLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            statusLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            styleStatusLabel(statusLabel, status);
            card.addView(statusLabel, new LinearLayout.LayoutParams(-2, -2));

            TextView row = new TextView(this);
            row.setText(String.format(Locale.US,
                    "%s\nMonto: %s %s\nFecha y hora: %s\nCódigo: %s",
                    action,
                    RemotePayment.currency(request),
                    value(request, "amount"),
                    eventDate(request, status),
                    value(request, "requestId")));
            String rowText = row.getText().toString();
            int firstBreak = rowText.indexOf('\n');
            if (firstBreak >= 0) row.setText(rowText.substring(firstBreak + 1));
            row.append("\nMedio: " + RemotePayment.label(request));
            row.setTextSize(15);
            row.setLineSpacing(dp(2), 1.08f);
            row.setPadding(dp(16), dp(16), dp(16), dp(16));
            String requestedBy = value(request, "requestedBy");
            if (!"-".equals(requestedBy) && !"null".equalsIgnoreCase(requestedBy)) {
                row.append("\nSolicitada por: " + requestedBy);
            }
            row.setTextColor(Color.BLACK);
            applyHistoryColors(row, status);
            card.addView(row, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.setMargins(0, dp(6), 0, dp(6));
            content.addView(card, rowParams);
            if (focusRequestId != null && focusRequestId.equals(value(request, "requestId"))) {
                card.post(() -> scrollView.smoothScrollTo(0, Math.max(0, card.getTop() - dp(16))));
            }
            count++;
        }
        if (count == 0) {
            TextView empty = new TextView(this);
            empty.setText("No hay registros para este filtro.");
            empty.setTextSize(15);
            empty.setPadding(0, 20, 0, 20);
            content.addView(empty, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private String auditStatus(DataSnapshot request) {
        String status = value(request, "status");
        String decision = value(request, "ownerDecision");
        // La decisión de pago no realizado conserva su categoría aunque el POS
        // cierre la solicitud anterior para permitir un nuevo intento.
        if ("not_received".equals(decision)) return "owner_not_received";
        if ("confirmed".equals(status) || "cancelled".equals(status)) return status;
        if ("approved".equals(decision)) return "owner_approved";
        return null;
    }

    private boolean isFocused(DataSnapshot request) {
        return focusRequestId != null && focusRequestId.equals(value(request, "requestId"));
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

    private void setOwnerDecision(DataSnapshot request, String decision) {
        Map<String, Object> values = new HashMap<>();
        values.put("ownerDecision", decision);
        values.put("ownerDecisionAt", java.time.Instant.now().toString());
        request.getRef().updateChildren(values)
                .addOnSuccessListener(ignored -> Toast.makeText(
                        this,
                        "approved".equals(decision)
                                ? "Pago recibido registrado"
                                : "Pago no realizado registrado",
                        Toast.LENGTH_SHORT).show())
                .addOnFailureListener(error -> Toast.makeText(
                        this,
                        "No se pudo guardar la respuesta",
                        Toast.LENGTH_SHORT).show());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void styleFilter(Button button, boolean active) {
        button.setAllCaps(false);
        button.setTextColor(active ? Color.WHITE : Color.rgb(23, 105, 224));
        GradientDrawable background = new GradientDrawable();
        background.setColor(active ? Color.rgb(23, 105, 224) : Color.WHITE);
        background.setStroke(dp(1), Color.rgb(23, 105, 224));
        background.setCornerRadius(dp(10));
        button.setBackground(background);
        button.setPadding(dp(4), 0, dp(4), 0);
    }

    private void styleCard(TextView view) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setStroke(dp(1), Color.rgb(218, 226, 240));
        background.setCornerRadius(dp(12));
        view.setBackground(background);
        view.setTextColor(Color.rgb(30, 43, 68));
    }

    private void styleHistoryCard(View view) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setStroke(dp(1), Color.rgb(218, 226, 240));
        background.setCornerRadius(dp(12));
        view.setBackground(background);
    }

    private void styleStatusLabel(TextView view, String status) {
        int fill = Color.rgb(16, 145, 92);
        int stroke = Color.rgb(8, 96, 60);
        int text = Color.WHITE;
        if ("owner_not_received".equals(status)) {
            fill = Color.rgb(210, 55, 55);
            stroke = Color.rgb(150, 28, 28);
            text = Color.WHITE;
        } else if ("cancelled".equals(status)) {
            fill = Color.rgb(224, 139, 28);
            stroke = Color.rgb(170, 99, 12);
            text = Color.WHITE;
        }
        GradientDrawable background = new GradientDrawable();
        background.setColor(fill);
        background.setStroke(dp(1), stroke);
        background.setCornerRadius(dp(8));
        view.setBackground(background);
        view.setTextColor(text);
        view.setPadding(dp(10), dp(7), dp(10), dp(7));
    }

    private void applyHistoryColors(TextView view, String status) {
        SpannableString styled = new SpannableString(view.getText());
        String text = styled.toString();
        view.setTextColor(Color.BLACK);
        int amountStart = text.indexOf("Monto:");
        int dateStart = text.indexOf("Fecha y hora:");
        int codeStart = text.indexOf("igo:");
        int amountValueStart = text.indexOf(": ", amountStart) + 2;
        int dateValueStart = text.indexOf(": ", dateStart) + 2;
        int codeValueStart = text.indexOf(": ", codeStart) + 2;
        int dateEnd = codeStart > dateStart ? text.lastIndexOf('\n', codeStart) : text.length();
        int codeEnd = text.indexOf('\n', codeStart);
        if (codeEnd < 0) codeEnd = text.length();
        if (amountValueStart >= 0 && dateStart > amountValueStart) {
            styled.setSpan(new ForegroundColorSpan(Color.rgb(16, 145, 92)), amountValueStart, dateStart, 0);
        }
        if (dateValueStart > 1 && dateEnd > dateValueStart) {
            styled.setSpan(new ForegroundColorSpan(Color.rgb(23, 105, 224)), dateValueStart, dateEnd, 0);
        }
        if (codeValueStart > 1 && codeEnd > codeValueStart) {
            styled.setSpan(new ForegroundColorSpan(Color.rgb(126, 87, 194)), codeValueStart, codeEnd, 0);
        }
        view.setText(styled);
    }

    private void styleSuccess(Button button) {
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(Color.WHITE);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(16, 145, 92));
        background.setCornerRadius(dp(10));
        button.setBackground(background);
    }

    private void styleDanger(Button button) {
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(Color.WHITE);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(210, 55, 55));
        background.setCornerRadius(dp(10));
        button.setBackground(background);
    }

    private String value(DataSnapshot request, String key) {
        Object value = request.child(key).getValue();
        return value == null ? "-" : String.valueOf(value);
    }

    private String eventDate(DataSnapshot request, String status) {
        String raw = eventTimestamp(request, status);
        if ("-".equals(raw)) raw = value(request, "createdAt");
        try {
            return dateFormat.format(Instant.parse(raw));
        } catch (DateTimeParseException ignored) {
            return raw;
        }
    }

    private String eventTimestamp(DataSnapshot request, String status) {
        String key = "pending".equals(status) ? "createdAt" : "confirmed".equals(status)
                ? "confirmedAt"
                : "cancelled".equals(status) ? "cancelledAt" : "ownerDecisionAt";
        return value(request, key);
    }

    private Instant eventInstant(DataSnapshot request) {
        String status = auditStatus(request);
        String raw = eventTimestamp(request, status == null ? "pending" : status);
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException ignored) {
            return Instant.MIN;
        }
    }

    private boolean matchesSelectedDateTime(DataSnapshot request, String status) {
        if (selectedDate == null && selectedTime == null) return true;
        String raw = eventTimestamp(request, status);
        try {
            java.time.ZonedDateTime event = Instant.parse(raw).atZone(ZoneId.systemDefault());
            if (selectedDate != null && !selectedDate.equals(event.toLocalDate())) return false;
            return selectedTime == null
                    || (selectedTime.getHour() == event.getHour()
                    && selectedTime.getMinute() == event.getMinute());
        } catch (DateTimeParseException ignored) {
            return false;
        }
    }

    private boolean isCurrentWeek(DataSnapshot request, String status) {
        String key = "pending".equals(status) ? "createdAt" : "confirmed".equals(status)
                ? "confirmedAt"
                : "cancelled".equals(status) ? "cancelledAt" : "ownerDecisionAt";
        String raw = value(request, key);
        try {
            LocalDate date = Instant.parse(raw).atZone(ZoneId.systemDefault()).toLocalDate();
            LocalDate monday = LocalDate.now().with(DayOfWeek.MONDAY);
            return !date.isBefore(monday) && date.isBefore(monday.plusDays(7));
        } catch (DateTimeParseException ignored) {
            return false;
        }
    }
}
