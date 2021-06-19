package cn.bithon.rpc.core;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import io.netty.util.CharsetUtil;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class ServiceHost {

    private final NioEventLoopGroup bossGroup = new NioEventLoopGroup();
    private final NioEventLoopGroup workerGroup = new NioEventLoopGroup();

    public <T extends IService> ServiceHost addService(Class<T> interfaceClass, T impl) {
        ServiceRegistry.register(interfaceClass, impl);
        return this;
    }

    public ServiceHost start(int port) {
        ServerBootstrap serverBootstrap = new ServerBootstrap();
        serverBootstrap
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel.class)
            //服务端可连接队列数,对应TCP/IP协议listen函数中backlog参数
            .option(ChannelOption.SO_BACKLOG, 1024)
            //设置TCP长连接,一般如果两个小时内没有数据的通信时,TCP会自动发送一个活动探测数据报文
            .childOption(ChannelOption.SO_KEEPALIVE, false)
            //将小的数据包包装成更大的帧进行传送，提高网络的负载
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childHandler(new ChannelInitializer<NioSocketChannel>() {
                @Override
                protected void initChannel(NioSocketChannel ch) {
                    ch.pipeline().addLast("decoder", new StringDecoder(CharsetUtil.UTF_8));
                    ch.pipeline().addLast("encoder", new StringEncoder(CharsetUtil.UTF_8));
                    ch.pipeline().addLast(new ServiceChannelReader());
                    ch.pipeline().addLast(new ClientChannelManager());
                }
            });
        serverBootstrap.bind(port);

        return this;
    }

    public void stop() {
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

    class ClientChannelManager extends ChannelInboundHandlerAdapter {
        @Override
        public void channelActive(ChannelHandlerContext ctx) throws Exception {
            Channel channel = ctx.channel();
            clientServices.computeIfAbsent(channel.remoteAddress().toString(), key -> new ClientService(channel));
            super.channelActive(ctx);
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends IService> T getServiceStub(String clientEndpoint, Class<T> serviceClass) {
        ClientService clientService = clientServices.get(clientEndpoint);
        if (clientService == null) {
            return null;
        }

        return (T) clientService.services.computeIfAbsent(serviceClass,
                                                          key -> ServiceStubBuilder.create(() -> clientService.channel,
                                                                                           serviceClass));
    }
}