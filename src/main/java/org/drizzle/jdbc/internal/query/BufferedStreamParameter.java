package org.drizzle.jdbc.internal.query;

import static org.drizzle.jdbc.internal.Utils.needsEscaping;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * Created by IntelliJ IDEA.
 * User: marcuse
 * Date: Feb 19, 2009
 * Time: 8:56:34 PM
 * To change this template use File | Settings | File Templates.
 */
public class BufferedStreamParameter implements ParameterHolder{
    private byte [] byteRepresentation;
    private int length;

    public BufferedStreamParameter(InputStream is) throws IOException {
        byte b;
        byte [] tempByteRepresentation=new byte[1000];
        int pos = 0;
        while((b= (byte) is.read())!=-1) {
            if(pos>tempByteRepresentation.length-2) { //need two places in worst case
                tempByteRepresentation=Arrays.copyOf(tempByteRepresentation,tempByteRepresentation.length*2);
            }
            if(needsEscaping(b))
                tempByteRepresentation[pos++]='\\';
            tempByteRepresentation[pos++]=b;
        }
        length=pos;
        byteRepresentation=tempByteRepresentation;

    }

    public void writeTo(OutputStream os) throws IOException {
        for(byte b: byteRepresentation) {
            os.write(b);
        }
    }

    public long length() {
        return length;
    }
}