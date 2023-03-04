package org.thesis.blockchain;

import java.math.BigDecimal;
import java.util.ArrayList;

public class Block {
    int index;
    int miner_id;
    String parent_hash;
    String current_hash;
    long gas_limit;
    long gas_used;
    long base_fee;
    long tx_number;
    BigDecimal rewards; // total rewards paid directly to miner
    BigDecimal burned; // total amount burned
    BigDecimal pool; // total of current pool

    ArrayList<Transaction> confirmed_txs;
    ArrayList<Transaction> unconfirmed_txs;

    // GENESIS block
    public Block() {
        this.index = 0;
        this.miner_id = -1;
        this.parent_hash = null;
        this.current_hash = "GENESIS";
        this.gas_limit = -1;
        this.gas_used = -1;
        this.base_fee = -1;
        this.rewards = null;
        this.confirmed_txs = null;
        this.unconfirmed_txs = null;
        this.tx_number = -1;
        this.burned = null;
        this.pool = null;
    }

    // general block
    public Block(int index, int miner_id, String parent_hash, String current_hash, long gas_limit, Data d) {
        this.index = index;
        this.miner_id = miner_id;
        this.parent_hash = parent_hash;
        this.current_hash = current_hash;
        this.gas_limit = gas_limit;

        this.gas_used = d.getGasUsed();
        this.base_fee = d.getBaseFee();
        this.rewards = d.getRewards();
        this.confirmed_txs = d.getConfirmed();
        this.unconfirmed_txs = d.getUnconfirmed();
        this.tx_number = d.getConfirmed().size() + d.getUnconfirmed().size();
        this.burned = d.getBurned();
        this.pool = d.getPool();
    }

    public BigDecimal getPool() { return pool; }

    public int getIndex() {
        return index;
    }

    public int getMinerID() {
        return miner_id;
    }

    public String getParentHash() {
        return parent_hash;
    }

    public String getCurrentHash() {
        return current_hash;
    }

    public long getGasLimit() {
        return gas_limit;
    }

    public long getGasUsed() {
        return gas_used;
    }

    public long getBaseFee() {
        return base_fee;
    }

    public BigDecimal getRewards() { return rewards; }

    public ArrayList<Transaction> getConfirmedTXs() {
        return confirmed_txs;
    }

    public ArrayList<Transaction> getUnconfirmedTXs() {
        return unconfirmed_txs;
    }

    public long getTXNumber() {
        return tx_number;
    }

    public BigDecimal getBurned() {
        return burned;
    }

    public void setBaseFee(long bf) {
        this.base_fee = bf;
    }

    public void updatePool(BigDecimal s) {
        this.pool = s;
    }

    @Override
    public String toString() {
        return "Block{" +
                "index=" + index +
                ", miner_id=" + miner_id +
                ", parent_hash='" + parent_hash + '\'' +
                ", current_hash='" + current_hash + '\'' +
                ", gas_limit=" + gas_limit +
                ", gas_used=" + gas_used +
                ", base_fee=" + base_fee +
                ", confirmed_txs=" + confirmed_txs +
                ", unconfirmed_txs=" + unconfirmed_txs +
                '}';
    }
}

