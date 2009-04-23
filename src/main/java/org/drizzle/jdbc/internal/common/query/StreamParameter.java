package org.drizzle.jdbc.internal.common.query;

import static org.drizzle.jdbc.internal.common.Utils.needsEscaping;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * User: marcuse
 * Date: Feb 19, 2009
 * Time: 8:53:14 PM
 */
public class StreamParameter implements ParameterHolder{
    //private final InputStream stream;
    private final long length;
    private final byte [] buffer;
    public StreamParameter(InputStream is, long length) throws IOException {
        buffer = new byte[(int) (length*2) + 2];
        int pos=0;
        buffer[pos++] = '"';
        for(int i = 0;i<length;i++) {
            byte b = (byte) is.read();
            if(needsEscaping(b))
                buffer[pos++]='\\';
            buffer[pos++]=b;
        }
        buffer[pos++] = '"';
        this.length = pos;
    }

    public void writeTo(OutputStream os) throws IOException {
        os.write(buffer,0, (int) length);
    }

    public long length() {
        return length;
    }
}