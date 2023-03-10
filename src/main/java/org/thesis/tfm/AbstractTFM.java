package org.thesis.tfm;

import org.thesis.blockchain.Block;
import org.thesis.blockchain.Data;
import org.thesis.blockchain.Miner;
import org.thesis.blockchain.Transaction;

import java.util.ArrayList;

public abstract class AbstractTFM {
    protected String type;

    public AbstractTFM(String t) {
        this.type = t;
    }

    public String getType() {
        return type;
    }

    abstract public String[] logHeaders();

    abstract public Data fetchValidTX(ArrayList<Transaction> m, long g, Block b, Miner miner);

}
