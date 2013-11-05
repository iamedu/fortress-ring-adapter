package fortress.ring.http;

import io.netty.handler.codec.http.DefaultHttpRequest;
import java.io.File;

public class DiskHttpWrapper {
    private DefaultHttpRequest request;
    private File fileBody;

    public DiskHttpWrapper(DefaultHttpRequest request, File fileBody) {
        this.request = request;
        this.fileBody = fileBody;
    }

    public DefaultHttpRequest getRequest() {
        return request;
    }

    public File getBody() {
        return fileBody;
    }

}
