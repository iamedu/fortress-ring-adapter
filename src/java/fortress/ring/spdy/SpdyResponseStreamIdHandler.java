package fortress.ring.spdy;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageCodec;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.util.ReferenceCountUtil;
import io.netty.handler.codec.spdy.SpdyRstStreamFrame;
import io.netty.handler.codec.spdy.SpdyHttpHeaders;

import fortress.ring.http.DiskHttpWrapper;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

public class SpdyResponseStreamIdHandler extends MessageToMessageCodec<Object, HttpMessage> {
    private static final Integer NO_ID = -1;
    private final Queue<Integer> ids = new LinkedList<Integer>();

    @Override
    public boolean acceptInboundMessage(Object msg) throws Exception {
        return msg instanceof HttpMessage || msg instanceof SpdyRstStreamFrame
            || msg instanceof DiskHttpWrapper;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, HttpMessage msg, List<Object> out) throws Exception {
        Integer id = ids.poll();
        if (id != null && id.intValue() != NO_ID && !msg.headers().contains(SpdyHttpHeaders.Names.STREAM_ID)) {
            SpdyHttpHeaders.setStreamId(msg, id);
        }

        out.add(ReferenceCountUtil.retain(msg));
    }


    @Override
    protected void decode(ChannelHandlerContext ctx, Object msg, List<Object> out) throws Exception {
        if(msg instanceof DiskHttpWrapper) {
            boolean contains = ((DiskHttpWrapper) msg).getRequest().headers().contains(SpdyHttpHeaders.Names.STREAM_ID);
            if (!contains) {
                ids.add(NO_ID);
            } else {
                ids.add(SpdyHttpHeaders.getStreamId(((DiskHttpWrapper) msg).getRequest()));
            }
        } else if (msg instanceof HttpMessage) {
            boolean contains = ((HttpMessage) msg).headers().contains(SpdyHttpHeaders.Names.STREAM_ID);
            if (!contains) {
                ids.add(NO_ID);
            } else {
                ids.add(SpdyHttpHeaders.getStreamId((HttpMessage) msg));
            }
        } else if (msg instanceof SpdyRstStreamFrame) {
            ids.remove(((SpdyRstStreamFrame) msg).getStreamId());
        }

        out.add(ReferenceCountUtil.retain(msg));
    }

}
