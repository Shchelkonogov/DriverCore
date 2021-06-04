package ru.tecon;

import oracle.jdbc.OracleConnection;
import ru.tecon.beanInterface.LoadOPCLocal;
import ru.tecon.beanInterface.LoadOPCRemote;
import ru.tecon.driverCoreClient.model.LastData;
import ru.tecon.ejb.WebConsoleBean;
import ru.tecon.model.*;
import ru.tecon.webSocket.WebSocketServer;

import javax.annotation.Resource;
import javax.ejb.*;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stateless bean реализующий интерфейсы
 * {@link LoadOPCLocal} и {@link LoadOPCRemote}
 */
@Stateless(name = "LoadOPC", mappedName = "ejb/LoadOPC")
@Local(LoadOPCLocal.class)
@Remote(LoadOPCRemote.class)
public class LoadOPC implements LoadOPCLocal, LoadOPCRemote {

    private static final Logger LOG = Logger.getLogger(LoadOPC.class.getName());

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH");
    private static final Pattern PATTERN_IPV4 = Pattern.compile("_(?<ip>((0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)\\.){3}(0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?))_", Pattern.CASE_INSENSITIVE);

    /**
     * insert который загружает объект в базу <br>
     * первый параметр - имя объекта <br>
     * второй параметр - {@code <OpcKind>Hda</OpcKind><ItemName>'имя объекта'</ItemName><Server>'имя сервера'</Server>}
     */
    private static final String SQL_INSERT_OPC_OBJECT = "insert into tsa_opc_object values((select get_guid_base64 from dual), ?, ?, 1)";
    /**
     * select для опеределения подписан ли объект <br>
     * 1 - если подписан или ничего не вернет в обратном случае <br>
     * параметр - {@code <OpcKind>Hda</OpcKind><ItemName>'имя объекта'</ItemName><Server>'имя сервера'</Server>}
     */
    private static final String SQL_CHECK_LINKED = "select b.subscribed from tsa_opc_object a " +
            "inner join tsa_linked_object b on a.id = b.opc_object_id and a.opc_path = ?";



    /**
     * select для определения требует ли база <br>
     * загрузки в нее конфигурации сервера <br>
     * параметр - имя сервера
     */
    private static final String SQL_CHECK_REQUEST_LOAD_CONFIG = "select 1 from dual " +
            "where exists(select * from arm_commands " +
            "where kind = 'ForceBrowse' and extractValue(XMLType('<Group>' || args || '</Group>'), '/Group/Server') = ? " +
            "and is_success_execution is null)";



    /**
     * select для определения списка URL <br>
     * по которым база запросила конфигурацию сервера <br>
     * параметр - имя сервера
     */
    private static final String SQL_GET_REQUEST_LOAD_CONFIG = "select extractValue(XMLType('<Group>' || args || '</Group>'), '/Group/ItemName'), id " +
            "from arm_commands where kind = 'ForceBrowse' " +
            "and extractValue(XMLType('<Group>' || args || '</Group>'), '/Group/Server') = ? and is_success_execution is null";


