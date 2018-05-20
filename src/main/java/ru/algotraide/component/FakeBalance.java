package ru.algotraide.component;

import com.binance.api.client.domain.market.TickerPrice;

import java.math.BigDecimal;
import java.util.List;

public interface FakeBalance {
    BigDecimal getBalanceBySymbol(String symbol);
    void setBalanceBySymbol(String symbol, BigDecimal value);
    void addBalanceBySymbol(String symbol, BigDecimal value);
    void reduceBalanceBySymbol(String symbol, BigDecimal value);
    BigDecimal getAllBalanceInDollars(List<TickerPrice> prices);
    void resetBalance();
}
