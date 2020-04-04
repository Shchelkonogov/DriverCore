package ru.tecon.traffic;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class MonitorInputStream extends FilterInputStream {

    private Statistic statistic;

    /**
     * Creates a <code>FilterInputStream</code>
     * by assigning the  argument <code>in</code>
     * to the field <code>this.in</code> so as
     * to remember it for later use.
     *
     * @param in the underlying input stream, or <code>null</code> if
     *           this instance is to be created without an underlying stream.
     */
    public MonitorInputStream(InputStream in) {
        super(in);
    }

    @Override
    public int read() throws IOException {
        int c = in.read();
        statistic.updateInputTraffic(c);
        return c;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int nRead = in.read(b, off, len);
        statistic.updateInputTraffic(nRead);
        return nRead;
    }



    public void setStatistic(Statistic statistic) {
        this.statistic = statistic;
    }
}
