package ru.tecon.controllerData;

import com.jcraft.jsch.*;
import ru.tecon.ProjectProperty;
import ru.tecon.Utils;
import ru.tecon.isacom.*;
import ru.tecon.model.ObjectInfoModel;
import ru.tecon.server.EchoSocketServer;
import ru.tecon.traffic.ControllerSocket;
import ru.tecon.traffic.MonitorInputStream;
import ru.tecon.traffic.Statistic;

import javax.naming.NamingException;
import java.io.*;
import java.nio.file.Files;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Класс для работы с конфигурацией контроллера
 */
public final class ControllerConfig {

    private static final Logger LOG = Logger.getLogger(ControllerConfig.class.getName());

    private static Map<String, List<String>> config = new HashMap<>();
    private static Set<String> configList = new HashSet<>();
    private static List<String> instantConfigList = new LinkedList<>();

    private static ScheduledExecutorService service;
    private static ScheduledFuture future;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private ControllerConfig() {

    }

    /**
     * Метод парсит текстовый файл конфигурации контроллера
     * @return true если все впорядке
     * @throws IOException если ошибка в чтении файла
     */
    public static boolean parsControllerConfig() throws IOException {
        LOG.info("read config file " + ProjectProperty.getConfigFile());

        BufferedReader reader = Files.newBufferedReader(ProjectProperty.getConfigFile());

        String line;
        String key1 = null;
        String key2 = null;
        String key3 = null;
        while ((line = reader.readLine()) != null) {
            if (line.trim().startsWith("^")) {
                key1 = line.trim().substring(1);
            }
            if (line.trim().startsWith("@") && (line.trim().split(",").length == 2)) {
                key2 = line.trim().split(",")[0].substring(1);
                key3 = line.trim().split(",")[1];
                if ((key1 != null) && !config.containsKey(key1 + ":" + key2 + ":" + key3)) {
                    config.put(key1 + ":" + key2 + ":" + key3, new ArrayList<>());
                }
            }
            if (line.trim().startsWith("#")) {
                if ((key1 != null) && (key2 != null) && (key3 != null)) {
                    if (line.trim().split(",").length == 7) {
                        String addName = "";
                        switch (line.trim().split(",")[3]) {
                            case "5":
                                addName = ":i";
                                break;
                            case "6":
                                addName = ":iMinToSec";
                                break;
                            case "7":
                                addName = ":iHourToSec";
                                break;
                        }
                        if (config.get(key1 + ":" + key2 + ":" + key3).size() < Integer.parseInt(key3)) {
                            config.get(key1 + ":" + key2 + ":" + key3).add(line.trim().split(",")[6] + ":" +
                                    line.trim().split(",")[0].substring(1) + addName + "::" +
                                    line.trim().split(",")[3]);
                        }
                    }
                }
            }
        }

        for (String k: config.keySet()) {
            configList.addAll(config.get(k));
        }

        instantConfigList = new LinkedList<>(Files.readAllLines(ProjectProperty.getInstantConfigFile()));
        instantConfigList.removeIf(s -> s.trim().isEmpty());

        return !config.isEmpty();
    }

    /**
     * Метод закрывает службу проверки запроса на конфигурацию
     */
    public static void stopUploaderService() {
        if (Objects.nonNull(service)) {
            future.cancel(true);
            service.shutdown();
        }
    }

    /**
     * Метод запускает службу которая обрабатывает запросы на конфигурацию из базы
     */
    public static void startUploaderService() {
        if (ProjectProperty.isCheckRequestService()) {
            service = Executors.newSingleThreadScheduledExecutor();
            future = service.scheduleWithFixedDelay(() -> {
                try {
                    Utils.loadRMI().checkConfigRequest(ProjectProperty.getServerName());
                } catch (NamingException e) {
                    LOG.log(Level.WARNING, "error with config service", e);
                }
            }, 5, 30, TimeUnit.SECONDS);
        }
    }

    /**
     * Метод отправляет конфигурацию в базу
     */
    public static void uploadConfig(String url) {
        try {
            Set<String> config = getInstantConfigFromURL(url);
            config.addAll(configList);

            Utils.loadRMI().putConfig(config, url, ProjectProperty.getServerName());
            LOG.info("configuration for url: " + url + " is uploaded");
        } catch (NamingException e) {
            LOG.log(Level.WARNING, "error load RMI", e);
        }
    }

