package org.thesis.blockchain;

public class Transaction {
    String hash;
    double size; // in bytes
    double byte_fee; // fee per byte
    double total_fee; // total offered in fees

    public Transaction(String h, double s, double f) {
        this.hash = h;
        this.size = s;
        this.total_fee = f;
        this.byte_fee = total_fee / size;
    }

    public String getHash() {
        return hash;
    }

    public double getSize() {
        return size;
    }

    public double getByte_fee() {
        return byte_fee;
    }

    public double getTotal_fee() {
        return total_fee;
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "hash='" + hash + '\'' +
                ", size=" + size +
                ", byte_fee=" + byte_fee +
                ", total_fee=" + total_fee +
                '}';
    }
}
