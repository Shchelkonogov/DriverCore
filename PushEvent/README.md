На windows ставится как сервис с помощью nssm. <br>
В cmd пишем nssm.exe install <serviceName>. <br>
В открывшемся меню задаем <br>
Path java.exe <br>
Startup directory <указываем папку с jar файлом> <br>
Arguments пример <-jar "C:\MFK1500\PushEvent.jar" "C:\MFK1500\resources\config.properties"> <br>
На след закладке можно указать description <br><br>
Для запуска из cmd из любого места надо передавать 3 параметра в jar путь к config файлу, путь к папке с jar и путь к файлу log.properties<br>
Если запускать через cmd из той же папке где jar достаточно одного первого параметра


-XX:+UseG1GC -XX:+UseStringDeduplication -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=9010 -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Djava.rmi.server.hostname=172.16.4.41