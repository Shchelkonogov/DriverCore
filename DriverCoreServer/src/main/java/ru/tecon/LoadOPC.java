package ru.tecon;

import oracle.jdbc.OracleConnection;
import ru.tecon.beanInterface.LoadOPCLocal;
import ru.tecon.beanInterface.LoadOPCRemote;
import ru.tecon.model.DataModel;
import ru.tecon.model.ValueModel;

import javax.annotation.Resource;
import javax.ejb.*;
import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.logging.Logger;

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
     * параметр - {@code %<Server>'имя сервера'</Server>}
     */
    private static final String SQL_CHECK_REQUEST_LOAD_CONFIG = "select 1 from dual " +
            "where exists(select * from arm_commands " +
            "where kind = 'ForceBrowse' and args like ? and is_success_execution is null)";



    /**
     * select для определения списка URL <br>
     * по которым база запросила конфигурацию сервера <br>
     * параметр - {@code %<Server>'имя сервера'</Server>}
     */
    private static final String SQL_GET_REQUEST_LOAD_CONFIG = "select regexp_substr(args, '<ItemName>.*</ItemName>') " +
            "from arm_commands where kind = 'ForceBrowse' and args like ? and is_success_execution is null";



    /**
     * select выгружает id запросов на конфигурацию сервера <br>
     * id и имя объектов сервера <br>
     * параметро - {@code %<Server>'имя сервера'</Server>}
     */
    private static final String SQL_GET_OPC_OBJECT_ID = "select b.id, b.display_name, a.id from arm_commands a " +
            "inner join tsa_opc_object b " +
            "on a.args = b.opc_path and a.kind = 'ForceBrowse' " +
            "and a.is_success_execution is null and a.args like ?";
    /**
     * insert который загружает в базу конфигурацию сервера <br>
     * первый параметр - имя параметра <br>
     * второй параметр - {@code <ItemName>'имя объекта':'имя параметра'</ItemName>} <br>
     * третий параметр - id объекта
     */
    private static final String SQL_INSERT_CONFIG = "insert into tsa_opc_element values ((select get_guid_base64 from dual), ?, ?, ?, 1, null)";
    /**
     * update который выставляет статус выполнения insert по добавлению конфигурации <br>
     * первый параметр - {@code <'имя объекта'>'количество вставленных параметров'<'имя объекта'>} <br>
     * второй параметр - комментарии в произвольном виде <br>
     * третий параметр - id запроса
     */
    private static final String SQL_UPDATE_CHECK = "update arm_commands " +
            "set is_success_execution = 1, result_description = ?, display_result_description = ?, end_time = sysdate where id = ?";


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
    private static final String SQL_GET_LINKED_PARAMETERS = "select c.display_name, b.aspid_object_id, b.aspid_param_id, " +
            "b.aspid_agr_id, b.measure_unit_transformer " +
            "from tsa_linked_element b, tsa_opc_element c " +
            "where b.opc_element_id in (select id from tsa_opc_element where opc_object_id = ?) " +
            "and b.opc_element_id = c.id " +
            "and exists(select a.obj_id, a.par_id from dz_par_dev_link a where a.par_id = b.aspid_param_id and a.obj_id = b.aspid_object_id) " +
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
    private static final String SQL_CHECK_INSTANT_LOAD = "select to_number(replace(replace(args, '<ObjectId>', ''), '</ObjectId>', '')) from arm_commands " +
            "where to_number(replace(replace(args, '<ObjectId>', ''), '</ObjectId>', '')) " +
            "in (select id from opc_object where linked = 1 and server_name = ?) " +
            "and kind = 'AsyncRefresh' and is_success_execution is null";
    /**
     * select url по котрым надо загрузить мгновенные данные
     * парметр - objectId
     */
    private static final String SQL_GET_URL_TO_LOAD_INSTANT_DATA = "select display_name from tsa_opc_object " +
            "where id = (select opc_object_id from tsa_linked_object " +
            "where subscribed = 1 and aspid_object_id = ?)";


    /**
     * select для получения списка парамтров для выгрузки мгновенных данных <br>
     * параметр - имя сервера '_' url адрес '%'
     */
    private static final String SQL_GET_LINKED_PARAMETERS_FOR_INSTANT_DATA = "select c.display_name, b.aspid_object_id, " +
            "b.aspid_param_id, b.aspid_agr_id, b.measure_unit_transformer " +
            "from tsa_linked_element b, tsa_opc_element c " +
            "where b.opc_element_id in (select id from tsa_opc_element " +
            "where opc_object_id = (select id from tsa_opc_object " +
            "where display_name like ?)) " +
            "and b.opc_element_id = c.id " +
            "and exists(select a.obj_id, a.par_id from dz_par_dev_link a " +
            "where a.par_id = b.aspid_param_id and a.obj_id = b.aspid_object_id) " +
            "and b.aspid_agr_id is null";

    @Resource(name = "jdbc/DataSource")
    private DataSource ds;

    @Resource(name = "jdbc/DataSourceUpload")
    private DataSource dsUpload;

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
                    LOG.info("insertOPCObjects Успешная вставка объекта " + objectPath);
                } catch(SQLException e) {
                    LOG.warning("insertOPCObjects Данная запись уже существует " + objectPath);
                }
            }
        } catch (SQLException e) {
            LOG.warning("insertOPCObjects ошибка обращения к базе " + e.getMessage() +
                    " objects: " + objects + " serverName: " + serverName);
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
                    LOG.info("checkObject Успешная вставка объекта: " + objectPath);
                } catch(SQLException e) {
                    LOG.warning("checkObject Данная запись уже существует: " + objectPath);
                }
                return false;
            }
        } catch (SQLException e) {
            LOG.warning("checkObject Ошибка обращения к базе " + e.getMessage() +
                    " objectName: " + objectName + " serverName: " + serverName);
        }
        return false;
    }

    @Override
    public boolean isLoadConfig(String serverName) {
        try (Connection connect = ds.getConnection();
             PreparedStatement stm = connect.prepareStatement(SQL_CHECK_REQUEST_LOAD_CONFIG)) {
            stm.setString(1, "%<Server>" + serverName + "</Server>");

            ResultSet res = stm.executeQuery();
            return res.next();
        } catch (SQLException e) {
            LOG.warning("isLoadConfig Ошибка обращения к базе " + e.getMessage() + " serverName: " + serverName);
            return false;
        }
    }

    @Override
    public ArrayList<String> getURLToLoadConfig(String serverName) {
        ArrayList<String> result = new ArrayList<>();
        try (Connection connect = ds.getConnection();
             PreparedStatement stm = connect.prepareStatement(SQL_GET_REQUEST_LOAD_CONFIG)) {
            stm.setString(1, "%<Server>" + serverName + "</Server>");

            ResultSet res = stm.executeQuery();
            while (res.next()) {
                result.add(res.getString(1).split("_")[1]);
            }
            return result;
        } catch (SQLException e) {
            LOG.warning("Ошибка обращения к базе " + e.getMessage() + " serverName: " + serverName);
            return result;
        }
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void putConfig(List<String> config, String serverName) {
        try (Connection connect = ds.getConnection();
             PreparedStatement stmObjectId = connect.prepareStatement(SQL_GET_OPC_OBJECT_ID);
             PreparedStatement stmUpdateConfig = connect.prepareStatement(SQL_INSERT_CONFIG);
             PreparedStatement stmUpdateCheck = connect.prepareStatement(SQL_UPDATE_CHECK)) {
            stmObjectId.setString(1, "%<Server>" + serverName + "</Server>");

            ResultSet resObjectId = stmObjectId.executeQuery();
            while (resObjectId.next()) {
                int count = 0;
                count = getCount(stmUpdateConfig, resObjectId, count, config);

                stmUpdateCheck.setString(1, "<" + resObjectId.getString(2) + ">" + count + "</" + resObjectId.getString(2) + ">");
                stmUpdateCheck.setString(2, "Получено " + count + " элементов по объекту '" + resObjectId.getString(2) + "'.");
                stmUpdateCheck.setString(3, resObjectId.getString(3));

                stmUpdateCheck.executeUpdate();
            }
        } catch (SQLException e) {
            LOG.warning("putConfig Ошибка обращения к базе " + e.getMessage() +
                    " config: " + config + " serverName: " + serverName);
        }
    }

    @Override
    public void putConfig(List<String> config, Map<String, List<String>> instantConfig, String serverName) {
        try (Connection connect = ds.getConnection();
             PreparedStatement stmObjectId = connect.prepareStatement(SQL_GET_OPC_OBJECT_ID);
             PreparedStatement stmUpdateConfig = connect.prepareStatement(SQL_INSERT_CONFIG);
             PreparedStatement stmUpdateCheck = connect.prepareStatement(SQL_UPDATE_CHECK)) {
            stmObjectId.setString(1, "%<Server>" + serverName + "</Server>");

            ResultSet resObjectId = stmObjectId.executeQuery();
            while (resObjectId.next()) {
                int count = 0;
                count = getCount(stmUpdateConfig, resObjectId, count, config);

                for (String key: instantConfig.keySet()) {
                    if (resObjectId.getString(2).contains(key)) {
                        count = getCount(stmUpdateConfig, resObjectId, count, instantConfig.get(key));
                        break;
                    }
                }

                stmUpdateCheck.setString(1, "<" + resObjectId.getString(2) + ">" + count + "</" + resObjectId.getString(2) + ">");
                stmUpdateCheck.setString(2, "Получено " + count + " элементов по объекту '" + resObjectId.getString(2) + "'.");
                stmUpdateCheck.setString(3, resObjectId.getString(3));

                stmUpdateCheck.executeUpdate();
            }
        } catch (SQLException e) {
            LOG.warning("putConfig Ошибка обращения к базе " + e.getMessage() +
                    " config: " + config + " serverName: " + serverName);
        }
    }

    private int getCount(PreparedStatement stmUpdateConfig, ResultSet resObjectId, int count, List<String> config) throws SQLException {
        for (String item: config) {
            stmUpdateConfig.setString(1, item);
            stmUpdateConfig.setString(2, "<ItemName>" + resObjectId.getString(2) + ":" + item + "</ItemName>");
            stmUpdateConfig.setString(3, resObjectId.getString(1));

            try {
                stmUpdateConfig.executeUpdate();
                count++;
                LOG.info("putConfig Успешная вставка " + item);
            } catch (SQLException e) {
                LOG.warning("putConfig Запись уже существует " + item);
            }
        }
        return count;
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
                return null;
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
            LOG.warning("loadObjectParams SQLException: " + e.getMessage());
        }

        LOG.info("loadObjectParams object: " + objectName + ":" + serverName +
                " parameters count: " + paramList.size());

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

            if (dataList.size() > 0) {
                long timer = System.currentTimeMillis();
//                LOG.info("putData put: " + dataList.size() + " values " + paramList);

                Array array = connect.createOracleArray("T_DZ_UTIL_INPUT_DATA", dataList.toArray());

                stm.setArray(1, array);
                stm.execute();

                LOG.info("putData done put: " + dataList.size() + 
                        " values; put time: " + (System.currentTimeMillis() - timer));
            }
        } catch (SQLException e) {
            LOG.warning("putData error upload: " + e.getMessage() + " " + paramList);
        }
        return null;
    }

    @Override
    public void checkInstantRequest(String serverName) {
        try (Connection connect = ds.getConnection();
             PreparedStatement stmGetObjectId = connect.prepareStatement(SQL_CHECK_INSTANT_LOAD);
             PreparedStatement stmGetUrl = connect.prepareStatement(SQL_GET_URL_TO_LOAD_INSTANT_DATA)) {
            stmGetObjectId.setString(1, serverName);
            ResultSet resObjectId = stmGetObjectId.executeQuery();
            while (resObjectId.next()) {
                stmGetUrl.setInt(1, resObjectId.getInt(1));
                ResultSet resUrl = stmGetUrl.executeQuery();
                if (resUrl.next()) {
                    WebSocketServer.sendTo(serverName, "loadInstantData " + resUrl.getString(1).split("_")[1]);
                }
            }
        } catch (SQLException e) {
            LOG.warning("error when check instant request: " + e.getMessage() + " serverName: " + serverName);
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
            LOG.warning("error while load instant data parameters: " + e.getMessage());
        }
        return result;
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
