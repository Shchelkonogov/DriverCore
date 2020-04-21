package ru.tecon.traffic;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class MonitorOutputStream extends FilterOutputStream {

    private Statistic statistic;

    /**
     * Creates an output stream filter built on top of the specified
     * underlying output stream.
     *
     * @param out the underlying output stream to be assigned to
     *            the field <tt>this.out</tt> for later use, or
     *            <code>null</code> if this instance is to be
     *            created without an underlying stream.
     */
    public MonitorOutputStream(OutputStream out) {
        super(out);
    }

    @Override
    public void write(byte[] b) throws IOException {
        out.write(b);
        statistic.updateOutputTraffic(b.length);
    }

    @Override
    public void write(int b) throws IOException {
        out.write(b);
        statistic.updateOutputTraffic(1);
    }

    public void setStatistic(Statistic statistic) {
        this.statistic = statistic;
    }
}
