package com.bogdan3000.dintegrate.donation;

import java.util.function.Consumer;

public interface DonationProvider {
    void connect();
    void disconnect();
    boolean isConnected();
    void onDonation(Consumer<DonationEvent> handler);

    class DonationEvent {
        private final String username;
        private final float amount;
        private final String message;
        private final int id;

        public DonationEvent(String username, float amount, String message, int id) {
            this.username = username;
            this.amount = amount;
            this.message = message;
            this.id = id;
        }

        public String getUsername() { return username; }
        public float getAmount() { return amount; }
        public String getMessage() { return message; }
        public int getId() { return id; }
    }
}