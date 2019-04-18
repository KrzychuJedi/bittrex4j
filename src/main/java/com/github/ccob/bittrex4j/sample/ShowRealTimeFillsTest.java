package com.github.ccob.bittrex4j.sample;

import com.github.ccob.bittrex4j.BittrexExchange;
import com.github.ccob.bittrex4j.dao.OrderType;
import com.github.ccob.bittrex4j.dao.Response;
import com.github.ccob.bittrex4j.dao.SimpleMarketSummary;
import com.github.ccob.bittrex4j.dao.UuidResult;
import com.github.ccob.bittrex4j.util.MarketUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class ShowRealTimeFillsTest {

    private static BigDecimal volumeLimit = BigDecimal.valueOf(150);

    private static Logger log = LoggerFactory.getLogger(ShowRealTimeFillsTest.class);

    private static double commission = 0.9975d;

    private static double loose = 1.0050d;

    private static double oneOfThousand = 0.001;

    private static SimpleMarketSummary btcEthMarket = new SimpleMarketSummary(0d, 0d);
    private static SimpleMarketSummary zeroMarket = new SimpleMarketSummary(0d, 0d);

    private static Map<String, SimpleMarketSummary> btcMarkets = new HashMap<>();

    private static Map<String, SimpleMarketSummary> ethMarkets = new HashMap<>();

    private static Map<String, SimpleMarketSummary> deals = new HashMap<>();

    private static final String BTC = "BTC";
    private static final String BTC_MARKET_PREFIX = "BTC-";
    private static final String ETH = "ETH";
    private static final String BTC_ETH = "BTC-ETH";

    public static void main(String[] args) throws IOException {

        System.out.println("Press any key to quit");

        Properties prop = new Properties();
        prop.setProperty("apikey", args[0]);
        prop.setProperty("secret", args[1]);

        try (BittrexExchange bittrexExchange = new BittrexExchange(prop.getProperty("apikey"), prop.getProperty("secret"))) {

            bittrexExchange.onUpdateSimpleSummaryState(exchangeSummaryState -> {
                if (exchangeSummaryState.getDeltas().length > 0) {

                    Arrays.stream(exchangeSummaryState.getDeltas())
                            .filter(marketSummary -> marketSummary.getMarketName().startsWith(BTC))
                            .filter(marketSummary -> marketSummary.getMarketName().equals(BTC_ETH) || ethMarkets.keySet().stream().anyMatch(s -> s.endsWith(marketSummary.getMarketName().substring(4))))
//                            .filter(marketSummary -> marketSummary.getAsk().compareTo(BigDecimal.ZERO) > 0)
//                            .filter(marketSummary -> marketSummary.getBid().compareTo(BigDecimal.ZERO) > 0)
//                            .filter(marketSummary -> marketSummary.getBaseVolume().compareTo(volumeLimit) > 0)
                            .forEach(marketSummary -> {

                                long time = System.nanoTime();

                                String marketName = marketSummary.getMarketName().substring(4);
                                btcMarkets.put(marketName, marketSummary);
                                if (marketSummary.getMarketName().equals(BTC_ETH)) {
                                    btcEthMarket = marketSummary;
                                    btcMarkets.keySet().forEach(s -> countProfits(s, btcMarkets.get(s), bittrexExchange));
                                } else {
                                    countProfits(marketName, marketSummary, bittrexExchange);
                                }
                                long endTime = System.nanoTime();
                                log.debug("btc path, start buying after: " + TimeUnit.NANOSECONDS.toNanos(endTime - time));
                            });
                }
            });


            bittrexExchange.onUpdateSimpleSummaryState(exchangeSummaryState -> {
                if (exchangeSummaryState.getDeltas().length > 0) {

                    Arrays.stream(exchangeSummaryState.getDeltas())
                            .filter(marketSummary -> marketSummary.getMarketName().startsWith(ETH))
//                            .filter(marketSummary -> marketSummary.getAsk().compareTo(BigDecimal.ZERO) > 0)
//                            .filter(marketSummary -> marketSummary.getBid().compareTo(BigDecimal.ZERO) > 0)
//                            .filter(marketSummary -> marketSummary.getBaseVolume().compareTo(volumeLimit) > 0)
                            .forEach(marketSummary -> {

                                long time = System.nanoTime();

                                String marketName = marketSummary.getMarketName().substring(4); //TODO fast substring
                                ethMarkets.put(marketName, marketSummary);
                                countProfitsEth(marketName, marketSummary, bittrexExchange);

                                long endTime = System.nanoTime();
                                log.debug("eth path, start buying after: " + TimeUnit.NANOSECONDS.toNanos(endTime - time));
                            });
                }
            });

            bittrexExchange.onOrderStateChange(orderDelta -> {
                if (orderDelta.getType() == OrderType.Open || orderDelta.getType() == OrderType.Partial) {
                    log.info(String.format("%s order open with id %s, remaining %.04f", orderDelta.getOrder().getExchange(),
                            orderDelta.getOrder().getOrderUuid(), orderDelta.getOrder().getQuantityRemaining()));
                } else if (orderDelta.getType() == OrderType.Filled) {
                    log.info(String.format("%s order with id %s filled, qty %.04f", orderDelta.getOrder().getExchange(),
                            orderDelta.getOrder().getOrderUuid(), orderDelta.getOrder().getQuantity()));

                    // sell X and get ETH
                    // sell ETH and get BTC
                    SimpleMarketSummary market = deals.get(orderDelta.getOrder().getOrderUuid());
                    if(market!= null) {
                        Response<UuidResult> sellResponse = bittrexExchange.sellLimit(market.getMarketName(),
                                orderDelta.getOrder().getQuantity(),
                                market.getBid());

                        if (!sellResponse.isSuccess() || sellResponse.getResult() == null) {
                            log.info("Operation failed");
                            log.info(sellResponse.getMessage());
                        } else if (market.next() != null) {
                            deals.put(sellResponse.getResult().getUuid(), market.next());
                        }
                    }


                } else if (orderDelta.getType() == OrderType.Cancelled) {
                    log.info(String.format("%s order with id %s cancelled", orderDelta.getOrder().getExchange(),
                            orderDelta.getOrder().getOrderUuid()));
                }
            });

            bittrexExchange.onBalanceStateChange(balanceDelta -> {
                log.info(String.format("%s wallet balance updated, available: %s, pending: %s", balanceDelta.getBalance().getCurrency(),
                        balanceDelta.getBalance().getAvailable(), balanceDelta.getBalance().getPending()));
            });

            bittrexExchange.connectToWebSocket(() -> {
                bittrexExchange.queryExchangeState("BTC-ETH", exchangeState -> {
                    log.info(String.format("BTC-ETH order book has %d open buy orders and %d open sell orders (500 return limit)", exchangeState.getBuys().length, exchangeState.getSells().length));

                });
                bittrexExchange.subscribeToExchangeDeltas("BTC-ETH", null);
                bittrexExchange.subscribeToMarketSummaries(null);
            });

            System.in.read();
        }

        System.out.println("Closing websocket and exiting");
    }


    private static void countProfits(String marketName, SimpleMarketSummary btcMarket, BittrexExchange bittrexExchange) {

        SimpleMarketSummary ethMarket = ethMarkets.get(marketName);

        if (ethMarket != null) {

            calculateProfits(btcMarket, ethMarket, bittrexExchange, marketName);
        }
    }

    private static void countProfitsEth(String marketName, SimpleMarketSummary ethMarket, BittrexExchange bittrexExchange) {
        SimpleMarketSummary btcMarket = btcMarkets.get(marketName);

        if (btcMarket != null) {
            calculateProfits(btcMarket, ethMarket, bittrexExchange, marketName);
        }
    }

    private static void calculateProfits(SimpleMarketSummary btcMarket, SimpleMarketSummary ethMarket, BittrexExchange bittrexExchange, String marketName){
        double coinsForBTC = MarketUtil.getBuyQuantity(btcMarket.getAsk(), commission);

        double ethForCoin = coinsForBTC * commission * ethMarket.getBid();

        double btcForEth = ethForCoin * commission * btcEthMarket.getBid();

        if (btcForEth > loose) {

            ethMarket.setNext(btcEthMarket);
            buyCryptoUsingBTC(btcMarket, ethMarket, bittrexExchange);

            log.info("Crypto bought: " + marketName);
            log.info("Ask: " + btcMarket.getAsk());
            log.info("Profit: " + loose);
        }
    }


    private static void buyCryptoUsingBTC(SimpleMarketSummary btcMarket, SimpleMarketSummary ethMarket, BittrexExchange bittrexExchange) {

        // buy X for BTC

        Response<UuidResult> buyResponse = bittrexExchange.buyLimit(BTC_MARKET_PREFIX + btcMarket.getMarketName(),
                MarketUtil.getBuyQuantity(btcMarket.getAsk(), oneOfThousand),
                btcMarket.getAsk());

        if (!buyResponse.isSuccess() || buyResponse.getResult() == null) {
            log.info("Operation failed");
            log.info(buyResponse.getMessage());
        } else {
            deals.put(buyResponse.getResult().getUuid(), ethMarket);
            log.info("Operation succeed");
            log.info(buyResponse.getResult().getUuid());
            log.info(buyResponse.getMessage());
        }

    }
}