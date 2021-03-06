package io.hydrosphere.serving.gateway.grpc

import io.hydrosphere.serving.gateway.service.{ApplicationExecutionService, RequestTracingInfo}
import io.hydrosphere.serving.grpc.Headers
import io.hydrosphere.serving.tensorflow.api.predict.{PredictRequest, PredictResponse}
import io.hydrosphere.serving.tensorflow.api.prediction_service.PredictionServiceGrpc.PredictionService
import org.apache.logging.log4j.scala.Logging

import scala.concurrent.{ExecutionContext, Future}

class GrpcPredictionService(
  gatewayPredictionService: ApplicationExecutionService
)(implicit ec: ExecutionContext) extends PredictionService with Logging {

  override def predict(request: PredictRequest): Future[PredictResponse] = {
    logger.info(s"Got grpc request modelSpec=${request.modelSpec}")
    request.modelSpec match {
      case Some(_) =>
        val requestId = Option(Headers.XRequestId.contextKey.get())
        gatewayPredictionService.serveGrpcApplication(
          request,
          requestId.map(r => RequestTracingInfo(
            xRequestId = r,
            xB3requestId = Option(Headers.XB3TraceId.contextKey.get()),
            xB3SpanId = Option(Headers.XB3SpanId.contextKey.get())
          ))).flatMap {
          case Left(err) => Future.failed(new RuntimeException(err.toString))
          case Right(value) => Future.successful(value)
        }
      case None => Future.failed(new IllegalArgumentException("ModelSpec is not defined"))
    }
  }
}
