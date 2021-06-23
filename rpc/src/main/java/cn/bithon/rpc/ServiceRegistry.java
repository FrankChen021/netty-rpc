package cn.bithon.rpc;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServiceRegistry {

    private final Map<String, RpcServiceProvider> registry = new ConcurrentHashMap<>();

    public <T extends IService> void addService(Class<T> serviceType, T serviceImpl) {
        // override methods are not supported
        for (Method method : serviceType.getDeclaredMethods()) {
            registry.put(serviceType.getSimpleName() + "#" + method.getName(), new RpcServiceProvider(method,
                                                                                                      serviceImpl));
        }
    }

    public RpcServiceProvider findServiceProvider(CharSequence serviceName, CharSequence methodName) {
        return registry.get(serviceName + "#" + methodName);
    }

    public static class ParameterType {
        private final Class<?> rawType;
        private final Class<?> messageType;

        public ParameterType(Class<?> rawType, Class<?> messageType) {
            this.rawType = rawType;
            this.messageType = messageType;
        }

        public Class<?> getRawType() {
            return rawType;
        }

        public Class<?> getMessageType() {
            return messageType;
        }
    }

    public static class RpcServiceProvider {
        private final Method method;
        private final Object serviceImpl;
        private final boolean isReturnVoid;
        private final ParameterType[] parameterTypes;

        public RpcServiceProvider(Method method, Object serviceImpl) {
            this.method = method;
            this.serviceImpl = serviceImpl;
            this.isReturnVoid = method.getReturnType().equals(Void.TYPE);
            this.parameterTypes = new ParameterType[method.getParameterCount()];

            Class<?>[] parameterRawTypes = method.getParameterTypes();
            for (int i = 0; i < parameterRawTypes.length; i++) {
                parameterTypes[i] = new ParameterType(parameterRawTypes[i],
                                                      parameterRawTypes[i]);
            }
        }

        public Object invoke(Object[] args) throws InvocationTargetException, IllegalAccessException {
            return method.invoke(serviceImpl, args);
        }

        public boolean isReturnVoid() {
            return isReturnVoid;
        }

        public ParameterType[] getParameterTypes() {
            return parameterTypes;
        }
    }
}