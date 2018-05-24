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
import com.binance.api.client.domain.market.BookTicker;
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

import static ru.algotraide.utils.CalcUtils.*;

@Component
public class DriverBotImpl implements DriverBot {

    private BinanceApiRestClient apiRestClient;
    private BinanceApiAsyncRestClient apiAsyncRestClient;
    private ExchangeInfo exchangeInfo;
    private List<TickerPrice> prices;
    private List<BookTicker> tradeBooks;
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
        tradeBooks = apiRestClient.getBookTickers();
        allProfit = BigDecimal.ZERO;
        coefficient = new BigDecimal("1.0015"); //Поправочный коэффициент (имитация изменения цены в худшую сторону)
        startRefreshingPrices();
        startRefreshingTradeBook();
        startRefreshingExchangeInfo();
        if (BNBCommission) commission = toBigDec("0.0005");
        else commission = toBigDec("0.001");
    }

    @Override
    public BigDecimal getProfit(BigDecimal startAmt, PairTriangle pairTriangle) {
        String firstPair = pairTriangle.getFirstPair();
        String secondPair = pairTriangle.getSecondPair();
        String thirdPair = pairTriangle.getThirdPair();
        BigDecimal amtAfterFirstTransaction;
        BigDecimal amtAfterSecondTransaction;
        BigDecimal amtAfterThirdTransaction;
        BigDecimal beforeTradeBalance;
        BigDecimal afterTradeBalance;
        beforeTradeBalance = fakeBalance.getBalanceBySymbol("USDT");
        if (commission.compareTo(toBigDec("0.0005")) == 0) {
            amtAfterFirstTransaction = normalizeQuantity(firstPair, divide(startAmt, toBigDec(getTradeBook(firstPair).getAskPrice())));
//            System.out.println("profit afterFirst: " + amtAfterFirstTransaction + "Pair: " + firstPair);
            if (firstPair.contains("QTUM") && pairTriangle.isDirect()) {
                amtAfterFirstTransaction = downGrade(amtAfterFirstTransaction);
//                System.out.println("profit afterFirst: " + amtAfterFirstTransaction);
            }
            fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(firstPair).getQuoteAsset(), multiply(startAmt, coefficient));
            fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(firstPair).getBaseAsset(), amtAfterFirstTransaction);
            fakeBalance.reduceBalanceBySymbol("BNB", divide(multiply(multiply(startAmt, coefficient), commission), getPrice("BNBUSDT")));
            boolean isNotional1 = isNotional(amtAfterFirstTransaction, firstPair);

            boolean isNotional2;
            if (pairTriangle.isDirect()) {
                if (secondPair.contains("BTC") || secondPair.contains("ETH")) {
                    amtAfterSecondTransaction = normalizeQuantity(secondPair, divide(amtAfterFirstTransaction, toBigDec(getTradeBook(secondPair).getAskPrice())));
//                    System.out.println("profit afterSecond: " + amtAfterSecondTransaction + "Pair: " + secondPair);
                    fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(secondPair).getQuoteAsset(), amtAfterFirstTransaction);
                    fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(secondPair).getBaseAsset(), amtAfterSecondTransaction);
                    if (secondPair.contains("BTC")) {
                        fakeBalance.reduceBalanceBySymbol("BNB", divide(multiply(amtAfterFirstTransaction, commission), getPrice("BNBBTC")));
                    }
                    if (secondPair.contains("ETH")) {
                        fakeBalance.reduceBalanceBySymbol("BNB", divide(multiply(amtAfterFirstTransaction, commission), getPrice("BNBETH")));
                    }
                    isNotional2 = isNotional(amtAfterSecondTransaction, secondPair);
                } else {
                    BigDecimal normAfterFirstTr = normalizeQuantity(secondPair, amtAfterFirstTransaction);
                    amtAfterSecondTransaction = multiply(normAfterFirstTr, getTradeBook(secondPair).getBidPrice());
//                    System.out.println("profit afterSecond: " + amtAfterSecondTransaction + "Pair: " + secondPair);
                    fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(secondPair).getBaseAsset(), normAfterFirstTr);
                    fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(secondPair).getQuoteAsset(), amtAfterSecondTransaction);
                    fakeBalance.reduceBalanceBySymbol("BNB", multiply(amtAfterSecondTransaction, commission));
                    isNotional2 = isNotional(amtAfterSecondTransaction, secondPair);
