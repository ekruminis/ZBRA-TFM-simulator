package org.thesis.blockchain;

import java.math.BigDecimal;

public class Miner {
    int stake;
    int nodeID;
    BigDecimal winnings; // total rewards earned by miner

    public Miner(int id, int s) {
        this.nodeID = id;
        this.stake = s;
        this.winnings = new BigDecimal("0");
    }

    public int getStake() {
        return stake;
    }

    public int getID() {
        return nodeID;
    }

    public void updateWinnings(BigDecimal w) {
        this.winnings = winnings.add(w);
    }

    public BigDecimal getRewards() {
        return winnings;
    }
}
