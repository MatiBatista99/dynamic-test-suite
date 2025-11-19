package ar.cabal.channels;

import java.io.*;
import java.net.ServerSocket;
import org.jpos.iso.*;
import org.jpos.util.*;

public class B24Channel extends BaseChannel {
    protected byte[] header;
    /**
     * Public constructor (used by Class.forName("...").newInstance())
     */
    public B24Channel () {
        super();
    }
    /**
     * Construct client ISOChannel
     * @param host  server TCP Address
     * @param port  server port number
     * @param p     an ISOPackager
     * @see ISOPackager
     */
    public B24Channel (String host, int port, ISOPackager p) {
        super(host, port, p);
    }
    /**
     * Construct server ISOChannel
     * @param p     an ISOPackager
     * @see ISOPackager
     * @exception IOException
     */
    public B24Channel (ISOPackager p) throws IOException {
        super(p);
    }
    /**
     * constructs a server ISOChannel associated with a Server Socket
     * @param p     an ISOPackager
     * @param serverSocket where to accept a connection
     * @exception IOException
     * @see ISOPackager
     */
    public B24Channel (ISOPackager p, ServerSocket serverSocket)
            throws IOException
    {
        super(p, serverSocket);
    }
    /**
     * @param m the Message to send (in this case it is unused)
     * @param b trailler (ignored)
     * @exception IOException
     */
    @Override
    protected void sendMessageTrailler(ISOMsg m, byte[] b) throws IOException {
        // serverOut.write (3);
    }
    @Override
    protected void sendMessageHeader(ISOMsg m, int len) throws IOException {
        LogEvent evt = new LogEvent (this, "send-message-header");
        if (m.getHeader() != null) {
            evt.addMessage ("message-header: "+
                    ISOUtil.hexString (m.getHeader())
            );
            serverOut.write(m.getHeader());
        }
        else if (header != null) {
            evt.addMessage ("channel-header: "+
                    ISOUtil.hexString (header)
            );
            serverOut.write(header);
        }
        Logger.log (evt);
    }
    @Override
    protected int getHeaderLength() {
        return header != null ? header.length : 0;
    }
    @Override
    public void setHeader (byte[] header) {
        this.header = header;
    }
    @Override
    public void setHeader (String header) {
        setHeader (header.getBytes());
    }
    @Override
    public byte[] getHeader () {
        return header;
    }
    @Override
    protected void sendMessageLength(int len) throws IOException {
        // len++;  // one byte trailler
        serverOut.write (len >> 8);
        serverOut.write (len);
    }
    @Override
    protected int getMessageLength() throws IOException, ISOException {
        int l = 0;
        byte[] b = new byte[2];
        Logger.log (new LogEvent (this, "get-message-length"));
        while (l == 0) {
            serverIn.readFully(b,0,2);
            l = ((((int)b[0])&0xFF) << 8) | (((int)b[1])&0xFF);
            if (l == 0) {
                serverOut.write(b);
                serverOut.flush();
            }
        }
        Logger.log (new LogEvent (this, "got-message-length", Integer.toString(l)));
        return l;
    }
    @Override
    protected void getMessageTrailler() throws IOException {
        // Logger.log (new LogEvent (this, "get-message-trailler"));
        // byte[] b = new byte[1];
        // serverIn.readFully(b,0,1);
        // Logger.log (new LogEvent (this, "got-message-trailler", ISOUtil.hexString(b)));
    }
}


