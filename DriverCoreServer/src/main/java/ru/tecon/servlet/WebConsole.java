package ru.tecon.servlet;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Сервлет обертка, что бы разделить консоль для просмотра и консоль для управления
 * @author Maksim Shchelkonogov
 */
@WebServlet(urlPatterns = {"/console", "/adm/console"})
public class WebConsole extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        switch (req.getServletPath()) {
            case "/adm/console":
                req.getRequestDispatcher("/console.xhtml?adm=true").forward(req, resp);
                break;
            case "/console":
                req.getRequestDispatcher("/console.xhtml").forward(req, resp);
                break;
        }
    }
}
