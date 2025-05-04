package ZBRA.blockchain;

import java.math.BigDecimal;

public class Miner {
    int stake;
    int nodeID;
    BigDecimal winnings; // total rewards earned by miner
    BigDecimal privatePool; // totals of private pool (for 'pool' TFM)
    BigDecimal poolEffect; // positive/negative change on shared pool
    // double malice;

    public Miner(int id, int s) {
        this.nodeID = id;
        this.stake = s;
        this.winnings = new BigDecimal("0");
        this.privatePool = new BigDecimal("0");
        this.poolEffect = new BigDecimal("0");
    }

    public Miner(int id, int s, BigDecimal pp, BigDecimal cp) {
        this.nodeID = id;
        this.stake = s;
        this.winnings = new BigDecimal("0");
        this.privatePool = pp;
        this.poolEffect = cp;
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

    public BigDecimal getPrivatePool() {
        return privatePool;
    }

    public BigDecimal getPoolEffect() {
        return poolEffect;
    }

    public void updatePoolEffect(BigDecimal pe) { this.poolEffect = poolEffect.add(pe); }

    public void setPoolEffect(BigDecimal pe) {
        this.poolEffect = pe;
    }
}
