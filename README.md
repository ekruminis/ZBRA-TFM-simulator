# TFM Simulation

## Example (using .jar)
java -jar simulator.jar \*TFM_STYLE\* \*TIME_IN_MINUTES\* \*SEED\* \*NUMBER_OF_MINERS\* \*OUTPUT_FILENAME\* \*INPUT_FILENAME\*

e.g. java -jar simulator.jar second_price 1440 89433 15 sp_logs blockchair_ethereum_transactions_20221009.tsv

***

### TFM Styles
- **first_price** -> 1st Price Auction TFM *(i.e. attach highest offering txs)*

- **second_price** -> 2nd Price Auction TFM *(i.e. attach highest offering txs, users pay lowest included gas_price bid)*

- **eip1559** -> EIP-1559 TFM *(i.e. attach txs based on dynamic base fee which is burned, only tips go to miner payout)*

- **pool** -> Reserve Pool TFM *(i.e. attach txs based on dynamic base fee, pay miners optimal payout (base_fee * block_target_size), give rest to the shared pool)*

### Others
- TIME_IN_MINUTES = based on 12sec block cycle, so each minute will simulate 5 blocks
- SEED = random integer value for seed which determines the txs to add to the mempool in each block cycle
- NUMBER_OF_MINERS = randomly generates X number of miners with individual stake values
- OUTPUT_FILENAME = end of simulation will produce a .csv file containing logged data in the "output" folder
- INPUT_FILENAME = name of dataset file (file extension must be specified), must be in a "input" folder of the same directory

#### Sample Dataset
Ethereum confirmed transactions from 2022-10-09 (1mil+ txs) : https://drive.google.com/file/d/1gUoJucQlGA1JXnNlRJXHGIl8ckF_e_GH