    public static List<String> getConfigNames(int bufferNumber, int eventCode, int size) {
        return config.getOrDefault(bufferNumber + ":" + eventCode + ":" + size, null);
    }

    /**
     * Метод по ssh общается с MFK1500 и получает от него конфигурацию
     * @param url url прибора
     * @return список параметров конфигурации
     */
    private static Set<String> getInstantConfigFromURL(String url) {
        LOG.info("request load instant config from: " + url);

        Set<String> result = new HashSet<>();

        if (EchoSocketServer.isBlocked(url)) {
            LOG.info("traffic block");
            return result;
        }

        try {
            Session session = new JSch().getSession(ProjectProperty.SSH_LOGIN, url, ProjectProperty.SSH_PORT);
            session.setPassword(ProjectProperty.SSH_PASSWORD);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect();

            Channel channel = session.openChannel("exec");
            ((ChannelExec)channel).setCommand("cat /user/IDS00201");
            channel.connect();

            StringBuilder sb = new StringBuilder();

            Statistic st = EchoSocketServer.getStatistic(url);
            st.updateOutputTraffic(100);

            try (MonitorInputStream monitor = new MonitorInputStream(channel.getInputStream())) {
                monitor.setStatistic(st);

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(monitor))) {
                    reader.lines().forEach(s -> {
                        if (!s.isEmpty()) {
                            sb.append(s).append("\n");
                        }
                    });
                } catch (IOException e) {
                    LOG.log(Level.WARNING, "error ssh file read", e);
                }
            } catch (IOException e) {
                LOG.log(Level.WARNING, "error with my monitor", e);
            }

            channel.disconnect();
            session.disconnect();

            LOG.info("symbol table from url: " + url + " is loaded");

            String types = sb.substring(sb.indexOf("[TYPE]") + "[TYPE]".length(), sb.indexOf("[DEVTYP]"));

            String variables = sb.substring(sb.indexOf("[VARIABLE]") + "[VARIABLE]".length(), sb.indexOf("[END]"));

            List<String> typesList = new LinkedList<>(Arrays.asList(types.split("\n")));
            typesList.removeIf(s -> !(s.startsWith("T") || s.startsWith("F")));

            List<String> variablesList = new LinkedList<>(Arrays.asList(variables.split("\n")));
            variablesList.removeIf(s -> !s.startsWith("V"));

            List<String> simpleTypes = Arrays.stream(IsacomSimpleTypes.values())
                    .map(Enum::name)
                    .collect(Collectors.toList());

