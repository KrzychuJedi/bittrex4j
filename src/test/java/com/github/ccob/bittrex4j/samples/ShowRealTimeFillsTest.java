package com.github.ccob.bittrex4j.samples;

import com.github.ccob.bittrex4j.BittrexExchange;
import com.github.ccob.bittrex4j.dao.Fill;
import com.github.ccob.bittrex4j.dao.MarketSummary;
import com.github.ccob.bittrex4j.dao.OrderType;
import com.github.ccob.bittrex4j.util.MarketUtil;
import net.openhft.chronicle.map.ChronicleMap;

import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class ShowRealTimeFillsTest {

    private static BigDecimal volumeLimit = BigDecimal.valueOf(150);

    private static BigDecimal commission = BigDecimal.valueOf(0.9975);

    private static BigDecimal loose = BigDecimal.valueOf(1.0050);

    private static MarketSummary btcEthMarket = new MarketSummary(BigDecimal.ZERO, BigDecimal.ZERO);
    private static MarketSummary zeroMarket = new MarketSummary(BigDecimal.ZERO, BigDecimal.ZERO);

    private static Map<String, MarketSummary> btcMarkets = ChronicleMap
            .of(String.class,MarketSummary.class)
            .name("btc-markets")
            .averageKey("BTC-ETH")
            .entries(300)
            .averageValue(zeroMarket)
            .create();

    private static Map<String, MarketSummary> ethMarkets = ChronicleMap
            .of(String.class,MarketSummary.class)
            .name("eth-markets")
            .averageKey("ETH-XOR")
            .entries(300)
            .averageValue(zeroMarket)
            .create();;

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
                                    btcMarkets.keySet().forEach(s -> countProfits(s, btcMarkets.get(s)));
                                } else {
                                    countProfits(marketName, marketSummary);
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
                                countProfitsEth(marketName, marketSummary);

                                long endTime = System.nanoTime();
                                System.out.println("eth path, start buying after: " + TimeUnit.NANOSECONDS.toNanos(endTime - time));
                            });
                }
            });

            bittrexExchange.onUpdateExchangeState(updateExchangeState -> {
                double volume = Arrays.stream(updateExchangeState.getFills())
                        .mapToDouble(Fill::getQuantity)
                        .sum();
/*
                Arrays.stream(updateExchangeState.getBuys())
                        .forEach(System.out::println);
*/
                if (updateExchangeState.getFills().length > 0) {
                    System.out.println(String.format("N: %d, %02f volume across %d fill(s) for %s", updateExchangeState.getNounce(),
                            volume, updateExchangeState.getFills().length, updateExchangeState.getMarketName()));
                }
            });

            bittrexExchange.onOrderStateChange(orderDelta -> {
                if (orderDelta.getType() == OrderType.Open || orderDelta.getType() == OrderType.Partial) {
                    System.out.println(String.format("%s order open with id %s, remaining %.04f", orderDelta.getOrder().getExchange(),
                            orderDelta.getOrder().getOrderUuid(), orderDelta.getOrder().getQuantityRemaining()));
                } else if (orderDelta.getType() == OrderType.Filled) {
                    System.out.println(String.format("%s order with id %s filled, qty %.04f", orderDelta.getOrder().getExchange(),
                            orderDelta.getOrder().getOrderUuid(), orderDelta.getOrder().getQuantity()));
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


    private static void countProfits(String marketName, MarketSummary btcMarket) {

        MarketSummary ethMarket = ethMarkets.get(marketName);

        if (ethMarket != null) {

            BigDecimal coinsForBTC = MarketUtil.getBuyQuantity(btcMarket.getAsk(), BigDecimal.ONE.multiply(commission));

            BigDecimal ethForCoin = coinsForBTC.multiply(commission).multiply(ethMarket.getBid())
                    .setScale(8, BigDecimal.ROUND_HALF_UP);

            BigDecimal btcForEth = ethForCoin.multiply(commission).multiply(btcEthMarket.getBid())
                    .setScale(8, BigDecimal.ROUND_HALF_UP);

            if (btcForEth.compareTo(loose) > 0) {
                long time = System.nanoTime();

//            arbitrationService.makeOpearations(btcMarket, ethMarket, btcEth, time);
                long endTime = System.nanoTime();

                System.out.println("BTC: " + btcForEth.toString());
                System.out.println("Market " + marketName);
            }
        }
    }

    private static void countProfitsEth(String marketName, MarketSummary ethMarket) {
        MarketSummary btcMarket = btcMarkets.get(marketName);

        if (btcMarket != null) {

            BigDecimal coinsForBTC = MarketUtil.getBuyQuantity(btcMarket.getAsk(), BigDecimal.ONE.multiply(commission));

            BigDecimal ethForCoin = coinsForBTC.multiply(commission).multiply(ethMarket.getBid())
                    .setScale(8, BigDecimal.ROUND_HALF_UP);

            BigDecimal btcForEth = ethForCoin.multiply(commission).multiply(btcEthMarket.getBid())
                    .setScale(8, BigDecimal.ROUND_HALF_UP);

            if (btcForEth.compareTo(loose) > 0) {
                long time = System.nanoTime();

//            arbitrationService.makeOpearations(btcMarket, ethMarket, btcEth, time);
                long endTime = System.nanoTime();

                System.out.println("BTC: " + btcForEth.toString());
                System.out.println("Market " + marketName);
            }
        }
    }
}