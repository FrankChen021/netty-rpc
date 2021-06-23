package cn.bithon.rpc.channel;

import cn.bithon.rpc.IService;
import cn.bithon.rpc.ServiceRegistry;
import cn.bithon.rpc.invocation.ServiceMessageHandler;
import cn.bithon.rpc.invocation.ServiceStubFactory;
import cn.bithon.rpc.message.ServiceMessageDecoder;
import cn.bithon.rpc.message.ServiceMessageEncoder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;

import java.io.Closeable;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ServerChannel implements Closeable {

    private final NioEventLoopGroup bossGroup = new NioEventLoopGroup(1);
    private final NioEventLoopGroup workerGroup = new NioEventLoopGroup();
    private final ServiceRegistry serviceRegistry = new ServiceRegistry();

    /**
     * 服务端的请求直接在worker线程中处理，无需单独定义线程池
     */
    private final ServiceMessageHandler channelReader = new ServiceMessageHandler(serviceRegistry);

    public <T extends IService> ServerChannel bindService(Class<T> interfaceClass, T impl) {
        serviceRegistry.addService(interfaceClass, impl);
        return this;
    }

    public ServerChannel start(int port) {

        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            //设置底层使用对象池减少内存开销 提升GC效率
            .option(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
            //服务端可连接队列数,对应TCP/IP协议listen函数中backlog参数
            .option(ChannelOption.SO_BACKLOG, 1024)
            //设置TCP长连接,一般如果两个小时内没有数据的通信时,TCP会自动发送一个活动探测数据报文
            .childOption(ChannelOption.SO_KEEPALIVE, false)
            //将小的数据包包装成更大的帧进行传送，提高网络的负载
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childHandler(new ChannelInitializer<NioSocketChannel>() {
                @Override
                protected void initChannel(NioSocketChannel ch) {
                    ChannelPipeline pipeline = ch.pipeline();
                    pipeline.addLast("frameDecoder", new LengthFieldBasedFrameDecoder(16384, 0, 4, 0, 4));
                    pipeline.addLast("frameEncoder", new LengthFieldPrepender(4));
                    pipeline.addLast("decoder", new ServiceMessageDecoder());
                    pipeline.addLast("encoder", new ServiceMessageEncoder());
                    pipeline.addLast(channelReader);
                    pipeline.addLast(new ClientServiceManager());
                }
            });
        try {
            serverBootstrap.bind(port).sync();
        } catch (InterruptedException e) {
            System.exit(-1);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            close();
        }));
        return this;
    }

    public ServerChannel debug(boolean on) {
        channelReader.setChannelDebugEnabled(on);
        return this;
    }

    @Override
    public void close() {
        try {
            bossGroup.shutdownGracefully().sync();
        } catch (InterruptedException ignored) {
        }
        try {
            workerGroup.shutdownGracefully().sync();
        } catch (InterruptedException ignored) {
        }
    }

    static class ClientService {
        public ClientService(Channel channel) {
            this.channel = channel;
        }

        private final Channel channel;
        private final Map<Class<? extends IService>, IService> services = new ConcurrentHashMap<>();
    }

    private final Map<String, ClientService> clientServices = new ConcurrentHashMap<>();

    public Set<String> getClientEndpoints() {
        return clientServices.keySet();
    }

    class ClientServiceManager extends ChannelInboundHandlerAdapter {
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            Channel channel = ctx.channel();
            clientServices.computeIfAbsent(channel.remoteAddress().toString(), key -> new ClientService(channel));
            super.channelActive(ctx);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            clientServices.remove(ctx.channel().remoteAddress().toString());
            super.channelInactive(ctx);
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends IService> T getRemoteService(String clientEndpoint, Class<T> serviceClass) {
        ClientService clientService = clientServices.get(clientEndpoint);
        if (clientService == null) {
            return null;
        }

        return (T) clientService.services.computeIfAbsent(serviceClass,
                                                          key -> ServiceStubFactory.create(new IChannelWriter() {
                                                                                               @Override
                                                                                               public Channel getChannel() {
                                                                                                   return clientService.channel;
                                                                                               }

                                                                                               @Override
                                                                                               public void writeAndFlush(Object obj) {
                                                                                                   clientService.channel.writeAndFlush(obj);
                                                                                               }
                                                                                           },
                                                                                           serviceClass));
    }
}