package ru.tecon.servlet;

import ru.tecon.WebSocketServer;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet(urlPatterns = "/loadConfig")
public class LoadConfig extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        WebSocketServer.sendTo(req.getParameter("serverName"), "loadConfig " + req.getParameter("url"));
    }
}
