package org.sbt.devops.updateshandlers;

import org.sbt.devops.BotConfig;
import org.sbt.devops.BuildVars;
import org.sbt.devops.Commands;
import org.sbt.devops.database.HSQLDBManager;
import org.sbt.devops.service.Emoji;
import org.telegram.telegrambots.api.methods.send.SendMessage;
import org.telegram.telegrambots.api.objects.Message;
import org.telegram.telegrambots.api.objects.ResponseParameters;
import org.telegram.telegrambots.api.objects.Update;
import org.telegram.telegrambots.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.exceptions.TelegramApiException;
import org.telegram.telegrambots.logging.BotLogger;
import org.w3c.dom.Document;
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
    private static final String PATH = BuildVars.FILE_PATH; // <myDataFile.xml>

    private static final int STARTSTATE = 0;
    private static final int MAINMENU = 1;
    private static final int DEPARTMENT_STATE = 2;
    private static final int MODULE_STATE = 3;
    private static final int NOT_UPDATED_MODULE_STATE = 4;
    private static final int NOTIFICATION_STATE = 5;
    private static final int AFTER_NOTIFICATION_STATE = 6;

    private static String DEPARTMENT;
    private static String PIRNAME;

    private static HashSet<String > MODULES = new HashSet<>();
    private static HashSet<String> PIR = new HashSet<>();
    private static HashSet<String> DEPARTMENTS = new HashSet<>();

    private static boolean err = false;

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasMessage()) {
                Message message = update.getMessage();
                if (message.hasText()) {
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
        HashSet<Integer> users = BuildVars.getUSERS();
        if (users.contains(message.getFrom().getId())) {
            final int state = HSQLDBManager.getInstance().getState(message.getFrom().getId(), message.getChatId());
            if (!message.isUserMessage() && message.hasText()) {
                if (isCommandForOther(message.getText())) {
                    return;
                } else if (message.getText().startsWith(Commands.STOPCOMMAND)) {
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
                    sendMessageRequest = messageOnDepartmentMenu(message);
                    break;
                case MODULE_STATE:
                case NOT_UPDATED_MODULE_STATE:
                    sendMessageRequest = messageOnModuleMenu(message);
                    break;
                case NOTIFICATION_STATE:
                    sendMessageRequest = messageOnNotificationMenu(message);
                    break;
                case AFTER_NOTIFICATION_STATE:
                    try {
                        sendMessage(sendNotification());
                        sendMessageRequest = messageOnAfterNotificationMenu(message,true);
                    } catch (Exception e) {
                        sendMessageRequest = messageOnAfterNotificationMenu(message, false);
                        BotLogger.error(LOGTAG, e.getMessage());
                    }
                    break;
                default:
                    sendMessageRequest = sendMessageDefault(message);
                    break;
            }
            if (sendMessageRequest == null) {
                sendMessageRequest = sendMessageOnError(message);
            }
            sendMessage(sendMessageRequest);
        }
        else
            sendMessage(sendMessageNoRights(message));
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
        if (PIR.isEmpty()) {
            getPIR();
        }
        if (message.hasText()) {
            if (PIR.contains(message.getText())) {
                sendMessageRequest = onPIRChoosen(message, message.getText());
                PIRNAME = message.getText();
            } else {
                sendMessageRequest = sendChooseOptionMessage(message.getChatId(), message.getMessageId(), getMainMenuKeyboard());
            }
        } else {
            sendMessageRequest = sendChooseOptionMessage(message.getChatId(), message.getMessageId(), getMainMenuKeyboard());
        }
        return sendMessageRequest;
    }

    private static SendMessage messageOnDepartmentMenu(Message message) {
        SendMessage sendMessageRequest;
        if (DEPARTMENTS.isEmpty())
            sendMessageRequest = sendMessageOnError(message);
        else {
            if (message.hasText()) {
                if (DEPARTMENTS.contains(message.getText())) {
                    sendMessageRequest = onDepartmentChoosen(message);
                    DEPARTMENT = message.getText();
                } else if (message.getText().equals("Назад")) {
                    sendMessageRequest = onCancelCommand(message.getFrom().getId(), message.getChatId(), message.getMessageId(), getMainMenuKeyboard());
                } else {
                    sendMessageRequest = sendChooseOptionMessage(message.getChatId(), message.getMessageId(), getMainMenuKeyboard());
                }
            } else {
                sendMessageRequest = sendChooseOptionMessage(message.getChatId(), message.getMessageId(), getMainMenuKeyboard());
            }
        }
        return sendMessageRequest;
    }

    private static SendMessage messageOnModuleMenu(Message message) {
        final int state = HSQLDBManager.getInstance().getState(message.getFrom().getId(), message.getChatId());
        SendMessage sendMessageRequest = null;
        switch (state) {
            case MODULE_STATE:
                sendMessageRequest = onModuleMenu(message);
                break;
            case NOT_UPDATED_MODULE_STATE:
                sendMessageRequest = onNotUpdatedModulesMenu(message);
                break;
        }

        return sendMessageRequest;
    }
    private static SendMessage onModuleMenu(Message message) {
        SendMessage sendMessageRequest = null;
        if (message.hasText()) {
            if (MODULES.contains(message.getText())) {
                sendMessageRequest = sendModuleStatusMessage (message.getFrom().getId(), message.getChatId(), message.getMessageId(), message.getText(),true);
            } else if (message.getText().equals("АС с неактуальной информацией")) {
                sendMessageRequest = onNotUpdatedModulesChoosen (message.getFrom().getId(), message.getChatId(), message.getMessageId());
            } else if (message.getText().equals("Назад")) {
                sendMessageRequest = onBackCommand(message.getFrom().getId(), message.getChatId(), message.getMessageId(), getRecentsKeyboardForDepartments(PIRNAME));
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
                sendMessageRequest = onBackCommand(message.getFrom().getId(), message.getChatId(), message.getMessageId(), getRecentsKeyboardForModules(DEPARTMENT,true));
            } else if (message.getText().equals("Отправить уведомление начальнику отдела?")) {
                sendMessageRequest = sendNotificationMenu(message.getFrom().getId(), message.getChatId(), message.getMessageId());
            }
        }

        return sendMessageRequest;
    }

    private static SendMessage sendModuleStatusMessage(Integer userId, Long chatId, Integer messageId, String moduleName, boolean updated) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.enableMarkdown(true);

        ReplyKeyboardMarkup replyKeyboardMarkup = getRecentsKeyboardForModules(DEPARTMENT, updated);

        if (!updated) {
            List<KeyboardRow> keyboard = replyKeyboardMarkup.getKeyboard();
            KeyboardRow row = new KeyboardRow();
            row.add("Отправить уведомление начальнику отдела?");
            keyboard.add(row);
            replyKeyboardMarkup.setKeyboard(keyboard);
        }

        sendMessage.setReplyMarkup(replyKeyboardMarkup);
        sendMessage.setChatId(chatId.toString());
        sendMessage.setReplyToMessageId(messageId);
        sendMessage.setParseMode("HTML");
        sendMessage.setText(getModuleStatus(DEPARTMENT, moduleName));

        if (updated)
            HSQLDBManager.getInstance().insertState(userId, chatId, MODULE_STATE);
        else
            HSQLDBManager.getInstance().insertState(userId, chatId, NOT_UPDATED_MODULE_STATE);
        return sendMessage;
    }

    private static SendMessage onNotUpdatedModulesChoosen (Integer userId, Long chatId, Integer messageId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.enableMarkdown(true);

        ReplyKeyboardMarkup replyKeyboardMarkup = getRecentsKeyboardForModules(DEPARTMENT, false);
        if (replyKeyboardMarkup.getKeyboard().size() > 1) {

            List<KeyboardRow> keyboard = replyKeyboardMarkup.getKeyboard();
            KeyboardRow row = new KeyboardRow();
            row.add("Отправить уведомление начальнику отдела?");
            keyboard.add(row);
            replyKeyboardMarkup.setKeyboard(keyboard);

            sendMessage.setReplyMarkup(replyKeyboardMarkup);
            sendMessage.setChatId(chatId.toString());
            sendMessage.setReplyToMessageId(messageId);
            //sendMessage.setParseMode("HTML");
            sendMessage.setText("Список АС с неактуальной информацией");
            HSQLDBManager.getInstance().insertState(userId, chatId, NOT_UPDATED_MODULE_STATE);
        }
        else {
            replyKeyboardMarkup = getRecentsKeyboardForModules(DEPARTMENT, true);
            sendMessage.setReplyMarkup(replyKeyboardMarkup);
            sendMessage.setChatId(chatId.toString());
            sendMessage.setReplyToMessageId(messageId);
            sendMessage.setText("В отделе " + DEPARTMENT + " нет АС с неактуальной информацией. Супер!");
            HSQLDBManager.getInstance().insertState(userId, chatId, MODULE_STATE);
        }
        return sendMessage;
    }

    private static SendMessage onPIRChoosen (Message message, String pir) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.enableMarkdown(true);

        ReplyKeyboardMarkup replyKeyboardMarkup = getRecentsKeyboardForDepartments(pir);
        sendMessage.setReplyMarkup(replyKeyboardMarkup);
        sendMessage.setReplyToMessageId(message.getMessageId());
        sendMessage.setChatId(message.getChatId());
        sendMessage.setText("Выберите отдел");

        HSQLDBManager.getInstance().insertState(message.getFrom().getId(), message.getChatId(), DEPARTMENT_STATE);
        return sendMessage;
    }

    private static SendMessage onDepartmentChoosen (Message message) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.enableMarkdown(true);

        ReplyKeyboardMarkup replyKeyboardMarkup = getRecentsKeyboardForModules(message.getText(), true);
        sendMessage.setReplyMarkup(replyKeyboardMarkup);
        sendMessage.setReplyToMessageId(message.getMessageId());
        sendMessage.setChatId(message.getChatId());
        sendMessage.setText("Вы в отделе " + message.getText() + ". Выберите нужную АС");

        HSQLDBManager.getInstance().insertState(message.getFrom().getId(), message.getChatId(), MODULE_STATE);
        return sendMessage;
    }

    private static ReplyKeyboardMarkup getRecentsKeyboardForDepartments(String pir) {
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        replyKeyboardMarkup.setSelective(true);
        replyKeyboardMarkup.setResizeKeyboard(true);
        replyKeyboardMarkup.setOneTimeKeyboard(true);

        getDepartments(pir);
        Object[] objects = DEPARTMENTS.toArray();
        List<KeyboardRow> keyboard = new ArrayList<>();

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

    private static ReplyKeyboardMarkup getRecentsKeyboardForModules(String departmentName, boolean updated) {
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        replyKeyboardMarkup.setSelective(true);
        replyKeyboardMarkup.setResizeKeyboard(true);
        replyKeyboardMarkup.setOneTimeKeyboard(true);

        getDepModules(PIRNAME, departmentName, updated);
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

    private static void getPIR() {
        if (!PIR.isEmpty())
            PIR.clear();
        if (new File(PATH).exists()) {
            DocumentBuilder documentBuilder;
            try {
                documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                Document document = documentBuilder.parse(PATH);
                NodeList pirList = document.getElementsByTagName("PIR");
                for (int i = 0; i < pirList.getLength(); i++) {
                    Node pir = pirList.item(i);
                    PIR.add(pir.getAttributes().getNamedItem("name").getNodeValue().trim());
                }
            } catch (SAXException | ParserConfigurationException | IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void getDepartments(String pirName) {
        if (!DEPARTMENTS.isEmpty())
            DEPARTMENTS.clear();
        if (new File(PATH).exists()) {
            DocumentBuilder documentBuilder;
            try {
                documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                Document document = documentBuilder.parse(PATH);
                NodeList pirList = document.getElementsByTagName("PIR");
                for (int i = 0; i < pirList.getLength(); i++) {
                    Node pir = pirList.item(i);
                    if (pir.getAttributes().getNamedItem("name").getNodeValue().trim().equals(pirName)) {
                        NodeList departments = pir.getChildNodes();
                        for (int j = 0; j < departments.getLength(); j++) {
                            Node department = departments.item(j);
                            try {
                                DEPARTMENTS.add(department.getAttributes().getNamedItem("name").getNodeValue());
                            } catch (Exception ignored) {}
                        }
                    }
                }
            } catch (SAXException | ParserConfigurationException | IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void getDepModules(String pirName, String departmentName, boolean updated) {
        if (!MODULES.isEmpty())
            MODULES.clear();
        if (new File(PATH).exists()) {
            DocumentBuilder documentBuilder;
            try {
                documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                Document document = documentBuilder.parse(PATH);
                NodeList pirList = document.getElementsByTagName("PIR");
                for (int i = 0; i < pirList.getLength(); i++) {
                    Node pir = pirList.item(i);
                    if (pir.getAttributes().getNamedItem("name").getNodeValue().trim().equals(pirName)) {
                        NodeList departments = pir.getChildNodes();
                        for (int j = 0; j < departments.getLength(); j++) {
                            Node department = departments.item(j);
                            try {
                                if (department.getAttributes().getNamedItem("name").getNodeValue().trim().equals(departmentName)) {
                                    NodeList modules = department.getChildNodes();
                                    for (int k = 0; k < modules.getLength(); k++) {
                                        Node module = modules.item(k);
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
                            } catch (Exception ignored) {}
                        }
                    }
                }
            } catch (ParserConfigurationException | IOException | SAXException e) {
                e.printStackTrace();
            }
        }
    }

    private static String getModuleStatus(String departmentName, String moduleName) {
        String result = "";
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
                NodeList pirList = document.getElementsByTagName("PIR");
                for (int index = 0; index < pirList.getLength(); index ++) {
                    Node pir = pirList.item(index);
                    if (pir.getAttributes().getNamedItem("name").getNodeValue().equals(PIRNAME)) {
                        NodeList departments = pir.getChildNodes();
                        for (int i = 0; i < departments.getLength(); i++) {
                            Node department = departments.item(i);
                            try {
                                if (department.getAttributes().getNamedItem("name").getNodeValue().equals(departmentName)) {
                                    NodeList modules = department.getChildNodes();
                                    for (int j = 0; j < modules.getLength(); j++) {
                                        Node module = modules.item(j);
                                        try {
                                            if (module.getAttributes().getNamedItem("name").getNodeValue().equals(moduleName)) {
                                                result = "<b>Информация по " + PIRNAME + "</b>\n";
                                                result = result + "АС: " + moduleName;
                                                NodeList attributes = module.getChildNodes();
                                                for (int ind = 0; ind < attributes.getLength(); ind++) {
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

    private static SendMessage sendMessageOnError(Message message) {
        ReplyKeyboardMarkup replyKeyboardMarkup = getMainMenuKeyboard();
        HSQLDBManager.getInstance().insertState(message.getFrom().getId(), message.getChatId(), MAINMENU);
        return sendErrorMessage(message.getChatId(), message.getMessageId(), replyKeyboardMarkup);
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

    private static SendMessage sendErrorMessage(Long chatId, Integer messageId, ReplyKeyboardMarkup replyKeyboardMarkup) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.enableMarkdown(true);
        sendMessage.setChatId(chatId);
        sendMessage.setReplyToMessageId(messageId);
        if (replyKeyboardMarkup != null) {
            sendMessage.setReplyMarkup(replyKeyboardMarkup);
        }
        sendMessage.setText("Что-то пошло не так. Возможно, бот немного устал и решил отдохнуть, но теперь он снова в деле!");
        return sendMessage;
    }

    private static SendMessage onBackCommand(Integer userId, Long chatId, Integer messageId, ReplyKeyboard replyKeyboard) {
        final int state = HSQLDBManager.getInstance().getState(userId, chatId);
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId.toString());
        sendMessage.enableMarkdown(true);
        sendMessage.setReplyToMessageId(messageId);
        sendMessage.setReplyMarkup(replyKeyboard);
        sendMessage.setText("Назад");

        HSQLDBManager.getInstance().insertState(userId, chatId, state - 1);
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
                "\nВыбери нужный ПИР и ты узнаешь намного больше!";
    }

    private static ReplyKeyboardMarkup getMainMenuKeyboard() {
        getPIR();

        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        replyKeyboardMarkup.setSelective(true);
        replyKeyboardMarkup.setResizeKeyboard(true);
        replyKeyboardMarkup.setOneTimeKeyboard(false);

        List<KeyboardRow> keyboard = new ArrayList<>();
        for (Object pir : PIR) {
            KeyboardRow row = new KeyboardRow();
            row.add(pir.toString());
            keyboard.add(row);
        }
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

    private static SendMessage sendNotificationMenu (Integer userId, Long chatId, Integer messageId) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.enableMarkdown(true);

        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        List<KeyboardRow> keyboard = replyKeyboardMarkup.getKeyboard();
        KeyboardRow row = new KeyboardRow();
        row.add("Да");
        row.add("Нет");
        keyboard.add(row);
        replyKeyboardMarkup.setKeyboard(keyboard);
        replyKeyboardMarkup.setResizeKeyboard(true);

        sendMessage.setReplyMarkup(replyKeyboardMarkup);
        sendMessage.setChatId(chatId.toString());
        sendMessage.setReplyToMessageId(messageId);
        sendMessage.setText("Отправляем уведомление на почту и в Telegram о статусах АС?");

        HSQLDBManager.getInstance().insertState(userId, chatId, NOTIFICATION_STATE);

        return sendMessage;
    }

    private static SendMessage messageOnNotificationMenu (Message message) {
        SendMessage sendMessageRequest = null;

        if (message.hasText()) {
            if (message.getText().equals("Да")) {

                ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
                List<KeyboardRow> keyboard = replyKeyboardMarkup.getKeyboard();
                KeyboardRow row = new KeyboardRow();
                row.add("OK");
                keyboard.add(row);
                replyKeyboardMarkup.setKeyboard(keyboard);
                replyKeyboardMarkup.setResizeKeyboard(true);

                SendMessage sendMessage = new SendMessage();
                sendMessage.setChatId(message.getChatId().toString());
                sendMessage.setReplyToMessageId(message.getMessageId());
                sendMessage.setReplyMarkup(replyKeyboardMarkup);
                sendMessage.setText("Отправляем уведомление ...");

                HSQLDBManager.getInstance().insertState(message.getFrom().getId(), message.getChatId(), AFTER_NOTIFICATION_STATE);
                sendMessageRequest = sendMessage;

            } else if (message.getText().equals("Нет")) {

                SendMessage sendMessage = new SendMessage();
                sendMessage.setChatId(message.getChatId().toString());
                sendMessage.enableMarkdown(true);
                sendMessage.setReplyToMessageId(message.getMessageId());
                sendMessage.setReplyMarkup(getRecentsKeyboardForModules(DEPARTMENT,true));
                sendMessage.setText("Назад");

                HSQLDBManager.getInstance().insertState(message.getFrom().getId(), message.getChatId(), MODULE_STATE);
                sendMessageRequest = sendMessage;
            }
        } else {
            sendMessageRequest = sendChooseOptionMessage(message.getChatId(), message.getMessageId(), getMainMenuKeyboard());
        }
        return sendMessageRequest;
    }

    private static SendMessage messageOnAfterNotificationMenu (Message message, boolean messageSent) {
        SendMessage sendMessageRequest;

        SendMessage sendMessage = new SendMessage();
        sendMessage.enableNotification();
        sendMessage.setChatId(message.getChatId().toString());
        sendMessage.enableMarkdown(true);
        sendMessage.setReplyToMessageId(message.getMessageId());
        sendMessage.setReplyMarkup(getRecentsKeyboardForModules(DEPARTMENT,true));
        if (messageSent)
            sendMessage.setText("Отправлено");
        else
            sendMessage.setText("Сообщение не отправлено. \n" +
                    "Адресат должен начать беседу с ботом, после этого он будет получать уведомления");

        HSQLDBManager.getInstance().insertState(message.getFrom().getId(), message.getChatId(), MODULE_STATE);
        sendMessageRequest = sendMessage;

        return sendMessageRequest;
    }

    private static SendMessage sendNotification () {
        SendMessage sendMessage = new SendMessage();
        sendMessage.enableNotification();
        sendMessage.enableMarkdown(true);
        sendMessage.setChatId(getDirectorInfo());
        sendMessage.setText(getAllModules());

        return sendMessage;
    }

    private static String getAllModules() {
        StringBuilder result = new StringBuilder("Доброго времени суток! Довожу до сведения, что в следующих АС имеется неактулизированная информация:\n");
        getDepModules(PIRNAME, DEPARTMENT,false);
        for (Object module : MODULES) {
            //result = result + getModuleStatus(DEPARTMENT, module.toString());
            result.append("*").append(module.toString()).append("*");
            result.append("\n");
        }
        result.append("За подробной информацией обращайтесь ко мне!");
        return result.toString();
    }

    private static String getDirectorInfo() {
        String telegram_id = "";

        if (new File(PATH).exists()) {
            DocumentBuilder documentBuilder;
            try {
                documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                Document document = documentBuilder.parse(PATH);
                NodeList pirList = document.getElementsByTagName("PIR");
                for (int index = 0; index < pirList.getLength(); index ++) {
                    Node pir = pirList.item(index);
                    if (pir.getAttributes().getNamedItem("name").getNodeValue().equals(PIRNAME)) {
                        NodeList departments = pir.getChildNodes();
                        for (int i = 0; i < departments.getLength(); i++) {
                            Node department = departments.item(i);
                            try {
                                if (department.getAttributes().getNamedItem("name").getNodeValue().equals(DEPARTMENT)) {
                                    NodeList modules = department.getChildNodes();
                                    for (int j = 0; j < modules.getLength(); j++) {
                                        Node node = modules.item(j);
                                        try {
                                            if (node.getNodeName().equals("Director")) {
                                                NodeList directorInfo = node.getChildNodes();
                                                for (int k = 0; k < directorInfo.getLength(); k++) {
                                                    switch (directorInfo.item(k).getNodeName()) {
                                                        case ("telegram_id"):
                                                            telegram_id = directorInfo.item(k).getTextContent();
                                                            break;
                                                        default:
                                                            break;
                                                    }
                                                }
                                            }
                                        } catch (Exception ignored) {}
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
        return telegram_id;
    }

    private static SendMessage sendMessageNoRights (Message message) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.enableNotification();
        sendMessage.enableMarkdown(true);
        sendMessage.setChatId(message.getChatId().toString());
        sendMessage.setText("У вас нет прав для пользования ботом");

        return sendMessage;
    }
}
