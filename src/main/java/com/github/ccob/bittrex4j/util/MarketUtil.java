package com.github.ccob.bittrex4j.util;



import java.math.BigDecimal;

/**
 * Created by Krzychu on 2017-07-03.
 */
public class MarketUtil {

    public static BigDecimal getBuyQuantity(BigDecimal price, BigDecimal amount) {
        return amount.divide(price, BigDecimal.ROUND_HALF_UP).setScale(8, BigDecimal.ROUND_HALF_UP);
    }


    public static double getBuyQuantity(double price, double amount) {
        return amount / price;
    }

}