//                    if(amtAfterFirstTransaction.compareTo(normAfterFirstTr) > 0){
//                        String newPair = secondPair.replace("BNB", "USDT");
//                        BigDecimal remain = subtract(amtAfterFirstTransaction, normAfterFirstTr);
//                        BigDecimal remainAfterSale = multiply(remain, getPrice(newPair));
//                        fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(newPair).getBaseAsset(), remain);
//                        fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(newPair).getQuoteAsset(), remainAfterSale);
//                        fakeBalance.reduceBalanceBySymbol("BNB", divide(multiply(remainAfterSale, commission), getPrice("BNBUSDT")));
//                    }
                }
            } else {
                if (secondPair.contains("BTC") || secondPair.contains("ETH")) {
                    BigDecimal normAfterFirstTr = normalizeQuantity(secondPair, amtAfterFirstTransaction);
                    amtAfterSecondTransaction = multiply(normAfterFirstTr, getTradeBook(secondPair).getBidPrice());
//                    System.out.println("profit afterSecond: " + amtAfterSecondTransaction + "Pair: " + secondPair);
                    fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(secondPair).getBaseAsset(), normAfterFirstTr);
                    fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(secondPair).getQuoteAsset(), amtAfterSecondTransaction);
                    fakeBalance.reduceBalanceBySymbol("BNB", multiply(amtAfterSecondTransaction, commission));
                    isNotional2 = isNotional(amtAfterSecondTransaction, secondPair);
                } else {
                    amtAfterSecondTransaction = normalizeQuantity(secondPair, divide(amtAfterFirstTransaction, toBigDec(getTradeBook(secondPair).getAskPrice())));
//                    System.out.println("profit afterSecond: " + amtAfterSecondTransaction + "Pair: " + secondPair);
                    fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(secondPair).getQuoteAsset(),  multiply(amtAfterFirstTransaction, coefficient));
                    fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(secondPair).getBaseAsset(), amtAfterSecondTransaction);
                    fakeBalance.reduceBalanceBySymbol("BNB", multiply(multiply(amtAfterFirstTransaction, coefficient), commission));
                    isNotional2 = isNotional(amtAfterSecondTransaction, secondPair);
                }
            }
            BigDecimal normAfterThirdTr = normalizeQuantity(thirdPair, amtAfterSecondTransaction);
            amtAfterThirdTransaction = multiply(normAfterThirdTr, getTradeBook(thirdPair).getBidPrice());
//            System.out.println("profit afterThird: " + amtAfterThirdTransaction + "Pair: " + thirdPair);
            fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(thirdPair).getBaseAsset(), normAfterThirdTr);
            fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(thirdPair).getQuoteAsset(), amtAfterThirdTransaction);
            fakeBalance.reduceBalanceBySymbol("BNB", divide(multiply(amtAfterThirdTransaction, commission), getPrice("BNBUSDT")));
            afterTradeBalance = fakeBalance.getBalanceBySymbol("USDT");
            BigDecimal profit = divide(multiply(subtract(afterTradeBalance, beforeTradeBalance), toBigDec("100")), startAmt);
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
            BigDecimal amtAfterFirstTransaction = TestBuyCoins(startAmt, firstPair, direct, 1);
//            System.out.println("buyCycle afterFirst: " + amtAfterFirstTransaction + "Pair: " + firstPair);
            BigDecimal amtAfterSecondTransaction = TestBuyCoins(amtAfterFirstTransaction, secondPair, direct, 2);
//            System.out.println("buyCycle afterSecond: " + amtAfterSecondTransaction + "Pair: " + secondPair);
            BigDecimal amtAfterThirdTransaction = TestBuyCoins(amtAfterSecondTransaction, thirdPair, direct, 3);
