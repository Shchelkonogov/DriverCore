package ru.tecon.instantData;

import ru.tecon.ProjectProperty;
import ru.tecon.model.DataModel;
import ru.tecon.server.Utils;
import ru.tecon.webSocket.WebSocketClient;

import javax.naming.NamingException;
import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class InstantDataService {

    private static final Logger LOG = Logger.getLogger(InstantDataService.class.getName());

    /**
     * Метод запускает службу которая обрабатывает запросы на мгновенные данные
     */
    public static void startService() {
        new WebSocketClient().connectToWebSocketServer();
        if (!ProjectProperty.isPushFromDataBase()) {
            Executors.newSingleThreadScheduledExecutor().scheduleWithFixedDelay(() -> {
                try {
                    Utils.loadRMI().checkInstantRequest(ProjectProperty.getServerName());
                } catch (NamingException e) {
                    LOG.warning("error instant data service. Message: " + e.getMessage());
                }
            }, 5, 30, TimeUnit.SECONDS);
        }
    }

    public static void uploadInstantData(String url) {
        try {
            List<DataModel> parameters = Utils.loadRMI().loadObjectInstantParameters(ProjectProperty.getServerName(), url);

            System.out.println(parameters);
        } catch (NamingException e) {
            LOG.warning("error with load RMI: " + e.getMessage());
        }
    }

    public static void main1(String[] args) {
        System.out.println("TNV_H".getBytes().length + 1 + "REAL".getBytes().length + 1 + 4);
        ByteBuffer bf = ByteBuffer.allocate(4).putInt("TNV_H".getBytes().length + 1 + "REAL".getBytes().length + 1 + 4);
        System.out.println(Arrays.toString(bf.array()));
        System.out.println();

        System.out.println(Arrays.toString("REAL".getBytes()));

        System.out.println((byte)128);


    }

    public static void main(String[] args) throws IOException {
//        TNV_H:REAL:Текущие данные



        Socket socket = new Socket(InetAddress.getByName("192.168.1.26"), 30001);

        BufferedInputStream in = new BufferedInputStream(socket.getInputStream());
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());

        byte[] data = new byte[31];
////        msg_type
//        data[0] = 5;
//        data[1] = 0;
//        data[2] = 0;
//        data[3] = 0;
//
////        msg_param
//        data[4] = 1;
//        data[5] = 0;
//        data[6] = 0;
//        data[7] = 0;
//
////        msg_len
//        data[8] = 0;
//        data[9] = 0;
//        data[10] = 0;
//        data[11] = 0;
//
////        msg_flags
//        data[12] = 0;
//        data[13] = 0;
//
////        __reserved
//        data[14] = 0;
//        data[15] = 0;

        data[0] = 6;
        data[1] = 0;
        data[2] = 0;
        data[3] = 0;

        data[4] = 5;   //0000 0101  0000 0010  0000 0000  0000 0000
        data[5] = 2;
        data[6] = 0;
        data[7] = 0;

        ByteBuffer bf = ByteBuffer.allocate(4).putInt("TNV_H".getBytes().length + 1 + "REAL".getBytes().length + 1 + 4); // 15

        data[8] = 15; //размер списка
        data[9] = 0;
        data[10] = 0;
        data[11] = 0;

        data[12] = 0;
        data[13] = 0;

        data[14] = 0;
        data[15] = 0;



        data[16] = 72; //TNV_H
        data[17] = 95;
        data[18] = 86;
        data[19] = 78;
        data[20] = 84;

        data[21] = 0;

        data[22] = 76; //REAL
        data[23] = 65;
        data[24] = 69;
        data[25] = 82;

        data[26] = 0;

        data[27] = 4;
        data[28] = 0;
        data[29] = 0;
        data[30] = 0;

//        byte[] ddd = new byte[15];
//        ddd.


        out.write(data);
        out.flush();

        byte[] result = new byte[1];

        List<Byte> resultList = new ArrayList<>();

        while (in.read(result, 0, 1) != -1) {
            resultList.add(result[0]);
        }

        System.out.println(resultList);

        Socket socket1 = new Socket(InetAddress.getByName("192.168.1.26"), 30001);

        BufferedInputStream in1 = new BufferedInputStream(socket1.getInputStream());
        DataOutputStream out1 = new DataOutputStream(socket1.getOutputStream());

        byte[] data1 = new byte[16];
//        msg_type
        data1[0] = 7;
        data1[1] = 0;
        data1[2] = 0;
        data1[3] = 0;

//        msg_param
        data1[4] = 1;
        data1[5] = 0;
        data1[6] = 0;
        data1[7] = 0;

//        msg_len
        data1[8] = 0;
        data1[9] = 0;
        data1[10] = 0;
        data1[11] = 0;

//        msg_flags
        data1[12] = 0;
        data1[13] = 0;

//        __reserved
        data1[14] = 0;
        data1[15] = 0;

        out1.write(data1);
        out1.flush();

        byte[] result1 = new byte[1];

        List<Byte> resultList1 = new ArrayList<>();

        while (in1.read(result1, 0, 1) != -1) {
            resultList1.add(result1[0]);
        }

        System.out.println(resultList1);
    }
}
