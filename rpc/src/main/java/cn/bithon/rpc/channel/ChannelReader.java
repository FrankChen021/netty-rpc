package cn.bithon.rpc.channel;

import cn.bithon.rpc.ServiceRegistry;
import cn.bithon.rpc.invocation.IServiceInvoker;
import cn.bithon.rpc.invocation.ServiceInvocationRunnable;
import cn.bithon.rpc.invocation.ServiceRequestManager;
import cn.bithon.rpc.message.ServiceMessage;
import cn.bithon.rpc.message.ServiceMessageType;
import cn.bithon.rpc.message.ServiceRequestMessage;
import cn.bithon.rpc.message.ServiceResponseMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ChannelHandler.Sharable
public class ChannelReader extends ChannelInboundHandlerAdapter {

    private final IServiceInvoker invoker;
    private final ServiceRegistry serviceRegistry;
    private final ObjectMapper om = new ObjectMapper();
    private boolean channelDebugEnabled;

    public ChannelReader(ServiceRegistry serviceRegistry) {
        this(serviceRegistry, ServiceInvocationRunnable::run);
    }

    public ChannelReader(ServiceRegistry serviceRegistry, IServiceInvoker dispatcher) {
        this.serviceRegistry = serviceRegistry;
        this.invoker = dispatcher;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof ServiceMessage)) {
            return;
        }

        ServiceMessage message = (ServiceMessage) msg;
        if (message.getMessageType() == ServiceMessageType.CLIENT_REQUEST) {
            ServiceRequestMessage request = (ServiceRequestMessage) message;
            if (channelDebugEnabled) {
                log.info("receiving request, txId={}, service={}#{}",
                         request.getTransactionId(),
                         request.getServiceName(),
                         request.getMethodName());
            }

            invoker.invoke(new ServiceInvocationRunnable(om,
                                                         serviceRegistry,
                                                         ctx.channel(),
                                                         (ServiceRequestMessage) message));
        } else if (message.getMessageType() == ServiceMessageType.SERVER_RESPONSE) {
            if (channelDebugEnabled) {
                log.info("receiving response, txId={}", message.getTransactionId());
            }

            ServiceRequestManager.getInstance().onResponse((ServiceResponseMessage) message);
        }
    }

    public boolean isChannelDebugEnabled() {
        return channelDebugEnabled;
    }

    public void setChannelDebugEnabled(boolean channelDebugEnabled) {
        this.channelDebugEnabled = channelDebugEnabled;
    }
}
