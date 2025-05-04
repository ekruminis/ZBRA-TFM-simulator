package ZBRA.tfm;

import ZBRA.blockchain.Block;
import ZBRA.blockchain.Data;
import ZBRA.blockchain.Miner;
import ZBRA.blockchain.Transaction;

import java.util.ArrayList;

// Abstract class for different TFM styles
public abstract class AbstractTFM {
    protected String type;

    public AbstractTFM(String t) {
        this.type = t;
    }

    public String getType() {
        return type;
    }

    abstract public String[] logHeaders();

    abstract public Data fetchValidTX(ArrayList<Transaction> m, double g, Block b, Miner miner, double target);

}
