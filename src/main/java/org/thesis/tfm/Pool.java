package org.thesis.tfm;

import org.thesis.blockchain.Block;
import org.thesis.blockchain.Data;
import org.thesis.blockchain.Miner;
import org.thesis.blockchain.Transaction;

import java.math.BigDecimal;
import java.util.ArrayList;

public class Pool extends AbstractTFM {
    private final static String type = "Reserve Pool";
    private double maxSharedTake = 1; // percentage of how much of the payout can be taken away from shared pool

    public Pool() {
        super(type);
    }

    public Pool(double mst) {
        super(type);
        this.maxSharedTake = mst;
    }


    // used for logging each tx data
    public String[] logStart(int i, String h, double f) {
        return new String[] {
                String.valueOf(i),
                h,
                String.valueOf(f),
        };
    }

    @Override
    public String[] logHeaders() {
        return new String[] {
                "Time",
                "Block Height",
                "Parent Hash",
                "Current Hash",
                "Miner ID",
                "Block Reward",
                "Size Limit",
                "Block Size",
                "Number of TX",
                "Base Fee",
                "Reserve Pool",
                "TX Index",
                "TX Hash",
                "TX Paid"
        };
    }

    // Main EIP-1559 Mechanism Implementation
    @Override
    public Data fetchValidTX(ArrayList<Transaction> m, double blockLimit, Block b, Miner miner, double target) {
        // sort current mempool by highest fee per byte price offered
        m.sort((t1, t2) -> Double.compare(t2.getByte_fee(), t1.getByte_fee()));

        double sizeUsedUp = 0; // total bytes used by current block
        ArrayList<String[]> logs = new ArrayList<String[]>(); // log data for printing later
        ArrayList<Transaction> txList = new ArrayList<Transaction>(); // list of *confirmed* transactions
        BigDecimal rewards = new BigDecimal("0"); // total rewards given to miner
        BigDecimal pool = b.getPool(); // get current pool reserves level

        // calculate base fee for this current block (max of 12.5% update in a single cycle)
        double baseFee = (b.getBaseFee()*(1.0+0.125*((b.getSize()-target)/target)));

        int index = 1;

        while(true) {
            // if mempool is not empty..
            if(!m.isEmpty()) {
                // if block is not yet filled to capacity..
                if ((sizeUsedUp + m.get(0).getSize()) < blockLimit) {
                    // if current mempool tx is capable of paying base fee..
                    if(m.get(0).getByte_fee() >= baseFee) {
                        // add to *confirmed* tx list
                        txList.add(m.get(0));

                        // update parameters
                        BigDecimal val = new BigDecimal(m.get(0).getTotal_fee());
                        rewards = rewards.add(val);
                        sizeUsedUp += m.get(0).getSize();

                        // log data
                        logs.add(logStart(index, m.get(0).getHash(), m.get(0).getTotal_fee()));

                        // remove from mempool and continue..
                        m.remove(0);
                        index++;
                    }
                    // mempool tx are sorted so as soon as one TX can't afford to pay base fee, we can leave
                    else {
                        break;
                    }
                }
                else {
                    break;
                }
            }
            else {
                break;
            }
        }

        // calculate optimal payout for the block, and how much of it can be taken from the shared pool
        BigDecimal payout = new BigDecimal(baseFee * target);
        BigDecimal maxTakeout = payout.multiply(BigDecimal.valueOf(maxSharedTake));

        // if current block payout is less than optimal payout, take rest from reserve pool if it is not empty already
        if(rewards.subtract(payout).signum() == -1 && pool.signum() == 1) {
            BigDecimal dif = payout.subtract(rewards);
            if(dif.compareTo(maxTakeout) > 0) dif = maxTakeout;

            // if pool doesn't contain enough coin to fully payout miner, take whatever is possible
            if(dif.compareTo(pool) > 0) {
                rewards = rewards.add(pool);
                pool = pool.subtract(dif);
            }
            // else just take what's needed
            else {
                pool = pool.subtract(dif);
                rewards = payout;
            }

            miner.updatePoolEffect(dif.negate());
        }
        // else add extra revenue back to pool, only award miner the optimal payout
        else {
            BigDecimal dif = rewards.subtract(payout);
            pool = pool.add(dif);
            miner.updatePoolEffect(dif);

            rewards = payout;
        }

        return new Data(m, txList, rewards, baseFee, pool, sizeUsedUp, logs);
    }
}
