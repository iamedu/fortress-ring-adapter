package fortress.util;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelFuture;

public class NettyHelper {
    public static ChannelFuture writeAndFlush(ChannelHandlerContext ctx, Object msg) {
        return ctx.writeAndFlush(msg);
    }   

    public static ChannelFuture write(ChannelHandlerContext ctx, Object msg) {
        return ctx.write(msg);
    }   

    public static ChannelHandlerContext flush(ChannelHandlerContext ctx) {
        return ctx.flush();
    }
}