    /**
     * select выгружает id запросов на конфигурацию сервера <br>
     * id и имя объектов сервера <br>
     * параметр - имя сервера
     */
    private static final String SQL_GET_OPC_OBJECT_ID = "select b.id, b.display_name, a.id from arm_commands a " +
            "inner join tsa_opc_object b " +
            "on a.args = b.opc_path and a.kind = 'ForceBrowse' " +
            "and (a.is_success_execution = 2 or a.is_success_execution is null) " +
            "and extractValue(XMLType('<Group>' || a.args || '</Group>'), '/Group/Server') = ?";
    /**
     * select выгружает id запросов на конфигурацию сервера <br>
     * id и имя объектов сервера <br>
     * первый параметр - имя сервера
     * второй параметр - ip адрес запроса мгновенных данных
     */
    private static final String SQL_GET_OPC_OBJECT_ID_2 = "select b.id, b.display_name, a.id from arm_commands a " +
            "inner join tsa_opc_object b " +
            "on a.args = b.opc_path and a.kind = 'ForceBrowse' " +
            "and (a.is_success_execution = 2 or a.is_success_execution is null) " +
            "and extractValue(XMLType('<Group>' || a.args || '</Group>'), '/Group/Server') = ? " +
            "and extractValue(XMLType('<Group>' || a.args || '</Group>'), '/Group/ItemName') like ?";
    /**
     * insert который загружает в базу конфигурацию сервера <br>
     * первый параметр - имя параметра <br>
     * второй параметр - {@code <ItemName>'имя объекта':'имя параметра'</ItemName>} <br>
     * третий параметр - id объекта
     */
    private static final String SQL_INSERT_CONFIG = "insert into tsa_opc_element values ((select get_guid_base64 from dual), ?, ?, ?, 1, null)";
    /**
     * update который выставляет статус выполнения insert по добавлению конфигурации <br>
     * первый параметр - статус выполения 1 - выполенно 0 - ошибка выполнения
     * второй параметр - {@code <'имя объекта'>'количество вставленных параметров'<'имя объекта'>} <br>
     * третий параметр - комментарии в произвольном виде <br>
     * четвертый параметр - id запроса
     */
    private static final String SQL_UPDATE_CHECK = "update arm_commands " +
            "set is_success_execution = ?, result_description = ?, display_result_description = ?, end_time = sysdate where id = ?";


    /**
     * select для получения opc_id объекта <br>
     * параметр - {@code <OpcKind>Hda</OpcKind><ItemName>'имя объекта'</ItemName><Server>'имя сервера'</Server>}
     */
    private static final String SQL_GET_OBJECT = "select a.id from tsa_opc_object a " +
            "where opc_path = ? " +
            "and exists(select 1 from tsa_linked_object where opc_object_id = a.id and subscribed = 1)";
    /**
     * select для получения списка парамтров для выгрузки данных <br>
     * параметр - opc_id
     */
    private static final String SQL_GET_LINKED_PARAMETERS = "select c.display_name || " +
                "nvl2(extractValue(XMLType('<Group>' || opc_path || '</Group>'), '/Group/SysInfo'), " +
                    "'::' || extractValue(XMLType('<Group>' || opc_path || '</Group>'), '/Group/SysInfo'), ''), " +
            "b.aspid_object_id, b.aspid_param_id, b.aspid_agr_id, b.measure_unit_transformer " +
            "from tsa_linked_element b, tsa_opc_element c " +
            "where b.opc_element_id in (select id from tsa_opc_element where opc_object_id = ?) " +
            "and b.opc_element_id = c.id " +
            "and exists(select a.obj_id, a.par_id from dz_par_dev_link a " +
                        "where a.par_id = b.aspid_param_id and a.obj_id = b.aspid_object_id) " +
            "and b.aspid_agr_id is not null";
    /**
     * select для получения даты с которой нужны значения по парамтерам <br>
     * первый параметр - id объекта <br>
     * второй параметр - id параметра <br>
     * третий параметр - stat_aggregate
     */
    private static final String SQL_GET_START_DATE = "select to_char(time_stamp, 'dd.mm.yyyy hh24') " +
            "from dz_input_start where obj_id = ? and par_id = ? and stat_aggr = ?";


    /**
     * function для загрузки значений в базу принимает массив T_DZ_UTIL_INPUT_DATA типа T_DZ_UTIL_INPUT_DATA_ROW
     */
    private static final String SQL_INSERT_DATA = "{call dz_util1.input_data(?)}";


    /**
     * select objectId по которым надо запустить загрузку мгновенных данных
     * параметр - имя сервера
     */
    private static final String SQL_CHECK_INSTANT_LOAD = "select a.id, b.display_name from arm_commands a, tsa_opc_object b " +
            "    where to_number(extractValue(XMLType(args), '/ObjectId')) in " +
            "          (select id from opc_object where linked = 1 and server_name = ?) " +
            "      and kind = 'AsyncRefresh' and is_success_execution is null " +
            "      and b.id = (select opc_object_id from tsa_linked_object " +
            "           where subscribed = 1 and aspid_object_id = to_number(extractValue(XMLType(a.args), '/ObjectId')))";


