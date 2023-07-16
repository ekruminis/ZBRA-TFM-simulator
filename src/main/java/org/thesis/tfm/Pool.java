package org.thesis.tfm;

import org.thesis.blockchain.Block;
import org.thesis.blockchain.Data;
import org.thesis.blockchain.Miner;
import org.thesis.blockchain.Transaction;

import java.math.BigDecimal;
import java.util.ArrayList;

public class Pool extends AbstractTFM {
    private final static String type = "Reserve Pool";
    private double maxSharedTake = 0.66; // percentage of how much of the payout can be taken away from shared pool
    private double blockRewardPercentage = 1.0; // percentage of how much of the current block payout they can take extra for a block reward

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
        BigDecimal poolContribution = new BigDecimal("0");
        boolean takenPublic = false;
        boolean takenPrivate = false;
        BigDecimal blockReward = new BigDecimal("0");
        BigDecimal blockSurplus = null;

        // calculate base fee for this current block (max of 12.5% update in a single cycle)
        double baseFee = (b.getBaseFee()*(1.0+0.125*((b.getSize()-target)/target)));

        blockSurplus = new BigDecimal(baseFee * 500 * 4200 * 6 * 24 * 7);

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
        BigDecimal optimalPayout = new BigDecimal(baseFee * target);
        BigDecimal difference = rewards.subtract(optimalPayout);

        BigDecimal maxPublicTakeout = optimalPayout.multiply(BigDecimal.valueOf(maxSharedTake));

        // if pool is not empty, and we have a shortage in payout
        if(pool.signum() == 1 && difference.signum() == -1) {
            // if pool has enough in resources..
            if(pool.compareTo(difference) >= 0) {
                difference = difference.negate();

                //if difference is greater than max possible takeout, then take what's possible
                if(difference.compareTo(maxPublicTakeout) >= 0) {
                    // if miner positively contributed to pool in the past, let them take extra if possible
                    if(miner.getPoolEffect().signum() == 1) {
                        // if possible take full 100%
                        if(miner.getPoolEffect().compareTo(difference) >= 0) {
                            pool = pool.subtract(difference.negate());
                            miner.updatePoolEffect(difference.negate());
                            poolContribution = difference.negate();
                            takenPublic = true;
                            takenPrivate = true;
                            rewards = optimalPayout;
                        }
                        else {
                            miner.updatePoolEffect(maxPublicTakeout.negate());
                            // if still can take something from personal pool
                            if(miner.getPoolEffect().signum() == 1) {
                                BigDecimal takeout = maxPublicTakeout.add(miner.getPoolEffect());
                                miner.setPoolEffect(new BigDecimal("0"));
                                rewards = rewards.add(takeout);
                                pool = pool.subtract(takeout);
                                poolContribution = takeout.negate();
                                takenPublic = true;
                                takenPrivate = true;
                            }
                            // just take max possible takeout
                            else {
                                miner.updatePoolEffect(maxPublicTakeout.negate());
                                rewards = rewards.add(maxPublicTakeout);
                                pool = pool.subtract(maxPublicTakeout);
                                poolContribution = maxPublicTakeout.negate();
                                takenPublic = true;
                            }
                        }

                    }
                }
                // take whatever is needed from the pool
                else {
                    pool = pool.subtract(difference);
                    rewards = rewards.add(difference);
                    miner.setPoolEffect(difference.negate());
                    poolContribution = difference.negate();
                    takenPublic = true;
                }

            }
            // if pool doesn't have enough to pay off fully
            else if(pool.compareTo(maxPublicTakeout) < 0) {
                rewards.add(pool);
                miner.updatePoolEffect(pool.negate());
                poolContribution = pool.negate();
                takenPublic = true;
                pool = new BigDecimal("0");
            }
        }
        // if block contains a surplus in fees
        else if(difference.signum() == 1) {
            rewards = optimalPayout;
            pool = pool.add(difference);
            miner.updatePoolEffect(difference);
            poolContribution = difference;

            // take 5% of reserve pool
            if(pool.compareTo(blockSurplus) >= 0) {
                blockReward = rewards.multiply(new BigDecimal(blockRewardPercentage));
                rewards = rewards.add(blockReward);
                pool.add(blockReward.negate());
            }
        }

        return new Data(m, txList, rewards, baseFee, pool, sizeUsedUp, poolContribution, takenPublic, takenPrivate, blockReward, blockSurplus, logs);
    }
}
