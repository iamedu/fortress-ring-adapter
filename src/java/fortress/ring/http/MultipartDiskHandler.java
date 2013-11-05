package fortress.ring.http;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.HttpObject;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.util.ReferenceCounted;
import java.io.FileOutputStream;
import java.io.File;
import java.util.List;

public class MultipartDiskHandler extends MessageToMessageDecoder<HttpObject> {

    private FileOutputStream outputStream;
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String CONTENT_LENGTH = "Content-Length";
    private DefaultHttpRequest currentMessage;
    private boolean multipartRequest = false;
    private File tempDirectory;
    private File tempFile;
    private long maxMemorySize;

    public MultipartDiskHandler(File tempDirectory, long maxMemorySize) {
        this.tempDirectory = tempDirectory;
        this.maxMemorySize = maxMemorySize;
    }

    @Override
    public boolean acceptInboundMessage(Object msg) {
        if(msg instanceof DefaultHttpRequest) {
            DefaultHttpRequest request = (DefaultHttpRequest)msg;
            String ctype = request.headers().get(CONTENT_TYPE);
            return ctype != null && ctype.startsWith("multipart");
        } else if(multipartRequest) {
            return (msg instanceof DefaultHttpContent) || 
                   (msg instanceof DefaultLastHttpContent);
        }
        return false;
    }

    @Override
    public void decode(ChannelHandlerContext ctx, HttpObject message, List<Object> out) {
        if(message instanceof ReferenceCounted) {
            ((ReferenceCounted)message).retain();
        }
        
        if(message instanceof DefaultHttpRequest) {
            if(!handleMultipartMessage((DefaultHttpRequest)message)) {
                out.add(message);
            }
        } else {
            writeContent((DefaultHttpContent)message);
        }

        if(message instanceof DefaultLastHttpContent) {
            handleEnding(out);
        }
    }

    private boolean handleMultipartMessage(DefaultHttpRequest request) {
        long contentLength = Long.parseLong(request.headers().get(CONTENT_LENGTH));
        if(contentLength > maxMemorySize) {
            currentMessage = request;
            multipartRequest = true;
            tempFile = createFile();
            try {
                outputStream = new FileOutputStream(tempFile);
            } catch(Exception ex) {
                throw new RuntimeException(ex);
            }
            return true;
        } else {
            return false;
        }
    }

    private void writeContent(DefaultHttpContent content) {
        try {
            int length = content.content().readableBytes();
            content.content().readBytes(outputStream, length);
            content.content().release();
        } catch(Exception ex) {
            if(outputStream != null) {
                try {
                    outputStream.close();
                } catch(Exception iex) {
                    //Report original exception
                    throw new RuntimeException(ex);
                }
            }
            throw new RuntimeException(ex);
        }
    }

    private void handleEnding(List<Object> out) {
        try {
            outputStream.close();
            out.add(new DiskHttpWrapper(currentMessage, tempFile));
        } catch(Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private File createFile() {
        try {
            File f = File.createTempFile("fortress", ".multipart", tempDirectory);
            f.deleteOnExit();
            return f;
        } catch(Exception ex) {
            throw new RuntimeException(ex);
        }
    }

}
