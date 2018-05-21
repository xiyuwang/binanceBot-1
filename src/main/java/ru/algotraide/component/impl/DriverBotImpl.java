package ru.algotraide.component.impl;

import com.binance.api.client.BinanceApiAsyncRestClient;
import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.account.NewOrder;
import com.binance.api.client.domain.account.NewOrderResponse;
import com.binance.api.client.domain.account.Trade;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.general.SymbolStatus;
import com.binance.api.client.domain.market.TickerPrice;
import com.binance.api.client.exception.BinanceApiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.algotraide.component.DriverBot;
import ru.algotraide.component.FakeBalance;
import ru.algotraide.object.PairTriangle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

@Component
public class DriverBotImpl implements DriverBot {

    private BinanceApiRestClient apiRestClient;
    private BinanceApiAsyncRestClient apiAsyncRestClient;
    private ExchangeInfo exchangeInfo;
    private List<TickerPrice> prices;
    private FakeBalance fakeBalance;
    private BigDecimal commission;
    private BigDecimal allProfit;
    private BigDecimal coefficient;

    public DriverBotImpl(String apiKey, String secretKey, Boolean BNBCommission) {
        BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance(apiKey, secretKey);
        apiRestClient = factory.newRestClient();
        apiAsyncRestClient = factory.newAsyncRestClient();
        exchangeInfo = apiRestClient.getExchangeInfo();
        prices = apiRestClient.getAllPrices();
        allProfit = BigDecimal.ZERO;
        coefficient = new BigDecimal("1.0015"); //Поправочный коэффициент (имитация изменения цены в худшую сторону)
        startRefreshingPrices();
        startRefreshingExchangeInfo();
        if (BNBCommission) commission = new BigDecimal("0.0005");
        else commission = new BigDecimal("0.001");
    }

    @Override
    public BigDecimal getProfit(BigDecimal startAmt, PairTriangle pairTriangle) {
        String firstPair = pairTriangle.getFirstPair();
        String secondPair = pairTriangle.getSecondPair();
        String thirdPair = pairTriangle.getThirdPair();
//        Double firstPairPrice = getPrice(firstPair);
//        Double secondPairPrice = getPrice(secondPair);
//        Double thirdPairPrice = getPrice(thirdPair);
        BigDecimal amtAfterFirstTransaction;
        BigDecimal amtAfterSecondTransaction;
        BigDecimal amtAfterThirdTransaction;
        BigDecimal beforeTradeBalance;
        BigDecimal afterTradeBalance;
        beforeTradeBalance = fakeBalance.getBalanceBySymbol("USDT");
        if (commission.compareTo(new BigDecimal("0.0005")) == 0) {
            amtAfterFirstTransaction = new BigDecimal(normalizeQuantity(firstPair, startAmt.divide(getPrice(firstPair), 8, RoundingMode.DOWN)));
            fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(firstPair).getQuoteAsset(), startAmt.multiply(coefficient));
            fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(firstPair).getBaseAsset(), amtAfterFirstTransaction);
            fakeBalance.reduceBalanceBySymbol("BNB", startAmt.multiply(coefficient).multiply(commission).divide(getPrice("BNBUSDT"), 8, RoundingMode.DOWN));
            boolean isNotional1 = isNotional(amtAfterFirstTransaction, firstPair);

