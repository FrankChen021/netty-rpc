import cn.bithon.rpc.IServiceHelper;
import cn.bithon.rpc.channel.ClientChannel;
import cn.bithon.rpc.channel.ServerChannel;
import cn.bithon.rpc.example.ICalculator;
import cn.bithon.rpc.exception.ServiceInvocationException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class RpcTest {

    ServerChannel serverChannel;

    @Before
    public void setup() {
        serverChannel = new ServerChannel()
            .bindService(ICalculator.class, new ICalculator() {

                @Override
                public int div(int a, int b) {
                    return a / b;
                }

                @Override
                public int block(int timeout) {
                    try {
                        Thread.sleep(timeout * 1000L);
                    } catch (InterruptedException ignored) {
                    }
                    return 0;
                }
            }).start(8070).debug(true);
    }

    @After
    public void teardown() {
        System.out.println("TestCase Teardown...");
        serverChannel.close();
    }

    @Test
    public void test() {
        try (ClientChannel ch = new ClientChannel("127.0.0.1", 8070)) {
            ICalculator calculator = ch.getRemoteService(ICalculator.class);
            IServiceHelper invoker = (IServiceHelper) calculator;
            invoker.debug(true);
            System.out.println("Start calling");
            Assert.assertEquals(2, calculator.div(6, 3));
            System.out.println("End calling");
        }
    }

    @Test
    public void testInvocationException() {
        try (ClientChannel ch = new ClientChannel("127.0.0.1", 8070)) {
            ICalculator calculator = ch.getRemoteService(ICalculator.class);

            try {
                calculator.div(6, 0);
                Assert.assertTrue(false);
            } catch (ServiceInvocationException e) {
                System.out.println("Exception Occurred when calling RPC:" + e.getMessage());
                Assert.assertTrue(e.getMessage().contains("/ by zero"));
            }
        }
    }

    @Test
    public void testClientSideTimeout() {
        try (ClientChannel ch = new ClientChannel("127.0.0.1", 8070)) {
            ICalculator calculator = ch.getRemoteService(ICalculator.class);

            calculator.block(2);

            try {
                calculator.block(6);
                Assert.assertTrue(false);
            } catch (ServiceInvocationException e) {
                Assert.assertTrue(true);
            }

            calculator.toInvoker().setTimeout(2000);
            try {
                calculator.block(3);
                Assert.assertTrue(false);
            } catch (ServiceInvocationException e) {
                Assert.assertTrue(true);
            }
        }
    }

    @Test
    public void testConcurrent() {
        try (ClientChannel ch = new ClientChannel("127.0.0.1", 8070)) {
            ICalculator calculator = ch.getRemoteService(ICalculator.class);

            AtomicInteger v = new AtomicInteger();
            AtomicInteger i = new AtomicInteger();
            ThreadLocalRandom.current().ints(1000, 5, 1000).parallel().forEach(divisor -> {
                try {
                    int idx = i.incrementAndGet();
                    int val = calculator.div(divisor, 1);
                    if (val != divisor) {
                        v.incrementAndGet();
                    }
                    System.out.printf("%s:%d, ret=%s\n", Thread.currentThread().getName(), idx, val == divisor);
                } catch (ServiceInvocationException e) {
                    System.out.println(e.getMessage());
                    v.incrementAndGet();
                }
            });

            Assert.assertEquals(0, v.get());
        }
    }

    /**
     * TODO：server--call-->client
     */
    @Test
    public void testServerCallsClient() {
        try (ClientChannel ch = new ClientChannel("127.0.0.1", 8070)) {
            // bind a service at client side
            ch.bindService(ICalculator.class, new ICalculator() {
                @Override
                public int div(int a, int b) {
                    return a / b;
                }

                @Override
                public int block(int timeout) {
                    throw new NotImplementedException();
                }
            });

            //make sure the client has been connected to the server
            ICalculator calculator = ch.getRemoteService(ICalculator.class);
            Assert.assertEquals(20, calculator.div(100, 5));

            Set<String> clients = serverChannel.getClientEndpoints();
            Assert.assertEquals(1, clients.size());

            String endpoint = clients.stream().findFirst().get();
            ICalculator clientCalculator = serverChannel.getRemoteService(endpoint, ICalculator.class);
            Assert.assertEquals(5, clientCalculator.div(100, 20));
        }
    }
}