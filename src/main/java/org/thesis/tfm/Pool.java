package org.thesis.tfm;

import org.thesis.blockchain.Block;
import org.thesis.blockchain.Data;
import org.thesis.blockchain.Transaction;

import java.math.BigDecimal;
import java.util.ArrayList;

public class Pool extends AbstractTFM {
    private final static String type = "Reserve Pool";

    public Pool() {
        super(type);
    }

    // used for logging each tx data
    public String[] logStart(int i, String h, long f) {
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
                "Gas Limit",
                "Block Size (gas)",
                "Number of Confirmed TX",
                "Base Fee",
                "Reserve Pool",
                "TX Index",
                "TX Hash",
                "TX Paid"
        };
    }

    // Main EIP-1559 Mechanism Implementation
    @Override
    public Data fetchValidTX(ArrayList<Transaction> m, long gasLimit, Block b) {
        // sort current mempool by highest gas price offered
        m.sort((t1, t2) -> Long.compare(t2.getGasPrice(), t1.getGasPrice()));

        long gasUsedUp = 0; // total gas used by current block
        ArrayList<String[]> logs = new ArrayList<String[]>(); // log data for printing later
        ArrayList<Transaction> txList = new ArrayList<Transaction>(); // list of *confirmed* transactions
        BigDecimal rewards = new BigDecimal("0"); // total rewards given to miner
        BigDecimal pool = b.getPool(); // get current pool reserves level

        // calculate base fee for this current block (max of 12.5% update), based on 15mil optimal block target
        long baseFee = (long)((double)b.getBaseFee()*(1.0+0.125*(((double)b.getGasUsed()-(double)15_000_000)/(double)15_000_000)));

        int index = 1;

        while(true) {
            // if mempool is not empty..
            if(!m.isEmpty()) {
                // if block is not yet filled to capacity..
                if ((gasUsedUp + m.get(0).getGasUsed()) < gasLimit) {
                    // if current mempool tx is capable of paying base fee..
                    if(m.get(0).getGasPrice() >= baseFee) {
                        // add to *confirmed* tx list
                        txList.add(m.get(0));

                        // update parameters
                        BigDecimal val = new BigDecimal(m.get(0).getFee());
                        rewards = rewards.add(val);
                        gasUsedUp += m.get(0).getGasUsed();

                        // log data
                        logs.add(logStart(index, m.get(0).getHash(), m.get(0).getFee()));

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

        // calculate optimal payout
        BigDecimal payout = new BigDecimal(baseFee * 15_000_000);

        // if current block payout is less than optimal payout, take rest from reserve pool if it is not empty already
        if(rewards.subtract(payout).signum() == -1 && pool.signum() == 1) {
            BigDecimal dif = payout.subtract(rewards);
            pool = pool.subtract(dif);
            rewards = payout;
        }
        // else add extra revenue back to pool, only award miner the optimal payout
        else {
            BigDecimal dif = rewards.subtract(payout);
            pool = pool.add(dif);
            rewards = payout;
        }

        return new Data(m, txList, rewards, baseFee, pool, gasUsedUp, logs);
    }
}
