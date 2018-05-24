package ru.algotraide.component.impl;

import com.binance.api.client.BinanceApiClientFactory;
import com.binance.api.client.BinanceApiRestClient;
import com.binance.api.client.BinanceApiWebSocketClient;
import com.binance.api.client.domain.account.Account;
import com.binance.api.client.domain.account.AssetBalance;
import com.binance.api.client.domain.market.TickerPrice;
import ru.algotraide.component.BalanceCache;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.binance.api.client.domain.event.UserDataUpdateEvent.UserDataUpdateEventType.ACCOUNT_UPDATE;

public class BalanceCacheImpl implements BalanceCache {
    private final BinanceApiClientFactory clientFactory;

    /**
     * Key is the symbol, and the value is the balance of that symbol on the account.
     */
    private Map<String, AssetBalance> accountBalanceCache;
    private Map<String, AssetBalance> oldAccountBalanceCache;
    private BinanceApiRestClient restClient;

    /**
     * Listen key used to interact with the user data streaming API.
     */
    private final String listenKey;

    public BalanceCacheImpl(String apiKey, String secret) {
        this.clientFactory = BinanceApiClientFactory.newInstance(apiKey, secret);
        this.restClient = clientFactory.newRestClient();
        oldAccountBalanceCache = new TreeMap<>();
        this.listenKey = initializeAssetBalanceCacheAndStreamSession();
        startAccountBalanceEventStreaming(listenKey);
    }

    /**
     * Initializes the asset balance cache by using the REST API and starts a new user data streaming session.
     *
     * @return a listenKey that can be used with the user data streaming API.
     */
    private String initializeAssetBalanceCacheAndStreamSession() {
        Account account = restClient.getAccount();
        this.accountBalanceCache = new TreeMap<>();
        for (AssetBalance assetBalance : account.getBalances()) {
            accountBalanceCache.put(assetBalance.getAsset(), assetBalance);
            oldAccountBalanceCache.putAll(accountBalanceCache);
        }
        return restClient.startUserDataStream();
    }

    /**
     * Begins streaming of agg trades events.
     */
    private void startAccountBalanceEventStreaming(String listenKey) {
        BinanceApiWebSocketClient client = clientFactory.newWebSocketClient();

        client.onUserDataUpdateEvent(listenKey, response -> {
            if (response.getEventType() == ACCOUNT_UPDATE) {
                // Override cached asset balances
                for (AssetBalance assetBalance : response.getAccountUpdateEvent().getBalances()) {
                    accountBalanceCache.put(assetBalance.getAsset(), assetBalance);
                }
                viewInfo();
            }
        });
    }

    /**
     * @return an account balance cache, containing the balance for every asset in this account.
     */
    public Map<String, AssetBalance> getAccountBalanceCache() {
        return accountBalanceCache;
    }

    public void viewInfo(){
        new Thread(() -> {
            List<TickerPrice> tickerPrice = restClient.getAllPrices();
            String BNBUSDTPrice = tickerPrice.stream().filter(s -> s.getSymbol().equals("BNBUSDT")).findFirst().get().getPrice();
            String BnbOnBalanceFree = accountBalanceCache.get("BNB").getFree();
            String BnbOnBalanceLocked = accountBalanceCache.get("BNB").getLocked();
            BigDecimal allBnb = new BigDecimal(BnbOnBalanceFree).add(new BigDecimal(BnbOnBalanceLocked));
            BigDecimal bnbInDollars = allBnb.multiply(new BigDecimal(BNBUSDTPrice));
            BigDecimal UsdtProfit = new BigDecimal(accountBalanceCache.get("USDT").getFree())
                    .subtract(new BigDecimal(oldAccountBalanceCache.get("USDT").getFree()));
            BigDecimal BnbProfit = new BigDecimal(accountBalanceCache.get("BNB").getFree())
                    .subtract(new BigDecimal(oldAccountBalanceCache.get("BNB").getFree()));
            System.out.println("------");
            System.out.println("До | USDT: " + oldAccountBalanceCache.get("USDT").getFree() + ", BNB: " +
                    oldAccountBalanceCache.get("BNB").getFree() + ", USDT+BNB в долларах: " + bnbInDollars + " |");
            System.out.println("После | USDT: " + accountBalanceCache.get("USDT").getFree() + ", BNB: " +
                    accountBalanceCache.get("BNB").getFree() +
                    ", USDT+BNB в долларах: " + bnbInDollars + " |");
            System.out.println("Доход | USDT: " + UsdtProfit + ", BNB: " + BnbProfit + " |");
            System.out.println("------");
            oldAccountBalanceCache.putAll(accountBalanceCache);
        }).start();
    }
}
