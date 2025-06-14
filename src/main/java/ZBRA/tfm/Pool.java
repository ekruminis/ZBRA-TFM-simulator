package ZBRA.tfm;

import java.math.BigDecimal;
import java.util.ArrayList;

import ZBRA.blockchain.Block;
import ZBRA.blockchain.Data;
import ZBRA.blockchain.Miner;
import ZBRA.blockchain.Transaction;

public class Pool extends AbstractTFM {
    private final static String type = "Reserve Pool";
    private double maxSharedTake = 0.66; // percentage of how much of the total payout can be taken away from shared pool
    private double blockRewardPercentage = 1.0; // percentage of the current block payout they can take extra for a block reward

    public Pool() {
        super(type);
    }

    public Pool(double maxSharedTake) {
        super(type);
        this.maxSharedTake = maxSharedTake;
    }


    // used for logging each tx data
    public String[] logStart(int index, String hash, double feeTotal, double feeBase, double feeTip, double weight, double size) {
        return new String[] {
                String.valueOf(index),
                hash,
                String.valueOf(feeTotal),
                String.valueOf(feeBase),
                String.valueOf(feeTip),
                String.valueOf(weight),
                String.valueOf(size)
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
                "Miner Rewards",
                "Block Reward (total)",
                "Block Reward in Base Fees",
                "Block Reward in Tips",
                "Block Size",
                "Block Weight",
                "Number of TX",
                "Base Fee",
                "Reserve Pool Balance",
                "Pool Contribution",
                "TX Index",
                "TX Hash",
                "TX Paid",
                "TX Base Fee",
                "TX Tip",
                "TX Weight",
                "TX Size"
        };
    }

