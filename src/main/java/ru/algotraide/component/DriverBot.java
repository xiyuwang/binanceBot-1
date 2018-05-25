package ru.algotraide.component;

import ru.algotraide.object.PairTriangle;

import java.math.BigDecimal;

public interface DriverBot {
    BigDecimal getProfit(BigDecimal startAmt, PairTriangle pairTriangle);
    void buyCycle(BigDecimal startAmt, PairTriangle pairTriangle, boolean test);
}
