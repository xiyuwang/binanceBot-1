package ru.algotraide;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import ru.algotraide.component.MainBot;
import ru.algotraide.config.AppConfig;

@SpringBootApplication
public class App {
    public static void main(String[] args) throws InterruptedException {
        //ApplicationContext context = new AnnotationConfigApplicationContext(AppConfig.class);
        ConfigurableApplicationContext context = SpringApplication.run(App.class, args);
        context.getBean(AppConfig.class).mainBot().start();
    }
}
