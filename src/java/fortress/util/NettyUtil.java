package fortress.util;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;

public class NettyUtil {
    public static ChannelPipeline pipeline(ChannelHandlerContext ctx) {
        return ctx.pipeline();
    }
}

