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

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class DriverBotImpl implements DriverBot {

    private BinanceApiRestClient apiRestClient;
    private BinanceApiAsyncRestClient apiAsyncRestClient;
    private ExchangeInfo exchangeInfo;
    private List<TickerPrice> prices;
    private FakeBalance fakeBalance;
    private Double commission;

    public DriverBotImpl(String apiKey, String secretKey, Boolean BNBCommission) {
        BinanceApiClientFactory factory = BinanceApiClientFactory.newInstance(apiKey, secretKey);
        apiRestClient = factory.newRestClient();
        apiAsyncRestClient = factory.newAsyncRestClient();
        exchangeInfo = apiRestClient.getExchangeInfo();
        prices = apiRestClient.getAllPrices();
        startRefreshingPrices();
        startRefreshingExchangeInfo();
        if (BNBCommission) commission = 0.0005;
        else commission = 0.001;
    }

    @Override
    public Double getProfit(Double startAmt, PairTriangle pairTriangle) {
        String firstPair = pairTriangle.getFirstPair();
        String secondPair = pairTriangle.getSecondPair();
        String thirdPair = pairTriangle.getThirdPair();
//        Double firstPairPrice = getPrice(firstPair);
//        Double secondPairPrice = getPrice(secondPair);
//        Double thirdPairPrice = getPrice(thirdPair);
        Double amtAfterFirstTransaction;
        Double amtAfterSecondTransaction;
        Double amtAfterThirdTransaction;
        Double coefficient = 1.0015; //Поправочный коэффициент (имитация изменения цены в худшую сторону)
        Double beforeTradeBalance;
        Double afterTradeBalance;
        beforeTradeBalance = fakeBalance.getAllBalanceInDollars(prices);
        if (commission == 0.0005) {
            amtAfterFirstTransaction = Double.valueOf(normalizeQuantity(firstPair, startAmt / getPrice(firstPair)));
            fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(firstPair).getQuoteAsset(), startAmt * coefficient);
            fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(firstPair).getBaseAsset(), amtAfterFirstTransaction);
            fakeBalance.reduceBalanceBySymbol("BNB", startAmt * coefficient * 0.0005 / getPrice("BNBUSDT"));
            boolean isNotional1 = isNotional(amtAfterFirstTransaction, firstPair);

            boolean isNotional2;
            if (pairTriangle.isDirect()) {
                if (secondPair.contains("BTC") || secondPair.contains("ETH")) {
                    amtAfterSecondTransaction = Double.valueOf(normalizeQuantity(secondPair, amtAfterFirstTransaction / getPrice(secondPair)));
                    fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(secondPair).getQuoteAsset(), amtAfterFirstTransaction);
                    fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(secondPair).getBaseAsset(), amtAfterSecondTransaction);
                    if (secondPair.contains("BTC")) {
                        fakeBalance.reduceBalanceBySymbol("BNB", amtAfterFirstTransaction * 0.0005 / getPrice("BNBBTC"));
                    }
                    if (secondPair.contains("ETH")) {
                        fakeBalance.reduceBalanceBySymbol("BNB", amtAfterFirstTransaction * 0.0005 / getPrice("BNBETH"));
                    }
                    isNotional2 = isNotional(amtAfterSecondTransaction, secondPair);
                } else {
                    Double normAfterFirstTr = Double.valueOf(normalizeQuantity(secondPair, amtAfterFirstTransaction));
                    amtAfterSecondTransaction = normAfterFirstTr * getPrice(secondPair);
                    fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(secondPair).getBaseAsset(), Double.valueOf(normalizeQuantity(secondPair, amtAfterFirstTransaction)));
                    fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(secondPair).getQuoteAsset(), amtAfterSecondTransaction);
                    fakeBalance.reduceBalanceBySymbol("BNB", amtAfterSecondTransaction * 0.0005);
                    isNotional2 = isNotional(amtAfterSecondTransaction, secondPair);
                }
            } else {
                if (secondPair.contains("BTC") || secondPair.contains("ETH")) {
                    amtAfterSecondTransaction = Double.valueOf(normalizeQuantity(secondPair, amtAfterFirstTransaction)) * getPrice(secondPair);
                    fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(secondPair).getQuoteAsset(), Double.valueOf(normalizeQuantity(secondPair, amtAfterFirstTransaction)));
                    fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(secondPair).getBaseAsset(), amtAfterSecondTransaction);
                    fakeBalance.reduceBalanceBySymbol("BNB", amtAfterSecondTransaction * 0.0005);
                    isNotional2 = isNotional(amtAfterSecondTransaction, secondPair);
                } else {
                    amtAfterSecondTransaction = Double.valueOf(normalizeQuantity(secondPair, amtAfterFirstTransaction / getPrice(secondPair)));
                    fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(secondPair).getQuoteAsset(), amtAfterFirstTransaction * coefficient);
                    fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(secondPair).getBaseAsset(), amtAfterSecondTransaction);
                    fakeBalance.reduceBalanceBySymbol("BNB", amtAfterFirstTransaction * coefficient * 0.0005);
                    isNotional2 = isNotional(amtAfterSecondTransaction, secondPair);
                }
            }
            amtAfterThirdTransaction = Double.valueOf(normalizeQuantity(thirdPair, amtAfterSecondTransaction)) * getPrice(thirdPair);
            fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(thirdPair).getBaseAsset(), Double.valueOf(normalizeQuantity(thirdPair, amtAfterSecondTransaction)));
            fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(thirdPair).getQuoteAsset(), amtAfterThirdTransaction);
            fakeBalance.reduceBalanceBySymbol("BNB", amtAfterThirdTransaction * 0.0005 / getPrice("BNBUSDT"));
            afterTradeBalance = fakeBalance.getAllBalanceInDollars(prices);
            Double d = (afterTradeBalance - beforeTradeBalance) * (100 / startAmt);
            fakeBalance.resetBalance();
            boolean isNotional3 = isNotional(amtAfterThirdTransaction, thirdPair);
            if (isNotional1 && isNotional2 && isNotional3) {
                return (afterTradeBalance - beforeTradeBalance) * (100 / startAmt);
            } else return 0.0;
        }


