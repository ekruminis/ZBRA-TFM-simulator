# TFM Simulation

## Example (using .jar)
java -jar simulator.jar \*TFM_STYLE\* \*NUMBER_OF_BLOCK_CYCLES\* \*SEED\* \*NUMBER_OF_MINERS\* \*OUTPUT_FILENAME\* \*INPUT_FILENAME\*

e.g. java -jar simulator.jar second_price 144 89433 15 sp_logs txs-week.json

***

### TFM Styles
- **first_price** -> 1st Price Auction TFM *(i.e. attach highest offering txs)*

- **second_price** -> 2nd Price Auction TFM *(i.e. attach highest offering txs, users pay lowest included gas_price bid)*

- **eip1559** -> EIP-1559 TFM *(i.e. attach txs based on dynamic base fee which is burned, only tips go to miner payout)*

- **pool** -> Reserve Pool TFM *(i.e. attach txs based on dynamic base fee, pay miners optimal payout (base_fee * block_target_size), give rest to the shared pool)*

- **burning_second_price** -> Burning 2nd Price Auction TFM *(i.e. attach highest offering txs, top N txs are confirmed, {block_size - N} txs are unconfirmed, users only pay the fee of the highest unconfirmed txs, miner payout is total of fees from unconfirmed txs, any surplus is burned.)* based on Chung/Shi paper - https://arxiv.org/pdf/2111.03151.pdf

### Others
- NUMBER_OF_BLOCK_CYCLES = based on a 10min block cycle
- SEED = random integer value for seed which determines the txs to add to the mempool in each block cycle
- NUMBER_OF_MINERS = randomly generates X number of miners with individual stake values
- OUTPUT_FILENAME = end of simulation will produce a .csv file containing logged data in the "output" folder
- INPUT_FILENAME = name of dataset file (file extension must be specified), must be in a "input" folder of the same directory
