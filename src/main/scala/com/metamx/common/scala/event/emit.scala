package com.metamx.common.scala.event

import com.metamx.common.scala.Logging._
import com.metamx.emitter.service.{AlertBuilder, ServiceEmitter}
import com.metamx.emitter.service.AlertEvent.Severity
import com.metamx.common.scala.untyped._
import com.metamx.common.scala.Json
import com.metamx.common.scala.Predef._
import scala.compat.Platform
import org.scala_tools.time.Imports._
import org.codehaus.jackson.map.ObjectMapper
import Severity._

object emit
{

  def emitAlert(log: Logger, emitter: ServiceEmitter, severity: Severity, description: String, data: Dict) {
    emitAlert(null, log, emitter, severity, description, data)
  }

  def emitAlert(
    e:           Throwable,
    log:         Logger,
    emitter:     ServiceEmitter,
    severity:    Severity,
    description: String,
    data:        Dict
    ) {
    ((if (severity == ANOMALY) log.warn(_,_) else log.error(_,_)): (Throwable, String) => Unit)(
      e, "Emitting alert: [%s] %s\n%s" format (severity, description, Json.pretty(data))
    )

    emitter.emit({
      AlertBuilder.create(description).severity(severity) withEffect { x =>
        data foreach { case (k, v) => x.addData(k, v) }
      }
    } build)
  }

  def emitMetricTimed[T](emitter: ServiceEmitter,
    metric: Metric)(action: => T) = {
    val t0 = Platform.currentTime
    val res = action
    val t = Platform.currentTime - t0
    emitter.emit(metric + Metric(value = t, created = new DateTime()))
    res
  }

  // HACK: Map scala-native types to java types by writing out through jerkson and reading back in through jackson.
  // This will not only normalize scala collections, which untyped.normalizeJava knows how to do, but also things like
  // Option and Either, which untyped.normalizeJava doesn't and shouldn't know how to do. (It would be nice if we could
  // wholly extract the scala->java behavior out of jerkson...)
  def normalizeJavaViaJson(x: Any): Any = _jacksonMapper.readValue(Json.generate(x), classOf[Any])
  private lazy val _jacksonMapper = new ObjectMapper

}