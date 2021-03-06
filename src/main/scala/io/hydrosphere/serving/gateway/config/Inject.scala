package io.hydrosphere.serving.gateway.config

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.headers.HttpOriginRange
import akka.stream.ActorMaterializer
import ch.megard.akka.http.cors.scaladsl.model.HttpHeaderRange
import ch.megard.akka.http.cors.scaladsl.settings.CorsSettings
import io.grpc.{Channel, ClientInterceptors, Deadline, ManagedChannelBuilder}
import io.hydrosphere.serving.gateway.service.{ApplicationExecutionServiceImpl, ApplicationStorageImpl, XDSApplicationUpdateService}
import io.hydrosphere.serving.grpc.{AuthorityReplacerInterceptor, Headers}
import io.hydrosphere.serving.monitoring.monitoring.MonitoringServiceGrpc
import io.hydrosphere.serving.profiler.profiler.DataProfilerServiceGrpc
import io.hydrosphere.serving.tensorflow.api.prediction_service.PredictionServiceGrpc
import org.apache.logging.log4j.scala.Logging

import scala.collection.immutable.Seq
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

object Inject extends Logging {
  logger.info("Reading configuration")
  implicit val appConfig: Configuration = Configuration.loadOrFail()
  logger.info(s"Configuration: $appConfig")

  implicit val actorSystem = ActorSystem("hydroserving-gateway")
  implicit val actorMat = ActorMaterializer.create(actorSystem)

  implicit val executor: ExecutionContextExecutor = ExecutionContext.global
  implicit val corsSettings: CorsSettings.Default = CorsSettings.Default(
    allowGenericHttpRequests = true,
    allowCredentials = true,
    allowedOrigins = HttpOriginRange.*,
    allowedHeaders = HttpHeaderRange.*,
    allowedMethods = Seq(GET, POST, HEAD, OPTIONS, DELETE),
    exposedHeaders = Seq.empty,
    maxAge = Some(30 * 60)
  )

  logger.debug(s"Setting up GRPC sidecar channel")
  private val builder = ManagedChannelBuilder
    .forAddress(appConfig.sidecar.host, appConfig.sidecar.port)
  builder.enableRetry()
  builder.usePlaintext()

  val sidecarChannel: Channel = ClientInterceptors
    .intercept(builder.build, new AuthorityReplacerInterceptor +: Headers.interceptors: _*)

  val predictGrpcClient = PredictionServiceGrpc.stub(sidecarChannel)
  val profilerGrpcClient = DataProfilerServiceGrpc.stub(sidecarChannel)
  val monitoringGrpcClient = MonitoringServiceGrpc.stub(sidecarChannel)

  logger.debug(s"Initializing application storage")
  implicit val applicationStorage = new ApplicationStorageImpl()

  logger.debug(s"Initializing application update service")
  implicit val applicationUpdater = new XDSApplicationUpdateService(applicationStorage, appConfig.sidecar)

  logger.debug("Initializing app execution service")
  implicit val gatewayPredictionService = new ApplicationExecutionServiceImpl(
    appConfig.application,
    applicationStorage,
    predictGrpcClient,
    profilerGrpcClient,
    monitoringGrpcClient
  )
}