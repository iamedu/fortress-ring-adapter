package io.netty.handler.codec.spdy;

import java.io.File;
import java.io.FileOutputStream;

public class MultipartMessageWrapper {
    private File tmpFile;
    private FileOutputStream outputStream;

    public MultipartMessageWrapper(File tmpFile, FileOutputStream outputStream) {
        this.tmpFile = tmpFile;
        this.outputStream = outputStream;
    }

    public File getTmpFile() {
        return tmpFile;
    }

    public FileOutputStream getOutputStream() {
        return outputStream;
    }
           
}

