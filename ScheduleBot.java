package org.example;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.Month;

public class ScheduleBot extends TelegramLongPollingBot {

    private static final Logger logger = LoggerFactory.getLogger(ScheduleBot.class);

    private static final String BOT_TOKEN = "";
    private static final String BOT_USERNAME = "";

    @Override
    public String getBotUsername() {
        return BOT_USERNAME;
    }

    @Override
    public String getBotToken() {
        return BOT_TOKEN;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String message = update.getMessage().getText();
            Long chatId = update.getMessage().getChatId();

            try {
                if (message.startsWith("/day")) {
                    handleDayCommand(chatId, message);
                } else if (message.startsWith("/all")) {
                    handleAllCommand(chatId, message);
                } else if (message.startsWith("/tom")) {
                    handleTomorrowCommand(chatId, message);
                } else if (message.startsWith("/near")) {
                    handleNearCommand(chatId, message);
                } else if (message.startsWith("/help")) {
                    handleHelpCommand(chatId);
                } else {
                    sendMessage(chatId, "Неизвестная команда. Используйте /help для получения списка команд.");
                }
            } catch (Exception e) {
                logger.error("Ошибка при обработке команды: ", e);
                sendMessage(chatId, "Произошла ошибка. Попробуйте позже.");
            }
        }
    }

    private void handleAllCommand(Long chatId, String message) throws Exception {
        String[] parts = message.split(" ");
        if (parts.length != 2) {
            sendMessage(chatId, "Неверный формат команды. Используйте: /all <группа>.");
            return;
        }

        String groupNumber = parts[1];
        String schedule = fetchAllSchedule(groupNumber);
        sendMessage(chatId, schedule);
    }

    private void handleTomorrowCommand(Long chatId, String message) throws Exception {
        String[] parts = message.split(" ");
        if (parts.length != 2) {
            sendMessage(chatId, "Неверный формат команды. Используйте: /tom <группа>.");
            return;
        }

        String groupNumber = parts[1];
        String schedule = fetchTomorrowSchedule(groupNumber);
        sendMessage(chatId, schedule);
    }

    private void handleNearCommand(Long chatId, String message) throws Exception {
        String[] parts = message.split(" ");
        if (parts.length != 2) {
            sendMessage(chatId, "Неверный формат команды. Используйте: /near <группа>.");
            return;
        }

        String groupNumber = parts[1];
        String schedule = fetchNearLesson(groupNumber);
        sendMessage(chatId, schedule);
    }

    private void handleDayCommand(Long chatId, String message) throws Exception {
        String[] parts = message.split(" ");
        if (parts.length != 4) {
            sendMessage(chatId, """
                    Неверный формат команды. Используйте: /day <день> <неделя> <группа>.
                    Пример: /day mon ne 3352
                    """);
            return;
        }

        String day = parts[1].toUpperCase();
        String weekType = parts[2].toLowerCase();
        String groupNumber = parts[3];

        int weekNumber = getWeekNumber(weekType);
        if (weekNumber == -1) {
            sendMessage(chatId, "Некорректное значение недели. Используйте 'ne' (нечётная) или 'e' (чётная).");
            return;
        }

        String schedule = fetchDaySchedule(day, weekNumber, groupNumber);
        sendMessage(chatId, schedule);
    }

    private void handleHelpCommand(Long chatId) {
        String helpMessage = """
                Доступные команды:
                
                /all <группа> - Узнать расписание на всю неделю.
                /tom <группа> - Узнать расписание на завтра.
                /near <группа> - Узнать ближайшую пару.
                /day <день> <неделя> <группа> - Узнать расписание на день.
                
                день - mon | tue | wed | thu | fri | sat
                неделя - ne (нечётная) | e (чётная)
                группа - номер группы (например, 3352)
                Пример: /day mon ne 3352

                /help - Вывод списка доступных команд
                """;
        sendMessage(chatId, helpMessage);
    }

    private String fetchDaySchedule(String day, int weekNumber, String groupNumber) throws Exception {
        String season = getCurrentSeason();
        int year = LocalDate.now().getYear();

        String apiUrl = String.format("https://digital.etu.ru/api/mobile/schedule?weekDay=%s&groupNumber=%s&joinWeeks=false&season=%s&year=%d",
                day, groupNumber, season, year);

        JSONObject response = fetchJson(apiUrl);
        JSONObject groupSchedule = response.optJSONObject(groupNumber);

        if (groupSchedule == null) {
            return "Не удалось найти расписание для группы " + groupNumber;
        }

        JSONObject daysSchedule = groupSchedule.optJSONObject("days");
        JSONObject selectedDay = daysSchedule != null ? daysSchedule.optJSONObject(toWeekString(day)) : null;

        if (selectedDay == null) {
            return "Нет занятий на этот день.";
        }

        int dayIndex = Integer.parseInt(toWeekString(day));
        String dayName = getDayName(dayIndex);

        StringBuilder result = new StringBuilder("Расписание на ").append(dayName).append(":\n");
        JSONArray lessons = selectedDay.getJSONArray("lessons");

        boolean hasLessons = false;
        for (int i = 0; i < lessons.length(); i++) {
            JSONObject lesson = lessons.getJSONObject(i);
            int lessonWeek = lesson.getInt("week");

            if (lessonWeek == 0 || lessonWeek == weekNumber) {
                hasLessons = true;
                result.append(String.format("%s - %s: %s (%s)\nАудитория: %s\n\n",
                        lesson.getString("start_time"),
                        lesson.getString("end_time"),
                        lesson.getString("name"),
                        lesson.getString("teacher"),
                        lesson.getString("room")));
            }
        }

        if (!hasLessons) {
            result.append("Занятий на этой неделе нет.\n");
        }

        return result.toString();
    }

    private String fetchAllSchedule(String groupNumber) throws Exception {
        String season = getCurrentSeason();
        int year = LocalDate.now().getYear();

        String apiUrl = String.format(
                "https://digital.etu.ru/api/mobile/schedule?groupNumber=%s&joinWeeks=true&season=%s&year=%d",
                groupNumber, season, year);

        JSONObject response = fetchJson(apiUrl);
        JSONObject groupSchedule = response.optJSONObject(groupNumber);

        if (groupSchedule == null) {
            return "Не удалось найти расписание для группы " + groupNumber;
        }

        JSONObject daysSchedule = groupSchedule.optJSONObject("days");

        if (daysSchedule == null || daysSchedule.isEmpty()) {
            return "Нет расписания для группы " + groupNumber;
        }

        StringBuilder result = new StringBuilder("Расписание на всю неделю для группы ").append(groupNumber).append(":\n\n");

        for (int i = 0; i <= 5; i++) {
            String day = String.valueOf(i);
            JSONObject daySchedule = daysSchedule.optJSONObject(day);

            if (daySchedule != null) {
                result.append(getDayName(i)).append(":\n");

                JSONArray lessons = daySchedule.optJSONArray("lessons");
                if (lessons != null && !lessons.isEmpty()) {
                    for (int j = 0; j < lessons.length(); j++) {
                        JSONObject lesson = lessons.getJSONObject(j);
                        result.append(String.format(
                                "%s - %s: %s (%s)\nАудитория: %s\n\n",
                                lesson.getString("start_time"),
                                lesson.getString("end_time"),
                                lesson.getString("name"),
                                lesson.getString("teacher"),
                                lesson.getString("room")
                        ));
                    }
                } else {
                    result.append("Нет занятий\n\n");
                }
            } else {
                result.append(getDayName(i)).append(": Нет данных о расписании\n\n");
            }
        }

        return result.toString();
    }

    private String fetchTomorrowSchedule(String groupNumber) throws Exception {
        String season = getCurrentSeason();
        int year = LocalDate.now().getYear();

        LocalDate tomorrow = LocalDate.now().plusDays(1);
        String day = toDayString(tomorrow);
        String dayName = getDayName(Integer.parseInt(toWeekString(day)));

        String apiUrl = String.format(
                "https://digital.etu.ru/api/mobile/schedule?weekDay=%s&groupNumber=%s&joinWeeks=false&season=%s&year=%d",
                day, groupNumber, season, year);

        JSONObject response = fetchJson(apiUrl);
        JSONObject groupSchedule = response.optJSONObject(groupNumber);

        if (groupSchedule == null) {
            return "Не удалось найти расписание для группы " + groupNumber;
        }

        JSONObject daysSchedule = groupSchedule.optJSONObject("days");
        JSONObject selectedDay = daysSchedule != null ? daysSchedule.optJSONObject(toWeekString(day)) : null;

        if (selectedDay == null) {
            return "Завтра занятий нет.";
        }

        StringBuilder result = new StringBuilder("Расписание на завтра (").append(dayName).append("):\n");
        JSONArray lessons = selectedDay.getJSONArray("lessons");

        boolean hasLessons = false;
        for (int i = 0; i < lessons.length(); i++) {
            JSONObject lesson = lessons.getJSONObject(i);
            int lessonWeek = lesson.getInt("week");

            if (lessonWeek == 0 || lessonWeek == getWeekNumberByDate(tomorrow)) {
                hasLessons = true;
                result.append(String.format("%s - %s: %s (%s)\nАудитория: %s\n\n",
                        lesson.getString("start_time"),
                        lesson.getString("end_time"),
                        lesson.getString("name"),
                        lesson.getString("teacher"),
                        lesson.getString("room")));
            }
        }

        if (!hasLessons) {
            result.append("Занятий на завтрашней неделе нет.\n");
        }

        return result.toString();
    }

    private String fetchNearLesson(String groupNumber) throws Exception {
        String season = getCurrentSeason();
        int year = LocalDate.now().getYear();

        LocalDate today = LocalDate.now();
        String todayDay = toDayString(today);
        LocalDateTime now = LocalDateTime.now();

        String apiUrl = String.format(
                "https://digital.etu.ru/api/mobile/schedule?weekDay=%s&groupNumber=%s&joinWeeks=false&season=%s&year=%d",
                todayDay, groupNumber, season, year);

        JSONObject response = fetchJson(apiUrl);
        JSONObject groupSchedule = response.optJSONObject(groupNumber);

        if (groupSchedule == null) {
            return "Не удалось найти расписание для группы " + groupNumber;
        }

        JSONObject daysSchedule = groupSchedule.optJSONObject("days");
        JSONObject todaySchedule = daysSchedule != null ? daysSchedule.optJSONObject(toWeekString(todayDay)) : null;

        if (todaySchedule == null) {
            return "Сегодня занятий нет. Используйте команду /tom для получения расписания на завтра.";
        }

        JSONArray lessons = todaySchedule.getJSONArray("lessons");
        for (int i = 0; i < lessons.length(); i++) {
            JSONObject lesson = lessons.getJSONObject(i);

            int lessonWeek = lesson.getInt("week");
            if (lessonWeek == 0 || lessonWeek == getWeekNumberByDate(today)) {
                LocalTime lessonStartTime = LocalTime.parse(lesson.getString("start_time"));
                if (now.toLocalTime().isBefore(lessonStartTime)) {
                    return String.format("Ближайшая пара сегодня:\n%s - %s: %s (%s)\nАудитория: %s",
                            lesson.getString("start_time"),
                            lesson.getString("end_time"),
                            lesson.getString("name"),
                            lesson.getString("teacher"),
                            lesson.getString("room"));
                }
            }
        }

        LocalDate tomorrow = today.plusDays(1);
        String tomorrowDay = toDayString(tomorrow);
        String tomorrowApiUrl = String.format(
                "https://digital.etu.ru/api/mobile/schedule?weekDay=%s&groupNumber=%s&joinWeeks=false&season=%s&year=%d",
                tomorrowDay, groupNumber, season, year);

        JSONObject tomorrowResponse = fetchJson(tomorrowApiUrl);
        JSONObject tomorrowGroupSchedule = tomorrowResponse.optJSONObject(groupNumber);

        if (tomorrowGroupSchedule == null) {
            return "Не удалось найти расписание для завтрашнего дня.";
        }

        JSONObject tomorrowDaysSchedule = tomorrowGroupSchedule.optJSONObject("days");
        JSONObject tomorrowSchedule = tomorrowDaysSchedule != null ? tomorrowDaysSchedule.optJSONObject(toWeekString(tomorrowDay)) : null;

        if (tomorrowSchedule == null) {
            return "На завтра занятий нет.";
        }

        JSONArray tomorrowLessons = tomorrowSchedule.getJSONArray("lessons");
        for (int i = 0; i < tomorrowLessons.length(); i++) {
            JSONObject lesson = tomorrowLessons.getJSONObject(i);
            int lessonWeek = lesson.getInt("week");
            if (lessonWeek == 0 || lessonWeek == getWeekNumberByDate(tomorrow)) {
                return String.format("Ближайшая пара завтра:\n%s - %s: %s (%s)\nАудитория: %s",
                        lesson.getString("start_time"),
                        lesson.getString("end_time"),
                        lesson.getString("name"),
                        lesson.getString("teacher"),
                        lesson.getString("room"));
            }
        }
        return "Ближайших пар не найдено.";
    }

    private String toDayString(LocalDate date) {
        return switch (date.getDayOfWeek()) {
            case MONDAY -> "MON";
            case TUESDAY -> "TUE";
            case WEDNESDAY -> "WED";
            case THURSDAY -> "THU";
            case FRIDAY -> "FRI";
            case SATURDAY -> "SAT";
            default -> "SUN";
        };
    }

    private int getWeekNumberByDate(LocalDate date) {
        int weekOfYear = date.getDayOfYear() / 7;
        return (weekOfYear % 2 == 0) ? 2 : 1;
    }

    private String getDayName(int dayIndex) {
        return switch (dayIndex) {
            case 0 -> "Понедельник";
            case 1 -> "Вторник";
            case 2 -> "Среда";
            case 3 -> "Четверг";
            case 4 -> "Пятница";
            case 5 -> "Суббота";
            default -> "Неизвестный день";
        };
    }

    private JSONObject fetchJson(String apiUrl) throws Exception {
        URL url = new URL(apiUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                response.append(line);
            }
            return new JSONObject(response.toString());
        }
    }

    private String getCurrentSeason() {
        Month currentMonth = LocalDate.now().getMonth();
        return (currentMonth.getValue() >= Month.SEPTEMBER.getValue() && currentMonth.getValue() <= Month.DECEMBER.getValue())
                ? "autumn" : "spring";
    }

    private String toWeekString(String day) {
        return switch (day.toUpperCase()) {
            case "MON" -> "0";
            case "TUE" -> "1";
            case "WED" -> "2";
            case "THU" -> "3";
            case "FRI" -> "4";
            case "SAT" -> "5";
            default -> "-1";
        };
    }

    private int getWeekNumber(String weekType) {
        if ("ne".equals(weekType)) return 1;
        if ("e".equals(weekType)) return 2;
        return -1;
    }

    private void sendMessage(Long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId.toString());
        message.setText(text);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            logger.error("Ошибка отправки сообщения: ", e);
        }
    }

    public static void main(String[] args) {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new ScheduleBot());
        } catch (TelegramApiException e) {
            Logger logger = LoggerFactory.getLogger(ScheduleBot.class);
            logger.error("Ошибка запуска бота: ", e);
        }
    }
}
