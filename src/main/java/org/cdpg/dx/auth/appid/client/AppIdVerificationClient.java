package org.cdpg.dx.auth.appid.client;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.auth.appid.v1.AppIdVerificationServiceGrpc;
import org.cdpg.dx.auth.appid.v1.VerifyAppIdRequest;
import org.cdpg.dx.auth.appid.v1.VerifyAppIdResponse;

/**
 * Async gRPC client for the AppIdVerificationService hosted on dx-controlplane.
 *
 * <p>Uses a non-blocking async stub so gRPC callbacks do not block the Vert.x event-loop. The
 * returned {@link Future} is completed on the gRPC callback thread; callers should use
 * {@code .onSuccess()} / {@code .onFailure()} rather than blocking.
 *
 * <p>TLS: currently uses plaintext ({@code usePlaintext()}). For production across clusters,
 * replace with {@code useTransportSecurity()} and configure the appropriate trust manager
 * in {@link ManagedChannelBuilder} (OQ3 — pending network topology confirmation).
 */
public class AppIdVerificationClient {

  private static final Logger LOGGER = LogManager.getLogger(AppIdVerificationClient.class);

  private final AppIdVerificationServiceGrpc.AppIdVerificationServiceStub asyncStub;

  public AppIdVerificationClient(String host, int port) {
    ManagedChannel channel =
        ManagedChannelBuilder.forAddress(host, port)
            .usePlaintext() // TODO(OQ3): switch to TLS for cross-cluster production
            .keepAliveTime(30, TimeUnit.SECONDS)
            .build();
    this.asyncStub = AppIdVerificationServiceGrpc.newStub(channel);
  }

  /**
   * Sends a VerifyAppId RPC to dx-controlplane.
   *
   * @param appId UUID string — safe to log
   * @param appSecret plaintext secret — NEVER log
   */
  public Future<VerifyAppIdResponse> verify(String appId, String appSecret) {
    Promise<VerifyAppIdResponse> promise = Promise.promise();
    asyncStub.verifyAppId(
        VerifyAppIdRequest.newBuilder().setAppId(appId).setAppSecret(appSecret).build(),
        new StreamObserver<>() {
          @Override
          public void onNext(VerifyAppIdResponse response) {
            promise.complete(response);
          }

          @Override
          public void onError(Throwable t) {
            LOGGER.error("gRPC VerifyAppId failed appId={}: {}", appId, t.getMessage());
            promise.fail(t);
          }

          @Override
          public void onCompleted() {
            // no-op — promise already completed in onNext
          }
        });
    return promise.future();
  }
}
