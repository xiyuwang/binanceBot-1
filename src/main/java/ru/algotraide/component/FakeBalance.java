package ru.algotraide.component;

import com.binance.api.client.domain.market.TickerPrice;

import java.util.List;

public interface FakeBalance {
    Double getBalanceBySymbol(String symbol);
    void setBalanceBySymbol(String symbol, Double value);
    void addBalanceBySymbol(String symbol, Double value);
    void reduceBalanceBySymbol(String symbol, Double value);
    Double getAllBalanceInDollars(List<TickerPrice> prices);
    void resetBalance();
}
