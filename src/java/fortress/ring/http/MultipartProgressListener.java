package fortress.ring.http;

import io.netty.handler.codec.http.HttpRequest;

public interface MultipartProgressListener {
    public void uploadStarted(HttpRequest request);
    public void bytesWritten(long byteCount);
    public void uploadFinished();
}
