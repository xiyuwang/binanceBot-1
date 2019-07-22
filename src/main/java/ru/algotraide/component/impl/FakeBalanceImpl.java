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
            System.err.println("FakeBalanceImpl reduceBalanceBySymbol" + symbol + " overflow");
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
        BigDecimal EOSinDollar = accountFakeBalance.get("EOS").multiply(currencyRate.get("EOSUSDT"));
        BigDecimal XEMinDollar = accountFakeBalance.get("XEM").multiply(currencyRate.get("XEMUSDT"));
        BigDecimal STEEMinDollar = accountFakeBalance.get("STEEM").multiply(currencyRate.get("STEEMUSDT"));

        balanceInDollars = ADAinDollar.add(BNBinDollar).add(BCCinDollar).add(BTCinDollar).add(ETHinDollar)
                .add(LTCinDollar).add(NEOinDollar).add(QTUMinDollar).add(accountFakeBalance.get("USDT"))
                        .add(EOSinDollar).add(XEMinDollar).add(STEEMinDollar);
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
        currencyRate.put("EOSUSDT", BigDecimal.ZERO);
        currencyRate.put("XEMUSDT", BigDecimal.ZERO);
        currencyRate.put("STEEMUSDT", BigDecimal.ZERO);
    }

    private void initFakeBalance(){
        accountFakeBalance = new TreeMap<>();
        accountFakeBalance.put("ADA", BigDecimal.ZERO);
        accountFakeBalance.put("BNB", new BigDecimal("0.50000000"));
        accountFakeBalance.put("BCC", BigDecimal.ZERO);
        accountFakeBalance.put("BTC", new BigDecimal("100.0000000"));
        accountFakeBalance.put("ETH", new BigDecimal("1000.0000000"));
        accountFakeBalance.put("LTC", BigDecimal.ZERO);
        accountFakeBalance.put("NEO", BigDecimal.ZERO);
        accountFakeBalance.put("QTUM", BigDecimal.ZERO);
        accountFakeBalance.put("EOS", BigDecimal.ZERO);
        accountFakeBalance.put("XEM", BigDecimal.ZERO);
        accountFakeBalance.put("STEEM", BigDecimal.ZERO);
        accountFakeBalance.put("USDT", new BigDecimal("10000.00000000"));
    }
}
