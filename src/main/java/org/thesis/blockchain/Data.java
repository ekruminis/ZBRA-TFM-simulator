package org.thesis.blockchain;

import java.math.BigDecimal;
import java.util.ArrayList;

public class Data {
    ArrayList<Transaction> mempool;
    ArrayList<Transaction> confirmed;
    ArrayList<Transaction> unconfirmed = new ArrayList<>();
    long gas_used;
    long baseFee;
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

    public long getGasUsed() {
        return gas_used;
    }

    public ArrayList<String[]> getLogs() {
        return logs;
    }

    public BigDecimal getRewards() { return rewards; }

    public BigDecimal getBurned() { return burned; }

    public long getBaseFee() { return baseFee; }

    public BigDecimal getPool() { return sharedPool; }

    // First-Price Data Style
    public Data(ArrayList<Transaction> m, ArrayList<Transaction> c, BigDecimal r, long gu, ArrayList<String[]> l) {
        this.mempool = m;
        this.confirmed = c;

        this.gas_used = gu;
        this.rewards = r;
        this.baseFee = -1;

        this.logs = l;
    }

    // Second-Price Data Style
    public Data(ArrayList<Transaction> m, ArrayList<Transaction> c, BigDecimal r, long f, long gu, ArrayList<String[]> l) {
        this.mempool = m;
        this.confirmed = c;

        this.gas_used = gu;
        this.rewards = r;
        this.baseFee = f;

        this.logs = l;
    }

    // EIP-1559 Data Style
    public Data(ArrayList<Transaction> m, ArrayList<Transaction> c, BigDecimal r, BigDecimal b, long f, long gu, ArrayList<String[]> l) {
        this.mempool = m;
        this.confirmed = c;

        this.gas_used = gu;
        this.rewards = r;
        this.burned = b;
        this.baseFee = f;

        this.logs = l;
    }

    // Pool Data Style
    public Data(ArrayList<Transaction> m, ArrayList<Transaction> c, BigDecimal r, long f, BigDecimal s, long gu, ArrayList<String[]> l) {
        this.mempool = m;
        this.confirmed = c;

        this.gas_used = gu;
        this.rewards = r;
        this.sharedPool = s;
        this.baseFee = f;

        this.logs = l;
    }

    // Burning 2nd Price Data Style
    public Data(ArrayList<Transaction> m, ArrayList<Transaction> c, ArrayList<Transaction> uc, BigDecimal r, long f, BigDecimal b, long gu, ArrayList<String[]> l) {
        this.mempool = m;
        this.confirmed = c;
        this.unconfirmed = uc;

        this.gas_used = gu;
        this.rewards = r;
        this.burned = b;
        this.baseFee = f;

        this.logs = l;
    }
}
