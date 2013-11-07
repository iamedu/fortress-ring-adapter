package fortress.ring.spdy;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.util.ReferenceCounted;

import java.util.List;

public class HttpsSchemeAdder extends MessageToMessageDecoder<HttpRequest> {
    @Override
    public void decode(ChannelHandlerContext ctx, HttpRequest message, List<Object> out) {
        message.headers().add("X-Scheme", "https");
        out.add(message);
        if(message instanceof ReferenceCounted) {
            ((ReferenceCounted)message).retain();
        }
    }
}
