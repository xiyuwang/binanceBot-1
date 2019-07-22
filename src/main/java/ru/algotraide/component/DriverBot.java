package ru.algotraide.component;

import ru.algotraide.object.PairTriangle;

import java.math.BigDecimal;
import java.util.Optional;

public interface DriverBot {
    BigDecimal getProfit(BigDecimal startAmt, PairTriangle pairTriangle);
    boolean buyCycle(BigDecimal startAmt, PairTriangle pairTriangle);
    public String getQuoteAssetFromPair(String pair);
}
