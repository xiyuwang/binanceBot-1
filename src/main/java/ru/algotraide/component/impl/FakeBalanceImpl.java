package ru.algotraide.component.impl;

import com.binance.api.client.domain.market.TickerPrice;
import ru.algotraide.component.FakeBalance;

import java.util.*;

public class FakeBalanceImpl implements FakeBalance {

    private Map<String, Double> accountFakeBalance;
    private Map<String, Double> currencyRate;
    private Double step = 0.00000001;

    public FakeBalanceImpl(){
        initFakeBalance();
        initCurrencyRate();
    }

    @Override
    public Double getBalanceBySymbol(String symbol) {
        return accountFakeBalance.get(symbol);
    }

    @Override
    public void setBalanceBySymbol(String symbol, Double value) {
        accountFakeBalance.put(symbol, Math.floor(value / step) * step);
    }

    @Override
    public void addBalanceBySymbol(String symbol, Double value) {
        Double newValue = accountFakeBalance.get(symbol) + Math.floor(value / step) * step;
        setBalanceBySymbol(symbol, newValue);
    }

    @Override
    public void reduceBalanceBySymbol(String symbol, Double value) {
        Double newValue = (Math.floor((accountFakeBalance.get(symbol) - value) / step) * step);
        if (newValue >= 0.0){
            setBalanceBySymbol(symbol, newValue);
        } else {
            setBalanceBySymbol(symbol, newValue);
            System.err.println("Баланс по " + symbol + " отрицательный!");
        }
    }

    @Override
    public Double getAllBalanceInDollars(List<TickerPrice> prices) {
        Double balanceInDollars;
        prices.forEach(p -> {
            if (currencyRate.containsKey(p.getSymbol())){
                currencyRate.replace(p.getSymbol(), Double.valueOf(p.getPrice()));
            }
        });
        Double ADAinDollar = accountFakeBalance.get("ADA") * currencyRate.get("ADAUSDT");
        Double BNBinDollar = accountFakeBalance.get("BNB") * currencyRate.get("BNBUSDT");
        Double BCCinDollar = accountFakeBalance.get("BCC") * currencyRate.get("BCCUSDT");
        Double BTCinDollar = accountFakeBalance.get("BTC") * currencyRate.get("BTCUSDT");
        Double ETHinDollar = accountFakeBalance.get("ETH") * currencyRate.get("ETHUSDT");
        Double LTCinDollar = accountFakeBalance.get("LTC") * currencyRate.get("LTCUSDT");
        Double NEOinDollar = accountFakeBalance.get("NEO") * currencyRate.get("NEOUSDT");
        Double QTUMinDollar = accountFakeBalance.get("QTUM") * currencyRate.get("QTUMUSDT");
        balanceInDollars = ADAinDollar + BNBinDollar + BCCinDollar + BTCinDollar + ETHinDollar +
                LTCinDollar + NEOinDollar + QTUMinDollar + accountFakeBalance.get("USDT");
        return Math.floor(balanceInDollars / step) * step;
    }

    @Override
    public void resetBalance() {
        initFakeBalance();
    }

    private void initCurrencyRate(){
        currencyRate = new TreeMap<>();
        currencyRate.put("ADAUSDT", 0.0);
        currencyRate.put("BNBUSDT", 0.0);
        currencyRate.put("BCCUSDT", 0.0);
        currencyRate.put("BTCUSDT", 0.0);
        currencyRate.put("ETHUSDT", 0.0);
        currencyRate.put("LTCUSDT", 0.0);
        currencyRate.put("NEOUSDT", 0.0);
        currencyRate.put("QTUMUSDT", 0.0);
    }

    private void initFakeBalance(){
        accountFakeBalance = new TreeMap<>();
        accountFakeBalance.put("ADA", 0.0);
        accountFakeBalance.put("BNB", 0.5);
        accountFakeBalance.put("BCC", 0.0);
        accountFakeBalance.put("BTC", 0.0);
        accountFakeBalance.put("ETH", 0.0);
        accountFakeBalance.put("LTC", 0.0);
        accountFakeBalance.put("NEO", 0.0);
        accountFakeBalance.put("QTUM", 0.0);
        accountFakeBalance.put("USDT", 20.0);
    }
}
