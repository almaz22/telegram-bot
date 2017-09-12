package org.sbt.devops;

import org.sbt.devops.updateshandlers.BuroBot;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.TelegramBotsApi;
import org.telegram.telegrambots.exceptions.TelegramApiException;

public class App {
    public static void main(String[] args) {
        ApiContextInitializer.init();
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi();
        try {
            telegramBotsApi.registerBot(new BuroBot());
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
