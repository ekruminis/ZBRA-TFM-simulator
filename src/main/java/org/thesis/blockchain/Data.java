package org.thesis.blockchain;

import java.math.BigDecimal;
import java.util.ArrayList;

public class Data {
    ArrayList<Transaction> mempool;
    ArrayList<Transaction> confirmed;
    ArrayList<Transaction> unconfirmed = new ArrayList<>();
    double size;
    double baseFee;
    BigDecimal sharedPool = new BigDecimal(0);
    BigDecimal rewards = new BigDecimal(0);
    BigDecimal burned = new BigDecimal(0);
    ArrayList<String[]> logs;

    public ArrayList<Transaction> getUnconfirmed() {
        return unconfirmed;
    }

    public ArrayList<Transaction> getMempool() {
        return mempool;
    }

    public ArrayList<Transaction> getConfirmed() {
        return confirmed;
    }

    public double getSize() {
        return size;
    }

    public ArrayList<String[]> getLogs() {
        return logs;
    }

    public BigDecimal getRewards() { return rewards; }

    public BigDecimal getBurned() { return burned; }

    public double getBaseFee() { return baseFee; }

    public BigDecimal getPool() { return sharedPool; }

    // First-Price Data Style
    public Data(ArrayList<Transaction> m, ArrayList<Transaction> c, BigDecimal r, double gu, ArrayList<String[]> l) {
        this.mempool = m;
        this.confirmed = c;

        this.size = gu;
        this.rewards = r;
        this.baseFee = -1;

        this.logs = l;
    }

    // Second-Price Data Style
    public Data(ArrayList<Transaction> m, ArrayList<Transaction> c, BigDecimal r, double f, double gu, ArrayList<String[]> l) {
        this.mempool = m;
        this.confirmed = c;

        this.size = gu;
        this.rewards = r;
        this.baseFee = f;

        this.logs = l;
    }

    // EIP-1559 Data Style
    public Data(ArrayList<Transaction> m, ArrayList<Transaction> c, BigDecimal r, BigDecimal b, double f, double gu, ArrayList<String[]> l) {
        this.mempool = m;
        this.confirmed = c;

        this.size = gu;
        this.rewards = r;
        this.burned = b;
        this.baseFee = f;

        this.logs = l;
    }

    // Pool Data Style
    public Data(ArrayList<Transaction> m, ArrayList<Transaction> c, BigDecimal r, double f, BigDecimal s, double gu, ArrayList<String[]> l) {
        this.mempool = m;
        this.confirmed = c;

        this.size = gu;
        this.rewards = r;
        this.sharedPool = s;
        this.baseFee = f;

        this.logs = l;
    }

    // Burning 2nd Price Data Style
    public Data(ArrayList<Transaction> m, ArrayList<Transaction> c, ArrayList<Transaction> uc, BigDecimal r, double f, BigDecimal b, double gu, ArrayList<String[]> l) {
        this.mempool = m;
        this.confirmed = c;
        this.unconfirmed = uc;

        this.size = gu;
        this.rewards = r;
        this.burned = b;
        this.baseFee = f;

        this.logs = l;
    }
}