    // Main EIP-1559 Mechanism Implementation
    @Override
    public Data fetchValidTX(ArrayList<Transaction> mempool, double weightLimit, Block block, Miner miner, double weightTarget) {
        // Sort mempool by highest fee per byte
        mempool.sort((tx1, tx2) -> Double.compare(tx2.getWeightFee(), tx1.getWeightFee()));

        double sizeUsedUp = 0;
        double weightUsedUp = 0;

        ArrayList<String[]> logs = new ArrayList<>();

        ArrayList<Transaction> txList = new ArrayList<>();

        BigDecimal currentPoolBalance = block.getPool();

        BigDecimal minerRewards = new BigDecimal("0");
        BigDecimal blockFeeTotal = new BigDecimal("0");
        BigDecimal blockTipTotal = new BigDecimal("0");

        BigDecimal blockPoolContribution = new BigDecimal("0");
        boolean minerTakenPublic = false;
        boolean minerTakenPrivate = false;

        // Calculate base fee of this block
        double baseFee = block.getBaseFee() * (1.0 + 0.125 * ((block.getWeight() - weightTarget) / weightTarget));
        //BigDecimal blockSurplus = new BigDecimal(baseFee * weightTarget * 6 * 24 * 7);

        int index = 1;

        while (!mempool.isEmpty()) {
            Transaction tx = mempool.get(0);
            double txSize = tx.getSize();
            double txWeight = tx.getWeight();

            // Skip transactions that are too large to ever fit
            if (txWeight > weightLimit) {
                mempool.remove(0);
                continue;
            }

            // Stop if this transaction would exceed the block limit
            if ((weightUsedUp + txWeight) > weightLimit) {
                break;
            }

            // Stop if transaction can't afford the base fee (mempool is sorted)
            if (tx.getWeightFee() < baseFee) {
                break;
            }

            // Confirm transaction
            txList.add(tx);
            BigDecimal txTotalFee = new BigDecimal(tx.getTotalFee());
            BigDecimal txBaseFee = new BigDecimal(baseFee * txWeight);
            BigDecimal txTip = txTotalFee.subtract(txBaseFee);

            blockFeeTotal = blockFeeTotal.add(txTotalFee);
            minerRewards = minerRewards.add(txBaseFee);

            sizeUsedUp += txSize;
            weightUsedUp += txWeight;

            logs.add(logStart(index, tx.getHash(), tx.getTotalFee(), txBaseFee.doubleValue(), txTip.doubleValue(), txWeight, txSize));
            mempool.remove(0);
            index++;
        }

        blockTipTotal = blockFeeTotal.subtract(minerRewards);
        // Determine optimal payout
        BigDecimal optimalPayout = new BigDecimal(baseFee * weightTarget);
        BigDecimal difference = minerRewards.subtract(optimalPayout);
        BigDecimal maxPublicTakeout = optimalPayout.multiply(BigDecimal.valueOf(maxSharedTake)); // percentage of optimal payout

        currentPoolBalance = currentPoolBalance.add(blockTipTotal); // add tips to pool
        miner.updatePublicPoolEffect(blockTipTotal);
        blockPoolContribution = blockPoolContribution.add(blockTipTotal);

        // If there's a deficit in payout, and the pool has some funds
        if (currentPoolBalance.signum() == 1 && difference.signum() == -1) {
            difference = difference.negate(); // Make deficit positive

            // If miner has contributed positively to the pool in the past
            if (miner.getPublicPoolEffect().signum() == 1) {

                // If miner's past contribution fully covers the deficit
                if (miner.getPublicPoolEffect().compareTo(difference) >= 0) {
                    // Take as needed
                    currentPoolBalance = currentPoolBalance.subtract(difference);
                    miner.updatePublicPoolEffect(miner.getPublicPoolEffect().add(difference.negate()).max(BigDecimal.ZERO));
                    blockPoolContribution = blockPoolContribution.add(difference.negate());
                    minerTakenPublic = true;
                    minerRewards = minerRewards.add(difference);
                }

                // Else miner's contribution is not enough to fully cover the deficit
                else {
                    if (difference.compareTo(maxPublicTakeout) >= 0) {
                        // Deficit is greater than max public takeout

                        // Reduce public pool effect by maxTakeout (clamped at 0)
                        miner.updatePublicPoolEffect(miner.getPublicPoolEffect().subtract(maxPublicTakeout).max(BigDecimal.ZERO));

                        // Take maxTakeout from pool
                        currentPoolBalance = currentPoolBalance.subtract(maxPublicTakeout);
                        blockPoolContribution = blockPoolContribution.subtract(maxPublicTakeout);
                        minerRewards = minerRewards.add(maxPublicTakeout);
                        minerTakenPublic = true;

                        difference = difference.subtract(maxPublicTakeout);

                        // if needed, and can still take some more from miners' own previous contributions
                        if (miner.getPublicPoolEffect().signum() == 1 && difference.signum() == 1) {
                            if(miner.getPublicPoolEffect().compareTo(difference) >= 0) {
                                miner.updatePublicPoolEffect(miner.getPublicPoolEffect().subtract(difference).max(BigDecimal.ZERO));
                                currentPoolBalance = currentPoolBalance.subtract(difference);
                                blockPoolContribution = blockPoolContribution.subtract(difference);
                                minerTakenPublic = true;
                                minerRewards = minerRewards.add(difference);
                            }
                            else {
                                // take whatever is possible from miner's previous contributions
                                currentPoolBalance = currentPoolBalance.subtract(miner.getPublicPoolEffect());
                                minerRewards = minerRewards.add(miner.getPublicPoolEffect());
                                blockPoolContribution = blockPoolContribution.subtract(miner.getPublicPoolEffect());
                                minerTakenPublic = true;
                                miner.updatePublicPoolEffect(BigDecimal.ZERO);
                            }
                        }
                    } else {
                        // Deficit is less than maxTakeout — just take what's needed
                        currentPoolBalance = currentPoolBalance.subtract(difference);
                        minerRewards = minerRewards.add(difference);
                        miner.updatePublicPoolEffect(miner.getPublicPoolEffect().subtract(difference).max(BigDecimal.ZERO));
                        blockPoolContribution = blockPoolContribution.subtract(difference);
                        minerTakenPublic = true;
                    }
                }
            }

            // If miner has NOT contributed positively to the pool
            else {
                // Miner can take up to maxTakeout
                if (currentPoolBalance.compareTo(maxPublicTakeout) >= 0) {
                    minerRewards = minerRewards.add(maxPublicTakeout);
                    currentPoolBalance = currentPoolBalance.subtract(maxPublicTakeout);
                    miner.updatePublicPoolEffect(miner.getPublicPoolEffect().add(maxPublicTakeout.negate()).max(BigDecimal.ZERO));
                    blockPoolContribution.add(maxPublicTakeout.negate());
                    minerTakenPublic = true;
                } else {
                    // Pool is less than maxTakeout, take whatever is there
                    minerRewards = minerRewards.add(currentPoolBalance);
                    miner.updatePublicPoolEffect(miner.getPublicPoolEffect().add(currentPoolBalance.negate()).max(BigDecimal.ZERO));
                    blockPoolContribution = blockPoolContribution.add(currentPoolBalance.negate());
                    minerTakenPublic = true;
                    currentPoolBalance = BigDecimal.ZERO;
                }
            }
        }

        // If there's a surplus from base fees (difference is positive)
        else if (difference.signum() == 1) {
            minerRewards = optimalPayout;
            currentPoolBalance = currentPoolBalance.add(difference);
            miner.updatePublicPoolEffect(difference);
            blockPoolContribution = blockPoolContribution.add(difference);
        }

        return new Data(
                    mempool, txList, minerRewards, baseFee, 
                    currentPoolBalance, sizeUsedUp, weightUsedUp, 
                    blockPoolContribution, minerTakenPublic, minerTakenPrivate, 
                    blockFeeTotal, blockTipTotal, logs);
    }
}
