package com.github.ccob.bittrex4j.dao;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SimpleExchangeSummaryState extends Deltas<SimpleMarketSummary> {

    public SimpleExchangeSummaryState(@JsonProperty("Nounce") @JsonAlias("N") long nounce,
                                @JsonProperty("Deltas") @JsonAlias("D") SimpleMarketSummary[] deltas) {
        super(nounce, deltas);
    }
}
