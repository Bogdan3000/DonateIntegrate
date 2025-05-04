package com.bogdan3000.dintegrate.donation;

public interface DonationProvider {
    void connect();
    void disconnect();
    boolean isConnected();
    void onDonation(java.util.function.Consumer<DonationEvent> handler);

    class DonationEvent {
        private final String username;
        private final float amount;
        private final String message;
        private final int id;

        public DonationEvent(String username, float amount, String message, int id) {
            if (username == null) throw new IllegalArgumentException("Имя пользователя не может быть null");
            if (message == null) throw new IllegalArgumentException("Сообщение не может быть null");
            if (amount < 0) throw new IllegalArgumentException("Сумма не может быть отрицательной");
            this.username = username;
            this.amount = amount;
            this.message = message;
            this.id = id;
        }

        public String username() { return username; }
        public float amount() { return amount; }
        public String message() { return message; }
        public int id() { return id; }
    }
}