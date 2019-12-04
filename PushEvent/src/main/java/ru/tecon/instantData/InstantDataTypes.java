package ru.tecon.instantData;

public enum InstantDataTypes {

    LWORD(8),
    DATE(4),
    ULINT(8),
    UDINT(4),
    UINT(2),
    USINT(1),
    LINT(8),
    LREAL(8),
    STRING(1),
    BOOL(1),
    TIME(4),
    REAL(4),
    DINT(4),
    INT(2),
    SINT(1),
    DWORD(4),
    WORD(2),
    BYTE(1),
    TYPVA(4);

    private int size;

    InstantDataTypes(int size) {
        this.size = size;
    }

    public int getSize() {
        return size;
    }
}
