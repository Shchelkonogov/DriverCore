package ru.tecon.servlet;

import ru.tecon.WebSocketServer;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Сервлет для загрузки конфигурации объектов.
 * Входные параметры serverName и url.
 * ServerName для того что бы по webSocket передать
 * запрос на нужный поключеннный клиент.
 * url для того что бы клиент знал по какому адресу живет прибор
 * для запроса от него конфигурации по мгновенным данным.
 * Пример запроса: http://localhost:7001/DriverCore/loadConfig?serverName=MFK1500-1&url=192.168.1.26
 */
@WebServlet(urlPatterns = "/loadConfig")
public class LoadConfig extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        WebSocketServer.sendTo(req.getParameter("serverName"), "loadConfig " + req.getParameter("url"));
    }
}
