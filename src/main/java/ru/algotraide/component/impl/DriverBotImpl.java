package ru.algotraide.component.impl;

import com.binance.api.client.BinanceApiAsyncRestClient;
import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.domain.TimeInForce;
import com.binance.api.client.domain.account.NewOrder;
import com.binance.api.client.domain.account.NewOrderResponse;
import com.binance.api.client.domain.account.Trade;
import com.binance.api.client.domain.general.ExchangeInfo;
import com.binance.api.client.domain.general.SymbolFilter;
import com.binance.api.client.domain.general.SymbolInfo;
import com.binance.api.client.domain.general.SymbolStatus;
import com.binance.api.client.domain.market.OrderBook;
import com.binance.api.client.domain.market.TickerPrice;
import com.binance.api.client.exception.BinanceApiException;
import org.apache.el.lang.ELArithmetic;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import ru.algotraide.component.DriverBot;
import ru.algotraide.component.FakeBalance;
import ru.algotraide.object.PairTriangle;

import javax.validation.constraints.AssertFalse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

public class DriverBotImpl implements DriverBot {

    private static final Logger logger = Logger.getLogger(DriverBotImpl.class);
    private BinanceApiRestClient apiRestClient;
    private BinanceApiAsyncRestClient apiAsyncRestClient;
    private ExchangeInfo exchangeInfo;
    private List<TickerPrice> prices;
    private Map<String,OrderBook> orderBookMap;
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
        initOrderBookeMap();
        startRefreshingBookOrder();
        if (BNBCommission) commission = new BigDecimal("0.0005");
        else commission = new BigDecimal("0.001");
    }
    @Override
    public String getQuoteAssetFromPair(String pair){
        return exchangeInfo.getSymbolInfo(pair).getQuoteAsset();
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
        //beforeTradeBalance = fakeBalance.getBalanceBySymbol("USDT");
        beforeTradeBalance = fakeBalance.getBalanceBySymbol(exchangeInfo.getSymbolInfo(firstPair).getQuoteAsset());
        if (commission.compareTo(new BigDecimal("0.0005")) == 0) {
            //amtAfterFirstTransaction = new BigDecimal(normalizeQuantity(firstPair, startAmt.multiply(coefficient).divide(getPrice(firstPair), 8, RoundingMode.DOWN)));
            amtAfterFirstTransaction = new BigDecimal(normalizeQuantity(firstPair, startAmt.multiply(coefficient).divide(getOrderbookPrice(firstPair,true), 8, RoundingMode.DOWN)));
            //logger.info("firstPair:"+firstPair+" amtAfterFirstTransaction:"+amtAfterFirstTransaction+"\n");

            if (firstPair.contains("QTUM") && pairTriangle.isDirect()){
                amtAfterFirstTransaction = new BigDecimal(downgrade(amtAfterFirstTransaction));
            }
            fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(firstPair).getQuoteAsset(), startAmt.multiply(coefficient));
            fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(firstPair).getBaseAsset(), amtAfterFirstTransaction);
            fakeBalance.reduceBalanceBySymbol("BNB", startAmt.multiply(coefficient).multiply(commission).divide(getPrice("BNBUSDT"), 8, RoundingMode.DOWN));
            boolean isNotional1 = isNotional(amtAfterFirstTransaction, firstPair);

            boolean isNotional2;
            if (pairTriangle.isDirect()) {
                if (secondPair.contains("BTC") || secondPair.contains("ETH")) {
                    //amtAfterSecondTransaction = new BigDecimal(normalizeQuantity(secondPair, amtAfterFirstTransaction.divide(getPrice(secondPair), 8, RoundingMode.DOWN)));
                    amtAfterSecondTransaction = new BigDecimal(normalizeQuantity(secondPair, amtAfterFirstTransaction.divide(getOrderbookPrice(secondPair,true), 8, RoundingMode.DOWN)));
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
                    //amtAfterSecondTransaction = normAfterFirstTr.multiply(getPrice(secondPair));
                    amtAfterSecondTransaction = normAfterFirstTr.multiply(getOrderbookPrice(secondPair,false));
                    fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(secondPair).getBaseAsset(), normAfterFirstTr);
                    fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(secondPair).getQuoteAsset(), amtAfterSecondTransaction);
                    fakeBalance.reduceBalanceBySymbol("BNB", amtAfterSecondTransaction.multiply(commission));
                    isNotional2 = isNotional(amtAfterSecondTransaction, secondPair);