    /**
     * select для получения списка парамтров для выгрузки мгновенных данных <br>
     * параметр - имя сервера '_' url адрес '%'
     */
    private static final String SQL_GET_LINKED_PARAMETERS_FOR_INSTANT_DATA = "select c.display_name || " +
                "nvl2(extractValue(XMLType('<Group>' || opc_path || '</Group>'), '/Group/SysInfo'), " +
                    "'::' || extractValue(XMLType('<Group>' || opc_path || '</Group>'), '/Group/SysInfo'), ''), " +
            "b.aspid_object_id, b.aspid_param_id, b.aspid_agr_id, b.measure_unit_transformer " +
            "from tsa_linked_element b, tsa_opc_element c " +
            "where b.opc_element_id in (select id from tsa_opc_element " +
                                        "where opc_object_id = (select id from tsa_opc_object " +
                                                                "where display_name like ?)) " +
            "and b.opc_element_id = c.id " +
            "and exists(select a.obj_id, a.par_id from dz_par_dev_link a " +
                        "where a.par_id = b.aspid_param_id and a.obj_id = b.aspid_object_id) " +
            "and b.aspid_agr_id is null";


    /**
     * select для получения последнего известного значения по параметру <br>
     * первый параметр - id объекта <br>
     * второй параметр - id параметра <br>
     * третий параметр - id агрегата
     */
    private static final String SQL_GET_LAST_VALUE = "select par_value from dz_input_start " +
            "where obj_id = ? and par_id = ? and stat_aggr = ?";


    /**
     * select для получения id запросов на получение мгновенных данных
     * первый параметр - <ObjectId>id объекта</ObjectId>
     */
    private static final String SQL_GET_INSTANT_COMMAND_ID = "select id from arm_commands " +
            "where to_number(extractValue(XMLType(args), '/ObjectId')) = ? " +
            "and kind = 'AsyncRefresh' and (is_success_execution is null or is_success_execution = 2)";

    /**
     * insert для заполнения таблицы мгновенными данными
     * первый параметр - id объекта
     * второй параметр - значение параметра
     * третий параметр - качество значения
     * четвертый параметр - id параметра
     * пятый параметр - id запроса на мгновенные данные
     * шестой параметр - id объекта
     * седьмой параметр - id параметра
     */
    private static final String INSERT_ASYNC_REFRESH_DATA = "insert into arm_async_refresh_data " +
            "values (?, ?, sysdate - 3/24, ?, ?, ?, sysdate, " +
            "(select extractValue(XMLType('<Group>' || opc_path || '</Group>'), '/Group/ItemName') " +
                "from tsa_opc_element where id in (select opc_element_id from tsa_linked_element " +
                "where aspid_object_id = ? and aspid_param_id = ? and aspid_agr_id is null)), " +
            "null)";



    /**
     * SQL для определения id комманд которые не закрыты по id объекта
     * первый параметр - имя сервера_url
     */
    private static final String SQL_GET_COMMAND_IDS = "select id from arm_commands " +
            "where (extractValue(XMLType(args), '/ObjectId')) = " +
            "      (select aspid_object_id from tsa_linked_object " +
            "           where opc_object_id in (select id from tsa_opc_object " +
            "               where display_name like ?)) " +
            "and kind = 'AsyncRefresh' and (is_success_execution is null or is_success_execution = 2)";

    /**
     * SQL для определения имени объекта по имени сервера и ip адреса прибора
     * первый параметр - имя сервера
     * второй параметр - ip прибора
     */
    private static final String SQL_GET_OBJECT_NAME = "select obj_name from obj_object " +
            "where obj_id = (select id from opc_object where server_name = ? and item_name like ? escape '!' and subscribed = 1)";

    @Resource(name = "jdbc/DataSource")
    private DataSource ds;

    @Resource(name = "jdbc/DataSourceUpload")
    private DataSource dsUpload;

    @Resource
    private EJBContext context;

