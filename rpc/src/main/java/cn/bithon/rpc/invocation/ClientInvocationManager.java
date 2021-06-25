package cn.bithon.rpc.invocation;

import cn.bithon.rpc.Oneway;
import cn.bithon.rpc.channel.IChannelWriter;
import cn.bithon.rpc.exception.ServiceInvocationException;
import cn.bithon.rpc.exception.TimeoutException;
import cn.bithon.rpc.message.in.ServiceResponseMessageIn;
import cn.bithon.rpc.message.out.ServiceRequestMessageOut;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.netty.channel.Channel;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manage inflight requests from a service client to a service provider
 * <p>
 * Note: the concept 'client' here is a relative concept.
 * It could be a network client, which connects to a RPC server,
 * it could also be a RPC server which calls service provided by a network client
 */
@Slf4j
public class ClientInvocationManager {

    private static final ClientInvocationManager INSTANCE = new ClientInvocationManager();
    private final AtomicLong transactionId = new AtomicLong(21515);
    private final ObjectMapper om = new JsonMapper();
    private final Map<Long, InflightRequest> inflightRequests = new ConcurrentHashMap<>();

    public static ClientInvocationManager getInstance() {
        return INSTANCE;
    }

    public Object invoke(IChannelWriter channelWriter, boolean debug, long timeout, Method method, Object[] args) {
        //
        // make sure channel has been established
        //
        channelWriter.connect();

        //
        // check channel status
        //
        Channel ch = channelWriter.getChannel();
        if (ch == null) {
            throw new ServiceInvocationException("Failed to invoke %s#%s due to channel is empty",
                                                 method.getDeclaringClass().getSimpleName(),
                                                 method.getName());
        }
        if (!ch.isActive()) {
            throw new ServiceInvocationException("Failed to invoke %s#%s due to channel is not active",
                                                 method.getDeclaringClass().getSimpleName(),
                                                 method.getName());
        }
        if (!ch.isWritable()) {
            throw new ServiceInvocationException("Failed to invoke %s#%s due to channel is not writable",
                                                 method.getDeclaringClass().getSimpleName(),
                                                 method.getName());
        }

        // TODO: cache method.toString()
        ServiceRequestMessageOut serviceRequest = ServiceRequestMessageOut.builder()
                                                                          .serviceName(method.getDeclaringClass()
                                                                                             .getSimpleName())
                                                                          .methodName(method.toString())
                                                                          .transactionId(transactionId.incrementAndGet())
                                                                          .args(args)
                                                                          .build();

        boolean isOneway = method.getAnnotation(Oneway.class) != null;
        InflightRequest inflightRequest = null;
        if (!isOneway) {
            inflightRequest = new InflightRequest();
            inflightRequest.requestAt = System.currentTimeMillis();
            inflightRequest.methodName = serviceRequest.getMethodName();
            inflightRequest.serviceName = serviceRequest.getServiceName();
            inflightRequest.returnObjType = method.getGenericReturnType();
            this.inflightRequests.put(serviceRequest.getTransactionId(), inflightRequest);
        }
        if (debug) {
            //log.info("[DEBUGGING] Sending message: {}", message);
        }
        channelWriter.writeAndFlush(serviceRequest);

        if (inflightRequest != null) {
            try {
                //noinspection SynchronizationOnLocalVariableOrMethodParameter
                synchronized (inflightRequest) {
                    inflightRequest.wait(timeout);
                }
            } catch (InterruptedException e) {
                inflightRequests.remove(serviceRequest.getTransactionId());
                throw new ServiceInvocationException("interrupted");
            }

            //make sure it has been cleared when timeout
            inflightRequests.remove(serviceRequest.getTransactionId());

            if (inflightRequest.exception != null) {
                throw new ServiceInvocationException(inflightRequest.exception);
            }

            if (!inflightRequest.returned) {
                throw new TimeoutException(serviceRequest.getServiceName(),
                                           serviceRequest.getMethodName(),
                                           5000);
            }

            return inflightRequest.response;
        }
        return null;
    }

    public void onResponse(ServiceResponseMessageIn response) {
        long txId = response.getTransactionId();
        InflightRequest inflightRequest = inflightRequests.remove(txId);
        if (inflightRequest == null) {
            return;
        }

        try {
            inflightRequest.response = response.getReturning(inflightRequest.returnObjType);
        } catch (IOException e) {
            e.printStackTrace();
        }

        inflightRequest.exception = response.getException();

        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (inflightRequest) {
            inflightRequest.returned = true;
            inflightRequest.notify();
        }
    }

    @Data
    static class InflightRequest {
        long requestAt;
        long responseAt;
        Type returnObjType;
        Object response;
        /**
         * indicate whether this request has response.
         * This is required so that {@link #response} might be null
         */
        boolean returned;
        CharSequence exception;
        private CharSequence serviceName;
        private CharSequence methodName;
    }
}
