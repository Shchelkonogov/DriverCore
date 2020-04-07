package ru.tecon.controller;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class LogStage {

    private static List<Byte> bytes = new ArrayList<>();

    private static Stage stage = new Stage();
    private static TextArea ta = new TextArea();
    private static Button button = new Button("ok");

    static {
        ta.setEditable(false);

        button.setPrefWidth(100);
        button.setOnAction(event -> stage.close());

        AnchorPane pane = new AnchorPane();

        AnchorPane.setBottomAnchor(ta, 40d);
        AnchorPane.setLeftAnchor(ta, 0d);
        AnchorPane.setTopAnchor(ta, 0d);
        AnchorPane.setRightAnchor(ta, 0d);

        AnchorPane.setBottomAnchor(button, 5d);
        AnchorPane.setRightAnchor(button, 200d);

        pane.getChildren().addAll(button, ta);

        stage.setTitle("logs");
        stage.setResizable(false);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setScene(new Scene(pane, 500, 300));

        PrintStream ps = new PrintStream(new Console(), true);
        System.setOut(ps);
    }

    public static void setOwner(Window owner) {
        stage.initOwner(owner);
    }

    public static void show() {
        bytes.clear();
        stage.show();
    }

    private static void update() {
        byte[] array = new byte[bytes.size()];
        int q = 0;
        for (Byte current : bytes) {
            array[q] = current;
            q++;
        }
        ta.setText(new String(array, StandardCharsets.UTF_8));
        ta.appendText("");
    }

    public static class Console extends OutputStream {
        private PrintStream out;

        Console() {
            out = System.out;
        }

        @Override
        public void write(int i) {
            Platform.runLater(() ->  {
                bytes.add((byte)i);
                out.write(i);
                update();
            });
        }

        @Override
        public void write(byte[] i) {
            Platform.runLater(() -> {
                for (byte b : i) {
                    bytes.add(b);
                    out.write(b);
                }
                update();
            });
        }
    }
}
