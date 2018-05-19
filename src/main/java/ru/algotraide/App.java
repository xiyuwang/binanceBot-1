package ru.algotraide;

import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import ru.algotraide.component.MainBot;
import ru.algotraide.config.AppConfig;

public class App {
    public static void main(String[] args) throws InterruptedException {
        ApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        ((MainBot)context.getBean("mainBot")).start();
    }
}
