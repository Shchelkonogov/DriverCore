package ru.tecon.servlet;

import ru.tecon.WebSocketServer;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Сервлет для отправки запроса на загрузку мновенных данных объекта.
 * Входные параметры serverName и url.
 * ServerName для того что бы по webSocket передать
 * запрос на нужный поключеннный клиент.
 * url для того что бы клиент знал по какому адресу живет прибор
 * для запроса от него мгновенных данных.
 * Пример запроса: http://localhost:7001/DriverCore/loadInstantData?serverName=MFK1500-1&url=192.168.1.26
 */
@WebServlet(urlPatterns = "/loadInstantData")
public class LoadInstantData extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        WebSocketServer.sendTo(req.getParameter("serverName"), "loadInstantData " + req.getParameter("url"));
    }
}
