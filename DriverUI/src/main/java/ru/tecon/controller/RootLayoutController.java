package ru.tecon.controller;

import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import ru.tecon.exception.MyServerStartException;
import ru.tecon.traffic.Event;
import ru.tecon.server.EchoSocketServer;
import ru.tecon.traffic.Statistic;

public class RootLayoutController {

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
    private TableColumn<Statistic, String> objectNameColumn;

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
    private TableColumn<Statistic, String> lastRequestTimeColumn;

    @FXML
    private TableColumn<Statistic, String> block;

    @FXML
    private TableView<Statistic> tableView;

    @FXML
    public void initialize() {
        ipColumn.setCellValueFactory(new PropertyValueFactory<>("ip"));
        objectNameColumn.setCellValueFactory(new PropertyValueFactory<>("objectName"));
        inputTrafficColumn.setCellValueFactory(new PropertyValueFactory<>("inputTraffic"));
        outputTrafficColumn.setCellValueFactory(new PropertyValueFactory<>("outputTraffic"));
        trafficColumn.setCellValueFactory(new PropertyValueFactory<>("traffic"));
        monthTrafficColumn.setCellValueFactory(new PropertyValueFactory<>("monthTraffic"));
        lastRequestTimeColumn.setCellValueFactory(new PropertyValueFactory<>("lastRequestTime"));
        socketCountColumn.setCellValueFactory(new PropertyValueFactory<>("socketCount"));
        block.setCellValueFactory(new PropertyValueFactory<>("blockToString"));

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

        tableView.getItems().clear();

        LogStage.show();

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

//        testEvent(new Random().nextInt(10));
    }

    @FXML
    private void onStopClick() {
        EchoSocketServer.stopSocket();

        LogStage.show();

        startButton.setDisable(false);
        stopButton.setDisable(true);
    }

//    private void testEvent(int count) {
//        for (int i = 0; i < count; i++) {
//            Statistic st = new Statistic("255.255.255." + i, event, false);
//            Thread thread = new Thread(() -> {
//                Random r = new Random();
//                while (true) {
//                    st.updateInputTraffic(r.nextInt(100));
//                    st.updateOutputTraffic(r.nextInt(100));
//                    try {
//                        Thread.sleep(r.nextInt(1000));
//                    } catch (InterruptedException e) {
//                        e.printStackTrace();
//                    }
//                }
//            });
//            thread.setDaemon(true);
//            thread.start();
//        }
//    }
}
