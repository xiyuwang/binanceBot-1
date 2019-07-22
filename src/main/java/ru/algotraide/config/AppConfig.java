package ru.algotraide.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.algotraide.component.BalanceCache;
import ru.algotraide.component.DriverBot;
import ru.algotraide.component.FakeBalance;
import ru.algotraide.component.MainBot;
import ru.algotraide.component.impl.BalanceCacheImpl;
import ru.algotraide.component.impl.DriverBotImpl;
import ru.algotraide.component.impl.FakeBalanceImpl;
import ru.algotraide.component.impl.MainBotImpl;

@Configuration
public class AppConfig {
    @Value("${binanceApiKey}")
    private String apiKey;
    @Value("${binanceSecretKey}")
    private String secretKey;
    private boolean bnbCommission = true;

    @Bean
    public DriverBot driverBot(){
        return new DriverBotImpl(apiKey, secretKey, bnbCommission);
    }
    @Bean
    public BalanceCache balanceCache(){
        return new BalanceCacheImpl(apiKey, secretKey);
    }
    @Bean
    public FakeBalance fakeBalance(){
        return new FakeBalanceImpl();
    }
    @Bean
    public MainBot mainBot(){
        return new MainBotImpl(driverBot(), balanceCache(), fakeBalance());
    }
}