            boolean isNotional2;
            if (pairTriangle.isDirect()) {
                if (secondPair.contains("BTC") || secondPair.contains("ETH")) {
                    amtAfterSecondTransaction = new BigDecimal(normalizeQuantity(secondPair, amtAfterFirstTransaction.divide(getPrice(secondPair), 8, RoundingMode.DOWN)));
                    fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(secondPair).getQuoteAsset(), amtAfterFirstTransaction);
                    fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(secondPair).getBaseAsset(), amtAfterSecondTransaction);
                    if (secondPair.contains("BTC")) {
                        fakeBalance.reduceBalanceBySymbol("BNB", amtAfterFirstTransaction.multiply(commission).divide(getPrice("BNBBTC"), 8, RoundingMode.DOWN));
                    }
                    if (secondPair.contains("ETH")) {
                        fakeBalance.reduceBalanceBySymbol("BNB", amtAfterFirstTransaction.multiply(commission).divide(getPrice("BNBETH"), 8, RoundingMode.DOWN));
                    }
                    isNotional2 = isNotional(amtAfterSecondTransaction, secondPair);
                } else {
                    BigDecimal normAfterFirstTr = new BigDecimal(normalizeQuantity(secondPair, amtAfterFirstTransaction));
                    amtAfterSecondTransaction = normAfterFirstTr.multiply(getPrice(secondPair));
                    fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(secondPair).getBaseAsset(), normAfterFirstTr);
                    fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(secondPair).getQuoteAsset(), amtAfterSecondTransaction);
                    fakeBalance.reduceBalanceBySymbol("BNB", amtAfterSecondTransaction.multiply(commission));
                    isNotional2 = isNotional(amtAfterSecondTransaction, secondPair);
                }
            } else {
                if (secondPair.contains("BTC") || secondPair.contains("ETH")) {
                    BigDecimal normAfterFirstTr = new BigDecimal(normalizeQuantity(secondPair, amtAfterFirstTransaction));
                    amtAfterSecondTransaction = normAfterFirstTr.multiply(getPrice(secondPair));
                    fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(secondPair).getBaseAsset(), normAfterFirstTr);
                    fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(secondPair).getQuoteAsset(), amtAfterSecondTransaction);
                    fakeBalance.reduceBalanceBySymbol("BNB", amtAfterSecondTransaction.multiply(commission));
                    isNotional2 = isNotional(amtAfterSecondTransaction, secondPair);
                } else {
                    amtAfterSecondTransaction = new BigDecimal(normalizeQuantity(secondPair, amtAfterFirstTransaction.divide(getPrice(secondPair), 8, RoundingMode.DOWN)));
                    fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(secondPair).getQuoteAsset(), amtAfterFirstTransaction.multiply(coefficient));
                    fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(secondPair).getBaseAsset(), amtAfterSecondTransaction);
                    fakeBalance.reduceBalanceBySymbol("BNB", amtAfterFirstTransaction.multiply(coefficient.multiply(commission)));
                    isNotional2 = isNotional(amtAfterSecondTransaction, secondPair);
                }
            }
            BigDecimal normAfterThirdTr = new BigDecimal(normalizeQuantity(thirdPair, amtAfterSecondTransaction));
            amtAfterThirdTransaction = normAfterThirdTr.multiply(getPrice(thirdPair));
            fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(thirdPair).getBaseAsset(), normAfterThirdTr);
            fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(thirdPair).getQuoteAsset(), amtAfterThirdTransaction);
            fakeBalance.reduceBalanceBySymbol("BNB", amtAfterThirdTransaction.multiply(commission).divide(getPrice("BNBUSDT"), 8, RoundingMode.DOWN));
            afterTradeBalance = fakeBalance.getBalanceBySymbol("USDT");
            BigDecimal profit = (afterTradeBalance.subtract(beforeTradeBalance)).multiply((new BigDecimal("100").divide(startAmt, 8, RoundingMode.DOWN)));
            fakeBalance.resetBalance();
            boolean isNotional3 = isNotional(amtAfterThirdTransaction, thirdPair);
            if (isNotional1 && isNotional2 && isNotional3) {
                return (profit);
            } else return BigDecimal.ZERO;
        }
        return BigDecimal.ZERO;
    }

    @Override
    public void buyCycle(BigDecimal startAmt, PairTriangle pairTriangle) {
        String firstPair = pairTriangle.getFirstPair();
        String secondPair = pairTriangle.getSecondPair();
        String thirdPair = pairTriangle.getThirdPair();
        boolean direct = pairTriangle.isDirect();
        BigDecimal profit;
        BigDecimal beforeTradeBalance;
        BigDecimal afterTradeBalance;
        beforeTradeBalance = fakeBalance.getBalanceBySymbol("USDT");
        if (isAllPairTrading(pairTriangle)) {
            String amtAfterFirstTransaction = TestBuyCoins(startAmt, firstPair, direct, 1);
            String amtAfterSecondTransaction = TestBuyCoins(new BigDecimal(amtAfterFirstTransaction), secondPair, direct, 2);
            String amtAfterThirdTransaction = TestBuyCoins(new BigDecimal(amtAfterSecondTransaction), thirdPair, direct, 3);
        }
        afterTradeBalance = fakeBalance.getBalanceBySymbol("USDT");
        profit = afterTradeBalance.subtract(beforeTradeBalance);
        allProfit = allProfit.add(profit);
        System.out.println("Доход со сделки: " + profit + " | Общий доход: " + allProfit + " | Общая сумма на кошельке в $: " + fakeBalance.getAllBalanceInDollars(prices));
    }

    private String buyCoins(BigDecimal amtForTrade, String pair, boolean direct, int numPair) {
        BigDecimal pairQuantity;
        String normalQuantity;
        String amtAfterTransaction = "0";

        switch (numPair) {
            case 1:
                pairQuantity = amtForTrade.divide(getPrice(pair), 8, RoundingMode.DOWN);
                normalQuantity = normalizeQuantity(pair, pairQuantity);
                if (isValidQty(pair, normalQuantity))
                    amtAfterTransaction = buyOrSell(pair, normalQuantity, numPair, direct);
                System.out.println("amtAfterTransaction_1 " + amtAfterTransaction);
                break;
            case 2:
                if (direct) {
                    if (pair.contains("BTC") || pair.contains("ETH")) {
                        pairQuantity = amtForTrade.divide(getPrice(pair), 8, RoundingMode.DOWN);
                        normalQuantity = normalizeQuantity(pair, pairQuantity);
                        if (isValidQty(pair, normalQuantity))
                            amtAfterTransaction = buyOrSell(pair, normalQuantity, numPair, true);
                    } else {
                        System.out.println("amtForTrade_2 " + amtForTrade);
                        normalQuantity = normalizeQuantity(pair, amtForTrade);
                        System.out.println("amtForTrade_2_norm " + normalQuantity);
                        if (isValidQty(pair, normalQuantity))
                            amtAfterTransaction = buyOrSell(pair, normalQuantity, numPair, true);
                        System.out.println("amtAfterTransaction_2 " + amtAfterTransaction);
                    }
                } else {
                    if (pair.contains("BTC") || pair.contains("ETH")) {
                        normalQuantity = normalizeQuantity(pair, amtForTrade);
                        if (isValidQty(pair, normalQuantity))
                            amtAfterTransaction = buyOrSell(pair, normalQuantity, numPair, false);
                    } else {
                        pairQuantity = amtForTrade.divide(getPrice(pair), 8, RoundingMode.DOWN);
                        normalQuantity = normalizeQuantity(pair, pairQuantity);
                        if (isValidQty(pair, normalQuantity))
                            amtAfterTransaction = buyOrSell(pair, normalQuantity, numPair, false);
                    }
                }
                break;
            case 3:
                System.out.println("amtForTrade_3 " + amtForTrade);
                normalQuantity = normalizeQuantity(pair, amtForTrade);
                System.out.println("amtForTrade_3_norm " + normalQuantity);
                if (isValidQty(pair, normalQuantity))
                    amtAfterTransaction = buyOrSell(pair, normalQuantity, numPair, direct);
                System.out.println("amtAfterTransaction_3 " + amtAfterTransaction);
                break;
            default:
        }
        return amtAfterTransaction;
    }

    private String TestBuyCoins(BigDecimal amtForTrade, String pair, boolean direct, int numPair) {
        String pairQuantity;
        String amtAfterTransaction = "0";
        switch (numPair) {
            case 1:
                pairQuantity = normalizeQuantity(pair, amtForTrade.divide(getPrice(pair), 8, RoundingMode.DOWN));
                fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(pair).getQuoteAsset(), amtForTrade.multiply(coefficient));
                fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(pair).getBaseAsset(), new BigDecimal(pairQuantity));
                fakeBalance.reduceBalanceBySymbol("BNB", amtForTrade.multiply(coefficient).multiply(commission).divide(getPrice("BNBUSDT"), 8, RoundingMode.DOWN));
                if (isValidQty(pair, pairQuantity)) {
                    apiRestClient.newOrderTest(NewOrder.marketBuy(pair, pairQuantity));
                    amtAfterTransaction = pairQuantity;
                }
                break;
            case 2:
                if (direct) {
                    if (pair.contains("BTC") || pair.contains("ETH")) {
                        pairQuantity = normalizeQuantity(pair, amtForTrade.divide(getPrice(pair), 8, RoundingMode.DOWN));
                        fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(pair).getQuoteAsset(), amtForTrade);
                        fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(pair).getBaseAsset(), new BigDecimal(pairQuantity));
                        if (pair.contains("BTC")) {
                            fakeBalance.reduceBalanceBySymbol("BNB", amtForTrade.multiply(commission).divide(getPrice("BNBBTC"), 8, RoundingMode.DOWN));
                        }
                        if (pair.contains("ETH")) {
                            fakeBalance.reduceBalanceBySymbol("BNB", amtForTrade.multiply(commission).divide(getPrice("BNBETH"), 8, RoundingMode.DOWN));
                        }
                        if (isValidQty(pair, pairQuantity)) {
                            apiRestClient.newOrderTest(NewOrder.marketBuy(pair, pairQuantity));
                            amtAfterTransaction = pairQuantity;
                        }
                    } else {
                        BigDecimal normAfterTr = new BigDecimal(normalizeQuantity(pair, amtForTrade));
                        pairQuantity = normAfterTr.multiply(getPrice(pair)).toString();
                        fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(pair).getBaseAsset(), normAfterTr);
                        fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(pair).getQuoteAsset(), new BigDecimal(pairQuantity));
                        fakeBalance.reduceBalanceBySymbol("BNB", new BigDecimal(pairQuantity).multiply(commission));
                        if (isValidQty(pair, normAfterTr.toString())) {
                            apiRestClient.newOrderTest(NewOrder.marketSell(pair, normAfterTr.toString()));
                            amtAfterTransaction = pairQuantity;
                        }
                        if(amtForTrade.compareTo(normAfterTr) > 0){
                            String newPair = pair.replace("BNB", "USDT");
                            BigDecimal remain = amtForTrade.subtract(normAfterTr);
                            pairQuantity = remain.multiply(getPrice(newPair)).toString();
                            fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(newPair).getBaseAsset(), remain);
                            fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(pair).getQuoteAsset(), new BigDecimal(pairQuantity));
                            fakeBalance.reduceBalanceBySymbol("BNB", new BigDecimal(pairQuantity).multiply(commission).divide(getPrice("BNBUSDT"), 8, RoundingMode.DOWN));
                        }
                    }
                } else {
                    if (pair.contains("BTC") || pair.contains("ETH")) {
                        BigDecimal normAfterTr = new BigDecimal(normalizeQuantity(pair, amtForTrade));
                        pairQuantity = normAfterTr.multiply(getPrice(pair)).toString();
                        fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(pair).getBaseAsset(), normAfterTr);
                        fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(pair).getQuoteAsset(), new BigDecimal(pairQuantity));
                        fakeBalance.reduceBalanceBySymbol("BNB", new BigDecimal(pairQuantity).multiply(commission));
                        if (isValidQty(pair, normAfterTr.toString())) {
                            apiRestClient.newOrderTest(NewOrder.marketSell(pair, normAfterTr.toString()));
                            amtAfterTransaction = pairQuantity;
                        }
                    } else {
                        pairQuantity = normalizeQuantity(pair, amtForTrade.divide(getPrice(pair), 8, RoundingMode.DOWN));
                        fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(pair).getQuoteAsset(), amtForTrade.multiply(coefficient));
                        fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(pair).getBaseAsset(), new BigDecimal(pairQuantity));
                        fakeBalance.reduceBalanceBySymbol("BNB", amtForTrade.multiply(coefficient.multiply(commission)));
                        if (isValidQty(pair, pairQuantity)) {
                            apiRestClient.newOrderTest(NewOrder.marketBuy(pair, pairQuantity));
                            amtAfterTransaction = pairQuantity;
                        }
                    }
                }
                break;
            case 3:
                BigDecimal normAfterTr = new BigDecimal(normalizeQuantity(pair, amtForTrade));
                pairQuantity = normAfterTr.multiply(getPrice(pair)).toString();
                fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(pair).getBaseAsset(), new BigDecimal(normalizeQuantity(pair, normAfterTr)));
                fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(pair).getQuoteAsset(), new BigDecimal(pairQuantity));
                fakeBalance.reduceBalanceBySymbol("BNB", new BigDecimal(pairQuantity).multiply(commission).divide(getPrice("BNBUSDT"), 8, RoundingMode.DOWN));
                if (isValidQty(pair, normAfterTr.toString())) {
                    apiRestClient.newOrderTest(NewOrder.marketSell(pair, normalizeQuantity(pair, normAfterTr)));
                    amtAfterTransaction = pairQuantity;
                }
                break;
            default:
        }
        return amtAfterTransaction;
    }

    private BigDecimal withCommission(BigDecimal withoutCommission) {
        return withoutCommission.subtract(withoutCommission.multiply(commission));
    }

    private String normalizeQuantity(String pair, BigDecimal pairQuantity) {
        SymbolInfo pairInfo = exchangeInfo.getSymbolInfo(pair);
        String step = pairInfo.getFilters().get(1).getStepSize();
        int scale = step.lastIndexOf("1") - 1;
        pairQuantity = pairQuantity.setScale(scale, RoundingMode.DOWN);
        return pairQuantity.toString();


//        String normQty;
//        if (pair.contains("BTC") || pair.contains("ETH")){
//            normQty = String.format(Locale.UK, "%.8f", Math.floor(pairQuantity / step) * step);
//        } else {
//            Double afterFloor = Math.floor(pairQuantity / step) * step;
//            normQty = String.format(Locale.UK, "%.8f", afterFloor);
//        }
//        return normQty;
    }

