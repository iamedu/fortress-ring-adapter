package fortress.ring.spdy;

import io.netty.handler.stream.ChunkedInput;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.DefaultHttpContent;

import java.nio.channels.ClosedChannelException;
import java.util.ArrayDeque;
import java.util.Queue;

public class SpdyChunkedWriteHandler
    extends ChannelDuplexHandler {

    private static final InternalLogger logger =
        InternalLoggerFactory.getInstance(SpdyChunkedWriteHandler.class);

    private final Queue<PendingWrite> queue = new ArrayDeque<PendingWrite>();
    private volatile ChannelHandlerContext ctx;
    private PendingWrite currentWrite;

    public SpdyChunkedWriteHandler() {
    }

    /**
     * @deprecated use {@link #ChunkedWriteHandler()}
     */
    @Deprecated
    public SpdyChunkedWriteHandler(int maxPendingWrites) {
        if (maxPendingWrites <= 0) {
            throw new IllegalArgumentException(
                    "maxPendingWrites: " + maxPendingWrites + " (expected: > 0)");
        }
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
        this.ctx = ctx;
    }

    /**
     * Continues to fetch the chunks from the input.
     */
    public void resumeTransfer() {
        final ChannelHandlerContext ctx = this.ctx;
        if (ctx == null) {
            return;
        }
        if (ctx.executor().inEventLoop()) {
            try {
                doFlush(ctx);
            } catch (Exception e) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Unexpected exception while sending chunks.", e);
                }
            }
        } else {
            // let the transfer resume on the next event loop round
            ctx.executor().execute(new Runnable() {

                @Override
                public void run() {
                    try {
                        doFlush(ctx);
                    } catch (Exception e) {
                        if (logger.isWarnEnabled()) {
                            logger.warn("Unexpected exception while sending chunks.", e);
                        }
                    }
                }
            });
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        queue.add(new PendingWrite(msg, promise));
    }

    @Override
    public void flush(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        if (channel.isWritable() || !channel.isActive()) {
            doFlush(ctx);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        doFlush(ctx);
        super.channelInactive(ctx);
    }

    @Override
    public void channelWritabilityChanged(ChannelHandlerContext ctx) throws Exception {
        if (ctx.channel().isWritable()) {
            // channel is writable again try to continue flushing
            doFlush(ctx);
        }
        ctx.fireChannelWritabilityChanged();
    }

    private void discard(Throwable cause) {
        for (;;) {
            PendingWrite currentWrite = this.currentWrite;

            if (this.currentWrite == null) {
                currentWrite = queue.poll();
            } else {
                this.currentWrite = null;
            }

            if (currentWrite == null) {
                break;
            }
            Object message = currentWrite.msg;
            if (message instanceof ChunkedInput) {
                ChunkedInput<?> in = (ChunkedInput<?>) message;
                try {
                    if (!in.isEndOfInput()) {
                        if (cause == null) {
                            cause = new ClosedChannelException();
                        }
                        currentWrite.fail(cause);
                    } else {
                        currentWrite.success();
                    }
                    closeInput(in);
                } catch (Exception e) {
                    currentWrite.fail(e);
                    logger.warn(ChunkedInput.class.getSimpleName() + ".isEndOfInput() failed", e);
                    closeInput(in);
                }
            } else {
                if (cause == null) {
                    cause = new ClosedChannelException();
                }
                currentWrite.fail(cause);
            }
        }
    }

    private void doFlush(final ChannelHandlerContext ctx) throws Exception {
        final Channel channel = ctx.channel();
        if (!channel.isActive()) {
            discard(null);
            return;
        }
        boolean needsFlush;
        while (channel.isWritable()) {
            if (currentWrite == null) {
                currentWrite = queue.poll();
            }

            if (currentWrite == null) {
                break;
            }
            needsFlush = true;
            final PendingWrite currentWrite = this.currentWrite;
            final Object pendingMessage = currentWrite.msg;

            if (pendingMessage instanceof ChunkedInput) {
                final ChunkedInput<?> chunks = (ChunkedInput<?>) pendingMessage;
                boolean endOfInput;
                boolean suspend;
                Object message = null;
                try {
                    message = chunks.readChunk(ctx);
                    endOfInput = chunks.isEndOfInput();

                    if (message == null) {
                        // No need to suspend when reached at the end.
                        suspend = !endOfInput;
                    } else {
                        suspend = false;
                    }
                } catch (final Throwable t) {
                    this.currentWrite = null;

                    if (message != null) {
                        ReferenceCountUtil.release(message);
                    }

                    currentWrite.fail(t);
                    closeInput(chunks);
                    break;
                }

                if (suspend) {
                    // ChunkedInput.nextChunk() returned null and it has
                    // not reached at the end of input. Let's wait until
                    // more chunks arrive. Nothing to write or notify.
                    break;
                }

                if (message == null) {
                    // If message is null write an empty ByteBuf.
                    // See https://github.com/netty/netty/issues/1671
                    message = Unpooled.EMPTY_BUFFER;
                }

                final int amount = amount(message);
                if(endOfInput) {
                    message = new DefaultLastHttpContent((ByteBuf)message);
                } else {
                    message = new DefaultHttpContent((ByteBuf)message);
                }

                ChannelFuture f = ctx.write(message);
                if (endOfInput) {
                    this.currentWrite = null;

                    // Register a listener which will close the input once the write is complete.
                    // This is needed because the Chunk may have some resource bound that can not
                    // be closed before its not written.
                    //
                    // See https://github.com/netty/netty/issues/303
                    f.addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            currentWrite.progress(amount);
                            currentWrite.success();
                            closeInput(chunks);
                        }
                    });
                } else if (channel.isWritable()) {
                    f.addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            if (!future.isSuccess()) {
                                closeInput((ChunkedInput<?>) pendingMessage);
                                currentWrite.fail(future.cause());
                            } else {
                                currentWrite.progress(amount);
                            }
                        }
                    });
                } else {
                    f.addListener(new ChannelFutureListener() {
                        @Override
                        public void operationComplete(ChannelFuture future) throws Exception {
                            if (!future.isSuccess()) {
                                closeInput((ChunkedInput<?>) pendingMessage);
                                currentWrite.fail(future.cause());
                            } else {
                                currentWrite.progress(amount);
                                if (channel.isWritable()) {
                                    resumeTransfer();
                                }
                            }
                        }
                    });
                }
            } else {
                ctx.write(pendingMessage, currentWrite.promise);
                this.currentWrite = null;
            }

            if (needsFlush) {
                ctx.flush();
            }
            if (!channel.isActive()) {
                discard(new ClosedChannelException());
                return;
            }
        }
    }

    static void closeInput(ChunkedInput<?> chunks) {
        try {
            chunks.close();
        } catch (Throwable t) {
            if (logger.isWarnEnabled()) {
                logger.warn("Failed to close a chunked input.", t);
            }
        }
    }

    private static final class PendingWrite {
        final Object msg;
        final ChannelPromise promise;
        private long progress;

        PendingWrite(Object msg, ChannelPromise promise) {
            this.msg = msg;
            this.promise = promise;
        }

        void fail(Throwable cause) {
            ReferenceCountUtil.release(msg);
            if (promise != null) {
                promise.tryFailure(cause);
            }
        }

        void success() {
            if (promise instanceof ChannelProgressivePromise) {
                // Now we know what the total is.
                ((ChannelProgressivePromise) promise).tryProgress(progress, progress);
            }
            promise.setSuccess();
        }

        void progress(int amount) {
            progress += amount;
            if (promise instanceof ChannelProgressivePromise) {
                ((ChannelProgressivePromise) promise).tryProgress(progress, -1);
            }
        }
    }

    private static int amount(Object msg) {
        if (msg instanceof ByteBuf) {
            return ((ByteBuf) msg).readableBytes();
        }
        if (msg instanceof ByteBufHolder) {
            return ((ByteBufHolder) msg).content().readableBytes();
        }
        return 1;
    }
}
