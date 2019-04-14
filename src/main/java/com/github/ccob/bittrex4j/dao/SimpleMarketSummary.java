package com.github.ccob.bittrex4j.dao;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public class SimpleMarketSummary {

    @JsonProperty("MarketName")
    @JsonAlias("M")
    String marketName;

    @JsonProperty("Bid")
    @JsonAlias("B")
    double bid;

    @JsonProperty("Ask")
    @JsonAlias("A")
    double ask;

    SimpleMarketSummary nextMarket;

    public SimpleMarketSummary() {
    }

    public SimpleMarketSummary(double bid, double ask) {
        this.bid = bid;
        this.ask = ask;
    }

    public String getMarketName() {
        return marketName;
    }

    public void setMarketName(String marketName) {
        this.marketName = marketName;
    }

    public double getBid() {
        return bid;
    }

    public void setBid(double bid) {
        this.bid = bid;
    }

    public double getAsk() {
        return ask;
    }

    public void setAsk(double ask) {
        this.ask = ask;
    }

    public SimpleMarketSummary next() {
        return nextMarket;
    }

    public void setNext(SimpleMarketSummary nextMarket) {
        this.nextMarket = nextMarket;
    }

}
