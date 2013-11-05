package fortress.ring.http;

import io.netty.handler.codec.http.DefaultHttpRequest;

public interface MultipartProgressListener {
    public void uploadStarted(DefaultHttpRequest request);
    public void bytesWritten(long byteCount);
    public void uploadFinished();
}