//            System.out.println("buyCycle afterThird: " + amtAfterThirdTransaction + "Pair: " + thirdPair);
        }
        afterTradeBalance = fakeBalance.getBalanceBySymbol("USDT");
        profit = subtract(afterTradeBalance, beforeTradeBalance);
        allProfit = add(allProfit, profit);
        System.out.println("Доход со сделки: " + profit + " | Общий доход: " + allProfit + " | Общая сумма на кошельке в $: " + fakeBalance.getAllBalanceInDollars(prices));
    }

    private BigDecimal buyCoins(BigDecimal amtForTrade, String pair, boolean direct, int numPair) {
        BigDecimal pairQuantity;
        BigDecimal normalQuantity;
        BigDecimal amtAfterTransaction = BigDecimal.ZERO;

        switch (numPair) {
            case 1:
                pairQuantity = divide(amtForTrade, getPrice(pair));
                normalQuantity = normalizeQuantity(pair, pairQuantity);
                if (pair.contains("QTUM") && direct) {
                    normalQuantity = downGrade(normalQuantity);
                }
                if (isValidQty(pair, normalQuantity))
                    amtAfterTransaction = buyOrSell(pair, normalQuantity.toString(), numPair, direct);
//                System.out.println("amtAfterTransaction_1 " + amtAfterTransaction);
                break;
            case 2:
                if (direct) {
                    if (pair.contains("BTC") || pair.contains("ETH")) {
                        pairQuantity = divide(amtForTrade, getPrice(pair));
                        normalQuantity = normalizeQuantity(pair, pairQuantity);
                        if (isValidQty(pair, normalQuantity))
                            amtAfterTransaction = buyOrSell(pair, normalQuantity.toString(), numPair, true);
                    } else {
//                        System.out.println("amtForTrade_2 " + amtForTrade);
                        normalQuantity = normalizeQuantity(pair, amtForTrade);
//                        System.out.println("amtForTrade_2_norm " + normalQuantity);
                        if (isValidQty(pair, normalQuantity))
                            amtAfterTransaction = buyOrSell(pair, normalQuantity.toString(), numPair, true);
//                        System.out.println("amtAfterTransaction_2 " + amtAfterTransaction);
                    }
                } else {
                    if (pair.contains("BTC") || pair.contains("ETH")) {
                        normalQuantity = normalizeQuantity(pair, amtForTrade);
                        if (isValidQty(pair, normalQuantity))
                            amtAfterTransaction = buyOrSell(pair, normalQuantity.toString(), numPair, false);
                    } else {
                        pairQuantity = amtForTrade.divide(getPrice(pair), 8, RoundingMode.DOWN);
                        normalQuantity = normalizeQuantity(pair, pairQuantity);
                        if (isValidQty(pair, normalQuantity))
                            amtAfterTransaction = buyOrSell(pair, normalQuantity.toString(), numPair, false);
                    }
                }
                break;
            case 3:
//                System.out.println("amtForTrade_3 " + amtForTrade);
                normalQuantity = normalizeQuantity(pair, amtForTrade);
//                System.out.println("amtForTrade_3_norm " + normalQuantity);
                if (isValidQty(pair, normalQuantity))
                    amtAfterTransaction = buyOrSell(pair, normalQuantity.toString(), numPair, direct);
//                System.out.println("amtAfterTransaction_3 " + amtAfterTransaction);
                break;
            default:
        }
        return amtAfterTransaction;
    }

    private BigDecimal buyOrSell(String pair, String normalQuantity, int numPair, boolean direct) {
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
        return toBigDec(amtAfterTransaction);
    }

    private BigDecimal TestBuyCoins(BigDecimal amtForTrade, String pair, boolean direct, int numPair) {
        BigDecimal pairQuantity;
        BigDecimal amtAfterTransaction = BigDecimal.ZERO;
        switch (numPair) {
            case 1:
                pairQuantity = normalizeQuantity(pair, divide(amtForTrade, toBigDec(getTradeBook(pair).getAskPrice())));
                if (pair.contains("QTUM") && direct) {
                    pairQuantity = downGrade(pairQuantity);
                }
                fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(pair).getQuoteAsset(), multiply(amtForTrade, coefficient));
                fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(pair).getBaseAsset(), pairQuantity);
                fakeBalance.reduceBalanceBySymbol("BNB", divide(multiply(multiply(amtForTrade, coefficient), commission), getPrice("BNBUSDT")));
                if (isValidQty(pair, pairQuantity)) {
                    apiRestClient.newOrderTest(NewOrder.marketBuy(pair, pairQuantity.toString()));
                    amtAfterTransaction = pairQuantity;
                }
                break;
            case 2:
                if (direct) {
                    if (pair.contains("BTC") || pair.contains("ETH")) {
                        pairQuantity = normalizeQuantity(pair, divide(amtForTrade, toBigDec(getTradeBook(pair).getAskPrice())));
                        fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(pair).getQuoteAsset(), amtForTrade);
                        fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(pair).getBaseAsset(), pairQuantity);
                        if (pair.contains("BTC")) {
                            fakeBalance.reduceBalanceBySymbol("BNB", divide(multiply(amtForTrade, commission), getPrice("BNBBTC")));
                        }
                        if (pair.contains("ETH")) {
                            fakeBalance.reduceBalanceBySymbol("BNB", divide(multiply(amtForTrade, commission), getPrice("BNBETH")));
                        }
                        if (isValidQty(pair, pairQuantity)) {
                            apiRestClient.newOrderTest(NewOrder.marketBuy(pair, pairQuantity.toString()));
                            amtAfterTransaction = pairQuantity;
                        }
                    } else {
                        BigDecimal normAfterTr = normalizeQuantity(pair, amtForTrade);
                        pairQuantity = multiply(normAfterTr, getTradeBook(pair).getBidPrice());
                        fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(pair).getBaseAsset(), normAfterTr);
                        fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(pair).getQuoteAsset(), pairQuantity);
                        fakeBalance.reduceBalanceBySymbol("BNB", multiply(pairQuantity, commission));
                        if (isValidQty(pair, normAfterTr)) {
                            apiRestClient.newOrderTest(NewOrder.marketSell(pair, normAfterTr.toString()));
                            amtAfterTransaction = pairQuantity;
                        }
