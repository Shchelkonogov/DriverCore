package ru.tecon.controller;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleObjectProperty;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import ru.tecon.exception.MyServerStartException;
import ru.tecon.traffic.Event;
import ru.tecon.server.EchoSocketServer;
import ru.tecon.traffic.Statistic;

import java.time.format.DateTimeFormatter;
import java.util.Random;

public class RootLayoutController {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");

    private Event event = new Event() {
        @Override
        public void addItem(Statistic statistic) {
            for (Statistic stat: tableView.getItems()) {
                if (stat.getIp().equals(statistic.getIp())) {
                    return;
                }
            }
            tableView.getItems().add(statistic);
        }

        @Override
        public void update() {
            tableView.refresh();
        }
    };

    @FXML
    private Button startButton;

    @FXML
    private Button stopButton;

    @FXML
    private TableColumn<Statistic, String> ipColumn;

    @FXML
    private TableColumn<Statistic, String> inputTrafficColumn;

    @FXML
    private TableColumn<Statistic, String> outputTrafficColumn;

    @FXML
    private TableColumn<Statistic, String> monthTrafficColumn;

    @FXML
    private TableColumn<Statistic, String> trafficColumn;

    @FXML
    private TableColumn<Statistic, Integer> socketCountColumn;

    @FXML
    private TableColumn<Statistic, String> time1;

    @FXML
    private TableColumn<Statistic, String> time2;

    @FXML
    private TableColumn<Statistic, String> block;

    @FXML
    private TableView<Statistic> tableView;

    @FXML
    public void initialize() {
        ipColumn.setCellValueFactory(new PropertyValueFactory<>("ip"));

        inputTrafficColumn.setCellValueFactory(new PropertyValueFactory<>("inputTraffic"));
        outputTrafficColumn.setCellValueFactory(new PropertyValueFactory<>("outputTraffic"));
        trafficColumn.setCellValueFactory(new PropertyValueFactory<>("traffic"));
        monthTrafficColumn.setCellValueFactory(new PropertyValueFactory<>("monthTraffic"));

        time1.setCellValueFactory(cellData -> new SimpleObjectProperty<>(
                cellData.getValue().getTrafficStartTime() == null ? "" : cellData.getValue().getTrafficStartTime().format(FORMATTER)));
        time2.setCellValueFactory(cellData -> new SimpleObjectProperty<>(
                cellData.getValue().getSocketStartTime() == null ? "" : cellData.getValue().getSocketStartTime().format(FORMATTER)));

        socketCountColumn.setCellValueFactory(new PropertyValueFactory<>("socketCount"));

        block.setCellValueFactory(cellData -> new SimpleObjectProperty<>(cellData.getValue().isBlock() ? "Заблокировано" : "Свободно"));

        tableView.setRowFactory(param -> {
            TableRow<Statistic> row = new TableRow<>();

            ContextMenu rowMenu = new ContextMenu();
            MenuItem blockItem = new MenuItem("блокировать");
            blockItem.setOnAction(event1 -> tableView.getSelectionModel().getSelectedItem().setBlock(true));
            MenuItem unblockItem = new MenuItem("разблокировать");
            unblockItem.setOnAction(event1 -> tableView.getSelectionModel().getSelectedItem().setBlock(false));
            rowMenu.getItems().addAll(blockItem, unblockItem);

            row.contextMenuProperty().bind(
                    Bindings.when(Bindings.isNotNull(row.itemProperty()))
                            .then(rowMenu)
                            .otherwise((ContextMenu)null));
            return row;
        });

        EchoSocketServer.setEvent(event);
        EchoSocketServer.setCloseApplication(false);
    }

    @FXML
    private void onStartClick() {
        startButton.setDisable(true);

        Thread serviceThread = new Thread(() -> {
            try {
                stopButton.setDisable(false);
                EchoSocketServer.startService(System.getProperty("user.dir") + "/resources/config.properties");
            } catch (MyServerStartException e) {
                startButton.setDisable(false);
                stopButton.setDisable(true);
                e.printStackTrace();
            }
        });
        serviceThread.setDaemon(true);
        serviceThread.start();
        LogStage.show();

//        testEvent();
    }

    @FXML
    private void onStopClick() {
        EchoSocketServer.stopSocket();

        LogStage.show();

        startButton.setDisable(false);
        stopButton.setDisable(true);
    }

    private void testEvent() {
        Statistic s = new Statistic("255.255.255.255", event);
        Statistic s1 = new Statistic("10.10.10.2", event);

        Thread t1 = new Thread(() -> {
            Random r = new Random();
            while (true) {
                s.updateInputTraffic(10);
                s.updateOutputTraffic(5);
                try {
                    Thread.sleep(r.nextInt(1000));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        t1.setDaemon(true);
        t1.start();

        Thread t2 = new Thread(() -> {
            Random r = new Random();
            while (true) {
                s1.updateInputTraffic(1);
                s1.updateOutputTraffic(2);
                try {
                    Thread.sleep(r.nextInt(1000));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        t2.setDaemon(true);
        t2.start();
    }
}
