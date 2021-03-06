// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: metrics/webrequest.proto

package com.sbss.bithon.component.brpc.example;

public interface WebRequestMetricsOrBuilder extends
    // @@protoc_insertion_point(interface_extends:cn.bithon.rpc.example.WebRequestMetrics)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>string uri = 2;</code>
   * @return The uri.
   */
  String getUri();
  /**
   * <code>string uri = 2;</code>
   * @return The bytes for uri.
   */
  com.google.protobuf.ByteString
      getUriBytes();

  /**
   * <code>int64 costNanoTime = 3;</code>
   * @return The costNanoTime.
   */
  long getCostNanoTime();

  /**
   * <code>int64 requests = 4;</code>
   * @return The requests.
   */
  long getRequests();

  /**
   * <code>int64 count4xx = 6;</code>
   * @return The count4xx.
   */
  long getCount4Xx();

  /**
   * <code>int64 count5xx = 7;</code>
   * @return The count5xx.
   */
  long getCount5Xx();

  /**
   * <code>int64 requestBytes = 8;</code>
   * @return The requestBytes.
   */
  long getRequestBytes();

  /**
   * <code>int64 responseBytes = 9;</code>
   * @return The responseBytes.
   */
  long getResponseBytes();
}
