package ru.algotraide.component;

import ru.algotraide.object.PairTriangle;

public interface DriverBot {
    Double getProfit(Double startAmt, PairTriangle pairTriangle);
    void buyCycle(Double startAmt, PairTriangle pairTriangle);
}
