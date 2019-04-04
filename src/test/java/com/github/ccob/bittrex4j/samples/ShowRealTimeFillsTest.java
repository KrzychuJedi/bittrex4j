package com.github.ccob.bittrex4j.samples;

import com.github.ccob.bittrex4j.BittrexExchange;
import com.github.ccob.bittrex4j.dao.MarketSummary;
import com.github.ccob.bittrex4j.dao.OrderType;
import com.github.ccob.bittrex4j.dao.Response;
import com.github.ccob.bittrex4j.dao.UuidResult;
import com.github.ccob.bittrex4j.util.MarketUtil;

import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class ShowRealTimeFillsTest {

    private static BigDecimal volumeLimit = BigDecimal.valueOf(150);

    private static BigDecimal commission = BigDecimal.valueOf(0.9975);

    private static BigDecimal loose = BigDecimal.valueOf(1.0050);

    private static BigDecimal oneOfThousand = BigDecimal.valueOf(0.001);

    private static MarketSummary btcEthMarket = new MarketSummary(BigDecimal.ZERO, BigDecimal.ZERO);
    private static MarketSummary zeroMarket = new MarketSummary(BigDecimal.ZERO, BigDecimal.ZERO);

    private static Map<String, MarketSummary> btcMarkets = new HashMap<>();

    private static Map<String, MarketSummary> ethMarkets = new HashMap<>();

    private static Map<String, MarketSummary> deals = new HashMap<>();

    private static final String BTC = "BTC";
    private static final String ETH = "ETH";
    private static final String BTC_ETH = "BTC-ETH";

    public static void main(String[] args) throws IOException {

        System.out.println("Press any key to quit");

        Properties prop = new Properties();
        prop.load(new FileInputStream("I:/projects/bittrex4j/src/test/resources/test_keys.properties"));

        try (BittrexExchange bittrexExchange = new BittrexExchange(prop.getProperty("apikey"), prop.getProperty("secret"))) {

            bittrexExchange.onUpdateSummaryState(exchangeSummaryState -> {
                if (exchangeSummaryState.getDeltas().length > 0) {

                    Arrays.stream(exchangeSummaryState.getDeltas())
                            .filter(marketSummary -> marketSummary.getMarketName().startsWith(BTC))
                            .filter(marketSummary -> ethMarkets.keySet().stream().anyMatch(s -> s.endsWith(marketSummary.getMarketName().substring(4))))
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
                                System.out.println("btc path, start buying after: " + TimeUnit.NANOSECONDS.toNanos(endTime - time));
                            });
                }
            });


            bittrexExchange.onUpdateSummaryState(exchangeSummaryState -> {
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
                                System.out.println("eth path, start buying after: " + TimeUnit.NANOSECONDS.toNanos(endTime - time));
                            });
                }
            });

            bittrexExchange.onOrderStateChange(orderDelta -> {
                if (orderDelta.getType() == OrderType.Open || orderDelta.getType() == OrderType.Partial) {
                    System.out.println(String.format("%s order open with id %s, remaining %.04f", orderDelta.getOrder().getExchange(),
                            orderDelta.getOrder().getOrderUuid(), orderDelta.getOrder().getQuantityRemaining()));
                } else if (orderDelta.getType() == OrderType.Filled) {
                    System.out.println(String.format("%s order with id %s filled, qty %.04f", orderDelta.getOrder().getExchange(),
                            orderDelta.getOrder().getOrderUuid(), orderDelta.getOrder().getQuantity()));

                    // sell X and get ETH
                    // sell ETH and get BTC
                    MarketSummary market = deals.get(orderDelta.getOrder().getOrderUuid());
                    if(market!= null) {
                        Response<UuidResult> sellResponse = bittrexExchange.sellLimit(market.getMarketName(),
                                orderDelta.getOrder().getQuantity(),
                                market.getBid().doubleValue());

                        if (!sellResponse.isSuccess() || sellResponse.getResult() == null) {
                            System.out.println("Operation failed");
                            System.out.println(sellResponse.getMessage());
                        } else if (!orderDelta.getOrder().getExchange().equals(BTC_ETH)) {
                            deals.put(sellResponse.getResult().getUuid(), btcEthMarket);
                        }
                    }


                } else if (orderDelta.getType() == OrderType.Cancelled) {
                    System.out.println(String.format("%s order with id %s cancelled", orderDelta.getOrder().getExchange(),
                            orderDelta.getOrder().getOrderUuid()));
                }
            });

            bittrexExchange.onBalanceStateChange(balanceDelta -> {
                System.out.println(String.format("%s wallet balance updated, available: %s, pending: %s", balanceDelta.getBalance().getCurrency(),
                        balanceDelta.getBalance().getAvailable(), balanceDelta.getBalance().getPending()));
            });

            bittrexExchange.connectToWebSocket(() -> {
                bittrexExchange.queryExchangeState("BTC-ETH", exchangeState -> {
                    System.out.println(String.format("BTC-ETH order book has %d open buy orders and %d open sell orders (500 return limit)", exchangeState.getBuys().length, exchangeState.getSells().length));

                });
                bittrexExchange.subscribeToExchangeDeltas("BTC-ETH", null);
                bittrexExchange.subscribeToMarketSummaries(null);
            });

            System.in.read();
        }

        System.out.println("Closing websocket and exiting");
    }


    private static void countProfits(String marketName, MarketSummary btcMarket, BittrexExchange bittrexExchange) {

        MarketSummary ethMarket = ethMarkets.get(marketName);

        if (ethMarket != null) {

            calculateProfits(btcMarket, ethMarket, bittrexExchange, marketName);
        }
    }

    private static void countProfitsEth(String marketName, MarketSummary ethMarket, BittrexExchange bittrexExchange) {
        MarketSummary btcMarket = btcMarkets.get(marketName);

        if (btcMarket != null) {
            calculateProfits(btcMarket, ethMarket, bittrexExchange, marketName);
        }
    }

    private static void calculateProfits(MarketSummary btcMarket, MarketSummary ethMarket, BittrexExchange bittrexExchange, String marketName){
        BigDecimal coinsForBTC = MarketUtil.getBuyQuantity(btcMarket.getAsk(), BigDecimal.ONE.multiply(commission));

        BigDecimal ethForCoin = coinsForBTC.multiply(commission).multiply(ethMarket.getBid())
                .setScale(8, BigDecimal.ROUND_HALF_UP);

        BigDecimal btcForEth = ethForCoin.multiply(commission).multiply(btcEthMarket.getBid())
                .setScale(8, BigDecimal.ROUND_HALF_UP);

        if (btcForEth.compareTo(loose) > 0) {

            buyCryptoUsingBTC(btcMarket, ethMarket, btcForEth, bittrexExchange);

            System.out.println("Crypto bought: " + marketName);
        }
    }


    private static void buyCryptoUsingBTC(MarketSummary btcMarket, MarketSummary ethMarket, BigDecimal btcForEth, BittrexExchange bittrexExchange) {

        // buy X for BTC

        Response<UuidResult> buyResponse = bittrexExchange.buyLimit(BTC + btcMarket.getMarketName(),
                MarketUtil.getBuyQuantity(btcMarket.getAsk(), oneOfThousand).doubleValue(),
                btcMarket.getAsk().doubleValue());

        if (!buyResponse.isSuccess() || buyResponse.getResult() == null) {
            System.out.println("Operation failed");
            System.out.println(buyResponse.getMessage());
        } else {
            deals.put(buyResponse.getResult().getUuid(), ethMarket);
            System.out.println("Operation succeed");
            System.out.println(buyResponse.getResult().getUuid());
        }

    }
}