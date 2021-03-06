package com.sbss.bithon.component.brpc.example;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ExampleServiceImpl implements IExampleService {

    @Override
    public int div(int a, int b) {
        return a / b;
    }

    @Override
    public void block(int timeout) {
        try {
            Thread.sleep(timeout * 1000L);
        } catch (InterruptedException ignored) {
        }
    }

    @Override
    public void sendOneway(String msg) {
        System.out.println("Got message:" + msg);
        try {
            Thread.sleep(3000);
        } catch (InterruptedException ignored) {
        }
    }

    @Override
    public int[] append(int[] arrays, int value) {
        int[] newArray = Arrays.copyOf(arrays, arrays.length + 1);
        newArray[arrays.length] = value;
        return newArray;
    }

    @Override
    public String[] append(String[] arrays, String value) {
        String[] newArray = Arrays.copyOf(arrays, arrays.length + 1);
        newArray[arrays.length] = value;
        return newArray;
    }

    @Override
    public List<String> delete(List<String> list, int index) {
        list.remove(index);
        return list;
    }

    @Override
    public Map<String, String> merge(Map<String, String> a, Map<String, String> b) {
        if (b != null) {
            if (a != null) {
                b.forEach(a::put);
            } else {
                return b;
            }
        }
        return a;
    }

    @Override
    public String send(WebRequestMetrics metrics) {
        System.out.printf("Receiving metrics: %s\n", metrics);
        return metrics.getUri();
    }

    @Override
    public String send(WebRequestMetrics metrics1, WebRequestMetrics metrics2) {
        return metrics1.getUri() + "-" + metrics2.getUri();
    }

    @Override
    public String send(String uri, WebRequestMetrics metrics) {
        return uri + "-" + metrics.getUri();
    }

    @Override
    public String send(WebRequestMetrics metrics, String uri) {
        return metrics.getUri() + "-" + uri;
    }

    @Override
    public Map<String, String> mergeWithJson(Map<String, String> a, Map<String, String> b) {
        return merge(a, b);
    }

    @Override
    public String ping() {
        return "pong";
    }
}
