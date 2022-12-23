package ru.tecon.traffic;

import ru.tecon.mfk1500Server.DriverProperty;
import ru.tecon.mfk1500Server.MFK1500Server;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;

/**
 * Класс для открытия socket к контроллеру MFK1500
 * и формирование InputStream и OutputStream с мониторингом трафика
 * @author Maksim Shchelkonogov
 */
public final class ControllerSocket implements Closeable {

    private Socket socket;
    private MonitorInputStream in;
    private MonitorOutputStream out;

    public ControllerSocket(String url) throws IOException {
        socket = new Socket(InetAddress.getByName(url), DriverProperty.getInstance().getInstantPort());

        Statistic st = MFK1500Server.getStatistic(url);

        in = new MonitorInputStream(socket.getInputStream());
        in.setStatistic(st);

        out = new MonitorOutputStream(socket.getOutputStream());
        out.setStatistic(st);
    }

    public InputStream getInputStream() {
        return in;
    }

    public OutputStream getOutputStream() {
        return out;
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