//        amtAfterFirstTransaction = Double.valueOf(normalizeQuantity(firstPair, withCommission(startAmt / firstPairPrice)));
//        boolean isNotional1 = isNotional(amtAfterFirstTransaction, firstPair);
//        boolean isNotional2;
//        if (pairTriangle.isDirect()) {
//            if (secondPair.contains("BTC") || secondPair.contains("ETH")) {
//                amtAfterSecondTransaction = Double.valueOf(normalizeQuantity(secondPair, withCommission(amtAfterFirstTransaction / secondPairPrice)));
//                isNotional2 = isNotional(amtAfterSecondTransaction, secondPair);
//            } else {
//                amtAfterSecondTransaction = Double.valueOf(normalizeQuantity(secondPair, withCommission(amtAfterFirstTransaction * secondPairPrice)));
//                isNotional2 = isNotional(amtAfterSecondTransaction, secondPair);
//            }
//        } else {
//            if (secondPair.contains("BTC") || secondPair.contains("ETH")) {
//                amtAfterSecondTransaction = Double.valueOf(normalizeQuantity(secondPair, withCommission(amtAfterFirstTransaction * secondPairPrice)));
//                isNotional2 = isNotional(amtAfterSecondTransaction, secondPair);
//            } else {
//                amtAfterSecondTransaction = Double.valueOf(normalizeQuantity(secondPair, withCommission(amtAfterFirstTransaction / secondPairPrice)));
//                isNotional2 = isNotional(amtAfterSecondTransaction, secondPair);
//            }
//        }
//        amtAfterThirdTransaction = withCommission(amtAfterSecondTransaction * thirdPairPrice);
//        boolean isNotional3 = isNotional(amtAfterThirdTransaction, thirdPair);
//        if (isNotional1 && isNotional2 && isNotional3) {
//            return (amtAfterThirdTransaction - startAmt) * (100 / startAmt);
//        } else return 0.0;
        return 0.0;
    }

    @Override
    public void buyCycle(Double startAmt, PairTriangle pairTriangle) {
        String firstPair = pairTriangle.getFirstPair();
        String secondPair = pairTriangle.getSecondPair();
        String thirdPair = pairTriangle.getThirdPair();
        boolean direct = pairTriangle.isDirect();
        if (isAllPairTrading(pairTriangle)) {
            Double amtAfterFirstTransaction = TestBuyCoins(startAmt, firstPair, direct, 1);
            Double amtAfterSecondTransaction = TestBuyCoins(amtAfterFirstTransaction, secondPair, direct, 2);
            Double amtAfterThirdTransaction = TestBuyCoins(amtAfterSecondTransaction, thirdPair, direct, 3);
        }
    }

    private Double buyCoins(Double amtForTrade, String pair, boolean direct, int numPair) {
        Double pairQuantity;
        String normalQuantity;
        Double amtAfterTransaction = 0.0;

        switch (numPair) {
            case 1:
                pairQuantity = amtForTrade / getPrice(pair);
                normalQuantity = normalizeQuantity(pair, pairQuantity);
                if (isValidQty(pair, normalQuantity))
                    amtAfterTransaction = buyOrSell(pair, normalQuantity, numPair, direct);
                System.out.println("amtAfterTransaction_1 " + amtAfterTransaction);
                break;
            case 2:
                if (direct) {
                    if (pair.contains("BTC") || pair.contains("ETH")) {
                        pairQuantity = amtForTrade / getPrice(pair);
                        normalQuantity = normalizeQuantity(pair, pairQuantity);
                        if (isValidQty(pair, normalQuantity))
                            amtAfterTransaction = buyOrSell(pair, normalQuantity, numPair, true);
                    } else {
                        System.out.println("amtForTrade_2 " + amtForTrade);
                        normalQuantity = normalizeQuantityWithoutRound(amtForTrade);
                        System.out.println("amtForTrade_2_norm " + normalQuantity);
                        if (isValidQty(pair, normalQuantity))
                            amtAfterTransaction = buyOrSell(pair, normalQuantity, numPair, true);
                        System.out.println("amtAfterTransaction_2 " + amtAfterTransaction);
                    }
                } else {
                    if (pair.contains("BTC") || pair.contains("ETH")) {
                        normalQuantity = normalizeQuantityWithoutRound(amtForTrade);
                        if (isValidQty(pair, normalQuantity))
                            amtAfterTransaction = buyOrSell(pair, normalQuantity, numPair, false);
                    } else {
                        pairQuantity = amtForTrade / getPrice(pair);
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

    private Double TestBuyCoins(Double amtForTrade, String pair, boolean direct, int numPair) {
        Double pairQuantity;
        String normalQuantity;
        Double amtAfterTransaction = 0.0;

        switch (numPair) {
            case 1:
                pairQuantity = withCommission(amtForTrade / getPrice(pair));
                normalQuantity = normalizeQuantity(pair, pairQuantity);
                if (isValidQty(pair, normalQuantity)) {
                    apiRestClient.newOrderTest(NewOrder.marketBuy(pair, normalQuantity));
                    amtAfterTransaction = Double.valueOf(normalQuantity);
                }
                break;
            case 2:
                if (direct) {
                    if (pair.contains("BTC") || pair.contains("ETH")) {
                        pairQuantity = withCommission(amtForTrade / getPrice(pair));
                        normalQuantity = normalizeQuantity(pair, pairQuantity);
                        if (isValidQty(pair, normalQuantity)) {
                            apiRestClient.newOrderTest(NewOrder.marketBuy(pair, normalQuantity));
                            amtAfterTransaction = Double.valueOf(normalQuantity);
                        }
                    } else {
                        pairQuantity = withCommission(amtForTrade * getPrice(pair));
                        normalQuantity = normalizeQuantity(pair, pairQuantity);
                        if (isValidQty(pair, amtForTrade.toString())) {
                            apiRestClient.newOrderTest(NewOrder.marketSell(pair, normalizeQuantity(pair, amtForTrade)));
                            amtAfterTransaction = Double.valueOf(normalQuantity);
                        }
                    }
                } else {
                    if (pair.contains("BTC") || pair.contains("ETH")) {
                        pairQuantity = withCommission(amtForTrade * getPrice(pair));
                        normalQuantity = normalizeQuantity(pair, pairQuantity);
                        if (isValidQty(pair, amtForTrade.toString())) {
                            apiRestClient.newOrderTest(NewOrder.marketSell(pair, normalizeQuantity(pair, amtForTrade)));
                            amtAfterTransaction = Double.valueOf(normalQuantity);
                        }
                    } else {
                        pairQuantity = withCommission(amtForTrade / getPrice(pair));
                        normalQuantity = normalizeQuantity(pair, pairQuantity);
                        if (isValidQty(pair, normalQuantity)) {
                            apiRestClient.newOrderTest(NewOrder.marketBuy(pair, normalQuantity));
                            amtAfterTransaction = Double.valueOf(normalQuantity);
                        }
                    }
                }
                break;
            case 3:
                pairQuantity = withCommission(amtForTrade * getPrice(pair));
                normalQuantity = String.valueOf(pairQuantity);
                if (isValidQty(pair, amtForTrade.toString())) {
                    apiRestClient.newOrderTest(NewOrder.marketSell(pair, normalizeQuantity(pair, amtForTrade)));
                    amtAfterTransaction = Double.valueOf(normalQuantity);
                }
                break;
            default:
        }
        return amtAfterTransaction;
    }

    private Double withCommission(Double withoutCommission) {
        return withoutCommission - (withoutCommission * commission);
    }

    private String normalizeQuantity(String pair, Double pairQuantity) {
        String normQty;
        SymbolInfo pairInfo = exchangeInfo.getSymbolInfo(pair);
        Double step = Double.valueOf(pairInfo.getFilters().get(1).getStepSize());
        if (pair.contains("BTC") || pair.contains("ETH")){
            normQty = String.format(Locale.UK, "%.8f", Math.floor(pairQuantity / step) * step);
        } else {
            Double afterFloor = Math.floor(pairQuantity / step) * step;
            normQty = String.format(Locale.UK, "%.8f", afterFloor);
        }
        return normQty;
    }

    private String normalizeQuantityWithoutRound(Double pairQuantity) {
        return String.format(Locale.UK, "%.8f", pairQuantity);
    }

    private Boolean isValidQty(String pair, String normalQuantity) {
        SymbolInfo pairInfo = exchangeInfo.getSymbolInfo(pair);
        Double minQty = Double.valueOf(pairInfo.getFilters().get(1).getMinQty());
        Double maxQty = Double.valueOf(pairInfo.getFilters().get(1).getMaxQty());
        return Double.valueOf(normalQuantity) > minQty && Double.valueOf(normalQuantity) < maxQty;
    }

    private Boolean isNotional(Double qty, String pair) {
        SymbolInfo pairInfo = exchangeInfo.getSymbolInfo(pair);
        return qty * getPrice(pair) > Double.valueOf(pairInfo.getFilters().get(2).getMinNotional());
    }

    private Boolean isAllPairTrading(PairTriangle pairTriangle) {
        boolean pair1 = exchangeInfo.getSymbolInfo(pairTriangle.getFirstPair()).getStatus().equals(SymbolStatus.TRADING);
        boolean pair2 = exchangeInfo.getSymbolInfo(pairTriangle.getSecondPair()).getStatus().equals(SymbolStatus.TRADING);
        boolean pair3 = exchangeInfo.getSymbolInfo(pairTriangle.getThirdPair()).getStatus().equals(SymbolStatus.TRADING);
        return pair1 && pair2 && pair3;
    }

    private Double buyOrSell(String pair, String normalQuantity, int numPair, boolean direct) {
        Double amtAfterTransaction;
        NewOrderResponse response;
        switch (numPair) {
            case 1:
                amtAfterTransaction = Double.valueOf(apiRestClient.newOrder(NewOrder.marketBuy(pair, normalQuantity)).getExecutedQty());
                break;
            case 2:
                if (direct) {
                    if (pair.contains("BTC") || pair.contains("ETH")) {
                        amtAfterTransaction = Double.valueOf(apiRestClient.newOrder(NewOrder.marketBuy(pair, normalQuantity)).getExecutedQty());
                    } else {
                        response = apiRestClient.newOrder(NewOrder.marketSell(pair, normalQuantity));
                        amtAfterTransaction = getAmtAfterSellTransaction(response, pair);
                    }
                } else {
                    if (pair.contains("BTC") || pair.contains("ETH")) {
                        response = apiRestClient.newOrder(NewOrder.marketSell(pair, normalQuantity));
                        amtAfterTransaction = getAmtAfterSellTransaction(response, pair);
                    } else {
                        amtAfterTransaction = Double.valueOf(apiRestClient.newOrder(NewOrder.marketBuy(pair, normalQuantity)).getExecutedQty());
                    }
                }
                break;
            case 3:
                response = apiRestClient.newOrder(NewOrder.marketSell(pair, normalQuantity));
                amtAfterTransaction = getAmtAfterSellTransaction(response, pair);
                break;
            default:
                amtAfterTransaction = 0.0;
        }
        return amtAfterTransaction;
    }

    private Double getAmtAfterSellTransaction(NewOrderResponse response, String pair) {
        List<Trade> tradeList = apiRestClient.getMyTrades(pair, 1);
        if (tradeList.size() > 0) {
            Trade trade = tradeList.get(0);
            if (Long.valueOf(trade.getOrderId()).longValue() == response.getOrderId().longValue()) {
                return Double.valueOf(trade.getPrice()) * Double.valueOf(trade.getQty());
            } else return 0.0;
        } else return 0.0;
    }

    private Double getPrice(String pair) {
        Optional<TickerPrice> tickerPrice = prices.stream().filter(s -> s.getSymbol().equals(pair)).findFirst();
        return tickerPrice.map(tickerPrice1 -> Double.valueOf(tickerPrice1.getPrice())).orElse(0.0);
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