//                        if(amtForTrade.compareTo(normAfterTr) > 0){
//                            String newPair = pair.replace("BNB", "USDT");
//                            BigDecimal remain = subtract(amtForTrade, normAfterTr);
//                            pairQuantity = multiply(remain, getPrice(newPair));
//                            fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(newPair).getBaseAsset(), remain);
//                            fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(newPair).getQuoteAsset(), pairQuantity);
//                            fakeBalance.reduceBalanceBySymbol("BNB", divide(multiply(pairQuantity, commission), getPrice("BNBUSDT")));
//                        }
                    }
                } else {
                    if (pair.contains("BTC") || pair.contains("ETH")) {
                        BigDecimal normAfterTr = normalizeQuantity(pair, amtForTrade);
                        pairQuantity = multiply(normAfterTr, getTradeBook(pair).getBidPrice());
                        fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(pair).getBaseAsset(), normAfterTr);
                        fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(pair).getQuoteAsset(), pairQuantity);
                        fakeBalance.reduceBalanceBySymbol("BNB", multiply(pairQuantity, commission));
                        if (isValidQty(pair, normAfterTr)) {
                            apiRestClient.newOrderTest(NewOrder.marketSell(pair, normAfterTr.toString()));
                            amtAfterTransaction = pairQuantity;
                        }
                    } else {
                        pairQuantity = normalizeQuantity(pair, divide(amtForTrade, toBigDec(getTradeBook(pair).getAskPrice())));
                        fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(pair).getQuoteAsset(), multiply(amtForTrade, coefficient));
                        fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(pair).getBaseAsset(), pairQuantity);
                        fakeBalance.reduceBalanceBySymbol("BNB", multiply(multiply(amtForTrade, coefficient), commission));
                        if (isValidQty(pair, pairQuantity)) {
                            apiRestClient.newOrderTest(NewOrder.marketBuy(pair, pairQuantity.toString()));
                            amtAfterTransaction = pairQuantity;
                        }
                    }
                }
                break;
            case 3:
                BigDecimal normAfterTr = normalizeQuantity(pair, amtForTrade);
                pairQuantity = multiply(normAfterTr, getTradeBook(pair).getBidPrice());
                fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(pair).getBaseAsset(), normalizeQuantity(pair, normAfterTr));
                fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(pair).getQuoteAsset(), pairQuantity);
                fakeBalance.reduceBalanceBySymbol("BNB", divide(multiply(pairQuantity, commission), getPrice("BNBUSDT")));
                if (isValidQty(pair, normAfterTr)) {
                    apiRestClient.newOrderTest(NewOrder.marketSell(pair, normalizeQuantity(pair, normAfterTr).toString()));
                    amtAfterTransaction = pairQuantity;
                }
                break;
            default:
        }
        return amtAfterTransaction;
    }

    private BigDecimal normalizeQuantity(String pair, BigDecimal pairQuantity) {
        SymbolInfo pairInfo = exchangeInfo.getSymbolInfo(pair);
        String step = pairInfo.getFilters().get(1).getStepSize();
        int scale = step.lastIndexOf("1") - 1;
        pairQuantity = pairQuantity.setScale(scale, RoundingMode.DOWN);
        return pairQuantity;
    }

    private BigDecimal downGrade(BigDecimal pairQuantity) {
        pairQuantity = pairQuantity.setScale(pairQuantity.scale() - 1, RoundingMode.DOWN);
        return pairQuantity;
    }

    private Boolean isValidQty(String pair, BigDecimal normalQuantity) {
        SymbolInfo pairInfo = exchangeInfo.getSymbolInfo(pair);
        BigDecimal minQty = toBigDec(pairInfo.getFilters().get(1).getMinQty());
        BigDecimal maxQty = toBigDec(pairInfo.getFilters().get(1).getMaxQty());
        return normalQuantity.compareTo(minQty) > 0 && normalQuantity.compareTo(maxQty) < 0;
    }

    private Boolean isNotional(BigDecimal qty, String pair) {
        SymbolInfo pairInfo = exchangeInfo.getSymbolInfo(pair);
        return multiply(qty, getPrice(pair)).compareTo(toBigDec(pairInfo.getFilters().get(2).getMinNotional())) > 0;
    }

    private Boolean isAllPairTrading(PairTriangle pairTriangle) {
        boolean pair1 = exchangeInfo.getSymbolInfo(pairTriangle.getFirstPair()).getStatus().equals(SymbolStatus.TRADING);
        boolean pair2 = exchangeInfo.getSymbolInfo(pairTriangle.getSecondPair()).getStatus().equals(SymbolStatus.TRADING);
        boolean pair3 = exchangeInfo.getSymbolInfo(pairTriangle.getThirdPair()).getStatus().equals(SymbolStatus.TRADING);
        return pair1 && pair2 && pair3;
    }

    private String getAmtAfterSellTransaction(NewOrderResponse response, String pair) {
        List<Trade> tradeList = apiRestClient.getMyTrades(pair, 1);
        if (tradeList.size() > 0) {
            Trade trade = tradeList.get(0);
            if (trade.getOrderId().equals(response.getOrderId().toString())) {
                return multiply(trade.getPrice(), trade.getQty()).toString();
            } else return "0";
        } else return "0";
    }

    private BigDecimal getPrice(String pair) {
        Optional<TickerPrice> tickerPrice = prices.stream().filter(s -> s.getSymbol().equals(pair)).findFirst();
        return tickerPrice.map(tickerPrice1 -> new BigDecimal(tickerPrice1.getPrice())).orElse(BigDecimal.ZERO);
    }

    private BookTicker getTradeBook(String pair) {
        Optional<BookTicker> tradeBook = tradeBooks.stream().filter(s -> s.getSymbol().equals(pair)).findFirst();
        return tradeBook.orElse(new BookTicker());
    }

    private BigDecimal withCommission(BigDecimal withoutCommission) {
        return withoutCommission.subtract(withoutCommission.multiply(commission));
    }

    private void startRefreshingTradeBook() {
        new Thread(() -> {
            while (true) {
                int timeoutCount = 0;
                do {
                    try {
                        apiAsyncRestClient.getBookTickers((List<BookTicker> response) -> tradeBooks = response);
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