package ru.tecon.ejb.instantData;

import ru.tecon.dataUploaderClient.beanInterface.instantData.InstantDataRemote;
import ru.tecon.ejb.WebConsoleBean;
import ru.tecon.model.Command;

import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.Remote;
import javax.ejb.Stateless;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stateless bean реализация интерфейса для загрузки мгновенных данных
 * @author Maksim Shchelkonogov
 */
@Stateless(name = "MFK1500InstantData", mappedName = "ejb/MFK1500InstantData")
@Remote(InstantDataRemote.class)
public class InstantDataBean implements InstantDataRemote {

    private static final Logger LOG = Logger.getLogger(InstantDataBean.class.getName());

    private static final Pattern PATTERN_IPV4 = Pattern.compile("_(?<ip>((0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?)\\.){3}(0|1\\d?\\d?|2[0-4]?\\d?|25[0-5]?|[3-9]\\d?))_", Pattern.CASE_INSENSITIVE);

    private static final String SELECT_ASYNC_REQUEST = "select server_name, obj_name from arm_tecon_commands where rowid = ?";

    @Resource(name = "jdbc/DataSource")
    private DataSource ds;

    @EJB
    private WebConsoleBean webConsoleBean;

    @Override
    public void initLoadInstantData(String rowID) {
        try (Connection connect = ds.getConnection();
             PreparedStatement stm = connect.prepareStatement(SELECT_ASYNC_REQUEST)) {
            stm.setString(1, rowID);

            ResultSet res = stm.executeQuery();
            if (res.next()) {
                Matcher m = PATTERN_IPV4.matcher(res.getString(2));
                if (m.find()) {
                    Command command = new Command("loadInstantData");
                    command.addParameter("server", res.getString(1));
                    command.addParameter("url", m.group("ip"));
                    command.addParameter("rowID", rowID);

                    webConsoleBean.produceMessage(command);
                } else {
                    LOG.warning("Есть запрос на мгновенные данные но он не содержит ip address: " + res.getString(2) +
                            " rowID: " + rowID);
                }
            }
        } catch (SQLException e) {
            LOG.log(Level.WARNING, "error processing instant request rowID: " + rowID, e);
        }
    }
}
