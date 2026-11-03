package com.balsaroja.yapecompanion;

import android.content.Context;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.auth.FirebaseAuth;

final class RemotePayment {
    static final String[] METHODS = {"yape", "net_empresa", "cpf", "bradesco"};
    private RemotePayment() {}

    static String method(DataSnapshot request) {
        Object raw = request.child("paymentMethod").getValue();
        String method = raw == null ? "yape" : String.valueOf(raw);
        for (String supported : METHODS) {
            if (supported.equals(method)) return supported;
        }
        return "yape";
    }

    static String label(String method) {
        switch (method) {
            case "net_empresa": return "Net Empresa";
            case "cpf": return "CPF";
            case "bradesco": return "Bradesco";
            default: return "Yape";
        }
    }

    static String label(DataSnapshot request) {
        return label(method(request));
    }

    static String currency(DataSnapshot request) {
        return "Yape".equals(label(request)) ? "S/" : "R/";
    }

    static boolean belongsToThisDevice(Context context, DataSnapshot request) {
        String local = context.getSharedPreferences("pos_pairing", Context.MODE_PRIVATE)
                .getString("device_id", "");
        Object rawTarget = request.child("targetDeviceId").getValue();
        Object rawUid = request.child("targetDeviceUid").getValue();
        String uid = FirebaseAuth.getInstance().getUid();
        return !local.isEmpty() && rawTarget != null && local.equals(String.valueOf(rawTarget))
                && uid != null && rawUid != null && uid.equals(String.valueOf(rawUid));
    }
}
