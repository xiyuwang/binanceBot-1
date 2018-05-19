package ru.algotraide.component.impl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.algotraide.component.BalanceCache;
import ru.algotraide.component.DriverBot;
import ru.algotraide.component.FakeBalance;
import ru.algotraide.component.MainBot;
import ru.algotraide.object.PairTriangle;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

@Component
public class MainBotImpl implements MainBot {

    private Double myBalance;
    private Double percentForBet = 1.0;
    private Double diffInPresent = 0.4;
    private Double diff2InPresent = 0.4;
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
        initPairTriangle();
    }

    @Override
    public void start() throws InterruptedException {
        while (true) {
            myBalance = 15.0;
//            myBalance = Double.valueOf(balanceCache.getAccountBalanceCache().get("USDT").getFree());
            Double bet = myBalance * percentForBet;
            Double profit;

            for (PairTriangle pairTriangle : pairTriangleList) {
                long t1 = System.currentTimeMillis();
                profit = driverBot.getProfit(bet, pairTriangle);
                if (profit >= diffInPresent) {
                    System.out.println(balanceCache.getAccountBalanceCache().get("USDT").getFree() + ", " + balanceCache.getAccountBalanceCache().get("BNB").getFree());
                    System.out.printf("%s Diff: %.3f%% \n", pairTriangle.toString(), profit);
                    do {
                        driverBot.buyCycle(bet, pairTriangle);
                        Toolkit.getDefaultToolkit().beep();
                        System.out.println(balanceCache.getAccountBalanceCache().get("USDT") + ", " + balanceCache.getAccountBalanceCache().get("BNB"));
                        profit = driverBot.getProfit(bet, pairTriangle);
                    } while (profit >= diff2InPresent);
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
