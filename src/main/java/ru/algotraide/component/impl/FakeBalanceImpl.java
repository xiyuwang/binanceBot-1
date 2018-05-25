package ru.algotraide.component.impl;

import com.binance.api.client.domain.market.TickerPrice;
import ru.algotraide.component.FakeBalance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public class FakeBalanceImpl implements FakeBalance {

    private Map<String, BigDecimal> accountFakeBalance;
    private Map<String, BigDecimal> currencyRate;
    private int scale = 8;

    public FakeBalanceImpl(){
        initFakeBalance();
        initCurrencyRate();
    }

    @Override
    public BigDecimal getBalanceBySymbol(String symbol) {
        return accountFakeBalance.get(symbol);
    }

    @Override
    public void setBalanceBySymbol(String symbol, BigDecimal value) {
        BigDecimal normValue = value.setScale(scale, RoundingMode.DOWN);
        accountFakeBalance.put(symbol, normValue);
    }

    @Override
    public void addBalanceBySymbol(String symbol, BigDecimal value) {
        BigDecimal normValue = accountFakeBalance.get(symbol).add(value).setScale(scale, RoundingMode.DOWN);
        setBalanceBySymbol(symbol, normValue);
    }

    @Override
    public void reduceBalanceBySymbol(String symbol, BigDecimal value) {
        BigDecimal normValue = accountFakeBalance.get(symbol).subtract(value).setScale(scale, RoundingMode.DOWN);
        if (normValue.compareTo(BigDecimal.ZERO) >= 0.0){
            setBalanceBySymbol(symbol, normValue);
        } else {
            setBalanceBySymbol(symbol, normValue);
            System.err.println("Баланс по " + symbol + " отрицательный!");
        }
    }

    @Override
    public BigDecimal getAllBalanceInDollars(List<TickerPrice> prices) {
        BigDecimal balanceInDollars;
        prices.forEach(p -> {
            if (currencyRate.containsKey(p.getSymbol())){
                currencyRate.replace(p.getSymbol(), new BigDecimal(p.getPrice()));
            }
        });
        BigDecimal ADAinDollar = accountFakeBalance.get("ADA").multiply(currencyRate.get("ADAUSDT"));
        BigDecimal BNBinDollar = accountFakeBalance.get("BNB").multiply(currencyRate.get("BNBUSDT"));
        BigDecimal BCCinDollar = accountFakeBalance.get("BCC").multiply(currencyRate.get("BCCUSDT"));
        BigDecimal BTCinDollar = accountFakeBalance.get("BTC").multiply(currencyRate.get("BTCUSDT"));
        BigDecimal ETHinDollar = accountFakeBalance.get("ETH").multiply(currencyRate.get("ETHUSDT"));
        BigDecimal LTCinDollar = accountFakeBalance.get("LTC").multiply(currencyRate.get("LTCUSDT"));
        BigDecimal NEOinDollar = accountFakeBalance.get("NEO").multiply(currencyRate.get("NEOUSDT"));
        BigDecimal QTUMinDollar = accountFakeBalance.get("QTUM").multiply(currencyRate.get("QTUMUSDT"));
        BigDecimal XRPinDollar = accountFakeBalance.get("XRP").multiply(currencyRate.get("XRPUSDT"));
        balanceInDollars = ADAinDollar.add(BNBinDollar).add(BCCinDollar).add(BTCinDollar).add(ETHinDollar)
                .add(LTCinDollar).add(NEOinDollar).add(QTUMinDollar).add(XRPinDollar).add(accountFakeBalance.get("USDT"));
        balanceInDollars = balanceInDollars.setScale(scale, RoundingMode.DOWN);
        return balanceInDollars;
    }

    @Override
    public void resetBalance() {
        initFakeBalance();
    }

    private void initCurrencyRate(){
        currencyRate = new TreeMap<>();
        currencyRate.put("ADAUSDT", BigDecimal.ZERO);
        currencyRate.put("BNBUSDT", BigDecimal.ZERO);
        currencyRate.put("BCCUSDT", BigDecimal.ZERO);
        currencyRate.put("BTCUSDT", BigDecimal.ZERO);
        currencyRate.put("ETHUSDT", BigDecimal.ZERO);
        currencyRate.put("LTCUSDT", BigDecimal.ZERO);
        currencyRate.put("NEOUSDT", BigDecimal.ZERO);
        currencyRate.put("QTUMUSDT", BigDecimal.ZERO);
        currencyRate.put("XRPUSDT", BigDecimal.ZERO);
    }

    private void initFakeBalance(){
        accountFakeBalance = new TreeMap<>();
        accountFakeBalance.put("ADA", BigDecimal.ZERO);
        accountFakeBalance.put("BNB", new BigDecimal("0.50000000"));
        accountFakeBalance.put("BCC", BigDecimal.ZERO);
        accountFakeBalance.put("BTC", BigDecimal.ZERO);
        accountFakeBalance.put("ETH", BigDecimal.ZERO);
        accountFakeBalance.put("LTC", BigDecimal.ZERO);
        accountFakeBalance.put("NEO", BigDecimal.ZERO);
        accountFakeBalance.put("QTUM", BigDecimal.ZERO);
        accountFakeBalance.put("XRP", BigDecimal.ZERO);
        accountFakeBalance.put("USDT", new BigDecimal("20.00000000"));
    }
}
