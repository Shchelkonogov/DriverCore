package ru.tecon.controllerData;

import ru.tecon.isacom.IsacomSimpleTypes;

/**
 * Перечисления параметров, которую отображаем как системные переменные контроллера
 */
enum ControllerSystemVariable {

    ARC_WRITE_MINUTES(IsacomSimpleTypes.DINT, true),
    ARC_WRITE_HOURS(IsacomSimpleTypes.DINT, true),
    TS_READ_DELAY(IsacomSimpleTypes.TIME, true);

    private IsacomSimpleTypes type;
    private boolean write;

    ControllerSystemVariable(IsacomSimpleTypes type, boolean write) {
        this.type = type;
        this.write = write;
    }

    IsacomSimpleTypes getType() {
        return type;
    }

    static boolean isContains(String value) {
        try {
            ControllerSystemVariable.valueOf(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static boolean isWrite(String value) {
        if (isContains(value)) {
            return ControllerSystemVariable.valueOf(value).write;
        } else {
            return false;
        }
    }
}