//                    if(amtAfterFirstTransaction.compareTo(normAfterFirstTr) > 0){
//                        String newPair = secondPair.replace("BNB", "USDT");
//                        BigDecimal remain = amtAfterFirstTransaction.subtract(normAfterFirstTr);
//                        BigDecimal remainAfterSale = remain.multiply(getPrice(newPair));
//                        fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(newPair).getBaseAsset(), remain);
//                        fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(newPair).getQuoteAsset(), remainAfterSale);
//                        fakeBalance.reduceBalanceBySymbol("BNB", remainAfterSale.multiply(commission).divide(getPrice("BNBUSDT"), 8, RoundingMode.DOWN));
//                    }
                }

            } else {
                if (secondPair.contains("BTC") || secondPair.contains("ETH")) {
                    BigDecimal normAfterFirstTr = new BigDecimal(normalizeQuantity(secondPair, amtAfterFirstTransaction));
                    //amtAfterSecondTransaction = normAfterFirstTr.multiply(getPrice(secondPair));
                    amtAfterSecondTransaction = normAfterFirstTr.multiply(getOrderbookPrice(secondPair,false));
                    fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(secondPair).getBaseAsset(), normAfterFirstTr);
                    fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(secondPair).getQuoteAsset(), amtAfterSecondTransaction);
                    fakeBalance.reduceBalanceBySymbol("BNB", amtAfterSecondTransaction.multiply(commission));
                    isNotional2 = isNotional(amtAfterSecondTransaction, secondPair);
                } else {
                    //amtAfterSecondTransaction = new BigDecimal(normalizeQuantity(secondPair, amtAfterFirstTransaction.divide(getPrice(secondPair), 8, RoundingMode.DOWN)));
                    amtAfterSecondTransaction = new BigDecimal(normalizeQuantity(secondPair, amtAfterFirstTransaction.divide(getOrderbookPrice(secondPair,true), 8, RoundingMode.DOWN)));
                    fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(secondPair).getQuoteAsset(), amtAfterFirstTransaction.multiply(coefficient));
                    fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(secondPair).getBaseAsset(), amtAfterSecondTransaction);
                    fakeBalance.reduceBalanceBySymbol("BNB", amtAfterFirstTransaction.multiply(coefficient.multiply(commission)));
                    isNotional2 = isNotional(amtAfterSecondTransaction, secondPair);
                }
            }
            //logger.info("secondPair:"+secondPair+" amtAfterSecondTransaction:"+amtAfterSecondTransaction+"\n");

            BigDecimal normAfterThirdTr = new BigDecimal(normalizeQuantity(thirdPair, amtAfterSecondTransaction));
            //amtAfterThirdTransaction = normAfterThirdTr.multiply(getPrice(thirdPair));
            amtAfterThirdTransaction = normAfterThirdTr.multiply(getOrderbookPrice(thirdPair,false));
            fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(thirdPair).getBaseAsset(), normAfterThirdTr);
            fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(thirdPair).getQuoteAsset(), amtAfterThirdTransaction);
            fakeBalance.reduceBalanceBySymbol("BNB", amtAfterThirdTransaction.multiply(commission).divide(getPrice("BNBUSDT"), 8, RoundingMode.DOWN));
            //afterTradeBalance = fakeBalance.getBalanceBySymbol("USDT");
            afterTradeBalance = fakeBalance.getBalanceBySymbol(exchangeInfo.getSymbolInfo(thirdPair).getQuoteAsset());
            BigDecimal profit = (afterTradeBalance.subtract(beforeTradeBalance)).multiply((new BigDecimal("100").divide(startAmt, 8, RoundingMode.DOWN)));
            fakeBalance.resetBalance();
            if(profit.compareTo(BigDecimal.ZERO) > 0.2) {
                logger.info("Pair:" + secondPair + "/" + thirdPair + " profit:" + profit + " amtAfterThirdTransaction:" + amtAfterThirdTransaction + "\n");
                logger.info("Price:" + getPrice(firstPair) + "/" + getPrice(secondPair) + "/" + getPrice(thirdPair) +"\n");
            }
            boolean isNotional3 = isNotional(amtAfterThirdTransaction, thirdPair);
            if (isNotional1 && isNotional2 && isNotional3) {
                return (profit);
            } else return BigDecimal.ZERO;
        }
        return BigDecimal.ZERO;
    }

    @Override
    public boolean buyCycle(BigDecimal startAmt, PairTriangle pairTriangle) {

        String firstPair = pairTriangle.getFirstPair();
        String secondPair = pairTriangle.getSecondPair();
        String thirdPair = pairTriangle.getThirdPair();
        boolean direct = pairTriangle.isDirect();
        BigDecimal profit;
        BigDecimal beforeTradeBalance;
        BigDecimal afterTradeBalance;
//        beforeTradeBalance = fakeBalance.getBalanceBySymbol("USDT");
        if (!isAllPairTrading(pairTriangle))  return false;
        String amtAfterFirstTransaction = buyCoins(startAmt, firstPair, direct, 1);
        String amtAfterSecondTransaction = buyCoins(new BigDecimal(amtAfterFirstTransaction), secondPair, direct, 2);
        String amtAfterThirdTransaction = buyCoins(new BigDecimal(amtAfterSecondTransaction), thirdPair, direct, 3);
        return true;
//        afterTradeBalance = fakeBalance.getBalanceBySymbol("USDT");
//        profit = afterTradeBalance.subtract(beforeTradeBalance);
//        allProfit = allProfit.add(profit);
//        System.out.println("Доход со сделки: " + profit + " | Общий доход: " + allProfit + " | Общая сумма на кошельке в $: " + fakeBalance.getAllBalanceInDollars(prices));
    }

    private String buyCoins(BigDecimal amtForTrade, String pair, boolean direct, int numPair) {
        BigDecimal pairQuantity;
        String normalQuantity;
        String amtAfterTransaction = "0";

        switch (numPair) {
            case 1:
                pairQuantity = amtForTrade.divide(getPrice(pair), 3, RoundingMode.DOWN);
                normalQuantity = normalizeQuantity(pair, pairQuantity);
                if (pair.contains("QTUM") && direct){
                    normalQuantity = downgrade(new BigDecimal(normalQuantity));
                }
                if (isValidQty(pair, normalQuantity))
                    amtAfterTransaction = buyOrSell(pair, normalQuantity, numPair, direct);
//                System.out.println("amtAfterTransaction_1 " + amtAfterTransaction);
                break;
            case 2:
                if (direct) {
                    if (pair.contains("BTC") || pair.contains("ETH")) {
                        pairQuantity = amtForTrade.divide(getPrice(pair), 8, RoundingMode.DOWN);
                        normalQuantity = normalizeQuantity(pair, pairQuantity);
                        if (isValidQty(pair, normalQuantity))
                            amtAfterTransaction = buyOrSell(pair, normalQuantity, numPair, true);
                    } else {
//                        System.out.println("amtForTrade_2 " + amtForTrade);
                        normalQuantity = normalizeQuantity(pair, amtForTrade);
//                        System.out.println("amtForTrade_2_norm " + normalQuantity);
                        if (isValidQty(pair, normalQuantity))
                            amtAfterTransaction = buyOrSell(pair, normalQuantity, numPair, true);
//                        System.out.println("amtAfterTransaction_2 " + amtAfterTransaction);
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
//                System.out.println("amtForTrade_3 " + amtForTrade);
                normalQuantity = normalizeQuantity(pair, amtForTrade);
//                System.out.println("amtForTrade_3_norm " + normalQuantity);
                if (isValidQty(pair, normalQuantity))
                    amtAfterTransaction = buyOrSell(pair, normalQuantity, numPair, direct);
//                System.out.println("amtAfterTransaction_3 " + amtAfterTransaction);
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
                if (pair.contains("QTUM") && direct){
                    pairQuantity = downgrade(new BigDecimal(pairQuantity));
                }
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
//                        if(amtForTrade.compareTo(normAfterTr) > 0){
//                            String newPair = pair.replace("BNB", "USDT");
//                            BigDecimal remain = amtForTrade.subtract(normAfterTr);
//                            pairQuantity = remain.multiply(getPrice(newPair)).toString();
//                            fakeBalance.reduceBalanceBySymbol(exchangeInfo.getSymbolInfo(newPair).getBaseAsset(), remain);
//                            fakeBalance.addBalanceBySymbol(exchangeInfo.getSymbolInfo(newPair).getQuoteAsset(), new BigDecimal(pairQuantity));
//                            fakeBalance.reduceBalanceBySymbol("BNB", new BigDecimal(pairQuantity).multiply(commission).divide(getPrice("BNBUSDT"), 8, RoundingMode.DOWN));
//                        }
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
        //String step = pairInfo.getFilters().get(1).getStepSize();
        //String step = findFilter(pairInfo.getFilters(),"LOT_SIZE").map(f->f.getStepSize()).orElse("0.0100");
        String step = "0.0100";
        for (SymbolFilter f:  pairInfo.getFilters()) {
            if(f.getStepSize() != null){
                step = f.getStepSize();
                break;
            }
        }
        //logger.info("normalizeQuantity stepsize: " + pairInfo.getFilters().get(1).getStepSize());
        int scale = step.lastIndexOf("1") - 1;
        pairQuantity = pairQuantity.setScale(scale, RoundingMode.DOWN);
        return String.valueOf(pairQuantity.doubleValue());
    }

    private String downgrade(BigDecimal pairQuantity) {
        pairQuantity = pairQuantity.setScale(pairQuantity.scale() - 1, RoundingMode.DOWN);
        return pairQuantity.toString();
    }

    private Boolean isValidQty(String pair, String normalQuantity) {
        SymbolInfo pairInfo = exchangeInfo.getSymbolInfo(pair);
        //Double minQty = Double.valueOf(pairInfo.getFilters().get(1).getMinQty());
        //Double maxQty = Double.valueOf(pairInfo.getFilters().get(1).getMaxQty());
        Double minQty =  Double.valueOf("0");
        Double maxQty =  Double.valueOf("0");
        for (SymbolFilter f:  pairInfo.getFilters()) {
                //if("LOT_SIZE".equals(f.getFilterType())){
                if(f.getMaxQty() != null){
              //  logger.info("filters: pair:"+pair+ "filter type: "+f.getFilterType()+" minPrice:"+f.getMinPrice()+" maxPrice:"+f.getMaxPrice()+" minQty:"+f.getMinQty()
               //         +" maxQty:"+f.getMaxQty()+" stepSize:"+f.getStepSize()+" minNotional:"+f.getMinNotional()+"\n");
                minQty = Double.valueOf(f.getMinQty());
                maxQty = Double.valueOf(f.getMaxQty());
                break;
            }
        }
        //Double minQty = Double.valueOf(findFilter(pairInfo.getFilters(),"LOT_SIZE").map(SymbolFilter::getMinQty).orElse("0.00000100"));
        //Double maxQty = Double.valueOf(findFilter(pairInfo.getFilters(),"LOT_SIZE").map(SymbolFilter::getMaxQty).orElse("100000.0000000"));
      //  logger.info("isValid pair:"+pair+" minQty:"+minQty+" maxQty:"+maxQty+"\n");
        return Double.valueOf(normalQuantity) > minQty && Double.valueOf(normalQuantity) < maxQty;
    }

    private Boolean isNotional(BigDecimal qty, String pair) {
        SymbolInfo pairInfo = exchangeInfo.getSymbolInfo(pair);
        String minNotional = findFilter(pairInfo.getFilters(),"MIN_NOTIONAL").map(f->f.getMinNotional()).orElse("0.00000100");
        return qty.multiply(getPrice(pair)).compareTo(new BigDecimal(minNotional)) > 0;
    }

    private Boolean isAllPairTrading(PairTriangle pairTriangle) {
        boolean pair1 = exchangeInfo.getSymbolInfo(pairTriangle.getFirstPair()).getStatus().equals(SymbolStatus.TRADING);
        boolean pair2 = exchangeInfo.getSymbolInfo(pairTriangle.getSecondPair()).getStatus().equals(SymbolStatus.TRADING);
        boolean pair3 = exchangeInfo.getSymbolInfo(pairTriangle.getThirdPair()).getStatus().equals(SymbolStatus.TRADING);
        System.out.printf(" DriverBotImpl isAllPairTrading status:"+pairTriangle.getFirstPair()+":"+pair1+" "+pairTriangle.getSecondPair()+":"+pair2+" "+pairTriangle.getThirdPair()+":"+pair3+"\n");
        return pair1 && pair2 && pair3;
    }

    private String buyOrSell(String pair, String normalQuantity, int numPair, boolean direct) {
        logger.info("buyOrSell: pair:"+pair+" normalQuantiry:"+normalQuantity+" numPair:"+ numPair+" direct:"+direct);
        String amtAfterTransaction;
        NewOrderResponse response;
        String price = String.valueOf(getPrice(pair));
        switch (numPair) {
            case 1:
                amtAfterTransaction = apiRestClient.newOrder(NewOrder.marketBuy(pair, normalQuantity)).getExecutedQty();
                //amtAfterTransaction = apiRestClient.newOrder(NewOrder.limitBuy(pair, TimeInForce.GTC, normalQuantity,price)).getExecutedQty();
                break;
            case 2:
                if (direct) {
                    if (pair.contains("BTC") || pair.contains("ETH")) {
                        amtAfterTransaction = apiRestClient.newOrder(NewOrder.marketBuy(pair, normalQuantity)).getExecutedQty();
                        //amtAfterTransaction = apiRestClient.newOrder(NewOrder.limitBuy(pair,TimeInForce.GTC, normalQuantity, price)).getExecutedQty();
                    } else {
                        response = apiRestClient.newOrder(NewOrder.marketSell(pair, normalQuantity));
                        //response = apiRestClient.newOrder(NewOrder.limitSell(pair,TimeInForce.GTC, normalQuantity, price));
                        amtAfterTransaction = getAmtAfterSellTransaction(response, pair);
                    }
                } else {
                    if (pair.contains("BTC") || pair.contains("ETH")) {
                        response = apiRestClient.newOrder(NewOrder.marketSell(pair, normalQuantity));
                        //response = apiRestClient.newOrder(NewOrder.limitSell(pair,TimeInForce.GTC, normalQuantity, price));
                        amtAfterTransaction = getAmtAfterSellTransaction(response, pair);
                    } else {
                        amtAfterTransaction = apiRestClient.newOrder(NewOrder.marketBuy(pair, normalQuantity)).getExecutedQty();
                        //amtAfterTransaction = apiRestClient.newOrder(NewOrder.limitBuy(pair, TimeInForce.GTC, normalQuantity,price)).getExecutedQty();
                    }
                }
                break;
            case 3:
                response = apiRestClient.newOrder(NewOrder.marketSell(pair, normalQuantity));
                //response = apiRestClient.newOrder(NewOrder.limitSell(pair,TimeInForce.GTC, normalQuantity,price));
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
    private BigDecimal getOrderbookPrice(String pair, boolean isBuy) {
        OrderBook orderBook = orderBookMap.get(pair);
        return new BigDecimal(isBuy?orderBook.getAsks().get(0).getPrice():orderBook.getBids().get(0).getPrice());
    }
    private Optional<SymbolFilter> findFilter(List<SymbolFilter> fs, String fType){
        return  fs.stream().filter(f -> ((SymbolFilter)f).getFilterType().equals(fType)).findFirst();
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
                        System.err.println("refresh price error");
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e1) {
                            e1.printStackTrace();
                        }
                    }
                } while (timeoutCount <= 100);
                try {
                    Thread.sleep(100);
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
                        System.err.println("async get exchangeInfo error");
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
    private void initOrderBookeMap(){
        orderBookMap = new HashMap<>();
        try {
        orderBookMap.put("ADAUSDT", apiRestClient.getOrderBook("ADAUSDT", 10));
        Thread.sleep(100);
        orderBookMap.put("ADABNB", apiRestClient.getOrderBook("ADABNB", 10));
        Thread.sleep(100);
        orderBookMap.put("BNBUSDT", apiRestClient.getOrderBook("BNBUSDT", 10));
        Thread.sleep(100);
        orderBookMap.put("BTCUSDT", apiRestClient.getOrderBook("BTCUSDT", 10));
        Thread.sleep(100);
        orderBookMap.put("BNBETH", apiRestClient.getOrderBook("BNBETH", 10));
        Thread.sleep(100);
        orderBookMap.put("ETHUSDT", apiRestClient.getOrderBook("ETHUSDT", 10));
        Thread.sleep(100);
        orderBookMap.put("LTCUSDT", apiRestClient.getOrderBook("LTCUSDT", 10));
        Thread.sleep(100);
        orderBookMap.put("NEOUSDT", apiRestClient.getOrderBook("NEOUSDT", 10));
        Thread.sleep(100);
        orderBookMap.put("NEOBNB", apiRestClient.getOrderBook("NEOBNB", 10));
        Thread.sleep(100);
        orderBookMap.put("ADABNB", apiRestClient.getOrderBook("ADABNB", 10));
        Thread.sleep(100);
        orderBookMap.put("ADAUSDT", apiRestClient.getOrderBook("ADAUSDT", 10));
        Thread.sleep(100);
        orderBookMap.put("QTUMUSDT", apiRestClient.getOrderBook("QTUMUSDT", 10));
        Thread.sleep(100);
        orderBookMap.put("LTCBNB", apiRestClient.getOrderBook("LTCBNB", 10));
        Thread.sleep(100);
        orderBookMap.put("QTUMBNB", apiRestClient.getOrderBook("QTUMBNB", 10));
        Thread.sleep(100);
        orderBookMap.put("EOSUSDT", apiRestClient.getOrderBook("EOSUSDT", 10));
        Thread.sleep(100);
        orderBookMap.put("EOSBNB", apiRestClient.getOrderBook("EOSBNB", 10));
        Thread.sleep(100);
        orderBookMap.put("XEMETH", apiRestClient.getOrderBook("XEMETH", 10));
        Thread.sleep(100);
        orderBookMap.put("XEMBNB", apiRestClient.getOrderBook("XEMBNB", 10));
        Thread.sleep(100);
        orderBookMap.put("STEEMBNB", apiRestClient.getOrderBook("STEEMBNB", 10));
        Thread.sleep(100);
        orderBookMap.put("STEEMETH", apiRestClient.getOrderBook("STEEMETH", 10));
        } catch (InterruptedException e2) {
            e2.printStackTrace();
        }
    }
    private void startRefreshingBookOrder() {
        new Thread(() -> {
            while (true) {
                int timeoutCount = 0;
                do {
                    try {
                        for (Map.Entry<String, OrderBook> entry : orderBookMap.entrySet()) {
                            apiAsyncRestClient.getOrderBook(entry.getKey().toString(), 10,(OrderBook response) -> orderBookMap.put(entry.getKey().toString(),response));
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e2) {
                                e2.printStackTrace();
                            }                        }
                    } catch (BinanceApiException e) {
                        ++timeoutCount;
                        System.err.println("orderBook fresh error");
                        e.printStackTrace();
                        try {
                            Thread.sleep(200);
                        } catch (InterruptedException e1) {
                            e1.printStackTrace();
                        }
                    }
                } while (timeoutCount <= 100);

            }
        }).start();
    }
    @Autowired
    public void setFakeBalance(FakeBalance fakeBalance) {
        this.fakeBalance = fakeBalance;
    }

}