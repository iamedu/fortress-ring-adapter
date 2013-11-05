package io.netty.handler.codec.spdy;

import fortress.ring.http.MultipartProgressListener;
import java.io.File;
import java.io.FileOutputStream;

public class MultipartMessageWrapper {
    private File tmpFile;
    private FileOutputStream outputStream;
    private MultipartProgressListener progressListener;
    private boolean fileBasedUpload;

    public MultipartMessageWrapper(File tmpFile, FileOutputStream outputStream, MultipartProgressListener progressListener, boolean fileBasedUpload) {
        this.tmpFile = tmpFile;
        this.outputStream = outputStream;
        this.progressListener = progressListener;
        this.fileBasedUpload = fileBasedUpload;
    }

    public File getTmpFile() {
        return tmpFile;
    }

    public FileOutputStream getOutputStream() {
        return outputStream;
    }

    public MultipartProgressListener getProgressListener() {
        return progressListener;
    }

    public boolean isFileBasedUpload() {
        return fileBasedUpload;
    }
           
}

