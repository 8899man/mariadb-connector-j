package org.drizzle.jdbc.internal.query.drizzle;

import static org.drizzle.jdbc.internal.Utils.countChars;
import org.drizzle.jdbc.internal.query.ParameterizedQuery;
import org.drizzle.jdbc.internal.query.ParameterHolder;
import org.drizzle.jdbc.internal.query.IllegalParameterException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 .
 * User: marcuse
 * Date: Feb 18, 2009
 * Time: 10:13:42 PM

 */
public class DrizzleParameterizedQuery implements ParameterizedQuery {
    private final static Logger log = LoggerFactory.getLogger(DrizzleParameterizedQuery.class);
    private List<ParameterHolder> parameters;
    private final int paramCount;
    private final String query;

    public DrizzleParameterizedQuery(String query) {
        this.query=query;
        this.paramCount=countChars(query,'?');
        log.debug("Found {} questionmarks",paramCount);
        parameters=new ArrayList<ParameterHolder>(paramCount);
    }
    
    public DrizzleParameterizedQuery(ParameterizedQuery query) {
        this.query=query.getQuery();
        this.paramCount=query.getParamCount();
        parameters=new ArrayList<ParameterHolder>(paramCount);
        log.debug("Copying an existing parameterized query");    
    }

    public void setParameter(int position, ParameterHolder parameter) throws IllegalParameterException {
        log.info("Setting parameter {} at position {}",parameter.toString(),position);
        if(position>=0 && position<paramCount)
            this.parameters.add(position,parameter);
        else
            throw new IllegalParameterException("No '?' on that position");
    }

    public void clearParameters() {
        this.parameters.clear();
    }

    public int length() {
        int length = query.length() - paramCount; // remove the ?s
        for(ParameterHolder param : parameters)
            length+=param.length();
        return length;
    }

    public void writeTo(OutputStream os) throws IOException {
//        log.info("Sending query to outputstream");
        StringReader strReader = new StringReader(query);
        int ch;
        int paramCounter=0;
        while((ch=strReader.read())!=-1) {
            if(ch=='?') {
 //               log.debug("Found '?', sending parameter {}",parameters.get(paramCounter).toString());
                parameters.get(paramCounter++).writeTo(os);
            } else {
                os.write(ch);
            }
        }
    }


    public String getQuery() {
        return query;
    }

    public int getParamCount() {
        return paramCount;
    }
}
