package org.thesis.blockchain;

public class Transaction {
    String hash;
    long gas_used;
    long gas_price;
    long fee; // = gas_price * gas_used

    public Transaction(String h, long gu, long gp) {
        this.hash = h;
        this.gas_used = gu;
        this.gas_price = gp;
        this.fee = gas_used * gas_price;
    }

    public String getHash() {
        return hash;
    }

    public long getFee() {
        return fee;
    }

    public long getGasUsed() {
        return gas_used;
    }

    public long getGasPrice() {
        return gas_price;
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "hash='" + hash + '\'' +
                ", fee=" + fee +
                ", gas_used=" + gas_used +
                ", gas_price=" + gas_price +
                '}';
    }
}
