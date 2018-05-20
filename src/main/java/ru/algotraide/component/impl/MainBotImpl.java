package ru.algotraide.component.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.algotraide.component.BalanceCache;
import ru.algotraide.component.DriverBot;
import ru.algotraide.component.FakeBalance;
import ru.algotraide.component.MainBot;
import ru.algotraide.object.PairTriangle;

import java.awt.*;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Component
public class MainBotImpl implements MainBot {

    private BigDecimal myBalance;
    private BigDecimal percentForBet;
    private BigDecimal diffInPresent;
    private BigDecimal diff2InPresent;
    private List<PairTriangle> pairTriangleList;
    private BalanceCache balanceCache;
    private FakeBalance fakeBalance;
    private DriverBot driverBot;

    @Autowired
    public MainBotImpl(DriverBot driverBot, BalanceCache balanceCache, FakeBalance fakeBalance) {
        this.driverBot = driverBot;
        this.balanceCache = balanceCache;
        this.fakeBalance = fakeBalance;
        pairTriangleList = new ArrayList<>();
        myBalance = BigDecimal.ZERO;
        percentForBet = new BigDecimal("1");
        diffInPresent = new BigDecimal("0.3");
        diff2InPresent = new BigDecimal("0.4");
        initPairTriangle();
    }

    @Override
    public void start() throws InterruptedException {
        while (true) {
            myBalance = new BigDecimal("15");
//            myBalance = new BigDecimal(balanceCache.getAccountBalanceCache().get("USDT").getFree());
            BigDecimal bet = myBalance.multiply(percentForBet);
            BigDecimal profit;
            for (PairTriangle pairTriangle : pairTriangleList) {
                long t1 = System.currentTimeMillis();
                profit = driverBot.getProfit(bet, pairTriangle);
                if (profit.compareTo(diffInPresent) >= 0) {
                    System.out.println(balanceCache.getAccountBalanceCache().get("USDT").getFree() + ", " + balanceCache.getAccountBalanceCache().get("BNB").getFree());
                    System.out.printf("%s Diff: %.3f%% \n", pairTriangle.toString(), profit);
                    do {
                        driverBot.buyCycle(bet, pairTriangle);
                        Toolkit.getDefaultToolkit().beep();
                        System.out.println(balanceCache.getAccountBalanceCache().get("USDT").getFree() + ", " + balanceCache.getAccountBalanceCache().get("BNB").getFree());
                        profit = driverBot.getProfit(bet, pairTriangle);
                    } while (profit.compareTo(diff2InPresent) >= 0);
                    System.out.println(System.currentTimeMillis() - t1);
                } else System.out.println(profit);
                Thread.sleep(100);
            }
        }
    }

    @Override
    public void initPairTriangle() {
        pairTriangleList.add(new PairTriangle("ADAUSDT", "ADABNB", "BNBUSDT", true));
        pairTriangleList.add(new PairTriangle("BCCUSDT", "BCCBNB", "BNBUSDT", true));
        pairTriangleList.add(new PairTriangle("BTCUSDT", "BNBBTC", "BNBUSDT", true));
        pairTriangleList.add(new PairTriangle("ETHUSDT", "BNBETH", "BNBUSDT", true));
        pairTriangleList.add(new PairTriangle("LTCUSDT", "LTCBNB", "BNBUSDT", true));
        pairTriangleList.add(new PairTriangle("NEOUSDT", "NEOBNB", "BNBUSDT", true));
        pairTriangleList.add(new PairTriangle("QTUMUSDT", "QTUMBNB", "BNBUSDT", true));

        pairTriangleList.add(new PairTriangle("BNBUSDT", "ADABNB", "ADAUSDT", false));
        pairTriangleList.add(new PairTriangle("BNBUSDT", "BCCBNB", "BCCUSDT", false));
        pairTriangleList.add(new PairTriangle("BNBUSDT", "BNBBTC", "BTCUSDT", false));
        pairTriangleList.add(new PairTriangle("BNBUSDT", "BNBETH", "ETHUSDT", false));
        pairTriangleList.add(new PairTriangle("BNBUSDT", "LTCBNB", "LTCUSDT", false));
        pairTriangleList.add(new PairTriangle("BNBUSDT", "NEOBNB", "NEOUSDT", false));
        pairTriangleList.add(new PairTriangle("BNBUSDT", "QTUMBNB", "QTUMUSDT", false));
    }
}