//    private String normalizeQuantityWithoutRound(Double pairQuantity) {
//        return String.format(Locale.UK, "%.8f", pairQuantity);
//    }

    private Boolean isValidQty(String pair, String normalQuantity) {
        SymbolInfo pairInfo = exchangeInfo.getSymbolInfo(pair);
        Double minQty = Double.valueOf(pairInfo.getFilters().get(1).getMinQty());
        Double maxQty = Double.valueOf(pairInfo.getFilters().get(1).getMaxQty());
        return Double.valueOf(normalQuantity) > minQty && Double.valueOf(normalQuantity) < maxQty;
    }

    private Boolean isNotional(BigDecimal qty, String pair) {
        SymbolInfo pairInfo = exchangeInfo.getSymbolInfo(pair);
        return qty.multiply(getPrice(pair)).compareTo(new BigDecimal(pairInfo.getFilters().get(2).getMinNotional())) > 0;
    }

    private Boolean isAllPairTrading(PairTriangle pairTriangle) {
        boolean pair1 = exchangeInfo.getSymbolInfo(pairTriangle.getFirstPair()).getStatus().equals(SymbolStatus.TRADING);
        boolean pair2 = exchangeInfo.getSymbolInfo(pairTriangle.getSecondPair()).getStatus().equals(SymbolStatus.TRADING);
        boolean pair3 = exchangeInfo.getSymbolInfo(pairTriangle.getThirdPair()).getStatus().equals(SymbolStatus.TRADING);
        return pair1 && pair2 && pair3;
    }

    private String buyOrSell(String pair, String normalQuantity, int numPair, boolean direct) {
        String amtAfterTransaction;
        NewOrderResponse response;
        switch (numPair) {
            case 1:
                amtAfterTransaction = apiRestClient.newOrder(NewOrder.marketBuy(pair, normalQuantity)).getExecutedQty();
                break;
            case 2:
                if (direct) {
                    if (pair.contains("BTC") || pair.contains("ETH")) {
                        amtAfterTransaction = apiRestClient.newOrder(NewOrder.marketBuy(pair, normalQuantity)).getExecutedQty();
                    } else {
                        response = apiRestClient.newOrder(NewOrder.marketSell(pair, normalQuantity));
                        amtAfterTransaction = getAmtAfterSellTransaction(response, pair);
                    }
                } else {
                    if (pair.contains("BTC") || pair.contains("ETH")) {
                        response = apiRestClient.newOrder(NewOrder.marketSell(pair, normalQuantity));
                        amtAfterTransaction = getAmtAfterSellTransaction(response, pair);
                    } else {
                        amtAfterTransaction = apiRestClient.newOrder(NewOrder.marketBuy(pair, normalQuantity)).getExecutedQty();
                    }
                }
                break;
            case 3:
                response = apiRestClient.newOrder(NewOrder.marketSell(pair, normalQuantity));
                amtAfterTransaction = getAmtAfterSellTransaction(response, pair);
                break;
            default:
                amtAfterTransaction = "0";
        }
        return amtAfterTransaction;
    }

    private String getAmtAfterSellTransaction(NewOrderResponse response, String pair) {
        List<Trade> tradeList = apiRestClient.getMyTrades(pair, 1);
        if (tradeList.size() > 0) {
            Trade trade = tradeList.get(0);
            if (Long.valueOf(trade.getOrderId()).longValue() == response.getOrderId().longValue()) {
                return new BigDecimal(trade.getPrice()).multiply(new BigDecimal(trade.getQty())).toString();
            } else return "0";
        } else return "0";
    }

    private BigDecimal getPrice(String pair) {
        Optional<TickerPrice> tickerPrice = prices.stream().filter(s -> s.getSymbol().equals(pair)).findFirst();
        return tickerPrice.map(tickerPrice1 -> new BigDecimal(tickerPrice1.getPrice())).orElse(BigDecimal.ZERO);
    }

    private void startRefreshingPrices() {
        new Thread(() -> {
            while (true) {
                int timeoutCount = 0;
                do {
                    try {
                        apiAsyncRestClient.getAllPrices((List<TickerPrice> response) -> prices = response);
                        break;
                    } catch (BinanceApiException e) {
                        ++timeoutCount;
                        System.err.println("Что-то пошло не так, пробую еще раз");
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e1) {
                            e1.printStackTrace();
                        }
                    }
                } while (timeoutCount <= 100);
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e2) {
                    e2.printStackTrace();
                }
            }
        }).start();
    }

    private void startRefreshingExchangeInfo() {
        new Thread(() -> {
            while (true) {
                int timeoutCount = 0;
                do {
                    try {
                        apiAsyncRestClient.getExchangeInfo((ExchangeInfo response) -> exchangeInfo = response);
                        break;
                    } catch (BinanceApiException e) {
                        ++timeoutCount;
                        System.err.println("Что-то пошло не так, пробую еще раз");
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e1) {
                            e1.printStackTrace();
                        }
                    }
                } while (timeoutCount <= 100);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e2) {
                    e2.printStackTrace();
                }
            }
        }).start();
    }

    @Autowired
    public void setFakeBalance(FakeBalance fakeBalance) {
        this.fakeBalance = fakeBalance;
    }
}