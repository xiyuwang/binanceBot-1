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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.math.RoundingMode;

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
    private BigDecimal limit;

    @Autowired
    public MainBotImpl(DriverBot driverBot, BalanceCache balanceCache, FakeBalance fakeBalance) {
        this.driverBot = driverBot;
        this.balanceCache = balanceCache;
        this.fakeBalance = fakeBalance;
        pairTriangleList = new ArrayList<>();
        myBalance = BigDecimal.ZERO;
        percentForBet = new BigDecimal("1");
        diffInPresent = new BigDecimal("0.20");
        diff2InPresent = new BigDecimal("1.50");
        limit = new BigDecimal("1");
        initPairTriangle();
    }

    @Override
    public void start() throws InterruptedException {
        while (true) {

            myBalance = new BigDecimal("15");
//            myBalance = new BigDecimal(balanceCache.getAccountBalanceCache().get("USDT").getFree());
            BigDecimal profit;
            for (PairTriangle pairTriangle : pairTriangleList) {
                String firstUuote = driverBot.getQuoteAssetFromPair(pairTriangle.getFirstPair());
                BigDecimal startBalance = new BigDecimal(balanceCache.getAccountBalanceCache().get(firstUuote).getFree());
                BigDecimal bet = startBalance.multiply(percentForBet);
                long t1 = System.currentTimeMillis();
                profit = driverBot.getProfit(bet, pairTriangle);

                if (profit.compareTo(diffInPresent) >= 0) {
//                    System.out.println(balanceCache.getAccountBalanceCache().get("USDT").getFree() + ", " + balanceCache.getAccountBalanceCache().get("BNB").getFree());
                    System.out.printf("%s profit: %.3f%% \n", pairTriangle.toString(), profit);
                    do {
                        String amount=balanceCache.getAccountBalanceCache().get(firstUuote).getFree();
                        BigDecimal bigAmount = new BigDecimal(amount).divide(new BigDecimal("10"), 2, RoundingMode.DOWN);
                        boolean bRet = driverBot.buyCycle(bigAmount, pairTriangle);
                        if(!bRet) break;
//                        Toolkit.getDefaultToolkit().beep();
                        BigDecimal diffBalance = startBalance.subtract(new BigDecimal(balanceCache.getAccountBalanceCache().get(firstUuote).getFree()));
                        System.out.printf(" MainBotImpl start: startBalance(%s),diffBalance(%s) \n", startBalance, diffBalance);

                        if(diffBalance.compareTo(limit) >= 0){
                            System.out.printf(" MainBotImpl start: diffBalance(%s) is big than (%s$) \n", diffBalance, limit);
                            break;
                        }
//                        System.out.println(balanceCache.getAccountBalanceCache().get("USDT").getFree() + ", " + balanceCache.getAccountBalanceCache().get("BNB").getFree());
                        profit = driverBot.getProfit(bet, pairTriangle);
                    } while (profit.compareTo(diff2InPresent) >= 0);
//                    System.out.println(System.currentTimeMillis() - t1);
                } else /*System.out.println(profit);*/
                Thread.sleep(100);
            }
        }
    }

    @Override
    public void initPairTriangle() {
/*
        pairTriangleList.add(new PairTriangle("ADAUSDT", "ADABNB", "BNBUSDT", true));
        //pairTriangleList.add(new PairTriangle("BCCUSDT", "BCCBNB", "BNBUSDT", true));
        pairTriangleList.add(new PairTriangle("BTCUSDT", "BNBBTC", "BNBUSDT", true));
        pairTriangleList.add(new PairTriangle("ETHUSDT", "BNBETH", "BNBUSDT", true));
        //pairTriangleList.add(new PairTriangle("LTCUSDT", "LTCBNB", "BNBUSDT", true));
        pairTriangleList.add(new PairTriangle("NEOUSDT", "NEOBNB", "BNBUSDT", true));
        pairTriangleList.add(new PairTriangle("QTUMUSDT", "QTUMBNB", "BNBUSDT", true));

        pairTriangleList.add(new PairTriangle("BNBUSDT", "ADABNB", "ADAUSDT", false));
        //pairTriangleList.add(new PairTriangle("BNBUSDT", "BCCBNB", "BCCUSDT", false));
        pairTriangleList.add(new PairTriangle("BNBUSDT", "LTCBNB", "LTCUSDT", false));
        pairTriangleList.add(new PairTriangle("BNBUSDT", "NEOBNB", "NEOUSDT", false));
        pairTriangleList.add(new PairTriangle("BNBUSDT", "QTUMBNB", "QTUMUSDT", false));

        pairTriangleList.add(new PairTriangle("EOSUSDT","EOSBNB","BNBUSDT",true));
        //pairTriangleList.add(new PairTriangle("STEEMETH","STEEMBNB","BNBETH",true));
 */
        pairTriangleList.add(new PairTriangle("XEMETH","XEMBNB","BNBETH",true));
    }
}
