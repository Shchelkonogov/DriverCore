package ru.tecon.traffic;

/**
 * Перечисления возможных блокировок
 * @author Maksim Shchelkonogov
 */
public enum BlockType {

    TRAFFIC("превышение трафика"),
    LINKED("не слинковано"),
    SERVER_ERROR("ошибка сервера"),
    USER("заблокировано пользователем"),
    LINK_ERROR("разрыв соединения"),
    DEVICE_CHANGE("изменился прибор");

    private String name;

    BlockType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