    @EJB
    private WebConsoleBean webConsoleBean;

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void insertOPCObjects(List<String> objects, String serverName) {
        try (Connection connect = ds.getConnection();
             PreparedStatement stmInsertObject = connect.prepareStatement(SQL_INSERT_OPC_OBJECT)) {
            for (String object: objects) {
                String objectPath = loadObjectPath(object, serverName);
                stmInsertObject.setString(1, object);
                stmInsertObject.setString(2, objectPath);
                try {
                    stmInsertObject.executeUpdate();
                    LOG.info("Успешная вставка объекта " + objectPath);
                } catch(SQLException e) {
                    // TODO Сделать проверку на существование записи и ничего не выводить если запись существует
                    LOG.warning("Данная запись уже существует " + objectPath);
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Ошибка обращения к базе; server name: " + serverName + "; objects: " + objects, e);
        }
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public boolean checkObject(String objectName, String serverName) {
        try (Connection connect = ds.getConnection();
             PreparedStatement stm = connect.prepareStatement(SQL_CHECK_LINKED);
             PreparedStatement stmInsertObject = connect.prepareStatement(SQL_INSERT_OPC_OBJECT)) {
            String objectPath = loadObjectPath(objectName, serverName);
            stm.setString(1, objectPath);

            ResultSet res = stm.executeQuery();
            if (res.next() && (res.getInt(1) == 1)) {
                return true;
            } else {
                stmInsertObject.setString(1, objectName);
                stmInsertObject.setString(2, objectPath);

                try {
                    stmInsertObject.executeUpdate();
                    LOG.info("Успешная вставка объекта: " + objectPath);
                } catch(SQLException e) {
                    // TODO Сделать проверку на существование записи и ничего не выводить если запись существует
                    LOG.warning("Данная запись уже существует: " + objectPath);
                }
                return false;
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Ошибка обращения к базе; server name: " + serverName + "; object name: " + objectName, e);
        }
        return false;
    }

    @Override
    public boolean isLoadConfig(String serverName) {
        try (Connection connect = ds.getConnection();
             PreparedStatement stm = connect.prepareStatement(SQL_CHECK_REQUEST_LOAD_CONFIG)) {
            stm.setString(1, serverName);

            ResultSet res = stm.executeQuery();
            return res.next();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Ошибка обращения к базе; server name: " + serverName, e);
        }
        return false;
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void checkConfigRequest(String serverName) {
        try (Connection connect = ds.getConnection();
             PreparedStatement stm = connect.prepareStatement(SQL_GET_REQUEST_LOAD_CONFIG);
             PreparedStatement stmUpdate = connect.prepareStatement(SQL_UPDATE_CHECK)) {
            stm.setString(1, serverName);

            Map<String, Set<String>> ipMap = new HashMap<>();

            ResultSet res = stm.executeQuery();
            while (res.next()) {
                Matcher m = PATTERN_IPV4.matcher(res.getString(1));
                if (m.find()) {
                    ipMap.putIfAbsent(m.group("ip"), new HashSet<>());
                    ipMap.get(m.group("ip")).add(res.getString(2));
                } else {
                    LOG.warning("Есть запрос на конфигурацию но он не содержит ip address " + res.getString(1) +
                            " server name: " + serverName);
                }
            }

            for (String ip: ipMap.keySet()) {
                try {
                    for (String id: ipMap.get(ip)) {
                        stmUpdate.setInt(1, 2);
                        stmUpdate.setString(2, null);
                        stmUpdate.setString(3, "Обработка запроса на конфигурацию");
                        stmUpdate.setString(4, id);

                        stmUpdate.addBatch();
                    }
                    stmUpdate.executeBatch();

                    Command command = new Command("loadConfig");
                    command.addParameter("server", serverName);
                    command.addParameter("url", ip);

                    webConsoleBean.produceMessage(command);
                } catch (SQLException e) {
                    LOG.log(Level.WARNING, "error while update status", e);
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Ошибка обращения к базе; server name: " + serverName, e);
        }
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void putConfig(Set<String> config, String serverName) {
        try (Connection connect = ds.getConnection();
             PreparedStatement stmObjectId = connect.prepareStatement(SQL_GET_OPC_OBJECT_ID);
             PreparedStatement stmUpdateConfig = connect.prepareStatement(SQL_INSERT_CONFIG);
             PreparedStatement stmUpdateCheck = connect.prepareStatement(SQL_UPDATE_CHECK)) {
            stmObjectId.setString(1, serverName);

            putConfig(stmObjectId, stmUpdateConfig, stmUpdateCheck, config);
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Ошибка обращения к базе; server name: " + serverName + "; config: " + config, e);
            context.setRollbackOnly();
        }
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void putConfig(Set<String> config, String ipAddress, String serverName) {
        try (Connection connect = ds.getConnection();
             PreparedStatement stmObjectId = connect.prepareStatement(SQL_GET_OPC_OBJECT_ID_2);
             PreparedStatement stmUpdateConfig = connect.prepareStatement(SQL_INSERT_CONFIG);
             PreparedStatement stmUpdateCheck = connect.prepareStatement(SQL_UPDATE_CHECK)) {
            stmObjectId.setString(1, serverName);
            stmObjectId.setString(2, "%_" + ipAddress + "_%");

            putConfig(stmObjectId, stmUpdateConfig, stmUpdateCheck, config);
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Ошибка обращения к базе; server name: " + serverName + "; ip address: " +
                    ipAddress + "; config: " + config, e);
            context.setRollbackOnly();
        }
    }

    private void putConfig(PreparedStatement stmObjectId, PreparedStatement stmUpdateConfig,
                                  PreparedStatement stmUpdateCheck, Set<String> config) throws SQLException {
        ResultSet resObjectId = stmObjectId.executeQuery();
        while (resObjectId.next()) {
            int count = 0;

            for (String item: config) {
                String[] items = item.split("::");
                if (items.length == 2) {
                    stmUpdateConfig.setString(1, items[0]);
                    stmUpdateConfig.setString(2, "<ItemName>" + resObjectId.getString(2) + ":" + items[0] +
                            "</ItemName><SysInfo>" + items[1] + "</SysInfo>");
                } else {
                    stmUpdateConfig.setString(1, item);
                    stmUpdateConfig.setString(2, "<ItemName>" + resObjectId.getString(2) + ":" + item + "</ItemName>");
                }
                stmUpdateConfig.setString(3, resObjectId.getString(1));

                try {
                    stmUpdateConfig.executeUpdate();
                    count++;
                    LOG.info("Успешная вставка параметра: " + item);
                } catch (SQLException e) {
                    // TODO Сделать проверку на существование записи и ничего не выводить если запись существует
                    LOG.warning("putConfig Запись уже существует " + item);
                    LOG.warning(e.getMessage() + " " + e.getSQLState() + " " + e.getErrorCode());
                }
            }

            stmUpdateCheck.setInt(1, 1);
            stmUpdateCheck.setString(2, "<" + resObjectId.getString(2) + ">" + config.size() + "</" + resObjectId.getString(2) + ">");
            stmUpdateCheck.setString(3, "Получено '" + config.size() + "' элементов по объекту '" + resObjectId.getString(2) +
                    "'. '" + count + "' новых элементов.");
            stmUpdateCheck.setString(4, resObjectId.getString(3));

            stmUpdateCheck.executeUpdate();
        }
    }

    @Override
    public ArrayList<DataModel> loadObjectParameters(String objectName, String serverName) {
        LocalDateTime startDate;
        ArrayList<DataModel> paramList = new ArrayList<>();

        try (Connection connect = ds.getConnection();
             PreparedStatement stmGetObject = connect.prepareStatement(SQL_GET_OBJECT);
             PreparedStatement stmGetLinkedParameters = connect.prepareStatement(SQL_GET_LINKED_PARAMETERS);
             PreparedStatement stmGetStartDate = connect.prepareStatement(SQL_GET_START_DATE)) {
            stmGetObject.setString(1, loadObjectPath(objectName, serverName));

            ResultSet resGetObject = stmGetObject.executeQuery();
            String objectId;
            if (resGetObject.next()) {
                objectId = resGetObject.getString(1);
            } else {
                return paramList;
            }

            ResultSet resStartDate;

            stmGetLinkedParameters.setString(1, objectId);

            ResultSet resLinked = stmGetLinkedParameters.executeQuery();
            while (resLinked.next()) {
                stmGetStartDate.setInt(1, resLinked.getInt(2));
                stmGetStartDate.setInt(2, resLinked.getInt(3));
                stmGetStartDate.setInt(3, resLinked.getInt(4));

                startDate = null;

                resStartDate = stmGetStartDate.executeQuery();
                while (resStartDate.next()) {
                    startDate = LocalDateTime.parse(resStartDate.getString(1), FORMAT);
                }

                paramList.add(new DataModel(resLinked.getString(1), resLinked.getInt(2), resLinked.getInt(3),
                        resLinked.getInt(4), startDate,
                        (resLinked.getString(5) == null) ? null : resLinked.getString(5).substring(2)));
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Error when load parameters list", e);
        }

        LOG.info("object: " + objectName + ":" + serverName + " parameters count: " + paramList.size());

        return paramList;
    }

    @Override
    @Asynchronous
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public Future<Void> putData(List<DataModel> paramList) {
        try (OracleConnection connect = dsUpload.getConnection().unwrap(oracle.jdbc.OracleConnection.class);
             PreparedStatement stmAlter = connect.prepareStatement("alter session set nls_numeric_characters = '.,'");
             CallableStatement stm = connect.prepareCall(SQL_INSERT_DATA)) {
            stmAlter.execute();

            List<Object> dataList = new ArrayList<>();
            for (DataModel item: paramList) {
                for (ValueModel value: item.getData()) {
                    Date date = new java.sql.Date(value.getTime()
                            .atZone(ZoneId.systemDefault())
                            .toInstant().toEpochMilli());

                    Object[] row = {item.getObjectId(), item.getParamId(), item.getAggregateId(), value.getValue(),
                            value.getQuality(), date, null};
                    Struct str = connect.createStruct("T_DZ_UTIL_INPUT_DATA_ROW", row);
                    dataList.add(str);
                }
            }

            if (!dataList.isEmpty()) {
                long timer = System.currentTimeMillis();

                Array array = connect.createOracleArray("T_DZ_UTIL_INPUT_DATA", dataList.toArray());

                stm.setArray(1, array);
                stm.execute();

                LOG.info("execute upload data; values count: " + dataList.size() +
                        "; upload time: " + (System.currentTimeMillis() - timer) + " milli seconds");
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "error upload data: " + paramList, e);
        }
        return null;
    }

    @Override
    public void putDataWithCalculateIntegrator(List<DataModel> paramList) {
        if (paramList != null) {
            paramList.forEach(dataModel -> {
                String[] splitStr =  dataModel.getParamName().split("::");
                if (splitStr.length == 2) {
                    BigDecimal addValue;
                    BigDecimal newValue;
                    switch (splitStr[1]) {
                        case "5":
                            addValue = getLastValue(dataModel);
                            for (ValueModel valueModel: dataModel.getData()) {
                                newValue = new BigDecimal(valueModel.getValue())
                                        .add(addValue);
                                valueModel.setValue(newValue.toString());
                                addValue = newValue;
                            }
                            break;
                        case "6":
                            addValue = getLastValue(dataModel);
                            for (ValueModel valueModel: dataModel.getData()) {
                                newValue = new BigDecimal(valueModel.getValue())
                                        .multiply(new BigDecimal("60"))
                                        .add(addValue);
                                valueModel.setValue(newValue.toString());
                                addValue = newValue;
                            }
                            break;
                        case "7":
                            addValue = getLastValue(dataModel);
                            for (ValueModel valueModel: dataModel.getData()) {
                                newValue = new BigDecimal(valueModel.getValue())
                                        .multiply(new BigDecimal("3600"))
                                        .add(addValue);
                                valueModel.setValue(newValue.toString());
                                addValue = newValue;
                            }
                            break;
                    }
                }
            });

            putData(paramList);
        }
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void putInstantData(List<DataModel> paramList) {
        try (Connection connection = ds.getConnection();
             PreparedStatement stmGetId = connection.prepareStatement(SQL_GET_INSTANT_COMMAND_ID);
             PreparedStatement stmUpdate = connection.prepareStatement(SQL_UPDATE_CHECK);
             PreparedStatement stmInsert = connection.prepareStatement(INSERT_ASYNC_REFRESH_DATA)) {
            Set<Integer> objectsId = new HashSet<>();
            paramList.forEach(model -> objectsId.add(model.getObjectId()));

            for (Integer id: objectsId) {
                int count = 0;
                for (DataModel model: paramList) {
                    if (model.getObjectId() == id) {
                        count += model.getData().size();
                    }
                }

                stmGetId.setInt(1, id);

                ResultSet res = stmGetId.executeQuery();
                while (res.next()) {
                    stmUpdate.setInt(1, 1);
                    stmUpdate.setString(2, "<" + id + ">" + count + "</" + id + ">");
                    stmUpdate.setString(3, "Получено " + count + " элементов по объекту '" + id + "'.");
                    stmUpdate.setString(4, res.getString(1));

                    stmUpdate.executeUpdate();

                    for (DataModel model: paramList) {
                        if (model.getObjectId() == id) {
                            for (ValueModel valueModel: model.getData()) {
                                stmInsert.setInt(1, id);
                                stmInsert.setString(2, valueModel.getValue());
                                stmInsert.setInt(3, valueModel.getQuality());
                                stmInsert.setInt(4, model.getParamId());
                                stmInsert.setString(5, res.getString(1));
                                stmInsert.setInt(6, id);
                                stmInsert.setInt(7, model.getParamId());

                                stmInsert.addBatch();
                            }
                        }
                    }

                    int[] size = stmInsert.executeBatch();

                    LOG.info("Вставил " + size.length + " мгновенных значений значений по объекту " + id +
                            ". Номер запроса " + res.getString(1));
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Ошибка импорта мгновенных данных в базу", e);
            context.setRollbackOnly();
            return;
        }

        putData(paramList);
    }

    /**
     * Метод получает последнее известно значения параметра
     * @param model данные по параметру
     * @return последнее известное значение
     */
    private BigDecimal getLastValue(DataModel model) {
        BigDecimal result = new BigDecimal("0");
        try (Connection connect = ds.getConnection();
             PreparedStatement stm = connect.prepareStatement(SQL_GET_LAST_VALUE)) {
            stm.setInt(1, model.getObjectId());
            stm.setInt(2, model.getParamId());
            stm.setInt(3, model.getAggregateId());

            ResultSet res = stm.executeQuery();
            if (res.next()) {
                if (res.getString(1) != null) {
                    result = new BigDecimal(res.getString(1));
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "Error when load last value of parameter", e);
        }
        return result;
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void checkInstantRequest(String serverName) {
        try (Connection connect = ds.getConnection();
             PreparedStatement stm = connect.prepareStatement(SQL_CHECK_INSTANT_LOAD);
             PreparedStatement stmUpdate = connect.prepareStatement(SQL_UPDATE_CHECK)) {
            stm.setString(1, serverName);

            Map<String, Set<String>> ipMap = new HashMap<>();

            ResultSet res = stm.executeQuery();
            while (res.next()) {
                Matcher m = PATTERN_IPV4.matcher(res.getString(2));
                if (m.find()) {
                    ipMap.putIfAbsent(m.group("ip"), new HashSet<>());
                    ipMap.get(m.group("ip")).add(res.getString(1));
                } else {
                    LOG.warning("Есть запрос на мгновенные данные но он не содержит ip address: " + res.getString(2) +
                            " server name: " + serverName);
                }
            }

            for (String ip: ipMap.keySet()) {
                try {
                    for (String id: ipMap.get(ip)) {
                        stmUpdate.setInt(1, 2);
                        stmUpdate.setString(2, null);
                        stmUpdate.setString(3, "Обработка запроса на мгновенные данные");
                        stmUpdate.setString(4, id);

                        stmUpdate.addBatch();
                    }
                    stmUpdate.executeBatch();

                    Command command = new Command("loadInstantData");
                    command.addParameter("server", serverName);
                    command.addParameter("url", ip);

                    webConsoleBean.produceMessage(command);
                } catch (SQLException e) {
                    LOG.log(Level.WARNING, "error while update status", e);
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "error when check instant request server name: " + serverName, e);
        }
    }

    @Override
    public ArrayList<DataModel> loadObjectInstantParameters(String serverName, String url) {
        ArrayList<DataModel> result = new ArrayList<>();
        try (Connection connect = ds.getConnection();
             PreparedStatement stm = connect.prepareStatement(SQL_GET_LINKED_PARAMETERS_FOR_INSTANT_DATA)) {
            stm.setString(1, serverName + '_' + url + '%');
            ResultSet res = stm.executeQuery();
            while (res.next()) {
                result.add(new DataModel(res.getString(1), res.getInt(2), res.getInt(3), res.getInt(4),
                        null, (res.getString(5) == null) ? null : res.getString(5).substring(2)));
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "error while load instant data parameters for server name: " + serverName + " url: " + url, e);
        }
        return result;
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void errorExecuteAsyncRefreshCommand(String path, String message) {
        try (Connection connection = ds.getConnection();
             PreparedStatement stmGetIds = connection.prepareStatement(SQL_GET_COMMAND_IDS);
             PreparedStatement stmUpdate = connection.prepareStatement(SQL_UPDATE_CHECK)) {
            stmGetIds.setString(1, path + "%");
            ResultSet resObjectId = stmGetIds.executeQuery();
            while (resObjectId.next()) {
                stmUpdate.setInt(1, 0);
                stmUpdate.setString(2, "<Error>" + message + "</Error>");
                stmUpdate.setString(3, message);
                stmUpdate.setString(4, resObjectId.getString(1));

                stmUpdate.addBatch();
            }
            stmUpdate.executeBatch();
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "error when put failure message", e);
        }
    }

    @Override
    @Asynchronous
    public Future<Void> uploadStatistic(WebStatistic statistic) {
        Jsonb json = JsonbBuilder.create();
        WebSocketServer.sendAllClients("update:" + json.toJson(statistic));
        return null;
    }

    @Override
    @Asynchronous
    public Future<Void> uploadStatistic(String sessionID, List<WebStatistic> statistic) {
        Jsonb json = JsonbBuilder.create();
        WebSocketServer.sendToClient(sessionID, "allStatistic:" + json.toJson(statistic));
        return null;
    }

    @Override
    @Asynchronous
    public Future<Void> uploadLogData(String sessionID, List<LastData> logData) {
        Jsonb json = JsonbBuilder.create();
        WebSocketServer.sendToClient(sessionID, "logData:" + json.toJson(logData));
        return null;
    }

    @Override
    @Asynchronous
    public Future<Void> uploadConfigNames(String sessionID, List<String> configNames) {
        Jsonb json = JsonbBuilder.create();
        WebSocketServer.sendToClient(sessionID, "configNames:" + json.toJson(configNames));
        return null;
    }

    @Override
    public String loadObjectName(String serverName, String ip) {
        try (Connection connect = ds.getConnection();
            PreparedStatement stm = connect.prepareStatement(SQL_GET_OBJECT_NAME)) {
            stm.setString(1, serverName);
            stm.setString(2, "%" + ip + "!_%");

            ResultSet res = stm.executeQuery();
            if (res.next()) {
                return res.getString(1);
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "error when load object name:", e);
        }
        return "";
    }

    @Override
    @Asynchronous
    public void sendInfo(String sessionID, List<ObjectInfoModel> info) {
        Jsonb json = JsonbBuilder.create();
        WebSocketServer.sendToClient(sessionID, "info:" + json.toJson(info));
    }

    /**
     * Метод формирует строку для базы 
     * {@code <OpcKind>Hda</OpcKind><ItemName>"objectName"</ItemName><Server>"serverName"</Server>}
     * @param objectName имя объекта
     * @param serverName имя сервера
     * @return строка результата
     */
    private String loadObjectPath(String objectName, String serverName) {
        return "<OpcKind>Hda</OpcKind><ItemName>" + objectName + "</ItemName><Server>" + serverName + "</Server>";
    }
}