            outer: for (String globalConfigItem: instantConfigList) {
                for (Iterator<String> it = variablesList.iterator(); it.hasNext();) {
                    String variableItem = it.next();
                    String variableItemName = variableItem.substring(variableItem.indexOf("=") + 1).split(",")[0];
                    if (globalConfigItem.equals(variableItemName)) {
                        // Если имена совпадают полностью то вытаскиваем тип
                        String type = typesList.stream().filter(s -> s.startsWith("T" + variableItem.split(",")[6]))
                                .findFirst().orElse(null);

                        // Проверяем что тип найден и он пройтой из реализованных в драйвере
                        if (type != null) {
                            String typeName = type.substring(type.indexOf("=") + 1).split(",")[0];
                            if (type.split(",")[1].equals("0") && simpleTypes.contains(typeName)) {
                                // Формат записи имя переменной :Текущие данные:: имя типа
                                result.add(globalConfigItem + ":Текущие данные::" + typeName);
                            } else {
                                LOG.warning(globalConfigItem + " " + variableItemName + " тип не простой или тип не реализован");
                            }
                        } else {
                            LOG.warning(globalConfigItem + " " + variableItemName + " не нашел тип");
                        }

                        it.remove();
                        continue outer;
                    } else {
                        // Если имена совпадают если откинуть последнию часть после "_"
                        if (globalConfigItem.contains("_") &&
                                globalConfigItem.substring(0, globalConfigItem.lastIndexOf("_")).equals(variableItemName)) {

                            // Вытаскиваю тип данных
                            String type = typesList.stream().filter(s -> s.startsWith("T" + variableItem.split(",")[6]))
                                    .findFirst().orElse(null);

                            // Проверяю что тип существует и он соответствует структурному типу
                            if ((type != null) && type.split(",")[1].equals("2")) {
                                // Вытаскиваю массив полей структурного типа
                                List<String> fieldsList = new ArrayList<>();
                                for (int i = typesList.indexOf(type) + 1; i < typesList.size(); i++) {
                                    if (typesList.get(i).startsWith("T")) {
                                        break;
                                    } else {
                                        fieldsList.add(typesList.get(i));
                                    }
                                }

                                // Определяю нужное поле в структурном типе
                                String subField = fieldsList.stream().filter(s -> s.substring(s.indexOf("=") + 1).split(",")[0].equals(globalConfigItem.substring(globalConfigItem.lastIndexOf("_") + 1)))
                                        .findFirst().orElse(null);

                                if (subField != null) {
                                    // Если нужное поле содержит 4 значения то это String тип и из него надо вытащить длину для String
                                    String addValue = "";
                                    if (subField.split(",").length == 4) {
                                        addValue = ":" + subField.split(",")[3];
                                    }

                                    // Определяю какого простого типа поле
                                    String subType = typesList.stream().filter(s -> s.startsWith("T" + subField.split(",")[1]))
                                            .findFirst().orElse(null);

                                    // Проверяем что тип найден и он пройтой из реализованных в драйвере
                                    if (subType != null) {
                                        String subTypeName = subType.substring(subType.indexOf("=") + 1).split(",")[0];
                                        if (subType.split(",")[1].equals("0") && simpleTypes.contains(subTypeName)) {
                                            // Формат записи имя переменной :Текущие данные:: имя типа : длина типа : смещение в типе под нужное поле :
                                            // имя простого типа : если простой тип String то длина этой String
                                            result.add(globalConfigItem + ":Текущие данные::" +
                                                    type.substring(type.indexOf("=") + 1).split(",")[0] + ":" +
                                                    type.split(",")[2] + ":" +
                                                    subField.split(",")[2] + ":" +
                                                    subTypeName +
                                                    addValue);

                                            continue outer;
                                        } else {
                                            LOG.warning(globalConfigItem + " " + variableItemName + " тип переменной функционального блока не простой или тип не реализован");
                                        }
                                    } else {
                                        LOG.warning(globalConfigItem + " " + variableItemName + " не нашел тип");
                                    }
                                } else {
                                    LOG.warning(globalConfigItem + " " + variableItemName + " не нашел нужную переменную в функциональном блоке");
                                }
                            } else {
                                LOG.warning(globalConfigItem + " " + variableItemName + " не нашел тип или тип не структура");
                            }
                        }
                    }
                }
            }
        } catch (JSchException e) {
            LOG.log(Level.WARNING, "error ssh connect", e);
        }

        return result;
    }

    /**
     * Метод по ssh и по isacom общается с MFK1500 и получает от него системные параметры
     * По isacom получаем параметры из {@link ControllerSystemVariable}
     * По ssh получаем время на MFK1500
     * @param url ip прибора
     * @return список полученных системных параметров
     */
    public static List<ObjectInfoModel> getControllerInfo(String url) {
        List<ObjectInfoModel> result = new ArrayList<>();
        try (ControllerSocket socket = new ControllerSocket(url);
             InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            // Получаем системные параметры по isacom
            List<IsacomModel> model = new ArrayList<>();
            for (ControllerSystemVariable value: ControllerSystemVariable.values()) {
                model.add(new IsacomModel(value.name(), new IsacomType() {
                    @Override
                    public String getName() {
                        return value.getType().name();
                    }

                    @Override
                    public int getSize() {
                        return value.getType().getSize();
                    }
                }));
            }

            IsacomProtocol.createVariableList(in, out, model);
            IsacomProtocol.readVariableList(in, out, model);

            model.forEach(isacomModel -> result.add(new ObjectInfoModel(isacomModel.getName(), isacomModel.getValue(), 
                    ControllerSystemVariable.isWrite(isacomModel.getName()))));

            // Получаем время на MFK1500
            StringBuilder sb = jschShellExecCommand(url, "tvar print /var/tm/local");
            if (!sb.toString().isEmpty()) {
                String[] split = sb.toString().split(" ");
                if (split.length == 6) {
                    LocalDateTime localDateTime = LocalDateTime.of(Integer.valueOf(split[5]), Integer.valueOf(split[4]),
                            Integer.valueOf(split[3]), Integer.valueOf(split[2]), Integer.valueOf(split[1]),
                            Integer.valueOf(split[0]));
                    result.add(new ObjectInfoModel("Время", localDateTime.format(FORMATTER), false));

                    Duration duration = Duration.between(LocalDateTime.now(), localDateTime);

                    long input = duration.getSeconds();

                    String format = "";
                    if (duration.getSeconds() < 0) {
                        format = "- ";
                        input *= -1;
                    }

                    long numberOfDays = input / 86400;
                    long numberOfHours = (input % 86400 ) / 3600 ;
                    long numberOfMinutes = ((input % 86400 ) % 3600 ) / 60;
                    long numberOfSeconds = ((input % 86400 ) % 3600 ) % 60;

                    String difference = String.format(format + "%d.%02d:%02d:%02d",
                            numberOfDays, numberOfHours, numberOfMinutes, numberOfSeconds);

                    result.add(new ObjectInfoModel("Рассинхронизация времени", difference, false));
                } else {
                    result.add(new ObjectInfoModel("Время", sb.toString(), false));
                }
            }
        } catch (IOException | IsacomException | JSchException e) {
            LOG.warning("Ошибка общения с прибором по isacom или ssh " + e.getMessage());
        }

        return result;
    }

    /**
     * Метод по isacom общается с MFK1500 и записывает в него новые значения системных параметров
     * Системные параметры из {@link ControllerSystemVariable}
     * @param url ip прибора
     * @param info информация для записи
     */
    public static void setControllerInfo(String url, String info) {
        try (ControllerSocket socket = new ControllerSocket(url);
             InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            List<IsacomModel> isacomModels = new ArrayList<>();
            for (String s: info.split(";")) {
                String name = s.split(":")[0];
                String value = s.split(":")[1];
                if (ControllerSystemVariable.isContains(name)) {
                    IsacomSimpleTypes simpleType = ControllerSystemVariable.valueOf(name).getType();
                    isacomModels.add(new IsacomModel(name, new IsacomType() {
                        @Override
                        public String getName() {
                            return simpleType.name();
                        }

                        @Override
                        public int getSize() {
                            return simpleType.getSize();
                        }
                    }, value));
                }
            }

            LOG.info("Записываем новые значения " + isacomModels.toString() + " в прибор по адресу " + url);

            IsacomProtocol.extendedVariableWriting(in, out, isacomModels);

        } catch (IOException | IsacomException e) {
            LOG.warning("Ошибка записи новых значений в прибор " + url + " " + e.getMessage());
        }
    }

    /**
     * Метод по ssh общается с MFK1500 и отправляет ему команду на синхронизацию времени
     * @param url ip прибора
     */
    public static void synchronizeDate(String url) {
        try {
            jschShellExecCommand(url, "rdate -4 -n 10.98.254.2");
        } catch (IOException | JSchException e) {
            LOG.warning("Ошибка синхронизации времени " + e.getMessage());
        }
    }

    /**
     * Метод открывает ssh подключение к прибору и выполняет переданную команду
     * @param host имя хоста
     * @param command команда
     * @return результат выполнения команды
     * @throws IOException ошибка
     * @throws JSchException ошибка
     */
    private static StringBuilder jschShellExecCommand(String host, String command) throws IOException, JSchException {
        StringBuilder sb = new StringBuilder();

        Session session = new JSch().getSession(ProjectProperty.SSH_LOGIN, host, ProjectProperty.SSH_PORT);
        session.setPassword(ProjectProperty.SSH_PASSWORD);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect();

        Channel channel = session.openChannel("shell");

        Statistic st = EchoSocketServer.getStatistic(host);
        st.updateOutputTraffic(100);

        try (MonitorInputStream monitor = new MonitorInputStream(channel.getInputStream())) {
            monitor.setStatistic(st);

            try (PrintStream toServer = new PrintStream(channel.getOutputStream());
                 BufferedReader reader = new BufferedReader(new InputStreamReader(monitor))) {
                channel.connect();

                toServer.println(command);
                toServer.println("exit");
                toServer.flush();

                List<String> lines = new ArrayList<>();

                reader.lines().forEach(s -> {
                    if (!s.isEmpty()) {
                        lines.add(s);
                    }
                });

                boolean add = false;
                String endString = "";
                for (String line: lines) {
                    if (add) {
                        if (line.startsWith(endString)) {
                            break;
                        }
                        sb.append(line);
                    }

                    if (line.endsWith(command)) {
                        add = true;
                        endString = line.replaceFirst(command, "");
                    }
                }
            }
        }

        channel.disconnect();
        session.disconnect();

        return sb;
    }
}
