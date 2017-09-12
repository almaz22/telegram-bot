package org.sbt.devops.updateshandlers;

import org.sbt.devops.BotConfig;
import org.sbt.devops.Commands;
import org.sbt.devops.database.HSQLDBManager;
import org.sbt.devops.service.Emoji;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.objects.Message;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiException;
import org.telegram.telegrambots.logging.BotLogger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by SBT-Kamalov-AN on 21.08.2017.
 */
public class BuroBot extends TelegramLongPollingBot {

    private static final String LOGTAG = "BUROBOT";
    private static final String PATH = "C:\\Users\\SBT-Kamalov-AN\\Desktop\\DataPIR.xml";

    private static final int STARTSTATE = 0;
    private static final int MAINMENU = 1;
    private static final int DEPARTMENT_STATE = 2;
    private static final int NOTUPDATEDMODULE_STATE = 3;

    private static String DEPARTMENT;
    private static HashSet MODULES = new HashSet();

    private static boolean err = false;

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage()) {
                Message message = update.getMessage();
                if (message.hasText() || message.hasLocation()) {
                    handleIncomingMessage(message);
                }
            }
        } catch (Exception e) {
            BotLogger.error(LOGTAG, e);
        }
    }

    @Override
    public String getBotUsername() {
        return BotConfig.BUROBOT_USER; // <bot_username>
        //return BotConfig.TESTBOT_USER;
    }

    @Override
    public String getBotToken() {
        return BotConfig.BUROBOT_TOKEN; // <token>
        //return BotConfig.TESTBOT_TOKEN;
    }

    private void handleIncomingMessage(Message message) throws TelegramApiException {
        final int state = HSQLDBManager.getInstance().getState(message.getFrom().getId(), message.getChatId());
        if (!message.isUserMessage() && message.hasText()) {
            if (isCommandForOther(message.getText())) {
                return;
            } else if (message.getText().startsWith(Commands.STOPCOMMAND)){
                sendHideKeyboard(message.getFrom().getId(), message.getChatId(), message.getMessageId());
                return;
            }
        }
        SendMessage sendMessageRequest;
        switch (state) {
            case MAINMENU:
                sendMessageRequest = messageOnMainMenu(message);
                break;
            case DEPARTMENT_STATE:
            case NOTUPDATEDMODULE_STATE:
                sendMessageRequest = messageOnDepartmentMenu(message);
                break;
            default:
                sendMessageRequest = sendMessageDefault(message);
                break;
        }
        sendMessage(sendMessageRequest);
    }

    private void sendHideKeyboard(Integer userId, Long chatId, Integer messageId) throws TelegramApiException {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId.toString());
        sendMessage.enableMarkdown(true);
        sendMessage.setReplyToMessageId(messageId);
        sendMessage.setText(Emoji.WAVING_HAND_SIGN.toString());

        ReplyKeyboardRemove replyKeyboardRemove = new ReplyKeyboardRemove();
        replyKeyboardRemove.setSelective(true);
        sendMessage.setReplyMarkup(replyKeyboardRemove);

        sendMessage(sendMessage);
        HSQLDBManager.getInstance().insertState(userId, chatId, STARTSTATE);
    }

    private static boolean isCommandForOther(String text) {
        boolean isSimpleCommand = text.equals("/start") || text.equals("/stop");
        boolean isCommandForMe = text.equals("/start@SBT_BuroBot") || text.equals("/stop@SBT_BuroBot")
                || text.equals("/start@SBT_testBot") || text.equals("/stop@SBT_testBot");
        return text.startsWith("/") && !isSimpleCommand && !isCommandForMe;
    }

    private static SendMessage messageOnMainMenu(Message message) {
        SendMessage sendMessageRequest;
        if (message.hasText()) {
            if (message.getText().equals(getONTABSCommand())) {
                sendMessageRequest = onDepartmentChoosen(message, getONTABSCommand());
                DEPARTMENT = message.getText();
            } else if (message.getText().equals(getONTARCommand())) {
                sendMessageRequest = onDepartmentChoosen(message, getONTARCommand());
                DEPARTMENT = message.getText();
            } else if (message.getText().equals(getONTIBIURCommand())) {
                sendMessageRequest = onDepartmentChoosen(message, getONTIBIURCommand());
                DEPARTMENT = message.getText();
            } else if (message.getText().equals(getONTSABPCommand())) {
                sendMessageRequest = onDepartmentChoosen(message, getONTSABPCommand());
                DEPARTMENT = message.getText();
            } else if (message.getText().equals(getONTFSIEKOCommand())) {
                sendMessageRequest = onDepartmentChoosen(message, getONTFSIEKOCommand());
                DEPARTMENT = message.getText();
            } else if (message.getText().equals(getONTFSIEKOEFSCommand())) {
                sendMessageRequest = onDepartmentChoosen(message, getONTFSIEKOEFSCommand());
                DEPARTMENT = message.getText();
            } else {
                sendMessageRequest = sendChooseOptionMessage(message.getChatId(), message.getMessageId(), getMainMenuKeyboard());
            }
        } else {
            sendMessageRequest = sendChooseOptionMessage(message.getChatId(), message.getMessageId(), getMainMenuKeyboard());
        }

        return sendMessageRequest;
    }

    private static SendMessage messageOnDepartmentMenu (Message message) {
        final int state = HSQLDBManager.getInstance().getState(message.getFrom().getId(), message.getChatId());
        SendMessage sendMessageRequest = null;
        switch (state) {
            case DEPARTMENT_STATE:
                sendMessageRequest = onDepartmentMenu(message);
                break;
            case NOTUPDATEDMODULE_STATE:
                sendMessageRequest = onNotUpdatedModulesMenu(message);
                break;
        }

        return sendMessageRequest;
    }
    private static SendMessage onDepartmentMenu (Message message) {
        SendMessage sendMessageRequest = null;
        if (message.hasText()) {
            if (MODULES.contains(message.getText())) {
                sendMessageRequest = sendModuleStatusMessage (message.getFrom().getId(), message.getChatId(), message.getMessageId(), message.getText(),true);
            } else if (message.getText().equals("АС с неактуальной информацией")) {
                sendMessageRequest = onNotUpdatedModulesChoosen (message.getFrom().getId(), message.getChatId(), message.getMessageId());
            } else if (message.getText().equals("Назад")) {
                sendMessageRequest = onCancelCommand(message.getFrom().getId(), message.getChatId(), message.getMessageId(), getMainMenuKeyboard());
            }
        }
        return sendMessageRequest;
    }

    private static SendMessage onNotUpdatedModulesMenu (Message message) {
        SendMessage sendMessageRequest = null;

        if (message.hasText()) {
            if (MODULES.contains(message.getText())) {
                sendMessageRequest = sendModuleStatusMessage (message.getFrom().getId(), message.getChatId(), message.getMessageId(), message.getText(), false);
            } else if (message.getText().equals("Назад")) {
                sendMessageRequest = onBackCommand(message.getFrom().getId(), message.getChatId(), message.getMessageId(), getRecentsKeyboard(DEPARTMENT,true));
            }
        }

        return sendMessageRequest;
    }

    private static SendMessage sendModuleStatusMessage(Integer userId, Long chatId, Integer messageId, String moduleName, boolean updated) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.enableMarkdown(true);

        ReplyKeyboardMarkup replyKeyboardMarkup = getRecentsKeyboard(DEPARTMENT, updated);
        sendMessage.setReplyMarkup(replyKeyboardMarkup);
        sendMessage.setChatId(chatId.toString());
        sendMessage.setReplyToMessageId(messageId);
        sendMessage.setParseMode("HTML");
        sendMessage.setText(getModuleStatus(DEPARTMENT, moduleName));

        if (updated)
            HSQLDBManager.getInstance().insertState(userId, chatId, DEPARTMENT_STATE);
        else
            HSQLDBManager.getInstance().insertState(userId, chatId, NOTUPDATEDMODULE_STATE);
        return sendMessage;
    }

    private static SendMessage onNotUpdatedModulesChoosen (Integer userId, Long chatId, Integer messageId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.enableMarkdown(true);

        ReplyKeyboardMarkup replyKeyboardMarkup = getRecentsKeyboard(DEPARTMENT, false);
        if (replyKeyboardMarkup.getKeyboard().size() > 1) {
            sendMessage.setReplyMarkup(replyKeyboardMarkup);
            sendMessage.setChatId(chatId.toString());
            sendMessage.setReplyToMessageId(messageId);
            sendMessage.setParseMode("HTML");
            sendMessage.setText("Список АС с неактуальной информацией");
            HSQLDBManager.getInstance().insertState(userId, chatId, NOTUPDATEDMODULE_STATE);
        }
        else {
            replyKeyboardMarkup = getRecentsKeyboard(DEPARTMENT, true);
            sendMessage.setReplyMarkup(replyKeyboardMarkup);
            sendMessage.setChatId(chatId.toString());
            sendMessage.setReplyToMessageId(messageId);
            sendMessage.setText("В отделе " + DEPARTMENT + " нет АС с неактуальной информацией. Супер!");
            HSQLDBManager.getInstance().insertState(userId, chatId, DEPARTMENT_STATE);
        }
        return sendMessage;
    }

    private static SendMessage onDepartmentChoosen (Message message, String departmentName) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.enableMarkdown(true);

        ReplyKeyboardMarkup replyKeyboardMarkup = getRecentsKeyboard(departmentName, true);
        sendMessage.setReplyMarkup(replyKeyboardMarkup);
        sendMessage.setReplyToMessageId(message.getMessageId());
        sendMessage.setChatId(message.getChatId());
        sendMessage.setText("Вы в отделе " + departmentName + ". Выберите нужную АС");

        HSQLDBManager.getInstance().insertState(message.getFrom().getId(), message.getChatId(), DEPARTMENT_STATE);
        return sendMessage;
    }

    private static ReplyKeyboardMarkup getRecentsKeyboard(String departmentName, boolean updated) {
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        replyKeyboardMarkup.setSelective(true);
        replyKeyboardMarkup.setResizeKeyboard(true);
        replyKeyboardMarkup.setOneTimeKeyboard(true);

        getDepModules(departmentName, updated);
        Object[] objects = MODULES.toArray();
        List<KeyboardRow> keyboard = new ArrayList<>();

        if (updated) {
            KeyboardRow row = new KeyboardRow();
            row.add("АС с неактуальной информацией");
            keyboard.add(row);
        }

        for (Object obj : objects) {
            KeyboardRow row = new KeyboardRow();
            row.add(obj.toString());
            keyboard.add(row);
        }

        KeyboardRow row = new KeyboardRow();
        row.add("Назад");
        keyboard.add(row);

        replyKeyboardMarkup.setKeyboard(keyboard);

        return replyKeyboardMarkup;
    }

    private static ArrayList<String> getDepModules(String departmentName, boolean updated) {
        if (!MODULES.isEmpty()) {
            MODULES.clear();
        }
        ArrayList<String> result = new ArrayList<>();
        if (new File(PATH).exists()) {
            DocumentBuilder documentBuilder;
            try {
                documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                Document document = documentBuilder.parse(PATH);
                NodeList departments = document.getElementsByTagName("Department");
                for (int i = 0; i < departments.getLength(); i++) {
                    Node department = departments.item(i);
                    if (department.getAttributes().getNamedItem("name").getNodeValue().equals(departmentName)) {
                        NodeList modules = department.getChildNodes();
                        for(int j = 0; j < modules.getLength(); j++) {
                            Node module = modules.item(j);
                            try {
                                String moduleName = module.getAttributes().getNamedItem("name").getNodeValue();
                                err = false;
                                if (updated)
                                    MODULES.add(moduleName);
                                else {
                                    getModuleStatus(departmentName, moduleName);
                                    if (err) {
                                        MODULES.add(moduleName);
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                    }

                }
            } catch (ParserConfigurationException | IOException | SAXException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    private static String getModuleStatus(String departmentName, String moduleName) {
        String result = "";
        String PIR;
        String updateDate = "";
        String change_time = "";
        String mnt_progress = "";
        String mnt_stop = "";
        String stand_progress = "";
        String stand_stop = "";
        String snt_progress = "";
        String snt_stop = "";
        String nt_progress = "";
        String nt_stop = "";
        ArrayList<String> responsibles = new ArrayList<>();

        if (new File(PATH).exists()) {
            DocumentBuilder documentBuilder;
            try {
                documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                Document document = documentBuilder.parse(PATH);
                Element root = document.getDocumentElement();
                PIR = root.getAttribute("name");
                NodeList departments = document.getElementsByTagName("Department");
                for (int i = 0; i < departments.getLength(); i++) {
                    Node department = departments.item(i);
                    if (department.getAttributes().getNamedItem("name").getNodeValue().equals(departmentName)) {
                        NodeList modules = department.getChildNodes();
                        for(int j = 0; j < modules.getLength(); j++) {
                            Node module = modules.item(j);
                            try {
                                if (module.getAttributes().getNamedItem("name").getNodeValue().equals(moduleName)) {
                                    result = "<b>Информация по " + PIR + "</b>\n";
                                    result = result + "АС: " + moduleName;
                                    NodeList attributes = module.getChildNodes();
                                    for (int ind = 0; ind < attributes.getLength(); ind ++) {
                                        Node attribute = attributes.item(ind);
                                        switch (attribute.getNodeName()) {
                                            case "updateDate":
                                                updateDate = attribute.getTextContent();
                                                break;
                                            case "change_time":
                                                change_time = attribute.getTextContent();
                                                break;
                                            case "mnt_progress":
                                                mnt_progress = attribute.getTextContent();
                                                break;
                                            case "mnt_stop":
                                                mnt_stop = attribute.getTextContent();
                                                break;
                                            case "stand_progress":
                                                stand_progress = attribute.getTextContent();
                                                break;
                                            case "stand_stop":
                                                stand_stop = attribute.getTextContent();
                                                break;
                                            case "snt_progress":
                                                snt_progress = attribute.getTextContent();
                                                break;
                                            case "snt_stop":
                                                snt_stop = attribute.getTextContent();
                                                break;
                                            case "nt_progress":
                                                nt_progress = attribute.getTextContent();
                                                break;
                                            case "nt_stop":
                                                nt_stop = attribute.getTextContent();
                                                break;
                                            case "responsibles":
                                                if (attribute.getChildNodes().getLength() > 0)
                                                    for (int k = 0; k < attribute.getChildNodes().getLength(); k++)
                                                        responsibles.add(attribute.getChildNodes().item(k).getTextContent());
                                                break;
                                            default:
                                                break;
                                        }
                                    }
                                    result = result + statusMessage(updateDate, change_time, mnt_progress, mnt_stop, stand_progress,
                                            stand_stop, snt_progress, snt_stop, nt_progress, nt_stop, responsibles);
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            } catch (ParserConfigurationException | IOException | SAXException e) {
                e.printStackTrace();
            }
        }
        return result;
    }
    
    private static String statusMessage (
                    String updateDate,      String change_time,
                    String mnt_progress,    String mnt_stop,
                    String stand_progress,  String stand_stop,
                    String snt_progress,    String snt_stop,
                    String nt_progress,     String nt_stop,
                    ArrayList<String> responsibles)
    {
        int MAX_DAY_UPDATE = 3;
        long millisForDays = 24 * 3600 * 1000; // миллисекунд в сутках

        Date curDate = new GregorianCalendar(Calendar.getInstance().get(Calendar.YEAR),
                                             Calendar.getInstance().get(Calendar.MONTH),
                                             Calendar.getInstance().get(Calendar.DAY_OF_MONTH)).getTime();
        DateFormat dt = new SimpleDateFormat("dd.MM.yyyy");
        DateFormat dt2 = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

        StringBuilder result = new StringBuilder();
        try {
            result.append("\nСинхронизация: ").append(updateDate);
            if ((   Double.parseDouble(mnt_progress) < 1.0 | Double.parseDouble(stand_progress) < 1.0 |
                    Double.parseDouble(snt_progress) < 1.0 | Double.parseDouble(nt_progress) < 1.0) &&
                    System.currentTimeMillis() - (millisForDays * MAX_DAY_UPDATE) > dt2.parse(change_time).getTime()) {
                result.append("\n<b>Последнее обновление: ").append(change_time).append("</b>");
                err = true;
            }
            else
                result.append("\nПоследнее обновление: ").append(change_time);
            if (dt.parse(mnt_stop).getTime() < curDate.getTime() && Double.parseDouble(mnt_progress) != 1.0) {
                result.append("\n<b>МНТ (выполнено %): ").append(mnt_progress).append("</b>");
                result.append("\n<b>МНТ срок: ").append(mnt_stop).append("</b>");
                err = true;
            }
            else {
                result.append("\nМНТ (выполнено %): ").append(mnt_progress);
                result.append("\nМНТ срок: ").append(mnt_stop);
            }
            if (dt.parse(stand_stop).getTime() < curDate.getTime() && Double.parseDouble(stand_progress) != 1.0) {
                result.append("\n<b>Стенд (выполнено %): ").append(stand_progress).append("</b>");
                result.append("\n<b>Стенд срок: ").append(stand_stop).append("</b>");
                err = true;
            }
            else {
                result.append("\nСтенд (выполнено %): ").append(stand_progress);
                result.append("\nСтенд срок: ").append(stand_stop);
            }
            if (dt.parse(snt_stop).getTime() < curDate.getTime() && Double.parseDouble(snt_progress) != 1.0) {
                result.append("\n<b>СНТ (выполнено %): ").append(snt_progress).append("</b>");
                result.append("\n<b>СНТ срок: ").append(snt_stop).append("</b>");
                err = true;
            }
            else {
                result.append("\nСНТ (выполнено %): ").append(snt_progress);
                result.append("\nСНТ срок: ").append(snt_stop);
            }
            if (dt.parse(nt_stop).getTime() < curDate.getTime() && Double.parseDouble(nt_progress) != 1.0) {
                result.append("\n<b>НТ (выполнено %): ").append(nt_progress).append("</b>");
                result.append("\n<b>НТ срок: ").append(nt_stop).append("</b>");
                err = true;
            }
            else {
                result.append("\nНТ (выполнено %): ").append(nt_progress);
                result.append("\nНТ срок: ").append(nt_stop);
            }
            for (String responsible : responsibles) {
                result.append("\nОтветственный: ").append(responsible);
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return result.toString();
    }

    private static SendMessage sendMessageDefault(Message message) {
        ReplyKeyboardMarkup replyKeyboardMarkup = getMainMenuKeyboard();
        HSQLDBManager.getInstance().insertState(message.getFrom().getId(), message.getChatId(), MAINMENU);
        return sendHelpMessage(message.getChatId(), message.getMessageId(), replyKeyboardMarkup);
    }

    private static SendMessage sendHelpMessage(Long chatId, Integer messageId, ReplyKeyboardMarkup replyKeyboardMarkup) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.enableMarkdown(true);
        sendMessage.setChatId(chatId);
        sendMessage.setReplyToMessageId(messageId);
        if (replyKeyboardMarkup != null) {
            sendMessage.setReplyMarkup(replyKeyboardMarkup);
        }
        sendMessage.setText(getHelpMessage());
        return sendMessage;
    }

    private static SendMessage onBackCommand(Integer userId, Long chatId, Integer messageId, ReplyKeyboard replyKeyboard) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId.toString());
        sendMessage.enableMarkdown(true);
        sendMessage.setReplyToMessageId(messageId);
        sendMessage.setReplyMarkup(replyKeyboard);
        sendMessage.setText("Назад");

        HSQLDBManager.getInstance().insertState(userId, chatId, DEPARTMENT_STATE);
        return sendMessage;
    }

    private static SendMessage onCancelCommand(Integer userId, Long chatId, Integer messageId, ReplyKeyboard replyKeyboard) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId.toString());
        sendMessage.enableMarkdown(true);
        sendMessage.setReplyToMessageId(messageId);
        sendMessage.setReplyMarkup(replyKeyboard);
        sendMessage.setText("Возврат в главное меню");

        HSQLDBManager.getInstance().insertState(userId, chatId, MAINMENU);
        return sendMessage;
    }

    private static String getHelpMessage() {
        return "Хочешь знать, как дела в ПИРе?" +
                "\nВыбери нужный отдел и ты узнаешь намного больше!";
    }

    private static ReplyKeyboardMarkup getMainMenuKeyboard() {
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        replyKeyboardMarkup.setSelective(true);
        replyKeyboardMarkup.setResizeKeyboard(true);
        replyKeyboardMarkup.setOneTimeKeyboard(false);

        List<KeyboardRow> keyboard = new ArrayList<>();
        KeyboardRow keyboardFirstRow = new KeyboardRow();
        keyboardFirstRow.add(getONTABSCommand());
        keyboardFirstRow.add(getONTARCommand());
        KeyboardRow keyboardSecondRow = new KeyboardRow();
        keyboardSecondRow.add(getONTIBIURCommand());
        keyboardSecondRow.add(getONTSABPCommand());
        KeyboardRow keyboardThirdRow = new KeyboardRow();
        keyboardThirdRow.add(getONTFSIEKOCommand());
        keyboardThirdRow.add(getONTFSIEKOEFSCommand());
        keyboard.add(keyboardFirstRow);
        keyboard.add(keyboardSecondRow);
        keyboard.add(keyboardThirdRow);
        replyKeyboardMarkup.setKeyboard(keyboard);

        return replyKeyboardMarkup;
    }

    private static SendMessage sendChooseOptionMessage(Long chatId, Integer messageId, ReplyKeyboard replyKeyboard) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.enableMarkdown(true);
        sendMessage.setChatId(chatId.toString());
        sendMessage.setReplyToMessageId(messageId);
        sendMessage.setReplyMarkup(replyKeyboard);
        sendMessage.setText("Пожалуйста, выберите опцию из меню");

        return sendMessage;
    }

    private static String getONTABSCommand() {
        return "ОНТАБС";
    }
    private static String getONTIBIURCommand() {
        return "ОНТИБИУР";
    }
    private static String getONTFSIEKOCommand() {
        return "ОНТФСИЭКО";
    }
    private static String getONTSABPCommand() {
        return "ОНТСАБП";
    }
    private static String getONTFSIEKOEFSCommand() {
        return "ОНТФСИЭКО ЕФС";
    }
    private static String getONTARCommand() {
        return "ОНТАР";
    }
}
